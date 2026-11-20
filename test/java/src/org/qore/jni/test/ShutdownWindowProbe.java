// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;

/** A Java thread that calls into Qore exactly when the jni module's shutdown runs this Runnable (see
    test/jni-shutdown-window-context.qtest and Jni::dbg_jni_set_shutdown_window_hook())

    The thread is started when the probe is created and waits until run() is called; run() releases it, waits until
    its call into Qore has returned, and prints <tt>probe: </tt> and the result (<tt>"ok: " + the result</tt>, or the
    class and the message of the Throwable that the call threw) on stderr.  With a mode that ends in
    <tt>"-late"</tt>, the message of the Throwable is not read in the window: the Throwable is kept with
    ExceptionWrapperRelease.keep(), which can print its message when the JVM is destroyed.
*/
public final class ShutdownWindowProbe implements Runnable {
    private final CountDownLatch go = new CountDownLatch(1);
    private final Thread thread;
    private volatile String result = "no result from the Java thread";

    //! starts the thread that makes the call: \a mode is "function" (QoreJavaApi.callFunction() of the function named
    //! by \a arg), "newobject" (QoreJavaApi.newObjectSave() of the class named by \a arg), "instanceof"
    //! (QoreObject.instanceOf("Mutex") of the object in \a arg) or "generated" (the constructor of the generated Java
    //! class named by \a arg, which is loaded here, before the shutdown; the constructor calls
    //! QoreObjectBase.create0()), with an optional "-late" suffix
    public ShutdownWindowProbe(String mode_arg, Object call_arg) throws ClassNotFoundException {
        final boolean late = mode_arg.endsWith("-late");
        final String mode = late ? mode_arg.substring(0, mode_arg.length() - 5) : mode_arg;
        final Object arg = mode.equals("generated")
            ? Class.forName((String)call_arg, true, ShutdownWindowProbe.class.getClassLoader())
            : call_arg;
        thread = new Thread(() -> {
            try {
                go.await();
            } catch (InterruptedException e) {
                result = "interrupted";
                return;
            }
            try {
                result = "ok: " + call(mode, arg);
            } catch (Throwable t) {
                if (late) {
                    ExceptionWrapperRelease.keep(t);
                    result = t.getClass().getName() + " (kept)";
                } else {
                    result = t.getClass().getName() + ": " + t.getMessage();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static Object call(String mode, Object arg) throws Throwable {
        switch (mode) {
            case "function":
                return QoreJavaApi.callFunction((String)arg);
            case "newobject":
                return QoreJavaApi.newObjectSave((String)arg).className();
            case "instanceof":
                return ((QoreObject)arg).instanceOf("Mutex");
            case "generated":
                try {
                    return ((Class<?>)arg).getDeclaredConstructor().newInstance();
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            default:
                throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    //! releases the thread, waits until its call has returned, and prints the result on stderr
    @Override
    public void run() {
        go.countDown();
        try {
            thread.join();
        } catch (InterruptedException e) {
            result = "interrupted while waiting";
        }
        System.err.println("probe: " + result);
        System.err.flush();
    }
}
