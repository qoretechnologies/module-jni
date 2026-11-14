// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.ArrayList;

/** Overloads the methods of its parent with the same %Qore signature with a varargs method. */
public class VarargsListLeaf extends VarargsListMid {
    public VarargsListLeaf() {
    }

    public String h(ArrayList<?>... a) {
        return "listleaf:" + a.length;
    }
}
