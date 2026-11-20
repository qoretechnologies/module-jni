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

#include <atomic>
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
    test/jni-no-program-data.qtest; with no code, a gate that has not run is cleared
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
        } else if (strcmp(p, "lookup")) {
            xsink->raiseException("DBG-JNI-GATE-ERROR", "unknown gate point '%s'; expecting \"lookup\", "
                "\"entry\" or \"create\"", p);
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
std::atomic<bool> dbg_jni_fail_point_set{false};

// the names of the fail points
static const std::map<std::string, DbgJniFailPoint> fail_points = {
    {"attach", DbgJniFailPoint::Attach},
    {"program-data", DbgJniFailPoint::ProgramData},
    {"program-copy", DbgJniFailPoint::ProgramCopy},
    {"native-cleanup-stop", DbgJniFailPoint::NativeCleanupStop},
    {"classpath", DbgJniFailPoint::Classpath},
};

int dbg_jni_set_fail_point(const char* name) {
    auto i = fail_points.find(name);
    if (i == fail_points.end()) {
        return -1;
    }
    std::lock_guard<std::mutex> guard(fail_point_lock);
    fail_point = i->second;
    fail_point_thread = std::this_thread::get_id();
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
    added on the calling thread, as for an entry that cannot be added: qore#6779, see test/jni-classpath-error.qtest).
    The point fails only on the thread that set it (the module is torn down on the thread that ends the process), so
    that a test can prove that the C++ exception thrown there does not escape into libqore (qore#6787, qore#6788,
    qore#6789) - see test/jni-cpp-exception-escape.qtest.  DEBUG builds only.
*/
static QoreValue f_dbg_jni_fail_point(const QoreListNode* args, RuntimeConfig& rtcfg, ExceptionSink* xsink) {
    const QoreStringNode* point = get_param_value(args, 0).get<const QoreStringNode>();
    if (!point) {
        std::lock_guard<std::mutex> guard(fail_point_lock);
        dbg_jni_fail_point_set.store(false, std::memory_order_release);
        return QoreValue();
    }
    if (dbg_jni_set_fail_point(point->c_str())) {
        xsink->raiseException("DBG-JNI-FAIL-POINT-ERROR", "unknown point '%s'; expecting \"attach\", "
            "\"program-data\", \"program-copy\", \"native-cleanup-stop\" or \"classpath\"", point->c_str());
    }
    return QoreValue();
}

bool dbg_jni_fail_point_hit(DbgJniFailPoint point) {
    std::lock_guard<std::mutex> guard(fail_point_lock);
    if (!dbg_jni_fail_point_set.load(std::memory_order_relaxed) || fail_point != point
            || fail_point_thread != std::this_thread::get_id()) {
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

void init_jni_debug_functions(QoreNamespace& ns) {
    ns.addBuiltinVariant("dbg_jni_set_classpath_jdk_feature", f_dbg_jni_set_classpath_jdk_feature, QCF_NO_FLAGS,
        QDOM_DEFAULT, nothingTypeInfo, 1, bigIntOrNothingTypeInfo, QoreValue(), "feature");
    ns.addBuiltinVariant("dbg_jni_import_into_program", f_dbg_jni_import_into_program, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 2, QC_PROGRAM->getTypeInfo(), QoreValue(), "pgm", stringTypeInfo, QoreValue(), "import");
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
    ns.addBuiltinVariant("dbg_jni_fail_point", f_dbg_jni_fail_point, QCF_NO_FLAGS, QDOM_DEFAULT, nothingTypeInfo, 1,
        stringOrNothingTypeInfo, QoreValue(), "point");
    ns.addBuiltinVariant("dbg_jni_context_program_counts", f_dbg_jni_context_program_counts, QCF_NO_FLAGS,
        QDOM_DEFAULT, hashTypeInfo);
}

#endif // DEBUG
