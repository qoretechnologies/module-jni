/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJniClassMap.cpp

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

#include <qore/Qore.h>

#include <string.h>

#include <functional>
#include <memory>
#include <optional>
#include <tuple>
#include <algorithm>
#include <set>
#include <atomic>
#include <stdexcept>
#include <mutex>
#include <unordered_set>
#include <unordered_map>

#include "defs.h"
#include "Jvm.h"
#include "QoreJniClassMap.h"
#include "GeneratedBinding.h"
#include "ql_jni_debug.h"
#include "Class.h"
#include "Method.h"
#include "Functions.h"
#include "JavaToQore.h"
#include "Utf8String.h"
#include "Array.h"

#include "JavaClassQoreJavaDynamicApi.inc"
#include "JavaClassQoreJavaDynamicApi_AccessibleMethods.inc"

namespace jni {
static std::string JNI_CK_JAVA_BIN_NAME = "jni_bin_name";

static bool is_dynamic_qore_bin_name(const char* name) {
    return !strncmp(name, "qore.", 5)
        || !strncmp(name, "qoremod.", 8)
        || !strncmp(name, "python.", 7)
        || !strncmp(name, "pythonmod.", 10)
        || !strncmp(name, "kotlin.", 7)
        || !strncmp(name, "kotlinmod.", 10);
}

// the QoreClass for java::lang::Object
JniQoreClass* QC_OBJECT;
// the Qore class ID for java::lang::Object
qore_classid_t CID_OBJECT;
// the QoreClass for java::lang::Class
JniQoreClass* QC_CLASS;
// the Qore class ID for java::lang::Class
qore_classid_t CID_CLASS;
// the QoreClass for java::lang::reflect::Method
JniQoreClass* QC_METHOD;
// the Qore class ID for java::reflect::Method
qore_classid_t CID_METHOD;
// the QoreClass for java::lang::ClassLoader
JniQoreClass* QC_CLASSLOADER;
// the Qore class ID for java::lang::ClassLoader
qore_classid_t CID_CLASSLOADER;
// the QoreClass for java::lang::Throwable
JniQoreClass* QC_THROWABLE;
// the Qore class ID for java::lang::Throwable
qore_classid_t CID_THROWABLE;
// the QoreClass for java::lang::reflect::InvocationHandler
JniQoreClass* QC_INVOCATIONHANDLER;
// the Qore class ID for java::lang::reflect::InvocationHandler
qore_classid_t CID_INVOCATIONHANDLER;
// the QoreClass for java::time::ZonedDateTime
JniQoreClass* QC_ZONEDDATETIME;
// the Qore class ID for java::time::ZonedDateTime
qore_classid_t CID_ZONEDDATETIME;

bool QoreJniClassMap::init_done = false;
#ifdef JNI_INIT_BACKGROUND
std::mutex QoreJniClassMap::init_mutex;
std::condition_variable QoreJniClassMap::init_cond;
#endif

QoreJniClassMap qjcm;
static void exec_java_constructor(const QoreMethod& meth, BaseMethod* m, QoreObject* self, const QoreListNode* args,
    q_rt_flags_t rtflags, ExceptionSink* xsink);
static QoreValue exec_java_static_method(const QoreMethod& meth, BaseMethod* m, const QoreListNode* args,
    q_rt_flags_t rtflags, ExceptionSink* xsink);
static QoreValue exec_java_method(const QoreMethod& meth, BaseMethod* m, QoreObject* self, QoreJniPrivateData* jd,
    const QoreListNode* args, q_rt_flags_t rtflags, ExceptionSink* xsink);

QoreRecursiveThreadLock QoreJniClassMap::m;
QoreCondition QoreJniClassMap::class_create_cond;

// see the class docs in QoreJniClassMap.h for the lock hierarchy these enforce; the acquisition
// order is encoded in the member declaration order, so there is nothing to do here
JniClassMapLocker::JniClassMapLocker() : al_map(QoreJniClassMap::m) {
}

//! The declared %Qore types of the Java classes whose values are converted to %Qore values other than objects
/** <b>The nullability rule</b>: every Java reference type can hold \c null, which is converted to no value, so the
    type of a value of the class (\c value_type: a return value, a field or a constant) always accepts no value.
    %Qore checks the value read from a field against its declared type at run time, and uses the types of return
    values and constants at parse time, so a type that did not accept no value failed for a \c null field ("expects
    type 'string', but got no value") and narrowed wrongly at parse time.  Only Java primitives (see jpmap) have types
    that do not accept no value.

    The rule applies to parameters as well: a parameter of a reference type takes no value, which is passed as
    \c null.  A parameter of a class listed here also has a variant taking the Java object (\c altType, ex:
    <tt>*object<String></tt>), and a parameter of a class that the classes listed here implement or extend also takes
    the %Qore values converted to them (see jni_get_converted_param_type()).  See design/java-qore-type-mapping.md.

    Java lists and maps are not mapped here; see jni_get_collection_type()
*/
QoreJniClassMap::jtmap_t QoreJniClassMap::jtmap = {
    {"java.lang.Object", autoTypeInfo},
    {"java.lang.String", stringOrNothingTypeInfo},
    {"java.lang.Float", floatOrNothingTypeInfo},
    {"java.lang.Double", floatOrNothingTypeInfo},
    {"java.lang.Boolean", boolOrNothingTypeInfo},
    {"java.lang.Byte", bigIntOrNothingTypeInfo},
    {"java.lang.Short", bigIntOrNothingTypeInfo},
    {"java.lang.Integer", bigIntOrNothingTypeInfo},
    {"java.lang.Long", bigIntOrNothingTypeInfo},
    // a Character is converted to an integer; a Character parameter takes a Character, not an integer, as in Java
    {"java.lang.Character", bigIntOrNothingTypeInfo},
    {"java.lang.Void", nothingTypeInfo},
    // the date and time classes whose values are converted to dates (see JavaToQore::convertToQore()); a date
    // passed for one of them is converted to it (see QoreToJava::toObject()), and the Java object is accepted as well
    {"java.time.ZonedDateTime", dateOrNothingTypeInfo},
    {"java.time.OffsetDateTime", dateOrNothingTypeInfo},
    {"java.time.OffsetTime", dateOrNothingTypeInfo},
    {"java.time.LocalDateTime", dateOrNothingTypeInfo},
    {"java.time.LocalDate", dateOrNothingTypeInfo},
    {"java.time.LocalTime", dateOrNothingTypeInfo},
    {"java.time.Instant", dateOrNothingTypeInfo},
    {"java.sql.Timestamp", dateOrNothingTypeInfo},
    {"java.sql.Date", dateOrNothingTypeInfo},
    {"java.sql.Time", dateOrNothingTypeInfo},
    {"org.qore.jni.QoreRelativeTime", dateOrNothingTypeInfo},
    {"java.math.BigDecimal", numberOrNothingTypeInfo},
    {"org.qore.jni.QoreObject", objectOrNothingTypeInfo},
    {"org.qore.jni.QoreClosureMarker", codeOrNothingTypeInfo},
    {"org.qore.jni.QoreClosure", codeOrNothingTypeInfo},
};

QoreJniClassMap::qt2jmap_t QoreJniClassMap::qt2jmap;

QoreJniClassMap::jpmap_t QoreJniClassMap::jpmap = {
    {"byte", {bigIntTypeInfo, "B"}},
    {"char", {bigIntTypeInfo, "C"}},
    {"int", {bigIntTypeInfo, "I"}},
    {"long", {bigIntTypeInfo, "J"}},
    {"short", {bigIntTypeInfo, "S"}},
    {"double", {floatTypeInfo, "D"}},
    {"float", {floatTypeInfo, "F"}},
    {"void", {nothingTypeInfo, "V"}},
    {"boolean", {boolTypeInfo, "Z"}},
};

static std::string get_class_hash(const QoreClass& qc) {
    std::string rv = qc.getNamespacePath();
    SimpleRefHolder<BinaryNode> b(qc.getBinaryHash());
    rv += std::string(reinterpret_cast<const char*>(b->getPtr()), b->size());

    /*
    QoreString hex;
    hex.concatHex(*b);
    printd(5, "get_class_hash() '%s': <%s>\n", qc.getName(), hex.c_str());
    */

    /*
    std::string name = qc.getNamespacePath();
    rv.insert(0, name);
    */

    return rv;
}

QoreProgram* jni_get_program_context() {
    QoreProgram* pgm = nullptr;
    jni_get_context(pgm);
    return pgm;
}

JniExternalProgramData* jni_get_context() {
    QoreProgram* pgm = nullptr;
    return jni_get_context(pgm);
}

JniExternalProgramData* jni_get_context(QoreProgram*& pgm) {
    JniExternalProgramData* jpc;

    // first try to get any provided context
    if (pgm) {
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        if (jpc) {
            return jpc;
        }
    }

    // next try to get the actual Program context of the current object
    QoreProgram* pgm0 = getProgram();
    if (pgm0 && pgm0 != pgm) {
        jpc = static_cast<JniExternalProgramData*>(pgm0->getExternalData("jni"));
        if (jpc) {
            pgm = pgm0;
            return jpc;
        }
    }

    // then try the Program context of the code
    QoreProgram* pgm1 = qore_get_call_program_context();
    if (pgm1 && pgm1 != pgm0 && pgm1 != pgm) {
        jpc = static_cast<JniExternalProgramData*>(pgm1->getExternalData("jni"));
        if (jpc) {
            pgm = pgm1;
            return jpc;
        }
    }
    pgm = nullptr;
    return nullptr;
}

JniExternalProgramData* jni_get_context_unconditional(JniContextProgramHolder& holder) {
    QoreProgram* pgm = nullptr;
    return jni_get_context_unconditional(pgm, holder);
}

JniExternalProgramData* jni_get_context_or_global(QoreProgram*& pgm, JniContextProgramHolder& holder) {
    JniExternalProgramData* jpc = jni_get_context(pgm);
    if (!jpc) {
        // held, so that the module's shutdown does not delete the Program while the caller uses it (qore#6829)
        pgm = Globals::getJavaContextProgram(holder);
        if (!pgm) {
            // a Java thread can call into Qore during the module's shutdown (ex: an ActiveMQ/JMS listener thread),
            // after the global context Program has been deleted by Globals::clearGlobalContext(), which is not
            // created again then
            return nullptr;
        }
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        assert(jpc);
#ifdef DEBUG
        // a test can hold the thread here, after it has obtained the Program and its jni data and before it uses them
        // (qore#6829)
        if (dbg_jni_context_program_hook_set.load(std::memory_order_acquire)) {
            dbg_jni_run_context_program_hook();
        }
#endif
    }
    return jpc;
}

JniExternalProgramData* jni_get_context_unconditional(QoreProgram*& pgm, JniContextProgramHolder& holder) {
    JniExternalProgramData* jpc = jni_get_context_or_global(pgm, holder);
    if (!jpc) {
        throw BasicException("the jni module is being shut down; no Program context is available");
    }
    return jpc;
}

// returns the jni data of the given Program, creating it if necessary, or, with no Program, the jni data of the
// global Java context Program
static JniExternalProgramData* jni_get_create_program_data(QoreProgram*& pgm, JniContextProgramHolder& holder) {
    if (pgm) {
        JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
        assert(jpc);
        return jpc;
    }
    return jni_get_context_unconditional(pgm, holder);
}

JniExternalProgramData* jni_get_create_context(QoreProgram*& pgm, JniContextProgramHolder& holder) {
    pgm = nullptr;
    JniExternalProgramData* jpc = jni_get_context(pgm);
    if (jpc) {
        return jpc;
    }
    // no Program in the context has jni data: it is created in the Program of the calling code (qore#6801)
    pgm = qore_get_call_program_context();
    return jni_get_create_program_data(pgm, holder);
}

JniExternalProgramData* jni_get_create_call_context(QoreProgram*& pgm, JniContextProgramHolder& holder) {
    pgm = qore_get_call_program_context();
    return jni_get_create_program_data(pgm, holder);
}

static QoreNamespace* jni_find_create_namespace(QoreNamespace& jns, const char* name, const char*& sn) {
    printd(LogLevel, "jni_find_create_namespace() jns: %p '%s'\n", &jns, name);

    sn = rindex(name, '.');

    QoreNamespace* ns;
    // find parent namespace
    if (!sn) {
        ns = &jns;
        sn = name;
        printd(LogLevel, "jni_find_create_namespace() same namespace\n");
    } else {
        QoreString nsp(name);
        nsp.replaceAll(".", "::");
        ++sn;
        ns = jns.findCreateNamespacePath(nsp.c_str());
        printd(LogLevel, "jni_find_create_namespace() jns target: %p '%s' nsp: '%s' ns: %p '%s' new: '%s'\n", &jns,
            jns.getName(), nsp.c_str(), ns, ns->getPath(true).c_str(), sn);
    }

    return ns;
}

#ifdef JNI_INIT_BACKGROUND
void QoreJniClassMap::staticInitBackground(ExceptionSink* xsink, void* pgm_ptr) {
    QoreProgram* pgm = static_cast<QoreProgram*>(pgm_ptr);
    // set program context for initialization
    QoreProgramContextHelper pgm_ctx(pgm);

    // ensure that the parent thread is signaled on exit
    InitSignaler signaler;
    try {
        qjcm.initIntern(pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}
#endif

void QoreJniClassMap::init(QoreProgram* pgm, bool already_initialized) {
    assert(pgm);
    if (already_initialized) {
        qjcm.initIntern(pgm);
        return;
    }

    // issue #3199: perform initialization in the background
    ExceptionSink xsink;
#ifdef JNI_INIT_BACKGROUND
    // grab init mutex
    std::unique_lock<std::mutex> init_lock(init_mutex);
    q_start_thread(&xsink, &staticInitBackground, pgm);
    // wait for initialization to complete
    init_cond.wait(init_lock);
#else
    // there should be no reason to initialize in a background thread
    try {
        qjcm.initIntern(pgm);
    } catch (jni::Exception& e) {
        e.convert(&xsink);
    }
#endif

    // if the background thread threw an exception, then rethrow it here
    if (xsink) {
        throw XsinkException(xsink);
    }
}

void QoreJniClassMap::initIntern(QoreProgram* pgm) {
    // create java.lang namespace with automatic class loader handler
    QoreNamespace* javans = new QoreNamespace("Jni::java");
    QoreNamespace* langns = new QoreNamespace("Jni::java::lang");
    langns->setClassHandler(jni_class_handler);
    javans->addInitialNamespace(langns);

    // add "java" to "Jni" namespace
    default_jns->addInitialNamespace(javans);

    Env env;

    // the variants of varargs methods with typed elements take a JavaArray for the array (see
    // jni_add_loose_varargs_variants()), so its class must exist before any Java class is created
    if (!QC_JAVAARRAY) {
        preinitJavaArrayClass();
    }

    // add "Object" class
    // find/create parent namespace in default / master Jni namespace first
    const char* sn;
    QoreNamespace* ns = jni_find_create_namespace(*default_jns, "java.lang.Object", sn);

    QC_OBJECT = new JniQoreClass(pgm, "Object", "Jni::java::lang::Object", "java.lang.Object");
    QC_OBJECT->setJavaInternalName("java/lang/Object");
    CID_OBJECT = QC_OBJECT->getID();
    createClassInNamespace(ns, *default_jns, "java/lang/Object",
        Functions::loadClass(env, "java/lang/Object"), QC_OBJECT, *this, pgm);

    QC_CLASS = findCreateQoreClass(env, "java.lang.Class", pgm);
    CID_CLASS = QC_CLASS->getID();
    QC_METHOD = findCreateQoreClass(env, "java.lang.reflect.Method", pgm);
    CID_METHOD = QC_METHOD->getID();
    QC_CLASSLOADER = findCreateQoreClass(env, "java.lang.ClassLoader", pgm);
    CID_CLASSLOADER = QC_CLASSLOADER->getID();
    QC_THROWABLE = findCreateQoreClass(env, "java.lang.Throwable", pgm);
    CID_THROWABLE = QC_THROWABLE->getID();
    QC_INVOCATIONHANDLER = findCreateQoreClass(env, "java.lang.reflect.InvocationHandler", pgm);
    CID_INVOCATIONHANDLER = QC_INVOCATIONHANDLER->getID();

    QC_ZONEDDATETIME = findCreateQoreClass(env, "java.time.ZonedDateTime", pgm);
    CID_ZONEDDATETIME = QC_ZONEDDATETIME->getID();

    // populate classes after initial hierarchy done
    init_done = true;

    // now populate all classes created up until now
    {
        // copy all classes to a vector
        std::vector<JniQoreClass*> cvec;
        cvec.reserve(jcmap.size());
        for (auto& i : jcmap) {
            cvec.push_back(i.second);
        }
        // populate all classes in the vector
        for (auto& i : cvec) {
            populateQoreClass(*i, static_cast<Class*>(i->getManagedUserData()), pgm);
        }
    }

    // rescan all classes
    for (auto& i : jcmap) {
        i.second->rescanParents();
    }

    // add low-level API classes
    {
        QoreNamespace* org = new QoreNamespace("Jni::org");
        QoreNamespace* qore = new QoreNamespace("Jni::org::qore");
        QoreNamespace* jni = new QoreNamespace("Jni::org::qore::jni");

        jni->addSystemClass(initQoreInvocationHandlerClass(*jni));
        jni->addSystemClass(initJavaArrayClass(*jni));

        // add low-level API functions
        init_jni_functions(*jni);
#ifdef DEBUG
        // debug-build-only diagnostics for regression tests
        init_jni_debug_functions(*jni);
#endif

        org->addInitialNamespace(qore);
        qore->addInitialNamespace(jni);

        default_jns->addInitialNamespace(org);
    }

    // initialize Qore base type -> java class map
    qt2jmap[NT_INT] = GlobalReference<jclass>((jclass)Globals::classPrimitiveLong);
    qt2jmap[NT_FLOAT] = GlobalReference<jclass>((jclass)Globals::classPrimitiveDouble);
    qt2jmap[NT_BOOLEAN] = GlobalReference<jclass>((jclass)Globals::classPrimitiveBoolean);
    qt2jmap[NT_STRING] = env.findClass("java/lang/String").makeGlobal();

    // NOTE: at runtime Qore values will be converted to either java.time.ZonedDateTime or org.qore.jni.QoreRelativeTime
    qt2jmap[NT_DATE] = env.findClass("java/lang/Object").makeGlobal();

    qt2jmap[NT_NUMBER] = env.findClass("java/math/BigDecimal").makeGlobal();
    qt2jmap[NT_BINARY] = GlobalReference<jclass>((jclass)Globals::arrayClassByte);
    // Use Hash class for method signatures (backward compatibility)
    // Note: JavaToQore.cpp already accepts any Map implementation as input
    qt2jmap[NT_HASH] = GlobalReference<jclass>((jclass)Globals::classHash);
    qt2jmap[NT_LIST] = env.findClass("[Ljava/lang/Object;").makeGlobal();
    qt2jmap[NT_NOTHING] = GlobalReference<jclass>((jclass)Globals::classPrimitiveVoid);

    // issu #4593: https://github.com/qorelanguage/qore/issues/4593
    qt2jmap[NT_RUNTIME_CLOSURE] = GlobalReference<jclass>((jclass)Globals::classQoreClosureMarkerImpl);

    // take the template that new Programs get a copy of: the bootstrap classes only.  Classes that Programs
    // import from now on are added to default_jns, which owns them, but not to the template, so that an import
    // is not visible in Programs created after it
    {
        JniClassMapLocker al;
        assert(!program_jns);
        program_jns = default_jns->copy();
    }
}

void QoreJniClassMap::destroy(ExceptionSink& xsink) {
    if (program_jns) {
        program_jns->clear(&xsink);
        delete program_jns;
        program_jns = nullptr;
    }
    default_jns->clear(&xsink);
    delete default_jns;
    default_jns = nullptr;
}

// takes an internal name (ex: java/lang/Class)
jclass QoreJniClassMap::findLoadClass(const QoreString& name, QoreProgram* pgm) {
   Utf8String nameUtf8(name);
   // the name is a NUL-terminated string from here on, so a name with a NUL character is refused (qore#6738)
   return findLoadClass(nameUtf8.name("class name", "class names"), pgm);
}

// takes an internal name (ex: java/lang/Class)
jclass QoreJniClassMap::findLoadClass(const char* jpath, QoreProgram* pgm) {
    // attaches the thread to the JVM: the returned local reference is created on it, also when the class is found
    // in the cache, which can be the thread's first call into Java (ex: load_class() in a new thread)
    Env env;

    // must be initialized: if there is no Java context below (jpc is nullptr), qc is never assigned
    // before being tested, which would otherwise read an indeterminate value
    JniQoreClass* qc = nullptr;

    // fast path: the class is already in the global cache.  This path only reads jcmap under a brief
    // m; it cannot reach Java or module loading (see JniClassMapLocker)
    {
        JniClassMapLocker al;
        jcmap_t::iterator i = jcmap.find(jpath);
        if (i != jcmap.end()) {
            qc = i->second;
            //printd(LogLevel, "findLoadClass() '%s': %p (cached)\n", jpath, qc);
        }
    }

    if (!qc) {
        // slow path: the class may have to be loaded from Java and a Qore class created for it.
        // loadClass() and findCreateQoreClass() call into Java and can drive classloading that calls
        // back into module loading, so they must run with m NOT held (m is a strict leaf that is
        // never held across a module load; see JniClassMapLocker)
        JniExternalProgramData* jpc;
        if (!pgm) {
            jpc = jni_get_context();
        } else {
            jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        }

        {
            // re-check the caches under a brief m: another thread may have created the class after
            // the fast path released m above
            JniClassMapLocker al;
            jcmap_t::iterator i = jcmap.find(jpath);
            if (i != jcmap.end()) {
                qc = i->second;
                //printd(LogLevel, "findLoadClass() '%s': %p (cached 2)\n", jpath, qc);
            } else if (jpc) {
                assert(static_cast<QoreJniClassMapBase*>(jpc) != static_cast<QoreJniClassMapBase*>(this));
                qc = jpc->find(jpath);
            }
        }

        //printd(5, "findLoadClass() '%s': qc: %p pgm: %p jpc: %p\n", jpath, qc, pgm, jpc);
        if (!qc) {
            // create the class with NO global lock held; findCreateQoreClass() coordinates
            // concurrent/recursive creation internally
            bool base;
            SimpleRefHolder<Class> cls(loadClass(env, jpath, base, jpc));

            QoreString cpath(jpath);
            cpath.replaceAll("/", ".");
            //cpath.replaceAll("$", "__");

            // do not create Qore classes for classes imported from Qore
            if (cpath.startsWith("qore.") || cpath.startsWith("qoremod.")
                || cpath.startsWith("python.") || cpath.startsWith("pythonmod.")) {
                return cls->toLocal();
            }
            qc = findCreateQoreClass(env, cpath, jpath, cls.release(), base, pgm);
            assert(qc);
            //printd(5, "findLoadClass() '%s': %p (created) pgm: %p\n", jpath, qc, pgm);
        }
    }

    assert(qc);
    return static_cast<Class*>(qc->getManagedUserData())->toLocal();
}

QoreValue QoreJniClassMap::getValue(LocalReference<jobject>& obj, QoreProgram* pgm, bool compat_types) {
    Env env;

    // see if object is an array
    LocalReference<jclass> jc = env.getObjectClass(obj);

    if (env.callBooleanMethod(jc, Globals::methodClassIsArray, nullptr)) {
        ReferenceHolder<> return_value(nullptr);
        Array::getList(return_value, env, obj.cast<jarray>(), jc, pgm, compat_types);
        return return_value.release();
    }

    if (env.isSameObject(jc, Globals::classInteger)) {
        return env.callIntMethod(obj, Globals::methodIntegerIntValue, nullptr);
    }

    if (env.isSameObject(jc, Globals::classLong))
        return env.callLongMethod(obj, Globals::methodLongLongValue, nullptr);

    if (env.isSameObject(jc, Globals::classShort))
        return env.callShortMethod(obj, Globals::methodShortShortValue, nullptr);

    if (env.isSameObject(jc, Globals::classByte))
        return env.callByteMethod(obj, Globals::methodByteByteValue, nullptr);

    if (env.isSameObject(jc, Globals::classBoolean)) {
        return (bool)env.callBooleanMethod(obj, Globals::methodBooleanBooleanValue, nullptr);
    }

    if (env.isSameObject(jc, Globals::classDouble))
        return (double)env.callDoubleMethod(obj, Globals::methodDoubleDoubleValue, nullptr);

    if (env.isSameObject(jc, Globals::classFloat))
        return (double)env.callFloatMethod(obj, Globals::methodFloatFloatValue, nullptr);

    if (env.isSameObject(jc, Globals::classCharacter))
        return (int64)env.callCharMethod(obj, Globals::methodCharacterCharValue, nullptr);

    // check for microsoft.sql.DateTimeOffset
    {
        LocalReference<jstring> clsName = env.callObjectMethod(jc, Globals::methodClassGetName,
            nullptr).as<jstring>();
        Env::GetStringUtfChars tname(env, clsName);
        // the whole name is compared, as it can have a NUL character (qore#6738)
        if (tname == "microsoft.sql.DateTimeOffset") {
            JniPendingContextHelper::set(env);
            LocalReference<jstring> date_str = env.callObjectMethod(obj,
                Globals::methodObjectToString, nullptr).as<jstring>();
            Env::GetStringUtfChars chars(env, date_str);
            return QoreValue(new DateTimeNode(chars.c_str()));
        }
    }

    assert(pgm);
    QoreClass* qc = qjcm.findCreateQoreClass(env, jc, pgm);
    assert(qc);
    return new QoreObject(qc, pgm, new QoreJniPrivateData(obj));
}

static LocalReference<jstring> get_dot_name(Env& env, const char* name) {
    QoreString nname(name);
    nname.replaceAll("/", ".");
    //printd(LogLevel, "using '%s' -> '%s'\n", name, nname.c_str());
    return env.newString(nname.c_str());
}

namespace {
//! Sets the thread's Java context for the lookup of a class that is not in a cache, and restores the previous one
/** JNI FindClass initializes a class that it loads, which runs its static initializers: Java code that runs in the
    context of the Program that the class is loaded for (qore#6551); the lookup can be made in the middle of another
    operation (ex: a conversion), whose context is restored after it; no context is set while the module bootstraps,
    before a Program has a jni context
*/
class JniClassLookupContextHelper {
public:
    DLLLOCAL JniClassLookupContextHelper(Env& env, JniExternalProgramData* jpc) : env(env) {
        if (!Globals::methodQoreURLClassLoaderSetContext || !Globals::methodQoreURLClassLoaderGetCurrent) {
            return;
        }
        QoreProgram* pgm = nullptr;
        // no context is set during the module's shutdown, when no global context Program exists; the global context
        // Program is held only while its class loader is made the context: the context keeps a reference to the
        // class loader, not to the Program
        JniContextProgramHolder ctx_holder;
        JniExternalProgramData* ctx = jpc ? jpc : jni_get_context_or_global(pgm, ctx_holder);
        if (!ctx || !ctx->getClassLoader()) {
            return;
        }
        LocalReference<jobject> current = env.callStaticObjectMethod(Globals::classQoreURLClassLoader,
            Globals::methodQoreURLClassLoaderGetCurrent, nullptr);
        if (current) {
            saved = current.makeGlobal();
        }
        env.callVoidMethod(ctx->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);
    }

    DLLLOCAL ~JniClassLookupContextHelper() {
        if (!saved) {
            return;
        }
        // the destructor can run while an exception propagates: a pending Java exception is set aside for the call
        // and thrown again afterwards
        JNIEnv* jenv = Jvm::getEnv();
        jthrowable pending = jenv->ExceptionOccurred();
        if (pending) {
            jenv->ExceptionClear();
        }
        jenv->CallVoidMethod(saved, Globals::methodQoreURLClassLoaderSetContext);
        if (jenv->ExceptionCheck()) {
            // a failure to restore the context is not reported in place of the operation's own result
            jenv->ExceptionClear();
        }
        if (pending) {
            jenv->Throw(pending);
            jenv->DeleteLocalRef(pending);
        }
    }

private:
    Env& env;
    GlobalReference<jobject> saved;
};
}  // namespace

Class* QoreJniClassMap::loadClass(Env& env, const char* name, bool& base, JniExternalProgramData* jpc) {
    // this path runs only for a class that is not in a cache
    JniClassLookupContextHelper ctx_helper(env, jpc);
    try {
        base = true;

        // first we try to load with the builtin classloader
        return Functions::loadClass(env, name);
    } catch (jni::JavaException& e) {
        // NOTE: JavaException::toString() ignores the exception
        //SimpleRefHolder<QoreStringNode> desc(e.toString());
        //printd(5, "loadClass() %s failed: %s\n", name, desc->c_str());
        e.ignore();
    }
    base = false;
    if (!jpc) {
        jpc = jni_get_context();
        if (!jpc) {
            printd(5, "failed to load class '%s' with default classloader; no program-specific classloader present",
                name);
            // the Java class name is UTF-8; the text of the error is in the default encoding
            throw QoreJniException("JNI-IMPORT-ERROR", "failed to load class '%s' with default classloader; no "
                "program-specific classloader present", jni_utf8_to_default(name).c_str());
        }
    }
    return loadProgramClass(env, name, jpc);
}

Class* QoreJniClassMap::loadProgramClass(Env& env, const char* name, JniExternalProgramData* jpc) {
    LocalReference<jstring> jname = get_dot_name(env, name).release();
    jvalue jarg;
    jarg.l = jname;

    LocalReference<jclass> c = env.callObjectMethod(jpc->getClassLoader(),
        Globals::methodQoreURLClassLoaderLoadClass, &jarg).as<jclass>();

    return new Class(c.release());
}

//! Throws an exception if a name that the Qore class of a Java class is made with has a NUL character (qore#6738)
/** The JVM allows a NUL character in the name of a class, method or field, and a Qore name would be cut at it: the
    names of the parent class and the interfaces, which are looked up by name, and of the methods and fields that
    become Qore methods, members, static variables and constants (not those of synthetic members, which are skipped,
    apart from bridge methods, nor of Kotlin default parameter method variants) are checked, before the Qore class is built.  doMethodsIntern(), doFields() and
    addSuperClass() check them again where they are used.

    @throw XsinkException \c JNI-ERROR: a name has a NUL character
*/
static void jni_check_java_names(Class* jc) {
    Env env;
    auto check_class_name = [&env](jobject cls) {
        LocalReference<jstring> name = env.callObjectMethod(cls, Globals::methodClassGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars str(env, name);
        str.name("Java class name", "Java class names");
    };
    LocalReference<jclass> parent = env.callObjectMethod(jc->getJavaObject(), Globals::methodClassGetSuperClass,
        nullptr).as<jclass>();
    if (parent) {
        check_class_name(parent);
    }
    LocalReference<jobjectArray> interfaces = jc->getInterfaces();
    for (jsize i = 0, e = interfaces ? env.getArrayLength(interfaces) : 0; i < e; ++i) {
        check_class_name(env.getObjectArrayElement(interfaces, i));
    }
    LocalReference<jobjectArray> methods = jc->getDeclaredMethods();
    for (jsize i = 0, e = methods ? env.getArrayLength(methods) : 0; i < e; ++i) {
        LocalReference<jobject> m = env.getObjectArrayElement(methods, i);
        int mods = env.callIntMethod(m, Globals::methodMethodGetModifiers, nullptr);
        if ((mods & ACC_SYNTHETIC) && !(mods & ACC_BRIDGE)) {
            continue;
        }
        LocalReference<jstring> name = env.callObjectMethod(m, Globals::methodMethodGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars str(env, name);
        // a Kotlin default parameter method variant is skipped, as in doMethodsIntern()
        if (strstr(str.c_str(), "$default")) {
            continue;
        }
        str.name("Java method name", "Java method names");
    }
    LocalReference<jobjectArray> fields = jc->getDeclaredFields();
    for (jsize i = 0, e = fields ? env.getArrayLength(fields) : 0; i < e; ++i) {
        LocalReference<jobject> f = env.getObjectArrayElement(fields, i);
        int mods = env.callIntMethod(f, Globals::methodFieldGetModifiers, nullptr);
        if (mods & ACC_SYNTHETIC) {
            continue;
        }
        LocalReference<jstring> name = env.callObjectMethod(f, Globals::methodFieldGetName, nullptr).as<jstring>();
        Env::GetStringUtfChars str(env, name);
        str.name("Java field name", "Java field names");
    }
}

JniQoreClass* QoreJniClassMap::findCreateQoreClass(Env& env, LocalReference<jclass>& jc, QoreProgram* pgm) {
    LocalReference<jstring> clsName = env.callObjectMethod(jc, Globals::methodClassGetName, nullptr).as<jstring>();
    Env::GetStringUtfChars tname(env, clsName);

    printd(5, "QoreJniClassMap::findCreateQoreClass() looking up: '%s' pgm: %p\n", tname.c_str(), pgm);

    // the Qore class and namespace names are made of the Java name, which the JVM allows to have a NUL character,
    // at which they would be cut (qore#6738)
    QoreString cname(tname.name("Java class name", "Java class names"));
    //cname.replaceAll("$", "__");

    QoreString jpath(tname.c_str());
    jpath.replaceAll(".", "/");

    // see if class is a builtin class or loaded by our custom classloader
    LocalReference<jobject> cl = env.callObjectMethod(jc, Globals::methodClassGetClassLoader, nullptr);
    bool base = (!baseClassLoader && !cl) || (cl && baseClassLoader && env.isSameObject(baseClassLoader, cl));
    printd(5, "QoreJniClassMap::findCreateQoreClass() '%s' base: %d\n", jpath.c_str(), base);
    return findCreateQoreClass(env, cname, jpath.c_str(), new Class(jc), base, pgm);
}

JniQoreClass* QoreJniClassMap::findCreateQoreClassInProgram(QoreString& name, const char* jpath, Class* c, QoreProgram* pgm) {
    SimpleRefHolder<Class> cls(c);

    // Lock order here is: Program parse lock -> m (m is a strict leaf; see JniClassMapLocker).  This
    // function must NOT hold the global class-map lock m across MM.runTimeLoadModule() below or
    // across createClassInNamespace() -> populateQoreClass() (which calls into Java and can drive
    // classloading that calls back into ModuleManager::runTimeLoadModule()): holding a global lock
    // across a module load reintroduces the module-load deadlock.  The per-Program parse lock IS held
    // across the build, which is safe (a per-Program lock cannot form the process-global cycle).
    ExceptionSink xsink;

    // check current Program's namespace
    JniExternalProgramData* jpc;
    if (!pgm) {
        jpc = jni_get_context(pgm);
        if (!jpc) {
            throw BasicException("no Java context to create Qore class");
        }
    } else {
        if (jni_qore_init_done) {
            // ensure that the jni module symbols are loaded into the new Program object; run this
            // before taking any jni lock (it needs no class-map access)
            MM.runTimeLoadModule("jni", pgm, &xsink);
            if (xsink) {
                throw XsinkException(xsink);
            }
        }
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        assert(jpc);
    }

    {
        // brief m for the program-local class-map lookup (never returns a half-built class)
        JniClassMapLocker al;
        if (JniQoreClass* qc = jpc->find(jpath)) {
            return qc;
        }
    }

    assert(pgm);
    // no runtime entry when the Program is current: a module command can add the class to a Program that has code
    // pending (see jni_enter_program_for_class_add()); xsink, declared above, outlives the entry, which uses it when it
    // is left
    std::optional<QoreExternalProgramContextHelper> epch;
    jni_enter_program_for_class_add(epch, pgm, xsink);

    // grab current Program's parse lock before manipulating namespaces
    CurrentProgramRuntimeExternalParseContextHelper pch;
    if (!pch) {
        throw BasicException("could not attach to deleted Qore Program when creating class in Qore program");
    }

    // see if we have an inner class
    int ic_idx = name.rfind('$');
    if (ic_idx != -1) {
        name.replaceChar(ic_idx, '_');
        name.insertch('_', ic_idx + 1, 1);
    }

    // find/create parent namespace in default / master Jni namespace first
    const char* sn;
    QoreNamespace* ns = jni_find_create_namespace(*jpc->getJniNamespace(), name.c_str(), sn);
    JniQoreClass* qc = nullptr;
    // we need to see if a builtin native Java class has already been imported without a lookup entry
    // or there is not already a Qore class in the namespace with the same name
    {
        // get last '.'
        int dot = name.rfind('.');

        // check for name conflict
        while (QoreClass* ec = ns->findLocalClass(sn)) {
            // a non-"Java" language means a hand-written builtin Qore class (e.g. the qpp-defined
            // QoreInvocationHandler / JavaArray system classes) already occupies this name; those
            // are QoreBuiltinClass, not JniQoreClass, and are already usable directly, so importing
            // the underlying Java class is both impossible and unnecessary
            if (strcmp(ec->getLanguage(), "Java")) {
                // the Java class name is UTF-8; the text of the error is in the default encoding
                throw QoreJniException("JNI-IMPORT-ERROR", "cannot import Java class '%s': a built-in Qore class "
                    "with that name already exists in the Jni namespace", jni_utf8_to_default(name.c_str()).c_str());
            }
            qc = static_cast<JniQoreClass*>(ec);
            // make sure the class is not already a representation of the new class
            if (qc->getJavaName() == name.c_str()) {
                return qc;
            }

            // add an underscore
            name.insertch('_', ic_idx + 2, 1);
            sn = name.c_str() + (dot != -1 ? dot + 1 : 0);
        }
    }
    assert(!ns->findLocalClass(sn));

    // assert that we are not creating a Qore class for an imported class; match the
    // Qore namespace on a segment boundary so names such as qore.QoreRagUtils.* remain valid
    assert(name != "qore.Qore" && name.find("qore.Qore.") != 0);
    QoreString path(name);
    path.replaceAll(".", "::");
    path.insert("::Jni::", 0);
    qc = new JniQoreClass(pgm, sn, path.c_str(), name.c_str());
    qc->setJavaInternalName(jpath);
    assert(qc->isSystem());
    // use the return value: createClassInNamespace() may delete qc and return a pre-existing class
    // on a name collision — using the freed qc afterwards would be a use-after-free
    qc = createClassInNamespace(ns, *jpc->getJniNamespace(), jpath, cls.release(), qc, *jpc, pgm);

    return qc;
}

JniQoreClass* QoreJniClassMap::findCreateQoreClass(Env& env, const char* name, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    QoreString jpath(name);
    jpath.replaceAll(".", "/");
    jpath.replaceAll("__", "$");

    // first try to find class in the global cache; must hold m to avoid a data race with concurrent
    // inserts into jcmap (std::map read+write is UB).  m is a strict leaf here: the lookup takes m
    // for a short critical section only.  addClassToProgram() must be called OUTSIDE the m scope
    // because it takes the Program parse lock then m (parseLock -> m); calling it while holding m
    // would invert that order.  The class-creation path below runs after m is released and
    // re-acquires the locks in the correct order via findCreateQoreClass*().
    {
        JniQoreClass* rv;
        bool need_add_to_program = false;
        // the Program's jni data is looked up before m is taken: getExternalData() takes the Program's lock, and m
        // is a leaf (the copy of the data for a child Program takes m, with that lock held in older libraries)
        if (!jpc && pgm) {
            jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        }
        {
            JniClassMapLocker al;
            rv = findInternal(jpath.c_str());
            if (rv && pgm) {
                // class is in global cache but NOT in our Program - add it below (outside m)
                need_add_to_program = jpc && !jpc->find(jpath.c_str());
            }
        }
        if (rv) {
            if (need_add_to_program) {
                addClassToProgram(rv, jpath.c_str(), pgm);
            }
            return rv;
        }
    }
    //printd(LogLevel, "QoreJniClassMap::findCreateQoreClass() '%s' not cached\n", name);

    if (!jpc) {
        jpc = pgm
            ? static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"))
            : jni_get_context(pgm);
        if (!jpc) {
            return nullptr;
        }
    }

    // first try to load the class if possible
    bool base;
    SimpleRefHolder<Class> cls(loadClass(env, jpath.c_str(), base, jpc));

    QoreString cname(name);

    // do not create Qore classes for classes imported from Qore
    if (cname.startsWith("qore.") || cname.startsWith("qoremod.")
        || cname.startsWith("python.") || cname.startsWith("pythonmod.")) {
        return nullptr;
    }

    // create the class in the correct namespace
    return findCreateQoreClass(env, cname, jpath.c_str(), cls.release(), base, pgm);
}

JniQoreClass* QoreJniClassMap::findCreateQoreClassInBase(Env& env, QoreString& name, const char* jpath, Class* c,
        QoreProgram* pgm) {
    SimpleRefHolder<Class> cls(c);

    printd(LogLevel, "QoreJniClassMap::findCreateQoreClassInBase() looking up: '%s'\n", jpath);

    // Lock order: m is a strict leaf guarding the global class map and the global default_jns
    // template namespace; it is never held across a Java call or a module load (see JniClassMapLocker).
    // createClassInNamespace() below releases m across the Java-calling build and re-acquires it to
    // publish; addClassToProgram() takes the Program parse lock then m.

    // if we have the QoreClass already in the global cache, ensure it's also in the calling Program's namespace
    {
        JniQoreClass* qc;
        {
            JniClassMapLocker al;
            qc = find(jpath);
        }
        if (qc) {
            addClassToProgram(qc, jpath, pgm);
            return qc;
        }
    }

    // workaround for the headless awt toolkit; we cannot initialize sun.awt.dnd.SunDropTargetEvent, because
    // the DataTransferer for the headless awt toolkit is null, and this will cause an initializer exception
    // in this class when initializing the static member "ToolkitThreadBlockedHandler handler"
    if (name == "sun.awt.dnd.SunDropTargetEvent") {
        Globals::ensureGraphicsEnvironment();
        Env env;
        if (env.callStaticBooleanMethod(Globals::classGraphicsEnvironment, Globals::methodGraphicsEnvironmentIsHeadless,
            nullptr)) {
            printd(5, "retuning Object for '%s' when running in a headless environment\n", name.c_str());
            return QC_OBJECT;
        }
    }

#ifdef __APPLE__
    // initializing java.awt.* classes can lead to a deadlock on macOS
    if (name.startsWith("java.awt")) {
        printd(5, "retuning Object for '%s' when running on Darwin\n", name.c_str());
        return QC_OBJECT;
    }
#endif

    // resolve the target namespace and handle name conflicts under a brief m (default_jns is the
    // global template namespace, guarded by m); addClassToProgram() for an already-registered class
    // is deferred until m is released (it takes the parse lock -> m)
    const char* sn;
    QoreNamespace* ns;
    JniQoreClass* jexisting = nullptr;
    JniQoreClass* qc = nullptr;
    {
        JniClassMapLocker al;

        // see if we have an inner class
        int ic_idx = name.rfind('$');
        if (ic_idx != -1) {
            name.replaceChar(ic_idx, '_');
            name.insertch('_', ic_idx + 1, 1);
        }

        // find/create parent namespace in default / master Jni namespace first
        ns = jni_find_create_namespace(*default_jns, name.c_str(), sn);

        // check if the class already exists in the namespace (e.g., same Java class loaded from
        // multiple JARs on the classpath)
        if (QoreClass* existing = ns->findLocalClass(sn)) {
            // reflection-created classes (JniQoreClass) report language "Java"; any other language
            // means a hand-written builtin Qore class already occupies this name — in particular the
            // qpp-defined Jni::org::qore::jni::QoreInvocationHandler and JavaArray system classes,
            // which are QoreBuiltinClass, not JniQoreClass.  We must not treat those as JniQoreClass
            // (that reads JniQoreClass-only members and crashes); they are already usable directly, so
            // importing the underlying Java class is both impossible and unnecessary
            if (strcmp(existing->getLanguage(), "Java")) {
                // the Java class name is UTF-8; the text of the error is in the default encoding
                throw QoreJniException("JNI-IMPORT-ERROR", "cannot import Java class '%s': a built-in Qore class "
                    "with that name already exists in the Jni namespace", jni_utf8_to_default(name.c_str()).c_str());
            }
            JniQoreClass* je = static_cast<JniQoreClass*>(existing);
            if (je->getJavaName() == name.c_str()) {
                // same Java class already registered; return it (and add to the calling Program below)
                jexisting = je;
            }
        }

        if (!jexisting) {
            if (ic_idx != -1) {
                // get last '.'
                int dot = name.rfind('.');

                // check for name conflict with a different Java class
                while (ns->findLocalClass(sn)) {
                    // add an underscore
                    name.insertch('_', ic_idx + 2, 1);
                    sn = name.c_str() + (dot != -1 ? dot + 1 : 0);
                }
            }

            assert(pgm);
            assert(name.find("qore.Qore") == -1);
            QoreString path(name);
            path.replaceAll(".", "::");
            path.insert("::Jni::", 0);
            qc = new JniQoreClass(pgm, sn, path.c_str(), name.c_str());
            qc->setJavaInternalName(jpath);
            assert(qc->isSystem());
        }
    }

    if (jexisting) {
        addClassToProgram(jexisting, jpath, pgm);
        return jexisting;
    }

    // createClassInNamespace() will "save" qc in the namespace; it may delete the passed-in qc and
    // return a pre-existing class on a name collision (e.g. the same Java class loaded recursively
    // via addSuperClasses()), so we must use its return value — using the freed qc here would be a
    // use-after-free.  It builds the class with m released and re-acquires m to publish.
    qc = createClassInNamespace(ns, *default_jns, jpath, cls.release(), qc, *this, pgm);

    // add to the calling Program's namespace
    addClassToProgram(qc, jpath, pgm);
    return qc;
}

void QoreJniClassMap::addClassToProgram(JniQoreClass* qc, const char* jpath, QoreProgram* pgm) {
    // Lock order: Program parse lock -> m.  This method does not call into Java or load a module, so
    // it may hold the parse lock (per-Program) across its work; the jpc class-map accesses take the
    // strict-leaf m for a short critical section, always inside the parse lock (parseLock -> m).

    // A class that has not yet been published into its declaring namespace must NOT be copied into a
    // Program's namespace here.  QoreClass copies SHARE qore_class_private (QoreClass(const QoreClass&)
    // takes old.priv), and QoreNamespace::addSystemClass() assigns the namespace through
    // setNamespaceConditional(), which claims the class for the FIRST namespace to receive it.  Adding
    // an unpublished class therefore stamps the shared private data with this Program's namespace, and
    // the later publish into the global default_jns tree silently does nothing.  The class stays in the
    // process-global cache while its declaring namespace dies with this Program, so every later Program
    // handed that cached class gets one whose namespace has been purged:
    //
    //   PROGRAM-ERROR: cannot call method 'QoreURLClassLoader::addPath()'; the Program object that owns
    //   class 'QoreURLClassLoader' has already been deleted and therefore cannot be accessed at runtime
    //
    // This is reached through createClassInNamespace()'s same-thread recursion path, which hands the
    // caller the still-unpublished partial class (the frame that owns the in-progress marker publishes
    // it).  That owning frame calls addClassToProgram() again once the class is published, so skipping
    // here loses nothing.
    if (!qc->getNamespace()) {
        printd(LogLevel, "QoreJniClassMap::addClassToProgram() '%s' qc: %p not yet published to its "
            "declaring namespace; deferring to the frame that owns its creation\n", jpath, qc);
        return;
    }

    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (!jpc) {
        return;
    }

    // check if the class is already in the Program's namespace
    {
        JniClassMapLocker al;
        if (jpc->find(jpath)) {
            return;
        }
    }

    assert(pgm);
    // no runtime entry when the Program is current: a module command can add the class to a Program that has code
    // pending (see jni_enter_program_for_class_add()); the sink is declared before the entry, which uses it when it is
    // left
    ExceptionSink xsink;
    std::optional<QoreExternalProgramContextHelper> epch;
    jni_enter_program_for_class_add(epch, pgm, xsink);

    // grab current Program's parse lock before manipulating namespaces
    CurrentProgramRuntimeExternalParseContextHelper pch;
    if (!pch) {
        throw BasicException("could not attach to deleted Qore Program when adding class to program");
    }

    // re-check under parse lock
    {
        JniClassMapLocker al;
        if (jpc->find(jpath)) {
            return;
        }
    }

    const char* sn;
    QoreNamespace* ns = jni_find_create_namespace(*jpc->getJniNamespace(), qc->getJavaName().c_str(), sn);

    // check if the class is already in the namespace — must check both the local namespace
    // and the root namespace path, because the class may have been inherited from a loaded
    // module's namespace (e.g., JakartaJmsDataProvider imports jakarta.jms.* classes, and
    // when the test program loads that module, the classes are merged into the program's
    // namespace tree but not directly into the program-local Jni:: namespace)
    if (QoreClass* existing = ns->findLocalClass(sn)) {
        // class already in this namespace - just add the jpc mapping and return
        {
            JniClassMapLocker al;
            jpc->add(jpath, static_cast<JniQoreClass*>(existing));
        }
        printd(LogLevel, "QoreJniClassMap::addClassToProgram() '%s' already in namespace '%s', "
            "added jpc mapping\n", jpath, ns->getName());
        return;
    }
    // also check via the program's root namespace path, which catches classes inherited
    // from loaded modules
    {
        QoreString full_path("Jni::");
        full_path.concat(qc->getJavaName().c_str());
        full_path.replaceAll(".", "::");
        ExceptionSink xsink;
        if (const QoreClass* existing = pgm->findClass(full_path.c_str(), &xsink)) {
            {
                JniClassMapLocker al;
                jpc->add(jpath, const_cast<JniQoreClass*>(static_cast<const JniQoreClass*>(existing)));
            }
            printd(LogLevel, "QoreJniClassMap::addClassToProgram() '%s' found via path '%s', "
                "added jpc mapping\n", jpath, full_path.c_str());
            return;
        }
        // clear any exception from class lookup failure
        xsink.clear();
    }

    // copy class for assignment
    std::unique_ptr<JniQoreClass> new_qc(new JniQoreClass(*qc));
    assert(new_qc->isSystem());

    printd(LogLevel, "QoreJniClassMap::addClassToProgram() jpc: %p '%s' qc: %p ns: %p '%s::%s'\n", jpc,
        jpath, new_qc.get(), ns, ns->getName(), qc->getName());

    assert(new_qc->getManagedUserData());

    // create entry for class in map
    {
        JniClassMapLocker al;
        jpc->add(jpath, new_qc.get());
    }

    JniQoreClass* saved_qc = new_qc.release();
    // issue #5056: resolve abstract methods for copied class
    saved_qc->runtimeResolveAbstractMethods();
    ns->addSystemClass(saved_qc);
}

// ACC opcodes
constexpr int ACC_PUBLIC = 0x0001; // class, field, method
constexpr int ACC_PRIVATE = 0x0002; // class, field, method
constexpr int ACC_PROTECTED = 0x0004; // class, field, method
constexpr int ACC_STATIC = 0x0008; // field, method
constexpr int ACC_FINAL = 0x0010; // class, field, method, parameter
constexpr int ACC_SUPER = 0x0020; // class
constexpr int ACC_SYNCHRONIZED = 0x0020; // method
constexpr int ACC_VOLATILE = 0x0040; // field
// ACC_BRIDGE defined in Globals.h
constexpr int ACC_VARARGS = 0x0080; // method
constexpr int ACC_TRANSIENT = 0x0080; // field
constexpr int ACC_NATIVE = 0x0100; // method
constexpr int ACC_INTERFACE = 0x0200; // class
constexpr int ACC_ABSTRACT = 0x0400; // class, method
constexpr int ACC_STRICT = 0x0800; // method
// ACC_SYNTHETIC defined in Globals.h
constexpr int ACC_ANNOTATION = 0x2000; // class
constexpr int ACC_ENUM = 0x4000; // class(?) field inner
constexpr int ACC_MANDATED = 0x8000; // parameter

namespace {
// RAII finalizer for a class-creation-in-progress marker installed by createClassInNamespace().
// On success the owner calls commit() (under m, from the publish critical section); on any early
// exit or exception the destructor removes the marker, wakes waiters (which then re-resolve the
// class through the namespace or retry the build), and frees the marker — all under m.
class ClassCreateMarkerFinalizer {
public:
    DLLLOCAL ClassCreateMarkerFinalizer(QoreJniClassMapBase& map, const std::string& jpath,
            QoreJniClassMapBase::ClassCreateInProgress* marker) : map(map), jpath(jpath), marker(marker) {
    }

    DLLLOCAL ~ClassCreateMarkerFinalizer() {
        if (!marker) {
            return;
        }
        // failure / early exit: remove the marker and wake any waiters so they can retry
        JniClassMapLocker al;
        finish();
    }

    // called under m from the publish critical section on success
    DLLLOCAL void commit() {
        assert(marker);
        finish();
    }

private:
    QoreJniClassMapBase& map;
    const std::string& jpath;
    QoreJniClassMapBase::ClassCreateInProgress* marker;

    // must be called with m held; erases + broadcasts + frees the marker exactly once
    DLLLOCAL void finish() {
        map.jcmap_inprogress.erase(jpath);
        QoreJniClassMap::class_create_cond.broadcast();
        delete marker;
        marker = nullptr;
    }
};
}

JniQoreClass* QoreJniClassMap::findClassOrSelfPartial(QoreJniClassMapBase& map, const char* jpath) {
    QoreString slash(jpath);
    slash.replaceAll(".", "/");
    JniClassMapLocker al;
    if (JniQoreClass* qc = map.findInternal(slash.c_str())) {
        return qc;
    }
    QoreJniClassMapBase::in_progress_class_map_t::iterator i = map.jcmap_inprogress.find(std::string(slash.c_str()));
    if (i != map.jcmap_inprogress.end() && i->second->tid == q_gettid()) {
        return i->second->partial;
    }
    return nullptr;
}

JniQoreClass* QoreJniClassMap::createClassInNamespace(QoreNamespace* ns, QoreNamespace& jns, const char* jpath,
        Class* jc, JniQoreClass* qc, QoreJniClassMapBase& map, QoreProgram* pgm) {
    QoreClassHolder qc_holder(qc);
    // Duplicate and same-thread recursive lookups return before the new Qore class
    // takes ownership. Keep the Java wrapper guarded until that transfer occurs.
    SimpleRefHolder<Class> jc_holder(jc);

    JniExternalProgramData* jpc = pgm
        ? static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"))
        : jni_get_context(pgm);
    assert(jpc);

    const std::string jpath_key(jpath);
    const int mytid = q_gettid();

    // Phase 1 (under m): resolve duplicates and coordinate concurrent/recursive creation.  m must
    // NOT be held across the Java-calling population phase below (addSuperClasses() +
    // populateQoreClass()), which can drive module loading; instead of publishing a half-built class
    // into the map under m (the old approach), we install a per-class marker and release m while
    // building.  See JniClassMapLocker and ClassCreateInProgress.
    JniQoreClass* existing_rv = nullptr;
    bool same_thread_partial = false;
    QoreJniClassMapBase::ClassCreateInProgress* marker = nullptr;
    {
        JniClassMapLocker al;
        while (true) {
            // duplicate: same Qore class name already fully present in the namespace (e.g. the same
            // Java class loaded from multiple JARs, or committed by a concurrent creator we waited on)
            if (JniQoreClass* dup = static_cast<JniQoreClass*>(ns->findLocalClass(qc->getName()))) {
                existing_rv = dup;
                break;
            }
            QoreJniClassMapBase::in_progress_class_map_t::iterator i = map.jcmap_inprogress.find(jpath_key);
            if (i != map.jcmap_inprogress.end()) {
                QoreJniClassMapBase::ClassCreateInProgress* ip = i->second;
                if (ip->tid == mytid) {
                    // same-thread recursion: this class refers back to itself while being populated;
                    // return the partial placeholder (mirrors the old early-publish behavior).  The
                    // outer frame that owns the marker will publish it into the map and saveClass()
                    // it, so we must NOT touch the map here (a map.add() would trip the add()
                    // assertion when the owner later publishes the same jpath)
                    assert(ip->partial);
                    existing_rv = ip->partial;
                    same_thread_partial = true;
                    break;
                }
                // another thread is creating this class: wait for it to finish, then re-check.  m is
                // held exactly once here (no caller holds m across createClassInNamespace()), so
                // cond.wait() correctly releases and reacquires the single level
                QoreJniClassMap::class_create_cond.wait(QoreJniClassMap::m);
                continue;
            }
            // we will build it: install the in-progress marker (partial = qc for self-reference)
            marker = new QoreJniClassMapBase::ClassCreateInProgress;
            marker->tid = mytid;
            marker->partial = qc;
            map.jcmap_inprogress[jpath_key] = marker;
            break;
        }
    }

    if (same_thread_partial) {
        // qc_holder deletes the unused new class; the owner frame handles map + saveClass
        return existing_rv;
    }

    if (existing_rv) {
        printd(LogLevel, "QoreJniClassMap::createClassInNamespace() '%s' already exists in namespace "
            "'%s'; returning existing class %p\n", jpath, ns->getName(), existing_rv);
        // register the jpath mapping to the existing class (if not already present) under m, then
        // save the Java reference OUTSIDE m: saveClass() takes codeGenLock, and m is a strict leaf
        // that must never be held while acquiring codeGenLock
        {
            JniClassMapLocker al;
            if (!map.findInternal(jpath)) {
                map.add(jpath, existing_rv);
            }
        }
        jpc->saveClass(*existing_rv, jc->getJavaObjectRef());
        // qc_holder will delete the unused new class
        return existing_rv;
    }

    // we own the marker: guarantee it is finalized (removed + waiters woken) on every exit path,
    // including exceptions from the Java-calling build below
    ClassCreateMarkerFinalizer fin(map, jpath_key, marker);

    // save pointer to java class info in JniQoreClass
    qc->setManagedUserData(jc_holder.release());

    int mods = jc->getModifiers();
    if (mods & JVM_ACC_FINAL) {
        qc->setFinal();
    }

    printd(LogLevel, "QoreJniClassMap::createClassInNamespace() qc: %p ns: %p '%s::%s'\n", qc, ns, ns->getName(),
        qc->getName());

    assert(qc->getManagedUserData());

    // a Java name with a NUL character is refused before the class is built: refused while it is built, a class built
    // recursively in the meantime could keep the partial class, which is deleted when its creation fails (qore#6738)
    jni_check_java_names(jc);

    // build the class WITHOUT m held (only the caller's per-Program parse lock, which is safe to
    // hold across a module load).  Same-thread recursion that reaches this jpath resolves to the
    // partial via the marker; cross-thread creators block on class_create_cond.
    addSuperClasses(qc, jc, jpath, pgm, jpc);

    // initialize now that all parents are set up; this must happen before
    // populateQoreClass() which can trigger recursive class loading via type
    // resolution — without this, a recursively-loaded class that has this class
    // as an ancestor would prematurely initialize it with an incomplete parent list
    qc->initializeBuiltin();

    // add methods after parents
    if (init_done) {
        // qore#6551: adding the class's members initializes the Java class (the IDs of its static members), which
        // runs its static initializers: Java code that runs in the context of the Program that the class is created
        // in, which the thread has entered; this path runs only when a class is created
        {
            Env env;
            QoreProgram* ctx_pgm = pgm;
            JniContextProgramHolder ctx_holder;
            JniExternalProgramData::setContext(env, ctx_pgm, ctx_holder);
        }
        populateQoreClass(*qc, jc, pgm);
    }

    // issue #5056: resolve abstract methods at runtime for classes created dynamically
    // this is needed for JNI classes imported through Python where abstract method resolution
    // that normally happens at parse time doesn't get called
    qc->runtimeResolveAbstractMethods();

    // Phase 2 (publish under m): commit the finished class into the map + namespace.
    // ns->addSystemClass() for a program-attached namespace is serialized by the caller's parse
    // lock; for the global default_jns it is serialized by m.
    JniQoreClass* committed;
    {
        JniClassMapLocker al;
        // defensive re-check to handle the same Java class loaded from multiple JARs or inherited
        // from a loaded module's namespace: addSuperClasses()/populateQoreClass() may have triggered
        // recursive creation that added a class with this name under a different jpath
        if (JniQoreClass* existing = static_cast<JniQoreClass*>(ns->findLocalClass(qc->getName()))) {
            printd(LogLevel, "QoreJniClassMap::createClassInNamespace() '%s' already exists in namespace '%s'; "
                "using existing class %p instead of %p\n", jpath, ns->getName(), existing, qc);
            if (map.findInternal(jpath)) {
                map.replace(jpath, existing);
            } else {
                map.add(jpath, existing);
            }
            // do NOT delete qc here: a same-thread recursion may already reference it as a base
            // class (it was handed out as the marker's partial), so abandon it as the prior code
            // did rather than risk a use-after-free
            qc_holder.release();
            committed = existing;
        } else {
            map.add(jpath, static_cast<JniQoreClass*>(qc_holder.release()));
            ns->addSystemClass(qc);
            committed = qc;
        }
        // remove the marker + wake waiters while still under m
        fin.commit();
    }

    // save the Java reference OUTSIDE m (codeGenLock; see the existing_rv path above)
    jpc->saveClass(*committed, jc->getJavaObjectRef());

    printd(LogLevel, "QoreJniClassMap::createClassInNamespace() '%s' returning qc: %p ns: %p -> '%s::%s'\n", jpath,
        committed, ns, ns->getName(), committed->getName());

    return committed;
}

void QoreJniClassMap::addSuperClasses(JniQoreClass* qc, Class* jc, const char* jpath, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    Class* parent = jc->getSuperClass();

    printd(LogLevel, "QoreJniClassMap::addSuperClasses() '%s' parent: %p\n", jpath, parent);

    Env env;

    // add superclass
    if (parent) {
        addSuperClass(env, *qc, parent, false, pgm, jpc);
    } else if (qc == QC_OBJECT) {
        // set base class loader: the return value for Class.getClassLoader() with classes loaded by the bootstrap
        // class loader is implementation-dependent; it's possible that this will be nullptr
        LocalReference<jobject> cl = env.callObjectMethod(jc->getJavaObject(), Globals::methodClassGetClassLoader,
            nullptr);
        if (cl) {
            baseClassLoader = cl.makeGlobal();
        }
    } else { // make interface classes at least inherit Object
        qc->addBuiltinVirtualBaseClass(QC_OBJECT);
    }

    // get and process interfaces
    LocalReference<jobjectArray> interfaceArray = jc->getInterfaces();

    for (jsize i = 0, e = env.getArrayLength(interfaceArray); i < e; ++i) {
        addSuperClass(env, *qc, new Class(env.getObjectArrayElement(interfaceArray, i).as<jclass>()), true, pgm, jpc);
    }
}

void QoreJniClassMap::addSuperClass(Env& env, JniQoreClass& qc, jni::Class* parent, bool interface, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    QoreClassUserDataHolder udh(parent);
    //printd(LogLevel, "QoreJniClassMap::addSuperClass() %s parent: %p if: %d pgm: %p jpc: %p qcb: %p jo: %p\n",
    //  qc.getPath(), parent, interface, pgm, jpc, (jclass)Globals::classQoreJavaClassBase, parent->getJavaObject());
    // see if the parent class wraps a Qore class
    if (!interface && !env.isSameObject(Globals::classQoreJavaClassBase, parent->getJavaObject())) {
        jvalue jarg;
        jarg.l = parent->getJavaObject();

        if (env.callBooleanMethod(Globals::classQoreJavaClassBase, Globals::methodClassIsAssignableFrom, &jarg)) {
            // get class field
            bool throw_exception = false;
            auto parent_binding = JniExternalProgramData::tryGetQoreClass(env, parent->getJavaObject());
            QoreClass* qore_parent = parent_binding ? const_cast<QoreClass*>((*parent_binding)->cls) : nullptr;
            if (qore_parent) {
                printd(5, "QoreJniClassMap::addSuperClass() Java class '%s' (%d) has Qore parent '%s' (%d)\n",
                    qc.getName(), qc.getID(), qore_parent->getName(), qore_parent->getID());

                if (qore_parent->isFinal()) {
                    throw_exception = true;
                } else {
                    qc.addBaseClass(qore_parent, true);
                    qc.addBuiltinVirtualBaseClass(QC_OBJECT);
                    return;
                }
            }
            if (throw_exception) {
                throw QoreJniException("FINAL-ERROR", "Java class '%s' cannot inherit final Qore class '%s'",
                    qc.getName(), qore_parent->getName());
            }
        }

        //assert(!env.callBooleanMethod(Globals::classQoreObjectBase, Globals::methodClassIsAssignableFrom, &jarg));
    }

    LocalReference<jstring> clsName = env.callObjectMethod(parent->getJavaObject(), Globals::methodClassGetName,
        nullptr).as<jstring>();
    Env::GetStringUtfChars chars(env, clsName);

    printd(5, "QoreJniClassMap::addSuperClass() qc: '%s' parent: '%s'\n", qc.getName(), chars.c_str());

    // the parent class is looked up by its name, which would be cut at a NUL character (qore#6738)
    QoreString jpath(chars.name("Java class name", "Java class names"));
    jpath.replaceAll(".", "/");
    // use the coordinated lookup: if this parent class is currently being created by this thread
    // (a class referring back to itself through its inheritance/type graph), resolve it to the
    // in-progress partial rather than re-entering creation (which would copy a half-built class)
    JniQoreClass* pc = findClassOrSelfPartial(*this, jpath.c_str());
    if (!pc) {
        // make sure we are not trying to create a Qore class for a dynamic class that already exists in Qore
        assert(!jpath.startsWith("qore"));
        assert(!jpath.startsWith("python"));

        bool base;
        SimpleRefHolder<Class> cls(loadClass(env, jpath.c_str(), base, jpc));

        QoreString cstr(chars.c_str());
        pc = findCreateQoreClass(env, cstr, jpath.c_str(), cls.release(), base, pgm);
        assert(pc);
    }

    // only add if no other parent class already inherits the interface
    if (interface) {
        bool priv;
        if (qc.getClass(*pc, priv))
            return;
    }

    qc.addBuiltinVirtualBaseClass(pc);
}

void QoreJniClassMap::populateQoreClass(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    // do constructors
    doConstructors(qc, jc, pgm);

    // do methods
    doMethods(qc, jc, pgm);

    // do fields
    doFields(qc, jc, pgm);
}

namespace {
//! A Java method or constructor whose variants are added to a %Qore class
struct JniMethodEntry {
    //! the method, until the Java class tracks it when a variant of it is added
    SimpleRefHolder<BaseMethod> holder;
    //! the method
    BaseMethod* meth;
    //! the name of the %Qore method
    std::string name;
    //! the %Qore parameter types of the variant converting %Qore values, and of the variant taking Java objects
    type_vec_t params, alt_params;
    //! the %Qore return type
    const QoreTypeInfo* return_type = nullptr;
    //! the Java signature, for a deterministic choice between methods with the same %Qore signature
    std::string signature;
    //! the depth of the Java parameter classes in the type hierarchy, for overloaded methods; see getDepth()
    int depth = 0;

    DLLLOCAL JniMethodEntry(BaseMethod* meth) : holder(meth), meth(meth) {
    }

    //! Called when a variant of the method is added to the class: the Java class keeps the method
    DLLLOCAL void setUsed(jni::Class* jc) {
        if (*holder) {
            jc->trackMethod(holder.release());
        }
    }
};

//! A variant to add for a Java method or constructor
struct JniVariant {
    JniMethodEntry* entry;
    //! true for the variant taking Java objects for list and map parameters
    bool alt;

    DLLLOCAL const type_vec_t& getParams() const {
        return alt ? entry->alt_params : entry->params;
    }
};
}

//! Returns true if every Java parameter class of \a a is the class, or a subclass, of the parameter of \a b
static bool jni_is_more_specific(Env& env, const BaseMethod& a, const BaseMethod& b) {
    const auto& ap = a.getParamClasses();
    const auto& bp = b.getParamClasses();
    if (ap.size() != bp.size()) {
        return false;
    }
    for (size_t i = 0; i < ap.size(); ++i) {
        jvalue jarg;
        jarg.l = ap[i].second;
        if (!env.callBooleanMethod(bp[i].second, Globals::methodClassIsAssignableFrom, &jarg)) {
            return false;
        }
    }
    return true;
}

//! Returns true if the Java parameter classes of a variant take the Java objects that %Qore lists and hashes are
//! converted to by default: java.util.ArrayList and org.qore.jni.Hash
static bool jni_takes_default_conversions(Env& env, const JniVariant& v) {
    const type_vec_t& params = v.getParams();
    const auto& classes = v.entry->meth->getParamClasses();
    for (size_t i = 0; i < params.size() && i < classes.size(); ++i) {
        jclass target;
        if (params[i] == autoListTypeInfo || params[i] == autoListOrNothingTypeInfo) {
            target = Globals::classArrayList;
        } else if (params[i] == autoHashTypeInfo || params[i] == autoHashOrNothingTypeInfo) {
            target = Globals::classHash;
        } else {
            continue;
        }
        jvalue jarg;
        jarg.l = target;
        if (!env.callBooleanMethod(classes[i].second, Globals::methodClassIsAssignableFrom, &jarg)) {
            return false;
        }
    }
    return true;
}

//! Chooses the Java method or constructor called for a %Qore signature that several of them have
/** Java methods with different Java parameter types can have the same %Qore signature, as every Java list
    parameter is a %Qore list and every Java map parameter a %Qore hash (ex: \c f(List) and \c f(ArrayList)).  The
    method called for a %Qore value is the one Java would call for the Java object that the value is converted to by
    default (a list to a \c java.util.ArrayList, a hash to an \c org.qore.jni.Hash, which is a
    \c java.util.LinkedHashMap):
    - a method that is not a bridge method is preferred
    - then a method whose parameters take those objects (ex: \c f(ArrayList) rather than \c f(LinkedList), and
      \c f(Map) rather than \c f(TreeMap))
    - then the most specific of them, as in Java (ex: \c f(ArrayList) rather than \c f(List))
    - then, if no method is more specific than the others, the first by Java signature

    @return the index of the chosen variant in \a group
*/
static size_t jni_choose_variant(Env& env, const std::vector<JniVariant>& group) {
    std::vector<size_t> pool;
    for (size_t i = 0; i < group.size(); ++i) {
        pool.push_back(i);
    }
    auto filter = [&pool](std::function<bool(size_t)> pred) {
        std::vector<size_t> rv;
        for (size_t i : pool) {
            if (pred(i)) {
                rv.push_back(i);
            }
        }
        if (!rv.empty()) {
            pool.swap(rv);
        }
    };
    if (pool.size() > 1) {
        filter([&group](size_t i) { return !group[i].entry->meth->isBridge(); });
    }
    if (pool.size() > 1) {
        filter([&env, &group](size_t i) { return jni_takes_default_conversions(env, group[i]); });
    }
    if (pool.size() > 1) {
        for (size_t i : pool) {
            bool most_specific = true;
            for (size_t j : pool) {
                if (i != j && !jni_is_more_specific(env, *group[i].entry->meth, *group[j].entry->meth)) {
                    most_specific = false;
                    break;
                }
            }
            if (most_specific) {
                return i;
            }
        }
    }
    size_t rv = pool[0];
    for (size_t i : pool) {
        if (group[i].entry->signature < group[rv].entry->signature) {
            rv = i;
        }
    }
    return rv;
}

//! Returns the number of parameters taking a %Qore list for a Java list
static size_t jni_count_list_params(const type_vec_t& params) {
    size_t rv = 0;
    for (const QoreTypeInfo* ti : params) {
        if (ti == autoListTypeInfo || ti == autoListOrNothingTypeInfo) {
            ++rv;
        }
    }
    return rv;
}

//! Returns the variants to add for Java methods or constructors: for each %Qore name and signature, the variant of
//! the Java method chosen by jni_choose_variant(), in the order in which they are added (see below)
/** @param env the JNI environment
    @param entries the methods or constructors
    @param is_static for each entry: true if it is a static method (static methods and normal methods with the same
    name are added separately)
    @param has_alt for each entry: true if its variant taking Java objects for list and map parameters is added
*/
static std::vector<JniVariant> jni_get_variants(Env& env, std::vector<std::unique_ptr<JniMethodEntry>>& entries,
        std::function<bool(const JniMethodEntry&)> is_static, std::function<bool(const JniMethodEntry&)> has_alt) {
    // the depth of the parameter classes of overloaded methods, so that a Java object argument is passed to the
    // method of its class rather than of a superclass or interface when both variants match it
    std::map<std::string, size_t> name_count;
    for (auto& e : entries) {
        ++name_count[e->name];
    }
    for (auto& e : entries) {
        if (name_count[e->name] > 1) {
            jvalue jarg;
            jarg.l = e->meth->getJavaObject();
            e->depth = env.callStaticIntMethod(Globals::classJavaCollectionTypes,
                Globals::methodJavaCollectionTypesGetDepth, &jarg);
        }
    }

    // the variants for each name and signature, in the order of the methods; a private method, whose types are
    // auto, is never chosen instead of a method that is not private
    typedef std::tuple<std::string, bool, bool, type_vec_t> key_t;
    std::map<key_t, std::vector<JniVariant>> groups;
    for (auto& e : entries) {
        groups[key_t(e->name, is_static(*e), e->meth->isPrivate(), e->params)].push_back(JniVariant{e.get(), false});
        if (!e->alt_params.empty() && has_alt(*e)) {
            groups[key_t(e->name, is_static(*e), e->meth->isPrivate(), e->alt_params)].push_back(
                JniVariant{e.get(), true});
        }
    }
    std::vector<JniVariant> rv;
    for (auto& i : groups) {
        rv.push_back(i.second.size() == 1 ? i.second[0] : i.second[jni_choose_variant(env, i.second)]);
    }
    // the variants are added in an order that does not depend on the addresses of their types; when variants match
    // an argument equally, the first one added is called, so:
    // - a variant converting a list to a Java array is added before a variant converting it to a Java list: a list
    //   argument is passed as an array, as before Java list parameters took Qore lists (ex: f(Object[]) rather than
    //   f(ArrayList))
    // - a variant whose Java parameter classes are subclasses of those of another variant is added before it: a Java
    //   object is passed to the method of its class, as in Java (ex: f(TreeMap) rather than f(Map))
    // - the variants of private methods are added after the others, so that a private variant with the same %Qore
    //   signature as another one (its types are auto) is the one that is not added
    std::stable_sort(rv.begin(), rv.end(), [](const JniVariant& a, const JniVariant& b) {
        if (a.entry->name != b.entry->name) {
            return a.entry->name < b.entry->name;
        }
        if (a.entry->meth->isPrivate() != b.entry->meth->isPrivate()) {
            return !a.entry->meth->isPrivate();
        }
        size_t al = jni_count_list_params(a.getParams());
        size_t bl = jni_count_list_params(b.getParams());
        if (al != bl) {
            return al < bl;
        }
        // a variant whose parameters are subclasses of another's is added first
        if (a.entry->depth != b.entry->depth) {
            return a.entry->depth > b.entry->depth;
        }
        if (a.entry->signature != b.entry->signature) {
            return a.entry->signature < b.entry->signature;
        }
        return !a.alt && b.alt;
    });
    return rv;
}

namespace {
//! A variant taking the variable arguments of a varargs method or constructor as separate arguments
struct JniLooseVariant {
    //! the method or constructor
    BaseMethod* meth;
    //! the name of the %Qore method, owned by its JniMethodEntry; null for a constructor
    const char* name;
    //! the parameter types of the variant; the parameter types of the variant taking the array before
    //! jni_add_loose_varargs_variants() sets them
    type_vec_t params;
    //! the type of the elements of the array of variable arguments
    const QoreTypeInfo* elem_type;
    //! the return type of a method
    const QoreTypeInfo* return_type;
    //! true for an abstract method, which has only the variant for separate arguments, as a concrete variant
    bool abstract_method = false;
};
}

//! Returns true if two lists of parameter types are the same
static bool jni_same_param_types(const type_vec_t& a, const type_vec_t& b) {
    if (a.size() != b.size()) {
        return false;
    }
    for (size_t i = 0, e = a.size(); i < e; ++i) {
        if (!qore_type_equal(a[i], b[i])) {
            return false;
        }
    }
    return true;
}

//! The public variants of a method of the parent classes with given parameter types; see
//! jni_get_parent_variants()
struct JniParentVariants {
    //! true if a variant is not abstract, which may be one of a Java method with fixed parameters
    bool concrete = false;
    //! true if a variant is abstract
    bool abstract = false;
};

//! Returns the public variants of a method of the parent classes of \a qc, at any depth, with the given parameter
//! types that do not take extra arguments
/** A variant that is not public (ex: a protected Java method) is not callable from outside the class, so the class's
    own variants are called instead.
*/
static void jni_get_parent_variants(const QoreClass& qc, const char* name, bool is_static, const type_vec_t& params,
        JniParentVariants& rv) {
    QoreParentClassIterator ci(qc);
    while (ci.next()) {
        const QoreClass& pc = ci.getParentClass();
        const QoreMethod* qm = is_static ? pc.findLocalStaticMethod(name) : pc.findLocalMethod(name);
        if (qm) {
            QoreExternalFunctionIterator vi(*qm->getFunction());
            while (vi.next()) {
                const QoreExternalMethodVariant* v =
                    reinterpret_cast<const QoreExternalMethodVariant*>(vi.getVariant());
                if (v->getAccess() != Public || (v->getCodeFlags() & QCF_USES_EXTRA_ARGS)
                    || !jni_same_param_types(v->getParamTypeList(), params)) {
                    continue;
                }
                if (v->isAbstract()) {
                    rv.abstract = true;
                } else {
                    rv.concrete = true;
                }
            }
        }
        jni_get_parent_variants(pc, name, is_static, params, rv);
    }
}

//! Returns true if the class or one of its parents is a %Qore class, which is not a Java class
static bool jni_has_qore_parent(const QoreClass& qc) {
    QoreParentClassIterator ci(qc);
    while (ci.next()) {
        const QoreClass& pc = ci.getParentClass();
        if (!dynamic_cast<const JniQoreClass*>(&pc) || jni_has_qore_parent(pc)) {
            return true;
        }
    }
    return false;
}

//! Adds the variants taking the variable arguments of varargs methods or constructors as separate arguments
/** Java packs the arguments given for the variable arguments parameter into an array.  The variant taking the array
    declares it as a list (see QoreJniClassMap::JavaTypeUse::VarargsArrayParam), which does not convert a single
    value, so a value that is not a list is a variable argument given as a separate argument.  This variant takes the
    fixed parameters, followed by the variable arguments as separate arguments: it declares the first variable
    argument with the type of the array's elements and takes the remaining arguments as further variable arguments
    (\c QCF_USES_EXTRA_ARGS), so that %Qore passes the arguments as they are given, and
    BaseMethod::getVarargsArray() packs them into the array.  The array variant must not declare a soft list, which
    would convert the first of several separate variable arguments to a list nested in the array, and match no call
    with further arguments with \c %strict-args.

    If a variant added before has the same parameter types, as a Java method with fixed parameters for the same
    number of arguments does (ex: \c EnumSet.of(E, E) and \c EnumSet.of(E, E...)), that variant is called for that
    number of arguments, as Java prefers the method with fixed parameters, and the variant takes one more variable
    argument with the type of the elements, until no variant added before has its parameter types.  The parameter
    types of all the variants are determined before any of them is added; a variant with the same parameter types as
    one of them added before is not added.

    A varargs method or constructor that can be called whose array has elements of a type other than \c auto also
    has a variant taking a @ref Jni::org::qore::jni::JavaArray "JavaArray" for the array, which neither the list nor
    the elements' type accept.  An abstract method has only the variant taking the variable arguments as separate
    arguments, as a concrete variant calling the Java method, so that a %Qore class implementing the method only
    implements the variant taking the soft list (see QoreJniClassMap::JavaTypeUse::VarargsParam).

    @param variants the varargs variants added to the class
    @param exists returns true if the class has a variant with the given parameter types for the method of the
    variant
    @param add adds the variant to the class with the given parameter types and flags
*/
static void jni_add_loose_varargs_variants(std::vector<JniLooseVariant>& variants,
        std::function<bool(const JniLooseVariant&, const type_vec_t&)> exists,
        std::function<void(const JniLooseVariant&, const type_vec_t&, int64)> add) {
    // the variants taking a JavaArray for the array
    std::vector<std::pair<const JniLooseVariant*, type_vec_t>> array_variants;
    for (JniLooseVariant& lv : variants) {
        assert(!lv.params.empty());
        if (!lv.abstract_method && lv.elem_type != autoTypeInfo) {
            type_vec_t params = lv.params;
            params.back() = QC_JAVAARRAY->getTypeInfo();
            array_variants.emplace_back(&lv, std::move(params));
        }
        // replace the array with the first variable argument
        lv.params.back() = lv.elem_type;
        while (exists(lv, lv.params)) {
            // a Java method has at most 255 parameters
            if (lv.params.size() >= 255) {
                lv.params.clear();
                break;
            }
            lv.params.push_back(lv.elem_type);
        }
    }
    for (const JniLooseVariant& lv : variants) {
        if (!lv.params.empty() && !exists(lv, lv.params)) {
            add(lv, lv.params, QCF_USES_EXTRA_ARGS);
        }
    }
    for (const auto& i : array_variants) {
        if (!exists(*i.first, i.second)) {
            add(*i.first, i.second, QCF_NO_FLAGS);
        }
    }
}

void QoreJniClassMap::doConstructors(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    Env env;

    // get constructor methods
    LocalReference<jobjectArray> conArray = jc->getDeclaredConstructors();

    std::vector<std::unique_ptr<JniMethodEntry>> entries;
    for (jsize i = 0, e = env.getArrayLength(conArray); i < e; ++i) {
        // get Constructor object
        LocalReference<jobject> c = env.getObjectArrayElement(conArray, i);

        std::unique_ptr<JniMethodEntry> entry(new JniMethodEntry(new BaseMethod(env, c, jc,
            BaseMethod::Kind::Constructor)));
        BaseMethod* meth = entry->meth;

        // get method's parameter types
        if (meth->getParamTypes(env, entry->params, entry->alt_params, *this, pgm)) {
            printd(LogLevel, "+ skipping %s.constructor(); unsupported parameter type for variant %d\n",
                qc.getName(), i + 1);
            continue;
        }
        entry->name = "constructor";
        QoreString sig;
        meth->getSignature(sig);
        entry->signature = sig.c_str();
        entries.push_back(std::move(entry));
    }

    // the varargs constructors added
    std::vector<JniLooseVariant> varargs_variants;
    for (const JniVariant& v : jni_get_variants(env, entries, [](const JniMethodEntry&) { return false; },
            [](const JniMethodEntry&) { return true; })) {
        BaseMethod* meth = v.entry->meth;
        // check for duplicate signature
        const QoreMethod* qm = qc.getConstructor();
        if (qm && qm->existsVariant(v.getParams())) {
            printd(LogLevel, "QoreJniClassMap::doConstructors() skipping already-created variant %s::constructor()\n",
                qc.getName());
            continue;
        }
        // the variant taking Java objects is an alternative for list and map arguments
        qc.addConstructor((void*)meth, (q_external_constructor_t)exec_java_constructor, meth->getAccess(),
            meth->getFlags(), QDOM_UNCONTROLLED_API, v.getParams());
        v.entry->setUsed(jc);
        if (meth->isVarargs()) {
            varargs_variants.push_back(JniLooseVariant{meth, nullptr, v.getParams(),
                meth->getVarargsElementTypeInfo(*this, pgm), nullptr});
        }
    }

    // the variants taking the variable arguments as separate arguments
    jni_add_loose_varargs_variants(varargs_variants,
        [&qc](const JniLooseVariant&, const type_vec_t& params) {
            const QoreMethod* qm = qc.getConstructor();
            return qm && qm->existsVariant(params);
        },
        [&qc](const JniLooseVariant& lv, const type_vec_t& params, int64 flags) {
            qc.addConstructor((void*)lv.meth, (q_external_constructor_t)exec_java_constructor, lv.meth->getAccess(),
                flags, QDOM_UNCONTROLLED_API, params);
        });
}

// guards relaxed_class_types; a strict leaf taken only around the set operation
static QoreThreadLock relaxed_class_type_lock;
// Java classes whose load failure has already been reported by reportClassLoadRelaxation()
static std::set<std::string> relaxed_class_types;

//! Reports a signature type relaxed to "auto" because the Java class could not be loaded
/** Reported once per Java class for the life of the process: a missing optional JAR can be
    referenced by many signatures, and the consequence is the same for all of them.

    @param jpath the internal (slash-separated) name of the class that could not be loaded
    @param err the error code of the Java exception
    @param desc the description of the Java exception
*/
static void reportClassLoadRelaxation(const char* jpath, const char* err, const char* desc) {
    {
        AutoLocker al(relaxed_class_type_lock);
        if (!relaxed_class_types.insert(jpath).second) {
            return;
        }
    }
    QoreString name(jpath);
    name.replaceAll("/", ".");
    printe("WARNING: jni: Java class '%s' could not be loaded (%s: %s); types referring to it are "
        "relaxed to 'auto' in Qore classes created for Java classes that mention it.  Such classes do not "
        "match classes created where this class does resolve; add the JAR providing it to the classpath to "
        "avoid type errors.\n", name.c_str(), err, desc);
}

//! Returns the declared %Qore type of a Java list or map, or nullptr if the class is neither
/** @see QoreJniClassMap::getQoreType()
*/
static const QoreTypeInfo* jni_get_collection_type(Env& env, jclass cls, JniQoreClass* qc,
        const QoreTypeInfo*& altType, QoreJniClassMap::JavaTypeUse use, jobject member) {
    jvalue jarg;
    jarg.l = cls;
    if (env.callBooleanMethod(Globals::classList, Globals::methodClassIsAssignableFrom, &jarg)) {
        // every Java list is converted to a Qore list
        altType = qc->getOrNothingTypeInfo();
        return autoListOrNothingTypeInfo;
    }
    if (!env.callBooleanMethod(Globals::classMap, Globals::methodClassIsAssignableFrom, &jarg)) {
        // a value of a class that lists or maps implement or extend (ex: Collection, Iterable, Serializable) is a
        // Qore list or hash when it is a list or a map
        if (use == QoreJniClassMap::JavaTypeUse::Value && env.callStaticBooleanMethod(Globals::classJavaCollectionTypes,
                Globals::methodJavaCollectionTypesMayBeListOrMap, &jarg)) {
            return autoTypeInfo;
        }
        return nullptr;
    }
    if (use == QoreJniClassMap::JavaTypeUse::Value) {
        // a map is converted to a hash when all its keys are strings
        jarg.l = member ? member : cls;
        if (!env.callStaticBooleanMethod(Globals::classJavaCollectionTypes,
                Globals::methodJavaCollectionTypesIsStringKeyMap, &jarg)) {
            return autoTypeInfo;
        }
    }
    altType = qc->getOrNothingTypeInfo();
    return autoHashOrNothingTypeInfo;
}

//! Returns true if a value of a Java class can be a value that is converted to a %Qore value that is not an object,
//! without the class being the class of such values
/** The classes are those whose values JavaToQore::convertToQore() and QoreJniClassMap::getValue() convert to strings,
    numbers, booleans, and dates; this must be kept in sync with those conversions.  A class that one of them
    implements or extends (ex: \c java.lang.Number, \c java.lang.CharSequence, \c java.lang.Comparable,
    \c java.time.temporal.Temporal, \c java.util.Date) returns true.  \c org.qore.jni.QoreRelativeTime, whose values
    are converted to dates as well, extends only \c java.lang.Object, and arrays, lists, and maps are handled by
    jni_get_collection_type().
*/
static bool jni_may_be_converted_value(Env& env, jclass cls) {
    const jclass converted_classes[] = {
        Globals::classString,
        Globals::classInteger,
        Globals::classLong,
        Globals::classShort,
        Globals::classByte,
        Globals::classBoolean,
        Globals::classDouble,
        Globals::classFloat,
        Globals::classCharacter,
        Globals::classBigDecimal,
        Globals::classZonedDateTime,
        Globals::classOffsetDateTime,
        Globals::classOffsetTime,
        Globals::classLocalDateTime,
        Globals::classLocalDate,
        Globals::classLocalTime,
        Globals::classInstant,
        Globals::classTimestamp,
        Globals::classDate,
        Globals::classTime,
    };
    for (jclass converted : converted_classes) {
        assert(converted);
        if (env.isSameObject(cls, converted)) {
            return false;
        }
    }
    for (jclass converted : converted_classes) {
        jvalue jarg;
        jarg.l = converted;
        if (env.callBooleanMethod(cls, Globals::methodClassIsAssignableFrom, &jarg)) {
            return true;
        }
    }
    return false;
}

//! Returns true if a value of the given declared type is a container that is converted to a %Qore container only
//! conditionally, so that it is the Java object otherwise
/** A Java map is converted to a hash only when its keys are strings, and neither a list nor a map is converted when
    compatibility types are in effect (see JavaToQore::convertToQore())
*/
static bool jni_value_conversion_is_conditional(const QoreTypeInfo* ti) {
    return ti == autoHashOrNothingTypeInfo || ti == autoListOrNothingTypeInfo;
}

// The single source of the declared Qore types of Java types (see QoreJniClassMap.h and
// design/java-qore-type-mapping.md).  The nullability rule: the type of a Java reference type accepts no value in
// every use (a parameter, a return value, a field or a constant), as Java null is converted to and from no value and
// Qore checks the value read from a field against its declared type and uses the types of return values and
// constants at parse time; it is applied here last, so no mapping can bypass it.
const QoreTypeInfo* QoreJniClassMap::getQoreType(jclass cls, const QoreTypeInfo*& altType, QoreProgram* pgm,
        JavaTypeUse use, jobject member) {
    assert(!altType);
    Env env;
    if (use == JavaTypeUse::VarargsParam) {
        // the array of variable arguments of an abstract method: a soft list, which converts no value to an empty
        // array, as Java passes when there are no variable arguments; its elements follow the nullability rule
        return getQoreTypeIntern(env, cls, altType, pgm, JavaTypeUse::Param, member);
    }
    if (use == JavaTypeUse::VarargsArrayParam) {
        // the array of variable arguments given as an array: a list, which is not converted from a single value, as
        // a soft list would convert it, so that a single value is a variable argument given as a separate argument;
        // no value is an empty array, as Java passes when there are no variable arguments (see
        // BaseMethod::getVarargsArray())
        LocalReference<jstring> clsName = env.callObjectMethod(cls, Globals::methodClassGetName,
            nullptr).as<jstring>();
        Env::GetStringUtfChars tname(env, clsName);
        if (tname == "[B") {
            return binaryOrNothingTypeInfo;
        }
        return qore_get_complex_list_or_nothing_type(getVarargsElementType(cls, pgm));
    }
    const QoreTypeInfo* ti = getQoreTypeIntern(env, cls, altType, pgm, use, member);
    if (use == JavaTypeUse::Param) {
        // the nullability rule: a parameter of a reference type takes no value, which is passed as null
        return env.callBooleanMethod(cls, Globals::methodClassIsPrimitive, nullptr) ? ti
            : qore_get_or_nothing_type(ti);
    }

    // the type of a value accepts every value the conversion yields: a container that is converted only
    // conditionally is the Java object otherwise, and a byte[] is a list of integers when compatibility types are in
    // effect
    const QoreTypeInfo* java_type = altType;
    altType = nullptr;
    if (java_type && jni_value_conversion_is_conditional(ti)) {
        type_vec_t types = {ti, java_type};
        if (const QoreTypeInfo* union_type = qore_get_union_or_nothing_type(types)) {
            ti = union_type;
        }
    } else if (ti == binaryTypeInfo) {
        type_vec_t types = {binaryOrNothingTypeInfo, autoListOrNothingTypeInfo};
        if (const QoreTypeInfo* union_type = qore_get_union_or_nothing_type(types)) {
            ti = union_type;
        }
    }

    // the nullability rule: a value of a reference type can be null, which is converted to no value
    if (!env.callBooleanMethod(cls, Globals::methodClassIsPrimitive, nullptr)) {
        ti = qore_get_or_nothing_type(ti);
    }
    return ti;
}

//! Returns the declared type of a parameter of a Java class that %Qore values other than objects are converted to,
//! or nullptr if no such value is converted to an object of the class
/** A %Qore argument selects the variant Java would select for the converted value: QoreToJava::toObject() converts
    a string to a \c java.lang.String, an integer to a \c java.lang.Long, a float to a \c java.lang.Double, a
    boolean to a \c java.lang.Boolean, a number to a \c java.math.BigDecimal, a date to a
    \c java.time.ZonedDateTime, a binary to a \c byte[], a list to a \c java.util.ArrayList, and a hash to an
    \c org.qore.jni.Hash for a parameter of a class that the converted value's class implements or extends (ex: a
    string for a \c java.lang.CharSequence, an integer for a \c java.lang.Number, a list for a
    \c java.lang.Iterable), as for \c java.lang.Object, so the parameter takes the %Qore value as well as an object of
    the class, and its declared type ranks above the \c auto of an \c Object parameter, as Java prefers the more
    specific class (ex: <tt>m(CharSequence)</tt> over <tt>m(Object)</tt> for a string).  An integer is not passed
    for a \c java.lang.Character, as Java does not convert an \c int to a \c Character.  The classes of the values
    themselves, lists and maps are handled before (see jtmap and jni_get_collection_type()), and \c java.lang.Object
    is \c auto.
*/
static const QoreTypeInfo* jni_get_converted_param_type(Env& env, jclass cls, JniQoreClass* qc) {
    LocalReference<jclass> byte_array_class = env.findClass("[B");
    // an integer is listed before a number, so that an integer argument is passed as a Long
    const std::pair<jclass, const QoreTypeInfo*> conversions[] = {
        {Globals::classString, stringOrNothingTypeInfo},
        {Globals::classLong, bigIntOrNothingTypeInfo},
        {Globals::classDouble, floatOrNothingTypeInfo},
        {Globals::classBoolean, boolOrNothingTypeInfo},
        {Globals::classBigDecimal, numberOrNothingTypeInfo},
        {Globals::classZonedDateTime, dateOrNothingTypeInfo},
        {byte_array_class, binaryOrNothingTypeInfo},
        {Globals::classArrayList, autoListOrNothingTypeInfo},
        {Globals::classHash, autoHashOrNothingTypeInfo},
    };
    type_vec_t types;
    for (const auto& i : conversions) {
        assert(i.first);
        jvalue jarg;
        jarg.l = i.first;
        if (env.callBooleanMethod(cls, Globals::methodClassIsAssignableFrom, &jarg)) {
            types.push_back(i.second);
        }
    }
    if (types.empty()) {
        return nullptr;
    }
    types.push_back(qc->getOrNothingTypeInfo());
    return qore_get_union_or_nothing_type(types);
}

const QoreTypeInfo* QoreJniClassMap::getVarargsElementType(jclass array_cls, QoreProgram* pgm) {
    Env env;
    LocalReference<jclass> elem_cls = env.callObjectMethod(array_cls, Globals::methodClassGetComponentType,
        nullptr).as<jclass>();
    assert(elem_cls);
    // the element as a parameter follows the nullability rule, as the elements of an array parameter do (see
    // getQoreTypeIntern())
    const QoreTypeInfo* elemAltType = nullptr;
    const QoreTypeInfo* elem_type = getQoreType(elem_cls, elemAltType, pgm, JavaTypeUse::Param);
    if (elem_type == objectTypeInfo || elem_type == objectOrNothingTypeInfo) {
        return autoTypeInfo;
    }
    if (!env.callBooleanMethod(elem_cls, Globals::methodClassIsPrimitive, nullptr)) {
        return qore_get_or_nothing_type(elem_type);
    }
    return elem_type;
}

const QoreTypeInfo* QoreJniClassMap::getQoreTypeIntern(Env& env, jclass cls, const QoreTypeInfo*& altType,
        QoreProgram* pgm, JavaTypeUse use, jobject member) {

    // get class name
    LocalReference<jstring> clsName = env.callObjectMethod(cls, Globals::methodClassGetName, nullptr).as<jstring>();
    Env::GetStringUtfChars tname(env, clsName);

    // check for byte[]
    if (tname == "[B") {
        return binaryTypeInfo;
    }

     // do primitive types
    if (env.callBooleanMethod(cls, Globals::methodClassIsPrimitive, nullptr)) {
        jpmap_t::const_iterator i = jpmap.find(tname.c_str());
        assert(i != jpmap.end());
        return i->second.typeInfo;
    }

    // process array types
    if (env.callBooleanMethod(cls, Globals::methodClassIsArray, nullptr)) {
        // get vararg arg type
        LocalReference<jclass> elem_cls = env.callObjectMethod(cls, Globals::methodClassGetComponentType,
            nullptr).as<jclass>();
        if (!elem_cls) {
            return softAutoListTypeInfo;
        }
        const QoreTypeInfo* elemAltType = nullptr;
        // an element of a value has the complete type of a value (see getQoreType())
        const QoreTypeInfo* elem_type = getQoreType(elem_cls, elemAltType, pgm, use);

        if (elem_type == objectTypeInfo || elem_type == objectOrNothingTypeInfo) {
            return softAutoListTypeInfo;
        } else {
            // Java object arrays (e.g. Integer[]) can contain null elements, so use or-nothing element type;
            // Java primitive arrays (e.g. int[]) cannot contain null elements
            if (!env.callBooleanMethod(elem_cls, Globals::methodClassIsPrimitive, nullptr)) {
                elem_type = qore_get_or_nothing_type(elem_type);
            }
            printd(5, "QoreJniClassMap::getQoreType() array type: '%s'\n", qore_type_get_name(elem_type));
            return qore_get_complex_softlist_type(elem_type);
        }
    }

    // a class whose name has a NUL character, which the JVM allows, cannot have a Qore class: the class is looked up by
    // its name, which would be cut at the NUL character and name another class (qore#6738); the type is any object,
    // as for an internal class, and an object of the class is refused when it is converted to Qore
    if (tname.hasNul()) {
        return objectTypeInfo;
    }

    // check for class imported from Qore
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (jpc) {
        jvalue jarg;
        jarg.l = cls;
        if (env.callBooleanMethod(Globals::classQoreJavaClassBase, Globals::methodClassIsAssignableFrom, &jarg)) {
            // Use the registered class binding lookup to avoid dangling
            // QoreClass* dereferences when the canonical owner has been
            // destroyed but the cached classloader still surfaces the class.
            // tryGetQoreClass() retains a lease for the complete metadata read and
            // returns nullptr cleanly on dead-owner.
            auto binding = JniExternalProgramData::tryGetQoreClass(env, cls);
            if (binding) {
                const QoreClass* qc = (*binding)->cls;
                return qc->getOrNothingTypeInfo();
            }
            // dead canonical owner — fall through to standard Java type
            // resolution below
        }
    }

    QoreString cname(tname.c_str());
    if (cname.startsWith("jdk.internal.")) {
        return objectTypeInfo;
    }
    QoreString jname(tname.c_str());
    jname.replaceAll(".", "/");

    printd(LogLevel, "QoreJniClassMap::getQoreType() class: '%s' jname: '%s'\n", cname.c_str(), jname.c_str());

    // find or create a class for the type; use the coordinated lookup so a type that refers back to
    // the class currently being created on this thread resolves to the in-progress partial instead
    // of re-entering creation (which would copy a half-built class)
    JniQoreClass* qc = findClassOrSelfPartial(*this, jname.c_str());
    if (!qc) {
        // try to find mapping in Program-specific class map
        if (jpc) {
            assert(static_cast<QoreJniClassMapBase*>(jpc) != static_cast<QoreJniClassMapBase*>(this));
            qc = findClassOrSelfPartial(*jpc, jname.c_str());
        }

        if (!qc) {
            printd(LogLevel, "QoreJniClassMap::getQoreType() creating cname: '%s' jname: '%s'\n", cname.c_str(),
                jname.c_str());
            try {
                bool base;
                SimpleRefHolder<Class> cls(loadClass(env, jname.c_str(), base, jpc));
                qc = findCreateQoreClass(env, cname, jname.c_str(), cls.release(), base, pgm);
                assert(qc);
            } catch (jni::Exception& e) {
                // A type that appears only in a signature and cannot be loaded must not make the
                // class being built unimportable: Java itself loads and uses such a class as long as
                // the members mentioning the missing type are not called, and optional dependencies
                // are common.  The type is therefore relaxed to "auto" and class creation continues.
                //
                // This is one of the few places where an exception is deliberately not propagated, so
                // it is always reported: relaxing the type changes the class signature, which means a
                // class built here does not compare equal to the same Java class built where the
                // dependency did resolve, and objects of one will not satisfy declared types of the
                // other.  Reported once per class so a missing optional JAR cannot flood the output.
                printd(5, "QoreJniClassMap::getQoreType() failed to load '%s'; using auto type\n",
                    jname.c_str());
                ExceptionSink xsink;
                e.convert(&xsink);
                {
                    QoreStringValueHelper err(xsink.getExceptionErr());
                    QoreStringValueHelper desc(xsink.getExceptionDesc());
                    reportClassLoadRelaxation(jname.c_str(), err->c_str(), desc->c_str());
                }
                xsink.clear();
                return autoTypeInfo;
            }
        }
    }

    // Java lists and maps
    if (const QoreTypeInfo* ti = jni_get_collection_type(env, cls, qc, altType, use, member)) {
        return ti;
    }

    // find static mapping
    jtmap_t::const_iterator i = jtmap.find(tname.c_str());
    if (i != jtmap.end()) {
        if (use == JavaTypeUse::Param) {
            if (!strcmp(tname.c_str(), "java.lang.Character")) {
                // an integer is not converted to a Character parameter, as Java does not convert an int to a
                // Character, so that a call selects the variant Java would select
                return qc->getOrNothingTypeInfo();
            }
            // a parameter takes the Java object as well, with an alternative variant
            altType = qc->getOrNothingTypeInfo();
        }
        return i->second;
    }

    // try all parents to see if a static mapping matches
    QoreParentClassIterator hierarchy_iterator(*qc);
    while (hierarchy_iterator.next()) {
        const QoreClass& pqc = hierarchy_iterator.getParentClass();
        const JniQoreClass* jpqc = dynamic_cast<const JniQoreClass*>(&pqc);
        if (!jpqc) {
            continue;
        }
        const std::string jcname = jpqc->getJavaName();
        // do not return a generic type for the base object class
        if (jcname == "java.lang.Object") {
            continue;
        }
        i = jtmap.find(jcname.c_str());
        if (i != jtmap.end()) {
            // add to jtmap
            jtmap.insert(jtmap_t::value_type(tname.c_str(), i->second));
            if (use == JavaTypeUse::Param) {
                altType = qc->getOrNothingTypeInfo();
            }
            return i->second;
        }
    }

    // a value of a class that the classes of converted values implement or extend (ex: Number, CharSequence,
    // Comparable) is converted to a Qore value that is not an object of the class when it is a converted value (ex: a
    // String returned for a CharSequence is a Qore string)
    if (use == JavaTypeUse::Value) {
        if (jni_may_be_converted_value(env, cls)) {
            return autoTypeInfo;
        }
    } else if (const QoreTypeInfo* ti = jni_get_converted_param_type(env, cls, qc)) {
        // a parameter of a class that the classes of converted values implement or extend takes the Qore values
        // converted to them (ex: a string for a CharSequence) as well as an object of the class
        return ti;
    }

    return qc->getOrNothingTypeInfo();
}

void QoreJniClassMap::doMethodsIntern(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm, Env& env,
        jobjectArray mArray) {
    bool abstract_class = jc->getModifiers() & JVM_ACC_ABSTRACT;

    std::vector<std::unique_ptr<JniMethodEntry>> entries;
    for (jsize i = 0, e = env.getArrayLength(mArray); i < e; ++i) {
        // get Method object
        LocalReference<jobject> m = env.getObjectArrayElement(mArray, i);

        std::unique_ptr<JniMethodEntry> entry(new JniMethodEntry(new BaseMethod(m, jc)));
        BaseMethod* meth = entry->meth;

        QoreString mname;
        meth->getName(mname);

        // Skip synthetic methods that are not bridge methods; bridge methods are typically
        // also marked synthetic but are needed to satisfy interface contracts (e.g.
        // Float.compareTo(Object) implementing Comparable.compareTo); a bridge method is added only for a %Qore
        // signature that no other method has (see jni_choose_variant())
        if (meth->isSynthetic() && !meth->isBridge()) {
            printd(LogLevel, "+ skipping synthetic method %s.%s()\n", qc.getName(), mname.c_str());
            continue;
        }

        // Skip Kotlin default parameter method variants (e.g., "methodName$default")
        if (mname.find("$default") != -1) {
            printd(LogLevel, "+ skipping Kotlin default param method %s.%s()\n", qc.getName(), mname.c_str());
            continue;
        }

        // the name of the Qore method would be cut at a NUL character, which the JVM allows in a method name
        // (qore#6738)
        jni_check_name(mname.c_str(), mname.size(), "Java method name", "Java method names");

        printd(LogLevel, "+ adding %smethod %s.%s()\n", meth->isBridge() ? "bridge " : "", qc.getName(),
            mname.c_str());

        // get method's parameter types
        if (meth->getParamTypes(env, entry->params, entry->alt_params, *this, pgm)) {
            printd(LogLevel, "+ skipping %s.%s(); unsupported parameter type for variant %d\n", qc.getName(),
                mname.c_str(), i + 1);
            continue;
        }

        // get method's return type
        entry->return_type = meth->getReturnTypeInfo(*this, pgm);

        if (!meth->isStatic() && (mname == "copy" || mname == "constructor" || mname == "destructor"
            || mname == "methodGate" || mname == "memberNotification" || mname == "memberGate")) {
            mname.prepend("java_");
        }
        entry->name = mname.c_str();
        QoreString sig;
        meth->getSignature(sig);
        entry->signature = sig.c_str();
        entries.push_back(std::move(entry));
    }

    // the varargs methods added
    std::vector<JniLooseVariant> varargs_variants;
    bool qore_parent = jni_has_qore_parent(qc);
    for (const JniVariant& v : jni_get_variants(env, entries,
            [](const JniMethodEntry& e) { return (bool)e.meth->isStatic(); },
            // do not add additional abstract variants for alternate parameter types
            [abstract_class](const JniMethodEntry& e) { return !(abstract_class && e.meth->isAbstract()); })) {
        BaseMethod* meth = v.entry->meth;
        const char* mname = v.entry->name.c_str();
        const type_vec_t& params = v.getParams();
        // the variant taking Java objects for list and map arguments has no flags
        int64 flags = v.alt ? QCF_NO_FLAGS : meth->getFlags();
        // a varargs method of a class that is not abstract implements every abstract variant of its name that the
        // class inherits, as Java requires; the array is declared as a list, while an abstract method declares it as
        // a soft list (see QoreJniClassMap::JavaTypeUse), so the signatures differ; not for a class with a %Qore
        // parent, whose abstract variants Java does not know
        if (meth->isVarargs() && !abstract_class && !meth->isStatic() && !qore_parent) {
            flags |= QCF_ABSTRACT_OVERRIDE_ALL;
        }

        if (meth->isStatic()) {
            // check for duplicate signature
            const QoreMethod* qm = qc.findLocalStaticMethod(mname);
            if (qm && qm->existsVariant(params)) {
                printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created static variant %s::%s()\n",
                    qc.getName(), mname);
                continue;
            }
            qc.addStaticMethod((void*)meth, mname, (q_external_static_method_t)exec_java_static_method,
                meth->getAccess(), flags, QDOM_UNCONTROLLED_API, v.entry->return_type, params);
        } else {
            // check for duplicate signature
            const QoreMethod* qm = qc.findLocalMethod(mname);
            if (qm && qm->existsVariant(params)) {
                printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created variant %s::%s()\n",
                    qc.getName(), mname);
                continue;
            }

            if (abstract_class && meth->isAbstract()) {
                qc.addAbstractMethod(mname, meth->getAccess(), flags, v.entry->return_type, params);
            } else {
                qc.addMethod((void*)meth, mname, (q_external_method_t)exec_java_method, meth->getAccess(), flags,
                    QDOM_UNCONTROLLED_API, v.entry->return_type, params);
            }
        }
        v.entry->setUsed(jc);
        if (meth->isVarargs()) {
            JniLooseVariant lv{meth, mname, params, meth->getVarargsElementTypeInfo(*this, pgm),
                v.entry->return_type};
            lv.abstract_method = abstract_class && meth->isAbstract();
            varargs_variants.push_back(std::move(lv));
        }
    }

    // the variants taking the variable arguments as separate arguments; the entries owning the names are alive
    // the methods (name, static, parameter types) for which no inherited Java method with fixed parameters was found
    // while adding the variants of this class, so that its Java methods are not listed again for them
    std::vector<std::tuple<std::string, bool, type_vec_t>> no_fixed_arity;
    jni_add_loose_varargs_variants(varargs_variants,
        [this, &qc, jc, pgm, &no_fixed_arity](const JniLooseVariant& lv, const type_vec_t& params) {
            bool is_static = lv.meth->isStatic();
            const QoreMethod* qm = is_static ? qc.findLocalStaticMethod(lv.name) : qc.findLocalMethod(lv.name);
            if (qm && qm->existsVariant(params)) {
                return true;
            }
            JniParentVariants pv;
            jni_get_parent_variants(qc, lv.name, is_static, params, pv);
            // a variant of a parent class for a Java method with fixed parameters is called for its number of
            // arguments, as in Java, also when an ancestor declares the method as abstract; other variants (ex:
            // of a varargs method) are not
            if (pv.concrete) {
                bool known = false;
                for (const auto& i : no_fixed_arity) {
                    if (std::get<0>(i) == lv.name && std::get<1>(i) == is_static
                        && jni_same_param_types(std::get<2>(i), params)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    if (addInheritedFixedArityVariant(qc, jc, lv.name, is_static, params, pgm)) {
                        return true;
                    }
                    no_fixed_arity.emplace_back(lv.name, is_static, params);
                }
            }
            // an abstract variant of a parent with the parameter types (ex: I1.m(String) for I2.m(String...)) must
            // not be implemented by the variant for separate arguments
            return pv.abstract;
        },
        [&qc](const JniLooseVariant& lv, const type_vec_t& params, int64 flags) {
            // the variant of an abstract method calls the Java method of the object, as a concrete variant
            if (lv.meth->isStatic()) {
                qc.addStaticMethod((void*)lv.meth, lv.name, (q_external_static_method_t)exec_java_static_method,
                    lv.meth->getAccess(), flags, QDOM_UNCONTROLLED_API, lv.return_type, params);
            } else {
                qc.addMethod((void*)lv.meth, lv.name, (q_external_method_t)exec_java_method, lv.meth->getAccess(),
                    flags, QDOM_UNCONTROLLED_API, lv.return_type, params);
            }
        });
}

bool QoreJniClassMap::addInheritedFixedArityVariant(JniQoreClass& qc, jni::Class* jc, const char* name,
        bool is_static, const type_vec_t& params, QoreProgram* pgm) {
    // find the public Java methods with fixed parameters that have the parameter types; Class.getMethods() returns
    // the inherited public methods as well
    std::vector<std::unique_ptr<JniMethodEntry>> entries;
    std::vector<JniVariant> group;
    Env env;
    LocalReference<jobjectArray> methods = env.callObjectMethod(jc->getJavaObject(), Globals::methodClassGetMethods,
        nullptr).as<jobjectArray>();
    for (jsize i = 0, e = methods ? env.getArrayLength(methods) : 0; i < e; ++i) {
        LocalReference<jobject> m = env.getObjectArrayElement(methods, i);
        // check the name before creating the method
        {
            LocalReference<jstring> jmname = env.callObjectMethod(m, Globals::methodMethodGetName,
                nullptr).as<jstring>();
            Env::GetStringUtfChars mn(env, jmname);
            // a name with a NUL character is not the name, which cannot have one (qore#6738)
            if (mn.hasNul()) {
                continue;
            }
            QoreString mname(mn.c_str());
            // see doMethodsIntern()
            if (!is_static && (mname == "copy" || mname == "constructor" || mname == "destructor"
                || mname == "methodGate" || mname == "memberNotification" || mname == "memberGate")) {
                mname.prepend("java_");
            }
            if (strcmp(mname.c_str(), name)) {
                continue;
            }
        }
        std::unique_ptr<JniMethodEntry> entry(new JniMethodEntry(new BaseMethod(m, jc)));
        BaseMethod* meth = entry->meth;
        // bridge methods are synthetic, and the method they bridge to is preferred
        if ((bool)meth->isStatic() != is_static || meth->isAbstract() || meth->isVarargs() || meth->isPrivate()
            || meth->isSynthetic() || meth->getAccess() != Public) {
            continue;
        }
        if (meth->getParamTypes(env, entry->params, entry->alt_params, *this, pgm)) {
            continue;
        }
        bool alt = false;
        if (!jni_same_param_types(entry->params, params)) {
            if (entry->alt_params.empty() || !jni_same_param_types(entry->alt_params, params)) {
                continue;
            }
            alt = true;
        }
        entry->name = name;
        entry->return_type = meth->getReturnTypeInfo(*this, pgm);
        QoreString sig;
        meth->getSignature(sig);
        entry->signature = sig.c_str();
        group.push_back(JniVariant{entry.get(), alt});
        entries.push_back(std::move(entry));
    }
    if (group.empty()) {
        // the parent's variant is not one of a public Java method with fixed parameters
        return false;
    }

    // the method is chosen as for the methods of a class with the same %Qore signature
    const JniVariant& v = group.size() == 1 ? group[0] : group[jni_choose_variant(env, group)];
    BaseMethod* meth = v.entry->meth;
    printd(LogLevel, "QoreJniClassMap::addInheritedFixedArityVariant() adding inherited variant %s::%s()\n",
        qc.getName(), name);
    // the variant taking Java objects for list and map arguments has no flags
    int64 flags = v.alt ? QCF_NO_FLAGS : meth->getFlags();
    if (is_static) {
        qc.addStaticMethod((void*)meth, name, (q_external_static_method_t)exec_java_static_method,
            meth->getAccess(), flags, QDOM_UNCONTROLLED_API, v.entry->return_type, params);
    } else {
        qc.addMethod((void*)meth, name, (q_external_method_t)exec_java_method, meth->getAccess(), flags,
            QDOM_UNCONTROLLED_API, v.entry->return_type, params);
    }
    // the Java class keeps the method
    v.entry->setUsed(jc);
    return true;
}

void QoreJniClassMap::doMethods(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    Env env;

    LocalReference<jobjectArray> mArray = jc->getDeclaredMethods();
    doMethodsIntern(qc, jc, pgm, env, mArray);
}

static int qore_jni_get_acc_visibility(ClassAccess access) {
    switch (access) {
        case Internal: return ACC_PRIVATE;
        case Private: return ACC_PROTECTED;
        default:
            break;
    }
    return ACC_PUBLIC;
}

//! Returns the Java class of a Qore class created for a Java class, or no value if the class is not one
/** The class is loaded with the class loader, which finds it through its parent loaders; it must not be generated from
    the Qore class, as is done for classes defined in Qore
*/
static LocalReference<jclass> jni_get_java_class_for_jni_class(Env& env, jobject class_loader, const QoreClass& qc) {
    const JniQoreClass* jqc = dynamic_cast<const JniQoreClass*>(&qc);
    if (!jqc || jqc->getJavaBinaryName().empty()) {
        return LocalReference<jclass>();
    }
    LocalReference<jstring> jname = env.newString(jqc->getJavaBinaryName().c_str());
    jvalue jarg;
    jarg.l = jname;
    return env.callObjectMethod(class_loader, Globals::methodClassLoaderLoadClass, &jarg).as<jclass>();
}

static LocalReference<jobject> get_type_def_from_class(Env& env, jclass jcls) {
    //printd(5, "get_type_def_from_class() jcls: %p\n", jcls);
    jvalue arg;
    arg.l = jcls;
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeDescriptionCls, &arg);
}

static LocalReference<jobject> get_reference_type_def_from_base_type(Env& env, qore_type_t t) {
    switch (t) {
        case NT_INT: {
            LocalReference<jclass> jtype = env.findClass("java/lang/Long");
            return get_type_def_from_class(env, static_cast<jclass>(jtype));
        }
        case NT_FLOAT: {
            LocalReference<jclass> jtype = env.findClass("java/lang/Double");
            return get_type_def_from_class(env, static_cast<jclass>(jtype));
        }
        case NT_BOOLEAN: {
            LocalReference<jclass> jtype = env.findClass("java/lang/Boolean");
            return get_type_def_from_class(env, static_cast<jclass>(jtype));
        }
        case NT_NOTHING:
        case NT_NULL:
            return get_type_def_from_class(env, Globals::classObject);
        default:
            break;
    }

    LocalReference<jclass> jtype(QoreJniClassMap::getPrimitiveType(t));
    return get_type_def_from_class(env, static_cast<jclass>(jtype));
}

#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
static const char* get_type_param_name_for_context(const QoreTypeInfo* ti, const QoreClass* generic_context) {
    if (!generic_context || !qore_type_is_type_parameter(ti)) {
        return nullptr;
    }

    const QoreClass* owner = qore_type_get_type_parameter_owner_class(ti);
    if (!owner) {
        return nullptr;
    }

    const char* name = qore_type_get_type_parameter_name(ti);
    if (!name || !name[0]) {
        return nullptr;
    }

    if (owner == generic_context) {
        return name;
    }

    if (strcmp(owner->getPath(), generic_context->getPath())) {
        return nullptr;
    }

    size_t index = qore_type_get_type_parameter_index(ti);
    if (index == static_cast<size_t>(-1) || index >= generic_context->getTypeParameterCount()) {
        return nullptr;
    }

    const char* context_name = generic_context->getTypeParameterName(index);
    if (!context_name || strcmp(name, context_name)) {
        return nullptr;
    }

    return context_name;
}

static LocalReference<jobject> get_type_variable_def(Env& env, const char* name) {
    jvalue arg;
    LocalReference<jstring> jname = env.newString(name);
    arg.l = jname;
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeVariable, &arg);
}

static LocalReference<jobject> get_java_type_param_list(Env& env, const QoreClass& qcls) {
    size_t count = qcls.getTypeParameterCount();
    if (!count) {
        return nullptr;
    }

    LocalReference<jobject> rv = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);
    for (size_t i = 0; i < count; ++i) {
        const char* param = qcls.getTypeParameterName(i);
        if (!param || !param[0]) {
            continue;
        }

        jvalue arg;
        LocalReference<jstring> jparam = env.newString(param);
        arg.l = jparam;
        env.callBooleanMethod(rv, Globals::methodArrayListAdd, &arg);
    }

    return rv;
}
#endif

// Only declared fields identify generated classes: a user Java subclass must
// get its own Qore wrapper, rather than inheriting the parent's class identity.
// Legacy pointer fields are deliberately never dereferenced.
std::unique_ptr<GeneratedBindingLease> JniExternalProgramData::tryGetQoreClass(Env& env, jclass jcls) {
    try {
        jvalue arg;
        arg.l = Globals::javaQoreClassField;
        LocalReference<jobject> field = env.callObjectMethod(jcls, Globals::methodClassGetDeclaredField, &arg);
        arg.l = nullptr;
        jlong handle = env.callLongMethod(field, Globals::methodFieldGetLong, &arg);
        return std::make_unique<GeneratedBindingLease>(handle, GeneratedBindingKind::Class);
    } catch (jni::Exception& e) {
        e.ignore();
    } catch (const std::runtime_error&) {
        // A missing or expired binding has no usable native class identity.
    }
    return nullptr;
}

void JniExternalProgramData::clearCompilationCache() {
    // never waits for codeGenLock (see the header): a recursive lock that this thread holds is acquired again
    if (!codeGenLock.trylock()) {
        q2jmap.clear();
        codeGenLock.unlock();
        return;
    }
    // another thread holds it: it clears the cache when it releases its outermost hold; if it released it before it
    // could see the request, the lock is taken now, by this thread or by another holder, which clears the cache
    clearPending.store(true, std::memory_order_release);
    if (!codeGenLock.trylock()) {
        if (clearPending.exchange(false, std::memory_order_acq_rel)) {
            q2jmap.clear();
        }
        codeGenLock.unlock();
    }
}

#ifdef DEBUG
size_t JniExternalProgramData::getCompilationCacheSizeForTest() {
    CodeGenLocker al(*this);
    return q2jmap.size();
}
#endif

void JniExternalProgramData::saveClass(const QoreClass& qc, LocalReference<jclass> jcls) {
    CodeGenLocker al(*this);

    std::string cls_hash = get_class_hash(qc);
    q2jmap_t::iterator i = q2jmap.lower_bound(cls_hash);
    if (i == q2jmap.end() || i->first != cls_hash) {
        q2jmap.insert(i, q2jmap_t::value_type(cls_hash, jcls.makeGlobal()));
    }
}

jclass JniExternalProgramData::findJavaClass(const QoreClass& qc) {
    std::string cls_hash = get_class_hash(qc);
    q2jmap_t::iterator i = q2jmap.find(cls_hash);
    return i == q2jmap.end() ? nullptr : (jclass)i->second;
}

jobject JniExternalProgramData::getJavaParamList(Env& env, jobject class_loader, const QoreExternalVariant& v,
        unsigned& len, bool do_varargs, bool is_abstract, const QoreClass* generic_context) {
    const type_vec_t& params = v.getParamTypeList();
    len = params.size();
    if (params.empty() && !do_varargs) {
        return nullptr;
    }
    assert(len || (do_varargs && (v.getCodeFlags() & QCF_USES_EXTRA_ARGS)));

    printd(5, "JniExternalProgramData::getJavaParamList() %s %d param(s)\n", v.getSignatureText(),
        (int)params.size());

    // create parameter list
    LocalReference<jobject> plist = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);

    for (const QoreTypeInfo* i : params) {
        LocalReference<jobject> ptype = getJavaTypeDefinition(env, class_loader, i, true, generic_context);
        printd(5, "%s: adding %s -> %p\n", v.getSignatureText(), type_get_name(i), *ptype);

        jvalue jarg;
        jarg.l = ptype;
        env.callBooleanMethod(plist, Globals::methodArrayListAdd, &jarg);
    }
    if (do_varargs) {
        assert((v.getCodeFlags() & QCF_USES_EXTRA_ARGS));
        // add Object... as the final parameter
        jvalue jarg;
        LocalReference<jobject> jtype(get_type_def_from_class(env, (jclass)Globals::arrayClassObject));
        jarg.l = jtype;
        env.callBooleanMethod(plist, Globals::methodArrayListAdd, &jarg);
    }

    return plist.release();
}

static void shorten_params(Env& env, LocalReference<jobject>& params, unsigned len) {
    jvalue jarg;
    jarg.i = (int)len;
    env.callObjectMethod(params, Globals::methodArrayListRemove, &jarg);
}

static bool check_optional_last_param(Env& env, const QoreExternalVariant& v, LocalReference<jobject>& params,
        unsigned& len) {
    --len;
    const type_vec_t& qore_params = v.getParamTypeList();
    assert(qore_params.size() > len);
    // if the type accepts NOTHING, then it's optional
    if (qore_type_is_assignable_from(qore_params[len], QoreValue())) {
        shorten_params(env, params, len);
        return true;
    }

    // auto type is effectively optional for Java overloads
    if (qore_type_equal(qore_params[len], autoTypeInfo)) {
        shorten_params(env, params, len);
        return true;
    }

    const arg_vec_t& def_args = v.getDefaultArgList();
    if (def_args.size() > len && def_args[len]) {
        shorten_params(env, params, len);
        return true;
    }
    return false;
}

QoreJavaParamHelper::QoreJavaParamHelper(Env& env, const char* mname, jclass parent_class,
        const QoreClass* qore_parent)
        : env(env), mname(mname),
        parent_class(parent_class), qore_parent(qore_parent),
        plist(env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr)) {
}

void QoreJavaParamHelper::add(LocalReference<jobject>& params) {
    jvalue jarg;
    LocalReference<jobject> params_copy;
    if (params) {
        // first we need to clone the object
        params_copy = env.callObjectMethod(params, Globals::methodObjectClone, nullptr);
        jarg.l = params_copy;
    } else {
        jarg.l = nullptr;
    }
    env.callBooleanMethod(plist, Globals::methodArrayListAdd, &jarg);
}

int QoreJavaParamHelper::checkVariant(LocalReference<jobject>& params, qore_method_type_t method_type) {
    int list_size = env.callIntMethod(plist, Globals::methodArrayListSize, nullptr);

    // get size of param list
    int plen = params ? env.callIntMethod(params, Globals::methodArrayListSize, nullptr) : 0;

    for (int i = 0; i < list_size; ++i) {
        // get list element
        jvalue jarg;
        jarg.i = i;
        LocalReference<jobject> params0 = env.callObjectMethod(plist, Globals::methodArrayListGet, &jarg);

        // get size of p0
        int plen0 = params0 ? env.callIntMethod(params0, Globals::methodArrayListSize, nullptr) : 0;

        // skip comparisons if the sizes are different
        if (plen != plen0) {
            printd(5, "QoreJavaParamHelper::checkVariant() IGNORING variant with plen0: %d (plen: %d " \
                "list_size: %d)\n", plen0, plen, list_size);
            continue;
        }
        printd(5, "QoreJavaParamHelper::checkVariant() CHECKING plen: %d plen0: %d (list_size: %d)\n", plen, plen0,
            list_size);

        bool match = true;
        // compare each parameter type in order
        for (int j = 0; j < plen; ++j) {
            jarg.i = j;
            LocalReference<jobject> e = env.callObjectMethod(params, Globals::methodArrayListGet, &jarg);
            LocalReference<jobject> e0 = env.callObjectMethod(params0, Globals::methodArrayListGet, &jarg);

            jarg.l = e0;
            bool equal = env.callBooleanMethod(e, Globals::methodObjectEquals, &jarg);

            /*
            // XXX DEBUG: FIXME add TypeDescription.getCanonicalName()
            LocalReference<jstring> eName = env.callObjectMethod(e, Globals::methodTypeDescriptionGetCanonicalName,
                nullptr).as<jstring>();
            LocalReference<jstring> eName0 = env.callObjectMethod(e0, Globals::methodTypeDescriptionGetCanonicalName,
                nullptr).as<jstring>();
            Env::GetStringUtfChars c(env, eName);
            Env::GetStringUtfChars c0(env, eName0);
            printd(5, "QoreJavaParamHelper::checkVariant() %s == %s (%s)\n", c.c_str(), c0.c_str(),
                equal ? "true" : "false");
            */

            if (!equal) {
                match = false;
                break;
            }
        }
        if (match) {
            printd(5, "QoreJavaParamHelper::checkVariant() SKIPPING method with plen: %d (list_size: %d)\n", plen,
                list_size);
            return -1;
        }
    }

    // if there are no matches in the list, then check base class methods of the opposite type.
    // we cannot skip this check when qore_parent is non-null: even when we inherit from another
    // Qore class, the parent's bytecode is loaded into the JVM, and the JVM rejects same-name +
    // same-descriptor pairs across the static / non-static boundary in the same class file
    // (e.g. child declaring a static method that collides with an inherited instance method).
    if (mname) {
        try {
            // check for a conflict in a base class with a method of the opposite type
            jvalue jargs[4];
            jargs[0].l = parent_class;
            LocalReference<jstring> jname = env.newString(mname);
            jargs[1].l = jname;
            jargs[2].l = params;
            jargs[3].z = method_type == QMT_NORMAL;
            jboolean conflict = env.callStaticBooleanMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderFindBaseClassMethodConflict, &jargs[0]);

            if (conflict) {
                printd(5, "QoreJavaParamHelper::checkVariant() SKIPPING method '%s' with matching base method; " \
                    "plen %d (list_size: %d)\n", mname, plen, list_size);
                return -1;
            }

            /*
            // convert ArrayList to array

            jvalue jargs[2];
            LocalReference<jstring> jname = env.newString(mname);
            jargs[0].l = jname;
            LocalReference<jobject> param_types = env.callObjectMethod(params, Globals::methodArrayListToArray, nullptr);
            jargs[1].l = param_types;
            LocalReference<jobject> method = env.callObjectMethod(parent_class, Globals::methodClassGetMethod, &jargs[0]);

            int mods = env.callIntMethod(method, Globals::methodMethodGetModifiers, nullptr);
            if ((method_type == QMT_NORMAL && (mods & JVM_ACC_STATIC))
                || (method_type == QMT_STATIC && !(mods & JVM_ACC_STATIC))) {
                printd(5, "QoreJavaParamHelper::checkVariant() SKIPPING method with matching base method; " \
                    "plen %d (list_size: %d)\n", plen, list_size);
                return -1;
            }
            */
        } catch (JavaException& e) {
            e.ignore();
        }
    }

    return 0;
}

LocalReference<jobject> QoreJavaParamHelper::findOverriddenMethod(const char* name,
        const QoreExternalMethodVariant& v, QoreProgram* pgm, jobject class_loader, const QoreClass& qcls) {
    // the Java classes and interfaces of the class's parents; the base class of the generated class is not searched,
    // as it is a jni class (ex: QoreJavaClassBase) or a class generated for a Qore parent class, whose methods are not
    // Java methods that a Qore method overrides
    std::vector<LocalReference<jclass>> loaded;
    std::vector<jclass> classes;
    {
        QoreParentClassIterator ci(qcls);
        while (ci.next()) {
            LocalReference<jclass> jcls = jni_get_java_class_for_jni_class(env, class_loader, ci.getParentClass());
            if (jcls) {
                classes.push_back(jcls);
                loaded.push_back(std::move(jcls));
            }
        }
    }

    const type_vec_t& vtypes = v.getParamTypeList();
    LocalReference<jstring> jname = env.newString(name);
    for (jclass jcls : classes) {
        jvalue jargs[3];
        jargs[0].l = jcls;
        jargs[1].l = jname;
        jargs[2].i = (jint)vtypes.size();
        LocalReference<jobjectArray> methods = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
            Globals::methodJavaClassBuilderGetOverridableMethods, &jargs[0]).as<jobjectArray>();
        for (jsize i = 0, e = methods ? env.getArrayLength(methods) : 0; i < e; ++i) {
            LocalReference<jobject> jm = env.getObjectArrayElement(methods, i);
            LocalReference<jobjectArray> pclasses = env.callObjectMethod(jm, Globals::methodMethodGetParameterTypes,
                nullptr).as<jobjectArray>();
            bool varargs = env.callBooleanMethod(jm, Globals::methodMethodIsVarArgs, nullptr);
            // the variant must have the types of the variant converting Qore values, or of the variant taking Java
            // objects for list and map parameters
            bool primary = true;
            bool alt = true;
            for (size_t j = 0; j < vtypes.size() && (primary || alt); ++j) {
                LocalReference<jclass> pcls = env.getObjectArrayElement(pclasses, (jsize)j).as<jclass>();
                const QoreTypeInfo* alt_type = nullptr;
                const QoreTypeInfo* ti = qjcm.getQoreType(pcls, alt_type, pgm);
                // the array of variable arguments may also be declared as the module declares it: a list, or a soft
                // list as for an abstract method (see QoreJniClassMap::JavaTypeUse)
                if (varargs && j == vtypes.size() - 1 && !qore_type_equal(ti, vtypes[j])) {
                    const QoreTypeInfo* va_alt_type = nullptr;
                    if (qore_type_equal(qjcm.getQoreType(pcls, va_alt_type, pgm,
                                QoreJniClassMap::JavaTypeUse::VarargsArrayParam), vtypes[j])
                            || qore_type_equal(qjcm.getQoreType(pcls, va_alt_type, pgm,
                                QoreJniClassMap::JavaTypeUse::VarargsParam), vtypes[j])) {
                        ti = vtypes[j];
                    }
                }
                if (!qore_type_equal(ti, vtypes[j])) {
                    primary = false;
                }
                if (!qore_type_equal(alt_type ? alt_type : ti, vtypes[j])) {
                    alt = false;
                }
            }
            if (primary || alt) {
                return jm;
            }
        }
    }
    return LocalReference<jobject>();
}

bool QoreJavaParamHelper::isFinalMethod(const char* name, LocalReference<jobject>& params) {
    if (!parent_class) {
        return false;
    }
    try {
        jvalue jargs[3];
        jargs[0].l = parent_class;
        LocalReference<jstring> jname = env.newString(name);
        jargs[1].l = jname;
        jargs[2].l = params;
        return env.callStaticBooleanMethod(Globals::classJavaClassBuilder,
            Globals::methodJavaClassBuilderIsFinalBaseClassMethod, &jargs[0]);
    } catch (JavaException& e) {
        e.ignore();
    }
    return false;
}

int JniExternalProgramData::addConstructorVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v, jclass parent_class,
        QoreJavaParamHelper& jph) {
    printd(5, "JniExternalProgramData::addConstructorVariant() adding Java constructor %s %s::constructor(%s) {}\n",
        v.getAccessString(), qcls.getName(), v.getSignatureText());

    bool varargs = v.getCodeFlags() & QCF_USES_EXTRA_ARGS ? true : false;

    // first get the params
    unsigned len;
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs, false, &qcls);

    while (true) {
        if (!jph.checkVariant(params, QMT_CONSTRUCTOR)) {
            std::vector<jvalue> jargs(7);
            jargs[0].l = bb;
            jargs[1].l = parent_class;
            jargs[2].j = 0;
            jargs[3].j = 0;
            jargs[4].i = qore_jni_get_acc_visibility(v.getAccess());
            jargs[5].l = params;
            jargs[6].z = varargs;

            printd(5, "JniExternalProgramData::addConstructorVariant() %s %s::constructor(%s): adding (len: %d " \
                "params: %p)\n", v.getAccessString(), qcls.getName(), v.getSignatureText(), len, (jobject)params);
            bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderAddConstructor, &jargs[0]);
            printd(5, "JniExternalProgramData::addConstructorVariant() bb: %p\n", (jobject)bb);

            // add to param list
            jph.add(params);
        } else {
            printd(5, "JniExternalProgramData::addConstructorVariant() %s %s::constructor(%s): " \
                "skipping duplicate variant (len: %d)\n",
                v.getAccessString(), qcls.getName(), v.getSignatureText(), len);
        }

        if (varargs) {
            varargs = false;
            params = getJavaParamList(env, class_loader, v, len, false, false, &qcls);
            continue;
        }

        if (!params || !len || !check_optional_last_param(env, v, params, len)) {
            break;
        }
    }

    return 0;
}

int JniExternalProgramData::addNormalMethodVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v,
        QoreJavaParamHelper& jph) {
    printd(5, "JniExternalProgramData::addNormalMethodVariant() adding Java normal method %s %s::%s(%s)\n",
        qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(), v.getSignatureText());

    bool varargs = v.getCodeFlags() & QCF_USES_EXTRA_ARGS ? true : false;
    bool is_abstract = v.isAbstract();

    // first get the params
    unsigned len;
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs, is_abstract, &qcls);

    QoreString jname;
    if (!strcmp(m.getName(), "getClass")) {
        jname = "getQoreClass";
    }

    // skip methods that would override final methods in parent classes (e.g., wait(), notify(),
    // notifyAll() from java.lang.Object)
    if (jname.empty() && jph.isFinalMethod(m.getName(), params)) {
        printd(5, "JniExternalProgramData::addNormalMethodVariant() %s %s::%s(%s): skipping final parent " \
            "method\n", qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(),
            v.getSignatureText());
        return 0;
    }

    // a variant overriding a method of the Java parent class is added with the signature of the overridden method,
    // so that Java calls it: its Qore types do not give the Java classes (ex: a Qore list for java.util.ArrayList)
    if (!is_abstract && jname.empty()) {
        LocalReference<jobject> overridden = jph.findOverriddenMethod(m.getName(), v, getProgram(), class_loader,
            qcls);
        if (overridden) {
            // the overridden method's parameter types, to skip variants with the same Java parameters below
            LocalReference<jobjectArray> pclasses = env.callObjectMethod(overridden,
                Globals::methodMethodGetParameterTypes, nullptr).as<jobjectArray>();
            LocalReference<jobject> oparams = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);
            for (jsize i = 0, e = env.getArrayLength(pclasses); i < e; ++i) {
                LocalReference<jclass> pcls = env.getObjectArrayElement(pclasses, i).as<jclass>();
                LocalReference<jobject> pdef = get_type_def_from_class(env, pcls);
                jvalue jarg;
                jarg.l = pdef;
                env.callBooleanMethod(oparams, Globals::methodArrayListAdd, &jarg);
            }
            if (!jph.checkVariant(oparams, QMT_NORMAL)) {
                auto binding = std::make_shared<GeneratedBinding>(getProgram(), GeneratedBindingKind::Method,
                    qcls.getProgram());
                binding->cls = &qcls;
                binding->method = &m;
                binding->variant = &v;
                std::vector<jvalue> jargs(5);
                jargs[0].l = bb;
                jargs[1].j = register_generated_binding(std::move(binding));
                jargs[2].j = 0;
                jargs[3].i = qore_jni_get_acc_visibility(v.getAccess());
                jargs[4].l = overridden;
                printd(5, "JniExternalProgramData::addNormalMethodVariant() %s::%s(%s): overriding the Java method\n",
                    qcls.getName(), m.getName(), v.getSignatureText());
                bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                    Globals::methodJavaClassBuilderAddOverridingMethod, &jargs[0]);
                jph.add(oparams);
            }
        }
    }

    while (true) {
        printd(5, "JniExternalProgramData::addNormalMethodVariant() adding Java normal method %s %s::%s(%s) " \
          "len: %d varargs: %d\n", qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(),
          v.getSignatureText(), len, !v.isAbstract() && (v.getCodeFlags() & QCF_USES_EXTRA_ARGS));

        if (!jph.checkVariant(params, QMT_NORMAL)) {
            std::vector<jvalue> jargs(9);
            jargs[0].l = bb;
            // rename methods that are final in java.lang.Object()
            LocalReference<jstring> mname;
            if (!jname.empty()) {
                mname = env.newString(jname.c_str());
            } else {
                mname = env.newString(m.getName());
            }
            jargs[1].l = mname;
            auto binding = std::make_shared<GeneratedBinding>(getProgram(), GeneratedBindingKind::Method,
                qcls.getProgram());
            binding->cls = &qcls;
            binding->method = &m;
            binding->variant = &v;
            jargs[2].j = register_generated_binding(std::move(binding));
            jargs[3].j = 0;
            jargs[4].i = qore_jni_get_acc_visibility(v.getAccess());
            LocalReference<jobject> return_type = getJavaTypeDefinition(env, class_loader, v.getReturnTypeInfo(),
                false, &qcls);
            jargs[5].l = return_type;
            jargs[6].l = params;
            jargs[7].z = v.isAbstract();
            jargs[8].z = !v.isAbstract() && varargs;

            printd(5, "JniExternalProgramData::addNormalMethodVariant() %s %s::%s(%s): adding (len: %d)\n",
                qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(), v.getSignatureText(), len);

            bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderAddNormalMethod, &jargs[0]);
            printd(5, "JniExternalProgramData::addNormalMethodVariant() bb: %p\n", (jobject)bb);

            // add to param list
            jph.add(params);
        } else {
            printd(5, "JniExternalProgramData::addNormalMethodVariant() %s %s::%s(%s): skipping duplicate variant\n",
                qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(), v.getSignatureText());
        }

        if (varargs && !is_abstract) {
            varargs = false;
            params = getJavaParamList(env, class_loader, v, len, false, false, &qcls);
            continue;
        }

        // issue #4570: only add one method per abstract variant
        if (!params || !len || !check_optional_last_param(env, v, params, len) || is_abstract) {
            break;
        }
    }

    return 0;
}

int JniExternalProgramData::addStaticMethodVariant(Env& env, jobject class_loader,
        const QoreClass& qcls, LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v,
        QoreJavaParamHelper& jph) {
    printd(5, "JniExternalProgramData::addStaticMethodVariant() adding Java method static %s %s %s::%s(%s)\n",
        v.getAccessString(), qore_type_get_name(v.getReturnTypeInfo()), qcls.getPath(), m.getName(),
        v.getSignatureText());

    bool varargs = v.getCodeFlags() & QCF_USES_EXTRA_ARGS ? true : false;

    // first get the params
    unsigned len;
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs, false, &qcls);

    while (true) {
        if (!jph.checkVariant(params, QMT_STATIC)) {
            std::vector<jvalue> jargs(9);
            jargs[0].l = bb;
            LocalReference<jstring> mname = env.newString(m.getName());
            jargs[1].l = mname;
            jargs[2].j = getProgram()->getProgramId();
            auto binding = std::make_shared<GeneratedBinding>(getProgram(), GeneratedBindingKind::StaticMethod,
                qcls.getProgram());
            binding->cls = &qcls;
            binding->method = &m;
            binding->variant = &v;
            jargs[3].j = register_generated_binding(std::move(binding));
            jargs[4].j = 0;
            jargs[5].i = qore_jni_get_acc_visibility(v.getAccess());
            LocalReference<jobject> return_type = getJavaTypeDefinition(env, class_loader, v.getReturnTypeInfo(),
                false, &qcls);
            jargs[6].l = return_type;
            jargs[7].l = params;
            jargs[8].z = varargs;

            printd(5, "JniExternalProgramData::addStaticMethodVariant() static %s %s %s::%s(%s): adding (len: %d) " \
                "pgm: %p cpgm: %p varargs: %d\n",
                v.getAccessString(), qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(),
                v.getSignatureText(), len, getProgram(), qore_get_call_program_context(),
                v.getCodeFlags() & QCF_USES_EXTRA_ARGS);
            bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderAddStaticMethod, &jargs[0]);
            printd(5, "JniExternalProgramData::addStaticMethodVariant() bb: %p\n", (jobject)bb);

            // add to param list
            jph.add(params);
        } else {
            printd(5, "JniExternalProgramData::addStaticMethodVariant() static %s %s::%s(%s): skipping duplicate " \
                "variant (len: %d)\n", qore_type_get_name(v.getReturnTypeInfo()), qcls.getName(), m.getName(),
                v.getSignatureText(), len);
        }

        if (varargs) {
            varargs = false;
            params = getJavaParamList(env, class_loader, v, len, false, false, &qcls);
            continue;
        }

        if (!params || !len || !check_optional_last_param(env, v, params, len)) {
            break;
        }
    }

    return 0;
}

int JniExternalProgramData::addStaticMethods(Env& env, jobject class_loader,
        const QoreClass& qcls, const QoreMethod& m, QoreJavaParamHelper& jph, LocalReference<jobject>& bb) {
    QoreExternalFunctionIterator vi(*m.getFunction());
    while (vi.next()) {
        const QoreExternalMethodVariant* v = reinterpret_cast<const QoreExternalMethodVariant*>(vi.getVariant());
        // skip private:internal variants
        if (v->getAccess() > Private) {
            printd(5, "JniExternalProgramData::addStaticMethods() skipping static method: %s::%s(%s)\n",
                qcls.getName(), m.getName(), v->getSignatureText());
            continue;
        }

        printd(5, "JniExternalProgramData::addStaticMethods() %s::%s(%s)\n",
            qcls.getName(), m.getName(), v->getSignatureText());
        assert(m.getMethodType() == MT_Static);
        if (addStaticMethodVariant(env, class_loader, qcls, bb, m, *v, jph)) {
            return -1;
        }
    }
    return 0;
}

int JniExternalProgramData::addMethods(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, jclass parent_class, const QoreClass* qore_parent, strset_t& mset,
        const QoreClass* other_base) {
    // map of static methods already provisioned
    strset_t static_methods;

    const QoreClass& source_class = other_base ? *other_base : qcls;
    {
        QoreMethodIterator i(source_class);
        unsigned constructor_count = 0;
        while (i.next()) {
            const QoreMethod* m = i.getMethod();
            switch (m->getMethodType()) {
                case MT_Constructor: {
                    if (other_base) {
                        break;
                    }
                    QoreExternalFunctionIterator vi(*m->getFunction());
                    QoreJavaParamHelper jph(env, nullptr, parent_class, qore_parent);
                    while (vi.next()) {
                        const QoreExternalMethodVariant* v =
                            reinterpret_cast<const QoreExternalMethodVariant*>(vi.getVariant());
                        // skip private:internal variants
                        if (v->getAccess() > Private) {
                            printd(5, "JniExternalProgramData::addMethods() skipping constructor: %s::%s(%s)\n",
                                source_class.getName(), m->getName(), v->getSignatureText());
                            continue;
                        }

                        printd(5, "JniExternalProgramData::addMethods() constructor: %s::%s(%s)\n",
                            source_class.getName(), m->getName(), v->getSignatureText());

                        if (addConstructorVariant(env, class_loader, source_class, bb, *m, *v, parent_class, jph)) {
                            return -1;
                        }
                        ++constructor_count;
                    }
                    break;
                };

                case MT_Normal: {
                    if (other_base && (mset.find(m->getName()) != mset.end())) {
                        //printd(5, "JniExternalProgramData::addMethods() skipping %s qcls: %s\n", m->getName(), qcls.getName());
                        break;
                    }
                    QoreExternalFunctionIterator vi(*m->getFunction());
                    QoreJavaParamHelper jph(env, nullptr, parent_class, qore_parent);
                    bool set_method = true;
                    while (vi.next()) {
                        const QoreExternalMethodVariant* v =
                            reinterpret_cast<const QoreExternalMethodVariant*>(vi.getVariant());
                        // skip private:internal variants
                        if (v->getAccess() > Private) {
                            printd(5, "JniExternalProgramData::addMethods() skipping normal method: %s::%s(%s)\n",
                                source_class.getName(), m->getName(), v->getSignatureText());
                            continue;
                        }

                        printd(5, "JniExternalProgramData::addMethods() normal method: %s::%s(%s)\n",
                            source_class.getName(), m->getName(), v->getSignatureText());

                        if (addNormalMethodVariant(env, class_loader, qcls, bb, *m, *v, jph)) {
                            return -1;
                        }
                        if (set_method) {
                            set_method = false;
                        }
                    }

                    if (!other_base && !set_method) {
                        assert(mset.find(m->getName()) == mset.end());
                        mset.insert(m->getName());
                        set_method = true;
                    }

                    // find any static method with the same name and process here to ensure that no arguments conflict
                    const QoreMethod* sm = source_class.findStaticMethod(m->getName());
                    if (sm) {
                        if (addStaticMethods(env, class_loader, qcls, *sm, jph, bb)) {
                            return -1;
                        }
                        assert(static_methods.find(sm->getName()) == static_methods.end());
                        static_methods.insert(sm->getName());
                        if (!other_base && !set_method) {
                            assert(mset.find(m->getName()) == mset.end());
                            mset.insert(m->getName());
                        }
                    }
                    break;
                }

                default: {
                    printd(5, "JniExternalProgramData::addMethods() ignoring method %s::%s(...) " \
                        "(all variants)\n", source_class.getName(), m->getName());
                    break;
                }
            }
        }

        // add default constructor if necessary
        if (!other_base && !constructor_count) {
            std::vector<jvalue> jargs(7);
            jargs[0].l = bb;
            jargs[1].l = parent_class;
            jargs[2].j = 0;
            jargs[3].j = 0;
            jargs[4].i = ACC_PUBLIC;
            jargs[5].l = nullptr;
            jargs[6].z = false;

            bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderAddConstructor,
                &jargs[0]);
            printd(5, "JniExternalProgramData::addMethods() %p: %s: bb: %p (default constructor)\n", &qcls,
                qcls.getName(), (jobject)bb);
        }
    }

    QoreStaticMethodIterator i(source_class);
    while (i.next()) {
        const QoreMethod* m = i.getMethod();
        if (static_methods.find(m->getName()) != static_methods.end()) {
            continue;
        }
        if (other_base && (qcls.findMethod(m->getName()) || qcls.findStaticMethod(m->getName()))) {
            continue;
        }

        // pass mname so checkVariant() can detect a conflict with an inherited instance method
        // of the same name+params (Java rejects same name+descriptor regardless of static/non-static)
        QoreJavaParamHelper jph(env, m->getName(), parent_class, qore_parent);
        if (addStaticMethods(env, class_loader, qcls, *m, jph, bb)) {
            return -1;
        }
        if (!other_base) {
            assert(mset.find(m->getName()) == mset.end());
            mset.insert(m->getName());
        }
    }

    return 0;
}

LocalReference<jbyteArray> JniExternalProgramData::generateByteCode(Env& env, jobject class_loader,
        const QoreString& qpath, jstring jname, const char* module, const QoreClass* qcls) {
    printd(5, "JniExternalProgramData::generateByteCode() '%s' pgm: %p qc: %p\n", qpath.c_str(), pgm, qcls);

    // Lock ordering: the total order is "Program parse lock -> codeGenLock -> m" (see JniClassMapLocker).
    //
    // Byte code generation resolves method parameter and return types, which can create Qore classes
    // for referenced Java types (getQoreType() -> findCreateQoreClass*()) while codeGenLock is held;
    // that class-creation path takes the Program parse lock.  The Java->Qore import path takes the
    // parse lock and then codeGenLock (via saveClass()).  To keep a single consistent order and avoid
    // a codeGenLock<->parse-lock ABBA (which the old coarse global lock m used to mask), we acquire
    // this Program's parse lock here, BEFORE codeGenLock.  m is not held across the Java calls below
    // (loadClassWithPtr()/generateByteCodeIntern() call back into module loading via the classloader
    // callback); m is taken only as a leaf inside the nested type-resolution.  The parse lock is
    // per-Program and is safe to hold across a module load; it is re-entrant, so nested generation on
    // the same thread is fine.
    //
    // pgm is the current Program (the classloader callback set the program context before calling us).
    CurrentProgramRuntimeExternalParseContextHelper parse_lock;
    if (!parse_lock) {
        throw BasicException("could not attach to deleted Qore Program during Java byte code generation");
    }

    ExceptionSink xsink;
    if (!qcls) {
        assert(!qpath.empty());
        // the Program is current: the class loader callback entered it, or found it current already and did not
        // enter it, as for a module command on a Program with code pending, which no runtime entry is allowed into
        // (qore#6785); finding the class reads its definitions under the parse lock taken above and runs none of its
        // code
        assert(pgm == ::getProgram());

        qcls = pgm->findClass(qpath.c_str(), &xsink);
        if (xsink) {
            assert(!qcls);
            throw XsinkException(xsink);
        }
    }
    //printd(5, "JniExternalProgramData::generateByteCode() qpath: '%s': qcls: %p\n", qpath.c_str(), qcls);

    if (!qcls) {
        // check if we are looking for a "$Functions" class
        QoreString cname(qpath.c_str());
        qore_offset_t i = cname.rfind("::");
        if (i >= 0) {
            cname.replace(0, i + 2, (const char*)nullptr);
        }
        if (cname == JniImportedFunctionClassName) {
            // ensure exclusive access while creating java classes
            CodeGenLocker al(*this);

            if (i > 0) {
                QoreString ns_path(qpath.c_str(), i);

                // create function class
                return generateFunctionClassIntern(env, class_loader, pgm, jname, module, ns_path.c_str());
            }

            // create function class
            return generateFunctionClassIntern(env, class_loader, pgm, jname, module);
        }
        if (cname == JniImportedConstantClassName) {
            // ensure exclusive access while creating java classes
            CodeGenLocker al(*this);

            if (i > 0) {
                QoreString ns_path(qpath.c_str(), i);

                // create constant class
                return generateConstantClassIntern(env, class_loader, pgm, jname, module, ns_path.c_str());
            }

            // create constant class
            return generateConstantClassIntern(env, class_loader, pgm, jname, module);
        }

        if (!qcls) {
            // get java name for error message
            Env::GetStringUtfChars java_name(env, jname);
            ReferenceHolder<QoreListNode> feature_list(pgm->getFeatureList(), &xsink);
            // the message in UTF-8: the Java name, and the Qore path made from it, are UTF-8
            QoreString desc(QCS_UTF8);
            desc.sprintf("Java class '%s' cannot be generated, because Qore class '%s' cannot be found (loaded " \
                "modules: ", java_name.c_str(), qpath.c_str());
            bool first = true;
            ConstListIterator fi(*feature_list);
            while (fi.next()) {
                QoreValue feature = fi.getValue();
                if (feature.getType() == NT_STRING) {
                    if (!first) {
                        desc.concat(", ");
                    }
                    first = false;
                    jni_append_utf8(desc, feature);
                }
            }
            desc.concat(')');
            jni_throw_class_not_found(*env, desc);
            return nullptr;
        }
    }

    // Hard-fail legacy qore.<X>.<Y> imports of a class that lives INSIDE its owning
    // module's own namespace.
    //
    // Qore module classes whose qpath is under their module's own namespace are emitted
    // under one canonical Java binary name: qoremod.<mod>.<rest> (see
    // getJavaNameForClass()).  Older module-jni also exposed them via qore.<class-path> —
    // that form is still parsed by ClassModInfo for backward compatibility with truly
    // non-module Qore.* classes (e.g. qore.Qore.File, qore.OMQ.$Constants), but accepting
    // it for in-namespace module classes produces two distinct Java Class objects for the
    // same QoreClass.  At runtime the JVM then fails legitimate casts with
    // ClassCastException or rejects subclass / parent override resolution with
    // "loader constraint violation" once the class is needed across loaders.
    //
    // Shadow / injection modules (e.g. QorusFakeApi* parse-time API stubs) are exempt:
    // their classes have a qpath OUTSIDE the module's own namespace and resolve under
    // the runtime qore.<class-path> form at execution time — there is no module-owned
    // qoremod.<shadow-mod>.<rest> Java Class to alias against, so accepting the legacy
    // form does not produce dual Class objects.  See getJavaNameForClass() for the
    // matching emission rule.
    //
    if (qcls->getModuleName() && jname) {
        Env::GetStringUtfChars jn(env, jname);
        bool is_legacy_module = jn[0] == 'q' && jn[1] == 'o' && jn[2] == 'r' && jn[3] == 'e'
            && jn[4] == '.' && strchr(jn.c_str() + 5, '.');
        if (is_legacy_module) {
            // Determine whether the class lives inside its owning module's own namespace.
            // Only those classes are subject to the qoremod.<mod>.<rest> canonicalization;
            // shadow-module classes (qpath outside module namespace) are runtime-aliased
            // and the legacy form is the correct binary name for them.
            bool class_under_module_ns = false;
            QoreProgram* qpgm = qcls->getProgram();
            if (!qpgm) {
                qpgm = pgm;
            }
            const QoreNamespace* mod_ns = nullptr;
            if (!isInjectedModule(qcls->getModuleName())) {
                if (qpgm) {
                    mod_ns = get_module_root_ns(qcls->getModuleName(), qpgm);
                }
                if (!mod_ns) {
                    // same blind spot as in getJavaNameForClass(): a Program-based search cannot
                    // see a module's private dependencies, and without this fallback the guard
                    // against dual binary names is disabled for exactly those classes
                    mod_ns = get_class_module_root_ns(*qcls, qcls->getModuleName());
                }
            }
            // Skip the canonicalization rule when the module's root namespace
            // has multiple module contributors — no single module is the
            // canonical "owner" so there is no unambiguous qoremod.<mod>.*
            // form to canonicalise to.  In that case the legacy `qore.<X>.<Y>`
            // form is the correct binary name for the class.
            if (mod_ns && mod_ns->getModuleCount() > 1) {
                mod_ns = nullptr;
            }
            if (mod_ns) {
                std::string nspath = mod_ns->getPath(true);
                class_under_module_ns = qpath.size() >= nspath.size()
                    && !memcmp(qpath.c_str(), nspath.c_str(), nspath.size());
            }
            if (class_under_module_ns) {
                const char* rest = strchr(jn.c_str() + 5, '.') + 1;
                // the message in UTF-8: the Java name, and the Qore path made from it, are UTF-8
                QoreString desc(QCS_UTF8);
                desc.sprintf("Java class '%s' refers to module-owned class '%s' (module '%s') " \
                    "via the legacy qore.<X>.<Y> binary name; module classes are exposed only under " \
                    "the canonical qoremod.<mod>.<rest> form.  Re-emit / re-import as " \
                    "'qoremod.%s.%s'.",
                    jn.c_str(), qpath.c_str(), qcls->getModuleName(), qcls->getModuleName(), rest);
                jni_throw_class_not_found(*env, desc);
                return nullptr;
            }
        }
    }

    // ensure exclusive access while creating java classes
    CodeGenLocker al(*this);

    //printd(5, "JniExternalProgramData::generateByteCode() qpath: '%s' (%p)\n", qpath.c_str(), qcls);
    LocalReference<jbyteArray> rv = generateByteCodeIntern(env, class_loader, qcls, jname).as<jbyteArray>();
    return rv;
}

static void convert_qore_ns_to_java_pkg(std::string& str) {
    size_t start_pos = 0;
    while ((start_pos = str.find("::", start_pos)) != std::string::npos) {
        str.replace(start_pos, 2, ".");
        ++start_pos;
    }
}

LocalReference<jobject> JniExternalProgramData::loadServiceLoader(Env& env, jclass jcls) {
    std::vector<jvalue> jargs(2);
    jargs[0].l = jcls;
    jargs[1].l = classLoader;
    return env.callStaticObjectMethod(dynamicApi, methodQoreJavaDynamicApiLoadServiceLoader, &jargs[0])
        .release();
}

LocalReference<jstring> JniExternalProgramData::getJavaNameForClass(Env& env, const QoreClass& qc) {
    const char* mod = qc.getModuleName();
    ValueHolder v(qc.getReferencedKeyValue(JNI_CK_JAVA_BIN_NAME), nullptr);
    if (v) {
        assert(v->getType() == NT_STRING);
        QoreStringValueHelper jname_str(*v);
        const char* jname = jname_str->c_str();
        if (!mod || !is_dynamic_qore_bin_name(jname)) {
            printd(5, "JniExternalProgramData::getJavaNameForClass() cls '%s' -> embedded java '%s'\n",
                qc.getName(), jname);
            return env.newString(jname);
        }
    }

    std::string pname = qc.getNamespacePath(true);
    // if it's already a Java class, then return the original Java binary name
    if (pname.rfind("::Jni::", 0) == 0) {
        pname.erase(0, 7);
        convert_qore_ns_to_java_pkg(pname);
    } else {
        if (mod) {
            bool done = false;
#if QORE_VERSION_CODE >= 10013
            if (!strcmp(mod, "python")) {
                const QoreNamespace* ns = qc.getNamespace();
                ValueHolder pm(ns->getReferencedKeyValue("python_module"), nullptr);
                printd(5, "JniExternalProgramData::getJavaNameForClass() pname: '%s' ns: '%s pm: %s\n", pname.c_str(),
                    ns->getPath(true).c_str(), pm ? pm->getFullTypeName() : "n/a");
                if (pm && pm->getType() == NT_STRING) {
                    QoreStringValueHelper pm_str(*pm);
                    pname.assign(pm_str->c_str(), pm_str->size());
                    convert_qore_ns_to_java_pkg(pname);
                    pname += ".";
                    pname += qc.getName();
                    pname.insert(0, "pythonmod.");
                    done = true;
                }
            }
#endif
            if (!done) {
                // Module classes that live INSIDE their owning module's own namespace use
                // the canonical qoremod.<mod>.<rest> shape — there is one canonical Java
                // binary name per QoreClass.  Accepting both qoremod.<mod>.<rest> AND
                // legacy qore.<class-path> for the same class produced two distinct Java
                // Class objects (different defining loaders) and surfaced at runtime as
                // ClassCastException between the two forms, plus
                // LinkageError("loader constraint violation") on subclass / parent override
                // resolution.
                //
                // Shadow / injection modules are different.  A shadow module (e.g. the
                // QorusFakeApi* parse-time API stubs in Qorus) declares classes whose
                // Qore qualified path lies OUTSIDE the module's own namespace — those
                // classes' "real" home is a runtime namespace (e.g. ::OMQ::UserApi::*)
                // that is provided by the host program, NOT by any module, at runtime.
                // The shadow module is loaded only into user-interface programs to give
                // user code parse-time API verification; it is never loaded into the host
                // (qorus-core) at runtime, so qoremod.<shadow-mod>.<rest> does not resolve
                // there.  The only form that resolves at runtime IS the legacy
                // qore.<class-path>: the bytecode loader looks up the class under the
                // runtime qore.* namespace where it really lives.  Detect this case
                // structurally — class qpath does not start with the module's own
                // namespace path — and emit the legacy form so the bytecode generated for
                // user code references the runtime class, not a phantom shadow-module
                // class that cannot exist at run time.  There is no dual-name aliasing
                // problem here because the qoremod.<shadow-mod>.<rest> form is never
                // emitted, so only one Java Class object ever exists per shadow-class.
                QoreProgram* pgm = qc.getProgram();
                if (!pgm) {
                    pgm = getProgram();
                    assert(pgm);
                }
                const QoreNamespace* ns = nullptr;
                if (!isInjectedModule(mod)) {
                    ns = get_module_root_ns(mod, pgm);
                    if (!ns) {
                        // The Program search sees only modules the consumer imported.  A module's
                        // private (non-reexported) dependencies are not imported into the consumer,
                        // and an AOT-compiled module has no Program of its own to fall back on, so
                        // for those classes the search fails in every Program.  Falling through to
                        // the shadow-module branch here would emit the legacy qore.<class-path>
                        // name for a module-owned class: a second binary name for a class that
                        // already has a canonical one, which no loader resolves in the consumer
                        // (NoClassDefFoundError) and which splits the class's identity wherever it
                        // is generated from a class pointer.  Derive the module's root namespace
                        // from the class's own namespace chain instead - a property of the class,
                        // so the name is the same in every context.
                        ns = get_class_module_root_ns(qc, mod);
                    }
                }
                bool class_under_module_ns = false;
                if (ns) {
                    std::string nspath = ns->getPath(true);
                    if (pname.rfind(nspath, 0) == 0) {
                        printd(5, "pname before '%s' (nspath: '%s')\n", pname.c_str(), nspath.c_str());
                        // class lives inside its module's own namespace
                        pname.erase(0, nspath.size());
                        class_under_module_ns = true;
                        printd(5, "pname after '%s' (pgm: %p jpc: %p mod: %s)\n", pname.c_str(), pgm, this, mod);
                    }
                }
                convert_qore_ns_to_java_pkg(pname);

                if (class_under_module_ns) {
                    pname.insert(0, mod);
                    if (strcmp(mod, "python")) {
                        pname.insert(0, "qoremod.");
                    }
                } else {
                    // shadow / runtime-aliased class — emit legacy qore.<class-path>
                    pname.insert(0, "qore");
                }
            }

            printd(5, "JniExternalProgramData::getJavaNameForClass() cls '%s' -> java '%s'\n", qc.getName(),
                pname.c_str());
        } else {
            convert_qore_ns_to_java_pkg(pname);
            pname.insert(0, "qore");
        }
    }
    printd(5, "JniExternalProgramData::getJavaNameForClass() cls '%s' -> java '%s'\n", qc.getName(), pname.c_str());
    return env.newString(pname.c_str());
}

int JniExternalProgramData::addFunctionVariant(Env& env, jobject class_loader, LocalReference<jobject>& bb,
        const QoreExternalFunction& func, const QoreExternalVariant& v, QoreProgram* pgm, QoreJavaParamHelper& jph) {
    printd(5, "JniExternalProgramData::addFunctionVariant() adding Java method static %s %s::%s(%s) " \
        "pgm: %p\n", qore_type_get_name(v.getReturnTypeInfo()), JniImportedFunctionClassName.c_str(), func.getName(),
        v.getSignatureText(), pgm);

    bool varargs = v.getCodeFlags() & QCF_USES_EXTRA_ARGS ? true : false;

    // first get the params
    unsigned len;
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs);

    while (true) {
        if (!jph.checkVariant(params, QMT_STATIC)) {
            std::vector<jvalue> jargs(8);
            jargs[0].l = bb;
            LocalReference<jstring> fname = env.newString(func.getName());
            jargs[1].l = fname;
            jargs[2].j = pgm->getProgramId();
            auto binding = std::make_shared<GeneratedBinding>(pgm, GeneratedBindingKind::Function);
            binding->function = &func;
            binding->variant = &v;
            jargs[3].j = register_generated_binding(std::move(binding));
            jargs[4].j = 0;
            LocalReference<jobject> return_type = getJavaTypeDefinition(env, class_loader, v.getReturnTypeInfo());
            jargs[5].l = (jobject)return_type;
            jargs[6].l = params;
            jargs[7].z = varargs;

            printd(5, "JniExternalProgramData::addFunctionVariant() static public %s %s::%s(%s): adding (len: %d) " \
                "rt: %p\n", qore_type_get_name(v.getReturnTypeInfo()), JniImportedFunctionClassName.c_str(),
                func.getName(), v.getSignatureText(), len, (jobject)return_type);
            bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderAddFunction, &jargs[0]);
            printd(5, "JniExternalProgramData::addFunctionVariant() bb: %p\n", (jobject)bb);

            // add to param list
            jph.add(params);
        } else {
            printd(5, "JniExternalProgramData::addFunctionVariant() static %s %s::%s(%s): skipping duplicate " \
                "variant (len: %d)\n", qore_type_get_name(v.getReturnTypeInfo()), JniImportedFunctionClassName.c_str(),
                func.getName(), v.getSignatureText(), len);
        }

        if (varargs) {
            varargs = false;
            params = getJavaParamList(env, class_loader, v, len, false);
            continue;
        }

        if (!params || !len || !check_optional_last_param(env, v, params, len)) {
            break;
        }
    }

    return 0;
}

int JniExternalProgramData::addFunctions(Env& env, jobject class_loader, const QoreNamespace& ns,
        LocalReference<jobject>& bb, QoreProgram* pgm) {

    QoreNamespaceFunctionIterator i(ns);
    while (i.next()) {
        const QoreExternalFunction& f = i.get();

        QoreJavaParamHelper jph(env, nullptr, nullptr, nullptr);
        QoreExternalFunctionIterator vi(f);
        while (vi.next()) {
            const QoreExternalVariant* v = vi.getVariant();
            if (addFunctionVariant(env, class_loader, bb, f, *v, pgm, jph)) {
                return -1;
            }
        }
    }

    return 0;
}

LocalReference<jbyteArray> JniExternalProgramData::generateFunctionClassIntern(Env& env, jobject class_loader,
        QoreProgram* pgm, jstring jname, const char* module, const char* ns_path) {
    // first get Qore namespace
    const QoreNamespace* ns = nullptr;
    if (module) {
        ns = get_module_root_ns(module, pgm);
        if (ns && ns_path && ns_path[0]) {
            ns = find_ns_path(ns, ns_path);
        }
    }
    if (!ns) {
        ns = ns_path
            ? pgm->findNamespace(ns_path)
            : pgm->getRootNS();
    }

    if (!ns) {
        assert(ns_path);
        // the message in UTF-8: the namespace path comes from the Java name
        QoreString desc(QCS_UTF8);
        desc.sprintf("cannot find Qore namespace '%s' to generate '%s' class for importing functions to Java",
            ns_path, JniImportedFunctionClassName.c_str());
        jni_throw_class_not_found(*env, desc);
        return nullptr;
    }

    assert(jname);

    // NOTE: arg array reused below; 2 args needed below
    jvalue jargs[2];
    jargs[0].l = jname;
    LocalReference<jobject> bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetFunctionConstantClassBuilder, &jargs[0]);
    //printd(5, "JniExternalProgramData::generateFunctionClassIntern() bb: %p ns: '%s'\n", (jobject)bb, ns->getPath(true).c_str());

    // add methods
    if (addFunctions(env, class_loader, *ns, bb, pgm)) {
        //printd(5, "JniExternalProgramData::generateFunctionClassIntern() failed to add members\n");
        return nullptr;
    }

    jargs[0].l = bb;
    jargs[1].l = class_loader;
    LocalReference<jbyteArray> rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetByteCodeFromBuilder, &jargs[0]).as<jbyteArray>();

#ifdef DEBUG_1
    {
        Env::GetStringUtfChars jname_str(env, jname);
        printd(5, "JniExternalProgramData::generateFunctionClassIntern() %s\n", jname_str.c_str());
    }
#endif

    printd(5, "JniExternalProgramData::generateFunctionClassIntern() '%s' rv: %p\n", ns->getName(), (jobject)rv);
    return rv;
}

int JniExternalProgramData::addConstants(Env& env, jobject class_loader, jstring jname, const QoreNamespace& ns,
        LocalReference<jobject>& bb, QoreProgram* pgm) {

    // create ArrayList for static class initializer
    LocalReference<jobject> ilist = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);

#ifdef DEBUG
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    assert(jpc == this);
#endif

    QoreNamespaceConstantIterator i(ns);
    while (i.next()) {
        const QoreExternalConstant& c = i.get();

        const QoreTypeInfo* typeInfo = c.getTypeInfo();
        // cannot create Java fields with type void
        if (typeInfo == nothingTypeInfo || typeInfo == nullTypeInfo) {
            continue;
        }

        LocalReference<jstring> jcname = env.newString(c.getName());

        printd(5, "JniExternalProgramData::addConstants() '%s' type: %s cl: %x pgm: %p jpc cl: %x\n",
            c.getName(), qore_type_get_name(typeInfo),
            env.callIntMethod(class_loader, jni::Globals::methodObjectHashCode, nullptr), pgm,
            env.callIntMethod(getClassLoader(), jni::Globals::methodObjectHashCode, nullptr));
        jvalue jargs[6];
        jargs[0].l = bb;
        jargs[1].l = jcname;
        jargs[2].i = qore_jni_get_acc_visibility(c.getAccess());
        LocalReference<jobject> const_type = getJavaTypeDefinition(env, class_loader, typeInfo, true);
        jargs[3].l = const_type;
        auto binding = std::make_shared<GeneratedBinding>(pgm, GeneratedBindingKind::Constant);
        binding->constant = &c;
        jargs[4].j = register_generated_binding(std::move(binding));
        jargs[5].l = ilist;
        bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderAddStaticField,
            &jargs[0]);
    }

    // create static initializer
    jvalue jargs[4];
    jargs[0].l = bb;
    jargs[1].l = jname;
    jargs[2].j = pgm->getProgramId();
    jargs[3].l = ilist;

    bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderCreateStaticInitializer,
        &jargs[0]);

    return 0;
}

LocalReference<jbyteArray> JniExternalProgramData::generateConstantClassIntern(Env& env, jobject class_loader,
        QoreProgram* pgm, jstring jname, const char* module, const char* ns_path) {
    // first get Qore namespace
    const QoreNamespace* ns = nullptr;
    if (module) {
        ns = get_module_root_ns(module, pgm);
        if (ns && ns_path && ns_path[0]) {
            ns = find_ns_path(ns, ns_path);
        }
    }
    if (!ns) {
        ns = ns_path
            ? pgm->findNamespace(ns_path)
            : pgm->getRootNS();
    }

    if (!ns) {
        assert(ns_path);
        // the message in UTF-8: the namespace path comes from the Java name
        QoreString desc(QCS_UTF8);
        desc.sprintf("cannot find Qore namespace '%s' to generate '%s' class for importing constants to Java",
            ns_path, JniImportedConstantClassName.c_str());
        jni_throw_class_not_found(*env, desc);
        return nullptr;
    }
    assert(jname);

    // NOTE: arg array reused below; 2 args needed below
    jvalue jargs[2];
    jargs[0].l = jname;
    LocalReference<jobject> bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetFunctionConstantClassBuilder, &jargs[0]);
    printd(5, "JniExternalProgramData::generateConstantClassIntern() bb: %p\n", (jobject)bb);

    // add static fields
    if (addConstants(env, class_loader, jname, *ns, bb, pgm)) {
        //printd(5, "JniExternalProgramData::generateConstantClassIntern() failed to add members\n");
        return nullptr;
    }

    jargs[0].l = bb;
    jargs[1].l = class_loader;
    LocalReference<jbyteArray> rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetByteCodeFromBuilder, &jargs[0]).as<jbyteArray>();

#ifdef DEBUG_1
    {
        Env::GetStringUtfChars jname_str(env, jname);
        printd(5, "JniExternalProgramData::generateConstantClassIntern() %s\n", jname_str.c_str());
    }
#endif

    printd(5, "JniExternalProgramData::generateConstantClassIntern() '%s' rv: %p\n", ns->getName(), (jobject)rv);
    return rv;
}

int JniExternalProgramData::addClassConstants(Env& env, jstring jname, const QoreClass& qcls,
        LocalReference<jobject>& bb, QoreProgram* pgm) {
    // create ArrayList for static class initializer
    LocalReference<jobject> ilist = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);

    QoreClassConstantIterator i(qcls);
    while (i.next()) {
        const QoreExternalConstant& c = i.get();

        const QoreTypeInfo* typeInfo = c.getTypeInfo();
        // cannot create Java fields with type void
        if (typeInfo == nothingTypeInfo || typeInfo == nullTypeInfo) {
            continue;
        }

        LocalReference<jstring> jcname = env.newString(c.getName());

        printd(5, "JniExternalProgramData::addClassConstants() '%s' type: %s pgm: %p jpc cl: %x\n",
            c.getName(), qore_type_get_name(typeInfo), pgm,
            env.callIntMethod(getClassLoader(), jni::Globals::methodObjectHashCode, nullptr));
        jvalue jargs[6];
        jargs[0].l = bb;
        jargs[1].l = jcname;
        jargs[2].i = qore_jni_get_acc_visibility(c.getAccess());
        LocalReference<jobject> const_type = getJavaTypeDefinition(env, (jobject)classLoader, typeInfo, true);
        jargs[3].l = const_type;
        auto binding = std::make_shared<GeneratedBinding>(pgm, GeneratedBindingKind::Constant, qcls.getProgram());
        binding->constant = &c;
        jargs[4].j = register_generated_binding(std::move(binding));
        jargs[5].l = ilist;
        bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderAddStaticField,
            &jargs[0]);
    }

    // create static initializer
    jvalue jargs[4];
    jargs[0].l = bb;
    jargs[1].l = jname;
    jargs[2].j = pgm->getProgramId();
    jargs[3].l = ilist;

    bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderCreateStaticInitializer,
        &jargs[0]);

    return 0;
}

// This is the C++ interface to the JavaClassBuilder class in Java (i.e. the Java ByteBuddy interface) for building
// Java classes in bytecode
LocalReference<jbyteArray> JniExternalProgramData::generateByteCodeIntern(Env& env, jobject class_loader,
        const QoreClass* qcls, jstring jname) {
    //printd(5, "JniExternalProgramData::generateByteCodeIntern() '%s'\n", qcls->getName());

    // Mark this class as creation-in-progress before any method-signature or
    // constant materialization.  Both addMethods() and addClassConstants()
    // resolve Qore-backed Java types through getJavaRawClassTypeDefinition(),
    // which re-enters Java class generation for any referenced class that is
    // not already in progress.  A self/cyclic reference (e.g. a DataProvider
    // class constant whose type is the class itself) would otherwise re-enter
    // generation of the same class and throw "<class> is already being
    // created".  With the marker set up front, such references resolve to a
    // forward TypeDescription instead.  The marker is cleared on every exit
    // path (including the early returns and exceptions below) via RAII.
    std::string qpath = qcls->getNamespacePath();
    setCreateInProgress(qpath);
    ON_BLOCK_EXIT_OBJ(*this, &JniExternalProgramData::clearCreateInProgress, qpath);

    // get parent class
    LocalReference<jclass> parent_class;
    jclass parent_ptr = nullptr;
    // the Qore class corresponding to parent_ptr
    const QoreClass* qore_parent = nullptr;
    LocalReference<jobject> parent_type;
    // issue #4337: get list of parent interfaces
    LocalReference<jobject> parent_interfaces;

    // get single base class - Java and Qore's inheritance models are not compatible
    // we can only set a single class for the Java base class
    {
        QoreParentClassIterator ci(*qcls);
        while (ci.next()) {
            if (ci.getAccess() > Private) {
                continue;
            }

            // a Java class is loaded through the class loader's parents; a class defined in Qore is generated
            parent_class = jni_get_java_class_for_jni_class(env, class_loader, ci.getParentClass());
            if (!parent_class) {
                // get internal name for Qore class
                LocalReference<jstring> jname = getJavaNameForClass(env, ci.getParentClass());

                jvalue jargs[2];
                jargs[0].l = jname;
                jargs[1].j = (jlong)&ci.getParentClass();
                // the byte code generator accepts the class pointer for this call only (qore#6854)
                JniIssuedClassHelper issued(&ci.getParentClass(), pgm);
                parent_class = env.callObjectMethod(class_loader, Globals::methodQoreURLClassLoaderLoadClassWithPtr,
                    &jargs[0]).as<jclass>();
            }

            // java.lang.Object, the parent of the Qore classes of Java interfaces, is not a base class: the class
            // extends QoreJavaClassBase, which has the constructors that the generated class calls
            if (env.isSameObject(parent_class, Globals::classObject)) {
                parent_class = nullptr;
                continue;
            }

            // check if parent class is actually a Java interface
            if (env.callBooleanMethod(parent_class, Globals::methodClassIsInterface, nullptr)) {
                if (!parent_interfaces) {
                    parent_interfaces = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);
                }
                jvalue jarg;
#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
                LocalReference<jobject> parent_interface_type = getJavaTypeDefinition(env, class_loader,
                    ci.getTypeInfo(), false, qcls, true);
                jarg.l = parent_interface_type;
#else
                LocalReference<jobject> parent_interface_type = get_type_def_from_class(env,
                    static_cast<jclass>(parent_class));
                jarg.l = parent_interface_type;
#endif
                env.callBooleanMethod(parent_interfaces, Globals::methodArrayListAdd, &jarg);
                parent_class = nullptr;
                printd(5, "JniExternalProgramData::generateByteCodeIntern() cls: '%s' <- interface '%s'\n",
                    qcls->getName(), ci.getParentClass().getName());
            } else {
                parent_ptr = (jclass)parent_class;
                qore_parent = &ci.getParentClass();
#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
                parent_type = getJavaTypeDefinition(env, class_loader, ci.getTypeInfo(), false, qcls, true);
#endif
                printd(5, "JniExternalProgramData::generateByteCodeIntern() cls: '%s' <- '%s'\n",
                    qcls->getName(), ci.getParentClass().getName());
                break;
            }
        }
    }
    if (!parent_ptr) {
        parent_ptr = (jclass)Globals::classQoreJavaClassBase;
        printd(5, "JniExternalProgramData::generateByteCodeIntern() cls: '%s' parent: QoreBaseClass\n",
            qcls->getName());
    }

    auto class_binding = std::make_shared<GeneratedBinding>(getProgram(), GeneratedBindingKind::Class,
        qcls->getProgram());
    class_binding->cls = qcls;
    jlong cptr = register_generated_binding(std::move(class_binding));

    printd(5, "JniExternalProgramData::generateByteCodeIntern() ns path: '%s': %p (abstract: %d) " \
        "jparent: %p (jname: %p)\n", qcls->getNamespacePath(true).c_str(), cptr, qcls->isAbstract(),
        parent_ptr, jname);

    bool has_jname = (bool)jname;

    LocalReference<jstring> njname;
    if (!jname) {
        njname = getJavaNameForClass(env, *qcls);
        //printd(5, "JniExternalProgramData::generateByteCodeIntern() cls '%s' -> java '%s' (generated)\n",
        //    qcls->getName(), pname.c_str());
        jname = njname;
    }

    // Retain programId + qpath as descriptive class identity metadata
    // (see JavaClassBuilder.CLASS_PGM_ID_FIELD / CLASS_PATH_FIELD). Use the
    // class's source program (where it was originally declared, preserved
    // across imports) so consumers in other Programs that import this class
    // resolve to the same canonical entry.  Fall back to the namespace's
    // host program when the class has no recorded source program.
    QoreProgram* id_pgm = qcls->getSourceProgram();
    if (!id_pgm) {
        id_pgm = qcls->getProgram();
    }
    jlong cls_pgm_id = id_pgm ? (jlong)id_pgm->getProgramId() : 0;
    std::string id_qpath = qcls->getNamespacePath(true);
    LocalReference<jstring> cls_path_str = env.newString(id_qpath.c_str());

    LocalReference<jobject> type_params;
#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
    type_params = get_java_type_param_list(env, *qcls);
#endif

    std::vector<jvalue> jargs(10);
    jargs[0].l = jname;
    jargs[1].l = parent_ptr;
    jargs[2].l = parent_type;
    jargs[3].l = parent_interfaces;
    jargs[4].l = type_params;
    jargs[5].z = qcls->isAbstract();
    jargs[6].j = cptr;
    jargs[7].j = cls_pgm_id;
    jargs[8].l = cls_path_str;
    // generating class loader: used to describe the superclass from bytecode via a TypePool
    // (see JavaClassBuilder.getClassBuilder()) so the superclass's method signatures are not
    // eagerly resolved through reflection during subclass generation
    jargs[9].l = class_loader;

    LocalReference<jobject> bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetClassBuilder, &jargs[0]);
    printd(5, "JniExternalProgramData::generateByteCodeIntern() bb: %p\n", (jobject)bb);

    strset_t mset;

    // add methods
    if (addMethods(env, class_loader, *qcls, bb, parent_ptr, qore_parent, mset)) {
        //printd(5, "JniExternalProgramData::generateByteCodeIntern() failed to add members\n");
        return nullptr;
    }

    // add methods inherited from other parent classes
    {
        QoreParentClassIterator ci(*qcls);
        bool skipped = false;
        while (ci.next()) {
            if (ci.getAccess() > Private) {
                continue;
            }
            if (!skipped) {
                skipped = true;
                continue;
            }

            //printd(5, "JniExternalProgramData::generateByteCodeIntern() adding other base '%s'\n",
            //  ci.getParentClass().getName());
            if (addMethods(env, class_loader, *qcls, bb, parent_ptr, qore_parent, mset, &ci.getParentClass())) {
                //printd(5, "JniExternalProgramData::generateByteCodeIntern() failed to add members\n");
                return nullptr;
            }
        }
    }

    // Static constant initializers must materialize object constants through the
    // owning Program's class loader.  When bytecode is generated from a
    // transient consumer Program for a module-owned class, using the consumer
    // Program here creates same-name Java classes in two loaders.
    if (addClassConstants(env, jname, *qcls, bb, id_pgm ? id_pgm : pgm)) {
        return nullptr;
    }

    printd(5, "JniExternalProgramData::generateByteCodeIntern() %s methods added bb: %p; building class with " \
        "cl: %p pgm: %p\n", qcls->getPath(), (jobject)bb,
        env.callIntMethod((jobject)class_loader, jni::Globals::methodObjectHashCode, nullptr), pgm);

    jargs[0].l = bb;
    jargs[1].l = class_loader;

    LocalReference<jbyteArray> rv;
#if 0
    try {
        rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
            Globals::methodJavaClassBuilderGetByteCodeFromBuilder, &jargs[0]).as<jbyteArray>();
    } catch (...) {
        printd(0, "ERR START: %s (in progress: %d)\n", qcls->getPath(), (int)in_progress_set.size());
        for (auto& i : in_progress_set) {
            printd(0, "+ %s (%u)\n", i.first.c_str(), i.second);
        }
        printd(0, "ERR END: %s\n", qcls->getPath());
        throw;
    }
#else
    rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetByteCodeFromBuilder, &jargs[0]).as<jbyteArray>();
#endif

    //LocalReference<jbyteArray> rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
    //    Globals::methodJavaClassBuilderGetByteCodeFromBuilder, &jargs[0]).as<jbyteArray>();

    // save Java bin name in Qore class if necessary
    if (has_jname) {
        Env::GetStringUtfChars jname_str(env, jname);
        if (!qcls->getModuleName() || !is_dynamic_qore_bin_name(jname_str.c_str())) {
            printd(5, "JniExternalProgramData::generateByteCodeIntern() saving class name %p %s: %s\n", qcls,
                qcls->getName(), jname_str.c_str());
            const_cast<QoreClass*>(qcls)->setKeyValueIfNotSet(JNI_CK_JAVA_BIN_NAME, jname_str.c_str());
        }
    }

    printd(5, "JniExternalProgramData::generateByteCodeIntern() %s rv: %p cl: %x (this->cl: %x) pgm: %p\n",
        qcls->getPath(), (jobject)rv,
        env.callIntMethod((jobject)class_loader, jni::Globals::methodObjectHashCode, nullptr),
        env.callIntMethod((jobject)classLoader, jni::Globals::methodObjectHashCode, nullptr), pgm
    );
    return rv;
}

LocalReference<jobject> JniExternalProgramData::getJavaRawClassTypeDefinition(Env& env, jobject class_loader,
        const QoreClass* cls) {
    assert(cls);

    // a Java class
    {
        LocalReference<jclass> jcls = jni_get_java_class_for_jni_class(env, class_loader, *cls);
        if (jcls) {
            return get_type_def_from_class(env, jcls);
        }
    }

    // get internal name for Qore class
    LocalReference<jstring> jname = getJavaNameForClass(env, *cls);

    // Only eagerly generate the referenced Java class when neither it nor any of its ancestors
    // is currently being generated.  Eagerly generating a class whose superclass chain leads back
    // to an in-progress class would re-enter that class's generation via parent-class resolution
    // and fail with "<class> is already being created"; in that case fall through and emit a
    // forward (name-based) type reference, deferring generation until the class is actually loaded.
    if (!isAncestorCreateInProgress(*cls)) {
        printd(5, "JniExternalProgramData::getJavaRawClassTypeDefinition() creating Java class for '%s' (%p)\n",
            cls->getPath(), cls);

        try {
            // NOTE: do NOT acquire the global class-map lock QoreJniClassMap::m here.  This code is
            // reached from generateByteCode(), which holds "parse lock -> codeGenLock"; m is a strict
            // leaf in the total order "parse lock -> codeGenLock -> m" and is taken only for the short
            // class-map critical sections in the nested type resolution below, never held across this
            // Java call.  The class-import path takes the same order (parse lock, then codeGenLock via
            // saveClass()), so there is no codeGenLock<->m or codeGenLock<->parse-lock inversion.
            jvalue jargs[2];
            jargs[0].l = jname;
            jargs[1].j = (jlong)cls;
            // the byte code generator accepts the class pointer for this call only (qore#6854)
            JniIssuedClassHelper issued(cls, pgm);
            LocalReference<jclass> jcls = env.callObjectMethod(class_loader,
                Globals::methodQoreURLClassLoaderLoadClassWithPtr, &jargs[0]).as<jclass>();
            assert(jcls);
            jargs[0].l = jcls;
            LocalReference<jobject> rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderGetTypeDescriptionCls, &jargs[0]);
            printd(5, "JniExternalProgramData::getJavaRawClassTypeDefinition() got Java class for '%s' (%p): %p\n",
                cls->getPath(), cls, *rv);
            return rv.release();
        } catch (jni::Exception& e) {
            e.ignore();
        }
    }

    /** FIXME: we try to create the Java class here and then create a forward reference if it fails

        The problem comes with circular references; valid forward references created here may not be resolvable to the
        Qore type at runtime, because the Qore class may not be accessible - the solution to this is not currently clear

        Ideally we would annotate the byte code with the ptr to the Qore class and then use it when the JVM resolves
        the Java class
    */

    printd(5, "JniExternalProgramData::getJavaRawClassTypeDefinition() this: %p creating forward ref for Java "
        "class for '%s' (%p)\n", this, cls->getPath(), cls);
    jvalue jargs[2];
    jargs[0].l = jname;
#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
    LocalReference<jobject> type_params = get_java_type_param_list(env, *cls);
    jargs[1].l = type_params;
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeDescriptionGenericStr, &jargs[0]);
#else
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeDescriptionStr, &jargs[0]);
#endif
}

LocalReference<jobject> JniExternalProgramData::getJavaTypeDefinition(Env& env, jobject class_loader,
        const QoreTypeInfo* ti, bool no_void, const QoreClass* generic_context, bool generic_position) {
    qore_type_t t = qore_type_get_base_type(ti);
    printd(5, "JniExternalProgramData::getJavaTypeDefinition() looking up type '%s' (%d) cl: %x (no void: %d)\n",
        qore_type_get_name(ti), t, env.callIntMethod(static_cast<jobject>(class_loader),
            jni::Globals::methodObjectHashCode, nullptr), no_void);

#ifdef QORE_JNI_HAVE_GENERIC_CLASS_TYPES
    if (const char* param_name = get_type_param_name_for_context(ti, generic_context)) {
        return get_type_variable_def(env, param_name);
    }

    if (qore_type_is_parameterized(ti)) {
        const QoreClass* cls = type_info_get_return_class(ti);
        if (!cls) {
            printd(5, "JniExternalProgramData::getJavaTypeDefinition() no class mapping for parameterized '%s'\n",
                qore_type_get_name(ti));
            return get_type_def_from_class(env, Globals::classObject);
        }

#ifdef QORE_JNI_TYPE_ARGS_BY_REF
        // the form introduced by qore#6419 copies the type arguments to a caller-owned vector
        type_vec_t type_args_vec;
        const type_vec_t* type_args = qore_type_get_type_arguments(ti, type_args_vec) ? &type_args_vec : nullptr;
#else
        const type_vec_t* type_args = qore_type_get_type_arguments(ti);
#endif
        LocalReference<jobject> jtype_args;
        if (type_args && !type_args->empty()) {
            jtype_args = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);
            for (const QoreTypeInfo* arg_type : *type_args) {
                LocalReference<jobject> jtype = getJavaTypeDefinition(env, class_loader, arg_type, true,
                    generic_context, true);
                jvalue jarg;
                jarg.l = jtype;
                env.callBooleanMethod(jtype_args, Globals::methodArrayListAdd, &jarg);
            }
        }

        LocalReference<jobject> raw_type = getJavaRawClassTypeDefinition(env, class_loader, cls);
        jvalue jargs[2];
        jargs[0].l = raw_type;
        jargs[1].l = jtype_args;
        return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
            Globals::methodJavaClassBuilderGetParameterizedType, &jargs[0]);
    }
#endif

    if (t != NT_OBJECT) {
        if (no_void && (t == NT_NOTHING || t == NT_NULL)) {
            return get_type_def_from_class(env, Globals::classObject);
        }
        if (generic_position) {
            return get_reference_type_def_from_base_type(env, t);
        }
        LocalReference<jclass> jtype(QoreJniClassMap::getPrimitiveType(t));
        return get_type_def_from_class(env, static_cast<jclass>(jtype));
    }

    const QoreClass* cls = type_info_get_return_class(ti);
    if (!cls) {
        printd(5, "JniExternalProgramData::getJavaTypeDefinition() no mapping for '%s'\n", qore_type_get_name(ti));
        return get_type_def_from_class(env, Globals::classObject);
    }

    return getJavaRawClassTypeDefinition(env, class_loader, cls);
}

jobject QoreJniClassMap::getJavaObject(const QoreObject* o) {
    if (!o->isValid()) {
        return nullptr;
    }
    ExceptionSink xsink;
    TryPrivateDataRefHolder<QoreJniPrivateData> jo(o, CID_OBJECT, &xsink);
    if (jo) {
        return jo->makeLocal().release();
    }

    // return a new Java QoreObject with a weak reference to the actual Qore object; the handle also captures the
    // sandbox of the code that hands the object to Java, which governs the calls that Java makes
    std::unique_ptr<JniObjectHandle> handle(new JniObjectHandle(const_cast<QoreObject*>(o)));
    jvalue arg;
    arg.j = reinterpret_cast<jlong>(handle.get());
    Env env;
    jobject rv = env.newObject(Globals::classQoreObject, Globals::ctorQoreObject, &arg).release();
    // the Java object owns the handle now; if it could not be created, the handle releases the weak reference
    handle.release();
    return rv;
}

LocalReference<jclass> QoreJniClassMap::getPrimitiveType(qore_type_t t) {
    qt2jmap_t::const_iterator i = qt2jmap.find(t);
    LocalReference<jclass> rv = i != qt2jmap.end() ? i->second.toLocal() : Globals::classObject.toLocal();
    printd(5, "QoreJniClassMap::getPrimitiveType() type %d -> java cls %p\n", t, (jclass)rv);
    return rv;
}

jobject QoreJniClassMap::getJavaClosure(const ResolvedCallReferenceNode* call) {
    // return a new Java QoreClosure; weak references are not needed, as ResolvedCallReferenceNode objects always
    // implement a weak reference to any captured QoreObject*s; the handle references the closure and captures the
    // sandbox of the code that hands it to Java, which governs every call that Java makes and the release
    std::unique_ptr<JniClosureHandle> handle(new JniClosureHandle(call));
    jvalue arg;
    arg.j = reinterpret_cast<jlong>(handle.get());
    Env env;
    jobject rv = env.newObject(Globals::classQoreClosure, Globals::ctorQoreClosure, &arg).release();
    // the Java object owns the handle now; if it could not be created, the handle releases the closure
    handle.release();
    return rv;
}

jarray QoreJniClassMap::getJavaArray(const QoreListNode* l, jclass cls, JniExternalProgramData* jpc) {
    Env env;

    if (!cls)
        return getJavaArrayIntern(env, l, Globals::classObject, jpc);

    // get component class for array
    LocalReference<jclass> ccls = env.callObjectMethod(cls, Globals::methodClassGetComponentType,
        nullptr).as<jclass>();
    if (!ccls) {
        LocalReference<jstring> clsName = env.callObjectMethod(cls, Globals::methodClassGetCanonicalName,
            nullptr).as<jstring>();
        Env::GetStringUtfChars cname(env, clsName);
        QoreStringMaker str("cannot instantiate array from '%s'", cname.c_str());

        throw BasicException(str.c_str());
    }

    return getJavaArrayIntern(env, l, ccls, jpc);
}

jarray QoreJniClassMap::getJavaArrayIntern(Env& env, const QoreListNode* l, jclass cls, JniExternalProgramData* jpc) {
    Type elementType = Globals::getType(cls);

    LocalReference<jarray> array = Array::getNew(elementType, cls, l->size());

    // now populate array
    for (jsize i = 0; i < static_cast<jsize>(l->size()); ++i) {
        Array::set(array, elementType, cls, i, l->retrieveEntry(i), jpc);
    }

    return array.release();
}

static void exec_java_constructor(const QoreMethod& qmeth, BaseMethod* m, QoreObject* self, const QoreListNode* args,
        q_rt_flags_t rtflags, ExceptionSink* xsink) {
    // the Program of the calling code, for Java's calls into Qore during the call (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::callSite());
    try {
        // issue #3585: set context for external java threads
        //QoreProgram* pgm = qmeth.getClass()->getProgram();
        QoreProgram* pgm = self->getProgram(); //qmeth.getClass()->getProgram();
        JniContextProgramHolder ctx_holder;
        JniExternalProgramData* jpc = JniExternalProgramData::setContext(pgm, ctx_holder);

        /*
        // issue #xxxx: check if class is abstract, if so we need to create a new class and instantiate it
        if (m->isClassAbstract()) {
            printf("abstract %s::%s()\n", qmeth.getName(), qmeth.getClassName());
        }
        */

        self->setPrivate(qmeth.getClass()->getID(), new QoreJniPrivateData(m->newQoreInstance(args, jpc)));
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

static QoreValue exec_java_static_method(const QoreMethod& meth, BaseMethod* m, const QoreListNode* args,
        q_rt_flags_t rtflags, ExceptionSink* xsink) {
    // the jni context of the current Program: Qore has entered the class's source Program for the call of a static
    // method of a class that another Program loaded, and the thread is in the current Program, so its jni data cannot
    // be destroyed during the call; the Program of the class's namespace is not necessarily one that the thread is in
    // (ex: a call reference to a static method of a class imported into another Program) (qore#6485)
    QoreProgram* pgm = nullptr;
    // the Program of the calling code, for Java's calls into Qore during the call (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::callSite());
    // holds the global context Program, if it is the Program of the call (qore#6829)
    JniContextProgramHolder ctx_holder;
    try {
        // issue #3585: set context for external java threads; this attaches the thread to the JVM, which raises an
        // exception when the thread is cancelled or cannot be attached
        JniExternalProgramData::setContext(pgm, ctx_holder);
        return m->invokeStatic(args, pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

static QoreValue exec_java_method(const QoreMethod& meth, BaseMethod* m, QoreObject* self, QoreJniPrivateData* jd,
        const QoreListNode* args, q_rt_flags_t rtflags, ExceptionSink* xsink) {
    // NOTE: Java base classes will have no Qore program context
    // always use the current object's Program, otherwise the wrong ClassLoader will be used
    QoreProgram* pgm = self->getProgram();
    // the Program of the calling code, recorded before the object's Program is entered: Java's calls into Qore
    // during the call are governed by the caller, not by the object's owner (qore#7023); the Java code of an object
    // of another Program that has a sandbox of its own (ex: a tenant's object that a host calls) reaches no Program:
    // it is neither the caller's code nor code that the object's sandbox governs as its caller
    QoreProgram* caller_pgm = JniJavaCallerScope::callSite();
    bool deny = false;
    if (pgm && pgm != caller_pgm) {
        QoreSandboxManagerHelper own(pgm);
        deny = jni_sandbox_restricts_io(own.get());
    }
    JniJavaCallerScope caller(caller_pgm, deny);
    // holds the global context Program, if it is the Program of the call (qore#6829)
    JniContextProgramHolder ctx_holder;
    try {
        // issue #3585: set context for external java threads; this attaches the thread to the JVM, which raises an
        // exception when the thread is cancelled or cannot be attached; attached before the entry below, so that a
        // thread's first attach sets its Java context to the caller's Program
        Env env;
        // issues qore#6183 and qore#6185: the call runs in the object's Program, which is entered first when it is not
        // the current one (see jni_enter_call_program()); its jni data is looked up only then, as the Program's
        // teardown can destroy the data of a Program that the thread is not in; the entry is left when epch is
        // destroyed, also when an exception propagates; a call within the object's Program costs only the comparison
        std::optional<QoreExternalProgramContextHelper> epch;
        if (pgm && pgm != ::getProgram() && jni_enter_call_program(epch, pgm, xsink)) {
            return QoreValue();
        }
        JniExternalProgramData::setContext(env, pgm, ctx_holder);
        return m->invoke(jd->getObject(), args, pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

[[maybe_unused]] static const char* access_str(ClassAccess a) {
    switch (a) {
        case Public: return "public";
        case Private: return "private";
        default: return "private:internal";
    }
}

//! Reads a Java static field for the %Qore static variable mirroring it
static QoreValue jni_static_field_get(const QoreClass& cls, const void* ptr, ExceptionSink* xsink) {
    BaseField* field = const_cast<BaseField*>(static_cast<const BaseField*>(ptr));
    // reading a field can initialize its class, which runs Java code (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::current());
    try {
        return field->getStatic(getProgram(), JniExternalProgramData::compatTypes());
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

//! Writes a Java static field for the %Qore static variable mirroring it
static void jni_static_field_set(const QoreClass& cls, const void* ptr, QoreValue val, ExceptionSink* xsink) {
    BaseField* field = const_cast<BaseField*>(static_cast<const BaseField*>(ptr));
    ValueHolder holder(val, xsink);
    // writing a field can initialize its class, which runs Java code (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::current());
    try {
        field->setStatic(*holder, jni_get_context());
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

//! Releases the field held for a %Qore static variable mirroring a Java static field
static void jni_static_field_del(const QoreClass& cls, const void* ptr) {
    const_cast<BaseField*>(static_cast<const BaseField*>(ptr))->deref();
}

#ifdef _QORE_HAS_BUILTIN_MEMBER_ACCESSORS
//! Reads a Java instance field of the Java object of a %Qore object for the member mirroring it
static QoreValue jni_field_get(QoreObject* self, AbstractPrivateData* data, const char* member, const void* ptr,
        ExceptionSink* xsink) {
    BaseField* field = const_cast<BaseField*>(static_cast<const BaseField*>(ptr));
    // the Program of the calling code, for Java code that the access runs (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::current());
    try {
        return field->get(static_cast<QoreJniPrivateData*>(data)->getObject(), getProgram(),
            JniExternalProgramData::compatTypes());
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

//! Writes a Java instance field of the Java object of a %Qore object for the member mirroring it
static void jni_field_set(QoreObject* self, AbstractPrivateData* data, const char* member, const void* ptr,
        QoreValue val, ExceptionSink* xsink) {
    BaseField* field = const_cast<BaseField*>(static_cast<const BaseField*>(ptr));
    // the Program of the calling code, for Java code that the access runs (qore#7023)
    JniJavaCallerScope caller(JniJavaCallerScope::current());
    try {
        field->set(static_cast<QoreJniPrivateData*>(data)->getObject(), val, jni_get_context());
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

//! Releases the field held for a %Qore member mirroring a Java instance field
static void jni_field_del(const void* ptr) {
    const_cast<BaseField*>(static_cast<const BaseField*>(ptr))->deref();
}
#endif

void QoreJniClassMap::doFields(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    Env env;

    printd(LogLevel, "QoreJniClassMap::doFields() %s qc: %p jc: %p\n", qc.getName(), &qc, jc);

    LocalReference<jobjectArray> fArray = jc->getDeclaredFields();
    for (jsize i = 0, e = env.getArrayLength(fArray); i < e; ++i) {
        // get Field object
        LocalReference<jobject> f = env.getObjectArrayElement(fArray, i);

        SimpleRefHolder<BaseField> field(new BaseField(f, jc));

        QoreString fname;
        field->getName(fname);

        // Skip synthetic fields (Kotlin/compiler-generated)
        if (field->isSynthetic()) {
            printd(LogLevel, "+ skipping synthetic field %s.%s\n", qc.getName(), fname.c_str());
            continue;
        }

        // the name of the Qore member, static variable or constant would be cut at a NUL character, which the JVM
        // allows in a field name (qore#6738)
        jni_check_name(fname.c_str(), fname.size(), "Java field name", "Java field names");

        // the declared type accepts every value read from the field, including no value for a field of a reference
        // type (see getQoreType()), as Qore checks the value read from a field against its declared type
        const QoreTypeInfo* fieldTypeInfo = field->getQoreTypeInfo(*this, pgm);

        if (field->isStatic()) {
            printd(LogLevel, "+ adding static field %s %s %s.%s (%s)\n", access_str(field->getAccess()),
                typeInfoGetName(fieldTypeInfo), qc.getName(), fname.c_str(), field->isFinal() ? "const" : "var");

            if (field->isFinal()) {
                QoreValue v(field->getStatic(pgm, false));
                // a null field is a constant with no value; as its type cannot be derived from its value, it is
                // declared with the field's type, which accepts no value for a field of a reference type, while the
                // type of a constant holding a value is the type of the value
                qc.addBuiltinConstant(fname.c_str(), v, field->getAccess(), v.isNothing() ? fieldTypeInfo : nullptr);
            } else {
                // a mutable Java field is read and written through accessors: a copy of its value taken here would
                // diverge from the field as soon as either side changed it
                field->ref();
                qc.addBuiltinStaticVarWithAccessors(fname.c_str(), field->getAccess(), fieldTypeInfo,
                    jni_static_field_get, jni_static_field_set, *field, jni_static_field_del);
            }
        } else if (!qc.findLocalMember(fname.c_str())) {
            printd(LogLevel, "+ adding field %s %s %s.%s\n", access_str(field->getAccess()),
                typeInfoGetName(fieldTypeInfo), qc.getName(), fname.c_str());
#ifdef _QORE_HAS_BUILTIN_MEMBER_ACCESSORS
            // the value is held only by the Java object: every read and write of the member goes to the Java field,
            // so the member never holds a copy that diverges from the field
            field->ref();
            qc.addBuiltinMemberWithAccessors(fname.c_str(), field->getAccess(), fieldTypeInfo, jni_field_get,
                jni_field_set, *field, jni_field_del);
#else
            qc.addMember(fname.c_str(), field->getAccess(), fieldTypeInfo);
#endif
        }
    }
}

// the Programs that have jni data: a Program's data is registered when it is created and unregistered when it is
// destroyed, which the Program's teardown does without the Program's lock once no other thread is in the Program;
// hasProgramData() tells a thread that is not in a Program whether its data still exists without reading the
// Program's external data, which the teardown can be destroying at the same time
// a plain mutex: the lock is held only for a hash lookup, and an uncontended mutex costs less than a shared one;
// the registry is created on first use and never destroyed, so that a Program's data destroyed during the process's
// static destruction (after this file's statics) can still unregister
namespace {
struct JniProgramDataRegistry {
    std::mutex lock;
    //! the jni data of each Program; the data is valid while it is registered, as its destructor unregisters it first
    std::unordered_multimap<const QoreProgram*, JniExternalProgramData*> pgms;
};

JniProgramDataRegistry& jni_pgm_data() {
    static JniProgramDataRegistry& reg = *new JniProgramDataRegistry;
    return reg;
}
}  // namespace

#ifdef DEBUG
std::mutex& jni_dbg_program_registry_lock() {
    return jni_pgm_data().lock;
}
#endif

// The registry is not used in a child process created with fork(): a thread of the parent can have held its lock, or
// been changing it, at the fork.  No Program can be reached from Java there, so a lookup finds no jni data, and the data
// of a Program is destroyed without being removed from it.
static void jni_pgm_data_register(const QoreProgram* pgm, JniExternalProgramData* jpc) {
    if (Jvm::isForkedChild()) {
        return;
    }
    JniProgramDataRegistry& reg = jni_pgm_data();
    std::lock_guard<std::mutex> guard(reg.lock);
    reg.pgms.emplace(pgm, jpc);
}

static void jni_pgm_data_unregister(const QoreProgram* pgm, JniExternalProgramData* jpc) {
    if (Jvm::isForkedChild()) {
        return;
    }
    JniProgramDataRegistry& reg = jni_pgm_data();
    std::lock_guard<std::mutex> guard(reg.lock);
    auto range = reg.pgms.equal_range(pgm);
    for (auto i = range.first; i != range.second; ++i) {
        if (i->second == jpc) {
            reg.pgms.erase(i);
            break;
        }
    }
}

bool JniExternalProgramData::clearCompilationCache(const QoreProgram* pgm) {
    if (Jvm::isForkedChild()) {
        return false;
    }
    JniProgramDataRegistry& reg = jni_pgm_data();
    // the data is kept alive by the lock, as its destructor, which can run at the same time, unregisters it first
    std::lock_guard<std::mutex> guard(reg.lock);
    auto range = reg.pgms.equal_range(pgm);
    if (range.first == range.second) {
        return false;
    }
    for (auto i = range.first; i != range.second; ++i) {
        i->second->clearCompilationCache();
    }
    return true;
}

#ifdef DEBUG
int64 JniExternalProgramData::getCompilationCacheSizeForTest(const QoreProgram* pgm) {
    if (Jvm::isForkedChild()) {
        return -1;
    }
    JniProgramDataRegistry& reg = jni_pgm_data();
    std::lock_guard<std::mutex> guard(reg.lock);
    auto range = reg.pgms.equal_range(pgm);
    if (range.first == range.second) {
        return -1;
    }
    int64 rv = 0;
    for (auto i = range.first; i != range.second; ++i) {
        rv += (int64)i->second->getCompilationCacheSizeForTest();
    }
    return rv;
}
#endif

bool JniExternalProgramData::hasProgramData(const QoreProgram* pgm) {
    if (Jvm::isForkedChild()) {
        return false;
    }
    JniProgramDataRegistry& reg = jni_pgm_data();
    std::lock_guard<std::mutex> guard(reg.lock);
    return reg.pgms.find(pgm) != reg.pgms.end();
}

void jni_enter_program_for_class_add(std::optional<QoreExternalProgramContextHelper>& epch, QoreProgram* pgm,
        ExceptionSink& xsink) {
    assert(pgm);
    assert(!xsink);
    if (pgm == ::getProgram()) {
        return;
    }
    // the entry keeps a pointer to the caller's sink, which it uses when it is left
    epch.emplace(&xsink, pgm);
    if (xsink) {
        // the entry is left by the caller's unwinding, after the exceptions have been taken from the sink, as when
        // the caller held the entry itself
        throw XsinkException(xsink);
    }
}

// the error of a Program pointer received from Java whose Program has been deleted
static constexpr const char* JAVA_PROGRAM_DELETED = "the Qore Program of the Java class loader has been deleted";
// the error of a Program pointer received from Java that names a Program other than the one of the calling code, when
// the calling code is governed by a sandbox (qore#6845)
static constexpr const char* JAVA_PROGRAM_NOT_GOVERNED = "Java code called by code that a sandbox governs cannot call "
    "into a Qore Program other than the one of the calling code";

bool jni_sandbox_restricts_io(const QoreSandboxManager* sm) {
    // a sandbox that refuses uncontrolled APIs restricts its code as well: Java code that trusted code put into its
    // Program (ex: with define-class) must not get the host's authority (qore#7041)
    return sm && (sm->filesystemRestricts() || sm->networkRestricts() || !sm->getUncontrolledApiPolicy());
}

bool jni_restricting_sandbox_governs() {
    // a sandbox that restricts nothing (ex: one that a host attaches to trusted code only for its interrupts or its
    // resource limits, and that allows uncontrolled APIs) does not make its code sandboxed (qore#7041); resolved for the
    // governing sandbox, through a policy barrier or a system-policy capability, which do not make sandboxed code
    // trusted
    QoreSandboxManagerHelper governing(QoreSandboxManagerHelper::All);
    return jni_sandbox_restricts_io(governing.get());
}

namespace {
//! the record of the %Qore code that made the innermost call from %Qore into Java on the thread (qore#7023)
thread_local JniJavaCallerRecord jni_java_caller;
//! the record that the innermost call from Java into %Qore on the thread cleared: the caller of the Java code that
//! called into %Qore, which supplies the values that the call converts
thread_local JniJavaCallerRecord jni_java_supplier;

//! true once code that a sandbox governs has called Java in the process (qore#7023)
std::atomic<bool> jni_governed_java_used{false};

//! the depth of the attachments of the thread to Qore by the module (the thread is one that Java started)
thread_local int jni_java_thread_depth = 0;
}

JniJavaCallerScope::JniJavaCallerScope(QoreProgram* pgm, bool deny) : prev(jni_java_caller) {
    jni_java_caller.set = true;
    jni_java_caller.deny = deny;
    jni_java_caller.pgm = deny ? nullptr : pgm;
    // the first call into Java by code that a sandbox governs: from then on, a Java thread whose Qore caller is not
    // known can have been handed work by such code (see jni_governed_caller_reaches()); a failure to resolve the
    // sandbox counts as such a call
    if (!jni_governed_java_used.load(std::memory_order_relaxed)) {
        try {
            if (jni_restricting_sandbox_governs()) {
                jni_governed_java_used.store(true, std::memory_order_release);
            }
        } catch (...) {
            jni_governed_java_used.store(true, std::memory_order_release);
        }
    }
}

JniJavaCallerScope::JniJavaCallerScope(ClearTag) : prev(jni_java_caller), prev_supplier(jni_java_supplier),
        clearing(true) {
    jni_java_supplier = jni_java_caller;
    jni_java_caller = JniJavaCallerRecord();
}

JniJavaCallerScope::JniJavaCallerScope(const JniJavaCallerRecord& rec) : JniJavaCallerScope(rec.pgm, rec.deny) {
}

JniJavaCallerScope::~JniJavaCallerScope() {
    jni_java_caller = prev;
    if (clearing) {
        jni_java_supplier = prev_supplier;
    }
}

JniJavaCallerRecord jni_java_caller_owner() {
    if (jni_java_caller.set) {
        return jni_java_caller;
    }
    if (jni_java_supplier.set) {
        return jni_java_supplier;
    }
    // no Qore code is known to have supplied the value (ex: Java code on a thread that Java started)
    JniJavaCallerRecord rv;
    rv.set = true;
    rv.deny = true;
    return rv;
}

void jni_set_java_thread_policy_strict() {
    jni_governed_java_used.store(true, std::memory_order_release);
}

bool jni_java_thread_policy_strict() {
    return jni_governed_java_used.load(std::memory_order_acquire);
}

void jni_java_thread_attached(bool attached) {
    if (attached) {
        ++jni_java_thread_depth;
    } else if (jni_java_thread_depth) {
        --jni_java_thread_depth;
    }
}

namespace {
//! Pushed for a moment to read the innermost frame of the thread's call stack (see JniJavaCallerScope::callSite())
class JniCallSiteProbe : public QoreExternalRuntimeStackLocationHelper {
public:
    //! returns the frame that was innermost when this location was pushed, or nullptr
    DLLLOCAL const QoreStackLocation* previous() const {
        return stack_next;
    }

    DLLLOCAL virtual const std::string& getCallName() const override {
        static const std::string name = "<jni call site>";
        return name;
    }

    DLLLOCAL virtual qore_call_t getCallType() const override {
        return CT_BUILTIN;
    }

    DLLLOCAL virtual const QoreProgramLocation& getLocation() const override {
        static const QoreExternalProgramLocationWrapper loc("<jni call site>", -1, -1);
        return loc.get();
    }
};
}

QoreProgram* JniJavaCallerScope::callSite() {
#ifdef _QORE_HAS_BUILTIN_CALL_SITE_PROGRAM
    // the Program of the code at the call site of the innermost builtin call, also when that code was called directly
    // by another Program, which the frame of the call does not name (qore#7028)
    return qore_get_builtin_call_site_program();
#endif
    // the frame of the builtin call (the Java method, constructor or function of the module) is innermost: libqore
    // records in it the Program of the code that made the call, also when the call has entered the Program of the
    // class (a Java class of another Program), which neither the current Program nor the Program context of the call
    // then names.  For such a call made by code that another Program called directly (ex: a closure), libqore records
    // the Program that called that code instead, as the Program context of the call names it; without the call-site
    // API above, that case is judged with that Program (qore#7028)
    JniCallSiteProbe probe;
    const QoreStackLocation* frame = probe.previous();
    // without a frame or a Program in it, no Program is recorded, which reaches none when a sandbox governs the call
    return frame ? frame->getProgram() : nullptr;
}

//! Returns true if the Program pointer \a ptr that Java gave names a sandboxed Program
/** A Program is sandboxed if its own sandbox restricts (jni_sandbox_restricts_io()), or if the sandbox of the code
    that created or changed it does (a Program that a tenant created), which only a Qore library with
    qore_program_get_creator_sandbox() tells (qore#7023, qore#7028).  A pointer that names no Program with jni data (a
    Program that has been deleted) counts as sandboxed: the code of its classes is not known to be trusted
*/
static bool jni_program_ptr_sandboxed(jlong ptr) {
    // Java does not run in a child process created with fork(); fails closed
    if (Jvm::isForkedChild()) {
        return true;
    }
    QoreProgram* p = reinterpret_cast<QoreProgram*>(ptr);
    {
        JniProgramDataRegistry& reg = jni_pgm_data();
        std::lock_guard<std::mutex> guard(reg.lock);
        if (reg.pgms.find(p) == reg.pgms.end()) {
            return true;
        }
        p->depRef();
    }
    ON_BLOCK_EXIT([p] () { p->depDeref(); });
    QoreSandboxManagerHelper own(p);
    if (own && jni_sandbox_restricts_io(own.get())) {
        return true;
    }
#ifdef _QORE_HAS_PROGRAM_CREATOR_SANDBOX
    // the sandbox of the code that created or changed the Program counts with the same rule as its own: a sandbox of
    // its own that restricts nothing does not hide a restricting one of its creator, and a creator's sandbox that
    // restricts nothing (trusted code with a sandbox only for its interrupts or limits) does not make it sandboxed.
    // Both of the creator's managers are checked, and either can be null: the governing one walks out to a sandboxed
    // caller through a policy barrier or a system-policy capability, where the policy one resolves to the system's
    // policy, so a Program that trusted code created for a sandboxed caller stays sandboxed (qore#7028)
    QoreSandboxManager* policy = nullptr;
    SimpleRefHolder<QoreSandboxManager> creator(qore_program_get_creator_sandbox(p, &policy));
    SimpleRefHolder<QoreSandboxManager> creator_policy(policy);
    return jni_sandbox_restricts_io(*creator) || jni_sandbox_restricts_io(*creator_policy);
#else
    return false;
#endif
}

//! Returns true if no Java code of a sandboxed Program other than \a pgm is on the thread's stack (qore#7023)
/** Java code that a sandboxed Program defined (its classes, defined by its class loader or one derived from it) runs
    with the authority of the code that called it: a host that calls a method of a tenant's object, Java code of the
    host that calls a tenant's listener, a call reference to a static method of a tenant's class.  Such code reaches
    only its own Program, whatever the record of the call.  The stack is walked only once code that a sandbox governs
    has called Java in the process (or with the strict policy of Java threads), as no sandboxed Program has Java code
    of its own before
*/
static bool jni_stack_allows(const QoreProgram* pgm) {
    if (!jni_governed_java_used.load(std::memory_order_acquire)) {
        return true;
    }
    // a native method runs on a thread that is attached to the JVM; no Java context is set for it
    Env env(false);
    LocalReference<jlongArray> ptrs = env.callStaticObjectMethod(Globals::classQoreJavaApi,
        Globals::methodQoreJavaApiGetStackProgramPtrs, nullptr).as<jlongArray>();
    if (!ptrs) {
        // fail closed: the Java stack cannot be attributed
        return false;
    }
    jsize len = env.getArrayLength(ptrs);
    std::vector<jlong> v(len);
    if (len) {
        (*env)->GetLongArrayRegion(ptrs, 0, len, v.data());
        if ((*env)->ExceptionCheck()) {
            throw JavaException();
        }
    }
    for (jlong ptr : v) {
        if (ptr != reinterpret_cast<jlong>(pgm) && jni_program_ptr_sandboxed(ptr)) {
            return false;
        }
    }
    return true;
}

bool jni_governed_caller_reaches(const QoreProgram* pgm) {
    return jni_governed_caller_reaches_intern(pgm) && jni_stack_allows(pgm);
}

bool jni_governed_caller_reaches_intern(const QoreProgram* pgm) {
    // a record that reaches no Program: Java code of a call that must not reach any (qore#7023)
    if (jni_java_caller.set && jni_java_caller.deny) {
        return false;
    }
    if (jni_restricting_sandbox_governs()) {
        // the Program of the code that made the innermost call into Java: a call of a Java method of an object of
        // another Program runs in the object's Program, which libqore also makes the Program context of the call
        // (qore#7023)
        if (jni_java_caller.set) {
            return pgm && pgm == jni_java_caller.pgm;
        }
        // no recorded call: the Program context of the call; the current Program can be the Program of the class of
        // a Java method that the code called
        QoreProgram* code_pgm = qore_get_call_program_context();
        if (!code_pgm) {
            code_pgm = ::getProgram();
        }
        return pgm && pgm == code_pgm;
    }
    // a recorded caller that is a sandboxed Program (ex: the owner of a Java closure that runs in a trusted Program, on
    // a thread that no sandbox governs then) reaches only its own Program, once code that a sandbox governs has called
    // Java in the process, before which no such record can have been made
    if (jni_java_caller.set && jni_java_caller.pgm && pgm != jni_java_caller.pgm
        && jni_governed_java_used.load(std::memory_order_acquire)
        && jni_program_ptr_sandboxed(reinterpret_cast<jlong>(jni_java_caller.pgm))) {
        return false;
    }
    // Qore code that no sandbox governs, and a thread of Qore with no recorded call, reach any Program
    if (jni_java_caller.set || !jni_java_thread_depth) {
        return true;
    }
    // a thread that Java started, which no Qore code calls: its Qore caller is not known, and once code that a sandbox
    // governs has called Java in the process, such code can have created the thread or handed work to it (a pool
    // reuses its threads for the work of any caller), so it reaches no Program by a pointer; the callbacks that Qore
    // code hands to Java carry their own Program and sandbox (qore#7023)
    return !jni_governed_java_used.load(std::memory_order_acquire);
}

void jni_check_governed_caller_reaches(const QoreProgram* pgm) {
    if (!jni_governed_caller_reaches(pgm)) {
        throw BasicException(JAVA_PROGRAM_NOT_GOVERNED);
    }
}

QoreProgram* jni_java_api_fallback_program(const QoreProgram* pgm) {
    QoreProgram* call_pgm = qore_get_call_program_context();
    if (!call_pgm || call_pgm == pgm || !jni_governed_caller_reaches(call_pgm)) {
        return nullptr;
    }
    return call_pgm;
}

JniJavaProgramRef::JniJavaProgramRef(Env& env, ExceptionSink& xsink, jlong ptr, jobject loader) {
    acquire(env, ptr, loader);
    if (!pgm) {
        return;
    }
    try {
        jni_enter_program_for_class_add(epch, pgm, xsink);
    } catch (...) {
        release();
        throw;
    }
}

JniJavaProgramRef::JniJavaProgramRef(Env& env, ExceptionSink& xsink, jlong ptr, bool callback) {
    acquire(env, ptr, nullptr, callback);
    if (!pgm) {
        return;
    }
    try {
        jni_enter_program_for_class_add(epch, pgm, xsink);
    } catch (...) {
        release();
        throw;
    }
}

JniJavaProgramRef::JniJavaProgramRef(Env& env, jlong ptr, jobject loader) {
    acquire(env, ptr, loader);
}

void JniJavaProgramRef::acquire(Env& env, jlong ptr, jobject loader, bool callback) {
#ifdef DEBUG
    // a test can hold the thread here, once it has received the Program pointer from Java and before it uses it
    if (dbg_jni_java_program_ptr_hook_set.load(std::memory_order_acquire)) {
        dbg_jni_run_java_program_ptr_hook();
    }
#endif
    if (!ptr) {
        return;
    }
    // Java does not run in a child process created with fork(), and the registry is not used there
    if (Jvm::isForkedChild()) {
        throw ForkedChildException();
    }
    QoreProgram* p = reinterpret_cast<QoreProgram*>(ptr);
    {
        // a Program is registered while it has jni data, which its teardown destroys before it frees the Program: the
        // reference taken under the lock keeps its memory
        JniProgramDataRegistry& reg = jni_pgm_data();
        std::lock_guard<std::mutex> guard(reg.lock);
        if (reg.pgms.find(p) == reg.pgms.end()) {
            throw BasicException(JAVA_PROGRAM_DELETED);
        }
        p->depRef();
    }
    pgm = p;
    try {
        // the Program found can have been created at the address of the deleted Program of the class loader, whose
        // pointer was cleared before the deleted Program was freed: the pointer must still be the class loader's
        LocalReference<jobject> current;
        if (!loader) {
            current = env.callStaticObjectMethod(Globals::classQoreURLClassLoader,
                Globals::methodQoreURLClassLoaderGetCurrent, nullptr);
            loader = current;
        }
        if (!loader || env.callLongMethod(loader, Globals::methodQoreURLClassLoaderGetPtr, nullptr) != ptr) {
            throw BasicException(JAVA_PROGRAM_DELETED);
        }
        // Java code is not governed by a sandbox: for code that a sandbox governs, the Program must be the Program of
        // the calling code, which the sandbox governs; Java code then cannot run Qore code in another Program, such as
        // a Program of another tenant or the global Java context Program (qore#6845; see
        // jni_governed_caller_reaches())
        if (!jni_governed_caller_reaches(p)) {
            // a callback runs in its own Program: the Program that Java named is not used (see the constructor)
            if (callback) {
                release();
                return;
            }
            throw BasicException(JAVA_PROGRAM_NOT_GOVERNED);
        }
    } catch (...) {
        release();
        throw;
    }
}

void JniJavaProgramRef::release() noexcept {
    // the entry is left before the reference is released, which can be the Program's last
    epch.reset();
    if (pgm) {
        pgm->depDeref();
        pgm = nullptr;
    }
}

int jni_enter_call_program(std::optional<QoreExternalProgramContextHelper>& epch, QoreProgram*& pgm,
        ExceptionSink* xsink) {
    assert(!*xsink);
    if (!pgm || pgm == ::getProgram()) {
        return 0;
    }
    // a Program with no jni data, also one that has been deleted, whose jni data has been destroyed: the call is
    // made in the context of the calling code, and the Program is not entered; the registry is read, not the
    // Program's own data, which its teardown can be destroying
    if (!JniExternalProgramData::hasProgramData(pgm)) {
        pgm = nullptr;
        return 0;
    }
#ifdef DEBUG
    // a test gate that runs code between the check above and the entry
    if (dbg_jni_context_gate_set.load(std::memory_order_acquire)) {
        dbg_jni_run_context_gate(DbgJniGatePoint::Entry);
    }
#endif
    // the Program is entered before its jni data is looked up: the entry holds the Program, whose teardown destroys
    // the data only once no thread is in it; an exception means that the Program cannot be entered (it is being torn
    // down, or another thread is parsing it), or one raised by its entry handler or thread initialization code
    epch.emplace(xsink, pgm);
    if (*xsink) {
        // qore#6485: the Program was deleted after the check above, and its teardown destroyed its jni data: the
        // entry was refused for that reason, so the call is made in the context of the calling code, as for a Program
        // deleted before the call; the data is checked before the entry is released, as a Program that this thread
        // has entered, even in part (a parse by another thread, an exception from its entry handler or thread
        // initialization code), keeps its data until then, so another reason, also a teardown still in progress,
        // keeps the exception
        bool deleted = !JniExternalProgramData::hasProgramData(pgm);
        epch.reset();
        if (deleted) {
            xsink->clear();
            pgm = nullptr;
            return 0;
        }
        return -1;
    }
    // the data exists for the whole call: the teardown destroys it only after it has refused new entries (an entry
    // made after the check above, once the teardown had started, has failed) and no thread is in the Program
    return 0;
}

JniExternalProgramData::JniExternalProgramData(QoreNamespace* jni, QoreProgram* pgm) : pgm(pgm), jni(jni) {
    assert(jni);
    Env env(false);

    assert(pgm);

    printd(5, "JniExternalProgramData::JniExternalProgramData() new root Qore pgm, bootstrap: %d\n",
        Globals::bootstrap);

    static bool once = false;

    if (!once && Globals::bootstrap) {
        once = true;
        borrowedClassLoader = true;
        classLoader = GlobalReference<jobject>((jobject)Globals::syscl);
    } else {
        // set up QoreURLClassLoader constructor args
        std::vector<jvalue> jargs(2);
        jargs[0].j = reinterpret_cast<long>(pgm);
        jargs[1].l = Globals::syscl;

        // create our custom classloader
        classLoader = env.newObject(Globals::classQoreURLClassLoader, Globals::ctorQoreURLClassLoader,
            &jargs[0]).makeGlobal();
    }

    // the constructor can fail once the class loader exists: then the destructor does not run, so the class loader
    // is released here (qore#6779)
    try {
        initDynamicApi(env);

        // setup classpath
        TempString classpath(SystemEnvironment::get("QORE_JNI_CLASSPATH"));
        if (classpath) {
            // the variable is in the default encoding; an error is thrown to the code that creates the data, which
            // fails (qore#6778, qore#6779)
            addClasspath(env, env.newPathString(QoreString(classpath->c_str(), QCS_DEFAULT)), "QORE_JNI_CLASSPATH",
                nullptr);
        }
        jni_pgm_data_register(pgm, this);
    } catch (JavaException& e) {
        // the Java exception stays pending for the caller; no JNI call may be made while it is pending
        LocalReference<jthrowable> je(e.save());
        releaseFailedClassLoader(env);
        e.restore(je);
        throw;
    } catch (...) {
        releaseFailedClassLoader(env);
        throw;
    }
}

void JniExternalProgramData::releaseFailedClassLoader(Env& env) noexcept {
    if (borrowedClassLoader) {
        // Globals::syscl keeps its reference; the system class loader is not taken again for the jni data of another
        // root Program, as the classes of the dynamic API may already be defined in it
        classLoader.release();
        return;
    }
    // the class loader refers to the Program, which has no jni data, and its constructor made it the context class
    // loader of the thread: neither may be used any more
    try {
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderClearProgramPtr, nullptr);
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderClearContext, nullptr);
    } catch (jni::Exception& e) {
        // nothing more can be done; the error of the constructor is the one reported
        e.ignore();
    }
}

JniExternalProgramData::JniExternalProgramData(const JniExternalProgramData& parent, Env& env, QoreProgram* pgm) :
        pgm(pgm),
        classLoader(nullptr),
        compat_types_override(parent.compat_types_override.load(std::memory_order_relaxed)) {
    // create the classLoader and set the parent
    {
        jvalue jargs[2];
        jargs[0].j = (jlong)pgm;
        jargs[1].l = parent.classLoader;
        classLoader = env.newObject(Globals::classQoreURLClassLoader, Globals::ctorQoreURLClassLoader,
            &jargs[0]).makeGlobal();
    }

    // the class loader has the pointer of the Program, which gets no jni data if this fails: its pointer is cleared,
    // as it would never be cleared otherwise, and would name the Program once it has been deleted
    try {
        initDynamicApi(env);

        // copy the parent's class map to this one; another thread can add a class to the parent's map at the same
        // time (the writers hold the class-map lock), and the copy for a child Program can run concurrently with them
        {
            JniClassMapLocker al;
            jcmap = parent.jcmap;
        }

#if QORE_VERSION_CODE >= 10013
        ProgramRuntimeExternalParseContextHelper pch(pgm);
#endif

        // find Jni namespace in new Program if present
        jni = pgm->findNamespace("Jni");
        if (!jni) {
            jni = qjcm.copyProgramJniNs();
            pgm->getRootNS()->addNamespace(jni);
        }
        jni_pgm_data_register(pgm, this);
    } catch (JavaException& e) {
        // the Java exception stays pending for the caller; no JNI call may be made while it is pending
        LocalReference<jthrowable> je(e.save());
        releaseFailedClassLoader(env);
        e.restore(je);
        throw;
    } catch (...) {
        releaseFailedClassLoader(env);
        throw;
    }
}

AbstractQoreProgramExternalData* JniExternalProgramData::copy(QoreProgram* pgm) const noexcept {
    // qore#6787: libqore offers no way to report an error here, and the constructor of the child Program, from which
    // this is called, does not catch C++ exceptions; until it does (qoretechnologies/qore#6786), a failure is
    // reported, and the child Program gets no jni data
    try {
#ifdef DEBUG
        // a test fail point: the thread cannot be attached to the JVM
        if (dbg_jni_fail_at(DbgJniFailPoint::ProgramCopy)) {
            throw UnableToAttachException(JNI_ERR);
        }
#endif
        // issue #3862: ensure that the thread is registered for this call
        Env env;
        return new JniExternalProgramData(*this, env, pgm);
    } catch (...) {
        jni_report_current_exception("copying the jni data of a Program for a child Program");
    }
    return nullptr;
}

JniExternalProgramData::~JniExternalProgramData() {
#ifdef DEBUG
    Globals::dbgContextMarker("jni-dbg: the jni data of a Program is destroyed\n");
#endif
    jni_pgm_data_unregister(pgm, this);
    // NOTE: the module root namespace cache is NOT purged here.  It is keyed by the Program that
    // owns each class (qc.getProgram()), which for a module class is the module's own Program and
    // need not have a JniExternalProgramData at all, so this destructor cannot see every entry.
    // It is purged from the program cleanup callback registered in jni_module_init() instead,
    // which libqore calls for every Program before its namespace data is cleared.

    // NOTE: canonical loader cache invalidation is intentionally NOT performed here.  With the
    // cross-program-import strong-ref activation in qore_class_private (imported
    // classes hold a strong ref on their source Program via spgm + programRefSelf),
    // a JEPD destructor only fires once nothing references its program's classes.
    // The canonical_loader_cache holds an independent NewGlobalRef on the
    // classloader (see cacheCanonicalLoader), which keeps the Java classloader
    // object alive even after the JEPD is destroyed; future lookups for the same
    // class name return that loader and load classes from it without ever calling
    // back into the dead program (the classes' bytecode stops being usable, but
    // the JVM won't observe that until something tries to access an spgm-resolved
    // entry — which requires the program to still be alive, contradicted by our
    // strong-ref invariant).  Cache entries thus leak harmlessly until process
    // exit; they would only be reachable from new consumer Programs, which would
    // re-resolve through getCanonicalLoader and create fresh entries.

    // NOTE: any exception thrown here will be printed out to stderr
    ExceptionSink xsink;
    // the class loader's pointer to the Program is cleared on a cancelled thread too: Env's constructor raised the
    // pending cancellation, so the loader kept a pointer to the destroyed Program
    QoreCancelDeferralHelper cancel_deferral;
    try {
        Env env;
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderClearProgramPtr, nullptr);
    } catch (UnableToAttachException& e) {
        // ignore error - raised when destructions is run after the JVM has shut down
    } catch (AbstractException& e) {
        e.convert(&xsink);
    } catch (std::exception& e) {
        // the destructor is implicitly noexcept: no exception may leave it (qore#6773)
        xsink.raiseException("JNI-ERROR", "error releasing the class loader of a Program: %s", e.what());
    } catch (...) {
        xsink.raiseException("JNI-ERROR", "unknown error releasing the class loader of a Program");
    }

    // delete fake "$" classes
    for (auto& i : fake_cls_map) {
        delete i.second;
    }
    if (borrowedClassLoader) {
        // Globals::syscl owns the reference
        classLoader.release();
    } else {
        classLoader = nullptr;
    }
}

// defines a class built in to the module with the given class loader and returns it
static LocalReference<jclass> define_built_in_class(Env& env, jobject class_loader, const char* name,
        const unsigned char* byte_code, unsigned len) {
    LocalReference<jstring> jname = env.newString(name);

    // make byte array
    LocalReference<jbyteArray> jbyte_code = env.newByteArray(len).as<jbyteArray>();
    for (jsize i = 0; (unsigned)i < len; ++i) {
        env.setByteArrayElement(jbyte_code, i, byte_code[i]);
    }

    std::vector<jvalue> jargs(4);
    jargs[0].l = jname;
    jargs[1].l = jbyte_code;
    jargs[2].i = 0;
    jargs[3].i = len;

    printd(5, "define_built_in_class() name: %s bc: %p len: %d\n", name, (jobject)jbyte_code, len);
    return env.callObjectMethod(class_loader, Globals::methodQoreURLClassLoaderDefineResolveClass, &jargs[0])
        .as<jclass>();
}

void JniExternalProgramData::initDynamicApi(Env& env) {
    // define the QoreJavaDynamicApi class using our new classloader
    dynamicApi = define_built_in_class(env, classLoader, "org.qore.jni.QoreJavaDynamicApi",
        java_org_qore_jni_QoreJavaDynamicApi_class, java_org_qore_jni_QoreJavaDynamicApi_class_len).makeGlobal();
    // its nested class must be defined by the same loader: a class loader delegates to its parent first, which
    // would define the class from the module's built-in classes in another runtime package, where the private
    // nested class is not accessible to QoreJavaDynamicApi, which then fails to initialize
    define_built_in_class(env, classLoader, "org.qore.jni.QoreJavaDynamicApi$AccessibleMethods",
        java_org_qore_jni_QoreJavaDynamicApi_AccessibleMethods_class,
        java_org_qore_jni_QoreJavaDynamicApi_AccessibleMethods_class_len);
    methodQoreJavaDynamicApiNewInstance = env.getStaticMethod(dynamicApi, "newInstance",
        "(Ljava/lang/reflect/Constructor;[Ljava/lang/Object;)Ljava/lang/Object;");
    methodQoreJavaDynamicApiInvokeMethod = env.getStaticMethod(dynamicApi, "invokeMethod",
        "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    methodQoreJavaDynamicApiInvokeMethodNonvirtual = env.getStaticMethod(dynamicApi, "invokeMethodNonvirtual",
        "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    methodQoreJavaDynamicApiGetField = env.getStaticMethod(dynamicApi, "getField",
        "(Ljava/lang/reflect/Field;Ljava/lang/Object;)Ljava/lang/Object;");
    methodQoreJavaDynamicApiLoadServiceLoader = env.getStaticMethod(dynamicApi, "loadServiceLoader",
        "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;");
    methodQoreJavaDynamicApiGetConnection = env.getStaticMethod(dynamicApi, "getConnection",
        "(Ljava/lang/String;Ljava/util/Properties;)Ljava/sql/Connection;");

    printd(LogLevel, "JniExternalProgramData::JniExternalProgramData(): this: %p: dynamic API created: %p " \
        "classloader: %p QoreJavaClassBase: %p\n", this, getDynamicApi(), getClassLoader(),
        (jclass)Globals::classQoreJavaClassBase);
}

//! Throws the error of a classpath that a Java exception kept from being added (qore#6779)
/** The pending Java exception is described as text, with no Program context: the classpath of the
    \c QORE_JNI_CLASSPATH environment variable is added while the jni data of a Program is created, before it can
    be found, so the Java exception cannot be converted to a %Qore exception with its Java object there (qore#6778)

    @param env the JNI environment
    @param path the classpath
    @param source the input that the classpath comes from, for the error
    @param e the Java exception, which is cleared

    @throw JNI-CLASSPATH-ERROR always
*/
static void jni_throw_classpath_error(Env& env, jstring path, const char* source, JavaException& e) {
    SimpleRefHolder<QoreStringNode> jdesc(e.toString());
    // the classpath as Java has it, which is the string that was checked and used
    Env::GetStringUtfChars cp(env, path);
    std::string desc(cp.c_str(), cp.size());
    throw QoreJniException("JNI-CLASSPATH-ERROR", "%s: cannot add classpath '%s': %s", source,
        jni_utf8_to_default(desc.c_str()).c_str(), jni_utf8_to_default(jdesc->c_str()).c_str());
}

void JniExternalProgramData::addClasspath(Env& env, jstring path, const char* source, jobject checked) {
    jvalue jarg;
    // a checked classpath is added as it was checked, with no second resolution of the string (qore#6818)
    jarg.l = checked ? checked : path;
#ifdef DEBUG
    // a test fail point: the class loader raises a Java exception (an IllegalArgumentException for a null classpath)
    if (dbg_jni_fail_at(DbgJniFailPoint::Classpath)) {
        jarg.l = nullptr;
    }
#endif
    try {
        env.callVoidMethod(classLoader, checked ? Globals::methodQoreURLClassLoaderAddResolvedPath
            : Globals::methodQoreURLClassLoaderAddPath, &jarg);
    } catch (JavaException& e) {
        jni_throw_classpath_error(env, path, source, e);
    }
}

void JniExternalProgramData::addParentClasspath(Env& env, jstring path, const char* source) {
    jvalue jarg;
    jarg.l = path;
#ifdef DEBUG
    // a test fail point: the class loader raises a Java exception (an IllegalArgumentException for a null classpath)
    if (dbg_jni_fail_at(DbgJniFailPoint::Classpath)) {
        jarg.l = nullptr;
    }
#endif
    // with no parent QoreURLClassLoader (ex: the bootstrap Program), the classpath is added to this class loader
    try {
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderAddParentPath, &jarg);
    } catch (JavaException& e) {
        jni_throw_classpath_error(env, path, source, e);
    }
}

thread_local bool JniPendingContextHelper::pending = false;

void JniExternalProgramData::setContext(Env& env) {
    QoreProgram* pgm = nullptr;
    // the context keeps a reference to the class loader, not to the Program, which is held only while it is set
    JniContextProgramHolder ctx_holder;
    setContext(env, pgm, ctx_holder);
}

void JniExternalProgramData::setAttachContext(Env& env) {
    // qore#6799: the global Java context Program is deleted when the module is shut down
    // (Globals::clearGlobalContext()), and the deletion of its jni data attaches the thread to the JVM to release the
    // class loader; on a thread that was never attached (ex: the main thread, when all jni work ran in other
    // threads), the context of the new attachment was the global context Program, which no longer existed and was
    // created again, and never deleted; during the shutdown, a thread in no Program with jni data gets no Java context
    // jni_get_context_or_global() creates no global context Program once the shutdown has started, and returns
    // nullptr instead, also if the shutdown starts while the context is looked up
    QoreProgram* pgm = nullptr;
    // holds the global context Program until its class loader is set as the context (qore#6829)
    JniContextProgramHolder ctx_holder;
    if (!jni_get_context_or_global(pgm, ctx_holder)) {
        return;
    }
    // the Program found here is passed on, so that setContext() takes it directly
    setContext(env, pgm, ctx_holder);
}

JniExternalProgramData* JniExternalProgramData::setContext(Env& env, QoreProgram*& pgm,
        JniContextProgramHolder& holder) {
    // issue #3199: no program is available when initializing the jni module from the command line
    // issue #3153: no context is available when called from a static method
    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm, holder);
#ifdef DEBUG
    // a test gate that runs code between the lookup of the context and its use
    if (dbg_jni_context_gate_set.load(std::memory_order_acquire)) {
        dbg_jni_run_context_gate(DbgJniGatePoint::Lookup);
    }
#endif
    // set classloader context in new thread
    env.callVoidMethod(jpc->classLoader, Globals::methodQoreURLClassLoaderSetContext, nullptr);
    return jpc;
}

bool JniExternalProgramData::compatTypes() {
    // issue #3199: no program is available when initializing the jni module from the command line
    // issue #3153: no context is available when called from a static method
    JniContextProgramHolder ctx_holder;
    JniExternalProgramData* jpc = jni_get_context_unconditional(ctx_holder);
    return jpc->getCompatTypes();
}

JniExternalProgramData* JniExternalProgramData::getCreateJniProgramData(QoreProgram* pgm) {
#ifdef DEBUG
    // a test fail point: the jni data cannot be created, as for a Program that is being deleted (below)
    if (dbg_jni_fail_at(DbgJniFailPoint::ProgramData)) {
        throw BasicException("dbg_jni_fail_point(): the jni context of the Program cannot be created");
    }
#endif
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    //printd(5, "parse-cmd '%s' jpc: %p jnins: %p\n", arg.c_str(), jpc, jpc ? jpc->getJniNamespace() : nullptr);
    if (!jpc) {
#ifdef DEBUG
        // a test gate that runs code between the check above and taking the parse lock below
        if (dbg_jni_context_gate_set.load(std::memory_order_acquire)) {
            dbg_jni_run_context_gate(DbgJniGatePoint::Create);
        }
#endif
#if QORE_VERSION_CODE >= 10013
        ProgramRuntimeExternalParseContextHelper pch(pgm);
        // the Program is being deleted: its parse lock could not be taken, so nothing may be added to it
        if (!pch) {
            throw BasicException("the Program is being deleted; its jni context cannot be created");
        }
        // another thread can have created the data after the check above, while this thread waited for the parse
        // lock: the jni APIs create it on demand, so two threads of a Program without jni data can try to create it
        // at the same time (qore#6801)
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        if (jpc) {
            return jpc;
        }
#endif
        QoreNamespace* jnins = pgm->findNamespace("::Jni");
        if (!jnins) {
            jnins = qjcm.copyProgramJniNs();
            pgm->getRootNS()->addNamespace(jnins);
        }
        jpc = new JniExternalProgramData(jnins, pgm);
        pgm->setExternalData("jni", jpc);
        pgm->addFeature(QORE_JNI_MODULE_NAME);
    }

    return jpc;
}

LocalReference<jclass> JniExternalProgramData::getClassForValue(const QoreObject* o) {
    if (!o->isValid()) {
        return nullptr;
    }

    Env env;

    ExceptionSink xsink;
    TryPrivateDataRefHolder<QoreJniPrivateData> jo(o, CID_OBJECT, &xsink);
    if (jo) {
        return env.getObjectClass(jo->getObject()).release();
    }

    return getJavaClassForQoreClass(env, o->getClass());
}

LocalReference<jclass> JniExternalProgramData::getJavaClassForQoreClass(Env& env, const QoreClass* qc) {
    // Lock ordering: "Program parse lock -> codeGenLock -> m" (see generateByteCode()/JniClassMapLocker).
    // loadClassWithPtr() below drives byte code generation, which resolves referenced types (taking
    // the parse lock and the leaf m) and can call back into module loading via the classloader
    // callback.  Holding codeGenLock across that Java call would create a codeGenLock->parse-lock /
    // codeGenLock->module-load edge that the old coarse global lock m used to mask, reintroducing an
    // ABBA.  So codeGenLock is taken only for the q2jmap read and the store; the Java call runs with
    // NO jni lock held.  A double-checked insert handles a concurrent generation of the same class
    // (the JVM serializes class definition per name, so both threads produce the same class).
    std::string cls_hash = get_class_hash(*qc);
    {
        CodeGenLocker al(*this);
        q2jmap_t::iterator i = q2jmap.find(cls_hash);
        if (i != q2jmap.end()) {
            return i->second.toLocal();
        }
    }

    // generate the Java class with no jni lock held
    LocalReference<jstring> jname = getJavaNameForClass(env, *qc);
    jvalue jargs[2];
    jargs[0].l = jname;
    jargs[1].j = (long)qc;
    // the byte code generator accepts the class pointer for this call only (qore#6854)
    JniIssuedClassHelper issued(qc, pgm);
    LocalReference<jclass> jcls = env.callObjectMethod(classLoader,
        Globals::methodQoreURLClassLoaderLoadClassWithPtr, &jargs[0]).as<jclass>();
    assert(jcls);

    // store the generated class, double-checking for a concurrent insert
    CodeGenLocker al(*this);
#ifdef DEBUG
    // a test gate that runs code while code generation holds its lock, before the class is stored (qore#6855)
    if (dbg_jni_context_gate_set.load(std::memory_order_acquire)) {
        dbg_jni_run_context_gate(DbgJniGatePoint::CodeGenSave);
    }
#endif
    q2jmap_t::iterator i = q2jmap.lower_bound(cls_hash);
    if (i == q2jmap.end() || i->first != cls_hash) {
        i = q2jmap.insert(i, q2jmap_t::value_type(cls_hash, jcls.makeGlobal()));
        //printd(5, "JniExternalProgramData::getJavaClassForQoreClass() generated class for '%s': %p\n",
        //  qc->getName(), (jclass)i->second);
    }

    return i->second.toLocal();
}

LocalReference<jobject> JniExternalProgramData::getJavaObject(const QoreObject* o) {
    if (!o->isValid()) {
        return nullptr;
    }
    ExceptionSink xsink;
    TryPrivateDataRefHolder<QoreJniPrivateData> jo(o, CID_OBJECT, &xsink);
    if (jo) {
        return jo->makeLocal();
    }

    Env env;
    LocalReference<jclass> jcls = getJavaClassForQoreClass(env, o->getSurfaceClass());
    // return a new Java object with a weak reference to the actual Qore object
    jmethodID ctor = env.getMethod(jcls, "<init>", "(Lorg/qore/jni/QoreJavaObjectPtr;)V");
    // the handle makes the weak reference to the object and captures the sandbox of the code that hands the object to
    // Java, which governs the calls that Java makes
    std::unique_ptr<JniObjectHandle> handle(new JniObjectHandle(const_cast<QoreObject*>(o)));
    jvalue arg;
    arg.j = reinterpret_cast<jlong>(handle.get());
    LocalReference<jobject> jarg = env.newObject(Globals::classQoreJavaObjectPtr, Globals::ctorQoreJavaObjectPtr,
        &arg);

    arg.l = jarg;
    LocalReference<jobject> rv = env.newObject(jcls, ctor, &arg);
    // the Java object owns the handle now; if it could not be created, the handle releases the weak reference
    handle.release();
    return rv;
}

bool JniExternalProgramData::addInjectedModule(const char* modstr) {
    std::string mod(modstr);
    AutoLocker al(injected_module_lock);

    strset_t::iterator i = injected_module_set.lower_bound(mod);
    if (i == injected_module_set.end() || (*i) != mod) {
        injected_module_set.insert(i, mod);
        printd(5, "JniExternalProgramData::addInjectedModule() this: %p mod: '%s'\n", this, modstr);
        return true;
    }
    return false;
}

bool JniExternalProgramData::isInjectedModule(const char* mod) const {
    AutoLocker al(injected_module_lock);
    bool rv = injected_module_set.find(mod) != injected_module_set.end();
    printd(5, "JniExternalProgramData::isInjectedModule() this: %p find '%s': %d (size: %d)\n", this, mod, rv,
        (int)injected_module_set.size());
    return rv;
}

// count of JniExternalProgramData destructions since last GC hint
static std::atomic<int> jni_pgm_deref_count{0};

void JniExternalProgramData::doDeref() {
    // no C++ exception may leave this function (qore#6773): AbstractQoreProgramExternalData::doDeref() has no
    // exception sink and does not document that it may throw; the teardown of the Program catches an
    // AbstractException thrown as an object, not as a pointer (qore_program_private::del()), but other callers of
    // doDeref() do not, so
    // every exception raised here is caught and converted to a Qore exception in this local sink, whose destructor
    // reports it as an unhandled exception on stderr when the function returns
    ExceptionSink xsink;
    // the callback is released before the object is deleted, as it is a member; releasing it can run Qore code, for
    // example the destructor of an object that only the callback holds, which can raise a Qore exception; should that
    // code set a new callback for this Program, which can still find its jni data, that one is released in turn
    // the member is taken under its lock, which is released before the callback is, as for setSaveObjectCallback()
    // (qore#6790)
    while (true) {
        ResolvedCallReferenceNode* callback;
        {
            SaveObjectCallbackLocker al(save_object_callback_lock);
            callback = save_object_callback;
            save_object_callback = nullptr;
        }
        if (!callback) {
            break;
        }
        try {
            callback->deref(&xsink);
        } catch (AbstractException& e) {
            e.convert(&xsink);
        } catch (std::exception& e) {
            xsink.raiseException("JNI-ERROR", "error releasing the save object callback of a Program: %s",
                e.what());
        } catch (...) {
            xsink.raiseException("JNI-ERROR", "unknown error releasing the save object callback of a Program");
        }
    }
    // the destructor is implicitly noexcept and catches and reports every exception itself; nothing after this
    // statement may use a member of the object
    delete this;
    // hint the JVM to collect orphaned classloaders periodically; the destructor above
    // released GlobalReferences, making Java objects eligible for GC.  Without this,
    // the JVM never runs GC because only native memory is under pressure, causing
    // classloaders and their associated native memory to accumulate indefinitely.
    //
    // Uses pre-cached method IDs (Globals::classSystem / methodSystemGC) — dynamic
    // lookups via findClass/getStaticMethod crash during the Qore destruction chain.
    // Batched (every 20 destructions) to avoid overhead.
    if (++jni_pgm_deref_count % 20 == 0) {
        // a pending cancellation of the thread is deferred, so that the hint is not skipped and the cancellation is not
        // reported here as an unhandled exception, as Env's constructor would raise it otherwise; it stays pending for
        // the code that follows (libqore itself defers it in the destructor of a Program object, but the Program can
        // also be released elsewhere)
        QoreCancelDeferralHelper cancel_deferral;
        try {
            Env env;
            env.callStaticVoidMethod(Globals::classSystem, Globals::methodSystemGC, nullptr);
        } catch (UnableToAttachException&) {
        } catch (AbstractException& e) {
            e.convert(&xsink);
        } catch (std::exception& e) {
            xsink.raiseException("JNI-ERROR", "error running the JVM garbage collector: %s", e.what());
        } catch (...) {
            xsink.raiseException("JNI-ERROR", "unknown error running the JVM garbage collector");
        }
    }
}
}
