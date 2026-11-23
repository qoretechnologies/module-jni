# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# included by test/jni-fork.qtest and its child scripts: waits for a child process created with fork() to end, and tells
# a child that is blocked from one that is slow, with no limit on the time that a child that makes progress can take

#! the time to wait for a report from the child before its threads are sampled
const ForkSampleInterval = 2s;

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
    or in an uninterruptible wait, is never reported, however long it takes
*/
bool sub wait_fork_child(ReadOnlyFile pipe, int pid, *code on_data) {
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
        *hash<auto> sample = fork_child_cpu(pid);
        # the child has ended, and another process holds the write end of the pipe
        if (sample.ended || (!sample && is_dir("/proc/self/task"))) {
            return True;
        }
        if (sample && sample.sleeping && exists last && last == sample.ticks) {
            if (++idle == ForkBlockedSamples) {
                return False;
            }
        } else {
            idle = 0;
        }
        last = sample.ticks;
    }
}
