// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/** A JDBC driver for "jdbc:qore-utf8:" URLs that records the strings it is given and returns the configured
    strings: the metadata names, and a result set with a column for each label whose value is the string bound
    to the statement at the label's position (or null)
*/
public final class Utf8Driver implements java.sql.Driver {
    private static boolean installed;

    //! the URL, user and password of the last connection
    public static String url;
    public static String user;
    public static String password;
    //! the last SQL prepared or executed
    public static String sql;
    //! the strings bound to the last prepared statement, by position
    public static final List<String> bound = new ArrayList<>();
    //! the column labels of the result set
    public static String[] labels = {"value"};
    //! the metadata names
    public static String productName = "product";
    public static String productVersion = "1.0";
    public static String driverName = "driver";
    public static String driverVersion = "1.0";

    public static synchronized void install() throws SQLException {
        if (!installed) {
            DriverManager.registerDriver(new Utf8Driver());
            installed = true;
        }
        url = null;
        user = null;
        password = null;
        sql = null;
        bound.clear();
    }

    //! the UTF-16 units of the given recorded string ("url", "user", "password", "sql" or "bound0"...)
    public static synchronized int[] recorded(String which) {
        String str;
        switch (which) {
            case "url": str = url; break;
            case "user": str = user; break;
            case "password": str = password; break;
            case "sql": str = sql; break;
            default: str = bound.get(Integer.parseInt(which.substring(5))); break;
        }
        return str == null ? null : Utf8Boundary.units(str);
    }

    //! returns the default value of a method's return type, for the methods that the fixture does not implement
    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static <T> T proxy(Class<T> cls, java.lang.reflect.InvocationHandler handler) {
        return cls.cast(Proxy.newProxyInstance(Utf8Driver.class.getClassLoader(), new Class<?>[] {cls},
            handler));
    }

    private static DatabaseMetaData metaData() {
        return proxy(DatabaseMetaData.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getDatabaseProductName": return productName;
                case "getDatabaseProductVersion": return productVersion;
                case "getDriverName": return driverName;
                case "getDriverVersion": return driverVersion;
                case "getDatabaseMajorVersion":
                case "getDriverMajorVersion": return 1;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == args[0];
                default: return defaultValue(method);
            }
        });
    }

    // the result set of a statement: one row with a column for each label
    private static ResultSet resultSet(List<String> values) {
        ResultSetMetaData md = proxy(ResultSetMetaData.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getColumnCount": return labels.length;
                case "getColumnLabel":
                case "getColumnName": return labels[(Integer) args[0] - 1];
                case "getColumnType": return Types.VARCHAR;
                case "getColumnTypeName": return "varchar";
                case "getColumnClassName": return "java.lang.String";
                case "getPrecision": return 100;
                case "isNullable": return ResultSetMetaData.columnNullable;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == args[0];
                default: return defaultValue(method);
            }
        });
        int[] row = {0};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (p, method, args) -> {
            switch (method.getName()) {
                case "next": return ++row[0] == 1;
                case "getMetaData": return md;
                case "getString":
                case "getObject": {
                    int i = (Integer) args[0] - 1;
                    String v = i < values.size() ? values.get(i) : null;
                    wasNull[0] = v == null;
                    return v;
                }
                case "wasNull": return wasNull[0];
                case "getType": return ResultSet.TYPE_FORWARD_ONLY;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == args[0];
                default: return defaultValue(method);
            }
        });
    }

    private static PreparedStatement statement(String query, boolean prepared) {
        synchronized (Utf8Driver.class) {
            sql = query;
            bound.clear();
        }
        boolean[] done = {false};
        ResultSet[] current = {null};
        return proxy(PreparedStatement.class, (p, method, args) -> {
            switch (method.getName()) {
                case "setString": {
                    synchronized (Utf8Driver.class) {
                        int i = (Integer) args[0] - 1;
                        while (bound.size() <= i) {
                            bound.add(null);
                        }
                        bound.set(i, (String) args[1]);
                    }
                    return null;
                }
                case "execute":
                    if (!prepared) {
                        synchronized (Utf8Driver.class) {
                            sql = (String) args[0];
                        }
                    }
                    synchronized (Utf8Driver.class) {
                        current[0] = resultSet(new ArrayList<>(bound));
                    }
                    done[0] = false;
                    return true;
                case "getResultSet": return done[0] ? null : current[0];
                case "getMoreResults": done[0] = true; return false;
                case "getUpdateCount": return -1;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == args[0];
                default: return defaultValue(method);
            }
        });
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        synchronized (Utf8Driver.class) {
            Utf8Driver.url = url;
            user = info.getProperty("user");
            password = info.getProperty("password");
        }
        boolean[] closed = {false};
        boolean[] autoCommit = {true};
        return proxy(Connection.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getMetaData": return metaData();
                case "prepareStatement": return statement((String) args[0], true);
                case "createStatement": return statement(null, false);
                case "setAutoCommit": autoCommit[0] = (Boolean) args[0]; return null;
                case "getAutoCommit": return autoCommit[0];
                case "close": closed[0] = true; return null;
                case "isClosed": return closed[0];
                case "isValid": return !closed[0];
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == args[0];
                default: return defaultValue(method);
            }
        });
    }

    @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:qore-utf8:"); }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }
    @Override public int getMajorVersion() { return 1; }
    @Override public int getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant() { return false; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
