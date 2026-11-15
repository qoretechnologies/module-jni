/* -*- indent-tabs-mode: nil -*- */
/*
    QoreJdbcSandbox.cpp

    Qore Programming Language JNI Module

    Copyright (C) 2026 Qore Technologies, s.r.o.

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

#include "QoreJdbcSandbox.h"
#include "JavaSandbox.h"
#include "Env.h"
#include "Globals.h"
#include "Jvm.h"
#include "LocalReference.h"
#include "QoreJdbcDriver.h"

#include <cstring>
#include <strings.h>

// the classes of org.qore.jni.jdbc (qore#5786), and the SingleStore adapter's class bytes
#include "JavaClassJdbcConnectGuard.inc"
#include "JavaClassGuardedSocket.inc"
#include "JavaClassRedshiftGuardSocketFactory.inc"
#include "JavaClassJdbcTargets.inc"
#include "JavaClassJdbcDriverProfile.inc"
#include "JavaClassRedshiftProfile.inc"
#include "JavaClassSingleStoreProfile.inc"
#include "JavaClassJdbcSandboxConnect.inc"
#include "JavaClassQoreGuardSocketFactory.inc"

namespace jni {

namespace {

//! org.qore.jni.jdbc.JdbcSandboxConnect
GlobalReference<jclass> classJdbcSandboxConnect;
//! static boolean isSupportedUrl(String)
jmethodID methodIsSupportedUrl = nullptr;
//! static JdbcSandboxConnect prepare(ClassLoader, String, Properties)
jmethodID methodPrepare = nullptr;
//! String[] getTargetHosts()
jmethodID methodGetTargetHosts = nullptr;
//! int[] getTargetPorts()
jmethodID methodGetTargetPorts = nullptr;
//! void pin(byte[][][])
jmethodID methodPin = nullptr;
//! void pinAddresses(String[][])
jmethodID methodPinAddresses = nullptr;
//! AutoCloseable connect()
jmethodID methodConnect = nullptr;
//! void checkSql(String)
jmethodID methodCheckSql = nullptr;
//! void close()
jmethodID methodClose = nullptr;

//! SingleStoreProfile.adapterBytes0(): the class bytes of the SingleStore adapter
/** PROVISIONAL (design section 11, item 10): the adapter is built in to the module; see
    SingleStoreProfile.adapterClass()
*/
jbyteArray JNICALL jdbc_singlestore_adapter_bytes(JNIEnv* jenv, jclass) {
    jsize len = static_cast<jsize>(java_org_qore_jni_QoreGuardSocketFactory_class_len);
    jbyteArray rv = jenv->NewByteArray(len);
    if (!rv) {
        // OutOfMemoryError is pending
        return nullptr;
    }
    jenv->SetByteArrayRegion(rv, 0, len, reinterpret_cast<const jbyte*>(java_org_qore_jni_QoreGuardSocketFactory_class));
    if (jenv->ExceptionCheck()) {
        jenv->DeleteLocalRef(rv);
        return nullptr;
    }
    return rv;
}

JNINativeMethod singleStoreProfileNativeMethods[] = {
    {const_cast<char*>("adapterBytes0"), const_cast<char*>("()[B"),
        reinterpret_cast<void*>(jdbc_singlestore_adapter_bytes)},
};

//! Returns the trusted class loader that a restricted connection loads its driver from
/** The class loader of the classpath of every Program (\c Globals::syscl, which \c global-add-classpath adds to):
    sandboxed code cannot add to it or define classes in it, with the prerequisites of QORE_JNI_JDBC_SANDBOX_DRIVERS:
    - \c global-add-classpath and \c global-add-relative-classpath are refused to every restricting sandbox, also
      through a policy barrier or a system-policy capability, and to every restricted issuer
      (jni_check_uncontrolled_cmd(), jni_check_command_issuer())
    - every other module command that adds classes or classpath entries (\c add-classpath, \c define-class,
      \c define-pending-class, \c kotlin-eval, \c register-shared-class, \c import) is refused to a sandboxed issuer
      (module API 2.3), and changes only the Program's own class loader anyway; restricted code cannot issue a
      command into another Program (\c RUNTIME-COMMAND-ERROR)
    - the \c classpath option of the jdbc driver is judged by the code that set it and refused to sandboxed setters
      (qore#5741), and adds to the Program's own class loader only
    - the module's functions that define or load classes or give the class loader (\c define_class(),
      \c load_class(), \c get_class_loader()) and every Java class are uncontrolled APIs, which a sandbox that does
      not allow them refuses at run time (qore#5741), so sandboxed code cannot reach the loader's Java API
    - the names of dynamic classes that the module generates (\c qore., \c qoremod., \c python., ...) are not names of
      driver or guard classes

    A sandboxed Program's own class loader is never used: trusted code may have put classes into it, which it
    prefers to its parent's (QoreURLClassLoader.loadClass()).

    @return the loader, or nullptr if there is none
*/
jobject jdbc_trusted_loader() {
    return Globals::syscl;
}

//! Raises the refusal of a connection for restricted code with an unsupported URL
void jdbc_refuse_url(const char* url, ExceptionSink* xsink) {
    std::string scheme = jdbc_url_scheme(url);
    xsink->raiseException("NETWORK-ACCESS-DENIED", "the JDBC driver of URL '%s' is not supported for sandboxed "
        "connections; the jdbc driver connects for code that a sandbox governs only with the audited Amazon Redshift "
        "and SingleStore drivers (qore#5786)", scheme.c_str());
}

//! Returns the targets of a prepared connection
void jdbc_get_targets(Env& env, jobject prep, std::vector<JniNetTarget>& targets) {
    LocalReference<jobjectArray> hosts = env.callObjectMethod(prep, methodGetTargetHosts, nullptr)
        .as<jobjectArray>();
    LocalReference<jintArray> ports = env.callObjectMethod(prep, methodGetTargetPorts, nullptr).as<jintArray>();
    jsize n = env.getArrayLength(hosts);
    assert(n == env.getArrayLength(ports));
    targets.clear();
    targets.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        LocalReference<jstring> h = env.getObjectArrayElement(hosts, i).as<jstring>();
        Env::GetStringUtfChars hs(env, h);
        JniNetTarget t;
        t.host = hs.c_str();
        t.port = env.getIntArrayElement(ports, i);
        t.proto = QSEC_NET_TCP;
        targets.push_back(std::move(t));
    }
}

//! Pins the destinations that the module checked
void jdbc_pin_checked(Env& env, jobject prep, const std::vector<JniCheckedTarget>& checked) {
    LocalReference<jclass> byte_array_class = env.findClass("[B");
    LocalReference<jclass> byte_array2_class = env.findClass("[[B");
    LocalReference<jobjectArray> rv = env.newObjectArray(static_cast<jsize>(checked.size()), byte_array2_class);
    for (size_t i = 0; i < checked.size(); ++i) {
        const std::vector<std::string>& addrs = checked[i].addrs;
        LocalReference<jobjectArray> a = env.newObjectArray(static_cast<jsize>(addrs.size()), byte_array_class);
        for (size_t j = 0; j < addrs.size(); ++j) {
            const std::string& addr = addrs[j];
            LocalReference<jbyteArray> bytes = env.newByteArray(static_cast<jsize>(addr.size()));
            env.setByteArrayRegion(bytes, 0, static_cast<jsize>(addr.size()),
                reinterpret_cast<const jbyte*>(addr.data()));
            env.setObjectArrayElement(a, static_cast<jsize>(j), bytes);
        }
        env.setObjectArrayElement(rv, static_cast<jsize>(i), a);
    }
    jvalue jarg;
    jarg.l = rv;
    env.callVoidMethod(prep, methodPin, &jarg);
}

#ifdef QDBI_METHOD_GET_CONNECT_TARGETS
//! Pins the destinations that Qore checked (Qore-5747 mode), after comparing them with the URL's destinations
/** @return 0 for success, -1 if an exception was raised
*/
int jdbc_pin_qore_checked(Env& env, jobject prep, const std::vector<JniNetTarget>& targets,
        const QoreListNode* checked, ExceptionSink* xsink) {
    if (checked->size() != targets.size()) {
        xsink->raiseException("NETWORK-ACCESS-DENIED", "the destinations of the JDBC connection changed after they "
            "were checked against the sandbox");
        return -1;
    }
    LocalReference<jclass> string_class = env.findClass("java/lang/String");
    LocalReference<jclass> string_array_class = env.findClass("[Ljava/lang/String;");
    LocalReference<jobjectArray> rv = env.newObjectArray(static_cast<jsize>(targets.size()), string_array_class);
    for (size_t i = 0; i < targets.size(); ++i) {
        QoreValue cv = checked->retrieveEntry(i);
        const QoreHashNode* c = cv.getType() == NT_HASH ? cv.get<const QoreHashNode>() : nullptr;
        QoreValue host = c ? c->getKeyValue("host") : QoreValue();
        if (!c || host.getType() != NT_STRING || targets[i].host != host.get<const QoreStringNode>()->c_str()
            || c->getKeyValue("port").getAsBigInt() != targets[i].port) {
            xsink->raiseException("NETWORK-ACCESS-DENIED", "the destinations of the JDBC connection changed after "
                "they were checked against the sandbox");
            return -1;
        }
        QoreValue av = c->getKeyValue("addresses");
        const QoreListNode* addresses = av.getType() == NT_LIST ? av.get<const QoreListNode>() : nullptr;
        if (!addresses || addresses->empty()) {
            xsink->raiseException("NETWORK-ACCESS-DENIED", "a checked destination of a JDBC connection has no "
                "address");
            return -1;
        }
        LocalReference<jobjectArray> a = env.newObjectArray(static_cast<jsize>(addresses->size()), string_class);
        for (size_t j = 0; j < addresses->size(); ++j) {
            QoreValue v = addresses->retrieveEntry(j);
            if (v.getType() != NT_STRING) {
                xsink->raiseException("NETWORK-ACCESS-DENIED", "invalid checked address of a JDBC connection");
                return -1;
            }
            LocalReference<jstring> s = env.newString(v.get<const QoreStringNode>()->c_str());
            env.setObjectArrayElement(a, static_cast<jsize>(j), s);
        }
        env.setObjectArrayElement(rv, static_cast<jsize>(i), a);
    }
    jvalue jarg;
    jarg.l = rv;
    env.callVoidMethod(prep, methodPinAddresses, &jarg);
    return 0;
}

//! Returns the JDBC URL of a datasource: the "url" option, or the database name, with the "jdbc:" prefix
std::string jdbc_datasource_url(Datasource* ds) {
    std::string str;
    ConstHashIterator hi(ds->getConnectOptions());
    while (hi.next()) {
        if (!strcasecmp(hi.getKey(), JDBC_OPT_URL) && hi.get().getType() == NT_STRING) {
            str = hi.get().get<const QoreStringNode>()->c_str();
        }
    }
    if (str.empty()) {
        str = ds->getDBNameStr();
    }
    if (str.rfind("jdbc:", 0)) {
        str.insert(0, "jdbc:");
    }
    return str;
}
#endif

//! Returns new driver properties with the user and password, if set
LocalReference<jobject> jdbc_user_props(Env& env, const std::string& user, const std::string& pass) {
    LocalReference<jobject> props = env.newObject(Globals::classProperties, Globals::ctorProperties, nullptr);
    std::vector<std::pair<const char*, const std::string*>> v = {{"user", &user}, {"password", &pass}};
    for (auto& i : v) {
        if (i.second->empty()) {
            continue;
        }
        jvalue jargs[2];
        LocalReference<jstring> k = env.newString(i.first);
        LocalReference<jstring> val = env.newString(i.second->c_str());
        jargs[0].l = k;
        jargs[1].l = val;
        LocalReference<jobject> ignore = env.callObjectMethod(props, Globals::methodPropertiesSetProperty, jargs);
    }
    return props;
}

//! Prepares a restricted connection in Java: the lexical, driver and parse checks, and the guard
LocalReference<jobject> jdbc_prepare_java(Env& env, const char* url, const std::string& user,
        const std::string& pass) {
    LocalReference<jobject> props = jdbc_user_props(env, user, pass);
    LocalReference<jstring> jurl = env.newString(url);
    jvalue jargs[3];
    jargs[0].l = jdbc_trusted_loader();
    jargs[1].l = jurl;
    jargs[2].l = props;
    return env.callStaticObjectMethod(classJdbcSandboxConnect, methodPrepare, jargs);
}

} // namespace

std::vector<QoreSandboxManager*> JdbcConnectDecision::managers() const {
    std::vector<QoreSandboxManager*> rv;
    if (policy && *policy) {
        rv.push_back(policy->get());
    }
    return rv;
}

int jdbc_connect_decision(Datasource* ds, JdbcConnectDecision& d, ExceptionSink* xsink) {
    d.restricted = false;
    d.policy.reset();
    d.qore_checked = nullptr;
#ifdef QDBI_METHOD_GET_CONNECT_TARGETS
    // the Qore side of qore#5747: Qore checked the destinations against the policy of the code that chose them, and
    // its decision is final
    if (const QoreListNode* checked = ds->getCheckedConnectTargets()) {
        d.restricted = true;
        d.qore_checked = checked;
    }
    return 0;
#else
    // PROVISIONAL (design section 6 / 11, item 1): the thread decides, until the chooser record of qore#6142 Option A
    // or the Qore side of qore#5747 replaces this function: a sandbox that does not allow uncontrolled APIs governs
    // the code that opens the connection; its network policy applies to the destinations
    if (!jni_sandbox_restricts_caller(QoreSandboxManagerHelper::Policy)) {
        return 0;
    }
    d.restricted = true;
    d.policy.reset(new QoreSandboxManagerHelper(QoreSandboxManagerHelper::Policy));
    if (!*d.policy) {
        // a missing policy is not a permission
        xsink->raiseException("NETWORK-ACCESS-DENIED", "the sandbox policy of the JDBC connection cannot be "
            "determined");
        return -1;
    }
    return 0;
#endif
}

void JdbcGuardHolder::close() {
    if (!static_cast<jobject>(prep)) {
        return;
    }
    GlobalReference<jobject> p = std::move(prep);
    // closed on a cancelled thread too, and never throws; also while a Java exception is pending (ex: the driver's
    // close() failed), which is restored afterwards
    QoreCancelDeferralHelper cancel_deferral;
    try {
        Env env;
        JNIEnv* jenv = Jvm::getEnv();
        jthrowable pending = jenv->ExceptionOccurred();
        if (pending) {
            jenv->ExceptionClear();
        }
        jenv->CallVoidMethod(p, methodClose);
        if (jenv->ExceptionCheck()) {
            // JdbcConnectGuard.close() only sets a flag and removes the token: an error here is an
            // OutOfMemoryError, and the guard's flag was set before it
            jenv->ExceptionClear();
        }
        if (pending) {
            jenv->Throw(pending);
            jenv->DeleteLocalRef(pending);
        }
    } catch (jni::Exception& e) {
        e.ignore();
    } catch (...) {
        // a destructor path: nothing may escape
    }
}

std::string jdbc_url_scheme(const char* url) {
    std::string rv(url ? url : "");
    size_t i = rv.find("//");
    if (i != std::string::npos) {
        rv.erase(i);
    } else {
        // at most the subprotocol: "jdbc:name:"
        size_t c = rv.find(':', rv.rfind("jdbc:", 0) ? 0 : 5);
        if (c != std::string::npos) {
            rv.erase(c + 1);
        }
    }
    if (rv.size() > 64) {
        rv.erase(64);
    }
    return rv;
}

bool jdbc_sandbox_supported_url(Env& env, const char* url) {
    LocalReference<jstring> jurl = env.newString(url);
    jvalue jarg;
    jarg.l = jurl;
    return env.callStaticBooleanMethod(classJdbcSandboxConnect, methodIsSupportedUrl, &jarg);
}

int jdbc_prepare_guarded(Env& env, const JdbcConnectDecision& d, const char* url, const std::string& user,
        const std::string& pass, JdbcGuardHolder& holder, ExceptionSink* xsink) {
    assert(d.restricted);
    if (!jdbc_trusted_loader()) {
        xsink->raiseException("JNI-SANDBOX-ERROR", "the jni module has no class loader to load JDBC drivers from for "
            "sandboxed connections");
        return -1;
    }
    try {
        if (!jdbc_sandbox_supported_url(env, url)) {
            jdbc_refuse_url(url, xsink);
            return -1;
        }
        // from here on, the guard is closed on every error path by the holder
        holder.reset(jdbc_prepare_java(env, url, user, pass).makeGlobal());
        std::vector<JniNetTarget> targets;
        jdbc_get_targets(env, holder.get(), targets);
#ifdef QDBI_METHOD_GET_CONNECT_TARGETS
        if (d.qore_checked) {
            if (jdbc_pin_qore_checked(env, holder.get(), targets, d.qore_checked, xsink)) {
                holder.close();
                return -1;
            }
            return 0;
        }
#endif
        // every destination of the URL, before the driver connects to any of them
        std::vector<JniCheckedTarget> checked;
        if (jni_check_network_targets(d.managers(), targets, checked, xsink)) {
            holder.close();
            return -1;
        }
        jdbc_pin_checked(env, holder.get(), checked);
        return 0;
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    holder.close();
    return -1;
}

GlobalReference<jobject> jdbc_guarded_connect(Env& env, const JdbcGuardHolder& holder) {
    assert(holder);
    return env.callObjectMethod(holder.get(), methodConnect, nullptr).makeGlobal();
}

void jdbc_guarded_check_sql(Env& env, const JdbcGuardHolder& holder, const char* sql) {
    assert(holder);
    LocalReference<jstring> jsql = env.newString(sql);
    jvalue jarg;
    jarg.l = jsql;
    env.callVoidMethod(holder.get(), methodCheckSql, &jarg);
}

void jdbc_sandbox_init(Env& env) {
    // in the bootstrap class loader, so that every class loader finds the same classes; superclasses first
    Globals::findDefineClass(env, "org.qore.jni.jdbc.JdbcConnectGuard", nullptr,
        java_org_qore_jni_JdbcConnectGuard_class, java_org_qore_jni_JdbcConnectGuard_class_len);
    Globals::findDefineClass(env, "org.qore.jni.jdbc.GuardedSocket", nullptr, java_org_qore_jni_GuardedSocket_class,
        java_org_qore_jni_GuardedSocket_class_len);
    Globals::findDefineClass(env, "org.qore.jni.jdbc.RedshiftGuardSocketFactory", nullptr,
        java_org_qore_jni_RedshiftGuardSocketFactory_class, java_org_qore_jni_RedshiftGuardSocketFactory_class_len);
    Globals::findDefineClass(env, "org.qore.jni.jdbc.JdbcTargets", nullptr, java_org_qore_jni_JdbcTargets_class,
        java_org_qore_jni_JdbcTargets_class_len);
    Globals::findDefineClass(env, "org.qore.jni.jdbc.JdbcDriverProfile", nullptr,
        java_org_qore_jni_JdbcDriverProfile_class, java_org_qore_jni_JdbcDriverProfile_class_len);
    Globals::findDefineClass(env, "org.qore.jni.jdbc.RedshiftProfile", nullptr,
        java_org_qore_jni_RedshiftProfile_class, java_org_qore_jni_RedshiftProfile_class_len);
    LocalReference<jclass> ss = Globals::findDefineClass(env, "org.qore.jni.jdbc.SingleStoreProfile", nullptr,
        java_org_qore_jni_SingleStoreProfile_class, java_org_qore_jni_SingleStoreProfile_class_len);
    env.registerNatives(ss, singleStoreProfileNativeMethods,
        sizeof(singleStoreProfileNativeMethods) / sizeof(JNINativeMethod));
    classJdbcSandboxConnect = Globals::findDefineClass(env, "org.qore.jni.jdbc.JdbcSandboxConnect", nullptr,
        java_org_qore_jni_JdbcSandboxConnect_class, java_org_qore_jni_JdbcSandboxConnect_class_len).makeGlobal();
    methodIsSupportedUrl = env.getStaticMethod(classJdbcSandboxConnect, "isSupportedUrl", "(Ljava/lang/String;)Z");
    methodPrepare = env.getStaticMethod(classJdbcSandboxConnect, "prepare",
        "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/util/Properties;)Lorg/qore/jni/jdbc/JdbcSandboxConnect;");
    methodGetTargetHosts = env.getMethod(classJdbcSandboxConnect, "getTargetHosts", "()[Ljava/lang/String;");
    methodGetTargetPorts = env.getMethod(classJdbcSandboxConnect, "getTargetPorts", "()[I");
    methodPin = env.getMethod(classJdbcSandboxConnect, "pin", "([[[B)V");
    methodPinAddresses = env.getMethod(classJdbcSandboxConnect, "pinAddresses", "([[Ljava/lang/String;)V");
    methodConnect = env.getMethod(classJdbcSandboxConnect, "connect", "()Ljava/lang/AutoCloseable;");
    methodCheckSql = env.getMethod(classJdbcSandboxConnect, "checkSql", "(Ljava/lang/String;)V");
    methodClose = env.getMethod(classJdbcSandboxConnect, "close", "()V");
}

void jdbc_sandbox_cleanup() {
    classJdbcSandboxConnect = nullptr;
}

#ifdef QDBI_METHOD_GET_CONNECT_TARGETS
QoreListNode* jdbc_get_connect_targets(Datasource* ds, ExceptionSink* xsink) {
    std::string url = jdbc_datasource_url(ds);
    try {
        Env env;
        if (!jdbc_trusted_loader()) {
            xsink->raiseException("NETWORK-ACCESS-DENIED", "the jni module has no class loader to load JDBC drivers "
                "from for sandboxed connections");
            return nullptr;
        }
        if (!jdbc_sandbox_supported_url(env, url.c_str())) {
            jdbc_refuse_url(url.c_str(), xsink);
            return nullptr;
        }
        JdbcGuardHolder holder;
        holder.reset(jdbc_prepare_java(env, url.c_str(), ds->getUsernameStr(), ds->getPasswordStr()).makeGlobal());
        std::vector<JniNetTarget> targets;
        jdbc_get_targets(env, holder.get(), targets);
        ReferenceHolder<QoreListNode> rv(new QoreListNode(autoTypeInfo), xsink);
        for (const JniNetTarget& t : targets) {
            ReferenceHolder<QoreHashNode> h(new QoreHashNode(autoTypeInfo), xsink);
            h->setKeyValue("host", new QoreStringNode(t.host), xsink);
            h->setKeyValue("port", t.port, xsink);
            rv->push(h.release(), xsink);
        }
        return rv.release();
    } catch (jni::Exception& e) {
        e.convert(xsink);
    }
    return nullptr;
}
#endif

}
