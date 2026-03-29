/*  JdbcQueryExecutor.java Copyright 2026 Qore Technologies, s.r.o.

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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.qore.jni.Hash;

/**
 * Wraps a {@link java.sql.Connection} for executing parameterized queries and
 * returning results as {@link Hash} objects suitable for Qore consumption.
 * <p>
 * Implements {@link Closeable} but does <b>not</b> close the underlying
 * connection — that is owned by the caller.
 */
public class JdbcQueryExecutor implements Closeable {
    private final Connection connection;

    /**
     * Creates a new query executor for the given connection.
     *
     * @param connection the JDBC connection to use (caller retains ownership)
     */
    public JdbcQueryExecutor(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes a SELECT query and returns all rows as a list of Hash.
     *
     * @param sql the SQL SELECT statement
     * @return list of Hash entries, one per row
     * @throws SQLException if a database access error occurs
     */
    public ArrayList<Hash> executeQuery(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            ArrayList<Hash> list = new ArrayList<>();
            while (rs.next()) {
                list.add(resultSetRowToHash(rs, meta));
            }
            return list;
        }
    }

    /**
     * Executes an INSERT, UPDATE, or DELETE statement.
     *
     * @param sql the SQL DML statement
     * @return the number of affected rows
     * @throws SQLException if a database access error occurs
     */
    public int executeUpdate(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * Executes a parameterized SELECT query and returns all rows as a list of Hash.
     *
     * @param sql    the SQL SELECT statement with {@code ?} placeholders
     * @param params the parameter values to bind
     * @return list of Hash entries, one per row
     * @throws SQLException if a database access error occurs
     */
    public ArrayList<Hash> executeQueryWithParams(String sql, Object[] params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                ArrayList<Hash> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(resultSetRowToHash(rs, meta));
                }
                return list;
            }
        }
    }

    /**
     * Executes a parameterized INSERT, UPDATE, or DELETE statement.
     *
     * @param sql    the SQL DML statement with {@code ?} placeholders
     * @param params the parameter values to bind
     * @return the number of affected rows
     * @throws SQLException if a database access error occurs
     */
    public int executeUpdateWithParams(String sql, Object[] params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParams(ps, params);
            return ps.executeUpdate();
        }
    }

    /**
     * Sets parameters on a {@link PreparedStatement} based on the runtime type
     * of each parameter value.
     *
     * @param ps     the prepared statement
     * @param params the parameter values to bind
     * @throws SQLException if a database access error occurs
     */
    private void setParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            int idx = i + 1;
            Object param = params[i];
            if (param == null) {
                ps.setNull(idx, Types.NULL);
            } else if (param instanceof String) {
                ps.setString(idx, (String) param);
            } else if (param instanceof Integer) {
                ps.setInt(idx, (Integer) param);
            } else if (param instanceof Long) {
                ps.setLong(idx, (Long) param);
            } else if (param instanceof Float) {
                ps.setFloat(idx, (Float) param);
            } else if (param instanceof Double) {
                ps.setDouble(idx, (Double) param);
            } else if (param instanceof BigDecimal) {
                ps.setBigDecimal(idx, (BigDecimal) param);
            } else if (param instanceof Boolean) {
                ps.setBoolean(idx, (Boolean) param);
            } else if (param instanceof byte[]) {
                ps.setBytes(idx, (byte[]) param);
            } else if (param instanceof java.sql.Date) {
                ps.setDate(idx, (java.sql.Date) param);
            } else if (param instanceof java.sql.Timestamp) {
                ps.setTimestamp(idx, (java.sql.Timestamp) param);
            } else {
                ps.setObject(idx, param);
            }
        }
    }

    /**
     * Converts the current row of a {@link ResultSet} to a {@link Hash},
     * mapping SQL types to appropriate Java types.
     *
     * @param rs   the result set positioned at the current row
     * @param meta the result-set metadata
     * @return a Hash with column names as keys
     * @throws SQLException if a database access error occurs
     */
    private Hash resultSetRowToHash(ResultSet rs, ResultSetMetaData meta) throws SQLException {
        Hash h = new Hash();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnLabel(i);
            int sqlType = meta.getColumnType(i);
            Object value;
            switch (sqlType) {
                case Types.VARCHAR:
                case Types.CHAR:
                case Types.LONGVARCHAR:
                case Types.NVARCHAR:
                case Types.NCHAR:
                case Types.CLOB:
                case Types.NCLOB:
                    value = rs.getString(i);
                    break;
                case Types.INTEGER:
                case Types.SMALLINT:
                case Types.TINYINT:
                    value = rs.getObject(i) != null ? rs.getInt(i) : null;
                    break;
                case Types.BIGINT:
                    value = rs.getObject(i) != null ? rs.getLong(i) : null;
                    break;
                case Types.FLOAT:
                case Types.REAL:
                    value = rs.getObject(i) != null ? rs.getFloat(i) : null;
                    break;
                case Types.DOUBLE:
                    value = rs.getObject(i) != null ? rs.getDouble(i) : null;
                    break;
                case Types.DECIMAL:
                case Types.NUMERIC:
                    value = rs.getBigDecimal(i);
                    break;
                case Types.BOOLEAN:
                case Types.BIT:
                    value = rs.getObject(i) != null ? rs.getBoolean(i) : null;
                    break;
                case Types.DATE:
                    java.sql.Date sqlDate = rs.getDate(i);
                    value = sqlDate != null ? sqlDate.toLocalDate() : null;
                    break;
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    java.sql.Timestamp ts = rs.getTimestamp(i);
                    value = ts != null
                        ? ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault())
                        : null;
                    break;
                case Types.BLOB:
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    value = rs.getBytes(i);
                    break;
                case Types.NULL:
                    value = null;
                    break;
                default:
                    value = rs.getString(i);
                    break;
            }
            h.put(columnName, value);
        }
        return h;
    }

    /**
     * No-op close — the connection is owned by the caller.
     */
    @Override
    public void close() throws IOException {
        // Connection is owned by the caller; nothing to release here.
    }
}
