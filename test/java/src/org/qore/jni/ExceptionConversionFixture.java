// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Exercises Qore exception fields and failures while converting their arguments. */
public final class ExceptionConversionFixture {
    private static Object shutdownRoot;
    private ExceptionConversionFixture() {
    }

    public static void raiseError(String mode) {
        Object arg = mode.equals("bad-argument") ? new BrokenMap() : 42L;
        throw new QoreException(mode.equals("null-error") ? null : "CONVERSION-FIXTURE",
            mode.equals("null-description") ? null : "exception description",
            mode.equals("null-argument") ? null : arg);
    }

    public static void roundTrip() throws Throwable {
        QoreJavaApi.callFunction("referenceSafetyThrow");
    }

    public static Object invokeHandler(java.lang.reflect.InvocationHandler handler) throws Throwable {
        return handler.invoke(null, Object.class.getMethod("toString"), null);
    }

    public static Object returnObject(Object value) {
        return value;
    }

    public static void raiseObject(Object value) {
        throw new QoreException("OBJECT-FIXTURE", "object argument", value);
    }

    public static void retainForShutdown(Object value) {
        shutdownRoot = value;
    }

    private static final class BrokenMap extends HashMap<String, Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            throw new IllegalStateException("argument conversion failed");
        }
    }
}
