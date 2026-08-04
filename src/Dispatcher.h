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

namespace jni {

class Dispatcher {

public:
    virtual ~Dispatcher() = default;

    virtual jobject dispatch(Env& env, jobject proxy, jobject method, jobjectArray args) = 0;

protected:
    Dispatcher() = default;

private:
    Dispatcher(const Dispatcher&) = delete;
    Dispatcher(Dispatcher&&) = delete;
    Dispatcher& operator=(const Dispatcher&) = delete;
    Dispatcher& operator=(Dispatcher&&) = delete;
};

/**
 * \brief An implementation of Dispatcher that delegates the dispatch() call to a Qore function.
 */
class QoreCodeDispatcher : public Dispatcher {

public:
    QoreCodeDispatcher(const ResolvedCallReferenceNode* callback);
    ~QoreCodeDispatcher();

    jobject dispatch(Env& env, jobject proxy, jobject method, jobjectArray args) override;

private:
    // the Program that owns the callback; set in the constructor, see the comment there for why this
    // cannot be the thread-current Program
    QoreProgram* pgm = nullptr;
    ResolvedCallReferenceNode* callback;
};

} // namespace jni

#endif // QORE_JNI_DISPATCHER_H_
