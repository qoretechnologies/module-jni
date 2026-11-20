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
    //! in JniExternalProgramData::getCreateJniProgramData(), between the check that a Program has no jni data and
    //! taking the Program's parse lock to create it (qore#6801)
    Create,
};

//! Runs the one-shot gate set with Jni::dbg_jni_set_context_gate() if it was set by the current thread for the point
/** Called at the given point of a call (see DbgJniGatePoint, JniExternalProgramData::setContext(),
    jni_enter_call_program() and JniExternalProgramData::getCreateJniProgramData()), so that a test can run code
    exactly in that window; throws jni::XsinkException if the gate's code raises an exception
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

//! Prints the number of global context Programs created and deleted in the process on \c stderr, if the environment
//! variable \c QORE_JNI_DBG_CONTEXT_PROGRAM_REPORT is set
/** Called at the end of the module's delete function, so that a test can check that the module's shutdown leaves no
    global context Program behind: each one created must have been deleted (qore#6799); the line printed is
    <tt>jni-dbg: global context Programs created: <created> deleted: <deleted></tt>
*/
DLLLOCAL void dbg_jni_report_context_programs() noexcept;

//! The points at which Jni::dbg_jni_fail_point() can make the module fail with a C++ exception
enum class DbgJniFailPoint {
    //! jni::Env's constructor fails to attach the thread to the JVM (jni::UnableToAttachException)
    Attach,
    //! JniExternalProgramData::getCreateJniProgramData() fails (jni::BasicException)
    ProgramData,
    //! JniExternalProgramData::copy() fails to attach the thread to the JVM (jni::UnableToAttachException)
    ProgramCopy,
    //! Globals::stopNativeCleanupThread() fails to attach the thread to the JVM (jni::UnableToAttachException)
    NativeCleanupStop,
    //! JniExternalProgramData::addClasspath() or addParentClasspath(): the class loader raises a Java exception
    Classpath,
};

//! The JDK feature version that the sandbox check of manifest Class-Path attributes assumes, or 0 for the running
//! JDK's (qore#6822)
/** Set with Jni::dbg_jni_set_classpath_jdk_feature(), so that a test can check the refusal of manifest Class-Path
    attributes on a JDK version whose class loader has not been verified; it does not exist in release builds
*/
DLLLOCAL extern std::atomic<int> dbg_jni_classpath_jdk_feature;

//! Sets the fail point named \a name for the calling thread, as Jni::dbg_jni_fail_point() does
/** @return 0 if set, -1 if \a name is not the name of a fail point
*/
DLLLOCAL int dbg_jni_set_fail_point(const char* name);

//! Sets the fail point named in the \c QORE_JNI_DBG_FAIL_POINT environment variable, if any, for the calling thread
/** Called at the start of the module's initialization, so that a test can make the initialization fail at a point
    that is reached before Jni::dbg_jni_fail_point() can be called (ex: \c "classpath" for the classpath of the
    \c QORE_JNI_CLASSPATH environment variable, qore#6778)

    @return nullptr, or the error if the variable does not name a fail point, which the caller owns
*/
DLLLOCAL QoreStringNode* dbg_jni_set_env_fail_point();

//! True while a fail point is set with Jni::dbg_jni_fail_point(); checked inline in dbg_jni_fail_at()
DLLLOCAL extern std::atomic<bool> dbg_jni_fail_point_set;

//! Returns true once, and clears the fail point, if the fail point set with Jni::dbg_jni_fail_point() is \a point
//! and was set by the calling thread
DLLLOCAL bool dbg_jni_fail_point_hit(DbgJniFailPoint point);

//! Returns true if the code at \a point must fail with a C++ exception, as set with Jni::dbg_jni_fail_point()
/** The caller throws the C++ exception that the code at the point throws when it fails; a check made with no fail
    point set costs only an atomic load
*/
DLLLOCAL inline bool dbg_jni_fail_at(DbgJniFailPoint point) {
    return dbg_jni_fail_point_set.load(std::memory_order_acquire) && dbg_jni_fail_point_hit(point);
}
#endif

#endif // QORE_JNI_QL_JNI_DEBUG_H
