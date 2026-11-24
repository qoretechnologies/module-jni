#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-fork.qtest: a child created with fork() after the JVM was started ends its script, so that its Program
# and the module are deleted in the child
#
# the argument is "none", or "block": a global object of the child then blocks forever in its destructor, in the
# cleanup of the script.  The child's stderr is the write end of a pipe, which stays open until the child has ended; the
# parent reports a blocked child (see wait_fork_child()), kills it, and prints how the child ended.

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import java.lang.StringBuilder

%include jni-fork-progress.ql

class BlockInCleanup {
    destructor() {
        Queue never();
        never.get();
    }
}

string mode = ARGV[0] ?? "none";
our *BlockInCleanup blocker;

StringBuilder sb("used");
printf("parent: %s\n", sb.toString());
flush();

int parent_pid = getpid();
# the child until it has ended: it is killed also if the parent fails, as it holds the parent's output open
*int uncollected;
on_exit {
    if (uncollected && getpid() == parent_pid) {
        kill(uncollected, SIGKILL);
    }
}

hash<PipeInfo> pipe = File::getPipe();
int pid = fork();
if (!pid) {
    pipe.read.close();
    stderr.redirect(pipe.write);
    pipe.write.close();
    stderr.printf("child: done\n");
    stderr.sync();
    if (mode == "block") {
        blocker = new BlockInCleanup();
    }
} else {
    uncollected = pid;
    pipe.write.close();
    if (wait_fork_child(pipe.read, pid, sub (binary data) { print(data.toString()); })) {
        *int status = fork_child_exit_status(pid);
        remove uncollected;
        printf("parent: the child has ended: %s\n", exists status ? string(status) : "unknown");
    } else {
        printf("parent: the child did not exit\n");
        flush();
        fork_hang_stack(pid);
    }
    flush();
}
