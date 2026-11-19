// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

/** Java code for jni-sigterm-libjsig.qtest: the JVM's own use of signals in a %Qore process */
public final class JvmSignals {
    private JvmSignals() {
    }

    /** An object whose field is read by read() */
    public static final class Holder {
        public int value;

        public Holder(int value) {
            this.value = value;
        }
    }

    /** Reads the field: the JIT compilers check the reference for null implicitly, with a load that faults (SIGSEGV)
        when the reference is null, which the JVM's signal handler turns into a NullPointerException */
    public static int read(Holder holder) {
        return holder.value;
    }

    /** Calls read() until the JIT compilers have compiled it (in the loop, and by on-stack replacement of this
        method), then with null; returns the number of NullPointerExceptions raised by the null calls

        @param iterations the number of calls with an object, enough for the method to be compiled
        @param nullCalls the number of calls with null
    */
    public static int warmUpAndReadNull(int iterations, int nullCalls) {
        Holder holder = new Holder(1);
        long sum = 0;
        for (int i = 0; i < iterations; ++i) {
            sum += read(holder);
        }
        if (sum != iterations) {
            throw new IllegalStateException("unexpected sum " + sum);
        }
        int npes = 0;
        for (int i = 0; i < nullCalls; ++i) {
            try {
                read(null);
            } catch (NullPointerException e) {
                ++npes;
            }
        }
        return npes;
    }

    /** Reads the field of a null reference: the NullPointerException is raised to the %Qore caller */
    public static int readNull() {
        return read(null);
    }

    /** Adds a shutdown hook that prints a line to standard output: it runs when the JVM ends the process with
        System.exit() and when the JVM is destroyed, but not when the process ends with _Exit() */
    public static void addShutdownHookMarker(String line) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(line);
            System.out.flush();
        }));
    }

    /** Calls System.exit() in the calling thread */
    public static void exit(int status) {
        System.exit(status);
    }

    /** Calls Runtime.halt() in the calling thread, which does not run the shutdown hooks */
    public static void halt(int status) {
        Runtime.getRuntime().halt(status);
    }

    /** Calls System.exit() in a new Java thread and waits for it: the process ends while the caller waits */
    public static void exitInJavaThread(int status) throws InterruptedException {
        Thread t = new Thread(() -> System.exit(status), "JvmSignals-exit");
        t.start();
        t.join();
    }
}
