/* Copyright (C) 2026 Qore Technologies, s.r.o.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.qore.jni.test;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreURLClassLoader;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

/** Regression for the attribution of Java code when the JVM is the primary runtime.
 *
 * <p>The module's classes and the application's are then defined by the parent of the system class loader, a
 * {@link QoreURLClassLoader}.  They are trusted code, so a trusted call into Qore must keep working after sandboxed
 * code has used Java in the process.  The sandboxed code must still not reach the host's Program, and Java code of a
 * class loader below the system class loader that belongs to no Program counts as sandboxed, even when trusted code
 * calls it.
 *
 * <p>The verdict is the process exit status.
 */
public class JvmPrimarySandboxTrust {
    //! Program parse options of the Program that runs the test: new style, with no restrictions for its child Programs
    private static final long DRIVER_PARSE_OPTIONS = 402655232L;

    private static final String DRIVER = """
        %modern
        %requires jni
        %module-cmd(jni) import org.qore.jni.test.JvmPrimarySandboxTrust
        %module-cmd(jni) import org.qore.jni.test.ClassLoaderAccessProbe
        %module-cmd(jni) import org.qore.jni.test.GovernedProgramProbe
        %module-cmd(jni) import org.qore.jni.KotlinScriptEngine

        const TenantSource = "%modern
        %requires jni
        %module-cmd(jni) import org.qore.jni.test.JvmPrimarySandboxTrust
        %module-cmd(jni) import org.qore.jni.test.ClassLoaderAccessProbe
        %module-cmd(jni) import org.qore.jni.test.GovernedProgramProbe
        %module-cmd(jni) import org.qore.jni.KotlinScriptEngine
        string sub who() {
            return 'tenant';
        }
        auto sub ev(string script) {
            return KotlinScriptEngine::eval(script);
        }
        string sub add_parent(string dir) {
            return ClassLoaderAccessProbe::addParentPath(GovernedProgramProbe::currentLoader(), dir);
        }
        string sub register_loader(object loader) {
            return ClassLoaderAccessProbe::registerShared(loader);
        }
        string sub define_own() {
            return ClassLoaderAccessProbe::defineShared(GovernedProgramProbe::currentLoader());
        }
        object sub foreign_call(object parent) {
            return ClassLoaderAccessProbe::newForeignCall(parent);
        }
        string sub new_loader(object parent) {
            return ClassLoaderAccessProbe::newLoader(parent);
        }
        string sub benign() {
            return JvmPrimarySandboxTrust::benign();
        }
        string sub reach_host() {
            return JvmPrimarySandboxTrust::callHost();
        }
        ";

        hash<auto> sub drive() {
            hash<auto> h;
            object host_loader = GovernedProgramProbe::currentLoader();
            h.trusted_before = JvmPrimarySandboxTrust::callCurrent();
            h.trusted_foreign = ClassLoaderAccessProbe::runCall(ClassLoaderAccessProbe::newForeignCall(host_loader));
            Program t(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
            t.parse(TenantSource, "tenant");
            SandboxManager sm();
            sm.setUncontrolledApiPolicy(True);
            t.setSandboxManager(sm);
            h.tenant = t.callFunction("benign");
            h.trusted_after = JvmPrimarySandboxTrust::callCurrent();
            h.tenant_reach = t.callFunction("reach_host");
            h.unknown_loader = JvmPrimarySandboxTrust::callUnknownLoader();
            h.parent_path = t.callFunction("add_parent", "/nonexistent/qore-7131");
            h.register_host = t.callFunction("register_loader", host_loader);
            h.trusted_register = ClassLoaderAccessProbe::registerShared(host_loader);
            h.define_shared = t.callFunction("define_own");
            h.foreign_host = ClassLoaderAccessProbe::runCall(t.callFunction("foreign_call", host_loader));
            return h;
        }

        string sub who() {
            return 'host';
        }

        # Kotlin scripts of trusted and of sandboxed code
        hash<auto> sub drive_kotlin(string report, string reach) {
            hash<auto> h;
            h.trusted_before = KotlinScriptEngine::eval(report);
            ClassLoaderAccessProbe::setHolder(GovernedProgramProbe::currentLoader());
            Program t(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
            t.parse(TenantSource, "tenant");
            SandboxManager sm();
            sm.setUncontrolledApiPolicy(True);
            t.setSandboxManager(sm);
            h.tenant = t.callFunction("ev", report);
            h.trusted_after = KotlinScriptEngine::eval(report);
            h.tenant_reach = t.callFunction("ev", reach);
            return h;
        }

        # a class loader created after the JVM's start, by trusted and by sandboxed code
        hash<auto> sub drive_new_loader() {
            hash<auto> h;
            object host_loader = GovernedProgramProbe::currentLoader();
            h.trusted_new_loader = ClassLoaderAccessProbe::newLoader(host_loader);
            Program t(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
            t.parse(TenantSource, "tenant");
            SandboxManager sm();
            sm.setUncontrolledApiPolicy(True);
            t.setSandboxManager(sm);
            h.tenant = t.callFunction("benign");
            h.new_loader = t.callFunction("new_loader", host_loader);
            h.trusted_after = JvmPrimarySandboxTrust::callCurrent();
            return h;
        }
        """;

    //! reports the class of the script's class loader and the Program that a call into Qore runs in
    private static final String KOTLIN_REPORT = "val r = try { object {}.javaClass.classLoader.javaClass.simpleName + "
        + "\":\" + org.qore.jni.QoreJavaApi.callFunction(\"who\") } catch (e: Throwable) { \"refused: \" + e }\nr";

    //! calls into the Program of the class loader that the test published
    private static final String KOTLIN_REACH = "val r = try { org.qore.jni.test.ClassLoaderAccessProbe.holder!!"
        + ".setContext(); \"reached: \" + org.qore.jni.QoreJavaApi.callFunction(\"who\") } "
        + "catch (e: Throwable) { \"refused: \" + e }\nr";

    //! a Java call that makes no call back into Qore
    public static String benign() {
        return "benign";
    }

    //! calls a Qore function in the Program of the current class loader context
    public static String callCurrent() {
        try {
            return "ok: " + QoreJavaApi.callFunction("getpid");
        } catch (Throwable e) {
            return "refused: " + e;
        }
    }

    //! calls a Qore function in the Program of the system class loader
    public static String callHost() {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        try {
            ((QoreURLClassLoader)ClassLoader.getSystemClassLoader()).setContext();
            return "ok: " + QoreJavaApi.callFunction("getpid");
        } catch (Throwable e) {
            return "refused: " + e;
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }

    //! calls into Qore from the code of a class that it is run from; defined below by an unknown class loader
    public static class UnknownLoaderCall implements Supplier<String> {
        @Override
        public String get() {
            try {
                return "ok: " + QoreJavaApi.callFunction("getpid");
            } catch (Throwable e) {
                return "refused: " + e;
            }
        }
    }

    //! a class loader that belongs to no Program, whose parent is the system class loader
    private static class UnknownLoader extends ClassLoader {
        UnknownLoader() {
            super(ClassLoader.getSystemClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    //! calls into Qore from a class that an unknown class loader defines
    @SuppressWarnings("unchecked")
    public static String callUnknownLoader() throws Exception {
        String name = UnknownLoaderCall.class.getName();
        byte[] bytes;
        try (InputStream in = JvmPrimarySandboxTrust.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            bytes = in.readAllBytes();
        }
        Class<?> cls = new UnknownLoader().define(name, bytes);
        check(cls != UnknownLoaderCall.class, "the class is defined by the unknown class loader", cls.getClassLoader());
        return ((Supplier<String>)cls.getDeclaredConstructor().newInstance()).get();
    }

    private static void check(boolean ok, String what, Object value) {
        if (!ok) {
            throw new AssertionError(what + ": " + value);
        }
    }

    private static boolean isRefused(String rv) {
        return rv.startsWith("refused: ") && rv.contains("JNI-ERROR")
            && rv.contains("cannot call into a Qore Program other than the one of the calling code");
    }

    private static void checkRefused(String what, String rv) {
        check(isRefused(rv), "sandboxed class loader operation " + what, rv);
    }

    //! a class loader can be closed once the Qore library has been torn down, as a shutdown hook can do
    private static void checkCloseAfterShutdown() throws Throwable {
        QoreURLClassLoader loader = ClassLoaderAccessProbe.newDerived(
            (QoreURLClassLoader)ClassLoader.getSystemClassLoader());
        java.lang.reflect.Method shutdown = QoreURLClassLoader.class.getDeclaredMethod("shutdownContext0");
        shutdown.setAccessible(true);
        shutdown.invoke(null);
        loader.close();
        loader.clearCompilationCache();
        System.out.println("closed after shutdown");
    }

    //! the classes of a Kotlin script are attributed to the Program that evaluated it
    private static void checkKotlin(QoreObject pgm) throws Throwable {
        @SuppressWarnings("unchecked")
        Map<String, Object> h = (Map<String, Object>)pgm.callMethod("callFunction", "drive_kotlin", KOTLIN_REPORT,
            KOTLIN_REACH);
        check("QoreURLClassLoader:host".equals(h.get("trusted_before")), "trusted script", h.get("trusted_before"));
        check("QoreURLClassLoader:tenant".equals(h.get("tenant")), "sandboxed script", h.get("tenant"));
        check("QoreURLClassLoader:host".equals(h.get("trusted_after")), "trusted script after sandboxed code used Java",
            h.get("trusted_after"));
        checkRefused("tenant_reach", (String)h.get("tenant_reach"));
    }

    //! a class loader created after the JVM's start leaves the module's state as it is
    private static void checkNewLoader(QoreObject pgm) throws Throwable {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        @SuppressWarnings("unchecked")
        Map<String, Object> h = (Map<String, Object>)pgm.callMethod("callFunction", "drive_new_loader");
        check("ok: true".equals(h.get("trusted_new_loader")), "trusted class loader creation",
            h.get("trusted_new_loader"));
        checkRefused("new_loader", (String)h.get("new_loader"));
        String after = (String)h.get("trusted_after");
        check(after.startsWith("ok: "), "trusted call after class loaders were created", after);
        check(ClassLoader.getSystemClassLoader() == system, "system class loader", ClassLoader.getSystemClassLoader());
    }

    public static void main(String[] args) throws Throwable {
        ((QoreURLClassLoader)ClassLoader.getSystemClassLoader()).setContext();
        QoreObject pgm = QoreJavaApi.newObjectSave("Qore::Program", DRIVER_PARSE_OPTIONS);
        pgm.callMethod("parse", DRIVER, "driver");
        if (args.length == 1 && "new-loader".equals(args[0])) {
            checkNewLoader(pgm);
            return;
        }
        if (args.length == 1 && "close-after-shutdown".equals(args[0])) {
            checkCloseAfterShutdown();
            return;
        }
        if (args.length == 1 && "kotlin".equals(args[0])) {
            checkKotlin(pgm);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> h = (Map<String, Object>)pgm.callMethod("callFunction", "drive");

        String before = (String)h.get("trusted_before");
        String after = (String)h.get("trusted_after");
        String reach = (String)h.get("tenant_reach");
        check(before.startsWith("ok: "), "trusted call before sandboxed code used Java", before);
        check("benign".equals(h.get("tenant")), "sandboxed Java call", h.get("tenant"));
        check(after.startsWith("ok: "), "trusted call after sandboxed code used Java", after);
        check(reach.startsWith("refused: ") && reach.contains("JNI-ERROR"), "sandboxed code reaching the host's Program",
            reach);
        String unknown = (String)h.get("unknown_loader");
        check(unknown.startsWith("refused: ") && unknown.contains("JNI-ERROR"),
            "Java code of an unknown class loader reaching the host's Program", unknown);

        String foreign = (String)h.get("trusted_foreign");
        check(foreign.startsWith("ok: "), "trusted class loader before sandboxed code used Java", foreign);
        check("ok: registered".equals(h.get("trusted_register")), "trusted shared class registration",
            h.get("trusted_register"));
        StringBuilder failed = new StringBuilder();
        for (String key : new String[] {"parent_path", "register_host", "define_shared", "foreign_host"}) {
            String rv = (String)h.get(key);
            if (!isRefused(rv)) {
                failed.append(key).append(": ").append(rv).append("; ");
            }
        }
        check(failed.length() == 0, "sandboxed class loader operations that were not refused", failed);
    }
}
