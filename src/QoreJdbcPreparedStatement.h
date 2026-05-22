/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcPreparedStatement.h

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

#ifndef _QORE_JNI_QOREJDBCPREPAREDSTATEMENT_H

#define _QORE_JNI_QOREJDBCPREPAREDSTATEMENT_H

#include <qore/Qore.h>
#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
#include <qore/QoreColumnarResult.h>
#endif

#include "QoreJdbcStatement.h"

#include "QoreJdbcPreparedStatement.h"

namespace jni {

//! A class representing JDBC prepared statement.
class QoreJdbcPreparedStatement : public QoreJdbcStatement {
public:
    //! Constructor
    DLLLOCAL QoreJdbcPreparedStatement(ExceptionSink* xsink, QoreJdbcConnection* c);

    //! Destructor
    DLLLOCAL virtual ~QoreJdbcPreparedStatement();

    //! Prepare an JDBC SQL statement
    /** @param qstr Qore-style SQL statement
        @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int prepare(const QoreString& qstr, const QoreListNode* args, ExceptionSink* xsink);

    //! Execute the prepared statement
    /** @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int exec(ExceptionSink* xsink);

    //! Bind the passed arguments to the statement
    /** @param args SQL parameters
        @param xsink exception sink

        @return 0 for OK, -1 for error
    */
    DLLLOCAL int bind(const QoreListNode& args, ExceptionSink* xsink);

    DLLLOCAL QoreHashNode* getOutputHash(ExceptionSink* xsink, bool empty_hash_if_nothing, int max_rows = -1);

    //! Get one result row.
    /** @param xsink exception sink

        @return one result-set row
    */
    DLLLOCAL QoreHashNode* fetchRow(ExceptionSink* xsink);

    //! Get result rows (get result list)
    /** @param xsink exception sink
        @param rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return list of row hashes
    */
    DLLLOCAL QoreListNode* fetchRows(int max_rows, ExceptionSink* xsink);

    //! Get result columns (get result hash)
    /** @param xsink exception sink
        @param rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return hash of result column lists
    */
    DLLLOCAL QoreHashNode* fetchColumns(int max_rows, ExceptionSink* xsink);

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
    //! Get result columns as a ColumnarResult
    /** @param xsink exception sink
        @param rows maximum count of rows to return; if <= 0 the count of returned rows is not limited

        @return columnar result
    */
    DLLLOCAL QoreColumnarResult* fetchColumnar(int rows, ExceptionSink* xsink);
#endif

    //! Retrieve the next result-set row
    /** @param xsink exception sink

        @return true if a row was successfully retrieved, false if not (no more rows available) or an error occured
    */
    DLLLOCAL bool next(ExceptionSink* xsink);

    //! Clears the object
    DLLLOCAL int clear(ExceptionSink* xsink);

private:
    //! Prepared / parsed SQL string
    QoreString sql;

    //! Assign bind args
    DLLLOCAL void assignBindArgs(ExceptionSink* xsink, QoreListNode* args) {
        ReferenceHolder<QoreListNode> tmp(params.swap(args), xsink);
    }

    //! Disabled copy constructor.
    DLLLOCAL QoreJdbcPreparedStatement(const QoreJdbcStatement& s) = delete;

    //! Disabled assignment operator.
    DLLLOCAL QoreJdbcPreparedStatement& operator=(const QoreJdbcStatement& s) = delete;
};

}

#endif
