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
///
/// \file
/// \brief Defines the Field class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_FIELD_H_
#define QORE_JNI_FIELD_H_

#include <qore/Qore.h>

#include <classfile_constants.h>

#include "Class.h"
#include "Globals.h"
#include "Env.h"
#include "QoreJniClassMap.h"

namespace jni {

/**
 * \brief Represents a Java field.
 */
class BaseField : public ObjectBase {
public:
    /**
     * \brief Constructor.
     * \param cls the class associated with the field id
     * \param id the field id
     * \param isStatic true if the field is static
     */
    BaseField(Class *cls, jfieldID id, bool isStatic) : cls(cls), id(id) {
        printd(LogLevel, "BaseField::BaseField(), this: %p, cls: %p, id: %p\n", this, cls, id);
        Env env;
        field = env.toReflectedField(cls->getJavaObject(), id, isStatic).makeGlobal();
        init(env);
    }

    /**
     * \brief Constructor.
     * \param field an instance of java.lang.reflect.Field
     * \param cls the owning class
     */
    BaseField(jobject field, Class* cls) : cls(cls) {
        Env env;
        id = env.fromReflectedField(field);
        this->field = GlobalReference<jobject>::fromLocal(field);
        printd(LogLevel, "BaseField::BaseField(), this: %p, cls: %p, id: %p\n", this, cls, id);
        init(env);
    }

    ~BaseField() {
        printd(LogLevel, "BaseField::~BaseField(), this: %p, cls: %p, id: %p\n", this, cls, id);
    }

    /**
     * \brief Gets the value of an instance field.
     *
     * \param object the instance
     * \param compat_types if backwards-compatible types should be used
     *
     * \return the value of the static field
     *
     * \throws Exception if the value cannot be retrieved
     */
    QoreValue get(jobject object, QoreProgram* pgm, bool compat_types);

    /**
     * \brief Sets the value of an instance field.
     * \param object the instance
     * \param value the new value
     * \throws Exception if the value cannot be set
     */
    void set(jobject object, const QoreValue &value, JniExternalProgramData* jpc);

    /**
     * \brief Gets the value of a static field.
     * \return the value of the static field
     * \throws Exception if the value cannot be retrieved
     */
    QoreValue getStatic(QoreProgram* pgm, bool compat_types);

    /**
     * \brief Sets the value of a static field.
     * \param value the new value
     * \throws Exception if the value cannot be set
     */
    void setStatic(const QoreValue &value, JniExternalProgramData* jpc);

    jobject getJavaObject() const override {
        return field;
    }

    DLLLOCAL void getName(QoreString& str);

    DLLLOCAL const QoreTypeInfo* getQoreTypeInfo(QoreJniClassMap& clsmap, QoreProgram* pgm = nullptr) {
        return clsmap.getQoreType(typeClass, pgm);
    }

    DLLLOCAL int isStatic() const {
        return mods & JVM_ACC_STATIC;
    }

    DLLLOCAL int isFinal() const {
        return mods & JVM_ACC_FINAL;
    }

    DLLLOCAL bool isSynthetic() const {
        return mods & ACC_SYNTHETIC;
    }

    ClassAccess getAccess() const {
        ClassAccess access = Public;

        if (mods & JVM_ACC_PRIVATE)
            access = Internal;
        else if (mods & JVM_ACC_PROTECTED)
            access = Private;

        return access;
    }

protected:
    DLLLOCAL BaseField() {
    }

    DLLLOCAL void init(Env &env) {
        typeClass = env.callObjectMethod(field, Globals::methodFieldGetType, nullptr).as<jclass>().makeGlobal();
        type = Globals::getType(typeClass);
        mods = env.callIntMethod(field, Globals::methodFieldGetModifiers, nullptr);
    }

protected:
    Class* cls;
    jfieldID id;
    GlobalReference<jobject> field;              // the instance of java.lang.reflect.Field
    GlobalReference<jclass> typeClass;           // the type of the field
    Type type;
    int mods;
};

class Field : public BaseField {
public:
   /**
    * \brief Constructor.
    * \param cls the class associated with the field id
    * \param id the field id
    * \param isStatic true if the field is static
    */
   Field(Class *cls, jfieldID id, bool isStatic) : BaseField(cls, id, isStatic) {
      cls->ref();
      cls_holder = cls;
   }

   /**
    * \brief Constructor.
    * \param field an instance of java.lang.reflect.Field
    */
   Field(jobject field) {
      Env env;
      cls_holder = cls = new Class(env.callObjectMethod(field, Globals::methodFieldGetDeclaringClass, nullptr).as<jclass>());
      id = env.fromReflectedField(field);
      this->field = GlobalReference<jobject>::fromLocal(field);
      printd(LogLevel, "Field::Field(), this: %p, cls: %p, id: %p\n", this, cls, id);
      init(env);
   }

private:
   SimpleRefHolder<Class> cls_holder;
};

} // namespace jni

#endif // QORE_JNI_FIELD_H_
