/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJniClassMap.cpp

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2023 Qore Technologies, s.r.o.

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

#include <memory>
#include <set>

#include "defs.h"
#include "Jvm.h"
#include "QoreJniClassMap.h"
#include "Class.h"
#include "Method.h"
#include "Functions.h"
#include "JavaToQore.h"
#include "ModifiedUtf8String.h"

#include "JavaClassQoreJavaDynamicApi.inc"

namespace jni {
static std::string JNI_CK_JAVA_BIN_NAME = "jni_bin_name";

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

QoreJniClassMap::jtmap_t QoreJniClassMap::jtmap = {
    {"java.lang.Object", autoTypeInfo},
    // because of automatic array conversions, we do not use "or nothing" types for simple types
    {"java.lang.String", stringTypeInfo},
    {"java.lang.Float", floatTypeInfo},
    {"java.lang.Double", floatTypeInfo},
    {"java.lang.Boolean", boolTypeInfo},
    {"java.lang.Byte", bigIntTypeInfo},
    {"java.lang.Short", bigIntTypeInfo},
    {"java.lang.Integer", bigIntTypeInfo},
    {"java.lang.Long", bigIntTypeInfo},
    {"java.lang.Void", nothingTypeInfo},
    // for complex types, we use "or nothing" types to allow NULL to be passed explicitly
    {"java.time.ZonedDateTime", dateOrNothingTypeInfo},
    {"org.qore.jni.QoreRelativeTime", dateOrNothingTypeInfo},
    {"java.math.BigDecimal", numberOrNothingTypeInfo},
    {"java.util.Map", autoHashOrNothingTypeInfo},
    {"java.util.AbstractMap", autoHashOrNothingTypeInfo},
    {"java.util.HashMap", autoHashOrNothingTypeInfo},
    {"java.util.LinkedHashMap", autoHashOrNothingTypeInfo},
    {"org.qore.jni.Hash", autoHashOrNothingTypeInfo},
    {"java.util.List", autoListOrNothingTypeInfo},
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

QoreProgram* jni_get_program_context_unconditional() {
    QoreProgram* pgm = nullptr;;
    jni_get_context_unconditional(pgm);
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

JniExternalProgramData* jni_get_context_unconditional() {
    QoreProgram* pgm = nullptr;
    return jni_get_context_unconditional(pgm);
}

JniExternalProgramData* jni_get_context_unconditional(QoreProgram*& pgm) {
    JniExternalProgramData* jpc = jni_get_context(pgm);
    if (!jpc) {
        pgm = Globals::getJavaContextProgram();
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        assert(jpc);
    }
    return jpc;
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

    // add "Object" class
    // find/create parent namespace in default / master Jni namespace first
    const char* sn;
    QoreNamespace* ns = jni_find_create_namespace(*default_jns, "java.lang.Object", sn);

    QC_OBJECT = new JniQoreClass(pgm, "Object", "Jni::java::lang::Object", "java.lang.Object");
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
    qt2jmap[NT_HASH] = GlobalReference<jclass>((jclass)Globals::classHash);
    qt2jmap[NT_LIST] = env.findClass("[Ljava/lang/Object;").makeGlobal();
    qt2jmap[NT_NOTHING] = GlobalReference<jclass>((jclass)Globals::classPrimitiveVoid);

    // issu #4593: https://github.com/qorelanguage/qore/issues/4593
    qt2jmap[NT_RUNTIME_CLOSURE] = GlobalReference<jclass>((jclass)Globals::classQoreClosureMarkerImpl);
}

void QoreJniClassMap::destroy(ExceptionSink& xsink) {
    default_jns->clear(&xsink);
    delete default_jns;
    default_jns = nullptr;
}

// takes an internal name (ex: java/lang/Class)
jclass QoreJniClassMap::findLoadClass(const QoreString& name, QoreProgram* pgm) {
   ModifiedUtf8String nameUtf8(name);
   return findLoadClass(nameUtf8.c_str(), pgm);
}

// takes an internal name (ex: java/lang/Class)
jclass QoreJniClassMap::findLoadClass(const char* jpath, QoreProgram* pgm) {
    JniQoreClass* qc;
    {
        AutoLocker al(m);
        jcmap_t::iterator i = jcmap.find(jpath);
        if (i != jcmap.end()) {
            qc = i->second;
            //printd(LogLevel, "findLoadClass() '%s': %p (cached)\n", jpath, qc);
        } else {
            JniExternalProgramData* jpc;
            if (!pgm) {
                jpc = jni_get_context();
            } else {
                jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
            }
            if (jpc) {
                assert(static_cast<QoreJniClassMapBase*>(jpc) != static_cast<QoreJniClassMapBase*>(this));
                qc = jpc->find(jpath);
            }

            //printd(5, "findLoadClass() '%s': qc: %p pgm: %p jpc: %p\n", jpath, qc, pgm, jpc);
            if (!qc) {
                Env env;
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
            } else {
                //printd(LogLevel, "findLoadClass() '%s': %p (cached 2)\n", jpath, qc);
            }
        }
    }

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
        if (!strcmp(tname.c_str(), "microsoft.sql.DateTimeOffset")) {
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

Class* QoreJniClassMap::loadClass(Env& env, const char* name, bool& base, JniExternalProgramData* jpc) {
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
            throw QoreJniException("JNI-IMPORT-ERROR", "failed to load class '%s' with default classloader; no " \
                "program-specific classloader present", name);
        }
    }
    return loadProgramClass(env, name, jpc);
}

Class* QoreJniClassMap::loadProgramClass(Env& env, const char* name, JniExternalProgramData* jpc) {
    LocalReference<jstring> jname = get_dot_name(env, name).release();
    jvalue jarg;
    jarg.l = jname;

    //printd(5, "QoreJniClassMap::loadProgramClass() '%s' jpc: %p\n", name, jpc);

    LocalReference<jclass> c = env.callObjectMethod(jpc->getClassLoader(),
        Globals::methodQoreURLClassLoaderLoadClass, &jarg).as<jclass>();

    return new Class(c.release());
}

JniQoreClass* QoreJniClassMap::findCreateQoreClass(Env& env, LocalReference<jclass>& jc, QoreProgram* pgm) {
    LocalReference<jstring> clsName = env.callObjectMethod(jc, Globals::methodClassGetName, nullptr).as<jstring>();
    Env::GetStringUtfChars tname(env, clsName);

    printd(5, "QoreJniClassMap::findCreateQoreClass() looking up: '%s' pgm: %p\n", tname.c_str(), pgm);

    QoreString cname(tname.c_str());
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

    // we always grab the global JNI lock first because we might need to add base classes
    // while setting up the class loaded with the jni module's classloader, and we need to
    // ensure that these locks are always acquired in order
    AutoLocker al(m);

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
            // ensure that the jni module symbols are loaded into the new Program object
            MM.runTimeLoadModule("jni", pgm, &xsink);
            if (xsink) {
                throw XsinkException(xsink);
            }
        }
        jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
        assert(jpc);
    }

    JniQoreClass* qc = jpc->find(jpath);
    if (qc) {
        return qc;
    }

    assert(pgm);
    QoreExternalProgramContextHelper epch(&xsink, pgm);
    if (xsink) {
        throw XsinkException(xsink);
    }

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
    // we need to see if a builtin native Java class has already been imported without a lookup entry
    // or there is not already a Qore class in the namespace with the same name
    {
        // get last '.'
        int dot = name.rfind('.');

        // check for name conflict
        while ((qc = static_cast<JniQoreClass*>(ns->findLocalClass(sn)))) {
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

    // assert that we are not creating a Qore class for an imported class
    assert(name.find("qore.Qore"));
    QoreString path(name);
    path.replaceAll(".", "::");
    path.insert("::Jni::", 0);
    qc = new JniQoreClass(pgm, sn, path.c_str(), name.c_str());
    assert(qc->isSystem());
    createClassInNamespace(ns, *jpc->getJniNamespace(), jpath, cls.release(), qc, *jpc, pgm);

    return qc;
}

JniQoreClass* QoreJniClassMap::findCreateQoreClass(Env& env, const char* name, QoreProgram* pgm,
        JniExternalProgramData* jpc) {
    QoreString jpath(name);
    jpath.replaceAll(".", "/");
    jpath.replaceAll("__", "$");

    // first try to find class
    JniQoreClass* rv = findInternal(jpath.c_str());
    if (rv) {
        //printd(LogLevel, "QoreJniClassMap::findCreateQoreClass() '%s': %p\n", name, rv);
        return rv;
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

    // we need to protect access to the default namespace and class map with a lock
    AutoLocker al(m);

    // if we have the QoreClass already, then return it
    {
        JniQoreClass* qc = find(jpath);
        if (qc)
            return qc;
    }

    // workaround for the headless awt toolkit; we cannot initialize sun.awt.dnd.SunDropTargetEvent, because
    // the DataTransferer for the headless awt toolkit is null, and this will cause an initializer exception
    // in this class when initializing the static member "ToolkitThreadBlockedHandler handler"
    if (name == "sun.awt.dnd.SunDropTargetEvent") {
        Env env;
        if (env.callBooleanMethod(Globals::classGraphicsEnvironment, Globals::methodGraphicsEnvironmentIsHeadless,
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

    // see if we have an inner class
    int ic_idx = name.rfind('$');
    if (ic_idx != -1) {
        name.replaceChar(ic_idx, '_');
        name.insertch('_', ic_idx + 1, 1);
    }

    // find/create parent namespace in default / master Jni namespace first
    const char* sn;
    QoreNamespace* ns = jni_find_create_namespace(*default_jns, name.c_str(), sn);
    if (ic_idx != -1) {
        // get last '.'
        int dot = name.rfind('.');

        // check for name conflict
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
    JniQoreClass* qc = new JniQoreClass(pgm, sn, path.c_str(), name.c_str());
    assert(qc->isSystem());

    // createClassInNamespace() will "save" qc in the namespace
    createClassInNamespace(ns, *default_jns, jpath, cls.release(), qc, *this, pgm);

    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    // now add to the current Program's namespace
    if (jpc) {
        assert(pgm);
        ExceptionSink xsink;
        QoreExternalProgramContextHelper epch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

        // grab current Program's parse lock before manipulating namespaces
        CurrentProgramRuntimeExternalParseContextHelper pch;
        if (!pch) {
            throw BasicException("could not attach to deleted Qore Program when creating Qore class in base");
        }

        {
            JniQoreClass* qc0 = jpc->find(jpath);
            if (qc0) {
                return qc0;
            }
        }

        ns = jni_find_create_namespace(*jpc->getJniNamespace(), name.c_str(), sn);

        // copy class for assignment
        std::unique_ptr<JniQoreClass> new_qc(new JniQoreClass(*qc));
        assert(new_qc->isSystem());

        printd(LogLevel, "QoreJniClassMap::findCreateQoreClassInBase() jpc: %p '%s' qc: %p ns: %p '%s::%s'\n", jpc,
            jpath, new_qc.get(), ns, ns->getName(), qc->getName());

        assert(new_qc->getManagedUserData());

        // create entry for class in map
        jpc->add(jpath, new_qc.get());

        // save class in namespace
        qc = new_qc.release();
        // issue #5056: resolve abstract methods for copied class
        qc->runtimeResolveAbstractMethods();
        ns->addSystemClass(qc);
    }

    return qc;
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

JniQoreClass* QoreJniClassMap::createClassInNamespace(QoreNamespace* ns, QoreNamespace& jns, const char* jpath,
        Class* jc, JniQoreClass* qc, QoreJniClassMapBase& map, QoreProgram* pgm) {
    QoreClassHolder qc_holder(qc);

    JniExternalProgramData* jpc = pgm
        ? static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"))
        : jni_get_context(pgm);
    assert(jpc);

    // save pointer to java class info in JniQoreClass
    qc->setManagedUserData(jc);

    int mods = jc->getModifiers();
    if (mods & JVM_ACC_FINAL) {
        qc->setFinal();
    }

    printd(LogLevel, "QoreJniClassMap::createClassInNamespace() qc: %p ns: %p '%s::%s'\n", qc, ns, ns->getName(),
        qc->getName());

    assert(qc->getManagedUserData());

    // add to class maps
    map.add(jpath, static_cast<JniQoreClass*>(qc_holder.release()));

    addSuperClasses(qc, jc, jpath, pgm, jpc);

    // add methods after parents
    if (init_done) {
        populateQoreClass(*qc, jc, pgm);
    }

    // issue #5056: resolve abstract methods at runtime for classes created dynamically
    // this is needed for JNI classes imported through Python where abstract method resolution
    // that normally happens at parse time doesn't get called
    qc->runtimeResolveAbstractMethods();

    // save class in namespace
    ns->addSystemClass(qc);

    jpc->saveClass(*qc, jc->getJavaObjectRef());

    printd(LogLevel, "QoreJniClassMap::createClassInNamespace() '%s' returning qc: %p ns: %p -> '%s::%s'\n", jpath,
        qc, ns, ns->getName(), qc->getName());

    return qc;
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
            QoreClass* qore_parent = JniExternalProgramData::tryGetQoreClass(env, parent->getJavaObject(), true);
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

    QoreString jpath(chars.c_str());
    jpath.replaceAll(".", "/");
    JniQoreClass* pc = find(jpath.c_str());
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

void QoreJniClassMap::doConstructors(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    Env env;

    // get constructor methods
    LocalReference<jobjectArray> conArray = jc->getDeclaredConstructors();

    for (jsize i = 0, e = env.getArrayLength(conArray); i < e; ++i) {
        // get Constructor object
        LocalReference<jobject> c = env.getObjectArrayElement(conArray, i);

        SimpleRefHolder<BaseMethod> meth(new BaseMethod(env, c, jc));

#ifdef DEBUG
        LocalReference<jstring> conStr = env.callObjectMethod(c,
            Globals::methodConstructorToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, conStr);
        QoreString mstr(chars.c_str());
#endif

        // get method's parameter types
        type_vec_t paramTypeInfo;
        type_vec_t altParamTypeInfo;
        if (meth->getParamTypes(env, paramTypeInfo, altParamTypeInfo, *this, pgm)) {
            printd(LogLevel, "+ skipping %s.constructor() (%s); unsupported parameter type for variant %d\n",
                qc.getName(), mstr.c_str(), i + 1);
            continue;
        }

        // check for duplicate signature
        const QoreMethod* qm = qc.getConstructor();
        if (qm && qm->existsVariant(paramTypeInfo)) {
            printd(LogLevel, "QoreJniClassMap::doConstructors() skipping already-created variant %s::constructor()\n",
                qc.getName());
            continue;
        }

        qc.addConstructor((void*)*meth, (q_external_constructor_t)exec_java_constructor, meth->getAccess(),
            meth->getFlags(), QDOM_UNCONTROLLED_API, paramTypeInfo);

        // add native alternatives for hash and list arg types, if any
        if (!altParamTypeInfo.empty()) {
            if (qm && qm->existsVariant(altParamTypeInfo)) {
                printd(LogLevel, "QoreJniClassMap::doConstructors() skipping already-created variant " \
                    "%s::constructor()\n", qc.getName());
                continue;
            }
            qc.addConstructor((void*)*meth, (q_external_constructor_t)exec_java_constructor, meth->getAccess(),
                meth->getFlags(), QDOM_UNCONTROLLED_API, altParamTypeInfo);
        }

        jc->trackMethod(meth.release());
    }
}

const QoreTypeInfo* QoreJniClassMap::getQoreType(jclass cls, const QoreTypeInfo*& altType, QoreProgram* pgm, bool literal) {
    assert(!altType);
    Env env;

    // get class name
    LocalReference<jstring> clsName = env.callObjectMethod(cls, Globals::methodClassGetName, nullptr).as<jstring>();
    Env::GetStringUtfChars tname(env, clsName);

    // check for byte[]
    if (!strcmp(tname.c_str(), "[B")) {
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
        const QoreTypeInfo* elem_type = getQoreType(elem_cls, elemAltType, pgm, literal);

        if (elem_type == objectTypeInfo) {
            return softAutoListTypeInfo;
        } else {
            printd(5, "QoreJniClassMap::getQoreType() array type: '%s'\n", qore_type_get_name(elem_type));
            return qore_get_complex_softlist_type(elem_type);
        }
    }

    // check for class imported from Qore
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    if (jpc) {
        jvalue jarg;
        jarg.l = cls;
        if (env.callBooleanMethod(Globals::classQoreJavaClassBase, Globals::methodClassIsAssignableFrom, &jarg)) {
            jfieldID class_field = env.getStaticField(cls, JAVA_QORE_CLASS_FIELD, "J");
            const QoreClass* qc = reinterpret_cast<const QoreClass*>(
                env.getStaticLongField(cls, class_field)
            );
            assert(qc);
            return literal ? qc->getTypeInfo() : qc->getOrNothingTypeInfo();
        }
    }

    QoreString cname(tname.c_str());
    if (cname.startsWith("jdk.internal.")) {
        return objectTypeInfo;
    }
    QoreString jname(tname.c_str());
    jname.replaceAll(".", "/");

    printd(LogLevel, "QoreJniClassMap::getQoreType() class: '%s' jname: '%s'\n", cname.c_str(), jname.c_str());

    // find or create a class for the type
    JniQoreClass* qc = find(jname.c_str());
    if (!qc) {
        // try to find mapping in Program-specific class map
        if (jpc) {
            assert(static_cast<QoreJniClassMapBase*>(jpc) != static_cast<QoreJniClassMapBase*>(this));
            qc = jpc->find(jname.c_str());
        }

        if (!qc) {
            printd(LogLevel, "QoreJniClassMap::getQoreType() creating cname: '%s' jname: '%s'\n", cname.c_str(),
                jname.c_str());
            bool base;
            SimpleRefHolder<Class> cls(loadClass(env, jname.c_str(), base, jpc));
            qc = findCreateQoreClass(env, cname, jname.c_str(), cls.release(), base, pgm);
            assert(qc);
        }
    }

    // find static mapping
    jtmap_t::const_iterator i = jtmap.find(tname.c_str());
    if (i != jtmap.end()) {
        altType = literal ? qc->getTypeInfo() : qc->getOrNothingTypeInfo();
        const QoreTypeInfo* rv = i->second;
        if (literal && qore_type_is_assignable_from(rv, nothingTypeInfo)) {
            // get type string
            QoreString typestr(qore_type_get_name(rv));
            if (typestr[0] != '*') {
                return rv;
            }
            ExceptionSink xsink;
            typestr.splice(0, 1, &xsink);
            if (xsink) {
                xsink.clear();
                return rv;
            }
            const QoreTypeInfo* new_type = qore_get_type_from_string(typestr.c_str(), xsink);
            if (xsink) {
                xsink.clear();
                return rv;
            }
            return new_type;
        }
        return rv;
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
            //printd(LogLevel, "returning %s (%s) -> %s\n", tname.c_str(), jcname.c_str(),
            //  typeInfoGetName(i->second));
            altType = literal ? qc->getTypeInfo() : qc->getOrNothingTypeInfo();
            return i->second;
        }
    }

    return literal ? qc->getTypeInfo() : qc->getOrNothingTypeInfo();
}

void QoreJniClassMap::doMethods(JniQoreClass& qc, jni::Class* jc, QoreProgram* pgm) {
    Env env;
    //printd(LogLevel, "QoreJniClassMap::doMethods() qc: %p jc: %p\n", qc, jc);

    bool abstract_class = jc->getModifiers() & JVM_ACC_ABSTRACT;

    LocalReference<jobjectArray> mArray = jc->getDeclaredMethods();

    for (jsize i = 0, e = env.getArrayLength(mArray); i < e; ++i) {
        // get Method object
        LocalReference<jobject> m = env.getObjectArrayElement(mArray, i);

        SimpleRefHolder<BaseMethod> meth(new BaseMethod(m, jc));

        QoreString mname;
        meth->getName(mname);

        // Skip synthetic and bridge methods (Kotlin/compiler-generated)
        if (meth->isSynthetic() || meth->isBridge()) {
            printd(LogLevel, "+ skipping synthetic/bridge method %s.%s()\n", qc.getName(), mname.c_str());
            continue;
        }

        // Skip Kotlin default parameter method variants (e.g., "methodName$default")
        if (mname.find("$default") < mname.size()) {
            printd(LogLevel, "+ skipping Kotlin default param method %s.%s()\n", qc.getName(), mname.c_str());
            continue;
        }

        printd(LogLevel, "+ adding method %s.%s()\n", qc.getName(), mname.c_str());

        // get method's parameter types
        type_vec_t paramTypeInfo;
        type_vec_t altParamTypeInfo;
        if (meth->getParamTypes(env, paramTypeInfo, altParamTypeInfo, *this, pgm)) {
            printd(LogLevel, "+ skipping %s.%s(); unsupported parameter type for variant %d\n", qc.getName(),
                mname.c_str(), i + 1);
            continue;
        }

        // get method's return type
        const QoreTypeInfo* returnTypeInfo = meth->getReturnTypeInfo(*this, pgm);

        if (meth->isStatic()) {
            // check for duplicate signature
            const QoreMethod* qm = qc.findLocalStaticMethod(mname.c_str());
            if (qm && qm->existsVariant(paramTypeInfo)) {
                printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created static variant %s::%s()\n",
                    qc.getName(), mname.c_str());
                continue;
            }
            qc.addStaticMethod((void*)*meth, mname.c_str(), (q_external_static_method_t)exec_java_static_method,
                meth->getAccess(), meth->getFlags(), QDOM_UNCONTROLLED_API, returnTypeInfo, paramTypeInfo);

            if (!altParamTypeInfo.empty()) {
                if (qm && qm->existsVariant(altParamTypeInfo)) {
                    printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created static variant " \
                        "%s::%s()\n", qc.getName(), mname.c_str());
                    continue;
                }
                qc.addStaticMethod((void*)*meth, mname.c_str(), (q_external_static_method_t)exec_java_static_method,
                    meth->getAccess(), QCF_NO_FLAGS, QDOM_UNCONTROLLED_API, returnTypeInfo, altParamTypeInfo);
            }
        } else {
            if (mname == "copy" || mname == "constructor" || mname == "destructor" || mname == "methodGate"
                || mname == "memberNotification" || mname == "memberGate")
                mname.prepend("java_");

            // check for duplicate signature
            const QoreMethod* qm = qc.findLocalMethod(mname.c_str());
            if (qm && qm->existsVariant(paramTypeInfo)) {
                printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created variant %s::%s()\n",
                    qc.getName(), mname.c_str());
                continue;
            }

            if (abstract_class && meth->isAbstract()) {
                qc.addAbstractMethod(mname.c_str(), meth->getAccess(), meth->getFlags(), returnTypeInfo,
                    paramTypeInfo);
                // do not add additional abstract variants for alternate parameter types
            } else {
                qc.addMethod((void*)*meth, mname.c_str(), (q_external_method_t)exec_java_method, meth->getAccess(),
                    meth->getFlags(), QDOM_UNCONTROLLED_API, returnTypeInfo, paramTypeInfo);

                if (!altParamTypeInfo.empty()) {
                    if (qm && qm->existsVariant(altParamTypeInfo)) {
                        printd(LogLevel, "QoreJniClassMap::doMethods() skipping already-created variant %s::%s()\n",
                            qc.getName(), mname.c_str());
                        continue;
                    }
                    qc.addMethod((void*)*meth, mname.c_str(), (q_external_method_t)exec_java_method,
                        meth->getAccess(), QCF_NO_FLAGS, QDOM_UNCONTROLLED_API, returnTypeInfo, altParamTypeInfo);
                }
            }
        }
        jc->trackMethod(meth.release());
    }
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

static LocalReference<jobject> get_type_def_from_class(Env& env, jclass jcls) {
    //printd(5, "get_type_def_from_class() jcls: %p\n", jcls);
    jvalue arg;
    arg.l = jcls;
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeDescriptionCls, &arg);
}

QoreClass* JniExternalProgramData::tryGetQoreClass(Env& env, jclass jcls, bool inherited) {
    try {
        if (inherited) {
            jfieldID class_field = env.getStaticField(jcls, JAVA_QORE_CLASS_FIELD, "J");
            return reinterpret_cast<QoreClass*>(
                env.getStaticLongField(jcls, class_field)
            );
        }

        // check if the "$qore_cls_ptr" static field is declared in this class
        jvalue jarg;
        jarg.l = Globals::javaQoreClassField;
        LocalReference<jobject> field = env.callObjectMethod(jcls, Globals::methodClassGetDeclaredField, &jarg);
        jarg.l = nullptr;
        return reinterpret_cast<QoreClass*>(env.callLongMethod(field, Globals::methodFieldGetLong, &jarg));
    } catch (jni::Exception& e) {
        // ignore exceptions when the field is not found
        e.ignore();
    }
    return nullptr;
}

void JniExternalProgramData::saveClass(const QoreClass& qc, LocalReference<jclass> jcls) {
    AutoLocker al(codeGenLock);

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
        unsigned& len, bool do_varargs, bool is_abstract) {
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
        LocalReference<jobject> ptype = getJavaTypeDefinition(env, class_loader, i, true);
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
    if (!list_size) {
        return 0;
    }

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

    if (qore_parent) {
        return 0;
    }

    // if there are no matches in the list, then check base class methods of the opposite type
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

int JniExternalProgramData::addConstructorVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v, jclass parent_class,
        QoreJavaParamHelper& jph) {
    printd(5, "JniExternalProgramData::addConstructorVariant() adding Java constructor %s %s::constructor(%s) {}\n",
        v.getAccessString(), qcls.getName(), v.getSignatureText());

    bool varargs = v.getCodeFlags() & QCF_USES_EXTRA_ARGS ? true : false;

    // first get the params
    unsigned len;
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs);

    while (true) {
        if (!jph.checkVariant(params, QMT_CONSTRUCTOR)) {
            std::vector<jvalue> jargs(7);
            jargs[0].l = bb;
            jargs[1].l = parent_class;
            jargs[2].j = reinterpret_cast<jlong>(&m);
            jargs[3].j = reinterpret_cast<jlong>(&v);
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
            params = getJavaParamList(env, class_loader, v, len, false);
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
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs, is_abstract);

    QoreString jname;
    if (!strcmp(m.getName(), "getClass")) {
        jname = "getQoreClass";
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
            jargs[2].j = reinterpret_cast<jlong>(&m);
            jargs[3].j = reinterpret_cast<jlong>(&v);
            jargs[4].i = qore_jni_get_acc_visibility(v.getAccess());
            LocalReference<jobject> return_type = getJavaTypeDefinition(env, class_loader, v.getReturnTypeInfo());
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
            params = getJavaParamList(env, class_loader, v, len, false);
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
    LocalReference<jobject> params = getJavaParamList(env, class_loader, v, len, varargs);

    while (true) {
        if (!jph.checkVariant(params, QMT_STATIC)) {
            std::vector<jvalue> jargs(9);
            jargs[0].l = bb;
            LocalReference<jstring> mname = env.newString(m.getName());
            jargs[1].l = mname;
            jargs[2].j = (jlong)getProgram(),
            jargs[3].j = reinterpret_cast<jlong>(&m);
            jargs[4].j = reinterpret_cast<jlong>(&v);
            jargs[5].i = qore_jni_get_acc_visibility(v.getAccess());
            LocalReference<jobject> return_type = getJavaTypeDefinition(env, class_loader, v.getReturnTypeInfo());
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
            params = getJavaParamList(env, class_loader, v, len, false);
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

                        if (addNormalMethodVariant(env, class_loader, source_class, bb, *m, *v, jph)) {
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
                        if (addStaticMethods(env, class_loader, source_class, *sm, jph, bb)) {
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

        QoreJavaParamHelper jph(env, nullptr, parent_class, qore_parent);
        if (addStaticMethods(env, class_loader, source_class, *m, jph, bb)) {
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
    ExceptionSink xsink;
    if (!qcls) {
        assert(!qpath.empty());
        // set program context (and read lock) before calling QoreProgram::findClass()
        QoreExternalProgramContextHelper pch(&xsink, pgm);
        if (xsink) {
            throw XsinkException(xsink);
        }

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
            AutoLocker al(codeGenLock);

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
            AutoLocker al(codeGenLock);

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
            QoreStringMaker desc("Java class '%s' cannot be generated, because Qore class '%s' cannot be found; loaded " \
                "modules: ", java_name.c_str(), qpath.c_str());
            ConstListIterator fi(*feature_list);
            while (fi.next()) {
                desc.sprintf("%s, ", fi.getValue().get<const QoreStringNode>()->c_str());
            }
            desc.terminate(desc.size() - 2);
            desc.concat(')');
            env.throwNew(env.findClass("java/lang/ClassNotFoundException"), desc.c_str());
            return nullptr;
        }
    }

    // ensure exclusive access while creating java classes
    AutoLocker al(codeGenLock);

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
    ValueHolder v(qc.getReferencedKeyValue(JNI_CK_JAVA_BIN_NAME), nullptr);
    if (v) {
        assert(v->getType() == NT_STRING);
        const char* jname = v->get<const QoreStringNode>()->c_str();
        printd(5, "JniExternalProgramData::getJavaNameForClass() cls '%s' -> embedded java '%s'\n", qc.getName(),
            jname);
        return env.newString(jname);
    }

    std::string pname = qc.getNamespacePath(true);
    // if it's already a Java class, then return the original Java binary name
    if (pname.rfind("::Jni::", 0) == 0) {
        pname.erase(0, 7);
        convert_qore_ns_to_java_pkg(pname);
    } else {
        const char* mod = qc.getModuleName();

        if (mod) {
            bool done = false;
#if QORE_VERSION_CODE >= 10013
            if (!strcmp(mod, "python")) {
                const QoreNamespace* ns = qc.getNamespace();
                ValueHolder pm(ns->getReferencedKeyValue("python_module"), nullptr);
                printd(5, "JniExternalProgramData::getJavaNameForClass() pname: '%s' ns: '%s pm: %s\n", pname.c_str(),
                    ns->getPath(true).c_str(), pm->getFullTypeName());
                if (pm) {
                    pname = pm->get<QoreStringNode>()->c_str();
                    convert_qore_ns_to_java_pkg(pname);
                    pname += ".";
                    pname += qc.getName();
                    pname.insert(0, "pythonmod.");
                    done = true;
                }
            }
#endif
            if (!done) {
                if (isInjectedModule(mod)) {
                    convert_qore_ns_to_java_pkg(pname);
                    pname.insert(0, "qore");
                } else {
                    QoreProgram* pgm = qc.getProgram();
                    if (!pgm) {
                        pgm = getProgram();
                        assert(pgm);
                    }
                    const QoreNamespace* ns = get_module_root_ns(mod, pgm);
                    if (!ns) {
                        convert_qore_ns_to_java_pkg(pname);
                        pname.insert(0, "qore");
                    } else {
                        std::string nspath = ns->getPath(true);
                        if (pname.rfind(nspath, 0) == 0) {
                            printd(5, "pname before '%s' (nspath: '%s')\n", pname.c_str(), nspath.c_str());
                            // create namespace path from the module's main namespace
                            pname.erase(0, nspath.size());
                            printd(5, "pname after '%s' (pgm: %p jpc: %p mod: %s)\n", pname.c_str(), pgm, this, mod);
                        }
                        convert_qore_ns_to_java_pkg(pname);

                        pname.insert(0, mod);
                        if (strcmp(mod, "python")) {
                            pname.insert(0, "qoremod.");
                        }
                    }
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
            jargs[2].j = reinterpret_cast<jlong>(pgm);
            jargs[3].j = reinterpret_cast<jlong>(&func);
            jargs[4].j = reinterpret_cast<jlong>(&v);
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
        QoreStringMaker desc("cannot find Qore namespace '%s' to generate '%s' class for importing functions to Java",
            ns_path, JniImportedFunctionClassName.c_str());
        env.throwNew(env.findClass("java/lang/ClassNotFoundException"), desc.c_str());
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
    // NOTE this must come last as using Env::GetStringUtfChars on a java string destroys the string
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
        jargs[4].j = (jlong)&c;
        jargs[5].l = ilist;
        bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderAddStaticField,
            &jargs[0]);
    }

    // create static initializer
    jvalue jargs[4];
    jargs[0].l = bb;
    jargs[1].l = jname;
    jargs[2].j = (long)pgm;
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
        QoreStringMaker desc("cannot find Qore namespace '%s' to generate '%s' class for importing constants to Java",
            ns_path, JniImportedConstantClassName.c_str());
        env.throwNew(env.findClass("java/lang/ClassNotFoundException"), desc.c_str());
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
    // NOTE this must come last as using Env::GetStringUtfChars on a java string destroys the string
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
        jargs[4].j = (jlong)&c;
        jargs[5].l = ilist;
        bb = env.callStaticObjectMethod(Globals::classJavaClassBuilder, Globals::methodJavaClassBuilderAddStaticField,
            &jargs[0]);
    }

    // create static initializer
    jvalue jargs[4];
    jargs[0].l = bb;
    jargs[1].l = jname;
    jargs[2].j = (long)pgm;
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

    // get parent class
    LocalReference<jclass> parent_class;
    jclass parent_ptr = nullptr;
    // the Qore class corresponding to parent_ptr
    const QoreClass* qore_parent = nullptr;
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

            // get internal name for Qore class
            LocalReference<jstring> jname = getJavaNameForClass(env, ci.getParentClass());

            jvalue jargs[2];
            jargs[0].l = jname;
            jargs[1].j = (jlong)&ci.getParentClass();
            parent_class = env.callObjectMethod(class_loader, Globals::methodQoreURLClassLoaderLoadClassWithPtr,
                &jargs[0]).as<jclass>();

            // check if parent class is actually a Java interface
            if (env.callBooleanMethod(parent_class, Globals::methodClassIsInterface, nullptr)) {
                if (!parent_interfaces) {
                    parent_interfaces = env.newObject(Globals::classArrayList, Globals::ctorArrayList, nullptr);
                }
                jvalue jarg;
                jarg.l = parent_class;
                env.callBooleanMethod(parent_interfaces, Globals::methodArrayListAdd, &jarg);
                parent_class = nullptr;
                printd(5, "JniExternalProgramData::generateByteCodeIntern() cls: '%s' <- interface '%s'\n",
                    qcls->getName(), ci.getParentClass().getName());
            } else {
                parent_ptr = (jclass)parent_class;
                qore_parent = &ci.getParentClass();
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

    jlong cptr = reinterpret_cast<jlong>(qcls);

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

    std::vector<jvalue> jargs(5);
    jargs[0].l = jname;
    jargs[1].l = parent_ptr;
    jargs[2].l = parent_interfaces;
    jargs[3].z = qcls->isAbstract();
    jargs[4].j = cptr;

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

    if (addClassConstants(env, jname, *qcls, bb, pgm)) {
        return nullptr;
    }

    std::string qpath = qcls->getNamespacePath();
    setCreateInProgress(qpath);

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
            printd(0, "+ %s\n", i.c_str());
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

    clearCreateInProgress(qpath);

    // save Java bin name in Qore class if necessary
    if (has_jname) {
        // NOTE this must come last as using Env::GetStringUtfChars on a java string destroys the string
        Env::GetStringUtfChars jname_str(env, jname);
        printd(5, "JniExternalProgramData::generateByteCodeIntern() saving class name %p %s: %s\n", qcls,
            qcls->getName(), jname_str.c_str());
        const_cast<QoreClass*>(qcls)->setKeyValueIfNotSet(JNI_CK_JAVA_BIN_NAME, jname_str.c_str());
    }

    printd(5, "JniExternalProgramData::generateByteCodeIntern() %s rv: %p cl: %x (this->cl: %x) pgm: %p\n",
        qcls->getPath(), (jobject)rv,
        env.callIntMethod((jobject)class_loader, jni::Globals::methodObjectHashCode, nullptr),
        env.callIntMethod((jobject)classLoader, jni::Globals::methodObjectHashCode, nullptr), pgm
    );
    return rv;
}

LocalReference<jobject> JniExternalProgramData::getJavaTypeDefinition(Env& env, jobject class_loader,
        const QoreTypeInfo* ti, bool no_void) {
    qore_type_t t = qore_type_get_base_type(ti);
    printd(5, "JniExternalProgramData::getJavaTypeDefinition() looking up type '%s' (%d) cl: %x (no void: %d)\n",
        qore_type_get_name(ti), t, env.callIntMethod((jobject)class_loader, jni::Globals::methodObjectHashCode,
            nullptr), no_void);
    if (t != NT_OBJECT) {
        if (no_void && (t == NT_NOTHING || t == NT_NULL)) {
            return get_type_def_from_class(env, Globals::classObject);
        }
        LocalReference<jclass> jtype(QoreJniClassMap::getPrimitiveType(t));
        return get_type_def_from_class(env, (jclass)jtype);
    }

    const QoreClass* cls = type_info_get_return_class(ti);
    if (!cls) {
        printd(5, "JniExternalProgramData::getJavaTypeDefinition() no mapping for '%s'\n", qore_type_get_name(ti));
        return get_type_def_from_class(env, Globals::classObject);
    }

    // get internal name for Qore class
    LocalReference<jstring> jname = getJavaNameForClass(env, *cls);

    if (!isCreateInProgress(cls->getNamespacePath())) {
        printd(5, "JniExternalProgramData::getJavaTypeDefinition() type '%s' (%d) creating Java class for '%s' (%p)\n",
            qore_type_get_name(ti), t, cls->getPath(), cls);

        try {
            AutoLocker al(QoreJniClassMap::m);

            jvalue jargs[2];
            jargs[0].l = jname;
            jargs[1].j = (jlong)cls;
            LocalReference<jclass> jcls = env.callObjectMethod(class_loader, Globals::methodQoreURLClassLoaderLoadClassWithPtr,
                &jargs[0]).as<jclass>();
            assert(jcls);
            jargs[0].l = jcls;
            LocalReference<jobject> rv = env.callStaticObjectMethod(Globals::classJavaClassBuilder,
                Globals::methodJavaClassBuilderGetTypeDescriptionCls, &jargs[0]);
            printd(5, "JniExternalProgramData::getJavaTypeDefinition() type '%s' (%d) got Java class for '%s' (%p): %p\n",
                qore_type_get_name(ti), t, cls->getPath(), cls, *rv);
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

    printd(5, "JniExternalProgramData::getJavaTypeDefinition() this: %p type '%s' (%d) creating forward ref for Java " \
        "class for '%s' (%p)\n", this, qore_type_get_name(ti), t, cls->getPath(), cls);
    jvalue jarg;
    jarg.l = jname;
    return env.callStaticObjectMethod(Globals::classJavaClassBuilder,
        Globals::methodJavaClassBuilderGetTypeDescriptionStr, &jarg);
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

    // return a new Java QoreObject with a weak reference to the actual Qore object
    o->tRef();
    jvalue arg;
    arg.j = reinterpret_cast<jlong>(o);
    try {
        Env env;
        return env.newObject(Globals::classQoreObject, Globals::ctorQoreObject, &arg).release();
    } catch (jni::Exception& e) {
        const_cast<QoreObject*>(o)->tDeref();
        throw;
    }
}

LocalReference<jclass> QoreJniClassMap::getPrimitiveType(qore_type_t t) {
    qt2jmap_t::const_iterator i = qt2jmap.find(t);
    LocalReference<jclass> rv = i != qt2jmap.end() ? i->second.toLocal() : Globals::classObject.toLocal();
    printd(5, "QoreJniClassMap::getPrimitiveType() type %d -> java cls %p\n", t, (jclass)rv);
    return rv;
}

jobject QoreJniClassMap::getJavaClosure(const ResolvedCallReferenceNode* call) {
    // return a new Java QoreClosure; weak references are not needed, as ResolvedCallReferenceNode objects always
    // implement a weak reference to any captured QoreObject*s
    call->ref();
    jvalue arg;
    arg.j = reinterpret_cast<jlong>(call);
    try {
        Env env;
        return env.newObject(Globals::classQoreClosure, Globals::ctorQoreClosure, &arg).release();
    } catch (jni::Exception& e) {
        // NOTE: in the very unlikely case of a Qore exception here, the default exception handler will handle it
        ExceptionSink xsink;
        const_cast<ResolvedCallReferenceNode*>(call)->deref(&xsink);
        throw;
    }
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
    try {
        // issue #3585: set context for external java threads
        //QoreProgram* pgm = qmeth.getClass()->getProgram();
        QoreProgram* pgm = self->getProgram(); //qmeth.getClass()->getProgram();
        JniExternalProgramData* jpc = JniExternalProgramData::setContext(pgm);

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
    try {
        // issue #3585: set context for external java threads
        QoreProgram* pgm = meth.getClass()->getProgram();
        JniExternalProgramData::setContext(pgm);
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
    // issue #3585: set context for external java threads
    JniExternalProgramData::setContext(pgm);
    QoreProgramContextHelper pch(pgm);

    try {
        return m->invoke(jd->getObject(), args, pgm);
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return QoreValue();
    }
}

static const char* access_str(ClassAccess a) {
    switch (a) {
        case Public: return "public";
        case Private: return "private";
        default: return "private:internal";
    }
}

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

        const QoreTypeInfo* fieldTypeInfo = field->getQoreTypeInfo(*this, pgm);

        if (field->isStatic()) {
            printd(LogLevel, "+ adding static field %s %s %s.%s (%s)\n", access_str(field->getAccess()),
                typeInfoGetName(fieldTypeInfo), qc.getName(), fname.c_str(), field->isFinal() ? "const" : "var");

            QoreValue v(field->getStatic(pgm, false));
            if (field->isFinal()) {
                if (v.isNothing())
                    v.assign(0ll);
                qc.addBuiltinConstant(fname.c_str(), v, field->getAccess());
            } else
                qc.addBuiltinStaticVar(fname.c_str(), v, field->getAccess(), fieldTypeInfo);
        } else if (!qc.findLocalMember(fname.c_str())) {
            printd(LogLevel, "+ adding field %s %s %s.%s\n", access_str(field->getAccess()),
                typeInfoGetName(fieldTypeInfo), qc.getName(), fname.c_str());
            qc.addMember(fname.c_str(), field->getAccess(), fieldTypeInfo);
        }
    }
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

    initDynamicApi(env);

    // setup classpath
    TempString classpath(SystemEnvironment::get("QORE_JNI_CLASSPATH"));
    if (classpath) {
        addClasspath(classpath->c_str());
    }
}

JniExternalProgramData::JniExternalProgramData(const JniExternalProgramData& parent, Env& env, QoreProgram* pgm) :
        pgm(pgm),
        classLoader(nullptr),
        override_compat_types(parent.override_compat_types),
        compat_types(parent.compat_types) {
    // create the classLoader and set the parent
    {
        jvalue jargs[2];
        jargs[0].j = (jlong)pgm;
        jargs[1].l = parent.classLoader;
        classLoader = env.newObject(Globals::classQoreURLClassLoader, Globals::ctorQoreURLClassLoader,
            &jargs[0]).makeGlobal();
    }

    initDynamicApi(env);

    // copy the parent's class map to this one
    jcmap = parent.jcmap;

#if QORE_VERSION_CODE >= 10013
    ProgramRuntimeExternalParseContextHelper pch(pgm);
#endif

    // find Jni namespace in new Program if present
    jni = pgm->findNamespace("Jni");
    if (!jni) {
        jni = qjcm.getJniNs().copy();
        pgm->getRootNS()->addNamespace(jni);
    }
}

JniExternalProgramData::~JniExternalProgramData() {
    // NOTE: any exception thrown here will be printed out to stderr
    ExceptionSink xsink;
    try {
        Env env;
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderClearProgramPtr, nullptr);
    } catch (UnableToAttachException& e) {
        // ignore error - raised when destructions is run after the JVM has shut down
    } catch (jni::Exception& e) {
        e.convert(&xsink);
    }

    // delete fake "$" classes
    for (auto& i : fake_cls_map) {
        delete i.second;
    }
    classLoader = nullptr;
}

void JniExternalProgramData::initDynamicApi(Env& env) {
    // define the QoreJavaDynamicApi class using our new classloader
    LocalReference<jstring> jname = env.newString("org.qore.jni.QoreJavaDynamicApi");

    // make byte array
    LocalReference<jbyteArray> jbyte_code =
        env.newByteArray(java_org_qore_jni_QoreJavaDynamicApi_class_len).as<jbyteArray>();
    for (jsize i = 0; (unsigned)i < java_org_qore_jni_QoreJavaDynamicApi_class_len; ++i) {
        env.setByteArrayElement(jbyte_code, i, java_org_qore_jni_QoreJavaDynamicApi_class[i]);
    }

    std::vector<jvalue> jargs(4);
    jargs[0].l = jname;
    jargs[1].l = jbyte_code;
    jargs[2].i = 0;
    jargs[3].i = java_org_qore_jni_QoreJavaDynamicApi_class_len;

    printd(5, "JniExternalProgramData::JniExternalProgramData() jname: %p bc: %p cl: %d\n", (jobject)jname,
        (jobject)jbyte_code, java_org_qore_jni_QoreJavaDynamicApi_class_len);
    dynamicApi = env.callObjectMethod(classLoader, Globals::methodQoreURLClassLoaderDefineResolveClass,
        &jargs[0]).as<jclass>().makeGlobal();
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

void JniExternalProgramData::addClasspath(const char* path) {
    Env env;
    LocalReference<jstring> jname = env.newString(path);
    jvalue jarg;
    jarg.l = jname;
    try {
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderAddPath, &jarg);
    } catch (jni::Exception& e) {
        // display exception info on the console as an unhandled exception
        ExceptionSink xsink;
        e.convert(&xsink);
    }
}

void JniExternalProgramData::addParentClasspath(const char* path) {
    Env env;
    LocalReference<jstring> jname = env.newString(path);
    jvalue jarg;
    jarg.l = jname;
    try {
        env.callVoidMethod(classLoader, Globals::methodQoreURLClassLoaderAddParentPath, &jarg);
    } catch (jni::Exception& e) {
        // display exception info on the console as an unhandled exception
        ExceptionSink xsink;
        e.convert(&xsink);
    }
}

JniExternalProgramData* JniExternalProgramData::setContext(Env& env) {
    QoreProgram* pgm = nullptr;
    return setContext(env, pgm);
}

JniExternalProgramData* JniExternalProgramData::setContext(Env& env, QoreProgram*& pgm) {
    // issue #3199: no program is available when initializing the jni module from the command line
    // issue #3153: no context is available when called from a static method
    JniExternalProgramData* jpc = jni_get_context_unconditional(pgm);
    // set classloader context in new thread
    env.callVoidMethod(jpc->classLoader, Globals::methodQoreURLClassLoaderSetContext, nullptr);
    return jpc;
}

bool JniExternalProgramData::compatTypes() {
    // issue #3199: no program is available when initializing the jni module from the command line
    // issue #3153: no context is available when called from a static method
    JniExternalProgramData* jpc = jni_get_context_unconditional();
    return jpc->getCompatTypes();
}

JniExternalProgramData* JniExternalProgramData::getCreateJniProgramData(QoreProgram* pgm) {
    JniExternalProgramData* jpc = static_cast<JniExternalProgramData*>(pgm->getExternalData("jni"));
    //printd(5, "parse-cmd '%s' jpc: %p jnins: %p\n", arg.c_str(), jpc, jpc ? jpc->getJniNamespace() : nullptr);
    if (!jpc) {
#if QORE_VERSION_CODE >= 10013
        ProgramRuntimeExternalParseContextHelper pch(pgm);
#endif
        QoreNamespace* jnins = pgm->findNamespace("::Jni");
        if (!jnins) {
            jnins = qjcm.getJniNs().copy();
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
    // ensure that class generation is atomic
    AutoLocker al(codeGenLock);

    // get or create a Java class for the given Qore class
    std::string cls_hash = get_class_hash(*qc);
    q2jmap_t::iterator i = q2jmap.lower_bound(cls_hash);
    if (i == q2jmap.end() || i->first != cls_hash) {
        // get Java name for class
        LocalReference<jstring> jname = getJavaNameForClass(env, *qc);

        jvalue jargs[2];
        jargs[0].l = jname;
        jargs[1].j = (long)qc;
        LocalReference<jclass> jcls = env.callObjectMethod(classLoader,
            Globals::methodQoreURLClassLoaderLoadClassWithPtr, &jargs[0]).as<jclass>();
        assert(jcls);

        // save generated class
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
    o->tRef();
    try {
        jvalue arg;
        arg.j = reinterpret_cast<jlong>(o);
        LocalReference<jobject> jarg = env.newObject(Globals::classQoreJavaObjectPtr, Globals::ctorQoreJavaObjectPtr,
            &arg);

        arg.l = jarg;
        return env.newObject(jcls, ctor, &arg);
    } catch (jni::Exception& e) {
        const_cast<QoreObject*>(o)->tDeref();
        throw;
    }
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

void JniExternalProgramData::doDeref() {
    ExceptionSink xsink;
    if (save_object_callback) {
        save_object_callback->deref(&xsink);
    }
    try {
        delete this;
    } catch (jni::Exception& e) {
        e.convert(&xsink);
    }
    if (xsink) {
        throw new QoreXSinkException(xsink);
    }
}
}