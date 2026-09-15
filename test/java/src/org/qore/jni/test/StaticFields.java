package org.qore.jni.test;

import java.util.HashMap;
import java.util.Map;

public class StaticFields {

    private static boolean z;
    public static byte b;
    public static char c;
    public static short s;
    public static int i;
    public static long j;
    public static float f;
    public static double d;
    public static Number n;
    public static Long l;

    public static byte[] ba = new byte[3];
    public static Object o;
    public static Object[] oa = new Object[3];
    public static Integer[] ia = new Integer[3];

    // a map with string keys is converted to a Qore hash; one with other keys stays a Java object
    public static Map<String, String> stringKeyMap = new HashMap<String, String>();
    public static Map<Integer, String> otherKeyMap = new HashMap<Integer, String>();

    static {
        stringKeyMap.put("key", "value");
        otherKeyMap.put(1, "value");
    }

    public static Integer wrap(int i) {
        return i;
    }

    public static int unwrapN() {
        return n.intValue();
    }
}
