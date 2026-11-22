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
/// \brief Defines the method invocation dispatcher.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_DISPATCHER_H_
#define QORE_JNI_DISPATCHER_H_

#include "Env.h"
#include "JniHandleRegistry.h"

namespace jni {

class Dispatcher {

public:
    virtual ~Dispatcher() = default;

    virtual jobject dispatch(Env& env, jobject proxy, jobject method, jobjectArray args) = 0;

    // Explicit destruction reports Qore cleanup errors to the caller. The
    // destructor also releases resources, but must discard errors during GC/shutdown.
    virtual void destroy(ExceptionSink& xsink) = 0;

protected:
    Dispatcher() = default;

private:
    Dispatcher(const Dispatcher&) = delete;
    Dispatcher(Dispatcher&&) = delete;
    Dispatcher& operator=(const Dispatcher&) = delete;
    Dispatcher& operator=(Dispatcher&&) = delete;
};

template <>
struct JniHandleTraits<Dispatcher> {
    static constexpr JniHandleKind kind = JniHandleKind::InvocationHandler;
};

//! Reports the errors of releasing a dispatcher to the caller if this thread drops the last reference to it
/** Whichever thread drops the last reference destroys the dispatcher.  While this object exists, a destruction of the
    given dispatcher on this thread reports to the given sink.  A destruction outside of this scope, when a call that
    was in progress holds the last reference, discards its errors.
*/
class DispatcherReleaseScope {
public:
    DLLLOCAL DispatcherReleaseScope(const Dispatcher* dispatcher, ExceptionSink& xsink) noexcept;
    DLLLOCAL ~DispatcherReleaseScope();

    //! Returns the sink for the destruction of the dispatcher on this thread, or nullptr
    DLLLOCAL static ExceptionSink* sinkFor(const Dispatcher* dispatcher) noexcept;

    DispatcherReleaseScope(const DispatcherReleaseScope&) = delete;
    DispatcherReleaseScope& operator=(const DispatcherReleaseScope&) = delete;

private:
    const Dispatcher* prev_dispatcher;
    ExceptionSink* prev_sink;
};

/**
 * \brief An implementation of Dispatcher that delegates the dispatch() call to a Qore function.
 */
class QoreCodeDispatcher : public Dispatcher {

public:
    QoreCodeDispatcher(const ResolvedCallReferenceNode* callback);
    ~QoreCodeDispatcher();

    void destroy(ExceptionSink& xsink) override;

    jobject dispatch(Env& env, jobject proxy, jobject method, jobjectArray args) override;

private:
    // the Program that owns the callback; set in the constructor, see the comment there for why this
    // cannot be the thread-current Program
    QoreProgram* pgm = nullptr;
    ResolvedCallReferenceNode* callback;
    // the sandbox of the code that created the dispatcher (the code that created the QoreInvocationHandler),
    // captured in the constructor: Java can call the proxy on any thread, including threads that Qore did not
    // start and that have no Qore caller, so the callback and its release run under this sandbox
    QoreSandboxCapture sandbox;
};

} // namespace jni

#endif // QORE_JNI_DISPATCHER_H_
