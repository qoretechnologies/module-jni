/*
    JdbcDriverProfile.java

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
import java.util.Properties;
import java.util.regex.Pattern;

import org.qore.jni.QoreException;

//! The rules of one audited JDBC driver for restricted connections (qore#5786)
/** A restricted connection is allowed only with a driver that has a profile, and only after:
    - the lexical check of the URL and the properties, made before the driver's code runs (the Redshift parser makes
      a DNS lookup for some URLs): the URL form, the host list, and an allowlist of option keys, split as the driver
      splits them
    - the authoritative check on the driver's own parse, with the forced properties in place: every option in the
      result is allowed or forced, every forced value is unchanged, and the destinations are those of the lexical
      check; a difference between the two parsers therefore fails closed
    - the version gate (the exact audited version) and the class identity checks

    The profiles are hard-coded: nothing can be added from %Qore or by properties.

    The classes of this package are defined in the bootstrap class loader, so that every class loader finds them;
    the \c java.sql module is not visible there, so the driver is used through reflection (see jdbcDriverClass()).
*/
abstract class JdbcDriverProfile {
    //! the error of a refused connection
    static final String DENIED = "NETWORK-ACCESS-DENIED";
    //! the error of a failed integrity check
    static final String SANDBOX_ERROR = "JNI-SANDBOX-ERROR";

    //! LOAD [DATA] LOCAL INFILE, as the SingleStore driver's own validator matches it: a superset of what it serves
    static final Pattern LOCAL_INFILE = Pattern.compile("LOAD\\s+(DATA\\s+)?LOCAL\\s+INFILE",
        Pattern.CASE_INSENSITIVE);

    //! a host name, an IPv4 address, or a bracketed IPv6 address
    private static final Pattern HOST_NAME = Pattern.compile(
        "[A-Za-z0-9_]([A-Za-z0-9_-]*[A-Za-z0-9_])?(\\.[A-Za-z0-9_]([A-Za-z0-9_-]*[A-Za-z0-9_])?)*");
    private static final Pattern IPV6_BRACKETED = Pattern.compile("\\[[0-9A-Fa-f:.]+\\]");

    //! Returns the profile of a URL, or null if no audited driver accepts it
    static JdbcDriverProfile forUrl(String url) {
        if (url.startsWith("jdbc:redshift:")) {
            return RedshiftProfile.INSTANCE;
        }
        if (url.startsWith("jdbc:singlestore:")) {
            return SingleStoreProfile.INSTANCE;
        }
        return null;
    }

    //! the name of the profile, as in the module info key \c jdbc_sandbox_drivers
    abstract String name();

    //! the class name of the driver
    abstract String driverClassName();

    //! PROVISIONAL: the exact audited version of the driver (design section 11, item 6)
    abstract String auditedVersion();

    //! Returns the version of the driver class
    abstract String driverVersion(Class<?> driverClass) throws ReflectiveOperationException;

    //! Checks the URL and properties before the driver's code runs and returns the destinations of the URL
    abstract JdbcTargets lexicalCheck(String url, Properties props);

    //! Checks that the classes that the driver loads by name are the module's (==), and prepares them
    /** @param driverClass the driver class, loaded from the trusted loader
    */
    abstract void checkClasses(Class<?> driverClass) throws ReflectiveOperationException;

    //! Returns the driver properties of the connection: the given ones and the forced ones
    abstract Properties force(Properties props, JdbcConnectGuard guard, String token);

    //! The authoritative check on the driver's own parse, with the forced properties in place
    /** @return the destinations of the driver's parse, which must be those of the lexical check
    */
    abstract JdbcTargets effectiveCheck(Class<?> driverClass, String url, Properties forced, JdbcConnectGuard guard,
        String token, JdbcTargets lexical) throws ReflectiveOperationException;

    //! Checks SQL that a restricted connection is given; nothing by default
    void checkSql(String sql) {
    }

    //! Returns true if checkSql() must also check the SQL as the driver rewrites it (Connection.nativeSQL())
    boolean checksNativeSql() {
        return false;
    }

    //! Returns the \c java.sql.Driver interface
    static Class<?> jdbcDriverClass() {
        try {
            return Class.forName("java.sql.Driver", false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException e) {
            throw sandboxError("the java.sql module is not available");
        }
    }

    //! Returns the driver of the profile from the loader, after the version gate and the class identity checks
    /** @return a \c java.sql.Driver
    */
    final Object resolveDriver(ClassLoader loader) {
        Class<?> dc;
        try {
            dc = Class.forName(driverClassName(), true, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw deny("the %s JDBC driver (%s) is not available from the classpath of every Program, which a "
                + "sandboxed connection loads its driver from (the global-add-classpath module command)", name(),
                driverClassName());
        }
        if (!jdbcDriverClass().isAssignableFrom(dc)) {
            throw deny("%s is not a JDBC driver", driverClassName());
        }
        String version;
        try {
            version = driverVersion(dc);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw deny("the version of the %s JDBC driver cannot be determined (%s)", name(), describe(e));
        }
        if (!auditedVersion().equals(version)) {
            throw deny("the %s JDBC driver version %s is not supported for sandboxed connections; only the audited "
                + "version %s is", name(), version, auditedVersion());
        }
        try {
            checkClasses(dc);
            return dc.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw sandboxError("the %s JDBC driver cannot be prepared for a sandboxed connection: %s", name(),
                describe(e));
        }
    }

    //! Returns NETWORK-ACCESS-DENIED with a formatted message
    static QoreException deny(String fmt, Object... args) {
        return new QoreException(DENIED, String.format(fmt, args));
    }

    //! Returns JNI-SANDBOX-ERROR with a formatted message
    static QoreException sandboxError(String fmt, Object... args) {
        return new QoreException(SANDBOX_ERROR, String.format(fmt, args));
    }

    //! Returns the text of an exception, with the cause of a reflective call
    static String describe(Throwable e) {
        if (e instanceof InvocationTargetException && e.getCause() != null) {
            e = e.getCause();
        }
        return e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    //! Checks that an option key cannot be folded onto another one: non-empty printable ASCII only
    /** (ex: \c SOCKETFACTORY with the Kelvin sign lowercases to \c socketfactory)
    */
    static void checkKey(String key) {
        if (key.isEmpty()) {
            throw deny("an option without a name is not allowed for sandboxed connections");
        }
        for (int i = 0; i < key.length(); ++i) {
            char c = key.charAt(i);
            if (c <= 0x20 || c >= 0x7f) {
                throw deny("option names of sandboxed connections must be printable ASCII; got %s", quote(key));
            }
        }
    }

    //! Checks a host of a URL: a host name, an IPv4 address or a bracketed IPv6 address
    static void checkHost(String host, String url) {
        if (host.isEmpty()) {
            throw deny("a sandboxed connection URL must name its hosts: %s", url);
        }
        if (!HOST_NAME.matcher(host).matches() && !IPV6_BRACKETED.matcher(host).matches()) {
            throw deny("invalid host %s in a sandboxed connection URL", quote(host));
        }
    }

    //! Parses a port of a URL: digits only, 1 .. 65535
    static int parsePort(String port, String url) {
        if (!port.matches("[0-9]{1,5}")) {
            throw deny("invalid port %s in a sandboxed connection URL", quote(port));
        }
        int p = Integer.parseInt(port);
        if (p < 1 || p > 65535) {
            throw deny("invalid port %d in a sandboxed connection URL", p);
        }
        return p;
    }

    //! Returns true if the SQL could make the driver read a client file (LOAD DATA LOCAL INFILE)
    static boolean hasLocalInfile(String sql) {
        return sql != null && LOCAL_INFILE.matcher(sql).find();
    }

    //! Returns a value for an error message
    static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length() && i < 200; ++i) {
            char c = s.charAt(i);
            if (c < 0x20 || c >= 0x7f) {
                sb.append(String.format("\\u%04x", (int)c));
            } else {
                sb.append(c);
            }
        }
        if (s.length() > 200) {
            sb.append("...");
        }
        return sb.append('\'').toString();
    }
}
