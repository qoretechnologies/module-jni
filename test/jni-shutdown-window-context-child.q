#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-shutdown-window-context.qtest: a Java thread calls into Qore exactly in the window of the module's
# shutdown between the deletion of the global Java context Program (Globals::clearGlobalContext()) and the
# destruction of the JVM, held there by the hook set with Jni::dbg_jni_set_shutdown_window_hook() (qore#6827)
#
# the argument names the call that the Java thread makes (see org.qore.jni.test.ShutdownWindowProbe):
# - "function": QoreJavaApi.callFunction()
# - "newobject": QoreJavaApi.newObjectSave()
# - "instanceof": QoreObject.instanceOf()
# - "none": no hook is set
# it prints "debug: <bool>" (whether the module is a debug build), then "armed: <case>", and the main thread exits;
# the probe prints "probe: <result>" on stderr when the module's shutdown runs it

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.ShutdownWindowProbe

string sub jni_shutdown_window_echo() {
    return "echo";
}

string case_name = ARGV[0] ?? "function";

bool debug;
try {
    call_function("Jni::org::qore::jni::dbg_jni_context_program_counts");
    debug = True;
} catch (hash<ExceptionInfo> ex) {
    # the diagnostics exist in debug builds only
}
printf("debug: %y\n", debug);

# the Mutex is kept by Java, so that the object exists when the probe calls instanceOf()
our Mutex m();
if (debug && case_name != "none") {
    auto arg;
    switch (case_name) {
        case "instanceof": arg = m; break;
        case "newobject": arg = "Mutex"; break;
        default: arg = "jni_shutdown_window_echo"; break;
    }
    ShutdownWindowProbe probe(case_name, arg);
    call_function("Jni::org::qore::jni::dbg_jni_set_shutdown_window_hook", probe);
}
printf("armed: %s\n", case_name);
