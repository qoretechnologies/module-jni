// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import org.qore.jni.QoreJavaApi;

/** Keeps a Throwable that a call into Qore threw, so that it is used after its Qore exception has been released (see
    test/jni-exception-wrapper-released.qtest)

    A Qore exception that a call from Java raises is thrown in Java as an org.qore.jni.QoreExceptionWrapper, which
    holds the Qore exception until Qore takes it (when the wrapper is thrown back to Qore) or the shutdown of the jni
    module releases it; Java can keep the wrapper after either.
*/
public final class ExceptionWrapperRelease {
    private static volatile Throwable kept;

    private ExceptionWrapperRelease() {
    }

    //! keeps the Throwable
    public static void keep(Throwable t) {
        kept = t;
    }

    //! calls the Qore function, keeps the Throwable that the call threw, and returns its class name
    public static String callAndKeep(String function) {
        try {
            QoreJavaApi.callFunction(function);
            return "no exception";
        } catch (Throwable t) {
            kept = t;
            return t.getClass().getName();
        }
    }

    //! throws the Throwable kept
    public static void throwKept() throws Throwable {
        throw kept;
    }

    //! returns the class and the message of the Throwable kept
    public static String describe() {
        Throwable t = kept;
        return t == null ? "none" : t.getClass().getName() + ": " + t.getMessage();
    }

    //! prints <tt>shutdown: </tt> and the description of the Throwable kept on stderr when the JVM is destroyed
    /** The JVM runs its shutdown hooks when the jni module destroys it, after the module has released the Qore
        exceptions of the wrappers that still exist.
    */
    public static void describeAtShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("shutdown: " + describe());
            System.err.flush();
        }));
    }
}
