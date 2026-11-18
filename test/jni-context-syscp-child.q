#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# a child process of test/jni-context-direct-calls.qtest (qore#6551): run with the test jar on the JVM's system class
# path (QORE_JNI_JVM_ARGS=-Djava.class.path=...), so that the module's lookup of a class finds it with JNI FindClass,
# which initializes the class, running its static initializer, whose Java code calls into Qore; prints the Program
# that the call resolved against
%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import org.qore.jni.test.ContextProbe

const OwnerSource = "
%requires jni
%module-cmd(jni) import java.util.ArrayList

string sub which() {
    return \"owner\";
}

object sub make_list() {
    ArrayList l();
    l.add(\"x\");
    return l;
}
";

string sub which() {
    return "main";
}

Program owner(PO_NEW_STYLE);
owner.parse(OwnerSource, "owner");
object l = owner.callFunction("make_list");
# a Java call of the owner's object leaves the thread's Java context set to the owner
l.size();
load_class("org/qore/jni/test/ContextProbeSysInit");
printf("which: %s\n", ContextProbe::sysInitWhich);
