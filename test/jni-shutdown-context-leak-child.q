#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-shutdown-context-leak.qtest (qore#6799): the module's shutdown at process exit deletes the global
# Java context Program, whose deletion attaches the main thread to the JVM to release the Program's class loader, if
# the main thread was never attached (all jni work ran in other threads); the context of that attachment was the
# global context Program, which was created again then and never deleted
#
# the argument names the case:
# - "background": the jni work runs in a background thread, and the main thread is never attached to the JVM
# - "main": the same work runs in the main thread, which is attached to the JVM when the module is shut down
# the work loads the module in a Program, calls Java code, and deletes the Program; it prints "debug: <bool>" (whether
# the module is a debug build, with the Jni::dbg_jni_context_program_counts() diagnostic), then
# "counts: <created>/<deleted>" in a debug build (the global context Programs created and deleted so far), and
# "result: <value>"; then the main thread prints "after: <case>" and exits with status 0; with the environment
# variable QORE_JNI_DBG_CONTEXT_PROGRAM_REPORT set, a debug build of the module prints the counts at its shutdown on
# stderr (see dbg_jni_report_context_programs())

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"

#! the code of the Program that loads the jni module
const Src = "
string sub get() {
    return (new java::lang::StringBuilder(\"abc\")).append(\"def\").toString();
}

*hash<auto> sub counts() {
    try {
        return call_function(\"Jni::org::qore::jni::dbg_jni_context_program_counts\");
    } catch (hash<ExceptionInfo> ex) {
        # the diagnostic exists in debug builds only
    }
}
";

#! loads the module in a Program, calls Java code, and deletes the Program
sub work() {
    try {
        Program p(PO_NEW_STYLE);
        p.loadModule("jni");
        p.parse(Src, "jni-shutdown-context-leak");
        *hash<auto> counts = p.callFunction("counts");
        printf("debug: %y\n", exists counts);
        if (counts) {
            printf("counts: %d/%d\n", counts.created, counts.deleted);
        }
        printf("result: %s\n", p.callFunction("get"));
        delete p;
    } catch (hash<ExceptionInfo> ex) {
        printf("caught: %s: %s\n", ex.err, ex.desc);
    }
}

string case_name = ARGV[0] ?? "background";
if (case_name == "main") {
    work();
} else {
    Counter done(1);
    background sub () {
        on_exit {
            done.dec();
        }
        work();
    }();
    done.waitForZero();
}
printf("after: %s\n", case_name);
