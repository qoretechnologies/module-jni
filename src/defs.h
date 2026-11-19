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

// _QORE_HAS_SANDBOX_RESTRICTS_API: the sandbox API that has QoreSandboxManager::trustsUncontrolledApis(), which
// aggregates over the members of a composition of sandboxes
#ifdef _QORE_HAS_SANDBOX_RESTRICTS_API
//! Returns true if uncontrolled code is refused to the calling code
/** Uncontrolled code (a JDBC driver, a Java class that an option names) runs in Java, where no sandbox policy or limit
    applies to its I/O, threads or memory, and can reach any Java API, also one that changes the state of the whole
    JVM.  It is refused unless the governing sandbox trusts the code with uncontrolled APIs
    (QoreSandboxManager::trustsUncontrolledApis()), whatever the sandbox restricts: a sandbox that restricts nothing
    but does not trust the code (an allow-all manager without SandboxManager::setUncontrolledApiPolicy()) refuses it,
    as the Qore library refuses such code every uncontrolled API.  For a composition of sandboxes, which governs code
    when more than one Program's manager is on the thread's stack, the trust is the AND over the members, so the code
    is allowed only if every member trusts it.  The parse options and functional domains of the calling Program are
    not judged here.

    Code that no sandbox governs, and code under a system-policy barrier or capability of trusted code (resolved with
    QoreSandboxManagerHelper::Policy), has no governing manager and is not refused.  The Qore library resolves a
    governing manager for all sandboxed code, so code that is restricted (jni_sandbox_restricts_caller()) without
    one is not expected; such code would be refused (defence in depth).

    @param purpose how the governing sandbox is resolved (see QoreSandboxManagerHelper); QoreSandboxManagerHelper::Policy
    honours a policy barrier and a system-policy capability of trusted code
*/
static inline bool jni_sandbox_refuses_uncontrolled_code(QoreSandboxManagerHelper::Purpose purpose) {
    QoreSandboxManagerHelper governing(purpose);
    if (!governing) {
        return jni_sandbox_restricts_caller(purpose);
    }
    return !governing->trustsUncontrolledApis();
}
#endif

// module API 2.3 (qore#5699): module commands are judged by the restrictions of the code that issues them, and module
// functions can judge their caller the same way
#if QORE_MODULE_API_MAJOR > 2 || (QORE_MODULE_API_MAJOR == 2 && QORE_MODULE_API_MINOR >= 3)
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

   //! Leaves the %Qore exception unfreed, for where it cannot be freed safely (no %Qore thread, or %Qore shut down)
   DLLLOCAL void leak() noexcept {
      // deliberately leaked: freeing it there would report it as unhandled and free Qore values in an invalid state
      static_cast<void>(sink.release());
   }

private:
   std::unique_ptr<ExceptionSink> sink;
};

//! Returns true if the string has a NUL character before \a len
static inline bool jni_has_nul(const char* str, size_t len) {
    return memchr(str, '\0', len) != nullptr;
}

//! Raises \c JNI-ERROR if a name has a NUL character (qore#6738)
/** A name (ex: a class, method, field, function or module name) is a NUL-terminated string in %Qore and in JNI, so
    a name with a NUL character would be cut at it, and name something else: it is refused instead, with the error
    of q_check_no_nul() (ex: <tt>class name "java/lang/String\x00junk" contains a NUL character at byte offset 16;
    class names cannot contain NUL characters</tt>).

    @param name the name, which need not be NUL-terminated
    @param len the length of the name in bytes
    @param what what the name is, in the singular (ex: \c "class name")
    @param what_plural what the name is, in the plural (ex: \c "class names")
    @param xsink the exception is raised here if the name has a NUL character

    @return 0 if the name has no NUL character, -1 if it has one and the exception was raised
*/
static inline int jni_check_name(const char* name, size_t len, const char* what, const char* what_plural,
        ExceptionSink* xsink) {
    return q_check_no_nul(name, len, what, what_plural, "JNI-ERROR", xsink);
}

//! Throws XsinkException with \c JNI-ERROR if a name has a NUL character; see jni_check_name()
static inline void jni_check_name(const char* name, size_t len, const char* what, const char* what_plural) {
    if (jni_has_nul(name, len)) {
        ExceptionSink xsink;
        jni_check_name(name, len, what, what_plural, &xsink);
        throw XsinkException(xsink);
    }
}

//! Raises an exception if a classpath has a NUL character (qore#6771)
/** A classpath entry is a file path, a NUL-terminated string for the operating system, so a classpath with a NUL
    character would be cut at it, and the cut classpath would be both checked against the sandbox and used: it is
    refused instead, as libqore's q_check_path() refuses a path, with an error like <tt>classpath "/dir\x00junk"
    contains a NUL character at byte offset 4; classpaths cannot contain NUL characters</tt>.  Every classpath that
    the module adds is checked with this function before it is used in any way (the \c add-classpath and
    \c add-relative-classpath module commands, their \c global-* forms and the \c classpath option of the \c jdbc
    driver); \c QORE_JNI_CLASSPATH, an environment variable, is a C string and cannot have a NUL character.

    @param cp the classpath, which need not be NUL-terminated
    @param len the length of the classpath in bytes
    @param err the error code of the exception
    @param xsink the exception is raised here if the classpath has a NUL character

    @return 0 if the classpath has no NUL character, -1 if it has one and the exception was raised
*/
static inline int jni_check_classpath(const char* cp, size_t len, const char* err, ExceptionSink* xsink) {
    return q_check_no_nul(cp, len, "classpath", "classpaths", err, xsink);
}

//! Throws XsinkException if a classpath has a NUL character (qore#6771); see jni_check_classpath()
static inline void jni_check_classpath(const QoreString& cp, const char* err) {
    if (jni_has_nul(cp.c_str(), cp.size())) {
        ExceptionSink xsink;
        jni_check_classpath(cp.c_str(), cp.size(), err, &xsink);
        assert(xsink);
        throw XsinkException(xsink);
    }
}

//! Throws XsinkException with \c HASH-KEY-ERROR if a string that becomes a hash key has a NUL character (qore#6738)
/** A hash key is a NUL-terminated string, so a key with a NUL character would be cut at it, and name another key: it
    is refused instead, with the error of QoreHashNode::checkKey() (ex: <tt>Java Map key "a\x00b" contains a NUL
    character at byte offset 1, which a Qore hash key cannot hold</tt>), as a %Qore string with a NUL character is
    refused as a hash key.

    @param key the key, which need not be NUL-terminated
    @param len the length of the key in bytes
    @param what what the key is (ex: \c "Java Map key")
*/
static inline void jni_check_key(const char* key, size_t len, const char* what) {
    if (jni_has_nul(key, len)) {
        ExceptionSink xsink;
        QoreHashNode::checkKey(key, len, "HASH-KEY-ERROR", what, &xsink);
        throw XsinkException(xsink);
    }
}

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

//! Appends a value of a %Qore exception (ex: its code or description) to a string in UTF-8
/** The value is converted to a string, and the string to UTF-8; a string that cannot be converted to UTF-8 is
    appended as it is (Env::newString() decodes it strictly, so that each invalid sequence becomes U+FFFD).  No C++
    exception is raised other than \c std::bad_alloc.

    @param utf8 the string to append to, in UTF-8
    @param v the value to append
*/
DLLLOCAL void jni_append_utf8(QoreString& utf8, QoreValue v);

//! Returns a UTF-8 string (ex: a Java name) in the default encoding, for the text of an error that is in it
/** The text of an error raised in a module command is in the default encoding, as Qore appends the command to it as
    it is; a string that cannot be converted is returned as it is.  No C++ exception is raised other than
    \c std::bad_alloc.
*/
DLLLOCAL QoreString jni_utf8_to_default(const char* utf8);

//! Reports the C++ exception being handled on stderr as the failure of a step
/** Must be called in a \c catch block; for the paths that libqore or the C library call where there is no exception
    sink and from which no C++ exception may propagate: the module's teardown (the module's delete function, the
    \c atexit() handler, the rollback of a failed initialization), so that the teardown continues after the step
    fails (qore#6788), and the copy of a Program's jni data for a child Program (qore#6787).  A jni::Exception is
    described with the %Qore exception that it converts to.  Never throws.

    @param step what failed (ex: \c "stopping the NativeCleanup thread")
*/
DLLLOCAL void jni_report_current_exception(const char* step) noexcept;

//! Runs a step of the module's teardown, reporting a C++ exception that it throws and continuing (qore#6788)
/** see jni_report_current_exception()
*/
template <typename F>
DLLLOCAL void jni_teardown_step(const char* step, F&& f) noexcept {
    try {
        f();
    } catch (...) {
        jni_report_current_exception(step);
    }
}

} // namespace jni

namespace jni {
class Env;
}

//! Refuses a classpath to sandboxed code where its policy does not allow it (see jni-module.cpp)
/** @param classpath the classpath as the Java string that is then given to QoreURLClassLoader.addPath()

    @throw JNI-SANDBOX-ERROR sandboxed or restricted code changes the classpath of every Program
    @throw FILESYSTEM-ACCESS-DENIED the sandbox policy denies reading a classpath entry
*/
DLLLOCAL void jni_check_classpath_sandbox(const char* cmd, const QoreProgram* pgm, jni::Env& env, jstring classpath,
        bool global);

//! Checks the entries of a classpath against a filesystem policy, as the JVM uses them (see jni-module.cpp)
/** @param policy the policy to check the entries against; nullptr: no policy, every entry is allowed
    @param env the JNI environment
    @param classpath the classpath as the Java string that is then given to QoreURLClassLoader.addPath(), which
    splits it into entries at the system path separator or \c ';'; each entry is checked as the absolute path that
    the JVM resolves it to (against its \c user.dir), in the bytes that the JVM passes to the operating system for it

    @throw FILESYSTEM-ACCESS-DENIED the policy denies reading a classpath entry
    @throw JNI-SANDBOX-ERROR an entry cannot be represented in the file name encoding of the JVM
*/
DLLLOCAL void jni_check_classpath_policy(QoreSandboxManager* policy, jni::Env& env, jstring classpath);

extern "C" DLLEXPORT int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import);
extern "C" DLLEXPORT QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm);

#endif // QORE_JNI_DEFS_H_
