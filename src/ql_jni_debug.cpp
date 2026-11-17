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
#include <mutex>

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

// the one-shot gate run in the window between the lookup of a Program's jni context and its use; set, run and
// cleared under the lock, and run only on the thread that set it
static std::mutex context_gate_lock;
static ResolvedCallReferenceNode* context_gate = nullptr;
static int context_gate_tid = 0;
// set while a gate is set, so that a lookup made with no gate set costs only this load
std::atomic<bool> dbg_jni_context_gate_set{false};

//! Sets or clears a one-shot gate that runs the given code on this thread in the window between the lookup of a
//! Program's jni context and its use
/** The next lookup of a jni context made by this thread runs the code there (see
    JniExternalProgramData::setContext()), so that a test can delete the Program exactly in that window - see
    test/jni-cross-program-call.qtest; with no argument, a gate that has not run is cleared
*/
static QoreValue f_dbg_jni_set_context_gate(const QoreListNode* args, RuntimeConfig& rtcfg,
        ExceptionSink* xsink) {
    QoreValue arg = get_param_value(args, 0);
    const ResolvedCallReferenceNode* code = arg.getType() == NT_NOTHING
        ? nullptr
        : arg.get<const ResolvedCallReferenceNode>();
    ResolvedCallReferenceNode* old;
    {
        std::lock_guard<std::mutex> guard(context_gate_lock);
        old = context_gate;
        context_gate = code ? code->refRefSelf() : nullptr;
        context_gate_tid = code ? q_gettid() : 0;
        dbg_jni_context_gate_set.store(code != nullptr, std::memory_order_release);
    }
    if (old) {
        old->deref(xsink);
    }
    return QoreValue();
}

void dbg_jni_run_context_gate() {
    ResolvedCallReferenceNode* code;
    {
        std::lock_guard<std::mutex> guard(context_gate_lock);
        if (!context_gate || context_gate_tid != q_gettid()) {
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

void init_jni_debug_functions(QoreNamespace& ns) {
    ns.addBuiltinVariant("dbg_jni_set_context_gate", f_dbg_jni_set_context_gate, QCF_NO_FLAGS, QDOM_DEFAULT,
        nothingTypeInfo, 1, codeOrNothingTypeInfo, QoreValue(), "code");
    ns.addBuiltinVariant("dbg_jni_module_root_ns_search_count", f_dbg_jni_module_root_ns_search_count,
        QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_locked_search_count",
        f_dbg_jni_module_root_ns_locked_search_count, QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_size", f_dbg_jni_module_root_ns_cache_size,
        QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_purge_count",
        f_dbg_jni_module_root_ns_cache_purge_count, QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
}

#endif // DEBUG
