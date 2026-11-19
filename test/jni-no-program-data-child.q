#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-no-program-data.qtest (qore#6801): this Program loads the jni module, then creates a child Program
# with PO_NO_INHERIT_PROGRAM_DATA, which inherits the jni module's namespaces and its feature, but not its jni data, so
# the child has no jni data; then the child calls one jni entry point, which must not crash the process: its jni data
# is created on demand, as for a child Program that issues a jni module command
#
# the argument names the case (see Cases below); the child's code runs it, and prints "result: <value>" or
# "caught: <err>: <desc>"; then "after: <case>" is printed, and the process exits with status 0

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
# the classes that the child uses, which it could not import itself without creating its jni data; the child inherits
# the classes of this Program: the class with a public field of the "field_access" case, and the class whose Java
# code calls the Qore function which() in the Program of the thread's Java context
%module-cmd(jni) import java.awt.Point
%module-cmd(jni) import org.qore.jni.test.ContextProbe

#! the name of this Program for ContextProbe.which()
string sub which() {
    return "parent";
}

#! the code of the child Program: one function per case, which returns a string that describes its result
const ChildSrc = "
class ChildLocal {
}

# the name of this Program for ContextProbe.which(): the Java method that jni::invoke() and jni::invoke_nonvirtual()
# call runs in the context of the calling code, this Program, whose jni data is created on demand
string sub which() {
    return \"child\";
}

string sub get_class_loader() {
    return jni::get_class_loader().getClass().getName();
}

string sub get_class_loader_twice() {
    # the jni data is created once: the second call returns the same class loader
    return sprintf(\"same: %y\", jni::get_class_loader().equals(jni::get_class_loader()));
}

auto sub class_loader_object() {
    return jni::get_class_loader();
}

string sub load_class() {
    return jni::load_class(\"java/lang/String\").getName();
}

string sub define_class() {
    # invalid byte code: the class loader of the Program must be used to refuse it
    return jni::define_class(\"org/qore/test/NoProgramData6801\", binary(\"not byte code\")).getName();
}

string sub get_byte_code() {
    return sprintf(\"has byte code: %y\", jni::get_byte_code(\"ChildLocal\").size() > 0);
}

string sub get_java_name() {
    return jni::get_java_name(\"ChildLocal\");
}

string sub new_array() {
    return sprintf(\"length %d\", jni::new_array(jni::load_class(\"java/lang/String\"), 3).length());
}

string sub implement_interface() {
    Jni::org::qore::jni::QoreInvocationHandler h(auto sub (auto m, *list<auto> args) {});
    return jni::implement_interface(h, jni::load_class(\"java/lang/Runnable\")).getClass().getName() =~ /Proxy/
        ? \"proxy\" : \"other\";
}

string sub invoke() {
    # the class is inherited from the parent Program; this Program's class loader cannot load it
    Jni::org::qore::jni::test::ContextProbe p();
    Jni::java::lang::Class cls = p.getClass();
    # a static method, an instance method, and a method with a primitive return value
    auto s = jni::invoke(cls.getDeclaredMethod(\"which\"), NOTHING);
    auto i = jni::invoke(cls.getDeclaredMethod(\"whichSelf\"), p);
    auto l = jni::invoke(jni::load_class(\"java/lang/String\").getMethod(\"length\"),
        new Jni::java::lang::String(\"abcd\"));
    return sprintf(\"%y %y %y\", s, i, l);
}

string sub invoke_nonvirtual() {
    # the class is inherited from the parent Program; this Program's class loader cannot load it
    Jni::org::qore::jni::test::ContextProbe p();
    Jni::java::lang::Class cls = p.getClass();
    # a static method and an instance method
    auto s = jni::invoke_nonvirtual(cls.getDeclaredMethod(\"which\"), NOTHING);
    auto i = jni::invoke_nonvirtual(cls.getDeclaredMethod(\"whichSelf\"),
        p);
    return sprintf(\"%y %y\", s, i);
}

string sub new_object() {
    Jni::java::lang::StringBuilder sb(\"no-data\");
    sb.append(\"-6801\");
    return sb.toString();
}

string sub static_method() {
    return Jni::java::lang::Integer::toString(6801);
}

string sub field_access() {
    Jni::java::awt::Point p(68, 1);
    return sprintf(\"%d\", p.x);
}

string sub java_array_get() {
    return sprintf(\"length %d\", Jni::org::qore::jni::JavaArray::get(binary(\"abc\")).length());
}

string sub save_object_callback() {
    jni::set_save_object_callback(sub (object o) {});
    return \"set\";
}

# two threads create the jni data at the same time: a debug gate of the module runs in this thread after it found no
# jni data and before it takes the Program's parse lock to create it; the gate makes another thread create the data
# then, and waits for it; this thread must then use the data that the other thread created
string sub concurrent_create() {
    *object other;
    Counter done(1);
    code gate = sub () {
        background sub () {
            on_exit {
                done.dec();
            }
            other = jni::get_class_loader();
        }();
        done.waitForZero();
    };
    try {
        call_function(\"Jni::org::qore::jni::dbg_jni_set_context_gate\", gate, \"create\");
    } catch (hash<ExceptionInfo> ex) {
        # a release build of the module has no debug gates
        if (ex.err == \"NO-FUNCTION\") {
            return \"no debug gate\";
        }
        rethrow;
    }
    object mine = jni::get_class_loader();
    return sprintf(\"other created: %y same: %y\", exists other, exists other && mine.equals(other));
}
";

#! the case names
const Cases = (
    "get_class_loader", "get_class_loader_twice", "distinct_loader", "load_class", "define_class", "get_byte_code",
    "get_java_name", "new_array", "implement_interface", "invoke", "invoke_nonvirtual", "new_object", "static_method",
    "field_access", "java_array_get", "save_object_callback", "concurrent_create",
);

string case_name = ARGV[0] ?? "get_class_loader";
if (!inlist(case_name, Cases)) {
    stderr.printf("unknown case %y; known cases: %y\n", case_name, Cases);
    exit(2);
}

Program child(PO_NEW_STYLE | PO_NO_INHERIT_PROGRAM_DATA);
child.parse(ChildSrc, "jni-no-program-data-child");
try {
    if (case_name == "distinct_loader") {
        # the class loader of the child's jni data, created on demand, is not the class loader of this Program
        auto loader = child.callFunction("class_loader_object");
        printf("result: distinct: %y\n", !loader.equals(jni::get_class_loader()));
    } else {
        printf("result: %s\n", child.callFunction(case_name));
    }
} catch (hash<ExceptionInfo> ex) {
    printf("caught: %s: %s\n", ex.err, ex.desc);
}
printf("after: %s\n", case_name);
