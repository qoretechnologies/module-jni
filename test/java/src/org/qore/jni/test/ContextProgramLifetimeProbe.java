// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreURLClassLoader;

/** A Java thread that calls a Qore function with no Qore Program context, so that the call is made in the jni
    module's global Java context Program, and that can be held after the module has looked up that Program and before
    it has used it (see test/jni-context-program-lifetime.qtest, Jni::dbg_jni_set_context_program_hook() and
    Jni::dbg_jni_set_shutdown_window_hook())

    The thread makes two calls of the same function with QoreJavaApi.callFunction(): the first one when start() is
    called, and the second one when the module's shutdown runs the Runnable returned by windowHook().  As a hook set
    with Jni::dbg_jni_set_context_program_hook(), the probe holds the thread in its first call, once the module has
    obtained the global context Program for it, until a Runnable returned by releaseHook() or windowHook() runs,
    whichever runs first.

    The result of each call is <tt>"ok: " + the result</tt>, or the class and the message of the Throwable that the
    call threw; windowHook() prints <tt>probe: released by: </tt> and the Runnable that released the held thread
    (<tt>"release"</tt> or <tt>"window"</tt>, or <tt>"none"</tt> if the thread was not held), then
    <tt>probe: first: </tt> and <tt>probe: second: </tt> and the results, on stderr.
*/
public final class ContextProgramLifetimeProbe implements BooleanSupplier {
    private final CountDownLatch obtained = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch secondGo = new CountDownLatch(1);
    private final Thread thread;
    private final boolean hold;
    // set when the thread is held, then to the Runnable that released it
    private final AtomicReference<String> releasedBy = new AtomicReference<>("none");
    private volatile String first = "no result from the Java thread";
    private volatile String second = "no result from the Java thread";

    //! creates the thread that calls the Qore function named \a function; if \a hold is true, its first call is held
    //! once the module has obtained the global context Program for it (the probe must then be set as the hook)
    public ContextProgramLifetimeProbe(String function, boolean hold) {
        this.hold = hold;
        thread = new Thread(() -> {
            // the thread's Java context is a class loader with no Qore Program, so that its calls into Qore are made
            // in the global context Program
            QoreURLClassLoader.createWithoutProgram(ContextProgramLifetimeProbe.class.getClassLoader());
            first = call(function);
            firstDone.countDown();
            // start() also returns if the call was not held (ex: it did not use the global context Program)
            obtained.countDown();
            try {
                secondGo.await();
            } catch (InterruptedException e) {
                second = "interrupted";
                return;
            }
            second = call(function);
        });
        thread.setDaemon(true);
    }

    private static String call(String function) {
        try {
            return "ok: " + QoreJavaApi.callFunction(function);
        } catch (Throwable t) {
            return t.getClass().getName() + ": " + t.getMessage();
        }
    }

    //! starts the thread, and returns once its first call is held (if the probe holds it) or has returned
    public void start() throws InterruptedException {
        thread.start();
        if (hold) {
            obtained.await();
        } else {
            firstDone.await();
        }
    }

    //! The hook: holds the probe's thread, in its first call, until a Runnable of the probe releases it
    /** @return true if the current thread is the probe's thread, which was held; false for any other thread
    */
    @Override
    public boolean getAsBoolean() {
        if (Thread.currentThread() != thread) {
            return false;
        }
        releasedBy.set("held");
        obtained.countDown();
        awaitUninterruptibly(proceed);
        return true;
    }

    //! Returns a Runnable that releases the thread held in its first call, and returns without waiting for it
    /** The module's shutdown runs it once it has seen that a thread holds the global context Program, before it waits
        for that thread (Jni::dbg_jni_set_context_program_wait_hook()): the thread then uses the Program while the
        shutdown waits, and the shutdown deletes the Program only once the thread has released it
    */
    public Runnable releaseHook() {
        return () -> release("release");
    }

    // releases the held thread, recording the Runnable that released it if it is the first to
    private void release(String by) {
        releasedBy.compareAndSet("held", by);
        proceed.countDown();
    }

    //! Returns a Runnable that releases the thread held in its first call, if it is held, waits until that call has
    //! returned, makes the thread make its second call, waits until it has returned, and prints the results on stderr
    public Runnable windowHook() {
        return () -> {
            release("window");
            awaitUninterruptibly(firstDone);
            secondGo.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            System.err.println("probe: released by: " + releasedBy.get());
            System.err.println("probe: first: " + first);
            System.err.println("probe: second: " + second);
            System.err.flush();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        };
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
