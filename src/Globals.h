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
/// \brief TODO file description
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_GLOBALS_H_
#define QORE_JNI_GLOBALS_H_

#include "GlobalReference.h"
#include "Env.h"

#include <atomic>

DLLLOCAL QoreStringNode* jni_module_init_intern();

#define QORE_JNI_MODULE_NAME "jni"

namespace jni {

constexpr const char* JAVA_QORE_CLASS_FIELD = "$qore_cls_ptr";

enum class Type {
    Void, Boolean, Byte, Char, Short, Int, Long, Float, Double, Reference
};

// JVM access flags not in classfile_constants.h
constexpr int ACC_BRIDGE = 0x0040;    // method
constexpr int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter

DLLLOCAL extern bool jni_qore_init;
DLLLOCAL extern bool jni_qore_init_done;

DLLLOCAL const std::string JniImportedFunctionClassName = "$Functions";
DLLLOCAL const std::string JniImportedConstantClassName = "$Constants";

class Env;

class Globals {
public:
    DLLLOCAL static GlobalReference<jobject> syscl;                               // base/system classloader
    DLLLOCAL static bool bootstrap;                                               // bootstrap flag; our cl used as system cl

    DLLLOCAL static GlobalReference<jclass> classPrimitiveVoid;                   // class for the void return type
    DLLLOCAL static GlobalReference<jclass> classPrimitiveBoolean;                // class for the primitive type boolean
    DLLLOCAL static GlobalReference<jclass> classPrimitiveByte;                   // class for the primitive type byte
    DLLLOCAL static GlobalReference<jclass> classPrimitiveChar;                   // class for the primitive type char
    DLLLOCAL static GlobalReference<jclass> classPrimitiveShort;                  // class for the primitive type short
    DLLLOCAL static GlobalReference<jclass> classPrimitiveInt;                    // class for the primitive type int
    DLLLOCAL static GlobalReference<jclass> classPrimitiveLong;                   // class for the primitive type long
    DLLLOCAL static GlobalReference<jclass> classPrimitiveFloat;                  // class for the primitive type float
    DLLLOCAL static GlobalReference<jclass> classPrimitiveDouble;                 // class for the primitive type double

    DLLLOCAL static GlobalReference<jclass> arrayClassObject;                     // class for Object[]
    DLLLOCAL static GlobalReference<jclass> arrayClassByte;                       // class for byte[]

    DLLLOCAL static GlobalReference<jclass> classSystem;                          // java.lang.System
    DLLLOCAL static jmethodID methodSystemSetProperty;                            // String System.setProperty()
    DLLLOCAL static jmethodID methodSystemGetProperty;                            // String System.getProperty()
    DLLLOCAL static jmethodID methodSystemGC;                                     // void System.gc()

    DLLLOCAL static GlobalReference<jclass> classObject;                          // java.lang.Object
    DLLLOCAL static jmethodID methodObjectClone;                                  // Object Object.clone()
    DLLLOCAL static jmethodID methodObjectGetClass;                               // Class<?> Object.getClass()
    DLLLOCAL static jmethodID methodObjectEquals;                                 // boolean equals(Object)
    DLLLOCAL static jmethodID methodObjectHashCode;                               // int hashCode()
    DLLLOCAL static jmethodID methodObjectToString;                               // String toString()

    DLLLOCAL static GlobalReference<jclass> classClass;                           // java.lang.Class
    DLLLOCAL static jmethodID methodClassIsArray;                                 // boolean Class.isArray()
    DLLLOCAL static jmethodID methodClassIsInterface;                             // boolean Class.isInterface()
    DLLLOCAL static jmethodID methodClassGetComponentType;                        // Class<?> Class.getComponentType()
    DLLLOCAL static jmethodID methodClassGetClassLoader;                          // ClassLoader Class.getClassLoader()
    DLLLOCAL static jmethodID methodClassGetName;                                 // String Class.getName()
    DLLLOCAL static jmethodID methodClassGetDeclaredFields;                       // Field[] Class.getDeclaredFields()
    DLLLOCAL static jmethodID methodClassGetSuperClass;                           // Class<? super T> Class.getSuperClass()
    DLLLOCAL static jmethodID methodClassGetInterfaces;                           // Class<?>[] Class.getInterfacess()
    DLLLOCAL static jmethodID methodClassGetDeclaredConstructors;                 // Constructor<?>[] Class.getDeclaredConstructors()
    DLLLOCAL static jmethodID methodClassGetDeclaredConstructor;                  // Constructor<?> Class.getDeclaredConstructor(Class<?>...)
    DLLLOCAL static jmethodID methodClassGetModifiers;                            // int Class.getModifiers()
    DLLLOCAL static jmethodID methodClassIsPrimitive;                             // boolean Class.isPrimitive()
    DLLLOCAL static jmethodID methodClassGetDeclaredMethods;                      // Method[] Class.getDeclaredMethods()
    DLLLOCAL static jmethodID methodClassGetCanonicalName;                        // String Class.getCanonicalName()
    DLLLOCAL static jmethodID methodClassGetDeclaredField;                        // Field Class.getField()
    DLLLOCAL static jmethodID methodClassIsAssignableFrom;                        // boolean Class.isAsignableFrom(Class)
    DLLLOCAL static jmethodID methodClassGetMethod;                               // Method Class.getMethod(String, Class[])

    DLLLOCAL static GlobalReference<jclass> classThrowable;                       // java.lang.Throwable
    DLLLOCAL static jmethodID methodThrowableGetMessage;                          // String Throwable.getMessage()
    DLLLOCAL static jmethodID methodThrowableGetStackTrace;                       // String Throwable.getStackTrace()
    DLLLOCAL static jmethodID methodThrowableGetCause;                            // String Throwable.getCause()

    DLLLOCAL static GlobalReference<jclass> classStackTraceElement;               // java.lang.StackTraceElement
    DLLLOCAL static jmethodID methodStackTraceElementGetClassName;                // String StackTraceElement.getClassName()
    DLLLOCAL static jmethodID methodStackTraceElementGetFileName;                 // String StackTraceElement.getFileName()
    DLLLOCAL static jmethodID methodStackTraceElementGetLineNumber;               // int StackTraceElement.getLineNumber()
    DLLLOCAL static jmethodID methodStackTraceElementGetMethodName;               // String StackTraceElement.getMethodName()
    DLLLOCAL static jmethodID methodStackTraceElementIsNativeMethod;              // boolean StackTraceElement.isNativeMethod()

    DLLLOCAL static GlobalReference<jclass> classString;                          // java.lang.String

    DLLLOCAL static GlobalReference<jclass> classField;                           // java.lang.reflect.Field
    DLLLOCAL static jmethodID methodFieldGetType;                                 // Class<?> Field.getType()
    DLLLOCAL static jmethodID methodFieldGetDeclaringClass;                       // Class<?> Field.getDeclaringClass()
    DLLLOCAL static jmethodID methodFieldGetModifiers;                            // int Field.getModifiers()
    DLLLOCAL static jmethodID methodFieldGetName;                                 // String Field.getName()
    DLLLOCAL static jmethodID methodFieldGet;                                     // Object Field.get(Object)
    DLLLOCAL static jmethodID methodFieldGetLong;                                 // Object Field.getLong(Object)
    DLLLOCAL static jmethodID methodFieldSetAccessible;                           // void Field.setAccessible(boolean)

    DLLLOCAL static GlobalReference<jclass> classMethod;                          // java.lang.reflect.Method
    DLLLOCAL static jmethodID methodMethodGetReturnType;                          // Class<?> Method.getReturnType()
    DLLLOCAL static jmethodID methodMethodGetParameterTypes;                      // Class<?>[] Method.getParameterTypes()
    DLLLOCAL static jmethodID methodMethodGetDeclaringClass;                      // Class<?> Method.getDeclaringClass()
    DLLLOCAL static jmethodID methodMethodGetModifiers;                           // int Method.getModifiers()
    DLLLOCAL static jmethodID methodMethodIsVarArgs;                              // bool Method.isVarArgs()
    DLLLOCAL static jmethodID methodMethodGetName;                                // String Method.getName()
    DLLLOCAL static jmethodID methodMethodToGenericString;                        // String Method.getName()

    DLLLOCAL static GlobalReference<jclass> classConstructor;                     // java.lang.reflect.Constructor
    DLLLOCAL static jmethodID methodConstructorGetParameterTypes;                 // Class<?>[] Constructor.getParameterTypes()
    DLLLOCAL static jmethodID methodConstructorToString;                          // String Constructor.toString()
    DLLLOCAL static jmethodID methodConstructorGetModifiers;                      // int Constructor.getModifiers()
    DLLLOCAL static jmethodID methodConstructorIsVarArgs;                         // boolean Constructor.isVarArgs()
    DLLLOCAL static jmethodID methodConstructorNewInstance;                       // Object newInstance(Object...)

    DLLLOCAL static GlobalReference<jclass> classQoreInvocationHandler;           // org.qore.jni.QoreInvocationHandler
    DLLLOCAL static jmethodID ctorQoreInvocationHandler;                          // QoreInvocationHandler(long)
    DLLLOCAL static jmethodID methodQoreInvocationHandlerDestroy;                 // void QoreInvocationHandler.destroy()

    DLLLOCAL static GlobalReference<jclass> classQoreJavaApi;                     // org.qore.jni.QoreJavaApi
    DLLLOCAL static jmethodID methodQoreJavaApiGetStackTrace;                     // StackTraceElement[] getStackTrace()

    DLLLOCAL static GlobalReference<jclass> classQoreExceptionWrapper;            // org.qore.jni.QoreExceptionWrapper
    DLLLOCAL static jmethodID ctorQoreExceptionWrapper;                           // QoreExceptionWrapper(long)
    DLLLOCAL static jmethodID methodQoreExceptionWrapperGet;                      // long QoreExceptionWrapper.get()

    DLLLOCAL static GlobalReference<jclass> classQoreException;                   // org.qore.jni.QoreException
    DLLLOCAL static jmethodID methodQoreExceptionGetErr;                          // String QoreException.getErr()
    DLLLOCAL static jmethodID methodQoreExceptionGetDesc;                         // String QoreException.getDesc()
    DLLLOCAL static jmethodID methodQoreExceptionGetArg;                          // String QoreException.getArg()

    DLLLOCAL static GlobalReference<jclass> classQoreObjectBase;                  // org.qore.jni.QoreObjectBase
    DLLLOCAL static jmethodID methodQoreObjectBaseGet;                            // long get()

    DLLLOCAL static GlobalReference<jclass> classQoreJavaClassBase;               // org.qore.jni.QoreJavaClassBase

    DLLLOCAL static GlobalReference<jclass> classQoreObject;                      // org.qore.jni.QoreObject
    DLLLOCAL static jmethodID ctorQoreObject;                                     // QoreObject(long)

    DLLLOCAL static GlobalReference<jclass> classQoreJavaObjectPtr;               // org.qore.jni.QoreJavaObjectPtr
    DLLLOCAL static jmethodID ctorQoreJavaObjectPtr;                              // QoreJavaObjectPtr(long)

    DLLLOCAL static GlobalReference<jclass> classQoreClosure;                     // org.qore.jni.QoreClosure
    DLLLOCAL static jmethodID ctorQoreClosure;                                    // QoreClosure(long)
    DLLLOCAL static jmethodID methodQoreClosureGet;                               // long QoreClosure.get()

    DLLLOCAL static GlobalReference<jclass> classQoreObjectWrapper;               // org.qore.jni.QoreObjectWrapper

    DLLLOCAL static GlobalReference<jclass> classQoreClosureMarker;               // org.qore.jni.QoreClosureMarker
    DLLLOCAL static GlobalReference<jclass> classQoreClosureMarkerImpl;           // org.qore.jni.QoreClosureMarkerImpl

    DLLLOCAL static GlobalReference<jclass> classProxy;                           // java.lang.reflect.Proxy
    DLLLOCAL static jmethodID methodProxyNewProxyInstance;                        // Object Proxy.newProxyInstance(ClassLoader, Class[], InvocationHandler)

    DLLLOCAL static GlobalReference<jclass> classClassLoader;                     // java.lang.ClassLoader
    DLLLOCAL static jmethodID methodClassLoaderLoadClass;                         // Class loadClass(String)

    DLLLOCAL static GlobalReference<jclass> classQoreURLClassLoader;              // org.qore.jni.QoreURLClassLoader
    DLLLOCAL static jmethodID ctorQoreURLClassLoader;                             // QoreURLClassLoader(long)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderAddPath;                    // void QoreURLClassLoader.addPath(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderAddParentPath;              // void QoreURLClassLoader.addParentPath(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderLoadClass;                  // Class QoreURLClassLoader.loadClass(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderLoadClassWithPtr;           // Class QoreURLClassLoader.loadClassWithPtr(String, long)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderLoadResolveClass;           // Class QoreURLClassLoader.loadResolveClass(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderSetContext;                 // void QoreURLClassLoader.setContext()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderGetProgramPtr;              // long QoreURLClassLoader.getProgramPtr()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderAddPendingClass;            // void QoreURLClassLoader.addPendingClass(String, byte[])
    DLLLOCAL static jmethodID methodQoreURLClassLoaderDefineResolveClass;         // Class<?> QoreURLClassLoader.defineResolveClass​(String, byte[], int, int)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderGetResolveClass;            // Class<?> QoreURLClassLoader.getResolveClass(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderClearCache;                 // void QoreURLClassLoader.clearCache()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderDefineClassUnconditional;   // Class<?> QoreURLClassLoader.defineClassUnconditional(String, byte[])
    DLLLOCAL static jmethodID methodQoreURLClassLoaderGetPtr;                     // long getPtr()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderGetCurrent;                 // OoreURLClassLoader getCurrent()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderCheckInProgress;            // boolean checkInProgress(String)
    DLLLOCAL static jmethodID methodQoreURLClassLoaderClearProgramPtr;            // void ClearProgramPtr()

    DLLLOCAL static GlobalReference<jclass> classJavaClassBuilder;                // org.qore.jni.JavaClassBuilder
    DLLLOCAL static jmethodID methodJavaClassBuilderGetFunctionConstantClassBuilder; // static DynamicType.Builder<?> getFunctionConstantClassBuilder(String bin_name)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddFunction;                  // static DynamicType.Builder<?> addFunction(DynamicType.Builder<?>, String, long, long, long, TypeDefinition, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddStaticField;               // static DynamicType.Builder<?> addStaticField(DynamicType.Builder<?>, String, int, TypeDescription, long, ArrayList)
    DLLLOCAL static jmethodID methodJavaClassBuilderCreateStaticInitializer;      // static DynamicType.Builder<?> createStaticInitializer(DynamicType.Builder<?>, String, long, ArrayList)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetClassBuilder;              // static DynamicType.Builder<?> getClassBuilder(String, Class<?>, boolean, long)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddConstructor;               // static DynamicType.Builder<?> addConstructor(DynamicType.Builder<?>, Class<?>, long, long, int, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddNormalMethod;              // static DynamicType.Builder<?> addNormalMethod(DynamicType.Builder<?>, String, long, long, int, TypeDefinition, List<TypeDefinition>, boolean, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddStaticMethod;              // static DynamicType.Builder<?> addStaticMethod(DynamicType.Builder<?>, String, long, long, long, int, TypeDefinition, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetByteCodeFromBuilder;       // static byte[] getByteCodeFromBuilder(DynamicType.Builder<?>, QoreURLClassLoader)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeDescriptionCls;        // static TypeDescription getTypeDescription(Class<?>)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeDescriptionStr;        // static TypeDescription getTypeDescription(String)
    DLLLOCAL static jmethodID methodJavaClassBuilderFindBaseClassMethodConflict;  // static boolean findBaseClassMethodConflict(Class<?>, String, List<TypeDescription>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderIsFinalBaseClassMethod;       // static boolean isFinalBaseClassMethod(Class<?>, String, List<TypeDescription>)

    // Kotlin metadata helper class
    DLLLOCAL static GlobalReference<jclass> classKotlinMetadataHelper;           // org.qore.jni.KotlinMetadataHelper
    DLLLOCAL static jmethodID methodKotlinMetadataHelperIsKotlinClass;           // static boolean isKotlinClass(Class<?>)
    DLLLOCAL static jmethodID methodKotlinMetadataHelperGetKotlinKind;           // static int getKotlinKind(Class<?>)
    DLLLOCAL static jmethodID methodKotlinMetadataHelperIsCompanionObject;       // static boolean isCompanionObject(Class<?>)
    DLLLOCAL static jmethodID methodKotlinMetadataHelperIsDataClass;             // static boolean isDataClass(Class<?>)
    DLLLOCAL static jmethodID methodKotlinMetadataHelperIsFileFacade;            // static boolean isFileFacade(Class<?>)
    DLLLOCAL static jmethodID methodKotlinMetadataHelperIsKotlinRuntimeAvailable; // static boolean isKotlinRuntimeAvailable()

    // Kotlin script engine helper class
    DLLLOCAL static GlobalReference<jclass> classKotlinScriptEngine;              // org.qore.jni.KotlinScriptEngine
    DLLLOCAL static jmethodID methodKotlinScriptEngineIsAvailable;                // static boolean isAvailable()
    DLLLOCAL static jmethodID methodKotlinScriptEngineGetInitError;               // static String getInitError()
    DLLLOCAL static jmethodID methodKotlinScriptEngineEval;                       // static Object eval(String)
    DLLLOCAL static jmethodID methodKotlinScriptEngineEvalWithBindings;           // static Object evalWithBindings(String, HashMap)
    DLLLOCAL static jmethodID methodKotlinScriptEngineRetryInit;                  // static boolean retryInit()

    // Lazily initialize the KotlinScriptEngine class (can retry if failed)
    DLLLOCAL static void initKotlinScriptEngine();

    // to check for headless AWT to avoid importing classes that cannot be initialized when headless
    // NOTE: loaded lazily via ensureGraphicsEnvironment() to avoid spawning subprocesses during init
    DLLLOCAL static GlobalReference<jclass> classGraphicsEnvironment;             // java.awt.GraphicsEnvironment
    DLLLOCAL static jmethodID methodGraphicsEnvironmentIsHeadless;                // boolean GraphicsEnvironment.isHeadless()
    DLLLOCAL static void ensureGraphicsEnvironment();

    DLLLOCAL static GlobalReference<jclass> classThread;                          // java.lang.Thread
    DLLLOCAL static jmethodID methodThreadCurrentThread;                          // Thread Thread.currentThread()
    DLLLOCAL static jmethodID methodThreadGetContextClassLoader;                  // ClassLoader Thread.getContextClassLoader()

    DLLLOCAL static GlobalReference<jclass> classHashMap;                         // java.util.HashMap

    DLLLOCAL static GlobalReference<jclass> classHash;                            // org.qore.jni.Hash
    DLLLOCAL static jmethodID ctorHash;                                           // Hash()
    DLLLOCAL static jmethodID methodHashPut;                                      // Object Hash.put(Object K, Object V)

    DLLLOCAL static GlobalReference<jclass> classMap;                             // java.util.Map
    DLLLOCAL static jmethodID methodMapEntrySet;                                  // Set<Map.Entry<K,V>> Map.entrySet()

    DLLLOCAL static GlobalReference<jclass> classList;                            // java.util.List
    DLLLOCAL static jmethodID methodListSize;                                     // int List.size()
    DLLLOCAL static jmethodID methodListGet;                                      // Object List.get(int index)

    DLLLOCAL static GlobalReference<jclass> classArrayList;                       // java.util.ArrayList
    DLLLOCAL static jmethodID ctorArrayList;                                      // ArrayList()
    DLLLOCAL static jmethodID methodArrayListAdd;                                 // int ArrayList.add(Object)
    DLLLOCAL static jmethodID methodArrayListGet;                                 // Object ArrayList.get(int)
    DLLLOCAL static jmethodID methodArrayListRemove;                              // Object ArrayList.remove(int)
    DLLLOCAL static jmethodID methodArrayListSize;                                // int ArrayList.size()
    DLLLOCAL static jmethodID methodArrayListToArray;                             // Object[] ArrayList.toArray()

    DLLLOCAL static GlobalReference<jclass> classSet;                             // java.util.Set
    DLLLOCAL static jmethodID methodSetIterator;                                  // Set.iterator()

    DLLLOCAL static GlobalReference<jclass> classEntry;                           // java.util.Map.Entry
    DLLLOCAL static jmethodID methodEntryGetKey;                                  // Map.Entry.getKey()
    DLLLOCAL static jmethodID methodEntryGetValue;                                // Map.Entry.getValue()

    DLLLOCAL static GlobalReference<jclass> classIterator;                        // java.util.Iterator
    DLLLOCAL static jmethodID methodIteratorHasNext;                              // boolean hasNext()
    DLLLOCAL static jmethodID methodIteratorNext;                                 // Object next()

    DLLLOCAL static GlobalReference<jclass> classZonedDateTime;                   // java.time.ZonedDateTime
    DLLLOCAL static jmethodID methodZonedDateTimeParse;                           // ZonedDateTime.parse()
    DLLLOCAL static jmethodID methodZonedDateTimeToString;                        // ZonedDateTime.toString()

    DLLLOCAL static GlobalReference<jclass> classLocalDateTime;                   // java.time.LocalDateTime
    DLLLOCAL static jmethodID methodLocalDateTimeToString;                        // LocalDateTime.toString()

    DLLLOCAL static GlobalReference<jclass> classInstant;                         // java.time.Instant
    DLLLOCAL static jmethodID methodInstantToString;                              // Instant.toString()

    DLLLOCAL static GlobalReference<jclass> classQoreRelativeTime;                // org.qore.jni.QoreRelativeTime
    DLLLOCAL static jmethodID ctorQoreRelativeTime;                               // QoreRelativeTime(int, int, int, int, int, int, int)
    DLLLOCAL static jfieldID fieldQoreRelativeTimeYear;                           // QoreRelativeTime.year
    DLLLOCAL static jfieldID fieldQoreRelativeTimeMonth;                          // QoreRelativeTime.month
    DLLLOCAL static jfieldID fieldQoreRelativeTimeDay;                            // QoreRelativeTime.day
    DLLLOCAL static jfieldID fieldQoreRelativeTimeHour;                           // QoreRelativeTime.hour
    DLLLOCAL static jfieldID fieldQoreRelativeTimeMinute;                         // QoreRelativeTime.minute
    DLLLOCAL static jfieldID fieldQoreRelativeTimeSecond;                         // QoreRelativeTime.second
    DLLLOCAL static jfieldID fieldQoreRelativeTimeUs;                             // QoreRelativeTime.us

    DLLLOCAL static GlobalReference<jclass> classBigDecimal;                      // java.math.BigDecimal
    DLLLOCAL static jmethodID ctorBigDecimal;                                     // BigDecimal(String)
    DLLLOCAL static jmethodID methodBigDecimalToString;                           // BigDecimal.toString()

    DLLLOCAL static GlobalReference<jclass> classArrays;                          // java.util.Arrays
    DLLLOCAL static jmethodID methodArraysToString;                               // Arrays.toString()
    DLLLOCAL static jmethodID methodArraysDeepToString;                           // Arrays.deepToString()

    DLLLOCAL static GlobalReference<jclass> classBoolean;                         // java.lang.Boolean
    DLLLOCAL static jmethodID ctorBoolean;                                        // Boolean(boolean)
    DLLLOCAL static jmethodID methodBooleanBooleanValue;                          // boolean Boolean.booleanValue()

    DLLLOCAL static GlobalReference<jclass> classInteger;                         // java.lang.Integer
    DLLLOCAL static jmethodID ctorInteger;                                        // Integer(int)
    DLLLOCAL static jmethodID methodIntegerIntValue;                              // int Integer.intValue()

    DLLLOCAL static GlobalReference<jclass> classLong;                            // java.lang.Long
    DLLLOCAL static jmethodID ctorLong;                                           // Long(long)
    DLLLOCAL static jmethodID methodLongLongValue;                                // long Long.longValue()

    DLLLOCAL static GlobalReference<jclass> classShort;                           // java.lang.Short
    DLLLOCAL static jmethodID ctorShort;                                          // Short(short)
    DLLLOCAL static jmethodID methodShortShortValue;                              // short Short.shortValue()

    DLLLOCAL static GlobalReference<jclass> classByte;                            // java.lang.Byte
    DLLLOCAL static jmethodID ctorByte;                                           // Byte(byte)
    DLLLOCAL static jmethodID methodByteByteValue;                                // byte Byte.byteValue()

    DLLLOCAL static GlobalReference<jclass> classDouble;                          // java.lang.Double
    DLLLOCAL static jmethodID ctorDouble;                                         // Double(double)
    DLLLOCAL static jmethodID methodDoubleDoubleValue;                            // double Double.doubleValue()

    DLLLOCAL static GlobalReference<jclass> classFloat;                           // java.lang.Float
    DLLLOCAL static jmethodID ctorFloat;                                          // Float(float)
    DLLLOCAL static jmethodID methodFloatFloatValue;                              // float Float.floatValue()

    DLLLOCAL static GlobalReference<jclass> classCharacter;                       // java.lang.Character
    DLLLOCAL static jmethodID ctorCharacter;                                      // Character(char)
    DLLLOCAL static jmethodID methodCharacterCharValue;                           // char Character.charValue()

    DLLLOCAL static GlobalReference<jclass> classCharSequence;                    // java.lang.CharSequence

    DLLLOCAL static GlobalReference<jclass> classBooleanWrapper;                  // org.qore.jni.BooleanWrapper
    DLLLOCAL static jmethodID methodBooleanWrapperSetTrue;                        // setTrue()

    DLLLOCAL static GlobalReference<jclass> classProperties;                      // java.util.Properties
    DLLLOCAL static jmethodID ctorProperties;                                     // Properties()
    DLLLOCAL static jmethodID methodPropertiesSetProperty;                        // Object setProperty(String, String)

    DLLLOCAL static GlobalReference<jclass> classDriverManager;                   // java.sql.DriverManager
    DLLLOCAL static jmethodID methodDriverManagerGetConnection;                   // Connection getConnection(String, Properties)

    DLLLOCAL static GlobalReference<jclass> classConnection;                      // java.sql.Connection
    DLLLOCAL static jmethodID methodConnectionClose;                              // void Connection.close()
    DLLLOCAL static jmethodID methodConnectionCommit;                             // void Connection.commit()
    DLLLOCAL static jmethodID methodConnectionRollback;                           // void Connection.rollback()
    DLLLOCAL static jmethodID methodConnectionCreateArrayOf;                      // Array createArrayOf(String, Object[])
    DLLLOCAL static jmethodID methodConnectionGetMetaData;                        // DatabaseMetaData getMetaData()
    DLLLOCAL static jmethodID methodConnectionPrepareStatement;                   // PreparedStatement prepareStatement(String)
    DLLLOCAL static jmethodID methodConnectionPrepareStatementArray;              // PreparedStatement prepareStatement(String, String[])
    DLLLOCAL static jmethodID methodConnectionSetAutoCommit;                      // void setAutoCommit(boolean)
    DLLLOCAL static jmethodID methodConnectionIsValid;                            // boolean isValid(int)

    DLLLOCAL static GlobalReference<jclass> classDatabaseMetaData;                // java.sql.DatabaseMetaData
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDatabaseMajorVersion;      // int getDatabaseMajorVersion()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDatabaseMinorVersion;      // int getDatabaseMinorVersion()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDatabaseProductName;       // String getDatabaseProductName()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDatabaseProductVersion;    // String getDatabaseProductVersion()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDriverMajorVersion;        // int getDriverMajorVersion()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDriverMinorVersion;        // int getDriverMinorVersion()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDriverName;                // String getDriverName()
    DLLLOCAL static jmethodID methodDatabaseMetaDataGetDriverVersion;             // String getDriverVersion()

    DLLLOCAL static GlobalReference<jclass> classPreparedStatement;               // java.sql.PreparedStatement
    DLLLOCAL static jmethodID methodPreparedStatementAddBatch;                    // void addBatch()
    DLLLOCAL static jmethodID methodPreparedStatementClose;                       // void close()
    DLLLOCAL static jmethodID methodPreparedStatementExecute;                     // boolean execute()
    DLLLOCAL static jmethodID methodPreparedStatementExecuteBatch;                // int[] executeBatch()
    DLLLOCAL static jmethodID methodPreparedStatementGetResultSet;                // ResultSet getResultSet()
    DLLLOCAL static jmethodID methodPreparedStatementGetMoreResults;              // boolean getMoreResults()
    DLLLOCAL static jmethodID methodPreparedStatementGetUpdateCount;              // int getUpdateCount()
    DLLLOCAL static jmethodID methodPreparedStatementSetArray;                    // void setArray(int, Array)
    DLLLOCAL static jmethodID methodPreparedStatementSetBigDecimal;               // void setBigDecimal(int, BigDecimal)
    DLLLOCAL static jmethodID methodPreparedStatementSetBoolean;                  // void setBoolean(int, boolean)
    DLLLOCAL static jmethodID methodPreparedStatementSetByte;                     // void setByte(int, byte)
    DLLLOCAL static jmethodID methodPreparedStatementSetBytes;                    // void setBytes(int, byte[])
    DLLLOCAL static jmethodID methodPreparedStatementSetDouble;                   // void setBoolean(int, double)
    DLLLOCAL static jmethodID methodPreparedStatementSetInt;                      // void setInt(int, int)
    DLLLOCAL static jmethodID methodPreparedStatementSetLong;                     // void setLong(int, long)
    DLLLOCAL static jmethodID methodPreparedStatementSetShort;                    // void setShort(int, short)
    DLLLOCAL static jmethodID methodPreparedStatementSetNull;                     // void setNull(int, int)
    DLLLOCAL static jmethodID methodPreparedStatementSetString;                   // void setString(int, String)
    DLLLOCAL static jmethodID methodPreparedStatementSetTimestamp;                // void setTimestamp(int, Timestamp)

    DLLLOCAL static GlobalReference<jclass> classTimestamp;                       // java.sql.Timestamp
    DLLLOCAL static jmethodID ctorTimestamp;                                      // Timestamp(long)
    DLLLOCAL static jmethodID methodTimestampSetNanos;                            // void setNanos(int)
    DLLLOCAL static jmethodID methodTimestampToString;                            // String toString()

    DLLLOCAL static GlobalReference<jclass> classDate;                            // java.sql.Date
    DLLLOCAL static jmethodID methodDateToString;                                 // String toString()

    DLLLOCAL static GlobalReference<jclass> classTime;                            // java.sql.Time
    DLLLOCAL static jmethodID methodTimeToString;                                 // String toString()

    DLLLOCAL static GlobalReference<jclass> classResultSet;                       // java.sql.ResultSet
    DLLLOCAL static jmethodID methodResultSetClose;                               // void close()
    DLLLOCAL static jmethodID methodResultSetGetArray;                            // Array getArray(int)
    DLLLOCAL static jmethodID methodResultSetGetObject;                           // Object getObject(int)
    DLLLOCAL static jmethodID methodResultSetNext;                                // boolean next()
    DLLLOCAL static jmethodID methodResultSetGetMetaData;                         // ResultSetMetaData getMetaData()

    DLLLOCAL static GlobalReference<jclass> classResultSetMetaData;               // java.sql.ResultSetMetadata
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnClassName;          // String getColumnClassName()
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnCount;              // int getColumnCount()
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnLabel;              // String getColumnLabel(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnType;               // int getColumnType(int)

    DLLLOCAL static GlobalReference<jclass> classArray;                           // java.sql.Array
    DLLLOCAL static jmethodID methodArrayGetArray;                                // Object getArray()

    DLLLOCAL static GlobalReference<jclass> classSQLException;                    // java.sql.SQLException

    DLLLOCAL static GlobalReference<jclass> classServiceLoader;                   // java.util.ServiceLoader
    DLLLOCAL static jmethodID methodServiceLoaderIterator;                        // Iterator iterator()

    DLLLOCAL static GlobalReference<jclass> classDriver;                          // java.sql.Driver

    DLLLOCAL static int typeNull; // java.sql.Type.NULL value
    DLLLOCAL static int typeChar; // java.sql.Type.CHAR value

    DLLLOCAL static GlobalReference<jstring> javaQoreClassField;

    DLLLOCAL static GlobalReference<jclass> getQoreJavaClassBase(Env& env, jobject classLoader);

    // returns true if this is a Java bootstrap init
    DLLLOCAL static bool init();

    // Lazily initialize the KotlinMetadataHelper class
    DLLLOCAL static void initKotlinMetadataHelper();

    DLLLOCAL static void cleanup();
    DLLLOCAL static Type getType(jclass cls);

    //! Spawn the NativeCleanup C++ background thread.
    /** Replaces the legacy java.lang.ref.Cleaner-driven release0 dispatch with a
        thread that polls org.qore.jni.NativeCleanup.queue and dispatches the native
        cleanup directly in C++ — no JNI native-method invocation adapter, so the
        path is immune to JIT codecache reorganisation and class-unloading-driven
        adapter eviction.  Called from Jvm::createVM after JNI_CreateJavaVM and
        Globals static initialisation. */
    DLLLOCAL static void startNativeCleanupThread();

    //! Stop the NativeCleanup C++ background thread and join it.
    /** Enqueues a sentinel onto NativeCleanup.queue to wake the thread, then joins.
        Called from Jvm::destroyVM before DestroyJavaVM. */
    DLLLOCAL static void stopNativeCleanupThread();

    DLLLOCAL static jlong getContextProgram(jobject new_syscl, bool& created);
    DLLLOCAL static QoreProgram* createJavaContextProgram();
    DLLLOCAL static QoreProgram* getJavaContextProgram();

    DLLLOCAL static void clearGlobalContext() {
        jni_shutting_down.store(true, std::memory_order_release);
        if (qph) {
            qph.reset();
        }
    }

    //! Returns true if the JNI module is shutting down
    /** Java background threads (e.g. ActiveMQ/JMS listeners) must check
        this before calling back into Qore — after shutdown starts, the
        global context program and JNI external data are no longer valid.
    */
    DLLLOCAL static bool isShuttingDown() {
        return jni_shutting_down.load(std::memory_order_acquire);
    }

    DLLLOCAL static void setAlreadyInitialized() {
        assert(!already_initialized);
        already_initialized = true;
    }

    DLLLOCAL static bool getAlreadyInitialized() {
        return already_initialized;
    }

    // if already initialized, first tries to find the class, and then defines it only if not found, otherwise
    // defines the class
    DLLLOCAL static LocalReference<jclass> findDefineClass(Env& env, const char* name, jobject loader,
            const unsigned char* buf, jsize bufLen);

    //! Returns the canonical {@link QoreURLClassLoader} for a Java binary name.
    /** The canonical loader is the one belonging to the QoreProgram that owns the
        underlying QoreClass — every consumer Program then references the same Class
        object regardless of which loader observed the name first.  Handles both shapes:
        - `qoremod.<mod>.<X>` is resolved by module name (the owning user module's Program)
        - `qore.<X>.<Y>...` is converted to a Qore qpath (`::X::Y::...`), looked up in the
          calling loader's Program, and routed to `qc.getProgram()` of the matching QoreClass

        Lazily attaches JniExternalProgramData (and thus a QoreURLClassLoader) to the
        owning Program if it doesn't yet have one.  Returns nullptr when no canonical
        owner can be determined (binary modules with no owning user Program, names that
        don't resolve to a QoreClass in the calling Program, etc.); callers fall back to
        local generation in that case.

        @param env JNI Env
        @param this_loader the calling QoreURLClassLoader (used to find the calling Program
                           for the qpath lookup); may be nullptr to skip the qpath path
        @param bin_name the Java binary name being resolved
    */
    DLLLOCAL static jobject getCanonicalLoader(Env& env, jobject this_loader, const char* bin_name);

    //! Returns the QoreURLClassLoader of the named user module's owning Program.
    /** User modules (.qm files) live in a dedicated QoreProgram that is the canonical home for
        their %Qore classes — and thus for the corresponding `qoremod.<mod>.*` Java classes.
        Routing every defineClass / loadClass for `qoremod.<mod>.*` through this single loader
        is what keeps the class identity consistent across consumer Programs and avoids
        cross-loader LinkageError on subclass/override resolution.

        @param name the module name
        @return the loader (a global jobject) or nullptr if the module is not loaded as a user
                module or has no JNI external data attached
    */
    DLLLOCAL static jobject getModuleClassLoader(const char* name);

private:
    DLLLOCAL static ExceptionSink global_xsink;
    DLLLOCAL static std::unique_ptr<QoreProgramHelper> qph;

    DLLLOCAL static bool already_initialized;
    DLLLOCAL static std::atomic<bool> jni_shutting_down;

    DLLLOCAL static void defineQoreURLClassLoader(Env& env);
};

class QoreJniStackLocationHelper : public QoreExternalRuntimeStackLocationHelper {
public:
    DLLLOCAL QoreJniStackLocationHelper();

    //! returns the name of the function or method call
    DLLLOCAL virtual const std::string& getCallName() const;

    //! returns the call type
    DLLLOCAL virtual qore_call_t getCallType() const;

    //! returns the source location of the element
    DLLLOCAL virtual const QoreProgramLocation& getLocation() const;

    //! returns the next location in the stack or nullptr if there is none
    DLLLOCAL virtual const QoreStackLocation* getNext() const;

private:
    int tid = q_gettid();
    mutable jsize current = 0;

    mutable std::vector<std::string> stack_call;
    mutable std::vector<bool> stack_native;
    mutable std::vector<QoreExternalProgramLocationWrapper> stack_loc;

    mutable bool init = false;

    DLLLOCAL static std::string jni_no_call_name;
    DLLLOCAL static QoreExternalProgramLocationWrapper jni_loc_builtin;

    DLLLOCAL size_t size() const {
        return stack_call.size();
    }

    DLLLOCAL void checkInit() const;
};

// find the root namespace for the given module in the given QoreProgram
DLLLOCAL const QoreNamespace* get_module_root_ns(const char* name, QoreProgram* mod_pgm);

// find a subnamespace with "::" delimited paths; returns nullptr if not found
DLLLOCAL const QoreNamespace* find_ns_path(const QoreNamespace* ns, const char* ns_path);

} // namespace jni

// callled from the QoireURLClassLoader's constructor when used as the system classloader
DLLLOCAL QoreStringNode* jni_module_init_finalize(bool system = false);

#endif // QORE_JNI_GLOBALS_H_
