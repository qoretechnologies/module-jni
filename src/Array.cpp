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
#include "Array.h"
#include "JavaToQore.h"
#include "QoreToJava.h"

namespace jni {

Array::Array(jclass ecls, int size) {
    LocalReference<jclass> cls(ecls);
    if (size < 1) {
        QoreStringMaker desc("cannot instantiate an array with size %d; must be greater than 0", size);
        throw BasicException(desc.c_str());
    }

    elementClass = cls.makeGlobal();
    elementType = Globals::getType(elementClass);

    jobj = GlobalReference<jobject>::fromLocal(Array::getNew(elementType, elementClass, (jsize)size).as<jobject>());
}

Array::Array(jarray array) : QoreJniPrivateData(array) {
    printd(LogLevel, "Array::Array(), this: %p, object: %p\n", this, jobj.cast<jarray>());

    Env env;
    LocalReference<jclass> arrayClass = env.getObjectClass(array);
    elementClass = env.callObjectMethod(arrayClass, Globals::methodClassGetComponentType, nullptr).as<jclass>().makeGlobal();
    elementType = Globals::getType(elementClass);
}

int64 Array::length() const {
   Env env;
   return env.getArrayLength(jobj.cast<jarray>());
}

LocalReference<jarray> Array::getNew(Type elementType, jclass elementClass, jsize size) {
    Env env;

    switch (elementType) {
        case Type::Boolean: return env.newBooleanArray(size).as<jarray>();
        case Type::Byte: return env.newByteArray(size).as<jarray>();
        case Type::Char: return env.newCharArray(size).as<jarray>();
        case Type::Short: return env.newShortArray(size).as<jarray>();
        case Type::Int: return env.newIntArray(size).as<jarray>();
        case Type::Long: return env.newLongArray(size).as<jarray>();
        case Type::Float: return env.newFloatArray(size).as<jarray>();
        case Type::Double: return env.newDoubleArray(size).as<jarray>();
        case Type::Reference:
        default:
            assert(elementType == Type::Reference);
            return env.newObjectArray(size, elementClass).as<jarray>();
    }
}

SimpleRefHolder<BinaryNode> Array::getBinary(Env& env, jarray array) {
    SimpleRefHolder<BinaryNode> rv(new BinaryNode);

    jsize size = env.getArrayLength(array);
    rv->preallocate(size);

    for (jsize i = 0; i < size; ++i) {
        jbyte byte = env.getByteArrayElement(static_cast<jbyteArray>(array), i);
        reinterpret_cast<jbyte*>(const_cast<void*>(rv->getPtr()))[i] = byte;
    }
    return rv;
}

void Array::getList(ReferenceHolder<>& return_value, Env& env, jarray array, jclass arrayClass, QoreProgram* pgm,
        bool compat_types, bool varargs) {
    LocalReference<jclass> elementClass =
        env.callObjectMethod(arrayClass, Globals::methodClassGetComponentType, nullptr).as<jclass>();
    Type elementType = Globals::getType(elementClass);
    // issue #3026: return a binary object for byte[] unless jni_compat_types is set
    if (elementType == Type::Byte && !compat_types) {
        return_value = getBinary(env, array).release();
        return;
    }

    ExceptionSink xsink;
    ReferenceHolder<QoreListNode> l(new QoreListNode(autoTypeInfo), &xsink);

    jsize e = env.getArrayLength(array);
    bool fix_varargs = false;
    if (e > 0 && varargs) {
        fix_varargs = true;
    }

    for (jsize i = 0; i < e; ++i) {
        QoreValue v = get(env, array, elementType, elementClass, i, pgm, compat_types);
        if (fix_varargs && i == (e - 1) && v.getType() == NT_LIST) {
            ListIterator li(v.get<QoreListNode>());
            while (li.next()) {
                l->push(li.getReferencedValue(), nullptr);
            }
            v.discard(&xsink);
        } else {
            l->push(v, nullptr);
        }
    }

    return_value = l.release();
}

QoreValue Array::get(Env& env, jarray array, Type elementType, jclass elementClass, int64 index, QoreProgram* pgm,
        bool compat_types) {
    switch (elementType) {
        case Type::Boolean:
            return JavaToQore::convert(env.getBooleanArrayElement(static_cast<jbooleanArray>(array), index));
        case Type::Byte:
            return JavaToQore::convert(env.getByteArrayElement(static_cast<jbyteArray>(array), index));
        case Type::Char:
            return JavaToQore::convert(env.getCharArrayElement(static_cast<jcharArray>(array), index));
        case Type::Short:
            return JavaToQore::convert(env.getShortArrayElement(static_cast<jshortArray>(array), index));
        case Type::Int:
            return JavaToQore::convert(env.getIntArrayElement(static_cast<jintArray>(array), index));
        case Type::Long:
            return JavaToQore::convert(env.getLongArrayElement(static_cast<jlongArray>(array), index));
        case Type::Float:
            return JavaToQore::convert(env.getFloatArrayElement(static_cast<jfloatArray>(array), index));
        case Type::Double:
            return JavaToQore::convert(env.getDoubleArrayElement(static_cast<jdoubleArray>(array), index));
        case Type::Reference:
        default:
            assert(elementType == Type::Reference);
            return JavaToQore::convertToQore(env.getObjectArrayElement(static_cast<jobjectArray>(array), index), pgm,
                compat_types);
    }
}

QoreValue Array::get(int64 index, QoreProgram* pgm, bool compat_types) const {
    Env env;
    return get(env, jobj.cast<jarray>(), elementType, elementClass, index, pgm, compat_types);
}

void Array::set(int64 index, const QoreValue &value, JniExternalProgramData* jpc) {
    set(jobj.cast<jarray>(), elementType, elementClass, index, value, jpc);
}

QoreStringNodeHolder Array::deepToString() const {
    Env env;
    return deepToString(env, jobj.cast<jarray>());
}

QoreStringNodeHolder Array::deepToString(Env& env, jarray array) {
    jvalue jarg;
    jarg.l = array;
    LocalReference<jstring> str = env.callStaticObjectMethod(Globals::classArrays, Globals::methodArraysDeepToString, &jarg).as<jstring>();

    Env::GetStringUtfChars chars(env, str);
    return QoreStringNodeHolder(new QoreStringNode(chars.c_str(), QCS_UTF8));
}

void Array::set(jarray array, Type elementType, jclass elementClass, int64 index, const QoreValue &value,
        JniExternalProgramData* jpc) {
    Env env;
    switch (elementType) {
        case Type::Boolean:
            env.setBooleanArrayElement((jbooleanArray)array, index, QoreToJava::toBoolean(value));
            break;
        case Type::Byte:
            env.setByteArrayElement((jbyteArray)array, index, QoreToJava::toByte(value));
            break;
        case Type::Char:
            env.setCharArrayElement((jcharArray)array, index, QoreToJava::toChar(value));
            break;
        case Type::Short:
            env.setShortArrayElement((jshortArray)array, index, QoreToJava::toShort(value));
            break;
        case Type::Int:
            env.setIntArrayElement((jintArray)array, index, QoreToJava::toInt(value));
            break;
        case Type::Long:
            env.setLongArrayElement((jlongArray)array, index, QoreToJava::toLong(value));
            break;
        case Type::Float:
            env.setFloatArrayElement((jfloatArray)array, index, QoreToJava::toFloat(value));
            break;
        case Type::Double:
            env.setDoubleArrayElement((jdoubleArray)array, index, QoreToJava::toDouble(value));
            break;
        case Type::Reference:
        default:
            assert(elementType == Type::Reference);
            env.setObjectArrayElement((jobjectArray)array, index, QoreToJava::toObject(env, value, elementClass,
                jpc));
    }
}

LocalReference<jclass> Array::getClassForValue(QoreValue v, JniExternalProgramData* jpc) {
    // the element of a list may be a weak (":=") or opaque ("@=") reference; the Java class has
    // to be chosen for what it refers to, exactly as QoreToJava::toAnyObject() converts it
    v = v.resolveIndirect();
    switch (v.getType()) {
        case NT_INT: return Globals::classLong.toLocal();
        case NT_FLOAT: return Globals::classDouble.toLocal();
        case NT_BOOLEAN: return Globals::classBoolean.toLocal();
        case NT_STRING: return Globals::classString.toLocal();
        case NT_DATE: return Globals::classObject.toLocal();
        case NT_NUMBER: return Globals::classBigDecimal.toLocal();
        case NT_HASH: return Globals::classHash.toLocal();
        case NT_LIST: return Globals::classObject.toLocal();
        case NT_OBJECT: {
            Env env;
            if (jpc) {
                return jpc->getJavaClassForQoreClass(env, v.get<QoreObject>()->getClass());
            }

            QoreObject* o = v.get<QoreObject>();
            ExceptionSink xsink;
            SimpleRefHolder<QoreJniPrivateData> obj(static_cast<QoreJniPrivateData*>(o->getReferencedPrivateData(CID_OBJECT, &xsink)));
            if (!obj) {
                if (xsink) {
                    throw XsinkException(xsink);
                }
                return Globals::classQoreObject.toLocal();
            }
            return env.getObjectClass(obj->getObject());
        }
    }
    QoreStringMaker desc("cannot create a Java array from a list that contains values of type '%s'", v.getTypeName());
    throw BasicException(desc.c_str());
}

LocalReference<jarray> Array::toObjectArray(const QoreListNode* l, jclass elementClass, size_t start,
        JniExternalProgramData* jpc) {
    assert(start <= l->size());
    Type elementType = Globals::getType(elementClass);

    LocalReference<jarray> jarray = getNew(elementType, elementClass, l->size() - start);
    for (unsigned i = start, e = l->size(); i != e; ++i) {
        set(jarray, elementType, elementClass, i - start, l->retrieveEntry(i), jpc);
    }

    return jarray.release();
}

LocalReference<jarray> Array::toJava(const QoreListNode* l, size_t start, JniExternalProgramData* jpc) {
    if (l->size() <= start) {
        // An empty list has no element type to infer, but must remain distinct from null.
        return getNew(Type::Reference, Globals::classObject, 0);
    }

    LocalReference<jclass> elementClass = nullptr;
    // to determine the common type, we need a typeInfo object
    // because each local reference is a separate ptr
    const QoreTypeInfo* typeInfo = nullptr;

    // check list to see if we have a unique type
    for (unsigned i = start, e = l->size(); i != e; ++i) {
        // get this element's target Java class
        QoreValue v = l->retrieveEntry(i);
        if (v.isNullOrNothing()) {
            continue;
        }

        if (!elementClass) {
            elementClass = getClassForValue(v, jpc);
            if (!elementClass) {
                typeInfo = autoTypeInfo;
            } else {
                typeInfo = v.getTypeInfo();
            }
        } else {
            const QoreTypeInfo* newTypeInfo = v.getTypeInfo();
            if (newTypeInfo != typeInfo) {
                elementClass = Globals::classObject.toLocal();
                break;
            }
        }
    }

    if (!elementClass) {
        elementClass = Globals::classObject.toLocal();
    }

    return toObjectArray(l, elementClass, start, jpc).release();
}

void Array::getArgList(ReferenceHolder<QoreListNode>& return_value, Env& env, jarray array, QoreProgram* pgm, bool varargs) {
    LocalReference<jclass> arrayClass = env.getObjectClass(array);
    ReferenceHolder<> list(nullptr);
    getList(list, env, array, arrayClass, pgm, false, varargs);
    return_value = reinterpret_cast<QoreListNode*>(list.release());
}

} // namespace jni
