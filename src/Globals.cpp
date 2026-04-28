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

#include "Globals.h"
#include "Env.h"
#include "Dispatcher.h"
#include "InvocationHandler.h"
#include "ModifiedUtf8String.h"
#include "Array.h"
#include "QoreToJava.h"
#include "QoreJniClassMap.h"

#include <thread>

#include <qore/ModuleManager.h>

#include <bzlib.h>
#include <dlfcn.h>
#include <mutex>

namespace jni {

// Qore initialization flag
bool jni_qore_init = false;
bool jni_qore_init_done = false;

ExceptionSink Globals::global_xsink;
std::unique_ptr<QoreProgramHelper> Globals::qph;

bool Globals::already_initialized = false;
std::atomic<bool> Globals::jni_shutting_down{false};

GlobalReference<jobject> Globals::syscl;
bool Globals::bootstrap = false;

GlobalReference<jclass> Globals::classPrimitiveVoid;
GlobalReference<jclass> Globals::classPrimitiveBoolean;
GlobalReference<jclass> Globals::classPrimitiveByte;
GlobalReference<jclass> Globals::classPrimitiveChar;
GlobalReference<jclass> Globals::classPrimitiveShort;
GlobalReference<jclass> Globals::classPrimitiveInt;
GlobalReference<jclass> Globals::classPrimitiveLong;
GlobalReference<jclass> Globals::classPrimitiveFloat;
GlobalReference<jclass> Globals::classPrimitiveDouble;
GlobalReference<jclass> Globals::arrayClassByte;
GlobalReference<jclass> Globals::arrayClassObject;

GlobalReference<jclass> Globals::classSystem;
jmethodID Globals::methodSystemSetProperty;
jmethodID Globals::methodSystemGetProperty;
jmethodID Globals::methodSystemGC;

GlobalReference<jclass> Globals::classObject;
jmethodID Globals::methodObjectClone;
jmethodID Globals::methodObjectGetClass;
jmethodID Globals::methodObjectEquals;
jmethodID Globals::methodObjectHashCode;
jmethodID Globals::methodObjectToString;

GlobalReference<jclass> Globals::classClass;
jmethodID Globals::methodClassIsArray;
jmethodID Globals::methodClassIsInterface;
jmethodID Globals::methodClassGetComponentType;
jmethodID Globals::methodClassGetClassLoader;
jmethodID Globals::methodClassGetName;
jmethodID Globals::methodClassGetDeclaredFields;
jmethodID Globals::methodClassGetSuperClass;
jmethodID Globals::methodClassGetInterfaces;
jmethodID Globals::methodClassGetDeclaredConstructors;
jmethodID Globals::methodClassGetDeclaredConstructor;
jmethodID Globals::methodClassGetModifiers;
jmethodID Globals::methodClassIsPrimitive;
jmethodID Globals::methodClassGetDeclaredMethods;
jmethodID Globals::methodClassGetCanonicalName;
jmethodID Globals::methodClassGetDeclaredField;
jmethodID Globals::methodClassIsAssignableFrom;
jmethodID Globals::methodClassGetMethod;

GlobalReference<jclass> Globals::classThrowable;
jmethodID Globals::methodThrowableGetMessage;
jmethodID Globals::methodThrowableGetStackTrace;
jmethodID Globals::methodThrowableGetCause;

GlobalReference<jclass> Globals::classStackTraceElement;
jmethodID Globals::methodStackTraceElementGetClassName;
jmethodID Globals::methodStackTraceElementGetFileName;
jmethodID Globals::methodStackTraceElementGetLineNumber;
jmethodID Globals::methodStackTraceElementGetMethodName;
jmethodID Globals::methodStackTraceElementIsNativeMethod;

GlobalReference<jclass> Globals::classString;

GlobalReference<jclass> Globals::classField;
jmethodID Globals::methodFieldGetDeclaringClass;
jmethodID Globals::methodFieldGetType;
jmethodID Globals::methodFieldGetModifiers;
jmethodID Globals::methodFieldGetName;
jmethodID Globals::methodFieldGet;
jmethodID Globals::methodFieldGetLong;
jmethodID Globals::methodFieldSetAccessible;

GlobalReference<jclass> Globals::classMethod;
jmethodID Globals::methodMethodGetReturnType;
jmethodID Globals::methodMethodGetParameterTypes;
jmethodID Globals::methodMethodGetDeclaringClass;
jmethodID Globals::methodMethodGetModifiers;
jmethodID Globals::methodMethodIsVarArgs;
jmethodID Globals::methodMethodGetName;
jmethodID Globals::methodMethodToGenericString;

GlobalReference<jclass> Globals::classConstructor;
jmethodID Globals::methodConstructorGetParameterTypes;
jmethodID Globals::methodConstructorToString;
jmethodID Globals::methodConstructorGetModifiers;
jmethodID Globals::methodConstructorIsVarArgs;
jmethodID Globals::methodConstructorNewInstance;

GlobalReference<jclass> Globals::classQoreInvocationHandler;
jmethodID Globals::ctorQoreInvocationHandler;
jmethodID Globals::methodQoreInvocationHandlerDestroy;

GlobalReference<jclass> Globals::classQoreJavaApi;
jmethodID Globals::methodQoreJavaApiGetStackTrace;

GlobalReference<jclass> Globals::classQoreExceptionWrapper;
jmethodID Globals::ctorQoreExceptionWrapper;
jmethodID Globals::methodQoreExceptionWrapperGet;

GlobalReference<jclass> Globals::classQoreException;
jmethodID Globals::methodQoreExceptionGetErr;
jmethodID Globals::methodQoreExceptionGetDesc;
jmethodID Globals::methodQoreExceptionGetArg;

GlobalReference<jclass> Globals::classQoreObjectBase;

GlobalReference<jclass> Globals::classQoreJavaClassBase;
jmethodID Globals::methodQoreObjectBaseGet;

GlobalReference<jclass> Globals::classQoreObject;
jmethodID Globals::ctorQoreObject;

GlobalReference<jclass> Globals::classQoreClosure;
jmethodID Globals::ctorQoreClosure;
jmethodID Globals::methodQoreClosureGet;

GlobalReference<jclass> Globals::classQoreObjectWrapper;

GlobalReference<jclass> Globals::classQoreClosureMarker;
GlobalReference<jclass> Globals::classQoreClosureMarkerImpl;

GlobalReference<jclass> Globals::classQoreJavaObjectPtr;
jmethodID Globals::ctorQoreJavaObjectPtr;

GlobalReference<jclass> Globals::classProxy;
jmethodID Globals::methodProxyNewProxyInstance;

GlobalReference<jclass> Globals::classClassLoader;
jmethodID Globals::methodClassLoaderLoadClass;

GlobalReference<jclass> Globals::classQoreURLClassLoader;
jmethodID Globals::ctorQoreURLClassLoader;
jmethodID Globals::methodQoreURLClassLoaderAddPath;
jmethodID Globals::methodQoreURLClassLoaderAddParentPath;
jmethodID Globals::methodQoreURLClassLoaderLoadClass;
jmethodID Globals::methodQoreURLClassLoaderLoadClassWithPtr;
jmethodID Globals::methodQoreURLClassLoaderLoadResolveClass;
jmethodID Globals::methodQoreURLClassLoaderSetContext;
jmethodID Globals::methodQoreURLClassLoaderGetProgramPtr;
jmethodID Globals::methodQoreURLClassLoaderAddPendingClass;
jmethodID Globals::methodQoreURLClassLoaderDefineResolveClass;
jmethodID Globals::methodQoreURLClassLoaderGetResolveClass;
jmethodID Globals::methodQoreURLClassLoaderClearCache;
jmethodID Globals::methodQoreURLClassLoaderDefineClassUnconditional;
jmethodID Globals::methodQoreURLClassLoaderGetPtr;
jmethodID Globals::methodQoreURLClassLoaderGetCurrent;
jmethodID Globals::methodQoreURLClassLoaderCheckInProgress;
jmethodID Globals::methodQoreURLClassLoaderClearProgramPtr;

GlobalReference<jclass> Globals::classJavaClassBuilder;
jmethodID Globals::methodJavaClassBuilderGetClassBuilder;
jmethodID Globals::methodJavaClassBuilderGetFunctionConstantClassBuilder;
jmethodID Globals::methodJavaClassBuilderAddFunction;
jmethodID Globals::methodJavaClassBuilderAddStaticField;
jmethodID Globals::methodJavaClassBuilderCreateStaticInitializer;
jmethodID Globals::methodJavaClassBuilderAddConstructor;
jmethodID Globals::methodJavaClassBuilderAddNormalMethod;
jmethodID Globals::methodJavaClassBuilderAddStaticMethod;
jmethodID Globals::methodJavaClassBuilderGetByteCodeFromBuilder;
jmethodID Globals::methodJavaClassBuilderGetTypeDescriptionCls;
jmethodID Globals::methodJavaClassBuilderGetTypeDescriptionStr;
jmethodID Globals::methodJavaClassBuilderFindBaseClassMethodConflict;
jmethodID Globals::methodJavaClassBuilderIsFinalBaseClassMethod;

GlobalReference<jclass> Globals::classKotlinMetadataHelper;
jmethodID Globals::methodKotlinMetadataHelperIsKotlinClass;
jmethodID Globals::methodKotlinMetadataHelperGetKotlinKind;
jmethodID Globals::methodKotlinMetadataHelperIsCompanionObject;
jmethodID Globals::methodKotlinMetadataHelperIsDataClass;
jmethodID Globals::methodKotlinMetadataHelperIsFileFacade;
jmethodID Globals::methodKotlinMetadataHelperIsKotlinRuntimeAvailable;

GlobalReference<jclass> Globals::classKotlinScriptEngine;
jmethodID Globals::methodKotlinScriptEngineIsAvailable;
jmethodID Globals::methodKotlinScriptEngineGetInitError;
jmethodID Globals::methodKotlinScriptEngineEval;
jmethodID Globals::methodKotlinScriptEngineEvalWithBindings;
jmethodID Globals::methodKotlinScriptEngineRetryInit;

GlobalReference<jclass> Globals::classGraphicsEnvironment;
jmethodID Globals::methodGraphicsEnvironmentIsHeadless;

GlobalReference<jclass> Globals::classThread;
jmethodID Globals::methodThreadCurrentThread;
jmethodID Globals::methodThreadGetContextClassLoader;

GlobalReference<jclass> Globals::classHashMap;
GlobalReference<jclass> Globals::classHash;
jmethodID Globals::ctorHash;
jmethodID Globals::methodHashPut;

GlobalReference<jclass> Globals::classMap;
jmethodID Globals::methodMapEntrySet;

GlobalReference<jclass> Globals::classList;
jmethodID Globals::methodListSize;
jmethodID Globals::methodListGet;

GlobalReference<jclass> Globals::classArrayList;
jmethodID Globals::ctorArrayList;
jmethodID Globals::methodArrayListAdd;
jmethodID Globals::methodArrayListGet;
jmethodID Globals::methodArrayListRemove;
jmethodID Globals::methodArrayListSize;
jmethodID Globals::methodArrayListToArray;

GlobalReference<jclass> Globals::classSet;
jmethodID Globals::methodSetIterator;

GlobalReference<jclass> Globals::classEntry;
jmethodID Globals::methodEntryGetKey;
jmethodID Globals::methodEntryGetValue;

GlobalReference<jclass> Globals::classIterator;
jmethodID Globals::methodIteratorHasNext;
jmethodID Globals::methodIteratorNext;

GlobalReference<jclass> Globals::classZonedDateTime;
jmethodID Globals::methodZonedDateTimeParse;
jmethodID Globals::methodZonedDateTimeToString;

GlobalReference<jclass> Globals::classLocalDateTime;
jmethodID Globals::methodLocalDateTimeToString;

GlobalReference<jclass> Globals::classInstant;
jmethodID Globals::methodInstantToString;

GlobalReference<jclass> Globals::classQoreRelativeTime;
jmethodID Globals::ctorQoreRelativeTime;
jfieldID Globals::fieldQoreRelativeTimeYear;
jfieldID Globals::fieldQoreRelativeTimeMonth;
jfieldID Globals::fieldQoreRelativeTimeDay;
jfieldID Globals::fieldQoreRelativeTimeHour;
jfieldID Globals::fieldQoreRelativeTimeMinute;
jfieldID Globals::fieldQoreRelativeTimeSecond;
jfieldID Globals::fieldQoreRelativeTimeUs;

GlobalReference<jclass> Globals::classBigDecimal;
jmethodID Globals::ctorBigDecimal;
jmethodID Globals::methodBigDecimalToString;

GlobalReference<jclass> Globals::classArrays;
jmethodID Globals::methodArraysToString;
jmethodID Globals::methodArraysDeepToString;

GlobalReference<jclass> Globals::classBoolean;
jmethodID Globals::ctorBoolean;
jmethodID Globals::methodBooleanBooleanValue;

GlobalReference<jclass> Globals::classInteger;
jmethodID Globals::ctorInteger;
jmethodID Globals::methodIntegerIntValue;

GlobalReference<jclass> Globals::classLong;
jmethodID Globals::ctorLong;
jmethodID Globals::methodLongLongValue;

GlobalReference<jclass> Globals::classShort;
jmethodID Globals::ctorShort;
jmethodID Globals::methodShortShortValue;

GlobalReference<jclass> Globals::classByte;
jmethodID Globals::ctorByte;
jmethodID Globals::methodByteByteValue;

GlobalReference<jclass> Globals::classDouble;
jmethodID Globals::ctorDouble;
jmethodID Globals::methodDoubleDoubleValue;

GlobalReference<jclass> Globals::classFloat;
jmethodID Globals::ctorFloat;
jmethodID Globals::methodFloatFloatValue;

GlobalReference<jclass> Globals::classCharacter;
jmethodID Globals::ctorCharacter;
jmethodID Globals::methodCharacterCharValue;

GlobalReference<jclass> Globals::classCharSequence;

GlobalReference<jclass> Globals::classBooleanWrapper;
jmethodID Globals::methodBooleanWrapperSetTrue;

GlobalReference<jclass> Globals::classProperties;
jmethodID Globals::ctorProperties;
jmethodID Globals::methodPropertiesSetProperty;

GlobalReference<jclass> Globals::classDriverManager;
jmethodID Globals::methodDriverManagerGetConnection;

GlobalReference<jclass> Globals::classConnection;
jmethodID Globals::methodConnectionClose;
jmethodID Globals::methodConnectionCommit;
jmethodID Globals::methodConnectionRollback;
jmethodID Globals::methodConnectionCreateArrayOf;
jmethodID Globals::methodConnectionGetMetaData;
jmethodID Globals::methodConnectionPrepareStatement;
jmethodID Globals::methodConnectionPrepareStatementArray;
jmethodID Globals::methodConnectionSetAutoCommit;
jmethodID Globals::methodConnectionIsValid;

GlobalReference<jclass> Globals::classDatabaseMetaData;
jmethodID Globals::methodDatabaseMetaDataGetDatabaseMajorVersion;
jmethodID Globals::methodDatabaseMetaDataGetDatabaseMinorVersion;
jmethodID Globals::methodDatabaseMetaDataGetDatabaseProductName;
jmethodID Globals::methodDatabaseMetaDataGetDatabaseProductVersion;
jmethodID Globals::methodDatabaseMetaDataGetDriverMajorVersion;
jmethodID Globals::methodDatabaseMetaDataGetDriverMinorVersion;
jmethodID Globals::methodDatabaseMetaDataGetDriverName;
jmethodID Globals::methodDatabaseMetaDataGetDriverVersion;

GlobalReference<jclass> Globals::classPreparedStatement;
jmethodID Globals::methodPreparedStatementAddBatch;
jmethodID Globals::methodPreparedStatementClose;
jmethodID Globals::methodPreparedStatementExecute;
jmethodID Globals::methodPreparedStatementExecuteBatch;
jmethodID Globals::methodPreparedStatementGetResultSet;
jmethodID Globals::methodPreparedStatementGetMoreResults;
jmethodID Globals::methodPreparedStatementGetUpdateCount;
jmethodID Globals::methodPreparedStatementSetArray;
jmethodID Globals::methodPreparedStatementSetBigDecimal;
jmethodID Globals::methodPreparedStatementSetBoolean;
jmethodID Globals::methodPreparedStatementSetByte;
jmethodID Globals::methodPreparedStatementSetBytes;
jmethodID Globals::methodPreparedStatementSetDouble;
jmethodID Globals::methodPreparedStatementSetInt;
jmethodID Globals::methodPreparedStatementSetShort;
jmethodID Globals::methodPreparedStatementSetLong;
jmethodID Globals::methodPreparedStatementSetNull;
jmethodID Globals::methodPreparedStatementSetString;
jmethodID Globals::methodPreparedStatementSetTimestamp;

GlobalReference<jclass> Globals::classTimestamp;
jmethodID Globals::ctorTimestamp;
jmethodID Globals::methodTimestampSetNanos;
jmethodID Globals::methodTimestampToString;

GlobalReference<jclass> Globals::classDate;
jmethodID Globals::methodDateToString;

GlobalReference<jclass> Globals::classTime;
jmethodID Globals::methodTimeToString;

GlobalReference<jclass> Globals::classResultSet;
jmethodID Globals::methodResultSetClose;
jmethodID Globals::methodResultSetNext;
jmethodID Globals::methodResultSetGetMetaData;
jmethodID Globals::methodResultSetGetArray;
jmethodID Globals::methodResultSetGetObject;

GlobalReference<jclass> Globals::classResultSetMetaData;
jmethodID Globals::methodResultSetMetaDataGetColumnClassName;
jmethodID Globals::methodResultSetMetaDataGetColumnCount;
jmethodID Globals::methodResultSetMetaDataGetColumnLabel;
jmethodID Globals::methodResultSetMetaDataGetColumnType;

GlobalReference<jclass> Globals::classArray;
jmethodID Globals::methodArrayGetArray;

GlobalReference<jclass> Globals::classSQLException;

GlobalReference<jclass> Globals::classServiceLoader;
jmethodID Globals::methodServiceLoaderIterator;

GlobalReference<jclass> Globals::classDriver;

int Globals::typeNull;
int Globals::typeChar;

GlobalReference<jstring> Globals::javaQoreClassField;

std::string QoreJniStackLocationHelper::jni_no_call_name = "<jni_module_java_no_runtime_stack_info>";
QoreExternalProgramLocationWrapper QoreJniStackLocationHelper::jni_loc_builtin("<jni_module_unknown>", -1, -1);

// for the module namespace cache
QoreThreadLock qmnc_lock;
typedef std::map<std::string, const QoreNamespace*> qmnc_t;
typedef std::map<QoreProgram*, qmnc_t> qmnpc_t;
qmnpc_t qmnc;

static void JNICALL invocation_handler_finalize(JNIEnv *, jclass, jlong ptr) {
    delete reinterpret_cast<Dispatcher*>(ptr);
}

static jobject JNICALL invocation_handler_invoke(JNIEnv* jenv, jobject, jlong ptr, jobject proxy, jobject method, jobjectArray args) {
    Env env(jenv);
    Dispatcher* dispatcher = reinterpret_cast<Dispatcher*>(ptr);
    return dispatcher->dispatch(env, proxy, method, args);
}

static int save_object_thread(Env& env, const QoreValue& rv, QoreProgram* pgm, ExceptionSink& xsink) {
    QoreHashNode* data = pgm->getThreadData();
    assert(data);
    const char* domain_name;
    // get key name where to save the data if possible
    QoreValue v = data->getKeyValue("_jni_save");
    if (v.getType() != NT_STRING) {
        domain_name = "_jni_save";
    } else {
        domain_name = v.get<const QoreStringNode>()->c_str();
    }

    QoreValue kv = data->getKeyValue(domain_name);
    // ignore operation if domain exists but is not a list
    if (!kv || kv.getType() == NT_LIST) {
        QoreListNode* list;
        ReferenceHolder<QoreListNode> list_holder(&xsink);
        if (!kv) {
            // we need to assign list in data *after* we prepend the object to the list
            // in order to manage object counts
            list = new QoreListNode(autoTypeInfo);
            list_holder = list;
        } else {
            list = kv.get<QoreListNode>();
        }

        // prepend to list to ensure FILO destruction order
        list->splice(0, 0, rv, &xsink);
        if (!xsink && list_holder) {
             data->setKeyValue(domain_name, list_holder.release(), &xsink);
        }
        if (xsink) {
            QoreToJava::wrapException(env, xsink);
            return -1;
        }
#ifdef DEBUG
        const QoreObject* obj = rv.get<QoreObject>();
        printd(5, "save_object() domain: '%s' obj: %p %s (refs: %d)\n", domain_name, obj, obj->getClassName(), obj->reference_count());
#endif
    } else {
        printd(5, "save_object() NOT SAVING domain: '%s' HAS KEY v: %s (kv: %s)\n", domain_name, rv.getFullTypeName(), kv.getFullTypeName());
    }
    return 0;
}

static int save_object(Env& env, const QoreValue& rv, QoreProgram* pgm, ExceptionSink& xsink) {
    // save object in thread-local data if relevant
    if (rv.getType() != NT_OBJECT) {
        return 0;
    }

    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (jpc) {
        ResolvedCallReferenceNode* save_object_callback = jpc->getSaveObjectCallback();
        if (save_object_callback) {
            printd(5, "save_object() running callback %p\n", save_object_callback);
            ReferenceHolder<QoreListNode> args(new QoreListNode(autoTypeInfo), &xsink);
            args->push(rv.refSelf(), &xsink);
            save_object_callback->execValue(*args, &xsink);
            if (xsink) {
                QoreToJava::wrapException(env, xsink);
                return -1;
            }
            return 0;
        }
    }

    return save_object_thread(env, rv, pgm, xsink);
}

static jlong JNICALL java_api_init_qore(JNIEnv* jenv, jobject obj) {
    QoreThreadAttachHelper attach_helper;
    Env env(jenv);
    try {
        attach_helper.attach();
        assert(Globals::syscl);
        assert(Globals::bootstrap);
        // set current classloader
        env.callVoidMethod(Globals::syscl, Globals::methodQoreURLClassLoaderSetContext, nullptr);
        return reinterpret_cast<jlong>(Globals::getJavaContextProgram());
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return 0;
    }
}

static jobject java_api_call_function_internal(JNIEnv* jenv, jobject obj, jlong ptr, jboolean save, jstring name,
        jobjectArray args) {
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);
    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    ExceptionSink xsink;
    QoreExternalProgramContextHelper pch(&xsink, pgm);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    jsize len = args ? env.getArrayLength(args) : 0;
    ReferenceHolder<QoreListNode> qore_args(&xsink);

    if (len) {
        Array::getArgList(qore_args, env, args, pgm);
    }

    Env::GetStringUtfChars fname(env, name);
    //printd(LogLevel, "java_api_call_function() '%s()' args: %p %d\n", fname.c_str(), *qore_args, len);

    QoreJniStackLocationHelper slh;

    ValueHolder rv(pgm->callFunction(fname.c_str(), *qore_args, &xsink), &xsink);

    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    if (save && save_object(env, *rv, pgm, xsink)) {
        return nullptr;
    }

    try {
        return QoreToJava::toAnyObject(env, *rv, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

static jobject JNICALL java_api_call_function(JNIEnv* jenv, jobject obj, jlong ptr, jstring name,
    jobjectArray args) {
    return java_api_call_function_internal(jenv, obj, ptr, false, name, args);
}

static jobject JNICALL java_api_call_function_save(JNIEnv* jenv, jobject obj, jlong ptr, jstring name,
    jobjectArray args) {
    return java_api_call_function_internal(jenv, obj, ptr, true, name, args);
}

static jobject java_api_call_static_method_internal(JNIEnv* jenv, jobject obj, jlong ptr, jboolean save,
        jstring class_name, jstring method_name, jobjectArray args, const QoreClass* cls = nullptr,
        const QoreMethod* m = nullptr, const QoreExternalMethodVariant* v = nullptr) {
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);
    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    QoreJniStackLocationHelper slh;

    ExceptionSink xsink;
    QoreExternalProgramContextHelper epch(&xsink, pgm);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    jsize len = args ? env.getArrayLength(args) : 0;
    ReferenceHolder<QoreListNode> qore_args(&xsink);

    if (len) {
        Array::getArgList(qore_args, env, args, pgm,
            (v && v->getCodeFlags() & QCF_USES_EXTRA_ARGS) ? true : false);
    }

    if (!m) {
        assert(method_name);
        Env::GetStringUtfChars mname(env, method_name);
        if (!cls) {
            Env::GetStringUtfChars cname(env, class_name);
            //printd(LogLevel, "java_api_call_function() '%s()' args: %p %d\n", fname.c_str(), *qore_args, len);

            // grab the current Program's parse lock before calling QoreProgram::findClass()
            CurrentProgramRuntimeExternalParseContextHelper pch;

            cls = pgm->findClass(cname.c_str(), &xsink);
            if (!cls) {
                if (!xsink) {
                    xsink.raiseException("UNKNOWN-CLASS", "cannot resolve class '%s'", cname.c_str());
                }
                QoreToJava::wrapException(env, xsink);
                return nullptr;
            }
        }

        m = cls->findLocalStaticMethod(mname.c_str());
        if (!m) {
            xsink.raiseException("UNKNOWN-METHOD", "cannot resolve static method '%s::%s()'", cls->getName(),
                mname.c_str());
            QoreToJava::wrapException(env, xsink);
            return nullptr;
        }
    }

    ValueHolder rv(QoreObject::evalStaticMethodVariant(*m, m->getClass(), v, *qore_args, &xsink), &xsink);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    if (save && save_object(env, *rv, pgm, xsink)) {
        return nullptr;
    }

    try {
        return QoreToJava::toAnyObject(env, *rv, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

static jobject JNICALL java_api_call_static_method(JNIEnv* jenv, jobject obj, jlong ptr, jstring class_name,
    jstring method_name, jobjectArray args) {
    return java_api_call_static_method_internal(jenv, obj, ptr, false, class_name, method_name, args);
}

static jobject JNICALL java_api_call_static_method_save(JNIEnv* jenv, jobject obj, jlong ptr, jstring class_name,
    jstring method_name, jobjectArray args) {
    return java_api_call_static_method_internal(jenv, obj, ptr, true, class_name, method_name, args);
}

// private native static QoreObject newObjectSave0(long pgm_ptr, String class_name, Object...args);
static jobject JNICALL java_api_new_object_save(JNIEnv* jenv, jobject obj, jlong ptr, jstring cname,
        jobjectArray args) {
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);

    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    // create the object in the current program context if possible
    {
        QoreProgram* pgm0 = qore_get_call_program_context();
        if (pgm0 && pgm0 != pgm) {
            printd(5, "java_api_new_object_save() using call context for new pbject: %p (was: %p)\n", pgm0, pgm);
            pgm = pgm0;
        } else {
            printd(5, "java_api_new_object_save() using pgm: %p call ctx: %p current: %p\n", pgm, pgm0, getProgram());
        }
    }

    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    printd(5, "java_api_new_object_save() pgm: %p\n", pgm);

    ExceptionSink xsink;
    QoreExternalProgramContextHelper pch(&xsink, pgm);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    QoreJniStackLocationHelper slh;

    jsize len = args ? env.getArrayLength(args) : 0;
    ReferenceHolder<QoreListNode> qore_args(&xsink);

    if (len) {
        Array::getArgList(qore_args, env, args, pgm);
    }

    Env::GetStringUtfChars clsname(env, cname);
    printd(5, "java_api_new_object() class '%s' args: %p %d\n", clsname.c_str(), *qore_args, len);

    const QoreClass* cls = pgm->findClass(clsname.c_str(), &xsink);
    if (cls && !xsink) {
        if (pgm->getParseOptions() & cls->getDomain()) {
            xsink.raiseException("CREATE-OBJECT-ERROR", "Program sandboxing restrictions do not allow access to " \
                "the '%s' class", cls->getName());
        } else {
            cls->runtimeCheckInstantiateClass(&xsink);
        }
    }

    if (!cls && !xsink) {
        xsink.raiseException("CREATE-OBJECT-ERROR", "class '%s' cannot be found", clsname.c_str());
    }

    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    printd(5, "instantiating Qore class '%s' with args: %p (%d)\n", cls->getName(), *qore_args, *qore_args ? (int)qore_args->size() : 0);
    ValueHolder rv(cls->execConstructor(*qore_args, &xsink), &xsink);
    printd(5, "got rv: %s\n", rv->getFullTypeName());
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    if (save_object(env, *rv, pgm, xsink)) {
        return nullptr;
    }

    try {
        return QoreToJava::toAnyObject(env, *rv, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

static jboolean JNICALL java_api_register_java_thread(JNIEnv* jenv, jobject obj) {
    int rc = q_register_foreign_thread();
    jboolean rv;
    if (rc == QFT_OK) {
        printd(5, "java_api_register_java_thread(): thread attached to Qore\n");
        rv = true;
    } else {
        printd(5, "java_api_register_java_thread(): thread not attached to Qore (rc: %d)\n", rc);
        rv = false;
    }
    return rv;
}

static void JNICALL java_api_deregister_java_thread(JNIEnv* jenv, jobject obj) {
    q_deregister_foreign_thread();
}

static void JNICALL qore_exception_wrapper_finalize(JNIEnv*, jclass, jlong ptr) {
    ExceptionSink* xsink = reinterpret_cast<ExceptionSink*>(ptr);
    //printd(LogLevel, "qore_exception_wrapper_finalize() xsink: %p\n", xsink);
    if (xsink != nullptr) {
        xsink->clear();
        delete xsink;
    }
}

static jstring JNICALL qore_exception_wrapper_get_message(JNIEnv* jenv, jclass, jlong ptr) {
    ExceptionSink* xsink = reinterpret_cast<ExceptionSink*>(ptr);

    QoreString jstr;
    QoreValue err = xsink->getExceptionErr();
    QoreStringValueHelper err_str(err);
    QoreValue desc = xsink->getExceptionDesc();
    QoreStringValueHelper desc_str(desc);

    if (!err_str->empty()) {
        if (!desc_str->empty()) {
            jstr.concat(err_str->c_str());
            jstr.concat(": ");
            jstr.concat(desc_str->c_str());
        } else {
            jstr.concat(err_str->c_str());
        }
    } else {
        if (!desc_str->empty())
            jstr.concat(desc_str->c_str());
        else
            jstr.concat("No message");
    }

    //printd(LogLevel, "qore_exception_wrapper_get_message() xsink: %p %s\n", xsink, jstr.c_str());

    Env env(jenv);
    ModifiedUtf8String str(jstr);
    return env.newString(str.c_str()).release();
}

static jstring JNICALL qore_object_class_name(JNIEnv* jenv, jclass, jlong ptr) {
    assert(ptr);
    QoreObject* obj = reinterpret_cast<QoreObject*>(ptr);

    Env env(jenv);
    return env.newString(obj->getClassName()).release();
}

static jboolean JNICALL qore_object_instance_of(JNIEnv* jenv, jclass, jlong ptr, jstring cname) {
    assert(ptr);
    QoreObject* obj = reinterpret_cast<QoreObject*>(ptr);

    Env env(jenv);
    Env::GetStringUtfChars class_name(env, cname);

    ExceptionSink xsink;
    const QoreClass* cls = jni_get_program_context()->findClass(class_name.c_str(), &xsink);
    //printd(5, "qore_object_instance_of() cls: %p (%s) xsink: %d\n", cls, class_name.c_str(), (bool)xsink);
    if (!cls) {
        xsink.clear();
        return false;
    }
    assert(!xsink);
    return obj->validInstanceOf(*cls);
}

static jobject qore_object_closure_call_internal(JNIEnv* jenv, jclass, QoreProgram* pgm, jlong obj_ptr, jboolean save,
        jstring mname, jobjectArray args, const QoreMethod* m = nullptr,
        const QoreExternalMethodVariant* v = nullptr, bool flatten = false) {
    assert(obj_ptr);
    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);
    // must ensure that the thread is attached before executing Qore code
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    QoreJniStackLocationHelper slh;

    ExceptionSink xsink;
    try {
        // set program context before converting arguments
        QoreExternalProgramContextHelper pch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

        jsize len = args ? env.getArrayLength(args) : 0;
        ReferenceHolder<QoreListNode> qore_args(&xsink);

        if (len) {
            Array::getArgList(qore_args, env, args, pgm,
                (v && v->getCodeFlags() & QCF_USES_EXTRA_ARGS) ? true : false);
        }

        QoreObject* obj;

        ValueHolder val(&xsink);
        if (mname || m) {
            // this is a method call and "obj" is a QoreObject*
            obj = reinterpret_cast<QoreObject*>(obj_ptr);
            if (!m) {
                Env::GetStringUtfChars method_name(env, mname);
                val = obj->evalMethod(method_name.c_str(), *qore_args, &xsink);
                printd(5, "qore_object_closure_call_internal() %s::%s() (v: %p) %d arg(s) obj: %p pgm: %p cpgm: %p " \
                    "opgm: %p\n", obj->getClassName(), method_name.c_str(), v, (int)len, obj, pgm,
                    obj->getClass()->getProgram(), obj->getProgram());
            } else {
                printd(5, "qore_object_closure_call_internal() %s::%s() (v: %p id: %d) %d arg(s) obj: %p\n",
                    m->getClassName(), m->getName(), v, m->getClass()->getID(), (int)len, obj);
                val = obj->evalMethodVariant(*m, v, *qore_args, &xsink);

                // check for errors from objects with injected classes; make sure the error was raised due to
                // injection issues
                if (xsink && obj->isValid()
                    && !obj->validInstanceOfStrict(*m->getClass())
                    && xsink.getExceptionErr().getType() == NT_STRING
                    && *xsink.getExceptionErr().get<const QoreStringNode>() == "OBJECT-ALREADY-DELETED") {
                    xsink.clear();
                    val = obj->evalMethod(m->getName(), *qore_args, &xsink);
                }
            }
        } else {
            obj = nullptr;
            // otherwise must be a closure / call reference; "obj" is a ResolvedCallReferenceNode
            const ResolvedCallReferenceNode* call = reinterpret_cast<const ResolvedCallReferenceNode*>(obj_ptr);
            val = call->execValue(*qore_args, &xsink);
        }

        if (xsink) {
            throw XsinkException(xsink);
        }

        //printd(5, "qore_object_closure_call_internal() method: '%s::%s()' rv: %s\n", obj->getClassName(),
        //  method_name.c_str(), val->getFullTypeName());

        if (save && save_object(env, *val, pgm, xsink)) {
            return nullptr;
        }

        return QoreToJava::toAnyObject(env, *val, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }
    return nullptr;
}

static jobject JNICALL qore_object_call_method(JNIEnv* jenv, jclass jcls, QoreProgram* pgm, jlong obj_ptr,
        jstring mname, jobjectArray args) {
    return qore_object_closure_call_internal(jenv, jcls, pgm, obj_ptr, false, mname, args);
}

static jobject JNICALL qore_object_call_method_save(JNIEnv* jenv, jclass jcls, QoreProgram* pgm, jlong obj_ptr,
        jstring mname, jobjectArray args) {
    return qore_object_closure_call_internal(jenv, jcls, pgm, obj_ptr, true, mname, args);
}

static jobject JNICALL qore_closure_call(JNIEnv* jenv, jclass jcls, QoreProgram* pgm, jlong obj_ptr,
        jobjectArray args) {
    return qore_object_closure_call_internal(jenv, jcls, pgm, obj_ptr, false, nullptr, args);
}

static jobject JNICALL qore_closure_call_save(JNIEnv* jenv, jclass jcls, QoreProgram* pgm, jlong obj_ptr,
        jobjectArray args) {
    return qore_object_closure_call_internal(jenv, jcls, pgm, obj_ptr, true, nullptr, args);
}

static void JNICALL qore_closure_finalize(JNIEnv*, jclass, jlong ptr) {
    assert(ptr);
    ResolvedCallReferenceNode* call = reinterpret_cast<ResolvedCallReferenceNode*>(ptr);
    ExceptionSink xsink;
    call->deref(&xsink);
    // NOTE: any exceptions would be printed to stderr; no way to capture them in any case
}

static jobject JNICALL qore_object_get_member_value(JNIEnv* jenv, jobject jobj, QoreObject* obj,
        jstring member) {
    Env env(jenv);

    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    QoreJniStackLocationHelper slh;

    QoreProgram* pgm = obj->getProgram();

    ExceptionSink xsink;
    ValueHolder rv(&xsink);
    {
        QoreExternalProgramContextHelper epch(&xsink, pgm);
        if (xsink) {
            QoreToJava::wrapException(env, xsink);
            return nullptr;
        }

        Env::GetStringUtfChars mem(env, member);
        rv = obj->evalMember(mem.c_str(), &xsink);
    }
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    if (save_object(env, *rv, pgm, xsink)) {
        return nullptr;
    }

    try {
        return QoreToJava::toAnyObject(env, *rv, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

struct class_info_t {
    unsigned compressed_len;
    unsigned len;
    unsigned char* byte_code;
};

typedef std::map<const char*, class_info_t, ltstr> cmap_t;

DLLLOCAL extern cmap_t jar_cmap;

static jbyteArray JNICALL qore_url_classloader_get_cached_class(JNIEnv* jenv, jclass jcls, jstring bin_name) {
    Env env(jenv);
    Env::GetStringUtfChars bname(env, bin_name);

    cmap_t::const_iterator i = jar_cmap.find(bname.c_str());
    if (i == jar_cmap.end()) {
        //printd(LogLevel, "qore_url_classloader_get_cached_class() '%s' not found\n", bname.c_str());
        return nullptr;
    }

    // decompress class data
    SimpleRefHolder<BinaryNode> b(new BinaryNode);
    b->preallocate(i->second.len);
    unsigned size = i->second.len;
    int rc = BZ2_bzBuffToBuffDecompress((char*)b->getPtr(), &size, (char*)i->second.byte_code,
        i->second.compressed_len, 0, 0);
    assert(!rc);
    assert(size == i->second.len);

    LocalReference<jbyteArray> array = env.newByteArray(b->size()).as<jbyteArray>();
    for (jsize j = 0; j < static_cast<jsize>(i->second.len); ++j) {
        env.setByteArrayElement(array, j, ((const char*)b->getPtr())[j]);
    }

    //printd(LogLevel, "qore_url_classloader_get_cached_class() FOUND '%s'\n", bname.c_str());
    return array.release();
}

static jobject JNICALL java_class_builder_do_normal_call(JNIEnv* jenv, jclass jcls, jstring mname, jlong qobj,
        jlong mptr, jlong vptr, jobjectArray args) {
    const QoreMethod* m = reinterpret_cast<const QoreMethod*>(mptr);
    const QoreExternalMethodVariant* v = reinterpret_cast<const QoreExternalMethodVariant*>(vptr);
    printd(5, "java_class_builder_do_normal_call() jcls: %p %s::%s() (%d) qobj: %p args: %p\n", jcls,
        m->getClassName(), m->getName(), m->getClass()->getID(), qobj, args);

    Env env(jenv);

    if (!qobj) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "JavaClassBuilder.doNormalCall0(): QoreObject " \
            "ptr passed as nullptr");
        return nullptr;
    }

    QoreObject* obj = reinterpret_cast<QoreObject*>(qobj);
    QoreProgram* pgm = obj->getClass()->getProgram();
    if (!pgm) {
        jni_get_context_unconditional(pgm);
    }

    if (!pgm) {
        printd(5, "java_class_builder_do_normal_call() no Program ctx!\n");
        QoreStringMaker desc("JavaClassBuilder.doNormalCall0(): no Program context for object %p", obj);
        env.throwNew(env.findClass("java/lang/RuntimeException"), desc.c_str());
        return nullptr;
    }

    return qore_object_closure_call_internal(jenv, jcls, pgm, qobj, true, mname, args, m, v);
}

static jobject JNICALL java_class_builder_do_static_call(JNIEnv* jenv, jclass jcls, jstring mname, jlong qcls,
        jlong pgmptr, jlong mptr, jlong vptr, jobjectArray args) {
    printd(5, "java_class_builder_do_static_call() jcls: %p qcls: %p, args: %p\n", jcls, qcls, args);

    Env env(jenv);

    if (!qcls) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "JavaClassBuilder.doStaticCall0(): QoreClass ptr " \
            "passed as nullptr");
        return nullptr;
    }

    const QoreClass* qc = reinterpret_cast<const QoreClass*>(qcls);

    //printd(5, "java_class_builder_do_static_call() mname: %p mptr: %p pgmptr: %p cpgm: %p curr pgm: %p\n", mname,
    //    mptr, pgmptr, qc->getProgram(), getProgram());

    return java_api_call_static_method_internal(jenv, nullptr, pgmptr, true, nullptr, mname,
        args, qc, reinterpret_cast<const QoreMethod*>(mptr),
        reinterpret_cast<const QoreExternalMethodVariant*>(vptr));
}

static jobject JNICALL java_class_builder_do_function_call(JNIEnv* jenv, jclass jcls, QoreProgram* pgm,
        const QoreExternalFunction* func, const QoreExternalMethodVariant* v, jobjectArray args) {
    printd(5, "java_class_builder_do_function_call() %s() v: %p args: %p\n", func->getName(), v, args);

    assert(pgm);
    assert(func);

    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    Env env(jenv);

    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    QoreJniStackLocationHelper slh;

    ExceptionSink xsink;
    QoreExternalProgramContextHelper epch(&xsink, pgm);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    jsize len = args ? env.getArrayLength(args) : 0;
    ReferenceHolder<QoreListNode> qore_args(&xsink);

    if (len) {
        Array::getArgList(qore_args, env, args, pgm, true);
    }

    ValueHolder rv(func->evalFunction(v, *qore_args, pgm, &xsink), &xsink);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    if (save_object(env, *rv, pgm, xsink)) {
        return nullptr;
    }

    try {
        return QoreToJava::toAnyObject(env, *rv, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

static jobject JNICALL java_class_builder_get_constant_value(JNIEnv* jenv, jclass jcls, QoreProgram* pgm,
        const QoreExternalConstant* constant_entry) {
    assert(pgm);
    assert(constant_entry);

    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);

    Env env(jenv);

    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    ExceptionSink xsink;
    QoreExternalProgramContextHelper epch(&xsink, pgm);
    if (xsink) {
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }

    printd(5, "java_class_builder_get_constant_value() '%s' cl: %x pgm: %p jpc: %p\n", constant_entry->getName(),
        env.callIntMethod(jpc->getClassLoader(), jni::Globals::methodObjectHashCode, nullptr), pgm, jpc);

    ValueHolder val(constant_entry->getReferencedValue(), &xsink);
    printd(5, "java_class_builder_get_constant_value() '%s' = %s\n", constant_entry->getName(), val->getFullTypeName());
    try {
        return QoreToJava::toAnyObject(env, *val, jpc);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
        return nullptr;
    }
}

static int load_module(Env& env, Env::GetStringUtfChars& mod_str, QoreProgram* pgm) {
    ExceptionSink xsink;
    if (ModuleManager::runTimeLoadModule(mod_str.c_str(), pgm, &xsink)) {
        const char* err = xsink.getExceptionErr().get<const QoreStringNode>()->c_str();
        const char* desc = xsink.getExceptionDesc().get<const QoreStringNode>()->c_str();
        QoreStringMaker errdesc("%s: %s", err, desc);
        env.throwNew(env.findClass("java/lang/ClassNotFoundException"), errdesc.c_str());
        //printd(5, "load_module() '%s': %s\n", mod_str.c_str(), errdesc.c_str());
        return -1;
    }

    //printd(5, "load_module() '%s': OK\n", mod_str.c_str());
    return 0;
}

typedef int (*python_module_import_t)(ExceptionSink* xsink, QoreProgram* pgm, const char* module,
    const char* symbol);
static python_module_import_t python_module_import = nullptr;

// throws a Java exception and return -1 on failure
int load_python_module(Env& env, QoreProgram* pgm) {
    static bool python_loaded = false;

    if (!python_loaded) {
        ExceptionSink xsink;
        if (ModuleManager::runTimeLoadModule("python", pgm, &xsink)) {
            QoreToJava::wrapException(env, xsink);
            return -1;
        }
        printd(5, "load_python_module() python module loaded\n");

        python_module_import = (python_module_import_t)dlsym(RTLD_DEFAULT, "python_module_import");
        if (!python_module_import) {
            env.throwNew(env.findClass("java/lang/RuntimeException"), "python_module_import() symbol not found");
            return -1;
        }
        printd(5, "load_python_module() python_module_import symbol resolved\n");

        python_loaded = true;
    }
    return 0;
}

static jbyteArray JNICALL qore_url_classloader_generate_byte_code(JNIEnv* jenv, jobject class_loader, jlong ptr,
        jstring nspath, jstring jname, jstring module, jboolean python, jboolean kotlin, jlong class_ptr) {
    assert(ptr);
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);
    Env env(jenv);
    QoreString qpath;
    if (nspath) {
        Env::GetStringUtfChars jqpath(env, nspath);
        qpath = jqpath.c_str();
    }

    // must ensure that the thread is attached before calling Qore APIs
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    ExceptionSink xsink;
    try {
        // verify that program is still valid
        QoreExternalProgramContextHelper pch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

        JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        if (!jpc) {
            //printd(5, "pgm: %p\n", pgm);
            env.throwNew(env.findClass("java/lang/RuntimeException"), "No JNI context for Qore Program");
            return nullptr;
        }

        if (!Globals::classJavaClassBuilder) {
            env.throwNew(env.findClass("java/lang/RuntimeException"), "bytecode generation unavailable; cannot " \
                "perform dynamic imports in Java");
            return nullptr;
        }

        QoreString modstr;
        if (module) {
            Env::GetStringUtfChars mod_str(env, module);
            printd(5, "qore_url_classloader_generate_byte_code() mod: '%s' python: %d kotlin: %d\n", mod_str.c_str(),
                python, kotlin);
            modstr = mod_str.c_str();
            if (!python && !kotlin) {
                if (load_module(env, mod_str, pgm)) {
                    return nullptr;
                }
            } else if (python) {
                qpath.insert("::", 0);
                qpath.insert(mod_str.c_str(), 0);
                qpath.insert("::Python::", 0);
            } else {
                // kotlin
                qpath.insert("::", 0);
                qpath.insert(mod_str.c_str(), 0);
                qpath.insert("::Kotlin::", 0);
            }
        }
        printd(5, "qore_url_classloader_generate_byte_code() p: %p path: '%s' (mod: %p) class_loader: %x\n", pgm,
            qpath.c_str(), modstr.c_str(),
            env.callIntMethod(class_loader, jni::Globals::methodObjectHashCode, nullptr));

        return jpc->generateByteCode(env, class_loader, qpath, jname, modstr.empty() ? nullptr : modstr.c_str(),
            reinterpret_cast<const QoreClass*>(class_ptr)).release();
    } catch (jni::JavaException& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }
    return nullptr;
}

typedef std::map<const char*, const QoreListNode*, ltstr> mod_dep_map_t;
static bool is_module(const QoreNamespace* parent, const char* name, const QoreHashNode* all_mod_info,
        mod_dep_map_t& mod_dep_map) {
    const char* mod = parent->getModuleName();
    if (!mod) {
        return false;
    }
    if (!strcmp(mod, name)) {
        return true;
    }

    if (!all_mod_info) {
        return false;
    }

    const QoreListNode* reexport_list = nullptr;

    // see if we have the reexport list already
    mod_dep_map_t::iterator i = mod_dep_map.lower_bound(mod);
    if (i == mod_dep_map.end() || !strcmp(i->first, mod)) {
        const QoreHashNode* mod_info = all_mod_info->getKeyValue(name).get<QoreHashNode>();
        if (!mod_info) {
            return false;
        }
        reexport_list = mod_info->getKeyValue("reexported-modules").get<QoreListNode>();
        if (!reexport_list) {
            return false;
        }
        mod_dep_map.insert(i, mod_dep_map_t::value_type(mod, reexport_list));
    } else {
        reexport_list = i->second;
    }

    ConstListIterator li(reexport_list);
    while (li.next()) {
        const QoreValue v = li.getValue();
        if (v.getType() == NT_STRING && (*v.get<const QoreStringNode>() == mod)) {
            return true;
        }
    }

    //printd(5, "is_module() NOT parent: '%s' mod: %s; not in reexport list\n", parent->getName(), mod ? mod : "n/a");
    return false;
}

static const QoreNamespace* get_module_root_ns_intern(const char* name, QoreProgram* mod_pgm,
        const QoreHashNode* all_mod_info, mod_dep_map_t& mod_dep_map, bool check_mod) {
    // try to find the namespace immediately
    {
        QoreString n(name);
        if (islower(*name)) {
            char* p = (char*)n.c_str();
            // convert first letter to upper case
            *p = *p - 32;
        }
        const QoreNamespace* ns = mod_pgm->findNamespace(n);
        if (ns) {
            const char* mod = ns->getModuleName();
            if (mod && !strcmp(mod, name)) {
                return ns;
            }
        }
    }

    const QoreNamespace* root_ns = mod_pgm->getRootNS();
    QoreNamespaceConstIterator i(*root_ns);
    while (i.next()) {
        const QoreNamespace* ns = &i.get();
        if (!check_mod) {
            if (!strcmp(ns->getName(), name)) {
                return ns;
            }
            continue;
        }

        const char* mod = ns->getModuleName();
        if (!mod || strcmp(mod, name)) {
            continue;
        }
        //printd(5, "get_module_root_ns_intern('%s') found\n", name);
        // try to find parent ns
        while (true) {
            const QoreNamespace* parent = ns->getParent();
            if (!is_module(parent, name, all_mod_info, mod_dep_map)) {
                //printd(5, "get_module_root_ns_intern('%s') invalid parent '%s'\n", name, parent->getName());
                break;
            }
            ns = parent;
            //printd(5, "get_module_root_ns_intern('%s') got parent '%s'\n", name, ns->getName());
        }
        //printd(5, "get_module_root_ns_intern('%s') returning '%s'\n", name, ns->getName());
        return ns;
    }
    return nullptr;
}

const QoreNamespace* get_module_root_ns(const char* name, QoreProgram* mod_pgm) {
    AutoLocker al(qmnc_lock);
    qmnpc_t::iterator pi = qmnc.lower_bound(mod_pgm);
    qmnc_t::iterator i;
    if (pi == qmnc.end() || pi->first != mod_pgm) {
        pi = qmnc.insert(pi, qmnpc_t::value_type(mod_pgm, qmnc_t()));
        i = pi->second.end();
    } else {
        i = pi->second.lower_bound(name);
        if (i != pi->second.end() && i->first == name) {
            return i->second;
        }
    }

    ReferenceHolder<QoreHashNode> all_mod_info(MM.getModuleHash(), nullptr);
    mod_dep_map_t mod_dep_map;

    // look for a public namespace and then find the earliest ancestor provided by the module
    const QoreNamespace* rv = get_module_root_ns_intern(name, mod_pgm, *all_mod_info, mod_dep_map, true);
    if (!rv) {
        rv = get_module_root_ns_intern(name, mod_pgm, *all_mod_info, mod_dep_map, false);
    }
    if (rv) {
        pi->second.insert(i, qmnc_t::value_type(name, rv));
    }
    return rv;
}

static void get_java_pfx(QoreString& java_pfx, jboolean python, jboolean kotlin, const char* mod_str,
        QoreString& lang_path, const char* qname) {
    assert(java_pfx.empty());
    if (*mod_str) {
        if (python) {
            java_pfx = "pythonmod.";
        } else if (kotlin) {
            java_pfx = "kotlinmod.";
        } else {
            java_pfx = "qoremod.";
        }
        if (python || kotlin) {
            if (!lang_path.empty()) {
                java_pfx.concat(lang_path.c_str());
                java_pfx.concat('.');
            }
        } else {
            java_pfx.concat(mod_str);
            java_pfx.concat('.');
        }
    } else {
        if (python) {
            java_pfx = "python.";
            java_pfx += lang_path.c_str();
            java_pfx += ".";
        } else if (kotlin) {
            java_pfx = "kotlin.";
            java_pfx += lang_path.c_str();
            java_pfx += ".";
        } else {
            java_pfx = "qore.";
        }
    }

    if (!python && !kotlin && *qname) {
        if (!strncmp(qname, "::", 2)) {
            if (*(qname + 2)) {
                java_pfx.concat(qname + 2);
                java_pfx.replaceAll("::", ".");
                java_pfx.concat('.');
            }
        } else {
            java_pfx.concat(qname);
            java_pfx.replaceAll("::", ".");
            java_pfx.concat('.');
        }
    }
    assert(java_pfx.find("::") == -1);
    printd(5, "get_java_pfx() '%s' mod_str: '%s'\n", java_pfx.c_str(), mod_str ? mod_str : "n/a");
}

typedef std::vector<std::string> strlist_t;
static void get_string_list(strlist_t& l, std::string str, std::string separator = "::") {
    size_t start = 0;
    size_t len = separator.size();
    while (true) {
        size_t sep = str.find(separator, start);
        if (sep == std::string::npos) {
            break;
        }
        std::string element(str, start, sep - start);
        l.push_back(element);
        start = sep + len;
    }

    std::string element(str, start);
    l.push_back(element);
}

const QoreNamespace* find_ns_path(const QoreNamespace* ns, const char* ns_path) {
    strlist_t nslist;
    get_string_list(nslist, ns_path);
    for (std::string& i : nslist) {
        const QoreNamespace* nns = ns->findLocalNamespace(i.c_str());
        if (!nns) {
            printd(5, "find_ns_path() could not find ns '%s' in '%s'\n", i.c_str(), ns->getPath(true).c_str());
            return nullptr;
        }
        ns = nns;
    }
    printd(5, "find_ns_path() resolved '%s' -> %p (%s)\n", ns_path, ns->getPath(true).c_str());
    return ns;
}

static jobject JNICALL qore_url_classloader_get_classes_in_namespace(JNIEnv* jenv, jclass jcls, jlong ptr,
        jstring qname, jstring module, jboolean python, jboolean kotlin, jobject arraylist) {
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    assert(ptr);
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);

    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (!jpc) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "No JNI context for Qore Program");
        return nullptr;
    }

    ExceptionSink xsink;

    Env::GetStringUtfChars mod_str(env);
    if (module) {
        mod_str.set(module);
    }
    Env::GetStringUtfChars nsname(env);
    if (qname) {
        nsname.set(qname);
    }

    QoreString lang_path;

    if (python || kotlin) {
        if (module) {
            printd(5, "ms: '%s'\n", mod_str.c_str());
            lang_path.concat(mod_str.c_str());
            if (qname) {
                lang_path.concat('.');
            }
        }
        if (qname) {
            printd(5, "q: '%s'\n", nsname.c_str());
            if (python && !strncmp(nsname.c_str(), "::Python::", 10)) {
                lang_path.concat(nsname.c_str() + 10);
            } else if (kotlin && !strncmp(nsname.c_str(), "::Kotlin::", 10)) {
                lang_path.concat(nsname.c_str() + 10);
            } else {
                lang_path.concat(nsname.c_str());
            }
            if (lang_path.find("::") >= 0) {
                lang_path.replaceAll("::", ".");
            }
        }
    }

    printd(5, "qore_url_classloader_get_classes_in_namespace() qname: '%s' (%p) module: '%s' (%p) python: %d "
        "kotlin: %d lang_path: '%s' pgm: %p jpc: %p\n", nsname.c_str(), qname, mod_str.c_str(), module, python,
        kotlin, lang_path.c_str(), pgm, jpc);

    if (python) {
        try {
            if (!python_module_import && load_python_module(env, pgm)) {
                return nullptr;
            }
            printd(5, "qore_url_classloader_get_classes_in_namespace() python import path: '%s'\n", lang_path.c_str());
            if (python_module_import(&xsink, pgm, lang_path.c_str(), nullptr)) {
                QoreToJava::wrapException(env, xsink);
                return nullptr;
            }
        } catch (AbstractException& e) {
            e.convert(&xsink);
            QoreToJava::wrapException(env, xsink);
            return nullptr;
        }
    }

    // Note: kotlin classes are already Java classes, no special module loading needed for pre-compiled JARs
    if (module && !python && !kotlin && load_module(env, mod_str, pgm)) {
        return nullptr;
    }

    try {
        // set program context before converting arguments
        QoreExternalProgramContextHelper pch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

        const QoreNamespace* ns;
        if (python || kotlin) {
            QoreString ns_path = lang_path;
            ns_path.replaceAll(".", "::");
            ns = pgm->findNamespace(ns_path.c_str());
#if QORE_VERSION_CODE >= 10013
            if (python && !module) {
                ValueHolder pm(ns->getReferencedKeyValue("python_module"), nullptr);
                printd(5, "python_module: '%s'\n", pm ? pm->get<QoreStringNode>()->c_str() : "n/a");
                if (pm->getType() == NT_STRING) {
                    mod_str = pm->get<QoreStringNode>()->c_str();
                }
            }
#endif
            printd(5, "qore_url_classloader_get_classes_in_namespace() lang_path: '%s' => '%s' ns: %p\n",
                lang_path.c_str(), ns_path.c_str(), ns);
        } else if (qname) {
            ns = pgm->findNamespace(nsname.c_str());
            printd(5, "qore_url_classloader_get_classes_in_namespace() qname: '%s' ns: %p\n", nsname.c_str(), ns);
        } else {
            assert(module);
            ns = get_module_root_ns(mod_str.c_str(), pgm);
            printd(5, "qore_url_classloader_get_classes_in_namespace() loaded module mod_str: '%s' ns: %p (%s)\n",
                mod_str.c_str(), ns, ns ? ns->getPath(true).c_str() : "n/a");
            if (nsname) {
                ns = find_ns_path(ns, nsname.c_str());
            }
        }

#ifdef DEBUG
        {
            QoreStringNodeHolder name(pgm->getScriptPath());
            printd(5, "qore_url_classloader_get_classes_in_namespace() pgm: %p (%s) naname: '%s' " \
                "mod_str: '%s': ns: %p ('%s')\n", pgm,
                name ? name->c_str() : "n/a", nsname.c_str(), mod_str.c_str(), ns,
                ns ? ns->getPath(true).c_str() : "n/a");
        }
#endif
        if (ns) {
            QoreString java_pfx;

            if (!mod_str && !ns->isRoot()) {
                const char* mod = ns->getModuleName();
                if (mod && !jpc->isInjectedModule(mod)) {
                    // do not allow Qore symbols provided by modules to be accessed from the "qore" package
                    // or Python symbols provided by modules to be accessed from the 'python' package
                    printd(5, "qore_url_classloader_get_classes_in_namespace() not scanning ns %p (from mod %s)\n",
                        ns, ns->getModuleName());
                    return nullptr;
                }
            }

            QoreNamespaceClassIterator i(*ns);
            while (i.next()) {
                const QoreClass& qc = i.get();
                const char* cls_name = qc.getName();

                // Skip Kotlin companion object inner classes (ending with $Companion or __Companion)
                // Only filter when processing Kotlin classes to avoid affecting other languages
                if (kotlin && (strstr(cls_name, "$Companion") || strstr(cls_name, "__Companion"))) {
                    printd(5, "+ skipping Kotlin companion object class %s\n", cls_name);
                    continue;
                }

                std::string pname;
                if (java_pfx.empty()) {
                    get_java_pfx(java_pfx, python, kotlin, mod_str.c_str(), lang_path, nsname.c_str());
                }
                pname = java_pfx.c_str();
                pname += cls_name;
                printd(5, "pname: '%s'\n", pname.c_str());

#ifdef DEBUG
                std::string cnsn = qc.getNamespacePath();
                printd(5, "CLASS %s (%p) nsp: '%s' (%s)\n", cls_name, &qc, cnsn.c_str(), pname.c_str());
#endif
                printd(5, "qore_url_classloader_get_classes_in_namespace() pgm: %p '%s': qc: %p (%s -> %s)\n",
                    pgm, nsname.c_str(), &qc, cls_name, pname.c_str());
                assert(pname.find("::") == std::string::npos);

                // add to the ArrayList<String> var
                LocalReference<jstring> bin_name = env.newString(pname.c_str());

                jvalue jarg;
                jarg.l = bin_name;
                env.callBooleanMethod(arraylist, Globals::methodArrayListAdd, &jarg);
            }
            QoreNamespaceFunctionIterator fi(*ns);
            while (fi.next()) {
                // if there is at least one, then create the special "$Functions" class
                if (java_pfx.empty()) {
                    get_java_pfx(java_pfx, python, kotlin, mod_str.c_str(), lang_path, nsname.c_str());
                }
                std::string pname = java_pfx.c_str();
                pname += JniImportedFunctionClassName;

                printd(5, "function pname: '%s'\n", pname.c_str());
                LocalReference<jstring> bin_name = env.newString(pname.c_str());

                jvalue jarg;
                jarg.l = bin_name;
                env.callBooleanMethod(arraylist, Globals::methodArrayListAdd, &jarg);

                break;
            }
            QoreNamespaceConstantIterator ci(*ns);
            while (ci.next()) {
                // if there is at least one, then create the special "$Constants" class
                if (java_pfx.empty()) {
                    get_java_pfx(java_pfx, python, kotlin, mod_str.c_str(), lang_path, nsname.c_str());
                }
                std::string pname = java_pfx.c_str();
                pname += JniImportedConstantClassName;

                printd(5, "qore_url_classloader_get_classes_in_namespace() pgm: %p '%s' (%s)\n",
                    pgm, nsname.c_str(), pname.c_str());

                LocalReference<jstring> bin_name = env.newString(pname.c_str());

                jvalue jarg;
                jarg.l = bin_name;
                env.callBooleanMethod(arraylist, Globals::methodArrayListAdd, &jarg);

                break;
            }
            printd(5, "qore_url_classloader_get_classes_in_namespace() %s: DONE\n", nsname.c_str());
        }
    } catch (jni::Exception& e) {
        ExceptionSink xsink;
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }
    return nullptr;
}

static jlong JNICALL qore_url_classloader_get_context_program(JNIEnv* jenv, jclass jcls, jobject new_syscl,
        jobject created, jboolean finalize_init) {
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return 0;
    }

    printd(5, "qore_url_classloader_get_context_program() new_syscl: %p finalize_init: %d\n", new_syscl, finalize_init);
    if (finalize_init) {
        Globals::syscl = GlobalReference<jobject>::fromLocal(new_syscl);
        Globals::bootstrap = true;
        jni_module_init_finalize(true);
    }

    bool pcreated;
    jlong rv = Globals::getContextProgram(finalize_init ? nullptr : new_syscl, pcreated);
    if (pcreated) {
        env.callVoidMethod(created, Globals::methodBooleanWrapperSetTrue, nullptr);
    }
    printd(5, "qore_url_classloader_get_context_program() rv: %p created: %d\n", rv, pcreated);
    return rv;
}

static jobject JNICALL qore_url_classloader_clear_compilation_cache(JNIEnv* jenv, jclass jcls, jlong ptr) {
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (!jpc) {
        Env env(jenv);
        env.throwNew(env.findClass("java/lang/RuntimeException"), "No JNI context for Qore Program");
        return nullptr;
    }

    jpc->clearCompilationCache();
    return nullptr;
}

static jobject JNICALL qore_url_classloader_shutdown_context(JNIEnv* jenv, jclass jcls, jobject new_syscl,
        jobject created) {
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        Env env(jenv);
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }
    Globals::clearGlobalContext();
    return nullptr;
}

static jobject JNICALL qore_url_classloader_dummy(JNIEnv* jenv, jclass jcls) {
    return nullptr;
}

// Returns the canonical "system" QoreURLClassLoader.
//
// Syscl is now used only as the fallback home for `qore.Qore.*` built-in classes and for
// module classes whose owning Program cannot be resolved (binary modules, modules not yet
// loaded under their canonical name).  `qoremod.<mod>.*` classes route to the owning user
// module's Program loader instead — see qore_url_classloader_get_module_loader().
static jobject JNICALL qore_url_classloader_get_syscl(JNIEnv* jenv, jclass jcls) {
    return Globals::syscl ? (jobject)Globals::syscl : nullptr;
}

// Returns the QoreURLClassLoader of the named user module's owning Program, or null.
//
// Used by QoreURLClassLoader.loadClass / loadClassWithPtr (and by the C++ `define-class`
// module command) to route every reference to `qoremod.<mod>.*` through a single loader so
// all consumer Programs see the same Class object.  Returns null for unknown modules and
// for binary modules that don't have an owning user Program — callers fall back to syscl.
static jobject JNICALL qore_url_classloader_get_module_loader(JNIEnv* jenv, jclass jcls, jstring jname) {
    if (!jname) {
        return nullptr;
    }
    const char* utf = jenv->GetStringUTFChars(jname, nullptr);
    if (!utf) {
        return nullptr;
    }
    jobject loader = Globals::getModuleClassLoader(utf);
    jenv->ReleaseStringUTFChars(jname, utf);
    return loader;
}

static jobject JNICALL qore_url_classloader_debug(JNIEnv* jenv, jclass jcls, jlong ptr) {
    return nullptr;
}

static jlong JNICALL qore_object_create(JNIEnv* jenv, jclass ignore, const QoreClass* qc, const QoreMethod* meth,
        const QoreExternalMethodVariant* v, jobject java_this, jobjectArray args) {
    //printd(5, "qore_object_create() qc: %p meth: %p v: %p java_this: %p %s::%s() args: %p\n", qc, meth, v, java_this,
    //    qc->getName(), meth ? meth->getName() : "n/a", args);

    Env env(jenv);
    if (!qc) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "QoreClass ptr passed as nullptr");
        return 0;
    }

    // create the object in the current object's Program; using the QoreClass's program will result in the wrong
    // classloader being used to resolve any dependent classes
    QoreProgram* pgm = jni_get_program_context();
    if (!pgm) {
        pgm = Globals::getJavaContextProgram();
    }
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return 0;
    }

    ExceptionSink xsink;
    try {
        // see if "this" (in Java) is being instantiated for the Qore class directly or a child class; if it's a child
        // class, then create a new Qore class for the Java class
        LocalReference<jclass> jcls = env.callObjectMethod(java_this, Globals::methodObjectGetClass, nullptr).as<jclass>();
        // only look for a Qore class in the immediate local class
        const QoreClass* jqc = JniExternalProgramData::tryGetQoreClass(env, jcls, false);
        if (!jqc) {
            jqc = qjcm.findCreateQoreClass(env, jcls, pgm);
        }

        // ensure class can be instantiated
        if (jqc->runtimeCheckInstantiateClass(&xsink)) {
            QoreToJava::wrapException(env, xsink);
            return 0;
        }

        // set program context before converting arguments
        QoreExternalProgramContextHelper pch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

        jsize len = args ? env.getArrayLength(args) : 0;
        ReferenceHolder<QoreListNode> qore_args(&xsink);
        if (len) {
            Array::getArgList(qore_args, env, args, pgm, true);
        }

        ValueHolder obj(qc->execConstructor(*jqc, *qore_args, true, &xsink), &xsink);
        if (xsink) {
            QoreToJava::wrapException(env, xsink);
            return 0;
        }

        assert(obj);
        save_object(env, *obj, pgm, xsink);

        printd(5, "qore_object_create() created %s: %p (%s)\n", jqc->getName(), obj->get<const QoreObject>(),
            obj->getFullTypeName());

        assert(obj);
        // increment weak ref count for assignment to QoreObjectBase
        QoreObject* qobj = obj->get<QoreObject>();
        qobj->tRef();

        if (jqc != qc) {
            // set private data for Java
            qobj->setPrivate(jqc->getID(), new QoreJniPrivateData(java_this));
        }

        return reinterpret_cast<jlong>(qobj);
    } catch (jni::QoreJniException& e) {
        QoreString buf;
        env.throwNew(env.findClass("java/lang/RuntimeException"), e.what(buf));
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }

    //assert(false);
    return 0;
}

static void JNICALL qore_object_release(JNIEnv*, jclass, jlong ptr) {
    assert(ptr);
    reinterpret_cast<QoreObject*>(ptr)->tDeref();
}

static void JNICALL qore_object_destroy(JNIEnv* jenv, jclass, jlong ptr) {
    assert(ptr);
    // must ensure that the thread is attached before executing Qore code
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        // NOTE: this results in a memory leak
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return;
    }

    ExceptionSink xsink;
    try {
        reinterpret_cast<QoreObject*>(ptr)->doDelete(&xsink);
        reinterpret_cast<QoreObject*>(ptr)->tDeref();
        if (xsink) {
            throw XsinkException(xsink);
        }
    } catch (jni::Exception& e) {
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }
}

static void JNICALL qore_object_finalize(JNIEnv*, jclass, jlong ptr) {
    assert(ptr);
    reinterpret_cast<QoreObject*>(ptr)->tDeref();
}

static GlobalReference<jclass> getPrimitiveClass(Env& env, const char* wrapperName) {
    LocalReference<jclass> wrapperClass = env.findClass(wrapperName);
    jfieldID typeFieldId = env.getStaticField(wrapperClass, "TYPE", "Ljava/lang/Class;");
    return env.getStaticObjectField(wrapperClass, typeFieldId).as<jclass>().makeGlobal();
}

#include "JavaClassQoreInvocationHandler.inc"
#include "JavaClassQoreInvocationHandler_State.inc"
#include "JavaClassQoreExceptionWrapper.inc"
#include "JavaClassQoreException.inc"
#include "JavaClassQoreObjectBase.inc"
#include "JavaClassQoreObject.inc"
#include "JavaClassQoreJavaClassBase.inc"
#include "JavaClassQoreClosure.inc"
#include "JavaClassNativeCleanup.inc"
#include "JavaClassNativeCleanup_Ref.inc"
#include "JavaClassQoreObjectWrapper.inc"
#include "JavaClassQoreClosureMarker.inc"
#include "JavaClassQoreClosureMarkerImpl.inc"
#include "JavaClassBooleanWrapper.inc"
#include "JavaClassClassModInfo.inc"
#include "JavaClassQoreURLClassLoader.inc"
#include "JavaClassQoreURLClassLoader_1.inc"
#include "JavaClassQoreURLClassLoader_2.inc"
#include "JavaClassQoreJavaFileObject.inc"
#include "JavaClassQoreJavaObjectPtr.inc"
#include "JavaClassJavaClassBuilder.inc"
#include "JavaClassJavaClassBuilder_1.inc"
#include "JavaClassJavaClassBuilder_2.inc"
#include "JavaClassStaticEntry.inc"
#include "JavaClassQoreJavaApi.inc"
#include "JavaClassQoreRelativeTime.inc"
#include "JavaClassQoreJavaDynamicApi.inc"
#include "JavaClassHash.inc"
#include "JavaClassHash_1.inc"
#include "JavaClassHash_2.inc"
#include "JavaClassHash_3.inc"
#include "JavaClassHash_4.inc"
#include "JavaClassHash_5.inc"
#include "JavaClassHash_6.inc"
#include "JavaClassHash_7.inc"
#include "JavaClassHash_8.inc"
#include "JavaClassHash_9.inc"
#include "JavaClassHash_10.inc"

// compiler: org.qore.jni.compiler
#include "JavaClassCompilerOutput.inc"
#include "JavaClassCustomJavaFileObject.inc"
#include "JavaClassPackageInternalsFinder.inc"
#include "JavaClassQoreJavaClassObject.inc"
#include "JavaClassQoreJavaCompiler.inc"
#include "JavaClassQoreJavaCompilerException.inc"
#include "JavaClassJavaFileObjectImpl.inc"
#include "JavaClassFileManagerImpl.inc"
#include "JavaClassQoreJavaFileManager.inc"

#include "JavaJarByteBuddy.inc"

// to repopulate list:
// ls *inc | grep -v ByteBuddy| while read a; do c=`echo $a|sed -e s/JavaClass// -e s/.inc$//`;
//   b=`echo $c|sed s/_/$/`;
//   printf "    {\"org.qore.jni.%s\", {java_org_qore_jni_%s_class_len, java_org_qore_jni_%s_class}},\n" "$b" "$c" "$c"; done

struct uncompressed_class_info_t {
    unsigned len;
    unsigned char* byte_code;
};

typedef std::map<const char*, uncompressed_class_info_t, ltstr> ucmap_t;
static ucmap_t ucmap = {
    {"org.qore.jni.BooleanWrapper", {java_org_qore_jni_BooleanWrapper_class_len, java_org_qore_jni_BooleanWrapper_class}},
    {"org.qore.jni.ClassModInfo", {java_org_qore_jni_ClassModInfo_class_len, java_org_qore_jni_ClassModInfo_class}},
    {"org.qore.jni.Hash", {java_org_qore_jni_Hash_class_len, java_org_qore_jni_Hash_class}},
    {"org.qore.jni.Hash$1", {java_org_qore_jni_Hash_1_class_len, java_org_qore_jni_Hash_1_class}},
    {"org.qore.jni.Hash$10", {java_org_qore_jni_Hash_10_class_len, java_org_qore_jni_Hash_10_class}},
    {"org.qore.jni.Hash$2", {java_org_qore_jni_Hash_2_class_len, java_org_qore_jni_Hash_2_class}},
    {"org.qore.jni.Hash$3", {java_org_qore_jni_Hash_3_class_len, java_org_qore_jni_Hash_3_class}},
    {"org.qore.jni.Hash$4", {java_org_qore_jni_Hash_4_class_len, java_org_qore_jni_Hash_4_class}},
    {"org.qore.jni.Hash$5", {java_org_qore_jni_Hash_5_class_len, java_org_qore_jni_Hash_5_class}},
    {"org.qore.jni.Hash$6", {java_org_qore_jni_Hash_6_class_len, java_org_qore_jni_Hash_6_class}},
    {"org.qore.jni.Hash$7", {java_org_qore_jni_Hash_7_class_len, java_org_qore_jni_Hash_7_class}},
    {"org.qore.jni.Hash$8", {java_org_qore_jni_Hash_8_class_len, java_org_qore_jni_Hash_8_class}},
    {"org.qore.jni.Hash$9", {java_org_qore_jni_Hash_9_class_len, java_org_qore_jni_Hash_9_class}},
    {"org.qore.jni.JavaClassBuilder", {java_org_qore_jni_JavaClassBuilder_class_len, java_org_qore_jni_JavaClassBuilder_class}},
    {"org.qore.jni.JavaClassBuilder$1", {java_org_qore_jni_JavaClassBuilder_1_class_len, java_org_qore_jni_JavaClassBuilder_1_class}},
    {"org.qore.jni.JavaClassBuilder$2", {java_org_qore_jni_JavaClassBuilder_2_class_len, java_org_qore_jni_JavaClassBuilder_2_class}},
    {"org.qore.jni.StaticEntry", {java_org_qore_jni_StaticEntry_class_len, java_org_qore_jni_StaticEntry_class}},
    {"org.qore.jni.QoreClosure", {java_org_qore_jni_QoreClosure_class_len, java_org_qore_jni_QoreClosure_class}},
    {"org.qore.jni.QoreClosureMarker", {java_org_qore_jni_QoreClosureMarker_class_len, java_org_qore_jni_QoreClosureMarker_class}},
    {"org.qore.jni.QoreClosureMarkerImpl", {java_org_qore_jni_QoreClosureMarkerImpl_class_len, java_org_qore_jni_QoreClosureMarkerImpl_class}},
    {"org.qore.jni.QoreException", {java_org_qore_jni_QoreException_class_len, java_org_qore_jni_QoreException_class}},
    {"org.qore.jni.QoreExceptionWrapper", {java_org_qore_jni_QoreExceptionWrapper_class_len, java_org_qore_jni_QoreExceptionWrapper_class}},
    {"org.qore.jni.QoreInvocationHandler", {java_org_qore_jni_QoreInvocationHandler_class_len, java_org_qore_jni_QoreInvocationHandler_class}},
    {"org.qore.jni.QoreInvocationHandler$State", {java_org_qore_jni_QoreInvocationHandler_State_class_len, java_org_qore_jni_QoreInvocationHandler_State_class}},
    {"org.qore.jni.NativeCleanup", {java_org_qore_jni_NativeCleanup_class_len, java_org_qore_jni_NativeCleanup_class}},
    {"org.qore.jni.NativeCleanup$Ref", {java_org_qore_jni_NativeCleanup_Ref_class_len, java_org_qore_jni_NativeCleanup_Ref_class}},
    {"org.qore.jni.QoreJavaApi", {java_org_qore_jni_QoreJavaApi_class_len, java_org_qore_jni_QoreJavaApi_class}},
    {"org.qore.jni.QoreJavaClassBase", {java_org_qore_jni_QoreJavaClassBase_class_len, java_org_qore_jni_QoreJavaClassBase_class}},
    {"org.qore.jni.QoreJavaDynamicApi", {java_org_qore_jni_QoreJavaDynamicApi_class_len, java_org_qore_jni_QoreJavaDynamicApi_class}},
    {"org.qore.jni.QoreJavaFileObject", {java_org_qore_jni_QoreJavaFileObject_class_len, java_org_qore_jni_QoreJavaFileObject_class}},
    {"org.qore.jni.QoreJavaObjectPtr", {java_org_qore_jni_QoreJavaObjectPtr_class_len, java_org_qore_jni_QoreJavaObjectPtr_class}},
    {"org.qore.jni.QoreObject", {java_org_qore_jni_QoreObject_class_len, java_org_qore_jni_QoreObject_class}},
    {"org.qore.jni.QoreObjectBase", {java_org_qore_jni_QoreObjectBase_class_len, java_org_qore_jni_QoreObjectBase_class}},
    {"org.qore.jni.QoreObjectWrapper", {java_org_qore_jni_QoreObjectWrapper_class_len, java_org_qore_jni_QoreObjectWrapper_class}},
    {"org.qore.jni.QoreRelativeTime", {java_org_qore_jni_QoreRelativeTime_class_len, java_org_qore_jni_QoreRelativeTime_class}},
    {"org.qore.jni.QoreURLClassLoader", {java_org_qore_jni_QoreURLClassLoader_class_len, java_org_qore_jni_QoreURLClassLoader_class}},
    {"org.qore.jni.QoreURLClassLoader$1", {java_org_qore_jni_QoreURLClassLoader_1_class_len, java_org_qore_jni_QoreURLClassLoader_1_class}},
    {"org.qore.jni.QoreURLClassLoader$2", {java_org_qore_jni_QoreURLClassLoader_2_class_len, java_org_qore_jni_QoreURLClassLoader_2_class}},
    {"org.qore.jni.compiler.CompilerOutput", {java_org_qore_jni_CompilerOutput_class_len, java_org_qore_jni_CompilerOutput_class}},
    {"org.qore.jni.compiler.CustomJavaFileObject", {java_org_qore_jni_CustomJavaFileObject_class_len, java_org_qore_jni_CustomJavaFileObject_class}},
    {"org.qore.jni.compiler.PackageInternalsFinder", {java_org_qore_jni_PackageInternalsFinder_class_len, java_org_qore_jni_PackageInternalsFinder_class}},
    {"org.qore.jni.compiler.QoreJavaClassObject", {java_org_qore_jni_QoreJavaClassObject_class_len, java_org_qore_jni_QoreJavaClassObject_class}},
    {"org.qore.jni.compiler.QoreJavaCompiler", {java_org_qore_jni_QoreJavaCompiler_class_len, java_org_qore_jni_QoreJavaCompiler_class}},
    {"org.qore.jni.compiler.QoreJavaCompilerException", {java_org_qore_jni_QoreJavaCompilerException_class_len, java_org_qore_jni_QoreJavaCompilerException_class}},
    {"org.qore.jni.compiler.JavaFileObjectImpl", {java_org_qore_jni_JavaFileObjectImpl_class_len, java_org_qore_jni_JavaFileObjectImpl_class}},
    {"org.qore.jni.compiler.FileManagerImpl", {java_org_qore_jni_FileManagerImpl_class_len, java_org_qore_jni_FileManagerImpl_class}},
    {"org.qore.jni.compiler.QoreJavaFileManager", {java_org_qore_jni_QoreJavaFileManager_class_len, java_org_qore_jni_QoreJavaFileManager_class}},
};

// returns byte[] of class data if found
static jbyteArray qore_url_classloader_get_internal_class(JNIEnv* jenv, jclass jcls, jstring bin_name) {
    Env env(jenv);
    Env::GetStringUtfChars bname(env, bin_name);

    ucmap_t::const_iterator i = ucmap.find(bname.c_str());
    if (i == ucmap.end()) {
        //printd(LogLevel, "qore_url_classloader_get_internal_class() '%s' not found\n", bname.c_str());
        return nullptr;
    }

    LocalReference<jbyteArray> array = env.newByteArray(i->second.len).as<jbyteArray>();
    for (jsize j = 0; j < static_cast<jsize>(i->second.len); ++j) {
        env.setByteArrayElement(array, j, i->second.byte_code[j]);
    }

    //printd(LogLevel, "qore_url_classloader_get_internal_class() FOUND '%s'\n", bname.c_str());
    return array.release();
}

static jobject JNICALL qore_url_classloader_get_internal_classes_for_package(JNIEnv* jenv, jclass jcls, jlong ptr,
        jstring pkg, jobject arraylist) {
    Env env(jenv);
    QoreThreadAttachHelper attach_helper;
    try {
        attach_helper.attach();
    } catch (Exception& e) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "Unable to attach thread to Qore");
        return nullptr;
    }

    assert(ptr);
    QoreProgram* pgm = reinterpret_cast<QoreProgram*>(ptr);

    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (!jpc) {
        env.throwNew(env.findClass("java/lang/RuntimeException"), "No JNI context for Qore Program");
        return nullptr;
    }

    try {
        Env::GetStringUtfChars pname(env, pkg);
        unsigned len = strlen(pname.c_str());
        for (auto& i : ucmap) {
            unsigned plen = strlen(i.first);
            if (plen < len || i.first[len] != '.') {
                continue;
            }
            if (strncmp(pname.c_str(), i.first, len) || strchr(i.first + len + 1, '.')) {
                continue;
            }

            LocalReference<jstring> bin_name = env.newString(i.first);

            jvalue jarg;
            jarg.l = bin_name;
            env.callBooleanMethod(arraylist, Globals::methodArrayListAdd, &jarg);
        }
    } catch (jni::Exception& e) {
        ExceptionSink xsink;
        e.convert(&xsink);
        QoreToJava::wrapException(env, xsink);
    } catch (const std::bad_alloc& e) {
        // translate OOM C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/OutOfMemoryError"), e.what());
    } catch (const std::exception& e) {
        // translate unknown C++ exceptions to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), e.what());
    } catch (...) {
        // translate unknown C++ exception to a Java exception
        env.throwNew(env.findClass("java/lang/Error"), "Unknown exception type");
    }

    return nullptr;
}

static JNINativeMethod invocationHandlerNativeMethods[2] = {
    {
        const_cast<char*>("release0"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(invocation_handler_finalize)
    },
    {
        const_cast<char*>("invoke0"),
        const_cast<char*>("(JLjava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(invocation_handler_invoke)
    }
};

static JNINativeMethod qoreJavaApiNativeMethods[] = {
    {
        const_cast<char*>("initQore0"),
        const_cast<char*>("()J"),
        reinterpret_cast<void*>(java_api_init_qore)
    },
    {
        const_cast<char*>("initQoreBootstrap0"),
        const_cast<char*>("()V"),
        reinterpret_cast<void*>(qore_url_classloader_dummy)
    },
    {
        const_cast<char*>("callFunction0"),
        const_cast<char*>("(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_api_call_function)
    },
    {
        const_cast<char*>("callFunctionSave0"),
        const_cast<char*>("(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_api_call_function_save)
    },
    {
        const_cast<char*>("callStaticMethod0"),
        const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_api_call_static_method)
    },
    {
        const_cast<char*>("callStaticMethodSave0"),
        const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_api_call_static_method_save)
    },
    {
        // private native static QoreObject newObjectSave0(long pgm_ptr, String key, String class_name, Object...args);
        const_cast<char*>("newObjectSave0"),
        const_cast<char*>("(JLjava/lang/String;[Ljava/lang/Object;)Lorg/qore/jni/QoreObject;"),
        reinterpret_cast<void*>(java_api_new_object_save)
    },
    {
        // private native static boolean registerJavaThread0();
        const_cast<char*>("registerJavaThread0"),
        const_cast<char*>("()Z"),
        reinterpret_cast<void*>(java_api_register_java_thread)
    },
    {
        // private native static void deregisterJavaThread0();
        const_cast<char*>("deregisterJavaThread0"),
        const_cast<char*>("()V"),
        reinterpret_cast<void*>(java_api_deregister_java_thread)
    },
};

static JNINativeMethod qoreExceptionWrapperNativeMethods[] = {
    // The release path runs in module-jni's NativeCleanup C++ thread, which calls
    // qore_exception_wrapper_finalize directly without a JNI native-method binding.
    {
        const_cast<char*>("getMessage0"),
        const_cast<char*>("(J)Ljava/lang/String;"),
        reinterpret_cast<void*>(qore_exception_wrapper_get_message)
    }
};

static JNINativeMethod qoreObjectBaseNativeMethods[] = {
    {
        const_cast<char*>("create0"),
        const_cast<char*>("(JJJLjava/lang/Object;[Ljava/lang/Object;)J"),
        reinterpret_cast<void*>(qore_object_create)
    },
    {
        const_cast<char*>("release0"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(qore_object_release)
    },
    {
        const_cast<char*>("destroy0"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(qore_object_destroy)
    },
    // pointerRelease0 (formerly bound to qore_object_finalize) is gone: the
    // weak-reference release path now runs in module-jni's NativeCleanup C++
    // thread, which calls qore_object_finalize directly without going through
    // a JNI native-method invocation adapter.  See native_cleanup_thread_main
    // in Jvm.cpp.
};

static JNINativeMethod qoreObjectNativeMethods[] = {
    {
        const_cast<char*>("className0"),
        const_cast<char*>("(J)Ljava/lang/String;"),
        reinterpret_cast<void*>(qore_object_class_name)
    },
    {
        const_cast<char*>("instanceOf0"),
        const_cast<char*>("(JLjava/lang/String;)Z"),
        reinterpret_cast<void*>(qore_object_instance_of)
    },
    {
        const_cast<char*>("callMethod0"),
        const_cast<char*>("(JJLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(qore_object_call_method)
    },
    {
        const_cast<char*>("callMethodSave0"),
        const_cast<char*>("(JJLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(qore_object_call_method_save)
    },
    {
        const_cast<char*>("getMemberValue0"),
        const_cast<char*>("(JLjava/lang/String;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(qore_object_get_member_value)
    },
};

static JNINativeMethod qoreClosureNativeMethods[] = {
    {
        const_cast<char*>("call0"),
        const_cast<char*>("(JJ[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(qore_closure_call)
    },
    {
        const_cast<char*>("callSave0"),
        const_cast<char*>("(JJ[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(qore_closure_call_save)
    },
    // The release path runs in module-jni's NativeCleanup C++ thread, which calls
    // qore_closure_finalize directly without a JNI native-method binding.
};

static JNINativeMethod qoreURLClassLoaderNativeMethods[] = {
    {
        const_cast<char*>("getCachedClass0"),
        const_cast<char*>("(Ljava/lang/String;)[B"),
        reinterpret_cast<void*>(qore_url_classloader_get_cached_class),
    },
    {
        const_cast<char*>("getInternalClass0"),
        const_cast<char*>("(Ljava/lang/String;)[B"),
        reinterpret_cast<void*>(qore_url_classloader_get_internal_class),
    },
    {
        const_cast<char*>("generateByteCode0"),
        const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZJ)[B"),
        reinterpret_cast<void*>(qore_url_classloader_generate_byte_code),
    },
    {
        const_cast<char*>("getClassesInNamespace0"),
        const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;ZZLjava/util/ArrayList;)V"),
        reinterpret_cast<void*>(qore_url_classloader_get_classes_in_namespace),
    },
    {
        const_cast<char*>("getInternalClassesForPackage0"),
        const_cast<char*>("(JLjava/lang/String;Ljava/util/ArrayList;)V"),
        reinterpret_cast<void*>(qore_url_classloader_get_internal_classes_for_package),
    },
    {
        const_cast<char*>("getContextProgram0"),
        const_cast<char*>("(Lorg/qore/jni/QoreURLClassLoader;Lorg/qore/jni/BooleanWrapper;Z)J"),
        reinterpret_cast<void*>(qore_url_classloader_get_context_program),
    },
    {
        const_cast<char*>("clearCompilationCache0"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(qore_url_classloader_clear_compilation_cache),
    },
    {
        const_cast<char*>("shutdownContext0"),
        const_cast<char*>("()V"),
        reinterpret_cast<void*>(qore_url_classloader_shutdown_context),
    },
    {
        const_cast<char*>("dummy0"),
        const_cast<char*>("()V"),
        reinterpret_cast<void*>(qore_url_classloader_dummy),
    },
    {
        const_cast<char*>("debug0"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(qore_url_classloader_debug),
    },
    {
        const_cast<char*>("getSyscl0"),
        const_cast<char*>("()Lorg/qore/jni/QoreURLClassLoader;"),
        reinterpret_cast<void*>(qore_url_classloader_get_syscl),
    },
    {
        const_cast<char*>("getModuleLoader0"),
        const_cast<char*>("(Ljava/lang/String;)Lorg/qore/jni/QoreURLClassLoader;"),
        reinterpret_cast<void*>(qore_url_classloader_get_module_loader),
    },
};

static JNINativeMethod javaClassBuilderNativeMethods[] = {
    {
        const_cast<char*>("doStaticCall0"),
        const_cast<char*>("(Ljava/lang/String;JJJJ[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_class_builder_do_static_call),
    },
    {
        const_cast<char*>("doNormalCall0"),
        const_cast<char*>("(Ljava/lang/String;JJJ[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_class_builder_do_normal_call),
    },
    {
        const_cast<char*>("doFunctionCall0"),
        const_cast<char*>("(JJJ[Ljava/lang/Object;)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_class_builder_do_function_call),
    },
    {
        const_cast<char*>("getConstantValue0"),
        const_cast<char*>("(JJ)Ljava/lang/Object;"),
        reinterpret_cast<void*>(java_class_builder_get_constant_value),
    },
};

// calling Env::FindClass() when the class is not available will cause the class lookup to fail later after we define it
// therefore we have to only define the class if the java classes have not already been loaded
jobject Globals::getModuleClassLoader(const char* name) {
    if (!name || !*name) {
        return nullptr;
    }
    QoreProgram* pgm = ModuleManager::findUserModuleProgram(name);
    if (!pgm) {
        return nullptr;
    }
    // Lazily attach JniExternalProgramData (and thus a QoreURLClassLoader) to the module's
    // Program if it doesn't already have one — modules that don't `%requires jni` themselves
    // still need a single canonical loader to host their `qoremod.<mod>.*` Java classes for
    // every consumer Program that references them.
    JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
    if (!jpc) {
        return nullptr;
    }
    return jpc->getClassLoader();
}

LocalReference<jclass> Globals::findDefineClass(Env& env, const char* name, jobject loader, const unsigned char* buf,
    jsize bufLen) {
    if (!loader) {
        QoreString jname(name);
        jname.replaceAll(".", "/");
        if (already_initialized) {
            // During bootstrap, classes from qore-jni.jar are already loaded by the app classloader
            // We MUST use FindClass to get those classes so native methods are registered correctly
            // If FindClass fails (class not found), fall back to DefineClass
            return env.findDefineClass(jname.c_str(), nullptr, buf, bufLen);
        } else {
            return env.defineClass(jname.c_str(), nullptr, buf, bufLen);
        }
    } else {
        std::vector<jvalue> jargs(2);
        LocalReference<jbyteArray> jbyte_code = env.newByteArray(bufLen).as<jbyteArray>();
        for (jsize i = 0; i < static_cast<jsize>(bufLen); ++i) {
            env.setByteArrayElement(jbyte_code, i, ((const char*)buf)[i]);
        }

        LocalReference<jstring> bname = env.newString(name);
        jargs[0].l = bname;
        jargs[1].l = jbyte_code;
        return env.callObjectMethod(loader, Globals::methodQoreURLClassLoaderDefineClassUnconditional, &jargs[0]).as<jclass>();
    }
}

static bool digits_only(const char* s) {
    while (*s) {
        if (!isdigit(*s++)) {
            return false;
        }
    }

    return true;
}

static void check_java_version() {
    jni::Env env;
    LocalReference<jstring> jprop = env.newString("java.version");

    std::vector<jvalue> jargs(1);
    jargs[0].l = jprop;

    LocalReference<jstring> str = env.callStaticObjectMethod(Globals::classSystem,
        Globals::methodSystemGetProperty, &jargs[0]).as<jstring>();

    int mver = -1;
    Env::GetStringUtfChars jver(env, str);
    const char* p = strchr(jver.c_str(), '.');
    if (p) {
        QoreString maj(jver.c_str(), p - jver.c_str());
        mver = atoi(maj.c_str());
    } else {
        p = strchr(jver.c_str(), '-');
        if (p || digits_only(jver.c_str())) {
            mver = atoi(jver.c_str());
        } else {
            mver = atoi(jver.c_str());
            throw QoreStandardException("JAVA-VERSION-ERROR", "the jni module was compiled with Java %d, but runtime " \
                "Java version cannot be determined from '%s'; please install the correct version of Java and try again (%d)",
                JAVA_VERSION_MAJOR, jver.c_str());
        }
    }
    if (JAVA_VERSION_MAJOR != mver) {
        throw QoreStandardException("JAVA-VERSION-ERROR", "the jni module was compiled with Java %d; the runtime " \
            "Java version is %s; please install the correct version of Java and try again", JAVA_VERSION_MAJOR,
            jver.c_str());
    }
}

constexpr const char* INIT_PROP_NAME = "qore.QoreURLClassLoader.init";

void Globals::defineQoreURLClassLoader(Env& env) {
    printd(5, "defineQoreURLClassLoader() starting\n");

    findDefineClass(env, "org.qore.jni.QoreJavaFileObject", nullptr,
        java_org_qore_jni_QoreJavaFileObject_class, java_org_qore_jni_QoreJavaFileObject_class_len).makeGlobal();

    classBooleanWrapper = findDefineClass(env, "org.qore.jni.BooleanWrapper", nullptr,
        java_org_qore_jni_BooleanWrapper_class, java_org_qore_jni_BooleanWrapper_class_len).makeGlobal();
    methodBooleanWrapperSetTrue = env.getMethod(classBooleanWrapper, "setTrue", "()V");
    // NativeCleanup must be defined in the same loader as the wrapper classes
    // (QoreInvocationHandler, QoreClosure, QoreExceptionWrapper, QoreObjectBase) since
    // their constructors call NativeCleanup.register(); the inner Ref class also needs
    // to be available because we read its `ptr` and `kind` fields from the C++ cleanup
    // thread.  See native_cleanup_thread_main in Jvm.cpp.
    findDefineClass(env, "org.qore.jni.NativeCleanup", nullptr,
        java_org_qore_jni_NativeCleanup_class, java_org_qore_jni_NativeCleanup_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.NativeCleanup$Ref", nullptr,
        java_org_qore_jni_NativeCleanup_Ref_class, java_org_qore_jni_NativeCleanup_Ref_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.ClassModInfo", nullptr,
        java_org_qore_jni_ClassModInfo_class, java_org_qore_jni_ClassModInfo_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.QoreURLClassLoader$1", nullptr, java_org_qore_jni_QoreURLClassLoader_1_class,
        java_org_qore_jni_QoreURLClassLoader_1_class_len);
    findDefineClass(env, "org.qore.jni.QoreURLClassLoader$2", nullptr, java_org_qore_jni_QoreURLClassLoader_2_class,
        java_org_qore_jni_QoreURLClassLoader_2_class_len);

    // create our class loader to load module classes
    classQoreURLClassLoader = findDefineClass(env, "org.qore.jni.QoreURLClassLoader", nullptr,
        java_org_qore_jni_QoreURLClassLoader_class, java_org_qore_jni_QoreURLClassLoader_class_len).makeGlobal();

    env.registerNatives(classQoreURLClassLoader, qoreURLClassLoaderNativeMethods,
        sizeof(qoreURLClassLoaderNativeMethods) / sizeof(JNINativeMethod));

    ctorQoreURLClassLoader = env.getMethod(classQoreURLClassLoader, "<init>", "(JLjava/lang/ClassLoader;)V");
    methodQoreURLClassLoaderAddPath = env.getMethod(classQoreURLClassLoader, "addPath", "(Ljava/lang/String;)V");
    methodQoreURLClassLoaderAddParentPath = env.getMethod(classQoreURLClassLoader, "addParentPath",
        "(Ljava/lang/String;)V");
    methodQoreURLClassLoaderLoadClass = env.getMethod(classQoreURLClassLoader, "loadClass",
        "(Ljava/lang/String;)Ljava/lang/Class;");
    methodQoreURLClassLoaderLoadClassWithPtr = env.getMethod(classQoreURLClassLoader, "loadClassWithPtr",
        "(Ljava/lang/String;J)Ljava/lang/Class;");
    methodQoreURLClassLoaderLoadResolveClass = env.getMethod(classQoreURLClassLoader, "loadResolveClass",
        "(Ljava/lang/String;)Ljava/lang/Class;");
    methodQoreURLClassLoaderSetContext = env.getMethod(classQoreURLClassLoader, "setContext", "()V");
    methodQoreURLClassLoaderGetProgramPtr = env.getStaticMethod(classQoreURLClassLoader, "getProgramPtr", "()J");
    methodQoreURLClassLoaderAddPendingClass = env.getMethod(classQoreURLClassLoader, "addPendingClass",
        "(Ljava/lang/String;[B)V");
    methodQoreURLClassLoaderDefineResolveClass = env.getMethod(classQoreURLClassLoader, "defineResolveClass",
        "(Ljava/lang/String;[BII)Ljava/lang/Class;");
    methodQoreURLClassLoaderGetResolveClass = env.getMethod(classQoreURLClassLoader, "getResolveClass",
        "(Ljava/lang/String;)Ljava/lang/Class;");
    methodQoreURLClassLoaderClearCache = env.getMethod(classQoreURLClassLoader, "clearCache", "()V");
    methodQoreURLClassLoaderDefineClassUnconditional = env.getMethod(classQoreURLClassLoader, "defineClassUnconditional",
        "(Ljava/lang/String;[B)Ljava/lang/Class;");
    methodQoreURLClassLoaderGetPtr = env.getMethod(classQoreURLClassLoader, "getPtr", "()J");
    methodQoreURLClassLoaderGetCurrent = env.getStaticMethod(classQoreURLClassLoader, "getCurrent",
        "()Lorg/qore/jni/QoreURLClassLoader;");
    methodQoreURLClassLoaderCheckInProgress = env.getMethod(classQoreURLClassLoader, "checkInProgress",
        "(Ljava/lang/String;)Z");
    methodQoreURLClassLoaderClearProgramPtr = env.getMethod(classQoreURLClassLoader, "clearProgramPtr", "()V");

    //printd(5, "defineQoreURLClassLoader() done\n");
}

bool Globals::init() {
    Env env(false);

    // check version first
    classSystem = env.findClass("java/lang/System").makeGlobal();
    methodSystemSetProperty = env.getStaticMethod(classSystem, "setProperty",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    methodSystemGetProperty = env.getStaticMethod(classSystem, "getProperty",
        "(Ljava/lang/String;)Ljava/lang/String;");
    methodSystemGC = env.getStaticMethod(classSystem, "gc", "()V");
    check_java_version();

    // check for bootstrap initialization
    bool bootstrap = false;
    {
        jvalue jarg;
        LocalReference<jstring> jprop = env.newString(INIT_PROP_NAME);
        jarg.l = jprop;
        LocalReference<jstring> val = env.callObjectMethod(classSystem, methodSystemGetProperty, &jarg).as<jstring>();
        if (val) {
            Env::GetStringUtfChars strval(env, val);
            if (!strcmp(strval.c_str(), "true")) {
                bootstrap = true;
                // Set the static bootstrap flag early so findDefineClass() can use it
                // During bootstrap, FindClass() for our classes will fail because the system
                // classloader (QoreURLClassLoader) is still being initialized
                Globals::bootstrap = true;
                printd(5, "Globals::init() %s = %s\n", INIT_PROP_NAME, strval.c_str());
            }
        }

        printd(5, "Globals::init() %sbootstrap init\n", bootstrap ? "" : "non-");
    }

    // get exception info second
    classThrowable = env.findClass("java/lang/Throwable").makeGlobal();
    methodThrowableGetMessage = env.getMethod(classThrowable, "getMessage", "()Ljava/lang/String;");
    methodThrowableGetStackTrace = env.getMethod(classThrowable, "getStackTrace", "()[Ljava/lang/StackTraceElement;");
    methodThrowableGetCause = env.getMethod(classThrowable, "getCause", "()Ljava/lang/Throwable;");

    classStackTraceElement = env.findClass("java/lang/StackTraceElement").makeGlobal();
    methodStackTraceElementGetClassName = env.getMethod(classStackTraceElement, "getClassName",
        "()Ljava/lang/String;");
    methodStackTraceElementGetFileName = env.getMethod(classStackTraceElement, "getFileName", "()Ljava/lang/String;");
    methodStackTraceElementGetLineNumber = env.getMethod(classStackTraceElement, "getLineNumber", "()I");
    methodStackTraceElementGetMethodName = env.getMethod(classStackTraceElement, "getMethodName",
        "()Ljava/lang/String;");
    methodStackTraceElementIsNativeMethod = env.getMethod(classStackTraceElement, "isNativeMethod", "()Z");

    classQoreExceptionWrapper = findDefineClass(env, "org.qore.jni.QoreExceptionWrapper", nullptr,
        java_org_qore_jni_QoreExceptionWrapper_class, java_org_qore_jni_QoreExceptionWrapper_class_len).makeGlobal();
    env.registerNatives(classQoreExceptionWrapper, qoreExceptionWrapperNativeMethods,
        sizeof(qoreExceptionWrapperNativeMethods) / sizeof(JNINativeMethod));
    // QoreExceptionWrapper no longer has a CleanupState inner class — phantom-driven
    // release runs in the NativeCleanup C++ thread; see Jvm.cpp.
    ctorQoreExceptionWrapper = env.getMethod(classQoreExceptionWrapper, "<init>", "(J)V");
    methodQoreExceptionWrapperGet = env.getMethod(classQoreExceptionWrapper, "get", "()J");

    classQoreException = findDefineClass(env, "org.qore.jni.QoreException", nullptr,
        java_org_qore_jni_QoreException_class, java_org_qore_jni_QoreException_class_len).makeGlobal();
    methodQoreExceptionGetErr = env.getMethod(classQoreException, "getErr", "()Ljava/lang/String;");
    methodQoreExceptionGetDesc = env.getMethod(classQoreException, "getDesc", "()Ljava/lang/String;");
    methodQoreExceptionGetArg = env.getMethod(classQoreException, "getArg", "()Ljava/lang/Object;");

    // needed for exception handling
    classClass = env.findClass("java/lang/Class").makeGlobal();
    methodClassIsArray = env.getMethod(classClass, "isArray", "()Z");
    methodClassIsInterface = env.getMethod(classClass, "isInterface", "()Z");
    methodClassGetComponentType = env.getMethod(classClass, "getComponentType", "()Ljava/lang/Class;");
    methodClassGetClassLoader = env.getMethod(classClass, "getClassLoader", "()Ljava/lang/ClassLoader;");
    methodClassGetName = env.getMethod(classClass, "getName", "()Ljava/lang/String;");
    methodClassGetDeclaredFields = env.getMethod(classClass, "getDeclaredFields", "()[Ljava/lang/reflect/Field;");
    methodClassGetSuperClass = env.getMethod(classClass, "getSuperclass", "()Ljava/lang/Class;");
    methodClassGetInterfaces = env.getMethod(classClass, "getInterfaces", "()[Ljava/lang/Class;");
    methodClassGetDeclaredConstructors = env.getMethod(classClass, "getDeclaredConstructors",
        "()[Ljava/lang/reflect/Constructor;");
    methodClassGetDeclaredConstructor = env.getMethod(classClass, "getDeclaredConstructor",
        "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;");
    methodClassGetModifiers = env.getMethod(classClass, "getModifiers", "()I");
    methodClassIsPrimitive = env.getMethod(classClass, "isPrimitive", "()Z");
    methodClassGetDeclaredMethods = env.getMethod(classClass, "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");
    methodClassGetCanonicalName = env.getMethod(classClass, "getCanonicalName", "()Ljava/lang/String;");
    methodClassGetDeclaredField = env.getMethod(classClass, "getDeclaredField",
        "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
    methodClassIsAssignableFrom = env.getMethod(classClass, "isAssignableFrom", "(Ljava/lang/Class;)Z");
    methodClassGetMethod = env.getMethod(classClass, "getMethod",
        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");

    classClassLoader = env.findClass("java/lang/ClassLoader").makeGlobal();
    methodClassLoaderLoadClass = env.getMethod(classClassLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

    classQoreObjectBase = findDefineClass(env, "org.qore.jni.QoreObjectBase", nullptr,
        java_org_qore_jni_QoreObjectBase_class, java_org_qore_jni_QoreObjectBase_class_len).makeGlobal();
    env.registerNatives(classQoreObjectBase, qoreObjectBaseNativeMethods,
        sizeof(qoreObjectBaseNativeMethods) / sizeof(JNINativeMethod));
    // QoreObjectBase no longer has a CleanupState inner class — phantom-driven release
    // runs in the NativeCleanup C++ thread; see Jvm.cpp.
    //printd(5, "QoreObjectBase: %p\n", (jclass)classQoreObjectBase);

    classQoreObject = findDefineClass(env, "org.qore.jni.QoreObject", nullptr, java_org_qore_jni_QoreObject_class,
        java_org_qore_jni_QoreObject_class_len).makeGlobal();
    env.registerNatives(classQoreObject, qoreObjectNativeMethods,
        sizeof(qoreObjectNativeMethods) / sizeof(JNINativeMethod));
    ctorQoreObject = env.getMethod(classQoreObject, "<init>", "(J)V");

    classQoreJavaClassBase = findDefineClass(env, "org.qore.jni.QoreJavaClassBase", nullptr,
        java_org_qore_jni_QoreJavaClassBase_class, java_org_qore_jni_QoreJavaClassBase_class_len).makeGlobal();
    methodQoreObjectBaseGet = env.getMethod(classQoreObjectBase, "get", "()J");

    classQoreClosureMarker = findDefineClass(env, "org.qore.jni.QoreClosureMarker", nullptr,
        java_org_qore_jni_QoreClosureMarker_class, java_org_qore_jni_QoreClosureMarker_class_len).makeGlobal();
    classQoreClosureMarkerImpl = findDefineClass(env, "org.qore.jni.QoreClosureMarkerImpl", nullptr,
        java_org_qore_jni_QoreClosureMarkerImpl_class,
        java_org_qore_jni_QoreClosureMarkerImpl_class_len).makeGlobal();

    classQoreClosure = findDefineClass(env, "org.qore.jni.QoreClosure", nullptr, java_org_qore_jni_QoreClosure_class,
        java_org_qore_jni_QoreClosure_class_len).makeGlobal();
    env.registerNatives(classQoreClosure, qoreClosureNativeMethods,
        sizeof(qoreClosureNativeMethods) / sizeof(JNINativeMethod));
    // QoreClosure no longer has a CleanupState inner class — phantom-driven release
    // runs in the NativeCleanup C++ thread; see Jvm.cpp.
    ctorQoreClosure = env.getMethod(classQoreClosure, "<init>", "(J)V");
    methodQoreClosureGet = env.getMethod(classQoreClosure, "get", "()J");

    classQoreObjectWrapper = findDefineClass(env, "org.qore.jni.QoreObjectWrapper", nullptr,
        java_org_qore_jni_QoreObjectWrapper_class, java_org_qore_jni_QoreObjectWrapper_class_len).makeGlobal();

    classQoreJavaObjectPtr = findDefineClass(env, "org.qore.jni.QoreJavaObjectPtr", nullptr,
        java_org_qore_jni_QoreJavaObjectPtr_class, java_org_qore_jni_QoreJavaObjectPtr_class_len).makeGlobal();
    ctorQoreJavaObjectPtr = env.getMethod(classQoreJavaObjectPtr, "<init>", "(J)V");

    classPrimitiveVoid = getPrimitiveClass(env, "java/lang/Void");
    classPrimitiveBoolean = getPrimitiveClass(env, "java/lang/Boolean");
    classPrimitiveByte = getPrimitiveClass(env, "java/lang/Byte");
    classPrimitiveChar = getPrimitiveClass(env, "java/lang/Character");
    classPrimitiveShort = getPrimitiveClass(env, "java/lang/Short");
    classPrimitiveInt = getPrimitiveClass(env, "java/lang/Integer");
    classPrimitiveLong = getPrimitiveClass(env, "java/lang/Long");
    classPrimitiveFloat = getPrimitiveClass(env, "java/lang/Float");
    classPrimitiveDouble = getPrimitiveClass(env, "java/lang/Double");

    arrayClassByte = env.findClass("[B").makeGlobal();
    arrayClassObject = env.findClass("[Ljava/lang/Object;").makeGlobal();

    classObject = env.findClass("java/lang/Object").makeGlobal();
    methodObjectClone = env.getMethod(classObject, "clone", "()Ljava/lang/Object;");
    methodObjectGetClass = env.getMethod(classObject, "getClass", "()Ljava/lang/Class;");
    methodObjectEquals = env.getMethod(classObject, "equals", "(Ljava/lang/Object;)Z");
    methodObjectHashCode = env.getMethod(classObject, "hashCode", "()I");
    methodObjectToString = env.getMethod(classObject, "toString", "()Ljava/lang/String;");

    classString = env.findClass("java/lang/String").makeGlobal();

    classField = env.findClass("java/lang/reflect/Field").makeGlobal();
    methodFieldGetType = env.getMethod(classField, "getType", "()Ljava/lang/Class;");
    methodFieldGetDeclaringClass = env.getMethod(classField, "getDeclaringClass", "()Ljava/lang/Class;");
    methodFieldGetModifiers = env.getMethod(classField, "getModifiers", "()I");
    methodFieldGetName = env.getMethod(classField, "getName", "()Ljava/lang/String;");
    methodFieldGet = env.getMethod(classField, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
    methodFieldGetLong = env.getMethod(classField, "getLong", "(Ljava/lang/Object;)J");
    methodFieldSetAccessible = env.getMethod(classField, "setAccessible", "(Z)V");

    classMethod = env.findClass("java/lang/reflect/Method").makeGlobal();
    methodMethodGetReturnType = env.getMethod(classMethod, "getReturnType", "()Ljava/lang/Class;");
    methodMethodGetParameterTypes = env.getMethod(classMethod, "getParameterTypes", "()[Ljava/lang/Class;");
    methodMethodGetDeclaringClass = env.getMethod(classMethod, "getDeclaringClass", "()Ljava/lang/Class;");
    methodMethodGetModifiers = env.getMethod(classMethod, "getModifiers", "()I");
    methodMethodIsVarArgs = env.getMethod(classMethod, "isVarArgs", "()Z");
    methodMethodGetName = env.getMethod(classMethod, "getName", "()Ljava/lang/String;");
    methodMethodToGenericString = env.getMethod(classMethod, "toGenericString", "()Ljava/lang/String;");

    classConstructor = env.findClass("java/lang/reflect/Constructor").makeGlobal();
    methodConstructorGetParameterTypes = env.getMethod(classConstructor, "getParameterTypes", "()[Ljava/lang/Class;");
    methodConstructorToString = env.getMethod(classConstructor, "toString", "()Ljava/lang/String;");
    methodConstructorGetModifiers = env.getMethod(classConstructor, "getModifiers", "()I");
    methodConstructorIsVarArgs = env.getMethod(classConstructor, "isVarArgs", "()Z");
    methodConstructorNewInstance = env.getMethod(classConstructor, "newInstance",
        "([Ljava/lang/Object;)Ljava/lang/Object;");

    classQoreInvocationHandler = findDefineClass(env, "org.qore.jni.QoreInvocationHandler", nullptr,
        java_org_qore_jni_QoreInvocationHandler_class, java_org_qore_jni_QoreInvocationHandler_class_len).makeGlobal();
    env.registerNatives(classQoreInvocationHandler, invocationHandlerNativeMethods, 2);
    // QoreInvocationHandler's inner class is now $State (formerly $CleanupState).
    // It serves only the explicit-destroy synchronisation; phantom-driven release
    // runs in the NativeCleanup C++ thread (see Jvm.cpp).
    findDefineClass(env, "org.qore.jni.QoreInvocationHandler$State", nullptr,
        java_org_qore_jni_QoreInvocationHandler_State_class,
        java_org_qore_jni_QoreInvocationHandler_State_class_len);
    ctorQoreInvocationHandler = env.getMethod(classQoreInvocationHandler, "<init>", "(J)V");
    methodQoreInvocationHandlerDestroy = env.getMethod(classQoreInvocationHandler, "destroy", "()V");

    classQoreJavaApi = findDefineClass(env, "org.qore.jni.QoreJavaApi", nullptr, java_org_qore_jni_QoreJavaApi_class,
        java_org_qore_jni_QoreJavaApi_class_len).makeGlobal();
    env.registerNatives(classQoreJavaApi, qoreJavaApiNativeMethods,
        sizeof(qoreJavaApiNativeMethods) / sizeof(JNINativeMethod));
    methodQoreJavaApiGetStackTrace = env.getStaticMethod(classQoreJavaApi, "getStackTrace",
        "()[Ljava/lang/StackTraceElement;");

    classProxy = env.findClass("java/lang/reflect/Proxy").makeGlobal();
    methodProxyNewProxyInstance = env.getStaticMethod(classProxy, "newProxyInstance",
        "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;");

    classThread = env.findClass("java/lang/Thread").makeGlobal();
    methodThreadCurrentThread = env.getStaticMethod(classThread, "currentThread", "()Ljava/lang/Thread;");
    methodThreadGetContextClassLoader = env.getMethod(classThread, "getContextClassLoader",
        "()Ljava/lang/ClassLoader;");

    classHashMap = env.findClass("java/util/HashMap").makeGlobal();

    classHash = findDefineClass(env, "org.qore.jni.Hash", nullptr, java_org_qore_jni_Hash_class,
        java_org_qore_jni_Hash_class_len).makeGlobal();
    ctorHash = env.getMethod(classHash, "<init>", "()V");
    methodHashPut = env.getMethod(classHash, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    findDefineClass(env, "org.qore.jni.Hash$1", nullptr, java_org_qore_jni_Hash_1_class,
        java_org_qore_jni_Hash_1_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$2", nullptr, java_org_qore_jni_Hash_2_class,
        java_org_qore_jni_Hash_2_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$3", nullptr, java_org_qore_jni_Hash_3_class,
        java_org_qore_jni_Hash_3_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$4", nullptr, java_org_qore_jni_Hash_4_class,
        java_org_qore_jni_Hash_4_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$5", nullptr, java_org_qore_jni_Hash_5_class,
        java_org_qore_jni_Hash_5_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$6", nullptr, java_org_qore_jni_Hash_6_class,
        java_org_qore_jni_Hash_6_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$7", nullptr, java_org_qore_jni_Hash_7_class,
        java_org_qore_jni_Hash_7_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$8", nullptr, java_org_qore_jni_Hash_8_class,
        java_org_qore_jni_Hash_8_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$9", nullptr, java_org_qore_jni_Hash_9_class,
        java_org_qore_jni_Hash_9_class_len).makeGlobal();
    findDefineClass(env, "org.qore.jni.Hash$10", nullptr, java_org_qore_jni_Hash_10_class,
        java_org_qore_jni_Hash_10_class_len).makeGlobal();

    classMap = env.findClass("java/util/Map").makeGlobal();
    methodMapEntrySet = env.getMethod(classMap, "entrySet", "()Ljava/util/Set;");

    classList = env.findClass("java/util/List").makeGlobal();
    methodListSize = env.getMethod(classList, "size", "()I");
    methodListGet = env.getMethod(classList, "get", "(I)Ljava/lang/Object;");

    classArrayList = env.findClass("java/util/ArrayList").makeGlobal();
    ctorArrayList = env.getMethod(classArrayList, "<init>", "()V");
    methodArrayListAdd = env.getMethod(classArrayList, "add", "(Ljava/lang/Object;)Z");
    methodArrayListGet = env.getMethod(classArrayList, "get", "(I)Ljava/lang/Object;");
    methodArrayListRemove = env.getMethod(classArrayList, "remove", "(I)Ljava/lang/Object;");
    methodArrayListSize = env.getMethod(classArrayList, "size", "()I");
    methodArrayListToArray = env.getMethod(classArrayList, "toArray", "()[Ljava/lang/Object;");

    classSet = env.findClass("java/util/Set").makeGlobal();
    methodSetIterator = env.getMethod(classSet, "iterator", "()Ljava/util/Iterator;");

    classEntry = env.findClass("java/util/Map$Entry").makeGlobal();
    methodEntryGetKey = env.getMethod(classEntry, "getKey", "()Ljava/lang/Object;");
    methodEntryGetValue = env.getMethod(classEntry, "getValue", "()Ljava/lang/Object;");

    classIterator = env.findClass("java/util/Iterator").makeGlobal();
    methodIteratorHasNext = env.getMethod(classIterator, "hasNext", "()Z");
    methodIteratorNext = env.getMethod(classIterator, "next", "()Ljava/lang/Object;");

    classZonedDateTime = env.findClass("java/time/ZonedDateTime").makeGlobal();
    methodZonedDateTimeParse = env.getStaticMethod(classZonedDateTime, "parse",
        "(Ljava/lang/CharSequence;)Ljava/time/ZonedDateTime;");
    methodZonedDateTimeToString = env.getMethod(classZonedDateTime, "toString", "()Ljava/lang/String;");

    // issue #4892: LocalDateTime support for Kotlin/Java date conversion
    classLocalDateTime = env.findClass("java/time/LocalDateTime").makeGlobal();
    methodLocalDateTimeToString = env.getMethod(classLocalDateTime, "toString", "()Ljava/lang/String;");

    // issue #4892: Instant support for Kotlin/Java date conversion
    classInstant = env.findClass("java/time/Instant").makeGlobal();
    methodInstantToString = env.getMethod(classInstant, "toString", "()Ljava/lang/String;");

    classQoreRelativeTime = findDefineClass(env, "org.qore.jni.QoreRelativeTime", nullptr,
        java_org_qore_jni_QoreRelativeTime_class, java_org_qore_jni_QoreRelativeTime_class_len).makeGlobal();
    ctorQoreRelativeTime = env.getMethod(classQoreRelativeTime, "<init>", "(IIIIIII)V");
    fieldQoreRelativeTimeYear = env.getField(classQoreRelativeTime, "year", "I");
    fieldQoreRelativeTimeMonth = env.getField(classQoreRelativeTime, "month", "I");
    fieldQoreRelativeTimeDay = env.getField(classQoreRelativeTime, "day", "I");
    fieldQoreRelativeTimeHour = env.getField(classQoreRelativeTime, "hour", "I");
    fieldQoreRelativeTimeMinute = env.getField(classQoreRelativeTime, "minute", "I");
    fieldQoreRelativeTimeSecond = env.getField(classQoreRelativeTime, "second", "I");
    fieldQoreRelativeTimeUs = env.getField(classQoreRelativeTime, "us", "I");

    classBigDecimal = env.findClass("java/math/BigDecimal").makeGlobal();
    ctorBigDecimal = env.getMethod(classBigDecimal, "<init>", "(Ljava/lang/String;)V");
    methodBigDecimalToString = env.getMethod(classBigDecimal, "toString", "()Ljava/lang/String;");

    classArrays = env.findClass("java/util/Arrays").makeGlobal();
    methodArraysToString = env.getStaticMethod(classArrays, "toString", "([Ljava/lang/Object;)Ljava/lang/String;");
    methodArraysDeepToString = env.getStaticMethod(classArrays, "deepToString",
        "([Ljava/lang/Object;)Ljava/lang/String;");

    classBoolean = env.findClass("java/lang/Boolean").makeGlobal();
    ctorBoolean = env.getMethod(classBoolean, "<init>", "(Z)V");
    methodBooleanBooleanValue = env.getMethod(classBoolean, "booleanValue", "()Z");

    classInteger = env.findClass("java/lang/Integer").makeGlobal();
    ctorInteger = env.getMethod(classInteger, "<init>", "(I)V");
    methodIntegerIntValue = env.getMethod(classInteger, "intValue", "()I");

    classDouble = env.findClass("java/lang/Double").makeGlobal();
    ctorDouble = env.getMethod(classDouble, "<init>", "(D)V");
    methodDoubleDoubleValue = env.getMethod(classDouble, "doubleValue", "()D");

    classLong = env.findClass("java/lang/Long").makeGlobal();
    ctorLong = env.getMethod(classLong, "<init>", "(J)V");
    methodLongLongValue = env.getMethod(classLong, "longValue", "()J");

    classShort = env.findClass("java/lang/Short").makeGlobal();
    ctorShort = env.getMethod(classShort, "<init>", "(S)V");
    methodShortShortValue = env.getMethod(classShort, "shortValue", "()S");

    classByte = env.findClass("java/lang/Byte").makeGlobal();
    ctorByte = env.getMethod(classByte, "<init>", "(B)V");
    methodByteByteValue = env.getMethod(classByte, "byteValue", "()B");

    classFloat = env.findClass("java/lang/Float").makeGlobal();
    ctorFloat = env.getMethod(classFloat, "<init>", "(F)V");
    methodFloatFloatValue = env.getMethod(classFloat, "floatValue", "()F");

    classCharacter = env.findClass("java/lang/Character").makeGlobal();
    ctorCharacter = env.getMethod(classCharacter, "<init>", "(C)V");
    methodCharacterCharValue = env.getMethod(classCharacter, "charValue", "()C");

    classCharSequence = env.findClass("java/lang/CharSequence").makeGlobal();

    classProperties = env.findClass("java/util/Properties").makeGlobal();
    ctorProperties = env.getMethod(classProperties, "<init>", "()V");
    methodPropertiesSetProperty = env.getMethod(classProperties, "setProperty",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

    classDriverManager = env.findClass("java/sql/DriverManager").makeGlobal();
    methodDriverManagerGetConnection = env.getStaticMethod(classDriverManager, "getConnection",
        "(Ljava/lang/String;Ljava/util/Properties;)Ljava/sql/Connection;");

    classConnection = env.findClass("java/sql/Connection").makeGlobal();
    methodConnectionClose = env.getMethod(classConnection, "close", "()V");
    methodConnectionCommit = env.getMethod(classConnection, "commit", "()V");
    methodConnectionRollback = env.getMethod(classConnection, "rollback", "()V");
    methodConnectionCreateArrayOf = env.getMethod(classConnection, "createArrayOf",
        "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/sql/Array;");
    methodConnectionGetMetaData = env.getMethod(classConnection, "getMetaData", "()Ljava/sql/DatabaseMetaData;");
    methodConnectionPrepareStatement = env.getMethod(classConnection, "prepareStatement",
        "(Ljava/lang/String;)Ljava/sql/PreparedStatement;");
    methodConnectionPrepareStatementArray = env.getMethod(classConnection, "prepareStatement",
        "(Ljava/lang/String;[Ljava/lang/String;)Ljava/sql/PreparedStatement;");
    methodConnectionSetAutoCommit = env.getMethod(classConnection, "setAutoCommit", "(Z)V");
    methodConnectionIsValid = env.getMethod(classConnection, "isValid", "(I)Z");

    classDatabaseMetaData = env.findClass("java/sql/DatabaseMetaData").makeGlobal();
    methodDatabaseMetaDataGetDatabaseMajorVersion = env.getMethod(classDatabaseMetaData, "getDatabaseMajorVersion",
        "()I");
    methodDatabaseMetaDataGetDatabaseMinorVersion = env.getMethod(classDatabaseMetaData, "getDatabaseMinorVersion",
        "()I");
    methodDatabaseMetaDataGetDatabaseProductName = env.getMethod(classDatabaseMetaData, "getDatabaseProductName",
        "()Ljava/lang/String;");
    methodDatabaseMetaDataGetDatabaseProductVersion = env.getMethod(classDatabaseMetaData,
        "getDatabaseProductVersion", "()Ljava/lang/String;");
    methodDatabaseMetaDataGetDriverMajorVersion = env.getMethod(classDatabaseMetaData, "getDriverMajorVersion",
        "()I");
    methodDatabaseMetaDataGetDriverMinorVersion = env.getMethod(classDatabaseMetaData, "getDriverMinorVersion",
        "()I");
    methodDatabaseMetaDataGetDriverName = env.getMethod(classDatabaseMetaData, "getDriverName",
        "()Ljava/lang/String;");
    methodDatabaseMetaDataGetDriverVersion = env.getMethod(classDatabaseMetaData, "getDriverVersion",
        "()Ljava/lang/String;");

    classPreparedStatement = env.findClass("java/sql/PreparedStatement").makeGlobal();
    methodPreparedStatementAddBatch = env.getMethod(classPreparedStatement, "addBatch", "()V");
    methodPreparedStatementClose = env.getMethod(classPreparedStatement, "close", "()V");
    methodPreparedStatementExecute = env.getMethod(classPreparedStatement, "execute", "()Z");
    methodPreparedStatementExecuteBatch = env.getMethod(classPreparedStatement, "executeBatch", "()[I");
    methodPreparedStatementGetResultSet = env.getMethod(classPreparedStatement, "getResultSet",
        "()Ljava/sql/ResultSet;");
    methodPreparedStatementGetMoreResults = env.getMethod(classPreparedStatement, "getMoreResults", "()Z");
    methodPreparedStatementGetUpdateCount = env.getMethod(classPreparedStatement, "getUpdateCount", "()I");
    methodPreparedStatementSetArray = env.getMethod(classPreparedStatement, "setArray", "(ILjava/sql/Array;)V");
    methodPreparedStatementSetBigDecimal = env.getMethod(classPreparedStatement, "setBigDecimal",
        "(ILjava/math/BigDecimal;)V");
    methodPreparedStatementSetBoolean = env.getMethod(classPreparedStatement, "setBoolean", "(IZ)V");
    methodPreparedStatementSetByte = env.getMethod(classPreparedStatement, "setByte", "(IB)V");
    methodPreparedStatementSetBytes = env.getMethod(classPreparedStatement, "setBytes", "(I[B)V");
    methodPreparedStatementSetDouble = env.getMethod(classPreparedStatement, "setDouble", "(ID)V");
    methodPreparedStatementSetInt = env.getMethod(classPreparedStatement, "setInt", "(II)V");
    methodPreparedStatementSetLong = env.getMethod(classPreparedStatement, "setLong", "(IJ)V");
    methodPreparedStatementSetShort = env.getMethod(classPreparedStatement, "setShort", "(IS)V");
    methodPreparedStatementSetNull = env.getMethod(classPreparedStatement, "setNull", "(II)V");
    methodPreparedStatementSetString = env.getMethod(classPreparedStatement, "setString", "(ILjava/lang/String;)V");
    methodPreparedStatementSetTimestamp = env.getMethod(classPreparedStatement, "setTimestamp",
        "(ILjava/sql/Timestamp;)V");

    classTimestamp = env.findClass("java/sql/Timestamp").makeGlobal();
    ctorTimestamp = env.getMethod(classTimestamp, "<init>", "(J)V");
    methodTimestampSetNanos = env.getMethod(classTimestamp, "setNanos", "(I)V");
    methodTimestampToString = env.getMethod(classTimestamp, "toString", "()Ljava/lang/String;");

    classDate = env.findClass("java/sql/Date").makeGlobal();
    methodDateToString = env.getMethod(classDate, "toString", "()Ljava/lang/String;");

    classTime = env.findClass("java/sql/Time").makeGlobal();
    methodTimeToString = env.getMethod(classTime, "toString", "()Ljava/lang/String;");

    classResultSet = env.findClass("java/sql/ResultSet").makeGlobal();
    methodResultSetClose = env.getMethod(classResultSet, "close", "()V");
    methodResultSetNext = env.getMethod(classResultSet, "next", "()Z");
    methodResultSetGetMetaData = env.getMethod(classResultSet, "getMetaData", "()Ljava/sql/ResultSetMetaData;");
    methodResultSetGetArray = env.getMethod(classResultSet, "getArray", "(I)Ljava/sql/Array;");
    methodResultSetGetObject = env.getMethod(classResultSet, "getObject", "(I)Ljava/lang/Object;");

    classResultSetMetaData = env.findClass("java/sql/ResultSetMetaData").makeGlobal();
    methodResultSetMetaDataGetColumnClassName = env.getMethod(classResultSetMetaData, "getColumnClassName",
        "(I)Ljava/lang/String;");
    methodResultSetMetaDataGetColumnCount = env.getMethod(classResultSetMetaData, "getColumnCount", "()I");
    methodResultSetMetaDataGetColumnLabel = env.getMethod(classResultSetMetaData, "getColumnLabel",
        "(I)Ljava/lang/String;");
    methodResultSetMetaDataGetColumnType = env.getMethod(classResultSetMetaData, "getColumnType",
        "(I)I");

    classArray = env.findClass("java/sql/Array").makeGlobal();
    methodArrayGetArray = env.getMethod(classArray, "getArray", "()Ljava/lang/Object;");

    classSQLException = env.findClass("java/sql/SQLException").makeGlobal();

    classServiceLoader = env.findClass("java/util/ServiceLoader").makeGlobal();
    methodServiceLoaderIterator = env.getMethod(classServiceLoader, "iterator",
        "()Ljava/util/Iterator;");

    classDriver = env.findClass("java/sql/Driver").makeGlobal();

    {
        LocalReference<jclass> classTypes = env.findClass("java/sql/Types");
        jfieldID field = env.getStaticField(classTypes, "NULL", "I");
        typeNull = env.getStaticIntField(classTypes, field);
        field = env.getStaticField(classTypes, "CHAR", "I");
        typeChar = env.getStaticIntField(classTypes, field);
    }

    assert(!classQoreURLClassLoader);
    defineQoreURLClassLoader(env);

    javaQoreClassField = env.newString(JAVA_QORE_CLASS_FIELD).makeGlobal();

    if (bootstrap) {
        classJavaClassBuilder = env.findClass("org/qore/jni/JavaClassBuilder").makeGlobal();

        // check if we are using QoreURLClassLoader as the system class loader
        LocalReference<jstring> jprop = env.newString("java.system.class.loader");
        std::vector<jvalue> jargs(1);
        jargs[0].l = jprop;
        LocalReference<jstring> str = env.callStaticObjectMethod(Globals::classSystem,
            Globals::methodSystemGetProperty, &jargs[0]).as<jstring>();
        if (!str) {
            bootstrap = false;
        } else {
            Env::GetStringUtfChars val(env, str);
            if (val != "org.qore.jni.QoreURLClassLoader") {
                bootstrap = false;
            }
        }
        // the bootstrap class loader will assign itself when it is created
    }

    if (!bootstrap) {
        printd(5, "Globals::init() creating syscl\n");
        jmethodID ctorQoreURLClassLoaderSys = env.getMethod(classQoreURLClassLoader, "<init>", "(J)V");
        jvalue jarg;
        jarg.j = (jlong)Globals::createJavaContextProgram();
        printd(5, "Global syscl pgm: %p\n", jarg.j);
        syscl = env.newObject(classQoreURLClassLoader, ctorQoreURLClassLoaderSys, &jarg).makeGlobal();

        {
            std::vector<jvalue> jargs(2);
            LocalReference<jbyteArray> jbyte_code =
                env.newByteArray(java_org_qore_jni_JavaClassBuilder_1_class_len).as<jbyteArray>();
            for (jsize i = 0; i < static_cast<jsize>(java_org_qore_jni_JavaClassBuilder_1_class_len); ++i) {
                env.setByteArrayElement(jbyte_code, i, ((const char*)java_org_qore_jni_JavaClassBuilder_1_class)[i]);
            }
            LocalReference<jstring> bname = env.newString("org.qore.jni.JavaClassBuilder$1");
            jargs[0].l = bname;
            jargs[1].l = jbyte_code;
            env.callVoidMethod(syscl, Globals::methodQoreURLClassLoaderAddPendingClass, &jargs[0]);
            env.callObjectMethod(syscl, Globals::methodQoreURLClassLoaderGetResolveClass,
                &jargs[0]).as<jclass>().makeGlobal();
        }
        {
            std::vector<jvalue> jargs(2);
            LocalReference<jbyteArray> jbyte_code =
                env.newByteArray(java_org_qore_jni_JavaClassBuilder_class_len).as<jbyteArray>();
            for (jsize i = 0; i < static_cast<jsize>(java_org_qore_jni_JavaClassBuilder_class_len); ++i) {
                env.setByteArrayElement(jbyte_code, i, ((const char*)java_org_qore_jni_JavaClassBuilder_class)[i]);
            }
            LocalReference<jstring> bname = env.newString("org.qore.jni.JavaClassBuilder");
            jargs[0].l = bname;
            jargs[1].l = jbyte_code;
            env.callVoidMethod(syscl, Globals::methodQoreURLClassLoaderAddPendingClass, &jargs[0]);
        }
        {
            std::vector<jvalue> jargs(1);
            LocalReference<jstring> bname = env.newString("org.qore.jni.JavaClassBuilder");
            jargs[0].l = bname;
            classJavaClassBuilder = env.callObjectMethod(syscl, Globals::methodQoreURLClassLoaderGetResolveClass,
                &jargs[0]).as<jclass>().makeGlobal();

#ifdef DEBUG
            LocalReference<jobject> cl = env.callObjectMethod(classJavaClassBuilder,
                Globals::methodClassGetClassLoader, nullptr);
            assert(env.isSameObject(cl, syscl));
#endif
        }
    }

    env.registerNatives(classJavaClassBuilder, javaClassBuilderNativeMethods,
        sizeof(javaClassBuilderNativeMethods) / sizeof(JNINativeMethod));

    methodJavaClassBuilderGetClassBuilder = env.getStaticMethod(classJavaClassBuilder, "getClassBuilder",
        "(Ljava/lang/String;Ljava/lang/Class;Ljava/util/ArrayList;ZJ)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderGetFunctionConstantClassBuilder = env.getStaticMethod(classJavaClassBuilder,
        "getFunctionConstantClassBuilder",
        "(Ljava/lang/String;)Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderAddFunction = env.getStaticMethod(classJavaClassBuilder, "addFunction",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/String;JJJ" \
        "Lnet/bytebuddy/description/type/TypeDefinition;Ljava/util/List;Z)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderAddStaticField = env.getStaticMethod(classJavaClassBuilder, "addStaticField",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/String;I" \
        "Lnet/bytebuddy/description/type/TypeDescription;JLjava/util/ArrayList;)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderCreateStaticInitializer = env.getStaticMethod(classJavaClassBuilder,
        "createStaticInitializer",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/String;JLjava/util/ArrayList;)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderAddConstructor = env.getStaticMethod(classJavaClassBuilder, "addConstructor",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/Class;" \
            "JJILjava/util/List;Z)Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderAddNormalMethod = env.getStaticMethod(classJavaClassBuilder, "addNormalMethod",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/String;JJI" \
        "Lnet/bytebuddy/description/type/TypeDefinition;Ljava/util/List;ZZ)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderAddStaticMethod = env.getStaticMethod(classJavaClassBuilder, "addStaticMethod",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Ljava/lang/String;JJJI" \
        "Lnet/bytebuddy/description/type/TypeDefinition;Ljava/util/List;Z)" \
        "Lnet/bytebuddy/dynamic/DynamicType$Builder;");
    methodJavaClassBuilderGetByteCodeFromBuilder = env.getStaticMethod(classJavaClassBuilder,
        "getByteCodeFromBuilder",
        "(Lnet/bytebuddy/dynamic/DynamicType$Builder;Lorg/qore/jni/QoreURLClassLoader;)[B");
    methodJavaClassBuilderGetTypeDescriptionCls = env.getStaticMethod(classJavaClassBuilder, "getTypeDescription",
        "(Ljava/lang/Class;)Lnet/bytebuddy/description/type/TypeDescription;");
    methodJavaClassBuilderGetTypeDescriptionStr = env.getStaticMethod(classJavaClassBuilder, "getTypeDescription",
        "(Ljava/lang/String;)Lnet/bytebuddy/description/type/TypeDescription;");
    methodJavaClassBuilderFindBaseClassMethodConflict = env.getStaticMethod(classJavaClassBuilder,
        "findBaseClassMethodConflict", "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/List;Z)Z");
    methodJavaClassBuilderIsFinalBaseClassMethod = env.getStaticMethod(classJavaClassBuilder,
        "isFinalBaseClassMethod", "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/List;)Z");


    // Note: KotlinMetadataHelper is lazily initialized by initKotlinMetadataHelper() when first needed
    // This is because we can't load it until the classloader has access to qore-jni.jar

    // NOTE: GraphicsEnvironment is loaded lazily (in ensureGraphicsEnvironment()) to avoid
    // spawning a subprocess during init, which triggers a StackOverflowError on the JVM's
    // "process reaper" thread (hardcoded 32KB stack in JDK 25)

    return bootstrap;
}

static std::once_flag graphicsEnvironmentInitFlag;

void Globals::ensureGraphicsEnvironment() {
    std::call_once(graphicsEnvironmentInitFlag, []() {
        Env env;
        classGraphicsEnvironment = env.findClass("java/awt/GraphicsEnvironment").makeGlobal();
        methodGraphicsEnvironmentIsHeadless = env.getStaticMethod(classGraphicsEnvironment, "isHeadless", "()Z");
    });
}

static std::once_flag kotlinMetadataHelperInitFlag;

static void doInitKotlinMetadataHelper() {
    Env env;
    // Try to find the class - it should be available through the classloader now
    try {
        Globals::classKotlinMetadataHelper = env.findClass("org/qore/jni/KotlinMetadataHelper").makeGlobal();
        Globals::methodKotlinMetadataHelperIsKotlinClass = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "isKotlinClass", "(Ljava/lang/Class;)Z");
        Globals::methodKotlinMetadataHelperGetKotlinKind = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "getKotlinKind", "(Ljava/lang/Class;)I");
        Globals::methodKotlinMetadataHelperIsCompanionObject = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "isCompanionObject", "(Ljava/lang/Class;)Z");
        Globals::methodKotlinMetadataHelperIsDataClass = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "isDataClass", "(Ljava/lang/Class;)Z");
        Globals::methodKotlinMetadataHelperIsFileFacade = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "isFileFacade", "(Ljava/lang/Class;)Z");
        Globals::methodKotlinMetadataHelperIsKotlinRuntimeAvailable = env.getStaticMethod(Globals::classKotlinMetadataHelper,
            "isKotlinRuntimeAvailable", "()Z");
    } catch (jni::Exception& e) {
        // KotlinMetadataHelper not available - this is fine, Kotlin support will be limited
        printd(5, "KotlinMetadataHelper not available\n");
        e.ignore();
    }
}

void Globals::initKotlinMetadataHelper() {
    std::call_once(kotlinMetadataHelperInitFlag, doInitKotlinMetadataHelper);
}

static std::mutex kotlinScriptEngineMutex;
static bool kotlinScriptEngineInitAttempted = false;

void Globals::initKotlinScriptEngine() {
    std::lock_guard<std::mutex> lock(kotlinScriptEngineMutex);

    // If already initialized successfully, nothing to do
    if (classKotlinScriptEngine) {
        return;
    }

    // If we've already attempted and failed, don't retry automatically
    // The user can call retryInit() to force a retry
    if (kotlinScriptEngineInitAttempted) {
        return;
    }

    kotlinScriptEngineInitAttempted = true;

    Env env;
    // Try to find the class - first try the builtin classloader, then the program classloader
    try {
        classKotlinScriptEngine = env.findClass("org/qore/jni/KotlinScriptEngine").makeGlobal();
    } catch (jni::Exception& e) {
        e.ignore();
        // Try with the program classloader
        JniExternalProgramData* jpc = jni_get_context();
        if (jpc) {
            try {
                LocalReference<jstring> jname = env.newString("org.qore.jni.KotlinScriptEngine");
                jvalue jarg;
                jarg.l = jname;
                classKotlinScriptEngine = env.callObjectMethod(jpc->getClassLoader(),
                    methodQoreURLClassLoaderLoadClass, &jarg).as<jclass>().makeGlobal();
            } catch (jni::Exception& e2) {
                printd(5, "KotlinScriptEngine not available via program classloader\n");
                e2.ignore();
                kotlinScriptEngineInitAttempted = false;
                return;
            }
        } else {
            printd(5, "KotlinScriptEngine not available - no program context\n");
            kotlinScriptEngineInitAttempted = false;
            return;
        }
    }

    try {
        methodKotlinScriptEngineIsAvailable = env.getStaticMethod(classKotlinScriptEngine,
            "isAvailable", "()Z");
        methodKotlinScriptEngineGetInitError = env.getStaticMethod(classKotlinScriptEngine,
            "getInitError", "()Ljava/lang/String;");
        methodKotlinScriptEngineEval = env.getStaticMethod(classKotlinScriptEngine,
            "eval", "(Ljava/lang/String;)Ljava/lang/Object;");
        methodKotlinScriptEngineEvalWithBindings = env.getStaticMethod(classKotlinScriptEngine,
            "evalWithBindings", "(Ljava/lang/String;Ljava/util/HashMap;)Ljava/lang/Object;");
        methodKotlinScriptEngineRetryInit = env.getStaticMethod(classKotlinScriptEngine,
            "retryInit", "()Z");
    } catch (jni::Exception& e) {
        // Failed to get methods - reset
        printd(5, "KotlinScriptEngine method lookup failed\n");
        e.ignore();
        classKotlinScriptEngine = nullptr;
        kotlinScriptEngineInitAttempted = false;
    }
}

// ---------------- NativeCleanup C++ background thread ---------------------
//
// Replaces the legacy java.lang.ref.Cleaner-driven release0 dispatch (which races
// against JIT codecache reorganisation and class-unloading-driven adapter eviction)
// with a dedicated C++ thread that polls org.qore.jni.NativeCleanup.queue and
// dispatches the native release directly in C++ — no JNI native-method invocation
// adapter on the path, so SIGSEGV in the daemon Common-Cleaner thread cannot
// happen.
//
// Lifecycle:
//   - startNativeCleanupThread(): called from Jvm::createVM after Globals::init.
//     Caches NativeCleanup / Ref class refs, the queue static, the markProcessed
//     and shutdown methods, and Ref's ptr/kind fields.  Spawns the thread.
//   - The thread main loop calls queue.remove() (blocking).  On each Ref it reads
//     ptr+kind via JNI field access, dispatches by kind, then calls back into
//     NativeCleanup.markProcessed(ref) so Java can drop the strong ref.
//   - stopNativeCleanupThread(): called from Jvm::destroyVM before DestroyJavaVM.
//     Calls NativeCleanup.shutdown() (which enqueues a sentinel), then joins.

namespace {

GlobalReference<jclass> nativeCleanupClass;
GlobalReference<jclass> nativeCleanupRefClass;
GlobalReference<jobject> nativeCleanupQueue;     // global ref to ReferenceQueue
jmethodID methodReferenceQueueRemove = nullptr;
jmethodID methodNativeCleanupMarkProcessed = nullptr;
jmethodID methodNativeCleanupShutdown = nullptr;
jfieldID fieldNativeCleanupRefPtr = nullptr;
jfieldID fieldNativeCleanupRefKind = nullptr;

// Heap-allocated so we don't trigger std::terminate on abnormal exit.  A
// std::thread destructor on a joinable thread aborts the process with
// "terminate called without an active exception".  Some module-jni consumers
// (notably the external `oload --compile-java` worker spawned by qorus-core)
// can exit without going through Jvm::destroyVM, in which case
// stopNativeCleanupThread is never called.  Leaking the heap pointer at
// abnormal exit is benign — the kernel reaps the thread when the process is
// torn down.
std::thread* native_cleanup_thread = nullptr;
std::atomic<bool> native_cleanup_thread_started{false};

void native_cleanup_dispatch(jlong ptr, jint kind) {
    if (!ptr) {
        return;
    }
    switch (kind) {
        case 0: {  // NativeCleanup.KIND_INVOCATION_HANDLER
            delete reinterpret_cast<Dispatcher*>(ptr);
            break;
        }
        case 1: {  // NativeCleanup.KIND_CLOSURE
            ResolvedCallReferenceNode* call = reinterpret_cast<ResolvedCallReferenceNode*>(ptr);
            ExceptionSink xsink;
            call->deref(&xsink);
            // any exception is unrecoverable at this point; drop it
            xsink.clear();
            break;
        }
        case 2: {  // NativeCleanup.KIND_EXCEPTION_WRAPPER
            ExceptionSink* xsink = reinterpret_cast<ExceptionSink*>(ptr);
            xsink->clear();
            delete xsink;
            break;
        }
        case 3: {  // NativeCleanup.KIND_OBJECT_BASE_WEAK
            reinterpret_cast<QoreObject*>(ptr)->tDeref();
            break;
        }
        default:
            // unknown kind — silently ignore
            break;
    }
}

void native_cleanup_thread_main(JavaVM* vm) {
    JNIEnv* env;
    JavaVMAttachArgs args = {JNI_VERSION_21, const_cast<char*>("QoreNativeCleanup"), nullptr};
    if (vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), &args) != JNI_OK) {
        return;
    }

    while (true) {
        // Block on queue.remove(); returns when a Ref has been enqueued (either by
        // GC of its referent, or by NativeCleanup.shutdown() for the sentinel).
        jobject ref = env->CallObjectMethod(static_cast<jobject>(nativeCleanupQueue),
            methodReferenceQueueRemove);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            break;
        }
        if (!ref) {
            // remove() with no timeout shouldn't return null; defensive break
            break;
        }

        jlong ptr = env->GetLongField(ref, fieldNativeCleanupRefPtr);
        jint kind = env->GetIntField(ref, fieldNativeCleanupRefKind);

        if (kind == -1) {  // NativeCleanup.KIND_SENTINEL
            // Notify Java side to release the sentinel ref, then exit.
            env->CallStaticVoidMethod(static_cast<jclass>(nativeCleanupClass),
                methodNativeCleanupMarkProcessed, ref);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(ref);
            break;
        }

        // Dispatch native cleanup directly in C++ — no JNI adapter on the call path.
        native_cleanup_dispatch(ptr, kind);

        env->CallStaticVoidMethod(static_cast<jclass>(nativeCleanupClass),
            methodNativeCleanupMarkProcessed, ref);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(ref);
    }

    vm->DetachCurrentThread();
}

}  // anonymous namespace

void Globals::startNativeCleanupThread() {
    if (native_cleanup_thread_started.exchange(true)) {
        return;
    }

    Env env;
    nativeCleanupClass = env.findClass("org/qore/jni/NativeCleanup").makeGlobal();
    nativeCleanupRefClass = env.findClass("org/qore/jni/NativeCleanup$Ref").makeGlobal();

    fieldNativeCleanupRefPtr = env.getField(nativeCleanupRefClass, "ptr", "J");
    fieldNativeCleanupRefKind = env.getField(nativeCleanupRefClass, "kind", "I");

    jfieldID fieldQueue = env.getStaticField(nativeCleanupClass, "queue", "Ljava/lang/ref/ReferenceQueue;");
    nativeCleanupQueue = env.getStaticObjectField(nativeCleanupClass, fieldQueue).makeGlobal();

    LocalReference<jclass> refQueueCls = env.findClass("java/lang/ref/ReferenceQueue");
    methodReferenceQueueRemove = env.getMethod(refQueueCls, "remove", "()Ljava/lang/ref/Reference;");

    methodNativeCleanupMarkProcessed = env.getStaticMethod(nativeCleanupClass, "markProcessed",
        "(Lorg/qore/jni/NativeCleanup$Ref;)V");
    methodNativeCleanupShutdown = env.getStaticMethod(nativeCleanupClass, "shutdown", "()V");

    JavaVM* vm = Jvm::getVm();
    native_cleanup_thread = new std::thread([vm]() { native_cleanup_thread_main(vm); });
}

void Globals::stopNativeCleanupThread() {
    if (!native_cleanup_thread_started.load()) {
        return;
    }
    // Wake the thread by enqueueing the sentinel; loop will see kind=-1 and exit.
    Env env;
    env.callStaticVoidMethod(nativeCleanupClass, methodNativeCleanupShutdown, nullptr);

    if (native_cleanup_thread && native_cleanup_thread->joinable()) {
        native_cleanup_thread->join();
    }
    delete native_cleanup_thread;
    native_cleanup_thread = nullptr;

    nativeCleanupQueue = nullptr;
    nativeCleanupClass = nullptr;
    nativeCleanupRefClass = nullptr;
    native_cleanup_thread_started.store(false);
}

void Globals::cleanup() {
    // delete classes
    classThrowable = nullptr;
    classStackTraceElement = nullptr;
    classPrimitiveVoid = nullptr;
    classPrimitiveBoolean = nullptr;
    classPrimitiveByte = nullptr;
    classPrimitiveChar = nullptr;
    classPrimitiveShort = nullptr;
    classPrimitiveInt = nullptr;
    classPrimitiveLong = nullptr;
    classPrimitiveFloat = nullptr;
    classPrimitiveDouble = nullptr;
    arrayClassByte = nullptr;
    arrayClassObject = nullptr;
    classSystem = nullptr;
    classObject = nullptr;
    classClass = nullptr;
    classString = nullptr;
    classField = nullptr;
    classMethod = nullptr;
    classConstructor = nullptr;
    classQoreInvocationHandler = nullptr;
    classQoreExceptionWrapper = nullptr;
    classQoreException = nullptr;
    classQoreObjectBase = nullptr;
    classQoreJavaClassBase = nullptr;
    classQoreJavaObjectPtr = nullptr;
    classQoreObject = nullptr;
    classQoreClosure = nullptr;
    classQoreObjectWrapper = nullptr;
    classQoreClosureMarker = nullptr;
    classQoreClosureMarkerImpl = nullptr;
    classQoreJavaApi = nullptr;
    classProxy = nullptr;
    classClassLoader = nullptr;
    classQoreURLClassLoader = nullptr;
    classJavaClassBuilder = nullptr;
    classKotlinMetadataHelper = nullptr;
    classKotlinScriptEngine = nullptr;
    classGraphicsEnvironment = nullptr;
    classThread = nullptr;
    classHashMap = nullptr;
    classHash = nullptr;
    classMap = nullptr;
    classList = nullptr;
    classArrayList = nullptr;
    classSet = nullptr;
    classEntry = nullptr;
    classIterator = nullptr;
    classZonedDateTime = nullptr;
    classLocalDateTime = nullptr;
    classInstant = nullptr;
    classQoreRelativeTime = nullptr;
    classBigDecimal = nullptr;
    classArrays = nullptr;
    classBoolean = nullptr;
    classInteger = nullptr;
    classLong = nullptr;
    classShort = nullptr;
    classByte = nullptr;
    classDouble = nullptr;
    classFloat = nullptr;
    classCharacter = nullptr;
    classCharSequence = nullptr;
    classBooleanWrapper = nullptr;
    classProperties = nullptr;
    classDriverManager = nullptr;
    classConnection = nullptr;
    classDatabaseMetaData = nullptr;
    classPreparedStatement = nullptr;
    classTimestamp = nullptr;
    classDate = nullptr;
    classTime = nullptr;
    classResultSet = nullptr;
    classResultSetMetaData = nullptr;
    classArray = nullptr;
    classSQLException = nullptr;
    classServiceLoader = nullptr;
    classDriver = nullptr;
    javaQoreClassField = nullptr;
}

GlobalReference<jclass> Globals::getQoreJavaClassBase(Env& env, jobject classLoader) {
    return Globals::findDefineClass(env, "org.qore.jni.QoreJavaClassBase", classLoader,
        java_org_qore_jni_QoreJavaClassBase_class, java_org_qore_jni_QoreJavaClassBase_class_len).makeGlobal();
}

Type Globals::getType(jclass cls) {
    Env env;
    if (env.isSameObject(cls, classPrimitiveInt)) {
        return Type::Int;
    }
    if (env.isSameObject(cls, classPrimitiveVoid)) {
        return Type::Void;
    }
    if (env.isSameObject(cls, classPrimitiveBoolean)) {
        return Type::Boolean;
    }
    if (env.isSameObject(cls, classPrimitiveByte)) {
        return Type::Byte;
    }
    if (env.isSameObject(cls, classPrimitiveChar)) {
        return Type::Char;
    }
    if (env.isSameObject(cls, classPrimitiveShort)) {
        return Type::Short;
    }
    if (env.isSameObject(cls, classPrimitiveLong)) {
        return Type::Long;
    }
    if (env.isSameObject(cls, classPrimitiveFloat)) {
        return Type::Float;
    }
    if (env.isSameObject(cls, classPrimitiveDouble)) {
        return Type::Double;
    }
    return Type::Reference;
}

jlong Globals::getContextProgram(jobject new_syscl, bool& created) {
    created = false;

    if (!qph) {
        createJavaContextProgram();

        printd(5, "Globals::getContextProgram() new_sycl: %p syscl: %p pgm: %p\n", new_syscl, (jobject)syscl, **qph);
        if (new_syscl && !syscl) {
            syscl = GlobalReference<jobject>::fromLocal(new_syscl);
            created = true;
        }
    }

    return reinterpret_cast<jlong>(**qph);
}

QoreProgram* Globals::createJavaContextProgram() {
    if (!qph) {
        try {
            // create global QoreProgram object
            qph.reset(new QoreProgramHelper(PO_NEW_STYLE, global_xsink));

            QoreNamespace* jnins = qjcm.getJniNs().copy();
            RootQoreNamespace* rns = (*qph)->getRootNS();
            rns->addNamespace(jnins);
            (*qph)->setExternalData("jni", new JniExternalProgramData(jnins, **qph));
            (*qph)->setScriptPath("Qore jni module global Program context");
            printd(5, "Globals::createJavaContextProgram() created %p\n", **qph);
        } catch (jni::JavaException& e) {
#ifdef DEBUG
            SimpleRefHolder<QoreStringNode> err(e.toString());
            printd(5, "Globals::createJavaContextProgram(): %s\n", err->c_str());
#endif
            qph.reset();
            throw;
        } catch (...) {
            qph.reset();
            throw;
        }
    }
    printd(5, "Globals::createJavaContextProgram() pgm: %p jpc: %p\n", **qph, (**qph)->getExternalData("jni"));
    assert((**qph)->getExternalData("jni"));
    return **qph;
}

QoreProgram* Globals::getJavaContextProgram() {
    QoreProgram* rv = qph ? **qph : nullptr;
    if (rv) {
        return rv;
    }

    static QoreThreadLock jcl;
    AutoLocker al(jcl);
    return createJavaContextProgram();
}

QoreJniStackLocationHelper::QoreJniStackLocationHelper() {
}

const std::string& QoreJniStackLocationHelper::getCallName() const {
    if (tid != q_gettid()) {
        return jni_no_call_name;
    }
    checkInit();
    assert((unsigned)current < size());
    //printd(5, "QoreJniStackLocationHelper::getCallName() this: %p %d/%d '%s'\n", this, (int)current, (int)size,
    //    stack_call[current].c_str());
    return stack_call[current];
}

qore_call_t QoreJniStackLocationHelper::getCallType() const {
    if (tid != q_gettid()) {
        return CT_BUILTIN;
    }
    checkInit();
    assert((unsigned)current < size());
    return stack_native[current] ? CT_BUILTIN : CT_USER;
}

const QoreProgramLocation& QoreJniStackLocationHelper::getLocation() const {
    if (tid != q_gettid()) {
        return jni_loc_builtin.get();
    }
    checkInit();
    assert((unsigned)current < size());
    //printd(5, "QoreJniStackLocationHelper::getLocation() %s:%d (%s)\n", stack_loc[current].getFile(), stack_loc[current].getStartLine());
    return stack_loc[current].get();
}

const QoreStackLocation* QoreJniStackLocationHelper::getNext() const {
    if (tid != q_gettid()) {
        return stack_next;
    }
    checkInit();
    assert((unsigned)current < size());
    // issue #3169: reset the pointer after iterating all the information in the stack
    // the exception stack can be iterated multiple times
    ++current;
    if ((unsigned)current < size()) {
        return this;
    }
    current = 0;
    return stack_next;
}

void QoreJniStackLocationHelper::checkInit() const {
    assert(tid == q_gettid());
    if (init) {
        return;
    }
    init = true;

    Env env;

    try {
        LocalReference<jobjectArray> jstack = env.callStaticObjectMethod(Globals::classQoreJavaApi,
            Globals::methodQoreJavaApiGetStackTrace, nullptr).as<jobjectArray>();

        if (jstack) {
            jsize len = env.getArrayLength(jstack);
            stack_loc.reserve(len);
            stack_native.reserve(len);
            stack_call.reserve(len);
            for (jsize i = 0; i < len; ++i) {
                LocalReference<jobject> jste = env.getObjectArrayElement(jstack, i);

                LocalReference<jstring> jcls = env.callObjectMethod(jste, Globals::methodStackTraceElementGetClassName, nullptr).as<jstring>();
                jni::Env::GetStringUtfChars cname(env, jcls);
                LocalReference<jstring> jmethod = env.callObjectMethod(jste, Globals::methodStackTraceElementGetMethodName, nullptr).as<jstring>();
                jni::Env::GetStringUtfChars mname(env, jmethod);
                LocalReference<jstring> jfilename = env.callObjectMethod(jste, Globals::methodStackTraceElementGetFileName, nullptr).as<jstring>();
                jni::Env::GetStringUtfChars file(env, jfilename);
                jint line = env.callIntMethod(jste, Globals::methodStackTraceElementGetLineNumber, nullptr);
                jboolean native = env.callBooleanMethod(jste, Globals::methodStackTraceElementIsNativeMethod, nullptr);

                QoreStringMaker code("%s.%s", cname.c_str(), mname.c_str());

                QoreExternalProgramLocationWrapper loc(file.c_str(), line, line, nullptr, 0, "Java");
                //printd(5, "QoreJniStackLocationHelper::checkInit() %d/%d %s:%d %s::%s()\n", (int)i, (int)len,
                //    file.c_str(), line, cname.c_str(), mname.c_str());
                stack_loc.push_back(loc);
                stack_call.push_back(code.c_str());
                stack_native.push_back(native);
            }
        }
    } catch (jni::Exception& e) {
        e.ignore();
    }

    if (!size()) {
        stack_call.push_back(jni_no_call_name);
        stack_native.push_back(true);
        stack_loc.push_back(jni_loc_builtin);
    }
}

} // namespace jni
