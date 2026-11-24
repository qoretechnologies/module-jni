// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectBase;
import org.qore.jni.QoreSandbox;

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
            case "policy":
                return ((QoreSandbox.Policy)kept).isSandboxed();
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

    //! releases the object kept and returns it, so that Qore converts a QoreObject whose handle has been released
    public static Object releaseAndReturn() {
        ((QoreObject)kept).release();
        return kept;
    }

    //! keeps a policy captured for the Qore code that calls this method
    public static void keepPolicy() {
        kept = QoreSandbox.capture();
    }

    //! Creates an object of the generated class of a Qore class (qore.Qore.Thread.Counter), and calls its method
    //! getCount() through the generated method, before and after the object is released
    /** @return the results of the calls
    */
    public static String generatedReleased() throws ReflectiveOperationException {
        Class<?> cls = Class.forName("qore.Qore.Thread.Counter", true, ReleasedHandleCalls.class.getClassLoader());
        Object counter = cls.getDeclaredConstructor().newInstance();
        Method getCount = cls.getMethod("getCount");
        String before = callMethod(getCount, counter);
        ((QoreObject)counter).release();
        return "before: " + before + "; after: " + callMethod(getCount, counter);
    }

    private static String callMethod(Method m, Object obj) {
        try {
            return "ok: " + m.invoke(obj);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            return t.getClass().getName() + ": " + t.getMessage();
        } catch (Throwable t) {
            return t.getClass().getName() + ": " + t.getMessage();
        }
    }

    private static final CountDownLatch neverOpened = new CountDownLatch(1);

    //! called by the Qore code of the closure that startBlockedCall() calls: signals that the call is in progress,
    //! and waits for a latch that is never opened
    public static void blockForever() throws InterruptedException {
        inCall.countDown();
        neverOpened.await();
    }

    //! Starts a daemon thread that calls the closure kept, whose Qore code calls blockForever(), and returns once the
    //! call is in progress; the call holds a use of the closure's handle until the process ends
    public static void startBlockedCall() throws InterruptedException {
        QoreClosure closure = (QoreClosure)kept;
        inCall = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                closure.call();
            } catch (Throwable e) {
                System.err.println("blocked call: " + e.getClass().getName() + ": " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
        inCall.await();
    }

    //! returns the state of the NativeCleanup.Ref of the closure kept (with reflection): whether its pointer has been
    //! cleared, whether it has been claimed for release, and the number of uses in progress
    private static String closureRefState() throws ReflectiveOperationException {
        Field refField = QoreClosure.class.getDeclaredField("ref");
        refField.setAccessible(true);
        Object ref = refField.get(kept);
        Class<?> refClass = ref.getClass();
        Field ptr = refClass.getDeclaredField("ptr");
        Field claimed = refClass.getDeclaredField("claimed");
        Field users = refClass.getDeclaredField("users");
        claimed.setAccessible(true);
        users.setAccessible(true);
        synchronized (ref) {
            return "cleared: " + (ptr.getLong(ref) == 0) + "; claimed: " + claimed.getBoolean(ref) + "; uses: "
                + users.getInt(ref);
        }
    }

    //! Registers a JVM shutdown hook that prints <tt>shutdown: </tt> and the state of the handle of the closure kept
    //! on stderr
    /** The hook runs when the JVM is destroyed as the process ends normally, after the module's shutdown has claimed
        the handles of the wrappers: that releases a handle that it claims only if no use of it is in progress
        (NativeCleanup.Ref.acquireForCleanup() returns 0 otherwise, and the release of 0 frees nothing), so a handle
        that is claimed and cleared, with a use still in progress, has been left unreleased.  It does not run when the
        process ends while a call is in progress in a Java thread (qore#7340): see "shutdown-in-use" in
        jni-released-handles-child.q, which checks that it did not.
    */
    public static void reportClosureAtShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String state;
            try {
                state = closureRefState();
            } catch (Throwable t) {
                state = t.getClass().getName() + ": " + t.getMessage();
            }
            System.err.println("shutdown: " + state);
            System.err.flush();
        }));
    }

    //! Ends a use of the handle of the object kept that was never begun, before and after the object is released;
    //! each must raise an IllegalStateException and change nothing, so that a call on the object still works before
    //! the release
    public static String exitUnmatched() {
        QoreObject obj = (QoreObject)kept;
        String before = exitResult(obj);
        String call = call("classname");
        // a valid use still begins and ends
        long p = obj.qoreJniEnterHandle();
        obj.qoreJniExitHandle();
        String valid = p != 0 ? "ok" : "no handle";
        obj.release();
        return "before: " + before + "; call: " + call + "; valid: " + valid + "; released: " + exitResult(obj)
            + "; after: " + call("classname");
    }

    //! ends a use of the object's handle that was never begun; returns the exception and the number of uses in
    //! progress afterwards (read from the object's NativeCleanup.Ref with reflection), which must still be 0
    private static String exitResult(QoreObject obj) {
        String rv;
        try {
            obj.qoreJniExitHandle();
            rv = "no exception";
        } catch (Throwable t) {
            rv = t.getClass().getName() + ": " + t.getMessage();
        }
        try {
            Field refField = QoreObjectBase.class.getDeclaredField("ref");
            refField.setAccessible(true);
            Object ref = refField.get(obj);
            Field users = ref.getClass().getDeclaredField("users");
            users.setAccessible(true);
            synchronized (ref) {
                rv += " (uses: " + users.getInt(ref) + ")";
            }
        } catch (ReflectiveOperationException e) {
            rv += " (uses: " + e + ")";
        }
        return rv;
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
