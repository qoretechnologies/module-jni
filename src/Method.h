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
///
/// \file
/// \brief Defines the Method class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_METHOD_H_
#define QORE_JNI_METHOD_H_

#include <qore/Qore.h>
#include "Class.h"
#include "Globals.h"
#include "Env.h"

#include <classfile_constants.h>

namespace jni {

class QoreJniClassMap;
class JniExternalProgramData;

/**
 * \brief Represents a Java method.
 */
class BaseMethod : public ObjectBase {
public:
    /**
     * \brief Constructor.
     * \param cls the class associated with the method id
     * \param id the method id
     * \param isStatic true if the method is static
     */
    BaseMethod(Class* cls, jmethodID id, bool isStatic) : cls(cls), id(id) {
        printd(LogLevel, "BaseMethod::BaseMethod(), this: %p, cls: %p, id: %p\n", this, cls, id);
        Env env;
        method = env.toReflectedMethod(cls->getJavaObject(), id, isStatic).makeGlobal();
        init(env);
        cls->ref();
    }

    /**
     * \brief Constructor.
     * \param method an instance of java.lang.reflect.Method
     * \cls the Class object for the method
     */
    DLLLOCAL BaseMethod(jobject method, Class* cls) : cls(cls) {
        Env env;
        id = env.fromReflectedMethod(method);
        this->method = GlobalReference<jobject>::fromLocal(method);
        printd(LogLevel, "BaseMethod::BaseMethod() this: %p cls: %p id: %p\n", this, cls, id);
        init(env);
    }

    /**
     * \brief Constructor.
     * \param method an instance of java.lang.reflect.Method
     * \cls the Class object for the method
     */
    DLLLOCAL BaseMethod(Env& env, jobject method, Class* cls) : cls(cls) {
        id = env.fromReflectedMethod(method);
        this->method = GlobalReference<jobject>::fromLocal(method);
        printd(LogLevel, "BaseMethod::BaseMethod() this: %p cls: %p id: %p\n", this, cls, id);
        init(env);
    }

    DLLLOCAL ~BaseMethod() {
        printd(LogLevel, "BaseMethod::~BaseMethod() this: %p cls: %p id: %p\n", this, cls, id);

        /*
        Env env;
        LocalReference<jstring> clsName =
            env.callObjectMethod(cls->getJavaObject(), Globals::methodClassGetName, nullptr).as<jstring>();
        LocalReference<jstring> methName =
            env.callObjectMethod(method, Globals::methodMethodGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars cname(env, clsName);
        Env::GetStringUtfChars mname(env, methName);
        printd(LogLevel, "BaseMethod::~BaseMethod() got %s::%s()\n", cname.c_str(), mname.c_str());
        */
    }

    /**
     * \brief throws a BasicException when the passed object's class does not match the expected class
     */
    DLLLOCAL void doObjectException(Env& env, jobject object) const;

    /**
     * \brief Invokes an instance method.
     * \param object the instance
     * \param args the arguments
     * \param offset the offset in args for the arguments
     * \return the return value
     * \throws Exception if the arguments do not match the descriptor or if the method throws
     */
    QoreValue invoke(jobject object, const QoreListNode* args, QoreProgram* pgm, int offset = 0) const;

    /**
     * \brief Invokes an instance method non-virtually.
     * \param object the instance
     * \param args the arguments
     * \param offset the offset in args for the arguments
     * \return the return value
     * \throws Exception if the arguments do not match the descriptor or if the method throws
     */
    QoreValue invokeNonvirtual(jobject object, const QoreListNode* args, QoreProgram* pgm, int offset = 0) const;

    /**
     * \brief Invokes a static method.
     * \param args the arguments
     * \param offset the offset in args for the arguments
     * \return the return value
     * \throws Exception if the arguments do not match the descriptor or if the method throws
     */
    QoreValue invokeStatic(const QoreListNode* args, QoreProgram* pgm, int offset = 0) const;

    /**
     * \brief Creates a new object by invoking a constructor.
     * \param args the arguments
     * \return the return value
     * \throws Exception if the arguments do not match the descriptor or if the method throws
     */
    QoreValue newInstance(const QoreListNode* args, QoreProgram* pgm);

    /**
     * \brief Creates a new Qore object by invoking a constructor.
     * \param args the arguments
     * \return the return value
     * \throws Exception if the arguments do not match the descriptor or if the method throws
     */
    LocalReference<jobject> newQoreInstance(const QoreListNode* args, JniExternalProgramData* jpc);

    void getName(QoreString& str) const;

    //! Checks if any interface implemented by the declaring class has this method declared as varargs
    /** Called when a method's isVarArgs() is false but its last parameter is a non-byte array.
        This handles JVM lambda classes where LambdaMetafactory drops ACC_VARARGS from the
        generated SAM method.
    */
    DLLLOCAL bool checkInterfaceVarArgs(Env& env, jobjectArray paramTypesArray);

    int isStatic() const {
        return mods & JVM_ACC_STATIC;
    }

    bool isClassAbstract() const {
        return cls->isAbstract();
    }

    jobject getJavaObject() const override {
        return method;
    }

    ClassAccess getAccess() const {
        ClassAccess access = Public;

        if (mods & JVM_ACC_PRIVATE) {
            access = Internal;
        } else if (mods & JVM_ACC_PROTECTED) {
            access = Private;
        }

        return access;
    }

    bool isAbstract() const {
        return mods & JVM_ACC_ABSTRACT;
    }

    bool isSynthetic() const {
        return mods & ACC_SYNTHETIC;
    }

    bool isBridge() const {
        return mods & ACC_BRIDGE;
    }

    int64 getFlags() const {
        // NOTE: do not set or return QCF_USES_EXTRA_ARGS when varargs is set; this causes a trailing "softlist auto" 
        // param to be created instead
        return QCF_NO_FLAGS;
    }

    DLLLOCAL int getParamTypes(Env& env, type_vec_t& paramTypeInfo, type_vec_t& altParamTypeInfo,
            QoreJniClassMap& clsmap, QoreProgram* pgm = nullptr, bool literal = false);

    DLLLOCAL const QoreTypeInfo* getReturnTypeInfo(QoreJniClassMap& clsmap, QoreProgram* pgm = nullptr);

    DLLLOCAL void getSignature(QoreString& str) const;

protected:
    DLLLOCAL BaseMethod() {
    }

    //! converts Qore args to Java args for a method call
    /** @param args the Qore args
        @param arg_offset the offset in args where to start converting arguments
        @param output_offset the offset in the return value where arguments start
     */
    DLLLOCAL std::vector<jvalue> convertArgs(Env& env, const QoreListNode* args, size_t arg_offset = 0,
            JniExternalProgramData* jpc = nullptr) const;

    //! converts Qore args to Java args for a method call
    /** @param args the Qore args
        @param arg_offset the offset in args where to start converting arguments
        @param array_offset the offset in the return value where arguments start
     */
    DLLLOCAL LocalReference<jobjectArray> convertArgsToArray(Env& env, const QoreListNode* args,
            size_t arg_offset = 0, size_t array_offset = 0, JniExternalProgramData* jpc = nullptr) const;

    DLLLOCAL void init(Env &env);

    Class* cls;
    jmethodID id;
    GlobalReference<jobject> method;     // the instance of java.lang.reflect.Method or java.lang.reflect.Constructor
    GlobalReference<jclass> retValClass;
    Type retValType;
    std::vector<std::pair<Type, GlobalReference<jclass>>> paramTypes;
    // method modifiers
    int mods;
    // varargs flag
    bool varargs;
};

class Method : public BaseMethod {
public:
    /**
     * \brief Constructor.
     * \param cls the class associated with the method id
     * \param id the method id
     * \param isStatic true if the method is static
     */
    DLLLOCAL Method(Class* cls, jmethodID id, bool isStatic) : BaseMethod(cls, id, isStatic), cls_holder(cls) {
        cls->ref();
        cls_holder = cls;
    }

    /**
     * \brief Constructor.
     * \param method an instance of java.lang.reflect.Method
     */
    DLLLOCAL Method(jobject method) {
        Env env;
        cls = new Class(env.callObjectMethod(method, Globals::methodMethodGetDeclaringClass, nullptr).as<jclass>());
        cls_holder = cls;
        id = env.fromReflectedMethod(method);
        this->method = GlobalReference<jobject>::fromLocal(method);
        printd(LogLevel, "Method::Method() this: %p, cls: %p, id: %p\n", this, cls, id);

        init(env);
    }

private:
    SimpleRefHolder<Class> cls_holder;
};

} // namespace jni

#endif // QORE_JNI_METHOD_H_
