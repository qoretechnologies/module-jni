/* Copyright (C) 2026 Qore Technologies, s.r.o.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.qore.jni.test;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreURLClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

//! Operations on class loaders for the access control tests (qore#7131)
/** Each operation returns "ok: <result>" or "refused: <exception>", so that a test can assert the specific refusal.
*/
public class ClassLoaderAccessProbe {
    //! the name of the class that the shared class registry tests use
    public static final String FIXTURE = "qore.JniClassLoaderAccessFixture";

    //! a class loader that a test publishes to the Java code of a Kotlin script
    public static volatile QoreURLClassLoader holder;

    //! publishes \a loader to the Java code of a Kotlin script
    public static void setHolder(QoreURLClassLoader loader) {
        holder = loader;
    }

    //! evaluates a Kotlin script with one binding
    public static Object evalWithBinding(String script, String name, Object value) throws Exception {
        java.util.HashMap<String, Object> bindings = new java.util.HashMap<>();
        bindings.put(name, value);
        return org.qore.jni.KotlinScriptEngine.evalWithBindings(script, bindings);
    }

    private static String run(Callable<Object> op) {
        try {
            return "ok: " + op.call();
        } catch (Throwable e) {
            return "refused: " + e;
        }
    }

    private static byte[] classBytes(String name) throws IOException {
        try (InputStream in = ClassLoaderAccessProbe.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IOException("no class file for " + name);
            }
            return in.readAllBytes();
        }
    }

    //! adds \a dir to the classpath of the parent of \a loader
    public static String addParentPath(QoreURLClassLoader loader, String dir) {
        return run(() -> {
            loader.addParentPath(dir);
            return "added";
        });
    }

    //! returns true if the parent of \a loader, or \a loader if it has no such parent, has \a dir in its classpath
    public static boolean parentHasPath(QoreURLClassLoader loader, String dir) {
        ClassLoader parent = loader.getParent();
        QoreURLClassLoader target = parent instanceof QoreURLClassLoader ? (QoreURLClassLoader)parent : loader;
        String uri = new File(dir).toURI().toString();
        for (URL url : target.getURLs()) {
            if (url.toString().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    //! registers \a loader as the owner of the fixture class
    public static String registerShared(QoreURLClassLoader loader) {
        return run(() -> {
            loader.registerSharedDynamicClass(FIXTURE);
            return "registered";
        });
    }

    //! defines the fixture class through \a loader; returns which class loader defined it
    public static String defineShared(QoreURLClassLoader loader) {
        return run(() -> {
            byte[] bytes = classBytes(FIXTURE);
            Class<?> cls = loader.defineResolveClass(FIXTURE, bytes, 0, bytes.length);
            return cls.getClassLoader() == loader ? "this loader" : "another loader";
        });
    }

    //! creates a class loader with the public constructor; the thread's context is kept
    public static String newLoader(ClassLoader parent) {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ClassLoader prev_tccl = Thread.currentThread().getContextClassLoader();
        try {
            return run(() -> new QoreURLClassLoader("access-probe", parent) != null);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
            Thread.currentThread().setContextClassLoader(prev_tccl);
        }
    }

    //! creates a class loader for the Program of \a parent; the thread's context is kept
    public static QoreURLClassLoader newDerived(QoreURLClassLoader parent) {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ClassLoader prev_tccl = Thread.currentThread().getContextClassLoader();
        try {
            return QoreURLClassLoader.createDerived(parent);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
            Thread.currentThread().setContextClassLoader(prev_tccl);
        }
    }

    //! closes \a loader
    public static String close(QoreURLClassLoader loader) {
        return run(() -> {
            loader.close();
            return "closed";
        });
    }

    //! clears the compilation cache of \a loader
    public static String clearCompilationCache(QoreURLClassLoader loader) {
        return run(() -> {
            loader.clearCompilationCache();
            return "cleared";
        });
    }

    //! changes the default assertion status of \a loader
    public static String setAssertionStatus(QoreURLClassLoader loader) {
        return run(() -> {
            loader.setDefaultAssertionStatus(false);
            return "set";
        });
    }

    //! a subclass that records the object when the constructor of its base class calls an overridable method
    public static class RecordingLoader extends QoreURLClassLoader {
        static volatile RecordingLoader recorded;

        public RecordingLoader() {
            super();
        }

        public RecordingLoader(ClassLoader parent) {
            super(parent);
        }

        public RecordingLoader(String name, ClassLoader parent) {
            super(name, parent);
        }

        @Override
        public void setContext() {
            recorded = this;
            super.setContext();
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    //! creates a RecordingLoader with the public constructor \a ctor of the base class (0, 1 or 2)
    private static RecordingLoader newRecordingLoader(int ctor, ClassLoader parent) {
        switch (ctor) {
            case 0:
                return new RecordingLoader();
            case 1:
                return new RecordingLoader(parent);
            default:
                return new RecordingLoader("access-probe", parent);
        }
    }

    //! tries to create a RecordingLoader; reports the result, whether an object escaped and the context afterwards
    public static String tryRecordingLoader(int ctor, ClassLoader parent) {
        RecordingLoader.recorded = null;
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ClassLoader prev_tccl = Thread.currentThread().getContextClassLoader();
        String rv = run(() -> newRecordingLoader(ctor, parent) != null);
        boolean same = QoreURLClassLoader.getCurrent() == prev
            && Thread.currentThread().getContextClassLoader() == prev_tccl;
        if (prev != null) {
            prev.setContext();
        }
        Thread.currentThread().setContextClassLoader(prev_tccl);
        return rv + " | recorded=" + (RecordingLoader.recorded != null) + " context-unchanged=" + same;
    }

    //! returns an object of a class defined by the object that tryRecordingLoader() recorded, or null if there is none
    public static Object recordedCall() throws Exception {
        RecordingLoader loader = RecordingLoader.recorded;
        if (loader == null) {
            return null;
        }
        String name = ForeignCall.class.getName();
        return loader.define(name, classBytes(name)).getDeclaredConstructor().newInstance();
    }

    //! runs close() of \a object on a new thread through a proxy made only of JDK classes
    /** The error is taken from the thread that ran the proxy, whose stack has no frame of this class
    */
    public static String closeWithJdkProxy(Object object) {
        return run(() -> {
            java.lang.invoke.MethodHandle close = java.lang.invoke.MethodHandles.publicLookup().findVirtual(
                object.getClass(), "close", java.lang.invoke.MethodType.methodType(void.class)).bindTo(object);
            Runnable proxy = java.lang.invoke.MethodHandleProxies.asInterfaceInstance(Runnable.class, close);
            java.util.concurrent.atomic.AtomicReference<Thread> failed_on =
                new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
            Thread thread = new Thread(proxy);
            thread.setUncaughtExceptionHandler((t, e) -> {
                failed_on.set(t);
                error.set(e);
            });
            thread.start();
            thread.join();
            Throwable e = error.get();
            if (e == null) {
                return "closed";
            }
            if (failed_on.get() != thread) {
                throw new AssertionError("the proxy did not run on its own thread");
            }
            for (StackTraceElement frame : e.getStackTrace()) {
                if (frame.getClassName().startsWith(ClassLoaderAccessProbe.class.getName())) {
                    throw new AssertionError("a frame of the caller is on the stack: " + frame);
                }
            }
            throw (Exception)e;
        });
    }

    //! creates a Kotlin compiler for the Program of \a loader; the thread's context is kept
    public static Object newKotlinCompiler(QoreURLClassLoader loader) throws Exception {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ClassLoader prev_tccl = Thread.currentThread().getContextClassLoader();
        try {
            return Class.forName("org.qore.jni.compiler.QoreKotlinCompiler", true, loader)
                .getConstructor(QoreURLClassLoader.class, java.util.List.class).newInstance(loader, null);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
            Thread.currentThread().setContextClassLoader(prev_tccl);
        }
    }

    //! calls close() of \a object
    public static String closeObject(Object object) {
        return run(() -> {
            try {
                object.getClass().getMethod("close").invoke(object);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception)e.getCause();
            }
            return "closed";
        });
    }

    //! returns the result of isClosed() of \a object
    public static boolean isClosedObject(Object object) throws Exception {
        return (Boolean)object.getClass().getMethod("isClosed").invoke(object);
    }

    //! a compiled script whose module is not one that the Kotlin compiler makes
    public static class OtherCompiledScript extends javax.script.CompiledScript {
        public OtherCompiledScript getCompiledData() {
            return this;
        }

        public OtherCompiledScript getData() {
            return this;
        }

        public OtherCompiledScript getCompiledModule() {
            return this;
        }

        @Override
        public Object eval(javax.script.ScriptContext context) {
            return null;
        }

        @Override
        public javax.script.ScriptEngine getEngine() {
            return null;
        }
    }

    //! gives a compiled script with another module type to \a loader as the script engine does
    public static String addOtherCompiledScript(QoreURLClassLoader loader) {
        return run(() -> {
            java.lang.reflect.Method method = org.qore.jni.KotlinScriptEngine.class.getDeclaredMethod(
                "addCompiledClasses", QoreURLClassLoader.class, javax.script.CompiledScript.class);
            method.setAccessible(true);
            try {
                method.invoke(null, loader, new OtherCompiledScript());
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception)e.getCause();
            }
            return loader.getAllPendingClasses().isEmpty() ? "no classes" : "classes";
        });
    }

    //! calls into Qore from the code of the class that it is run from; defined by a class loader of no Program
    public static class ForeignCall implements Supplier<String> {
        @Override
        public String get() {
            try {
                return "ok: " + QoreJavaApi.callFunction("getpid");
            } catch (Throwable e) {
                return "refused: " + e;
            }
        }
    }

    //! a class loader that is not a QoreURLClassLoader, with any parent
    private static class ForeignLoader extends ClassLoader {
        ForeignLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    //! returns an object of a class defined by a class loader that is not a QoreURLClassLoader, with \a parent
    public static Object newForeignCall(ClassLoader parent) throws Exception {
        String name = ForeignCall.class.getName();
        Class<?> cls = new ForeignLoader(parent).define(name, classBytes(name));
        if (cls == ForeignCall.class) {
            throw new AssertionError("the class must be defined by the new class loader");
        }
        return cls.getDeclaredConstructor().newInstance();
    }

    //! runs an object returned by newForeignCall()
    @SuppressWarnings("unchecked")
    public static String runCall(Object call) {
        return ((Supplier<String>)call).get();
    }
}
