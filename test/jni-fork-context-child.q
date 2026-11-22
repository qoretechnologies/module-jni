#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# run by test/jni-fork.qtest: another thread of the process holds state of the jni module when fork() is called, and the
# child ends, which runs the module's cleanup in the child
#
# arguments: the hold ("lock", "program", "registry", "save-object" or "none") and how the child ends ("end": the end of
# its script, which deletes the module; "exit": exit(), which runs the module's exit handler)
#
# The child's stderr is the write end of a pipe, which stays open until the child has ended: the parent reads the
# module's markers from it until the end of the data, with a generous limit for each read, so that a child that does
# not end is reported instead of blocking the test.  The parent kills such a child, and collects the child's exit status
# in every case, so that a child that crashes in its cleanup is reported as well.

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import java.lang.StringBuilder

const ChildLimit = 30s;
const Holds = {
    "lock": "Jni::org::qore::jni::dbg_jni_hold_context_lock",
    "program": "Jni::org::qore::jni::dbg_jni_hold_context_program",
    "registry": "Jni::org::qore::jni::dbg_jni_hold_program_registry",
    "save-object": "Jni::org::qore::jni::dbg_jni_hold_save_object_lock",
};
const ReleaseHold = "Jni::org::qore::jni::dbg_jni_release_context_hold";
const WaitChild = "Jni::org::qore::jni::dbg_jni_wait_child_status";

string hold = ARGV[0];
string ending = ARGV[1];
int parent_pid = getpid();

StringBuilder sb("used");
if (Holds{hold}) {
    call_function(Holds{hold});
}
# only the parent has the thread of the hold
on_exit {
    if (Holds{hold} && getpid() == parent_pid) {
        call_function(ReleaseHold);
    }
}

hash<PipeInfo> pipe = File::getPipe();
flush();
int pid = fork();
if (!pid) {
    pipe.read.close();
    stderr.redirect(pipe.write);
    pipe.write.close();
    setenv("QORE_JNI_DBG_FORK_MARKERS", "1");
    stderr.printf("child: ending\n");
    stderr.sync();
    if (ending == "exit") {
        Qore::exit(0);
    }
} else {
    pipe.write.close();
    bool ended = False;
    while (pipe.read.isDataAvailable(ChildLimit)) {
        # one byte: a larger read would wait for that many bytes, past the limit
        *binary data = pipe.read.readBinary(1);
        if (!data) {
            ended = True;
            break;
        }
        print(data.toString());
    }
    if (ended) {
        printf("parent: the child has ended: %s\n", call_function(WaitChild, pid));
    } else {
        printf("parent: the child did not exit: pid %d\n", pid);
        flush();
        if (ENV.QORE_JNI_FORK_HANG_STACK) {
            system(sprintf("gdb -q -batch -p %d -ex 'thread apply all bt 14' > %s 2>&1", pid,
                ENV.QORE_JNI_FORK_HANG_STACK));
        }
        kill(pid, SIGKILL);
        call_function(WaitChild, pid);
    }
    flush();
}
