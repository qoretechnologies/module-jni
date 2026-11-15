/*
    JdbcSandboxConnect.java

    Qore Programming Language JNI Module

    Copyright (C) 2026 Qore Technologies, s.r.o.

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

package org.qore.jni.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import org.qore.jni.QoreException;
import org.qore.jni.QoreJavaApi;

//! A restricted JDBC connection: checked, pinned to its checked destinations, and connected through its guard
/** The jdbc driver of the jni module uses this class for a connection that the sandbox restricts (qore#5786):
    -# prepare() finds the profile of the URL (Amazon Redshift or SingleStore; any other driver is refused), makes
       the lexical check, loads the profile's exact driver class from the trusted class loader (never through
       \c DriverManager) with the version gate and the class identity checks, creates and registers the guard, and
       makes the authoritative check on the driver's own parse with the forced properties in place; the result is
       the destinations of the URL
    -# the jni module checks the destinations against the sandbox policy and pins the checked addresses with pin()
    -# connect() calls the driver on the cancellable connection thread of QoreJavaApi.openJdbcConnection(), and
       requires the driver to have used the guard
    -# close() closes the guard and removes it from the registry

    @note No class of this package may be declared sandbox-aware or made available to sandboxed code.

    @since jni 3.0
*/
public final class JdbcSandboxConnect implements AutoCloseable {
    private final JdbcDriverProfile profile;
    //! the java.sql.Driver
    private final Object driver;
    private final ClassLoader loader;
    private final String url;
    private final Properties forced;
    private final JdbcConnectGuard guard;
    private final JdbcTargets targets;
    //! the open connection (a java.sql.Connection), for checkSql()
    private volatile AutoCloseable connection;

    private JdbcSandboxConnect(JdbcDriverProfile profile, Object driver, ClassLoader loader, String url,
            Properties forced, JdbcConnectGuard guard, JdbcTargets targets) {
        this.profile = profile;
        this.driver = driver;
        this.loader = loader;
        this.url = url;
        this.forced = forced;
        this.guard = guard;
        this.targets = targets;
    }

    //! Returns true if an audited driver profile accepts the URL
    public static boolean isSupportedUrl(String url) {
        return url != null && JdbcDriverProfile.forUrl(url) != null;
    }

    //! Checks a restricted connection and returns it, ready to be pinned
    /** @param loader the trusted class loader to load the driver from
        @param url the JDBC URL
        @param props the driver properties of the connection (\c user, \c password)

        @throw QoreException NETWORK-ACCESS-DENIED: the URL, an option or the driver is not allowed;
        JNI-SANDBOX-ERROR: a class identity check failed
    */
    public static JdbcSandboxConnect prepare(ClassLoader loader, String url, Properties props) {
        JdbcDriverProfile profile = url == null ? null : JdbcDriverProfile.forUrl(url);
        if (profile == null) {
            throw JdbcDriverProfile.deny("the JDBC driver of URL %s is not supported for sandboxed connections; "
                + "only the audited Amazon Redshift and SingleStore drivers are",
                JdbcDriverProfile.quote(url == null ? "" : prefix(url)));
        }
        Properties p = props == null ? new Properties() : props;
        // before any code of the driver runs
        JdbcTargets lexical = profile.lexicalCheck(url, p);
        Object driver = profile.resolveDriver(loader);
        profile.checkEnvironment();
        JdbcConnectGuard guard = new JdbcConnectGuard();
        String token = guard.register();
        boolean ok = false;
        try {
            Properties forced = profile.force(p, guard, token);
            JdbcTargets targets;
            try {
                targets = profile.effectiveCheck(driver.getClass(), url, forced, guard, token, lexical);
            } catch (ReflectiveOperationException | LinkageError e) {
                throw JdbcDriverProfile.deny("the %s driver's parse of the URL failed: %s", profile.name(),
                    JdbcDriverProfile.describe(e));
            }
            JdbcSandboxConnect rv = new JdbcSandboxConnect(profile, driver, loader, url, forced, guard, targets);
            ok = true;
            return rv;
        } finally {
            if (!ok) {
                guard.close();
            }
        }
    }

    //! the scheme of a URL, for an error message
    private static String prefix(String url) {
        int i = url.indexOf("//");
        return i < 0 ? url.substring(0, Math.min(url.length(), 40)) : url.substring(0, i);
    }

    //! Returns the name of the driver profile
    public String getProfileName() {
        return profile.name();
    }

    //! Returns the host names of the destinations, as the sandbox checks them
    public String[] getTargetHosts() {
        return targets.checkHosts();
    }

    //! Returns the ports of the destinations
    public int[] getTargetPorts() {
        return targets.portArray();
    }

    //! Pins the checked addresses of the destinations, in the order of getTargetHosts()
    /** @param addrs for each destination, its checked addresses: 4 bytes for IPv4, or 16 bytes and the scope ID in
        4 bytes (big-endian) for IPv6
    */
    public void pin(byte[][][] addrs) throws UnknownHostException {
        String[] hosts = targets.checkHosts();
        if (addrs == null || addrs.length != hosts.length) {
            throw JdbcDriverProfile.sandboxError("the checked destinations do not match the destinations of the URL");
        }
        InetAddress[][] a = new InetAddress[addrs.length][];
        for (int i = 0; i < addrs.length; ++i) {
            a[i] = new InetAddress[addrs[i].length];
            for (int j = 0; j < addrs[i].length; ++j) {
                a[i][j] = decode(hosts[i], addrs[i][j]);
            }
        }
        guard.pin(hosts, targets.portArray(), a);
    }

    //! Pins the checked numeric addresses of the destinations, in the order of getTargetHosts()
    public void pinAddresses(String[][] addrs) throws UnknownHostException {
        String[] hosts = targets.checkHosts();
        if (addrs == null || addrs.length != hosts.length) {
            throw JdbcDriverProfile.sandboxError("the checked destinations do not match the destinations of the URL");
        }
        InetAddress[][] a = new InetAddress[addrs.length][];
        for (int i = 0; i < addrs.length; ++i) {
            a[i] = new InetAddress[addrs[i].length];
            for (int j = 0; j < addrs[i].length; ++j) {
                a[i][j] = JdbcConnectGuard.literal(addrs[i][j]);
            }
        }
        guard.pin(hosts, targets.portArray(), a);
    }

    //! decodes an address as the jni module encodes it (see QoreSandbox)
    private static InetAddress decode(String host, byte[] b) throws UnknownHostException {
        if (b.length == 4) {
            return InetAddress.getByAddress(host, b);
        }
        if (b.length == 20) {
            byte[] a = new byte[16];
            System.arraycopy(b, 0, a, 0, 16);
            int scope = ((b[16] & 0xff) << 24) | ((b[17] & 0xff) << 16) | ((b[18] & 0xff) << 8) | (b[19] & 0xff);
            return Inet6Address.getByAddress(host, a, scope);
        }
        throw new UnknownHostException("invalid encoded address of " + host);
    }

    //! Returns the guard of the connection
    public JdbcConnectGuard getGuard() {
        return guard;
    }

    //! Opens the connection through the guard
    /** The driver is called on the cancellable connection thread of QoreJavaApi.openJdbcConnection(), with the
        trusted class loader as the context class loader.  A connection whose driver did not open a socket through
        the guard is closed and refused.

        @throw QoreException NETWORK-ACCESS-DENIED: the guard refused a destination; JNI-SANDBOX-ERROR: the driver did
        not use the guard
    */
    public AutoCloseable connect() throws Throwable {
        if (!guard.isPinned()) {
            throw JdbcDriverProfile.deny("the destinations of the connection were not checked against the sandbox");
        }
        if (guard.isClosed()) {
            throw JdbcDriverProfile.deny("the connection guard was closed");
        }
        profile.checkEnvironment();
        // java.sql.Driver.connect(String, Properties), which returns a java.sql.Connection
        Method connectMethod = JdbcDriverProfile.jdbcDriverClass().getMethod("connect", String.class,
            Properties.class);
        AutoCloseable conn;
        try {
            conn = QoreJavaApi.openJdbcConnection(() -> {
                Thread t = Thread.currentThread();
                ClassLoader saved = t.getContextClassLoader();
                t.setContextClassLoader(loader);
                try {
                    // a copy: the SingleStore driver writes the URL's options into the given properties
                    Properties p = new Properties();
                    p.putAll(forced);
                    return (AutoCloseable)connectMethod.invoke(driver, url, p);
                } catch (InvocationTargetException e) {
                    throw e.getCause() instanceof Exception ? (Exception)e.getCause() : e;
                } finally {
                    t.setContextClassLoader(saved);
                }
            });
        } catch (QoreException e) {
            throw e;
        } catch (Throwable e) {
            String refusal = guard.lastRefusal();
            if (refusal != null) {
                QoreException qe = JdbcDriverProfile.deny("%s", refusal);
                qe.initCause(e);
                throw qe;
            }
            throw e;
        }
        if (conn == null) {
            throw JdbcDriverProfile.deny("the %s driver does not accept the URL", profile.name());
        }
        requireGuardUsed(guard, conn);
        connection = conn;
        return conn;
    }

    //! Closes the connection and raises JNI-SANDBOX-ERROR if no socket connected through the guard
    public static void requireGuardUsed(JdbcConnectGuard guard, AutoCloseable conn) {
        if (guard.socketCount() > 0) {
            return;
        }
        QoreException e = JdbcDriverProfile.sandboxError("the JDBC driver did not use the socket factory of the jni "
            + "module, so its connection was closed");
        try {
            conn.close();
        } catch (Throwable ce) {
            e.addSuppressed(ce);
        }
        throw e;
    }

    //! Checks SQL that the connection is given (SingleStore: LOAD DATA LOCAL INFILE)
    /** The SQL is checked as given and as the driver rewrites it (\c Connection.nativeSQL(), the JDBC escape
        processing that \c prepareStatement() and \c Statement.execute() apply before the SQL is sent, ex:
        <tt>{d LOCAL}</tt> becomes \c LOCAL): the driver checks a server's file request against the rewritten text.

        @throw QoreException JNI-SANDBOX-ERROR: the SQL is not allowed
    */
    public void checkSql(String sql) throws Throwable {
        profile.checkSql(sql);
        if (!profile.checksNativeSql()) {
            return;
        }
        AutoCloseable conn = connection;
        if (conn == null) {
            throw JdbcDriverProfile.sandboxError("the SQL of a sandboxed connection cannot be checked: the "
                + "connection is not open");
        }
        String nativeSql;
        try {
            Class<?> cc = Class.forName("java.sql.Connection", false, ClassLoader.getPlatformClassLoader());
            nativeSql = (String)cc.getMethod("nativeSQL", String.class).invoke(conn, sql);
        } catch (InvocationTargetException e) {
            // the driver cannot rewrite the SQL, so it cannot execute it either
            throw e.getCause();
        }
        profile.checkSql(nativeSql);
    }

    //! Checks SQL that restricted code gives a connection that is not restricted (qore#6142)
    /** A connection that trusted code opened has no guard; when restricted code uses it, the SQL check of the
        profile of its URL applies (SingleStore: LOAD DATA LOCAL INFILE, also as the driver rewrites the SQL); a URL
        without a profile is not checked.

        @param url the URL of the connection
        @param sql the SQL
        @param conn the \c java.sql.Connection

        @throw QoreException JNI-SANDBOX-ERROR: the SQL is not allowed
    */
    public static void checkSqlForUrl(String url, String sql, Object conn) throws Throwable {
        JdbcDriverProfile profile = url == null ? null : JdbcDriverProfile.forUrl(url);
        if (profile == null) {
            return;
        }
        profile.checkSql(sql);
        if (profile.checksNativeSql()) {
            Class<?> cc = Class.forName("java.sql.Connection", false, ClassLoader.getPlatformClassLoader());
            if (!cc.isInstance(conn)) {
                throw JdbcDriverProfile.sandboxError("the SQL of the connection cannot be checked");
            }
            try {
                profile.checkSql((String)cc.getMethod("nativeSQL", String.class).invoke(conn, sql));
            } catch (InvocationTargetException e) {
                // the driver cannot rewrite the SQL, so it cannot execute it either
                throw e.getCause();
            }
        }
    }

    //! Closes the guard: no socket of the connection can connect any more
    @Override
    public void close() {
        guard.close();
    }
}
