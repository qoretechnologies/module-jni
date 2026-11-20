#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-cached-program-ptr.qtest: a Java thread whose class loader context has the Program pointer of a
# child Qore Program calls into that Program with an API that passes the Program pointer to a native method (see
# org.qore.jni.test.CachedProgramPtrProbe)
#
# arguments: <case> <setup> <api>
# - case "live": the call is made and returns while the child Program exists
# - case "wait": the thread sets its class loader context up while the child Program exists, the child Program is
#   deleted, and the thread then makes the call
# - case "hook": the call is held by the hook set with Jni::dbg_jni_set_java_program_ptr_hook() once the native method
#   has received the Program pointer, the child Program is deleted, and the call is then released
# - case "hook-live": as "hook", but the child Program is deleted only once the call has returned
# - case "hook-clear": as "hook-live", and the hook clears the pointer of the thread's class loader context before the
#   native method uses the pointer that it has received
# - case "share": no call is made; class loaders are created for the Program of the child Program's class loader, whose
#   pointer they must share (CachedProgramPtrProbe.checkSharedPtr())
# - setup: how the thread sets its class loader context up: "context" (the child Program's class loader) or
#   "compiler" (the class loader of a QoreJavaCompiler created for the child Program's class loader)
# - api: the API of the call (see CachedProgramPtrProbe.call())
#
# it prints "debug: <bool>" (whether the module is a debug build), "ptr: <bool>" (whether the thread's class loader
# context had the pointer of the child Program before it was deleted), then "result: <result of the call>"

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.CachedProgramPtrProbe
%module-cmd(jni) import org.qore.jni.QoreURLClassLoader

# the code of the child Program: a function, a class and a static method that exist only in it, and the functions that
# create the probe with the child Program's class loader and check the sharing of its pointer
const ChildCode = "
%new-style
%requires jni
%module-cmd(jni) import org.qore.jni.test.CachedProgramPtrProbe

string sub pgmptr_probe_fn() {
    return \"child\";
}

class PgmPtrProbeClass {
    static string get() {
        return \"child\";
    }

    string name() {
        return \"child\";
    }
}

# the object of the \"method\" API, which exists as long as the Program
PgmPtrProbeClass probe_obj;

int sub make_probe(string setup, string api, string name, string mode) {
    object loader = get_class_loader();
    auto target;
    switch (api) {
        case \"method\": target = probe_obj = new PgmPtrProbeClass(); break;
        case \"closure\": target = string sub () { return \"child\"; }; break;
    }
    CachedProgramPtrProbe::create(loader, setup, api, name, target, mode);
    return loader.getPtr();
}

string sub check_shared_ptr() {
    return CachedProgramPtrProbe::checkSharedPtr(get_class_loader());
}
";

# the name used by each API
const Names = {
    "function": "pgmptr_probe_fn",
    "static": "PgmPtrProbeClass::get",
    "new": "PgmPtrProbeClass",
    "class": "qore.PgmPtrProbeClass",
    "method": "name",
    "closure": "",
};

# the probe's mode for each case
const Modes = {
    "live": "call",
    "wait": "wait",
    "hook": "hook",
    "hook-live": "hook",
    "hook-clear": "hook-clear",
};

string case_name = ARGV[0] ?? "hook";
string setup = ARGV[1] ?? "context";
string api = ARGV[2] ?? "function";

bool debug;
try {
    call_function("Jni::org::qore::jni::dbg_jni_context_program_counts");
    debug = True;
} catch (hash<ExceptionInfo> ex) {
    # the diagnostics exist in debug builds only
}
printf("debug: %y\n", debug);
if (!debug) {
    exit(0);
}

# the jni data of this Program is created first, so that the child Program gets jni data of its own, with a class
# loader of its own, as a copy of it
int parent_ptr = cast<QoreURLClassLoader>(get_class_loader()).getPtr();

Program p(PO_NEW_STYLE);
p.parse(ChildCode, "child");

if (case_name == "share") {
    printf("result: %s\n", p.callFunction("check_shared_ptr"));
    exit(0);
}

int child_ptr = p.callFunction("make_probe", setup, api, Names{api}, Modes{case_name});
CachedProgramPtrProbe probe = CachedProgramPtrProbe::last();
if (case_name =~ /^hook/) {
    call_function("Jni::org::qore::jni::dbg_jni_set_java_program_ptr_hook", probe);
}
# returns once the thread is held, or once its call has returned
probe.start();
printf("ptr: %y\n", child_ptr && child_ptr != parent_ptr && probe.programPtr() == child_ptr);

string result;
if (case_name == "wait" || case_name == "hook") {
    delete p;
    result = probe.proceed();
} else {
    result = probe.proceed();
    delete p;
}
printf("result: %s\n", result);
