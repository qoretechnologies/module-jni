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
#include <condition_variable>
#include <mutex>

DLLLOCAL QoreStringNode* jni_module_init_intern();

#define QORE_JNI_MODULE_NAME "jni"

namespace jni {

class JniContextProgramHolder;

// Registered class binding handle, used for construction and native type lookup
constexpr const char* JAVA_QORE_CLASS_FIELD = "$qore_cls_handle";
// Canonical owner Program ID, retained as descriptive class identity metadata
constexpr const char* JAVA_QORE_CLASS_PGM_ID_FIELD = "$qore_cls_pgm_id";
// qpath of the class within the owner Program (e.g. "::OMQ::UserApi::Job::QorusJob")
constexpr const char* JAVA_QORE_CLASS_PATH_FIELD = "$qore_cls_path";

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
    DLLLOCAL static jmethodID methodClassGetMethods;                              // Method[] Class.getMethods()
    DLLLOCAL static jmethodID methodClassGetCanonicalName;                        // String Class.getCanonicalName()
    DLLLOCAL static jmethodID methodClassGetDeclaredField;                        // Field Class.getField()
    DLLLOCAL static jmethodID methodClassIsAssignableFrom;                        // boolean Class.isAsignableFrom(Class)
    DLLLOCAL static jmethodID methodClassGetMethod;                               // Method Class.getMethod(String, Class[])

    DLLLOCAL static GlobalReference<jclass> classThrowable;                       // java.lang.Throwable
    DLLLOCAL static jmethodID methodThrowableGetMessage;                          // String Throwable.getMessage()
    DLLLOCAL static jmethodID methodThrowableGetStackTrace;                       // String Throwable.getStackTrace()
    DLLLOCAL static jmethodID methodThrowableGetCause;                            // String Throwable.getCause()

    // the classes of the Java exceptions that native methods raise for errors; looked up when the module is
    // initialized, so that raising one never needs a class lookup, which can fail (qore#6739; see jni_throw_new());
    // global references that are never deleted (these bootstrap classes are never unloaded), so that a native method
    // that runs while the module is cleaned up on another thread never uses a deleted reference
    DLLLOCAL static std::atomic<jclass> classError;                               // java.lang.Error
    DLLLOCAL static std::atomic<jclass> classRuntimeException;                    // java.lang.RuntimeException
    DLLLOCAL static std::atomic<jclass> classOutOfMemoryError;                    // java.lang.OutOfMemoryError
    DLLLOCAL static std::atomic<jclass> classClassNotFoundException;              // java.lang.ClassNotFoundException

    //! Looks up and keeps a class of the Java exceptions that native methods raise, once (see classError)
    DLLLOCAL static void cacheErrorClass(Env& env, std::atomic<jclass>& cls, const char* name);

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
    DLLLOCAL static GlobalReference<jclass> classQoreSandbox;                     // org.qore.jni.QoreSandbox
    DLLLOCAL static jmethodID methodQoreSandboxResolveClasspath;                  // static QoreURLClassLoader.ResolvedClasspath resolveClasspath(String, int)
    DLLLOCAL static jmethodID methodQoreSandboxExpandClasspath;                   // static void expandClasspath(ResolvedClasspath, int)
    DLLLOCAL static jmethodID methodQoreJavaApiGetStackTrace;                     // StackTraceElement[] getStackTrace()
    DLLLOCAL static jmethodID methodQoreJavaApiGetStackProgramPtrs;               // long[] getStackProgramPtrs()
    DLLLOCAL static GlobalReference<jclass> classJavaCollectionTypes;             // org.qore.jni.JavaCollectionTypes
    DLLLOCAL static jmethodID methodJavaCollectionTypesIsStringKeyMap;            // boolean isStringKeyMap(Object)
    DLLLOCAL static jmethodID methodJavaCollectionTypesMayBeListOrMap;            // boolean mayBeListOrMap(Class)
    DLLLOCAL static jmethodID methodJavaCollectionTypesGetDepth;                  // int getDepth(Executable)

    DLLLOCAL static GlobalReference<jclass> classQoreExceptionWrapper;            // org.qore.jni.QoreExceptionWrapper
    DLLLOCAL static jmethodID ctorQoreExceptionWrapper;                           // QoreExceptionWrapper(long)
    DLLLOCAL static jmethodID methodQoreExceptionWrapperGet;                      // long QoreExceptionWrapper.get()

    DLLLOCAL static GlobalReference<jclass> classQoreException;                   // org.qore.jni.QoreException
    DLLLOCAL static jmethodID methodQoreExceptionGetErr;                          // String QoreException.getErr()
    DLLLOCAL static jmethodID methodQoreExceptionGetDesc;                         // String QoreException.getDesc()
    DLLLOCAL static jmethodID methodQoreExceptionGetArg;                          // String QoreException.getArg()

    DLLLOCAL static GlobalReference<jclass> classQoreObjectBase;                  // org.qore.jni.QoreObjectBase
    DLLLOCAL static jmethodID methodQoreObjectBaseGet;                            // long get()
    DLLLOCAL static jmethodID methodQoreObjectBaseEnterHandle;                    // long qoreJniEnterHandle()
    DLLLOCAL static jmethodID methodQoreObjectBaseExitHandle;                     // void qoreJniExitHandle()

    DLLLOCAL static GlobalReference<jclass> classQoreJavaClassBase;               // org.qore.jni.QoreJavaClassBase

    DLLLOCAL static GlobalReference<jclass> classQoreObject;                      // org.qore.jni.QoreObject
    DLLLOCAL static jmethodID ctorQoreObject;                                     // QoreObject(long)

    DLLLOCAL static GlobalReference<jclass> classQoreJavaObjectPtr;               // org.qore.jni.QoreJavaObjectPtr
    DLLLOCAL static jmethodID ctorQoreJavaObjectPtr;                              // QoreJavaObjectPtr(long)

    DLLLOCAL static GlobalReference<jclass> classQoreClosure;                     // org.qore.jni.QoreClosure
    DLLLOCAL static jmethodID ctorQoreClosure;                                    // QoreClosure(long)
    DLLLOCAL static jmethodID methodQoreClosureGet;                               // long QoreClosure.get()
    DLLLOCAL static jmethodID methodQoreClosureEnterHandle;                       // long qoreJniEnterHandle()
    DLLLOCAL static jmethodID methodQoreClosureExitHandle;                        // void qoreJniExitHandle()

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
    DLLLOCAL static jmethodID methodQoreURLClassLoaderAddResolvedPath;            // void QoreURLClassLoader.addResolvedPath(ResolvedClasspath)
    DLLLOCAL static GlobalReference<jclass> classQoreURLClassLoaderResolvedClasspath; // org.qore.jni.QoreURLClassLoader$ResolvedClasspath
    DLLLOCAL static jmethodID methodResolvedClasspathSize;                        // int ResolvedClasspath.size()
    DLLLOCAL static jmethodID methodResolvedClasspathEncodedPath;                 // byte[] ResolvedClasspath.encodedPath(int)
    DLLLOCAL static jmethodID methodResolvedClasspathIsWildcardMatch;             // boolean ResolvedClasspath.isWildcardMatch(int)
    DLLLOCAL static jmethodID methodResolvedClasspathManifestReferrer;            // String ResolvedClasspath.manifestReferrer(int)
    DLLLOCAL static jmethodID methodResolvedClasspathRefusal;                     // String ResolvedClasspath.refusal(int)
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
    DLLLOCAL static jmethodID methodQoreURLClassLoaderClearContext;               // void clearContext()
    DLLLOCAL static jmethodID methodQoreURLClassLoaderRegisterSharedDynamicClass; // void registerSharedDynamicClassIntern(String)

    DLLLOCAL static GlobalReference<jclass> classJavaClassBuilder;                // org.qore.jni.JavaClassBuilder
    DLLLOCAL static jmethodID methodJavaClassBuilderGetFunctionConstantClassBuilder; // static DynamicType.Builder<?> getFunctionConstantClassBuilder(String bin_name)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddFunction;                  // static DynamicType.Builder<?> addFunction(DynamicType.Builder<?>, String, long, long, long, TypeDefinition, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddStaticField;               // static DynamicType.Builder<?> addStaticField(DynamicType.Builder<?>, String, int, TypeDescription, long, ArrayList)
    DLLLOCAL static jmethodID methodJavaClassBuilderCreateStaticInitializer;      // static DynamicType.Builder<?> createStaticInitializer(DynamicType.Builder<?>, String, long, ArrayList)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetClassBuilder;              // static DynamicType.Builder<?> getClassBuilder(String, Class<?>, TypeDefinition, ArrayList<TypeDefinition>, ArrayList<String>, boolean, long)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddConstructor;               // static DynamicType.Builder<?> addConstructor(DynamicType.Builder<?>, Class<?>, long, long, int, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddNormalMethod;              // static DynamicType.Builder<?> addNormalMethod(DynamicType.Builder<?>, String, long, long, int, TypeDefinition, List<TypeDefinition>, boolean, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddStaticMethod;              // static DynamicType.Builder<?> addStaticMethod(DynamicType.Builder<?>, String, long, long, long, int, TypeDefinition, List<TypeDefinition>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetByteCodeFromBuilder;       // static byte[] getByteCodeFromBuilder(DynamicType.Builder<?>, QoreURLClassLoader)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeDescriptionCls;        // static TypeDescription getTypeDescription(Class<?>)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeDescriptionStr;        // static TypeDescription getTypeDescription(String)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeDescriptionGenericStr; // static TypeDescription getTypeDescription(String, ArrayList<String>)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetTypeVariable;              // static TypeDefinition getTypeVariable(String)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetParameterizedType;         // static TypeDefinition getParameterizedType(TypeDescription, List<TypeDefinition>)
    DLLLOCAL static jmethodID methodJavaClassBuilderFindBaseClassMethodConflict;  // static boolean findBaseClassMethodConflict(Class<?>, String, List<TypeDescription>, boolean)
    DLLLOCAL static jmethodID methodJavaClassBuilderGetOverridableMethods;        // static Method[] getOverridableMethods(Class<?>, String, int)
    DLLLOCAL static jmethodID methodJavaClassBuilderAddOverridingMethod;          // static DynamicType.Builder<?> addOverridingMethod(DynamicType.Builder<?>, long, long, int, Method)
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
    DLLLOCAL static jmethodID methodZonedDateTimeFormat;                          // ZonedDateTime.format()
    DLLLOCAL static GlobalReference<jobject> isoOffsetDateTimeFormatter;          // DateTimeFormatter.ISO_OFFSET_DATE_TIME

    DLLLOCAL static GlobalReference<jclass> classLocalDateTime;                   // java.time.LocalDateTime
    DLLLOCAL static jmethodID methodLocalDateTimeToString;                        // LocalDateTime.toString()

    DLLLOCAL static GlobalReference<jclass> classInstant;                         // java.time.Instant
    DLLLOCAL static jmethodID methodInstantToString;                              // Instant.toString()

    DLLLOCAL static jmethodID methodZonedDateTimeToOffsetDateTime;                // ZonedDateTime.toOffsetDateTime()
    DLLLOCAL static jmethodID methodZonedDateTimeToLocalDateTime;                 // ZonedDateTime.toLocalDateTime()
    DLLLOCAL static jmethodID methodZonedDateTimeToLocalDate;                     // ZonedDateTime.toLocalDate()
    DLLLOCAL static jmethodID methodZonedDateTimeToLocalTime;                     // ZonedDateTime.toLocalTime()
    DLLLOCAL static jmethodID methodZonedDateTimeToInstant;                       // ZonedDateTime.toInstant()

    DLLLOCAL static GlobalReference<jclass> classOffsetDateTime;                  // java.time.OffsetDateTime
    DLLLOCAL static jmethodID methodOffsetDateTimeFormat;                         // OffsetDateTime.format()
    DLLLOCAL static jmethodID methodOffsetDateTimeToOffsetTime;                   // OffsetDateTime.toOffsetTime()

    DLLLOCAL static GlobalReference<jclass> classOffsetTime;                      // java.time.OffsetTime
    DLLLOCAL static jmethodID methodOffsetTimeFormat;                             // OffsetTime.format()
    DLLLOCAL static GlobalReference<jobject> isoOffsetTimeFormatter;              // DateTimeFormatter.ISO_OFFSET_TIME

    DLLLOCAL static GlobalReference<jclass> classLocalDate;                       // java.time.LocalDate
    DLLLOCAL static jmethodID methodLocalDateToString;                            // LocalDate.toString()

    DLLLOCAL static GlobalReference<jclass> classLocalTime;                       // java.time.LocalTime
    DLLLOCAL static jmethodID methodLocalTimeFormat;                              // LocalTime.format()
    DLLLOCAL static GlobalReference<jobject> isoLocalTimeFormatter;               // DateTimeFormatter.ISO_LOCAL_TIME

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
    DLLLOCAL static jmethodID methodConnectionCreateStatement;                    // Statement createStatement()
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

    // the methods of java.sql.Statement, which java.sql.PreparedStatement extends, are called on both
    DLLLOCAL static GlobalReference<jclass> classStatement;                       // java.sql.Statement
    DLLLOCAL static jmethodID methodStatementClose;                               // void close()
    DLLLOCAL static jmethodID methodStatementExecute;                             // boolean execute(String)
    DLLLOCAL static jmethodID methodStatementGetResultSet;                        // ResultSet getResultSet()
    DLLLOCAL static jmethodID methodStatementGetMoreResults;                      // boolean getMoreResults()
    DLLLOCAL static jmethodID methodStatementGetUpdateCount;                      // int getUpdateCount()

    DLLLOCAL static GlobalReference<jclass> classPreparedStatement;               // java.sql.PreparedStatement
    DLLLOCAL static jmethodID methodPreparedStatementAddBatch;                    // void addBatch()
    DLLLOCAL static jmethodID methodPreparedStatementClearBatch;                  // void clearBatch()
    DLLLOCAL static jmethodID methodPreparedStatementExecute;                     // boolean execute()
    DLLLOCAL static jmethodID methodPreparedStatementExecuteBatch;                // int[] executeBatch()
    DLLLOCAL static jmethodID methodPreparedStatementSetArray;                    // void setArray(int, Array)
    DLLLOCAL static jmethodID methodPreparedStatementSetBigDecimal;               // void setBigDecimal(int, BigDecimal)
    DLLLOCAL static jmethodID methodPreparedStatementSetBoolean;                  // void setBoolean(int, boolean)
    DLLLOCAL static jmethodID methodPreparedStatementSetBytes;                    // void setBytes(int, byte[])
    DLLLOCAL static jmethodID methodPreparedStatementSetDouble;                   // void setBoolean(int, double)
    DLLLOCAL static jmethodID methodPreparedStatementSetInt;                      // void setInt(int, int)
    DLLLOCAL static jmethodID methodPreparedStatementSetLong;                     // void setLong(int, long)
    DLLLOCAL static jmethodID methodPreparedStatementSetNull;                     // void setNull(int, int)
    DLLLOCAL static jmethodID methodPreparedStatementSetString;                   // void setString(int, String)
    DLLLOCAL static jmethodID methodPreparedStatementSetTimestamp;                // void setTimestamp(int, Timestamp)

    DLLLOCAL static GlobalReference<jclass> classTimestamp;                       // java.sql.Timestamp
    DLLLOCAL static jmethodID ctorTimestamp;                                      // Timestamp(long)
    DLLLOCAL static jmethodID methodTimestampSetNanos;                            // void setNanos(int)
    DLLLOCAL static jmethodID methodTimestampToString;                            // String toString()
    DLLLOCAL static jmethodID methodTimestampFrom;                                // static Timestamp from(Instant)

    DLLLOCAL static GlobalReference<jclass> classDate;                            // java.sql.Date
    DLLLOCAL static jmethodID methodDateToString;                                 // String toString()
    DLLLOCAL static jmethodID methodDateValueOf;                                  // static Date valueOf(LocalDate)

    DLLLOCAL static GlobalReference<jclass> classTime;                            // java.sql.Time
    DLLLOCAL static jmethodID methodTimeToString;                                 // String toString()
    DLLLOCAL static jmethodID methodTimeValueOf;                                  // static Time valueOf(LocalTime)

    DLLLOCAL static GlobalReference<jclass> classResultSet;                       // java.sql.ResultSet
    DLLLOCAL static jmethodID methodResultSetClose;                               // void close()
    DLLLOCAL static jmethodID methodResultSetGetArray;                            // Array getArray(int)
    DLLLOCAL static jmethodID methodResultSetGetBigDecimal;                       // BigDecimal getBigDecimal(int)
    DLLLOCAL static jmethodID methodResultSetGetBoolean;                          // boolean getBoolean(int)
    DLLLOCAL static jmethodID methodResultSetGetByte;                             // byte getByte(int)
    DLLLOCAL static jmethodID methodResultSetGetDouble;                           // double getDouble(int)
    DLLLOCAL static jmethodID methodResultSetGetFloat;                            // float getFloat(int)
    DLLLOCAL static jmethodID methodResultSetGetInt;                              // int getInt(int)
    DLLLOCAL static jmethodID methodResultSetGetLong;                             // long getLong(int)
    DLLLOCAL static jmethodID methodResultSetGetObject;                           // Object getObject(int)
    DLLLOCAL static jmethodID methodResultSetGetShort;                            // short getShort(int)
    DLLLOCAL static jmethodID methodResultSetGetString;                           // String getString(int)
    DLLLOCAL static jmethodID methodResultSetGetTimestamp;                        // Timestamp getTimestamp(int)
    DLLLOCAL static jmethodID methodResultSetGetObjectAs;                         // Object getObject(int, Class)
    DLLLOCAL static jmethodID methodResultSetNext;                                // boolean next()
    DLLLOCAL static jmethodID methodResultSetWasNull;                             // boolean wasNull()
    DLLLOCAL static jmethodID methodResultSetGetMetaData;                         // ResultSetMetaData getMetaData()

    DLLLOCAL static GlobalReference<jclass> classResultSetMetaData;               // java.sql.ResultSetMetadata
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnClassName;          // String getColumnClassName()
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnCount;              // int getColumnCount()
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnLabel;              // String getColumnLabel(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnType;               // int getColumnType(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataGetColumnTypeName;           // String getColumnTypeName(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataGetPrecision;                // int getPrecision(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataGetScale;                    // int getScale(int)
    DLLLOCAL static jmethodID methodResultSetMetaDataIsNullable;                  // int isNullable(int)

    DLLLOCAL static GlobalReference<jclass> classJdbcColumnarBatch;               // org.qore.jni.JdbcColumnarBatch
    DLLLOCAL static jmethodID methodJdbcColumnarBatchRead;                        // static JdbcColumnarBatch read()
    DLLLOCAL static jfieldID fieldJdbcColumnarBatchRows;                          // int rows
    DLLLOCAL static jfieldID fieldJdbcColumnarBatchData;                          // Object[] data
    DLLLOCAL static jfieldID fieldJdbcColumnarBatchValidity;                      // byte[][] validity
    DLLLOCAL static jfieldID fieldJdbcColumnarBatchNullCounts;                    // long[] nullCounts

    DLLLOCAL static GlobalReference<jclass> classBlob;                            // java.sql.Blob
    DLLLOCAL static jmethodID methodBlobLength;                                   // long length()
    DLLLOCAL static jmethodID methodBlobGetBytes;                                 // byte[] getBytes(long, int)
    DLLLOCAL static jmethodID methodBlobFree;                                     // void free()
    DLLLOCAL static GlobalReference<jclass> classClob;                            // java.sql.Clob
    DLLLOCAL static jmethodID methodClobLength;                                   // long length()
    DLLLOCAL static jmethodID methodClobGetSubString;                             // String getSubString(long, int)
    DLLLOCAL static jmethodID methodClobFree;                                     // void free()
    DLLLOCAL static GlobalReference<jclass> classSQLRecoverableException;          // java.sql.SQLRecoverableException
    DLLLOCAL static GlobalReference<jclass> classSQLNonTransientConnectionException; // java.sql.SQLNonTransientConnectionException
    DLLLOCAL static GlobalReference<jclass> classSQLTransientConnectionException; // java.sql.SQLTransientConnectionException
    DLLLOCAL static GlobalReference<jclass> classSQLTimeoutException;             // java.sql.SQLTimeoutException
    DLLLOCAL static jmethodID methodSQLExceptionGetSQLState;                      // String getSQLState()
    DLLLOCAL static GlobalReference<jclass> classUtilDate;                        // java.util.Date
    DLLLOCAL static jmethodID methodUtilDateGetTime;                              // long getTime()
    DLLLOCAL static jmethodID methodTimestampGetNanos;                            // int getNanos()

    DLLLOCAL static GlobalReference<jclass> classArray;                           // java.sql.Array
    DLLLOCAL static jmethodID methodArrayGetArray;                                // Object getArray()

    DLLLOCAL static GlobalReference<jclass> classSQLException;                    // java.sql.SQLException

    DLLLOCAL static GlobalReference<jclass> classServiceLoader;                   // java.util.ServiceLoader
    DLLLOCAL static jmethodID methodServiceLoaderIterator;                        // Iterator iterator()

    DLLLOCAL static GlobalReference<jclass> classDriver;                          // java.sql.Driver

    DLLLOCAL static int typeBigInt;                 // java.sql.Type.BIGINT value
    DLLLOCAL static int typeBinary;                 // java.sql.Type.BINARY value
    DLLLOCAL static int typeBit;                    // java.sql.Type.BIT value
    DLLLOCAL static int typeBoolean;                // java.sql.Type.BOOLEAN value
    DLLLOCAL static int typeChar;                   // java.sql.Type.CHAR value
    DLLLOCAL static int typeDate;                   // java.sql.Type.DATE value
    DLLLOCAL static int typeDecimal;                // java.sql.Type.DECIMAL value
    DLLLOCAL static int typeDouble;                 // java.sql.Type.DOUBLE value
    DLLLOCAL static int typeFloat;                  // java.sql.Type.FLOAT value
    DLLLOCAL static int typeInteger;                // java.sql.Type.INTEGER value
    DLLLOCAL static int typeLongNVarchar;           // java.sql.Type.LONGNVARCHAR value
    DLLLOCAL static int typeLongVarbinary;          // java.sql.Type.LONGVARBINARY value
    DLLLOCAL static int typeLongVarchar;            // java.sql.Type.LONGVARCHAR value
    DLLLOCAL static int typeNChar;                  // java.sql.Type.NCHAR value
    DLLLOCAL static int typeNull;                   // java.sql.Type.NULL value
    DLLLOCAL static int typeNumeric;                // java.sql.Type.NUMERIC value
    DLLLOCAL static int typeNVarchar;               // java.sql.Type.NVARCHAR value
    DLLLOCAL static int typeReal;                   // java.sql.Type.REAL value
    DLLLOCAL static int typeSmallInt;               // java.sql.Type.SMALLINT value
    DLLLOCAL static int typeTime;                   // java.sql.Type.TIME value
    DLLLOCAL static int typeTimeWithTimezone;       // java.sql.Type.TIME_WITH_TIMEZONE value
    DLLLOCAL static int typeTimestamp;              // java.sql.Type.TIMESTAMP value
    DLLLOCAL static int typeTimestampWithTimezone;  // java.sql.Type.TIMESTAMP_WITH_TIMEZONE value
    DLLLOCAL static int typeTinyInt;                // java.sql.Type.TINYINT value
    DLLLOCAL static int typeVarbinary;              // java.sql.Type.VARBINARY value
    DLLLOCAL static int typeVarchar;                // java.sql.Type.VARCHAR value
    DLLLOCAL static int typeRowId;                  // java.sql.Type.ROWID value
    DLLLOCAL static int typeBlob;                   // java.sql.Type.BLOB value
    DLLLOCAL static int typeClob;                   // java.sql.Type.CLOB value
    DLLLOCAL static int typeNClob;                  // java.sql.Type.NCLOB value

    DLLLOCAL static GlobalReference<jstring> javaQoreClassField;
    DLLLOCAL static GlobalReference<jstring> javaQoreClassPgmIdField;
    DLLLOCAL static GlobalReference<jstring> javaQoreClassPathField;

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
    /** Closes registration, snapshots live handles, wakes and joins the cleanup
        thread, then releases outstanding native pointers. Native clients must
        be quiescent. Called from Jvm::destroyVM before DestroyJavaVM.

        Throws a C++ exception if it fails (ex: a jni::Exception if the thread cannot be attached to the JVM); the
        worker is then
        abandoned: a reference that wakes it afterwards is dropped instead of dispatched (qore#6788). */
    DLLLOCAL static void stopNativeCleanupThread();

    //! Returns the global context Program, creating it if necessary, as a \c jlong, or 0 once the module's shutdown
    //! has started (see createJavaContextProgram())
    DLLLOCAL static jlong getContextProgram(jobject new_syscl, bool& created);

    //! Creates the global context Program if it does not exist, and returns it
    /** Returns nullptr once the module's shutdown has started (isShuttingDown()): the global context Program is
        deleted then (clearGlobalContext()), but Java threads still run until the JVM is destroyed, and a call from
        one of them with no Program context must not create it again, as it would never be deleted; throws a C++
        exception if the Program cannot be created
    */
    DLLLOCAL static QoreProgram* createJavaContextProgram();

    //! Returns the global context Program, creating it if necessary, held by \a holder; nullptr once the module's
    //! shutdown has started
    /** The Program is not deleted while \a holder holds it (see JniContextProgramHolder; qore#6829); see
        createJavaContextProgram(); a caller that needs a Program uses jni_get_context_unconditional(), which raises
        an exception instead; an existing Program is returned with no lock taken
    */
    DLLLOCAL static QoreProgram* getJavaContextProgram(JniContextProgramHolder& holder);

    //! Deletes the global context Program and reports the exceptions raised by its creation and deletion
    /** The exceptions are reported here, while the library is still initialized: the destructors of the objects in
        the Program run when it is deleted, and they can raise exceptions (ex: \c PROGRAM-ERROR if the Program of
        their class is gone).  An exception left in the sink would otherwise be reported only when the process exits,
        after qore_cleanup() has destroyed the thread data that reporting it uses.

        The module's shutdown starts here (see isShuttingDown()): the flag is set under the lock that the creation of
        the global context Program takes, so no Program is created after it, also by a Java thread that calls into
        Qore while the JVM still runs (createJavaContextProgram()); the Program is deleted outside the lock, as its
        deletion can call back into the module.

        The Program is deleted once no other thread holds it (JniContextProgramHolder): a thread that obtained it
        before the shutdown started can still be using it (qore#6829), and no thread can obtain it once the flag is
        set.  If the calling thread holds it itself, it cannot wait for itself, and the Program is then left
        allocated, as abandonGlobalContext() leaves it, instead of being deleted under that thread.
    */
    DLLLOCAL static void clearGlobalContext();

#ifdef DEBUG
    //! The number of global context Programs created by createJavaContextProgram() in the process (debug builds
    //! only); see Jni::dbg_jni_context_program_counts() and dbg_jni_report_context_programs() (qore#6799)
    /** A Program whose creation fails is not counted; the one that abandonGlobalContext() abandons at process exit is
        counted as created, but not as deleted (the report is printed by the module's delete function only, which
        deletes it with clearGlobalContext())
    */
    DLLLOCAL static std::atomic<int> dbg_context_programs_created;
    //! The number of global context Programs deleted by clearGlobalContext() in the process (debug builds only)
    DLLLOCAL static std::atomic<int> dbg_context_programs_deleted;
#endif

    //! Abandons the global context during process exit without running Qore destructors
    /** C atexit ordering cannot guarantee that function-local libqore synchronization
        objects are still alive.  The operating system reclaims this process-owned
        Program immediately after JVM teardown.  As clearGlobalContext(), it starts the module's shutdown, after
        which no global context Program is created.
    */
    DLLLOCAL static void abandonGlobalContext();

    //! Returns the global context Program if it exists, without creating it; only for comparing the pointer
    DLLLOCAL static const QoreProgram* peekContextProgram() {
        return context_pgm.load(std::memory_order_acquire);
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

    //! Removes all entries from the canonical loader cache that reference @a loader.
    /** Must be called before the loader's JNI global reference is deleted (i.e. before
        JniExternalProgramData destruction finishes) to prevent dangling jobject pointers
        in the static cache.
    */
    DLLLOCAL static void removeCanonicalLoaderCacheEntries(jobject loader);

private:
    //! Stops the NativeCleanup thread; see stopNativeCleanupThread()
    DLLLOCAL static void stopNativeCleanupThreadIntern();

    //! Returns the exception sink of the global context Program, which receives the exceptions raised when it is
    //! created and deleted
    /** The sink is allocated once and never destroyed: an ExceptionSink with static storage duration reports the
        exceptions that it holds when static objects are destroyed at process exit, after qore_cleanup() has freed the
        thread data that reporting them needs, which crashed the process (currentTZ() with no thread data);
        clearGlobalContext() reports and clears them while the library is initialized
    */
    DLLLOCAL static ExceptionSink& getGlobalXsink();

    DLLLOCAL static std::unique_ptr<QoreProgramHelper> qph;
    //! The global context Program in qph, or nullptr, for the lookup with no lock in getJavaContextProgram(); set and
    //! cleared under getContextLock()
    DLLLOCAL static std::atomic<QoreProgram*> context_pgm;
    //! Returns the lock that serializes the creation of the global context Program (qph) with the start of the
    //! module's shutdown
    /** Recursive, as the creation of the Program can look it up again on the same thread; never destroyed, as
        abandonGlobalContext() takes it in an \c atexit() handler, which can run after the objects with static storage
        duration are destroyed
    */
    DLLLOCAL static std::recursive_mutex& getContextLock();

    //! The number of JniContextProgramHolder objects that hold the global context Program, in all threads
    /** Incremented before the shutdown flag is checked, and the flag is set before clearGlobalContext() reads it,
        both sequentially consistent, so that either the holder sees the flag and holds nothing, or
        clearGlobalContext() sees the holder and waits for it (qore#6829)
    */
    DLLLOCAL static std::atomic<int> context_holders;

    //! Returns the lock of the condition that clearGlobalContext() waits on for the holders of the global context
    //! Program (getContextHolderCond()); never destroyed, as getContextLock()
    DLLLOCAL static std::mutex& getContextHolderLock();

    //! Returns the condition signaled when a holder of the global context Program releases it during the module's
    //! shutdown; never destroyed, as getContextLock()
    DLLLOCAL static std::condition_variable& getContextHolderCond();

    //! Counts a holder of the global context Program in this thread; see context_holders
    DLLLOCAL static void holdContextProgram() noexcept;

    //! Releases a holder of the global context Program in this thread, and wakes clearGlobalContext() if it waits
    //! for the holders
    DLLLOCAL static void releaseContextProgram() noexcept;

    //! Waits until no other thread holds the global context Program, and returns the number of holders of this
    //! thread, which it cannot wait for
    DLLLOCAL static int waitForContextHolders();

    friend class JniContextProgramHolder;

    DLLLOCAL static bool already_initialized;
    DLLLOCAL static std::atomic<bool> jni_shutting_down;

    DLLLOCAL static void defineQoreURLClassLoader(Env& env);
};

//! Holds the global Java context Program, which the module's shutdown does not delete while it is held (qore#6829)
/** A caller that uses the global context Program (Globals::getJavaContextProgram(), jni_get_context_or_global(),
    jni_get_context_unconditional() and the functions that fall back to it) holds it with one of these for as long as
    it uses the Program, its jni data, or anything that the Program owns: Globals::clearGlobalContext() deletes the
    Program once no holder in another thread holds it.  A holder holds nothing once the module's shutdown has started,
    as no Program can be obtained then.

    The holder does not reference the Program, and does not delete it: the Program is deleted where it always was, by
    the thread that shuts the module down, which waits for the holders before it deletes it.  A holder must not be
    kept while waiting for another thread that can be shutting the module down; it is meant to live in the scope of
    one call.

    The shutdown waits for a holder as it waits for a thread that runs in the Program (the deletion of the Program
    waits for its threads to leave it): a call that holds the Program for a Java call that uses its class loader (ex:
    a call of a Java method from %Qore code with no Program context) delays the shutdown until the Java call
    returns, and a Java call that waits for the shutdown itself (ex: \c System.exit() in the JVM's shutdown hooks)
    never returns then.  A holder is therefore kept only for as long as the Program, its jni data or its class loader
    is used.
*/
class JniContextProgramHolder {
public:
    DLLLOCAL JniContextProgramHolder() = default;

    DLLLOCAL ~JniContextProgramHolder() {
        release();
    }

    JniContextProgramHolder(const JniContextProgramHolder&) = delete;
    JniContextProgramHolder& operator=(const JniContextProgramHolder&) = delete;

    //! Returns the global context Program, creating it if necessary, and holds it until release(); nullptr, holding
    //! nothing, once the module's shutdown has started
    /** Returns the Program already held, if any; throws a C++ exception if the Program cannot be created, holding
        nothing then
    */
    DLLLOCAL QoreProgram* acquire();

    //! Releases the Program, if held
    DLLLOCAL void release() noexcept {
        if (pgm) {
            pgm = nullptr;
            Globals::releaseContextProgram();
        }
    }

    //! Returns the Program held, or nullptr
    DLLLOCAL QoreProgram* get() const {
        return pgm;
    }

private:
    QoreProgram* pgm = nullptr;
};

class QoreJniStackLocationHelper;

//! One Java frame below the first of a QoreJniStackLocationHelper
/** Each Java frame is its own stack location, so walking the stack keeps no state: a walk that stops at a Java frame
    (runtime_get_parse_options_stack() returns at the frame it was asked for) leaves the next walk unchanged.
*/
class QoreJniJavaFrameLocation : public QoreExternalStackLocation {
public:
    DLLLOCAL QoreJniJavaFrameLocation(const QoreJniStackLocationHelper& helper, size_t index)
            : helper(helper), index(index) {
    }

    DLLLOCAL virtual QoreProgram* getProgram() const;
    DLLLOCAL virtual const AbstractStatement* getStatement() const;
    DLLLOCAL virtual const std::string& getCallName() const;
    DLLLOCAL virtual qore_call_t getCallType() const;
    DLLLOCAL virtual const QoreProgramLocation& getLocation() const;
    DLLLOCAL virtual const QoreStackLocation* getNext() const;

private:
    const QoreJniStackLocationHelper& helper;
    //! the frame's index in the helper's Java stack (1 or more)
    size_t index;
};

class QoreJniStackLocationHelper : public QoreExternalRuntimeStackLocationHelper {
    friend class QoreJniJavaFrameLocation;
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
    //! the Qore code that Java calls records its own Program when it calls Java in turn: the caller of the enclosing
    //! call from Qore into Java is cleared for it (qore#7023)
    JniJavaCallerScope caller_clear{JniJavaCallerScope::Clear};

    int tid = q_gettid();

    mutable std::vector<std::string> stack_call;
    mutable std::vector<bool> stack_native;
    mutable std::vector<QoreExternalProgramLocationWrapper> stack_loc;
    //! the Java frames after the first, which this location stands for
    mutable std::vector<std::unique_ptr<QoreJniJavaFrameLocation>> frames;

    mutable bool init = false;

    DLLLOCAL static std::string jni_no_call_name;
    DLLLOCAL static QoreExternalProgramLocationWrapper jni_loc_builtin;

    DLLLOCAL size_t size() const {
        return stack_call.size();
    }

    DLLLOCAL void checkInit() const;

    //! returns the location after the Java frames
    DLLLOCAL const QoreStackLocation* getStackNext() const {
        return stack_next;
    }
};

// find the root namespace for the given module in the given QoreProgram
DLLLOCAL const QoreNamespace* get_module_root_ns(const char* name, QoreProgram* mod_pgm);

// find the root namespace of the given module from the class's own namespace chain
/** get_module_root_ns() answers the same question by searching a QoreProgram's namespace tree, so
    it can only see modules that the consumer Program actually imported.  A module's private
    (non-reexported) dependencies are not imported into the consumer - a source module keeps them in
    its own Program, and an AOT-compiled module has no Program at all - so for an AOT module's
    dependency the search fails in every Program and the class silently loses its canonical
    qoremod.<mod>.<rest> name.

    The namespace chain, by contrast, is a property of the class itself and gives the same answer in
    every context, which is what a canonical binary name requires.  Returns nullptr when the class
    does not live in a namespace contributed to by the module (a shadow / injection module's
    classes), where the legacy qore.<class-path> form is the correct name.
*/
DLLLOCAL const QoreNamespace* get_class_module_root_ns(const QoreClass& qc, const char* name);

// drop all cached module root namespaces for the given QoreProgram
/** get_module_root_ns() caches borrowed QoreNamespace pointers keyed by the raw QoreProgram
    pointer, so every entry for a Program dies with that Program.  This MUST be called before the
    Program's memory can be reused, or a later Program allocated at the same address inherits the
    dead one's entries and get_module_root_ns() hands back a dangling namespace.
*/
DLLLOCAL void purge_module_root_ns_cache(QoreProgram* mod_pgm);

// returns the number of Programs holding cached module root namespaces (diagnostics only)
DLLLOCAL size_t get_module_root_ns_cache_program_count();

// returns how many Programs have had cache entries purged (diagnostics only)
DLLLOCAL size_t get_module_root_ns_cache_purge_count();

#ifdef DEBUG
// returns how many module root namespace searches were made, or, with \a locked, how many of them were made while
// the searching thread held the cache lock (diagnostics only)
DLLLOCAL size_t get_module_root_ns_search_count(bool locked);
#endif

// find a subnamespace with "::" delimited paths; returns nullptr if not found
DLLLOCAL const QoreNamespace* find_ns_path(const QoreNamespace* ns, const char* ns_path);

//! Throws a new Java exception with a message in UTF-8, without C++ exceptions (qore#6739)
/** For the error paths of JNI native methods, which no C++ exception may leave: the class is one that the module looked
    up when it was initialized (ex: Globals::classError); jni::Env::findClass(), which throws a C++ exception if the
    lookup fails, is not used.  A pending Java exception is replaced.

    If the cached class is not available (before the module is initialized), the class is
    looked up with JNI's \c FindClass(); if that fails, the Java exception of the failure (ex: a
    \c NoClassDefFoundError or an \c OutOfMemoryError) is pending instead, which reports the error to the caller as
    well.  Only if no Java exception at all can be made pending, which JNI does not allow (a failed \c FindClass() or
    \c Throw() leaves an exception pending), is the process ended with JNI's \c FatalError(): returning from the
    native method with no exception pending would report a failed call to Java as a call that succeeded.

    @param jenv the JNI environment of the thread
    @param cls the class of the exception, from Globals, or nullptr
    @param cls_name the name of the class, for the lookup if \a cls is nullptr (ex: \c "java/lang/Error")
    @param msg the message in UTF-8; it may have NUL characters
    @param len the length of the message in bytes
*/
DLLLOCAL void jni_throw_new(JNIEnv* jenv, jclass cls, const char* cls_name, const char* msg, size_t len) noexcept;

//! Throws a new Java exception with a NUL-terminated message in UTF-8, without C++ exceptions; see jni_throw_new()
static inline void jni_throw_new(JNIEnv* jenv, jclass cls, const char* cls_name, const char* msg) noexcept {
    jni_throw_new(jenv, cls, cls_name, msg, strlen(msg));
}

//! Throws a new Java exception with a message in any encoding, without C++ exceptions; see jni_throw_new()
/** The message is converted to UTF-8 as by Env::throwNew(JNIEnv*, jclass, const QoreString&)
*/
DLLLOCAL void jni_throw_new(JNIEnv* jenv, jclass cls, const char* cls_name, const QoreString& msg) noexcept;

//! Throws a new \c java.lang.Error, without C++ exceptions; see jni_throw_new()
template <typename T>
static inline void jni_throw_error(JNIEnv* jenv, const T& msg) noexcept {
    jni_throw_new(jenv, Globals::classError.load(std::memory_order_acquire),
        "java/lang/Error", msg);
}

//! Throws a new \c java.lang.RuntimeException, without C++ exceptions; see jni_throw_new()
template <typename T>
static inline void jni_throw_runtime_exception(JNIEnv* jenv, const T& msg) noexcept {
    jni_throw_new(jenv, Globals::classRuntimeException.load(std::memory_order_acquire),
        "java/lang/RuntimeException", msg);
}

//! Throws a new \c java.lang.OutOfMemoryError, without C++ exceptions; see jni_throw_new()
template <typename T>
static inline void jni_throw_out_of_memory(JNIEnv* jenv, const T& msg) noexcept {
    jni_throw_new(jenv, Globals::classOutOfMemoryError.load(std::memory_order_acquire),
        "java/lang/OutOfMemoryError", msg);
}

//! Throws a new \c java.lang.ClassNotFoundException, without C++ exceptions; see jni_throw_new()
template <typename T>
static inline void jni_throw_class_not_found(JNIEnv* jenv, const T& msg) noexcept {
    jni_throw_new(jenv, Globals::classClassNotFoundException.load(std::memory_order_acquire),
        "java/lang/ClassNotFoundException", msg);
}

//! Raises the C++ exception being handled as a pending Java exception, without C++ exceptions (qore#6739)
/** Called only in a catch handler, by JNI_NATIVE_CATCH(); the exception is rethrown and raised in Java:
    - jni::JavaException: the Java exception is already pending, and is kept (\c java.lang.Error if none is pending)
    - any other jni::Exception (ex: jni::XsinkException, jni::QoreJniException) or other %Qore exception
      (\c AbstractException): the %Qore exception, as an \c org.qore.jni.QoreExceptionWrapper; the thread is
      attached to %Qore for the conversion if it is not attached, and if it cannot be attached, or %Qore or the
      module is shut down, a \c java.lang.Error is raised (and the %Qore exception is not freed)
    - \c std::bad_alloc: \c java.lang.OutOfMemoryError
    - any other \c std::exception: \c java.lang.Error with its message
    - any other C++ exception: \c java.lang.Error

    The C++ exception replaces a Java exception that is pending, other than for jni::JavaException.

    @param jenv the JNI environment of the thread
*/
DLLLOCAL void jni_native_exception(JNIEnv* jenv) noexcept;

//! Records a Qore class that the module's own code gives to QoreURLClassLoader.loadClassWithPtr() (qore#6854)
/** Java code must never supply a native class pointer: the byte code generator accepts a class pointer only if the
    module's own code gave it, on the current thread, during a call that is still running.  Create one around each call
    of loadClassWithPtr() that passes a class pointer.
*/
class JniIssuedClassHelper {
public:
    //! Issues the class pointer for the call that the module's code makes for the given Program
    DLLLOCAL JniIssuedClassHelper(const QoreClass* cls, const QoreProgram* pgm);
    DLLLOCAL ~JniIssuedClassHelper();

    //! Returns true if the module's own code gave the class pointer for the given Program for the innermost call that
    //! runs on the current thread
    /** Only the innermost issued class is accepted: a Java call made while the class is generated cannot use the
        class pointer of an outer call; and only for its Program: a class pointer that the module gave cannot be used
        to generate a class for another Program
    */
    DLLLOCAL static bool isIssued(const void* cls, const QoreProgram* pgm);

    //! Returns true if the module's own code gave the class pointer for the innermost call that runs on the current
    //! thread, for any Program
    DLLLOCAL static bool isIssued(const void* cls);

    DLLLOCAL JniIssuedClassHelper(const JniIssuedClassHelper&) = delete;
    DLLLOCAL JniIssuedClassHelper& operator=(const JniIssuedClassHelper&) = delete;
};

} // namespace jni

//! The top-level catch handler of a JNI native method: no C++ exception may leave a native method (qore#6739)
/** A C++ exception that leaves a JNI native method unwinds through JVM frames, which ends the process in
    \c std::terminate().  Every JNI native method of the module is a function-try-block that ends with this handler,
    which raises any C++ exception as a pending Java exception with jni::jni_native_exception() and returns the given
    value (none for a \c void method):
    @code{.cpp}
static jobject JNICALL my_native(JNIEnv* jenv, jclass cls) try {
    // ...
} JNI_NATIVE_CATCH(jenv, nullptr)
    @endcode

    The locals of the native method are destroyed before the handler runs.
*/
#define JNI_NATIVE_CATCH(jenv, ...) \
    catch (...) { \
        ::jni::jni_native_exception(jenv); \
        return __VA_ARGS__; \
    }

// callled from the QoireURLClassLoader's constructor when used as the system classloader
DLLLOCAL QoreStringNode* jni_module_init_finalize(bool system = false);

#endif // QORE_JNI_GLOBALS_H_
