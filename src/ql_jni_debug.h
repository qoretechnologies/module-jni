/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    ql_jni_debug.h

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

#ifndef QORE_JNI_QL_JNI_DEBUG_H
#define QORE_JNI_QL_JNI_DEBUG_H

#include <qore/Qore.h>

#include <jni.h>

#include <atomic>

#ifdef DEBUG
//! Registers debug-build-only diagnostic functions in the Jni namespace
/** Mirrors qore's own lib/ql_debug.cpp: these exist so regression tests can assert internal
    invariants that have no observable behaviour otherwise, and they are compiled out of
    release builds.
*/
DLLLOCAL void init_jni_debug_functions(QoreNamespace& ns);

//! The points of a call at which a gate set with Jni::dbg_jni_set_context_gate() can run
enum class DbgJniGatePoint {
    //! between the lookup of a Program's jni context and its use
    Lookup,
    //! between the check that a Program has jni data and the entry into it
    Entry,
};

//! Runs the one-shot gate set with Jni::dbg_jni_set_context_gate() if it was set by the current thread for the point
/** Called at the given point of a call (see DbgJniGatePoint, JniExternalProgramData::setContext() and
    jni_enter_call_program()), so that a test can run code exactly in that window; throws jni::XsinkException if the
    gate's code raises an exception
*/
DLLLOCAL void dbg_jni_run_context_gate(DbgJniGatePoint point);

//! True while a gate is set; checked inline before dbg_jni_run_context_gate(), so that a lookup made with no gate
//! set costs only this load
DLLLOCAL extern std::atomic<bool> dbg_jni_context_gate_set;

//! True while a class name is set with Jni::dbg_jni_fail_find_class(); checked inline in jni::Env::findClass()
DLLLOCAL extern std::atomic<bool> dbg_jni_find_class_fail_set;

//! Returns true if jni::Env::findClass() of the class must fail (see Jni::dbg_jni_fail_find_class())
/** If it returns true, the Java exception of a failed class lookup (\c NoClassDefFoundError) is pending in \a jenv,
    as when JNI's \c FindClass() fails
*/
DLLLOCAL bool dbg_jni_find_class_fails(JNIEnv* jenv, const char* name);

//! Throws the C++ exception set with Jni::dbg_jni_native_throw() for this thread, if any, once
/** Called at the start of the natives that call %Qore code from Java (see test/jni-native-exception.qtest), so that a
    test can prove that no C++ exception leaves a JNI native method (qore#6739)
*/
DLLLOCAL void dbg_jni_native_throw_point();
#endif

#endif // QORE_JNI_QL_JNI_DEBUG_H
