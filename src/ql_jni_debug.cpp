/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    ql_jni_debug.cpp

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

#include "ql_jni_debug.h"

#ifdef DEBUG

#include "Globals.h"
#include "defs.h"
#include "Env.h"
#include "QoreToJava.h"
#include "QoreJniClassMap.h"
#include "JniHandleRegistry.h"

#include <sys/types.h>
#include <sys/wait.h>
#include <cerrno>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <map>
#include <mutex>
#include <new>
#include <stdexcept>
#include <string>
#include <thread>
#include <cstring>
#include <cstdio>
#include <cstdlib>

//! Returns the number of Programs holding entries in the module root namespace cache
/** The cache is keyed by the raw QoreProgram pointer, so an entry that outlives its Program is a
    dangling-pointer hazard: the address can be reused by a new Program, which would then be
    handed the dead Program's namespaces.  Nothing about that is observable from Qore code until
    it crashes, so this exposes the one number a regression test needs - see
    test/JniModuleRootNsCache.qtest.
*/
static QoreValue f_dbg_jni_module_root_ns_cache_size(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return (int64)jni::get_module_root_ns_cache_program_count();
}

//! Returns how many Programs have had module root namespace cache entries purged
static QoreValue f_dbg_jni_module_root_ns_cache_purge_count(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return (int64)jni::get_module_root_ns_cache_purge_count();
}

//! Returns how many module root namespace searches were made
static QoreValue f_dbg_jni_module_root_ns_search_count(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return (int64)jni::get_module_root_ns_search_count(false);
}

//! Returns how many module root namespace searches were made while the searching thread held the cache lock
/** The search calls the module manager, whose mutex libqore holds while the cache lock is taken to purge the entries
    of a destroyed module Program; a search made with the cache lock held is a lock-order inversion that can deadlock
    - see test/JniModuleRootNsCache.qtest.
*/
static QoreValue f_dbg_jni_module_root_ns_locked_search_count(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return (int64)jni::get_module_root_ns_search_count(true);
}

// the one-shot gate run at one point of a call made in another Program (see DbgJniGatePoint); set, run and
// cleared under the lock, and run only on the thread that set it, at the point it was set for
static std::mutex context_gate_lock;
static ResolvedCallReferenceNode* context_gate = nullptr;
static int context_gate_tid = 0;
static DbgJniGatePoint context_gate_point = DbgJniGatePoint::Lookup;
// set while a gate is set, so that a lookup made with no gate set costs only this load
std::atomic<bool> dbg_jni_context_gate_set{false};

//! Sets or clears a one-shot gate that runs the given code on this thread at one point of a call
/** \a point \c "lookup" (the default): between the lookup of a Program's jni context and its use (see
    JniExternalProgramData::setContext()); \c "entry": between the check that a Program has jni data and the entry
    into it for a call made in another Program (see jni_enter_call_program()); \c "create": between the check that a
    Program has no jni data and taking its parse lock to create it (see
    JniExternalProgramData::getCreateJniProgramData()); so that a test can delete the Program exactly in that window -
    see test/jni-cross-program-call.qtest - or create the jni data in another thread first - see
    test/jni-no-program-data.qtest; \c "issued-class": in the byte code generator, once it has accepted a class
    pointer that the module's own code gave, while it is issued on the thread - see test/jni-class-ptr.qtest;
    \c "code-gen-save": while the code generation lock is held, before a generated class is stored in the
    compilation cache - see test/jni-compilation-cache-clear.qtest; with no code, a gate that has not run is
    cleared
*/
static QoreValue f_dbg_jni_set_context_gate(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    QoreValue arg = get_param_value(args, 0);
    const ResolvedCallReferenceNode* code = arg.getType() == NT_NOTHING
        ? nullptr
        : arg.get<const ResolvedCallReferenceNode>();
    QoreValue point_arg = get_param_value(args, 1);
    DbgJniGatePoint point = DbgJniGatePoint::Lookup;
    if (point_arg.getType() == NT_STRING) {
        const char* p = point_arg.get<const QoreStringNode>()->c_str();
        if (!strcmp(p, "entry")) {
            point = DbgJniGatePoint::Entry;
        } else if (!strcmp(p, "create")) {
            point = DbgJniGatePoint::Create;
        } else if (!strcmp(p, "issued-class")) {
            point = DbgJniGatePoint::IssuedClass;
        } else if (!strcmp(p, "code-gen-save")) {
            point = DbgJniGatePoint::CodeGenSave;
        } else if (strcmp(p, "lookup")) {
            xsink->raiseException("DBG-JNI-GATE-ERROR", "unknown gate point '%s'; expecting \"lookup\", "
                "\"entry\", \"create\", \"issued-class\" or \"code-gen-save\"", p);
            return QoreValue();
        }
    }
    ResolvedCallReferenceNode* old;
    {
        std::lock_guard<std::mutex> guard(context_gate_lock);
        old = context_gate;
        context_gate = code ? code->refRefSelf() : nullptr;
        context_gate_tid = code ? q_gettid() : 0;
        context_gate_point = point;
        dbg_jni_context_gate_set.store(code != nullptr, std::memory_order_release);
    }
    if (old) {
        old->deref(xsink);
    }
    return QoreValue();
}

void dbg_jni_run_context_gate(DbgJniGatePoint point) {
    ResolvedCallReferenceNode* code;
    {
        std::lock_guard<std::mutex> guard(context_gate_lock);
        if (!context_gate || context_gate_tid != q_gettid() || context_gate_point != point) {
            return;
        }
        code = context_gate;
        context_gate = nullptr;
        context_gate_tid = 0;
        dbg_jni_context_gate_set.store(false, std::memory_order_release);
    }
    ExceptionSink xsink;
    {
        ReferenceHolder<ResolvedCallReferenceNode> holder(code, &xsink);
        ValueHolder rv(code->execValue(nullptr, &xsink), &xsink);
    }
    if (xsink) {
        throw jni::XsinkException(xsink);
    }
}

//! Imports a Java class into the given Program as jni_module_import() does, without making the Program current
/** jni_module_import(), the entry point of other modules (module-python's Java loader), takes the target Program as
    an argument; its callers make it current first, as a module command is run, so the class is added to it without an
    entry.  This calls it from the calling code's Program, so that the class is added to a Program that is not
    current, which enters it, as for a class that a thread that Java started loads - see
    test/jni-import-pending-program.qtest.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_import_into_program(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    const QoreObject* obj = get_param_value(args, 0).get<const QoreObject>();
    ReferenceHolder<QoreProgram> pgm(static_cast<QoreProgram*>(obj->getReferencedPrivateData(CID_PROGRAM,
        xsink)), xsink);
    if (!pgm) {
        return QoreValue();
    }
    const QoreStringNode* import = get_param_value(args, 1).get<const QoreStringNode>();
    TempEncodingHelper str(import, QCS_UTF8, xsink);
    if (*xsink) {
        return QoreValue();
    }
    jni_module_import(xsink, *pgm, str->c_str());
    return QoreValue();
}

//! Returns the pointer of a class of the given Program, as an integer (qore#6854)
/** A real class pointer of another Program, which Java code is given in test/jni-class-ptr.qtest to show that the
    class loader does not accept a class pointer from Java code.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_get_class_ptr(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const QoreObject* obj = get_param_value(args, 0).get<const QoreObject>();
    ReferenceHolder<QoreProgram> pgm(static_cast<QoreProgram*>(obj->getReferencedPrivateData(CID_PROGRAM,
        xsink)), xsink);
    if (!pgm) {
        return QoreValue();
    }
    const QoreStringNode* path = get_param_value(args, 1).get<const QoreStringNode>();
    TempEncodingHelper str(path, QCS_UTF8, xsink);
    if (*xsink) {
        return QoreValue();
    }
    const QoreClass* cls = pgm->findClass(str->c_str(), xsink);
    if (!cls) {
        if (!*xsink) {
            xsink->raiseException("DBG-JNI-ERROR", "class '%s' not found", str->c_str());
        }
        return QoreValue();
    }
    return static_cast<int64>(reinterpret_cast<intptr_t>(cls));
}

//! Clears the compilation cache of the given Program as a Java compiler does when it ends (qore#6855)
/** Returns False if the Program has no jni data.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_clear_compilation_cache(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    const QoreObject* obj = get_param_value(args, 0).get<const QoreObject>();
    ReferenceHolder<QoreProgram> pgm(static_cast<QoreProgram*>(obj->getReferencedPrivateData(CID_PROGRAM,
        xsink)), xsink);
    if (!pgm) {
        return QoreValue();
    }
    return jni::JniExternalProgramData::clearCompilationCache(*pgm);
}

//! Returns the number of entries in the compilation cache of the given Program, -1 if it has no jni data (qore#6855)
/** Waits for the code generation lock of the Program; a test calls it while no thread generates code for it.  DEBUG
    builds only.
*/
static QoreValue f_dbg_jni_compilation_cache_size(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    const QoreObject* obj = get_param_value(args, 0).get<const QoreObject>();
    ReferenceHolder<QoreProgram> pgm(static_cast<QoreProgram*>(obj->getReferencedPrivateData(CID_PROGRAM,
        xsink)), xsink);
    if (!pgm) {
        return QoreValue();
    }
    return jni::JniExternalProgramData::getCompilationCacheSizeForTest(*pgm);
}

// the class name for which jni::Env::findClass() fails (see f_dbg_jni_fail_find_class()); set and read under the lock
static std::mutex find_class_fail_lock;
static std::string find_class_fail_name;
std::atomic<bool> dbg_jni_find_class_fail_set{false};

//! Makes jni::Env::findClass() of the given class fail on every thread, or with no name, no longer fail
/** A lookup of the class then fails as JNI's \c FindClass() fails, with a \c NoClassDefFoundError pending, so that a
    test can make the lookup of the class of an error that a native method raises fail (qore#6739) - see
    test/jni-native-exception.qtest.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_fail_find_class(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const QoreStringNode* name = get_param_value(args, 0).get<const QoreStringNode>();
    std::lock_guard<std::mutex> guard(find_class_fail_lock);
    if (name) {
        find_class_fail_name.assign(name->c_str(), name->size());
    } else {
        find_class_fail_name.clear();
    }
    dbg_jni_find_class_fail_set.store(name != nullptr, std::memory_order_release);
    return QoreValue();
}

bool dbg_jni_find_class_fails(JNIEnv* jenv, const char* name) {
    {
        std::lock_guard<std::mutex> guard(find_class_fail_lock);
        if (find_class_fail_name != name) {
            return false;
        }
    }
    // the lookup of a class that does not exist leaves the Java exception of a failed lookup pending
    jclass cls = jenv->FindClass("org/qore/jni/dbg/NoSuchClassForFailedLookup");
    if (cls) {
        jenv->DeleteLocalRef(cls);
    }
    return jenv->ExceptionCheck() == JNI_TRUE;
}

//! the kinds of C++ exceptions that Jni::dbg_jni_native_throw() can set
enum class DbgJniNativeThrow {
    None,
    BadAlloc,
    StdException,
    Unknown,
    QoreJni,
    Xsink,
    JavaNotPending,
};

// the C++ exception that the next native method that calls Qore code on this thread throws
static thread_local DbgJniNativeThrow native_throw = DbgJniNativeThrow::None;

//! Makes the next native method that calls %Qore code from Java on this thread throw a C++ exception, once
/** \a kind: \c "bad_alloc" (\c std::bad_alloc), \c "std" (a \c std::runtime_error), \c "unknown" (an \c int),
    \c "qore" (a jni::QoreJniException with \c DBG-JNI-NATIVE-ERROR), \c "xsink" (a jni::XsinkException with
    \c DBG-JNI-NATIVE-ERROR; only on a thread attached to %Qore, as QoreJavaApi.callFunction() throws it before it
    attaches the thread), \c "java" (a jni::JavaException with no Java exception pending); with no kind, a
    C++ exception that has not been thrown is cleared.  The natives are QoreJavaApi.callFunction() and the calls of
    QoreObject methods and QoreClosure objects (see dbg_jni_native_throw_point()), so that a test can prove that the
    C++ exception does not leave the native method (qore#6739) - see test/jni-native-exception.qtest.  DEBUG builds
    only.
*/
static QoreValue f_dbg_jni_native_throw(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const QoreStringNode* kind = get_param_value(args, 0).get<const QoreStringNode>();
    if (!kind) {
        native_throw = DbgJniNativeThrow::None;
        return QoreValue();
    }
    static const std::map<std::string, DbgJniNativeThrow> kinds = {
        {"bad_alloc", DbgJniNativeThrow::BadAlloc},
        {"std", DbgJniNativeThrow::StdException},
        {"unknown", DbgJniNativeThrow::Unknown},
        {"qore", DbgJniNativeThrow::QoreJni},
        {"xsink", DbgJniNativeThrow::Xsink},
        {"java", DbgJniNativeThrow::JavaNotPending},
    };
    auto i = kinds.find(kind->c_str());
    if (i == kinds.end()) {
        xsink->raiseException("DBG-JNI-NATIVE-THROW-ERROR", "unknown kind '%s'; expecting \"bad_alloc\", \"std\", "
            "\"unknown\", \"qore\", \"xsink\" or \"java\"", kind->c_str());
        return QoreValue();
    }
    native_throw = i->second;
    return QoreValue();
}

void dbg_jni_native_throw_point() {
    DbgJniNativeThrow kind = native_throw;
    native_throw = DbgJniNativeThrow::None;
    switch (kind) {
        case DbgJniNativeThrow::None:
            return;
        case DbgJniNativeThrow::BadAlloc:
            throw std::bad_alloc();
        case DbgJniNativeThrow::StdException:
            throw std::runtime_error("dbg_jni_native_throw() std::runtime_error");
        case DbgJniNativeThrow::Unknown:
            throw 1;
        case DbgJniNativeThrow::QoreJni:
            throw jni::QoreJniException("DBG-JNI-NATIVE-ERROR", "dbg_jni_native_throw() QoreJniException");
        case DbgJniNativeThrow::Xsink: {
            ExceptionSink xsink;
            xsink.raiseException("DBG-JNI-NATIVE-ERROR", "dbg_jni_native_throw() XsinkException");
            throw jni::XsinkException(xsink);
        }
        case DbgJniNativeThrow::JavaNotPending:
            throw jni::JavaException();
    }
}

// the fail point set with Jni::dbg_jni_fail_point() and the thread that set it; set, read and cleared under the lock
static std::mutex fail_point_lock;
static DbgJniFailPoint fail_point = DbgJniFailPoint::Attach;
static std::thread::id fail_point_thread;
// the number of times that the point is still to be reached before it fails
static int64 fail_point_skip = 0;
std::atomic<bool> dbg_jni_fail_point_set{false};

// the names of the fail points
static const std::map<std::string, DbgJniFailPoint> fail_points = {
    {"attach", DbgJniFailPoint::Attach},
    {"program-data", DbgJniFailPoint::ProgramData},
    {"program-copy", DbgJniFailPoint::ProgramCopy},
    {"native-cleanup-stop", DbgJniFailPoint::NativeCleanupStop},
    {"classpath", DbgJniFailPoint::Classpath},
    {"governed-stack", DbgJniFailPoint::GovernedStack},
};

int dbg_jni_set_fail_point(const char* name, int64 skip) {
    assert(skip >= 0);
    auto i = fail_points.find(name);
    if (i == fail_points.end()) {
        return -1;
    }
    std::lock_guard<std::mutex> guard(fail_point_lock);
    fail_point = i->second;
    fail_point_thread = std::this_thread::get_id();
    fail_point_skip = skip;
    dbg_jni_fail_point_set.store(true, std::memory_order_release);
    return 0;
}

QoreStringNode* dbg_jni_set_env_fail_point() {
    TempString name(SystemEnvironment::get("QORE_JNI_DBG_FAIL_POINT"));
    if (!name || name->empty()) {
        return nullptr;
    }
    if (dbg_jni_set_fail_point(name->c_str())) {
        return new QoreStringNodeMaker("QORE_JNI_DBG_FAIL_POINT: unknown fail point '%s'", name->c_str());
    }
    return nullptr;
}

//! Makes the code at the given point fail once with the C++ exception that it throws when it fails, or with no point,
//! clears a fail point that has not been reached
/** \a point: \c "attach" (the next jni::Env on the calling thread fails to attach the thread to the JVM),
    \c "program-data" (JniExternalProgramData::getCreateJniProgramData() fails, as for a Program that is being
    deleted), \c "program-copy" (the copy of the jni data of a Program for a child Program fails to attach the thread
    to the JVM), \c "native-cleanup-stop" (stopping the NativeCleanup thread in the module's teardown fails to attach
    the thread to the JVM), \c "classpath" (the class loader raises a Java exception while the next classpath is
    added on the calling thread, as for an entry that cannot be added: qore#6779, see test/jni-classpath-error.qtest),
    \c "governed-stack" (the Java call of the check that the Java code on the thread's stack reaches a Program fails,
    and leaves its Java exception pending, so that a test can make a call fail while it has entered a Program - see
    test/jni-native-handles.qtest).  The point is passed \a skip times on the thread and fails when it is reached the
    next time, so that a test can make a later check of a call fail.  The point fails only on the thread that set it
    (the module is torn down on the thread that ends the process), so that a test can prove that the C++ exception
    thrown there does not escape into libqore (qore#6787, qore#6788, qore#6789) - see
    test/jni-cpp-exception-escape.qtest.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_fail_point(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const QoreStringNode* point = get_param_value(args, 0).get<const QoreStringNode>();
    if (!point) {
        std::lock_guard<std::mutex> guard(fail_point_lock);
        dbg_jni_fail_point_set.store(false, std::memory_order_release);
        return QoreValue();
    }
    int64 skip = get_param_value(args, 1).getAsBigInt();
    if (skip < 0) {
        xsink->raiseException("DBG-JNI-FAIL-POINT-ERROR", "skip %lld is negative", static_cast<long long>(skip));
        return QoreValue();
    }
    if (dbg_jni_set_fail_point(point->c_str(), skip)) {
        xsink->raiseException("DBG-JNI-FAIL-POINT-ERROR", "unknown point '%s'; expecting \"attach\", "
            "\"program-data\", \"program-copy\", \"native-cleanup-stop\", \"classpath\" or \"governed-stack\"",
            point->c_str());
    }
    return QoreValue();
}

bool dbg_jni_fail_point_hit(DbgJniFailPoint point) {
    std::lock_guard<std::mutex> guard(fail_point_lock);
    if (!dbg_jni_fail_point_set.load(std::memory_order_relaxed) || fail_point != point
            || fail_point_thread != std::this_thread::get_id()) {
        return false;
    }
    if (fail_point_skip) {
        --fail_point_skip;
        return false;
    }
    dbg_jni_fail_point_set.store(false, std::memory_order_release);
    return true;
}

//! Returns the number of global context Programs created and deleted in the process (qore#6799)
/** Returns a hash with the keys \c created and \c deleted; see dbg_jni_report_context_programs() for the counts at
    the module's shutdown
*/
static QoreValue f_dbg_jni_context_program_counts(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    ReferenceHolder<QoreHashNode> rv(new QoreHashNode(bigIntTypeInfo), xsink);
    rv->setKeyValue("created", (int64)jni::Globals::dbg_context_programs_created.load(std::memory_order_relaxed),
        xsink);
    rv->setKeyValue("deleted", (int64)jni::Globals::dbg_context_programs_deleted.load(std::memory_order_relaxed),
        xsink);
    return rv.release();
}

void dbg_jni_report_context_programs() noexcept {
    if (!getenv("QORE_JNI_DBG_CONTEXT_PROGRAM_REPORT")) {
        return;
    }
    fprintf(stderr, "jni-dbg: global context Programs created: %d deleted: %d\n",
        jni::Globals::dbg_context_programs_created.load(std::memory_order_relaxed),
        jni::Globals::dbg_context_programs_deleted.load(std::memory_order_relaxed));
    fflush(stderr);
}

std::atomic<int> dbg_jni_classpath_jdk_feature{0};

//! Makes the sandbox check of manifest Class-Path attributes assume the given JDK feature version as well as the
//! running JDK's, or with no argument, only the running JDK's
/** So that a test can check the refusal of manifest Class-Path attributes on a JDK version whose class loader has
    not been verified (qore#6822) - see test/jni-classpath-manifest-sandbox.qtest; it can only make the check
    stricter.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_set_classpath_jdk_feature(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    QoreValue v = get_param_value(args, 0);
    int64 feature = v.isNothing() ? 0 : v.getAsBigInt();
    if (feature < 0 || feature > 100000) {
        xsink->raiseException("JNI-ERROR", "invalid JDK feature version " QLLD, feature);
        return QoreValue();
    }
    dbg_jni_classpath_jdk_feature.store(static_cast<int>(feature), std::memory_order_release);
    return QoreValue();
}

// the java.lang.Runnable run in the module's shutdown window, as a global reference; see
// dbg_jni_run_shutdown_window_hook()
static std::mutex shutdown_window_hook_lock;
static jobject shutdown_window_hook = nullptr;

// the java.lang.Runnable run once by Globals::clearGlobalContext() before it waits for the threads that hold the global
// context Program, as a global reference; see dbg_jni_run_context_program_wait_hook()
static std::mutex context_program_wait_hook_lock;
static jobject context_program_wait_hook = nullptr;

//! Sets or clears the one-shot java.lang.Runnable hook in \a hook_ref, guarded by \a lock, from the first argument
static void set_runnable_hook(const QoreListNode* args, std::mutex& lock, jobject& hook_ref, ExceptionSink* xsink) {
    QoreValue arg = get_param_value(args, 0);
    try {
        jni::Env env;
        jobject ref = nullptr;
        if (arg.getType() != NT_NOTHING) {
            jni::LocalReference<jobject> obj(jni::QoreToJava::toAnyObject(env, arg));
            jni::LocalReference<jclass> runnable = env.findClass("java/lang/Runnable");
            if (!obj || !env.isInstanceOf(obj, runnable)) {
                xsink->raiseException("DBG-JNI-HOOK-ERROR", "the hook must be a java.lang.Runnable");
                return;
            }
            ref = jni::GlobalReference<jobject>::fromLocal(obj).release();
        }
        jobject old;
        {
            std::lock_guard<std::mutex> guard(lock);
            old = hook_ref;
            hook_ref = ref;
        }
        if (old) {
            (*env)->DeleteGlobalRef(old);
        }
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

//! Runs the one-shot java.lang.Runnable hook in \a hook_ref, guarded by \a lock, if any, and clears it; an error is
//! reported on \c stderr with the hook's \a name
static void run_runnable_hook(std::mutex& lock, jobject& hook_ref, const char* name) noexcept {
    jobject hook;
    {
        std::lock_guard<std::mutex> guard(lock);
        hook = hook_ref;
        hook_ref = nullptr;
    }
    if (!hook) {
        return;
    }
    try {
        // the thread is attached first, as the holder of the reference checks it with the thread's JNI environment
        jni::Env env(false);
        jni::GlobalReference<jobject> holder(hook);
        jni::LocalReference<jclass> runnable = env.findClass("java/lang/Runnable");
        jmethodID run = env.getMethod(runnable, "run", "()V");
        env.callVoidMethod(holder, run, nullptr);
    } catch (jni::JavaException& e) {
        fprintf(stderr, "jni-dbg: the %s hook raised a Java exception\n", name);
        e.ignore();
    } catch (...) {
        fprintf(stderr, "jni-dbg: the %s hook failed\n", name);
    }
    fflush(stderr);
}

//! Sets or clears the java.lang.Runnable run once by the module's delete function between the deletion of the global
//! context Program and the destruction of the JVM (see dbg_jni_run_shutdown_window_hook())
static QoreValue f_dbg_jni_set_shutdown_window_hook(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    set_runnable_hook(args, shutdown_window_hook_lock, shutdown_window_hook, xsink);
    return QoreValue();
}

void dbg_jni_run_shutdown_window_hook() noexcept {
    run_runnable_hook(shutdown_window_hook_lock, shutdown_window_hook, "shutdown window");
}

//! Sets or clears the java.lang.Runnable run once by Globals::clearGlobalContext() before it waits for the threads
//! that hold the global context Program (see dbg_jni_run_context_program_wait_hook())
static QoreValue f_dbg_jni_set_context_program_wait_hook(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    set_runnable_hook(args, context_program_wait_hook_lock, context_program_wait_hook, xsink);
    return QoreValue();
}

void dbg_jni_run_context_program_wait_hook() noexcept {
    run_runnable_hook(context_program_wait_hook_lock, context_program_wait_hook, "context Program wait");
}

//! Runs the callback with the locks of the shared class records and of the handle registry held
/** A test calls fork() while another thread is in the callback: the locks are then held forever in the child, whose
    exit path must not take them.
*/
static QoreValue f_dbg_jni_hold_fork_locks(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const ResolvedCallReferenceNode* callback = get_param_value(args, 0).get<const ResolvedCallReferenceNode>();
    try {
        jni::dbg_jni_with_shared_class_lock([&] () {
            jni::jni_handle_with_all_locks([&] () {
                ValueHolder rv(callback->execValue(nullptr, xsink), xsink);
            });
        });
    } catch (const std::exception& e) {
        xsink->raiseException("DBG-JNI-HOOK-ERROR", "%s", e.what());
    }
    return QoreValue();
}

namespace {
//! A java.util.function.BooleanSupplier hook that a native runs at one point of a call, on any thread: the hook decides
//! from the thread whether it runs, and returns true if it ran, which clears it
struct BooleanSupplierHook {
    //! the hook's name, for the errors reported on \c stderr
    const char* name;
    //! true while a hook is set; checked inline before the hook is run, so that a call made with no hook set costs only
    //! this load
    std::atomic<bool>& set;
    //! guards hook
    std::mutex lock;
    //! the hook, as a global reference, or nullptr
    jobject hook = nullptr;

    BooleanSupplierHook(const char* name, std::atomic<bool>& set) : name(name), set(set) {
    }
};

//! set while the thread runs a BooleanSupplierHook, so that a call that a hook makes does not run a hook again
thread_local bool supplier_hook_running = false;

//! marks the thread as running a BooleanSupplierHook for its lifetime
class SupplierHookRunning {
public:
    SupplierHookRunning() {
        supplier_hook_running = true;
    }
    ~SupplierHookRunning() {
        supplier_hook_running = false;
    }
};
}

//! Sets or clears the hook from the first argument
static void set_supplier_hook(const QoreListNode* args, BooleanSupplierHook& h, ExceptionSink* xsink) {
    QoreValue arg = get_param_value(args, 0);
    try {
        jni::Env env;
        jobject ref = nullptr;
        if (arg.getType() != NT_NOTHING) {
            jni::LocalReference<jobject> obj(jni::QoreToJava::toAnyObject(env, arg));
            jni::LocalReference<jclass> supplier = env.findClass("java/util/function/BooleanSupplier");
            if (!obj || !env.isInstanceOf(obj, supplier)) {
                xsink->raiseException("DBG-JNI-HOOK-ERROR", "the hook must be a java.util.function.BooleanSupplier");
                return;
            }
            ref = jni::GlobalReference<jobject>::fromLocal(obj).release();
        }
        jobject old;
        {
            std::lock_guard<std::mutex> guard(h.lock);
            old = h.hook;
            h.hook = ref;
            h.set.store(ref != nullptr, std::memory_order_release);
        }
        if (old) {
            (*env)->DeleteGlobalRef(old);
        }
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

//! Runs the hook, if set; it is not run while a Java exception is pending or from within a hook; an error running it
//! is reported on \c stderr
static void run_supplier_hook(BooleanSupplierHook& h) noexcept {
    if (supplier_hook_running) {
        return;
    }
    try {
        // the thread's class loader context is not set, as setting it looks up the context Program
        jni::Env env(false);
        // no JNI call may be made while a Java exception is pending (ex: one that is being converted)
        if ((*env)->ExceptionCheck()) {
            return;
        }
        // a reference of its own, so that the hook is not deleted while it runs if it is cleared in the meantime
        jni::LocalReference<jobject> hook;
        {
            std::lock_guard<std::mutex> guard(h.lock);
            if (!h.hook) {
                return;
            }
            hook = (*env)->NewLocalRef(h.hook);
        }
        if (!hook) {
            return;
        }
        SupplierHookRunning running;
        jni::LocalReference<jclass> supplier = env.findClass("java/util/function/BooleanSupplier");
        jmethodID get = env.getMethod(supplier, "getAsBoolean", "()Z");
        if (!env.callBooleanMethod(hook, get, nullptr)) {
            return;
        }
        // the hook ran: it is cleared, unless it was replaced in the meantime
        jobject old = nullptr;
        {
            std::lock_guard<std::mutex> guard(h.lock);
            if (h.hook && env.isSameObject(h.hook, hook)) {
                old = h.hook;
                h.hook = nullptr;
                h.set.store(false, std::memory_order_release);
            }
        }
        if (old) {
            (*env)->DeleteGlobalRef(old);
        }
    } catch (jni::JavaException& e) {
        fprintf(stderr, "jni-dbg: the %s hook raised a Java exception\n", h.name);
        e.ignore();
    } catch (...) {
        fprintf(stderr, "jni-dbg: the %s hook failed\n", h.name);
    }
    fflush(stderr);
}

//! Describes a java.lang.Throwable as the module describes a pending Java exception (JavaException::toString())
/** So that a test can check that describing a QoreExceptionWrapper neither takes nor leaks its Qore exception
    (qore#6864) - see test/jni-exception-wrapper-released.qtest.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_describe_throwable(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    try {
        jni::Env env;
        jni::LocalReference<jobject> obj(jni::QoreToJava::toAnyObject(env, get_param_value(args, 0)));
        if (!obj || !env.isInstanceOf(obj, jni::Globals::classThrowable)) {
            xsink->raiseException("DBG-JNI-ERROR", "the argument must be a java.lang.Throwable");
            return QoreValue();
        }
        if ((*env)->Throw(obj.cast<jthrowable>()) != JNI_OK) {
            xsink->raiseException("DBG-JNI-ERROR", "the Throwable cannot be thrown");
            return QoreValue();
        }
        jni::JavaException e;
        return e.toString(true);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return QoreValue();
}

// the hook run where a caller has obtained the global context Program; see dbg_jni_run_context_program_hook()
std::atomic<bool> dbg_jni_context_program_hook_set{false};
static BooleanSupplierHook context_program_hook("context Program", dbg_jni_context_program_hook_set);

//! Sets or clears the java.util.function.BooleanSupplier run where a caller has obtained the global context Program
//! (see dbg_jni_run_context_program_hook())
static QoreValue f_dbg_jni_set_context_program_hook(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    set_supplier_hook(args, context_program_hook, xsink);
    return QoreValue();
}

void dbg_jni_run_context_program_hook() noexcept {
    run_supplier_hook(context_program_hook);
}

// the hook run where a native has received a Program pointer from Java; see dbg_jni_run_java_program_ptr_hook()
std::atomic<bool> dbg_jni_java_program_ptr_hook_set{false};
static BooleanSupplierHook java_program_ptr_hook("Java Program pointer", dbg_jni_java_program_ptr_hook_set);

//! Sets or clears the java.util.function.BooleanSupplier run where a native has received a Program pointer from Java
//! (see dbg_jni_run_java_program_ptr_hook())
static QoreValue f_dbg_jni_set_java_program_ptr_hook(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    set_supplier_hook(args, java_program_ptr_hook, xsink);
    return QoreValue();
}

void dbg_jni_run_java_program_ptr_hook() noexcept {
    run_supplier_hook(java_program_ptr_hook);
}

//! Makes a new thread hold \a kind until dbg_jni_release_context_hold()
static QoreValue dbg_hold_context(jni::Globals::DbgContextHoldKind kind, ExceptionSink* xsink) {
    if (!jni::Globals::dbgHoldContext(kind)) {
        xsink->raiseException("DBG-JNI-HOOK-ERROR", "a context hold is already active, or the Program has no jni data");
    }
    return QoreValue();
}

static QoreValue f_dbg_jni_hold_context_lock(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    return dbg_hold_context(jni::Globals::DbgContextHoldKind::Lock, xsink);
}

static QoreValue f_dbg_jni_hold_context_program(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return dbg_hold_context(jni::Globals::DbgContextHoldKind::Program, xsink);
}

static QoreValue f_dbg_jni_hold_program_registry(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return dbg_hold_context(jni::Globals::DbgContextHoldKind::Registry, xsink);
}

static QoreValue f_dbg_jni_hold_save_object_lock(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return dbg_hold_context(jni::Globals::DbgContextHoldKind::SaveObject, xsink);
}

static QoreValue f_dbg_jni_hold_shutdown_gate(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    return dbg_hold_context(jni::Globals::DbgContextHoldKind::ShutdownGate, xsink);
}

//! Collects a child process that has ended; returns "exited <status>" or "signal <number>"
static QoreValue f_dbg_jni_wait_child_status(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    pid_t pid = static_cast<pid_t>(get_param_value(args, 0).getAsBigInt());
    int status = 0;
    pid_t rc;
    do {
        rc = waitpid(pid, &status, 0);
    } while (rc < 0 && errno == EINTR);
    if (rc < 0) {
        xsink->raiseErrnoException("DBG-JNI-HOOK-ERROR", errno, "waitpid() failed");
        return QoreValue();
    }
    QoreStringNode* rv = new QoreStringNode;
    if (WIFEXITED(status)) {
        rv->sprintf("exited %d", WEXITSTATUS(status));
    } else if (WIFSIGNALED(status)) {
        rv->sprintf("signal %d", WTERMSIG(status));
    } else {
        rv->sprintf("status %d", status);
    }
    return rv;
}

//! Counts a hold of the global context Program on the calling thread
static QoreValue f_dbg_jni_hold_own_context_program(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    jni::Globals::dbgHoldContextProgramOnThisThread();
    return QoreValue();
}

//! Releases the hold of dbg_jni_hold_own_context_program()
static QoreValue f_dbg_jni_release_own_context_program(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    jni::Globals::dbgReleaseContextProgramOnThisThread();
    return QoreValue();
}

std::atomic<bool> dbg_jni_save_object_gate_set{false};

namespace {
//! the state of the gate of dbg_jni_run_save_object_gate()
std::mutex save_object_gate_lock;
std::condition_variable save_object_gate_cond;
bool save_object_gate_entered = false;
bool save_object_gate_released = false;
}

void dbg_jni_run_save_object_gate() {
    std::unique_lock<std::mutex> lock(save_object_gate_lock);
    if (!dbg_jni_save_object_gate_set.exchange(false, std::memory_order_acq_rel)) {
        return;
    }
    save_object_gate_entered = true;
    save_object_gate_cond.notify_all();
    save_object_gate_cond.wait(lock, [] () { return save_object_gate_released; });
}

//! Arms the gate for the next change of a save object callback
static QoreValue f_dbg_jni_arm_save_object_gate(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    std::lock_guard<std::mutex> lock(save_object_gate_lock);
    save_object_gate_entered = false;
    save_object_gate_released = false;
    dbg_jni_save_object_gate_set.store(true, std::memory_order_release);
    return QoreValue();
}

//! Waits until a thread is held by the gate
static QoreValue f_dbg_jni_wait_save_object_gate(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    std::unique_lock<std::mutex> lock(save_object_gate_lock);
    save_object_gate_cond.wait(lock, [] () { return save_object_gate_entered; });
    return QoreValue();
}

//! Releases the thread held by the gate
static QoreValue f_dbg_jni_release_save_object_gate(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    std::lock_guard<std::mutex> lock(save_object_gate_lock);
    save_object_gate_released = true;
    save_object_gate_cond.notify_all();
    return QoreValue();
}

//! Returns True if the save object callback lock of the calling Program's jni data is held
static QoreValue f_dbg_jni_save_object_lock_held(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    jni::JniExternalProgramData* jpc = jni::jni_get_context();
    if (!jpc) {
        return xsink->raiseException("DBG-JNI-HOOK-ERROR", "the Program has no jni data");
    }
    QoreThreadLock& lock = jpc->dbgSaveObjectLock();
    if (lock.trylock()) {
        return true;
    }
    lock.unlock();
    return false;
}

//! Returns the reference count of a call reference
static QoreValue f_dbg_jni_code_reference_count(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const ResolvedCallReferenceNode* code = get_param_value(args, 0).get<const ResolvedCallReferenceNode>();
    return static_cast<int64>(code->reference_count());
}

//! Returns the ID of the Program that owns an object, or -1 if it has none (qore#7394)
static QoreValue f_dbg_jni_object_program_id(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    const QoreProgram* pgm = get_param_value(args, 0).get<const QoreObject>()->getProgram();
    return pgm ? static_cast<int64>(pgm->getProgramId()) : static_cast<int64>(-1);
}

//! Returns true once code that a sandbox governs has called Java in the process, or with the strict policy of Java
//! threads (qore#7081)
static QoreValue f_dbg_jni_java_thread_policy_strict(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    return jni::jni_java_thread_policy_strict();
}

//! Ends the hold of dbg_jni_hold_context_lock() or dbg_jni_hold_context_program()
static QoreValue f_dbg_jni_release_context_hold(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    jni::Globals::dbgReleaseContextHold();
    return QoreValue();
}

namespace {
//! the number of times that the teardown of a Program marked the state of its jdbc connections deleted, and the
//! number of them that waited for the jdbc operations of other threads (qore#7665)
std::mutex jdbc_deleted_wait_lock;
std::condition_variable jdbc_deleted_wait_cond;
int64 jdbc_deleted_marks = 0;
int64 jdbc_deleted_waits = 0;
}

void dbg_jni_jdbc_deleted_wait_entered(bool waits) {
    std::lock_guard<std::mutex> lock(jdbc_deleted_wait_lock);
    ++jdbc_deleted_marks;
    if (waits) {
        ++jdbc_deleted_waits;
    }
    jdbc_deleted_wait_cond.notify_all();
}

//! Waits until the teardown of Programs has marked the state of their jdbc connections deleted \a count times in
//! all, or for \a timeout_ms milliseconds, and returns {"marks": <count>, "waits": <the marks that waited for
//! operations of other threads>, "timeout": <true if the count was not reached>}; with 0, returns the numbers at once
static QoreValue f_dbg_jni_jdbc_deleted_wait(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    int64 count = get_param_value(args, 0).getAsBigInt();
    int64 timeout_ms = get_param_value(args, 1).getAsBigInt();
    int64 marks;
    int64 waits;
    bool reached;
    {
        std::unique_lock<std::mutex> lock(jdbc_deleted_wait_lock);
        // the bound is a guard against a test that would never end, not a synchronization
        reached = jdbc_deleted_wait_cond.wait_for(lock, std::chrono::milliseconds(timeout_ms),
            [count] () { return jdbc_deleted_marks >= count; });
        marks = jdbc_deleted_marks;
        waits = jdbc_deleted_waits;
    }
    ReferenceHolder<QoreHashNode> rv(new QoreHashNode(autoTypeInfo), xsink);
    rv->setKeyValue("marks", marks, xsink);
    rv->setKeyValue("waits", waits, xsink);
    rv->setKeyValue("timeout", !reached, xsink);
    return rv.release();
}

void init_jni_debug_functions(QoreNamespace& ns) {
    ns.addBuiltinVariant("dbg_jni_jdbc_deleted_wait", f_dbg_jni_jdbc_deleted_wait, QCF_NO_FLAGS, QDOM_DEFAULT,
        hashTypeInfo, 2, bigIntTypeInfo, QoreValue(), "count", softBigIntTypeInfo, QoreValue((int64)60000),
        "timeout_ms");
    ns.addBuiltinVariant("dbg_jni_hold_context_lock", f_dbg_jni_hold_context_lock, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_hold_context_program", f_dbg_jni_hold_context_program, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_hold_program_registry", f_dbg_jni_hold_program_registry, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_hold_shutdown_gate", f_dbg_jni_hold_shutdown_gate, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_hold_save_object_lock", f_dbg_jni_hold_save_object_lock, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_release_context_hold", f_dbg_jni_release_context_hold, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_hold_own_context_program", f_dbg_jni_hold_own_context_program, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_release_own_context_program", f_dbg_jni_release_own_context_program, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_arm_save_object_gate", f_dbg_jni_arm_save_object_gate, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_wait_save_object_gate", f_dbg_jni_wait_save_object_gate, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_release_save_object_gate", f_dbg_jni_release_save_object_gate, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo);
    ns.addBuiltinVariant("dbg_jni_save_object_lock_held", f_dbg_jni_save_object_lock_held, QCF_NO_FLAGS, QDOM_DEFAULT,
        boolTypeInfo);
    ns.addBuiltinVariant("dbg_jni_code_reference_count", f_dbg_jni_code_reference_count, QCF_NO_FLAGS, QDOM_DEFAULT,
        bigIntTypeInfo, 1, codeTypeInfo, QoreValue(), "code");
    ns.addBuiltinVariant("dbg_jni_object_program_id", f_dbg_jni_object_program_id, QCF_NO_FLAGS, QDOM_DEFAULT,
        bigIntTypeInfo, 1, objectTypeInfo, QoreValue(), "obj");
    ns.addBuiltinVariant("dbg_jni_java_thread_policy_strict", f_dbg_jni_java_thread_policy_strict, QCF_NO_FLAGS,
        QDOM_DEFAULT, boolTypeInfo);
    ns.addBuiltinVariant("dbg_jni_wait_child_status", f_dbg_jni_wait_child_status, QCF_NO_FLAGS, QDOM_DEFAULT,
        stringTypeInfo, 1, bigIntTypeInfo, QoreValue(), "pid");
    ns.addBuiltinVariant("dbg_jni_describe_throwable", f_dbg_jni_describe_throwable, QCF_NO_FLAGS, QDOM_DEFAULT,
        stringTypeInfo, 1, objectTypeInfo, QoreValue(), "throwable");
    ns.addBuiltinVariant("dbg_jni_set_classpath_jdk_feature", f_dbg_jni_set_classpath_jdk_feature, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo, 1, bigIntOrNothingTypeInfo, QoreValue(), "feature");
    ns.addBuiltinVariant("dbg_jni_import_into_program", f_dbg_jni_import_into_program, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 2, QC_PROGRAM->getTypeInfo(), QoreValue(), "pgm", stringTypeInfo, QoreValue(), "import");
    ns.addBuiltinVariant("dbg_jni_get_class_ptr", f_dbg_jni_get_class_ptr, QCF_NO_FLAGS, QDOM_DEFAULT,
        bigIntTypeInfo, 2, QC_PROGRAM->getTypeInfo(), QoreValue(), "pgm", stringTypeInfo, QoreValue(), "path");
    ns.addBuiltinVariant("dbg_jni_clear_compilation_cache", f_dbg_jni_clear_compilation_cache, QCF_NO_FLAGS,
        QDOM_DEFAULT, boolTypeInfo, 1, QC_PROGRAM->getTypeInfo(), QoreValue(), "pgm");
    ns.addBuiltinVariant("dbg_jni_compilation_cache_size", f_dbg_jni_compilation_cache_size, QCF_NO_FLAGS,
        QDOM_DEFAULT, bigIntTypeInfo, 1, QC_PROGRAM->getTypeInfo(), QoreValue(), "pgm");
    ns.addBuiltinVariant("dbg_jni_set_context_gate", f_dbg_jni_set_context_gate, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 2, codeOrNothingTypeInfo, QoreValue(), "code", stringOrNothingTypeInfo, QoreValue(),
        "point");
    ns.addBuiltinVariant("dbg_jni_module_root_ns_search_count", f_dbg_jni_module_root_ns_search_count,
        QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_locked_search_count",
        f_dbg_jni_module_root_ns_locked_search_count, QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_size", f_dbg_jni_module_root_ns_cache_size,
        QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_purge_count",
        f_dbg_jni_module_root_ns_cache_purge_count, QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_fail_find_class", f_dbg_jni_fail_find_class, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 1, stringOrNothingTypeInfo, QoreValue(), "name");
    ns.addBuiltinVariant("dbg_jni_native_throw", f_dbg_jni_native_throw, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 1, stringOrNothingTypeInfo, QoreValue(), "kind");
    ns.addBuiltinVariant("dbg_jni_fail_point", f_dbg_jni_fail_point, QCF_NO_FLAGS, QDOM_DEFAULT, nothingTypeInfo, 2,
        stringOrNothingTypeInfo, QoreValue(), "point", softBigIntTypeInfo, QoreValue((int64)0), "skip");
    ns.addBuiltinVariant("dbg_jni_context_program_counts", f_dbg_jni_context_program_counts, QCF_NO_FLAGS,
        QDOM_DEFAULT, hashTypeInfo);
    ns.addBuiltinVariant("dbg_jni_set_shutdown_window_hook", f_dbg_jni_set_shutdown_window_hook, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo, 1, objectOrNothingTypeInfo, QoreValue(), "hook");
    ns.addBuiltinVariant("dbg_jni_set_context_program_hook", f_dbg_jni_set_context_program_hook, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo, 1, objectOrNothingTypeInfo, QoreValue(), "hook");
    ns.addBuiltinVariant("dbg_jni_set_java_program_ptr_hook", f_dbg_jni_set_java_program_ptr_hook, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo, 1, objectOrNothingTypeInfo, QoreValue(), "hook");
    ns.addBuiltinVariant("dbg_jni_set_context_program_wait_hook", f_dbg_jni_set_context_program_wait_hook,
        QCF_NO_FLAGS, QDOM_DEFAULT, nothingTypeInfo, 1, objectOrNothingTypeInfo, QoreValue(), "hook");
    ns.addBuiltinVariant("dbg_jni_hold_fork_locks", f_dbg_jni_hold_fork_locks, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 1, codeTypeInfo, QoreValue(), "callback");
}

#endif // DEBUG
