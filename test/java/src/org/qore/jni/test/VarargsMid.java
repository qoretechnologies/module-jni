// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** Implements the abstract method with fixed parameters of its parent. */
public class VarargsMid extends VarargsAbsBase {
    public VarargsMid() {
    }

    @Override
    public String m(String a) {
        return "mid(" + a + ")";
    }
}
