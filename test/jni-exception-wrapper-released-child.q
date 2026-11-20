#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-exception-wrapper-released.qtest: Java keeps an org.qore.jni.QoreExceptionWrapper after its Qore
# exception has been released, and uses it (see org.qore.jni.test.ExceptionWrapperRelease)
#
# the argument names the case:
# - "live": the message of a wrapper whose exception has not been released
# - "taken": the message of a wrapper that has been thrown back to Qore, which took its exception
# - "taken-rethrow": a wrapper that has been thrown back to Qore is thrown to Qore again
# - "describe": the module describes the wrapper as a pending Java exception (JavaException::toString()), which must
#   neither take nor leak its exception (debug builds only: Jni::dbg_jni_describe_throwable())
# - "shutdown": the message of a wrapper made before the module's shutdown, read in a JVM shutdown hook, after the
#   module's shutdown has released the exception
# - "window": as "shutdown", with a wrapper that the constructor of a generated class raises in the window of the
#   module's shutdown between the deletion of the global context Program and the destruction of the JVM (see
#   Jni::dbg_jni_set_shutdown_window_hook() and org.qore.jni.test.ShutdownWindowProbe); debug builds only
# it prints "debug: <bool>" (whether the module is a debug build), then the result of each step, and "after: <case>"

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.ExceptionWrapperRelease
%module-cmd(jni) import org.qore.jni.test.ShutdownWindowProbe

sub released_test_raise() {
    throw "RELEASED-TEST-ERROR", "the description";
}

sub out(string label, string result) {
    printf("%s: %s\n", label, result);
    flush();
}

#! throws the wrapper kept to Qore, and returns the code and the description of the Qore exception
string sub throw_kept() {
    try {
        ExceptionWrapperRelease::throwKept();
    } catch (hash<ExceptionInfo> ex) {
        return sprintf("%s: %s", ex.err, ex.desc);
    }
    return "no exception";
}

string case_name = ARGV[0] ?? "live";

bool debug = False;
try {
    call_function("Jni::org::qore::jni::dbg_jni_context_program_counts");
    debug = True;
} catch (hash<ExceptionInfo> ex) {
    # the diagnostics exist in debug builds only
}
out("debug", sprintf("%y", debug));

switch (case_name) {
    case "live":
        out("call", ExceptionWrapperRelease::callAndKeep("released_test_raise"));
        out("message", ExceptionWrapperRelease::describe());
        break;

    case "taken":
        out("call", ExceptionWrapperRelease::callAndKeep("released_test_raise"));
        out("rethrow", throw_kept());
        out("message", ExceptionWrapperRelease::describe());
        break;

    case "taken-rethrow":
        out("call", ExceptionWrapperRelease::callAndKeep("released_test_raise"));
        out("rethrow", throw_kept());
        out("rethrow-again", throw_kept());
        break;

    case "describe":
        # the module describes the wrapper as a pending Java exception twice; it keeps its exception, which Qore then
        # takes (debug builds only)
        if (debug) {
            out("call", ExceptionWrapperRelease::callAndKeep("released_test_raise"));
            out("describe", call_function("Jni::org::qore::jni::dbg_jni_describe_throwable",
                ExceptionWrapperRelease::getKept()));
            out("describe-again", call_function("Jni::org::qore::jni::dbg_jni_describe_throwable",
                ExceptionWrapperRelease::getKept()));
            out("rethrow", throw_kept());
        }
        break;

    case "shutdown":
        out("call", ExceptionWrapperRelease::callAndKeep("released_test_raise"));
        ExceptionWrapperRelease::describeAtShutdown();
        break;

    case "window":
        if (debug) {
            # the probe is kept by Java
            ShutdownWindowProbe probe("generated-late", "qore.Qore.Thread.Mutex");
            call_function("Jni::org::qore::jni::dbg_jni_set_shutdown_window_hook", probe);
            ExceptionWrapperRelease::describeAtShutdown();
        }
        break;

    default:
        printf("unknown case: %s\n", case_name);
        exit(1);
}
printf("after: %s\n", case_name);
