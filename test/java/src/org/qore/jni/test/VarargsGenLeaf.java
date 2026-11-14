// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.ArrayList;

/** Overloads the method of its parent, which has a bridge method, with a varargs method. */
public class VarargsGenLeaf extends VarargsGenMid {
    public VarargsGenLeaf() {
    }

    public String g(ArrayList<?>... t) {
        return "genleaf:" + t.length;
    }
}
