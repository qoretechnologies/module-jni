// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** An interface overloading the abstract method of its parent interface with an abstract varargs method. */
public interface VarargsSubIface extends VarargsBaseIface {
    String m(String... a);

    /** returns an object of a class that is not public, which Qore knows only by the interface */
    static VarargsSubIface make() {
        return new VarargsSubIface() {
            @Override
            public String m(String a) {
                return "fixed(" + a + ")";
            }

            @Override
            public String m(String... a) {
                return "varargs:" + VarargsCalls.describe(a);
            }
        };
    }

    /** calls both methods from Java */
    static String callBoth(VarargsSubIface i) {
        return i.m("x") + "|" + i.m("x", "y");
    }
}
