// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** Inherits m(String) from its parent class and implements m(String...) of an interface whose parent interface
    declares m(String). */
public class VarargsBoth extends VarargsFixedBase implements VarargsSubIface {
    public VarargsBoth() {
    }

    @Override
    public String m(String... a) {
        return "both:" + VarargsCalls.describe(a);
    }
}
