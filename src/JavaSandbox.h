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
#include <jni.h>

namespace jni {

class Env;

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
