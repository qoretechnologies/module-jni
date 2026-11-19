#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-save-object-race.qtest (qore#6790): the save-object callback of the Program is replaced or removed
# while it runs, by the callback itself or by another thread; the running callback must stay valid until it returns
#
# Java code creates a Qore object with org.qore.jni.QoreJavaApi.newObjectSave(), which runs the save-object callback
# of the Program; each callback is a closure that holds the only reference to a Marker object, whose destructor prints
# "<name> destroyed", so the output shows when the closure was released: after the callback returned, and not while
# it ran
#
# the argument names the case:
# - "reentrant-replace": the callback replaces itself with set_save_object_callback(), then reads its closure variable
# - "reentrant-clear": the same, but the callback removes itself (set_save_object_callback() with no argument); the
#   next object is saved in thread-local data
# - "concurrent-replace": the callback runs in a background thread and blocks on a Queue while the main thread replaces
#   it, then reads its closure variable
# - "release-throws": the callback removes itself, and the destructor of its Marker raises an exception when the
#   callback is released after it returns; the exception is raised to the Java code, which raises it to the caller of
#   newObjectSave()
# - "normal": the callback is set and runs twice, then is replaced by set_save_object_callback() outside of a callback
# then prints "after: <case>" and exits with status 0

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import org.qore.jni.QoreJavaApi

#! the class of the objects that the Java code creates
class Saved {
}

#! an object held only by a callback; its destructor shows when the callback was released
class Marker {
    private {
        string name;
        bool throw_in_dtor;
    }

    constructor(string name, bool throw_in_dtor = False) {
        self.name = name;
        self.throw_in_dtor = throw_in_dtor;
    }

    string getName() {
        return name;
    }

    destructor() {
        printf("%s destroyed\n", name);
        if (throw_in_dtor) {
            throw "SAVE-OBJECT-RACE-TEST-ERROR", sprintf("destructor of %s", name);
        }
    }
}

#! the objects saved by the callbacks
list<object> saved = ();

#! creates a Saved object from Java code, which runs the save-object callback
sub make_saved_object() {
    QoreJavaApi::newObjectSave("Saved");
}

#! returns a callback that holds the only reference to a new Marker, whose name it prints when it runs
code sub make_plain_callback(string name) {
    Marker marker(name);
    return sub (object obj) {
        saved += obj;
        printf("%s called\n", marker.getName());
    };
}

#! the callback replaces or removes itself, then reads its closure variable; with \a next_name, it is replaced by a
#! callback with that name
sub set_reentrant_callback(string name, *string next_name, bool throw_in_dtor = False) {
    Marker marker(name, throw_in_dtor);
    jni::set_save_object_callback(sub (object obj) {
        saved += obj;
        printf("%s start\n", marker.getName());
        if (next_name) {
            jni::set_save_object_callback(make_plain_callback(next_name));
        } else {
            jni::set_save_object_callback();
        }
        # the closure, and its closure variable, must still be valid here
        printf("%s end\n", marker.getName());
    });
}

sub reentrant_replace() {
    set_reentrant_callback("first", "second");
    make_saved_object();
    printf("saved: %d\n", saved.size());
    make_saved_object();
    printf("saved: %d\n", saved.size());
}

sub reentrant_clear() {
    set_reentrant_callback("first");
    make_saved_object();
    printf("saved: %d\n", saved.size());
    # no callback: the object is saved in thread-local data
    make_saved_object();
    printf("saved: %d thread-local: %d\n", saved.size(), (remove_thread_data("_jni_save")._jni_save ?? ()).size());
}

sub concurrent_replace() {
    # the callback signals that it runs on "running", then waits on "go"
    Queue running();
    Queue go();
    Queue done();
    {
        Marker marker("first");
        jni::set_save_object_callback(sub (object obj) {
            saved += obj;
            printf("%s start\n", marker.getName());
            running.push(True);
            go.get();
            # the closure, and its closure variable, must still be valid here
            printf("%s end\n", marker.getName());
        });
    }
    background sub () {
        try {
            make_saved_object();
        } catch (hash<ExceptionInfo> ex) {
            printf("background caught: %s: %s\n", ex.err, ex.desc);
        }
        done.push(True);
    }();
    running.get();
    # the callback runs in the background thread; it is replaced here
    jni::set_save_object_callback(make_plain_callback("second"));
    printf("replaced\n");
    go.push(True);
    done.get();
    printf("saved: %d\n", saved.size());
    make_saved_object();
    printf("saved: %d\n", saved.size());
}

sub release_throws() {
    set_reentrant_callback("first", NOTHING, True);
    try {
        make_saved_object();
        printf("no exception\n");
    } catch (hash<ExceptionInfo> ex) {
        printf("caught: %s: %s\n", ex.err, ex.desc);
    }
    printf("saved: %d\n", saved.size());
}

sub normal() {
    jni::set_save_object_callback(make_plain_callback("first"));
    make_saved_object();
    make_saved_object();
    printf("saved: %d\n", saved.size());
    jni::set_save_object_callback(make_plain_callback("second"));
    make_saved_object();
    printf("saved: %d\n", saved.size());
}

const Cases = {
    "reentrant-replace": \reentrant_replace(),
    "reentrant-clear": \reentrant_clear(),
    "concurrent-replace": \concurrent_replace(),
    "release-throws": \release_throws(),
    "normal": \normal(),
};

*string case_name = shift ARGV;
if (!case_name || !Cases{case_name}) {
    stderr.printf("usage: %s <%s>\n", get_script_name(), Cases.keys().join("|"));
    exit(2);
}
Cases{case_name}();
# release the last callback, so that its destructor output comes before the end marker
jni::set_save_object_callback();
printf("after: %s\n", case_name);
