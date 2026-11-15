/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcConnection.h

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

#ifndef _QORE_JNI_QOREJDBCCONNECTION_H

#define _QORE_JNI_QOREJDBCCONNECTION_H

#include <qore/Qore.h>
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
#include <qore/QoreColumnarResult.h>
#endif

#include "GlobalReference.h"
#include "QoreJniClassMap.h"
#include "JavaToQore.h"
#include "QoreJdbcDriver.h"
#include "QoreJdbcSandbox.h"

#include <string>

namespace jni {

#if 0
//! Known DB types
enum DbType {
    DBT_UNKNOWN = 0,
    DBT_ORACLE,
};
#endif

class QoreJdbcConnection {
public:
    DLLLOCAL QoreJdbcConnection(Datasource* d, ExceptionSink* xsink);
    DLLLOCAL ~QoreJdbcConnection();

    DLLLOCAL int reconnect(Env& env, ExceptionSink* xsink);

    DLLLOCAL jobject getConnectionObject() const {
        assert(connection);
        return connection;
    }

    //! Returns true if the JDBC connection is in a transaction (auto-commit disabled)
    DLLLOCAL bool inTransaction() const {
        return transaction;
    }

    //! Returns true if an error raised by a call on the connection means that the connection to the server was lost
    /** @param env the JNI environment
        @param error the Java exception raised by the call; no Java exception may be pending
        @param check_xsink receives the error of the validity check if the check itself fails; the caller raises it
        after \a error

        @return true if \a error is an \c SQLException and the connection is no longer valid
        (\c Connection.isValid() with a 1 second timeout); false if the validity check itself fails, as the connection
        is then not known to be lost
    */
    DLLLOCAL bool isConnectionLost(Env& env, jthrowable error, ExceptionSink* check_xsink);

    //! Set an option for the connection.
    /** @param opt option name
        @param val option value to use
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int setOption(const char* opt, const QoreValue val, ExceptionSink* xsink);

    //! Get the current value of an option of the connection.
    /** @param opt option name

        @return option's value
    */
    DLLLOCAL QoreValue getOption(const char* opt);

    DLLLOCAL int close(Env& env);

    //! Start a JDBC transaction; ordinary reads outside a transaction use auto-commit.
    /** If the connection is found lost when the transaction is started, it is re-established and the transaction is
        started on the new connection; see the implementation for the rule.
    */
    DLLLOCAL int beginTransaction(ExceptionSink* xsink);

    DLLLOCAL int commit(ExceptionSink* xsink);
    DLLLOCAL int rollback(ExceptionSink* xsink);

    DLLLOCAL QoreValue getServerVersion(ExceptionSink* xsink);
    DLLLOCAL QoreValue getClientVersion(ExceptionSink* xsink);
    DLLLOCAL QoreStringNode* getDriverRealName(ExceptionSink* xsink);

    //! Execute a Qore-style SQL statement and return all its results
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return the results; see QoreJdbcStatement::getResults()
    */
    DLLLOCAL QoreValue select(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    //! Select multiple rows from the database as a columnar result.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return columnar result of the only result set; update counts are skipped
    */
    DLLLOCAL QoreColumnarResult* selectColumnar(const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink);
#endif

    //! Select multiple rows from the database.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return a list of row hashes of the only result set; update counts are skipped
    */
    DLLLOCAL QoreListNode* selectRows(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Select one row from the database.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return the only row hash of the only result set; update counts are skipped
    */
    DLLLOCAL QoreHashNode* selectRow(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Execute a Qore-style SQL statement with arguments.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return the results; see QoreJdbcStatement::getResults()
    */
    DLLLOCAL QoreValue exec(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Execute a raw SQL statement.
    /** @param qstr SQL statement
        @param xsink exception sink

        @return the results; see QoreJdbcStatement::getResults()
    */
    DLLLOCAL QoreValue execRaw(const QoreString* qstr, ExceptionSink* xsink);

    DLLLOCAL QoreProgram* getProgram() const {
        return pgm;
    }

    DLLLOCAL Datasource* getDatasource() {
        return ds;
    }

    DLLLOCAL const Datasource* getDatasource() const {
        return ds;
    }

    DLLLOCAL JniExternalProgramData* getQoreJniContext() const {
        return jpc;
    }

    //! Returns the most bytes or characters read from a large object of an unknown length
    DLLLOCAL int64 getLobMaxSize() const {
        return lob_max_size;
    }

    DLLLOCAL NumericOption getNumericOption() const {
        return numeric;
    }

    //! Returns true if the connection is restricted: it was made through a connection guard (qore#5786)
    DLLLOCAL bool isGuarded() const {
        return static_cast<bool>(guard);
    }

    //! Checks SQL before it is given to the driver of a restricted connection (SingleStore: LOAD DATA LOCAL INFILE)
    /** @throw jni::Exception (JNI-SANDBOX-ERROR) the SQL is not allowed
    */
    /** A connection without a guard (trusted code opened it) is checked by the profile of its URL when restricted code
        uses it (qore#6142, jdbc_statement_restricted()).
    */
    DLLLOCAL void checkSql(Env& env, const char* sql) const;

#if 0
    DLLLOCAL DbType getDbType() const {
        return dbtype;
    }

    DLLLOCAL bool areArraysSupported(Env& env);

    DLLLOCAL jmethodID getOracleCreateArrayOfMethod() const {
        assert(dbtype == DBT_ORACLE);
        assert(methodOracleConnectionCreateOracleArray);
        return methodOracleConnectionCreateOracleArray;
    }
#endif

private:
    //! Qore Program context
    QoreProgram* pgm = qore_get_call_program_context();
    //! JNI program context
    JniExternalProgramData* jpc = nullptr;

    //! Qore datasource.
    Datasource* ds = nullptr;

    //! Connection object
    GlobalReference<jobject> connection;

    //! the connection guard of a restricted connection (qore#5786); closed when the connection is closed
    JdbcGuardHolder guard;

    //! a connection object that was restricted once stays restricted, also when it reconnects (qore#5786)
    JdbcStickyRestriction sticky;

    //! the URL of the current connection
    std::string connected_url;

    //! JDBC transaction mode; preserved when reconnecting before the first statement.
    bool transaction = false;

    DLLLOCAL int endTransaction(bool commit, ExceptionSink* xsink);

    //! Replaces a connection lost when the transaction was started with a new connection in the transaction
    /** @param env the JNI environment; no Java exception may be pending
        @param xsink exception sink for the errors of releasing the lost connection and of the reconnection

        @return 0 if the new connection is in the transaction, -1 if an error was raised in \a xsink
    */
    DLLLOCAL int reconnectInTransaction(Env& env, ExceptionSink* xsink);

    //! Classpath value
    std::string classpath;

    //! Overridden db URL value
    std::string db;

    //! Option for numeric values
    NumericOption numeric = ENO_OPTIMAL;

    //! the most bytes or characters read from a large object of an unknown length
    int64 lob_max_size = JDBC_LOB_MAX_SIZE_DEFAULT;

#if 0
    //! DB type
    DbType dbtype = DBT_UNKNOWN;

    //! Array support types
    enum DriverArraySupport {
        DAS_UNKNOWN = 0,
        DAS_SUPPORTED,
        DAS_NOT_SUPPORTED,
    };

    //! JDBC driver array support
    DriverArraySupport array_support = DAS_UNKNOWN;

    //! Array OracleConnection.createOracleArray(String, Object[]) method ID
    jmethodID methodOracleConnectionCreateOracleArray = 0;

    //! Mutex for atomic operations
    QoreThreadLock m;
#endif

    DLLLOCAL int connect(Env& env, ExceptionSink* xsink);

    //! The early check, before the options are processed: refuses a restricted connection that cannot be made
    /** Raises NETWORK-ACCESS-DENIED and returns -1 if the connection is restricted (jdbc_connect_decision()) and the
        URL has no audited driver profile (qore#5786), or for every restricted connection without the per-driver
        enforcement (qore#5747), so that the classpath option does not load jars for a connection that is refused
    */
    DLLLOCAL int checkSandbox(ExceptionSink* xsink);

    //! Prepares the connection: unrestricted, or restricted with its guard (qore#5786)
    /** @param env the JNI environment
        @param url the JDBC URL
        @param holder receives the prepared restricted connection; empty if the connection is unrestricted
        @param xsink receives the exception of a refusal

        @return 0 for success, -1 if an exception was raised
    */
    DLLLOCAL int prepareConnect(Env& env, const char* url, JdbcGuardHolder& holder, ExceptionSink* xsink);

    //! The end of a connection attempt: closes the connection if the thread was cancelled
    DLLLOCAL int connectDone(Env& env, ExceptionSink* xsink);

    //! Parse options passed through the Datasource
    /** @param xsink exception sink

        @return 0 for OK, -1 for error
     */
    DLLLOCAL int parseOptions(ExceptionSink* xsink);

    QoreJdbcConnection(const QoreJdbcConnection&) = delete;
    QoreJdbcConnection& operator=(const QoreJdbcConnection&) = delete;
};

}

#endif
