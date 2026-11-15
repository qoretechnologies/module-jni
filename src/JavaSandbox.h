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
///
/// \file
/// \brief The native methods of org.qore.jni.QoreSandbox: the sandbox policy of Qore code applied to Java I/O
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_JAVASANDBOX_H_
#define QORE_JNI_JAVASANDBOX_H_

#include <qore/Qore.h>
#include <qore/QoreSandboxManager.h>
#include <jni.h>

#include <string>
#include <vector>

namespace jni {

class Env;

//! A network destination to check against the sandbox policy
struct JniNetTarget {
    //! the host name or address literal, without IPv6 brackets
    std::string host;
    //! the port, 1 .. 65535
    int port = 0;
    //! the protocol: QSEC_NET_TCP or QSEC_NET_UDP
    int proto = QSEC_NET_TCP;
};

//! A network destination that passed the sandbox policy, with the addresses that were checked
struct JniCheckedTarget {
    //! the host name or address literal, as checked
    std::string host;
    //! the port
    int port = 0;
    //! the checked addresses, in the order of the resolver, once each: 4 bytes for IPv4, 16 bytes and the scope ID
    //! in 4 bytes (big-endian) for IPv6, as org.qore.jni.QoreSandbox decodes them
    std::vector<std::string> addrs;
};

//! Checks network destinations against sandbox policies, as Qore sockets are checked, and returns their addresses
/** The one rule of the module for Java I/O (org.qore.jni.QoreSandbox.checkNetworkAccess()) and for the destinations
    of restricted jdbc connections (qore#5786): for each destination, a host name that a policy denies whatever it
    resolves to is not looked up; it is resolved once with the %Qore resolver, and every address is checked, with the
    host name so that allowed host patterns apply, against every policy.

    @param managers the policies that every destination must pass; empty: no policy applies, and the destinations are
    only resolved
    @param targets the destinations
    @param out receives the checked destinations, in the order of \a targets
    @param xsink raises \c NETWORK-ACCESS-DENIED for a denied destination, \c JNI-SANDBOX-ERROR for an invalid one,
    and the resolver's error

    @return 0 if every destination passed, -1 if an exception was raised
*/
DLLLOCAL int jni_check_network_targets(const std::vector<QoreSandboxManager*>& managers,
    const std::vector<JniNetTarget>& targets, std::vector<JniCheckedTarget>& out, ExceptionSink* xsink);

//! Registers the native methods of org.qore.jni.QoreSandbox
/** @throw JavaException the methods cannot be registered
*/
DLLLOCAL void jni_register_qore_sandbox_natives(Env& env, jclass cls);

//! Releases a policy captured by org.qore.jni.QoreSandbox.capture(); the thread must be a Qore thread
/** Called by NativeCleanup for a QoreSandbox.Policy that was garbage-collected without being closed.
*/
DLLLOCAL void jni_release_sandbox_policy(jlong ptr);

} // namespace jni

#endif // QORE_JNI_JAVASANDBOX_H_
