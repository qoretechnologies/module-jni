/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcDriver.cpp

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

#include "QoreJdbcDriver.h"
#include "QoreJdbcConnection.h"
#include "QoreJdbcPreparedStatement.h"

#include <memory>

namespace jni {

static constexpr int jdbc_caps = DBI_CAP_TRANSACTION_MANAGEMENT
   | DBI_CAP_CHARSET_SUPPORT
   | DBI_CAP_STORED_PROCEDURES
   | DBI_CAP_LOB_SUPPORT
   | DBI_CAP_BIND_BY_VALUE
   | DBI_CAP_HAS_EXECRAW
   | DBI_CAP_TIME_ZONE_SUPPORT
   | DBI_CAP_HAS_NUMBER_SUPPORT
   | DBI_CAP_SERVER_TIME_ZONE
   | DBI_CAP_AUTORECONNECT
;

DBIDriver* DBID_JDBC = nullptr;

static int jdbc_open(Datasource* ds, ExceptionSink* xsink) {
    std::unique_ptr<QoreJdbcConnection> conn(new QoreJdbcConnection(ds, xsink));
    if (*xsink) {
        return -1;
    }

    ds->setPrivateData((void*)conn.release());
    return 0;
}

static int jdbc_close(Datasource* ds) {
    std::unique_ptr<QoreJdbcConnection> conn(ds->getPrivateData<QoreJdbcConnection>());
    ExceptionSink xsink;
    try {
        Env env;
        conn->close(env);
    } catch (JavaException& e) {
        e.convert(&xsink);
    }
    int rc = xsink ? -1 : 0;
    xsink.clear();
    ds->setPrivateData(nullptr);
    return rc;
}

static int jdbc_commit(Datasource* ds, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->commit(xsink);
}

static int jdbc_rollback(Datasource* ds, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->rollback(xsink);
}

static int jdbc_begin_transaction(Datasource* ds, ExceptionSink* xsink) {
    return 0;
}

static QoreValue jdbc_select(Datasource* ds, const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->select(qstr, args, xsink);
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
static QoreColumnarResult* jdbc_select_columnar(Datasource* ds, const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->selectColumnar(qstr, args, xsink);
}
#endif

static QoreHashNode* jdbc_select_row(Datasource* ds, const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->selectRow(qstr, args, xsink);
}

static QoreValue jdbc_select_rows(Datasource* ds, const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->selectRows(qstr, args, xsink);
}

static QoreValue jdbc_exec(Datasource* ds, const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->exec(qstr, args, xsink);
}

static QoreValue jdbc_exec_raw(Datasource* ds, const QoreString* qstr, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->execRaw(qstr, xsink);
}

static QoreValue jdbc_get_server_version(Datasource* ds, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->getServerVersion(xsink);
}

static QoreValue jdbc_get_client_version(const Datasource* ds, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    if (!conn) {
        xsink->raiseException("JDBC-CONNECTION-ERROR", "there is no open connection; client info is provided by the "
            "Java jdbc driver used for the DB connection by this Qore driver; please open a connection to the server "
            "before calling this method");
        return QoreValue();
    }
    return conn->getClientVersion(xsink);
}

static QoreStringNode* jdbc_get_driver_real_name(Datasource* ds, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->getDriverRealName(xsink);
}

static int jdbc_stmt_prepare(SQLStatement* stmt, const QoreString& str, const QoreListNode* args,
        ExceptionSink* xsink) {
    assert(!stmt->getPrivateData());

    QoreJdbcPreparedStatement* ps = new QoreJdbcPreparedStatement(xsink,
        stmt->getDatasource()->getPrivateData<QoreJdbcConnection>());
    if (*xsink) {
        delete ps;
        return -1;
    }
    stmt->setPrivateData(ps);

    return ps->prepare(str, args, xsink);
}

static int jdbc_stmt_prepare_raw(SQLStatement* stmt, const QoreString& str, ExceptionSink* xsink) {
    assert(!stmt->getPrivateData());

    QoreJdbcPreparedStatement* ps = new QoreJdbcPreparedStatement(xsink,
        stmt->getDatasource()->getPrivateData<QoreJdbcConnection>());
    if (*xsink) {
        delete ps;
        return -1;
    }
    stmt->setPrivateData(ps);

    return ps->prepare(str, nullptr, xsink);
}

static int jdbc_stmt_bind(SQLStatement* stmt, const QoreListNode& args, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->bind(args, xsink);
}

static int jdbc_stmt_bind_placeholders(SQLStatement* stmt, const QoreListNode& args, ExceptionSink* xsink) {
    xsink->raiseException("JDBC-BIND-PLACEHHODERS-ERROR", "binding placeholders is not necessary or supported with "
        "the jdbc driver");
    return -1;
}

static int jdbc_stmt_bind_values(SQLStatement* stmt, const QoreListNode& args, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->bind(args, xsink);
}

static int jdbc_stmt_exec(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->QoreJdbcPreparedStatement::exec(xsink);
}

static int jdbc_stmt_define(SQLStatement* stmt, ExceptionSink* xsink) {
    // define is a noop in the jdbc driver
    return 0;
}

static int jdbc_stmt_affected_rows(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    try {
        Env env;
        return ps->rowsAffected(env);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return -1;
}

static QoreHashNode* jdbc_stmt_get_output(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->getOutputHash(xsink, false);
}

static QoreHashNode* jdbc_stmt_get_output_rows(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->getOutputHash(xsink, false);
}

static QoreHashNode* jdbc_stmt_fetch_row(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->fetchRow(xsink);
}

static QoreListNode* jdbc_stmt_fetch_rows(SQLStatement* stmt, int maxRows, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->fetchRows(maxRows, xsink);
}

static QoreHashNode* jdbc_stmt_fetch_columns(SQLStatement* stmt, int maxRows, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->fetchColumns(maxRows, xsink);
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
static QoreColumnarResult* jdbc_stmt_fetch_columnar(SQLStatement* stmt, int maxRows, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->fetchColumnar(maxRows, xsink);
}
#endif

static QoreHashNode* jdbc_stmt_describe(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    //return ps->describe(xsink);
    return nullptr;
}

static bool jdbc_stmt_next(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    return ps->QoreJdbcPreparedStatement::next(xsink);
}

static int jdbc_stmt_free(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    // Free all handles without closing the statement or freeing private data
    return ps->clear(xsink);
}

static int jdbc_stmt_close(SQLStatement* stmt, ExceptionSink* xsink) {
    QoreJdbcPreparedStatement* ps = stmt->getPrivateData<QoreJdbcPreparedStatement>();
    assert(ps);

    try {
        Env env;
        ps->close(env);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    delete ps;
    stmt->setPrivateData(nullptr);
    return *xsink ? -1 : 0;
}

static int jdbc_opt_set(Datasource* ds, const char* opt, const QoreValue val, ExceptionSink* xsink) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    assert(conn);
    return conn->setOption(opt, val, xsink);
}

static QoreValue jdbc_opt_get(const Datasource* ds, const char* opt) {
    QoreJdbcConnection* conn = ds->getPrivateData<QoreJdbcConnection>();
    return conn ? conn->getOption(opt) : QoreValue();
}

void setup_jdbc_driver() {
    qore_dbi_method_list methods;
    methods.add(QDBI_METHOD_OPEN, jdbc_open);
    methods.add(QDBI_METHOD_CLOSE, jdbc_close);
    methods.add(QDBI_METHOD_COMMIT, jdbc_commit);
    methods.add(QDBI_METHOD_ROLLBACK, jdbc_rollback);
    methods.add(QDBI_METHOD_BEGIN_TRANSACTION, jdbc_begin_transaction);
    methods.add(QDBI_METHOD_SELECT, jdbc_select);
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    methods.add(QDBI_METHOD_SELECT_COLUMNAR, jdbc_select_columnar);
#endif
    methods.add(QDBI_METHOD_SELECT_ROWS, jdbc_select_rows);
    methods.add(QDBI_METHOD_SELECT_ROW, jdbc_select_row);
    methods.add(QDBI_METHOD_EXEC, jdbc_exec);
    methods.add(QDBI_METHOD_EXECRAW, jdbc_exec_raw);
    methods.add(QDBI_METHOD_GET_SERVER_VERSION, jdbc_get_server_version);
    methods.add(QDBI_METHOD_GET_CLIENT_VERSION, jdbc_get_client_version);
    methods.add(QDBI_METHOD_GET_DRIVER_REAL_NAME, jdbc_get_driver_real_name);

    methods.add(QDBI_METHOD_STMT_PREPARE, jdbc_stmt_prepare);
    methods.add(QDBI_METHOD_STMT_PREPARE_RAW, jdbc_stmt_prepare_raw);
    methods.add(QDBI_METHOD_STMT_BIND, jdbc_stmt_bind);
    methods.add(QDBI_METHOD_STMT_BIND_PLACEHOLDERS, jdbc_stmt_bind_placeholders);
    methods.add(QDBI_METHOD_STMT_BIND_VALUES, jdbc_stmt_bind_values);
    methods.add(QDBI_METHOD_STMT_EXEC, jdbc_stmt_exec);
    methods.add(QDBI_METHOD_STMT_DEFINE, jdbc_stmt_define);
    methods.add(QDBI_METHOD_STMT_FETCH_ROW, jdbc_stmt_fetch_row);
    methods.add(QDBI_METHOD_STMT_FETCH_ROWS, jdbc_stmt_fetch_rows);
    methods.add(QDBI_METHOD_STMT_FETCH_COLUMNS, jdbc_stmt_fetch_columns);
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    methods.add(QDBI_METHOD_STMT_FETCH_COLUMNAR, jdbc_stmt_fetch_columnar);
#endif
    methods.add(QDBI_METHOD_STMT_DESCRIBE, jdbc_stmt_describe);
    methods.add(QDBI_METHOD_STMT_NEXT, jdbc_stmt_next);
    methods.add(QDBI_METHOD_STMT_FREE, jdbc_stmt_free);
    methods.add(QDBI_METHOD_STMT_CLOSE, jdbc_stmt_close);
    methods.add(QDBI_METHOD_STMT_AFFECTED_ROWS, jdbc_stmt_affected_rows);
    methods.add(QDBI_METHOD_STMT_GET_OUTPUT, jdbc_stmt_get_output);
    methods.add(QDBI_METHOD_STMT_GET_OUTPUT_ROWS, jdbc_stmt_get_output_rows);

    methods.add(QDBI_METHOD_OPT_SET, jdbc_opt_set);
    methods.add(QDBI_METHOD_OPT_GET, jdbc_opt_get);

    methods.registerOption(DBI_OPT_NUMBER_OPT, "when set, numeric/decimal values are returned as integers if "
        "possible, otherwise as arbitrary-precision number values; the argument is ignored; setting this option "
        "turns it on and turns off 'string-numbers' and 'numeric-numbers'");
    methods.registerOption(DBI_OPT_NUMBER_STRING, "when set, numeric/decimal values are returned as strings for "
        "backwards-compatibility; the argument is ignored; setting this option turns it on and turns off "
        "'optimal-numbers' and 'numeric-numbers'");
    methods.registerOption(DBI_OPT_NUMBER_NUMERIC, "when set, numeric/decimal values are returned as "
        "arbitrary-precision number values; the argument is ignored; setting this option turns it on and turns off "
        "'string-numbers' and 'optimal-numbers'");
    methods.registerOption(JDBC_OPT_CLASSPATH, "set the classpath before loading the driver", stringTypeInfo);
    methods.registerOption(JDBC_OPT_URL, "override the database string with the jdbc driver URL", stringTypeInfo);

    DBID_JDBC = DBI.registerDriver("jdbc", methods, jdbc_caps);
}
}
