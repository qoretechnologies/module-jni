/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcStatement.h

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

#ifndef _QORE_JNI_QOREJDBCSTATEMENT_H

#define _QORE_JNI_QOREJDBCSTATEMENT_H

#include <qore/Qore.h>
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
#include <qore/QoreColumnarResult.h>
#endif

#include "QoreJdbcConnection.h"
#include "Env.h"
#include "GlobalReference.h"
#include "LocalReference.h"

#include <vector>
#include <string>

namespace jni {

struct QoreJdbcColumn {
    //! Column name in the DB
    std::string name;

    //! Column name in the output query
    std::string qname;

    //! Strip trailing spaces from string values retrieved (CHAR columns)
    bool strip = false;

    //! JDBC column type from java.sql.Types
    jint ctype = 0;

    //! JDBC-native column type name
    std::string native_type;

    //! JDBC-reported precision
    jint precision = 0;

    //! JDBC-reported scale
    jint scale = 0;

    //! True if ResultSetMetaData reports nullable or unknown nullability
    bool nullable = true;

    //! Constructor
    DLLLOCAL QoreJdbcColumn(std::string&& name, std::string&& qname, jint ctype, std::string&& native_type,
        jint precision, jint scale, bool nullable);
};

// column vector
typedef std::vector<QoreJdbcColumn> cvec_t;

class QoreJdbcStatement {
public:
    DLLLOCAL QoreJdbcStatement(ExceptionSink* xsink, QoreJdbcConnection* conn) : conn(conn), params(xsink) {
    }

    DLLLOCAL virtual ~QoreJdbcStatement();

    DLLLOCAL bool exec(Env& env, ExceptionSink* xsink, const QoreString& qstr, const QoreListNode* args);

    //! Return how many rows were affected by the executed statement.
    /** @return count of affected rows; -1 in case the number is not available
    */
    DLLLOCAL int rowsAffected(Env& env);

    DLLLOCAL bool next(Env& env);

    //! Acquires the result set
    DLLLOCAL int acquireResultSet(Env& env, ExceptionSink* xsink);

    //! Get result hash
    /** @param enc the JNI environment variable
        @param xsink exception sink
        @param empty_hash_if_nothing whether to return empty hash or empty hash with column names when no rows
        available
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return hash of result column lists
    */
    DLLLOCAL QoreHashNode* getOutputHash(Env& env, ExceptionSink* xsink, bool empty_hash_if_nothing,
            int max_rows = -1);

    //! Get a reuslt list as a list of hashes
    /** @param enc the JNI environment variable
        @param xsink exception sink
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return list of row hashes
    */
    DLLLOCAL QoreListNode* getOutputList(Env& env, ExceptionSink* xsink, int max_rows = -1);

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    //! Get result columns as a ColumnarResult
    /** @param env the JNI environment variable
        @param xsink exception sink
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return columnar result
    */
    DLLLOCAL QoreColumnarResult* getOutputColumnar(Env& env, ExceptionSink* xsink, int max_rows = -1);
#endif

    //! Get one result row as a hash
    /** @param enc the JNI environment variable
        @param xsink exception sink

        @return one result-set row
    */
    DLLLOCAL QoreHashNode* getSingleRow(Env& env, ExceptionSink* xsink);

    //! Closes the statement
    /**
    */
    DLLLOCAL void close(Env& env);

protected:
    //! Possible comment types; used in the parse() method
    enum SQLCommentType {
        ESCT_NONE = 0,
        ESCT_LINE,
        ESCT_BLOCK,
    };

    //! Connection
    QoreJdbcConnection* conn;

    //! PreparedStatement object
    GlobalReference<jobject> stmt;

    //! Count of bind parameters for the SQL command
    size_t bind_size = 0;

    //! The size of any array bind
    size_t array_bind_size = 0;

    //! Parameters to be used in the statement
    ReferenceHolder<QoreListNode> params;

    //! Column metadata from result sets
    cvec_t cvec;

    //! Any active result set
    LocalReference<jobject> rs;

    //! Batch execute flag
    bool do_batch_execute = false;

    DLLLOCAL void prepareAndBindStatement(Env& env, ExceptionSink* xsink, const QoreString& str);

    DLLLOCAL void prepareStatement(Env& env, const QoreString& str);

    //! Reset statement
    /**
        This function is called when the DB connection is lost while executing SQL so that
        the current state can be freed while the driver-specific context data is still present

        This call resets the query
    */
    DLLLOCAL void reset(Env& env);

    //! Parse a Qore-style SQL statement
    /** @param str Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int parse(QoreString* str, const QoreListNode* args, ExceptionSink* xsink);

    DLLLOCAL int reconnectLostConnection(Env& env, ExceptionSink* xsink);

    DLLLOCAL int bindQueryArguments(Env& env, ExceptionSink* xsink);

    DLLLOCAL bool execIntern(Env& env, const QoreString& sql, ExceptionSink* xsink);

#if 0
    DLLLOCAL const char* getArrayBindType(const QoreListNode* l) const;
    int bindInternArrayNative(Env& env, const QoreListNode* args, ExceptionSink* xsink);
#endif

    //! Return size of arrays in the passed arguments
    /** @param args SQL parameters

        @return parameter array size
    */
    DLLLOCAL size_t findArraySizeOfArgs(const QoreListNode* args) const;

    //! Return whether the passed arguments have arrays
    DLLLOCAL bool hasArrayBind() {
        array_bind_size = findArraySizeOfArgs(*params);
        return array_bind_size > 0;
    }

    //! Bind a single value argument
    /** @param enc the JNI environment variable
        @param column JDBC column number, starting from 1
        @param arg single value parameter
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int bindParamSingleValue(Env& env, int column, QoreValue arg, ExceptionSink* xsink);

    //! Bind a single value argument as an array
    /** @param enc the JNI environment variable
        @param column JDBC column number, starting from 1
        @param arg single value parameter
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int bindParamArraySingleValue(Env& env, int column, QoreValue arg, ExceptionSink* xsink);

    //! Bind a simple list of SQL parameters
    /** @param enc the JNI environment variable
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int bindIntern(Env& env, const QoreListNode* args, ExceptionSink* xsink);

    //! Bind a list of arrays of SQL parameters
    /** @param enc the JNI environment variable
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int bindInternArray(Env& env, const QoreListNode* args, ExceptionSink* xsink);

    int bindInternArrayBatch(Env& env, const QoreListNode* args, ExceptionSink* xsink);

    //! Describe result set
    DLLLOCAL int describeResultSet(Env& env, ExceptionSink* xsink);

    DLLLOCAL void populateOutputHash(QoreHashNode& h, ExceptionSink* xsink);

    DLLLOCAL QoreHashNode* getOutputHashIntern(Env& env, ExceptionSink* xsink, bool empty_hash_if_nothing,
            int max_rows = -1);

    DLLLOCAL QoreListNode* getOutputListIntern(Env& env, ExceptionSink* xsink, int max_rows = -1);

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    DLLLOCAL QoreColumnarResult* getOutputColumnarIntern(Env& env, ExceptionSink* xsink, int max_rows = -1);
#endif

    //! Get a column's value and return a Qore value for it
    /** @param enc the JNI environment variable
        @param rs the ResultSet object
        @param column column number
        @param rcol result column metadata
        @param xsink exception sink

        @return result value
    */
    DLLLOCAL QoreValue getColumnValue(Env& env, int column, QoreJdbcColumn& col, ExceptionSink* xsink);

    //! Get one result row as a hash
    /** @param enc the JNI environment variable
        @param xsink exception sink

        @return one result-set row
    */
    DLLLOCAL QoreHashNode* getSingleRowIntern(Env& env, ExceptionSink* xsink);
};

class JavaExceptionRethrowHelper {
public:
    DLLLOCAL JavaExceptionRethrowHelper() {
        if ((ex = env->ExceptionOccurred())) {
            env->ExceptionClear();
        }
    }

    DLLLOCAL ~JavaExceptionRethrowHelper() {
        if (ex) {
            env->Throw(ex.release());
        }
    }

private:
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = Jvm::getEnv();

    LocalReference<jthrowable> ex;
};

}
#endif
