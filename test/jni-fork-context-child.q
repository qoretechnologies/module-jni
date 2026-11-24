#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# run by test/jni-fork.qtest: another thread of the process holds state of the jni module when fork() is called, and the
# child ends, which runs the module's cleanup in the child
#
# arguments: the hold ("lock", "program", "registry", "save-object", "shutdown-gate", "own-program" or "none";
# "own-program" is a hold of the thread that calls fork()) and how the child ends ("end": the end of its script, which
# deletes the module; "exit": exit(), which runs the module's exit handler; "swap": the end of its script, after two threads of the child have
# changed the save object callback)
#
# The child's stderr is the write end of a pipe, which stays open until the child has ended: the parent reads the
# module's markers from it until the end of the data, so that a child that is blocked (see wait_fork_child()) is reported
# instead of blocking the test, with no limit on the time that a child that makes progress can take.  The parent kills a
# blocked child, and collects the child's exit status in every case, so that a child that crashes in its cleanup is
# reported as well.

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import java.lang.StringBuilder

%include jni-fork-progress.ql

const Holds = {
    "lock": "Jni::org::qore::jni::dbg_jni_hold_context_lock",
    "program": "Jni::org::qore::jni::dbg_jni_hold_context_program",
    "registry": "Jni::org::qore::jni::dbg_jni_hold_program_registry",
    "save-object": "Jni::org::qore::jni::dbg_jni_hold_save_object_lock",
    "shutdown-gate": "Jni::org::qore::jni::dbg_jni_hold_shutdown_gate",
};
const ReleaseHold = "Jni::org::qore::jni::dbg_jni_release_context_hold";
const OwnHold = "Jni::org::qore::jni::dbg_jni_hold_own_context_program";
const ReleaseOwnHold = "Jni::org::qore::jni::dbg_jni_release_own_context_program";
const Dbg = "Jni::org::qore::jni::dbg_jni_";
const WaitChild = "Jni::org::qore::jni::dbg_jni_wait_child_status";

string hold = ARGV[0];
string ending = ARGV[1];
int parent_pid = getpid();

StringBuilder sb("used");
if (Holds{hold}) {
    call_function(Holds{hold});
} else if (hold == "own-program") {
    call_function(OwnHold);
}
# the child until it has been collected: it is killed also if the parent fails, as it holds the parent's output open
*int uncollected;
# only the parent has the thread of the hold
on_exit {
    if (getpid() == parent_pid) {
        if (uncollected) {
            kill(uncollected, SIGKILL);
        }
        if (Holds{hold}) {
            call_function(ReleaseHold);
        } else if (hold == "own-program") {
            call_function(ReleaseOwnHold);
        }
    }
}

#! two threads of a child change the save object callback, one of them while the other is between its read and its change
sub swap_callback() {
    int calls = 0;
    code first = sub () { ++calls; };
    code second = sub () { ++calls; };
    code third = sub () { ++calls; };
    # references of its own, so that a release too many is counted instead of freeing the closure under this code
    list<code> pins = (first, first, first);
    int expected = call_function(Dbg + "code_reference_count", first);
    set_save_object_callback(first);

    call_function(Dbg + "arm_save_object_gate");
    Counter held_done(1);
    background sub () {
        on_exit held_done.dec();
        set_save_object_callback(second);
    }();
    call_function(Dbg + "wait_save_object_gate");
    # the change is exclusive if the thread holds the lock in the gate
    bool exclusive = call_function(Dbg + "save_object_lock_held");
    if (!exclusive) {
        # with no exclusion, another thread reads the same old callback
        Counter other_done(1);
        background sub () {
            on_exit other_done.dec();
            set_save_object_callback(third);
        }();
        other_done.waitForZero();
    }
    call_function(Dbg + "release_save_object_gate");
    held_done.waitForZero();
    if (exclusive) {
        set_save_object_callback(third);
    }
    int references = call_function(Dbg + "code_reference_count", first);
    stderr.printf("child: swap exclusive=%y references=%d expected=%d\n", exclusive, references, expected);
    stderr.sync();
    set_save_object_callback();
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
    if (ending == "swap") {
        swap_callback();
    }
} else {
    uncollected = pid;
    pipe.write.close();
    bool ended = wait_fork_child(pipe.read, pid, sub (binary data) { print(data.toString()); });
    if (ended) {
        string child_status = call_function(WaitChild, pid);
        remove uncollected;
        printf("parent: the child has ended: %s\n", child_status);
    } else {
        printf("parent: the child did not exit: pid %d\n", pid);
        flush();
        fork_hang_stack(pid);
        kill(pid, SIGKILL);
        remove uncollected;
        call_function(WaitChild, pid);
    }
    flush();
}
