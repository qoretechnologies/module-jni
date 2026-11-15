/*
    RedshiftProfile.java

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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

//! The rules of Amazon Redshift JDBC 2.3.0 for restricted connections (qore#5786)
/** Only <tt>jdbc:redshift://host[:port][,host[:port]...]/database[?|;options]</tt> with user and password
    authentication.  The driver's URL splitting (\c Driver.parseURL()): the server part ends at the first \c ?, or
    at the first \c ; if there is no \c ?; the options are split on \c ; and \c &; keys are not URL-decoded, values
    are.  Options of the URL override the given properties, so every forced property is checked again on the
    driver's own parse.
*/
final class RedshiftProfile extends JdbcDriverProfile {
    static final RedshiftProfile INSTANCE = new RedshiftProfile();

    static final String PREFIX = "jdbc:redshift://";
    //! the default port of the driver
    static final int DEFAULT_PORT = 5439;

    //! the options that affect only the protocol or the session, local memory or timeouts (lowercase)
    private static final Set<String> ALLOWED = Set.of(
        "applicationname", "assumeminserverversion", "autosave", "binarytransfer", "binarytransferenable",
        "binarytransferdisable", "blockingrowsmode", "cancelsignaltimeout", "cleanupsavepoints", "compression",
        "connecttimeout", "connectiontimezone", "currentschema", "databasemetadatacachefields",
        "databasemetadatacachefieldsmib", "databasemetadatacurrentdbonly", "defaultrowfetchsize",
        "disablecolumnsanitiser", "disableisvalidquery", "enablefetchringbuffer", "enablegeneratedname",
        "enablemultisqlsupport", "enablestatementcache", "enabletabletypes", "escapesyntaxcallmode",
        "fetchringbuffersize", "hideunprivilegedobjects", "hostrecheckseconds", "loadbalancehosts", "logintimeout",
        "logservererrordetail", "maxresultbuffer", "maxwarningcount", "options", "overrideschemapatterntype",
        "preferquerymode", "preparedstatementcachequeries", "preparedstatementcachesizemib", "preparethreshold",
        "protocolversion", "querygroup", "raiseexceptiononsilentrollback", "readonly", "readonlymode",
        "receivebuffersize", "rewritebatchedinserts", "rewritebatchedinsertssize", "sendbuffersize",
        "sockettimeout", "ssl", "sslmode", "statementclosetimeout", "stringtype", "targetservertype",
        "tcpkeepalive", "user", "password", "uid", "pwd", "dbname");

    //! the keys that the driver's parse adds besides the allowed and forced ones
    private static final Set<String> PARSE_KEYS = Set.of("host", "port", "dbname", "iamauth");

    //! the forced keys (lowercase)
    private static final String SOCKET_FACTORY = "socketfactory";
    private static final String SOCKET_FACTORY_ARG = "socketfactoryarg";
    private static final String INI_FILE = "inifile";

    //! PROVISIONAL: the only sslmode of restricted connections (design section 11, item 9)
    private static final String SSL_MODE = "verify-full";

    private RedshiftProfile() {
    }

    @Override
    String name() {
        return "redshift";
    }

    @Override
    String driverClassName() {
        return "com.amazon.redshift.Driver";
    }

    @Override
    String auditedVersion() {
        // PROVISIONAL: the exact audited version (design section 11, item 6)
        return "2.3.0";
    }

    @Override
    String driverVersion(Class<?> driverClass) throws ReflectiveOperationException {
        Class<?> info = Class.forName("com.amazon.redshift.util.DriverInfo", true, driverClass.getClassLoader());
        if (info.getClassLoader() != driverClass.getClassLoader()) {
            throw new ClassNotFoundException("the driver's DriverInfo comes from another class loader");
        }
        return String.valueOf(info.getField("DRIVER_VERSION").get(null));
    }

    //! Returns the empty file that the forced IniFile names, so that the driver searches for no INI file
    /** PROVISIONAL (design section 11, item 5): without \c /dev/null (Windows), restricted Redshift connections are
        refused.
    */
    static String iniFile() {
        File f = new File("/dev/null");
        if (File.separatorChar != '/' || !f.exists()) {
            throw deny("sandboxed Redshift connections need an empty INI file, and this platform has no /dev/null");
        }
        return f.getPath();
    }

    @Override
    JdbcTargets lexicalCheck(String url, Properties props) {
        if (!url.startsWith(PREFIX)) {
            throw deny("only jdbc:redshift://host[:port][,host[:port]...]/database URLs with user and password "
                + "authentication are supported for sandboxed connections");
        }
        // the driver's split: the server part ends at the first '?', or at the first ';' if there is no '?'
        String server = url;
        String args = "";
        int q = url.indexOf('?');
        if (q < 0) {
            q = url.indexOf(';');
        }
        if (q >= 0) {
            server = url.substring(0, q);
            args = url.substring(q + 1);
        }
        String rest = server.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw deny("a sandboxed Redshift connection URL must name its database: %s", quote(url));
        }
        JdbcTargets targets = new JdbcTargets();
        for (String address : rest.substring(0, slash).split(",", -1)) {
            int portIdx = address.lastIndexOf(':');
            String host;
            int port;
            if (portIdx != -1 && address.lastIndexOf(']') < portIdx) {
                host = address.substring(0, portIdx);
                port = parsePort(address.substring(portIdx + 1), url);
            } else {
                host = address;
                port = DEFAULT_PORT;
            }
            checkHost(host, url);
            targets.add(host, port);
        }

        Set<String> seen = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            checkOption(key, seen);
        }
        for (String token : args.split("[;&]", -1)) {
            if (token.isEmpty()) {
                // the driver skips empty tokens
                continue;
            }
            int pos = token.indexOf('=');
            if (pos < 0) {
                // the driver sets the key to "" (ex: a bare "inifile" reopens the implicit INI file search)
                throw deny("option %s of a sandboxed connection URL has no value", quote(token));
            }
            checkOption(token.substring(0, pos), seen);
        }
        return targets;
    }

    //! checks an option key against the allowlist and for duplicates
    private static void checkOption(String key, Set<String> seen) {
        checkKey(key);
        String lk = key.toLowerCase(Locale.ENGLISH);
        if (!ALLOWED.contains(lk)) {
            throw deny("option %s is not allowed for sandboxed Redshift connections", quote(key));
        }
        if (!seen.add(lk)) {
            throw deny("option %s is given more than once", quote(key));
        }
    }

    @Override
    void checkClasses(Class<?> driverClass) throws ReflectiveOperationException {
        // the driver loads the factory by name from its own class loader (ObjectFactory, ClassUtils.forName())
        Class<?> c = Class.forName(RedshiftGuardSocketFactory.class.getName(), false, driverClass.getClassLoader());
        if (c != RedshiftGuardSocketFactory.class) {
            throw sandboxError("the class loader of the Redshift driver has its own %s, which a sandboxed connection "
                + "cannot use", RedshiftGuardSocketFactory.class.getName());
        }
    }

    @Override
    Properties force(Properties props, JdbcConnectGuard guard, String token) {
        Properties f = new Properties();
        for (String key : props.stringPropertyNames()) {
            f.setProperty(key, props.getProperty(key));
        }
        f.setProperty("socketFactory", RedshiftGuardSocketFactory.class.getName());
        f.setProperty("socketFactoryArg", token);
        f.setProperty("IniFile", iniFile());
        // PROVISIONAL: TLS with host name validation also if the driver's default changes; the URL may give the same
        // value only
        f.setProperty("sslmode", SSL_MODE);
        return f;
    }

    //! The default properties of the driver, as Driver.getDefaultProperties() loads them
    private static Properties defaults(ClassLoader cl) throws ReflectiveOperationException {
        Properties merged = new Properties();
        String user = System.getProperty("user.name");
        if (user != null) {
            merged.setProperty("user", user);
        }
        try {
            List<URL> urls = new ArrayList<>();
            Enumeration<URL> e = cl.getResources("com/amazon/redshift/driverconfig.properties");
            while (e.hasMoreElements()) {
                urls.add(e.nextElement());
            }
            for (int i = urls.size() - 1; i >= 0; --i) {
                try (InputStream is = urls.get(i).openStream()) {
                    merged.load(is);
                }
            }
        } catch (IOException e) {
            throw deny("the default settings of the Redshift driver cannot be read: %s", describe(e));
        }
        return merged;
    }

    @Override
    JdbcTargets effectiveCheck(Class<?> driverClass, String url, Properties forced, JdbcConnectGuard guard,
            String token, JdbcTargets lexical) throws ReflectiveOperationException {
        ClassLoader cl = driverClass.getClassLoader();
        Class<?> rpc = Class.forName("com.amazon.redshift.util.RedshiftProperties", false, cl);
        Object rp = rpc.getConstructor(Properties.class, Properties.class).newInstance(forced, defaults(cl));
        Method parse = driverClass.getMethod("parseURL", String.class, rpc);
        Properties res = (Properties)parse.invoke(null, url, rp);
        if (res == null) {
            throw deny("the Redshift driver does not accept the URL %s", quote(url));
        }
        for (String key : res.stringPropertyNames()) {
            String lk = key.toLowerCase(Locale.ENGLISH);
            if (!ALLOWED.contains(lk) && !PARSE_KEYS.contains(lk) && !lk.equals(SOCKET_FACTORY)
                    && !lk.equals(SOCKET_FACTORY_ARG) && !lk.equals(INI_FILE)) {
                throw deny("the Redshift driver's parse of the URL gives option %s, which is not allowed for "
                    + "sandboxed connections", quote(key));
            }
        }
        // the driver's Properties lowercase the keys
        requireValue(res, SOCKET_FACTORY, RedshiftGuardSocketFactory.class.getName());
        requireValue(res, SOCKET_FACTORY_ARG, token);
        requireValue(res, INI_FILE, iniFile());
        requireValue(res, "iamauth", "false");
        String v = res.getProperty("sslmode");
        if (!SSL_MODE.equals(v)) {
            throw deny("option sslmode value %s is not allowed for sandboxed Redshift connections; only %s is", quote(v),
                SSL_MODE);
        }
        v = res.getProperty("ssl");
        if (v != null && !v.equalsIgnoreCase("true")) {
            throw deny("option ssl value %s is not allowed for sandboxed Redshift connections: TLS is required",
                quote(v));
        }
        String hosts = res.getProperty("host");
        String ports = res.getProperty("port");
        if (hosts == null || ports == null) {
            throw deny("the Redshift driver's parse of the URL has no destination");
        }
        String[] h = hosts.split(",", -1);
        String[] p = ports.split(",", -1);
        if (h.length != p.length) {
            throw deny("the Redshift driver's parse of the URL has %d hosts and %d ports", h.length, p.length);
        }
        JdbcTargets rv = new JdbcTargets();
        for (int i = 0; i < h.length; ++i) {
            rv.add(h[i], parsePort(p[i], url));
        }
        if (rv.size() != lexical.size()) {
            throw deny("the Redshift driver's parse of the URL gives other destinations (%s) than the URL (%s)", rv,
                lexical);
        }
        for (int i = 0; i < rv.size(); ++i) {
            if (!rv.hosts.get(i).equals(lexical.hosts.get(i)) || !rv.ports.get(i).equals(lexical.ports.get(i))) {
                throw deny("the Redshift driver's parse of the URL gives other destinations (%s) than the URL (%s)",
                    rv, lexical);
            }
        }
        return rv;
    }

    //! requires an option of the parse to have the given value
    private static void requireValue(Properties res, String key, String value) {
        String v = res.getProperty(key);
        if (!value.equals(v)) {
            throw deny("the Redshift driver's parse of the URL changes option %s, which a sandboxed connection must "
                + "not change", key);
        }
    }
}
