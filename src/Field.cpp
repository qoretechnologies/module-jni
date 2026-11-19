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

#include <memory>

#include "Field.h"
#include "Env.h"
#include "JavaToQore.h"
#include "QoreToJava.h"
#include "QoreJniClassMap.h"

namespace jni {

void BaseField::getName(QoreString& str) {
    Env env;
    LocalReference<jstring> fieldName = env.callObjectMethod(field, Globals::methodFieldGetName, nullptr).as<jstring>();
    Env::GetStringUtfChars fname(env, fieldName);
    // with its length: the JVM allows a NUL character in a field name (qore#6738)
    str.concat(fname.c_str(), fname.size());
}

QoreValue BaseField::get(jobject object, QoreProgram* pgm, bool compat_types) {
    Env env;
    if (!env.isInstanceOf(object, cls->getJavaObject())) {
        throw BasicException("Passed instance does not match the field's class");
    }
    switch (type) {
        case Type::Boolean:
            return JavaToQore::convert(env.getBooleanField(object, id));
        case Type::Byte:
            return JavaToQore::convert(env.getByteField(object, id));
        case Type::Char:
            return JavaToQore::convert(env.getCharField(object, id));
        case Type::Short:
            return JavaToQore::convert(env.getShortField(object, id));
        case Type::Int:
            return JavaToQore::convert(env.getIntField(object, id));
        case Type::Long:
            return JavaToQore::convert(env.getLongField(object, id));
        case Type::Float:
            return JavaToQore::convert(env.getFloatField(object, id));
        case Type::Double:
            return JavaToQore::convert(env.getDoubleField(object, id));
        case Type::Reference:
        default:
            assert(type == Type::Reference);
            {
                LocalReference<jobject> v = env.getObjectField(object, id);
                // qore#6551: converting the value can run its Java code (ex: entrySet() of a Map), which runs in
                // the context of the calling code; a primitive value runs no Java code
                JniPendingContextHelper pending_ctx;
                return JavaToQore::convertToQore(std::move(v), pgm, compat_types);
            }
    }
}

void BaseField::set(jobject object, const QoreValue& value, JniExternalProgramData* jpc) {
    Env env;
    if (!env.isInstanceOf(object, cls->getJavaObject())) {
        throw BasicException("Passed instance does not match the field's class");
    }
    switch (type) {
        case Type::Boolean:
            env.setBooleanField(object, id, QoreToJava::toBoolean(value));
            break;
        case Type::Byte:
            env.setByteField(object, id, QoreToJava::toByte(value));
            break;
        case Type::Char:
            env.setCharField(object, id, QoreToJava::toChar(value));
            break;
        case Type::Short:
            env.setShortField(object, id, QoreToJava::toShort(value));
            break;
        case Type::Int:
            env.setIntField(object, id, QoreToJava::toInt(value));
            break;
        case Type::Long:
            env.setLongField(object, id, QoreToJava::toLong(value));
            break;
        case Type::Float:
            env.setFloatField(object, id, QoreToJava::toFloat(value));
            break;
        case Type::Double:
            env.setDoubleField(object, id, QoreToJava::toDouble(value));
            break;
        case Type::Reference:
        default:
            assert(type == Type::Reference);
            env.setObjectField(object, id, QoreToJava::toObject(env, value, typeClass, jpc));
            break;
    }
}

QoreValue BaseField::getStatic(QoreProgram* pgm, bool compat_types) {
    Env env;
    switch (type) {
        case Type::Boolean:
            return JavaToQore::convert(env.getStaticBooleanField(cls->getJavaObject(), id));
        case Type::Byte:
            return JavaToQore::convert(env.getStaticByteField(cls->getJavaObject(), id));
        case Type::Char:
            return JavaToQore::convert(env.getStaticCharField(cls->getJavaObject(), id));
        case Type::Short:
            return JavaToQore::convert(env.getStaticShortField(cls->getJavaObject(), id));
        case Type::Int:
            return JavaToQore::convert(env.getStaticIntField(cls->getJavaObject(), id));
        case Type::Long:
            return JavaToQore::convert(env.getStaticLongField(cls->getJavaObject(), id));
        case Type::Float:
            return JavaToQore::convert(env.getStaticFloatField(cls->getJavaObject(), id));
        case Type::Double:
            return JavaToQore::convert(env.getStaticDoubleField(cls->getJavaObject(), id));
        case Type::Reference:
        default:
            assert(type == Type::Reference);
            {
                LocalReference<jobject> v = env.getStaticObjectField(cls->getJavaObject(), id);
                // qore#6551: converting the value can run its Java code, which runs in the context of the calling
                // code
                JniPendingContextHelper pending_ctx;
                return JavaToQore::convertToQore(std::move(v), pgm, compat_types);
            }
    }
}

void BaseField::setStatic(const QoreValue &value, JniExternalProgramData* jpc) {
    Env env;
    switch (type) {
        case Type::Boolean:
            env.setStaticBooleanField(cls->getJavaObject(), id, QoreToJava::toBoolean(value));
            break;
        case Type::Byte:
            env.setStaticByteField(cls->getJavaObject(), id, QoreToJava::toByte(value));
            break;
        case Type::Char:
            env.setStaticCharField(cls->getJavaObject(), id, QoreToJava::toChar(value));
            break;
        case Type::Short:
            env.setStaticShortField(cls->getJavaObject(), id, QoreToJava::toShort(value));
            break;
        case Type::Int:
            env.setStaticIntField(cls->getJavaObject(), id, QoreToJava::toInt(value));
            break;
        case Type::Long:
            env.setStaticLongField(cls->getJavaObject(), id, QoreToJava::toLong(value));
            break;
        case Type::Float:
            env.setStaticFloatField(cls->getJavaObject(), id, QoreToJava::toFloat(value));
            break;
        case Type::Double:
            env.setStaticDoubleField(cls->getJavaObject(), id, QoreToJava::toDouble(value));
            break;
        case Type::Reference:
        default:
            assert(type == Type::Reference);
            env.setStaticObjectField(cls->getJavaObject(), id, QoreToJava::toObject(env, value, typeClass, jpc));
            break;
    }
}

} // namespace jni
