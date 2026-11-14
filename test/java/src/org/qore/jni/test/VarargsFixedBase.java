// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A class with a public method with fixed parameters. */
public class VarargsFixedBase {
    public VarargsFixedBase() {
    }

    public String m(String a) {
        return "fixedbase(" + a + ")";
    }
}
