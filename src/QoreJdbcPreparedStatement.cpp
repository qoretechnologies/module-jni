/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcPreparedStatement.cpp

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.

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

#include "QoreJdbcPreparedStatement.h"

namespace jni {

QoreJdbcPreparedStatement::QoreJdbcPreparedStatement(ExceptionSink* xsink, QoreJdbcConnection* c) :
        QoreJdbcStatement(xsink, c) {
}

QoreJdbcPreparedStatement::~QoreJdbcPreparedStatement() {
    // jdbc_stmt_close() supplies the current call's sink before destroying persistent state.
    assert(!stmt && !rs);
}

int QoreJdbcPreparedStatement::prepare(const QoreString& qstr, const QoreListNode* args, ExceptionSink* xsink) {
    // Convert string to required character encoding.
    TempEncodingHelper str(qstr, QCS_UTF8, xsink);
    if (*xsink) {
        return false;
    }
    str.makeTemp();
    if (parse(const_cast<QoreString*>(*str), args, xsink)) {
        assert(*xsink);
        return -1;
    }
    sql = *str;

    try {
        //printd(5, "QoreJdbcPreparedStatement::prepare() this: %p '%s' args: %zd cvec: %zd\n", this, str->c_str(),
        //    args ? args->size() : 0, cvec.size());
        Env env;
        prepareStatement(env, *str);

        // Save bind arguments
        assignBindArgs(xsink, args ? args->listRefSelf() : nullptr);

        return 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcPreparedStatement::exec(ExceptionSink* xsink) {
    try {
        Env env;
        if (rs) {
            GlobalReference<jobject> previous = std::move(rs);
            env.callVoidMethod(previous, Globals::methodResultSetClose, nullptr);
        }
        cvec.clear();
        results_done = false;
        update_count_total = -1;
        // SQLStatement returns one result set; the results after it are read when it ends
        setOnlyResultSet("JDBC-RESULTSET-ERROR", "the SQL executed by SQLStatement");
        if (!stmt) {
            // the JDBC handles were released when the connection was lost; prepare on the current connection
            prepareStatement(env, sql);
        }
        if (bindQueryArguments(env, xsink)) {
            assert(*xsink);
            return -1;
        }

        bool result_set = execIntern(env, sql, xsink);
        if (*xsink) {
            return -1;
        }
        if (do_batch_execute) {
            return 0;
        }
        // update counts before the result set are added to affectedRows(); without a result set, every result is
        // read here
        result_set = skipUpdateCounts(env, result_set, xsink);
        if (*xsink) {
            return -1;
        }
        if (result_set) {
            if (acquireResultSet(env, xsink)) {
                return -1;
            }
            if (describeResultSet(env, xsink)) {
                if (*xsink) {
                    return -1;
                }
                // no columns, so no rows can be returned: read the remaining results
                return finishResults(env, xsink) ? -1 : 0;
            }
        }
        return 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcPreparedStatement::bind(const QoreListNode& args, ExceptionSink* xsink) {
    try {
        assignBindArgs(xsink, args.listRefSelf());
        return *xsink ? -1 : 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}

bool QoreJdbcPreparedStatement::requireResultSet(ExceptionSink* xsink) const {
    // a result set read to the end stays at its end
    if (rs || results_done) {
        return true;
    }
    xsink->raiseException("JDBC-RESULTSET-ERROR", "the executed statement did not produce a result set");
    return false;
}

QoreHashNode* QoreJdbcPreparedStatement::getOutputHash(ExceptionSink* xsink, bool empty_hash_if_nothing,
        int max_rows) {
    // Non-query statements have no output; getOutput() is also valid after a batch DML.
    if (!rs && !results_done) {
        return nullptr;
    }
    try {
        Env env;
        return getOutputHashIntern(env, xsink, empty_hash_if_nothing, max_rows);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreHashNode* QoreJdbcPreparedStatement::fetchRow(ExceptionSink* xsink) {
    if (!requireResultSet(xsink)) {
        return nullptr;
    }
    try {
        Env env;
        return getSingleRowIntern(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreListNode* QoreJdbcPreparedStatement::fetchRows(int max_rows, ExceptionSink* xsink) {
    if (!requireResultSet(xsink)) {
        return nullptr;
    }
    try {
        Env env;
        return getOutputListIntern(env, xsink, max_rows);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreHashNode* QoreJdbcPreparedStatement::fetchColumns(int max_rows, ExceptionSink* xsink) {
    if (!requireResultSet(xsink)) {
        return nullptr;
    }
    try {
        Env env;
        return getOutputHashIntern(env, xsink, true, max_rows);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
QoreColumnarResult* QoreJdbcPreparedStatement::fetchColumnar(int rows, ExceptionSink* xsink) {
    if (!requireResultSet(xsink)) {
        return nullptr;
    }
    try {
        Env env;
        return getOutputColumnarIntern(env, xsink, rows);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}
#endif

bool QoreJdbcPreparedStatement::next(ExceptionSink* xsink) {
    if (!requireResultSet(xsink)) {
        return false;
    }
    try {
        Env env;
        return QoreJdbcStatement::next(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return false;
}

QoreHashNode* QoreJdbcPreparedStatement::describe(ExceptionSink* xsink) {
    try {
        Env env;
        return QoreJdbcStatement::describe(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}

int QoreJdbcPreparedStatement::clear(ExceptionSink* xsink) {
    try {
        //printd(5, "QoreJdbcPreparedStatement::clear() %p\n", this);
        // Called when the connection is lost: release the JDBC handles but keep the SQL and the bind arguments,
        // so the statement can be executed again (or replayed by a reconnecting exec()) on the new connection.
        Env env;
        freeHandles(env, xsink);
        return *xsink ? -1 : 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}
}
