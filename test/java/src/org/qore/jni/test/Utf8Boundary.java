// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.util.LinkedHashMap;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreException;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;

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

    //! returns a map with the key "outer" whose value is a map with the given key and the value 1
    public static Map<String, Object> nestedMapOf(String key) {
        return mapOf("outer", mapOf(key, 1));
    }

    //! returns a map with the key "a" with a string value, and then the key "outer" with a map with the given key
    public static Map<String, Object> valueThenNestedMapOf(String key) {
        Map<String, Object> rv = mapOf("a", "value");
        rv.put("outer", mapOf(key, 1));
        return rv;
    }

    //! returns a list with a string, and then a map with the given key
    public static java.util.List<Object> valueThenMapListOf(String key) {
        java.util.List<Object> rv = new java.util.ArrayList<>();
        rv.add("value");
        rv.add(mapOf(key, 1));
        return rv;
    }

    //! throws a QoreException with a map with the given key as its argument
    public static void throwQoreWithArg(String err, String desc, String key) throws QoreException {
        throw new QoreException(err, desc, mapOf(key, 1));
    }

    //! returns a map with the given two keys, with the values 1 and 2
    public static Map<String, Object> mapOf2(String key1, String key2) {
        Map<String, Object> rv = mapOf(key1, 1);
        rv.put(key2, 2);
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

    //! returns a public class made with the given names, in a new class loader, which can have NUL characters
    /** javac drops a NUL character (an identifier-ignorable character) from a name, but the class file format allows
        one (modified UTF-8 has it as C0 80); the class has a public int instance field, a public static int field
        and a public method that returns 1

        @param className the binary name of the class (ex: "org.qore.test.Name")
        @param fieldName the name of the instance field
        @param staticFieldName the name of the static field
        @param methodName the name of the method
    */
    public static Class<?> classWithNames(String className, String fieldName, String staticFieldName,
            String methodName) {
        return new ByteBuddy()
            .subclass(Object.class)
            .name(className)
            .modifiers(java.lang.reflect.Modifier.PUBLIC)
            .defineField(fieldName, int.class, Visibility.PUBLIC)
            .defineField(staticFieldName, int.class, Visibility.PUBLIC, Ownership.STATIC)
            .defineMethod(methodName, int.class, Visibility.PUBLIC)
            .intercept(FixedValue.value(1))
            .make()
            .load(new ClassLoader(Utf8Boundary.class.getClassLoader()) {}, ClassLoadingStrategy.Default.INJECTION)
            .getLoaded();
    }

    //! returns a public class with the given name that extends the given class, in a new class loader
    public static Class<?> subclassOf(Class<?> parent, String className) {
        return new ByteBuddy()
            .subclass(parent)
            .name(className)
            .modifiers(java.lang.reflect.Modifier.PUBLIC)
            .make()
            .load(new ClassLoader(parent.getClassLoader()) {}, ClassLoadingStrategy.Default.INJECTION)
            .getLoaded();
    }

    //! returns a public class with the given name with a public method get() that returns a new object of the given
    //! class, which must have a public constructor without arguments, in a new class loader
    public static Class<?> classReturning(Class<?> type, String className) throws ReflectiveOperationException {
        Object value = type.getDeclaredConstructor().newInstance();
        return new ByteBuddy()
            .subclass(Object.class)
            .name(className)
            .modifiers(java.lang.reflect.Modifier.PUBLIC)
            .defineMethod("get", type, Visibility.PUBLIC)
            .intercept(FixedValue.reference(value))
            .make()
            .load(new ClassLoader(type.getClassLoader()) {}, ClassLoadingStrategy.Default.INJECTION)
            .getLoaded();
    }

    //! calls a Qore function from Java with a map with the given key and the value 1 as its argument
    public static Object callWithMap(String function, String key) throws Throwable {
        return QoreJavaApi.callFunction(function, mapOf(key, 1));
    }

    //! calls a Qore function from Java with a map that has a map with the given key as the value of key "outer"
    public static Object callWithNestedMap(String function, String key) throws Throwable {
        return QoreJavaApi.callFunction(function, nestedMapOf(key));
    }

    //! calls a Qore function from Java with a map whose entrySet() throws an IllegalStateException
    public static Object callWithThrowingMap(String function) throws Throwable {
        Map<String, Object> map = new java.util.AbstractMap<String, Object>() {
            @Override
            public java.util.Set<Map.Entry<String, Object>> entrySet() {
                throw new IllegalStateException("entrySet() failed");
            }
        };
        return QoreJavaApi.callFunction(function, map);
    }

    //! calls a static method of a Qore class from Java
    public static Object callStaticMethod(String className, String methodName) throws Throwable {
        return QoreJavaApi.callStaticMethod(className, methodName);
    }

    //! creates an object of a Qore class from Java and returns the name of its class
    public static String newObjectClassName(String className) throws Throwable {
        return QoreJavaApi.newObjectSave(className).className();
    }

    //! calls a Qore method of an object from Java
    public static Object callMethod(QoreObject obj, String name) throws Throwable {
        return obj.callMethod(name);
    }

    //! returns the value of a member of a Qore object from Java
    public static Object memberOf(QoreObject obj, String name) throws Throwable {
        return obj.getMemberValue(name);
    }

    //! returns true if the Qore object is an instance of the given class, from Java
    public static boolean instanceOf(QoreObject obj, String className) {
        return obj.instanceOf(className);
    }

    //! loads the class with the given name with the thread's context class loader and returns its name
    public static String loadClassName(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader()).getName();
    }
}
