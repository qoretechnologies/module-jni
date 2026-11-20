// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreExceptionWrapper;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreURLClassLoader;

/** Calls the native methods of the jni module in a new Java thread that Java starts, which is not attached to Qore
    (test/jni-unattached-thread.qtest, qore#6828)

    Each helper makes its call in a new plain Java thread, waits for the thread to end, and returns
    <tt>"ok: " + the result</tt>, or the class and the message of the Throwable that the call threw.
*/
public final class UnattachedThreadCalls {
    private UnattachedThreadCalls() {
    }

    //! a call that can throw any Throwable
    @FunctionalInterface
    private interface Call {
        Object call() throws Throwable;
    }

    //! returns the result of \a call made in a new Java thread
    private static String inThread(Call call) throws InterruptedException {
        final String[] rv = {"no result from the Java thread"};
        Thread t = new Thread(() -> {
            try {
                rv[0] = "ok: " + call.call();
            } catch (Throwable e) {
                rv[0] = describe(e);
            }
        });
        t.start();
        t.join();
        return rv[0];
    }

    //! QoreObject.instanceOf()
    public static String instanceOf(QoreObject obj, String class_name) throws InterruptedException {
        return inThread(() -> obj.instanceOf(class_name));
    }

    //! QoreObject.className()
    public static String className(QoreObject obj) throws InterruptedException {
        return inThread(() -> obj.className());
    }

    //! QoreObject.callMethod()
    public static String callMethod(QoreObject obj, String name) throws InterruptedException {
        return inThread(() -> obj.callMethod(name));
    }

    //! QoreObject.callMethod() with no Program in the class loader context of the thread
    /** The Program pointer of the class loader context is 0 for the call (and then restored), so the native method
        looks up the Program context of the thread
    */
    public static String callMethodNoProgram(QoreObject obj, String name) throws InterruptedException {
        return inThread(() -> {
            long ptr = QoreURLClassLoader.getProgramPtr();
            QoreURLClassLoader.setProgramPtr(0);
            try {
                return obj.callMethod(name);
            } finally {
                QoreURLClassLoader.setProgramPtr(ptr);
            }
        });
    }

    //! QoreClosure.call() with no Program in the class loader context of the thread; see callMethodNoProgram()
    public static String callClosureNoProgram(QoreClosure closure) throws InterruptedException {
        return inThread(() -> {
            long ptr = QoreURLClassLoader.getProgramPtr();
            QoreURLClassLoader.setProgramPtr(0);
            try {
                return closure.call();
            } finally {
                QoreURLClassLoader.setProgramPtr(ptr);
            }
        });
    }

    //! loads the class \a name with the class loader of the thread's context, inherited from the calling thread
    public static String loadClass(String name) throws InterruptedException {
        return inThread(() -> Class.forName(name, true, QoreURLClassLoader.getCurrent()).getName());
    }

    //! returns the class loader of the user module \a name from QoreURLClassLoader.getModuleLoader0(), which is
    //! called with reflection, as it is private
    public static String moduleLoader(String name) throws InterruptedException {
        return inThread(() -> {
            Method m = QoreURLClassLoader.class.getDeclaredMethod("getModuleLoader0", String.class);
            m.setAccessible(true);
            Object loader;
            try {
                loader = m.invoke(null, name);
            } catch (InvocationTargetException e) {
                // the exception of the native method
                throw e.getCause();
            }
            return loader == null ? "null" : loader.getClass().getName();
        });
    }

    //! QoreClosure.call()
    public static String callClosure(QoreClosure closure) throws InterruptedException {
        return inThread(() -> closure.call());
    }

    //! QoreObject.release(): releases the weak reference of the wrapper to the object
    public static String release(QoreObject obj) throws InterruptedException {
        return inThread(() -> {
            obj.release();
            return "released";
        });
    }

    //! the wrapper held by hold()
    private static QoreObject held;

    //! holds the wrapper of a Qore object, whose weak reference is then released by releaseHeld()
    public static void hold(QoreObject obj) {
        held = obj;
    }

    //! QoreObject.release() of the wrapper held by hold()
    /** If the Qore object has no other reference, the release frees it
    */
    public static String releaseHeld() throws InterruptedException {
        QoreObject obj = held;
        held = null;
        return release(obj);
    }

    //! QoreExceptionWrapper.getMessage() of the error of the Qore function \a function, called in the calling thread
    public static String exceptionMessage(String function, Object... args) throws Throwable {
        QoreExceptionWrapper err;
        try {
            QoreJavaApi.callFunction(function, args);
            return "no error from " + function;
        } catch (QoreExceptionWrapper e) {
            err = e;
        }
        final QoreExceptionWrapper e = err;
        return inThread(() -> e.getMessage());
    }

    //! QoreJavaApi.openJdbcConnection() with a connection factory that returns a connection named \a name
    public static String openJdbcConnection(String name) throws InterruptedException {
        return inThread(() -> {
            try (AutoCloseable c = QoreJavaApi.openJdbcConnection(() -> new NamedConnection(name))) {
                return c.toString();
            }
        });
    }

    //! a connection for openJdbcConnection()
    private static final class NamedConnection implements AutoCloseable {
        private final String name;

        NamedConnection(String name) {
            this.name = name;
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }
}
