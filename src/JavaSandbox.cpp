//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2026 Qore Technologies, s.r.o.
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

#include "JavaSandbox.h"
#include "defs.h"
#include "Env.h"
#include "QoreToJava.h"

#include <qore/QoreNet.h>
#include <qore/QoreSandboxManager.h>

#include <cstring>
#include <new>
#include <optional>
#include <string>
#include <vector>
#include <unordered_set>

#ifdef _Q_WINDOWS
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#endif

namespace jni {

namespace {

// the access modes and protocols of org.qore.jni.QoreSandbox are the QSEC_* values of the Qore library
static_assert(QSEC_READ == (1 << 0) && QSEC_WRITE == (1 << 1) && QSEC_EXECUTE == (1 << 2)
    && QSEC_DELETE == (1 << 3) && QSEC_CREATE == (1 << 4), "QoreSandbox filesystem modes must match QSEC_*");
static_assert(QSEC_NET_TCP == (1 << 0) && QSEC_NET_UDP == (1 << 1), "QoreSandbox protocols must match QSEC_NET_*");

//! the error of the checks for invalid arguments and an unknown policy
constexpr const char* JNI_SANDBOX_ERROR = "JNI-SANDBOX-ERROR";

//! The policy that a native method of org.qore.jni.QoreSandbox applies
/** The calling Java thread is attached to Qore for the duration of the call if it is not a Qore thread, so that the
    Qore library can be used on it.  The policy is either a policy captured with QoreSandbox.capture() or the policy
    of the thread's Qore caller; a thread with neither cannot be checked.

    A captured policy is checked with QoreSandboxCapture::getPolicyManagerRef(), which does not depend on the thread,
    and the policy of the thread's own Qore code (none on a Java thread) applies as well, so that the stricter one
    wins.  The capture is also applied with QoreSandboxCaptureHelper, for the call budgets and interrupts of the
    capturing code, but an applied capture governs the thread only as its outermost caller: a sandboxed Program that
    the thread runs would decide instead, and inside a system-policy call no sandbox would (qore#5644).
*/
class SandboxCallScope {
public:
    //! Attaches the thread to Qore if necessary
    /** @throw UnableToRegisterException the thread cannot be attached
    */
    DLLLOCAL SandboxCallScope() {
        attacher.attach();
    }

    //! Uses the captured policy \a ptr, or the policy of the thread's Qore caller if \a ptr is 0
    /** @return 0 if the policy is known, -1 if not (JNI-SANDBOX-ERROR is raised)
    */
    DLLLOCAL int enter(jlong ptr, ExceptionSink* xsink) {
        assert(!sch && !captured_policy && !thread_policy);
        if (ptr) {
            const QoreSandboxCapture* capture = reinterpret_cast<const QoreSandboxCapture*>(ptr);
            // applied for its call budgets and interrupts: the check and its host name lookup run as part of the
            // capturing code's call; the policy itself is checked explicitly below
            sch.emplace(*capture);
            captured_policy = capture->getPolicyManagerRef();
            // the policy of the code that runs on the thread applies as well: a captured policy never lowers it
            thread_policy.emplace(QoreSandboxManagerHelper::Policy);
            return 0;
        }
        // a thread attached by this call is a Java thread with no Qore caller; a Qore thread has a caller if a
        // sandbox governs it or it runs the code of a Program
        if (!attacher) {
            QoreSandboxCapture current;
            if (current.isSandboxed() || qore_get_call_program_context()) {
                thread_policy.emplace(QoreSandboxManagerHelper::Policy);
                return 0;
            }
        }
        xsink->raiseException(JNI_SANDBOX_ERROR, "cannot determine the sandbox policy: the current thread has no "
            "Qore caller; capture the policy with QoreSandbox.capture() in the call from Qore that hands the work to "
            "this thread, and make the check with the QoreSandbox.Policy object or run the work with "
            "QoreSandbox.Policy.run()");
        return -1;
    }

    //! Returns the policy managers that a check must pass after enter(): none if no sandbox applies
    /** The captured policy, if any, and the policy of the code that runs on the thread
    */
    DLLLOCAL std::vector<QoreSandboxManager*> managers() {
        std::vector<QoreSandboxManager*> rv;
        if (captured_policy) {
            rv.push_back(*captured_policy);
        }
        if (thread_policy && *thread_policy) {
            rv.push_back(thread_policy->operator->());
        }
        return rv;
    }

private:
    //! attaches a Java thread to Qore for the call; destroyed last
    QoreThreadAttacher attacher;
    //! applies a captured policy for its call budgets and interrupts; released after the policies below
    std::optional<QoreSandboxCaptureHelper> sch;
    //! the policy manager of a captured policy (referenced), or nullptr
    ReferenceHolder<QoreSandboxManager> captured_policy{nullptr};
    //! the policy of the thread's Qore caller
    std::optional<QoreSandboxManagerHelper> thread_policy;
};

//! Throws a Java exception of the given class with a message, ignoring errors
static void throw_java(JNIEnv* jenv, const char* cls_name, const char* msg) {
    if (jenv->ExceptionCheck()) {
        return;
    }
    jclass cls = jenv->FindClass(cls_name);
    if (cls) {
        jenv->ThrowNew(cls, msg);
        jenv->DeleteLocalRef(cls);
    }
}

//! Runs the body of a native method of QoreSandbox in a SandboxCallScope
/** A Qore exception raised by \a f is thrown in Java as a QoreExceptionWrapper; C++ exceptions are converted to
    Java exceptions.

    @param f the body: <tt>T f(Env& env, SandboxCallScope& scope, ExceptionSink* xsink)</tt>

    @return the value returned by \a f, or \a error_rv if an exception is thrown in Java
*/
template <typename T, typename F>
static T sandbox_native(JNIEnv* jenv, T error_rv, F&& f) {
    Env env(jenv);
    try {
        SandboxCallScope scope;
        // the exception sink is destroyed before the scope, while the thread is still attached
        ExceptionSink xsink;
        try {
            T rv = f(env, scope, &xsink);
            if (!xsink) {
                return rv;
            }
        } catch (JavaException&) {
            // a Java exception is pending; it is thrown in Java
            xsink.clear();
            return error_rv;
        } catch (jni::Exception& e) {
            e.convert(&xsink);
        } catch (...) {
            // a C++ exception (ex: std::bad_alloc) after a Qore exception was raised: the Qore exception is dropped
            xsink.clear();
            throw;
        }
        try {
            QoreToJava::wrapException(env, xsink);
        } catch (...) {
            xsink.clear();
            throw;
        }
        return error_rv;
    } catch (JavaException&) {
        // a Java exception is pending; it is thrown in Java
    } catch (jni::Exception& e) {
        // the thread cannot be attached to Qore
        e.ignore();
        throw_java(jenv, "java/lang/IllegalStateException", "cannot attach the thread to Qore for a sandbox check");
    } catch (const std::bad_alloc& e) {
        throw_java(jenv, "java/lang/OutOfMemoryError", "cannot allocate memory for a sandbox check");
    } catch (const std::exception& e) {
        throw_java(jenv, "java/lang/RuntimeException", e.what());
    } catch (...) {
        throw_java(jenv, "java/lang/RuntimeException", "sandbox check failed");
    }
    return error_rv;
}

//! Returns the bytes of a Java byte array
static std::string get_bytes(Env& env, jbyteArray array) {
    assert(array);
    jsize len = env.getArrayLength(array);
    std::string rv(static_cast<size_t>(len), '\0');
    if (len) {
        env.getByteArrayRegion(array, 0, len, reinterpret_cast<jbyte*>(&rv[0]));
    }
    return rv;
}

//! Encodes an address for QoreSandbox.makeAddresses(): 4 bytes for IPv4, 16 bytes and the scope ID for IPv6
/** @return false if the address is not an IPv4 or IPv6 address
*/
static bool encode_address(const struct addrinfo& ai, std::string& rv) {
    if (ai.ai_family == AF_INET && ai.ai_addrlen >= sizeof(struct sockaddr_in)) {
        const struct sockaddr_in* sin = reinterpret_cast<const struct sockaddr_in*>(ai.ai_addr);
        rv.assign(reinterpret_cast<const char*>(&sin->sin_addr), 4);
        return true;
    }
    if (ai.ai_family == AF_INET6 && ai.ai_addrlen >= sizeof(struct sockaddr_in6)) {
        const struct sockaddr_in6* sin6 = reinterpret_cast<const struct sockaddr_in6*>(ai.ai_addr);
        rv.assign(reinterpret_cast<const char*>(&sin6->sin6_addr), 16);
        uint32_t scope = static_cast<uint32_t>(sin6->sin6_scope_id);
        for (int shift = 24; shift >= 0; shift -= 8) {
            rv.push_back(static_cast<char>((scope >> shift) & 0xff));
        }
        return true;
    }
    return false;
}

//! QoreSandbox.capture0(): captures the policy of the current thread, or copies the policy \a ptr
static jlong JNICALL qore_sandbox_capture(JNIEnv* jenv, jclass, jlong ptr) {
    return sandbox_native<jlong>(jenv, 0, [ptr](Env&, SandboxCallScope& scope, ExceptionSink* xsink) -> jlong {
        // a capture made inside Policy.run() is the run's policy, whatever the thread runs (qore#5644)
        if (ptr) {
            return reinterpret_cast<jlong>(new QoreSandboxCapture(*reinterpret_cast<const QoreSandboxCapture*>(ptr)));
        }
        if (scope.enter(ptr, xsink)) {
            return 0;
        }
        return reinterpret_cast<jlong>(new QoreSandboxCapture);
    });
}

//! QoreSandbox.release0(): releases a captured policy
static void JNICALL qore_sandbox_release(JNIEnv* jenv, jclass, jlong ptr) {
    sandbox_native<jboolean>(jenv, JNI_FALSE, [ptr](Env&, SandboxCallScope&, ExceptionSink*) -> jboolean {
        delete reinterpret_cast<QoreSandboxCapture*>(ptr);
        return JNI_TRUE;
    });
}

//! QoreSandbox.isSandboxed0(): returns true if the policy \a ptr, or of the current thread, is sandboxed
static jboolean JNICALL qore_sandbox_is_sandboxed(JNIEnv* jenv, jclass, jlong ptr) {
    return sandbox_native<jboolean>(jenv, JNI_FALSE,
            [ptr](Env&, SandboxCallScope& scope, ExceptionSink* xsink) -> jboolean {
        if (scope.enter(ptr, xsink)) {
            return JNI_FALSE;
        }
        return scope.managers().empty() ? JNI_FALSE : JNI_TRUE;
    });
}

//! QoreSandbox.checkNetworkAccess0(): checks a network destination and returns the checked addresses
static jobjectArray JNICALL qore_sandbox_check_network_access(JNIEnv* jenv, jclass, jlong ptr, jbyteArray jhost,
        jint port, jint proto) {
    return sandbox_native<jobjectArray>(jenv, nullptr,
            [=](Env& env, SandboxCallScope& scope, ExceptionSink* xsink) -> jobjectArray {
        if (scope.enter(ptr, xsink)) {
            return nullptr;
        }
        // QoreSandbox removes the brackets of an IPv6 address literal, so that the returned addresses carry the
        // address itself as their host name
        std::string host = get_bytes(env, jhost);
        if (host.empty()) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "the host must not be empty");
            return nullptr;
        }
        // port 0 is no destination, and the policy does not restrict it: a check for it would allow every port
        if (port < 1 || port > 65535) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "port %d is outside the valid range 1..65535", (int)port);
            return nullptr;
        }
        if (proto != QSEC_NET_TCP && proto != QSEC_NET_UDP) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "invalid protocol %d; expecting QoreSandbox.TCP or "
                "QoreSandbox.UDP", (int)proto);
            return nullptr;
        }
        const char* proto_name = proto == QSEC_NET_TCP ? "TCP" : "UDP";

        std::vector<QoreSandboxManager*> managers = scope.managers();
        // a host name that a policy denies whatever it resolves to is not looked up
        for (QoreSandboxManager* sm : managers) {
            if (!sm->network().checkHostname(host.c_str(), port, proto)) {
                xsink->raiseException("NETWORK-ACCESS-DENIED", "%s connection to '%s' port %d denied by the sandbox "
                    "network policy", proto_name, host.c_str(), (int)port);
                return nullptr;
            }
        }

        // the Qore resolver, so that the host name resolves as it does for Qore sockets
        QoreAddrInfo ai;
        std::string service = std::to_string(port);
        if (ai.getInfo(xsink, host.c_str(), service.c_str(), Q_AF_UNSPEC, 0,
                proto == QSEC_NET_TCP ? SOCK_STREAM : SOCK_DGRAM)) {
            return nullptr;
        }

        // every address is checked, with the host name so that allowed host patterns apply, because the caller can
        // connect to any of them
        std::vector<std::string> addrs;
        std::unordered_set<std::string> seen;
        std::string addr;
        unsigned count = 0;
        for (const struct addrinfo* p = ai.getAddrInfo(); p; p = p->ai_next) {
            if (!(++count % 100) && qore_check_cancel(xsink, "checking the addresses of a host for the sandbox")) {
                return nullptr;
            }
            if (!encode_address(*p, addr)) {
                continue;
            }
            for (QoreSandboxManager* sm : managers) {
                if (!sm->checkNetworkAccess(host.c_str(), p->ai_addr, p->ai_addrlen, proto, xsink)) {
                    return nullptr;
                }
            }
            // the addresses are returned once each, in the order of the resolver
            if (seen.insert(addr).second) {
                addrs.push_back(addr);
            }
        }
        if (addrs.empty()) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "host '%s' has no IPv4 or IPv6 address", host.c_str());
            return nullptr;
        }

        LocalReference<jclass> byte_array_class = env.findClass("[B");
        LocalReference<jobjectArray> rv = env.newObjectArray(static_cast<jsize>(addrs.size()), byte_array_class);
        for (size_t i = 0; i < addrs.size(); ++i) {
            const std::string& a = addrs[i];
            LocalReference<jbyteArray> bytes = env.newByteArray(static_cast<jsize>(a.size()));
            env.setByteArrayRegion(bytes, 0, static_cast<jsize>(a.size()), reinterpret_cast<const jbyte*>(a.data()));
            env.setObjectArrayElement(rv, static_cast<jsize>(i), bytes);
        }
        return rv.release();
    });
}

//! QoreSandbox.checkFileAccess0(): checks a path with the filesystem policy
static void JNICALL qore_sandbox_check_file_access(JNIEnv* jenv, jclass, jlong ptr, jbyteArray jpath, jint mode) {
    sandbox_native<jboolean>(jenv, JNI_FALSE,
            [=](Env& env, SandboxCallScope& scope, ExceptionSink* xsink) -> jboolean {
        if (scope.enter(ptr, xsink)) {
            return JNI_FALSE;
        }
        std::string path = get_bytes(env, jpath);
        if (path.empty()) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "the path must not be empty");
            return JNI_FALSE;
        }
        if (!mode || (mode & ~QSEC_ALL)) {
            xsink->raiseException(JNI_SANDBOX_ERROR, "invalid access mode %d; expecting a combination of "
                "QoreSandbox.READ, WRITE, EXECUTE, DELETE and CREATE", (int)mode);
            return JNI_FALSE;
        }
        for (QoreSandboxManager* sm : scope.managers()) {
            if (!sm->checkFilesystemAccess(path.c_str(), mode, xsink)) {
                return JNI_FALSE;
            }
        }
        return JNI_TRUE;
    });
}

//! Returns a new JNI-SANDBOX-ERROR exception as a QoreExceptionWrapper, without throwing it
static jobject new_error(JNIEnv* jenv, const char* msg) {
    return sandbox_native<jobject>(jenv, nullptr, [msg](Env& env, SandboxCallScope&, ExceptionSink*) -> jobject {
        ExceptionSink err;
        err.raiseException(JNI_SANDBOX_ERROR, "%s", msg);
        try {
            return QoreToJava::newExceptionWrapper(env, err).release();
        } catch (...) {
            err.clear();
            throw;
        }
    });
}

//! QoreSandbox.argumentError0(): returns the JNI-SANDBOX-ERROR exception for invalid arguments
static jobject JNICALL qore_sandbox_argument_error(JNIEnv* jenv, jclass, jstring jmsg) {
    std::string msg;
    if (jmsg) {
        Env env(jenv);
        try {
            Env::GetStringUtfChars chars(env, jmsg);
            msg = chars.c_str();
        } catch (JavaException&) {
            return nullptr;
        }
    }
    return new_error(jenv, msg.c_str());
}

//! QoreSandbox.closedError0(): returns the JNI-SANDBOX-ERROR exception for a closed policy
static jobject JNICALL qore_sandbox_closed_error(JNIEnv* jenv, jclass) {
    return new_error(jenv, "the sandbox policy has been closed");
}

JNINativeMethod qoreSandboxNativeMethods[] = {
    {const_cast<char*>("capture0"), const_cast<char*>("(J)J"), reinterpret_cast<void*>(qore_sandbox_capture)},
    {const_cast<char*>("release0"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(qore_sandbox_release)},
    {const_cast<char*>("isSandboxed0"), const_cast<char*>("(J)Z"),
        reinterpret_cast<void*>(qore_sandbox_is_sandboxed)},
    {const_cast<char*>("checkNetworkAccess0"), const_cast<char*>("(J[BII)[[B"),
        reinterpret_cast<void*>(qore_sandbox_check_network_access)},
    {const_cast<char*>("checkFileAccess0"), const_cast<char*>("(J[BI)V"),
        reinterpret_cast<void*>(qore_sandbox_check_file_access)},
    {const_cast<char*>("argumentError0"), const_cast<char*>("(Ljava/lang/String;)Ljava/lang/RuntimeException;"),
        reinterpret_cast<void*>(qore_sandbox_argument_error)},
    {const_cast<char*>("closedError0"), const_cast<char*>("()Ljava/lang/RuntimeException;"),
        reinterpret_cast<void*>(qore_sandbox_closed_error)},
};

} // namespace

void jni_register_qore_sandbox_natives(Env& env, jclass cls) {
    env.registerNatives(cls, qoreSandboxNativeMethods, sizeof(qoreSandboxNativeMethods) / sizeof(JNINativeMethod));
}

void jni_release_sandbox_policy(jlong ptr) {
    delete reinterpret_cast<QoreSandboxCapture*>(ptr);
}

} // namespace jni
