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
#include <unistd.h>
#include <sys/resource.h>

#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include "defs.h"
#include "GeneratedBinding.h"
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

#ifdef _QORE_HAS_ENFORCE_STACK_SIZE_ON_PRIMARY_THREAD
namespace {
//! The JVM options that determine the stack of the primary thread
/** The stack zones are given in units of 4 KB, as in HotSpot; their defaults are HotSpot's defaults on 64-bit Linux
    and macOS
*/
struct JvmStackOptions {
    //! the stack size of Java threads in bytes; 0 if not set
    size_t stack_size = 0;
    //! the size of the red zone
    size_t red_pages = 1;
    //! the size of the yellow zone
    size_t yellow_pages = 2;
    //! the size of the reserved zone
    size_t reserved_pages = 1;
};

//! Parses a JVM size value with an optional k, m or g suffix
/** @return 0 if OK, -1 if the value is not a size
*/
int parse_jvm_size(const char* str, size_t& size) {
    if (!isdigit(static_cast<unsigned char>(*str))) {
        return -1;
    }
    char* end;
    errno = 0;
    unsigned long long val = strtoull(str, &end, 10);
    if (errno) {
        return -1;
    }
    unsigned long long mult = 1;
    switch (*end) {
        case '\0': break;
        case 'k': case 'K': mult = 1024; ++end; break;
        case 'm': case 'M': mult = 1024 * 1024; ++end; break;
        case 'g': case 'G': mult = 1024 * 1024 * 1024; ++end; break;
        default: return -1;
    }
    if (*end || val > SIZE_MAX / mult) {
        return -1;
    }
    size = static_cast<size_t>(val * mult);
    return 0;
}

//! Applies one JVM option to the stack options; options that do not affect the stack and invalid values are ignored
void apply_jvm_stack_option(const char* opt, JvmStackOptions& sopts) {
    size_t val;
    if (!strncmp(opt, "-Xss", 4)) {
        if (!parse_jvm_size(opt + 4, val)) {
            sopts.stack_size = val;
        }
        return;
    }
    if (strncmp(opt, "-XX:", 4)) {
        return;
    }
    opt += 4;
    const char* eq = strchr(opt, '=');
    if (!eq || parse_jvm_size(eq + 1, val)) {
        return;
    }
    size_t len = eq - opt;
    auto is = [opt, len] (const char* name) -> bool {
        return strlen(name) == len && !strncmp(opt, name, len);
    };
    if (is("ThreadStackSize")) {
        // given in KB
        if (val <= SIZE_MAX / 1024) {
            sopts.stack_size = val * 1024;
        }
    } else if (is("StackRedPages")) {
        sopts.red_pages = val;
    } else if (is("StackYellowPages")) {
        sopts.yellow_pages = val;
    } else if (is("StackReservedPages")) {
        sopts.reserved_pages = val;
    }
}

//! Returns the size of a stack zone in bytes: its size in units of 4 KB, rounded up to the page size, as in HotSpot
size_t get_stack_zone_size(size_t pages, size_t page_size) {
    size_t size = pages * 4096;
    return (size + page_size - 1) / page_size * page_size;
}

//! Sets the stack limit of the primary thread to the end of its stack as the JVM sees it
/** The JVM protects zones at the end of the stack of every thread attached to it, including the primary thread, and
    on Linux it limits the primary thread's stack to the smaller of \c -Xss and its stack resource limit less two
    pages.  Qore's stack guard must lie above those zones, or a thread can overflow into them between two stack
    checks instead of raising \c STACK-LIMIT-EXCEEDED.

    The options are taken from \c JAVA_TOOL_OPTIONS, which the JVM reads before the options passed to
    \c JNI_CreateJavaVM(), and then from \a vm_args, so the last occurrence of an option is used, as in the JVM.
*/
void enforce_jvm_stack_on_primary_thread(const JavaVMInitArgs& vm_args) {
    JvmStackOptions sopts;
    {
        QoreString val;
        if (!SystemEnvironment::get("JAVA_TOOL_OPTIONS", val)) {
            // options are separated by white space
            const char* p = val.c_str();
            while (*p) {
                while (*p && isspace(static_cast<unsigned char>(*p))) {
                    ++p;
                }
                const char* start = p;
                while (*p && !isspace(static_cast<unsigned char>(*p))) {
                    ++p;
                }
                if (p > start) {
                    std::string opt(start, p - start);
                    apply_jvm_stack_option(opt.c_str(), sopts);
                }
            }
        }
    }
    for (jint i = 0; i < vm_args.nOptions; ++i) {
        apply_jvm_stack_option(vm_args.options[i].optionString, sopts);
    }

    long page_size_val = sysconf(_SC_PAGESIZE);
    size_t page_size = page_size_val > 0 ? static_cast<size_t>(page_size_val) : 4096;
    size_t protected_size = get_stack_zone_size(sopts.red_pages, page_size)
        + get_stack_zone_size(sopts.yellow_pages, page_size)
        + get_stack_zone_size(sopts.reserved_pages, page_size);

    size_t max_size = sopts.stack_size;
#ifdef __linux__
    // HotSpot limits the stack of the primary thread to its stack resource limit less two pages (see
    // os::Linux::capture_initial_stack())
    rlimit rl;
    if (!getrlimit(RLIMIT_STACK, &rl) && rl.rlim_cur != RLIM_INFINITY && rl.rlim_cur >= 3 * page_size) {
        size_t rlimit_size = static_cast<size_t>(rl.rlim_cur) - 2 * page_size;
        if (!max_size || rlimit_size < max_size) {
            max_size = rlimit_size;
        }
    }
#endif
    printd(LogLevel, "jni module: primary thread stack max: %lu protected: %lu\n", max_size, protected_size);
    q_enforce_stack_size_on_primary_thread(max_size, protected_size);
}
}
#endif

JavaVM* Jvm::vm = nullptr;
thread_local JNIEnv* Jvm::env;
bool Jvm::native_access_option_enabled = false;
bool Jvm::forked_child = false;
QoreForkHandlerRegistration* Jvm::fork_reg = nullptr;

void Jvm::forkChild(void*) {
    // runs in the child of fork() before anything else runs in it; takes no lock and allocates nothing (see
    // QoreForkHandler.h)
    if (vm) {
        forked_child = true;
        generated_bindings_fork_child();
    }
}

int Jvm::registerForkHandler() {
    assert(!fork_reg);
    fork_reg = qore_register_fork_handler(QFR_MODULE_LEAF, "jni_vm", nullptr, nullptr, forkChild, nullptr);
    return fork_reg ? 0 : -1;
}

void Jvm::unregisterForkHandler() {
    qore_unregister_fork_handler(fork_reg);
    fork_reg = nullptr;
}

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
            // options are separated by spaces; empty parts (an empty value, or repeated, leading or trailing
            // spaces) are skipped, as the JVM does not start with an empty option
            size_t pos = 0;
            while (true) {
                ssize_t i = val.find(' ', pos);
                size_t end = (i == -1) ? val.strlen() : static_cast<size_t>(i);
                if (end > pos) {
                    strvec.emplace_back(val.c_str() + pos, end - pos);
                }
                if (i == -1) {
                    break;
                }
                pos = end + 1;
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
#ifdef _QORE_HAS_ENFORCE_STACK_SIZE_ON_PRIMARY_THREAD
    enforce_jvm_stack_on_primary_thread(vm_args);
#elif defined(_QORE_HAS_ENFORCE_THREAD_SIZE_ON_PRIMARY_THREAD)
    q_enforce_thread_size_on_primary_thread();
#endif
    return 0;
}

void Jvm::destroyVM() {
    // Idempotent teardown.  destroyVM() can be reached by two paths that may both
    // run during a single process exit:
    //   1. jni_module_delete() — the normal Qore module-manager teardown; and
    //   2. the atexit handler registered by jni_module_init() — which guarantees
    //      the JVM is quiesced even when the module's del function is never run
    //      (e.g. the `oload --compile-java` worker exits via the Qore exit()
    //      builtin, which runs C atexit handlers + static destructors but not the
    //      Qore module-manager teardown).
    // Only the first call does the work; subsequent calls are no-ops.
    if (!vm) {
        return;
    }

    if (forked_child) {
        // A child process created with fork(): the threads of the JVM, including the NativeCleanup thread, exist
        // only in the parent.  Joining the NativeCleanup thread or calling DestroyJavaVM() would wait forever for
        // threads that do not exist here, and no JNI call can be made, so the JVM is only forgotten; its memory is
        // released with the process.
        vm = nullptr;
        env = nullptr;
        return;
    }

    // Drain and join the NativeCleanup C++ background thread before the JVM
    // teardown begins.  Must happen before Globals::cleanup() so the JNI ID
    // caches the thread relies on are still valid.
    Globals::stopNativeCleanupThread();

    Globals::cleanup();
    // Only destroy the JVM if we created it.  In JVM-primary mode the host process owns the VM
    // and destroys it itself; this path is reached from the QoreURLClassLoader shutdown hook
    // (see qore_url_classloader_shutdown_context()), i.e. from inside the host's own
    // DestroyJavaVM() call, so calling DestroyJavaVM() here would deadlock.
    if (!Globals::getAlreadyInitialized()) {
        vm->DestroyJavaVM();
    }
    vm = nullptr;
    env = nullptr;
}

JNIEnv *Jvm::attachAndGetEnv() {
    if (forked_child) {
        throw ForkedChildException();
    }
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
    if (forked_child) {
        throw ForkedChildException();
    }
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
        // in a child process created with fork(), the thread was attached in the parent, and the JVM cannot be
        // called
        if (!forked_child) {
            printd(LogLevel, "JNI - detaching thread, env: %p\n", env);
            vm->DetachCurrentThread();
        }
        env = nullptr;
    }
}

} // namespace jni
