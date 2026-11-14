// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A class with a protected method with fixed parameters that a subclass overloads with variable arguments. */
public class VarargsProtectedBase {
    public VarargsProtectedBase() {
    }

    protected String over(String a) {
        return "protected(" + a + ")";
    }
}
