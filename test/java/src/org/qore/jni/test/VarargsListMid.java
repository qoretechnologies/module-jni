// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.ArrayList;
import java.util.LinkedList;

/** Two methods with fixed parameters with the same %Qore signature. */
public class VarargsListMid {
    public VarargsListMid() {
    }

    public String h(ArrayList<?> a) {
        return "arraylist:" + a.size();
    }

    public String h(LinkedList<?> a) {
        return "linkedlist:" + a.size();
    }
}
