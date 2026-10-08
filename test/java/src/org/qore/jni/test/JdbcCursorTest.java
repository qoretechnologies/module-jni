// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.util.ArrayList;
import java.util.HashMap;
import org.qore.jni.QoreObject;
import org.qore.lang.AbstractSQLStatement;
import org.qore.lang.DatasourcePool;

/** Each wrapper operation creates a separate JNI local-reference frame. */
@SuppressWarnings("deprecation")
public final class JdbcCursorTest {
    public static ArrayList<HashMap<String, Object>> readRows(QoreObject pool, String sql) throws Throwable {
        AbstractSQLStatement statement = new DatasourcePool(pool).getSQLStatement();
        try {
            statement.prepare(sql);
            statement.exec();
            // The result set must survive return from exec() and a Java collection.
            System.gc();
            ArrayList<HashMap<String, Object>> rows = new ArrayList<>();
            while (statement.next()) {
                rows.add(statement.fetchRow());
            }
            return rows;
        } finally {
            statement.rollback();
        }
    }
}
