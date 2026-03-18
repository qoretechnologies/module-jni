//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2023 Qore Technologies, s.r.o.
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
#include "Dispatcher.h"
#include "Array.h"
#include "Globals.h"
#include "Method.h"
#include "QoreToJava.h"

namespace jni {

QoreCodeDispatcher::QoreCodeDispatcher(const ResolvedCallReferenceNode *callback) : callback(callback->refRefSelf()) {
    pgm->ref();
    printd(LogLevel, "QoreCodeDispatcher::QoreCodeDispatcher(), this: %p\n", this);
}

class QoreThreadDetacher {
public:
    DLLLOCAL ~QoreThreadDetacher() {
        qoreThreadAttacher.detach();
    }
};

QoreCodeDispatcher::~QoreCodeDispatcher() {
    try {
        qoreThreadAttacher.attach();
    } catch (Exception &e) {
        printd(LogLevel, "~QoreCodeDispatcher() - unable to attach thread to Qore, this: %p", this);
        return;
    }
    QoreThreadDetacher qtd;

    printd(LogLevel, "QoreCodeDispatcher::~QoreCodeDispatcher(), this: %p\n", this);
    ExceptionSink xsink;
    callback->deref(&xsink);
    pgm->deref(&xsink);
    if (xsink) {
        QoreToJava::wrapException(xsink);
    }
}

jobject QoreCodeDispatcher::dispatch(Env& env, jobject proxy, jobject method, jobjectArray jargs) {
    if (q_libqore_shutdown()) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "could not execute Qore callback; the Qore library "
            "has already been shut down");
        return nullptr;
    }

    try {
        qoreThreadAttacher.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }
    QoreThreadDetacher qtd;

    QoreJniStackLocationHelper slh;

    printd(LogLevel, "QoreCodeDispatcher::dispatch(), this: %p pgm: %p\n", this, pgm);

    ExceptionSink xsink;
    try {
        // use the callback's program if it has JNI data; otherwise fall back to the
        // program captured at construction time (when the QoreInvocationHandler was
        // created) - this handles the case where the callback is a method reference
        // to a method in a module that doesn't have JNI external data, or where the
        // callback's program has already been destroyed
        QoreProgram* pgm = callback->getProgram();
        if (!pgm || !pgm->getExternalData("jni")) {
            pgm = this->pgm;
        }
        JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

        // Set up full program context including tlpd for thread pool threads
        QoreExternalProgramContextHelper pctx(&xsink, pgm);
        if (xsink) {
            QoreToJava::wrapException(xsink);
            return nullptr;
        }

        ReferenceHolder<QoreListNode> args(new QoreListNode(autoTypeInfo), &xsink);
        args->push(new QoreObject(QC_METHOD, pgm, new QoreJniPrivateData(method)), &xsink);
        if (jargs) {
            ReferenceHolder<> val(&xsink);
            Array::getList(val, env, jargs, env.getObjectClass(jargs), pgm);
            args->push(val.release(), &xsink);
        }

        QoreValue qv = callback->execValue(*args, &xsink);
        if (xsink) {
            QoreToJava::wrapException(xsink);
            return nullptr;
        }
        return QoreToJava::toObject(env, qv, nullptr, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(xsink);
        return nullptr;
    } catch (QoreStandardException& e) {
        ExceptionSink xsink;
        e.convert(&xsink);
        QoreString errstr;
        QoreStringValueHelper err(xsink.getExceptionErr());
        QoreStringValueHelper desc(xsink.getExceptionDesc());
        errstr.sprintf("failed to execute Qore callback: %s: %s", err->c_str(), desc->c_str());
        xsink.clear();
        env.throwNew(env.findClass("java/lang/RuntimeException"), errstr.c_str());
        return nullptr;
    }
}

} // namespace jni
