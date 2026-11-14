// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

/** An interface with an abstract varargs method and a default varargs method. */
public interface VarargsIface {
    /** an abstract method with variable arguments */
    String join(String prefix, Object... values);

    /** a default method with variable arguments */
    default String joinDefault(String prefix, Object... values) {
        return "default:" + join(prefix, values);
    }
}
