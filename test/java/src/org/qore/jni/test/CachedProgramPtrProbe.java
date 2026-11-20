/*
    Copyright (C) 2026 Qore Technologies, s.r.o.
    SPDX-License-Identifier: MIT
*/

package org.qore.jni.test;

import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreURLClassLoader;
import org.qore.jni.compiler.QoreJavaCompiler;

//! Calls into Qore from a Java thread whose class loader context has the Program pointer of a Qore Program
/** Used by test/jni-cached-program-ptr.qtest: the probe is created with the class loader of a Qore Program; its
    thread sets its class loader context up (see the constructor), then calls into Qore with an API that passes the
    Program pointer that its class loader context caches to a native method (see call()).

    The thread is held before the call, if the probe is created with \c "wait", until proceed() is called, so that the
    Program can be deleted before the call is made.  The probe is also a BooleanSupplier, which can be set as the hook
    of Jni::dbg_jni_set_java_program_ptr_hook(): if the probe is created with \c "hook", the native method runs it once
    it has received the Program pointer and before it uses it, and the probe holds the thread there until proceed() is
    called, so that the Program can be deleted in that window.
*/
public final class CachedProgramPtrProbe implements BooleanSupplier {
    private static volatile CachedProgramPtrProbe last;

    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private final Thread thread;
    private final String mode;
    private volatile long ptr;
    private volatile String result = "no result from the Java thread";

    //! creates the probe and its thread, which is started by start()
    /** @param loader the class loader of the Program
        @param setup how the thread sets its class loader context up: \c "context" makes \a loader the context, as a
        thread of a Java component that adopts the class loader of the Program that uses it does; \c "compiler"
        creates a QoreJavaCompiler for \a loader, whose class loader, which has the Program pointer of \a loader,
        becomes the context
        @param api the API of the call; see call()
        @param name the name used by the call; see call()
        @param target the Qore object or closure of the call, for the APIs \c "method" and \c "closure", or null
        @param mode \c "call": the call is made immediately; \c "wait": the thread waits until proceed() is called
        before it makes the call; \c "hook": the call is held by the hook where the native method has received the
        Program pointer (the probe must then be set as the hook); \c "hook-clear": as \c "hook", and the hook clears
        the pointer of the thread's class loader context once it is released, before the native method uses the
        pointer that it has received
    */
    private CachedProgramPtrProbe(QoreURLClassLoader loader, String setup, String api, String name, Object target,
            String mode) {
        this.mode = mode;
        thread = new Thread(() -> {
            try {
                QoreURLClassLoader context = setUp(loader, setup);
                ptr = context.getPtr();
                if (mode.equals("wait")) {
                    held.countDown();
                    awaitUninterruptibly(proceed);
                }
                result = "ok: " + call(context, api, name, target);
            } catch (Throwable t) {
                result = t.getClass().getName() + ": " + t.getMessage();
            } finally {
                done.countDown();
                // start() also returns if the call was not held
                held.countDown();
            }
        });
        thread.setDaemon(true);
    }

    //! sets the thread's class loader context up, and returns it
    private static QoreURLClassLoader setUp(QoreURLClassLoader loader, String setup) {
        switch (setup) {
            case "context":
                loader.setContext();
                break;
            case "compiler":
                // the compiler's class loader makes itself the thread's context when it is created
                new QoreJavaCompiler<Object>(loader, null);
                break;
            default:
                throw new IllegalArgumentException("unknown setup: " + setup);
        }
        return QoreURLClassLoader.getCurrent();
    }

    //! makes the call into Qore
    /** @param context the thread's class loader context
        @param api the API: \c "function" calls the Qore function \a name (QoreJavaApi.callFunction()), \c "static"
        calls the static method \a name, as <tt>class::method</tt> (QoreJavaApi.callStaticMethod()), \c "new" creates
        an object of the Qore class \a name (QoreJavaApi.newObjectSave()), and \c "class" loads the Java class
        generated for the Qore class \a name with the class loader (QoreURLClassLoader.loadClass(), whose class
        loader lists the classes of the package and generates the byte code of the class), \c "nsclass" as \c "class"
        for a class in a namespace (for which the canonical class loader is looked up), \c "compile" compiles a
        Java class that uses the Java class \a name with a QoreJavaCompiler for the class loader, \c "method" calls the
        method \a name of the Qore object \a target (QoreObject.callMethod()), and \c "closure" calls the Qore
        closure \a target (QoreClosure.call())
        @param name the name used by the call
        @param target the Qore object or closure of the call
    */
    private static Object call(QoreURLClassLoader context, String api, String name, Object target) throws Throwable {
        switch (api) {
            case "function":
                return QoreJavaApi.callFunction(name);
            case "static": {
                int i = name.indexOf("::");
                return QoreJavaApi.callStaticMethod(name.substring(0, i), name.substring(i + 2));
            }
            case "new": {
                Object obj = QoreJavaApi.newObjectSave(name);
                return obj == null ? null : obj.getClass().getName();
            }
            case "class":
            case "nsclass":
                return context.loadClass(name).getName();
            case "compile": {
                // compiles a class that uses the Qore class \a name with a compiler for the thread's class loader
                // context, which clears its Program's compilation cache once it is done
                QoreJavaCompiler<Object> compiler = new QoreJavaCompiler<Object>(context, null);
                try {
                    String src = "package pgmptr; public class Compiled { public static String get() { return "
                        + name + ".class.getName(); } }";
                    return compiler.compile("pgmptr.Compiled", src).cls.getName();
                } finally {
                    compiler.close();
                }
            }
            case "method":
                return ((QoreObject) target).callMethod(name);
            case "closure":
                return ((QoreClosure) target).call();
            default:
                throw new IllegalArgumentException("unknown API: " + api);
        }
    }

    //! creates a probe; see the constructor
    public static CachedProgramPtrProbe create(QoreURLClassLoader loader, String setup, String api, String name,
            Object target, String mode) {
        CachedProgramPtrProbe rv = new CachedProgramPtrProbe(loader, setup, api, name, target, mode);
        last = rv;
        return rv;
    }

    //! returns the probe created last, so that a Program other than the one that created it can use it, with no Qore
    //! object of the creating Program
    public static CachedProgramPtrProbe last() {
        return last;
    }

    //! returns the Program pointer of the thread's class loader context once it has been set up, or 0
    public long programPtr() {
        return ptr;
    }

    //! starts the thread, and returns once it is held (with \c "wait", \c "hook" or \c "hook-clear") or its call has
    //! returned
    public void start() throws InterruptedException {
        thread.start();
        if (mode.equals("call")) {
            done.await();
        } else {
            held.await();
        }
    }

    //! The hook: holds the probe's thread until proceed() is called
    /** @return true if the current thread is the probe's thread, which was held; false for any other thread
    */
    @Override
    public boolean getAsBoolean() {
        if (Thread.currentThread() != thread) {
            return false;
        }
        held.countDown();
        awaitUninterruptibly(proceed);
        if (mode.equals("hook-clear")) {
            // the Program exists, but the pointer that the native method received is no longer its class loader's
            QoreURLClassLoader.getCurrent().clearProgramPtr();
        }
        return true;
    }

    //! Checks that class loaders created for the Program of their parent share its pointer with the parent
    /** Two class loaders are created for the Program of \a loader, as QoreJavaCompiler creates its class loader; the
        pointer of the second is cleared, then the pointer of \a loader, as when its Program's jni data is destroyed
        (\a loader must not be used for a Program any more then)

        @return <tt>"shared: <bool> detached: <bool> follows: <bool>"</tt>: whether both class loaders had the
        pointer of \a loader, whether clearing the pointer of the second cleared it alone, and whether clearing the
        pointer of \a loader cleared the pointer of the first
    */
    public static String checkSharedPtr(QoreURLClassLoader loader) {
        long ptr = loader.getPtr();
        QoreURLClassLoader first = QoreURLClassLoader.createDerived(loader);
        QoreURLClassLoader second = QoreURLClassLoader.createDerived(loader);
        // the constructor makes the new class loader the thread's context
        loader.setContext();
        boolean shared = ptr != 0 && first.getPtr() == ptr && second.getPtr() == ptr;
        second.clearProgramPtr();
        boolean detached = second.getPtr() == 0 && first.getPtr() == ptr && loader.getPtr() == ptr;
        loader.clearProgramPtr();
        boolean follows = first.getPtr() == 0;
        return "shared: " + shared + " detached: " + detached + " follows: " + follows;
    }

    //! releases the held thread, waits until its call has returned, and returns the result of the call
    /** The result is <tt>"ok: " + the result</tt>, or the class and the message of the Throwable that the call threw
    */
    public String proceed() {
        proceed.countDown();
        awaitUninterruptibly(done);
        return result;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
