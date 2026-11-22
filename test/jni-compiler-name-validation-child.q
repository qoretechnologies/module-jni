#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# a process of its own for test/jni-compiler-name-validation.qtest: the state of Java threads is per process and cannot
# be undone, and the temporary directory of the JVM is set when the JVM starts
# usage: <mode> [<Kotlin compiler jar> <Kotlin stdlib jar>]; each result is printed as "result: <label>: <result>"

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) add-relative-classpath ../build/qore-jni-compiler.jar
%module-cmd(jni) import org.qore.jni.test.ClassLoaderAccessProbe

const TenantSource = "%modern
%requires jni
%module-cmd(jni) import org.qore.jni.test.ClassLoaderAccessProbe
string sub tenant_thread() {
    return ClassLoaderAccessProbe::threadRestrictsFilesystem();
}
string sub tenant_captured() {
    return ClassLoaderAccessProbe::threadCapturedRestrictsFilesystem();
}
";

const KotlinProgramSource = "%modern
%requires jni
%module-cmd(jni) import org.qore.jni.compiler.QoreKotlinCompiler
string sub compile_source(string src) {
    try {
        QoreKotlinCompiler compiler(get_class_loader(), NOTHING);
        on_exit compiler.close();
        compiler.compile({\"KOutput\": src});
        return \"compiled\";
    } catch (hash<ExceptionInfo> ex) {
        return \"refused\";
    }
}
";

const NoLocationsSource = "%modern
%requires jni
%module-cmd(jni) import org.qore.jni.compiler.QoreJavaCompiler
string sub compile_user(string name) {
    try {
        QoreJavaCompiler compiler(get_class_loader(), NOTHING);
        on_exit compiler.close();
        compiler.compile(name, sprintf(\"public class %s { CurrentDirFixture d; }\", name));
        return \"compiled\";
    } catch (hash<ExceptionInfo> ex) {
        return \"refused\";
    }
}
";

#! Kotlin sources whose package, class or facade names are not plain names
const OutputSources = {
    "package": "package `a/b`\nclass P1\n",
    "class": "class `a/b`\n",
    "facade": "@file:JvmName(\"a/b\")\nfun f() {}\n",
    "package name annotation": "@file:JvmPackageName(\"a/b\")\nfun f() {}\n",
};

#! a Program whose sandbox denies filesystem access and allows Java
Program sub make_tenant(string src) {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.loadModule("jni");
    p.issueModuleCmd("jni", "add-classpath " + get_script_dir() + "/qore-jni-test.jar");
    p.issueModuleCmd("jni", "add-classpath " + get_script_dir() + "/../build/qore-jni-compiler.jar");
    p.parse(src, "tenant");
    SandboxManager sm();
    sm.setUncontrolledApiPolicy(True);
    sm.setFilesystemDefaultPolicy(False);
    p.setSandboxManager(sm);
    return p;
}

sub result(string label, string value) {
    printf("result: %s: %s\n", label, value);
}

switch (ARGV[0]) {
    case "thread-before": {
        result("thread", ClassLoaderAccessProbe::threadRestrictsFilesystem());
        result("captured", ClassLoaderAccessProbe::threadCapturedRestrictsFilesystem());
        break;
    }
    case "thread-after": {
        Program p = make_tenant(TenantSource);
        # the tenant has used Java, and its Java thread does not know its caller
        result("tenant thread", p.callFunction("tenant_thread"));
        result("tenant captured", p.callFunction("tenant_captured"));
        result("host thread", ClassLoaderAccessProbe::threadRestrictsFilesystem());
        result("host captured", ClassLoaderAccessProbe::threadCapturedRestrictsFilesystem());
        break;
    }
    case "thread-strict": {
        Program::issueModuleCmd("jni", "set-java-thread-policy strict");
        result("host thread", ClassLoaderAccessProbe::threadRestrictsFilesystem());
        result("host captured", ClassLoaderAccessProbe::threadCapturedRestrictsFilesystem());
        break;
    }
    case "no-locations": {
        # Programs with nothing on their own classpath; the current directory has the class that the source uses
        foreach bool sandboxed in ((True, False)) {
            Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
            p.loadModule("jni");
            p.parse(NoLocationsSource, "no-locations");
            if (sandboxed) {
                SandboxManager sm();
                sm.setUncontrolledApiPolicy(True);
                sm.setFilesystemDefaultPolicy(False);
                p.setSandboxManager(sm);
            }
            result(sandboxed ? "restricted" : "trusted", p.callFunction("compile_user",
                sandboxed ? "NoLocationsRestricted" : "NoLocationsTrusted"));
        }
        break;
    }
    case "kotlin-after": {
        # a Kotlin compile of trusted code, on a thread that Qore code calls, after a tenant has used Java
        Program::issueModuleCmd("jni", "global-add-classpath " + ARGV[1]);
        Program::issueModuleCmd("jni", "global-add-classpath " + ARGV[2]);
        Program tenant = make_tenant(TenantSource);
        tenant.callFunction("tenant_thread");
        Program host(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
        host.loadModule("jni");
        host.issueModuleCmd("jni", "add-classpath " + get_script_dir() + "/../build/qore-jni-compiler.jar");
        host.parse(KotlinProgramSource, "host");
        result("host compile", host.callFunction("compile_source", "class HostAfter\n"));
        break;
    }
    case "output": {
        Program::issueModuleCmd("jni", "global-add-classpath " + ARGV[1]);
        Program::issueModuleCmd("jni", "global-add-classpath " + ARGV[2]);
        Program p = make_tenant(KotlinProgramSource);
        foreach hash<auto> i in (OutputSources.pairIterator()) {
            result(i.key, p.callFunction("compile_source", i.value));
        }
        break;
    }
    default: {
        throw "CHILD-ERROR", sprintf("unknown mode %y", ARGV[0]);
    }
}
