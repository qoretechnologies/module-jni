/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcUrlPolicy.cpp

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

#include "QoreJdbcUrlPolicy.h"

#include <set>
#include <strings.h>

namespace jni {

namespace {

//! The parameters of a driver that cannot make it read or write files, reach other destinations or load classes
/** Each name is matched exactly (or ignoring case for a driver whose parameter names are case-insensitive); a name in
    another case, a percent-encoded name and every name that is not listed is refused.  Excluded on purpose (not an
    exhaustive list, as everything that is not listed is refused): parameters that name files, URLs, key or trust
    stores (\c sslrootcert, \c sslcert, \c sslkey, \c serverRSAPublicKeyFile, \c *KeyStoreUrl, \c trustStore,
    \c serverSslCert, \c traceFile), classes or factories (\c sslfactory, \c socketFactory, \c queryInterceptors,
    \c authenticationPluginClassName, \c xmlFactoryFactory, \c trustManagerClass), local sockets and pipes
    (\c localSocket, \c pipe), other destinations (\c socksProxyHost, \c localSocketAddress, \c permitRedirect,
    \c authentication for Azure AD), external authentication that reads system files (Kerberos, GSS, JAAS, integrated
    security), service files (\c service), local infile and deserialization switches (\c allowLoadLocalInfile,
    \c allowLocalInfile, \c autoDeserialize), and configuration loading (\c useConfigs, \c propertiesTransform)
*/
struct QoreJdbcDriverPolicy {
    //! the subprotocol, matched with the "jdbc:" prefix and the "//" that follows it
    const char* subprotocol;
    //! the Java classes of the driver with the minimum driver version that the properties need:
    //! "<class>@<major>.<minor>"; see QoreJdbcRestrictedUrl::driver_classes
    std::vector<std::string> driver_classes;
    //! the parameters that the URL may set
    std::set<std::string> params;
    //! true if the driver matches parameter names ignoring case
    bool params_ignore_case;
    //! the properties of every connection; see QoreJdbcRestrictedUrl::properties
    std::vector<std::pair<std::string, std::string>> properties;
};

const QoreJdbcDriverPolicy pg_policy = {
    "postgresql",
    // 42.3: jaasLogin came in 42.2.0 and gssEncMode in 42.2.15, but the Driver API reports no patch version, so the
    // first minor version that has them all
    {"org.postgresql.Driver@42.3"},
    {
        "ApplicationName", "adaptiveFetch", "adaptiveFetchMaximum", "adaptiveFetchMinimum", "assumeMinServerVersion",
        "autosave", "binaryTransfer", "cancelSignalTimeout", "channelBinding", "cleanupSavepoints", "connectTimeout",
        "currentSchema", "defaultRowFetchSize", "disableColumnSanitiser", "escapeSyntaxCallMode",
        "groupStartupParameters", "hostRecheckSeconds", "loadBalanceHosts", "logUnclosedConnections", "loginTimeout",
        "maxResultBuffer", "password", "prepareThreshold", "preparedStatementCacheQueries",
        "preparedStatementCacheSizeMiB", "quoteReturningIdentifiers", "reWriteBatchedInserts", "readOnly",
        "readOnlyMode", "receiveBufferSize", "sendBufferSize", "socketTimeout", "ssl", "sslmode", "stringtype",
        "targetServerType", "tcpKeepAlive", "tcpNoDelay", "user",
    },
    false,
    {
        // no JAAS login, which would use the Kerberos credentials of the host for a GSS authentication that the
        // server requests, and no GSS encryption
        {"jaasLogin", "false"},
        {"gssEncMode", "disable"},
        // no SSPI (the logged-on Windows user), which the default "auto" uses when the server requests it
        {"gsslib", "gssapi"},
    },
};

const QoreJdbcDriverPolicy mysql_policy = {
    "mysql",
    // not the legacy com.mysql.jdbc.Driver class, which is also the class of the Connector/J 5.1 drivers, whose
    // properties differ
    {"com.mysql.cj.jdbc.Driver@8.0"},
    {
        "allowMultiQueries", "allowPublicKeyRetrieval", "cachePrepStmts", "characterEncoding", "characterSetResults",
        "connectTimeout", "connectionCollation", "connectionTimeZone", "defaultFetchSize", "enabledSslCipherSuites",
        "enabledTLSProtocols", "forceConnectionTimeZoneToSession", "maxAllowedPacket", "password", "prepStmtCacheSize",
        "prepStmtCacheSqlLimit", "preserveInstants", "requireSSL", "rewriteBatchedStatements", "serverTimezone",
        "socketTimeout", "sslMode", "tcpKeepAlive", "tcpNoDelay", "tcpRcvBuf", "tcpSndBuf", "tinyInt1isBit",
        "transformedBitIsBoolean", "useAffectedRows", "useCursorFetch", "useSSL", "useServerPrepStmts", "useUnicode",
        "user", "verifyServerCertificate", "yearIsDateType", "zeroDateTimeBehavior",
    },
    false,
    {
        // no LOAD DATA LOCAL INFILE, which reads local files (allowed by default before Connector/J 8.0.15)
        {"allowLoadLocalInfile", "false"},
        {"allowUrlInLocalInfile", "false"},
        // no authentication with the credentials or files of the host that the server can request: Kerberos (also
        // through LDAP SASL GSSAPI), OCI (its configuration and key files), OpenID Connect (its token file) and
        // WebAuthn; the driver has no list of the allowed authentication plugins
        {"disabledAuthenticationPlugins", "authentication_kerberos_client,authentication_ldap_sasl_client,"
            "authentication_oci_client,authentication_openid_connect_client,authentication_webauthn_client"},
    },
};

const QoreJdbcDriverPolicy mariadb_policy = {
    "mariadb",
    // 3.1: restrictedAuth came in 3.0.3, but the Driver API reports no patch version, so the first minor version
    // that has it
    {"org.mariadb.jdbc.Driver@3.1"},
    {
        "allowMultiQueries", "allowPublicKeyRetrieval", "autocommit", "cachePrepStmts", "connectTimeout",
        "connectionCollation", "defaultFetchSize", "enabledSslCipherSuites", "enabledSslProtocolSuites", "password",
        "prepStmtCacheSize", "socketTimeout", "sslMode", "tcpAbortiveClose", "tcpKeepAlive", "tcpNoDelay", "timezone",
        "transactionIsolation", "useAffectedRows", "useBulkStmts", "useServerPrepStmts", "user",
    },
    false,
    {
        // no LOAD DATA LOCAL INFILE, which reads local files and which the driver allows by default
        {"allowLocalInfile", "false"},
        // only the authentication plugins that use only the user and password of the connection: not GSSAPI, which
        // uses the Kerberos credentials of the host, when the server requests it
        {"restrictedAuth", "mysql_native_password,mysql_clear_password,dialog,client_ed25519,sha256_password,"
            "caching_sha2_password,parsec"},
    },
};

const QoreJdbcDriverPolicy sqlserver_policy = {
    "sqlserver",
    {"com.microsoft.sqlserver.jdbc.SQLServerDriver@0.0"},
    {
        // compared ignoring case: the Microsoft driver matches property names ignoring case
        "applicationintent", "applicationname", "cancelquerytimeout", "connectretrycount", "connectretryinterval",
        "database", "databasename", "delayloadinglobs", "disablestatementpooling", "encrypt", "failoverpartner",
        "hostnameincertificate", "instancename", "lastupdatecount", "locktimeout", "logintimeout", "maxresultbuffer",
        "multisubnetfailover", "packetsize", "password", "port", "portnumber", "preparemethod", "querytimeout",
        "responsebuffering", "selectmethod", "sendstringparametersasunicode", "sendtimeasdatetime", "servername",
        "servernameasace", "sockettimeout", "statementpoolingcachesize", "trustservercertificate",
        "usebulkcopyforbatchinsert", "user", "username", "workstationid", "xopenstates",
    },
    true,
    {},
};

const QoreJdbcDriverPolicy db2_policy = {
    "db2",
    {"com.ibm.db2.jcc.DB2Driver@0.0"},
    {
        "blockingReadConnectionTimeout", "clientApplicationInformation", "clientProgramName", "clientUser",
        "clientWorkstation", "currentSchema", "defaultIsolationLevel", "fullyMaterializeLobData", "keepAliveTimeOut",
        "loginTimeout", "password", "progressiveStreaming", "readOnly", "resultSetHoldability",
        "retrieveMessagesFromServerOnGetMessage", "sslConnection", "user",
    },
    false,
    {},
};

//! The classes of the Oracle thin driver, which takes no parameters in the allowed URL forms
const std::vector<std::string> oracle_driver_classes = {"oracle.jdbc.OracleDriver@0.0",
    "oracle.jdbc.driver.OracleDriver@0.0"};

bool is_host_char(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-'
        || c == '_';
}

bool is_digit(char c) {
    return c >= '0' && c <= '9';
}

//! Parses a host name, an IPv4 address or a bracketed IPv6 address at \a pos; returns false if there is none
bool parse_host(const std::string& str, size_t& pos) {
    size_t start = pos;
    if (pos < str.size() && str[pos] == '[') {
        ++pos;
        while (pos < str.size() && (is_host_char(str[pos]) || str[pos] == ':') && str[pos] != '_') {
            ++pos;
        }
        if (pos == start + 1 || pos == str.size() || str[pos] != ']') {
            return false;
        }
        ++pos;
        return true;
    }
    while (pos < str.size() && is_host_char(str[pos])) {
        ++pos;
    }
    return pos > start;
}

//! Parses an optional ":port" at \a pos; returns false if a ':' is not followed by a port number
bool parse_opt_port(const std::string& str, size_t& pos) {
    if (pos < str.size() && str[pos] == ':') {
        size_t start = ++pos;
        while (pos < str.size() && is_digit(str[pos])) {
            ++pos;
        }
        return pos > start && pos - start <= 5;
    }
    return true;
}

//! Parses a list of hosts with optional ports, separated by commas, at \a pos
bool parse_host_list(const std::string& str, size_t& pos, bool allow_list) {
    while (true) {
        if (!parse_host(str, pos) || !parse_opt_port(str, pos)) {
            return false;
        }
        if (!allow_list || pos == str.size() || str[pos] != ',') {
            return true;
        }
        ++pos;
    }
}

//! Returns true if \a str has a byte that no allowed URL may have: a control character or a non-ASCII byte
bool has_invalid_byte(const std::string& str) {
    for (char c : str) {
        unsigned char uc = static_cast<unsigned char>(c);
        if (uc < 0x20 || uc >= 0x7f) {
            return true;
        }
    }
    return false;
}

//! Checks a parameter name against the driver's allow-list
bool check_param(const QoreJdbcDriverPolicy& policy, const std::string& name, std::string& error) {
    if (policy.params_ignore_case) {
        std::string lc(name);
        for (char& c : lc) {
            if (c >= 'A' && c <= 'Z') {
                c = static_cast<char>(c - 'A' + 'a');
            }
        }
        if (policy.params.count(lc)) {
            return true;
        }
    } else if (policy.params.count(name)) {
        return true;
    }
    error = std::string("the '") + policy.subprotocol + "' JDBC URL parameter '" + name + "' is not on the list of "
        "parameters that cannot reach the filesystem, other destinations or classes";
    return false;
}

//! Checks "k=v" parameters separated by \a sep from \a pos to the end of \a str; a final separator is allowed
bool check_params(const QoreJdbcDriverPolicy& policy, const std::string& str, size_t pos, char sep,
        std::string& error) {
    while (pos < str.size()) {
        size_t end = str.find(sep, pos);
        if (end == std::string::npos) {
            end = str.size();
        }
        if (end == pos) {
            // an empty parameter (ex: "a=1&&b=2" or a final separator)
            ++pos;
            continue;
        }
        size_t eq = str.find('=', pos);
        std::string name = str.substr(pos, (eq == std::string::npos || eq > end ? end : eq) - pos);
        if (!check_param(policy, name, error)) {
            return false;
        }
        pos = end + 1;
    }
    return true;
}

//! Checks the URL of a driver with the "//hosts[/db][?k=v&k=v]" form (PostgreSQL, MySQL, MariaDB)
bool check_query_url(const QoreJdbcDriverPolicy& policy, const std::string& rest, std::string& error,
        bool allow_host_list) {
    // rest follows "jdbc:<subprotocol>:"
    if (rest.compare(0, 2, "//")) {
        error = std::string("only the '//host' network form of '") + policy.subprotocol + "' JDBC URLs can be used";
        return false;
    }
    size_t pos = 2;
    if (!parse_host_list(rest, pos, allow_host_list)) {
        error = std::string("the host of the '") + policy.subprotocol + "' JDBC URL must be a host name or address "
            "with an optional port";
        return false;
    }
    if (pos < rest.size() && rest[pos] == '/') {
        // the database name, up to the parameters
        size_t q = rest.find('?', pos);
        size_t db_end = q == std::string::npos ? rest.size() : q;
        for (size_t i = pos + 1; i < db_end; ++i) {
            char c = rest[i];
            if (c == '/' || c == '#' || c == '(' || c == ')' || c == '@' || c == ';' || c == '\\') {
                error = std::string("the database name of the '") + policy.subprotocol + "' JDBC URL has a '"
                    + c + "' character";
                return false;
            }
        }
        pos = db_end;
    }
    if (pos == rest.size()) {
        return true;
    }
    if (rest[pos] != '?') {
        error = std::string("the host of the '") + policy.subprotocol + "' JDBC URL must be a host name or address "
            "with an optional port";
        return false;
    }
    if (rest.find('#', pos) != std::string::npos) {
        error = std::string("the '") + policy.subprotocol + "' JDBC URL has a fragment ('#')";
        return false;
    }
    return check_params(policy, rest, pos + 1, '&', error);
}

//! Checks a SQL Server URL: "//[host[\\instance][:port]][;k=v...]"
/** Braced values (\c "{a;b}") are refused: the drivers have parsed them differently across versions, which could make
    a value a parameter of its own; a password with such characters is given as the password of the datasource
*/
bool check_sqlserver_url(const std::string& rest, std::string& error) {
    const QoreJdbcDriverPolicy& policy = sqlserver_policy;
    if (rest.compare(0, 2, "//")) {
        error = "only the '//host' network form of 'sqlserver' JDBC URLs can be used";
        return false;
    }
    if (rest.find_first_of("{}") != std::string::npos) {
        error = "'sqlserver' JDBC URLs with braced values cannot be used; give the password as the password of the "
            "datasource";
        return false;
    }
    size_t pos = 2;
    if (pos < rest.size() && rest[pos] != ';') {
        if (!parse_host(rest, pos)) {
            error = "the host of the 'sqlserver' JDBC URL must be a host name or address with an optional instance "
                "name and port";
            return false;
        }
        if (pos < rest.size() && rest[pos] == '\\') {
            size_t start = ++pos;
            while (pos < rest.size() && is_host_char(rest[pos]) && rest[pos] != '.') {
                ++pos;
            }
            if (pos == start) {
                error = "the instance name of the 'sqlserver' JDBC URL is empty or invalid";
                return false;
            }
        }
        if (!parse_opt_port(rest, pos)) {
            error = "the port of the 'sqlserver' JDBC URL is invalid";
            return false;
        }
    }
    if (pos < rest.size() && rest[pos] != ';') {
        error = "the host of the 'sqlserver' JDBC URL must be a host name or address with an optional instance name "
            "and port";
        return false;
    }
    while (pos < rest.size()) {
        assert(rest[pos] == ';');
        ++pos;
        if (pos == rest.size() || rest[pos] == ';') {
            continue;
        }
        size_t eq = rest.find('=', pos);
        size_t semi = rest.find(';', pos);
        if (eq == std::string::npos || (semi != std::string::npos && semi < eq)) {
            error = "a 'sqlserver' JDBC URL parameter has no value";
            return false;
        }
        // the driver ignores the spaces around a name
        size_t ns = pos, ne = eq;
        while (ns < ne && rest[ns] == ' ') {
            ++ns;
        }
        while (ne > ns && rest[ne - 1] == ' ') {
            --ne;
        }
        if (!check_param(policy, rest.substr(ns, ne - ns), error)) {
            return false;
        }
        pos = semi == std::string::npos ? rest.size() : semi;
    }
    return true;
}

//! Checks a DB2 URL: "//host[:port]/database[:k=v;k=v;]"
bool check_db2_url(const std::string& rest, std::string& error) {
    const QoreJdbcDriverPolicy& policy = db2_policy;
    if (rest.compare(0, 2, "//")) {
        error = "only the '//host' network form of 'db2' JDBC URLs can be used";
        return false;
    }
    size_t pos = 2;
    if (!parse_host(rest, pos) || !parse_opt_port(rest, pos) || pos == rest.size() || rest[pos] != '/') {
        error = "the 'db2' JDBC URL must have the form '//host[:port]/database'";
        return false;
    }
    size_t start = ++pos;
    while (pos < rest.size() && (is_host_char(rest[pos]) || rest[pos] == '$' || rest[pos] == '#'
            || rest[pos] == '@')) {
        ++pos;
    }
    if (pos == start) {
        error = "the 'db2' JDBC URL has no database name";
        return false;
    }
    if (pos == rest.size()) {
        return true;
    }
    if (rest[pos] != ':') {
        error = "the database name of the 'db2' JDBC URL is invalid";
        return false;
    }
    return check_params(policy, rest, pos + 1, ';', error);
}

//! Checks an Oracle thin URL in the EZConnect form: "[user[/password]]@host:port:sid", "@[//]host[:port]/service"
bool check_oracle_url(const std::string& rest, std::string& error) {
    // rest follows "jdbc:oracle:"
    if (rest.compare(0, 5, "thin:")) {
        error = "only the 'thin' driver can be used for 'oracle' JDBC URLs";
        return false;
    }
    size_t at = rest.find('@', 5);
    if (at == std::string::npos) {
        error = "the 'oracle' JDBC URL has no '@' before its destination";
        return false;
    }
    // "[user[/password]]": names only, so that nothing before the '@' can be parsed as anything but the user and
    // password; a password with other characters is given as the password of the datasource
    {
        bool slash = false;
        for (size_t i = 5; i < at; ++i) {
            char c = rest[i];
            if (c == '/' && !slash && i > 5 && i + 1 < at) {
                slash = true;
                continue;
            }
            if (!is_host_char(c) && c != '$' && c != '#') {
                error = std::string("the user or password of the 'oracle' JDBC URL has a '") + c + "' character";
                return false;
            }
        }
    }
    std::string dest = rest.substr(at + 1);
    if (dest.find('?') != std::string::npos) {
        error = "'oracle' JDBC URLs with parameters cannot be used";
        return false;
    }
    if (!dest.empty() && dest[0] == '(') {
        error = "'oracle' JDBC URLs with a connect descriptor cannot be used";
        return false;
    }
    size_t pos = 0;
    bool slashes = !dest.compare(0, 2, "//");
    if (slashes) {
        pos = 2;
    }
    if (!parse_host(dest, pos)) {
        error = "the host of the 'oracle' JDBC URL must be a host name or address";
        return false;
    }
    if (pos == dest.size()) {
        // a TNS alias, a protocol ("tcps://", "ldap://") or another form
        error = "'oracle' JDBC URLs with a TNS alias cannot be used; use '@host:port:sid' or '@host[:port]/service'";
        return false;
    }
    bool has_port = false;
    if (dest[pos] == ':') {
        size_t p = pos;
        if (!parse_opt_port(dest, p)) {
            error = "'oracle' JDBC URLs with a protocol cannot be used; use '@host:port:sid' or "
                "'@host[:port]/service'";
            return false;
        }
        pos = p;
        has_port = true;
    }
    auto is_name_char = [](char c) { return is_host_char(c) || c == '$' || c == '#'; };
    if (pos < dest.size() && dest[pos] == ':' && has_port && !slashes) {
        // host:port:sid
        size_t start = ++pos;
        while (pos < dest.size() && is_name_char(dest[pos])) {
            ++pos;
        }
        if (pos == start || pos != dest.size()) {
            error = "the SID of the 'oracle' JDBC URL is invalid";
            return false;
        }
        return true;
    }
    if (pos < dest.size() && dest[pos] == '/') {
        // host[:port]/service[:server_type][/instance]
        size_t start = ++pos;
        while (pos < dest.size() && is_name_char(dest[pos])) {
            ++pos;
        }
        if (pos == start) {
            error = "the service name of the 'oracle' JDBC URL is invalid";
            return false;
        }
        if (pos < dest.size() && dest[pos] == ':') {
            size_t s = ++pos;
            while (pos < dest.size() && is_name_char(dest[pos])) {
                ++pos;
            }
            if (pos == s) {
                error = "the server type of the 'oracle' JDBC URL is invalid";
                return false;
            }
        }
        if (pos < dest.size() && dest[pos] == '/') {
            size_t s = ++pos;
            while (pos < dest.size() && is_name_char(dest[pos])) {
                ++pos;
            }
            if (pos == s) {
                error = "the instance name of the 'oracle' JDBC URL is invalid";
                return false;
            }
        }
        if (pos != dest.size()) {
            error = "the service name of the 'oracle' JDBC URL is invalid";
            return false;
        }
        return true;
    }
    error = "only the '@host:port:sid' and '@host[:port]/service' forms of 'oracle' JDBC URLs can be used";
    return false;
}

}

bool qore_jdbc_check_restricted_url(const std::string& url, QoreJdbcRestrictedUrl& allowed, std::string& error) {
    allowed = QoreJdbcRestrictedUrl();
    if (has_invalid_byte(url)) {
        error = "the JDBC URL has a control character or a non-ASCII character";
        return false;
    }
    // the URL as the driver gets it
    size_t start = !url.compare(0, 5, "jdbc:") ? 5 : 0;
    size_t colon = url.find(':', start);
    std::string subprotocol = url.substr(start, colon == std::string::npos ? std::string::npos : colon - start);
    if (colon == std::string::npos) {
        error = "the JDBC URL has no subprotocol";
        return false;
    }
    std::string rest = url.substr(colon + 1);

    bool ok;
    if (subprotocol == "postgresql") {
        ok = check_query_url(pg_policy, rest, error, true);
        if (ok) {
            allowed.driver_classes = pg_policy.driver_classes;
            allowed.properties = pg_policy.properties;
        }
    } else if (subprotocol == "mysql") {
        ok = check_query_url(mysql_policy, rest, error, true);
        if (ok) {
            allowed.driver_classes = mysql_policy.driver_classes;
            allowed.properties = mysql_policy.properties;
        }
    } else if (subprotocol == "mariadb") {
        ok = check_query_url(mariadb_policy, rest, error, true);
        if (ok) {
            allowed.driver_classes = mariadb_policy.driver_classes;
            allowed.properties = mariadb_policy.properties;
        }
    } else if (subprotocol == "sqlserver") {
        ok = check_sqlserver_url(rest, error);
        if (ok) {
            allowed.driver_classes = sqlserver_policy.driver_classes;
            allowed.properties = sqlserver_policy.properties;
        }
    } else if (subprotocol == "db2") {
        ok = check_db2_url(rest, error);
        if (ok) {
            allowed.driver_classes = db2_policy.driver_classes;
            allowed.properties = db2_policy.properties;
        }
    } else if (subprotocol == "oracle") {
        ok = check_oracle_url(rest, error);
        if (ok) {
            allowed.driver_classes = oracle_driver_classes;
        }
    } else {
        error = "the '" + subprotocol + "' JDBC driver is not on the list of client/server drivers that cannot reach "
            "the filesystem, other destinations or classes";
        return false;
    }
    return ok;
}

}
