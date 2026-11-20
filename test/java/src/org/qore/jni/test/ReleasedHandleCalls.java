// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreObject;

/** Uses Qore objects, closures and proxies that Java keeps after the jni module's shutdown has released their native
    handles (see test/jni-released-handles.qtest)

    The module's shutdown releases the native handle of every wrapper that still exists
    (Globals::stopNativeCleanupThread()); a JVM shutdown hook runs after that, when the module destroys the JVM.
*/
public final class ReleasedHandleCalls {
    private static volatile Object kept;

    private ReleasedHandleCalls() {
    }

    //! keeps the object (a QoreObject, a QoreClosure, or a proxy of a QoreInvocationHandler)
    public static void keep(Object obj) {
        kept = obj;
    }

    //! makes the call on the object kept, and returns <tt>"ok: "</tt> and the result, or the class and the message
    //! of the Throwable that the call threw
    public static String call(String op) {
        try {
            return "ok: " + callIntern(op);
        } catch (Throwable t) {
            return t.getClass().getName() + ": " + t.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object callIntern(String op) throws Throwable {
        switch (op) {
            case "closure":
                return ((QoreClosure)kept).call();
            case "closure-save":
                return ((QoreClosure)kept).callSave();
            case "classname":
                return ((QoreObject)kept).className();
            case "instanceof":
                return ((QoreObject)kept).instanceOf("Mutex");
            case "method":
                return ((QoreObject)kept).callMethod("get");
            case "releaseself":
                return ((QoreObject)kept).callMethod("releaseSelf");
            case "member":
                return ((QoreObject)kept).getMemberValue("m");
            case "proxy":
                return ((Supplier<Object>)kept).get();
            default:
                throw new IllegalArgumentException("unknown operation: " + op);
        }
    }

    private static volatile CountDownLatch inCall;
    private static volatile CountDownLatch gate;

    //! called by the Qore method that a Java thread calls in releaseWhileInUse(): signals that the call is in
    //! progress, and waits until the gate is opened
    public static void blockInCall() throws InterruptedException {
        inCall.countDown();
        gate.await();
    }

    //! sets NativeCleanup's release wait hook (with reflection, so that the class loads with any version of the
    //! module); returns false if the module has none
    private static boolean setReleaseWaitHook(Runnable hook) {
        try {
            Method m = Class.forName("org.qore.jni.NativeCleanup").getMethod("setReleaseWaitHook", Runnable.class);
            m.invoke(null, hook);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    //! Releases the object kept while a call of its method "block" (which calls blockInCall()) is in progress on
    //! another thread
    /** The release must wait until the call has returned: the hook that the release runs once it has seen the call,
        before it waits, opens the gate; the gate is opened after the release as well, so that the call returns with
        a module that does not wait.

        @return <tt>"hook: "</tt> and whether the release ran the hook, the result of the call, the result of the
        release, and the result of className() after the release
    */
    public static String releaseWhileInUse() throws InterruptedException {
        QoreObject obj = (QoreObject)kept;
        inCall = new CountDownLatch(1);
        gate = new CountDownLatch(1);
        final String[] result = {"no result"};
        Thread t = new Thread(() -> {
            try {
                result[0] = "ok: " + obj.callMethod("block");
            } catch (Throwable e) {
                result[0] = e.getClass().getName() + ": " + e.getMessage();
            }
        });
        t.start();
        inCall.await();
        final boolean[] ran = {false};
        boolean hook = setReleaseWaitHook(() -> {
            ran[0] = true;
            gate.countDown();
        });
        String release;
        try {
            obj.release();
            release = "released";
        } catch (Throwable e) {
            release = e.getClass().getName() + ": " + e.getMessage();
        }
        gate.countDown();
        t.join();
        if (hook) {
            setReleaseWaitHook(null);
        }
        return "hook: " + ran[0] + "; call: " + result[0] + "; release: " + release + "; after: " + call("classname");
    }

    //! releases the object kept; called by its Qore method in callReleaseSelf(), while the call uses the object
    public static String releaseKept() {
        try {
            ((QoreObject)kept).release();
            return "released";
        } catch (Throwable e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    //! calls the method "releaseSelf" of the object kept, which calls releaseKept() on the same thread; returns its
    //! result and the result of className() after it
    public static String callReleaseSelf() {
        return call("releaseself") + "; after: " + call("classname");
    }

    //! makes the call on the object kept when the JVM is destroyed, and prints <tt>shutdown: </tt> and the result on
    //! stderr
    public static void callAtShutdown(String op) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("shutdown: " + call(op));
            System.err.flush();
        }));
    }
}
