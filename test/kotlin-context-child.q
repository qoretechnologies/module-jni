#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# a child process of test/kotlin.qtest (qore#6551): only the class loader of the calling Program has the Kotlin
# scripting jars, and the first use of Kotlin scripting comes after a Java call for another Program, whose class loader
# does not delegate to the caller's and which the call leaves as the thread's Java context; prints the result
# args: <directory of the Kotlin scripting jars> <kotlin-stdlib jar> <kotlin-reflect jar or ""> <mode>
# modes: "control" (kotlin_eval() with no Java call for another Program before it), "eval" (kotlin_eval()),
# "available" (kotlin_scripting_available()), "retry" (kotlin_scripting_retry()), "modcmd" (the kotlin-eval module
# command in a pending parse of a Program whose class loader has the jars)
%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath ../build/qore-jni.jar
%module-cmd(jni) add-relative-classpath ../build/qore-jni-compiler.jar

#! a Program whose Java object the caller uses
const OwnerSource = "
%requires jni
%module-cmd(jni) import java.util.ArrayList

object sub make_list() {
    ArrayList l();
    l.add(\"x\");
    return l;
}
";

#! the calling Program: its own class loader gets the scripting jars
const CallerSource = "
%requires jni

sub setup(list<string> jars, *object l) {
    object loader = get_class_loader();
    foreach string jar in (jars) {
        loader.addPath(jar);
    }
    if (l) {
        # a Java call of the owner's object leaves the thread's Java context set to the owner
        l.size();
    }
}

string sub run(string mode) {
    try {
        switch (mode) {
            case \"available\": return sprintf(\"result: %y\", kotlin_scripting_available());
            case \"retry\": return sprintf(\"result: %y\", kotlin_scripting_retry());
        }
        return sprintf(\"result: %y\", kotlin_eval(\"1 + 2\"));
    } catch (hash<ExceptionInfo> ex) {
        return sprintf(\"result: %s: %s\", ex.err, ex.desc);
    }
}
";

list<string> jars = glob(ARGV[0] + "/*.jar") ?? ();
jars += ARGV[1];
if (ARGV[2]) {
    jars += ARGV[2];
}
string mode = ARGV[3];

Program owner(PO_NEW_STYLE);
owner.parse(OwnerSource, "owner");
object l = owner.callFunction("make_list");

if (mode == "modcmd") {
    # a Program whose first pending parse creates its jni data (which makes its class loader the thread's Java
    # context) and adds the scripting jars to its class loader with module commands; after a Java call of the owner's
    # object, which leaves the thread's Java context set to the owner, its next pending parse runs the kotlin-eval
    # module command, which discards the script's value
    string setup = "%requires jni\n";
    foreach string jar in (jars) {
        setup += sprintf("%%module-cmd(jni) add-classpath %s\n", jar);
    }
    Program parsed(PO_NEW_STYLE);
    parsed.parsePending(setup, "setup");
    l.size();
    try {
        parsed.parsePending("%module-cmd(jni) kotlin-eval 1 + 2\n", "modcmd");
        parsed.parseCommit();
        printf("result: parsed\n");
    } catch (hash<ExceptionInfo> ex) {
        printf("result: %s: %s\n", ex.err, ex.desc);
    }
} else {
    Program caller(PO_NEW_STYLE);
    caller.parse(CallerSource, "caller");
    caller.callFunction("setup", jars, mode == "control" ? NOTHING : l);
    printf("%s\n", caller.callFunction("run", mode));
}
