//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
//
//  Permission is hereby granted, free of charge, to any person obtaining a
//  copy of this software and associated documentation files (the "Software"),
//  to deal in the Software without restriction, including without limitation
//  the rights to use, copy, modify, merge, publish, distribute, sublicense,
//  and/or sell copies of the Software, and to permit persons to whom the
//  Software is furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
//  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
//  DEALINGS IN THE SOFTWARE.
//
//------------------------------------------------------------------------------
#include "Jvm.h"

#include <qore/Qore.h>

#include <dlfcn.h>

#include "defs.h"
#include "Globals.h"
#include "QoreJniClassMap.h"

//! Returns true if libjsig is linked into the process
/** libjsig provides signal chaining so the JVM, V8, and Python can coexist.
    When libjsig is active, -Xrs must NOT be used because it prevents the JVM from
    installing the signal handlers that libjsig chains with other runtimes.
    Without libjsig, -Xrs is needed to prevent the JVM from clobbering signal handlers
    installed by V8 or Python.
*/
static bool hasLibjsig() {
    // libjsig interposes on sigaction(); if linked, its JVM_begin_signal_setting symbol is present
    void* sym = dlsym(RTLD_DEFAULT, "JVM_begin_signal_setting");
    return sym != nullptr;
}

namespace jni {

JavaVM* Jvm::vm = nullptr;
thread_local JNIEnv* Jvm::env;
bool Jvm::native_access_option_enabled = false;

QoreStringNode* Jvm::createVM() {
    assert(vm == nullptr);

    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_21;
    vm_args.ignoreUnrecognized = false;
    vm_args.nOptions = 0;

    // check if java.awt.headless should be set to avoid spawning subprocesses during
    // GraphicsEnvironment initialization (which causes StackOverflowError on the JVM's
    // "process reaper" thread with its hardcoded 32KB stack in JDK 25)
    bool set_headless = false;
    {
        QoreString val;
        if (SystemEnvironment::get("JAVA_AWT_HEADLESS", val)) {
            // not explicitly set; default to headless=true to avoid the process reaper issue
            set_headless = true;
        }
    }

    // When libjsig is linked into the process (qore links it by default), it chains signal
    // handlers between the JVM, V8, and Python so they can coexist. In that case, -Xrs must
    // NOT be used — it prevents the JVM from installing the handlers that libjsig chains.
    // Without libjsig, -Xrs is the only way to prevent the JVM from clobbering other runtimes'
    // signal handlers.
    bool use_reduced_signals = !hasLibjsig();
    size_t num_options = 1 + (use_reduced_signals ? 1 : 0) + (set_headless ? 1 : 0);
    bool disable_jit = false;
    bool enable_native_access = true;
    bool native_access_explicit = false;
#if JAVA_VERSION_MAJOR >= 23
    bool suppress_unsafe_warnings = true;
    bool unsafe_warnings_explicit = false;
#endif
    QoreString min_heap, max_heap, debug_cmd, native_access_opt;
    std::vector<std::string> strvec;
    // check QORE_JNI_DISABLE_JIT environment variable
    {
        QoreString val;
        if (!SystemEnvironment::get("QORE_JNI_DISABLE_JIT", val)) {
            disable_jit = q_parse_bool(val.c_str());
            if (disable_jit) {
                ++num_options;
                //printd(5, "jni module: disabling JIT\n");
            }
        }
    }
    if (!SystemEnvironment::get("QORE_JNI_MIN_HEAP_SIZE", min_heap)) {
        ++num_options;
        min_heap.prepend("-Xms");
    }
    if (!SystemEnvironment::get("QORE_JNI_MAX_HEAP_SIZE", max_heap)) {
        ++num_options;
        max_heap.prepend("-Xmx");
    }
    {
        QoreString val;
        if (!SystemEnvironment::get("QORE_JNI_DISABLE_NATIVE_ACCESS", val)) {
            if (q_parse_bool(val.c_str())) {
                enable_native_access = false;
            }
        }
    }
#if JAVA_VERSION_MAJOR >= 23
    {
        QoreString val;
        if (!SystemEnvironment::get("QORE_JNI_DISABLE_UNSAFE_WARNING_SUPPRESSION", val)) {
            if (q_parse_bool(val.c_str())) {
                suppress_unsafe_warnings = false;
            }
        }
    }
#endif
    {
        QoreString val;
        if (!SystemEnvironment::get("QORE_JNI_DEBUG", val)) {
            int port = atoi(val.c_str());
            if (port > 0) {
                // enable Java debugger
                ++num_options;
                debug_cmd.sprintf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=%d", port);
            }
        }
    }
    {
        QoreString val;
        if (!SystemEnvironment::get("QORE_JNI_JVM_ARGS", val)) {
            if (val.find("--enable-native-access") >= 0) {
                native_access_explicit = true;
            }
#if JAVA_VERSION_MAJOR >= 23
            if (val.find("--sun-misc-unsafe-memory-access") >= 0) {
                unsafe_warnings_explicit = true;
            }
#endif
            ssize_t pos = 0;
            while (true) {
                ssize_t i = val.find(' ', pos);
                if (i == -1) {
                    std::string opt(val.c_str() + pos);
                    strvec.push_back(opt);
                    break;
                }
                // add option
                std::string opt(val.c_str() + pos, i - pos);
                strvec.push_back(opt);
                pos = i + 1;
            }
            num_options += strvec.size();
        }
    }
    if (enable_native_access && !native_access_explicit) {
        ++num_options;
    }
#if JAVA_VERSION_MAJOR >= 23
    if (suppress_unsafe_warnings && !unsafe_warnings_explicit) {
        ++num_options;
    }
#endif
    native_access_option_enabled = enable_native_access;
#ifdef QORE_JNI_SUPPORT_CLASSPATH
    // this is disabled, because we use our own URLClassloader now to load all classes
    QoreString classpath;
    if (!SystemEnvironment::get("QORE_CLASSPATH", classpath)) {
        ++num_options;
    }
#endif
    JavaVMOption options[num_options];
    if (use_reduced_signals) {
        // "reduced signals" — only when libjsig is not available for signal chaining
        options[vm_args.nOptions++].optionString = (char*)"-Xrs";
        printd(LogLevel, "jni module: using -Xrs (libjsig not detected)\n");
    } else {
        printd(LogLevel, "jni module: libjsig detected, signal chaining active\n");
    }
    // thread stack size; use a minimum of 1MB if stack size is unknown/0
    // (can happen when module links against a different libqore than the running executable)
    size_t stack_size_kb = q_thread_get_stack_size() / 1024;
    if (!stack_size_kb) {
        stack_size_kb = 1024;  // 1MB minimum
    }
    QoreStringMaker thread_stack_size("-Xss%ldk", stack_size_kb);
    options[vm_args.nOptions++].optionString = (char*)thread_stack_size.c_str();
    if (set_headless) {
        options[vm_args.nOptions++].optionString = (char*)"-Djava.awt.headless=true";
    }
    if (disable_jit) {
        // disable JIT
        options[vm_args.nOptions++].optionString = (char*)"-Xint";
    }
    if (!debug_cmd.empty()) {
        // enable debugger
        options[vm_args.nOptions++].optionString = (char*)debug_cmd.c_str();
    }
    if (!min_heap.empty()) {
        // set minimum heap size
        options[vm_args.nOptions++].optionString = (char*)min_heap.c_str();
    }
    if (!max_heap.empty()) {
        // set maximum heap size
        options[vm_args.nOptions++].optionString = (char*)max_heap.c_str();
    }
    if (enable_native_access && !native_access_explicit) {
        native_access_opt = "--enable-native-access=ALL-UNNAMED";
        options[vm_args.nOptions++].optionString = (char*)native_access_opt.c_str();
    }
#if JAVA_VERSION_MAJOR >= 23
    QoreString unsafe_warning_opt;
    if (suppress_unsafe_warnings && !unsafe_warnings_explicit) {
        // suppress warnings about terminally deprecated sun.misc.Unsafe methods
        unsafe_warning_opt = "--sun-misc-unsafe-memory-access=allow";
        options[vm_args.nOptions++].optionString = (char*)unsafe_warning_opt.c_str();
    }
#endif
    if (!strvec.empty()) {
        for (auto& str: strvec) {
            //printd(5, "adding JVM pption: '%s'\n", str.c_str());
            options[vm_args.nOptions++].optionString = (char*)str.c_str();
        }
    }

#ifdef QORE_JNI_SUPPORT_CLASSPATH
    if (!classpath.empty()) {
        classpath.prepend("-Djava.class.path=");
        options[vm_args.nOptions++].optionString = (char*)classpath.c_str();
        printd(LogLevel, "classpath: '%s'\n", classpath.c_str());
    }
#endif

    vm_args.options = options;

    int rc = JNI_CreateJavaVM(&vm, reinterpret_cast<void**>(&env), &vm_args);
    if (rc != JNI_OK) {
        return new QoreStringNodeMaker("JNI_CreateJavaVM() failed with error code %d", rc);
    }
    // now the JVM will also enforce the thread size on the primary thread
#ifdef _QORE_HAS_ENFORCE_THREAD_SIZE_ON_PRIMARY_THREAD
    q_enforce_thread_size_on_primary_thread();
#endif
    return 0;
}

void Jvm::destroyVM() {
    assert(vm);

    Globals::cleanup();
    vm->DestroyJavaVM();
    vm = nullptr;
    env = nullptr;
}

JNIEnv *Jvm::attachAndGetEnv() {
    if (!vm) {
        throw UnableToAttachException(JNI_ERR);
    }

    if (env == nullptr) {
        jint err = vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        if (err != JNI_OK) {
            throw UnableToAttachException(err);
        }
        printd(LogLevel, "JNI - thread %d attached, env: %p\n", q_gettid(), env);
    }
    return env;
}

JNIEnv* Jvm::attachAndGetEnv(bool& new_attach) {
    if (!vm) {
        throw UnableToAttachException(JNI_ERR);
    }

    if (env == nullptr) {
        jint err = vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        if (err != JNI_OK) {
            throw UnableToAttachException(err);
        }
        new_attach = true;
        printd(LogLevel, "JNI - thread %d attached, env: %p\n", q_gettid(), env);
    } else {
        new_attach = false;
    }
    return env;
}

void Jvm::threadCleanup() {
    if (vm && env) {
        printd(LogLevel, "JNI - detaching thread, env: %p\n", env);
        vm->DetachCurrentThread();
        env = nullptr;
    }
}

} // namespace jni
