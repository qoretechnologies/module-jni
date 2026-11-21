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

        const TenantSource = "%modern
        %requires jni
        %module-cmd(jni) import org.qore.jni.test.JvmPrimarySandboxTrust
        string sub benign() {
            return JvmPrimarySandboxTrust::benign();
        }
        string sub reach_host() {
            return JvmPrimarySandboxTrust::callHost();
        }
        ";

        hash<auto> sub drive() {
            hash<auto> h;
            h.trusted_before = JvmPrimarySandboxTrust::callCurrent();
            Program t(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
            t.parse(TenantSource, "tenant");
            SandboxManager sm();
            sm.setUncontrolledApiPolicy(True);
            t.setSandboxManager(sm);
            h.tenant = t.callFunction("benign");
            h.trusted_after = JvmPrimarySandboxTrust::callCurrent();
            h.tenant_reach = t.callFunction("reach_host");
            h.unknown_loader = JvmPrimarySandboxTrust::callUnknownLoader();
            return h;
        }
        """;

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

    public static void main(String[] args) throws Throwable {
        ((QoreURLClassLoader)ClassLoader.getSystemClassLoader()).setContext();
        QoreObject pgm = QoreJavaApi.newObjectSave("Qore::Program", DRIVER_PARSE_OPTIONS);
        pgm.callMethod("parse", DRIVER, "driver");
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
    }
}
