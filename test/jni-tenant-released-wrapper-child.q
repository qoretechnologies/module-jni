#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-tenant-released-wrapper.qtest: can sandboxed code (a tenant) keep an org.qore.jni.QoreExceptionWrapper
# after Qore has taken its exception, and get its message (qore#6864)?
#
# the argument names the case:
# - "default": a Program with a default sandbox, which refuses uncontrolled APIs, tries to import a Java class and to
#   load one; it prints "import: <result>" and "load: <result>"
# - "uncontrolled": a Program with a sandbox that denies the filesystem but trusts the code with uncontrolled APIs
#   (SandboxManager::setUncontrolledApiPolicy(), as test/jni-sandbox.qtest models sandboxed code) uses JDK classes to
#   keep the QoreExceptionWrapper of a Qore exception that its own closure raises, makes Java throw the wrapper back
#   to Qore, and gets its message again; it prints "tenant: <result>" (a sandbox that restricts the filesystem refuses
#   Java code with a Qore library that has the rule of qore#7832, which it prints first as "classes-refused: <bool>")
# - "open": the same with a sandbox that trusts the code with uncontrolled APIs and restricts neither the filesystem
#   nor the network, which no Qore library refuses Java code; it prints "tenant: <result>"
# it then prints "after: <case>"

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni

#! the code of the tenant in the "uncontrolled" case
const TenantSource = "
%requires jni
%module-cmd(jni) import java.util.Optional
%module-cmd(jni) import java.util.concurrent.CompletableFuture
%module-cmd(jni) import java.util.function.Supplier

#! returns a java.util.function.Supplier that calls the closure
object sub supplier(code c) {
    return cast<Supplier>(implement_interface(new QoreInvocationHandler(auto sub (object method, *list<auto> args) {
        return c();
    }), load_class(\"java.util.function.Supplier\")));
}

string sub run() {
    # the closure's Qore exception reaches Java as a QoreExceptionWrapper, which the future keeps
    object future = CompletableFuture::supplyAsync(supplier(sub () { throw \"TENANT-ERROR\", \"tenant\"; }));
    try {
        future.join();
    } catch (hash<ExceptionInfo> ex) {
    }
    object wrapper = future.exceptionNow();
    string rv = sprintf(\"class: %s; message: %s\", wrapper.getClass().getName(), wrapper.getMessage());
    # Optional.orElseThrow() throws the wrapper itself to Qore, which takes its exception
    try {
        object empty = Optional::empty();
        empty.orElseThrow(supplier(auto sub () { return wrapper; }));
        rv += \"; taken: no exception\";
    } catch (hash<ExceptionInfo> ex) {
        rv += sprintf(\"; taken: %s: %s\", ex.err, ex.desc);
    }
    # the message of the wrapper whose exception Qore has taken
    return rv + \"; after: \" + wrapper.getMessage();
}
";

#! Returns True if a sandbox that trusts its code with uncontrolled APIs but restricts the filesystem or the network
#! refuses Java code (qore#7832)
bool sub sandbox_classes_refused() {
    return get_module_hash().jni.info.sandbox_uncontrolled_api_classes ?? False;
}

string sub try_parse(SandboxManager sm, string src) {
    Program p(PO_NEW_STYLE);
    p.setSandboxManager(sm);
    try {
        p.parse(src, "tenant");
    } catch (hash<ExceptionInfo> ex) {
        return sprintf("%s: %s", ex.err, ex.desc);
    }
    try {
        return sprintf("%y", p.callFunction("run"));
    } catch (hash<ExceptionInfo> ex) {
        return sprintf("%s: %s", ex.err, ex.desc);
    }
}

string case_name = ARGV[0] ?? "default";

switch (case_name) {
    case "default":
        printf("import: %s\n", try_parse(new SandboxManager(),
            "%requires jni\n%module-cmd(jni) import java.util.Optional\nauto sub run() {}\n"));
        printf("load: %s\n", try_parse(new SandboxManager(),
            "%requires jni\nauto sub run() {\n    return Jni::org::qore::jni::load_class(\"java.util.Optional\")"
            ".getName();\n}\n"));
        break;

    case "uncontrolled": {
        SandboxManager sm();
        sm.setFilesystemDefaultPolicy(False);
        call_object_method(sm, "setUncontrolledApiPolicy", True);
        printf("classes-refused: %y\n", sandbox_classes_refused());
        printf("tenant: %s\n", try_parse(sm, TenantSource));
        break;
    }

    case "open": {
        SandboxManager sm();
        sm.setFilesystemDefaultPolicy(True);
        sm.setNetworkDefaultPolicy(True);
        call_object_method(sm, "setUncontrolledApiPolicy", True);
        printf("tenant: %s\n", try_parse(sm, TenantSource));
        break;
    }

    default:
        printf("unknown case: %s\n", case_name);
        exit(1);
}
flush();
printf("after: %s\n", case_name);
