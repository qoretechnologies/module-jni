// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** A driver that accepts the PostgreSQL URLs of a test host, but is not the PostgreSQL driver (qore#7867)

    Code that is restricted to the allow-listed drivers is refused a connection that this driver would serve; other
    code connects with it: the connections are those of JdbcTransactionDriver
*/
public final class JdbcRestrictedUrlDriver implements Driver {
    //! the prefix of the URLs that this driver accepts
    public static final String PREFIX = "jdbc:postgresql://qore-restricted-url-test";

    private static boolean installed;
    private static final AtomicInteger connects = new AtomicInteger();

    public static synchronized void install() throws SQLException {
        if (!installed) {
            DriverManager.registerDriver(new JdbcRestrictedUrlDriver());
            installed = true;
        }
    }

    //! the number of connections that the driver made
    public static int connects() {
        return connects.get();
    }

    @Override public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        connects.incrementAndGet();
        return JdbcTransactionDriver.open();
    }

    @Override public boolean acceptsURL(String url) { return url.startsWith(PREFIX); }
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
