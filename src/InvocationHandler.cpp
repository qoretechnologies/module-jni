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

#include "InvocationHandler.h"
#include "Globals.h"

namespace jni {

InvocationHandler::InvocationHandler(std::unique_ptr<Dispatcher> dispatcher) {
   Env env;
   jvalue arg;
   arg.j = reinterpret_cast<jlong>(dispatcher.get());
   LocalReference<jobject> obj = env.newObject(Globals::classQoreInvocationHandler, Globals::ctorQoreInvocationHandler, &arg);
   dispatcher.release();    // from now on, the Java instance of QoreInvocationHandler is responsible for the dispatcher
   jobj = obj.makeGlobal();
}

InvocationHandler::InvocationHandler(const ResolvedCallReferenceNode *callback)
      : InvocationHandler(std::unique_ptr<Dispatcher>(new QoreCodeDispatcher(callback))) {
}

void InvocationHandler::destroy() {
   Env env;
   env.callVoidMethod(jobj, Globals::methodQoreInvocationHandlerDestroy, nullptr);
}

} // namespace jni
