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
/// \brief Contains various definitions.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_DEFS_H_
#define QORE_JNI_DEFS_H_

#include <qore/Qore.h>
#include <qore/QoreSandboxManager.h>

#include "jni.h"

#include <stdarg.h>
#include <memory>

//! Returns true if a sandbox that does not trust the calling code governs it
/** With a %Qore that has the uncontrolled-API policy of sandboxes (\c SandboxManager::setUncontrolledApiPolicy()),
    code counts as restricted only if a governing sandbox refuses uncontrolled APIs (the default), so that code that a
    host governs with a sandbox only for its interrupts or limits, and trusts, is not refused; without it, any
    governing sandbox restricts the code.

    @param purpose how the governing sandboxes are resolved (see QoreSandboxManagerHelper)
*/
static inline bool jni_sandbox_restricts_caller(QoreSandboxManagerHelper::Purpose purpose) {
#ifdef _QORE_HAS_SANDBOX_UNCONTROLLED_API_POLICY
    return !qore_sandbox_allows_uncontrolled_apis(purpose);
#else
    QoreSandboxManagerHelper governing(purpose);
    return static_cast<bool>(governing);
#endif
}

// module API 2.1 (qore#5699): module commands are judged by the restrictions of the code that issues them, and module
// functions can judge their caller the same way
#if QORE_MODULE_API_MAJOR > 2 || (QORE_MODULE_API_MAJOR == 2 && QORE_MODULE_API_MINOR >= 1)
#define QORE_JNI_HAS_COMMAND_CONTEXT 1
#endif

namespace jni {

/**
 * \brief Logging level used by the JNI module in calls to printd().
 */
constexpr int LogLevel = 10;

/**
 * \brief Base class for exceptions.
 */
class Exception : public AbstractException {
public:
    /**
     * \brief Default virtual destructor.
     */
    virtual ~Exception() = default;

    /**
     * \brief ignore the exception
     */
    virtual void ignore() = 0;

    Exception(Exception &&) = default;
    Exception &operator=(Exception &&) = default;

protected:
    /**
     * \brief Default constructor.
     */
    Exception() = default;

private:
    Exception(const Exception &) = delete;
    Exception &operator=(const Exception &) = delete;
};

/**
 * \brief An exception that can be safely ignored with no action
 */
class IgnorableException : public Exception {
    DLLLOCAL void ignore() override {
    }
};

/**
 * \brief An exception thrown when the current thread cannot be attached to the JVM.
 */
class UnableToAttachException : public IgnorableException {
public:
    /**
     * \brief Constructor.
     * \param err the error code
     */
    DLLLOCAL UnableToAttachException(jint err) : err(err) {
        printd(LogLevel, "JNI: error attaching thread %d, err: %d\n", q_gettid(), err);
    }

    DLLLOCAL void convert(ExceptionSink *xsink) override {
        xsink->raiseException("JNI-ERROR", "Unable to attach thread to the JVM, error %d", err);
    }

private:
    jint err;
};

/**
 * \brief An exception thrown when Java is used in a child process created with fork() after the JVM was started
 *
 * The JVM cannot be used in such a process (see Jvm::isForkedChild()); code that ignores attach errors (cleanup
 * paths) ignores this one too.
 */
class ForkedChildException : public UnableToAttachException {
public:
    DLLLOCAL ForkedChildException() : UnableToAttachException(JNI_ERR) {
    }

    DLLLOCAL void convert(ExceptionSink *xsink) override {
        xsink->raiseException("JNI-FORK-ERROR", "Java cannot be used in this process: it was created with fork() "
            "after the JVM was started, and the threads of the JVM exist only in the parent process");
    }
};

/**
 * \brief An exception thrown when the current thread cannot be registered with Qore.
 */
class UnableToRegisterException : public IgnorableException {
public:
    /**
     * \brief Constructor.
     */
    DLLLOCAL UnableToRegisterException() {
        printd(LogLevel, "JNI: error registering thread %ld with Qore\n", pthread_self());
    }

    DLLLOCAL void convert(ExceptionSink *xsink) override {
        xsink->raiseException("JNI-ERROR", "Unable to register thread with Qore");
    }
};

/**
 * \brief A C++ exception representing a Java exception.
 */
class JavaException : public Exception {
public:
    /**
     * \brief Constructor.
     */
    DLLLOCAL JavaException() {
    }

    DLLLOCAL void convert(ExceptionSink* xsink) override;

    DLLLOCAL void ignore() override;

    DLLLOCAL void ignoreOrRethrowNoClass();

    //! returns true if the exception should be rethrown
    /** workaround for https://bugs.openjdk.java.net/browse/JDK-8221530
    */
    DLLLOCAL bool checkBug_8221530();

    DLLLOCAL jthrowable save();

    DLLLOCAL void restore(jthrowable je);

    DLLLOCAL QoreStringNode* toString(bool clear = true) const;
};

/**
 * \brief Basic exception with a simple string messsage.
 */
class BasicException : public IgnorableException {
public:
   /**
    * \brief Constructor.
    * \param message the exception message
    */
   DLLLOCAL BasicException(std::string message) : message(std::move(message)) {
   }

   DLLLOCAL void convert(ExceptionSink *xsink) override {
      xsink->raiseException("JNI-ERROR", "%s", message.c_str());
   }

private:
   std::string message;
};

/**
 * \brief Basic exception with a simple string messsage.
 */
class QoreJniException : public IgnorableException {
public:
    /**
     * \brief Constructor.
     * \param err the exception error string
     * \param message the exception message
     */
    DLLLOCAL QoreJniException(std::string err, const char* message, ...) : err(std::move(err)) {
        va_list args;

        while (true) {
            va_start(args, message);
            int rc = desc.vsprintf(message, args);
            va_end(args);
            if (!rc)
                break;
        }
    }

    DLLLOCAL const char* what(QoreString& str) {
        str.sprintf("%s: %s", err.c_str(), desc.c_str());
        return str.c_str();
    }

    DLLLOCAL void convert(ExceptionSink *xsink) override {
        xsink->raiseException(err.c_str(), "%s", desc.c_str());
    }



private:
    std::string err;
    QoreString desc;
};

/**
 * \brief A C++ wrapper for an exception raised in the ExceptionSink.
 */
class XsinkException : public Exception {
public:
   /**
    * \brief Constructor.
    * \param xsink the exception sink with an exception
    */
   DLLLOCAL XsinkException(ExceptionSink &xsink) : sink(new ExceptionSink()) {
      sink->assimilate(xsink);
   }

   DLLLOCAL void convert(ExceptionSink *xsink) override {
      xsink->assimilate(*sink);
   }

   DLLLOCAL virtual void ignore() override {
      sink->clear();
   }

private:
   std::unique_ptr<ExceptionSink> sink;
};

//! The native state of a Java \c org.qore.jni.QoreClosure object: a Qore closure or call reference handed to Java
/** Java can call the closure on any thread, including a thread that Qore did not start and that has no Qore caller,
    so the closure is called and released under the sandbox of the code that handed it to Java, captured when the
    handle is created.
*/
class JniClosureHandle {
public:
    //! References the closure and captures the sandbox of the current thread
    DLLLOCAL JniClosureHandle(const ResolvedCallReferenceNode* call) : call(call->refRefSelf()) {
    }

    //! Releases the closure under the captured sandbox; must be called on a Qore thread
    DLLLOCAL ~JniClosureHandle() {
        QoreSandboxCaptureHelper sch(sandbox);
        ExceptionSink xsink;
        call->deref(&xsink);
        // an exception raised by releasing the closure cannot be reported to a caller
        xsink.clear();
    }

    //! Returns the closure
    DLLLOCAL ResolvedCallReferenceNode* getCall() const {
        return call;
    }

    //! Returns the sandbox of the code that handed the closure to Java
    DLLLOCAL const QoreSandboxCapture& getSandbox() const {
        return sandbox;
    }

private:
    ResolvedCallReferenceNode* call;
    QoreSandboxCapture sandbox;

    // non-copyable
    JniClosureHandle(const JniClosureHandle&) = delete;
    JniClosureHandle& operator=(const JniClosureHandle&) = delete;
};

//! The native state of a Java \c org.qore.jni.QoreObjectBase object: a weak reference to a Qore object in Java
/** The pointer to the handle is the \c obj field of the Java object, which the generated Java classes of Qore
    classes pass to the native methods unchanged.  Java can call the methods of the object on any thread, including
    a thread that Qore did not start and that has no Qore caller, so they are called, and the object is destroyed,
    under the sandbox of the code that handed the object to Java, captured when the handle is created (for an object
    that Java creates, the code that runs the Java constructor).
*/
class JniObjectHandle {
public:
    //! Makes a weak reference to the object and captures the sandbox of the current thread
    DLLLOCAL explicit JniObjectHandle(QoreObject* obj) : obj(obj) {
        obj->tRef();
    }

    //! Releases the weak reference
    DLLLOCAL ~JniObjectHandle() {
        obj->tDeref();
    }

    //! Returns the handle that a Java object carries
    DLLLOCAL static JniObjectHandle* fromJava(jlong ptr) {
        assert(ptr);
        return reinterpret_cast<JniObjectHandle*>(ptr);
    }

    //! Returns the object
    DLLLOCAL QoreObject* getObject() const {
        return obj;
    }

    //! Returns the sandbox of the code that handed the object to Java
    DLLLOCAL const QoreSandboxCapture& getSandbox() const {
        return sandbox;
    }

private:
    QoreObject* obj;
    QoreSandboxCapture sandbox;

    // non-copyable
    JniObjectHandle(const JniObjectHandle&) = delete;
    JniObjectHandle& operator=(const JniObjectHandle&) = delete;
};

class QoreThreadAttacher {
public:
    DLLLOCAL QoreThreadAttacher() : attached(false) {
    }

    DLLLOCAL ~QoreThreadAttacher() {
        if (attached) {
            detach();
        }
    }

    // returns 0 = attached, -1 = already attached
    DLLLOCAL int attach() {
        if (!attached) {
            attachIntern();
            return 0;
        }
        return -1;
    }

    DLLLOCAL void detach() {
        if (attached) {
            detachIntern();
        }
    }

    DLLLOCAL operator bool() const {
        return attached;
    }

private:
    bool attached;

    DLLLOCAL void attachIntern() {
        assert(!attached);
        int rc = q_register_foreign_thread();
        if (rc == QFT_OK) {
            attached = true;
            printd(LogLevel, "Thread %ld attached to Qore\n", pthread_self());
        } else if (rc != QFT_REGISTERED) {
            throw UnableToRegisterException();
        }
    }

    DLLLOCAL void detachIntern() {
        assert(attached);
        printd(LogLevel, "Detaching thread %ld from Qore\n", pthread_self());
        q_deregister_foreign_thread();
        attached = false;
    }
};

// class that serves to attach a thread to Qore if not already attached
// if attached in the constructor, then it will detach in the destructor
class QoreThreadAttachHelper {
public:
    DLLLOCAL void attach() {
        attached = !attacher.attach();
    }

    DLLLOCAL ~QoreThreadAttachHelper() {
        if (attached) {
            attacher.detach();
        }
    }

private:
    QoreThreadAttacher attacher;
    bool attached = false;
};

} // namespace jni

//! Refuses a classpath to sandboxed code where its policy does not allow it (see jni-module.cpp)
/** @throw JNI-SANDBOX-ERROR sandboxed or restricted code changes the classpath of every Program
    @throw FILESYSTEM-ACCESS-DENIED the sandbox policy denies reading a classpath entry
*/
DLLLOCAL void jni_check_classpath_sandbox(const char* cmd, const QoreProgram* pgm, const char* classpath, bool global);

extern "C" DLLEXPORT int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import);
extern "C" DLLEXPORT QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm);

#endif // QORE_JNI_DEFS_H_
