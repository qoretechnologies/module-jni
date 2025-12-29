/* -*- indent-tabs-mode: nil -*- */
/*
    jni-module.cpp

    JNI integration to Qore

    Qore Programming Language

    Copyright (C) 2016 - 2023 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.

    Note that the Qore library is released under a choice of three open-source
    licenses: MIT (as above), LGPL 2+, or GPL 2+; see README-LICENSE for more
    information.
*/

#include <qore/Qore.h>

#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <map>
#include <vector>

#include <dlfcn.h>

#include "defs.h"
#include "Jvm.h"
#include "QoreJniClassMap.h"
#include "Method.h"
#include "QoreToJava.h"
#include "Globals.h"
#include "QoreJdbcDriver.h"

using namespace jni;

#ifndef Q_WINDOWS
#include <signal.h>
#include <pthread.h>
#endif

sig_vec_t sig_vec = {
#ifndef Q_WINDOWS
    SIGTRAP, SIGSEGV, SIGBUS, SIGCHLD, SIGILL, SIGFPE
#endif
};

static QoreStringNode* jni_module_init();
static void jni_module_ns_init(QoreNamespace* rns, QoreNamespace* qns);
static void jni_module_delete();
static void jni_module_parse_cmd(const QoreString& cmd, ExceptionSink* xsink);

DLLEXPORT char qore_module_name[] = QORE_JNI_MODULE_NAME;
DLLEXPORT char qore_module_version[] = PACKAGE_VERSION;
DLLEXPORT char qore_module_description[] = "JNI module";
DLLEXPORT char qore_module_author[] = "Qore Technologies, s.r.o.";
DLLEXPORT char qore_module_url[] = "http://qore.org";
DLLEXPORT int qore_module_api_major = QORE_MODULE_API_MAJOR;
DLLEXPORT int qore_module_api_minor = QORE_MODULE_API_MINOR;
DLLEXPORT qore_module_init_t qore_module_init = jni_module_init;
DLLEXPORT qore_module_ns_init_t qore_module_ns_init = jni_module_ns_init;
DLLEXPORT qore_module_delete_t qore_module_delete = jni_module_delete;
DLLEXPORT qore_module_parse_cmd_t qore_module_parse_cmd = jni_module_parse_cmd;

DLLEXPORT qore_license_t qore_module_license = QL_MIT;
DLLEXPORT char qore_module_license_str[] = "MIT";

// global type compatibility option
DLLLOCAL bool jni_compat_types = false;

static bool jni_init_failed = false;

// module cmd type
using qore_jni_module_cmd_t = void (*) (const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_import(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_global_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_global_add_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_add_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
// define-pending-class: for resolving circular dependencies with inner classes
static void qore_jni_mc_define_pending_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_define_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_set_compat_types(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_set_property(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);
static void qore_jni_mc_mark_module_injected(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc);

// module cmds
typedef std::map<std::string, qore_jni_module_cmd_t> mcmap_t;
static mcmap_t mcmap = {
    {"import", qore_jni_mc_import},
    {"add-classpath", qore_jni_mc_add_classpath},
    {"add-relative-classpath", qore_jni_mc_add_relative_classpath},
    {"define-pending-class", qore_jni_mc_define_pending_class},
    {"define-class", qore_jni_mc_define_class},
    {"global-add-classpath", qore_jni_mc_global_add_classpath},
    {"global-add-relative-classpath", qore_jni_mc_global_add_relative_classpath},
    {"set-compat-types", qore_jni_mc_set_compat_types},
    {"set-property", qore_jni_mc_set_property},
    {"mark-module-injected", qore_jni_mc_mark_module_injected},
};

static void jni_thread_cleanup(void*) {
    jni::Jvm::threadCleanup();
}

static bool bootstrap = false;
static bool already_initialized = false;
static bool deferred_ns_init = false;

QoreStringNode* jni_module_init_finalize(bool system) {
    tclist.push(jni_thread_cleanup, nullptr);

    try {
        QoreProgram* pgm = Globals::createJavaContextProgram();
        printd(5, "jni_module_init_finalize() pgm: %p\n", pgm);
        // issue #4006: ensure there is a program context for initialization
        QoreProgramContextHelper pgm_ctx(pgm);

        qjcm.init(pgm, already_initialized);
    } catch (jni::Exception& e) {
        tclist.pop(false);
        qore_release_signals(sig_vec, QORE_JNI_MODULE_NAME);
        jni::Jvm::destroyVM();
        // display exception info on the console as an unhandled exception
        if (system) {
            throw;
            return nullptr;
        } else {
            ExceptionSink xsink;
            e.convert(&xsink);
            return new QoreStringNode("JNI-FINALIZATION-ERR");
        }
    }

    ExceptionSink xsink;
    ValueHolder v(qore_get_module_option("jni", "compat-types"), &xsink);
    if (v) {
        jni_compat_types = true;
    }

    jni::jni_qore_init_done = true;

    printd(5, "jni_module_init_finalize() jni module init done\n");
    return nullptr;
}

static QoreStringNode* jni_module_init() {
    if (jni_init_failed) {
        return new QoreStringNode("jni module initialization failed");
    }
    printd(5, "jni_module_init()\n");

    jni::jni_qore_init = true;

    qore_set_module_option("jni", "jni-version", JNI_VERSION_21);

    QoreStringNode* err = qore_reassign_signals(sig_vec, QORE_JNI_MODULE_NAME, true);
    if (err) {
        return err;
    }

    ValueHolder jvm_ptr(qore_get_module_option("jni", "jvm-ptr"), nullptr);
    if (jvm_ptr->getType() == NT_INT) {
        jni::Jvm::setVmPtr(reinterpret_cast<JavaVM*>(jvm_ptr->getAsBigInt()));
        already_initialized = true;
        Globals::setAlreadyInitialized();
    } else {
        already_initialized = false;
        try {
            err = jni::Jvm::createVM();
        } catch (jni::Exception& e) {
            ExceptionSink xsink;
            e.convert(&xsink);
            const QoreValue desc = xsink.getExceptionDesc();
            if (desc.getType() == NT_STRING) {
                err = new QoreStringNode(*desc.get<const QoreStringNode>());
            } else {
                err = new QoreStringNode("unknown exception calling Jvm::createVM()");
            }
        }
        if (err) {
            jni_init_failed = true;
            err->prepend("Could not create the Java Virtual Machine: ");
            return err;
        }
    }

    try {
        bootstrap = Globals::init();
    } catch (QoreStandardException &e) {
        throw;
    } catch (JavaException& e) {
        jni_init_failed = true;
        return e.toString();
    } catch (Exception &e) {
        jni_init_failed = true;
        return new QoreStringNode("JVM initialization failed due to an unknown error");
    }

    jni::setup_jdbc_driver();

    printd(5, "jni_module_init() initialized JVM\n");

    if (!bootstrap) {
        return jni_module_init_finalize();
    }

    jni::jni_qore_init_done = true;

    return nullptr;
}

static void jni_module_ns_init(QoreNamespace* rns, QoreNamespace* qns) {
    QoreProgram* pgm = getProgram();
    // we can ignore the first program to be initialized when bootstrapping
    if (bootstrap && !deferred_ns_init) {
        deferred_ns_init = true;
        return;
    }
    assert(pgm->getRootNS() == rns);
    if (!pgm->getExternalData("jni")) {
        QoreNamespace* jnins = qjcm.getJniNs().copy();
        rns->addNamespace(jnins);
        pgm->setExternalData("jni", new JniExternalProgramData(jnins, pgm));
    }
}

static void jni_module_delete() {
    // clear all objects from stored classes before destroying the JVM (releases all global references)
    Globals::clearGlobalContext();
    {
        ExceptionSink xsink;
        qjcm.destroy(xsink);
    }
    tclist.pop(false);
    jni::Jvm::destroyVM();
}

static QoreNamespace* qore_jni_wildcard_import(QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    if (arg[-2] != '.' || arg.strlen() < 3) {
        throw QoreJniException("JNI-IMPORT-ERROR", "invalid import argument: '%s'", arg.c_str());
        return nullptr;
    }

    arg.terminate(arg.strlen() - 2);

    arg.replaceAll(".", "::");
    return jni_module_find_create_java_namespace(arg, pgm);
}

extern "C" QoreNamespace* jni_module_find_create_java_namespace(QoreString& arg, QoreProgram* pgm) {
    arg.concat("::x");

    // create jni namespace in root namespace if necessary
    QoreNamespace* jns = pgm->getRootNS()->findLocalNamespace("Jni");

    QoreNamespace* ns = jns->findCreateNamespacePath(arg.c_str());
    printd(LogLevel, "jni_module_find_create_java_namespace() nsp: '%s' ns: %p '%s'\n", arg.c_str(), ns, ns->getName());
    ns->setClassHandler(jni_class_handler);

    return ns;
}

// exported function
extern "C" int jni_module_import(ExceptionSink* xsink, QoreProgram* pgm, const char* import) {
    JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
        //printd(5, "jni_module_import '%s' jpc: %p jnins: %p pgm: %p\n", import, jpc, jpc->getJniNamespace(), pgm);
    QoreString arg(import);
    try {
        if (arg[-1] != '*') {
            Env env;
            //printd(5, "jni_module_import() non wc lcc arg: '%s' (pgm: %p)\n", arg.c_str(), pgm);
            // the following call adds the class to the current program as well
            qjcm.findCreateQoreClass(env, arg.c_str(), pgm);
        } else {
            QoreNamespace* ns = qore_jni_wildcard_import(arg, pgm, jpc);
            if (!ns) {
                assert(*xsink);
                return -1;
            }
        }
    } catch (jni::Exception& e) {
        e.convert(xsink);
        return -1;
    }
    return 0;
}

static void jni_module_parse_cmd(const QoreString& cmd, ExceptionSink* xsink) {
    printd(LogLevel, "jni_module_parse_cmd() cmd: '%s'\n", cmd.c_str());

    const char* p = strchr(cmd.c_str(), ' ');

    if (!p) {
        xsink->raiseException("JNI-PARSE-COMMAND-ERROR", "missing command name in parse command: '%s'", cmd.c_str());
        return;
    }

    QoreString str(&cmd, p - cmd.c_str());

    QoreString arg(cmd);

    arg.replace(0, p - cmd.c_str() + 1, (const char*)0);
    arg.trim();

    mcmap_t::const_iterator i = mcmap.find(str.c_str());
    if (i == mcmap.end()) {
        QoreStringNode* desc = new QoreStringNodeMaker("unrecognized command '%s' in '%s' (valid commands: ",
            str.c_str(), cmd.c_str());
        for (mcmap_t::const_iterator i = mcmap.begin(), e = mcmap.end(); i != e; ++i) {
            if (i != mcmap.begin())
                desc->concat(", ");
            desc->sprintf("'%s'", i->first.c_str());
        }
        desc->concat(')');
        xsink->raiseException("JNI-PARSE-COMMAND-ERROR", desc);
        return;
    }

    // we must use "getProgram()" here for the parse context QoreProgram
    QoreProgram* pgm = getProgram();
    JniExternalProgramData* jpc = JniExternalProgramData::getCreateJniProgramData(pgm);
    try {
        i->second(arg, pgm, jpc);
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
}

static void qore_jni_mc_import(const QoreString& cmd_arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    QoreString arg(cmd_arg);
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));

    // process import statement
    printd(LogLevel, "qore_jni_mc_import() pgm: %p arg: %s c: %c\n", pgm, arg.c_str(), arg[-1]);

    // see if there is a wildcard at the end
    if (arg[-1] == '*') {
        qore_jni_wildcard_import(arg, pgm, jpc);
    } else {
        printd(LogLevel, "jni_module_parse_cmd() non wc lcc arg: '%s' (pgm: %p)\n", arg.c_str(), pgm);
        Env env;
        env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);
        // the following call adds the class to the current program as well
        qjcm.findCreateQoreClass(env, arg.c_str(), pgm, jpc);
    }
}

static void qore_jni_mc_global_add_classpath(const QoreString& cmd_arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    QoreString arg(cmd_arg);
    q_env_subst(arg);
    printd(LogLevel, "qore_jni_mc_global_add_classpath() jpc: %p arg: '%s'\n", jpc, arg.c_str());
    jpc->addParentClasspath(arg.c_str());
}

static void qore_jni_mc_global_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    SimpleRefHolder<QoreStringNode> cwd_str;

    assert(pgm);
    cwd_str = pgm->getScriptDir();

    if (!cwd_str) {
        char* cwd = getcwd(nullptr, 0);
        if (!cwd) {
            throw QoreJniException("JNI-GLOBAL-ADD-RELATIVE-CLASSPATH-ERROR", "cannot determine relative path; there "
                "is no information in the Program context and cannot get current working directory: %s",
                strerror(errno));
        }
        ON_BLOCK_EXIT(free, cwd);
        cwd_str = new QoreStringNode(cwd);
    }

    cwd_str->concat(QORE_DIR_SEP);
    cwd_str->concat(arg.c_str());
    q_normalize_path(**cwd_str);

    cwd_str->concat('/');

    printd(LogLevel, "qore_jni_mc_global_add_relative_classpath() arg: '%s' cwd: '%s'\n", arg.c_str(), cwd_str->c_str());

    jpc->addParentClasspath(cwd_str->c_str());
}

static void qore_jni_mc_add_classpath(const QoreString& cmd_arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    QoreString arg(cmd_arg);
    q_env_subst(arg);
    printd(LogLevel, "qore_jni_mc_add_classpath() jpc: %p arg: '%s'\n", jpc, arg.c_str());
    jpc->addClasspath(arg.c_str());
}

static void qore_jni_mc_add_relative_classpath(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    SimpleRefHolder<QoreStringNode> cwd_str;

    assert(pgm);
    cwd_str = pgm->getScriptDir();

    if (!cwd_str) {
        char* cwd = getcwd(nullptr, 0);
        if (!cwd) {
            throw QoreJniException("JNI-ADD-RELATIVE-CLASSPATH-ERROR", "cannot determine relative path; there is no "
                "information in the Program context and cannot get current working directory: %s", strerror(errno));
        }
        ON_BLOCK_EXIT(free, cwd);
        cwd_str = new QoreStringNode(cwd);
    }

    cwd_str->concat(QORE_DIR_SEP);
    cwd_str->concat(arg.c_str());
    q_normalize_path(**cwd_str);

    cwd_str->concat('/');

    printd(LogLevel, "qore_jni_mc_add_relative_classpath() arg: '%s' cwd: '%s'\n", arg.c_str(), cwd_str->c_str());

    jpc->addClasspath(cwd_str->c_str());
}

static void qore_jni_mc_define_pending_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));

    // find end of name
    qore_offset_t end = arg.find(' ');
    if (end == -1) {
        throw QoreJniException("JNI-DEFINE-CLASS-ERROR", "cannot find the end of the class name in the "
            "'define-pending-class' directive");
    }
    QoreString java_name(&arg, end);
    QoreString base64(arg.c_str() + end + 1);
    ExceptionSink xsink;
    SimpleRefHolder<BinaryNode> byte_code(base64.parseBase64(&xsink));
    if (xsink) {
        throw XsinkException(xsink);
    }
    jni::Env env;
    assert(jpc);

    printd(5, "define-pending-class %s pgm: %p loader: %x\n", java_name.c_str(), pgm,
        env.callIntMethod((jobject)jpc->getClassLoader(), jni::Globals::methodObjectHashCode, nullptr));

    // convert java name to dot name; QoreURLClassLoader.addPendingClass() requires the dot name
    java_name.replaceAll("/", ".");

    // add the byte code as a pending class
    LocalReference<jstring> jname = env.newString(java_name.c_str());
    LocalReference<jbyteArray> jbyte_code = QoreToJava::makeByteArray(env, **byte_code);

    std::vector<jvalue> jargs(2);
    jargs[0].l = jname;
    jargs[1].l = jbyte_code;

    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderAddPendingClass, &jargs[0]);
}

static void qore_jni_mc_define_class(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // find end of name
    qore_offset_t end = arg.find(' ');
    if (end == -1) {
        throw QoreJniException("JNI-DEFINE-CLASS-ERROR", "cannot find the end of the class name in the "
            "'define-class' directive");
    }
    QoreString java_name(&arg, end);
    QoreString base64(arg.c_str() + end + 1);
    ExceptionSink xsink;
    SimpleRefHolder<BinaryNode> byte_code(base64.parseBase64(&xsink));
    if (xsink) {
        throw XsinkException(xsink);
    }
    jni::Env env;

    // XXX DEBUG
    QoreProgram* ptr = (QoreProgram*)env.callLongMethod(jpc->getClassLoader(),
        Globals::methodQoreURLClassLoaderGetPtr, nullptr);
    assert(ptr == pgm);

    env.callVoidMethod(jpc->getClassLoader(), Globals::methodQoreURLClassLoaderSetContext, nullptr);
    //printd(5, "qore_jni_mc_define_class() jpc: %p name: '%s' class size: %d\n", jpc, java_name.c_str(),
    //    byte_code->size());

    // conver to binary name
    QoreString binary_name(java_name);
    binary_name.replaceAll("/", ".");

    assert(jpc->getClassLoader());

    LocalReference<jclass> jcls = Globals::findDefineClass(env, binary_name.c_str(), jpc->getClassLoader(),
        static_cast<const unsigned char*>(byte_code->getPtr()), byte_code->size());

    //LocalReference<jclass> jcls = env.defineClass(java_name.c_str(), jpc->getClassLoader(),
    //    static_cast<const unsigned char*>(byte_code->getPtr()), byte_code->size());

    // import the class immediately
    qjcm.findCreateQoreClassInProgram(binary_name, java_name.c_str(), new Class(jcls), pgm);
}

static void qore_jni_mc_set_compat_types(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    bool compat_types = q_parse_bool(arg.c_str());
    jpc->overrideCompatTypes(compat_types);
}

static void qore_jni_mc_set_property(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    // find end of property name
    qore_offset_t end = arg.find(' ');
    if (end == -1) {
        throw QoreJniException("JNI-SET-PROPERTY-ERROR", "cannot find the end of the property name in the 'set-property' directive");
    }

    QoreString property(&arg, end);
    QoreString value(arg.c_str() + end + 1);

    //printd(5, "qore_jni_mc_set_property() '%s' = '%s'\n", property.c_str(), value.c_str());

    jni::Env env;

    LocalReference<jstring> jprop = env.newString(property.c_str());
    LocalReference<jstring> jval = env.newString(value.c_str());

    std::vector<jvalue> jargs(2);
    jargs[0].l = jprop;
    jargs[1].l = jval;

    LocalReference<jstring> str = env.callStaticObjectMethod(Globals::classSystem,
        Globals::methodSystemSetProperty, &jargs[0]).as<jstring>();
    str = nullptr;
}

static void qore_jni_mc_mark_module_injected(const QoreString& arg, QoreProgram* pgm, JniExternalProgramData* jpc) {
    assert(pgm);
    assert(pgm->checkFeature(QORE_JNI_MODULE_NAME));
    assert(jpc);

    jpc->addInjectedModule(arg.c_str());
}

QoreClass* jni_class_handler(QoreNamespace* ns, const char* cname) {
    // get full class path
    QoreString cp(ns->getName());
    cp.concat('.');
    cp.concat(cname);

    const QoreNamespace* jns = ns;
    while (true) {
        printd(LogLevel, "jni_class_handler() ns: %p (%s) jns: %p (%s) cname: %s\n", ns, ns->getName(), jns,
            jns->getName(), cname);
        jns = jns->getParent();
        assert(jns);
        if (!strcmp(jns->getName(), "Jni"))
            break;
        cp.prepend(".");
        cp.prepend(jns->getName());
    }

    printd(LogLevel, "jni_class_handler() ns: %p cname: %s cp: %s\n", ns, cname, cp.c_str());

    QoreProgram* pgm = ns->getProgram();
    assert(pgm);
    try {
        Env env;
        QoreClass* qc = qjcm.findCreateQoreClass(env, cp.c_str(), pgm);
        printd(LogLevel, "jni_class_handler() cp: %s returning qc: %p\n", cp.c_str(), qc);
        return qc;
    } catch (jni::JavaException& e) {
        // ignore class not found exceptions here
        e.ignoreOrRethrowNoClass();
    } catch (jni::Exception& e) {
        // display exception info on the console as an unhandled exception
        {
            ExceptionSink xsink;
            e.convert(&xsink);
        }
        assert(false);
    }
    return nullptr;
}
