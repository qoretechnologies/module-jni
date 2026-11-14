// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** Overrides the varargs method of its parent, whose parent has the method with fixed parameters. */
public class VarargsGrand extends VarargsChild {
    public VarargsGrand() {
    }

    @Override
    public String over(String... a) {
        return "grand:" + VarargsCalls.describe(a);
    }
}
