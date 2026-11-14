// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.util.Arrays;

/** Reports on the Java side exactly which arguments a call from Qore passes, for fixed and variable arguments. */
public final class VarargsCalls {
    private final String name;

    public VarargsCalls(String name) {
        this.name = name;
    }

    /** a constructor with variable arguments */
    public VarargsCalls(String name, Object... values) {
        this.name = name + describe(values);
    }

    public String getName() {
        return name;
    }

    /** Describes a value with its Java class, recursing into arrays. */
    public static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Object[]) {
            Object[] a = (Object[])value;
            StringBuilder sb = new StringBuilder(value.getClass().getComponentType().getSimpleName()).append("[");
            for (int i = 0; i < a.length; ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(describe(a[i]));
            }
            return sb.append("]").toString();
        }
        if (value instanceof long[]) {
            return "long" + Arrays.toString((long[])value);
        }
        if (value instanceof byte[]) {
            return "byte" + Arrays.toString((byte[])value);
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    /** a static method with two fixed parameters and no variable arguments */
    public static String two(String a, String b) {
        return "two(" + a + ", " + b + ")";
    }

    /** an instance method with two fixed parameters and no variable arguments */
    public String twoInstance(String a, String b) {
        return name + ".two(" + a + ", " + b + ")";
    }

    /** a static method that takes only variable arguments */
    public static String objects(Object... values) {
        return describe(values);
    }

    /** a static method that takes a fixed argument and variable arguments */
    public static String mixed(String prefix, Object... values) {
        return prefix + ":" + describe(values);
    }

    /** a static method that takes a fixed primitive argument and variable String arguments */
    public static String strings(int count, String... values) {
        return count + ":" + describe(values);
    }

    /** a static method that takes primitive variable arguments */
    public static String longs(String prefix, long... values) {
        return prefix + ":" + describe(values);
    }

    /** a static method that takes byte variable arguments */
    public static String bytes(String prefix, byte... values) {
        return prefix + ":" + describe(values);
    }

    /** an instance method that takes a fixed argument and variable arguments */
    public String mixedInstance(String prefix, Object... values) {
        return name + "." + prefix + ":" + describe(values);
    }
}
