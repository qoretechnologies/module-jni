// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** A public class implementing an abstract varargs method. */
public class VarargsImpl implements VarargsIface {
    public VarargsImpl() {
    }

    @Override
    public String join(String prefix, Object... values) {
        return "impl." + prefix + ":" + VarargsCalls.describe(values);
    }

    /** returns an object of a class that is not public, which Qore knows only by the interface */
    public static VarargsIface makeHidden() {
        return new VarargsIface() {
            @Override
            public String join(String prefix, Object... values) {
                return "hidden." + prefix + ":" + VarargsCalls.describe(values);
            }
        };
    }

    /** calls the default method of the interface from Java */
    public static String callJoinDefault(VarargsIface i) {
        return i.joinDefault("d", "x", "y");
    }

    /** calls the abstract method of the interface from Java */
    public static String callJoin(VarargsIface i) {
        return i.join("j", "x", "y");
    }
}
