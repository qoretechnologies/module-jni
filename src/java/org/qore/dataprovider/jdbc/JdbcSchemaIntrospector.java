/*  JdbcSchemaIntrospector.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.dataprovider.jdbc;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;

import org.qore.jni.Hash;

/**
 * Uses {@link java.sql.DatabaseMetaData} to discover tables and columns
 * in a JDBC-connected database.
 * <p>
 * Implements {@link Closeable} but does <b>not</b> close the underlying
 * connection — that is owned by the caller.
 */
public class JdbcSchemaIntrospector implements Closeable {
    private final Connection connection;

    /**
     * Creates a new introspector for the given connection.
     *
     * @param connection the JDBC connection to introspect (caller retains ownership)
     */
    public JdbcSchemaIntrospector(Connection connection) {
        this.connection = connection;
    }

    /**
     * Returns a list of tables and views visible through the connection.
     *
     * @return list of Hash entries with table_name, table_type, catalog, schema
     * @throws SQLException if a database access error occurs
     */
    public ArrayList<Hash> getTables() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        ArrayList<Hash> list = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                Hash h = new Hash();
                h.put("table_name", rs.getString("TABLE_NAME"));
                h.put("table_type", rs.getString("TABLE_TYPE"));
                h.put("catalog", rs.getString("TABLE_CAT"));
                h.put("schema", rs.getString("TABLE_SCHEM"));
                list.add(h);
            }
        }
        return list;
    }

    /**
     * Returns column metadata for the specified table, including primary-key
     * information.
     *
     * @param tableName the table whose columns should be retrieved
     * @return list of Hash entries with column_name, data_type, type_name,
     *         column_size, nullable, is_primary_key
     * @throws SQLException if a database access error occurs
     */
    public ArrayList<Hash> getColumns(String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();

        // Collect primary-key column names first
        HashSet<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(null, null, tableName)) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        ArrayList<Hash> list = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                Hash h = new Hash();
                h.put("column_name", columnName);
                h.put("data_type", rs.getInt("DATA_TYPE"));
                h.put("type_name", rs.getString("TYPE_NAME"));
                h.put("column_size", rs.getInt("COLUMN_SIZE"));
                h.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                h.put("is_primary_key", pkColumns.contains(columnName));
                list.add(h);
            }
        }
        return list;
    }

    /**
     * Returns the primary-key columns for the specified table.
     *
     * @param tableName the table whose primary keys should be retrieved
     * @return list of Hash entries with column_name, key_seq, pk_name
     * @throws SQLException if a database access error occurs
     */
    public ArrayList<Hash> getPrimaryKeys(String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        ArrayList<Hash> list = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                Hash h = new Hash();
                h.put("column_name", rs.getString("COLUMN_NAME"));
                h.put("key_seq", rs.getShort("KEY_SEQ"));
                h.put("pk_name", rs.getString("PK_NAME"));
                list.add(h);
            }
        }
        return list;
    }

    /**
     * No-op close — the connection is owned by the caller.
     */
    @Override
    public void close() throws IOException {
        // Connection is owned by the caller; nothing to release here.
    }
}
