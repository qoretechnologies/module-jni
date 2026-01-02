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
/// \brief Defines the Env class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_ENV_H_
#define QORE_JNI_ENV_H_

#include "Jvm.h"
#include "LocalReference.h"

namespace jni {

/**
 * \brief Provides access to JNI functions.
 *
 * Wraps JNI references to RAII objects and uses C++ exceptions for error reporting.
 */
class Env {
public:
    /**
     * \brief Default constructor. Attaches current thread to the JVM.
     * \param set_context set the classloader context
     * \throws UnableToAttachException if the thread cannot be attached to the JVM
     */
    DLLLOCAL Env(bool set_context = true);

    /**
     * \brief Constructor.
     * \param env the Env object associated with this thread
     */
    DLLLOCAL Env(JNIEnv *env) : env(env) {
        Jvm::setEnv(env);
    }

    DLLLOCAL JNIEnv* operator*() {
        return env;
    }

   /**
    * \brief Returns the major version number in the higher 16 bits and the minor version number in the lower 16 bits.
    * \return the major version number in the higher 16 bits and the minor version number in the lower 16 bits
    */
   DLLLOCAL jint getVersion() {
      return env->GetVersion();
   }

   /**
    * \brief Finds a class with given name.
    * \param name fully-qualified class name or an array type signature
    * \return a local reference to the class object
    * \throws JavaException if the class cannot be found
    */
   DLLLOCAL LocalReference<jclass> findClass(const char* name) {
      jclass c = env->FindClass(name);
      if (c == nullptr) {
         throw JavaException();
      }
      return c;
   }

   /**
    * \brief Finds a field in a class.
    * \param cls the class
    * \param name the name of the field
    * \param descriptor the field descriptor
    * \return field id
    * \throws JavaException if the field cannot be found
    */
   DLLLOCAL jfieldID getField(jclass cls, const char* name, const char* descriptor) {
      jfieldID id = env->GetFieldID(cls, name, descriptor);
      if (id == nullptr) {
         throw JavaException();
      }
      return id;
   }

   /**
    * \brief Finds a static field in a class.
    * \param cls the class
    * \param name the name of the field
    * \param descriptor the field descriptor
    * \return field id
    * \throws JavaException if the field cannot be found
    */
   DLLLOCAL jfieldID getStaticField(jclass cls, const char* name, const char* descriptor) {
      jfieldID id = env->GetStaticFieldID(cls, name, descriptor);
      if (id == nullptr) {
         throw JavaException();
      }
      return id;
   }

   /**
    * \brief Finds a method in a class.
    * \param cls the class
    * \param name the name of the method
    * \param descriptor the method signature
    * \return method id
    * \throws JavaException if the method cannot be found
    */
   DLLLOCAL jmethodID getMethod(jclass cls, const char* name, const char* descriptor) {
      jmethodID id = env->GetMethodID(cls, name, descriptor);
      if (id == nullptr) {
         throw JavaException();
      }
      return id;
   }

   /**
    * \brief Finds a static method in a class.
    * \param cls the class
    * \param name the name of the method
    * \param descriptor the method signature
    * \return method id
    * \throws JavaException if the method cannot be found
    */
   DLLLOCAL jmethodID getStaticMethod(jclass cls, const char* name, const char* descriptor) {
      jmethodID id = env->GetStaticMethodID(cls, name, descriptor);
      if (id == nullptr) {
         throw JavaException();
      }
      return id;
   }

   /**
    * \brief Gets the value of a boolean field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jboolean getBooleanField(jobject object, jfieldID id) {
      return env->GetBooleanField(object, id);
   }

   /**
    * \brief Gets the value of a byte field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jbyte getByteField(jobject object, jfieldID id) {
      return env->GetByteField(object, id);
   }

   /**
    * \brief Gets the value of a char field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jchar getCharField(jobject object, jfieldID id) {
      return env->GetCharField(object, id);
   }

   /**
    * \brief Gets the value of a double field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jdouble getDoubleField(jobject object, jfieldID id) {
      return env->GetDoubleField(object, id);
   }

   /**
    * \brief Gets the value of a float field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jfloat getFloatField(jobject object, jfieldID id) {
      return env->GetFloatField(object, id);
   }

   /**
    * \brief Gets the value of an int field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jint getIntField(jobject object, jfieldID id) {
      return env->GetIntField(object, id);
   }

   /**
    * \brief Gets the value of a long field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jlong getLongField(jobject object, jfieldID id) {
      return env->GetLongField(object, id);
   }

   /**
    * \brief Gets the value of an Object field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL LocalReference<jobject> getObjectField(jobject object, jfieldID id) {
      return env->GetObjectField(object, id);
   }

   /**
    * \brief Gets the value of a short field.
    * \param object the instance
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jshort getShortField(jobject object, jfieldID id) {
      return env->GetShortField(object, id);
   }

   /**
    * \brief Gets the value of a static boolean field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jboolean getStaticBooleanField(jclass cls, jfieldID id) {
      return env->GetStaticBooleanField(cls, id);
   }

   /**
    * \brief Gets the value of a static byte field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jbyte getStaticByteField(jclass cls, jfieldID id) {
      return env->GetStaticByteField(cls, id);
   }

   /**
    * \brief Gets the value of a static char field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jchar getStaticCharField(jclass cls, jfieldID id) {
      return env->GetStaticCharField(cls, id);
   }

   /**
    * \brief Gets the value of a static double field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jdouble getStaticDoubleField(jclass cls, jfieldID id) {
      return env->GetStaticDoubleField(cls, id);
   }

   /**
    * \brief Gets the value of a static float field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jfloat getStaticFloatField(jclass cls, jfieldID id) {
      return env->GetStaticFloatField(cls, id);
   }

   /**
    * \brief Gets the value of a static int field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jint getStaticIntField(jclass cls, jfieldID id) {
      return env->GetStaticIntField(cls, id);
   }

   /**
    * \brief Gets the value of a static long field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jlong getStaticLongField(jclass cls, jfieldID id) {
      return env->GetStaticLongField(cls, id);
   }

   /**
    * \brief Gets the value of a static Object field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL LocalReference<jobject> getStaticObjectField(jclass cls, jfieldID id) {
      return env->GetStaticObjectField(cls, id);
   }

   /**
    * \brief Gets the value of a static short field.
    * \param cls the class of the field
    * \param id the field id
    * \return the field value
    */
   DLLLOCAL jshort getStaticShortField(jclass cls, jfieldID id) {
      return env->GetStaticShortField(cls, id);
   }

   /**
    * \brief Sets the value of a boolean field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setBooleanField(jobject object, jfieldID id, jboolean value) {
      env->SetBooleanField(object, id, value);
   }

   /**
    * \brief Sets the value of a byte field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setByteField(jobject object, jfieldID id, jbyte value) {
      env->SetByteField(object, id, value);
   }

   /**
    * \brief Sets the value of a char field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setCharField(jobject object, jfieldID id, jchar value) {
      env->SetCharField(object, id, value);
   }

   /**
    * \brief Sets the value of a double field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setDoubleField(jobject object, jfieldID id, jdouble value) {
      env->SetDoubleField(object, id, value);
   }

   /**
    * \brief Sets the value of a float field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setFloatField(jobject object, jfieldID id, jfloat value) {
      env->SetFloatField(object, id, value);
   }

   /**
    * \brief Sets the value of a int field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setIntField(jobject object, jfieldID id, jint value) {
      env->SetIntField(object, id, value);
   }

   /**
    * \brief Sets the value of a long field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setLongField(jobject object, jfieldID id, jlong value) {
      env->SetLongField(object, id, value);
   }

   /**
    * \brief Sets the value of a Object field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setObjectField(jobject object, jfieldID id, jobject value) {
      env->SetObjectField(object, id, value);
   }

   /**
    * \brief Sets the value of a short field.
    * \param object the instance
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setShortField(jobject object, jfieldID id, jshort value) {
      env->SetShortField(object, id, value);
   }

   /**
    * \brief Sets the value of a static boolean field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticBooleanField(jclass cls, jfieldID id, jboolean value) {
      env->SetStaticBooleanField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static byte field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticByteField(jclass cls, jfieldID id, jbyte value) {
      env->SetStaticByteField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static char field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticCharField(jclass cls, jfieldID id, jchar value) {
      env->SetStaticCharField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static double field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticDoubleField(jclass cls, jfieldID id, jdouble value) {
      env->SetStaticDoubleField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static float field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticFloatField(jclass cls, jfieldID id, jfloat value) {
      env->SetStaticFloatField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static int field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticIntField(jclass cls, jfieldID id, jint value) {
      env->SetStaticIntField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static long field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticLongField(jclass cls, jfieldID id, jlong value) {
      env->SetStaticLongField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static Object field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticObjectField(jclass cls, jfieldID id, jobject value) {
      env->SetStaticObjectField(cls, id, value);
   }

   /**
    * \brief Sets the value of a static short field.
    * \param cls the class of the field
    * \param id the field id
    * \param value the field value
    */
   DLLLOCAL void setStaticShortField(jclass cls, jfieldID id, jshort value) {
      env->SetStaticShortField(cls, id, value);
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jboolean callBooleanMethod(jobject object, jmethodID id, const jvalue *args) {
      jboolean ret = env->CallBooleanMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jbyte callByteMethod(jobject object, jmethodID id, const jvalue *args) {
      jbyte ret = env->CallByteMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jchar callCharMethod(jobject object, jmethodID id, const jvalue *args) {
      jchar ret = env->CallCharMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jdouble callDoubleMethod(jobject object, jmethodID id, const jvalue *args) {
      jdouble ret = env->CallDoubleMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jfloat callFloatMethod(jobject object, jmethodID id, const jvalue *args) {
      jfloat ret = env->CallFloatMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jint callIntMethod(jobject object, jmethodID id, const jvalue *args) {
      jint ret = env->CallIntMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jlong callLongMethod(jobject object, jmethodID id, const jvalue *args) {
      jlong ret = env->CallLongMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL LocalReference<jobject> callObjectMethod(jobject object, jmethodID id, const jvalue *args) {
      LocalReference<jobject> ret = env->CallObjectMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jshort callShortMethod(jobject object, jmethodID id, const jvalue *args) {
      jshort ret = env->CallShortMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method.
    * \param object the instance
    * \param id the method id
    * \param args the arguments
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL void callVoidMethod(jobject object, jmethodID id, const jvalue *args) {
      env->CallVoidMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jboolean callNonvirtualBooleanMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jboolean ret = env->CallNonvirtualBooleanMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jbyte callNonvirtualByteMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jbyte ret = env->CallNonvirtualByteMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jchar callNonvirtualCharMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jchar ret = env->CallNonvirtualCharMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jdouble callNonvirtualDoubleMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jdouble ret = env->CallNonvirtualDoubleMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jfloat callNonvirtualFloatMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jfloat ret = env->CallNonvirtualFloatMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jint callNonvirtualIntMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jint ret = env->CallNonvirtualIntMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jlong callNonvirtualLongMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jlong ret = env->CallNonvirtualLongMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL LocalReference<jobject> callNonvirtualObjectMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      LocalReference<jobject> ret = env->CallNonvirtualObjectMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jshort callNonvirtualShortMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      jshort ret = env->CallNonvirtualShortMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes an instance method non-virtually.
    * \param object the instance
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL void callNonvirtualVoidMethod(jobject object, jclass cls, jmethodID id, const jvalue *args) {
      env->CallNonvirtualVoidMethodA(object, cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jboolean callStaticBooleanMethod(jclass cls, jmethodID id, const jvalue *args) {
      jboolean ret = env->CallStaticBooleanMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jbyte callStaticByteMethod(jclass cls, jmethodID id, const jvalue *args) {
      jbyte ret = env->CallStaticByteMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jchar callStaticCharMethod(jclass cls, jmethodID id, const jvalue *args) {
      jchar ret = env->CallStaticCharMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jdouble callStaticDoubleMethod(jclass cls, jmethodID id, const jvalue *args) {
      jdouble ret = env->CallStaticDoubleMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jfloat callStaticFloatMethod(jclass cls, jmethodID id, const jvalue *args) {
      jfloat ret = env->CallStaticFloatMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jint callStaticIntMethod(jclass cls, jmethodID id, const jvalue *args) {
      jint ret = env->CallStaticIntMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jlong callStaticLongMethod(jclass cls, jmethodID id, const jvalue *args) {
      jlong ret = env->CallStaticLongMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL LocalReference<jobject> callStaticObjectMethod(jclass cls, jmethodID id, const jvalue *args) {
      LocalReference<jobject> ret = env->CallStaticObjectMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

   /**
    * \brief Invokes a static method.
    * \param cls the class of the method
    * \param id the method id
    * \param args the arguments
    * \return the return value
    * \throws JavaException if the method throws an exception
    */
   DLLLOCAL jshort callStaticShortMethod(jclass cls, jmethodID id, const jvalue *args) {
      jshort ret = env->CallStaticShortMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         throw JavaException();
      }
      return ret;
   }

    /**
     * \brief Invokes a static method.
     * \param cls the class of the method
     * \param id the method id
     * \param args the arguments
     * \throws JavaException if the method throws an exception
     */
    DLLLOCAL void callStaticVoidMethod(jclass cls, jmethodID id, const jvalue *args) {
        env->CallStaticVoidMethodA(cls, id, args);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    /**
     * \brief Tests whether an object is an instance of a class.
     * \param obj the object
     * \param cls the class
     * \return true if obj can be cast to cls
     */
    DLLLOCAL bool isInstanceOf(jobject obj, jclass cls) {
        return env->IsInstanceOf(obj, cls) == JNI_TRUE;
    }

    /**
     * \brief Creates a new Java object.
     * \param cls the class of the object
     * \param id the id of the constructor
     * \param args the arguments for the constructor
     * \return a reference to the new object
     * \throws JavaException if the construction fails
     */
    DLLLOCAL LocalReference<jobject> newObject(jclass cls, jmethodID id, const jvalue *args) {
        jobject ret = env->NewObjectA(cls, id, args);
        if (ret == nullptr) {
            throw JavaException();
        }
        return ret;
    }

    DLLLOCAL LocalReference<jstring> newString(const char* utf8) {
        jstring s = env->NewStringUTF(utf8);
        if (s == nullptr) {
            throw JavaException();
        }
        return s;
    }

    DLLLOCAL void registerNatives(jclass cls, const JNINativeMethod *methods, jint count) {
        if (env->RegisterNatives(cls, methods, count) != 0) {
            throw JavaException();
        }
    }

    DLLLOCAL LocalReference<jbooleanArray> newBooleanArray(jsize len) {
        jbooleanArray array = env->NewBooleanArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jbyteArray> newByteArray(jsize len) {
        jbyteArray array = env->NewByteArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jcharArray> newCharArray(jsize len) {
        jcharArray array = env->NewCharArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jshortArray> newShortArray(jsize len) {
        jshortArray array = env->NewShortArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jintArray> newIntArray(jsize len) {
        jintArray array = env->NewIntArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jlongArray> newLongArray(jsize len) {
        jlongArray array = env->NewLongArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jfloatArray> newFloatArray(jsize len) {
        jfloatArray array = env->NewFloatArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jdoubleArray> newDoubleArray(jsize len) {
        jdoubleArray array = env->NewDoubleArray(len);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL LocalReference<jobjectArray> newObjectArray(jsize len, jclass cls) {
        jobjectArray array = env->NewObjectArray(len, cls, nullptr);
        if (array == nullptr) {
            throw JavaException();
        }
        return array;
    }

    DLLLOCAL jsize getArrayLength(jarray array) {
        return env->GetArrayLength(array);
    }

    DLLLOCAL jboolean getBooleanArrayElement(jbooleanArray array, jsize index) {
        jboolean value;
        env->GetBooleanArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jbyte getByteArrayElement(jbyteArray array, jsize index) {
        jbyte value;
        env->GetByteArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jchar getCharArrayElement(jcharArray array, jsize index) {
        jchar value;
        env->GetCharArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jshort getShortArrayElement(jshortArray array, jsize index) {
        jshort value;
        env->GetShortArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jint getIntArrayElement(jintArray array, jsize index) {
        jint value;
        env->GetIntArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jlong getLongArrayElement(jlongArray array, jsize index) {
        jlong value;
        env->GetLongArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jfloat getFloatArrayElement(jfloatArray array, jsize index) {
        jfloat value;
        env->GetFloatArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL jdouble getDoubleArrayElement(jdoubleArray array, jsize index) {
        jdouble value;
        env->GetDoubleArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return value;
    }

    DLLLOCAL LocalReference<jobject> getObjectArrayElement(jobjectArray array, jsize index) {
        jobject o = env->GetObjectArrayElement(array, index);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
        return o;
    }

    DLLLOCAL void setBooleanArrayElement(jbooleanArray array, jsize index, jboolean value) {
        env->SetBooleanArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setByteArrayElement(jbyteArray array, jsize index, jbyte value) {
        env->SetByteArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setCharArrayElement(jcharArray array, jsize index, jchar value) {
        env->SetCharArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setShortArrayElement(jshortArray array, jsize index, jshort value) {
        env->SetShortArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setIntArrayElement(jintArray array, jsize index, jint value) {
        env->SetIntArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setLongArrayElement(jlongArray array, jsize index, jlong value) {
        env->SetLongArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setFloatArrayElement(jfloatArray array, jsize index, jfloat value) {
        env->SetFloatArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setDoubleArrayElement(jdoubleArray array, jsize index, jdouble value) {
        env->SetDoubleArrayRegion(array, index, 1, &value);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setObjectArrayElement(jobjectArray array, jsize index, jobject val) {
        env->SetObjectArrayElement(array, index, val);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL LocalReference<jobject> toReflectedField(jclass cls, jfieldID id, jboolean isStatic) {
        jobject o = env->ToReflectedField(cls, id, isStatic);
        if (o == nullptr) {
            throw JavaException();
        }
        return o;
    }

    DLLLOCAL LocalReference<jobject> toReflectedMethod(jclass cls, jmethodID id, jboolean isStatic) {
        jobject o = env->ToReflectedMethod(cls, id, isStatic);
        if (o == nullptr) {
            throw JavaException();
        }
        return o;
    }

    DLLLOCAL jfieldID fromReflectedField(jobject field) {
        jfieldID id = env->FromReflectedField(field);
        if (id == nullptr) {
            throw JavaException();
        }
        return id;
    }

    DLLLOCAL jmethodID fromReflectedMethod(jobject method) {
        jmethodID id = env->FromReflectedMethod(method);
        if (id == nullptr) {
            throw JavaException();
        }
        return id;
    }

    DLLLOCAL bool isSameObject(jobject obj1, jobject obj2) {
        return env->IsSameObject(obj1, obj2) == JNI_TRUE;
    }

    DLLLOCAL LocalReference<jclass> getObjectClass(jobject obj) {
        assert(obj != nullptr);
        return env->GetObjectClass(obj);
    }

    DLLLOCAL void throwException(jthrowable throwable) {
        env->Throw(throwable);
    }

    DLLLOCAL void throwNew(jclass cls, const char* msg) {
        env->ThrowNew(cls, msg);
    }

    DLLLOCAL LocalReference<jclass> defineClass(const char* name, jobject loader, const unsigned char* buf, jsize bufLen) {
        jclass c = env->DefineClass(name, loader, reinterpret_cast<const jbyte*>(buf), bufLen);
        if (c == nullptr) {
            throw JavaException();
        }
        return c;
    }

    //! find or define the given class
    /**
        we define a class if we are doing a clean initialization, otherwise if we have been initialized from Java, then
        the class already exists, so we look for it first
    */
    DLLLOCAL LocalReference<jclass> findDefineClass(const char* name, jobject loader, const unsigned char* buf, jsize bufLen) {
        jclass c = env->FindClass(name);
        //if (c) { printd(5, "FOUND '%s': %p\n", name, c); }
        if (!c) {
            // Clear any pending exception from FindClass before trying DefineClass
            // This handles cases like bootstrap mode where FindClass might throw
            // ExceptionInInitializerError due to getSystemClassLoader() being unavailable
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            c = env->DefineClass(name, loader, reinterpret_cast<const jbyte*>(buf), bufLen);
            if (!c) {
                throw JavaException();
            }
        }
        return c;
    }

    class GetStringUtfChars {
    public:
        DLLLOCAL GetStringUtfChars(Env &env, const LocalReference<jstring>& strref) :
                env(env), str(&strref),
                chars(strref ? env.env->GetStringUTFChars(strref, nullptr) : nullptr) {
            if (strref && chars == nullptr) {
                throw new JavaException;
            }
        }

        DLLLOCAL GetStringUtfChars(Env &env) :
                env(env), str(nullptr), chars(nullptr) {
        }

        DLLLOCAL ~GetStringUtfChars() {
            if (str) {
                env.env->ReleaseStringUTFChars(*str, chars);
            }
        }

        DLLLOCAL int set(const LocalReference<jstring>& strref) {
            discard();
            chars = env.env->GetStringUTFChars(strref, nullptr);
            if (chars) {
                str = &strref;
                return 0;
            }
            return -1;
        }

        DLLLOCAL void discard() {
            if (str) {
                env.env->ReleaseStringUTFChars(*str, chars);
                str = nullptr;
                chars = nullptr;
            }
        }

        DLLLOCAL const char* c_str() const {
            return chars ? chars : "";
        }

        DLLLOCAL char operator[](int i) const {
            if (i < 0) {
                i = strlen(chars) + i;
            }
            return chars[i];
        }

        DLLLOCAL operator bool() const {
            return (bool)chars;
        }

        DLLLOCAL bool operator==(const char* str) const {
            assert(chars);
            return !strcmp(str, chars);
        }

        DLLLOCAL bool operator!=(const char* str) const {
            assert(chars);
            return strcmp(str, chars);
        }

        DLLLOCAL GetStringUtfChars& operator=(const char* new_chars) {
            if (str) {
                env.env->ReleaseStringUTFChars(*str, chars);
                str = nullptr;
            }
            chars = new_chars;
            return *this;
        }

    private:
        Env& env;
        const LocalReference<jstring>* str;
        const char* chars;
    };

private:
    JNIEnv* env;

    friend class GetStringUtfChars;
};

} // namespace jni

#endif // QORE_JNI_ENV_H_
