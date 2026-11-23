# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# included by test/jni-fork.qtest and its child scripts: waits for a child process created with fork() to end, and tells
# a child that is blocked from one that is slow, with no limit on the time that a child that makes progress can take
#
# All of this reads /proc.  Without it (a platform other than Linux), a blocked child cannot be told from a slow one: the
# wait ends only when the child closes its pipe, a blocked child is left to the test runner's time limit, and the exit
# status of a child is not known.

#! the time to wait for a report from the child before its threads are sampled
const ForkSampleInterval = 2s;

#! the same time in milliseconds
const ForkSampleMs = 2000;

#! the consecutive samples with no CPU time used and every thread sleeping after which the child is blocked
const ForkBlockedSamples = 5;

#! Returns the CPU time used by all the threads of a process, and whether all of them are sleeping
/** @return a hash with \c ticks, \c sleeping and \c ended (every thread has exited), or nothing if the process has no
    thread information
*/
*hash<auto> sub fork_child_cpu(int pid) {
    int ticks = 0;
    int threads = 0;
    int running = 0;
    bool sleeping = True;
    foreach string path in (glob(sprintf("/proc/%d/task/*/stat", pid))) {
        string stat;
        try {
            stat = ReadOnlyFile::readTextFile(path);
        } catch (hash<ExceptionInfo> ex) {
            # a thread that has just ended: the process is not idle
            sleeping = False;
            continue;
        }
        # the fields after the command name, which can contain spaces: the state, ... utime (11), stime (12)
        list<string> fields = stat.substr(stat.rfind(")") + 2).split(" ");
        ticks += fields[11].toInt() + fields[12].toInt();
        if (fields[0] != "S") {
            sleeping = False;
        }
        if (fields[0] != "Z" && fields[0] != "X") {
            ++running;
        }
        ++threads;
    }
    return threads ? {"ticks": ticks, "sleeping": sleeping, "ended": !running} : NOTHING;
}

#! Reads the pipe of a child until the child has closed it by ending
/** @param pipe the read end of a pipe whose write end the child holds until it has ended
    @param pid the child's process ID
    @param on_data called with each byte that the child writes

    @return True if the child has ended; False if it is blocked: with no report, it used no CPU time across
    consecutive samples and all its threads were sleeping.  A child that uses CPU time, or has a thread that is runnable
    or in an uninterruptible wait, is never reported, however long it takes.  Without \c /proc, this returns only when
    the child closes the pipe
*/
bool sub wait_fork_child(ReadOnlyFile pipe, int pid, *code on_data) {
    bool have_proc = is_dir("/proc/self/task");
    *int last;
    int idle = 0;
    while (True) {
        if (pipe.isDataAvailable(ForkSampleInterval)) {
            # one byte: a larger read would wait for that many bytes
            *binary data = pipe.readBinary(1);
            if (!data) {
                return True;
            }
            if (on_data) {
                on_data(data);
            }
            last = NOTHING;
            idle = 0;
            continue;
        }
        if (!have_proc) {
            continue;
        }
        *hash<auto> sample = fork_child_cpu(pid);
        # the child has ended, and another process holds the write end of the pipe
        if (!sample || sample.ended) {
            return True;
        }
        if (sample.sleeping && exists last && last == sample.ticks) {
            if (++idle == ForkBlockedSamples) {
                return False;
            }
        } else {
            idle = 0;
        }
        last = sample.ticks;
    }
}

#! Returns the wait status of a child that has ended and that this process has not collected
/** The kernel sets the exit code at the start of the exit of a thread, before it closes the files of the process, so
    the code is set once wait_fork_child() has seen the end of the pipe.

    @return the wait status (the exit code in the high byte, or the signal in the low byte), or nothing without \c /proc

    @throw FORK-TEST-ERROR \c /proc exists, and the status of the child cannot be read from it
*/
*int sub fork_child_exit_status(int pid) {
    if (!is_dir("/proc/self/task")) {
        return;
    }
    string stat;
    try {
        stat = ReadOnlyFile::readTextFile(sprintf("/proc/%d/stat", pid));
    } catch (hash<ExceptionInfo> ex) {
        throw "FORK-TEST-ERROR", sprintf("cannot read the exit status of child %d: %s: %s", pid, ex.err, ex.desc);
    }
    # the exit code is field 52 of the file
    list<string> fields = stat.substr(stat.rfind(")") + 2).split(" ");
    if (fields.size() < 50) {
        throw "FORK-TEST-ERROR", sprintf("cannot parse the exit status of child %d: %y", pid, stat);
    }
    return fields[49].toInt();
}

#! Waits for a Java thread to signal that it holds its state, or raises an exception if it ended without doing so
/** @param p a Program with the function \c wait_entered(int), which returns True if the thread has signaled within the
    time given in milliseconds
    @param thread the Java thread
*/
sub wait_fork_holder(Program p, object thread) {
    while (!p.callFunction("wait_entered", ForkSampleMs)) {
        if (!thread.isAlive()) {
            # it can have signaled just before it ended
            if (p.callFunction("wait_entered", 1)) {
                return;
            }
            throw "FORK-TEST-ERROR", "the Java thread ended before it held its state";
        }
    }
}

#! Releases a Java thread that waits in a Program's function \c release(), and joins it
/** If the release fails, the thread may never end: it is joined for a limited time, and the error is raised
*/
sub release_fork_holder(Program p, object thread) {
    *hash<ExceptionInfo> error;
    try {
        p.callFunction("release");
    } catch (hash<ExceptionInfo> ex) {
        error = ex;
    }
    if (error) {
        thread.join(ForkSampleMs);
        throw error.err, error.desc, error.arg;
    }
    thread.join();
}

#! Writes the stacks of a blocked child to the file named by \c QORE_JNI_FORK_HANG_STACK, if set, in a limited time
sub fork_hang_stack(int pid) {
    if (!ENV.QORE_JNI_FORK_HANG_STACK) {
        return;
    }
    system(sprintf("timeout -s KILL 60 gdb -q -batch -p %d -ex 'thread apply all bt 14' > '%s' 2>&1", pid,
        replace(ENV.QORE_JNI_FORK_HANG_STACK, "'", "'\\''")));
}
