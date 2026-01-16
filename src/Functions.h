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
/// \brief Defines the Functions class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_FUNCTIONS_H_
#define QORE_JNI_FUNCTIONS_H_

#include "Env.h"
#include "Array.h"
#include "Class.h"
#include "ModifiedUtf8String.h"
#include "InvocationHandler.h"
#include "Globals.h"
#include "Jvm.h"

namespace jni {

/**
 * \brief Implementation of the global functions declared in ql_jni.qpp.
 */
class Functions {

public:
    static QoreStringNode* getVersion() {
        Env env;
        jint v = env.getVersion();
        QoreStringNode *str = new QoreStringNode();
        str->sprintf("%d.%d", v >> 16, v & 0xFFFF);
        return str;
    }

    // 'name' must be in UTF-8 encoding
    static Class* loadClass(const char* name) {
        Env env;
        printd(LogLevel, "loadClass '%s'\n", name);
        return new Class(env.findClass(name));
    }

    // 'name' must be in UTF-8 encoding
    static Class* loadClass(Env& env, const char* name) {
        printd(LogLevel, "loadClass '%s'\n", name);
        return new Class(env.findClass(name));
    }

    static Class* loadClass(const QoreString& name) {
        ModifiedUtf8String nameUtf8(name);
        return loadClass(nameUtf8.c_str());
    }

    static LocalReference<jobject> implementInterface(jobject classLoader, const InvocationHandler *invocationHandler, jclass cls) {
        Env env;

        LocalReference<jobject> cl;
        LocalReference<jobjectArray> interfaces = env.newObjectArray(1, Globals::classClass);
        env.setObjectArrayElement(interfaces, 0, cls);

        jvalue args[3];
        if (classLoader == nullptr) {
            cl = env.callObjectMethod(cls, Globals::methodClassGetClassLoader, nullptr);
            args[0].l = cl;
        } else {
            args[0].l = classLoader;
        }
        args[1].l = interfaces;
        args[2].l = invocationHandler->getObject();
        return env.callStaticObjectMethod(Globals::classProxy, Globals::methodProxyNewProxyInstance, args);
    }

    static Array *newBooleanArray(int64 size) {
        Env env;
        return new Array(env.newBooleanArray(size));
    }

    static Array *newByteArray(int64 size) {
        Env env;
        return new Array(env.newByteArray(size));
    }

    static Array *newCharArray(int64 size) {
        Env env;
        return new Array(env.newCharArray(size));
    }

    static Array *newShortArray(int64 size) {
        Env env;
        return new Array(env.newShortArray(size));
    }

    static Array *newIntArray(int64 size) {
        Env env;
        return new Array(env.newIntArray(size));
    }

    static Array *newLongArray(int64 size) {
        Env env;
        return new Array(env.newLongArray(size));
    }

    static Array *newFloatArray(int64 size) {
        Env env;
        return new Array(env.newFloatArray(size));
    }

    static Array *newDoubleArray(int64 size) {
        Env env;
        return new Array(env.newDoubleArray(size));
    }

    static Array *newObjectArray(int64 size, const Class *cls) {
        Env env;
        return new Array(env.newObjectArray(size, cls->getJavaObject()));
    }

    static bool nativeAccessOptionEnabled() {
        return Jvm::nativeAccessOptionEnabled();
    }

private:
    Functions() = delete;
};

} // namespace jni

#endif // QORE_JNI_FUNCTIONS_H_
