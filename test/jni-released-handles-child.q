#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-released-handles.qtest: Java keeps a Qore object, a closure or a proxy of a QoreInvocationHandler,
# and uses it in a JVM shutdown hook, after the jni module's shutdown has released its native handle (see
# org.qore.jni.test.ReleasedHandleCalls)
#
# the arguments are the operation (see ReleasedHandleCalls.call()) and "live" (the call is made now, with the handle
# alive) or "shutdown" (the call is made in a JVM shutdown hook); it prints "live: <result>" or arms the hook, then
# "after: <op>"; the hook prints "shutdown: <result>"
#
# two operations release the object while a call uses it, and print "live: <result>" (mode "live"):
# - "release-in-use": the object is released while a call of its method on another thread is in progress
#   (ReleasedHandleCalls.releaseWhileInUse())
# - "release-throwing-hook": the same, with a release wait hook that throws
# - "release-forced-hook": the same, with the hook stored directly in NativeCleanup's private field
# - "hook-enabled": prints whether the module runs the release wait hook (a debug build only)
# - "release-self": the object's method releases the object, on the thread of the call that uses it
#   (ReleasedHandleCalls.callReleaseSelf())
# - "convert-released": Java returns a QoreObject whose handle it has released, which Qore converts
# - "generated-released": a method of a generated class is called through its generated method before and after the
#   object is released (ReleasedHandleCalls.generatedReleased())
# - "shutdown-in-use": a Java thread's call of a closure of a user module (JniReleasedHandlesBlock) is in progress,
#   blocked until the process ends; the Qore library does not wait for it and ends the process without running any exit
#   handler (qore#7340), so the process must exit and the JVM shutdown hook that the module registers, which would
#   print "shutdown: <state>", the state of the closure's handle, does not run
# - "exit-unmatched": the end of a use of the object's handle that was never begun, before and after the object is
#   released (ReleasedHandleCalls.exitUnmatched())
# the operation "policy" keeps a QoreSandbox.Policy captured for the script and calls isSandboxed()

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%append-module-path "${SCRIPT_DIR}/jni-released-handles-mod"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.ReleasedHandleCalls

class ReleasedHandleObject {
    public {
        string m = "member";
    }

    string get() {
        return "method";
    }

    string block() {
        ReleasedHandleCalls::blockInCall();
        return "blocked";
    }

    string releaseSelf() {
        return ReleasedHandleCalls::releaseKept();
    }
}

string op = ARGV[0] ?? "closure";
string mode = ARGV[1] ?? "live";

# kept by the script until it exits, so that the object is alive for the live calls
our ReleasedHandleObject obj();
our Mutex mutex();

switch (op) {
    case "closure":
    case "closure-save":
        ReleasedHandleCalls::keep(string sub () { return "closure"; });
        break;
    case "instanceof":
        ReleasedHandleCalls::keep(mutex);
        break;
    case "proxy": {
        object handler = new QoreInvocationHandler(auto sub (object method, *list<auto> args) {
            return "proxy";
        });
        ReleasedHandleCalls::keep(implement_interface(handler, load_class("java.util.function.Supplier")));
        break;
    }
    case "policy":
        ReleasedHandleCalls::keepPolicy();
        break;
    default:
        ReleasedHandleCalls::keep(obj);
        break;
}

if (op == "release-in-use") {
    printf("live: %s\n", ReleasedHandleCalls::releaseWhileInUse());
} else if (op == "release-throwing-hook") {
    printf("live: %s\n", ReleasedHandleCalls::releaseWhileInUse(True));
} else if (op == "release-forced-hook") {
    printf("live: %s\n", ReleasedHandleCalls::releaseWhileInUse(False, True));
} else if (op == "hook-enabled") {
    printf("live: %y\n", ReleasedHandleCalls::releaseWaitHookEnabled());
} else if (op == "release-self") {
    printf("live: %s\n", ReleasedHandleCalls::callReleaseSelf());
} else if (op == "convert-released") {
    # Java returns a QoreObject whose handle has been released, which Qore converts
    try {
        auto v = ReleasedHandleCalls::releaseAndReturn();
        printf("live: converted: %y\n", v);
    } catch (hash<ExceptionInfo> ex) {
        printf("live: %s: %s\n", ex.err, ex.desc);
    }
} else if (op == "shutdown-in-use") {
    # a Java thread's call of a module's closure is in progress, blocked, until the process ends
    load_module("JniReleasedHandlesBlock");
    call_function("JniReleasedHandlesBlock::start");
    printf("live: in call\n");
} else if (op == "exit-unmatched") {
    printf("live: %s\n", ReleasedHandleCalls::exitUnmatched());
} else if (op == "generated-released") {
    printf("live: %s\n", ReleasedHandleCalls::generatedReleased());
} else if (mode == "live") {
    printf("live: %s\n", ReleasedHandleCalls::call(op));
} else {
    ReleasedHandleCalls::callAtShutdown(op);
}
printf("after: %s\n", op);
