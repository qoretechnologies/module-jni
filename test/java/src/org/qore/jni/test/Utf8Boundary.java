// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.util.LinkedHashMap;
import java.util.Map;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreException;

/** Helpers for the tests of strings at the JNI boundary (test/jni-strict-utf8.qtest) */
public final class Utf8Boundary {
    private Utf8Boundary() {
    }

    //! returns the UTF-16 units of a string, so that a test can check a string exactly
    public static int[] units(String str) {
        int[] rv = new int[str.length()];
        for (int i = 0; i < rv.length; ++i) {
            rv[i] = str.charAt(i);
        }
        return rv;
    }

    //! calls the closure and returns the message of the exception that it raises, or null
    public static String messageOf(QoreClosure closure) {
        try {
            closure.call();
            return null;
        } catch (Throwable t) {
            return t.getMessage();
        }
    }

    //! loads the class with the thread's context class loader and returns the message of the error, or null
    public static String loadError(String name) {
        try {
            Class.forName(name, true, Thread.currentThread().getContextClassLoader());
            return null;
        } catch (Throwable t) {
            return t.getMessage();
        }
    }

    //! throws a QoreException with the given code and description
    public static void throwQore(String err, String desc) throws QoreException {
        throw new QoreException(err, desc);
    }

    //! returns a map with the given key and value
    public static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> rv = new LinkedHashMap<>();
        rv.put(key, value);
        return rv;
    }

    //! returns the UTF-16 units of the first key of the map
    public static int[] firstKeyUnits(Map<String, Object> map) {
        return units(map.keySet().iterator().next());
    }

    //! returns the value of a system property as UTF-16 units, or null
    public static int[] propertyUnits(String name) {
        String value = System.getProperty(name);
        return value == null ? null : units(value);
    }
}
