// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.List;

/** A generic class whose subclass has a bridge method with the same %Qore types as the method it bridges to. */
public class VarargsGenBase<T extends List<?>> {
    public VarargsGenBase() {
    }

    public String g(T t) {
        return "genbase:" + t.size();
    }
}
