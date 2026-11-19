// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;

/** Helpers for the tests of C++ exceptions in JNI native methods (test/jni-native-exception.qtest)

    Each helper calls Qore code through a native method and returns <tt>"ok: " + the result</tt>, or the class and the
    message of the Throwable that the call threw, so that a test can check the Java exception that the native method
    raised for a C++ exception.
*/
public final class NativeExceptionProbe {
    private NativeExceptionProbe() {
    }

    //! calls a Qore function with QoreJavaApi.callFunction()
    public static String callFunction(String function) {
        try {
            return "ok: " + QoreJavaApi.callFunction(function);
        } catch (Throwable t) {
            return describe(t);
        }
    }

    //! calls a Qore closure
    public static String callClosure(QoreClosure closure) {
        try {
            return "ok: " + closure.call();
        } catch (Throwable t) {
            return describe(t);
        }
    }

    //! calls a method of a Qore object
    public static String callMethod(QoreObject obj, String name) {
        try {
            return "ok: " + obj.callMethod(name);
        } catch (Throwable t) {
            return describe(t);
        }
    }

    //! returns the class and the message of a Throwable
    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }
}
