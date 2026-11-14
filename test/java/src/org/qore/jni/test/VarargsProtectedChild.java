// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A subclass overloading a protected method of its parent with a public varargs method. */
public class VarargsProtectedChild extends VarargsProtectedBase {
    public VarargsProtectedChild() {
    }

    public String over(String... a) {
        return "child:" + VarargsCalls.describe(a);
    }
}
