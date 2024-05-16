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

#include "QoreToJava.h"

namespace jni {

static jstring jni_string_to_jstring(const QoreStringNode& qstr) {
    ModifiedUtf8String str(qstr);
    Env env;
    return env.newString(str.c_str()).release();
}

static jobject jni_date_to_jobject(const DateTimeNode& qdate) {
    Env env;

    if (qdate.isAbsolute()) {
        QoreString str;
        qdate.format(str, "YYYY-MM-DDTHH:mm:SS.xxZ");

        LocalReference<jstring> date_str = env.newString(str.c_str());
        std::vector<jvalue> jargs(1);
        jargs[0].l = date_str;
        return env.callStaticObjectMethod(Globals::classZonedDateTime, Globals::methodZonedDateTimeParse, &jargs[0]).release();
    }

    // return QoreRelativeTime object
    qore_tm info;
    qdate.getInfo(info);
    std::vector<jvalue> jargs(7);
    jargs[0].i = info.year;
    jargs[1].i = info.month;
    jargs[2].i = info.day;
    jargs[3].i = info.hour;
    jargs[4].i = info.minute;
    jargs[5].i = info.second;
    jargs[6].i = info.us;

    return env.newObject(Globals::classQoreRelativeTime, Globals::ctorQoreRelativeTime, &jargs[0]).release();
}

jobject QoreToJava::toAnyObject(Env& env, const QoreValue& value, JniExternalProgramData* jpc) {
    switch (value.getType()) {
        case NT_BOOLEAN: {
            jvalue arg;
            arg.z = value.getAsBool();
            return env.newObject(Globals::classBoolean, Globals::ctorBoolean, &arg).release();
        }
        case NT_INT: {
            jvalue arg;
            int64 v = value.getAsBigInt();
            arg.j = v;
            return env.newObject(Globals::classLong, Globals::ctorLong, &arg).release();
        }
        case NT_FLOAT: {
            jvalue arg;
            arg.d = value.getAsFloat();
            return env.newObject(Globals::classDouble, Globals::ctorDouble, &arg).release();
        }
        case NT_STRING: {
            return jni_string_to_jstring(*value.get<QoreStringNode>());
        }
        case NT_DATE: {
            return jni_date_to_jobject(*value.get<const DateTimeNode>());
        }
        case NT_NUMBER: {
            return makeBigDecimal(env, *value.get<const QoreNumberNode>());
        }
        case NT_OBJECT: {
            QoreObject* o = const_cast<QoreObject*>(value.get<const QoreObject>());
            if (jpc) {
                return jpc->getJavaObject(o).release();
            }

            jobject javaObjectRef = qjcm.getJavaObject(o);
            if (javaObjectRef) {
                return javaObjectRef;
            }
            return nullptr;
        }
        case NT_RUNTIME_CLOSURE:
        case NT_FUNCREF: {
            const ResolvedCallReferenceNode* call = value.get<const ResolvedCallReferenceNode>();
            return qjcm.getJavaClosure(call);
        }
        case NT_HASH: {
            return makeMap(*value.get<QoreHashNode>(), Globals::classHash, jpc);
        }
        case NT_BINARY: {
            return makeByteArray(env, *value.get<BinaryNode>());
        }

        case NT_NOTHING:
        case NT_NULL:
            return nullptr;
        case NT_LIST:
            return Array::toJava(value.get<QoreListNode>(), 0, jpc).release();
    }
    QoreStringMaker desc("XX(%d) don't know how to convert a value of type '%s' to a Java object (expecting " \
        "'java.lang.Object')", value.getType(), value.getFullTypeName());
    throw BasicException(desc.c_str());
}

jobject QoreToJava::toObject(Env& env, const QoreValue& value, jclass cls, JniExternalProgramData* jpc) {
    if (value.isNullOrNothing())
        return nullptr;

    if (cls) {
        if (env.isSameObject(cls, Globals::classObject)) {
            return toAnyObject(env, value, jpc);
        }

        switch (value.getType()) {
            // check compatible primitive types
            case NT_BOOLEAN: {
                if (env.isSameObject(cls, Globals::classBoolean)
                    || env.isSameObject(cls, Globals::classPrimitiveBoolean)) {
                    return toAnyObject(env, value, jpc);
                }
                break;
            }

            case NT_INT: {
                if (env.isSameObject(cls, Globals::classLong) || env.isSameObject(cls, Globals::classPrimitiveLong)) {
                    jvalue arg;
                    int64 v = value.getAsBigInt();
                    arg.j = (jlong)v;
                    return env.newObject(Globals::classLong, Globals::ctorLong, &arg).release();
                }
                if (env.isSameObject(cls, Globals::classInteger) || env.isSameObject(cls, Globals::classPrimitiveInt)) {
                    jvalue arg;
                    int64 v = value.getAsBigInt();
                    arg.i = (jint)v;
                    return env.newObject(Globals::classInteger, Globals::ctorInteger, &arg).release();
                }
                if (env.isSameObject(cls, Globals::classShort) || env.isSameObject(cls, Globals::classPrimitiveShort)) {
                    jvalue arg;
                    int64 v = value.getAsBigInt();
                    arg.s = (jshort)v;
                    return env.newObject(Globals::classShort, Globals::ctorShort, &arg).release();
                }
                if (env.isSameObject(cls, Globals::classCharacter) || env.isSameObject(cls, Globals::classPrimitiveChar)) {
                    jvalue arg;
                    int64 v = value.getAsBigInt();
                    arg.s = (jchar)v;
                    return env.newObject(Globals::classCharacter, Globals::ctorCharacter, &arg).release();
                }
                if (env.isSameObject(cls, Globals::classByte) || env.isSameObject(cls, Globals::classPrimitiveByte)) {
                    jvalue arg;
                    int64 v = value.getAsBigInt();
                    arg.s = (jbyte)v;
                    return env.newObject(Globals::classByte, Globals::ctorByte, &arg).release();
                }
                break;
            }

            case NT_FLOAT: {
                if (env.isSameObject(cls, Globals::classDouble)
                    || env.isSameObject(cls, Globals::classPrimitiveDouble)) {
                    jvalue arg;
                    double v = value.getAsFloat();
                    arg.d = v;
                    return env.newObject(Globals::classDouble, Globals::ctorDouble, &arg).release();
                }
                if (env.isSameObject(cls, Globals::classFloat)
                    || env.isSameObject(cls, Globals::classPrimitiveFloat)) {
                    jvalue arg;
                    double v = value.getAsFloat();
                    arg.f = (float)v;
                    return env.newObject(Globals::classFloat, Globals::ctorFloat, &arg).release();
                }
                break;
            }
        }
    }

    LocalReference<jobject> javaObjectRef;
    switch (value.getType()) {
        case NT_STRING: {
            javaObjectRef = jni_string_to_jstring(*value.get<QoreStringNode>());
            break;
        }
        case NT_LIST: {
            javaObjectRef = static_cast<jobject>(qjcm.getJavaArray(value.get<QoreListNode>(), cls));
            break;
        }
        case NT_DATE: {
            javaObjectRef = jni_date_to_jobject(*value.get<const DateTimeNode>());
            break;
        }
        case NT_NUMBER: {
            javaObjectRef = makeBigDecimal(env, *value.get<const QoreNumberNode>());
            break;
        }
        case NT_HASH: {
            return makeMap(*value.get<QoreHashNode>(), cls, jpc);
        }
        case NT_OBJECT: {
            const QoreObject* o = value.get<QoreObject>();

            if (jpc) {
                javaObjectRef = jpc->getJavaObject(o);
            } else {
                javaObjectRef = qjcm.getJavaObject(o);
            }
            if (!javaObjectRef) {
                ExceptionSink xsink;
                TryPrivateDataRefHolder<ObjectBase> jo(o, CID_JAVAARRAY, &xsink);
                if (!jo) {
                    if (xsink)
                        throw XsinkException(xsink);

                    if (cls) {
                        LocalReference<jstring> clsName = env.callObjectMethod(cls,
                            Globals::methodClassGetCanonicalName, nullptr).as<jstring>();
                        Env::GetStringUtfChars cname(env, clsName);
                        QoreStringMaker desc("A Java object argument of class '%s' expected; got object of class " \
                            "'%s' instead", cname.c_str(), o->getClassName());
                        throw BasicException(desc.c_str());
                    }

                    QoreStringMaker desc("A Java object argument expected; got object of class '%s' instead",
                        o->getClassName());
                    throw BasicException(desc.c_str());
                }
                javaObjectRef = jo->makeLocal();
            }
            break;
        }
        case NT_RUNTIME_CLOSURE:
        case NT_FUNCREF: {
            const ResolvedCallReferenceNode* call = value.get<const ResolvedCallReferenceNode>();
            javaObjectRef = qjcm.getJavaClosure(call);
            break;
        }
        case NT_BINARY: {
            return makeByteArray(env, *value.get<BinaryNode>());
        }
        default: {
            if (cls) {
                LocalReference<jstring> clsName = env.callObjectMethod(cls, Globals::methodClassGetCanonicalName,
                    nullptr).as<jstring>();
                Env::GetStringUtfChars cname(env, clsName);
                QoreStringMaker desc("A Java object argument of class '%s' expected; got type '%s' instead",
                    cname.c_str(), value.getTypeName());
                throw BasicException(desc.c_str());
            }

            // convert primitive types to java objects if possible
            return toAnyObject(env, value, jpc);
        }
    }

    if (cls) {
        if (!env.isInstanceOf(javaObjectRef, cls)) {
            LocalReference<jstring> clsName =
                env.callObjectMethod(cls, Globals::methodClassGetCanonicalName, nullptr).as<jstring>();
            Env::GetStringUtfChars cname(env, clsName);

            LocalReference<jclass> ocls = env.getObjectClass(javaObjectRef);

            LocalReference<jstring> oclsName =
                env.callObjectMethod(ocls, Globals::methodClassGetCanonicalName, nullptr).as<jstring>();
            Env::GetStringUtfChars ocname(env, oclsName);

            if (!strcmp(cname.c_str(), ocname.c_str())) {
                QoreStringMaker str("ClassLoader error; expected a Java object of class '%s'; object has same "
                    "class name but a different classloader and is therefore incompatible",
                    cname.c_str(), ocname.c_str());
                throw BasicException(str.c_str());
            }

            QoreStringMaker str("expected class '%s'; instead got an object of class '%s'", cname.c_str(),
                ocname.c_str());
            throw BasicException(str.c_str());
        }
    }
    return javaObjectRef.release();
}

jobject QoreToJava::makeMap(const QoreHashNode& h, jclass cls, JniExternalProgramData* jpc) {
    Env env;

    // get constructor for class
    jmethodID ctor;
    jmethodID put;
    try {
        ctor = env.getMethod(cls, "<init>", "()V");
        put = env.getMethod(cls, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    } catch (jni::Exception& e) {
        e.ignore();
        // try Hash
        cls = Globals::classHash;
        ctor = Globals::ctorHash;
        put = Globals::methodHashPut;
    }

    LocalReference<jobject> hm = env.newObject(cls, ctor, nullptr);

    ConstHashIterator i(h);
    while (i.next()) {
        LocalReference<jstring> key = env.newString(i.getKey());
        QoreValue v(i.get());
        LocalReference<jobject> value = toAnyObject(env, v, jpc);

        std::vector<jvalue> jargs(2);
        jargs[0].l = key;
        jargs[1].l = value;

        env.callObjectMethod(hm, put, &jargs[0]);
    }

    return hm.release();
}

jbyteArray QoreToJava::makeByteArray(Env& env, const BinaryNode& b) {
    LocalReference<jbyteArray> array = env.newByteArray(b.size()).as<jbyteArray>();
    for (jsize i = 0; i < static_cast<jsize>(b.size()); ++i) {
        env.setByteArrayElement(array, i, ((const char*)b.getPtr())[i]);
    }

    return array.release();
}

jobject QoreToJava::makeBigDecimal(Env& env, const QoreNumberNode& num) {
    QoreString str;
    num.toString(str);
    LocalReference<jstring> num_str = env.newString(str.c_str());
    std::vector<jvalue> jargs(1);
    jargs[0].l = num_str;
    return env.newObject(Globals::classBigDecimal, Globals::ctorBigDecimal, &jargs[0]).release();
}
}
