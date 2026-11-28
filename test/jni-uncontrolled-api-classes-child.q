#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# jni-uncontrolled-api-classes-child.q: loads a fixture library module first from a Program with the restrictions of
# the kind given and runs its Java code, for jni-uncontrolled-api-classes.qtest (qore#7832)
#
# usage: jni-uncontrolled-api-classes-child.q <module> <kind>
# prints the result as its last line: "ok", or "<err>: <desc>"
%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%prepend-module-path "${SCRIPT_DIR}/../qlib"
%requires jni

const Kinds = {
    "none": 0,
    "no-uncontrolled": PO_NO_UNCONTROLLED_APIS,
    "no-filesystem": PO_NO_FILESYSTEM,
    "no-network": PO_NO_NETWORK,
    "no-external-process": PO_NO_EXTERNAL_PROCESS,
    "no-process-control": PO_NO_PROCESS_CONTROL,
    "no-process-thread-control": PO_NO_PROCESS_CONTROL | PO_NO_THREAD_CONTROL,
};

const Modules = ("JniUcTrustedLibFixture", "JniUcLibFixture");

string module = ARGV[0] ?? "";
string kind = ARGV[1] ?? "";
if (!inlist(module, Modules) || !Kinds.hasKey(kind)) {
    printf("usage: %s <%s> <%s>\n", get_script_name(), Modules.join("|"), (keys Kinds).join("|"));
    exit(2);
}

Program p(PO_NEW_STYLE | Kinds{kind});
try {
    p.loadModule(module);
    p.parse(sprintf("string sub t() { return %s::run(); }", module), "jni-uncontrolled-api-classes-child");
    printf("%s\n", p.callFunction("t"));
} catch (hash<ExceptionInfo> ex) {
    printf("%s: %s\n", ex.err, ex.desc);
}
