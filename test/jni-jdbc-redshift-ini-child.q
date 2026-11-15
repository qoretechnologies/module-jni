#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# The child process of rsIniFileTest in jni-jdbc-driver-sandbox.qtest (qore#5786): opens one Redshift connection,
# trusted or sandboxed, in a JVM whose implicit INI file locations (AMAZON_REDSHIFT_JDBC_INI_FILE, user.home and
# java.io.tmpdir) the parent set to private directories of its own; prints "RESULT: <err>: <desc>" or "RESULT: ok"
#
# usage: jni-jdbc-redshift-ini-child.q trusted|sandboxed <url> <port>

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni

%exec-class JniJdbcRedshiftIniChild

class JniJdbcRedshiftIniChild {
    private:internal {
        const Source = "
string sub open_db(string url) {
    try {
        Datasource ds({\"type\": \"jdbc\", \"user\": \"u\", \"pass\": \"pw\", \"db\": url});
        ds.open();
        ds.close();
        return \"ok\";
    } catch (hash<ExceptionInfo> ex) {
        return ex.err + \": \" + ex.desc;
    }
}
";
    }

    constructor() {
        string mode = ARGV[0];
        string url = ARGV[1];
        int port = ARGV[2].toInt();
        Program::issueModuleCmd("jni", "global-add-classpath " + ENV.QORE_JDBC_REDSHIFT_JAR);
        Program p(PO_NEW_STYLE);
        if (mode == "sandboxed") {
            SandboxManager sm();
            sm.setNetworkDefaultPolicy(False);
            sm.addNetworkAllowedHost("127.0.0.1");
            sm.addNetworkAllowedPort(port, QSEC_NET_TCP);
            p.setSandboxManager(sm);
        }
        p.parse(Source, "jni-jdbc-redshift-ini-child");
        string result = p.callFunction("open_db", url);
        printf("RESULT: %s\n", replace(result, "\n", " "));
    }
}
