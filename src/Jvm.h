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
///
/// \file
/// \brief Defines the Jvm class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_JVM_H_
#define QORE_JNI_JVM_H_

#include <qore/Qore.h>

#include <atomic>
#include <cassert>
#include <jni.h>

namespace jni {

/**
 * \brief Provides access to JNI Invocation API.
 */
class Jvm {

public:
    /**
     * \brief Returns the Env object associated with this thread.
     *
     * Assumes that the thread has been attached to the JVM.
     * \return the Env object associated with this thread
     */
    static JNIEnv* getEnv() {
        assert(env != nullptr);
        return env;
    }

    /**
     * \brief Sets the Env object associated with this thread.
     * \param env the Env object associated with this thread
     */
    static void setEnv(JNIEnv* env) {
        Jvm::env = env;
    }

    /**
     * \brief Returns the Env object associated with this thread. Attaches the thread to the JVM if needed.
     * \param new_thread an output variable indicating if this thread is attaching to the JVM for the first time or not
     * \return the Env object associated with this thread
     * \throws UnableToAttachException if the thread cannot be attached to the JVM
     */
    static JNIEnv* attachAndGetEnv(bool& new_attach);

    /**
     * \brief Returns the Env object associated with this thread. Attaches the thread to the JVM if needed.
     * \return the Env object associated with this thread
     * \throws UnableToAttachException if the thread cannot be attached to the JVM
     */
    static JNIEnv* attachAndGetEnv();

    /**
     * \brief Creates the JVM.
     * \return 0 if successful
     */
    static QoreStringNode* createVM();

    /**
     * \brief Sets the VM pointer.
     */
    static void setVmPtr(JavaVM* vm) {
        assert(!Jvm::vm);
        Jvm::vm = vm;
    }

    /**
     * \brief Destroys the JVM.
     *
     * Called from the module's teardown, where no C++ exception may propagate: a step that fails is reported on
     * stderr and the JVM is destroyed nonetheless (qore#6788).
     */
    static void destroyVM() noexcept;

    /**
     * \brief Detaches the current thread from the JVM.
     */
    static void threadCleanup();

    /**
     * \brief Returns true if native access is enabled for unnamed modules.
     */
    static bool nativeAccessOptionEnabled() {
        return native_access_option_enabled;
    }

    /**
     * \brief Returns true in a child process created with fork() after the JVM was created or set
     *
     * Such a process cannot use the JVM: only the thread that called fork() exists in it, while the threads of the
     * JVM (the VM thread, the garbage collectors, the compilers, the NativeCleanup thread) remain in the parent.
     */
    static bool isForkedChild() {
        return forked_child;
    }

    /**
     * \brief Returns true once the JVM has started to end the process for Java's System.exit() or Runtime.halt()
     *
     * Set by the JVM's exit hook (see Jvm.cpp) on the VM Thread at the JVM's final safepoint, where Java threads stay
     * stopped for ever: the JVM can then be neither called nor destroyed.
     */
    static bool isExiting() {
        return exiting.load(std::memory_order_acquire);
    }

    /**
     * \brief Marks the JVM as ending the process (see isExiting())
     */
    static void setExiting() {
        exiting.store(true, std::memory_order_release);
    }

    /**
     * \brief Registers the fork handler for a child process created with fork()
     *
     * The handler marks the child (see isForkedChild()) and initializes the save object callback lock again.
     *
     * Called in the module's initialization function, as fork handlers must be (see QoreForkHandler.h).
     * \return 0 if successful, -1 if there was no memory for the registration
     */
    static int registerForkHandler();

    /**
     * \brief Removes the registration made with registerForkHandler()
     */
    static void unregisterForkHandler();

    /**
     * \brief Returns the JavaVM pointer.  Used by the NativeCleanup C++
     * background thread (Globals::startNativeCleanupThread) to attach
     * itself as a daemon JNI thread.
     */
    static JavaVM* getVm() {
        return vm;
    }

private:
    /**
     * \brief This is a static class - no instances are allowed.
     */
    Jvm() = delete;

    /**
     * \brief The fork handler run in the child of fork(): initializes the save object callback lock again, and marks
     * the process if the JVM was created or set
     */
    static void forkChild(void*);

private:
    static JavaVM* vm;
    static thread_local JNIEnv* env;
    static bool native_access_option_enabled;
    //! true in a child process created with fork() after the JVM was created or set; see isForkedChild()
    /** Set by forkChild() while only the forking thread exists in the child, so every thread created later in the
        child sees it; never set in the process that created the JVM
    */
    static bool forked_child;
    //! true once the JVM has started to end the process; see isExiting()
    static std::atomic<bool> exiting;
    //! the registration of forkChild()
    static QoreForkHandlerRegistration* fork_reg;
};

} // namespace jni

#endif // QORE_JNI_JVM_H_
