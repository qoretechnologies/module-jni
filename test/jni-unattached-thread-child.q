#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-unattached-thread.qtest (qore#6828): the native methods of the jni module are called from a Java
# thread that Java starts, which is not attached to Qore; a native method that used the Qore thread state of the
# thread before attaching it to Qore crashed the process
#
# the argument names the case; the child prints "<case>: <result>" for each call, where the result is
# "ok: <value>" or the class and the message of the Throwable that the call threw, then "after: <case>", and exits
# with status 0:
# - "instanceof": QoreObject.instanceOf() with the object's class, a parent class, an unrelated class, a class that
#   does not exist, and a class name with a NUL character
# - "instanceof-program": QoreObject.instanceOf() of an object of a class of a child Program, with the object's
#   class, a parent class, an unrelated builtin class, and a class that does not exist
# - "classname": QoreObject.className()
# - "method": QoreObject.callMethod() of a method that returns a value, and of one that raises an exception
# - "closure": QoreClosure.call()
# - "release": QoreObject.release() of the last reference to an object whose destructor has run, which frees it
# - "message": QoreExceptionWrapper.getMessage() of a Qore exception with a string description, and of one whose
#   description is a hash
# - "loadclass": a qoremod.* class of a user module, a qore.* class, and a qoremod.* class of a module that does not
#   exist are loaded
# - "moduleloader": QoreURLClassLoader.getModuleLoader0() (with reflection) of a module that does not exist, and of a
#   module whose lookup raises a Qore exception (a test hook of debug builds)
# - "jdbc": QoreJavaApi.openJdbcConnection()

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%requires Util

%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.UnattachedThreadCalls

#! a parent class
class UnattachedParent {
}

#! the class of the objects that the cases hand to Java
class UnattachedChild inherits UnattachedParent {
    string get() {
        return "value";
    }

    string fail() {
        throw "UNATTACHED-TEST-ERROR", "method error";
    }
}

#! a class that is unrelated to UnattachedChild
class UnattachedOther {
}

#! the code of the child Program of the "instanceof-program" case
const ChildSrc = "
class UnattachedProgramParent {
}

class UnattachedProgramChild inherits UnattachedProgramParent {
}

object sub make() {
    return new UnattachedProgramChild();
}
";

#! raises an exception with a string description
sub unattached_raise_string() {
    throw "UNATTACHED-TEST-ERROR", "string description";
}

#! raises an exception with a hash description
sub unattached_raise_hash() {
    throw "UNATTACHED-TEST-ERROR", {"a": 1};
}

#! raises an exception with a description that cannot be converted to UTF-8
sub unattached_raise_encoding() {
    throw "UNATTACHED-TEST-ERROR", force_encoding(binary_to_string(<1b2428ff>), "ISO-2022-JP");
}

#! prints the result of a call
sub out(string case_name, string result) {
    printf("%s: %s\n", case_name, result);
    flush();
}

string case_name = ARGV[0] ?? "instanceof";

switch (case_name) {
    case "instanceof": {
        UnattachedChild obj();
        out("child", UnattachedThreadCalls::instanceOf(obj, "UnattachedChild"));
        out("parent", UnattachedThreadCalls::instanceOf(obj, "UnattachedParent"));
        out("other", UnattachedThreadCalls::instanceOf(obj, "UnattachedOther"));
        out("missing", UnattachedThreadCalls::instanceOf(obj, "UnattachedNoSuchClass"));
        out("nul", UnattachedThreadCalls::instanceOf(obj, "UnattachedChild\0x"));
        Mutex m();
        out("builtin", UnattachedThreadCalls::instanceOf(m, "Mutex"));
        out("builtin-other", UnattachedThreadCalls::instanceOf(m, "Condition"));
        break;
    }

    case "instanceof-program": {
        # the object of a class of a child Program: its class is looked up in the object's Program
        Program p(PO_NEW_STYLE);
        p.parse(ChildSrc, "child");
        object obj = p.callFunction("make");
        out("program-child", UnattachedThreadCalls::instanceOf(obj, "UnattachedProgramChild"));
        out("program-parent", UnattachedThreadCalls::instanceOf(obj, "UnattachedProgramParent"));
        out("program-builtin", UnattachedThreadCalls::instanceOf(obj, "Mutex"));
        out("program-missing", UnattachedThreadCalls::instanceOf(obj, "UnattachedNoSuchClass"));
        break;
    }

    case "classname": {
        UnattachedChild obj();
        out("classname", UnattachedThreadCalls::className(obj));
        break;
    }

    case "method": {
        UnattachedChild obj();
        out("method", UnattachedThreadCalls::callMethod(obj, "get"));
        out("method-error", UnattachedThreadCalls::callMethod(obj, "fail"));
        out("method-no-program", UnattachedThreadCalls::callMethodNoProgram(obj, "get"));
        break;
    }

    case "closure": {
        out("closure", UnattachedThreadCalls::callClosure(string sub () { return "closure value"; }));
        out("closure-no-program", UnattachedThreadCalls::callClosureNoProgram(string sub () { return "no program"; }));
        break;
    }

    case "release": {
        # the only reference to the object after the call is the weak reference of the Java wrapper
        UnattachedThreadCalls::hold(new UnattachedChild());
        out("release", UnattachedThreadCalls::releaseHeld());
        break;
    }

    case "message": {
        out("message-string", UnattachedThreadCalls::exceptionMessage("unattached_raise_string"));
        out("message-hash", UnattachedThreadCalls::exceptionMessage("unattached_raise_hash"));
        out("message-encoding", UnattachedThreadCalls::exceptionMessage("unattached_raise_encoding"));
        break;
    }

    case "loadclass": {
        out("loadclass-module", UnattachedThreadCalls::loadClass("qoremod.Util.TerminalColor"));
        out("loadclass-qore", UnattachedThreadCalls::loadClass("qore.Qore.Thread.Condition"));
        out("loadclass-missing", UnattachedThreadCalls::loadClass("qoremod.UnattachedNoSuchModule.X"));
        break;
    }

    case "moduleloader": {
        out("module-loader-missing", UnattachedThreadCalls::moduleLoader("UnattachedNoSuchModule"));
        # a test hook of debug builds raises a Qore exception in the lookup of the named module
        setenv("QORE_JNI_TEST_MODULE_LOADER_FAIL", "UnattachedLoaderFail");
        out("module-loader-error", UnattachedThreadCalls::moduleLoader("UnattachedLoaderFail"));
        unsetenv("QORE_JNI_TEST_MODULE_LOADER_FAIL");
        break;
    }

    case "jdbc": {
        out("jdbc", UnattachedThreadCalls::openJdbcConnection("connection"));
        break;
    }

    default:
        printf("unknown case: %s\n", case_name);
        exit(1);
}

printf("after: %s\n", case_name);
