/* -*- indent-tabs-mode: nil -*- */
/*
    jni-module.cpp

    JNI integration to Qore

    Qore Programming Language

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

    Note that the Qore library is released under a choice of three open-source
    licenses: MIT (as above), LGPL 2+, or GPL 2+; see README-LICENSE for more
    information.
*/

#include <qore/Qore.h>

#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <optional>
#include <string>
#include <vector>

#include <dlfcn.h>

#include "jni-module.h"

#include <qore/QoreSandboxManager.h>

#include "defs.h"
#include "Jvm.h"
#include "QoreJniClassMap.h"
#include "GeneratedBinding.h"
#include "Method.h"
#include "QoreToJava.h"
#include "Globals.h"
#include "QoreJdbcDriver.h"

using namespace jni;

#ifndef Q_WINDOWS
#include <signal.h>
#include <pthread.h>
#endif

//! the signals that the module reserves for the JVM, which can also be reserved by the library itself
static const sig_vec_t jvm_sys_signals = {
#ifndef Q_WINDOWS
    SIGTRAP, SIGSEGV, SIGBUS, SIGCHLD, SIGILL, SIGFPE
#endif
};

//! the signals that the module has reserved for the JVM
/** jvm_sys_signals and the JVM's suspend/resume signal (see jvm_sr_signal()); set when the module is initialized
*/
sig_vec_t sig_vec;

#ifndef Q_WINDOWS
//! Returns the signal that HotSpot uses to suspend and resume threads (qore#6707)
/** HotSpot uses \c SIGUSR2, or the signal in the \c _JAVA_SR_SIGNUM environment variable if it is a decimal number
    greater than \c SIGSEGV and \c SIGBUS and less than \c NSIG (otherwise it warns and uses \c SIGUSR2).  It handles
    the signal in its own threads, where it unblocks it, so the signal is delivered to them and a %Qore handler for it
    never runs; the signal is therefore reserved for the JVM.
*/
static int jvm_sr_signal() {
    QoreString val;
    if (SystemEnvironment::get("_JAVA_SR_SIGNUM", val) || val.empty()) {
        return SIGUSR2;
    }
    errno = 0;
    char* end = nullptr;
    long sig = strtol(val.c_str(), &end, 10);
    if (errno || *end || sig <= std::max(SIGSEGV, SIGBUS) || sig >= NSIG) {
        return SIGUSR2;
    }
    return static_cast<int>(sig);
}

//! the JVM's suspend/resume signal if the module reserved it alone (see jni_module_init()), otherwise 0
static int jvm_sr_reserved = 0;
//! true if the suspend/resume signal was blocked in the thread that reserved it
/** libqore unblocks a signal that it reserves for a module in the reserving thread and does not block it again when
    the module releases it; unlike the other signals that the module reserves, which no %Qore thread blocks, the
    suspend/resume signal (ex: \c SIGUSR2) is blocked in %Qore threads, and with its default action it would end the
    process if it were delivered to that thread after a failed initialization released it
*/
static bool jvm_sr_was_blocked = false;
//! the thread that reserved the suspend/resume signal alone
static pthread_t jvm_sr_thread;

//! after the suspend/resume signal has been released: blocks it again in the calling thread if it is the thread that
//! reserved it alone and it was blocked there before
/** jni_module_init_finalize() can also fail in a Java thread in bootstrap mode (when a Java process loads %Qore),
    after the module's initialization has succeeded; the mask of the reserving thread cannot be restored there
*/
static void jni_reblock_sr_signal() {
    if (!jvm_sr_reserved) {
        return;
    }
    if (jvm_sr_was_blocked && pthread_equal(jvm_sr_thread, pthread_self())) {
        sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, jvm_sr_reserved);
        pthread_sigmask(SIG_BLOCK, &set, nullptr);
    }
    jvm_sr_reserved = 0;
}
#endif

#ifndef Q_WINDOWS
//! reserves the suspend/resume signal alone for the module, without taking it over from the library or another module
/** Records whether the signal was blocked in the calling thread, as libqore unblocks it there (see
    jvm_sr_was_blocked).

    @return nullptr if reserved, or the error, which the caller owns
*/
static QoreStringNode* jni_reserve_sr_signal(int sig) {
    sigset_t cur;
    bool was_blocked = !pthread_sigmask(SIG_BLOCK, nullptr, &cur) && sigismember(&cur, sig) == 1;
    QoreStringNode* err = qore_reassign_signal(sig, QORE_JNI_MODULE_NAME, false);
    if (err) {
        return err;
    }
    jvm_sr_reserved = sig;
    jvm_sr_was_blocked = was_blocked;
    jvm_sr_thread = pthread_self();
    return nullptr;
}
#endif

#if !defined(Q_WINDOWS) && defined(SIGRTMIN) && defined(SIGRTMAX)
//! true if the module can choose a real-time signal for the JVM's suspend/resume signal on this platform
#define QORE_JNI_CHOOSE_SR_SIGNAL 1

//! Chooses and reserves the real-time signal that the JVM is told to use to suspend and resume its threads
/** Without \c _JAVA_SR_SIGNUM, HotSpot uses \c SIGUSR2, which %Qore programs use (ex: Qorus for its shutdown); the
    module therefore gives the JVM a free real-time signal in \c _JAVA_SR_SIGNUM (qore#6707).

    The candidates are the real-time signals from \c SIGRTMAX() down to \c SIGRTMIN() + 2, determined at run time
    (the C library reserves real-time signals below \c SIGRTMIN() for itself), except:
    - \c SIGRTMIN() and \c SIGRTMIN() + 1: libqore's internal signal is \c SIGRTMIN() + 1 on Linux, and the lowest
      real-time signals are the ones that other libraries take first
    - \c SIGRTMAX() - 2: the JDK's NIO library interrupts threads blocked in I/O with it on Linux
    - a signal that HotSpot does not accept in \c _JAVA_SR_SIGNUM (a number greater than \c SIGSEGV and \c SIGBUS and
      less than \c NSIG)

    The first candidate whose disposition is \c SIG_DFL (no handler installed in the process and not ignored) and that
    the library can reserve for the module (no %Qore handler is set for it, and neither the library nor another module
    manages it) is reserved and returned.  This is normally \c SIGRTMAX(); under valgrind, which reserves
    \c SIGRTMAX() for itself and reports it as ignored, it is \c SIGRTMAX() - 1.

    @return the signal reserved, 0 if no candidate is free, or -1 if the platform has no candidate that HotSpot would
    accept (ex: FreeBSD, where \c NSIG is 32 and the real-time signals start above it)
*/
static int jni_choose_sr_signal() {
    int rtmin = SIGRTMIN;
    int rtmax = SIGRTMAX;
    if (rtmin + 2 >= NSIG) {
        return -1;
    }
    for (int sig = rtmax; sig >= rtmin + 2; --sig) {
        if (sig == rtmax - 2 || sig <= std::max(SIGSEGV, SIGBUS) || sig >= NSIG) {
            continue;
        }
        struct sigaction sa;
        if (sigaction(sig, nullptr, &sa) || (sa.sa_flags & SA_SIGINFO) || sa.sa_handler != SIG_DFL) {
            continue;
        }
        QoreStringNode* err = jni_reserve_sr_signal(sig);
        if (err) {
            err->deref();
            continue;
        }
        return sig;
    }
    return 0;
}

//! Sets \c _JAVA_SR_SIGNUM for the JVM that the module creates, and unsets it when destroyed
/** The variable is set through libqore's environment API, under its environment lock, and the JVM is created while
    the lock is held shared, so that no %Qore code changes the environment meanwhile; it is unset after the JVM is
    created, so that it is not inherited by the child processes that the process starts afterwards.  A child process
    started by another thread between the setting and the unsetting can inherit it.
*/
class JniSrSignalEnv {
public:
    //! sets the variable if \a sig is not 0
    /** @return 0 if OK or nothing to set, -1 for error (with \c errno set)
    */
    int set(int sig) {
        if (!sig) {
            return 0;
        }
        char val[16];
        snprintf(val, sizeof val, "%d", sig);
        // not overwritten: another thread can have set the variable since the module checked that it is not set
        if (SystemEnvironment::set("_JAVA_SR_SIGNUM", val, false)) {
            return -1;
        }
        lock.emplace();
        // the value must be the module's: another thread can have set the variable before, or changed it after
        QoreString cur;
        if (SystemEnvironment::get("_JAVA_SR_SIGNUM", cur) || cur != val) {
            // not the module's value, which is left as it is
            errno = EAGAIN;
            return -1;
        }
        is_set = true;
        return 0;
    }

    ~JniSrSignalEnv() {
        // the shared lock must be released before the variable can be unset
        lock.reset();
        if (is_set && SystemEnvironment::unset("_JAVA_SR_SIGNUM")) {
            printd(0, "jni module: cannot unset _JAVA_SR_SIGNUM: %s\n", strerror(errno));
        }
    }

private:
    //! true if the variable was set
    bool is_set = false;
    //! held shared while the JVM is created
    std::optional<QoreEnvironmentReadLock> lock;
};
#endif

//! releases the signals that the module reserved for the JVM when its initialization fails
static void jni_release_signals() {
    // all of them or none: the suspend/resume signal is in sig_vec if the module reserved it
    qore_release_signals(sig_vec, QORE_JNI_MODULE_NAME);
#ifndef Q_WINDOWS
    jni_reblock_sr_signal();
#endif
}

static void jni_module_init(QoreModuleInitContext& ctx, ExceptionSink& xsink);
#if defined(QORE_JNI_HAS_COMMAND_CONTEXT) && !defined(NDEBUG)
static bool jni_command_rules_match_commands();
#endif
static void jni_module_ns_init(QoreNamespace* rns, QoreNamespace* qns, ExceptionSink& xsink);
static void jni_module_delete() noexcept;
static void jni_module_parse_cmd(const QoreString& cmd, ExceptionSink* xsink);
static bool jni_exported_import_allowed(const QoreProgram* pgm, std::string& refused);
static QoreNamespace* jni_find_create_java_namespace_intern(QoreString& arg, QoreProgram* pgm);

void jni_qore_module_desc(QoreModuleInfo& mod_info) {
#ifdef QORE_JNI_HAS_COMMAND_CONTEXT
    // jni_module_parse_cmd() checks the issuer of every command (jni_check_command_issuer()); the field exists only in
    // the struct of a library that implements module API 2.3, and the API version is validated only after this
    // function returns, so it is set only when the loading library supports 2.3
    if (is_module_api_supported(2, 3)) {
        mod_info.cmd_caller_aware = true;
    }
#endif
    mod_info.name = QORE_JNI_MODULE_NAME;
    mod_info.version = PACKAGE_VERSION;
    mod_info.desc = "JNI module";
    mod_info.author = "Qore Technologies, s.r.o.";
    mod_info.url = "http://qore.org";
    mod_info.api_major = QORE_MODULE_API_MAJOR;
    mod_info.api_minor = QORE_MODULE_API_MINOR;
    mod_info.init = jni_module_init;
    mod_info.ns_init = jni_module_ns_init;
    mod_info.del = jni_module_delete;
    mod_info.parse_cmd = jni_module_parse_cmd;
    mod_info.license = QL_MIT;
    mod_info.license_str = "MIT";
    // the features of the module that depend on the Qore library it was built with, in the "info" key of the module
    // hash (get_module_hash()); the Qore library takes over the hash
    QoreHashNode* info = nullptr;
#ifdef _QORE_HAS_SANDBOX_UNCONTROLLED_API_POLICY
    // "uncontrolled_api_policy": code counts as restricted only if a governing sandbox refuses uncontrolled APIs
    // (qore#5741, jni_sandbox_restricts_caller()); without it, any governing sandbox restricts the code
    info = new QoreHashNode(autoTypeInfo);
    info->setKeyValue("uncontrolled_api_policy", true, nullptr);
#endif
#if defined(_QORE_HAS_DATASOURCE_OPTION_RESTRICTIONS) && defined(QORE_JNI_HAS_COMMAND_CONTEXT)
    // "jdbc_option_setter": the jdbc driver judges its options by the code that set them (qore#5741), which it does
    // only when both are available (QoreJdbcConnection::setOption())
    if (!info) {
        info = new QoreHashNode(autoTypeInfo);
    }
    info->setKeyValue("jdbc_option_setter", true, nullptr);
#endif
#ifdef _QORE_HAS_DATASOURCE_CONNECT_CHOOSERS
    // "jdbc_connect_choosers": the jdbc driver judges the code that chose a connection, also the code that set its
    // database name (qore#6142, QoreJdbcConnection::checkChoosers())
    if (!info) {
        info = new QoreHashNode(autoTypeInfo);
    }
    info->setKeyValue("jdbc_connect_choosers", true, nullptr);
#endif
#ifdef _QORE_HAS_SANDBOX_RESTRICTS_API
    // "sandbox_restricts_api": uncontrolled code (the jdbc driver, check_class_option()) is refused unless the
    // governing sandbox trusts the code with uncontrolled APIs, as aggregated over compositions by
    // QoreSandboxManager::trustsUncontrolledApis() (jni_sandbox_refuses_uncontrolled_code())
    if (!info) {
        info = new QoreHashNode(autoTypeInfo);
    }
    info->setKeyValue("sandbox_restricts_api", true, nullptr);
#endif
    mod_info.info = info;
}

// global type compatibility option
DLLLOCAL bool jni_compat_types = false;

static bool jni_init_failed = false;

// module cmd type
using qore_jni_module_cmd_t = void (*) (const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_import(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_global_add_relative_classpath(const QoreString& arg, QoreProgram* pgm,
    JniExternalProgramData* jpc);
static void qore_jni_mc_global_add_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_add_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
// define-pending-class: for resolving circular dependencies with inner classes
static void qore_jni_mc_define_pending_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_define_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_set_compat_types(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_set_property(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_mark_module_injected(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_register_shared_class(const QoreString& arg, QoreProgram* pgm,
    JniExternalProgramData* jpc);
static void qore_jni_mc_kotlin_eval(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);

// module cmds
typedef std::map<std::string, qore_jni_module_cmd_t> mcmap_t;
static mcmap_t mcmap = {
    {"import", qore_jni_mc_import},
    {"add-classpath", qore_jni_mc_add_classpath},
    {"add-relative-classpath", qore_jni_mc_add_relative_classpath},
    {"define-pending-class", qore_jni_mc_define_pending_class},
    {"define-class", qore_jni_mc_define_class},
    {"global-add-classpath", qore_jni_mc_global_add_classpath},
    {"global-add-relative-classpath", qore_jni_mc_global_add_relative_classpath},
    {"set-compat-types", qore_jni_mc_set_compat_types},
    {"set-property", qore_jni_mc_set_property},
    {"mark-module-injected", qore_jni_mc_mark_module_injected},
    {"register-shared-class", qore_jni_mc_register_shared_class},
    {"kotlin-eval", qore_jni_mc_kotlin_eval},
};

static void jni_thread_cleanup(void*) {
    jni::Jvm::threadCleanup();
}

static bool bootstrap = false;
static bool already_initialized = false;
static bool deferred_ns_init = false;

// Ensures the JVM is quiesced before process exit even when the module's del
// function is never run.  The `oload --compile-java` / `--kotlinc` / validation
// workers spawned by qorus-core finish by calling the Qore exit() builtin, which
// (for a single-threaded Qore process) calls C exit() — running atexit handlers
// and static destructors but NOT the Qore module-manager teardown that would
// otherwise invoke jni_module_delete() -> Jvm::destroyVM().  If the JVM is left
// running while libqore's static destructors tear down global state, a JVM daemon
// thread (GC, JIT, Reference-Handler, NativeCleanup) can fault (SIGSEGV) and die
// fatally inside the JVM<->Qore chained signal handler, crashing the process.
//
// Registering this handler from jni_module_init() (after createVM() succeeds)
// guarantees it runs before those static destructors: C++ runs atexit handlers
// and static destructors in reverse order of registration/construction, and the
// module is initialized long after libqore's globals are constructed, so our
// handler runs first.  destroyVM() is idempotent, so this is a no-op when the
// normal del path already ran.  Only registered when we created the VM ourselves
// (not when a host process passed in an existing JVM via the jvm-ptr option).
static void jni_atexit_destroy_vm() noexcept {
    // when the JVM ends the process (see Jvm::isExiting()), destroyVM() leaves the JVM, which can be neither called
    // nor destroyed; the global Program is abandoned in any case, as its static destructor must not run
    // an atexit handler must never propagate an exception: a failure is reported, and the JVM is destroyed
    // nonetheless (qore#6788)
    // Prevent the global Program's static destructor from running after
    // function-local libqore synchronization objects have been torn down.
    // the Qore library can have been cleaned up already, so an error is not converted to a Qore exception to report
    // it
    jni::jni_report_set_plain();
    jni::jni_teardown_step("abandoning the global context", [] () { Globals::abandonGlobalContext(); });
    jni::Jvm::destroyVM();
}

QoreStringNode* jni_module_init_finalize(bool system) {
    tclist.push(jni_thread_cleanup, nullptr);

    try {
        QoreProgram* pgm = Globals::createJavaContextProgram();
        printd(5, "jni_module_init_finalize() pgm: %p\n", pgm);
        // issue #4006: ensure there is a program context for initialization
        QoreProgramContextHelper pgm_ctx(pgm);

        qjcm.init(pgm, already_initialized);
    } catch (jni::Exception& e) {
        tclist.pop(false);
        jni_release_signals();
        jni::Jvm::destroyVM();
        // display exception info on the console as an unhandled exception
        if (system) {
            throw;
            return nullptr;
        } else {
            ExceptionSink xsink;
            e.convert(&xsink);
            return new QoreStringNode("JNI-FINALIZATION-ERR");
        }
    }

    ExceptionSink xsink;
    // the option is set by a value that is True in a boolean context, as documented; False, NULL and NOTHING leave
    // it off
    ValueHolder v(qore_get_module_option("jni", "compat-types"), &xsink);
    if (v->getAsBool()) {
        jni_compat_types = true;
    }

    jni::jni_qore_init_done = true;

    printd(5, "jni_module_init_finalize() jni module init done\n");
    return nullptr;
}

//! Drops everything the jni module caches for a Program that is being torn down
/** Called by libqore for every Program before its namespace data is cleared - see
    qore_register_program_cleanup_callback().
*/
static void jni_program_cleanup(QoreProgram* pgm) noexcept {
    // libqore calls this from the teardown of the Program, where no C++ exception may propagate (qore#6788)
    jni::jni_teardown_step("purging the generated bindings of a Program", [pgm] () {
        jni::purge_generated_bindings(pgm);
    });
    jni::jni_teardown_step("purging the module root namespace cache of a Program", [pgm] () {
        jni::purge_module_root_ns_cache(pgm);
    });
}

//! Undoes what a failed jni_module_init() set up
/** libqore unloads a module whose initialization fails without calling its del function, so everything the
    initialization registered with libqore or created must be released here: a callback, a handler, or a JVM left
    behind would refer to the module's code after it is unmapped (ex: the program cleanup callback was called for
    every Program destroyed afterwards, which crashed the process), and memory the module allocated would be lost.
    Steps are undone in the reverse order of jni_module_init(), as in jni_module_delete().

    @param vm_created true if the initialization created the JVM, which is then destroyed, as its native methods and
    threads run the module's code; a JVM passed in with the \c jvm-ptr option is left to its owner
*/
static void jni_module_init_rollback(bool vm_created) noexcept {
    // called from a scope guard, also while an exception propagates: a step that fails is reported, and the rollback
    // continues (qore#6788)
    jni::jni_teardown_step("clearing the generated bindings", [] () { clear_generated_bindings(); });
    // the Program created for the Java context, if any, is destroyed while the program cleanup callback is still
    // registered, so its cached entries are purged
    jni::jni_teardown_step("clearing the global context", [] () { Globals::clearGlobalContext(); });
    jni::jni_teardown_step("destroying the class map", [] () {
        // the class map's namespaces exist from the module's load on, also if they were never initialized
        ExceptionSink xsink;
        qjcm.destroy(xsink);
        // a failure to release them cannot be reported, as the module initialization already failed
        xsink.clear();
    });
    if (vm_created) {
        // stops the NativeCleanup thread, releases the global references, and destroys the JVM; the atexit handler
        // registered for the JVM then has nothing to do
        jni::Jvm::destroyVM();
    }
}

static void jni_module_init(QoreModuleInitContext& ctx, ExceptionSink& xsink) {
    if (jni_init_failed) {
        xsink.raiseException("MODULE-INIT-ERROR", "jni module initialization failed");
        return;
    }
    printd(5, "jni_module_init()\n");
#ifdef QORE_JNI_HAS_COMMAND_CONTEXT
    // the issuer rules and the commands are kept in two tables: every command must have a rule and every rule a
    // command, or a command would fall back to the rule for unlisted commands
    assert(jni_command_rules_match_commands());
#endif

    jni::jni_qore_init = true;

    // get_module_root_ns() caches borrowed QoreNamespace pointers keyed by the owning Program;
    // those namespaces die with the Program and its address is then reused, so the entries must
    // go before the namespaces do or a later Program inherits pointers to freed memory
    qore_register_program_cleanup_callback(jni_program_cleanup);

    // everything set up below is undone if the initialization fails, also when an exception propagates, as the
    // module is then unloaded without its del function being called (see jni_module_init_rollback())
    bool init_done = false;
    bool signals_assigned = false;
    bool fork_handler_registered = false;
    bool vm_created = false;
    ON_BLOCK_EXIT([&]() {
        if (init_done) {
            return;
        }
        jni_init_failed = true;
        jni_module_init_rollback(vm_created);
        if (fork_handler_registered) {
            jni::Jvm::unregisterForkHandler();
        }
        if (signals_assigned) {
            // jni_module_init_finalize() releases them itself when it fails, in which case this has no effect
            jni_release_signals();
        }
#ifndef Q_WINDOWS
        else if (jvm_sr_reserved) {
            // the suspend/resume signal was reserved alone, but the reservation of the others did not complete
            qore_release_signal(jvm_sr_reserved, QORE_JNI_MODULE_NAME);
            jni_reblock_sr_signal();
        }
#endif
        qore_deregister_program_cleanup_callback(jni_program_cleanup);
    });

    qore_set_module_option("jni", "jni-version", JNI_VERSION_21);

    sig_vec = jvm_sys_signals;
#ifndef Q_WINDOWS
    // the JVM's suspend/resume signal if the module chose it and tells the JVM with _JAVA_SR_SIGNUM, otherwise 0
    int sr_chosen = 0;
    // the JVM's suspend/resume signal is reserved too (qore#6707); unlike the signals above, it cannot be taken over
    // from the library (ex: if _JAVA_SR_SIGNUM names the library's internal signal), so it is reserved alone first,
    // which fails if the library or another module manages it
    int sr_sig = 0;
    // allocated first, so that nothing can fail once the signal is reserved
    sig_vec.reserve(sig_vec.size() + 1);
    {
        ValueHolder host_jvm(qore_get_module_option("jni", "jvm-ptr"), nullptr);
#ifdef QORE_JNI_CHOOSE_SR_SIGNAL
        // the module chooses the signal for a JVM that it creates, unless the user has set _JAVA_SR_SIGNUM
        if (host_jvm->getType() != NT_INT && !SystemEnvironment::isSet("_JAVA_SR_SIGNUM")) {
            sr_sig = jni_choose_sr_signal();
            if (sr_sig > 0) {
                sr_chosen = sr_sig;
            } else if (!sr_sig) {
                xsink.raiseException("MODULE-INIT-ERROR", "the jni module cannot start the JVM: no real-time "
                    "signal from SIGRTMAX down to SIGRTMIN+2 (%d - %d) is free for the JVM to suspend and resume its "
                    "threads with (each has a handler installed in the process or a Qore handler, or is managed "
                    "by the Qore library or another module); set _JAVA_SR_SIGNUM to choose the signal", SIGRTMAX,
                    SIGRTMIN + 2);
                return;
            }
        }
#endif
        if (!sr_chosen) {
            // the signal that the user set in _JAVA_SR_SIGNUM, or that the host process's JVM uses, or SIGUSR2 (also
            // where the platform has no real-time signal that HotSpot accepts)
            sr_sig = jvm_sr_signal();
        }
    }
    bool sr_alone = std::find(sig_vec.begin(), sig_vec.end(), sr_sig) == sig_vec.end();
    if (sr_alone) {
        if (!sr_chosen) {
            QoreStringNode* err = jni_reserve_sr_signal(sr_sig);
            if (err) {
                xsink.raiseException("MODULE-INIT-ERROR", err);
                return;
            }
        }
        sig_vec.push_back(sr_sig);
    }
#endif
    QoreStringNode* err = qore_reassign_signals(sig_vec, QORE_JNI_MODULE_NAME, true);
    if (err) {
#ifndef Q_WINDOWS
        if (sr_alone) {
            qore_release_signal(sr_sig, QORE_JNI_MODULE_NAME);
            jni_reblock_sr_signal();
            sig_vec.pop_back();
        }
#endif
        xsink.raiseException("MODULE-INIT-ERROR", err);
        return;
    }
    signals_assigned = true;

    // marks a child process created with fork() after the JVM is started, which cannot use the JVM (see
    // jni::Jvm::isForkedChild()); registered before the JVM is started, so that no fork() is missed, and removed
    // when the module is deleted, or here if its initialization fails, as the module is then unloaded
    if (jni::Jvm::registerForkHandler()) {
        xsink.outOfMemory();
        return;
    }
    fork_handler_registered = true;

    ValueHolder jvm_ptr(qore_get_module_option("jni", "jvm-ptr"), nullptr);
    if (jvm_ptr->getType() == NT_INT) {
        jni::Jvm::setVmPtr(reinterpret_cast<JavaVM*>(jvm_ptr->getAsBigInt()));
        already_initialized = true;
        Globals::setAlreadyInitialized();
    } else {
        already_initialized = false;
        try {
#ifdef QORE_JNI_CHOOSE_SR_SIGNAL
            // _JAVA_SR_SIGNUM is set only while the JVM is created
            JniSrSignalEnv sr_env;
            if (sr_env.set(sr_chosen)) {
                err = errno == EAGAIN
                    ? new QoreStringNodeMaker("cannot set _JAVA_SR_SIGNUM to %d: another thread set or changed the "
                        "variable meanwhile", sr_chosen)
                    : new QoreStringNodeMaker("cannot set _JAVA_SR_SIGNUM to %d: %s", sr_chosen, strerror(errno));
            } else {
                err = jni::Jvm::createVM();
            }
#else
            err = jni::Jvm::createVM();
#endif
        } catch (jni::Exception& e) {
            ExceptionSink xsink;
            e.convert(&xsink);
            const QoreValue desc = xsink.getExceptionDesc();
            if (desc.getType() == NT_STRING) {
                QoreStringValueHelper desc_str(desc);
                err = new QoreStringNode(**desc_str);
            } else {
                err = new QoreStringNode("unknown exception calling Jvm::createVM()");
            }
        }
        if (err) {
            err->prepend("Could not create the Java Virtual Machine: ");
            xsink.raiseException("MODULE-INIT-ERROR", err);
            return;
        }
        vm_created = true;

        // We created the VM, so we own its teardown: register an atexit handler to
        // quiesce the JVM before C exit() runs static destructors, covering exit
        // paths that bypass jni_module_delete() (see jni_atexit_destroy_vm()).
        // A registration failure is non-fatal — the JVM is still fully usable; only
        // the bare-exit() teardown safety net is lost — so log it and continue.
        if (atexit(jni_atexit_destroy_vm)) {
            printd(0, "jni module: failed to register atexit JVM teardown handler; "
                "the JVM will not be quiesced on exit paths that bypass module teardown\n");
        }
    }

    try {
        bootstrap = Globals::init();
    } catch (QoreStandardException &e) {
        throw;
    } catch (JavaException& e) {
        xsink.raiseException("MODULE-INIT-ERROR", e.toString());
        return;
    } catch (Exception &e) {
        xsink.raiseException("MODULE-INIT-ERROR", "JVM initialization failed due to an unknown error");
        return;
    }

    // Spawn the NativeCleanup C++ background thread now that the bootstrap
    // classes (NativeCleanup, NativeCleanup$Ref, the wrapper classes) are
    // defined and the JNI ID caches the thread depends on can be populated.
    try {
        Globals::startNativeCleanupThread();
    } catch (JavaException& e) {
        xsink.raiseException("MODULE-INIT-ERROR", e.toString());
        return;
    } catch (Exception& e) {
        xsink.raiseException("MODULE-INIT-ERROR", "Failed to start native cleanup thread");
        return;
    }

    printd(5, "jni_module_init() initialized JVM\n");

    if (!bootstrap) {
        QoreStringNode* finalize_err = jni_module_init_finalize();
        if (finalize_err) {
            xsink.raiseException("MODULE-INIT-ERROR", finalize_err);
            return;
        }
    } else {
        jni::jni_qore_init_done = true;
    }

    // the jdbc DBI driver is registered last, as libqore has no API to deregister a driver: registered before a
    // step that fails, it would be left pointing to the code of the unloaded module
    jni::setup_jdbc_driver();
    init_done = true;
}

static void jni_module_ns_init(QoreNamespace* rns, QoreNamespace* qns, ExceptionSink& xsink) {
    QoreProgram* pgm = getProgram();
    // we can ignore the first program to be initialized when bootstrapping
    if (bootstrap && !deferred_ns_init) {
        deferred_ns_init = true;
        return;
    }
    assert(pgm->getRootNS() == rns);
    if (!pgm->getExternalData("jni")) {
        // the namespace as it is after the module's initialization, without the classes other Programs imported
        QoreNamespace* jnins = qjcm.copyProgramJniNs();
        rns->addNamespace(jnins);
        // a Java exception raised while setting up the Program's class loader and dynamic API must be converted
        // here, while it is still pending in the JVM; left to unwind into the module loader, it terminated the
        // process instead of failing the module load
        try {
            pgm->setExternalData("jni", new JniExternalProgramData(jnins, pgm));
        } catch (jni::Exception& e) {
            e.convert(&xsink);
        }
    }
}

// qore#6788: libqore calls this from its own teardown, where a C++ exception ends the process in std::terminate(), so
// a step that fails is reported (there is no exception sink) and the teardown continues with the next step
static void jni_module_delete() noexcept {
    // stop libqore calling into this module's code once it is unloaded
    qore_deregister_program_cleanup_callback(jni_program_cleanup);
    jni::jni_teardown_step("clearing the generated bindings", [] () { clear_generated_bindings(); });

    // clear all objects from stored classes before destroying the JVM (releases all global references)
    jni::jni_teardown_step("clearing the global context", [] () { Globals::clearGlobalContext(); });
    jni::jni_teardown_step("destroying the class map", [] () {
        ExceptionSink xsink;
        qjcm.destroy(xsink);
    });
    tclist.pop(false);
    jni::Jvm::destroyVM();
    jni::Jvm::unregisterForkHandler();
    // the signals reserved for the JVM stay reserved: the module is deleted as the library is torn down, or the JVM
    // belongs to the Java host (the jvm-ptr option) and keeps running, and its handlers stay installed in either case
}

static QoreNamespace* qore_jni_wildcard_import(QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    if (arg[-2] != '.' || arg.strlen() < 3) {
        // the argument of the module command is UTF-8; the text of the error is in the default encoding
        throw QoreJniException("JNI-IMPORT-ERROR", "invalid import argument: '%s'",
            jni_utf8_to_default(arg.c_str()).c_str());
        return nullptr;
    }

    arg.terminate(arg.strlen() - 2);

    arg.replaceAll(".", "::");
    return jni_find_create_java_namespace_intern(arg, pgm);
}

// exported function
extern "C" QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm) {
    // the namespace's class handler loads Java classes when they are used: refused as an import (qore#5699); this
    // function has no exception sink, so a refusal returns nullptr, which its caller reports
    std::string refused;
    if (!jni_exported_import_allowed(pgm, refused)) {
        printd(LogLevel, "jni_module_find_create_java_namespace() '%s': %s\n", arg.c_str(), refused.c_str());
        return nullptr;
    }
    return jni_find_create_java_namespace_intern(arg, pgm);
}

//! Creates the Java namespace for a wildcard import; the caller has checked that the import is allowed
static QoreNamespace* jni_find_create_java_namespace_intern(QoreString& arg, QoreProgram* pgm) {
    arg.concat("::x");

    // create jni namespace in root namespace if necessary
    QoreNamespace* jns = pgm->getRootNS()->findLocalNamespace("Jni");

    QoreNamespace* ns = jns->findCreateNamespacePath(arg.c_str());
    printd(LogLevel, "jni_module_find_create_java_namespace() nsp: '%s' ns: %p '%s'\n", arg.c_str(), ns,
        ns->getName());
    ns->setClassHandler(jni_class_handler);

    return ns;
}

//! Returns true if the code that imports Java classes through an exported function may do so (qore#5699)
/** The exported functions jni_module_import() and jni_module_find_create_java_namespace() are called by other modules
    (module-python's Java loader), not through jni_module_parse_cmd(), so they apply the rule of the \c import command
    themselves: code that a governing sandbox does not trust with uncontrolled APIs is refused, and so is code with
    \c PO_NO_UNCONTROLLED_APIS.  The import is refused if either of these refuses it:
    - the Program that the classes are imported into: if it has \c PO_NO_UNCONTROLLED_APIS, no code can give it Java
      classes, whoever calls the loader (trusted code calling Python code in a standalone Program that does not allow
      uncontrolled APIs, for example)
    - the code that imports them: with module API 2.3 it is judged as the issuer of a module command is, with
      QoreModuleCommandRestrictions::getCallerRestrictions() (as the \c classpath option of the \c jdbc driver is):
      within a module command, its issuer; otherwise the code that calls into the loader, where the code of a library
      module has no parse options, only the sandbox that governs it, so the parse options that its Program inherited
      from the code that loaded it do not refuse it.  Before module API 2.3, the sandbox that governs the calling code
      is used.

    Called on the thread of the exported function, which %Qore knows (the Python code runs in a %Qore call).

    @param pgm the Program the classes are imported into
    @param refused set to a description of the refusal when the import is not allowed
*/
static bool jni_exported_import_allowed(const QoreProgram* pgm, std::string& refused) {
    // the Program that the classes are imported into
    if (pgm && (pgm->getParseOptions64() & PO_NO_UNCONTROLLED_APIS)) {
        refused = "a Program that does not allow uncontrolled APIs cannot import Java classes";
        return false;
    }
#ifdef QORE_JNI_HAS_COMMAND_CONTEXT
    // the restrictions that a module command issued by the same code would have; inside a module command, those of
    // its issuer
    QoreModuleCommandRestrictions caller = QoreModuleCommandRestrictions::getCallerRestrictions();
    if (caller.isSandboxed()) {
        refused = "sandboxed code cannot import Java classes";
        return false;
    }
    if (caller.hasAny(QoreParseOptions(PO_NO_UNCONTROLLED_APIS))) {
        refused = "code that does not allow uncontrolled APIs cannot import Java classes";
        return false;
    }
    return true;
#else
    // trust only, as for the jdbc driver and check_class_option(): importing a Java class is a direct use of an
    // uncontrolled API, which the core refuses on trust alone (qore#5741), so a sandbox that restricts nothing but does
    // not trust the code still refuses it
    if (jni_sandbox_restricts_caller(QoreSandboxManagerHelper::All)) {
        refused = "sandboxed code cannot import Java classes";
        return false;
    }
    return true;
#endif
}

// exported function
extern "C" int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import) {
    {
        std::string refused;
        if (!jni_exported_import_allowed(pgm, refused)) {
            xsink->raiseException("JNI-SANDBOX-ERROR", "cannot import Java class '%s': %s", import, refused.c_str());
            return -1;
        }
    }
    QoreString arg(import);
    try {
        // creating the Program's JNI context can raise a Java exception, which must be converted below
        JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
        //printd(5, "jni_module_import '%s' jpc: %p jnins: %p pgm: %p\n", import, jpc, jpc->getJniNamespace(), pgm);
        if (arg[-1] != '*') {
            Env env;
            //printd(5, "jni_module_import() non wc lcc arg: '%s' (pgm: %p)\n", arg.c_str(), pgm);
            // the following call adds the class to the current program as well
            qjcm.findCreateQoreClass(env, arg.c_str(), pgm);
        } else {
            QoreNamespace* ns = qore_jni_wildcard_import(arg, pgm, jpc);
            if (!ns) {
                assert(*xsink);
                return -1;
            }
        }
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return -1;
    }
    return 0;
}

#ifdef QORE_JNI_HAS_COMMAND_CONTEXT
//! The restrictions of the issuing code that refuse a module command (qore#5699)
struct JniCommandIssuerRule {
    //! the restricting parse options that refuse the command; ignored if \c all_restricted is set
    int64 mask;
    //! true if the command is refused to every restricted issuer: it changes state that every Program shares
    bool all_restricted;
    //! what the command does, for the error
    const char* what;
    //! true if the command is not checked: it changes only flags of its target Program
    bool exempt = false;
};

//! The issuer rules of every module command in mcmap; a command that is not listed is refused to restricted issuers
static const std::map<std::string, JniCommandIssuerRule> jni_command_issuer_rules = {
    // flags of the target Program
    {"set-compat-types", {0, false, "sets the type compatibility of the Program", true}},
    {"mark-module-injected", {0, false, "marks a module as injected in the Program", true}},
    // loads Java classes, running their static initializers; a wildcard import loads them when they are used
    {"import", {PO_NO_UNCONTROLLED_APIS, false, "imports Java classes"}},
    // loads jars or classes from the filesystem into the Program's class loader
    {"add-classpath", {PO_NO_UNCONTROLLED_APIS | PO_NO_FILESYSTEM, false, "adds to the Program's classpath"}},
    {"add-relative-classpath", {PO_NO_UNCONTROLLED_APIS | PO_NO_FILESYSTEM, false,
        "adds to the Program's classpath"}},
    // the parent class loader that every Program shares
    {"global-add-classpath", {0, true, "changes the classpath of every Program"}},
    {"global-add-relative-classpath", {0, true, "changes the classpath of every Program"}},
    // defines and runs code
    {"define-class", {PO_NO_UNCONTROLLED_APIS | PO_NO_EMBEDDED_LOGIC, false, "defines a Java class"}},
    {"define-pending-class", {PO_NO_UNCONTROLLED_APIS | PO_NO_EMBEDDED_LOGIC, false, "defines a Java class"}},
    {"kotlin-eval", {PO_NO_UNCONTROLLED_APIS | PO_NO_EMBEDDED_LOGIC, false, "runs Kotlin code"}},
    // process-wide state
    {"set-property", {0, true, "sets a system property of the JVM"}},
    {"register-shared-class", {0, true, "makes the class of this Program the class of every Program"}},
};

#ifndef NDEBUG
//! Returns true if every module command in mcmap has an issuer rule and every issuer rule a command
static bool jni_command_rules_match_commands() {
    if (mcmap.size() != jni_command_issuer_rules.size()) {
        return false;
    }
    for (const auto& i : mcmap) {
        if (jni_command_issuer_rules.find(i.first) == jni_command_issuer_rules.end()) {
            return false;
        }
    }
    return true;
}
#endif

//! Refuses a module command to an issuer that may not do what it does (qore#5699)
/** The command is judged by the restrictions of the code that issued it (QoreModuleCommandContext), which Qore
    captures before it switches to the target Program: a sandboxed issuer is refused every command that is listed in
    jni_command_issuer_rules, and an issuer with a restricting parse option is refused the commands that the option
    forbids (every restricted issuer for the commands that change state that every Program shares).  Code of a library
    module has no parse options, so only a sandbox restricts its commands.

    @param name the command
    @param xsink raises \c JNI-SANDBOX-ERROR when the command is refused

    @return 0 if the command is allowed, -1 if an exception was raised
*/
static int jni_check_command_issuer(const char* name, ExceptionSink* xsink) {
    auto i = jni_command_issuer_rules.find(name);
    if (i != jni_command_issuer_rules.end() && i->second.exempt) {
        return 0;
    }
    const QoreModuleCommandContext* ctx = QoreModuleCommandContext::get();
    if (!ctx) {
        // the core gives every command a context: none means the issuer is unknown
        xsink->raiseException("JNI-SANDBOX-ERROR", "the issuer of the \"%s\" module command is unknown", name);
        return -1;
    }
    if (i == jni_command_issuer_rules.end()) {
        // a command with no rule is refused to every restricted issuer (fail closed)
        QoreStringMaker what("use the \"%s\" module command", name);
        return ctx->check(QoreModuleCommandRestrictions::getRestrictingOptions(), true, "JNI-SANDBOX-ERROR",
            what.c_str(), xsink);
    }
    QoreStringMaker what("use the \"%s\" module command, which %s", name, i->second.what);
    const QoreParseOptions mask = i->second.all_restricted
        ? QoreModuleCommandRestrictions::getRestrictingOptions()
        : QoreParseOptions(i->second.mask);
    return ctx->check(mask, true, "JNI-SANDBOX-ERROR", what.c_str(), xsink);
}

//! Returns true if the target-side parse-option checks of a command apply (qore#5699)
/** When the command's issuer is its target Program, the issuer's restrictions (checked by jni_check_command_issuer())
    are that Program's, except for the directives of a library module, which have none (only a sandbox restricts
    them): the target's own parse options, which a module's Program inherits from the code that loads it, must not
    refuse them.  For a command issued into another Program, the target's options still apply.
*/
static bool jni_check_target_options() {
    const QoreModuleCommandContext* ctx = QoreModuleCommandContext::get();
    return !ctx || ctx->getIssuerProgram() != ctx->getTargetProgram();
}
#else
//! Before module API 2.3, the target-side checks are the only checks
static bool jni_check_target_options() {
    return true;
}
#endif

static void jni_module_parse_cmd(const QoreString& cmd_arg, ExceptionSink* xsink) {
    // the command can be in any encoding: it is processed in UTF-8, in which its arguments are passed to Java (ex:
    // a class name, a system property, Kotlin code)
    TempEncodingHelper cmd_utf8(cmd_arg, QCS_UTF8, xsink);
    if (*xsink) {
        return;
    }
    const QoreString& cmd = **cmd_utf8;
    printd(LogLevel, "jni_module_parse_cmd() cmd: '%s'\n", cmd.c_str());

    const char* p = strchr(cmd.c_str(), ' ');

    if (!p) {
        // the description shows the command as it was given, in its encoding, as Qore appends it to the description
        SimpleRefHolder<QoreStringNode> desc(new QoreStringNode(cmd_arg.getEncoding()));
        desc->sprintf("missing command name in parse command: '%s'", cmd_arg.c_str());
        xsink->raiseException("JNI-PARSE-COMMAND-ERROR", desc.release());
        return;
    }

    QoreString str(&cmd, p - cmd.c_str());

    QoreString arg(cmd);

    arg.replace(0, p - cmd.c_str() + 1, (const char*)0);
    arg.trim();

    mcmap_t::const_iterator i = mcmap.find(str.c_str());
    if (i == mcmap.end()) {
        // the description shows the command as it was given, in its encoding, as Qore appends it to the description
        const char* cmd_end = strchr(cmd_arg.c_str(), ' ');
        QoreString name(&cmd_arg, cmd_end ? cmd_end - cmd_arg.c_str() : cmd_arg.size());
        SimpleRefHolder<QoreStringNode> desc(new QoreStringNode(cmd_arg.getEncoding()));
        desc->sprintf("unrecognized command '%s' in '%s' (valid commands: ", name.c_str(), cmd_arg.c_str());
        for (mcmap_t::const_iterator i = mcmap.begin(), e = mcmap.end(); i != e; ++i) {
            if (i != mcmap.begin()) {
                desc->concat(", ");
            }
            desc->sprintf("'%s'", i->first.c_str());
        }
        desc->concat(')');
        xsink->raiseException("JNI-PARSE-COMMAND-ERROR", desc.release());
        return;
    }

#ifdef QORE_JNI_HAS_COMMAND_CONTEXT
    // the issuer first (qore#5699); the commands also check the target Program and its sandbox
    if (jni_check_command_issuer(str.c_str(), xsink)) {
        return;
    }
#endif

    // we must use "getProgram()" here for the parse context QoreProgram
    QoreProgram* pgm = getProgram();
    try {
        // creating the Program's JNI context can raise a Java exception, which must be converted below
        JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
        i->second(arg, pgm, jpc);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

static void qore_jni_mc_import(const QoreString& cmd_arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    QoreString arg(cmd_arg);
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));

    // process import statement
    printd(LogLevel, "qore_jni_mc_import() pgm: %p arg: %s c: %c\n", pgm, arg.c_str(), arg[-1]);

    // see if there is a wildcard at the end
    if (arg[-1] == '*') {
        // the package is a NUL-terminated namespace path from here on, so a name with a NUL character is refused
        // (qore#6738)
        jni_check_name(arg.c_str(), arg.size(), "package name", "package names");
        qore_jni_wildcard_import(arg, pgm, jpc);
    } else {
        // the name is a NUL-terminated string from here on, so a name with a NUL character is refused (qore#6738)
        jni_check_name(arg.c_str(), arg.size(), "class name", "class names");
        printd(LogLevel, "jni_module_parse_cmd() non wc lcc arg: '%s' (pgm: %p)\n", arg.c_str(), pgm);
        Env env;
        env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);
        // the following call adds the class to the current program as well
        qjcm.findCreateQoreClass(env, arg.c_str(), pgm, jpc);
    }
}

//! Refuses a module command that runs Java code, or changes state of the JVM, to sandboxed and restricted code
/** These commands do what an uncontrolled API does: they change state that every Program shares (the parent
    classpath, JVM system properties, the owners of shared dynamic classes), or they run Java code that the module
    command names or defines.  So they are refused:
    - in a Program that does not allow uncontrolled APIs (\c PO_NO_UNCONTROLLED_APIS), where the module's functions
      and Java classes are not available either
    - to any governing sandbox, also through a policy barrier or a system-policy capability: a sandboxed Program must
      not change what trusted Programs load or how the JVM connects for them

    @param cmd the module command, for the error
    @param pgm the Program that the command is issued in
    @param what what the command does, for the error

    @throw JNI-SANDBOX-ERROR the command is issued in a restricted Program or under a sandbox
*/
static void jni_check_uncontrolled_cmd(const char* cmd, const QoreProgram* pgm, const char* what) {
    if (pgm && jni_check_target_options() && (pgm->getParseOptions64() & PO_NO_UNCONTROLLED_APIS)) {
        throw QoreJniException("JNI-SANDBOX-ERROR", "the \"%s\" module command %s, so it cannot be used in a Program "
            "that does not allow uncontrolled APIs", cmd, what);
    }
    // trust only, as for the jdbc driver and check_class_option(): the module command is a direct use of an
    // uncontrolled API, which the core refuses on trust alone (qore#5741), so a sandbox that restricts nothing but does
    // not trust the code still refuses it
    if (jni_sandbox_restricts_caller(QoreSandboxManagerHelper::All)) {
        throw QoreJniException("JNI-SANDBOX-ERROR", "sandboxed code cannot use the \"%s\" module command, which %s",
            cmd, what);
    }
}

//! Refuses a class definition module command in a Program that does not allow uncontrolled APIs
/** Defining a class from byte code is running Java code: its static initializer runs when the class is first used,
    as when its constants are imported, so it is an uncontrolled API.

    @param cmd the module command, for the error
    @param pgm the Program that the command is issued in

    @throw JNI-SANDBOX-ERROR the Program does not allow uncontrolled APIs
*/
static void jni_check_define_class_restricted(const char* cmd, const QoreProgram* pgm) {
    if (pgm && jni_check_target_options() && (pgm->getParseOptions64() & PO_NO_UNCONTROLLED_APIS)) {
        throw QoreJniException("JNI-SANDBOX-ERROR", "the \"%s\" module command defines Java code, so it cannot be used "
            "in a Program that does not allow uncontrolled APIs", cmd);
    }
}

//! Refuses a classpath module command to sandboxed code where its policy does not allow it
/** A \c global-* command changes the classpath that every Program shares, so a sandboxed Program could make trusted
    Programs load its classes: it is refused as jni_check_uncontrolled_cmd() describes.  A Program's own classpath is
    checked entry by entry against the filesystem policy, so that sandboxed code reads no class or resource from a
    path its policy denies.

    @param cmd the module command, for the error
    @param pgm the Program that the command is issued in
    @param classpath the classpath: entries separated by the system path separator or \c ';', as
    QoreURLClassLoader.addPath() splits them
    @param global if the command changes the classpath that every Program shares

    @throw JNI-SANDBOX-ERROR sandboxed or restricted code uses a \c global-* classpath command
    @throw FILESYSTEM-ACCESS-DENIED the sandbox policy denies reading a classpath entry

    @see jni_check_classpath_policy()
*/
void jni_check_classpath_sandbox(const char* cmd, const QoreProgram* pgm, Env& env, jstring classpath,
        bool global) {
    if (global) {
        jni_check_uncontrolled_cmd(cmd, pgm, "changes the classpath of every Program");
        return;
    }
    // the filesystem policy, as for Qore's own file operations
    QoreSandboxManagerHelper smh(QoreSandboxManagerHelper::Policy);
    jni_check_classpath_policy(smh.get(), env, classpath);
}

void jni_check_classpath_policy(QoreSandboxManager* policy, Env& env, jstring classpath) {
    if (!policy) {
        return;
    }
    // each entry as the bytes that the JVM uses for it, so that the policy judges the representation that is used:
    // the entries are those that QoreURLClassLoader.addPath() makes of the same Java string, encoded in the JVM's
    // file name encoding
    jvalue jarg;
    jarg.l = classpath;
    LocalReference<jobjectArray> entries = env.callStaticObjectMethod(Globals::classQoreSandbox,
        Globals::methodQoreSandboxEncodeClasspath, &jarg).as<jobjectArray>();
    jsize count = env.getArrayLength(entries);
    for (jsize i = 0; i < count; ++i) {
        LocalReference<jbyteArray> bytes = env.getObjectArrayElement(entries, i).as<jbyteArray>();
        jsize len = env.getArrayLength(bytes);
        // the bytes have no NUL: an entry with one is refused when it is encoded
        std::string entry(static_cast<size_t>(len), '\0');
        if (len) {
            env.getByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte*>(&entry[0]));
        }
        ExceptionSink xsink;
        if (!policy->checkFilesystemAccess(entry.c_str(), QSEC_READ, &xsink)) {
            throw XsinkException(xsink);
        }
    }
}

//! Returns a module command's argument in the default encoding
/** The command is processed in UTF-8 (see jni_module_parse_cmd()); a classpath is made in the default encoding, the
    encoding of Qore's file names, of the Program's script directory and of the environment variables that
    q_env_subst() substitutes, before it is converted once to the Java string that is both checked and used; a
    module name is compared with Qore's module names, which are in the default encoding too

    @throw XsinkException the argument cannot be converted to the default encoding
*/
static QoreString jni_cmd_default_arg(const QoreString& arg) {
    ExceptionSink xsink;
    TempEncodingHelper str(arg, QCS_DEFAULT, &xsink);
    if (xsink) {
        throw XsinkException(xsink);
    }
    return QoreString(*str);
}

static void qore_jni_mc_global_add_classpath(const QoreString& cmd_arg, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    QoreString arg = jni_cmd_default_arg(cmd_arg);
    // a classpath with a NUL character is refused before it is used in any way (qore#6771)
    jni_check_classpath(arg, "JNI-ERROR");
    q_env_subst(arg);
    Env env;
    LocalReference<jstring> jcp = env.newPathString(arg);
    jni_check_classpath_sandbox("global-add-classpath", pgm, env, jcp, true);
    printd(LogLevel, "qore_jni_mc_global_add_classpath() jpc: %p arg: '%s'\n", jpc, arg.c_str());
    jpc->addParentClasspath(env, jcp);
}

static void qore_jni_mc_global_add_relative_classpath(const QoreString& arg, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    SimpleRefHolder<QoreStringNode> cwd_str;

    assert(pgm);
    cwd_str = pgm->getScriptDir();

    if (!cwd_str) {
        char* cwd = getcwd(nullptr, 0);
        if (!cwd) {
            throw QoreJniException("JNI-GLOBAL-ADD-RELATIVE-CLASSPATH-ERROR", "cannot determine relative path; there "
                "is no information in the Program context and cannot get current working directory: %s",
                strerror(errno));
        }
        ON_BLOCK_EXIT(free, cwd);
        cwd_str = new QoreStringNode(cwd);
    }

    // the path in the default encoding, as the script directory is; a path with a NUL character is refused before
    // it is used in any way (qore#6771)
    QoreString rel = jni_cmd_default_arg(arg);
    jni_check_classpath(rel, "JNI-ERROR");
    cwd_str->concat(QORE_DIR_SEP);
    cwd_str->concat(rel.c_str(), rel.size());
    q_normalize_path(**cwd_str);

    cwd_str->concat('/');

    printd(LogLevel, "qore_jni_mc_global_add_relative_classpath() arg: '%s' cwd: '%s'\n", arg.c_str(),
        cwd_str->c_str());
    Env env;
    LocalReference<jstring> jcp = env.newPathString(QoreString(cwd_str->c_str(), QCS_DEFAULT));
    jni_check_classpath_sandbox("global-add-relative-classpath", pgm, env, jcp, true);

    jpc->addParentClasspath(env, jcp);
}

static void qore_jni_mc_add_classpath(const QoreString& cmd_arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    QoreString arg = jni_cmd_default_arg(cmd_arg);
    // a classpath with a NUL character is refused before it is used in any way (qore#6771)
    jni_check_classpath(arg, "JNI-ERROR");
    q_env_subst(arg);
    printd(LogLevel, "qore_jni_mc_add_classpath() jpc: %p arg: '%s'\n", jpc, arg.c_str());
    // one Java string is both checked and added
    Env env;
    LocalReference<jstring> jcp = env.newPathString(arg);
    jni_check_classpath_sandbox("add-classpath", pgm, env, jcp, false);
    jpc->addClasspath(env, jcp);
}

static void qore_jni_mc_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    SimpleRefHolder<QoreStringNode> cwd_str;

    assert(pgm);
    cwd_str = pgm->getScriptDir();

    if (!cwd_str) {
        char* cwd = getcwd(nullptr, 0);
        if (!cwd) {
            throw QoreJniException("JNI-ADD-RELATIVE-CLASSPATH-ERROR", "cannot determine relative path; there is no "
                "information in the Program context and cannot get current working directory: %s", strerror(errno));
        }
        ON_BLOCK_EXIT(free, cwd);
        cwd_str = new QoreStringNode(cwd);
    }

    // the path in the default encoding, as the script directory is; a path with a NUL character is refused before
    // it is used in any way (qore#6771)
    QoreString rel = jni_cmd_default_arg(arg);
    jni_check_classpath(rel, "JNI-ERROR");
    cwd_str->concat(QORE_DIR_SEP);
    cwd_str->concat(rel.c_str(), rel.size());
    q_normalize_path(**cwd_str);

    cwd_str->concat('/');

    printd(LogLevel, "qore_jni_mc_add_relative_classpath() arg: '%s' cwd: '%s'\n", arg.c_str(), cwd_str->c_str());
    // one Java string is both checked and added
    Env env;
    LocalReference<jstring> jcp = env.newPathString(QoreString(cwd_str->c_str(), QCS_DEFAULT));
    jni_check_classpath_sandbox("add-relative-classpath", pgm, env, jcp, false);

    jpc->addClasspath(env, jcp);
}

static void qore_jni_mc_define_pending_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    jni_check_define_class_restricted("define-pending-class", pgm);
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));

    // find end of name; the whole argument is searched, as it can have a NUL character (qore#6738)
    const char* name_end = static_cast<const char*>(memchr(arg.c_str(), ' ', arg.size()));
    if (!name_end) {
        throw QoreJniException("JNI-DEFINE-CLASS-ERROR", "cannot find the end of the class name in the "
            "'define-pending-class' directive");
    }
    qore_offset_t end = name_end - arg.c_str();
    QoreString java_name(&arg, end);
    // a class name with a NUL character is refused, as it is for the other commands, which would cut it at the NUL
    // character (qore#6738)
    jni_check_name(java_name.c_str(), java_name.size(), "class name", "class names");
    QoreString base64(arg.c_str() + end + 1);
    ExceptionSink xsink;
    SimpleRefHolder<BinaryNode> byte_code(base64.parseBase64(&xsink));
    if (xsink) {
        throw XsinkException(xsink);
    }
    jni::Env env;
    assert(jpc);

    printd(5, "define-pending-class %s pgm: %p loader: %x\n", java_name.c_str(), pgm,
        env.callIntMethod((jobject)jpc->getClassLoader(), jni::Globals::methodObjectHashCode, nullptr));

    // convert java name to dot name; QoreURLClassLoader.addPendingClass() requires the dot name
    java_name.replaceAll("/", ".");

    // add the byte code as a pending class
    LocalReference<jstring> jname = env.newString(java_name.c_str(), java_name.size());
    LocalReference<jbyteArray> jbyte_code = QoreToJava::makeByteArray(env, **byte_code);

    std::vector<jvalue> jargs(2);
    jargs[0].l = jname;
    jargs[1].l = jbyte_code;

    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderAddPendingClass, &jargs[0]);
}

static void qore_jni_mc_define_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    jni_check_define_class_restricted("define-class", pgm);
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // find end of name; the whole argument is searched, as it can have a NUL character (qore#6738)
    const char* name_end = static_cast<const char*>(memchr(arg.c_str(), ' ', arg.size()));
    if (!name_end) {
        throw QoreJniException("JNI-DEFINE-CLASS-ERROR", "cannot find the end of the class name in the "
            "'define-class' directive");
    }
    qore_offset_t end = name_end - arg.c_str();
    QoreString java_name(&arg, end);
    // the name is a NUL-terminated string from here on, so a name with a NUL character is refused (qore#6738)
    jni_check_name(java_name.c_str(), java_name.size(), "class name", "class names");
    QoreString base64(arg.c_str() + end + 1);
    ExceptionSink xsink;
    SimpleRefHolder<BinaryNode> byte_code(base64.parseBase64(&xsink));
    if (xsink) {
        throw XsinkException(xsink);
    }
    jni::Env env;

    // The loader ownership probe is only needed for the debug invariant.
#ifndef NDEBUG
    QoreProgram* ptr = reinterpret_cast<QoreProgram*>(env.callLongMethod(jpc->getClassLoader(),
        Globals::methodQoreURLClassLoaderGetPtr, nullptr));
    assert(ptr == pgm);
#endif

    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);
    //printd(5, "qore_jni_mc_define_class() jpc: %p name: '%s' class size: %d\n", jpc, java_name.c_str(),
    //    byte_code->size());

    // conver to binary name
    QoreString binary_name(java_name);
    binary_name.replaceAll("/", ".");

    assert(jpc->getClassLoader());

    // Define the class in the loader of the QoreProgram that owns the underlying QoreClass
    // so every consumer Program references the same Class object.  Defining in the
    // consumer's loader instead leaves child / parent classes in disjoint loaders and the
    // JVM rejects cross-loader override resolution and field references with
    // "loader constraint violation" / NoClassDefFoundError.  See Globals::getCanonicalLoader
    // for the dispatch rules — qoremod.<mod>.<X> routes by module, qore.<X>.<Y>... routes
    // by QoreClass lookup.  When no owning Program can be determined (genuinely
    // anonymous / per-Program classes) we fall back to the consumer loader.
    jobject define_loader = jpc->getClassLoader();
    {
        jobject canon = Globals::getCanonicalLoader(env, jpc->getClassLoader(), binary_name.c_str());
        if (canon) {
            define_loader = canon;
        }
    }
    // a sandboxed Program defines classes only in its own loader: in the loader of another Program, its byte code
    // would become the class that the other Program, and every Program that it serves, loads under that name
    if (!env.isSameObject(define_loader, jpc->getClassLoader())) {
        // trust only, as for the jdbc driver and check_class_option(): defining a class in another Program's loader is
        // a direct use of an uncontrolled API, which the core refuses on trust alone (qore#5741), so a sandbox that
        // restricts nothing but does not trust the code still refuses it
        if (jni_sandbox_restricts_caller(QoreSandboxManagerHelper::All)) {
            // the class name of the module command is UTF-8; the text of the error is in the default encoding
            throw QoreJniException("JNI-SANDBOX-ERROR", "sandboxed code cannot use the \"define-class\" module "
                "command to define class '%s', which belongs to another Program",
                jni_utf8_to_default(binary_name.c_str()).c_str());
        }
    }

    LocalReference<jclass> jcls = Globals::findDefineClass(env, binary_name.c_str(), define_loader,
        static_cast<const unsigned char*>(byte_code->getPtr()), byte_code->size());

    // import the class immediately
    qjcm.findCreateQoreClassInProgram(binary_name, java_name.c_str(), new Class(jcls), pgm);
}

static void qore_jni_mc_set_compat_types(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    bool compat_types = q_parse_bool(arg.c_str());
    jpc->overrideCompatTypes(compat_types);
}

static void qore_jni_mc_set_property(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    jni_check_uncontrolled_cmd("set-property", pgm, "sets a system property of the JVM");
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // find end of property name
    qore_offset_t end = arg.find(' ');
    if (end == -1) {
        throw QoreJniException("JNI-SET-PROPERTY-ERROR", "cannot find the end of the property name in the "
            "'set-property' directive");
    }

    QoreString property(&arg, end);
    QoreString value(arg.c_str() + end + 1, arg.size() - end - 1, QCS_UTF8);

    //printd(5, "qore_jni_mc_set_property() '%s' = '%s'\n", property.c_str(), value.c_str());

    jni::Env env;

    LocalReference<jstring> jprop = env.newString(property.c_str(), property.size());
    LocalReference<jstring> jval = env.newString(value.c_str(), value.size());

    std::vector<jvalue> jargs(2);
    jargs[0].l = jprop;
    jargs[1].l = jval;

    LocalReference<jstring> str = env.callStaticObjectMethod(Globals::classSystem,
        Globals::methodSystemSetProperty, &jargs[0]).as<jstring>();
    str = nullptr;
}

static void qore_jni_mc_mark_module_injected(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // a module name is a NUL-terminated string, so a name with a NUL character is refused (qore#6738)
    jni_check_name(arg.c_str(), arg.size(), "module name", "module names");
    // module names are in the default encoding
    jpc->addInjectedModule(jni_cmd_default_arg(arg).c_str());
}

static void qore_jni_mc_register_shared_class(const QoreString& arg, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    jni_check_uncontrolled_cmd("register-shared-class", pgm, "makes the class of this Program the class of every "
        "Program");
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // a class name with a NUL character is refused, as it is for the other commands, which would cut it at the NUL
    // character (qore#6738)
    jni_check_name(arg.c_str(), arg.size(), "class name", "class names");
    Env env;
    LocalReference<jstring> jname = env.newString(arg.c_str(), arg.size());
    jvalue jarg;
    jarg.l = jname;
    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderRegisterSharedDynamicClass, &jarg);
}

static void qore_jni_mc_kotlin_eval(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    jni_check_uncontrolled_cmd("kotlin-eval", pgm, "runs Kotlin code");
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    jni::Env env;
    // qore#6551: the script engine's discovery, which the first use makes through the thread's context class loader
    // and whose failure is kept, and the evaluation run in the context of the Program being parsed
    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);

    // Initialize the Kotlin script engine if not already done
    Globals::initKotlinScriptEngine();

    if (!Globals::classKotlinScriptEngine) {
        throw QoreJniException("KOTLIN-SCRIPTING-NOT-AVAILABLE",
            "Kotlin scripting is not available - KotlinScriptEngine class not found");
    }

    // Check if scripting is available
    jboolean available = env.callStaticBooleanMethod(Globals::classKotlinScriptEngine,
        Globals::methodKotlinScriptEngineIsAvailable, nullptr);

    if (!available) {
        // Get the error message
        LocalReference<jstring> errMsg = env.callStaticObjectMethod(Globals::classKotlinScriptEngine,
            Globals::methodKotlinScriptEngineGetInitError, nullptr).as<jstring>();
        if (errMsg) {
            Env::GetStringUtfChars err(env, errMsg);
            // the message from Java is UTF-8; the text of the error is in the default encoding
            throw QoreJniException("KOTLIN-SCRIPTING-NOT-AVAILABLE", "Kotlin scripting is not available: %s",
                jni_utf8_to_default(err.c_str()).c_str());
        } else {
            throw QoreJniException("KOTLIN-SCRIPTING-NOT-AVAILABLE",
                "Kotlin scripting is not available - ensure kotlin-scripting-jsr223 JARs are in the classpath");
        }
    }

    // Evaluate the Kotlin code
    LocalReference<jstring> jscript = env.newString(arg.c_str(), arg.size());
    std::vector<jvalue> jargs(1);
    jargs[0].l = jscript;

    LocalReference<jobject> result = env.callStaticObjectMethod(Globals::classKotlinScriptEngine,
        Globals::methodKotlinScriptEngineEval, &jargs[0]);

    // Result is discarded for module command - use kotlin_eval() function for return value
    printd(5, "qore_jni_mc_kotlin_eval() executed Kotlin code successfully\n");
}

QoreClass* jni_class_handler(QoreNamespace* ns, const char* cname) {
    // get full class path
    QoreString cp(ns->getName());
    cp.concat('.');
    cp.concat(cname);

    const QoreNamespace* jns = ns;
    while (true) {
        printd(LogLevel, "jni_class_handler() ns: %p (%s) jns: %p (%s) cname: %s\n", ns, ns->getName(), jns,
            jns->getName(), cname);
        jns = jns->getParent();
        assert(jns);
        if (!strcmp(jns->getName(), "Jni"))
            break;
        cp.prepend(".");
        cp.prepend(jns->getName());
    }

    printd(LogLevel, "jni_class_handler() ns: %p cname: %s cp: %s\n", ns, cname, cp.c_str());

    QoreProgram* pgm = ns->getProgram();
    assert(pgm);
    try {
        Env env;
        QoreClass* qc = qjcm.findCreateQoreClass(env, cp.c_str(), pgm);
        printd(LogLevel, "jni_class_handler() cp: %s returning qc: %p\n", cp.c_str(), qc);
        return qc;
    } catch (jni::JavaException& e) {
        // ignore class not found exceptions here
        e.ignoreOrRethrowNoClass();
    } catch (jni::Exception& e) {
        // display exception info on the console as an unhandled exception
        {
            ExceptionSink xsink;
            e.convert(&xsink);
        }
        assert(false);
    }
    return nullptr;
}
