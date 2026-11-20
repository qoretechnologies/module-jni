/*
    Copyright (C) 2026 Qore Technologies, s.r.o.
    SPDX-License-Identifier: MIT
*/

package org.qore.jni.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.qore.jni.QoreURLClassLoader;

//! Java code that tries to give the class loader a native Qore class pointer (qore#6854)
/** Used by test/jni-class-ptr.qtest: QoreURLClassLoader's entry points that take a class pointer must not be public,
    and a class pointer that Java code supplies, also through reflection, must be refused.
*/
public final class ClassPtrProbe {
    private ClassPtrProbe() {
    }

    //! returns true if the method of QoreURLClassLoader that takes a class pointer is public
    /** @param name \c "loadClassWithPtr" or \c "generateByteCode"
    */
    public static boolean isPublic(String name) throws NoSuchMethodException {
        return Modifier.isPublic(getMethod(name).getModifiers());
    }

    //! calls the method of QoreURLClassLoader that takes a class pointer through reflection
    /** @param loader the class loader
        @param name \c "loadClassWithPtr" or \c "generateByteCode"
        @param bin_name the binary name of the class
        @param class_ptr the class pointer

        @return \c "ok" if the call returned, or the class and message of the exception that it raised
    */
    public static String call(QoreURLClassLoader loader, String name, String bin_name, long class_ptr) {
        try {
            Method m = getMethod(name);
            m.setAccessible(true);
            m.invoke(loader, bin_name, class_ptr);
            return "ok";
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            while (cause.getCause() != null && !(cause.getMessage() != null
                    && cause.getMessage().contains("class pointer"))) {
                cause = cause.getCause();
            }
            return cause.getClass().getName() + ": " + cause.getMessage();
        } catch (Throwable t) {
            return t.getClass().getName() + ": " + t.getMessage();
        }
    }

    private static Method getMethod(String name) throws NoSuchMethodException {
        return QoreURLClassLoader.class.getDeclaredMethod(name, String.class, long.class);
    }
}
