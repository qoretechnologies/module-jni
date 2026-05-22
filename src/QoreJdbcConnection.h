/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcConnection.h

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

#ifndef _QORE_JNI_QOREJDBCCONNECTION_H

#define _QORE_JNI_QOREJDBCCONNECTION_H

#include <qore/Qore.h>
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
#include <qore/QoreColumnarResult.h>
#endif

#include "GlobalReference.h"
#include "QoreJniClassMap.h"
#include "JavaToQore.h"

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

    DLLLOCAL int commit(ExceptionSink* xsink);
    DLLLOCAL int rollback(ExceptionSink* xsink);

    DLLLOCAL QoreValue getServerVersion(ExceptionSink* xsink);
    DLLLOCAL QoreValue getClientVersion(ExceptionSink* xsink);
    DLLLOCAL QoreStringNode* getDriverRealName(ExceptionSink* xsink);

    DLLLOCAL QoreValue select(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    //! Select multiple rows from the database as a columnar result.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return columnar result
    */
    DLLLOCAL QoreColumnarResult* selectColumnar(const QoreString* qstr, const QoreListNode* args,
        ExceptionSink* xsink);
#endif

    //! Select multiple rows from the database.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return a list of row hashes
    */
    DLLLOCAL QoreListNode* selectRows(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Select one row from the database.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return one row hash
    */
    DLLLOCAL QoreHashNode* selectRow(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Execute a Qore-style SQL statement with arguments.
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL QoreValue exec(const QoreString* qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Execute a raw SQL statement.
    /** @param qstr SQL statement
        @param xsink exception sink

        @return 0 for OK, -1 for error
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

    DLLLOCAL NumericOption getNumericOption() const {
        return numeric;
    }

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

    //! Classpath value
    std::string classpath;

    //! Overridden db URL value
    std::string db;

    //! Option for numeric values
    NumericOption numeric = ENO_OPTIMAL;

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
