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

void init_jni_debug_functions(QoreNamespace& ns) {
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_size", f_dbg_jni_module_root_ns_cache_size,
        QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
    ns.addBuiltinVariant("dbg_jni_module_root_ns_cache_purge_count",
        f_dbg_jni_module_root_ns_cache_purge_count, QCF_NO_FLAGS, QDOM_DEFAULT, bigIntTypeInfo);
}

#endif // DEBUG
