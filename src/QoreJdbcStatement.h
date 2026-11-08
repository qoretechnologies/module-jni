/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcStatement.h

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

    //! The class of the values that ResultSet.getObject() returns, if the driver reports it
    std::string class_name;

    //! How the values of the column are read from the result set
    /** ResultSet.getObject() returns a vendor class for some types, which is not converted to a Qore value; the
        values of these types are read as standard Java values
    */
    enum class Fetch {
        //! ResultSet.getObject(int)
        Object,
        //! ResultSet.getTimestamp(int), converted from the time since the epoch: Oracle's TIMESTAMP, TIMESTAMP WITH
        //! LOCAL TIME ZONE and DATE
        Timestamp,
        //! ResultSet.getObject(int, OffsetDateTime.class): Oracle's TIMESTAMP WITH TIME ZONE; with a driver without
        //! it, the string of the value, which has its offset
        OffsetDateTime,
        //! ResultSet.getString(int): ROWID
        String,
        //! ResultSet.getObject(int, LocalTime.class): TIME, which java.sql.Time gives without its fraction
        LocalTime,
        //! ResultSet.getObject(int, OffsetTime.class): TIME WITH TIME ZONE
        OffsetTime,
        //! a BLOB column: a java.sql.Blob is read in chunks and freed; any other value is converted
        Blob,
        //! a CLOB or NCLOB column: a java.sql.Clob is read in chunks and freed; any other value is converted
        Clob,
    };

    //! How the values of the column are read
    Fetch fetch = Fetch::Object;

    //! True if the driver does not support ResultSet.getObject(int, Class) for the column, which is then read with
    //! the fallback of its Fetch mode
    bool no_get_object_as = false;

    //! Constructor
    DLLLOCAL QoreJdbcColumn(std::string&& name, std::string&& qname, jint ctype, std::string&& native_type,
        jint precision, jint scale, bool nullable, std::string&& class_name);
};

// column vector
typedef std::vector<QoreJdbcColumn> cvec_t;

class QoreJdbcStatement {
public:
    DLLLOCAL QoreJdbcStatement(ExceptionSink* xsink, QoreJdbcConnection* conn) : conn(conn), params(xsink), cleanup_xsink(xsink) {
    }

    DLLLOCAL virtual ~QoreJdbcStatement();

    DLLLOCAL bool exec(Env& env, ExceptionSink* xsink, const QoreString& qstr, const QoreListNode* args);

    //! Executes SQL without binding, as given, with a plain java.sql.Statement
    /** The SQL is not parsed for placeholders and is not prepared: a driver may run prepared SQL in a scope of its
        own (the SQL Server driver runs it with \c sp_prepexec, which drops a \c \#temp table and resets \c SET
        options when the statement ends), while the effects of SQL executed directly last for the session.

        @return true if the first result is a result set
    */
    DLLLOCAL bool execRaw(Env& env, ExceptionSink* xsink, const QoreString& qstr);

    //! Return how many rows were affected by the executed statement
    /** @return the total of the update counts reported for the statement (or for the batch of an array bind);
        -1 if no count was reported
    */
    DLLLOCAL int rowsAffected() const;

    //! Retrieves the next row of the current result set
    /** If the statement may return only this result set (see acquireOnlyResultSet()), then when the result set
        ends, the remaining results are read: their errors are raised, their update counts are added to
        rowsAffected(), and another result set raises an exception.

        @param env the JNI environment
        @param xsink exception sink; check it when false is returned

        @return true if a row is available, false at the end of the result set (or if an exception was raised)
    */
    DLLLOCAL bool next(Env& env, ExceptionSink* xsink);

    //! Acquires the current result set
    DLLLOCAL int acquireResultSet(Env& env, ExceptionSink* xsink);

    //! Returns every result of the executed statement (for exec(), execRaw() and select())
    /** A statement with a single result returns it as before: the result set as a hash of column lists, or the
        update count (-1 if the driver reported no result).

        A statement with several results returns them as module-sybase does: result sets and update counts
        greater than 0 are kept (counts of 0 are left out); if none is kept, NOTHING is returned; if one is kept,
        it is returned alone; otherwise a hash with keys \c query0, \c query1, ... is returned in result order.

        Errors raised by later statements are raised as exceptions.

        @param env the JNI environment
        @param result_set the value returned by exec(): true if the first result is a result set
        @param xsink exception sink

        @return the results as described above
    */
    DLLLOCAL QoreValue getResults(Env& env, bool result_set, ExceptionSink* xsink);

    //! Positions on the only result set of the executed statement
    /** Update counts before the result set are skipped (and added to rowsAffected()). When the result set is read
        to the end, the remaining results are read (see next()).

        @param env the JNI environment
        @param result_set the value returned by exec(): true if the first result is a result set
        @param err the exception code for a statement without a result set or with more than one
        @param desc the subject of the exception description (ex: \c "SQL passed to selectRows()")
        @param xsink exception sink

        @return 0 if positioned on the result set, 1 if the result set has no columns (no rows can be returned;
        the remaining results have been read), -1 if an exception was raised
    */
    DLLLOCAL int acquireOnlyResultSet(Env& env, bool result_set, const char* err, const char* desc,
            ExceptionSink* xsink);

    //! Get result hash
    /** Acquires the current result set and returns its rows

        @param env the JNI environment variable
        @param xsink exception sink
        @param empty_hash_if_nothing whether to return empty hash or empty hash with column names when no rows
        available
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return hash of result column lists
    */
    DLLLOCAL QoreHashNode* getOutputHash(Env& env, ExceptionSink* xsink, bool empty_hash_if_nothing,
            int max_rows = -1);

    //! Get the rows of the acquired result set as a list of hashes
    /** @param env the JNI environment variable
        @param xsink exception sink
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return list of row hashes
    */
    DLLLOCAL QoreListNode* getOutputList(Env& env, ExceptionSink* xsink, int max_rows = -1) {
        return getOutputListIntern(env, xsink, max_rows);
    }

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    //! Get the columns of the acquired result set as a ColumnarResult
    /** @param env the JNI environment variable
        @param xsink exception sink
        @param max_rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return columnar result
    */
    DLLLOCAL QoreColumnarResult* getOutputColumnar(Env& env, ExceptionSink* xsink, int max_rows = -1) {
        return getOutputColumnarIntern(env, xsink, max_rows);
    }
#endif

    //! Get the only row of the acquired result set as a hash
    /** @param env the JNI environment variable
        @param xsink exception sink

        @return the only row; NOTHING if there are no rows; an exception is raised for more than one row
    */
    DLLLOCAL QoreHashNode* getSingleRow(Env& env, ExceptionSink* xsink);

    //! Closes the statement
    /**
    */
    DLLLOCAL void close(Env& env, ExceptionSink* xsink);

protected:
    //! Possible comment types; used in the parse() method
    enum SQLCommentType {
        ESCT_NONE = 0,
        ESCT_LINE,
        ESCT_BLOCK,
    };

    //! Connection
    QoreJdbcConnection* conn;

    //! The java.sql.PreparedStatement, or, for execRaw(), the java.sql.Statement
    GlobalReference<jobject> stmt;

    //! True if the statement is executed by execRaw(), with a plain java.sql.Statement
    bool raw = false;

    //! The SQL of execRaw(); a plain java.sql.Statement receives its SQL when executed
    std::string raw_sql;

    //! Count of bind parameters for the SQL command
    size_t bind_size = 0;

    //! The size of any array bind
    size_t array_bind_size = 0;

    //! Parameters to be used in the statement
    ReferenceHolder<QoreListNode> params;

    //! Column metadata from result sets
    cvec_t cvec;

    //! Result sets can survive separate Java-to-Qore native calls; local JNI references cannot.
    GlobalReference<jobject> rs;

    //! Temporary statements use their caller's sink during destruction. Persistent prepared
    //! statements must close their handles explicitly with the current call's sink first.
    ExceptionSink* cleanup_xsink;

    //! Batch execute flag
    bool do_batch_execute = false;

    //! Total for the last batch, or -1 if the driver does not report exact counts.
    int batch_rows_affected = -1;

    //! Total of the update counts of the executed statement; -1 if none was reported
    int update_count_total = -1;

    //! The statement may return only the current result set (SQLStatement, selectRow(), selectRows(),
    //! selectColumnar()): the results after it are read when it ends
    bool only_result_set = false;

    //! The only result set was read to the end and the results after it were read
    bool results_done = false;

    //! Exception code for a second result set when only one is allowed
    const char* only_result_set_err = nullptr;

    //! Subject of the exception description for a second result set when only one is allowed
    const char* only_result_set_desc = nullptr;

    //! Moves to the next result of the executed statement; closes the current result set
    /** @return true if the next result is a result set; false if it is an update count or there are no more
        results (then getUpdateCount() returns -1)
    */
    DLLLOCAL bool moreResults(Env& env);

    //! Returns the update count of the current result
    /** @return the update count, -1 if the current result is a result set or there are no more results; an
        exception is raised for an invalid count
    */
    DLLLOCAL int getUpdateCount(Env& env, ExceptionSink* xsink);

    //! Adds an update count to the total
    DLLLOCAL int addUpdateCount(int count, ExceptionSink* xsink);

    //! Skips update counts, adding them to the total, until a result set or the end of the results
    /** @return true if positioned on a result set; false at the end of the results or if an exception was raised
    */
    DLLLOCAL bool skipUpdateCounts(Env& env, bool result_set, ExceptionSink* xsink);

    //! Allows only one result set; a second one raises \a err with "<desc> returned more than one result set"
    DLLLOCAL void setOnlyResultSet(const char* err, const char* desc) {
        only_result_set = true;
        only_result_set_err = err;
        only_result_set_desc = desc;
    }

    //! Reads the results after the only result set
    /** Errors of later statements are raised, update counts are added to the total, and a result set raises an
        exception.

        @return 0 for OK, -1 if an exception was raised
    */
    DLLLOCAL int finishResults(Env& env, ExceptionSink* xsink);

    DLLLOCAL void prepareAndBindStatement(Env& env, ExceptionSink* xsink, const QoreString& str);

    DLLLOCAL void prepareStatement(Env& env, const QoreString& str);

    //! Creates the plain java.sql.Statement of execRaw()
    DLLLOCAL void createStatement(Env& env);

    //! Reset statement
    /**
        This function is called when the DB connection is lost while executing SQL so that
        the current state can be freed while the driver-specific context data is still present

        This call resets the query
    */
    DLLLOCAL void reset(Env& env, ExceptionSink* xsink);

    //! Releases the JDBC statement and result set but keeps the SQL and bind arguments
    /** Used when the connection is lost: the statement is prepared again before it is next executed.
    */
    DLLLOCAL void freeHandles(Env& env, ExceptionSink* xsink);

    //! Parse a Qore-style SQL statement
    /** @param str Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int parse(QoreString* str, const QoreListNode* args, ExceptionSink* xsink);

    DLLLOCAL int reconnectLostConnection(Env& env, ExceptionSink* xsink);

    DLLLOCAL int bindQueryArguments(Env& env, ExceptionSink* xsink);

    //! Executes the prepared statement; reconnects a lost connection
    /** The statement is executed once more on the new connection only if the lost attempt cannot have taken
        effect (see the implementation for the rule).

        @return true if the first result is a result set
    */
    DLLLOCAL bool execIntern(Env& env, const QoreString& sql, ExceptionSink* xsink);

    //! Executes the prepared statement or batch once
    /** @return true if the first result is a result set
    */
    DLLLOCAL bool executeOnce(Env& env, ExceptionSink* xsink);

#if 0
    DLLLOCAL const char* getArrayBindType(const QoreListNode* l) const;
    int bindInternArrayNative(Env& env, const QoreListNode* args, ExceptionSink* xsink);
#endif

    //! Return whether the passed arguments have arrays
    DLLLOCAL bool hasArrayBind(ExceptionSink* xsink) {
        if (params) {
            ConstListIterator i(*params);
            size_t count = 0;
            while (i.next()) {
                if (++count % 100 == 0 && qore_check_cancel(xsink, "JDBC array bind detection")) {
                    return false;
                }
                if (i.getValue().getType() == NT_LIST) {
                    array_bind_size = i.getValue().get<const QoreListNode>()->size();
                    return true;
                }
            }
        }
        array_bind_size = 0;
        return false;
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

    //! Describes the columns of the current result set, or of the last one if every result has been read
    /** @return a hash keyed by column name, as the rows are returned, with a description hash for each column (see
        SQLStatement::describe()); an empty hash if the statement has no result set
    */
    DLLLOCAL QoreHashNode* describe(Env& env, ExceptionSink* xsink);

    //! Returns the type code of the %Qore values returned for a column
    DLLLOCAL qore_type_t getColumnTypeCode(Env& env, const QoreJdbcColumn& col);

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
        jthrowable pending = env->ExceptionOccurred();
        if (pending) {
            // LocalReference's debug assertion calls GetObjectRefType(), which is not valid
            // while an exception is pending. Clear it before adopting the local reference.
            env->ExceptionClear();
            ex = LocalReference<jthrowable>(pending);
        }
    }

    DLLLOCAL ~JavaExceptionRethrowHelper() {
        if (ex) {
            // Throw retains the pending exception; the temporary local handle still belongs to us.
            env->Throw(ex);
        }
    }

private:
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = Jvm::getEnv();

    LocalReference<jthrowable> ex;
};

}
#endif
