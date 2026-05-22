/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcPreparedStatement.cpp

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

#include "QoreJdbcPreparedStatement.h"

namespace jni {

QoreJdbcPreparedStatement::QoreJdbcPreparedStatement(ExceptionSink* xsink, QoreJdbcConnection* c) :
        QoreJdbcStatement(xsink, c) {
}

QoreJdbcPreparedStatement::~QoreJdbcPreparedStatement() {
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
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcPreparedStatement::exec(ExceptionSink* xsink) {
    try {
        Env env;
        if (bindQueryArguments(env, xsink)) {
            assert(*xsink);
            return -1;
        }

        if (execIntern(env, sql, xsink)
            && (acquireResultSet(env, xsink) || describeResultSet(env, xsink))) {
                return -1;
        }
        return *xsink ? -1 : 0;
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return -1;
}

int QoreJdbcPreparedStatement::bind(const QoreListNode& args, ExceptionSink* xsink) {
    try {
        assignBindArgs(xsink, args.listRefSelf());
        return *xsink ? -1 : 0;
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return -1;
}

QoreHashNode* QoreJdbcPreparedStatement::getOutputHash(ExceptionSink* xsink, bool empty_hash_if_nothing,
        int max_rows) {
    try {
        Env env;
        return getOutputHashIntern(env, xsink, empty_hash_if_nothing, max_rows);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreHashNode* QoreJdbcPreparedStatement::fetchRow(ExceptionSink* xsink) {
    try {
        Env env;
        return getSingleRowIntern(env, xsink);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreListNode* QoreJdbcPreparedStatement::fetchRows(int max_rows, ExceptionSink* xsink) {
    try {
        Env env;
        return getOutputListIntern(env, xsink, max_rows);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return nullptr;
}

QoreHashNode* QoreJdbcPreparedStatement::fetchColumns(int max_rows, ExceptionSink* xsink) {
    try {
        Env env;
        return getOutputHashIntern(env, xsink, true, max_rows);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return nullptr;
}

#ifdef QORE_JNI_HAVE_COLUMNAR_RESULT_V2
QoreColumnarResult* QoreJdbcPreparedStatement::fetchColumnar(int rows, ExceptionSink* xsink) {
    try {
        Env env;
        return getOutputColumnarIntern(env, xsink, rows);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return nullptr;
}
#endif

bool QoreJdbcPreparedStatement::next(ExceptionSink* xsink) {
    try {
        Env env;
        return QoreJdbcStatement::next(env);
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return false;
}

int QoreJdbcPreparedStatement::clear(ExceptionSink* xsink) {
    try {
        //printd(5, "QoreJdbcPreparedStatement::clear() %p\n", this);
        assignBindArgs(xsink, nullptr);
        sql.clear();
        Env env;
        reset(env);
        return *xsink ? -1 : 0;
    } catch (JavaException& e) {
        e.convert(xsink);
    }
    return -1;
}
}
