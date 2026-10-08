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
#include "Dispatcher.h"
#include "Array.h"
#include "Globals.h"
#include "Method.h"
#include "QoreToJava.h"

namespace jni {

QoreCodeDispatcher::QoreCodeDispatcher(const ResolvedCallReferenceNode *callback) : callback(callback->refRefSelf()) {
    // Bind to the Program that owns the callback, not the thread-current Program.
    //
    // The callback is executed later on a Java thread, and the Program established for that call
    // determines which Program's JNI class map is used to wrap Java objects passed as arguments.  It
    // must therefore be the Program that imported the Java classes the callback's code was compiled
    // against; binding to any other Program makes the class map build a second QoreClass for the same
    // Java class - and, because that Program's classloader normally cannot see the caller's dynamically
    // added JARs, a class whose dependent types silently degrade to "auto".  Such a class does not
    // compare equal to the one the callback's declared types refer to, so passing an argument to a
    // typed variable fails with a RUNTIME-TYPE-ERROR naming the same class on both sides.
    //
    // getProgram() is not usable here: the thread-current Program during construction is not
    // guaranteed to be the callback's Program (it is the JNI global Java-context Program in some
    // contexts). The Program dependency reference is taken while the callback is known to be alive;
    // callback->getProgram() must not be called later from dispatch(), where it can dangle if the
    // owning Program has been destroyed.
    pgm = this->callback->getProgram();
    if (!pgm) {
        // call references with no owning Program (ex: builtin function references) fall back to the
        // thread-current Program
        pgm = getProgram();
    }
    assert(pgm);
    // A closure can outlive a Program that has already been cleared. Taking a
    // strong reference then would resurrect its zero reference count without
    // restoring the dependency reference released by clear(). Pin the Program's
    // metadata with a dependency reference instead; execution context validation
    // still rejects callbacks into a closed Program.
    pgm->depRef();
    printd(LogLevel, "QoreCodeDispatcher::QoreCodeDispatcher(), this: %p pgm: %p\n", this, pgm);
}

class QoreThreadDetacher {
public:
    DLLLOCAL ~QoreThreadDetacher() {
        qoreThreadAttacher.detach();
    }
};

QoreCodeDispatcher::~QoreCodeDispatcher() {
    ExceptionSink xsink;
    destroy(xsink);
    xsink.clear();
}

void QoreCodeDispatcher::destroy(ExceptionSink& xsink) {
    if (!callback) {
        return;
    }
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception &e) {
        e.convert(&xsink);
        return;
    }

    printd(LogLevel, "QoreCodeDispatcher::destroy(), this: %p\n", this);
    callback->deref(&xsink);
    callback = nullptr;
    pgm->depDeref();
    pgm = nullptr;
}

jobject QoreCodeDispatcher::dispatch(Env& env, jobject proxy, jobject method, jobjectArray jargs) {
    if (q_libqore_shutdown() || Globals::isShuttingDown()) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "could not execute Qore callback; the "
            "Qore library or JNI module has been shut down");
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
        // Our dependency reference keeps the Program metadata alive. The context
        // helper below checks whether the Program still permits execution.
        // callback->getProgram() is unsafe: it can return a dangling pointer
        // when the callback's owning program has been destroyed.
        QoreProgram* pgm = this->pgm;
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

        ValueHolder qv(callback->execValue(*args, &xsink), &xsink);
        if (xsink) {
            QoreToJava::wrapException(xsink);
            return nullptr;
        }
        return QoreToJava::toObject(env, *qv, nullptr, jpc);
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
