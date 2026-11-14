// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** Overloads the method with fixed parameters that its parent implements with a varargs method. */
public class VarargsLeaf extends VarargsMid {
    public VarargsLeaf() {
    }

    public String m(String... a) {
        return "leaf:" + VarargsCalls.describe(a);
    }
}
