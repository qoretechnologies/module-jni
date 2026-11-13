// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A subclass overloading a method of its parent with variable arguments. */
public class VarargsChild extends VarargsBase {
    public VarargsChild() {
    }

    public String over(String... a) {
        return "child:" + VarargsCalls.describe(a);
    }
}
