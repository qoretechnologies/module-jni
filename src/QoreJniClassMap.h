/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJniClassMap.h

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

#ifndef _QORE_JNI_QOREJAVACLASSMAP_H

#define _QORE_JNI_QOREJAVACLASSMAP_H

#include "QoreJniPrivateData.h"
#include "Env.h"
#include "Class.h"
#include "JniQoreClass.h"
#include "ql_jni_debug.h"

#include <atomic>
#include <set>
#include <map>
#include <mutex>
#include <optional>
#include <condition_variable>

typedef std::set<std::string> strset_t;

DLLLOCAL QoreClass* initJavaArrayClass(QoreNamespace& ns);
//! creates the JavaArray class before its methods are added, as Java classes created before it use its type
DLLLOCAL void preinitJavaArrayClass();
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
class GeneratedBindingLease;
class JniContextProgramHolder;

class QoreJniClassMapBase {
protected:
    // map of java class names (ex 'java/lang/Object') to JniQoreClass ptrs
    typedef std::map<std::string, JniQoreClass*> jcmap_t;
    // map of java class names (ex 'java/lang/Object') to JniQoreClass ptrs
    jcmap_t jcmap;

public:
    //! Per-class "creation in progress" marker (see createClassInNamespace()).
    /** A Qore class is built from its Java class in two Java-calling steps (addSuperClasses() +
        populateQoreClass()) that can drive classloading and therefore module loading.  The global
        class-map lock QoreJniClassMap::m must NOT be held across those steps (holding a global lock
        across a module load reintroduces the module-load deadlock).  This marker replaces the old
        "publish the half-built class into the map early, under m" approach: the builder installs a
        marker (under m), releases m, builds the class, then re-acquires m to publish.  Concurrent
        creators of the same class wait on QoreJniClassMap::class_create_cond and, on wake, re-resolve
        the finished class through the namespace (so no result/exception needs to be stored on the
        marker); if the owner failed, the marker is simply gone and the waiter retries the build
        itself.  Same-thread recursion (a class that refers back to itself while it is being
        populated) resolves to \a partial without waiting.
    */
    struct ClassCreateInProgress {
        int tid;                            //!< the thread building the class
        JniQoreClass* partial;              //!< the not-yet-populated class (same-thread self-reference)
    };
    // markers keyed by java path (ex 'java/lang/Object'); guarded by QoreJniClassMap::m
    typedef std::map<std::string, ClassCreateInProgress*> in_progress_class_map_t;
    in_progress_class_map_t jcmap_inprogress;

    DLLLOCAL void add(const char* name, JniQoreClass* qc) {
        printd(LogLevel, "QoreJniClassMapBase::add() this: %p name: %s qc: %p (%s)\n", this, name, qc, qc->getName());

#ifdef DEBUG
        if (jcmap.find(name) != jcmap.end())
            printd(0, "QoreJniClassMapBase::add() name: %s qc: %p (%s)\n", name, qc, qc->getName());
#endif

        assert(jcmap.find(name) == jcmap.end());
        jcmap[name] = qc;
    }

    //! Replaces an existing mapping (used when a duplicate class is detected after the initial add)
    DLLLOCAL void replace(const char* name, JniQoreClass* qc) {
        printd(LogLevel, "QoreJniClassMapBase::replace() this: %p name: %s qc: %p (%s)\n", this, name, qc,
            qc->getName());
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

//! Acquires the global class-map lock \c m alone, for a short critical section over the class maps.
/** Global lock order (total order, enforced at every site):

        Program parse lock (parseLock) -> codeGenLock -> QoreJniClassMap::m

    \c m is a strict <b>leaf</b> lock: it guards the class maps (the global \c jcmap, every
    program-local \c jpc->jcmap, and \c default_jns) and is held only for short critical sections.
    It is <b>never</b> held across a Java call, a module load, or the acquisition of codeGenLock or a
    Program parse lock.  This is what keeps \c m off the module-load path: holding a global lock
    across a nested module load (reachable whenever Java is called, via the JVM classloader callback
    qore_url_classloader_generate_byte_code() -> ModuleManager::runTimeLoadModule()) reintroduces the
    process-global module-load deadlock.  Class building therefore releases \c m across the
    Java-calling population phase and coordinates concurrent/recursive builders with a per-class
    marker (see QoreJniClassMapBase::ClassCreateInProgress and createClassInNamespace()).

    The per-Program locks (parseLock, codeGenLock) may be held across a module load: they are
    per-Program, so they cannot form the process-global module-load cycle, and the classloader
    callback re-enters on the same thread (recursive).

    The QoreModuleInnerLockHelper member makes the leaf invariant checkable rather than a comment: it
    marks \c m as held, so if any path ever holds \c m across a module load again, libqore asserts on
    the lock-order inversion in debug builds instead of deadlocking in the field.
*/
class JniClassMapLocker {
public:
    DLLLOCAL JniClassMapLocker();

    JniClassMapLocker(const JniClassMapLocker&) = delete;
    JniClassMapLocker& operator=(const JniClassMapLocker&) = delete;

private:
    AutoLocker al_map;
    QoreModuleInnerLockHelper inner_marker;
};

class QoreJniClassMap : public QoreJniClassMapBase {
public:
    // the global class-map lock; see JniClassMapLocker for the lock hierarchy it participates in
    static QoreRecursiveThreadLock m;

    // signalled when a class-creation-in-progress marker completes (guarded by m); see
    // createClassInNamespace() and QoreJniClassMapBase::ClassCreateInProgress.  Threads that wait on
    // this condition must hold m exactly once (m is recursive, and QoreCondition::wait() releases a
    // single level), which the createClassInNamespace() phase-1 critical section guarantees.
    static QoreCondition class_create_cond;

    DLLLOCAL void init(QoreProgram* pgm, bool already_initialized);

    DLLLOCAL void destroy(ExceptionSink& xsink);

    DLLLOCAL QoreValue getValue(LocalReference<jobject>& jobj, QoreProgram* pgm, bool compat_types);

    //! How a Java type is used, which decides its declared %Qore type; see getQoreType()
    enum class JavaTypeUse {
        //! the type of a parameter: a %Qore value is converted to the Java type
        Param,
        //! the type of the variable arguments parameter of an abstract varargs method: as for \c Param, except that
        //! no value is an empty array, as Java passes when there are no variable arguments, not null; a soft list,
        //! as a %Qore class implementing the method declares it
        VarargsParam,
        //! the type of the variable arguments parameter of a varargs method that can be called, given as an array:
        //! a list (or a binary for \c byte[]) or no value, which is an empty array; a single value is not converted
        //! to a list, as it is a variable argument given as a separate argument, which the variant taking the
        //! variable arguments as separate arguments takes (see jni_add_loose_varargs_variants() in
        //! QoreJniClassMap.cpp)
        VarargsArrayParam,
        //! the type of a value converted to %Qore: a return value, a field, or a constant
        Value,
    };

    //! Returns the %Qore type declared for a Java type
    /** <b>The nullability rule</b>: a Java value of a reference type (an object, including a \c String or a boxed
        value, or an array) can be \c null, which is converted to and from no value, so the type declared for a
        parameter, a return value, a field or a constant of a reference type always accepts no value (ex:
        \c *string for \c java.lang.String, <tt>*softlist<int></tt> for \c int[]); only a primitive type (ex: \c int,
        \c boolean) has a type that does not.  %Qore checks the value read from a
        field against the field's declared type at run time, and uses the declared types of return values and
        constants at parse time, so a type that does not accept no value raised \c RUNTIME-TYPE-ERROR for a
        \c null field and narrows wrongly at parse time.  The declared type of a value also accepts every value the
        conversion yields (ex: \c *int for \c java.lang.Character, \c auto for \c java.lang.Number, a byte array
        is a binary or a list of integers when compatibility types are in effect).

        <b>Parameters: a %Qore argument selects the variant Java would select for the converted value.</b>  A
        parameter takes every %Qore value that QoreToJava converts to an object of its class (ex: a string for a
        \c java.lang.CharSequence, an integer, converted to a \c java.lang.Long, for a \c java.lang.Number; see
        jni_get_converted_param_type() in QoreJniClassMap.cpp), and its declared type ranks above the \c auto of an
        \c Object parameter, as Java prefers the more specific class; an integer is not taken for a
        \c java.lang.Character, as Java does not convert an \c int to a \c Character.  A parameter of a class with a
        mapped %Qore type also has an alternative variant taking the Java object (\a altType).  A call passing no
        value for overloads that all accept it selects the first variant added (see jni_get_variants()), which is the
        variant of the most specific class, as in Java (ex: <tt>m(String)</tt> before <tt>m(Object)</tt>).  The
        variable arguments array of a varargs method takes no value as an empty array, as
        Java passes when there are no variable arguments; a varargs method that can be called also has a variant
        taking the variable arguments as separate arguments (see jni_add_loose_varargs_variants() in
        QoreJniClassMap.cpp).  See design/java-qore-type-mapping.md.

        A Java list (a class implementing \c java.util.List) is declared as a %Qore list, as every Java list is
        converted to one.  A Java map (a class implementing \c java.util.Map) is converted to a %Qore hash when all its
        keys are strings, so a parameter that takes a map is declared as a hash, and a return value or field is
        declared as a hash only if its declared key type is \c String (see org.qore.jni.JavaCollectionTypes),
        otherwise as \c auto, as it is a hash or a Java object depending on its keys.  A return value or field of a
        class that lists or maps implement or extend (ex: \c java.util.Collection, \c java.io.Serializable) is
        declared as \c auto, as it is a %Qore list or hash when it is a list or a map.  For a list or a map parameter,
        \a altType is set to the type of the Java class, so that a Java object is accepted as well.

        A value of a class that the classes of values converted to %Qore values that are not objects implement or
        extend (ex: \c java.lang.Number, \c java.lang.CharSequence, \c java.lang.Comparable,
        \c java.time.temporal.Temporal, \c java.util.Date) is declared as \c auto (see jni_may_be_converted_value()
        in QoreJniClassMap.cpp).

        @param cls the Java class
        @param altType for a parameter: set to the type of the Java class when a variant taking the Java object is
        needed; not set for a value, whose declared type accepts the Java object if it can be one
        @param pgm the program the type is resolved in
        @param use how the type is used
        @param member for a value: the java.lang.reflect.Method returning it or the java.lang.reflect.Field holding
        it, whose generic type gives the key type of a map; if null, the class gives it
    */
    DLLLOCAL const QoreTypeInfo* getQoreType(jclass cls, const QoreTypeInfo*& altType,
            QoreProgram* pgm = nullptr, JavaTypeUse use = JavaTypeUse::Param, jobject member = nullptr);

    //! Returns the %Qore type declared for a Java value; see getQoreType(jclass, const QoreTypeInfo*&, ...)
    DLLLOCAL const QoreTypeInfo* getQoreType(jclass cls, QoreProgram* pgm = nullptr,
            JavaTypeUse use = JavaTypeUse::Value, jobject member = nullptr) {
        const QoreTypeInfo* altType = nullptr;
        return getQoreType(cls, altType, pgm, use, member);
    }

    //! Returns the %Qore type of an element of the variable arguments array of a varargs method
    /** This is the type of the elements of the array as a parameter (ex: \c *string for \c String..., \c int for
        \c long..., \c auto for \c Object...), which a variable argument given as a separate argument has.

        @param array_cls the Java array class of the variable arguments parameter
        @param pgm the program the type is resolved in
    */
    DLLLOCAL const QoreTypeInfo* getVarargsElementType(jclass array_cls, QoreProgram* pgm = nullptr);

    //! Returns a new copy of the \c Jni namespace that a Program gets when the module is loaded into it
    /** The copy holds only the classes the module creates when it is initialized (\c java.lang.Object,
        \c java.lang.Class, the other bootstrap classes and the \c org::qore::jni API), and none of the classes that
        any Program imported afterwards: an import is visible only in the Program that made it (and in the
        child Programs that inherit that Program's classes).

        The classes that Programs import are created in \c default_jns, which owns them for every Program that
        imports them; it is therefore never copied into a Program after initialization.  Before initialization is
        complete, the copy is made from \c default_jns, which then only holds the bootstrap classes.

        @return a new namespace owned by the caller
    */
    DLLLOCAL QoreNamespace* copyProgramJniNs() {
        // the template is immutable once it is set, so it can be copied without the class-map lock
        if (program_jns) {
            return program_jns->copy();
        }
        // before initialization is complete, the classes are created by the initializing thread only
        return default_jns->copy();
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
    //! returns the declared %Qore type of a Java class; called by getQoreType(), which completes a value's type
    DLLLOCAL const QoreTypeInfo* getQoreTypeIntern(Env& env, jclass cls, const QoreTypeInfo*& altType,
            QoreProgram* pgm, JavaTypeUse use, jobject member);

    // map of java class names to their declared Qore types; see jtmap in QoreJniClassMap.cpp
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

    // parent namespace for jni module functionality; the global owner of every class loaded by the base class
    // loader, which every Program that imports such a class gets a copy of; never copied into a Program after
    // initialization (see program_jns)
    QoreNamespace* default_jns = new QoreNamespace("Jni");

    // the Jni namespace as it is after initialization, which every new Program gets a copy of; set once at the
    // end of initIntern() and never changed afterwards, so that a class imported by one Program is not visible in
    // Programs created afterwards
    QoreNamespace* program_jns = nullptr;

    // class loader
    GlobalReference<jobject> baseClassLoader;

    DLLLOCAL void doMethods(JniQoreClass& qc, Class* jc, QoreProgram* pgm = nullptr);
    DLLLOCAL void doMethodsIntern(JniQoreClass& qc, Class* jc, QoreProgram* pgm, Env& env,
        jobjectArray mArray);

    //! Adds a variant for an inherited public Java method with fixed parameters and the given parameter types
    /** A variant that a parent class has for a public Java method with fixed parameters (ex: \c A.m(String)) is added
        to the class, calling the Java method, when the class has a varargs method with the same name (ex:
        \c B.m(String...)) whose variant taking the variable arguments as separate arguments would have its
        parameter types: %Qore calls a variant of the class before one of its parents, so that variant would be
        called instead of the parent's, while Java calls the method with fixed parameters.  Bridge methods are not
        used, and if several methods have the parameter types, the method is chosen as for the methods of a class
        (see jni_choose_variant() in QoreJniClassMap.cpp).

        @param qc the class
        @param jc the Java class
        @param name the name of the %Qore method
        @param is_static true for a static method
        @param params the parameter types
        @param pgm the program the types are resolved in

        @return true if the variant was added; false if no public Java method with fixed parameters has the
        parameter types (ex: the parent's variant is one of a varargs method)
    */
    DLLLOCAL bool addInheritedFixedArityVariant(JniQoreClass& qc, Class* jc, const char* name, bool is_static,
            const type_vec_t& params, QoreProgram* pgm);

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

    //! Build-time lookup that resolves a same-thread in-progress class to its partial placeholder.
    /** Returns the fully-created class for \a jpath in \a map, or the not-yet-populated partial if
        \a jpath is currently being created by the calling thread (same-thread self-reference), or
        nullptr otherwise.  Must be called with \c m NOT held (it takes \c m briefly).

        Half-built classes are never published to the class map (only recorded in the in-progress
        marker), so a plain find() never returns a partial.  This helper restores, for the build-time
        lookups that must short-circuit a self-reference (addSuperClass(), getQoreType()), the
        behavior the old code got by publishing the half-built class into the map under a
        globally-held \c m: without it, a self-reference would re-enter class creation and copy a
        partial class into a Program via addClassToProgram().  Other-thread in-progress classes
        return nullptr here; the caller then re-enters createClassInNamespace(), which waits for the
        concurrent build to complete.
    */
    DLLLOCAL JniQoreClass* findClassOrSelfPartial(QoreJniClassMapBase& map, const char* jpath);
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

    // returns true if the parent class has a final method with matching name and params
    DLLLOCAL bool isFinalMethod(const char* name, LocalReference<jobject>& params);

    //! Returns the method of a Java parent class or interface that a %Qore method variant overrides, if any
    /** A %Qore variant overrides a Java method with the same name, declared in a Java class or interface that the
        %Qore class inherits, other than \c java.lang.Object, if its parameter types are those of a variant that the jni
        module declares for the Java method (see QoreJniClassMap::getQoreType())

        @param name the name of the method
        @param v the variant
        @param pgm the program
        @param class_loader the class loader of the class being generated
        @param qcls the %Qore class being generated, whose Java parent classes and interfaces are searched

        @return the java.lang.reflect.Method object, or no value
    */
    DLLLOCAL LocalReference<jobject> findOverriddenMethod(const char* name, const QoreExternalMethodVariant& v,
            QoreProgram* pgm, jobject class_loader, const QoreClass& qcls);

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

    //! Adds a classpath to the class loader
    /** @param env the JNI environment
        @param path the classpath
        @param source the input that the classpath comes from (ex: the module command), for the error
        @param checked the classpath as a filesystem policy checked it (the result of jni_check_classpath_policy()):
        if not null, exactly its files are added, and \a path is not resolved again (qore#6818); if null (no policy
        governs the classpath), \a path is added

        @throw JNI-CLASSPATH-ERROR the class loader raised a Java exception while the classpath was added (qore#6779)
    */
    DLLLOCAL void addClasspath(Env& env, jstring path, const char* source, jobject checked);
    //! Adds a classpath (the Java string that was checked) to the parent class loader
    /** With no parent class loader that is a QoreURLClassLoader, the classpath is added to this class loader; see
        addClasspath() for the parameters and the error
    */
    DLLLOCAL void addParentClasspath(Env& env, jstring path, const char* source);

private:
    //! Releases the class loader of a root Program's jni data whose constructor failed
    DLLLOCAL void releaseFailedClassLoader(Env& env) noexcept;

public:

    // returns Java byte code (byte[]) for a Qore class
    DLLLOCAL LocalReference<jbyteArray> generateByteCode(Env& env, jobject class_loader,
            const QoreString& qpath, jstring jname, const char* module, const QoreClass* qc);

    // returns a raw class type description for Java bytecode generation
    DLLLOCAL LocalReference<jobject> getJavaRawClassTypeDefinition(Env& env, jobject class_loader,
        const QoreClass* cls);

    // returns a type description for a concrete type or a future type for Java bytecode generation
    DLLLOCAL LocalReference<jobject> getJavaTypeDefinition(Env& env, jobject class_loader,
        const QoreTypeInfo* ti, bool no_void = false, const QoreClass* generic_context = nullptr,
        bool generic_position = false);

    DLLLOCAL void overrideCompatTypes(bool compat_types) {
        compat_types_override.store(compat_types ? 1 : 0, std::memory_order_relaxed);
        //printd(5, "JniExternalProgramData::overrideCompatTypes(%d) this: %p\n", compat_types, this);
    }

    DLLLOCAL bool getCompatTypes() const {
        int setting = compat_types_override.load(std::memory_order_relaxed);
        return setting < 0 ? jni_compat_types : setting != 0;
    }

    //! Returns the lock of the save object callbacks of all Programs
    /** Held only to swap a callback or take a reference to it.  One process-wide lock, so that the module's fork handler
        can initialize it again in a child process created with fork(): a thread of the parent can have held it, while
        the threads that the child starts must still exclude each other.  Never destroyed, as a Program's data can be
        destroyed during the process's static destruction; created by Jvm::registerForkHandler(), as the fork handler
        cannot allocate memory
    */
    DLLLOCAL static QoreThreadLock& saveObjectCallbackLock();

    //! Sets the save object callback, releasing the old one
    /** The old callback is released after the new one is set, as releasing it can run Qore code, for example the
        destructor of an object that only the callback holds; an exception raised by that code is raised in \a xsink;
        if the old callback is running (see getSaveObjectCallbackRef()), its last reference is released, and such an
        exception is raised, by save_object() when it returns
    */
    DLLLOCAL void setSaveObjectCallback(const ResolvedCallReferenceNode* save_object_callback, ExceptionSink* xsink) {
        ResolvedCallReferenceNode* new_callback = save_object_callback ? save_object_callback->refRefSelf()
            : nullptr;
        ResolvedCallReferenceNode* old;
        // the member is swapped under the lock (qore#6790); the old callback is released after the lock is released,
        // as its release can run Qore code, which could set the callback again
        {
            AutoLocker al(saveObjectCallbackLock());
            old = this->save_object_callback;
#ifdef DEBUG
            if (dbg_jni_save_object_gate_set.load(std::memory_order_acquire)) {
                dbg_jni_run_save_object_gate();
            }
#endif
            this->save_object_callback = new_callback;
        }
        if (old) {
            old->deref(xsink);
        }
    }

    //! Returns a new reference to the save object callback, or nullptr if there is none
    /** The caller owns the reference and must release it with deref(), with no lock held, as releasing the last
        reference to the callback can run Qore code; the reference keeps the callback valid while it runs, also if the
        callback is replaced or removed meanwhile by setSaveObjectCallback(), by another thread or by the callback
        itself (qore#6790)
    */
    DLLLOCAL ResolvedCallReferenceNode* getSaveObjectCallbackRef() const {
        AutoLocker al(saveObjectCallbackLock());
        return save_object_callback ? save_object_callback->refRefSelf() : nullptr;
    }

    //! Returns the jni data of a child Program created from the Program of this object
    /** Never throws: libqore calls it from the constructor of the child Program, where a C++ exception ends the
        process in std::terminate(), and it cannot report an error, so if the data cannot be copied, the error is
        reported on stderr, and nullptr is returned: the child Program is created without the jni data of its parent
        (qore#6787)
    */
    DLLLOCAL virtual AbstractQoreProgramExternalData* copy(QoreProgram* pgm) const noexcept;

    DLLLOCAL virtual void doDeref();

    //! Clears the compilation cache (the map of Qore classes to Java classes)
    /** The cache is guarded by codeGenLock, which this never waits for (qore#6855): it is taken by the thread that
        generates code, which can wait for a Java compiler running on another thread, and that compiler clears the
        cache when it ends.  So the cache is cleared at once if codeGenLock is free or held by this thread; otherwise
        the clear is left to the thread that holds it, which clears the cache when it releases its outermost hold
        (see CodeGenLocker), after the code it generates, as if the clear had come after it.
    */
    DLLLOCAL void clearCompilationCache();

#ifdef DEBUG
    //! Returns the number of entries in the compilation cache (DEBUG builds only, for tests)
    DLLLOCAL size_t getCompilationCacheSizeForTest();

    //! Returns the save object callback lock, for a debug hold (see Globals::dbgHoldContext())
    DLLLOCAL QoreThreadLock& dbgSaveObjectLock() {
        return saveObjectCallbackLock();
    }
#endif

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

    //! Sets the thread's Java context to the class loader of the Program context, and returns its jni data
    /** As jni_get_context_unconditional(), \a pgm is set to the Program, which can be the global Java context
        Program, held by \a holder then, which the caller keeps for as long as it uses the Program or its jni data
        (qore#6829)
    */
    DLLLOCAL static JniExternalProgramData* setContext(QoreProgram*& pgm, JniContextProgramHolder& holder) {
        Env env;
        return setContext(env, pgm, holder);
    }

    //! Sets the thread's Java context to the class loader of the Program context
    DLLLOCAL static void setContext(Env& env);

    DLLLOCAL static JniExternalProgramData* setContext(Env& env, QoreProgram*& pgm, JniContextProgramHolder& holder);

    //! Sets the Java context of a thread that has just been attached to the JVM (see jni::Env's constructor)
    /** As setContext(), but once the module's shutdown has started (see Globals::isShuttingDown()), a thread with no
        Program context gets no Java context: the global Java context Program, which is being deleted or has been
        deleted, is not created again; a Program created then would never be deleted (qore#6799).  The shutdown starts
        just before the global context Program is deleted, so a thread newly attached to the JVM between the start of
        the shutdown and the deletion gets no Java context either, which matters little, as Java calls of %Qore code
        through proxies are refused then (see QoreCodeDispatcher::dispatch()).
    */
    DLLLOCAL static void setAttachContext(Env& env);

    DLLLOCAL static bool compatTypes();

    //! Returns true if the given Program's jni data exists (it has been created and not yet destroyed)
    /** Reads no data of the Program, so it can be called by a thread that is not in the Program while the Program's
        teardown destroys its jni data
    */
    DLLLOCAL static bool hasProgramData(const QoreProgram* pgm);

    //! Clears the compilation cache of the jni data of the Program, if it has jni data; returns false if not
    /** For a thread that is not in the Program: the data is kept alive by the lock of the registry of the Programs
        with jni data, which the data's destructor takes to unregister it before it is destroyed; as
        clearCompilationCache(), the cache is cleared without the code generation lock, so a concurrent code
        generation for the Program is not serialized with it
    */
    DLLLOCAL static bool clearCompilationCache(const QoreProgram* pgm);

#ifdef DEBUG
    //! Returns the number of entries in the compilation cache of the Program, -1 if it has no jni data (DEBUG builds
    //! only, for tests; waits for its code generation lock, so it must be called while no thread generates code)
    DLLLOCAL static int64 getCompilationCacheSizeForTest(const QoreProgram* pgm);
#endif

    // get / create JNI program data in the given Qore program
    DLLLOCAL static JniExternalProgramData* getCreateJniProgramData(QoreProgram* pgm);

    // try to get the QoreClass for a dynamically-created JavaClass
    DLLLOCAL static std::unique_ptr<GeneratedBindingLease> tryGetQoreClass(Env& env, jclass obj);

    // load service loader
    DLLLOCAL LocalReference<jobject> loadServiceLoader(Env& env, jclass jcls);

protected:
    // owning QoreProgram object
    QoreProgram* pgm;
    // Jni namespace pointer for the current Program
    QoreNamespace* jni;
    // class loader
    GlobalReference<jobject> classLoader;
    //! true if \c classLoader is the system class loader, whose reference \c Globals::syscl owns (bootstrap mode)
    bool borrowedClassLoader = false;
    // dynamic API class
    GlobalReference<jclass> dynamicApi;
    // call reference for saving object references; saveObjectCallbackLock() must be held when accessing it, and is held
    // only to read, reference, or swap the pointer, never while Qore code runs
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
    //! the number of holds of codeGenLock by the thread that holds it; codeGenLock must be held to access it
    unsigned codeGenDepth = 0;
    //! set by clearCompilationCache() when another thread held codeGenLock: that thread clears the compilation cache
    //! when it releases its outermost hold (qore#6855)
    std::atomic<bool> clearPending{false};

    //! Holds codeGenLock for its scope; the outermost hold of a thread runs a pending clear of the compilation cache
    //! when it is taken and when it is released (see clearCompilationCache())
    class CodeGenLocker {
    public:
        DLLLOCAL CodeGenLocker(JniExternalProgramData& data) : data(data) {
            data.codeGenLock.lock();
            // a clear requested while the previous holder was releasing its hold, after it checked for one, is run
            // before the cache is used
            if (!data.codeGenDepth++ && data.clearPending.exchange(false, std::memory_order_acq_rel)) {
                data.q2jmap.clear();
            }
        }

        DLLLOCAL ~CodeGenLocker() {
            if (!--data.codeGenDepth && data.clearPending.exchange(false, std::memory_order_acq_rel)) {
                data.q2jmap.clear();
            }
            data.codeGenLock.unlock();
        }

        CodeGenLocker(const CodeGenLocker&) = delete;
        CodeGenLocker& operator=(const CodeGenLocker&) = delete;

    private:
        JniExternalProgramData& data;
    };

    // map of Qore class hashes to Java classes; class signature hash -> jclass
    /** codeGenLock must be held when accessing this data
     */
    typedef std::map<std::string, GlobalReference<jclass>> q2jmap_t;
    q2jmap_t q2jmap;

    // map of paths to fake "$" Qore classes
    typedef std::map<std::string, QoreBuiltinClass*> fake_cls_map_t;
    fake_cls_map_t fake_cls_map;

    // map of Qore class paths currently being created -> nesting refcount
    /** Refcounted (not a plain set) because the same class path can legitimately be entered
        nested during a single generation pass (e.g. two QoreClass instances that share a
        namespace path, reached through different generation entry points).  A plain set would
        let the inner scope's clear remove the marker the outer scope still needs, both
        dropping the in-progress guard early and tripping the clear assertion.
    */
    typedef std::map<std::string, unsigned> in_progress_map_t;
    in_progress_map_t in_progress_set;

    //! the compat-types setting of the Program: -1 if it is not set (the module's setting applies), else 0 or 1
    /** Set by the set-compat-types module command at any time, and read without a lock by every conversion and by
        the copy for a child Program, so the setting and its value are one atomic value
    */
    std::atomic<int> compat_types_override{-1};

    // injected module set
    strset_t injected_module_set;
    mutable QoreThreadLock injected_module_lock;

    // returns true if Java bytecode class creation is in progress
    /** @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL bool isCreateInProgress(const std::string& qpath) const {
        return in_progress_set.find(qpath) != in_progress_set.end();
    }

    // returns true if Java bytecode creation is in progress for the given class or any of its ancestors
    /** Eagerly generating a Java class requires its entire superclass chain to be defined first
        (a Java subclass cannot be defined before its superclass).  If any ancestor is currently
        being generated, eagerly generating @p cls would recurse back into that in-progress
        ancestor through parent-class resolution and fail with "<class> is already being created".
        In that case the caller must emit a forward (name-based) type reference instead and let
        the class be generated on demand once the ancestor's generation has completed.

        @param cls the Qore class whose inheritance closure is checked
    */
    DLLLOCAL bool isAncestorCreateInProgress(const QoreClass& cls) const {
        if (isCreateInProgress(cls.getNamespacePath())) {
            return true;
        }
        QoreParentClassIterator ci(cls);
        while (ci.next()) {
            if (isAncestorCreateInProgress(ci.getParentClass())) {
                return true;
            }
        }
        return false;
    }

    // marks the given class as creation in progress (increments the nesting refcount)
    /** @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL void setCreateInProgress(const std::string& qpath) {
        ++in_progress_set[qpath];
    }

    // clears one nesting level of creation-in-progress for the given class
    /** the marker remains in effect until the outermost (balancing) call
        @param qpath the full Qore classpath to the Qore class
    */
    DLLLOCAL void clearCreateInProgress(const std::string& qpath) {
        in_progress_map_t::iterator i = in_progress_set.find(qpath);
        assert(i != in_progress_set.end());
        if (!--i->second) {
            in_progress_set.erase(i);
        }
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
        unsigned& len, bool do_varargs, bool is_abstract = false, const QoreClass* generic_context = nullptr);

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

//! Sets the thread's Java context for a read of a Java value only if converting the value runs Java code (qore#6551)
/** A read of a Java field or array element runs no Java code of its own, but converting its value to Qore runs Java
    code for some values (ex: \c entrySet() of a \c Map, \c toString() of a \c java.sql.Timestamp, which a subclass
    can override), and that code runs in the context of the calling code: the read marks the context as pending for
    the conversion, and the conversion sets it, once, with set() before it runs such code, so that a read whose value
    runs no Java code makes no call into Java for the context
*/
class JniPendingContextHelper {
public:
    DLLLOCAL JniPendingContextHelper() : prev(pending) {
        pending = true;
    }

    DLLLOCAL ~JniPendingContextHelper() {
        pending = prev;
    }

    //! Sets the thread's Java context to the context of the calling code if a read on the thread has marked it as
    //! pending; called by the conversion of a value before it runs Java code that the value's class can override
    DLLLOCAL static void set(Env& env) {
        if (pending) {
            // cleared first: the context is set once per read, also if setting it raises an exception
            pending = false;
            JniExternalProgramData::setContext(env);
        }
    }

private:
    bool prev;
    DLLLOCAL static thread_local bool pending;
};

DLLLOCAL QoreProgram* jni_get_program_context();
DLLLOCAL JniExternalProgramData* jni_get_context();

#ifdef DEBUG
//! Returns the lock of the registry of the Programs' jni data, for a debug hold (see Globals::dbgHoldContext())
DLLLOCAL std::mutex& jni_dbg_program_registry_lock();
#endif
DLLLOCAL JniExternalProgramData* jni_get_context(QoreProgram*& pgm);

//! Returns the jni data of the Program context as jni_get_context(), or of the global Java context Program
/** Returns nullptr once the module's shutdown has started, as no global context Program exists then
    (Globals::getJavaContextProgram()); \a pgm is set to the Program, or to nullptr.  A child process created with
    fork() while the module was being initialized also has none, but the load of the module fails in that child, so no
    jni API that calls this function can be called there

    The global context Program is held by \a holder, which the caller keeps for as long as it uses the Program or its
    jni data, as the module's shutdown deletes it once no other thread holds it (qore#6829)
*/
DLLLOCAL JniExternalProgramData* jni_get_context_or_global(QoreProgram*& pgm, JniContextProgramHolder& holder);

//! Returns the jni data of the Program context as jni_get_context(), or of the global Java context Program
/** Throws jni::BasicException once the module's shutdown has started, as no global context Program exists then
    (ex: for a Java thread that calls into Qore while the module is shut down); see jni_get_context_or_global(),
    also for \a holder
*/
DLLLOCAL JniExternalProgramData* jni_get_context_unconditional(JniContextProgramHolder& holder);
DLLLOCAL JniExternalProgramData* jni_get_context_unconditional(QoreProgram*& pgm, JniContextProgramHolder& holder);

//! Returns the jni data of the Program context of a call of a jni API from Qore code, creating it if necessary
/** The Program is found as by jni_get_context(); when no Program in the context has jni data, the jni data of the
    Program of the calling code is created (JniExternalProgramData::getCreateJniProgramData()), as by a jni module
    command issued in the Program: a child Program that does not inherit the jni data of its parent
    (\c PO_NO_INHERIT_PROGRAM_DATA, or when the copy of the parent's jni data fails) has the namespaces and the feature
    of the jni module, but no jni data (qore#6801)

    @param pgm set to the Program of the returned jni data: the global Java context Program when there is no Program
    context; never nullptr
    @param holder holds the global Java context Program, if it is the Program returned (see
    jni_get_context_or_global())

    @return the jni data; never nullptr

    @throw jni::Exception the jni data could not be created, or there is no Program context and no global Java
    context Program (the module is being shut down)

    @note to be called only from code that Qore code calls, as it can take the parse lock of the Program
*/
DLLLOCAL JniExternalProgramData* jni_get_create_context(QoreProgram*& pgm, JniContextProgramHolder& holder);

//! Returns the jni data of the Program of the calling Qore code, creating it if necessary
/** For the APIs that must use the caller's Program in all cases (qore_get_call_program_context()); the jni data is
    created as by jni_get_create_context() when the Program has none (qore#6801)

    @param pgm set to the Program of the returned jni data: the global Java context Program when there is no Program
    context; never nullptr

    @return the jni data; never nullptr

    @throw jni::Exception the jni data could not be created, or there is no Program context and no global Java
    context Program (the module is being shut down)

    @note to be called only from code that Qore code calls, as it can take the parse lock of the Program
*/
DLLLOCAL JniExternalProgramData* jni_get_create_call_context(QoreProgram*& pgm, JniContextProgramHolder& holder);

//! Enters the Program of a Java call made in another Program, before its jni data is looked up
/** A call of a Java method of an object that the given Program owns, or of a Java closure that it created, runs in
    that Program: when it is not the current Program, it is entered, so that its jni data cannot be destroyed while the
    call uses it, Qore code that Java calls back during the call uses its thread-local data, and the calling code
    stays visible to the sandbox (qore#6183, qore#6185).  A call within the Program costs only the comparison.

    @param epch holds the entry for the call; it is left empty when no entry is needed
    @param pgm the Program of the call; set to nullptr when the call is made in the context of the calling code: the
    Program has no jni data, or it has been deleted and its jni data has been destroyed
    @param xsink for the exception if the Program cannot be entered (PROGRAM-ERROR if it is being torn down or
    another thread is parsing it), or if its entry handler or thread initialization code raises one

    @return 0 if the call can be made, -1 if an exception has been raised

    @note a C++ exception from the entry leaves \a epch empty
*/
DLLLOCAL int jni_enter_call_program(std::optional<QoreExternalProgramContextHelper>& epch, QoreProgram*& pgm,
        ExceptionSink* xsink);

//! Makes the Program that a Java class is added to current, for its namespaces to be changed under its parse lock
/** Adding a class to a Program changes its definitions, as parsing does, and runs none of its code: when the Program
    is the current one already, it is not entered.  The same holds for generating the byte code of a Java class for a
    Qore class (the superclass of a Java class that extends a Qore class, ex: one defined with \c define-class) and for
    listing the classes of a namespace, which read its definitions and run none of its code (qore#6785).  The
    Program is current for a module command (an \c import, \c define-class or \c define-pending-class, also one
    issued with Program::issueModuleCmd() while the Program has code pending, which no runtime entry is allowed into)
    and for a parse in the Program.  The code that made it current then holds only a reference to it, not its thread count,
    which keeps its memory but not its data: the caller must take the Program's parse lock immediately after this
    call (CurrentProgramRuntimeExternalParseContextHelper), as that is what keeps the Program from being torn down
    while it is changed (the parse lock is refused once the Program's teardown has begun, and the teardown waits for
    the parse lock to be released).  Otherwise (ex: a class loaded in a thread that Java started, or an import into
    another Program with jni_module_import()), the Program is entered, which takes its thread count.

    @param epch holds the entry; it is left empty when the Program is current
    @param pgm the Program that the class is added to
    @param xsink the sink of the entry, which the entry uses also when it is left: it must outlive \a epch (declared
    before it), and an exception that leaving the entry raises is handled by the sink as for any other exception left
    in it

    @throw XsinkException the Program cannot be entered (PROGRAM-ERROR if it is being torn down or parsed), or its
    entry handler or thread initialization code raised an exception; the entry is then left when \a epch is destroyed
*/
DLLLOCAL void jni_enter_program_for_class_add(std::optional<QoreExternalProgramContextHelper>& epch,
        QoreProgram* pgm, ExceptionSink& xsink);

//! Returns true if Java code that the calling code called can call into \a pgm (qore#6845, qore#7023)
/** Java code is not governed by a sandbox: for code that a sandbox governs, Java's calls into Qore are made only in
    the Program of the calling code, which the sandbox governs.  Code that no sandbox governs reaches any Program.

    The Program of the calling code is the Program of the %Qore code that made the innermost call from %Qore into Java
    on the thread, as recorded by JniJavaCallerScope (qore#7023): the Program that a call of a Java method of an object
    of another Program enters, and that libqore makes the Program context of such a call, is the object's, not the
    caller's.  Without a recorded call (ex: a thread that Java started), it is the Program context of the call, or the
    current Program without one, as before.

    The Program is only compared with the Program of the calling code: it is not used otherwise, and can be one that
    has been deleted

    @param pgm the Program that Java names

    @return true if the calling code is not governed by a sandbox or \a pgm is the Program of the calling code
*/
DLLLOCAL bool jni_governed_caller_reaches(const QoreProgram* pgm);

//! The rules of jni_governed_caller_reaches() without the attribution of the Java code on the stack
DLLLOCAL bool jni_governed_caller_reaches_intern(const QoreProgram* pgm);

//! Returns true if a restricting sandbox governs the calling code (QoreSandboxManagerHelper(All))
/** A sandbox that restricts nothing, as one that a host attaches to trusted code only for its interrupts or resource
    limits and that allows uncontrolled APIs, does not make its code sandboxed (jni_sandbox_restricts_io(), qore#7041)
*/
DLLLOCAL bool jni_restricting_sandbox_governs();

//! Returns the Program of the calling code that a restricting sandbox governs, through library code (qore#7394)
/** \a pgm is the Program of the calling code as the Program context of the call, or the frame of a builtin call,
    names it.  The code of a library (a module's Program, which the code of every Program that calls it shares) runs
    with its own Program as both: for code that a restricting sandbox governs (jni_restricting_sandbox_governs()), the
    Program of the calling code is then the Program of the code that the library code serves, the caller Program of
    libqore's call chain (qore_get_caller_program()), never the library's shared Program, for the Java state that the
    module creates or places for the calling code and for the checks of the Program that Java code reaches.  Code that
    no restricting sandbox governs keeps \a pgm.

    Only the current Program is resolved, as the caller walk starts from it: a library's Program is current whenever
    its code runs or a call enters it for an object or a class of the library, and is resolved then; a Program that is
    not current (the Program that the frame of a builtin call names after the call entered another Program, or the
    Program of a callback that the module set as the Program context) is returned as it is.

    @param pgm the Program of the calling code
    @param known set to false if the Program is not known: library code that a restricting sandbox governs serves code
    that the call chain does not identify, or code of a Program that the thread does not run in (work handed to the
    thread, whose caller the thread does not hold), or the sandbox could not be resolved

    @return the Program of the calling code, or nullptr if it is not known (fail closed)
*/
DLLLOCAL QoreProgram* jni_calling_code_program(QoreProgram* pgm, bool& known) noexcept;

//! As jni_calling_code_program(), for the Java state that the module creates or places for the calling code
/** @throw BasicException the Program of the calling code is not known: the state would otherwise be placed in the
    library's shared Program
*/
DLLLOCAL QoreProgram* jni_calling_code_program_for_state(QoreProgram* pgm);

//! Returns true if \a sm restricts its code: filesystem or network access, or uncontrolled APIs
/** Resource limits and interrupts are not isolation; a sandbox that refuses uncontrolled APIs restricts its code
*/
DLLLOCAL bool jni_sandbox_restricts_io(const QoreSandboxManager* sm);

//! Returns the Program context of the call as the fallback Program of a QoreJavaApi function call or class lookup
/** QoreJavaApi falls back to the Program context of the call when the function or class is not found in the Program
    of the class loader (a Qorus Java workflow step, whose class loader is the Program that parsed it).  For code that a
    sandbox governs, the fallback is made only into the Program of the calling code (qore#7023): the Program context of
    a call of a Java method of an object of another Program is the object's.  Must be called before the caller of the
    call into Java is cleared (QoreJniStackLocationHelper).

    @param pgm the Program of the class loader

    @return the fallback Program, or nullptr for no fallback
*/
DLLLOCAL QoreProgram* jni_java_api_fallback_program(const QoreProgram* pgm);

//! A Program named by a Program pointer that Java passed to a native method, validated and held for the native call
/** Java caches the pointers of Programs: each org.qore.jni.QoreURLClassLoader has the pointer of the Program whose
    class loader it is, and the class loaders derived from it (ex: the class loader of a QoreJavaCompiler) share it;
    Java passes it to the native methods that call into the Program or read its definitions (ex:
    QoreJavaApi.callFunction(), or the generation of the byte code of a Java class for a Qore class).  The Program can
    be deleted at any time while Java has the pointer: the pointer of a class loader is cleared when the Program's jni
    data is destroyed, but a native method can have received it just before, and the Program's memory can have been
    freed by the time the method uses it.

    The pointer is therefore not used as it is: the Program is looked up in the registry of the Programs with jni data
    and dependency-referenced under the registry's lock, which keeps its memory for the lifetime of this object; the
    pointer must still be the pointer of the class loader that Java read it from (\a loader), as a Program created at
    the address of a deleted one is registered under the same pointer, while the class loader of the deleted one was
    cleared before its memory was freed; the Program is then entered as jni_enter_program_for_class_add() enters it,
    which fails if the Program is being torn down, and keeps it from being torn down while it is entered.

    A pointer of 0 names no Program: get() then returns nullptr, and the caller uses the Program context of the thread
    or the global Java context Program, as it did before.

    A native that only reads the module's own data for the Program, or that is called while the Program can be parsed
    by another thread (ex: by the class loading of a Java compiler that runs on its own threads while the Program is
    parsed), which no runtime entry is allowed into, validates and holds the Program without entering it (the
    constructor without a sink): the Program's memory is kept, not its data, which the native then reads only in a way
    that is safe while the Program is torn down (ex: JniExternalProgramData::clearCompilationCache(const
    QoreProgram*)) or as the code before it did.

    This relies on the Program's teardown destroying its jni data before the Program is freed, as clearing a Program
    does (QoreProgram::waitForTerminationAndDeref() or QoreProgram::deref(ExceptionSink*)); a Program released with
    QoreProgram::deref() without being cleared first keeps its external data, which libqore does not support for a
    Program with code.

    @note the dependency reference is released when the object is destroyed; it is the last one only if the Program
    was deleted meanwhile by another thread, which cleared it (its data, and its code), so that only the Program
    object itself is destroyed on this thread (which detaches the debugger from it, if any, taking only the locks of
    the Program registry with no other lock held), as when a generated binding releases its reference.  An exception
    raised when the entry is left (ex: by a thread exit handler of the Program) is left in the sink given, which
    reports it when it is destroyed, as for any other exception left in a sink
*/
class JniJavaProgramRef {
public:
    //! Validates the Program pointer \a ptr received from Java, and holds and enters the Program if it is valid
    /** @param env the JNI environment of the thread
        @param xsink the sink of the entry, which the entry uses also when it is left: it must outlive this object
        @param ptr the Program pointer, or 0
        @param loader the class loader that Java read the pointer from, or nullptr for the thread's class loader
        context (QoreURLClassLoader.getCurrent()), which QoreURLClassLoader.getProgramPtr() reads

        @throw BasicException the pointer names no Program with jni data, as its Program has been deleted
        @throw JavaException the class loader's pointer could not be read
        @throw XsinkException the Program cannot be entered (ex: it is being torn down)
    */
    DLLLOCAL JniJavaProgramRef(Env& env, ExceptionSink& xsink, jlong ptr, jobject loader = nullptr);

    //! Validates and holds and enters the Program \a ptr for a call of a method of an existing Qore object or closure
    /** As the constructor above, for a callback (QoreObject.callMethod(), QoreClosure.call()), which runs in its own
        Program: on a thread that Java started, whose Qore caller is not known, the Program is only the context of
        the call (see jni_governed_caller_reaches(), qore#7023)
    */
    DLLLOCAL JniJavaProgramRef(Env& env, ExceptionSink& xsink, jlong ptr, bool callback);

    //! Validates the Program pointer \a ptr received from Java, and holds the Program, without entering it
    /** @param env the JNI environment of the thread
        @param ptr the Program pointer, or 0
        @param loader the class loader that Java read the pointer from, or nullptr for the thread's class loader
        context

        @throw BasicException the pointer names no Program with jni data, as its Program has been deleted
        @throw JavaException the class loader's pointer could not be read
    */
    DLLLOCAL JniJavaProgramRef(Env& env, jlong ptr, jobject loader = nullptr);

    DLLLOCAL ~JniJavaProgramRef() {
        release();
    }

    JniJavaProgramRef(const JniJavaProgramRef&) = delete;
    JniJavaProgramRef& operator=(const JniJavaProgramRef&) = delete;

    //! Returns the Program, or nullptr if the pointer was 0
    DLLLOCAL QoreProgram* get() const {
        return pgm;
    }

private:
    //! the Program, dependency-referenced, or nullptr
    QoreProgram* pgm = nullptr;
    //! the entry into the Program; empty if the Program is current (see jni_enter_program_for_class_add())
    std::optional<QoreExternalProgramContextHelper> epch;

    //! Validates the pointer and dependency-references its Program (see the constructors)
    DLLLOCAL void acquire(Env& env, jlong ptr, jobject loader, bool callback = false);

    //! Leaves the Program, then releases its dependency reference
    DLLLOCAL void release() noexcept;
};
}

#endif
