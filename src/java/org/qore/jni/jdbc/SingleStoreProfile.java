/*
    SingleStoreProfile.java

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

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//! The rules of SingleStore JDBC 1.2.12 for restricted connections (qore#5786)
/** Only <tt>jdbc:singlestore:[sequential:|loadbalance:]//hostlist[/database][?options]</tt> over TCP with password
    authentication.  The driver's URL splitting (\c Configuration.parse()): the options are split on \c & and are not
    decoded, and they are written into the given properties, so they override them; option keys are matched with the
    configuration's fields case-insensitively.

    The driver loads its socket factory by name from its own class loader and gives a
    \c com.singlestore.jdbc.util.ConfigurableSocketFactory the connection's configuration, whose unmapped options keep
    the JdbcConnectGuard object that the jni module puts in the properties.  The factory is the adapter
    \c com.singlestore.jdbc.util.QoreGuardSocketFactory, which the module defines in the driver's class loader (see
    adapterClass()).

    The driver serves a server's LOCAL INFILE request whatever \c allowLocalInfile says, if the SQL text matches its
    own LOAD DATA LOCAL INFILE pattern: every SQL text that a restricted connection is given (statements, \c initSql,
    \c sessionVariables) is refused if it matches that pattern.
*/
final class SingleStoreProfile extends JdbcDriverProfile {
    static final SingleStoreProfile INSTANCE = new SingleStoreProfile();

    //! the name of the adapter class
    static final String ADAPTER = "com.singlestore.jdbc.util.QoreGuardSocketFactory";
    //! the default port of the driver
    static final int DEFAULT_PORT = 3306;
    //! PROVISIONAL: the authentication plugins of restricted connections; no clear-text or PAM plugin (design section
    //! 11, item 9)
    static final String RESTRICTED_AUTH = "mysql_native_password,client_ed25519";

    private static final Pattern URL_FORM = Pattern.compile("jdbc:singlestore:(sequential:|loadbalance:)?//(.*)",
        Pattern.DOTALL);
    private static final Pattern ADDRESS = Pattern.compile(
        "address=\\(host=([^()=,/?\\s]+)\\)\\(port=([0-9]{1,5})\\)(\\(type=(primary|replica|master|slave)\\))?");
    //! an inline PEM certificate list: it contains a space, so the driver does not take it for a URL or a file
    private static final Pattern INLINE_PEM = Pattern.compile(
        "\\s*(-----BEGIN CERTIFICATE-----[A-Za-z0-9+/=\\s]+-----END CERTIFICATE-----\\s*)+");

    //! the options that affect only the protocol or the session (lowercase)
    private static final Set<String> ALLOWED = lower(Set.of(
        "user", "password", "autocommit", "useMysqlMetadata", "useMysqlVersion", "nullDatabaseMeansCurrent",
        "initSql", "defaultFetchSize", "maxQuerySizeToLog", "maxAllowedPacket",
        "geometryDefaultType", "transactionIsolation", "connectTimeout", "socketTimeout", "tcpKeepAlive",
        "tcpKeepIdle", "tcpKeepCount", "tcpKeepInterval", "tcpAbortiveClose", "useReadAheadInput", "sslMode",
        "serverSslCert", "enabledSslCipherSuites", "enabledSslProtocolSuites", "hostNameInCertificate",
        "allowMultiQueries", "useCompression", "useAffectedRows", "disablePipeline", "cachePrepStmts",
        "prepStmtCacheSize", "useServerPrepStmts", "sessionVariables", "connectionAttributes", "blankTableNameMeta",
        "transformedBitIsBoolean", "yearIsDateType", "dumpQueriesOnException", "retriesAllDown",
        "transactionReplay", "transactionReplaySize", "useResetConnection", "rewriteBatchedStatements",
        "preserveInstants", "enableExtendedDataTypes", "vectorTypeOutputFormat", "vectorExtendedMetadata"));

    //! the forced keys
    private static final String SOCKET_FACTORY = "socketFactory";
    private static final String ALLOW_LOCAL_INFILE = "allowLocalInfile";
    private static final String POOL = "pool";
    private static final String RESTRICTED_AUTH_KEY = "restrictedAuth";
    private static final String CREATE_DATABASE = "createDatabaseIfNotExist";
    private static final String SSL_MODE_KEY = "sslMode";
    private static final String KEY_STORE = "keyStore";
    private static final String KEY_STORE_PASSWORD = "keyStorePassword";
    private static final String KEY_STORE_TYPE = "keyStoreType";
    //! the password of the module's empty client key store
    static final String EMPTY_KEY_STORE_PASSWORD = "qore-jni-empty";
    //! PROVISIONAL: the only TLS mode of restricted connections (design section 11, item 9)
    static final String SSL_MODE = "verify-full";
    private static final Set<String> FORCED = lower(Set.of(SOCKET_FACTORY, ALLOW_LOCAL_INFILE, POOL,
        RESTRICTED_AUTH_KEY, CREATE_DATABASE, SSL_MODE_KEY, KEY_STORE, KEY_STORE_PASSWORD, KEY_STORE_TYPE,
        JdbcConnectGuard.PROPERTY));

    //! a database name: a plain identifier, as the driver quotes it in SQL and sends it in the handshake
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    //! the value of vectorTypeOutputFormat, which the driver puts into a SET statement as given
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{1,32}");

    //! the adapter class of each driver class loader; defining it twice in a loader raises a LinkageError
    private static final WeakHashMap<ClassLoader, WeakReference<Class<?>>> adapters = new WeakHashMap<>();

    private SingleStoreProfile() {
    }

    private static Set<String> lower(Set<String> s) {
        Set<String> rv = new HashSet<>();
        for (String k : s) {
            rv.add(k.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(rv);
    }

    @Override
    String name() {
        return "singlestore";
    }

    @Override
    String driverClassName() {
        return "com.singlestore.jdbc.Driver";
    }

    @Override
    String auditedVersion() {
        // PROVISIONAL: the exact audited version (design section 11, item 6)
        return "1.2.12";
    }

    @Override
    String driverVersion(Class<?> driverClass) throws ReflectiveOperationException {
        Class<?> vf = Class.forName("com.singlestore.jdbc.util.VersionFactory", true, driverClass.getClassLoader());
        if (vf.getClassLoader() != driverClass.getClassLoader()) {
            throw new ClassNotFoundException("the driver's VersionFactory comes from another class loader");
        }
        Object v = vf.getMethod("getInstance").invoke(null);
        return String.valueOf(v.getClass().getMethod("getVersion").invoke(v));
    }

    @Override
    JdbcTargets lexicalCheck(String url, Properties props) {
        Matcher m = URL_FORM.matcher(url);
        if (!m.matches()) {
            throw deny("only jdbc:singlestore:[sequential:|loadbalance:]//host[:port][,...][/database] URLs are "
                + "supported for sandboxed connections");
        }
        String rest = m.group(2);
        // the host list ends at the first '/' or '?'; neither can be part of a host of the grammar below
        int end = rest.length();
        for (int i = 0; i < rest.length(); ++i) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?') {
                end = i;
                break;
            }
        }
        String hostList = rest.substring(0, end);
        String tail = rest.substring(end);
        if (hostList.isEmpty()) {
            throw deny("a sandboxed SingleStore connection URL must name its hosts (no pipe or UNIX socket)");
        }
        JdbcTargets targets = new JdbcTargets();
        for (String token : hostList.split(",", -1)) {
            if (token.startsWith("address=")) {
                Matcher am = ADDRESS.matcher(token);
                if (!am.matches()) {
                    throw deny("invalid host address %s in a sandboxed connection URL: only "
                        + "address=(host=..)(port=..)[(type=..)] is allowed", quote(token));
                }
                String host = am.group(1);
                checkHost(host, url);
                // the driver lowercases the address and removes the brackets of an IPv6 address
                targets.add(host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", ""),
                    parsePort(am.group(2), url));
            } else if (token.startsWith("[")) {
                int ind = token.indexOf(']');
                if (ind < 0) {
                    throw deny("invalid host %s in a sandboxed connection URL", quote(token));
                }
                String host = token.substring(0, ind + 1);
                checkHost(host, url);
                int port = DEFAULT_PORT;
                if (ind != token.length() - 1) {
                    if (token.charAt(ind + 1) != ':') {
                        throw deny("invalid host %s in a sandboxed connection URL", quote(token));
                    }
                    port = parsePort(token.substring(ind + 2), url);
                }
                targets.add(host.substring(1, host.length() - 1), port);
            } else {
                String[] hp = token.split(":", -1);
                if (hp.length > 2) {
                    throw deny("invalid host %s in a sandboxed connection URL", quote(token));
                }
                checkHost(hp[0], url);
                targets.add(hp[0], hp.length == 2 ? parsePort(hp[1], url) : DEFAULT_PORT);
            }
        }

        // the database: "/" up to the options
        int q = tail.indexOf('?');
        if (tail.startsWith("/")) {
            checkDatabase(tail.substring(1, q < 0 ? tail.length() : q));
        }

        Set<String> seen = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            checkOption(key, props.getProperty(key), seen);
        }
        if (q >= 0) {
            String args = tail.substring(q + 1);
            if (!args.isEmpty()) {
                for (String token : args.split("&", -1)) {
                    int pos = token.indexOf('=');
                    if (pos < 0) {
                        // the driver sets the key to ""
                        throw deny("option %s of a sandboxed connection URL has no value", quote(token));
                    }
                    checkOption(token.substring(0, pos), token.substring(pos + 1), seen);
                }
            }
        }
        if (!seen.contains("user")) {
            // the driver's default user is the name of the process owner
            throw deny("a sandboxed SingleStore connection must give its user");
        }
        return targets;
    }

    //! checks the database of a URL: empty (none) or a plain identifier
    private static void checkDatabase(String db) {
        if (!db.isEmpty() && !DATABASE.matcher(db).matches()) {
            throw deny("database %s of a sandboxed SingleStore connection must be a plain identifier "
                + "([A-Za-z0-9_$], at most 64 characters)", quote(db));
        }
    }

    //! checks an option against the allowlist, for duplicates, and its value
    private static void checkOption(String key, String value, Set<String> seen) {
        checkKey(key);
        String lk = key.toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(lk)) {
            throw deny("option %s is not allowed for sandboxed SingleStore connections", quote(key));
        }
        if (lk.equals("user") && (value == null || value.isEmpty())) {
            // the driver sends the name of the process owner for an empty user
            throw deny("a sandboxed SingleStore connection must give a user that is not empty");
        }
        if (!seen.add(lk)) {
            throw deny("option %s is given more than once", quote(key));
        }
        checkValue(lk, value);
    }

    //! checks the value of an allowed option
    private static void checkValue(String lk, String value) {
        if (value == null) {
            return;
        }
        // every value: the driver sends some of them as SQL during the connection (initSql, sessionVariables,
        // vectorTypeOutputFormat)
        if (hasLocalInfile(value)) {
            throw deny("option %s value with LOAD DATA LOCAL INFILE is not allowed for sandboxed connections, "
                + "because the driver reads client files for it", lk);
        }
        if (lk.equals("vectortypeoutputformat") && !WORD.matcher(value).matches()) {
            throw deny("option vectorTypeOutputFormat value %s of a sandboxed connection must be a word", quote(value));
        }
        if (lk.equals("sslmode") && !value.equals(SSL_MODE)) {
            throw deny("option sslMode value %s is not allowed for sandboxed SingleStore connections; only %s is",
                quote(value), SSL_MODE);
        }
        if (lk.equals("serversslcert") && !value.isEmpty() && !INLINE_PEM.matcher(value).matches()) {
            throw deny("option serverSslCert value of a sandboxed connection must be an inline PEM certificate, "
                + "not a URL or a file");
        }
    }

    //! Returns the bytes of the adapter class
    /** PROVISIONAL (design section 11, item 10): the adapter is built in to the module and defined at run time in the
        driver's class loader; this is the one place to change for an adapter jar that is added to the driver's
        class loader instead.  The bytes are compiled against signature-only stubs of the driver's classes, so the
        build needs no driver jar.
    */
    private static native byte[] adapterBytes0();

    //! Returns the adapter class of the driver's class loader, defining it once per loader
    /** PROVISIONAL (design section 11, item 10): the adapter is defined in the vendor package
        \c com.singlestore.jdbc.util with \c MethodHandles.privateLookupIn(...).defineClass().

        @param base the driver's \c ConfigurableSocketFactory class
    */
    private static Class<?> adapterClass(Class<?> base) {
        ClassLoader cl = base.getClassLoader();
        synchronized (adapters) {
            WeakReference<Class<?>> ref = adapters.get(cl);
            Class<?> c = ref == null ? null : ref.get();
            if (c != null) {
                return c;
            }
            try {
                c = MethodHandles.privateLookupIn(base, MethodHandles.lookup()).defineClass(adapterBytes0());
            } catch (LinkageError e) {
                throw sandboxError("the class loader of the SingleStore driver has its own %s, which a sandboxed "
                    + "connection cannot use (%s)", ADAPTER, describe(e));
            } catch (IllegalAccessException | SecurityException e) {
                throw sandboxError("the SingleStore adapter cannot be defined in the driver's class loader: %s",
                    describe(e));
            }
            if (!c.getName().equals(ADAPTER) || !base.isAssignableFrom(c)) {
                throw sandboxError("the SingleStore adapter of the jni module is invalid");
            }
            adapters.put(cl, new WeakReference<>(c));
            return c;
        }
    }

    @Override
    void checkClasses(Class<?> driverClass) throws ReflectiveOperationException {
        ClassLoader dl = driverClass.getClassLoader();
        // the factory is loaded by the class loader of ConnectionHelper (Class.forName(name, false, loader))
        ClassLoader cl = Class.forName("com.singlestore.jdbc.client.impl.ConnectionHelper", false, dl)
            .getClassLoader();
        Class<?> base = Class.forName("com.singlestore.jdbc.util.ConfigurableSocketFactory", false, cl);
        if (base.getClassLoader() != cl) {
            throw sandboxError("the SingleStore driver's ConfigurableSocketFactory comes from another class loader");
        }
        // the adapter links to the guard by name, from the driver's class loader
        if (Class.forName(JdbcConnectGuard.class.getName(), false, cl) != JdbcConnectGuard.class) {
            throw sandboxError("the class loader of the SingleStore driver has its own %s, which a sandboxed "
                + "connection cannot use", JdbcConnectGuard.class.getName());
        }
        Class<?> adapter = adapterClass(base);
        if (Class.forName(ADAPTER, false, cl) != adapter) {
            throw sandboxError("the class loader of the SingleStore driver has its own %s, which a sandboxed "
                + "connection cannot use", ADAPTER);
        }
    }

    @Override
    Properties force(Properties props, JdbcConnectGuard guard, String token) {
        Properties f = new Properties();
        for (String key : props.stringPropertyNames()) {
            f.setProperty(key, props.getProperty(key));
        }
        f.setProperty(SOCKET_FACTORY, ADAPTER);
        f.put(JdbcConnectGuard.PROPERTY, guard);
        f.setProperty(ALLOW_LOCAL_INFILE, "false");
        f.setProperty(POOL, "false");
        f.setProperty(RESTRICTED_AUTH_KEY, RESTRICTED_AUTH);
        f.setProperty(CREATE_DATABASE, "false");
        f.setProperty(SSL_MODE_KEY, SSL_MODE);
        f.setProperty(KEY_STORE, ensureEmptyKeyStore());
        f.setProperty(KEY_STORE_PASSWORD, EMPTY_KEY_STORE_PASSWORD);
        f.setProperty(KEY_STORE_TYPE, "PKCS12");
        return f;
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException {
        Method m = o.getClass().getMethod(method);
        return m.invoke(o);
    }

    @Override
    JdbcTargets effectiveCheck(Class<?> driverClass, String url, Properties forced, JdbcConnectGuard guard,
            String token, JdbcTargets lexical) throws ReflectiveOperationException {
        Class<?> confClass = Class.forName("com.singlestore.jdbc.Configuration", false, driverClass.getClassLoader());
        // the driver writes the URL's options into the given properties: a copy
        Properties copy = new Properties();
        copy.putAll(forced);
        Object conf = confClass.getMethod("parse", String.class, Properties.class).invoke(null, url, copy);
        if (conf == null) {
            throw deny("the SingleStore driver does not accept the URL %s", quote(url));
        }
        // every option of the parse: the given ones, the URL's and the forced ones
        for (Map.Entry<Object, Object> e : copy.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                throw deny("the SingleStore driver's parse of the URL has an option that is not a string");
            }
            String lk = ((String)e.getKey()).toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(lk) && !FORCED.contains(lk)) {
                throw deny("the SingleStore driver's parse of the URL gives option %s, which is not allowed for "
                    + "sandboxed connections", quote((String)e.getKey()));
            }
            if (e.getValue() instanceof String) {
                checkValue(lk, (String)e.getValue());
            }
        }
        if (copy.get(JdbcConnectGuard.PROPERTY) != guard || !ADAPTER.equals(copy.get(SOCKET_FACTORY))
                || !"false".equals(copy.get(ALLOW_LOCAL_INFILE)) || !"false".equals(copy.get(POOL))
                || !RESTRICTED_AUTH.equals(copy.get(RESTRICTED_AUTH_KEY)) || !"false".equals(copy.get(CREATE_DATABASE))
                // the key store that force() made sure of must still be the current one: a concurrent restricted
                // connection that created it again in between makes this check refuse the connection (fail closed)
                || !SSL_MODE.equals(copy.get(SSL_MODE_KEY)) || !forced.get(KEY_STORE).equals(emptyKeyStore())
                || !forced.get(KEY_STORE).equals(copy.get(KEY_STORE))
                || !EMPTY_KEY_STORE_PASSWORD.equals(copy.get(KEY_STORE_PASSWORD))
                || !"PKCS12".equals(copy.get(KEY_STORE_TYPE))) {
            throw deny("the SingleStore driver's parse of the URL changes a forced option");
        }

        // the effective configuration
        if (!ADAPTER.equals(call(conf, "socketFactory"))) {
            throw deny("the SingleStore driver's parse of the URL changes the socket factory");
        }
        for (String m : new String[]{"pipe", "localSocket", "localSocketAddress", "socksProxyHost", "trustStore",
                "tlsSocketType", "credentialPlugin", "keyPassword"}) {
            if (call(conf, m) != null) {
                throw deny("the SingleStore driver's parse of the URL sets %s, which a sandboxed connection must not "
                    + "use", m);
            }
        }
        if (!Boolean.FALSE.equals(call(conf, "allowLocalInfile")) || !Boolean.FALSE.equals(call(conf, "pool"))
                || !RESTRICTED_AUTH.equals(call(conf, "restrictedAuth"))) {
            throw deny("the SingleStore driver's parse of the URL changes a forced option");
        }
        Properties nonMapped = (Properties)call(conf, "nonMappedOptions");
        if (nonMapped.size() != 1 || nonMapped.get(JdbcConnectGuard.PROPERTY) != guard) {
            throw deny("the SingleStore driver's parse of the URL has unknown options: %s", nonMapped.keySet());
        }
        // the module's empty client key store, so that the JVM's (javax.net.ssl.keyStore) is never read
        if (!forced.get(KEY_STORE).equals(call(conf, "keyStore"))
                || !EMPTY_KEY_STORE_PASSWORD.equals(call(conf, "keyStorePassword"))
                || !"PKCS12".equals(call(conf, "keyStoreType"))) {
            throw deny("the SingleStore driver's parse of the URL changes the client key store");
        }
        Object user = call(conf, "user");
        if (user == null || user.toString().isEmpty()) {
            // the driver sends the name of the process owner
            throw deny("a sandboxed SingleStore connection must give a user that is not empty");
        }
        if (!Boolean.FALSE.equals(call(conf, "createDatabaseIfNotExist"))) {
            throw deny("the SingleStore driver's parse of the URL changes a forced option");
        }
        if (!"VERIFY_FULL".equals(String.valueOf(call(conf, "sslMode")))) {
            throw deny("the SingleStore driver's parse of the URL gives the TLS mode %s; only %s is allowed",
                call(conf, "sslMode"), SSL_MODE);
        }
        Object db = call(conf, "database");
        if (db != null) {
            checkDatabase(db.toString());
        }
        Object vf = call(conf, "vectorTypeOutputFormat");
        if (vf != null && !WORD.matcher(vf.toString()).matches()) {
            throw deny("option vectorTypeOutputFormat value of a sandboxed connection must be a word");
        }
        Object sv = call(conf, "sessionVariables");
        if (sv != null) {
            // the SQL as the driver builds it: "set " + Security.parseSessionVariables()
            Class<?> sec = Class.forName("com.singlestore.jdbc.util.Security", false, driverClass.getClassLoader());
            Object parsed = sec.getMethod("parseSessionVariables", String.class).invoke(null, sv.toString());
            if (parsed != null && hasLocalInfile("set " + parsed)) {
                throw deny("option sessionVariables value with LOAD DATA LOCAL INFILE is not allowed for sandboxed "
                    + "connections, because the driver reads client files for it");
            }
        }
        for (String m : new String[]{"initSql", "sessionVariables"}) {
            Object v = call(conf, m);
            if (v != null && hasLocalInfile(v.toString())) {
                throw deny("option %s value with LOAD DATA LOCAL INFILE is not allowed for sandboxed connections, "
                    + "because the driver reads client files for it", m);
            }
        }
        Object cert = call(conf, "serverSslCert");
        if (cert != null && !INLINE_PEM.matcher(cert.toString()).matches()) {
            throw deny("option serverSslCert value of a sandboxed connection must be an inline PEM certificate");
        }
        String mode = String.valueOf(call(conf, "haMode"));
        String expected = url.startsWith("jdbc:singlestore:sequential:") ? "SEQUENTIAL"
            : url.startsWith("jdbc:singlestore:loadbalance:") ? "LOADBALANCE" : "NONE";
        if (!expected.equals(mode)) {
            throw deny("the SingleStore driver's parse of the URL gives the mode %s", mode);
        }
        List<?> addresses = (List<?>)call(conf, "addresses");
        JdbcTargets rv = new JdbcTargets();
        for (Object a : addresses) {
            Field hf = a.getClass().getField("host");
            Field pf = a.getClass().getField("port");
            Object host = hf.get(a);
            if (host == null) {
                throw deny("the SingleStore driver's parse of the URL has a destination without a host");
            }
            rv.add(host.toString(), pf.getInt(a));
        }
        if (rv.size() == 0 || !rv.sameAs(lexical)) {
            throw deny("the SingleStore driver's parse of the URL gives other destinations (%s) than the URL (%s)",
                rv, lexical);
        }
        return rv;
    }

    //! the contents of the module's empty client key store
    private static byte[] emptyKeyStoreBytes;
    //! the directory of the module's empty client key store
    private static java.nio.file.Path emptyKeyStoreDir;
    //! the identity of the directory when it was created (its file key, or its creation time without one)
    private static Object emptyKeyStoreDirKey;
    //! the owner of the directory when it was created: this process's user
    private static java.nio.file.attribute.UserPrincipal emptyKeyStoreDirOwner;
    //! the paths registered with deleteOnExit()
    private static final java.util.Set<java.nio.file.Path> emptyKeyStoreExitPaths = new java.util.HashSet<>();
    //! the URI of the module's empty client key store, as it was last made sure of
    private static String emptyKeyStore;

    //! Makes sure that the module's empty client key store exists with its contents, and returns its URI
    /** Without a \c keyStore option the driver presents the key of the \c javax.net.ssl.keyStore system property to
        every server, read on every TLS handshake (DefaultTlsSocketPlugin.getKeyManager(): also for reconnects,
        transaction replay and query cancels); with the forced \c keyStore, the system property is never read.  The
        store has no entries; it is the file \c empty.p12 in a new directory of this process
        (\c Files.createTempDirectory(): a random name, accessible by its owner only), made sure of for every
        restricted connection:
        - the directory is used again only if it is still the directory that this process created: the same file
          key (without links), the same owner, and no access for others; otherwise (ex: a temporary file cleaner
          removed it and another user created one with the name) a new directory is created
        - the file must be a regular file (without links) of the expected size with the expected contents, read
          through a bounded channel; otherwise it is written again atomically from the expected contents.  Only this
          process's user can create entries in the directory, so a file of another kind cannot replace it between
          the check and the read
        Every failure refuses the connection (fails closed), also a sharing violation on Windows when the file is
        replaced while the driver reads it.  The driver reads the file on each handshake, so a file that is removed
        later makes TLS fail (closed) until the next restricted connection writes it again.
    */
    static synchronized String ensureEmptyKeyStore() {
        try {
            if (emptyKeyStoreBytes == null) {
                java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                ks.store(bo, EMPTY_KEY_STORE_PASSWORD.toCharArray());
                emptyKeyStoreBytes = bo.toByteArray();
            }
            if (!isOwnKeyStoreDir()) {
                java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("qore-jni-jdbc-keystore-");
                java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(dir,
                    java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                emptyKeyStoreDirKey = a.fileKey() != null ? a.fileKey() : a.creationTime();
                emptyKeyStoreDirOwner = ownerOf(dir);
                emptyKeyStoreDir = dir;
                // the file is deleted before its directory: deleteOnExit() deletes in the reverse order
                registerDeleteOnExit(dir);
                registerDeleteOnExit(dir.resolve("empty.p12"));
            }
            java.nio.file.Path f = emptyKeyStoreDir.resolve("empty.p12");
            if (!hasExpectedContents(f)) {
                java.nio.file.Path tmp = java.nio.file.Files.createTempFile(emptyKeyStoreDir, "empty-", ".tmp");
                try {
                    java.nio.file.Files.write(tmp, emptyKeyStoreBytes);
                    java.nio.file.Files.move(tmp, f, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    java.nio.file.Files.deleteIfExists(tmp);
                }
            }
            emptyKeyStore = f.toUri().toString();
            return emptyKeyStore;
        } catch (java.io.IOException | java.security.GeneralSecurityException | RuntimeException e) {
            throw sandboxError("the empty client key store of sandboxed SingleStore connections cannot be "
                + "created: %s", describe(e));
        }
    }

    //! Registers a path with deleteOnExit() once
    private static void registerDeleteOnExit(java.nio.file.Path p) {
        if (emptyKeyStoreExitPaths.add(p)) {
            p.toFile().deleteOnExit();
        }
    }

    //! Returns the owner of a path (without links), or null if the file system has no owners
    private static java.nio.file.attribute.UserPrincipal ownerOf(java.nio.file.Path p) throws java.io.IOException {
        try {
            return java.nio.file.Files.getOwner(p, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    //! Returns true if the key store directory is still the one that this process created
    private static boolean isOwnKeyStoreDir() throws java.io.IOException {
        java.nio.file.Path dir = emptyKeyStoreDir;
        if (dir == null) {
            return false;
        }
        java.nio.file.attribute.BasicFileAttributes a;
        try {
            a = java.nio.file.Files.readAttributes(dir, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException e) {
            return false;
        }
        Object key = a.fileKey() != null ? a.fileKey() : a.creationTime();
        if (!a.isDirectory() || !key.equals(emptyKeyStoreDirKey)) {
            return false;
        }
        java.nio.file.attribute.UserPrincipal owner = ownerOf(dir);
        if (owner == null ? emptyKeyStoreDirOwner != null : !owner.equals(emptyKeyStoreDirOwner)) {
            return false;
        }
        // no access for the group or others, where the file system has POSIX permissions
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                java.nio.file.Files.getPosixFilePermissions(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            for (java.nio.file.attribute.PosixFilePermission perm : perms) {
                if (!perm.name().startsWith("OWNER_")) {
                    return false;
                }
            }
        } catch (UnsupportedOperationException e) {
            // no POSIX permissions (Windows: the directory's ACL is the creator's)
        }
        return true;
    }

    //! Returns true if the key store file is a regular file with the expected contents
    private static boolean hasExpectedContents(java.nio.file.Path f) throws java.io.IOException {
        java.nio.file.attribute.BasicFileAttributes a;
        try {
            a = java.nio.file.Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException e) {
            return false;
        }
        if (!a.isRegularFile() || a.size() != emptyKeyStoreBytes.length) {
            return false;
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(emptyKeyStoreBytes.length + 1);
        try (java.nio.channels.SeekableByteChannel ch = java.nio.file.Files.newByteChannel(f,
                java.nio.file.StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            while (buf.hasRemaining() && ch.read(buf) >= 0) {
            }
        } catch (java.nio.file.NoSuchFileException e) {
            return false;
        }
        return buf.position() == emptyKeyStoreBytes.length
            && java.util.Arrays.equals(java.util.Arrays.copyOf(buf.array(), buf.position()), emptyKeyStoreBytes);
    }

    //! Returns the URI of the module's empty client key store, as ensureEmptyKeyStore() last made sure of it
    static synchronized String emptyKeyStore() {
        return emptyKeyStore;
    }

    //! the driver rewrites JDBC escapes (NativeSql.parse()) before it sends the SQL and matches a file request
    @Override
    boolean checksNativeSql() {
        return true;
    }

    @Override
    void checkSql(String sql) {
        if (hasLocalInfile(sql)) {
            throw sandboxError("SQL with LOAD DATA LOCAL INFILE cannot be executed on a sandboxed SingleStore "
                + "connection, because the driver reads client files for it");
        }
    }
}
