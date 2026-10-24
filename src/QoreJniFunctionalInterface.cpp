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

#include <qore/QoreSandboxManager.h>
#include "QoreJniFunctionalInterface.h"
#include "QoreToJava.h"

namespace jni {
QoreJniFunctionalInterface::QoreJniFunctionalInterface(jobject obj) : obj(GlobalReference<jobject>::fromLocal(obj)),
        src_pgm(qore_get_call_program_context()) {
    assert(src_pgm);
    Env env;

    cls = new Class(env.callObjectMethod(obj, Globals::methodObjectGetClass, nullptr).as<jclass>());
    LocalReference<jobjectArray> methods = cls->getDeclaredMethods();

    // find first "call" method in the class
    for (jsize i = 0, num_methods = env.getArrayLength(methods); i < num_methods; ++i) {
        // get Method object
        LocalReference<jobject> m = env.getObjectArrayElement(methods, i);
        LocalReference<jstring> mName = env.callObjectMethod(m, Globals::methodMethodGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars mcn(env, mName);
        if (!strcmp(mcn.c_str(), "call")) {
            method = new BaseMethod(m, *cls);
            break;
        }
    }

    if (!method) {
        LocalReference<jstring> clsName = env.callObjectMethod(cls->getJavaObject(), Globals::methodClassGetName,
            nullptr).as<jstring>();
        Env::GetStringUtfChars cName(env, clsName);

        QoreStringMaker str("Java class '%s' for Java object to be used as a Qore closure has no call() method to " \
            "be used as a Qore closure", cName.c_str());
        throw BasicException(str.c_str());
    }
}

QoreValue QoreJniFunctionalInterface::execValue(const QoreListNode* args, ExceptionSink* xsink) const {
    QoreSandboxProvenanceHelper provenance(this);
    try {
        ReferenceHolder<QoreListNode> evaluated_args(args ? args->evalList(xsink) : nullptr, xsink);
        if (*xsink) {
            return QoreValue();
        }
        //printd(5, "QoreJniFunctionalInterface::execValue() args: %p (%lld) evaluated_args: %p (%lld)\n", args,
        //    args ? args->size() : 0l, *evaluated_args, evaluated_args ? evaluated_args->size() : 0l);

        // set call context
        QoreExternalProgramCallContextHelper call_ctx(src_pgm);

        // make sure that the Qore exception stack is populated correctly in case a Qore exception is thrown
        // from the Java code about to be called below
        QoreJniStackLocationHelper slh;
        // make the call
        return method->isStatic()
            ? method->invokeStatic(*evaluated_args, src_pgm)
            : method->invoke(obj, *evaluated_args, src_pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

int QoreJniFunctionalInterface::getAsString(QoreString& str, int foff, ExceptionSink* xsink) const {
    str.concat("java closure reference");
    return 0;
}

QoreString* QoreJniFunctionalInterface::getAsString(bool& del, int foff, ExceptionSink* xsink) const {
    del = true;
    return new QoreString("java closure reference");
}
}