#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-context-program-lifetime.qtest: a Java thread calls a Qore function with no Qore Program context, so
# that the call is made in the jni module's global Java context Program (qore#6829)
#
# the argument names the case (see org.qore.jni.test.ContextProgramLifetimeProbe):
# - "held": the thread's first call is held by the hook set with Jni::dbg_jni_set_context_program_hook() once the
#   module has obtained the global context Program for it, the main thread exits, and the module's shutdown releases
#   the thread before it waits for the threads that hold the Program (Jni::dbg_jni_set_context_program_wait_hook())
# - "unheld": the thread's first call returns before the main thread exits
# - "none": no Java thread calls into Qore
# in the first two cases, the thread makes its second call in the module's shutdown, between the deletion of the global
# context Program and the destruction of the JVM (Jni::dbg_jni_set_shutdown_window_hook()), and the probe prints the
# results of both calls then
#
# it prints "debug: <bool>" (whether the module is a debug build), then "armed: <case>", and the main thread exits

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.ContextProgramLifetimeProbe

# the function that the Java thread calls: a builtin function, which exists in the global context Program, and whose
# result is the script path of the Program that the call is made in
const Function = "get_script_path";

string case_name = ARGV[0] ?? "held";

bool debug;
try {
    call_function("Jni::org::qore::jni::dbg_jni_context_program_counts");
    debug = True;
} catch (hash<ExceptionInfo> ex) {
    # the diagnostics exist in debug builds only
}
printf("debug: %y\n", debug);

if (debug && case_name != "none") {
    bool hold = case_name == "held";
    ContextProgramLifetimeProbe probe(Function, hold);
    if (hold) {
        call_function("Jni::org::qore::jni::dbg_jni_set_context_program_hook", probe);
        # the module's shutdown releases the thread before it waits for the threads that hold the Program
        call_function("Jni::org::qore::jni::dbg_jni_set_context_program_wait_hook", probe.releaseHook());
    }
    call_function("Jni::org::qore::jni::dbg_jni_set_shutdown_window_hook", probe.windowHook());
    # returns once the thread's first call is held, or has returned
    probe.start();
}
printf("armed: %s\n", case_name);
