/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcConnection.cpp

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

#include "QoreJdbcConnection.h"
#include "QoreJdbcStatement.h"
#include "QoreJdbcDriver.h"
#include "LocalReference.h"
#include "Globals.h"
#include "QoreToJava.h"
#include "Env.h"

#include <vector>

namespace jni {

static void set_prop(Env& env, LocalReference<jobject>& props, const char* prop, const char* val) {
    std::vector<jvalue> jargs(2);

    LocalReference<jstring> jprop = env.newString(prop);
    jargs[0].l = jprop;
    LocalReference<jstring> jval = env.newString(val);
    jargs[1].l = jval;
    LocalReference<jobject> ignore = env.callObjectMethod(props, Globals::methodPropertiesSetProperty, &jargs[0]);
}

QoreJdbcConnection::QoreJdbcConnection(Datasource* ds, ExceptionSink* xsink) : ds(ds) {
    assert(pgm);
    try {
        jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return;
    }
    assert(jpc);

    // Parse options passed through the datasource.
    if (parseOptions(xsink)) {
        return;
    }

    Env env;
    if (connect(env, xsink)) {
        assert(*xsink);
        return;
    }

    // determine DB type
    LocalReference<jobject> md;
    try {
        md = env.callObjectMethod(connection, Globals::methodConnectionGetMetaData, nullptr);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        QoreCancelDeferralHelper cancel_deferral;
        try {
            close(env);
        } catch (jni::Exception& close_error) {
            close_error.convert(xsink);
        }
        return;
    }
    if (!md) {
        return;
    }

#if 0
    LocalReference<jstring> str = env.callObjectMethod(md,
        Globals::methodDatabaseMetaDataGetDatabaseProductName, nullptr).as<jstring>();
    if (str) {
        Env::GetStringUtfChars jname(env, str);
        QoreString product_name(jname.c_str());
        product_name.tolwr();
        if (product_name.startsWith("oracle")) {
            dbtype = DBT_ORACLE;
            array_support = DAS_SUPPORTED;
            LocalReference<jclass> cls = env.callObjectMethod(connection, Globals::methodObjectGetClass, nullptr)
                .as<jclass>();
            methodOracleConnectionCreateOracleArray = env.getMethod(cls, "createOracleArray",
                "(Ljava/lang/String;Ljava/lang/Object;)Ljava/sql/Array;");
        }
    }
#endif
}

QoreJdbcConnection::~QoreJdbcConnection() {
    assert(!connection);
}

int QoreJdbcConnection::reconnect(Env& env, ExceptionSink* xsink) {
    close(env);
    return connect(env, xsink);
}

int QoreJdbcConnection::connect(Env& env, ExceptionSink* xsink) {
    assert(!connection);
    // get URL
    const std::string& str = db.empty() ? ds->getDBNameStr() : db;
    if (str.empty()) {
        xsink->raiseException("JDBC-CONNECTION-ERROR", "cannot build JDBC URL without a db name or 'url' connection "
            "option");
        return -1;
    }
    QoreStringMaker url("%s%s", !str.rfind("jdbc:", 0) ? "" : "jdbc:", str.c_str());

    printd(5, "QoreJdbcConnection::connect() using URL '%s' (jpc: %p: %x)\n", url.c_str(), jpc,
        env.callIntMethod(jpc->getClassLoader(), jni::Globals::methodObjectHashCode, nullptr));

    try {
        LocalReference<jobject> props = env.newObject(Globals::classProperties, Globals::ctorProperties, nullptr);
        {
            const std::string& str = ds->getUsernameStr();
            if (!str.empty()) {
                set_prop(env, props, "user", str.c_str());
            }
        }
        {
            const std::string& str = ds->getPasswordStr();
            if (!str.empty()) {
                set_prop(env, props, "password", str.c_str());
            }
        }

        // use the dynamic API to ensure that the DriverManager.getConnection() call is made in the right context
        LocalReference<jstring> jurl = env.newString(url.c_str());
        std::vector<jvalue> jargs(2);
        jargs[0].l = jurl;
        jargs[1].l = props;

        connection = env.callStaticObjectMethod(jpc->getDynamicApi(), jpc->getGetConnectionMethodId(), &jargs[0])
            .makeGlobal();

        printd(5, "QoreJdbcConnection::connect() got connection: %p\n", (jobject)connection);

        // Qore starts transactions explicitly. Keep unallocated reads in auto-commit mode.
        // Reconnection before the first write must preserve an already requested transaction.
        jargs[0].z = !transaction;
        env.callVoidMethod(connection, Globals::methodConnectionSetAutoCommit, &jargs[0]);
    } catch (jni::Exception& e) {
        if (qore_check_cancel(xsink, "connecting to the JDBC server")) {
            e.ignore();
        } else {
            e.convert(xsink);
        }
        // A connection can exist even when setting its initial mode failed.
        QoreCancelDeferralHelper cancel_deferral;
        try {
            close(env);
        } catch (jni::Exception& close_error) {
            close_error.convert(xsink);
        }
        return -1;
    }
    if (qore_check_cancel(xsink, "connecting to the JDBC server")) {
        QoreCancelDeferralHelper cancel_deferral;
        try {
            close(env);
        } catch (jni::Exception& e) {
            e.convert(xsink);
        }
        return -1;
    }
    assert(!*xsink);
    return 0;
}

int QoreJdbcConnection::parseOptions(ExceptionSink* xsink) {
    ConstHashIterator hi(ds->getConnectOptions());
    while (hi.next()) {
        //printd(5, "QoreJdbcConnection::parseOptions() this: %p '%s' = %s\n", this, hi.getKey(),
        //    hi.get().getFullTypeName());
        if (setOption(hi.getKey(), hi.get(), xsink)) {
            return -1;
        }
    }
    return 0;
}

int QoreJdbcConnection::close(Env& env) {
    if (!connection) {
        return 0;
    }
    JavaExceptionRethrowHelper erh;
    // Release our reference even if the JDBC driver's close() throws.
    GlobalReference<jobject> closing = std::move(connection);
    env.callVoidMethod(closing, Globals::methodConnectionClose, nullptr);

    return 0;
}

int QoreJdbcConnection::setOption(const char* opt, const QoreValue val, ExceptionSink* xsink) {
    if (!strcasecmp(opt, JDBC_OPT_CLASSPATH)) {
        if (val.getType() != NT_STRING) {
            xsink->raiseException("JDBC-OPTION-ERROR", "'%s' expects a 'string' value; got type '%s' instead",
                JDBC_OPT_CLASSPATH, val.getFullTypeName());
            return -1;
        }
        QoreStringValueHelper cp_str(val);
        const char* cp = cp_str->c_str();
        if (!strcmp(classpath.c_str(), cp)) {
            return 0;
        }

        QoreString arg(cp);
        q_env_subst(arg);
        //printd(5, "QoreJdbcConnection::setOption() %p '%s' jpc: %p arg: '%s'\n", this, opt, jpc, arg.c_str());

        // the rule of the "add-classpath" module command, for the code that sets the option (qore#5699): it loads jars
        // into the Program's class loader and reads the filesystem
        {
            QoreReflectionCaller caller;
            if (caller.getParseOptions().hasAny(QoreParseOptions(PO_NO_UNCONTROLLED_APIS | PO_NO_FILESYSTEM))) {
                xsink->raiseException("JNI-SANDBOX-ERROR", "the jdbc '%s' option adds to the Program's classpath, "
                    "which code that does not allow uncontrolled APIs or the filesystem cannot use", JDBC_OPT_CLASSPATH);
                return -1;
            }
            QoreSandboxManagerHelper governing(QoreSandboxManagerHelper::All);
            if (governing) {
                xsink->raiseException("JNI-SANDBOX-ERROR", "the jdbc '%s' option adds to the Program's classpath, "
                    "which sandboxed code cannot use", JDBC_OPT_CLASSPATH);
                return -1;
            }
        }

        try {
            // the entries are read by this Program's class loader, as with the "add-classpath" module command
            ::jni_check_classpath_sandbox("jdbc classpath option", pgm, arg.c_str(), false);
            jpc->addClasspath(arg.c_str());
            classpath = arg.c_str();

            // try to load new drivers
            Env env;

            printd(5, "QoreJdbcConnection::setOption() %p '%s' jpc: %p (%x) add classpath: '%s' (driver: %p)\n", this,
                opt, jpc, env.callIntMethod(jpc->getClassLoader(), jni::Globals::methodObjectHashCode, nullptr),
                arg.c_str(), *Globals::classDriver);

            LocalReference<jobject> sm = jpc->loadServiceLoader(env, Globals::classDriver);
            LocalReference<jobject> iterator = env.callObjectMethod(sm, Globals::methodServiceLoaderIterator,
                nullptr);
            while (true) {
                jboolean b = env.callBooleanMethod(iterator, Globals::methodIteratorHasNext, nullptr);
                if (!b) {
                    break;
                }
                LocalReference<jobject> o = env.callObjectMethod(iterator, Globals::methodIteratorNext, nullptr);

                // XXX DEBUG
                LocalReference<jstring> ostr = env.callObjectMethod(o, Globals::methodObjectToString, nullptr).as<jstring>();
                Env::GetStringUtfChars oname(env, ostr);

                LocalReference<jclass> jcls = env.callObjectMethod(o, Globals::methodObjectGetClass, nullptr).as<jclass>();
                LocalReference<jstring> cstr = env.callObjectMethod(jcls, Globals::methodObjectToString, nullptr).as<jstring>();
                Env::GetStringUtfChars cname(env, cstr);

                printd(5, "loaded driver '%s' (%s)\n", oname.c_str(), cname.c_str());
            }
        } catch (jni::Exception& e) {
            e.convert(xsink);
            return -1;
        }
    } else if (!strcasecmp(opt, JDBC_OPT_URL)) {
        if (val.getType() != NT_STRING) {
            xsink->raiseException("JDBC-OPTION-ERROR", "'%s' expects a 'string' value; got type '%s' instead",
                JDBC_OPT_URL, val.getFullTypeName());
            return -1;
        }
        QoreStringValueHelper db_str(val);
        db.assign(db_str->c_str(), db_str->size());
    } else if (!strcasecmp(opt, JDBC_OPT_LOB_MAX_SIZE)) {
        // an integer, or a string of digits only (ex: from a datasource string); "2G" or "1e9x" is refused
        int64 v = -1;
        if (val.getType() == NT_INT) {
            v = val.getAsBigInt();
        } else if (val.getType() == NT_STRING) {
            const QoreStringNode* str = val.get<const QoreStringNode>();
            const char* p = str->c_str();
            size_t len = str->size();
            if (len && len <= 18 && strspn(p, "0123456789") == len) {
                v = strtoll(p, nullptr, 10);
            }
        }
        if (v <= 0) {
            QoreStringValueHelper text(val);
            xsink->raiseException("JDBC-OPTION-ERROR", "option '%s' must be a positive integer number of bytes or "
                "characters; got %s '%s'", opt, val.getTypeName(), text->c_str());
            return -1;
        }
        lob_max_size = v;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_OPT)) {
        numeric = ENO_OPTIMAL;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_STRING)) {
        numeric = ENO_STRING;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_NUMERIC)) {
        numeric = ENO_NUMERIC;
    } else {
        xsink->raiseException("JDBC-OPTION-ERROR", "invalid option '%s'", opt);
        return -1;
    }
    return 0;
}

QoreValue QoreJdbcConnection::getOption(const char* opt) {
    if (!strcasecmp(opt, JDBC_OPT_CLASSPATH)) {
        return classpath.empty() ? QoreValue() : new QoreStringNode(classpath);
    } else if (!strcasecmp(opt, JDBC_OPT_URL)) {
        return db.empty() ? QoreValue() : new QoreStringNode(db);
    } else if (!strcasecmp(opt, JDBC_OPT_LOB_MAX_SIZE)) {
        return lob_max_size;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_OPT)) {
        return numeric == ENO_OPTIMAL;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_STRING)) {
        return numeric == ENO_STRING;
    } else if (!strcasecmp(opt, DBI_OPT_NUMBER_NUMERIC)) {
        return numeric == ENO_NUMERIC;
    } else {
        assert(false);
    }
    return QoreValue();
}

#if 0
bool QoreJdbcConnection::areArraysSupported(Env& env) {
    if (array_support == DAS_SUPPORTED) {
        return true;
    }
    if (array_support == DAS_NOT_SUPPORTED) {
        return false;
    }
    // lock and check again
    AutoLocker al(m);
    if (array_support == DAS_SUPPORTED) {
        return true;
    }
    if (array_support == DAS_NOT_SUPPORTED) {
        return false;
    }

    // try to create an array of strings
    ExceptionSink xsink;
    ReferenceHolder<QoreListNode> l(new QoreListNode(autoTypeInfo), &xsink);
    l->push(new QoreStringNode("x"), &xsink);
    LocalReference<jobject> array_arg = QoreToJava::toAnyObject(env, *l, jpc);

    std::vector<jvalue> jargs(2);
    LocalReference<jstring> jurl = env.newString("VARCHAR");
    jargs[0].l = jurl;
    jargs[1].l = array_arg;
    try {
        LocalReference<jobject> array = env.callObjectMethod(connection, Globals::methodConnectionCreateArrayOf,
            &jargs[0]);
        printd(0, "QoreJdbcConnection::areArraysSupported() OK\n");
        array_support = DAS_SUPPORTED;
        return true;
    } catch (JavaException& e) {
        SimpleRefHolder<QoreStringNode> errtxt(e.toString(false));
        printd(0, "QoreJdbcConnection::areArraysSupported() %s\n", errtxt->c_str());
        e.ignore();
    }
    array_support = DAS_NOT_SUPPORTED;
    return false;
}
#endif

bool QoreJdbcConnection::isConnectionLost(Env& env, jthrowable error, ExceptionSink* check_xsink) {
    if (!connection || !env.isInstanceOf(error, Globals::classSQLException)) {
        return false;
    }
    try {
        // 1 second timeout
        jvalue jarg{};
        jarg.i = 1;
        return !env.callBooleanMethod(connection, Globals::methodConnectionIsValid, &jarg);
    } catch (JavaException& e) {
        // the validity check failed, so the connection is not known to be lost; the caller raises the original error,
        // followed by this one
        e.convert(check_xsink);
    }
    return false;
}

int QoreJdbcConnection::beginTransaction(ExceptionSink* xsink) {
    assert(connection);
    if (transaction) {
        return 0;
    }
    try {
        Env env;
        jvalue arg{};
        arg.z = false;
        try {
            env.callVoidMethod(connection, Globals::methodConnectionSetAutoCommit, &arg);
        } catch (JavaException& e) {
            LocalReference<jthrowable> throwable = e.save();
            assert(throwable);
            // Recovery rule: a connection found lost when the transaction is started is re-established, and the
            // transaction is started on the new connection, because nothing can be lost or executed twice:
            // - the connection was in auto-commit mode (transaction is false), so it had no uncommitted work, and the
            //   datasource has no active transaction (SQL that starts a transaction by itself, ex: an explicit BEGIN
            //   TRANSACTION in a select, is outside this guarantee, as it is for the replay rule)
            // - no statement of the transaction has been sent: the transaction is started before its first statement
            //   is passed to the driver (Datasource::exec() and execRaw() start it before the driver executes the SQL,
            //   jdbc_stmt_exec() before the prepared statement is executed, and Datasource::beginTransaction()
            //   before any statement)
            // Some JDBC drivers start a transaction with a request to the server (the SQL Server driver sends
            // "set implicit_transactions on" in setAutoCommit(false)), so they find a lost connection here; others,
            // such as the PostgreSQL driver, only when the statement is executed, where the replay rule of
            // QoreJdbcStatement::execIntern() applies. The connection is replaced once: an error on the new
            // connection, or when releasing the lost connection's statements, is raised after the lost connection's
            // error, and the caller discards the connection.
            ExceptionSink check_xsink;
            if (ds->activeTransaction() || !isConnectionLost(env, throwable, &check_xsink)) {
                // the original error, then the error of the validity check, if it failed
                e.restore(throwable);
                e.convert(xsink);
                xsink->assimilate(check_xsink);
                return -1;
            }
            assert(!check_xsink);
            ExceptionSink reconnect_xsink;
            if (reconnectInTransaction(env, &reconnect_xsink)) {
                // the lost connection's error first, then the errors of the recovery
                e.restore(throwable);
                e.convert(xsink);
                xsink->assimilate(reconnect_xsink);
                return -1;
            }
            assert(transaction && !reconnect_xsink);
            return 0;
        }
        transaction = true;
        return 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcConnection::reconnectInTransaction(Env& env, ExceptionSink* xsink) {
    assert(!transaction);
    // release the JDBC handles of the datasource's statements, which belong to the lost connection, while the
    // driver-specific context data is still present; the statements keep their SQL and bind arguments and are
    // prepared again on the new connection when they are next executed
    ds->connectionLost(xsink);
    if (*xsink) {
        return -1;
    }
    try {
        close(env);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return -1;
    }
    // connect() starts the new connection in the transaction
    transaction = true;
    if (connect(env, xsink)) {
        transaction = false;
        return -1;
    }
    return 0;
}

int QoreJdbcConnection::endTransaction(bool do_commit, ExceptionSink* xsink) {
    assert(connection);
    if (!transaction) {
        return 0;
    }
    try {
        Env env;
        env.callVoidMethod(connection, do_commit ? Globals::methodConnectionCommit
            : Globals::methodConnectionRollback, nullptr);
        // Restore auto-commit only after a successful outcome. setAutoCommit(true) can itself
        // commit pending work, so it must never run after a failed commit or rollback.
        jvalue arg{};
        arg.z = true;
        env.callVoidMethod(connection, Globals::methodConnectionSetAutoCommit, &arg);
        transaction = false;
        return 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcConnection::commit(ExceptionSink* xsink) {
    return endTransaction(true, xsink);
}

int QoreJdbcConnection::rollback(ExceptionSink* xsink) {
    return endTransaction(false, xsink);
}

QoreValue QoreJdbcConnection::getServerVersion(ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        LocalReference<jobject> md = env.callObjectMethod(connection, Globals::methodConnectionGetMetaData, nullptr);
        if (!md) {
            xsink->raiseException("JDBC-METADATA-ERROR", "the connection returned no metadata");
            return QoreValue();
        }

        ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);

        LocalReference<jstring> str = env.callObjectMethod(md,
            Globals::methodDatabaseMetaDataGetDatabaseProductName, nullptr).as<jstring>();
        if (str) {
            Env::GetStringUtfChars desc(env, str);
            rv->setKeyValue("name", new QoreStringNode(desc.c_str()), xsink);
        }
        str = env.callObjectMethod(md,
            Globals::methodDatabaseMetaDataGetDatabaseProductVersion, nullptr).as<jstring>();
        if (str) {
            Env::GetStringUtfChars desc(env, str);
            rv->setKeyValue("version", new QoreStringNode(desc.c_str()), xsink);
        }
        jint i = env.callIntMethod(md,
            Globals::methodDatabaseMetaDataGetDatabaseMajorVersion, nullptr);
        rv->setKeyValue("major_version", i, xsink);
        i = env.callIntMethod(md,
            Globals::methodDatabaseMetaDataGetDatabaseMinorVersion, nullptr);
        rv->setKeyValue("minor_version", i, xsink);
        return rv.release();
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return *xsink ? -1 : 0;
}

QoreValue QoreJdbcConnection::getClientVersion(ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        LocalReference<jobject> md = env.callObjectMethod(connection, Globals::methodConnectionGetMetaData, nullptr);
        if (!md) {
            xsink->raiseException("JDBC-METADATA-ERROR", "the connection returned no metadata");
            return QoreValue();
        }

        ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);

        LocalReference<jstring> str = env.callObjectMethod(md,
            Globals::methodDatabaseMetaDataGetDriverName, nullptr).as<jstring>();
        if (str) {
            Env::GetStringUtfChars desc(env, str);
            rv->setKeyValue("name", new QoreStringNode(desc.c_str()), xsink);
        }
        str = env.callObjectMethod(md,
            Globals::methodDatabaseMetaDataGetDriverVersion, nullptr).as<jstring>();
        if (str) {
            Env::GetStringUtfChars desc(env, str);
            rv->setKeyValue("version", new QoreStringNode(desc.c_str()), xsink);
        }
        jint i = env.callIntMethod(md,
            Globals::methodDatabaseMetaDataGetDriverMajorVersion, nullptr);
        rv->setKeyValue("major_version", i, xsink);
        i = env.callIntMethod(md,
            Globals::methodDatabaseMetaDataGetDriverMinorVersion, nullptr);
        rv->setKeyValue("minor_version", i, xsink);
        return rv.release();
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return *xsink ? -1 : 0;
}

QoreStringNode* QoreJdbcConnection::getDriverRealName(ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        LocalReference<jobject> md = env.callObjectMethod(connection, Globals::methodConnectionGetMetaData, nullptr);
        if (!md) {
            xsink->raiseException("JDBC-METADATA-ERROR", "the connection returned no metadata");
            return nullptr;
        }
        LocalReference<jstring> str = env.callObjectMethod(md,
            Globals::methodDatabaseMetaDataGetDatabaseProductName, nullptr).as<jstring>();
        if (!str) {
            xsink->raiseException("JDBC-METADATA-ERROR", "Connection.getMetaData() returned no server product name");
            return nullptr;
        }
        Env::GetStringUtfChars desc(env, str);
        SimpleRefHolder<QoreStringNode> rv(new QoreStringNode(desc.c_str()));
        return rv.release();
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return nullptr;
}

QoreValue QoreJdbcConnection::select(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        bool result_set = stmt.exec(env, xsink, qstr, args);
        if (*xsink) {
            return QoreValue();
        }

        return stmt.getResults(env, result_set, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return QoreValue();
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
QoreColumnarResult* QoreJdbcConnection::selectColumnar(const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        bool result_set = stmt.exec(env, xsink, qstr, args);
        if (*xsink) {
            return nullptr;
        }

        // update counts are skipped; exactly one result set is required
        if (stmt.acquireOnlyResultSet(env, result_set, "JDBC-SELECT-COLUMNAR-ERROR",
            "SQL passed to selectColumnar()", xsink)) {
            return nullptr;
        }

        return stmt.getOutputColumnar(env, xsink, -1);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return nullptr;
}
#endif

QoreListNode* QoreJdbcConnection::selectRows(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    assert(connection);
    ValueHolder rv(xsink);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        bool result_set = stmt.exec(env, xsink, qstr, args);
        if (*xsink) {
            return nullptr;
        }

        // update counts are skipped; exactly one result set is required
        if (stmt.acquireOnlyResultSet(env, result_set, "JDBC-SELECT-ROWS-ERROR", "SQL passed to selectRows()",
            xsink)) {
            return nullptr;
        }

        return stmt.getOutputList(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return nullptr;
}

QoreHashNode* QoreJdbcConnection::selectRow(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    assert(connection);
    ValueHolder rv(xsink);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        bool result_set = stmt.exec(env, xsink, qstr, args);
        if (*xsink) {
            return nullptr;
        }

        // update counts are skipped; exactly one result set is required
        if (stmt.acquireOnlyResultSet(env, result_set, "JDBC-SELECT-ROW-ERROR", "SQL passed to selectRow()",
            xsink)) {
            return nullptr;
        }

        return stmt.getSingleRow(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return nullptr;
}

QoreValue QoreJdbcConnection::exec(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        bool result_set = stmt.exec(env, xsink, qstr, args);
        if (*xsink) {
            return QoreValue();
        }

        return stmt.getResults(env, result_set, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return QoreValue();
}

QoreValue QoreJdbcConnection::execRaw(const QoreString* qstr, ExceptionSink* xsink) {
    assert(connection);
    try {
        // Env's constructor raises a pending cancellation as an exception
        Env env;
        QoreJdbcStatement stmt(xsink, this);

        // a plain java.sql.Statement executes the SQL as given (no placeholders are parsed), and its effects last for
        // the session: SQL Server runs a prepared statement with sp_prepexec, which drops the #temp tables it creates
        bool result_set = stmt.execRaw(env, xsink, *qstr);
        if (*xsink) {
            return QoreValue();
        }

        return stmt.getResults(env, result_set, xsink);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    assert(*xsink);
    return QoreValue();
}
}
