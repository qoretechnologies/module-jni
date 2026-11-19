#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-doderef-no-throw.qtest (qore#6773): a Program loads the jni module and sets a save-object callback,
# a closure that holds the only reference to an object; the Program is then released, which releases its jni data,
# and the release of the callback releases the object in the jni data's dereference; an exception raised there must be
# reported, and the process must continue
#
# the argument names the case:
# - "delete": the object's destructor raises an exception; the Program is deleted with the delete operator
# - "scope": the same, but the last reference to the Program goes out of scope
# - "nested": the object holds another object whose destructor raises too; both exceptions are reported
# - "replace": the callback is replaced while the Program is alive (set_save_object_callback()), and the exception is
#   raised to its caller; then the Program is deleted
# - "clear": the same, but the callback is removed (set_save_object_callback() with no argument)
# - "ok": the object's destructor raises nothing; nothing is reported
# - "many": the "delete" case for 25 Programs, one after the other, so that the release of the jni data also runs the
#   JVM's garbage collector hint, which runs in every 20th release in the process: 25 releases in a row include one;
#   each exception is reported once
# - "cancel": 25 Programs with no callback are created, then released in a thread with a pending cancellation, which
#   the release of their jni data, including the garbage collector hint, must leave pending: nothing is reported, and
#   the thread prints "pending after: True"
# - "child-class": the object's class belongs to the released Program, whose code cannot run any more when its jni
#   data is released: the exception that the destructor call raises is reported
# then prints "after: <case>" and exits with status 0
#
# the classes of the objects, but the last case's, belong to this Program, whose code still runs when the other
# Program is released

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"

#! an object whose destructor raises an exception
class Boom {
    private {
        string name;
        *Boom inner;
    }

    constructor(string name, *Boom inner) {
        self.name = name;
        self.inner = inner;
    }

    destructor() {
        throw "DOREF-TEST-ERROR", sprintf("destructor of %s", name);
    }
}

#! an object whose destructor raises nothing
class Quiet {
    destructor() {
        printf("quiet destructor\n");
    }
}

#! the number of Programs released in the "many" case: more than the interval of the jni module's garbage collection
#! hint, which runs in every 20th release of a Program's jni data in the process
const ManyCount = 25;

#! the code of the Program that loads the jni module
const Src = "
class ChildQuiet {
    destructor() {}
}

sub set_callback(*object obj) {
    if (!obj) {
        obj = new ChildQuiet();
    }
    # the closure holds the only reference to the object once this function returns
    code callback = sub (object o) { remove o; if (!obj) { throw \"DOREF-TEST-ERROR\", \"no object\"; } };
    jni::set_save_object_callback(callback);
}

sub replace_callback() {
    jni::set_save_object_callback(sub (object o) {});
}

sub clear_callback() {
    jni::set_save_object_callback();
}
";

sub run_case(string case_name) {
    Program p(PO_NEW_STYLE);
    p.loadModule("jni");
    p.parse(Src, "jni-doderef-no-throw");
    *object obj;
    switch (case_name) {
        case "nested": obj = new Boom("outer", new Boom("inner")); break;
        case "ok": obj = new Quiet(); break;
        case "child-class": break;
        default: obj = new Boom(case_name); break;
    }
    p.callFunction("set_callback", remove obj);
    if (case_name == "replace") {
        p.callFunction("replace_callback");
    } else if (case_name == "clear") {
        p.callFunction("clear_callback");
    }
    if (case_name != "scope") {
        delete p;
    }
}

#! releases the jni data of the Programs in the list, one after the other, without a loop, as a loop raises a pending
#! cancellation
sub release_programs(reference<list<Program>> pgms) {
    if (!pgms) {
        return;
    }
    Program p = shift pgms;
    delete p;
    release_programs(\pgms);
}

#! releases the jni data of Programs in a thread with a pending cancellation, which must stay pending
sub cancel_case() {
    Counter ready(1);
    Counter cancelled(1);
    Counter done(1);
    int tid = background sub () {
        on_exit {
            done.dec();
        }
        # the Programs are created before the cancellation, as loading the jni module raises it
        list<Program> pgms = ();
        for (int i = 0; i < ManyCount; ++i) {
            Program p(PO_NEW_STYLE);
            p.loadModule("jni");
            pgms += p;
        }
        ready.dec();
        try {
            cancelled.waitForZero();
        } catch (hash<ExceptionInfo> ex) {
            printf("wait: %s\n", ex.err);
        }
        release_programs(\pgms);
        printf("pending after: %y\n", thread_cancelled());
        clear_thread_cancel();
    }();
    ready.waitForZero();
    cancel_thread(tid);
    cancelled.dec();
    done.waitForZero();
}

string case_name = ARGV[0] ?? "delete";
try {
    if (case_name == "many") {
        for (int i = 1; i <= ManyCount; ++i) {
            run_case(sprintf("many-%d", i));
        }
    } else if (case_name == "cancel") {
        cancel_case();
    } else {
        run_case(case_name);
    }
} catch (hash<ExceptionInfo> ex) {
    printf("caught: %s: %s\n", ex.err, ex.desc);
}
printf("after: %s\n", case_name);
