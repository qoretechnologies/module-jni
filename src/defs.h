//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.
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
      xsink->raiseException("JNI-ERROR", message.c_str());
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
        xsink->raiseException(err.c_str(), desc.c_str());
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

extern thread_local QoreThreadAttacher qoreThreadAttacher;

} // namespace jni

extern "C" DLLEXPORT int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import);
extern "C" DLLEXPORT QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm);

#endif // QORE_JNI_DEFS_H_
