#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# jni-uncontrolled-api-classes-child.q: loads a fixture library module first from a Program with the restrictions of
# the kind given and runs its Java code, for jni-uncontrolled-api-classes.qtest (qore#7832)
#
# usage: jni-uncontrolled-api-classes-child.q <module> <kind> [first-load|caller-only|sandboxed-caller
#     <restricting|open>]
# prints the result as its last line: "ok", or "<err>: <desc>"
# with "first-load", the Program of the kind only loads the module first, and this (trusted) script calls its code;
# with "caller-only", this script loads the module first, and the Program of the kind only calls its code; with
# "sandboxed-caller", as "caller-only", with a sandbox that trusts the Program with uncontrolled APIs and restricts the
# filesystem and the network ("restricting": a new manager) or not ("open")
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
    switch (ARGV[2] ?? "") {
        case "caller-only":
            # this (trusted) script loads the module first; the Program of the kind only calls its code
            load_module(module);
            p.loadModule(module);
            p.parse(sprintf("string sub t() { return %s::run(); }", module), "jni-uncontrolled-api-classes-child");
            printf("%s\n", p.callFunction("t"));
            break;
        case "sandboxed-caller": {
            load_module(module);
            SandboxManager sm();
            sm.setUncontrolledApiPolicy(True);
            if (ARGV[3] == "open") {
                sm.setFilesystemDefaultPolicy(True);
                sm.setNetworkDefaultPolicy(True);
            }
            p.loadModule(module);
            p.parse(sprintf("string sub t() { return %s::run(); }", module), "jni-uncontrolled-api-classes-child");
            p.setSandboxManager(sm);
            printf("%s\n", p.callFunction("t"));
            break;
        }
        case "first-load":
            # the Program of the kind only loads the module first; this (trusted) script calls its code
            p.loadModule(module);
            load_module(module);
            printf("%s\n", call_function(module + "::run"));
            break;
        default:
            p.loadModule(module);
            p.parse(sprintf("string sub t() { return %s::run(); }", module), "jni-uncontrolled-api-classes-child");
            printf("%s\n", p.callFunction("t"));
            break;
    }
} catch (hash<ExceptionInfo> ex) {
    printf("%s: %s\n", ex.err, ex.desc);
}
