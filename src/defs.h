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
#include "JniHandleRegistry.h"

#include <stdarg.h>
#include <memory>
#include <mutex>

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

#ifdef QORE_HAVE_UNCONTROLLED_API_ENFORCEMENT
//! The restriction classes (bits of QORE_UNCONTROLLED_API_RESTRICTION_DOMAINS) that the Java code jni runs enforces
/** None (qore#7832): Java code does its own file, network and process I/O, which no Qore restriction reaches, so code
    with \c PO_NO_FILESYSTEM, \c PO_NO_NETWORK or \c PO_NO_EXTERNAL_PROCESS is refused Java as code with
    \c PO_NO_UNCONTROLLED_APIS is.  The module declares nothing (qore_module_declare_enforced_domains() is not called),
    and every Java class sets this value explicitly (JniQoreClass), so the builtins of the module and the Java classes
    all have it.
*/
#define QORE_JNI_ENFORCED_DOMAINS 0
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

//! Masks the secrets in the text of the Java exceptions converted while it is set (qore#7344)
/** The jdbc driver sets one for each operation on a datasource (see JavaExceptionMaskHelper), as a JDBC driver can
    name the URL of the connection, which can hold credentials, in its exception messages
*/
class JavaExceptionTextMasker {
public:
    DLLLOCAL virtual ~JavaExceptionTextMasker() = default;

    //! Replaces the secrets in a text in UTF-8 with their masked forms; returns true if a secret was replaced
    DLLLOCAL virtual bool maskText(QoreString& text) const = 0;
};

//! Sets the masker of the Java exceptions converted in the current thread while it exists (qore#7344)
class JavaExceptionMaskHelper {
public:
    //! Sets \a masker for the thread, which the helper keeps while it exists; a null masker leaves the masker of the
    //! thread as it is
    DLLLOCAL JavaExceptionMaskHelper(std::shared_ptr<const JavaExceptionTextMasker> masker) : masker(std::move(masker)),
            prev(current) {
        if (this->masker) {
            current = this->masker.get();
        }
    }

    DLLLOCAL ~JavaExceptionMaskHelper() {
        current = prev;
    }

    //! Masks the text of a Java exception with the masker of the thread, if any; returns true if a secret was replaced
    DLLLOCAL static bool mask(QoreString& text) {
        return current ? current->maskText(text) : false;
    }

    //! Returns true if a masker is set for the thread
    DLLLOCAL static bool active() {
        return current != nullptr;
    }

    JavaExceptionMaskHelper(const JavaExceptionMaskHelper&) = delete;
    JavaExceptionMaskHelper& operator=(const JavaExceptionMaskHelper&) = delete;

private:
    //! the masker that this helper set, which it keeps: the object that gave it can be deleted while the helper exists
    //! (ex: a connection closed by a callback during an operation of the connection)
    std::shared_ptr<const JavaExceptionTextMasker> masker;

    //! the masker of the thread before this helper set it
    const JavaExceptionTextMasker* prev;

    //! the masker of the thread
    DLLLOCAL static thread_local const JavaExceptionTextMasker* current;
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

    //! Describes the pending Java exception, which is cleared if \a clear is true and pending again otherwise
    /** The exception is not converted: a QoreExceptionWrapper keeps its %Qore exception (qore#6864).
    */
    DLLLOCAL QoreStringNode* toString(bool clear = true) const;

private:
    //! Describes a Java exception and its causes; no Java exception may be pending
    DLLLOCAL static QoreStringNode* describe(JNIEnv* env, jthrowable throwable);
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
 * \brief The Qore Program named by a Java class loader's pointer has been deleted, or is no longer the class loader's.
 */
class JavaProgramDeletedException : public BasicException {
public:
   using BasicException::BasicException;
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
/** Java holds an opaque handle of this state (see JniHandleRegistry.h), never its address.  Java can call the methods of the object on any thread, including
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

//! The native state of a Java \c org.qore.jni.QoreExceptionWrapper: a Qore exception held for Java
/** The lock serializes reading the exception with taking it, as Java can do both on any thread
*/
class JniExceptionHandle {
public:
    DLLLOCAL JniExceptionHandle() {
        sink.markExternallyManaged();
    }

    DLLLOCAL ~JniExceptionHandle() {
        sink.clear();
    }

    std::mutex lock;
    ExceptionSink sink;

    JniExceptionHandle(const JniExceptionHandle&) = delete;
    JniExceptionHandle& operator=(const JniExceptionHandle&) = delete;
};

template <>
struct JniHandleTraits<JniClosureHandle> {
    static constexpr JniHandleKind kind = JniHandleKind::Closure;
};

template <>
struct JniHandleTraits<JniObjectHandle> {
    static constexpr JniHandleKind kind = JniHandleKind::Object;
};

template <>
struct JniHandleTraits<JniExceptionHandle> {
    static constexpr JniHandleKind kind = JniHandleKind::ExceptionWrapper;
};

//! Runs the release of native state on any thread
/** The last reference to the state can be dropped on a thread that is not attached to Qore, or with a Java exception
    pending, and the release can run Qore code (a destructor): the thread is attached for the release, the record of
    the calling code is cleared, and a pending Java exception is kept.  Nothing is thrown.
*/
DLLLOCAL void jni_release_native_state(void (*release)(void*), void* state) noexcept;

//! Returns a shared pointer that releases the state with jni_release_native_state()
/** If the pointer cannot be allocated, the state is released and std::bad_alloc is thrown
*/
template <typename T>
std::shared_ptr<T> jni_make_native_state(T* state) {
    return std::shared_ptr<T>(state, [](T* p) {
        jni_release_native_state([](void* v) { delete static_cast<T*>(v); }, p);
    });
}

//! Counts an attachment of the calling thread to Qore by the module (\a attached true) or its end (false)
/** A thread that the module attached is one that Java started: its Qore caller is not known (qore#7023)
*/
DLLLOCAL void jni_java_thread_attached(bool attached);

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
            jni_java_thread_attached(true);
            printd(LogLevel, "Thread %ld attached to Qore\n", pthread_self());
        } else if (rc != QFT_REGISTERED) {
            throw UnableToRegisterException();
        }
    }

    DLLLOCAL void detachIntern() {
        assert(attached);
        printd(LogLevel, "Detaching thread %ld from Qore\n", pthread_self());
        if (!q_deregister_foreign_thread()) {
            jni_java_thread_attached(false);
        }
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

//! Makes jni_report_current_exception() report a jni::Exception without converting it to a %Qore exception
/** For the module's \c atexit() handler, which can run after the %Qore library has been cleaned up, when the
    conversion, which creates a %Qore exception, can no longer be relied on to run
*/
DLLLOCAL void jni_report_set_plain() noexcept;

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

//! Throws the refusal of qore#6845 if jni_governed_caller_reaches() refuses \a pgm
/** For the calls from Java into a Program that a generated binding or the Program context of the call names, which
    do not pass through JniJavaProgramRef: the functions, static methods, constants and constructors of the Java classes
    generated for Qore definitions, and the objects that QoreJavaApi creates (qore#7023).  The instance methods of an
    existing Qore object are not checked: the object was handed to Java by the code that has it, as a callback that
    runs in its own Program, as a call of a method of a Qore object does in Qore.

    @throw BasicException the calling code is governed by a sandbox and \a pgm is not the Program of the calling code
    @throw QoreJniException \c JNI-SANDBOX-ERROR: once sandboxed code has used Java, the call was made by a Java object
    whose origin cannot be determined (qore#7081)
*/
DLLLOCAL void jni_check_governed_caller_reaches(const QoreProgram* pgm);

//! The record of the %Qore code that calls Java (see JniJavaCallerScope)
struct JniJavaCallerRecord {
    //! true if a Program (also none) is recorded
    bool set = false;
    //! true if the record reaches no Program
    bool deny = false;
    //! the Program of the calling code (nullptr: no Program, which reaches none when a sandbox governs the call)
    QoreProgram* pgm = nullptr;
};

//! Returns the record of the code that obtained a value from Java on the thread (qore#7023)
/** The record of the innermost call from %Qore into Java, else the record that the innermost call from Java into
    %Qore cleared (the caller of the Java code that supplies the value), else a record that reaches no Program
*/
DLLLOCAL JniJavaCallerRecord jni_java_caller_owner();

class Env;

//! The origin of a Java object that sandboxed code made or handed over, as jni_origin_get() finds it (qore#7081)
struct JniJavaOrigin {
    //! true if an origin is recorded for the object
    bool found = false;
    //! true if the origin reaches no Program: more than one Program supplied the object, its Program has been
    //! deleted, or the Program of the code that supplied it was not known
    bool deny = false;
    //! the Program of the origin, held with a dependency reference while this object exists, if found and not denied
    QoreProgram* pgm = nullptr;

    DLLLOCAL JniJavaOrigin() = default;
    DLLLOCAL ~JniJavaOrigin();

    JniJavaOrigin(const JniJavaOrigin&) = delete;
    JniJavaOrigin& operator=(const JniJavaOrigin&) = delete;
};

//! Records the code of \a rec as the origin of the Java object \a obj (qore#7081)
/** The origin is recorded by the Program's ID, in a map of QoreJavaApi with weak keys, which neither keeps the object
    alive nor the Program: a Program is looked up by its ID, among the Programs with jni data, when the origin is
    used.  An origin once recorded is kept; another one makes the object reach no Program.  A record that reaches no
    Program is recorded as such.

    @throw JavaException the Java call failed
*/
DLLLOCAL void jni_origin_record(Env& env, jobject obj, const JniJavaCallerRecord& rec);

//! Records the origin of a Java object used as a %Qore closure that %Qore code hands to Java (qore#7081)
/** Once sandboxed code has used Java in the process: the Program of the %Qore object \a o of the Java object, if that
    Program is sandboxed, whatever code hands it over.  The objects of other Programs are not recorded: Java objects
    that a host shares with sandboxed code are handed back to it, and must not become the sandboxed Program's.  Costs
    only a load of the latch when no sandboxed code has used Java.

    @throw JavaException the Java call failed
*/
DLLLOCAL void jni_origin_record_handed_over(Env& env, jobject obj, const QoreObject* o);

//! Returns true if an origin can have been recorded (once sandboxed code has used Java and recorded one)
DLLLOCAL bool jni_origins_recorded();

//! Finds the origin of the Java object \a obj (qore#7081)
/** @throw JavaException the Java call failed
*/
DLLLOCAL void jni_origin_get(Env& env, jobject obj, JniJavaOrigin& rv);

//! Makes Java threads whose Qore caller is not known reach no Program by a Program pointer from now on (qore#7023)
/** As once code that a sandbox governs has called Java in the process (see jni_governed_caller_reaches()); for the
    \c set-java-thread-policy module command
*/
DLLLOCAL void jni_set_java_thread_policy_strict();

//! Returns true if Java threads whose Qore caller is not known reach no Program by a Program pointer (qore#7023)
DLLLOCAL bool jni_java_thread_policy_strict();



//! Records the Program of the %Qore code that calls Java, for the duration of the call (qore#7023)
/** The calls from %Qore into Java that can call back into %Qore (a Java method, constructor or field of an object or
    a class, a Java functional interface called as a %Qore callable, and the functions and builtin methods of the
    module that run Java code; see design/java-sandbox-io.md for the list) record the Program of the calling code
    before they enter any other Program; jni_governed_caller_reaches() decides with it while the call runs.  The calls
    from Java into %Qore code (QoreJniStackLocationHelper, and the natives that run constructors and destructors)
    clear the record for the %Qore code that they run, which records its own Program when it calls Java in turn; a
    call from %Qore into Java that does not record its caller is then judged by the rule for no recorded call, never
    with the caller of an enclosing call.  The previous record is restored when the scope ends, also when an exception propagates, so nested
    and re-entrant calls (host code, a tenant callback, a callback of the host again) each have their own caller.

    The record is thread-local: a thread that Java starts has none.
*/
class JniJavaCallerScope {
public:
    //! Selects the clearing constructor
    enum ClearTag { Clear };

    //! Records \a pgm as the Program of the calling code, or, with \a deny, a record that reaches no Program
    DLLLOCAL explicit JniJavaCallerScope(QoreProgram* pgm, bool deny = false);

    //! Records \a rec
    DLLLOCAL explicit JniJavaCallerScope(const JniJavaCallerRecord& rec);

    //! Clears the record, for %Qore code that Java calls
    DLLLOCAL explicit JniJavaCallerScope(ClearTag);

    //! Restores the previous record
    DLLLOCAL ~JniJavaCallerScope();

    JniJavaCallerScope(const JniJavaCallerScope&) = delete;
    JniJavaCallerScope& operator=(const JniJavaCallerScope&) = delete;

    //! Returns the Program of the code that made the innermost builtin call: the Program that libqore records in the
    //! call's frame, for a Java method, static method or constructor and for the module's functions and builtin
    //! methods; neither the current Program nor the Program context of the call names it when the call has entered
    //! the Program of a class of another Program, or the object's Program
    /** For library code that a restricting sandbox governs, the Program of the code that the library code serves, or
        a record that reaches no Program if that is not known (see jni_calling_code_program(), qore#7394)

        @return a record of the Program (\c set is always true)
    */
    DLLLOCAL static JniJavaCallerRecord callSite();

    //! Returns the current Program, for the calls from Qore into Java that are not builtin calls (the member accesses
    //! of Java fields), which libqore makes in the Program of the calling code; a Java object called as a Qore
    //! closure records instead the owner that jni_java_caller_owner() gave it when it was created
    /** For library code that a restricting sandbox governs, the Program of the code that the library code serves, or
        a record that reaches no Program if that is not known, as for callSite() (qore#7394)

        @return a record of the Program (\c set is always true)
    */
    DLLLOCAL static JniJavaCallerRecord current();

private:
    //! Returns the Program that the frame of the innermost builtin call names (see callSite())
    DLLLOCAL static QoreProgram* callSiteFrame();

    //! the previous record
    JniJavaCallerRecord prev;
    //! the previous record of the code that supplies converted values (restored by a clearing scope)
    JniJavaCallerRecord prev_supplier;
    //! true for a clearing scope
    bool clearing = false;
};

} // namespace jni

namespace jni {
class Env;
template<typename T> class LocalReference;
}

//! Refuses a classpath to sandboxed code where its policy does not allow it (see jni-module.cpp)
/** @param classpath the classpath as the Java string that is then given to QoreURLClassLoader.addPath()

    @return the checked classpath to add with JniExternalProgramData::addClasspath(), or a null reference if no
    filesystem policy governs it, and for a \c global-* command

    @throw JNI-SANDBOX-ERROR sandboxed or restricted code changes the classpath of every Program
    @throw FILESYSTEM-ACCESS-DENIED the sandbox policy denies reading a classpath entry
*/
DLLLOCAL jni::LocalReference<jobject> jni_check_classpath_sandbox(const char* cmd, const QoreProgram* pgm,
        jni::Env& env, jstring classpath, bool global);

//! Checks a classpath against a filesystem policy, file by file, as the JVM uses them (see jni-module.cpp)
/** The classpath is resolved once (QoreSandbox.resolveClasspath()) into the paths that are checked and the files
    that are added, so that every file added is one that was checked (qore#6818): each entry is checked as the
    absolute path that the JVM resolves it to (against its \c user.dir), with symbolic links resolved, in the bytes
    that the JVM passes to the operating system for it; a wildcard entry is checked as its directory and as each file
    that it selects.  The jars and directories that the manifest \c Class-Path attributes of the classpath's jars
    name are checked too, recursively, each as the path that the class loader opens for it, before it is opened
    (qore#6822).  Any denial refuses the whole classpath, and nothing is added; the denial of a file that a wildcard
    selects or that a manifest names is raised as \c JNI-CLASSPATH-ERROR, naming the file.

    @param policy the policy to check the classpath against; nullptr: no policy, every entry is allowed
    @param env the JNI environment
    @param classpath the classpath as a Java string, with entries separated by the system path separator or \c ';'

    @return the checked classpath to add with JniExternalProgramData::addClasspath(), or a null reference if there
    is no policy

    @throw FILESYSTEM-ACCESS-DENIED the policy denies reading a classpath entry
    @throw JNI-CLASSPATH-ERROR the policy denies reading a file that a wildcard entry selects or that a manifest
    \c Class-Path attribute names, or a manifest \c Class-Path entry cannot be checked
    @throw JNI-SANDBOX-ERROR a path cannot be represented in the file name encoding of the JVM
*/
DLLLOCAL jni::LocalReference<jobject> jni_check_classpath_policy(QoreSandboxManager* policy, jni::Env& env,
        jstring classpath);

extern "C" DLLEXPORT int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import);
extern "C" DLLEXPORT QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm);

#endif // QORE_JNI_DEFS_H_
