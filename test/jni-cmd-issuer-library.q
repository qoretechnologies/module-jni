#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# jni-cmd-issuer-library.q: run by jni-module-cmd-issuer.qtest in a process of its own, with the fixture modules'
# directory in QORE_MODULE_DIR, so that the fixture modules are library code and the Program of the issuer kind given
# is the first to load them; prints the result
# Copyright (C) 2026 Qore Technologies, s.r.o.

# usage: jni-cmd-issuer-library.q <kind> <case>
#   kind: trusted | no-uncontrolled | no-filesystem | no-embedded | sandboxed
#   case: cmd (JniCmdIssuerFixture issues add-classpath) | jdbc (JniJdbcOptionFixture sets the jdbc classpath option)
#         | directive (loads JniCmdDirectiveFixture, which has a jni directive)

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%prepend-module-path "${SCRIPT_DIR}/../qlib"

const Kinds = {
    "trusted": 0,
    "no-uncontrolled": PO_NO_UNCONTROLLED_APIS,
    "no-filesystem": PO_NO_FILESYSTEM,
    "no-embedded": PO_NO_EMBEDDED_LOGIC,
    "sandboxed": -1,
};

string kind = ARGV[0] ?? "";
string which = ARGV[1] ?? "";
if (!Kinds.hasKey(kind)) {
    printf("unknown issuer kind %y\n", kind);
    exit(2);
}

int po = Kinds{kind};
Program p(PO_NEW_STYLE | (po > 0 ? po : 0));
if (po < 0) {
    p.setSandboxManager(new SandboxManager());
}

try {
    switch (which) {
        case "cmd":
            p.loadModule("JniCmdIssuerFixture");
            p.parse("string sub run() { return JniCmdIssuerFixture::issue(\"add-classpath "
                "/nonexistent-qore-test-dir\"); }", "jni-cmd-issuer-library-cmd");
            printf("%s\n", p.callFunction("run"));
            break;
        case "jdbc":
            p.loadModule("JniJdbcOptionFixture");
            p.parse("string sub run() { return JniJdbcOptionFixture::openJdbc(\"/nonexistent-qore-test-dir\"); }",
                "jni-cmd-issuer-library-jdbc");
            printf("%s\n", p.callFunction("run"));
            break;
        case "directive":
            p.loadModule("JniCmdDirectiveFixture");
            printf("ok\n");
            break;
        default:
            printf("unknown case %y\n", which);
            exit(2);
    }
} catch (hash<ExceptionInfo> ex) {
    printf("%s: %s\n", ex.err, ex.desc);
}
