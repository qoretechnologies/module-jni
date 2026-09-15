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
/// \brief Defines the QoreToJava class
//------------------------------------------------------------------------------

#ifndef QORE_JNI_QORETOJAVA_H_
#define QORE_JNI_QORETOJAVA_H_

#include <qore/Qore.h>
#include <jni.h>

#include "Array.h"
#include "LocalReference.h"
#include "Object.h"
#include "defs.h"
#include "ModifiedUtf8String.h"
#include "QoreJniClassMap.h"
#include "Env.h"

namespace jni {

/**
 * \brief Provides functions for converting Qore values to Java.
 *
 * Although the majority of the methods is trivial, their purpose is to have a single point where a given conversion
 * is performed. This will allow to change the conversion (e.g. add range checking) in the future consistently.
 */
class QoreToJava {
public:
    static jboolean toBoolean(const QoreValue& value) {
        return value.getAsBool() ? JNI_TRUE : JNI_FALSE;
    }

    static jbyte toByte(const QoreValue& value) {
        return static_cast<jbyte>(value.getAsBigInt());
    }

    static jchar toChar(const QoreValue& value) {
        return static_cast<jchar>(value.getAsBigInt());
    }

    static jdouble toDouble(const QoreValue& value) {
        return static_cast<jdouble>(value.getAsFloat());
    }

    static jfloat toFloat(const QoreValue& value) {
        return static_cast<jfloat>(value.getAsFloat());
    }

    static jint toInt(const QoreValue& value) {
        return static_cast<jint>(value.getAsBigInt());
    }

    static jlong toLong(const QoreValue& value) {
        return static_cast<jlong>(value.getAsBigInt());
    }

    static jshort toShort(const QoreValue& value) {
        return static_cast<jshort>(value.getAsBigInt());
    }

    static jobject toObject(Env& env, const QoreValue& value, jclass cls, JniExternalProgramData* jpc = nullptr);

    static jobject toAnyObject(Env& env, const QoreValue& value, JniExternalProgramData* jpc = nullptr);

    static jobject makeMap(const QoreHashNode& h, jclass cls, JniExternalProgramData* jpc = nullptr);

    //! Returns a Java collection holding the list's elements, for a target class that is not an array
    static jobject makeList(const QoreListNode& l, jclass cls, JniExternalProgramData* jpc = nullptr);

    static jbyteArray makeByteArray(Env& env, const BinaryNode& b);

    static jobject makeBigDecimal(Env& env, const QoreNumberNode& n);

    static void wrapException(ExceptionSink& src) {
        Env env;
        wrapException(env, src);
    }

    static void wrapException(Env& env, ExceptionSink& src) {
        QoreValue n = src.getExceptionArg();

        if (n.getType() == NT_OBJECT) {
            const QoreObject* o = n.get<const QoreObject>();
            if (o->getClass(CID_THROWABLE) != nullptr) {
                ExceptionSink tempSink;
                SimpleRefHolder<QoreJniPrivateData> obj(
                    static_cast<QoreJniPrivateData*>(o->getReferencedPrivateData(CID_THROWABLE, &tempSink))
                );
                if (!tempSink) {
                    env.throwException(static_cast<jthrowable>(obj->getObject()));
                    src.clear();
                    return;
                }
            }
        }

        std::unique_ptr<ExceptionSink> xsink = std::unique_ptr<ExceptionSink>(new ExceptionSink());
        xsink->assimilate(src);
        xsink->markExternallyManaged();

        jvalue arg;
        arg.j = reinterpret_cast<jlong>(xsink.get());
        LocalReference<jobject> obj = env.newObject(Globals::classQoreExceptionWrapper,
            Globals::ctorQoreExceptionWrapper, &arg);
        xsink.release();
        // from now on, the Java instance of QoreExceptionWrapper is responsible for the exception sink
        env.throwException(obj.as<jthrowable>());
    }

private:
    QoreToJava() = delete;
};

} // namespace jni

#endif // QORE_JNI_QORETOJAVA_H_
