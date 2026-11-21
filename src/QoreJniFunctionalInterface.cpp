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
#include "QoreJniClassMap.h"

#include <optional>

namespace jni {
QoreJniFunctionalInterface::QoreJniFunctionalInterface(jobject obj)
        : obj(GlobalReference<jobject>::fromLocal(obj)), src_pgm(qore_get_call_program_context()) {
    assert(src_pgm);
    // the callable is a callback of the code that obtained it from Java: the code that called Java, also when it
    // obtained it from a Java object of another Program, whose Program is the Program context of the call; in a call
    // from Java into Qore, the code that called that Java code, and otherwise none: the callable then reaches no
    // Program (qore#7023)
    owner = jni_java_caller_owner();
    Env env;

    cls = new Class(env.callObjectMethod(obj, Globals::methodObjectGetClass, nullptr).as<jclass>());
    LocalReference<jobjectArray> methods = cls->getDeclaredMethods();

    // find first "call" method in the class
    for (jsize i = 0, num_methods = env.getArrayLength(methods); i < num_methods; ++i) {
        // get Method object
        LocalReference<jobject> m = env.getObjectArrayElement(methods, i);
        LocalReference<jstring> mName = env.callObjectMethod(m, Globals::methodMethodGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars mcn(env, mName);
        // the whole name is compared, as the JVM allows a NUL character in a method name (qore#6738)
        if (mcn == "call") {
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
    // the closure can be called after its Program has been deleted (it is handed to other Programs), so the Program
    // is kept allocated while the closure exists; taken last, as the constructor does not release it if it throws
    if (src_pgm) {
        src_pgm->depRef();
    }
    // the owner is only compared with Program pointers: its memory is kept, so that no Program created later at its
    // address is taken for it
    if (owner.pgm) {
        owner.pgm->depRef();
    }
}

QoreJniFunctionalInterface::~QoreJniFunctionalInterface() {
    if (owner.pgm) {
        owner.pgm->depDeref();
    }
    if (src_pgm) {
        src_pgm->depDeref();
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

        // the closure runs in the Program that created it, which is entered first when it is not the current one,
        // as for a Java method of an object of another Program (see jni_enter_call_program()); its jni data is looked
        // up only then
        // the thread is attached to the JVM before the Program is entered: a thread's first attach sets its Java
        // context (the Program that Java's calls into Qore use, see QoreURLClassLoader.getProgramPtr()) to the
        // current Program, which must be the caller's
        Env env;
        QoreProgram* pgm = src_pgm;
        std::optional<QoreExternalProgramContextHelper> epch;
        if (pgm && pgm != ::getProgram() && jni_enter_call_program(epch, pgm, xsink)) {
            return QoreValue();
        }
        std::optional<QoreExternalProgramCallContextHelper> call_ctx;
        if (pgm) {
            // set call context
            call_ctx.emplace(pgm);
        }
        // sets the thread's Java context for the call, as Java methods do (qore#6485); a Program that has been deleted
        // or has no jni data: the call is made in the context of the calling code (jni_get_context_unconditional()),
        // or in the global context Program, which is held for the call (qore#6829)
        JniContextProgramHolder ctx_holder;
        JniExternalProgramData::setContext(env, pgm, ctx_holder);

        // make sure that the Qore exception stack is populated correctly in case a Qore exception is thrown
        // from the Java code about to be called below
        QoreJniStackLocationHelper slh;
        // Java's calls into Qore during the call are governed by the code that obtained the callable, as for a Qore
        // closure, which runs in its own Program; recorded after the location helper, which clears the record for
        // Qore code that Java calls: this is a call from Qore into Java (qore#7023)
        JniJavaCallerScope caller(owner);
        // make the call
        return method->isStatic()
            ? method->invokeStatic(*evaluated_args, pgm)
            : method->invoke(obj, *evaluated_args, pgm);
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