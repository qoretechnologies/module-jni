// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.ArrayList;

/** Overrides the generic method with an ArrayList parameter; the compiler adds the bridge method g(List). */
public class VarargsGenMid extends VarargsGenBase<ArrayList<?>> {
    public VarargsGenMid() {
    }

    @Override
    public String g(ArrayList<?> t) {
        return "genmid:" + t.size();
    }
}
