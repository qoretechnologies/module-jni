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
     */
    static void destroyVM();

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

private:
    /**
     * \brief This is a static class - no instances are allowed.
     */
    Jvm() = delete;

private:
    static JavaVM* vm;
    static thread_local JNIEnv* env;
    static bool native_access_option_enabled;
};

} // namespace jni

#endif // QORE_JNI_JVM_H_
