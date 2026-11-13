// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A class with a method with fixed parameters that a subclass overloads with variable arguments, and a varargs
    method that a %Qore subclass overrides. */
public class VarargsBase {
    public VarargsBase() {
    }

    public String over(String a) {
        return "base(" + a + ")";
    }

    /** a varargs method that a %Qore subclass overrides */
    public String fmt(String... values) {
        return "base.fmt:" + VarargsCalls.describe(values);
    }

    /** calls over() from Java */
    public static String callOver(VarargsBase b) {
        return b.over("j");
    }

    /** calls fmt() from Java */
    public static String callFmt(VarargsBase b) {
        return b.fmt("x", "y");
    }
}
