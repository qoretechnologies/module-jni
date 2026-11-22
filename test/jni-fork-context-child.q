#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# run by test/jni-fork.qtest: another thread of the process holds the jni module's context lock or global context
# Program when fork() is called, and the child ends, which runs the module's cleanup in the child
#
# arguments: the hold ("lock", "program" or "none") and how the child ends ("end": the end of its script, which deletes
# the module; "exit": exit(), which runs the module's exit handler)
#
# The child's stderr is the write end of a pipe, which stays open until the child has exited: the parent reads the
# module's markers from it until the end of the data, with a generous limit for each read, so that a child that does
# not exit is reported instead of blocking the test.  The parent kills such a child.

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import java.lang.StringBuilder

const ChildLimit = 30s;
const Holds = {
    "lock": "Jni::org::qore::jni::dbg_jni_hold_context_lock",
    "program": "Jni::org::qore::jni::dbg_jni_hold_context_program",
};

string hold = ARGV[0];
string ending = ARGV[1];

StringBuilder sb("used");
if (Holds{hold}) {
    call_function(Holds{hold});
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
        printf("parent: the child has exited\n");
    } else {
        printf("parent: the child did not exit: pid %d\n", pid);
        flush();
        if (ENV.QORE_JNI_FORK_HANG_STACK) {
            system(sprintf("gdb -q -batch -p %d -ex 'thread apply all bt 12' > %s 2>&1", pid,
                ENV.QORE_JNI_FORK_HANG_STACK));
        }
        kill(pid, SIGKILL);
    }
    flush();
    if (Holds{hold}) {
        call_function("Jni::org::qore::jni::dbg_jni_release_context_hold");
    }
}
