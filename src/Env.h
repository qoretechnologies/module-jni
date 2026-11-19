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
/// \brief Defines the Env class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_ENV_H_
#define QORE_JNI_ENV_H_

#include "Jvm.h"
#include "LocalReference.h"

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

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
    * \param name fully-qualified class name or an array type signature in standard UTF-8; a name that is not
    * ASCII is converted to the modified UTF-8 of JNI (see ClassName)
    * \return a local reference to the class object
    * \throws JavaException if the class cannot be found
    */
   DLLLOCAL LocalReference<jclass> findClass(const char* name) {
      jclass c = env->FindClass(ClassName(name).c_str());
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
      jobject ret = env->CallObjectMethodA(object, id, args);
      if (env->ExceptionCheck()) {
         if (ret) {
            env->DeleteLocalRef(ret);
         }
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
      jobject ret = env->CallStaticObjectMethodA(cls, id, args);
      if (env->ExceptionCheck()) {
         if (ret) {
            env->DeleteLocalRef(ret);
         }
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

    //! Creates a Java string from a UTF-8 string
    /** JNI's NewStringUTF() takes "modified" UTF-8, in which a character outside the Basic Multilingual Plane is a
        UTF-16 surrogate pair of two 3-byte sequences and NUL is the two bytes C0 80; only an ASCII string is the same
        in both encodings and is created with NewStringUTF().  Any other string is decoded strictly to UTF-16: an
        invalid byte, a truncated or overlong sequence, an encoded surrogate (CESU-8) and a value beyond U+10FFFF
        each become U+FFFD.

        @param utf8 the string in UTF-8; it must have a NUL at \a len (a Qore string's buffer has one), which the
        ASCII fast path (NewStringUTF()) needs
        @param len the length of the string in bytes; it may have NUL characters
    */
    DLLLOCAL LocalReference<jstring> newString(const char* utf8, size_t len) {
        assert(!utf8[len]);
        jstring s;
        if (isAscii(utf8, len)) {
            s = env->NewStringUTF(utf8);
        } else {
            std::vector<jchar> utf16;
            decodeUtf8(utf8, len, utf16);
            s = env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
        }
        if (s == nullptr) {
            throw JavaException();
        }
        return s;
    }

    //! Creates a Java string from a NUL-terminated UTF-8 string; see newString(const char*, size_t)
    DLLLOCAL LocalReference<jstring> newString(const char* utf8) {
        return newString(utf8, strlen(utf8));
    }

    //! Creates a Java string from a %Qore string in any encoding; see newString(const char*, size_t)
    /** The string is converted to UTF-8 first; it may have NUL characters

        @throw XsinkException the string cannot be converted to UTF-8
    */
    DLLLOCAL LocalReference<jstring> newString(const QoreString& str);

    //! Creates a Java string from a %Qore string with a file path or a classpath, in any encoding
    /** The string is cut at its first NUL character, as the C string APIs that %Qore passes paths to cut it, and
        converted to UTF-8; the Java string is the one value that a path is both checked as and used as (see
        jni_check_classpath_policy())

        @throw XsinkException the string cannot be converted to UTF-8
    */
    DLLLOCAL LocalReference<jstring> newPathString(const QoreString& path) {
        return newString(QoreString(path.c_str(), path.getEncoding()));
    }

    //! Returns true if a string has only ASCII characters other than NUL
    DLLLOCAL static bool isAscii(const char* str, size_t len) {
        for (size_t i = 0; i < len; ++i) {
            unsigned char c = static_cast<unsigned char>(str[i]);
            if (!c || c >= 0x80) {
                return false;
            }
        }
        return true;
    }

    //! Decodes UTF-8 to UTF-16 strictly: each invalid sequence becomes U+FFFD
    DLLLOCAL static void decodeUtf8(const char* utf8, size_t len, std::vector<jchar>& out);

    //! Appends UTF-16 to a UTF-8 string: a surrogate pair is one character, an unpaired surrogate is U+FFFD
    DLLLOCAL static void appendUtf16(std::string& out, const jchar* utf16, size_t len);

    //! Converts the modified UTF-8 of JNI to standard UTF-8 if it differs
    /** A surrogate pair becomes the 4-byte sequence of its character, an unpaired surrogate U+FFFD, and the
        two bytes C0 80 a NUL character

        @param mutf8 a string in modified UTF-8, as GetStringUTFChars() returns it
        @param out the standard UTF-8 string, if a conversion was needed

        @return true if the string was converted to \a out, false if it is standard UTF-8 as it is
    */
    DLLLOCAL static bool convertModifiedUtf8(const char* mutf8, std::string& out);

    //! Converts standard UTF-8 to the modified UTF-8 of JNI, for the names that JNI takes in it
    /** JNI's FindClass() and DefineClass() take a class name in modified UTF-8, in which a character outside the
        Basic Multilingual Plane is a UTF-16 surrogate pair of two 3-byte sequences and NUL is the two bytes C0 80.
        The string is decoded strictly, as by decodeUtf8(): each invalid sequence becomes U+FFFD.

        @param utf8 the string in standard UTF-8
        @param len the length of the string in bytes; it may have NUL characters
        @param out the string in modified UTF-8, which has no NUL byte
    */
    DLLLOCAL static void toModifiedUtf8(const char* utf8, size_t len, std::string& out);

    //! A class name for JNI's FindClass() or DefineClass() in modified UTF-8, for as long as the object lives
    /** An ASCII name, as almost every class name is, is used as it is; any other name is converted with
        toModifiedUtf8()
    */
    class ClassName {
    public:
        //! Takes a NUL-terminated class name in standard UTF-8
        DLLLOCAL explicit ClassName(const char* utf8) : ClassName(utf8, strlen(utf8)) {
        }

        //! Takes a class name in standard UTF-8, which must have a NUL at \a len and may have NUL characters
        DLLLOCAL ClassName(const char* utf8, size_t len) {
            assert(!utf8[len]);
            if (isAscii(utf8, len)) {
                str = utf8;
            } else {
                toModifiedUtf8(utf8, len, converted);
                str = converted.c_str();
            }
        }

        ClassName(const ClassName&) = delete;
        ClassName& operator=(const ClassName&) = delete;

        DLLLOCAL const char* c_str() const {
            return str;
        }

    private:
        //! the converted name, which str may point to
        std::string converted;
        const char* str;
    };

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

    DLLLOCAL void getByteArrayRegion(jbyteArray array, jsize start, jsize len, jbyte* buf) {
        env->GetByteArrayRegion(array, start, len, buf);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL void setByteArrayRegion(jbyteArray array, jsize start, jsize len, const jbyte* buf) {
        env->SetByteArrayRegion(array, start, len, buf);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
    }

    DLLLOCAL jsize getStringLength(jstring str) {
        return env->GetStringLength(str);
    }

    DLLLOCAL void getStringRegion(jstring str, jsize start, jsize len, jchar* buf) {
        env->GetStringRegion(str, start, len, buf);
        if (env->ExceptionCheck()) {
            throw JavaException();
        }
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

    //! Returns a new local reference to the object
    DLLLOCAL jobject newLocalRef(jobject obj) {
        jobject rv = env->NewLocalRef(obj);
        if (obj && !rv) {
            throw JavaException();
        }
        return rv;
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

    //! Throws a new Java exception of the given class with a NUL-terminated message in UTF-8
    /** see throwNew(JNIEnv*, jclass, const char*, size_t)
    */
    DLLLOCAL void throwNew(jclass cls, const char* utf8) noexcept {
        throwNew(env, cls, utf8, strlen(utf8));
    }

    //! Throws a new Java exception of the given class with a message in any encoding
    /** see throwNew(JNIEnv*, jclass, const QoreString&)
    */
    DLLLOCAL void throwNew(jclass cls, const QoreString& msg) noexcept {
        throwNew(env, cls, msg);
    }

    //! Throws a new Java exception of the given class with a message in UTF-8, without C++ exceptions
    /** JNI's ThrowNew() takes the message in modified UTF-8, so only an ASCII message is passed to it; any other
        message is made a Java string as by newString() (strictly, from UTF-16), and the exception is made with the
        class's constructor that takes a \c String, and thrown.  The exception replaces a pending one, which is
        cleared first.

        @param env the JNI environment of the thread
        @param cls the class of the exception, which must have a constructor taking a \c String
        @param utf8 the message in UTF-8; it must have a NUL at \a len (a %Qore string's buffer has one)
        @param len the length of the message in bytes; it may have NUL characters

        @return 0 if the exception was thrown; a negative value if it could not be made, in which case the Java
        exception of the failure (ex: an \c OutOfMemoryError) is pending
    */
    DLLLOCAL static jint throwNew(JNIEnv* env, jclass cls, const char* utf8, size_t len) noexcept;

    //! Throws a new Java exception of the given class with a NUL-terminated message in UTF-8, without C++ exceptions
    DLLLOCAL static jint throwNew(JNIEnv* env, jclass cls, const char* utf8) noexcept {
        return throwNew(env, cls, utf8, strlen(utf8));
    }

    //! Throws a new Java exception of the given class with a message in any encoding, without C++ exceptions
    /** The message is converted to UTF-8; if it cannot be converted, its bytes are decoded as UTF-8, in which each
        invalid sequence becomes U+FFFD; see throwNew(JNIEnv*, jclass, const char*, size_t)
    */
    DLLLOCAL static jint throwNew(JNIEnv* env, jclass cls, const QoreString& msg) noexcept;

    //! Defines a class; \a name is in standard UTF-8 (see ClassName)
    DLLLOCAL LocalReference<jclass> defineClass(const char* name, jobject loader, const unsigned char* buf, jsize bufLen) {
        jclass c = env->DefineClass(ClassName(name).c_str(), loader, reinterpret_cast<const jbyte*>(buf), bufLen);
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
        // the name in modified UTF-8 (see ClassName)
        ClassName mname(name);
        jclass c = env->FindClass(mname.c_str());
        //if (c) { printd(5, "FOUND '%s': %p\n", name, c); }
        if (!c) {
            // Clear any pending exception from FindClass before trying DefineClass
            // This handles cases like bootstrap mode where FindClass might throw
            // ExceptionInInitializerError due to getSystemClassLoader() being unavailable
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            c = env->DefineClass(mname.c_str(), loader, reinterpret_cast<const jbyte*>(buf), bufLen);
            if (!c) {
                throw JavaException();
            }
        }
        return c;
    }

    //! A string from GetStringUTFChars() (modified UTF-8) in standard UTF-8, for as long as the object lives
    /** For the code that calls GetStringUTFChars() itself; see convertModifiedUtf8()
    */
    class ModifiedUtf8 {
    public:
        DLLLOCAL explicit ModifiedUtf8(const char* mutf8) {
            if (Env::convertModifiedUtf8(mutf8, converted)) {
                str = converted.c_str();
                len = converted.size();
            } else {
                str = mutf8;
                len = strlen(mutf8);
            }
        }

        ModifiedUtf8(const ModifiedUtf8&) = delete;
        ModifiedUtf8& operator=(const ModifiedUtf8&) = delete;

        DLLLOCAL const char* c_str() const {
            return str;
        }

        //! the length in bytes; the string may have NUL characters
        DLLLOCAL size_t size() const {
            return len;
        }

    private:
        //! the converted string, which str may point to
        std::string converted;
        const char* str;
        size_t len;
    };

    //! Holds the characters of a Java string from GetStringUTFChars() and releases them, without C++ exceptions
    /** For the code that must not use the Env wrapper; the characters are modified UTF-8 (see ModifiedUtf8)
    */
    class JniUtfChars {
    public:
        DLLLOCAL JniUtfChars(JNIEnv* env, jstring str) : env(env), str(str),
                chars(str ? env->GetStringUTFChars(str, nullptr) : nullptr) {
        }

        JniUtfChars(const JniUtfChars&) = delete;
        JniUtfChars& operator=(const JniUtfChars&) = delete;

        DLLLOCAL ~JniUtfChars() {
            if (chars) {
                env->ReleaseStringUTFChars(str, chars);
            }
        }

        DLLLOCAL const char* get() const {
            return chars;
        }

    private:
        JNIEnv* env;
        jstring str;
        const char* chars;
    };

    class GetStringUtfChars {
    public:
        //! Holds an independent local reference while the UTF character buffer is active.
        /** Native-method arguments are raw jstring values, while expressions such as
            LocalReference::as<jstring>() return temporary owning wrappers.  Taking an independent
            NewLocalRef here makes both cases safe: this helper never deletes the caller's reference,
            and a temporary caller-side wrapper can expire without invalidating the reference used
            by ReleaseStringUTFChars().
        */
        DLLLOCAL GetStringUtfChars(Env &env, jstring strref) : env(env), str(nullptr), chars(nullptr) {
            if (set(strref)) {
                throw JavaException();
            }
        }

        DLLLOCAL GetStringUtfChars(Env &env) : env(env), str(nullptr), chars(nullptr) {
        }

        GetStringUtfChars(const GetStringUtfChars&) = delete;
        GetStringUtfChars& operator=(const GetStringUtfChars&) = delete;

        DLLLOCAL ~GetStringUtfChars() {
            discard();
        }

        DLLLOCAL int set(jstring strref) {
            discard();
            if (!strref) {
                return 0;
            }
            str = static_cast<jstring>(env.env->NewLocalRef(strref));
            if (!str) {
                return -1;
            }
            jchars = env.env->GetStringUTFChars(str, nullptr);
            if (!jchars) {
                env.env->DeleteLocalRef(str);
                str = nullptr;
                return -1;
            }
            // the modified UTF-8 of a character outside the Basic Multilingual Plane or of NUL is converted to UTF-8;
            // the buffer is released if the conversion fails
            try {
                if (Env::convertModifiedUtf8(jchars, converted)) {
                    chars = converted.c_str();
                    len = converted.size();
                } else {
                    chars = jchars;
                    len = strlen(jchars);
                }
            } catch (...) {
                discard();
                throw;
            }
            return 0;
        }

        DLLLOCAL void discard() {
            if (str) {
                env.env->ReleaseStringUTFChars(str, jchars);
                env.env->DeleteLocalRef(str);
                str = nullptr;
                jchars = nullptr;
            }
            chars = nullptr;
            len = 0;
            converted.clear();
        }

        DLLLOCAL const char* c_str() const {
            return chars ? chars : "";
        }

        //! The length of the string in bytes; it may have NUL characters
        DLLLOCAL size_t size() const {
            return chars ? len : 0;
        }

        //! Returns true if the string has a NUL character, which a name or a hash key cannot have (qore#6738)
        DLLLOCAL bool hasNul() const {
            return chars && jni_has_nul(chars, len);
        }

        //! Returns the string for use as a name, which is a NUL-terminated string (qore#6738)
        /** @param what what the name is, in the singular, for the error (ex: \c "class name")
            @param what_plural what the name is, in the plural, for the error (ex: \c "class names")

            @throw XsinkException \c JNI-ERROR: the string has a NUL character, at which the name would be cut (see
            jni_check_name())
        */
        DLLLOCAL const char* name(const char* what, const char* what_plural) const {
            if (chars) {
                jni_check_name(chars, len, what, what_plural);
            }
            return c_str();
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

        //! Compares the whole string, which may have NUL characters, with a NUL-terminated string
        DLLLOCAL bool operator==(const char* str) const {
            assert(chars);
            return strlen(str) == len && !memcmp(str, chars, len);
        }

        DLLLOCAL bool operator!=(const char* str) const {
            return !(*this == str);
        }

        DLLLOCAL GetStringUtfChars& operator=(const char* new_chars) {
            discard();
            chars = new_chars;
            len = new_chars ? strlen(new_chars) : 0;
            return *this;
        }

    private:
        Env& env;
        jstring str;
        //! the string as the JVM gives it (modified UTF-8), released with the string
        const char* jchars = nullptr;
        //! the string in standard UTF-8: jchars, converted, or a string assigned with operator=()
        const char* chars;
        //! the length of chars in bytes
        size_t len = 0;
        //! jchars converted to standard UTF-8, if it has a character outside the Basic Multilingual Plane or NUL
        std::string converted;
    };

private:
    JNIEnv* env;

    friend class GetStringUtfChars;
};

} // namespace jni

#endif // QORE_JNI_ENV_H_
