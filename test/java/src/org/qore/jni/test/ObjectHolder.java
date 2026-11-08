// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.util.concurrent.CountDownLatch;

/** Holds a Java object across Qore Programs: the object outlives the Program that stores it */
public final class ObjectHolder {
    private static volatile Object value;

    private ObjectHolder() {
    }

    public static void set(Object v) {
        value = v;
    }

    private static CountDownLatch entered = new CountDownLatch(1);
    private static CountDownLatch release = new CountDownLatch(1);

    /** Resets the latches of block() */
    public static void reset() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    /** Waits in this Java call until release(), after making awaitEntered() return: the caller is in the call when
        it is cancelled */
    public static void block() throws InterruptedException {
        entered.countDown();
        release.await();
    }

    public static void awaitEntered() throws InterruptedException {
        entered.await();
    }

    public static void release() {
        release.countDown();
    }

    /** Returns the object and releases it */
    public static Object take() {
        Object v = value;
        value = null;
        return v;
    }
}
