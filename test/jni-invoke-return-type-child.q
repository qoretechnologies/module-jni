#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-invoke-return-type.qtest (qore#6808): calls jni::invoke() or jni::invoke_nonvirtual() with Java
# methods that return a primitive value, a value converted from a Java object, an object or nothing, and uses each
# result in three ways, each in its own Program, so that a parse error in one use does not prevent the others:
# - "method": a method is called on the result directly (jni::invoke(...).toString()), which crashed the process
#   when the function was declared to return Jni::java::lang::Object: the call was resolved at parse time from that
#   class, and IR and JIT code made it with the plain value as the object (as did the AST interpreter for a
#   pseudo-method of objects, such as size())
# - "plain": the result is used as the plain Qore value that it is (arithmetic, logic)
# - "typed": the result is assigned to a variable of its Qore type, which was a parse-time type error
#
# usage: jni-invoke-return-type-child.q <function> [<kind>...]
#   function: "invoke" or "invoke_nonvirtual"
#   kind: the return type of the method of org.qore.jni.test.InvokeReturnTypes (see Kinds below); all kinds by
#     default
#
# for each kind and use, "result <kind> <use>: <value>" or "caught <kind> <use>: <err>: <desc>" is printed; then
# "after: <function>" is printed, and the process exits with status 0

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
# the class of the methods that the child Programs, which inherit it, invoke
%module-cmd(jni) import org.qore.jni.test.InvokeReturnTypes

#! the Java method and the receiver of each kind of return type, and the uses of the result
/** "setup" is code that sets "m", the method of org.qore.jni.test.InvokeReturnTypes, and "o", the object (NOTHING for
    a static method); "args" is the arguments of the call after the object
*/
const Kinds = {
    # int length()
    "int": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"length\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "toString()",
        "plain": "%s + 1",
        "typed": "int",
    },
    # boolean isEmpty()
    "boolean": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"isEmpty\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "toString()",
        "plain": "!%s",
        "typed": "bool",
    },
    # double half(double)
    "double": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"half\"); *Jni::java::lang::Object o = obj();",
        "args": ", 3.0",
        "method": "toString()",
        "plain": "%s * 2",
        "typed": "float",
    },
    # char charAt(int): a char is converted to an int
    "char": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"charAt\"); *Jni::java::lang::Object o = obj();",
        "args": ", 1",
        "method": "toString()",
        "plain": "%s + 1",
        "typed": "int",
    },
    # void touch()
    "void": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"touch\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "toString()",
        "plain": "exists %s",
        "typed": "*int",
        # the method ran: the receiver is new for each use
        "typed_ret": "(v, cast<Jni::org::qore::jni::test::InvokeReturnTypes>(o).getTouched())",
    },
    # static double staticHalf(double)
    "static_double": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"staticHalf\"); *Jni::java::lang::Object o;",
        "args": ", 3.0",
        "method": "toString()",
        "plain": "%s * 2",
        "typed": "float",
    },
    # static void staticTouch()
    "static_void": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"staticTouch\"); *Jni::java::lang::Object o;",
        "args": "",
        "method": "toString()",
        "plain": "exists %s",
        "typed": "*int",
        # the method ran: once for each use of the process, so three times with this, the last one
        "typed_ret": "(v, Jni::org::qore::jni::test::InvokeReturnTypes::getStaticTouched())",
    },
    # Integer boxed(): a boxed value is converted to a Qore int
    "boxed_int": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"boxed\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "toString()",
        "plain": "%s + 1",
        "typed": "int",
    },
    # String nullString(): null is NOTHING
    "null_string": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"nullString\"); "
            "*Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "toString()",
        "plain": "exists %s",
        "typed": "*string",
    },
    # String upper(): a String is converted to a Qore string
    "string": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"upper\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "size()",
        "plain": "%s + \"x\"",
        "typed": "string",
    },
    # InvokeReturnTypes self(): an object, whose Java methods are called
    "object": {
        "setup": "Jni::java::lang::reflect::Method m = findMethod(\"self\"); *Jni::java::lang::Object o = obj();",
        "args": "",
        "method": "length()",
        "plain": "%s instanceof Jni::org::qore::jni::test::InvokeReturnTypes",
        "typed": "Jni::org::qore::jni::test::InvokeReturnTypes",
        "typed_ret": "v.upper()",
    },
};

#! the code common to all uses
const Common = "
# returns the public method of InvokeReturnTypes with the given name
Jni::java::lang::reflect::Method sub findMethod(string name) {
    foreach Jni::java::lang::reflect::Method m in
            (jni::load_class(\"org/qore/jni/test/InvokeReturnTypes\").getMethods()) {
        if (m.getName() == name) {
            return m;
        }
    }
    throw \"NO-METHOD\", name;
}

# returns the receiver
Jni::java::lang::Object sub obj() {
    return new Jni::org::qore::jni::test::InvokeReturnTypes(\"abcd\");
}
";

#! the uses of the result
const Uses = ("method", "plain", "typed");

string func = ARGV[0] ?? "";
softlist<string> kinds = ARGV.size() > 1 ? ARGV[1..] : keys Kinds;
list<string> unknown = select kinds, !Kinds{$1};
if (!inlist(func, ("invoke", "invoke_nonvirtual")) || unknown) {
    stderr.printf("unknown case %y (unknown kinds: %y); expecting <invoke|invoke_nonvirtual> [<kind>...] (kinds: "
        "%y)\n", ARGV, unknown, keys Kinds);
    exit(2);
}

foreach string kind in (kinds) {
    hash<auto> k = Kinds{kind};
    foreach string use in (Uses) {
        string call = sprintf("jni::%s(m, o%s)", func, k.args);
        string body;
        switch (use) {
            case "method":
                body = sprintf("return %s.%s;", call, k.method);
                break;
            case "plain":
                body = sprintf("return " + k.plain + ";", call);
                break;
            case "typed":
                body = sprintf("%s v = %s; return %s;", k.typed, call, k.typed_ret ?? "v");
                break;
        }
        try {
            Program p(PO_NEW_STYLE);
            p.loadModule("jni");
            # %modern: the code runs in the exec mode of the process (IR and JIT code require the parse options of
            # %modern)
            p.parse("%modern\n" + Common + "auto sub t() { " + k.setup + " " + body + " }",
                sprintf("%s-%s-%s", func, kind, use));
            printf("result %s %s: %y\n", kind, use, p.callFunction("t"));
        } catch (hash<ExceptionInfo> ex) {
            printf("caught %s %s: %s: %s\n", kind, use, ex.err, ex.desc);
        }
        # the lines printed so far are kept if the next use crashes the process
        flush();
    }
}
printf("after: %s\n", func);
