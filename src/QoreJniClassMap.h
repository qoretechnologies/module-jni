/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJniClassMap.h

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.

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

#ifndef _QORE_JNI_QOREJAVACLASSMAP_H

#define _QORE_JNI_QOREJAVACLASSMAP_H

#include "QoreJniPrivateData.h"
#include "Env.h"
#include "Class.h"
#include "JniQoreClass.h"

#include <set>
#include <map>
#include <mutex>
#include <condition_variable>

typedef std::set<std::string> strset_t;

DLLLOCAL QoreClass* initJavaArrayClass(QoreNamespace& ns);
DLLLOCAL QoreClass* initQoreInvocationHandlerClass(QoreNamespace& ns);

DLLLOCAL void init_jni_functions(QoreNamespace& ns);
DLLLOCAL QoreClass* jni_class_handler(QoreNamespace* ns, const char* cname);

DLLLOCAL extern bool jni_compat_types;

namespace jni {

// the JniQoreClass for java::lang::Object
extern JniQoreClass* QC_OBJECT;
// the Qore class ID for java::lang::Object
extern qore_classid_t CID_OBJECT;
// the JniQoreClass for java::lang::Class
extern JniQoreClass* QC_CLASS;
// the Qore class ID for java::lang::Class
extern qore_classid_t CID_CLASS;
// the JniQoreClass for java::lang::reflect::Method
extern JniQoreClass* QC_METHOD;
// the Qore class ID for java::reflect::Method
extern qore_classid_t CID_METHOD;
// the JniQoreClass for java::lang::ClassLoader
extern JniQoreClass* QC_CLASSLOADER;
// the Qore class ID for java::lang::ClassLoader
extern qore_classid_t CID_CLASSLOADER;
// the JniQoreClass for java::lang::Throwable
extern JniQoreClass* QC_THROWABLE;
// the Qore class ID for java::lang::Throwable
extern qore_classid_t CID_THROWABLE;
// the JniQoreClass for java::lang::reflect::InvocationHandler
extern JniQoreClass* QC_INVOCATIONHANDLER;
// the Qore class ID for java::lang::reflect::InvocationHandler
extern qore_classid_t CID_INVOCATIONHANDLER;

// forward references
class Class;
class JniExternalProgramData;

class QoreJniClassMapBase {
protected:
    // map of java class names (ex 'java/lang/Object') to JniQoreClass ptrs
    typedef std::map<std::string, JniQoreClass*> jcmap_t;
    // map of java class names (ex 'java/lang/Object') to JniQoreClass ptrs
    jcmap_t jcmap;

public:
    DLLLOCAL void add(const char* name, JniQoreClass* qc) {
        printd(LogLevel, "QoreJniClassMapBase::add() this: %p name: %s qc: %p (%s)\n", this, name, qc, qc->getName());

#ifdef DEBUG
        if (jcmap.find(name) != jcmap.end())
            printd(0, "QoreJniClassMapBase::add() name: %s qc: %p (%s)\n", name, qc, qc->getName());
#endif

        assert(jcmap.find(name) == jcmap.end());
        jcmap[name] = qc;
    }

    // accepts either a dotted name (ex: "java.lang.Object)") or an internal name ("java/lang/Object") as argument
    DLLLOCAL JniQoreClass* find(const char* jpath) const {
        if (strchr(jpath, '.')) {
            QoreString str(jpath);
            str.replaceAll(".", "/");
            jcmap_t::const_iterator i = jcmap.find(str.c_str());
            return i == jcmap.end() ? nullptr : i->second;
        }
        jcmap_t::const_iterator i = jcmap.find(jpath);
        return i == jcmap.end() ? nullptr : i->second;
    }

    // accepts an internal name as argument (ex: "java/lang/Object")
    DLLLOCAL JniQoreClass* findInternal(const char* jpath) const {
        jcmap_t::const_iterator i = jcmap.find(jpath);
        return i == jcmap.end() ? nullptr : i->second;
    }
};

class QoreJniClassMap : public QoreJniClassMapBase {
public:
    static QoreRecursiveThreadLock m;

    DLLLOCAL void init(QoreProgram* pgm, bool already_initialized);

    DLLLOCAL void destroy(ExceptionSink& xsink);

    DLLLOCAL QoreValue getValue(LocalReference<jobject>& jobj, QoreProgram* pgm, bool compat_types);

    DLLLOCAL const QoreTypeInfo* getQoreType(jclass cls, const QoreTypeInfo*& altType,
            QoreProgram* pgm = nullptr, bool literal = false);

    DLLLOCAL const QoreTypeInfo* getQoreType(jclass cls, QoreProgram* pgm = nullptr, bool literal = false) {
        const QoreTypeInfo* altType = nullptr;
        return getQoreType(cls, altType, pgm, literal);
    }

    DLLLOCAL QoreNamespace& getJniNs() {
        return *default_jns;
    }

    DLLLOCAL jobject getJavaObject(const QoreObject* o);
    DLLLOCAL jobject getJavaClosure(const ResolvedCallReferenceNode* call);

    DLLLOCAL jarray getJavaArray(const QoreListNode* l, jclass cls, JniExternalProgramData* jpc = nullptr);

    // takes an internal name (ex: java/lang/Class)
    DLLLOCAL jclass findLoadClass(const char* name, QoreProgram* pgm = nullptr);
    // takes an internal name (ex: java/lang/Class)
    DLLLOCAL jclass findLoadClass(const QoreString& name, QoreProgram* pgm = nullptr);

    DLLLOCAL JniQoreClass* findCreateQoreClass(Env& env, LocalReference<jclass>& jc, QoreProgram* pgm);

    // create the Qore class from the Java binary name (ex: java.lang.Object)
    DLLLOCAL JniQoreClass* findCreateQoreClass(Env& env, const char* name, QoreProgram* pgm,
            JniExternalProgramData* jpc = nullptr);

    DLLLOCAL JniQoreClass* findCreateQoreClass(Env& env, QoreString& name, const char* jpath, Class* c, bool base,
            QoreProgram* pgm) {
        //printd(5, "QoreJniClassMap::findCreateQoreClass() '%s' base: %d pgm: %p\n", jpath, base, pgm);
        return base
            ? findCreateQoreClassInBase(env, name, jpath, c, pgm)
            : findCreateQoreClassInProgram(name, jpath, c, pgm);
    }

    /** @param name an input/output variable, on input it is the java name for the class, which could
        be an inner class (ex: MyClass$1), on output it is the Qore name for the class (ex: MyClass_1)
        @param jpath the java path to the class
        @param c the Java class object

        @return the new builtin Qore class object wrapping the Java class
    */
    DLLLOCAL JniQoreClass* findCreateQoreClassInProgram(QoreString& name, const char* jpath, Class* c,
        QoreProgram* pgm = nullptr);

    //! Ensures a base class is available in the calling Program's namespace
    /** When a base class is already in the global cache, it may not yet be in the calling
        Program's namespace. This method checks and adds it if needed.
        @note Must be called with the global lock held.
    */
    DLLLOCAL void addClassToProgram(JniQoreClass* qc, const char* jpath, QoreProgram* pgm);

    DLLLOCAL static LocalReference<jclass> getPrimitiveType(qore_type_t t);

protected:
    // map of java class names to const QoreTypeInfo ptrs
    typedef std::map<const std::string, const QoreTypeInfo*> jtmap_t;
    // map for Qore types from Java classes
    static jtmap_t jtmap;

    // struct of type info and primitive type descriptor strings
    struct qore_java_primitive_info_t {
        const QoreTypeInfo* typeInfo;
        const char* descriptor;
    };
    // map of java primitive type names to type and descriptor info
    typedef std::map<const char*, struct qore_java_primitive_info_t, ltstr> jpmap_t;
    static jpmap_t jpmap;

    // map of Qore base types to java classes
    typedef std::map<qore_type_t, GlobalReference<jclass>> qt2jmap_t;
    static qt2jmap_t qt2jmap;

    // parent namespace for jni module functionality
    QoreNamespace* default_jns = new QoreNamespace("Jni");

    // class loader
    GlobalReference<jobject> baseClassLoader;

    DLLLOCAL void doMethods(JniQoreClass& qc, Class* jc, QoreProgram* pgm = nullptr);

    DLLLOCAL void doFields(JniQoreClass& qc, Class* jc, QoreProgram* pgm = nullptr);

    DLLLOCAL void doConstructors(JniQoreClass& qc, Class* jc, QoreProgram* pgm = nullptr);

    // add Java parent classes and interfaces as Qore parent classes
    DLLLOCAL void addSuperClasses(JniQoreClass* qc, Class* jc, const char* jpath, QoreProgram* pgm = nullptr,
        JniExternalProgramData* jpc = nullptr);

    // populate the Qore class with methods and members from the Java class
    DLLLOCAL void populateQoreClass(JniQoreClass& qc, Class* jc, QoreProgram* pgm = nullptr);

    DLLLOCAL void addSuperClass(Env& env, JniQoreClass& qc, Class* parent, bool interface,
        QoreProgram* pgm = nullptr, JniExternalProgramData* jpc = nullptr);

    DLLLOCAL JniQoreClass* createClassInNamespace(QoreNamespace* ns, QoreNamespace& jns, const char* jpath,
        Class* jc, JniQoreClass* qc, QoreJniClassMapBase& map, QoreProgram* pgm);
    DLLLOCAL JniQoreClass* findCreateQoreClassInBase(Env& env, QoreString& name, const char* jpath, Class* c, QoreProgram* pgm);
    DLLLOCAL Class* loadClass(Env& env, const char* name, bool& base, JniExternalProgramData* jpc = nullptr);

private:
    // initialization flag
    static bool init_done;

    DLLLOCAL void initIntern(QoreProgram* pgm);

    DLLLOCAL jarray getJavaArrayIntern(Env& env, const QoreListNode* l, jclass cls, JniExternalProgramData* jpc);

    DLLLOCAL Class* loadProgramClass(Env& env, const char* name, JniExternalProgramData* jpc);

#ifdef JNI_INIT_BACKGROUND
    static std::mutex init_mutex;
    static std::condition_variable init_cond;

    DLLLOCAL static void staticInitBackground(ExceptionSink* xsink, void* pgm);

    class InitSignaler {
    public:
        DLLLOCAL ~InitSignaler() {
            init_cond.notify_one();
        }

        std::lock_guard<std::mutex> init_guard();
    };
#endif
};

extern QoreJniClassMap qjcm;

//! access code modifiers
enum qore_method_type_t {
    QMT_CONSTRUCTOR = (1 << 0),
    QMT_NORMAL = (1 << 1),
    QMT_STATIC = (1 << 2),
};

class QoreJavaParamHelper {
public:
    DLLLOCAL QoreJavaParamHelper(Env& env, const char* mname, jclass parent_class, const QoreClass* qore_parent);

    DLLLOCAL void add(LocalReference<jobject>& params);

    // returns 0 if the types are not equal, -1 (skip) if they are equal
    DLLLOCAL int checkVariant(LocalReference<jobject>& params, qore_method_type_t method_type);

private:
    Env& env;
    const char* mname;
    jclass parent_class;
    const QoreClass* qore_parent;
    LocalReference<jobject> plist;
};

class JniExternalProgramData : public AbstractQoreProgramExternalData, public QoreJniClassMapBase {
public:
    DLLLOCAL JniExternalProgramData(QoreNamespace* n_jni, QoreProgram* pgm);

    DLLLOCAL JniExternalProgramData(const JniExternalProgramData& parent, Env& env, QoreProgram* pgm);

    // delete the copy constructor
    JniExternalProgramData(const JniExternalProgramData& parent) = delete;

    DLLLOCAL virtual ~JniExternalProgramData();

    DLLLOCAL jobject getClassLoader() const {
        return classLoader;
    }

    DLLLOCAL void setClassLoader(jobject classLoader) {
        this->classLoader = classLoader;
    }

    DLLLOCAL jclass getDynamicApi() const {
        assert(dynamicApi);
        return dynamicApi;
    }

    DLLLOCAL jmethodID getNewInstanceId() const {
        assert(methodQoreJavaDynamicApiNewInstance);
        return methodQoreJavaDynamicApiNewInstance;
    }

    DLLLOCAL jmethodID getInvokeMethodId() const {
        assert(methodQoreJavaDynamicApiInvokeMethod);
        return methodQoreJavaDynamicApiInvokeMethod;
    }

    DLLLOCAL jmethodID getInvokeMethodNonvirtualId() const {
        assert(methodQoreJavaDynamicApiInvokeMethodNonvirtual);
        return methodQoreJavaDynamicApiInvokeMethodNonvirtual;
    }

    DLLLOCAL jmethodID getGetConnectionMethodId() const {
        assert(methodQoreJavaDynamicApiGetConnection);
        return methodQoreJavaDynamicApiGetConnection;
    }

    DLLLOCAL jmethodID getFieldId() const {
        assert(methodQoreJavaDynamicApiGetField);
        return methodQoreJavaDynamicApiGetField;
    }

    DLLLOCAL QoreNamespace* getJniNamespace() const {
        return jni;
    }

    DLLLOCAL void addClasspath(const char* path);
    DLLLOCAL void addParentClasspath(const char* path);

    // returns Java byte code (byte[]) for a Qore class
    DLLLOCAL LocalReference<jbyteArray> generateByteCode(Env& env, jobject class_loader,
            const QoreString& qpath, jstring jname, const char* module, const QoreClass* qc);

    // returns a type description for a concrete type or a future type for Java bytecode generation
    DLLLOCAL LocalReference<jobject> getJavaTypeDefinition(Env& env, jobject class_loader, const QoreTypeInfo* ti, bool no_void = false);

    DLLLOCAL void overrideCompatTypes(bool compat_types) {
        override_compat_types = true;
        this->compat_types = compat_types;
        //printd(5, "JniExternalProgramData::overrideCompatTypes(%d) this: %p\n", compat_types, this);
    }

    DLLLOCAL bool getCompatTypes() const {
        return override_compat_types ? compat_types : jni_compat_types;
    }

    DLLLOCAL void setSaveObjectCallback(const ResolvedCallReferenceNode* save_object_callback) {
        if (this->save_object_callback) {
            this->save_object_callback->deref(nullptr);
        }
        this->save_object_callback = save_object_callback ? save_object_callback->refRefSelf() : nullptr;
    }

    DLLLOCAL ResolvedCallReferenceNode* getSaveObjectCallback() const {
        return save_object_callback;
    }

    DLLLOCAL virtual AbstractQoreProgramExternalData* copy(QoreProgram* pgm) const {
        // issue #3862: ensure that the thread is registered for this call
        Env env;
        return new JniExternalProgramData(*this, env, pgm);
    }

    DLLLOCAL virtual void doDeref();

    DLLLOCAL void clearCompilationCache() {
        //printd(5, "JniExternalProgramData::clearCompilationCache() clearing %d entries\n", (int)q2jmap.size());
        q2jmap.clear();
    }

    DLLLOCAL void saveClass(const QoreClass& qc, LocalReference<jclass> jcls);

    DLLLOCAL jclass findJavaClass(const QoreClass& qc);

    DLLLOCAL LocalReference<jclass> getClassForValue(const QoreObject* o);

    DLLLOCAL LocalReference<jclass> getJavaClassForQoreClass(Env& env, const QoreClass* qc);

    // Returns a Java object corresponding to the given Qore object
    /** A Java class for the given Qore class is created dynamically if necessary
     */
    DLLLOCAL LocalReference<jobject> getJavaObject(const QoreObject* o);

    DLLLOCAL LocalReference<jstring> getJavaNameForClass(Env& env, const QoreClass& qc);

    DLLLOCAL bool addInjectedModule(const char* mod);

    DLLLOCAL bool isInjectedModule(const char* mod) const;

    DLLLOCAL static JniExternalProgramData* setContext(QoreProgram*& pgm) {
        Env env;
        return setContext(env, pgm);
    }

    DLLLOCAL static JniExternalProgramData* setContext(Env& env);

    DLLLOCAL static JniExternalProgramData* setContext(Env& env, QoreProgram*& pgm);

    DLLLOCAL static bool compatTypes();

    // get / create JNI program data in the given Qore program
    DLLLOCAL static JniExternalProgramData* getCreateJniProgramData(QoreProgram* pgm);

    // try to get the QoreClass for a dynamically-created JavaClass
    DLLLOCAL static QoreClass* tryGetQoreClass(Env& env, jclass obj, bool inherited);

    // load service loader
    DLLLOCAL LocalReference<jobject> loadServiceLoader(Env& env, jclass jcls);

protected:
    // owning QoreProgram object
    QoreProgram* pgm;
    // Jni namespace pointer for the current Program
    QoreNamespace* jni;
    // class loader
    GlobalReference<jobject> classLoader;
    // dynamic API class
    GlobalReference<jclass> dynamicApi;
    // call reference for saving object references
    ResolvedCallReferenceNode* save_object_callback = nullptr;

    // QoreJavaDynamicApi.newInstance()
    jmethodID methodQoreJavaDynamicApiNewInstance = 0;
    // QoreJavaDynamicApi.invokeNethod()
    jmethodID methodQoreJavaDynamicApiInvokeMethod = 0;
    // QoreJavaDynamicApi.invokeNethodNonvirtual()
    jmethodID methodQoreJavaDynamicApiInvokeMethodNonvirtual = 0;
    // QoreJavaDynamicApi.getField()
    jmethodID methodQoreJavaDynamicApiGetField = 0;
    // QoreJavaDynamicApi.loadServiceLoader()
    jmethodID methodQoreJavaDynamicApiLoadServiceLoader = 0;
    // QoreJavaDynamicApi.getConnection()
    jmethodID methodQoreJavaDynamicApiGetConnection = 0;

    // code generation mutex
    QoreRecursiveThreadLock codeGenLock;

    // map of Qore class hashes to Java classes; class signature hash -> jclass
    /** codeGenLock must be held when accessing this data
     */
    typedef std::map<std::string, GlobalReference<jclass>> q2jmap_t;
    q2jmap_t q2jmap;

    // map of paths to fake "$" Qore classes
    typedef std::map<std::string, QoreBuiltinClass*> fake_cls_map_t;
    fake_cls_map_t fake_cls_map;

    // set of Java classes currently being created
    strset_t in_progress_set;

    // override compat-types
    bool override_compat_types = false;
    // compat-types values
    bool compat_types = false;

    // injected module set
    strset_t injected_module_set;
    mutable QoreThreadLock injected_module_lock;

    // returns true if Java bytecode class creation is in progress
    /** @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL bool isCreateInProgress(const std::string& qpath) const {
        return in_progress_set.find(qpath) != in_progress_set.end();
    }

    // sets the given class as creation in progress
    /** @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL void setCreateInProgress(const std::string& qpath) {
        in_progress_set.insert(qpath);
    }

    // clears the given class as creation in progress
    /** @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL void clearCreateInProgress(const std::string& qpath) {
        strset_t::iterator i = in_progress_set.find(qpath);
        assert(i != in_progress_set.end());
        in_progress_set.erase(i);
    }

    // initializes the dynamic API in the constructor
    DLLLOCAL void initDynamicApi(Env& env);

    // returns Java byte code (byte[]) for the given Qore class
    DLLLOCAL LocalReference<jbyteArray> generateByteCodeIntern(Env& env, jobject class_loader,
        const QoreClass* qcls, jstring jname = nullptr);

    // returns Java byte code (byte[]) for a wrapper class for Qore functions implemneted as static methods
    DLLLOCAL LocalReference<jbyteArray> generateFunctionClassIntern(Env& env, jobject class_loader, QoreProgram* pgm,
        jstring jname, const char* module, const char* ns_path = nullptr);

    // returns Java byte code (byte[]) for a wrapper class for Qore constants implemneted as static fields
    DLLLOCAL LocalReference<jbyteArray> generateConstantClassIntern(Env& env, jobject class_loader, QoreProgram* pgm,
        jstring jname, const char* module, const char* ns_path = nullptr);

    // Returns a param list of Java type corresponding to the Qore types
    DLLLOCAL jobject getJavaParamList(Env& env, jobject class_loader, const QoreExternalVariant& v,
        unsigned& len, bool do_varargs, bool is_abstract = false);

    DLLLOCAL int addConstructorVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v, jclass parent_class,
        QoreJavaParamHelper& jph);

    DLLLOCAL int addNormalMethodVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v,
        QoreJavaParamHelper& jph);

    DLLLOCAL int addStaticMethodVariant(Env& env, jobject class_loader, const QoreClass& qcls,
        LocalReference<jobject>& bb, const QoreMethod& m, const QoreExternalMethodVariant& v,
        QoreJavaParamHelper& jph);

    DLLLOCAL int addFunctionVariant(Env& env, jobject class_loader, LocalReference<jobject>& bb,
        const QoreExternalFunction& func, const QoreExternalVariant& v, QoreProgram* pgm, QoreJavaParamHelper& jph);

    DLLLOCAL int addStaticMethods(Env& env, jobject class_loader,
        const QoreClass& qcls, const QoreMethod& m, QoreJavaParamHelper& jph, LocalReference<jobject>& bb);

    DLLLOCAL int addMethods(Env& env, jobject class_loader, const QoreClass& qcls, LocalReference<jobject>& bb,
        jclass parent_class, const QoreClass* qore_parent, strset_t& mset, const QoreClass* other_base = nullptr);

    DLLLOCAL int addFunctions(Env& env, jobject class_loader, const QoreNamespace& ns, LocalReference<jobject>& bb,
        QoreProgram* pgm);

    DLLLOCAL int addConstants(Env& env, jobject class_loader, jstring jname, const QoreNamespace& ns,
        LocalReference<jobject>& bb, QoreProgram* pgm);

    DLLLOCAL int addClassConstants(Env& env, jstring jname, const QoreClass& qcls,
        LocalReference<jobject>& bb, QoreProgram* pgm);
};

DLLLOCAL QoreProgram* jni_get_program_context();
DLLLOCAL JniExternalProgramData* jni_get_context();
DLLLOCAL JniExternalProgramData* jni_get_context(QoreProgram*& pgm);

DLLLOCAL JniExternalProgramData* jni_get_context_unconditional();
DLLLOCAL JniExternalProgramData* jni_get_context_unconditional(QoreProgram*& pgm);

}

#endif
