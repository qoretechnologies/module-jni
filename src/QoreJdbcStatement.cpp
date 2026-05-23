/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcStatement.cpp

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2023 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

#include "QoreJdbcStatement.h"
#include "Globals.h"
#include "QoreToJava.h"
#include "JavaToQore.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <memory>
#include <set>

namespace jni {

QoreJdbcColumn::QoreJdbcColumn(std::string&& name, std::string&& qname, jint ctype,
        std::string&& native_type, jint precision, jint scale, bool nullable) : name(std::move(name)),
        qname(std::move(qname)), strip(ctype == Globals::typeChar), ctype(ctype),
        native_type(std::move(native_type)), precision(precision), scale(scale), nullable(nullable) {
}

QoreJdbcStatement::~QoreJdbcStatement() {
    if (stmt) {
        Env env;
        close(env);
    }
}

bool QoreJdbcStatement::exec(Env& env, ExceptionSink* xsink, const QoreString& qstr, const QoreListNode* args) {
    assert(!stmt);

    // Convert string to required character encoding.
    TempEncodingHelper str(qstr, QCS_UTF8, xsink);
    if (*xsink) {
        return false;
    }
    str.makeTemp();
    if (parse(const_cast<QoreString*>(*str), args, xsink)) {
        return false;
    }

    prepareAndBindStatement(env, xsink, **str);

    return execIntern(env, **str, xsink);
}

void QoreJdbcStatement::prepareAndBindStatement(Env& env, ExceptionSink* xsink, const QoreString& str) {
    prepareStatement(env, str);
    bindQueryArguments(env, xsink);
}

void QoreJdbcStatement::prepareStatement(Env& env, const QoreString& str) {
    assert(!stmt);
    // no exception handling needed; calls must be wrapped in a try/catch block
    std::vector<jvalue> jargs(1);
    LocalReference<jstring> jstr = env.newString(str.c_str());
    jargs[0].l = jstr;

    stmt = env.callObjectMethod(conn->getConnectionObject(), Globals::methodConnectionPrepareStatement, &jargs[0])
        .makeGlobal();
}

int QoreJdbcStatement::bindQueryArguments(Env& env, ExceptionSink* xsink) {
    if (hasArrayBind()) {
        if (bindInternArray(env, *params, xsink)) {
            return -1;
        }
    } else if (bindIntern(env, *params, xsink)) {
        return -1;
    }
    return 0;
}

bool QoreJdbcStatement::execIntern(Env& env, const QoreString& qstr, ExceptionSink* xsink) {
    do {
        // check for a lost connection
        try {
            if (do_batch_execute) {
                // ignore return value
                env.callObjectMethod(stmt, Globals::methodPreparedStatementExecuteBatch, nullptr);
                return false;
            }
            return env.callBooleanMethod(stmt, Globals::methodPreparedStatementExecute, nullptr);
        } catch (JavaException& e) {
            LocalReference<jthrowable> throwable = e.save();
            assert(throwable);
            if (env.isInstanceOf(throwable, Globals::classSQLException)) {
                // check if connection is still valid; 1 second timeout
                jvalue jarg;
                jarg.i = 1;
                bool connected = env.callBooleanMethod(conn->getConnectionObject(), Globals::methodConnectionIsValid,
                    &jarg);
                //printd(5, "QoreJdbcStatement::execIntern() connected: %d\n", connected);
                if (!connected && !reconnectLostConnection(env, xsink)) {
                    assert(!*xsink);
                    // repeat statement execution after reconnection when not in a transaction
                    prepareAndBindStatement(env, xsink, qstr);
                    continue;
                }
            }
            e.restore(throwable.release());
            throw;
        }
    } while (false);
    return false;
}

void QoreJdbcStatement::close(Env& env_obj) {
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = *env_obj;
    bool active_java_exception = env->ExceptionCheck();

    if (rs) {
        env->CallVoidMethodA(rs, Globals::methodResultSetClose, nullptr);
        rs = nullptr;
    }
    if (stmt) {
        env->CallVoidMethodA(stmt, Globals::methodPreparedStatementClose, nullptr);
        stmt = nullptr;
    }
    if (!active_java_exception && env->ExceptionCheck()) {
        throw new JavaException;
    }
}

void QoreJdbcStatement::reset(Env& env) {
    close(env);

    bind_size = 0;
    array_bind_size = 0;
    do_batch_execute = false;
    params = nullptr;
    cvec.clear();
}

int QoreJdbcStatement::reconnectLostConnection(Env& env, ExceptionSink* xsink) {
    if (conn->getDatasource()->activeTransaction()) {
        printd(0, "QoreJdbcStatement::reconnectLostConnection() connection lost while in a transaction\n");
        xsink->raiseException("ODBC-TRANSACTION-ERROR", "connection to database server lost while in a "
            "transaction; transaction has been lost");
    }

    // Reset current statement state while the driver-specific context data is still present
    close(env);

    // Free and reset statement states for all active statements while the driver-specific context data is still
    // present
    conn->getDatasource()->connectionLost(xsink);

    // Disconnect first
    conn->close(env);

    // try to reconnect
    if (conn->reconnect(env, xsink)) {
        // Free state completely.
        reset(env);

        // Reconnect failed; marking connection as closed
        // The following call will close any open statements and then the datasource
        conn->getDatasource()->connectionAborted();
        return -1;
    }

    // Don't execute again if any exceptions have occured
    if (*xsink) {
        // Close all statements and remove private data but leave datasource open
        conn->getDatasource()->connectionRecovered(xsink);
        return -1;
    }

    return 0;
}

int QoreJdbcStatement::describeResultSet(Env& env, ExceptionSink* xsink) {
    assert(rs);
    LocalReference<jobject> info = env.callObjectMethod(rs, Globals::methodResultSetGetMetaData,
        nullptr);
    assert(info);
    jint count = env.callIntMethod(info, Globals::methodResultSetMetaDataGetColumnCount, nullptr);
    //printd(5, "QoreJdbcStatement::describeResultSet() column count: %d\n", count);
    if (!count) {
        return -1;
    }

    assert(cvec.empty());
    cvec.reserve(count);

    // detect duplicate column names in the output
    typedef std::set<std::string> strset_t;
    strset_t strset;

    ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);
    for (jint i = 0; i < count; ++i) {
        jvalue jarg;
        jarg.i = i + 1;
        // NOTE: if we use ResultSetMetaData.getColumnName() it will ignore column aliases
        LocalReference<jstring> cname = env.callObjectMethod(info, Globals::methodResultSetMetaDataGetColumnLabel,
            &jarg).as<jstring>();
        Env::GetStringUtfChars name(env, cname);

        // convert to lower case
        QoreString qname(name.c_str());
        qname.tolwr();

        std::string unique_qname;
        strset_t::iterator it = strset.find(qname.c_str());
        if (it == strset.end()) {
            unique_qname = qname.c_str();
        } else {
            // Find a unique column name.
            unsigned num = 1;
            while (true) {
                QoreStringMaker tmp("%s_%d", name.c_str(), num);
                it = strset.find(tmp.c_str());
                if (it == strset.end()) {
                    unique_qname = tmp.c_str();
                    break;
                }
                ++num;
                continue;
            }
        }
        strset.insert(it, unique_qname);

        // get column type
        jint ctype = env.callIntMethod(info, Globals::methodResultSetMetaDataGetColumnType, &jarg);
        LocalReference<jstring> type_name = env.callObjectMethod(info,
            Globals::methodResultSetMetaDataGetColumnTypeName, &jarg).as<jstring>();
        std::string native_type;
        if (type_name) {
            Env::GetStringUtfChars nt(env, type_name);
            native_type = nt.c_str();
        }
        jint precision = env.callIntMethod(info, Globals::methodResultSetMetaDataGetPrecision, &jarg);
        jint scale = env.callIntMethod(info, Globals::methodResultSetMetaDataGetScale, &jarg);
        jint nullable = env.callIntMethod(info, Globals::methodResultSetMetaDataIsNullable, &jarg);
        cvec.emplace_back(QoreJdbcColumn(qname.c_str(), std::move(unique_qname), ctype,
            std::move(native_type), precision, scale, nullable != 0));
    }

    return 0;
}

bool QoreJdbcStatement::next(Env& env) {
    assert(rs);
    return env.callBooleanMethod(rs, Globals::methodResultSetNext, nullptr);
}

int QoreJdbcStatement::acquireResultSet(Env& env, ExceptionSink* xsink) {
    assert(!rs);
    rs = env.callObjectMethod(stmt, Globals::methodPreparedStatementGetResultSet, nullptr);
    if (!rs) {
        xsink->raiseException("JDBC-RESULTSET-ERROR", "no result set available from query");
        return -1;
    }

    return 0;
}

QoreHashNode* QoreJdbcStatement::getOutputHash(Env& env, ExceptionSink* xsink, bool empty_hash_if_nothing,
        int max_rows) {
    if (acquireResultSet(env, xsink) || describeResultSet(env, xsink)) {
        return nullptr;
    }

    return getOutputHashIntern(env, xsink, empty_hash_if_nothing, max_rows);
}

void QoreJdbcStatement::populateOutputHash(QoreHashNode& h, ExceptionSink* xsink) {
    for (auto& i : cvec) {
        h.setKeyValue(i.qname.c_str(), new QoreListNode(autoTypeInfo), xsink);
        assert(!*xsink);
    }
}

typedef std::vector<QoreListNode*> lvec_t;

class QoreListArray {
public:
    DLLLOCAL QoreListArray(ExceptionSink* xsink, cvec_t& cvec) : xsink(xsink), cvec(cvec) {
    }

    DLLLOCAL ~QoreListArray() {
        for (auto& i : l) {
            i->deref(xsink);
        }
        l.clear();
    }

    DLLLOCAL void populate() {
        assert(l.empty());
        for (size_t i = 0, e = cvec.size(); i < e; ++i) {
            l.push_back(new QoreListNode(autoTypeInfo));
        }
    }

    DLLLOCAL lvec_t& get() {
        return l;
    }

    DLLLOCAL QoreHashNode* getHash() {
        ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);
        if (!l.empty()) {
            for (size_t i = 0, e = cvec.size(); i < e; ++i) {
                rv->setKeyValue(cvec[i].qname.c_str(), l[i], xsink);
            }
            l.clear();
        }
        return rv.release();
    }

protected:
    lvec_t l;
    ExceptionSink* xsink;
    cvec_t& cvec;
};

QoreHashNode* QoreJdbcStatement::getOutputHashIntern(Env& env, ExceptionSink* xsink, bool empty_hash_if_nothing,
        int max_rows) {
    // we have to populate the lists and then assign the hash in case we have Java objects returned for DB values
    QoreListArray l(xsink, cvec);

    size_t row_count = 0;
    while (true) {
        // get next row
        if (!next(env)) {
            break;
        }

        if (!row_count) {
            l.populate();
        }

        //! get column data
        for (jint c = 0, e = (jint)cvec.size(); c < e; ++c) {
            QoreJdbcColumn& col = cvec[c];
            ValueHolder val(getColumnValue(env, c + 1, col, xsink), xsink);
            if (*xsink) {
                return nullptr;
            }

            l.get()[c]->push(val.release(), xsink);
            assert(!*xsink);
        }
        ++row_count;
        if (max_rows > 0 && row_count == static_cast<size_t>(max_rows)) {
            break;
        }
        if (row_count && !(row_count % 100) && qore_check_cancel(xsink, "JDBC column result fetch")) {
            return nullptr;
        }
    }

    if (!row_count && !empty_hash_if_nothing) {
        l.populate();
    }
    return l.getHash();
}

QoreListNode* QoreJdbcStatement::getOutputList(Env& env, ExceptionSink* xsink, int max_rows) {
    if (acquireResultSet(env, xsink) || describeResultSet(env, xsink)) {
        return nullptr;
    }

    return getOutputListIntern(env, xsink, max_rows);
}

QoreListNode* QoreJdbcStatement::getOutputListIntern(Env& env, ExceptionSink* xsink, int max_rows) {
    ReferenceHolder<QoreListNode> l(new QoreListNode(autoTypeInfo), xsink);

    int rowCount = 0;
    while (true) {
        // make sure there is at least one row
        if (!next(env)) {
            // if not, return NOTHING
            break;
        }

        ReferenceHolder<QoreHashNode> h(getSingleRowIntern(env, xsink), xsink);
        if (!h) {
            break;
        }
        l->push(h.release(), xsink);
        ++rowCount;
        if (max_rows > 0 && rowCount == max_rows) {
            break;
        }
        if (rowCount && !(rowCount % 100) && qore_check_cancel(xsink, "JDBC row result fetch")) {
            return nullptr;
        }
    }

    return l.release();
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2

static size_t jdbc_bitmap_bytes(size_t elements) {
    return ((elements + 63) / 64) * 8;
}

static bool jdbc_bitmap_is_set(const uint8_t* bitmap, size_t row) {
    return bitmap[row / 8] & (uint8_t(1) << (row % 8));
}

static void jdbc_bitmap_clear(std::vector<uint8_t>& bitmap, size_t row) {
    bitmap[row / 8] &= ~(uint8_t(1) << (row % 8));
}

static int jdbc_append_validity(std::vector<uint8_t>& dest, size_t dest_rows, const uint8_t* src, size_t src_rows,
        int64_t src_null_count, ExceptionSink* xsink) {
    if (!src_rows) {
        return 0;
    }
    if (!src_null_count && dest.empty()) {
        return 0;
    }

    size_t new_rows = dest_rows + src_rows;
    size_t new_bytes = jdbc_bitmap_bytes(new_rows);
    if (dest.empty()) {
        dest.assign(new_bytes, 0xff);
    } else if (dest.size() < new_bytes) {
        dest.resize(new_bytes, 0xff);
    }

    if (!src_null_count) {
        return 0;
    }
    if (!src) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR", "JDBC Java batch returned null counts without a validity bitmap");
        return -1;
    }

    if (!(dest_rows % 8)) {
        memcpy(dest.data() + (dest_rows / 8), src, jdbc_bitmap_bytes(src_rows));
        return 0;
    }

    for (size_t i = 0; i < src_rows; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC columnar validity bitmap merge")) {
            return -1;
        }
        if (!jdbc_bitmap_is_set(src, i)) {
            jdbc_bitmap_clear(dest, dest_rows + i);
        }
    }
    return 0;
}

static int jdbc_append_bitmap(std::vector<uint8_t>& dest, size_t dest_rows, const uint8_t* src, size_t src_rows,
        ExceptionSink* xsink) {
    if (!src_rows) {
        return 0;
    }

    size_t new_rows = dest_rows + src_rows;
    size_t old_bytes = dest.size();
    size_t new_bytes = jdbc_bitmap_bytes(new_rows);
    if (old_bytes < new_bytes) {
        dest.resize(new_bytes, 0);
    }

    if (!(dest_rows % 8)) {
        memcpy(dest.data() + (dest_rows / 8), src, jdbc_bitmap_bytes(src_rows));
        return 0;
    }

    for (size_t i = 0; i < src_rows; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC columnar boolean bitmap merge")) {
            return -1;
        }
        if (jdbc_bitmap_is_set(src, i)) {
            dest[(dest_rows + i) / 8] |= uint8_t(1) << ((dest_rows + i) % 8);
        }
    }
    return 0;
}

static QoreColumnarTypeDescriptor jdbc_make_schema(const QoreJdbcColumn& col, QoreColumnarTypeKind kind,
        QoreColumnarColumnType column_type, QoreBufferElementType buffer_type, bool nullable) {
    QoreColumnarTypeDescriptor schema;
    schema.name = col.qname;
    schema.kind = kind;
    schema.column_type = column_type;
    schema.buffer_type = buffer_type;
    schema.nullable = nullable;
    schema.native_type = col.native_type;
    if (kind == QoreColumnarTypeKind::Decimal128 || column_type == QoreColumnarColumnType::Number) {
        schema.precision = col.precision;
        schema.scale = col.scale;
    } else if (kind == QoreColumnarTypeKind::Timestamp) {
        schema.time_unit = "ns";
    }
    return schema;
}

enum class JdbcJavaBatchMode : jint {
    Unsupported = 0,
    Byte = 1,
    Short = 2,
    Int = 3,
    Long = 4,
    Float = 5,
    Double = 6,
    Boolean = 7,
    String = 8,
    DecimalString = 9,
};

constexpr int JDBC_COLUMNAR_JAVA_BATCH_ROWS = 8192;

class JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcColumnarBuilder(QoreColumnarTypeDescriptor schema) : schema(std::move(schema)) {
    }

    DLLLOCAL virtual ~JdbcColumnarBuilder() {
    }

    DLLLOCAL virtual bool needsGenericValue() const {
        return false;
    }

    DLLLOCAL virtual int appendJdbcValue(Env& env, jobject rs, int column, const QoreJdbcColumn& col,
            ExceptionSink* xsink) {
        (void)env;
        (void)rs;
        (void)column;
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "internal columnar builder for column '%s' does not support direct JDBC value retrieval",
            col.qname.c_str());
        return -1;
    }

    DLLLOCAL virtual int appendQoreValue(QoreValue value, ExceptionSink* xsink) {
        (void)value;
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "internal columnar builder does not support generic Qore value retrieval");
        return -1;
    }

    DLLLOCAL virtual JdbcJavaBatchMode getJavaBatchMode(const QoreJdbcColumn& col) const {
        (void)col;
        return JdbcJavaBatchMode::Unsupported;
    }

    DLLLOCAL virtual int appendJavaBatch(Env& env, jobject data, jbyteArray validity, int row_count,
            int64_t batch_null_count, const QoreJdbcColumn& col, ExceptionSink* xsink) {
        (void)env;
        (void)data;
        (void)validity;
        (void)row_count;
        (void)batch_null_count;
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "internal columnar builder for column '%s' does not support Java batch retrieval",
            col.qname.c_str());
        return -1;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) = 0;

    DLLLOCAL const QoreColumnarTypeDescriptor& getSchema() const {
        return schema;
    }

protected:
    QoreColumnarTypeDescriptor schema;
};

template <typename T>
struct JdbcExternalColumnOwner {
    std::vector<T> data;
    std::vector<uint8_t> validity;
};

#ifdef QORE_JNI_HAVE_DECIMAL128_BUFFER

constexpr int32_t JDBC_DECIMAL128_MAX_PRECISION = 38;

struct JdbcDecimalParseResult {
    __int128 unscaled = 0;
    int32_t scale = 0;
    int32_t precision = 1;
};

static unsigned __int128 jdbc_decimal_abs_unsigned(__int128 value) {
    return value < 0
        ? static_cast<unsigned __int128>(-(value + 1)) + 1
        : static_cast<unsigned __int128>(value);
}

static int32_t jdbc_decimal_precision(__int128 value) {
    unsigned __int128 abs_value = jdbc_decimal_abs_unsigned(value);
    int32_t rv = 1;
    while (abs_value >= 10) {
        abs_value /= 10;
        ++rv;
    }
    return rv;
}

static __int128 jdbc_decimal_pow10(int32_t exponent) {
    __int128 rv = 1;
    for (int32_t i = 0; i < exponent; ++i) {
        rv *= 10;
    }
    return rv;
}

static QoreBufferDecimal128 jdbc_decimal_storage_from_int128(__int128 value) {
    unsigned __int128 bits = static_cast<unsigned __int128>(value);
    return QoreBufferDecimal128{static_cast<uint64_t>(bits), static_cast<int64_t>(bits >> 64)};
}

static int jdbc_decimal_parse_string(const std::string& input, JdbcDecimalParseResult& out,
        const QoreJdbcColumn& col, ExceptionSink* xsink) {
    size_t begin = 0;
    while (begin < input.size() && std::isspace(static_cast<unsigned char>(input[begin]))) {
        if (begin && !(begin % 100) && qore_check_cancel(xsink, "JDBC decimal parsing")) {
            return -1;
        }
        ++begin;
    }
    size_t end = input.size();
    size_t trailing = 0;
    while (end > begin && std::isspace(static_cast<unsigned char>(input[end - 1]))) {
        if (trailing && !(trailing % 100) && qore_check_cancel(xsink, "JDBC decimal parsing")) {
            return -1;
        }
        --end;
        ++trailing;
    }
    if (begin == end) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' returned an empty value", col.qname.c_str());
        return -1;
    }

    bool negative = false;
    size_t pos = begin;
    if (input[pos] == '+' || input[pos] == '-') {
        negative = input[pos] == '-';
        ++pos;
    }

    bool seen_digit = false;
    bool seen_dot = false;
    int64_t fractional_digits = 0;
    std::string digits;
    size_t scanned = 0;
    for (; pos < end; ++pos) {
        if (scanned && !(scanned % 100) && qore_check_cancel(xsink, "JDBC decimal parsing")) {
            return -1;
        }
        ++scanned;
        unsigned char c = static_cast<unsigned char>(input[pos]);
        if (std::isdigit(c)) {
            seen_digit = true;
            digits.push_back(static_cast<char>(c));
            if (seen_dot) {
                ++fractional_digits;
            }
            continue;
        }
        if (input[pos] == '.' && !seen_dot) {
            seen_dot = true;
            continue;
        }
        break;
    }

    if (!seen_digit) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' returned value '%s' without decimal digits",
            col.qname.c_str(), input.c_str());
        return -1;
    }

    int64_t exponent = 0;
    if (pos < end && (input[pos] == 'e' || input[pos] == 'E')) {
        ++pos;
        bool exponent_negative = false;
        if (pos < end && (input[pos] == '+' || input[pos] == '-')) {
            exponent_negative = input[pos] == '-';
            ++pos;
        }
        if (pos == end || !std::isdigit(static_cast<unsigned char>(input[pos]))) {
            xsink->raiseException("JDBC-COLUMNAR-ERROR",
                "JDBC DECIMAL column '%s' returned value '%s' with an invalid exponent",
                col.qname.c_str(), input.c_str());
            return -1;
        }
        size_t exponent_digits = 0;
        while (pos < end && std::isdigit(static_cast<unsigned char>(input[pos]))) {
            if (exponent_digits && !(exponent_digits % 100) && qore_check_cancel(xsink, "JDBC decimal parsing")) {
                return -1;
            }
            exponent = (exponent * 10) + (input[pos] - '0');
            if (exponent > JDBC_DECIMAL128_MAX_PRECISION * 2) {
                xsink->raiseException("JDBC-COLUMNAR-ERROR",
                    "JDBC DECIMAL column '%s' returned value '%s' with an exponent too large for decimal128",
                    col.qname.c_str(), input.c_str());
                return -1;
            }
            ++pos;
            ++exponent_digits;
        }
        if (exponent_negative) {
            exponent = -exponent;
        }
    }

    if (pos != end) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' returned value '%s' with unexpected character '%c'",
            col.qname.c_str(), input.c_str(), input[pos]);
        return -1;
    }

    int64_t scale = fractional_digits - exponent;
    if (scale < 0) {
        digits.append(static_cast<size_t>(-scale), '0');
        scale = 0;
    }
    if (scale > JDBC_DECIMAL128_MAX_PRECISION) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' returned value '%s' with scale %lld; maximum decimal128 scale is %d",
            col.qname.c_str(), input.c_str(), static_cast<long long>(scale), JDBC_DECIMAL128_MAX_PRECISION);
        return -1;
    }

    size_t first_non_zero = digits.find_first_not_of('0');
    size_t significant_digits = first_non_zero == std::string::npos ? 1 : digits.size() - first_non_zero;
    if (significant_digits > JDBC_DECIMAL128_MAX_PRECISION) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' returned value '%s' with %lld significant digits; maximum decimal128 "
            "precision is %d", col.qname.c_str(), input.c_str(), static_cast<long long>(significant_digits),
            JDBC_DECIMAL128_MAX_PRECISION);
        return -1;
    }

    __int128 unscaled = 0;
    for (size_t i = 0, e = digits.size(); i < e; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC decimal parsing")) {
            return -1;
        }
        char c = digits[i];
        unscaled = (unscaled * 10) + (c - '0');
    }
    if (negative) {
        unscaled = -unscaled;
    }

    out.unscaled = unscaled;
    out.scale = static_cast<int32_t>(scale);
    out.precision = static_cast<int32_t>(significant_digits);
    return 0;
}

static int jdbc_decimal_rescale(JdbcDecimalParseResult& value, int32_t target_precision, int32_t target_scale,
        const std::string& source, const QoreJdbcColumn& col, ExceptionSink* xsink) {
    assert(target_precision > 0 && target_precision <= JDBC_DECIMAL128_MAX_PRECISION);
    assert(target_scale >= 0 && target_scale <= target_precision);

    if (value.scale < target_scale) {
        int32_t diff = target_scale - value.scale;
        __int128 multiplier = jdbc_decimal_pow10(diff);
        unsigned __int128 limit = static_cast<unsigned __int128>(jdbc_decimal_pow10(target_precision) - 1);
        if (jdbc_decimal_abs_unsigned(value.unscaled) > limit / static_cast<unsigned __int128>(multiplier)) {
            xsink->raiseException("JDBC-COLUMNAR-ERROR",
                "JDBC DECIMAL column '%s' value '%s' exceeds decimal128 precision %d after scaling",
                col.qname.c_str(), source.c_str(), target_precision);
            return -1;
        }
        value.unscaled *= multiplier;
        value.scale = target_scale;
    } else if (value.scale > target_scale) {
        int32_t diff = value.scale - target_scale;
        __int128 divisor = jdbc_decimal_pow10(diff);
        if (value.unscaled % divisor) {
            xsink->raiseException("JDBC-COLUMNAR-ERROR",
                "JDBC DECIMAL column '%s' value '%s' cannot be represented at JDBC scale %d without losing "
                "precision", col.qname.c_str(), source.c_str(), target_scale);
            return -1;
        }
        value.unscaled /= divisor;
        value.scale = target_scale;
    }

    value.precision = jdbc_decimal_precision(value.unscaled);
    if (value.precision > target_precision) {
        xsink->raiseException("JDBC-COLUMNAR-ERROR",
            "JDBC DECIMAL column '%s' value '%s' exceeds decimal128 precision %d",
            col.qname.c_str(), source.c_str(), target_precision);
        return -1;
    }
    return 0;
}

static bool jdbc_decimal128_metadata_supported(const QoreJdbcColumn& col, int32_t& precision, int32_t& scale) {
    precision = col.precision > 0 ? col.precision : JDBC_DECIMAL128_MAX_PRECISION;
    scale = col.scale >= 0 ? col.scale : 0;
    return precision > 0 && precision <= JDBC_DECIMAL128_MAX_PRECISION && scale >= 0 && scale <= precision;
}

#endif

enum class JdbcFixedGetter {
    Byte,
    Short,
    Int,
    Long,
    Float,
    Double,
};

template <typename T, JdbcFixedGetter getter>
class JdbcFixedColumnarBuilder : public JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcFixedColumnarBuilder(QoreColumnarTypeDescriptor schema, QoreBufferElementType element_type)
            : JdbcColumnarBuilder(std::move(schema)), element_type(element_type),
            owner(new JdbcExternalColumnOwner<T>) {
    }

    DLLLOCAL virtual int appendJdbcValue(Env& env, jobject rs, int column, const QoreJdbcColumn& col,
            ExceptionSink* xsink) {
        jvalue jarg;
        jarg.i = column;
        T value = 0;
        switch (getter) {
            case JdbcFixedGetter::Byte:
                value = static_cast<T>(env.callByteMethod(rs, Globals::methodResultSetGetByte, &jarg));
                break;
            case JdbcFixedGetter::Short:
                value = static_cast<T>(env.callShortMethod(rs, Globals::methodResultSetGetShort, &jarg));
                break;
            case JdbcFixedGetter::Int:
                value = static_cast<T>(env.callIntMethod(rs, Globals::methodResultSetGetInt, &jarg));
                break;
            case JdbcFixedGetter::Long:
                value = static_cast<T>(env.callLongMethod(rs, Globals::methodResultSetGetLong, &jarg));
                break;
            case JdbcFixedGetter::Float:
                value = static_cast<T>(env.callFloatMethod(rs, Globals::methodResultSetGetFloat, &jarg));
                break;
            case JdbcFixedGetter::Double:
                value = static_cast<T>(env.callDoubleMethod(rs, Globals::methodResultSetGetDouble, &jarg));
                break;
        }
        bool is_null = env.callBooleanMethod(rs, Globals::methodResultSetWasNull, nullptr);
        owner->data.push_back(is_null ? T() : value);
        appendValidity(is_null);
        return 0;
    }

    DLLLOCAL virtual JdbcJavaBatchMode getJavaBatchMode(const QoreJdbcColumn& col) const {
        (void)col;
        switch (getter) {
            case JdbcFixedGetter::Byte:
                return JdbcJavaBatchMode::Byte;
            case JdbcFixedGetter::Short:
                return JdbcJavaBatchMode::Short;
            case JdbcFixedGetter::Int:
                return JdbcJavaBatchMode::Int;
            case JdbcFixedGetter::Long:
                return JdbcJavaBatchMode::Long;
            case JdbcFixedGetter::Float:
                return JdbcJavaBatchMode::Float;
            case JdbcFixedGetter::Double:
                return JdbcJavaBatchMode::Double;
        }
        return JdbcJavaBatchMode::Unsupported;
    }

    DLLLOCAL virtual int appendJavaBatch(Env& env, jobject data, jbyteArray validity, int row_count,
            int64_t batch_null_count, const QoreJdbcColumn& col, ExceptionSink* xsink) {
        (void)col;
        if (!row_count) {
            return 0;
        }

        size_t old_rows = rows;
        owner->data.resize(old_rows + static_cast<size_t>(row_count));
        JNIEnv* jenv = *env;
        switch (getter) {
            case JdbcFixedGetter::Byte:
                jenv->GetByteArrayRegion(static_cast<jbyteArray>(data), 0, row_count,
                    reinterpret_cast<jbyte*>(owner->data.data() + old_rows));
                break;
            case JdbcFixedGetter::Short:
                jenv->GetShortArrayRegion(static_cast<jshortArray>(data), 0, row_count,
                    reinterpret_cast<jshort*>(owner->data.data() + old_rows));
                break;
            case JdbcFixedGetter::Int:
                jenv->GetIntArrayRegion(static_cast<jintArray>(data), 0, row_count,
                    reinterpret_cast<jint*>(owner->data.data() + old_rows));
                break;
            case JdbcFixedGetter::Long:
                jenv->GetLongArrayRegion(static_cast<jlongArray>(data), 0, row_count,
                    reinterpret_cast<jlong*>(owner->data.data() + old_rows));
                break;
            case JdbcFixedGetter::Float:
                jenv->GetFloatArrayRegion(static_cast<jfloatArray>(data), 0, row_count,
                    reinterpret_cast<jfloat*>(owner->data.data() + old_rows));
                break;
            case JdbcFixedGetter::Double:
                jenv->GetDoubleArrayRegion(static_cast<jdoubleArray>(data), 0, row_count,
                    reinterpret_cast<jdouble*>(owner->data.data() + old_rows));
                break;
        }
        if (jenv->ExceptionCheck()) {
            throw JavaException();
        }

        std::vector<uint8_t> validity_data;
        const uint8_t* validity_ptr = nullptr;
        if (batch_null_count) {
            validity_data.resize(jdbc_bitmap_bytes(static_cast<size_t>(row_count)));
            jenv->GetByteArrayRegion(validity, 0, static_cast<jsize>(validity_data.size()),
                reinterpret_cast<jbyte*>(validity_data.data()));
            if (jenv->ExceptionCheck()) {
                throw JavaException();
            }
            validity_ptr = validity_data.data();
        }
        if (jdbc_append_validity(owner->validity, old_rows, validity_ptr, static_cast<size_t>(row_count),
                batch_null_count, xsink)) {
            return -1;
        }

        rows += static_cast<size_t>(row_count);
        null_count += batch_null_count;
        return 0;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) {
        bool nullable = schema.nullable || null_count > 0;
        schema.nullable = nullable;
        return QoreBufferNode::wrapExternalStorage(element_type, nullable, rows,
            owner->data.empty() ? nullptr : owner->data.data(),
            nullable && !owner->validity.empty() ? owner->validity.data() : nullptr, owner, null_count, xsink);
    }

private:
    QoreBufferElementType element_type;
    std::shared_ptr<JdbcExternalColumnOwner<T>> owner;
    size_t rows = 0;
    int64_t null_count = 0;

    DLLLOCAL void appendValidity(bool is_null) {
        size_t row = rows++;
        if (is_null || !owner->validity.empty()) {
            size_t bytes = jdbc_bitmap_bytes(rows);
            if (owner->validity.size() < bytes) {
                owner->validity.resize(bytes, 0xff);
            }
            if (is_null) {
                owner->validity[row / 8] &= ~(uint8_t(1) << (row % 8));
                ++null_count;
            }
        }
    }
};

class JdbcBoolColumnarBuilder : public JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcBoolColumnarBuilder(QoreColumnarTypeDescriptor schema) : JdbcColumnarBuilder(std::move(schema)),
            owner(new JdbcExternalColumnOwner<uint8_t>) {
    }

    DLLLOCAL virtual int appendJdbcValue(Env& env, jobject rs, int column, const QoreJdbcColumn& col,
            ExceptionSink* xsink) {
        jvalue jarg;
        jarg.i = column;
        bool value = env.callBooleanMethod(rs, Globals::methodResultSetGetBoolean, &jarg);
        bool is_null = env.callBooleanMethod(rs, Globals::methodResultSetWasNull, nullptr);
        size_t row = rows++;
        size_t bytes = jdbc_bitmap_bytes(rows);
        if (owner->data.size() < bytes) {
            owner->data.resize(bytes, 0);
        }
        if (!is_null && value) {
            owner->data[row / 8] |= uint8_t(1) << (row % 8);
        }
        appendValidity(row, is_null);
        return 0;
    }

    DLLLOCAL virtual JdbcJavaBatchMode getJavaBatchMode(const QoreJdbcColumn& col) const {
        (void)col;
        return JdbcJavaBatchMode::Boolean;
    }

    DLLLOCAL virtual int appendJavaBatch(Env& env, jobject data, jbyteArray validity, int row_count,
            int64_t batch_null_count, const QoreJdbcColumn& col, ExceptionSink* xsink) {
        (void)col;
        if (!row_count) {
            return 0;
        }

        JNIEnv* jenv = *env;
        std::vector<uint8_t> data_bits(jdbc_bitmap_bytes(static_cast<size_t>(row_count)));
        jenv->GetByteArrayRegion(static_cast<jbyteArray>(data), 0, static_cast<jsize>(data_bits.size()),
            reinterpret_cast<jbyte*>(data_bits.data()));
        if (jenv->ExceptionCheck()) {
            throw JavaException();
        }

        size_t old_rows = rows;
        if (jdbc_append_bitmap(owner->data, old_rows, data_bits.data(), static_cast<size_t>(row_count), xsink)) {
            return -1;
        }

        std::vector<uint8_t> validity_data;
        const uint8_t* validity_ptr = nullptr;
        if (batch_null_count) {
            validity_data.resize(jdbc_bitmap_bytes(static_cast<size_t>(row_count)));
            jenv->GetByteArrayRegion(validity, 0, static_cast<jsize>(validity_data.size()),
                reinterpret_cast<jbyte*>(validity_data.data()));
            if (jenv->ExceptionCheck()) {
                throw JavaException();
            }
            validity_ptr = validity_data.data();
        }
        if (jdbc_append_validity(owner->validity, old_rows, validity_ptr, static_cast<size_t>(row_count),
                batch_null_count, xsink)) {
            return -1;
        }

        rows += static_cast<size_t>(row_count);
        null_count += batch_null_count;
        return 0;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) {
        bool nullable = schema.nullable || null_count > 0;
        schema.nullable = nullable;
        return QoreBufferNode::wrapExternalStorage(QoreBufferElementType::Bool, nullable, rows,
            owner->data.empty() ? nullptr : owner->data.data(),
            nullable && !owner->validity.empty() ? owner->validity.data() : nullptr, owner, null_count, xsink);
    }

private:
    std::shared_ptr<JdbcExternalColumnOwner<uint8_t>> owner;
    size_t rows = 0;
    int64_t null_count = 0;

    DLLLOCAL void appendValidity(size_t row, bool is_null) {
        if (is_null || !owner->validity.empty()) {
            size_t bytes = jdbc_bitmap_bytes(rows);
            if (owner->validity.size() < bytes) {
                owner->validity.resize(bytes, 0xff);
            }
            if (is_null) {
                owner->validity[row / 8] &= ~(uint8_t(1) << (row % 8));
                ++null_count;
            }
        }
    }
};

#ifdef QORE_JNI_HAVE_DECIMAL128_BUFFER

class JdbcDecimal128ColumnarBuilder : public JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcDecimal128ColumnarBuilder(QoreColumnarTypeDescriptor schema, int32_t precision, int32_t scale)
            : JdbcColumnarBuilder(std::move(schema)), owner(new JdbcExternalColumnOwner<QoreBufferDecimal128>),
            precision(precision), scale(scale) {
        this->schema.precision = precision;
        this->schema.scale = scale;
        this->schema.buffer_type = QoreBufferElementType::Decimal128;
    }

    DLLLOCAL virtual int appendJdbcValue(Env& env, jobject rs, int column, const QoreJdbcColumn& col,
            ExceptionSink* xsink) {
        jvalue jarg;
        jarg.i = column;
        LocalReference<jobject> num = env.callObjectMethod(rs, Globals::methodResultSetGetBigDecimal, &jarg);
        if (!num) {
            owner->data.push_back(QoreBufferDecimal128{0, 0});
            appendValidity(true);
            return 0;
        }

        LocalReference<jstring> jstr = env.callObjectMethod(num, Globals::methodBigDecimalToString, nullptr)
            .as<jstring>();
        Env::GetStringUtfChars str(env, jstr);
        std::string value(str.c_str());

        JdbcDecimalParseResult parsed;
        if (jdbc_decimal_parse_string(value, parsed, col, xsink)
                || jdbc_decimal_rescale(parsed, precision, scale, value, col, xsink)) {
            return -1;
        }
        owner->data.push_back(jdbc_decimal_storage_from_int128(parsed.unscaled));
        appendValidity(false);
        return 0;
    }

    DLLLOCAL virtual JdbcJavaBatchMode getJavaBatchMode(const QoreJdbcColumn& col) const {
        (void)col;
        return JdbcJavaBatchMode::DecimalString;
    }

    DLLLOCAL virtual int appendJavaBatch(Env& env, jobject data, jbyteArray validity, int row_count,
            int64_t batch_null_count, const QoreJdbcColumn& col, ExceptionSink* xsink) {
        if (!row_count) {
            return 0;
        }

        size_t old_rows = rows;
        jobjectArray strings = static_cast<jobjectArray>(data);
        for (int i = 0; i < row_count; ++i) {
            if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC decimal batch conversion")) {
                return -1;
            }
            LocalReference<jstring> jstr = env.getObjectArrayElement(strings, i).as<jstring>();
            if (!jstr) {
                owner->data.push_back(QoreBufferDecimal128{0, 0});
                continue;
            }

            Env::GetStringUtfChars str(env, jstr);
            std::string value(str.c_str());

            JdbcDecimalParseResult parsed;
            if (jdbc_decimal_parse_string(value, parsed, col, xsink)
                    || jdbc_decimal_rescale(parsed, precision, scale, value, col, xsink)) {
                return -1;
            }
            owner->data.push_back(jdbc_decimal_storage_from_int128(parsed.unscaled));
        }

        std::vector<uint8_t> validity_data;
        const uint8_t* validity_ptr = nullptr;
        if (batch_null_count) {
            validity_data.resize(jdbc_bitmap_bytes(static_cast<size_t>(row_count)));
            JNIEnv* jenv = *env;
            jenv->GetByteArrayRegion(validity, 0, static_cast<jsize>(validity_data.size()),
                reinterpret_cast<jbyte*>(validity_data.data()));
            if (jenv->ExceptionCheck()) {
                throw JavaException();
            }
            validity_ptr = validity_data.data();
        }
        if (jdbc_append_validity(owner->validity, old_rows, validity_ptr, static_cast<size_t>(row_count),
                batch_null_count, xsink)) {
            return -1;
        }

        rows += static_cast<size_t>(row_count);
        null_count += batch_null_count;
        return 0;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) {
        bool nullable = schema.nullable || null_count > 0;
        schema.nullable = nullable;
        return QoreBufferNode::wrapExternalStorage(QoreBufferElementType::Decimal128, nullable, rows,
            owner->data.empty() ? nullptr : owner->data.data(),
            nullable && !owner->validity.empty() ? owner->validity.data() : nullptr, owner, null_count,
            precision, scale, xsink);
    }

private:
    std::shared_ptr<JdbcExternalColumnOwner<QoreBufferDecimal128>> owner;
    int32_t precision;
    int32_t scale;
    size_t rows = 0;
    int64_t null_count = 0;

    DLLLOCAL void appendValidity(bool is_null) {
        size_t row = rows++;
        if (is_null || !owner->validity.empty()) {
            size_t bytes = jdbc_bitmap_bytes(rows);
            if (owner->validity.size() < bytes) {
                owner->validity.resize(bytes, 0xff);
            }
            if (is_null) {
                owner->validity[row / 8] &= ~(uint8_t(1) << (row % 8));
                ++null_count;
            }
        }
    }
};

#endif

class JdbcStringColumnarBuilder : public JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcStringColumnarBuilder(QoreColumnarTypeDescriptor schema) : JdbcColumnarBuilder(std::move(schema)) {
    }

    DLLLOCAL virtual int appendJdbcValue(Env& env, jobject rs, int column, const QoreJdbcColumn& col,
            ExceptionSink* xsink) {
        jvalue jarg;
        jarg.i = column;
        LocalReference<jstring> jstr = env.callObjectMethod(rs, Globals::methodResultSetGetString, &jarg)
            .as<jstring>();
        if (!jstr) {
            values.emplace_back();
            nulls.push_back(1);
            has_nulls = true;
            return 0;
        }

        Env::GetStringUtfChars str(env, jstr);
        values.emplace_back(str.c_str());
        if (col.strip) {
            size_t trimmed = 0;
            while (!values.back().empty() && values.back().back() == ' ') {
                if (trimmed && !(trimmed % 100) && qore_check_cancel(xsink, "JDBC CHAR column trimming")) {
                    return -1;
                }
                values.back().pop_back();
                ++trimmed;
            }
        }
        nulls.push_back(0);
        return 0;
    }

    DLLLOCAL virtual JdbcJavaBatchMode getJavaBatchMode(const QoreJdbcColumn& col) const {
        (void)col;
        return JdbcJavaBatchMode::String;
    }

    DLLLOCAL virtual int appendJavaBatch(Env& env, jobject data, jbyteArray validity, int row_count,
            int64_t batch_null_count, const QoreJdbcColumn& col, ExceptionSink* xsink) {
        (void)validity;
        (void)col;
        if (batch_null_count) {
            has_nulls = true;
        }
        jobjectArray strings = static_cast<jobjectArray>(data);
        for (int i = 0; i < row_count; ++i) {
            if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC string batch conversion")) {
                return -1;
            }
            LocalReference<jstring> jstr = env.getObjectArrayElement(strings, i).as<jstring>();
            if (!jstr) {
                values.emplace_back();
                nulls.push_back(1);
                has_nulls = true;
                continue;
            }
            Env::GetStringUtfChars str(env, jstr);
            values.emplace_back(str.c_str());
            nulls.push_back(0);
        }
        return 0;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) {
        schema.nullable = schema.nullable || has_nulls;
        ReferenceHolder<QoreListNode> list(new QoreListNode(autoTypeInfo), xsink);
        for (size_t i = 0, e = values.size(); i < e; ++i) {
            if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC string column buffer creation")) {
                return QoreValue();
            }
            if (nulls[i]) {
                list->push(QoreValue(), xsink);
            } else {
                list->push(new QoreStringNode(values[i], QCS_UTF8), xsink);
            }
            if (*xsink) {
                return QoreValue();
            }
        }
        return new QoreBufferNode(QoreBufferElementType::String, schema.nullable, *list, xsink);
    }

private:
    std::vector<std::string> values;
    std::vector<uint8_t> nulls;
    bool has_nulls = false;
};

class JdbcListColumnarBuilder : public JdbcColumnarBuilder {
public:
    DLLLOCAL JdbcListColumnarBuilder(QoreColumnarTypeDescriptor schema, ExceptionSink* xsink)
            : JdbcColumnarBuilder(std::move(schema)), list(new QoreListNode(autoTypeInfo)) {
        (void)xsink;
    }

    DLLLOCAL virtual ~JdbcListColumnarBuilder() {
        ExceptionSink xsink;
        if (list) {
            list->deref(&xsink);
        }
    }

    DLLLOCAL virtual bool needsGenericValue() const {
        return true;
    }

    DLLLOCAL virtual int appendQoreValue(QoreValue value, ExceptionSink* xsink) {
        assert(list);
        if (value.isNullOrNothing()) {
            has_nulls = true;
        }
        list->push(value, xsink);
        return *xsink ? -1 : 0;
    }

    DLLLOCAL virtual QoreValue makeValue(ExceptionSink* xsink) {
        schema.nullable = schema.nullable || has_nulls;
        QoreListNode* rv = list;
        list = nullptr;
        return rv;
    }

private:
    QoreListNode* list = nullptr;
    bool has_nulls = false;
};

static bool jdbc_is_string_type(jint ctype) {
    return ctype == Globals::typeChar || ctype == Globals::typeVarchar || ctype == Globals::typeLongVarchar
        || ctype == Globals::typeNChar || ctype == Globals::typeNVarchar || ctype == Globals::typeLongNVarchar;
}

static std::unique_ptr<JdbcColumnarBuilder> jdbc_make_columnar_builder(const QoreJdbcColumn& col,
        ExceptionSink* xsink) {
    if (col.ctype == Globals::typeTinyInt) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<int8_t, JdbcFixedGetter::Byte>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Int, QoreColumnarColumnType::Int,
                QoreBufferElementType::Int8, col.nullable), QoreBufferElementType::Int8));
    }
    if (col.ctype == Globals::typeSmallInt) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<int16_t, JdbcFixedGetter::Short>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Int, QoreColumnarColumnType::Int,
                QoreBufferElementType::Int16, col.nullable), QoreBufferElementType::Int16));
    }
    if (col.ctype == Globals::typeInteger) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<int32_t, JdbcFixedGetter::Int>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Int, QoreColumnarColumnType::Int,
                QoreBufferElementType::Int32, col.nullable), QoreBufferElementType::Int32));
    }
    if (col.ctype == Globals::typeBigInt) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<int64_t, JdbcFixedGetter::Long>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Int, QoreColumnarColumnType::Int,
                QoreBufferElementType::Int64, col.nullable), QoreBufferElementType::Int64));
    }
    if ((col.ctype == Globals::typeNumeric || col.ctype == Globals::typeDecimal)
            && !col.scale && col.precision > 0 && col.precision <= 18) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<int64_t, JdbcFixedGetter::Long>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Int, QoreColumnarColumnType::Int,
                QoreBufferElementType::Int64, col.nullable), QoreBufferElementType::Int64));
    }
    if (col.ctype == Globals::typeReal) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<float, JdbcFixedGetter::Float>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Float, QoreColumnarColumnType::Float,
                QoreBufferElementType::Float32, col.nullable), QoreBufferElementType::Float32));
    }
    if (col.ctype == Globals::typeFloat || col.ctype == Globals::typeDouble) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcFixedColumnarBuilder<double, JdbcFixedGetter::Double>(
            jdbc_make_schema(col, QoreColumnarTypeKind::Float, QoreColumnarColumnType::Float,
                QoreBufferElementType::Float64, col.nullable), QoreBufferElementType::Float64));
    }
    if (col.ctype == Globals::typeBit || col.ctype == Globals::typeBoolean) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcBoolColumnarBuilder(
            jdbc_make_schema(col, QoreColumnarTypeKind::Bool, QoreColumnarColumnType::Bool,
                QoreBufferElementType::Bool, col.nullable)));
    }
#ifdef QORE_JNI_HAVE_DECIMAL128_BUFFER
    if (col.ctype == Globals::typeNumeric || col.ctype == Globals::typeDecimal) {
        int32_t precision;
        int32_t scale;
        if (jdbc_decimal128_metadata_supported(col, precision, scale)) {
            return std::unique_ptr<JdbcColumnarBuilder>(new JdbcDecimal128ColumnarBuilder(
                jdbc_make_schema(col, QoreColumnarTypeKind::Decimal128, QoreColumnarColumnType::Number,
                    QoreBufferElementType::Decimal128, col.nullable), precision, scale));
        }
    }
#endif
    if (jdbc_is_string_type(col.ctype)) {
        return std::unique_ptr<JdbcColumnarBuilder>(new JdbcStringColumnarBuilder(
            jdbc_make_schema(col, QoreColumnarTypeKind::String, QoreColumnarColumnType::String,
                QoreBufferElementType::String, col.nullable)));
    }

    QoreColumnarTypeKind kind = QoreColumnarTypeKind::Auto;
    QoreColumnarColumnType column_type = QoreColumnarColumnType::Auto;
    if (col.ctype == Globals::typeNumeric || col.ctype == Globals::typeDecimal) {
        column_type = QoreColumnarColumnType::Number;
#ifdef QORE_JNI_HAVE_DECIMAL128_BUFFER
        int32_t precision;
        int32_t scale;
        if (jdbc_decimal128_metadata_supported(col, precision, scale)) {
            kind = QoreColumnarTypeKind::Decimal128;
        } else
#endif
        {
            kind = QoreColumnarTypeKind::Number;
        }
    } else if (col.ctype == Globals::typeDate) {
        kind = QoreColumnarTypeKind::Date;
        column_type = QoreColumnarColumnType::Date;
    } else if (col.ctype == Globals::typeTimestamp || col.ctype == Globals::typeTimestampWithTimezone) {
        kind = QoreColumnarTypeKind::Timestamp;
        column_type = QoreColumnarColumnType::Date;
    } else if (col.ctype == Globals::typeBinary || col.ctype == Globals::typeVarbinary
            || col.ctype == Globals::typeLongVarbinary) {
        kind = QoreColumnarTypeKind::Binary;
        column_type = QoreColumnarColumnType::Binary;
    }

    return std::unique_ptr<JdbcColumnarBuilder>(new JdbcListColumnarBuilder(
        jdbc_make_schema(col, kind, column_type, QoreBufferElementType::Invalid, col.nullable), xsink));
}

QoreColumnarResult* QoreJdbcStatement::getOutputColumnar(Env& env, ExceptionSink* xsink, int max_rows) {
    if (acquireResultSet(env, xsink) || describeResultSet(env, xsink)) {
        return nullptr;
    }

    return getOutputColumnarIntern(env, xsink, max_rows);
}

static int jdbc_set_int_array(Env& env, jintArray array, const std::vector<jint>& values) {
    if (values.empty()) {
        return 0;
    }
    JNIEnv* jenv = *env;
    jenv->SetIntArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    if (jenv->ExceptionCheck()) {
        throw JavaException();
    }
    return 0;
}

static int jdbc_set_boolean_array(Env& env, jbooleanArray array, const std::vector<jboolean>& values) {
    if (values.empty()) {
        return 0;
    }
    JNIEnv* jenv = *env;
    jenv->SetBooleanArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    if (jenv->ExceptionCheck()) {
        throw JavaException();
    }
    return 0;
}

static int jdbc_get_long_array(Env& env, jlongArray array, std::vector<jlong>& values) {
    if (values.empty()) {
        return 0;
    }
    JNIEnv* jenv = *env;
    jenv->GetLongArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    if (jenv->ExceptionCheck()) {
        throw JavaException();
    }
    return 0;
}

static int jdbc_fetch_columnar_java_batches(Env& env, jobject rs,
        std::vector<std::unique_ptr<JdbcColumnarBuilder>>& builders, const cvec_t& cvec,
        const std::vector<jint>& modes, int max_rows, ExceptionSink* xsink) {
    size_t column_count = builders.size();
    assert(column_count == cvec.size());
    assert(column_count == modes.size());

    LocalReference<jintArray> columns = env.newIntArray(static_cast<jsize>(column_count)).as<jintArray>();
    LocalReference<jintArray> jmodes = env.newIntArray(static_cast<jsize>(column_count)).as<jintArray>();
    LocalReference<jbooleanArray> trim = env.newBooleanArray(static_cast<jsize>(column_count)).as<jbooleanArray>();

    std::vector<jint> column_numbers(column_count);
    std::vector<jboolean> trim_values(column_count);
    for (size_t i = 0; i < column_count; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC columnar Java batch setup")) {
            return -1;
        }
        column_numbers[i] = static_cast<jint>(i + 1);
        trim_values[i] = cvec[i].strip ? JNI_TRUE : JNI_FALSE;
    }
    jdbc_set_int_array(env, columns, column_numbers);
    jdbc_set_int_array(env, jmodes, modes);
    jdbc_set_boolean_array(env, trim, trim_values);

    size_t fetched = 0;
    while (max_rows <= 0 || fetched < static_cast<size_t>(max_rows)) {
        int rows_to_fetch = JDBC_COLUMNAR_JAVA_BATCH_ROWS;
        if (max_rows > 0) {
            rows_to_fetch = std::min(rows_to_fetch, max_rows - static_cast<int>(fetched));
        }

        std::vector<jvalue> jargs(5);
        jargs[0].l = rs;
        jargs[1].l = columns;
        jargs[2].l = jmodes;
        jargs[3].l = trim;
        jargs[4].i = rows_to_fetch;
        LocalReference<jobject> batch = env.callStaticObjectMethod(Globals::classJdbcColumnarBatch,
            Globals::methodJdbcColumnarBatchRead, &jargs[0]);
        if (!batch) {
            xsink->raiseException("JDBC-COLUMNAR-ERROR", "JDBC Java batch reader returned no batch object");
            return -1;
        }

        int batch_rows = env.getIntField(batch, Globals::fieldJdbcColumnarBatchRows);
        if (!batch_rows) {
            break;
        }

        LocalReference<jobjectArray> data = env.getObjectField(batch, Globals::fieldJdbcColumnarBatchData)
            .as<jobjectArray>();
        LocalReference<jobjectArray> validity = env.getObjectField(batch, Globals::fieldJdbcColumnarBatchValidity)
            .as<jobjectArray>();
        LocalReference<jlongArray> null_counts = env.getObjectField(batch,
            Globals::fieldJdbcColumnarBatchNullCounts).as<jlongArray>();
        if (!data || !validity || !null_counts) {
            xsink->raiseException("JDBC-COLUMNAR-ERROR", "JDBC Java batch reader returned incomplete batch metadata");
            return -1;
        }

        std::vector<jlong> null_count_values(column_count);
        jdbc_get_long_array(env, null_counts, null_count_values);

        for (size_t c = 0; c < column_count; ++c) {
            if (c && !(c % 100) && qore_check_cancel(xsink, "JDBC columnar Java batch import")) {
                return -1;
            }
            LocalReference<jobject> column_data = env.getObjectArrayElement(data, static_cast<jsize>(c));
            LocalReference<jbyteArray> column_validity = env.getObjectArrayElement(validity, static_cast<jsize>(c))
                .as<jbyteArray>();
            if (!column_data || !column_validity) {
                xsink->raiseException("JDBC-COLUMNAR-ERROR",
                    "JDBC Java batch reader returned incomplete data for column '%s'", cvec[c].qname.c_str());
                return -1;
            }
            if (builders[c]->appendJavaBatch(env, column_data, column_validity, batch_rows, null_count_values[c],
                    cvec[c], xsink)) {
                return -1;
            }
        }

        fetched += static_cast<size_t>(batch_rows);
        if (batch_rows < rows_to_fetch) {
            break;
        }
        if (qore_check_cancel(xsink, "JDBC columnar Java batch result fetch")) {
            return -1;
        }
    }
    return 0;
}

QoreColumnarResult* QoreJdbcStatement::getOutputColumnarIntern(Env& env, ExceptionSink* xsink, int max_rows) {
    std::vector<std::unique_ptr<JdbcColumnarBuilder>> builders;
    builders.reserve(cvec.size());
    std::vector<jint> java_batch_modes;
    java_batch_modes.reserve(cvec.size());
    bool java_batch_supported = true;
    for (size_t i = 0, e = cvec.size(); i < e; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC columnar builder creation")) {
            return nullptr;
        }
        builders.push_back(jdbc_make_columnar_builder(cvec[i], xsink));
        if (*xsink) {
            return nullptr;
        }
        JdbcJavaBatchMode mode = builders.back()->getJavaBatchMode(cvec[i]);
        if (mode == JdbcJavaBatchMode::Unsupported) {
            java_batch_supported = false;
        }
        java_batch_modes.push_back(static_cast<jint>(mode));
    }

    if (java_batch_supported) {
        if (jdbc_fetch_columnar_java_batches(env, rs, builders, cvec, java_batch_modes, max_rows, xsink)) {
            return nullptr;
        }
    } else {
        size_t row_count = 0;
        while (true) {
            if (!next(env)) {
                break;
            }

            for (size_t c = 0, e = cvec.size(); c < e; ++c) {
                if (c && !(c % 100) && qore_check_cancel(xsink, "JDBC columnar row conversion")) {
                    return nullptr;
                }
                QoreJdbcColumn& col = cvec[c];
                if (builders[c]->needsGenericValue()) {
                    ValueHolder val(getColumnValue(env, static_cast<int>(c + 1), col, xsink), xsink);
                    if (*xsink) {
                        return nullptr;
                    }
                    if (builders[c]->appendQoreValue(val.release(), xsink)) {
                        return nullptr;
                    }
                } else if (builders[c]->appendJdbcValue(env, rs, static_cast<int>(c + 1), col, xsink)) {
                    return nullptr;
                }
            }
            ++row_count;
            if (max_rows > 0 && row_count == static_cast<size_t>(max_rows)) {
                break;
            }
            if (row_count && !(row_count % 100) && qore_check_cancel(xsink, "JDBC columnar result fetch")) {
                return nullptr;
            }
        }
    }

    ReferenceHolder<QoreColumnarResult> rv(new QoreColumnarResult, xsink);
    for (size_t i = 0, e = cvec.size(); i < e; ++i) {
        if (i && !(i % 100) && qore_check_cancel(xsink, "JDBC columnar result creation")) {
            return nullptr;
        }
        ValueHolder value(builders[i]->makeValue(xsink), xsink);
        if (*xsink) {
            return nullptr;
        }
        if (rv->addColumn(cvec[i].qname.c_str(), value.release(), builders[i]->getSchema(), xsink)) {
            return nullptr;
        }
    }
    return rv.release();
}

#endif

QoreHashNode* QoreJdbcStatement::getSingleRow(Env& env, ExceptionSink* xsink) {
    assert(!rs);
    rs = env.callObjectMethod(stmt, Globals::methodPreparedStatementGetResultSet, nullptr);
    if (!rs) {
        return nullptr;
    }

    // make sure there is at least one row
    if (!next(env)) {
        // if not, return NOTHING
        return nullptr;
    }

    if (describeResultSet(env, xsink)) {
        return nullptr;
    }

    ReferenceHolder<QoreHashNode> rv(getSingleRowIntern(env, xsink), xsink);

    // make sure there's not another row
    if (next(env)) {
        xsink->raiseException("JDBC-SELECT-ROW-ERROR", "SQL passed to selectRow() returned more than 1 row");
        return nullptr;
    }

    return rv.release();
}

QoreHashNode* QoreJdbcStatement::getSingleRowIntern(Env& env, ExceptionSink* xsink) {
    assert(rs);
    // get the row to return
    ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);
    jint c = 0;
    for (auto& col : cvec) {
        ValueHolder val(getColumnValue(env, c + 1, col, xsink), xsink);
        if (*xsink) {
            return nullptr;
        }

        HashAssignmentHelper hah(**rv, col.qname.c_str());
        hah.assign(val.release(), xsink);
        assert(!*xsink);
        ++c;
    }

    return rv.release();
}

QoreValue QoreJdbcStatement::getColumnValue(Env& env, int column, QoreJdbcColumn& col, ExceptionSink* xsink) {
    // get column value for this row
    jvalue jarg;
    jarg.i = column;
    LocalReference<jobject> val = env.callObjectMethod(rs, Globals::methodResultSetGetObject, &jarg);
    if (!val) {
        return &Null;
    }
    // return Qore value
    ValueHolder rv(JavaToQore::convertToQore(val.release(), conn->getProgram(), false, conn->getNumericOption()),
        xsink);
    // strip trailing spaces in CHAR columns if necessary
    if (col.strip && rv->getType() == NT_STRING) {
        QoreStringNodeValueHelper str(*rv);
        QoreStringNode* trimmed = new QoreStringNode(**str);
        trimmed->trim_trailing(' ');
        rv = trimmed;
    }
    return rv.release();
}

int QoreJdbcStatement::rowsAffected(Env& env) {
    return env.callIntMethod(stmt, Globals::methodPreparedStatementGetUpdateCount, nullptr);
}

int QoreJdbcStatement::parse(QoreString* str, const QoreListNode* args, ExceptionSink* xsink) {
    char quote = 0;
    const char *p = str->c_str();
    QoreString tmp;
    int index = 0;
    SQLCommentType comment = ESCT_NONE;
    params = nullptr;
    bind_size = 0;

    while (*p) {
        if (!quote) {
            if (comment == ESCT_NONE) {
                if ((*p) == '-' && (*(p+1)) == '-') {
                    comment = ESCT_LINE;
                    p += 2;
                    continue;
                }

                if ((*p) == '/' && (*(p+1)) == '*') {
                    comment = ESCT_BLOCK;
                    p += 2;
                    continue;
                }
            } else {
                if (comment == ESCT_LINE) {
                    if ((*p) == '\n' || ((*p) == '\r')) {
                        comment = ESCT_NONE;
                    }
                    ++p;
                    continue;
                }

                assert(comment == ESCT_BLOCK);
                if ((*p) == '*' && (*(p+1)) == '/') {
                    comment = ESCT_NONE;
                    p += 2;
                    continue;
                }

                ++p;
                continue;
            }

            if ((*p) == '%' && (p == str->c_str() || !isalnum(*(p-1)))) { // Found value marker.
                int offset = p - str->c_str();

                ++p;
                QoreValue v = args ? args->retrieveEntry(index++) : QoreValue();
                if ((*p) == 'd') {
                    DBI_concat_numeric(&tmp, v);
                    str->replace(offset, 2, tmp.c_str());
                    p = str->c_str() + offset + tmp.strlen();
                    tmp.clear();
                    continue;
                }
                if ((*p) == 's') {
                    if (DBI_concat_string(&tmp, v, xsink)) {
                        return -1;
                    }
                    str->replace(offset, 2, tmp.c_str());
                    p = str->c_str() + offset + tmp.strlen();
                    tmp.clear();
                    continue;
                }
                if ((*p) != 'v') {
                    xsink->raiseException("JDBC-PARSE-ERROR", "invalid value specification (expecting '%%v' or "
                        "'%%d', got %%%c)", *p);
                    return -1;
                }
                ++p;
                if (isalpha(*p)) {
                    xsink->raiseException("JDBC-PARSE-ERROR", "invalid value specification (expecting '%%v' or "
                        "'%%d', got %%v%c*)", *p);
                    return -1;
                }

                str->replace(offset, 2, "?");
                p = str->c_str() + offset + 1;
                ++bind_size;
                if (!params) {
                    params = new QoreListNode(autoTypeInfo);
                }
                params->push(v.refSelf(), xsink);
                continue;
            }

            // Allow escaping of '%' characters.
            if ((*p) == '\\' && (*(p+1) == ':' || *(p+1) == '%')) {
                str->splice(p - str->c_str(), 1, xsink);
                p += 2;
                continue;
            }
        }

        if (((*p) == '\'') || ((*p) == '\"')) {
            if (!quote) {
                quote = *p;
            } else if (quote == (*p)) {
                quote = 0;
            }
            ++p;
            continue;
        }

        ++p;
    }

    return 0;
}

size_t QoreJdbcStatement::findArraySizeOfArgs(const QoreListNode* args) const {
    size_t count = args ? args->size() : 0;
    for (unsigned int i = 0; i < count; ++i) {
        QoreValue arg = args->retrieveEntry(i);
        qore_type_t ntype = arg.getType();
        if (ntype == NT_LIST) {
            return arg.get<const QoreListNode>()->size();
        }
    }
    return 0;
}

int QoreJdbcStatement::bindIntern(Env& env, const QoreListNode* args, ExceptionSink* xsink) {
    size_t count = args ? args->size() : 0;
    // ignore excess arguments
    if (count > bind_size) {
        count = bind_size;
    }
    for (unsigned int i = 0; i < count; ++i) {
        QoreValue arg = args->retrieveEntry(i);
        if (bindParamSingleValue(env, i + 1, arg, xsink)) {
            return -1;
        }
    }
    // bind excess positions with NULL
    for (unsigned i = count; i < bind_size; ++i) {
        if (bindParamSingleValue(env, i + 1, QoreValue(), xsink)) {
            return -1;
        }
    }

    return 0;
}

int QoreJdbcStatement::bindParamSingleValue(Env& env, int column, QoreValue arg, ExceptionSink* xsink) {
    std::vector<jvalue> jargs(2);
    jargs[0].i = column;

    switch (arg.getType()) {
        case NT_NULL:
        case NT_NOTHING: {
            jargs[1].i = Globals::typeNull;
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetNull, &jargs[0]);
            break;
        }

        case NT_INT: {
            int64 i = arg.getAsBigInt();
            if (i <= 127 && i >= -128) {
                jargs[1].b = (int8_t)i;
                env.callVoidMethod(stmt, Globals::methodPreparedStatementSetByte, &jargs[0]);
            } else if (i <= 32767 && i >= -32768) {
                jargs[1].s = (int16_t)i;
                env.callVoidMethod(stmt, Globals::methodPreparedStatementSetShort, &jargs[0]);
            } else if (i <= 2147483647 && i >= -2147483648) {
                jargs[1].i = (int32_t)i;
                env.callVoidMethod(stmt, Globals::methodPreparedStatementSetInt, &jargs[0]);
            } else {
                jargs[1].j = i;
                env.callVoidMethod(stmt, Globals::methodPreparedStatementSetLong, &jargs[0]);
            }
            break;
        }

        case NT_FLOAT: {
            jargs[1].d = arg.getAsFloat();
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetDouble, &jargs[0]);
            break;
        }

        case NT_BOOLEAN: {
            jargs[1].z = arg.getAsBool();
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetBoolean, &jargs[0]);
            break;
        }

        case NT_STRING: {
            QoreStringValueHelper str(arg);
            LocalReference<jstring> jstr = env.newString(str->c_str());
            jargs[1].l = jstr;
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetString, &jargs[0]);
            break;
        }

        case NT_BINARY: {
            LocalReference<jbyteArray> array = QoreToJava::makeByteArray(env, *arg.get<const BinaryNode>());
            jargs[1].l = array;
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetBytes, &jargs[0]);
            break;
        }

        case NT_DATE: {
            const DateTimeNode* dt = arg.get<const DateTimeNode>();
            jvalue jarg;
            int64 epoch_s = dt->getEpochSecondsUTC();
            int us = dt->getMicrosecond();
            // set milliseconds from seconds value
            jarg.j = epoch_s * 1000;
            LocalReference<jobject> ts = env.newObject(Globals::classTimestamp, Globals::ctorTimestamp, &jarg);
            // set nanoseconds
            jint ns = us * 1000;
            jarg.i = ns;
            env.callVoidMethod(ts, Globals::methodTimestampSetNanos, &jarg);
            // bind timestamp value
            jargs[1].l = ts;
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetTimestamp, &jargs[0]);
            break;
        }

        case NT_NUMBER: {
            LocalReference<jobject> num = QoreToJava::makeBigDecimal(env, *arg.get<const QoreNumberNode>());
            jargs[1].l = num;
            env.callVoidMethod(stmt, Globals::methodPreparedStatementSetBigDecimal, &jargs[0]);
            break;
        }

        default:
            xsink->raiseException("JDBC-BIND-ERROR", "do not know how to bind arguments of type '%s'",
                arg.getFullTypeName());
            return -1;
    }

    return 0;
}

int QoreJdbcStatement::bindInternArray(Env& env, const QoreListNode* args, ExceptionSink* xsink) {
    // Check that enough parameters were passed for binding.
    size_t count = args ? args->size() : 0;
    if (bind_size != count) {
        xsink->raiseException("JDBC-BIND-ERROR",
            "mismatch between the parameter list size and number of parameters in the SQL command; %lu "
            "required, %lu passed", bind_size, args->size());
        return -1;
    }

    return bindInternArrayBatch(env, args, xsink);
#if 0
    if (conn->areArraysSupported(env)) {
        return bindInternArrayNative(env, args, xsink);
    } else {
        return bindInternArrayBatch(env, args, xsink);
    }
#endif
}

#if 0
const char* QoreJdbcStatement::getArrayBindType(const QoreListNode* l) const {
    ConstListIterator i(l);
    while (i.next()) {
        switch (i.getValue().getType()) {
            case NT_NOTHING:
            case NT_NULL:
                break;

            case NT_INT:
                return "int";

            case NT_FLOAT:
                return "double";

            case NT_NUMBER:
                return "numeric";

            case NT_STRING:
                return "varchar";

            case NT_BOOLEAN:
                return "boolean";

            case NT_DATE:
                return "timestamp";

            case NT_BINARY:
                return "blob";

            default:
                return i.getValue().getTypeName();
        }
    }

    return "null";
}

int QoreJdbcStatement::bindInternArrayNative(Env& env, const QoreListNode* args, ExceptionSink* xsink) {
    size_t count = args ? args->size() : 0;
    for (unsigned int i = 0; i < count; ++i) {
        QoreValue arg = args->retrieveEntry(i);

        if (arg.getType() != NT_LIST) {
            if (bindParamSingleValue(env, i + 1, arg, xsink)) {
                return -1;
            }
            continue;
        }

        const char* bind_type = getArrayBindType(arg.get<const QoreListNode>());
        printd(0, "QoreJdbcStatement::bindInternArray() binding array of SQL type '%s'\n", bind_type);

        std::vector<jvalue> jargs(2);
        LocalReference<jstring> jurl = env.newString(bind_type);
        jargs[0].l = jurl;
        LocalReference<jobject> array_arg = QoreToJava::toAnyObject(env, arg, conn->getQoreJniContext());
        jargs[1].l = array_arg;
        LocalReference<jobject> array = env.callObjectMethod(conn->getConnectionObject(),
            Globals::methodConnectionCreateArrayOf, &jargs[0]);
        jargs[0].l = array;
        env.callVoidMethod(stmt, Globals::methodPreparedStatementSetArray, &jargs[0]);
    }

    return 0;
}
#endif

int QoreJdbcStatement::bindInternArrayBatch(Env& env, const QoreListNode* args, ExceptionSink* xsink) {
    do_batch_execute = true;
    size_t list_size = findArraySizeOfArgs(args);
    size_t arg_count = args ? args->size() : 0;

    for (size_t i = 0; i < list_size; ++i) {
        for (unsigned int j = 0; j < arg_count; ++j) {
            QoreValue arg = args->retrieveEntry(j);
            // get value to bind from the list if necessary
            if (arg.getType() == NT_LIST) {
                const QoreListNode* l = arg.get<const QoreListNode>();
                if (l->size() != list_size) {
                    xsink->raiseException("JDBC-BIND-ERROR", "the array size for bind argument %d (starting from 1) "
                        "is %zu which is inconsistent with the detected array size %zu.  This is an error, because "
                        "all array bind arguments must have the same array / list size.", (int)j, l->size(),
                        list_size);
                    return -1;
                }
                arg = l->retrieveEntry(i);
            }
            if (bindParamSingleValue(env, j + 1, arg, xsink)) {
                return -1;
            }
        }
        env.callVoidMethod(stmt, Globals::methodPreparedStatementAddBatch, nullptr);
    }
    return 0;
}

}
