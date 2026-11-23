#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-fork.qtest: a Java thread holds the locks of the shared class records and of the handle registry when
# fork() is called, so that they stay locked forever in the child, which then deletes a Program and exits
#
# the arguments are the route, "exit" (Qore::exit() with the status given) or "end" (the script ends), and the status;
# the parent prints "status: <how the child ended>", or "skip: <reason>"
#
# The child's stderr is the write end of a pipe, which stays open until the child has ended, and the child reports each
# stage of its exit on it.  A child is reported as not having exited only if it is blocked (see wait_fork_child()), with
# no limit on the time that a child that makes progress can take.  Whatever fails in the parent, it kills a child that it
# has not collected and releases the Java thread that holds the locks.

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.GeneratedBindings

%include jni-fork-progress.ql

const WaitChild = "Jni::org::qore::jni::dbg_jni_wait_child_status";

const Source = "%modern\n%requires jni\n"
    "%module-cmd(jni) import org.qore.jni.QoreURLClassLoader\n"
    "our Queue entered();\n"
    "our Queue gate();\n"
    "public namespace EmbeddedBindingTest {\n"
    "    const NsValue = 73;\n"
    "    public class Constants { public { const Value = 42; } }\n"
    "    public class Calls {\n"
    "        int value(int arg) { return arg + 3; }\n"
    "        static int twice(int arg) { return arg * 2; }\n"
    "    }\n"
    "    int sub plus(int arg) { return arg + 1; }\n"
    "    int sub hold(int arg) {\n"
    "        Jni::org::qore::jni::dbg_jni_hold_fork_locks(sub () { ::entered.push(True); ::gate.get(); });\n"
    "        return arg;\n"
    "    }\n"
    "}\n"
    "binary sub bytes(string name) {\n"
    "    return QoreURLClassLoader::getCurrent().generateByteCode(\"qore.EmbeddedBindingTest.\" + name);\n"
    "}\n"
    "bool sub wait_entered(int ms) {\n"
    "    try {\n"
    "        entered.get(ms);\n"
    "        return True;\n"
    "    } catch (hash<ExceptionInfo> ex) {\n"
    "        if (ex.err == \"QUEUE-TIMEOUT\") {\n"
    "            return False;\n"
    "        }\n"
    "        rethrow;\n"
    "    }\n"
    "}\n"
    "sub release() { gate.push(1); }\n";

string route = ARGV[0] ?? "exit";
int status = (ARGV[1] ?? "0").toInt();

our *Program p = new Program(PO_NEW_STYLE);
try {
    p.parse(Source, "fork-locks-fixture");
} catch (hash<ExceptionInfo> ex) {
    if (ex.desc =~ /dbg_jni_hold_fork_locks/) {
        printf("skip: the module has no test hook (not a debug build)\n");
        exit(0);
    }
    rethrow;
}
p.run();
GeneratedBindings f(p.getProgramId(), p.callFunction("bytes", "Constants"), p.callFunction("bytes", "Calls"),
    p.callFunction("bytes", "$Functions"), p.callFunction("bytes", "$Constants"));
object thread = f.functionInThread("hold", 1);
# the Java thread holds the locks when the callback has signaled it
wait_fork_holder(p, thread);

int parent_pid = getpid();
# the child until it has been collected
*int uncollected;
# only the parent has the thread that holds the locks
on_exit {
    if (getpid() == parent_pid) {
        if (uncollected) {
            kill(uncollected, SIGKILL);
        }
        release_fork_holder(p, thread);
    }
}

hash<PipeInfo> pipe = File::getPipe();
flush();
int pid = fork();
if (!pid) {
    # the write end stays open until this process has exited: as standard error, which no Qore object owns, so that
    # the parent does not read the status of a child that is still in the teardown of its script
    pipe.read.close();
    stderr.redirect(pipe.write);
    pipe.write.close();
    setenv("QORE_JNI_DBG_FORK_MARKERS", "1");
    stderr.printf("child: ending\n");
    stderr.sync();
    delete p;
    stderr.printf("child: deleted the Program\n");
    stderr.sync();
    if (route == "exit") {
        Qore::exit(status);
    }
} else {
    uncollected = pid;
    pipe.write.close();
    bool ended = wait_fork_child(pipe.read, pid);
    string result;
    if (ended) {
        result = call_function(WaitChild, pid);
        remove uncollected;
    } else {
        result = "did not exit";
        fork_hang_stack(pid);
        kill(pid, SIGKILL);
        remove uncollected;
        call_function(WaitChild, pid);
    }
    printf("status: %s\n", result);
}
