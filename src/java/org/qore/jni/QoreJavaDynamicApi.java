/*
    QoreJavaDynamicApoi.java

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package org.qore.jni;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

// for DriverManager.getConnection() as called from Qore Datasource* classes through Qore DBI drivers
import java.util.ServiceLoader;
import java.util.Properties;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;

//! This class provides methods that allow Java to interface with Qore code
/** These Java methods set the correct ClassLoader context in the JVM; it's not otherwise possible to set
    programmatically.

    Any method that requires the ClassLoader context to be set should appear here.
 */
public class QoreJavaDynamicApi {
    //! Sets TCCL to the loader that defined the user-Java target before invoking it.
    /** Many widely-used Java libraries (Apache Xalan, Xerces, log4j, JAXB, ...)
        resolve their own resources (e.g. {@code Encodings.properties} in
        Xalan's serializer) via
        {@code Thread.currentThread().getContextClassLoader().getResourceAsStream(...)}.
        When Qore code invokes a Java method through this bridge, the invoking
        thread's TCCL is whatever happened to be set when the thread was
        attached — typically the JVM's system classloader, NOT the per-Program
        QoreURLClassLoader that has the user-deployed jars on its path.

        If a library's class is first triggered to load during such an
        invocation, its static initializer runs with that wrong TCCL.  Resource
        lookups via TCCL.getResourceAsStream(...) return null and the library's
        internal tables are permanently populated as empty.  In Xalan's
        Encodings that surfaces as every encoding being "unsupported" — even
        UTF-8 — the symptom that motivated this helper.  See the
        OdpDataProvider.qtest failure analysis in module-jni for a worked
        example.

        Setting TCCL to the target's defining class loader for the duration of
        the call gives library code a TCCL whose classpath matches the loader
        that actually defined the target class, so static initializers find
        their bundled resources.  We restore TCCL after the call so the change
        is invisible to the caller. */
    private static ClassLoader pushTccl(ClassLoader newTccl) {
        Thread t = Thread.currentThread();
        ClassLoader prev = t.getContextClassLoader();
        if (newTccl != null && newTccl != prev) {
            t.setContextClassLoader(newTccl);
            return prev;
        }
        // no-change sentinel
        return null;
    }

    private static void popTccl(ClassLoader prev) {
        if (prev != null) {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    //! creates an instance of the given class
    public static Object newInstance(Constructor<?> c, Object... args) throws Throwable {
        ClassLoader savedTccl = pushTccl(c.getDeclaringClass().getClassLoader());
        try {
            c.trySetAccessible();
            return c.newInstance(args);
        } catch (InvocationTargetException e) {
            Throwable e0 = e;
            while (e0 instanceof InvocationTargetException) {
                e0 = e0.getCause();
            }
            throw e0;
        } finally {
            popTccl(savedTccl);
        }
    }

    //! invokes the given method on the given object and returns the return value
    public static Object invokeMethod(Method m, Object obj, Object... args) throws Throwable {
        ClassLoader savedTccl = pushTccl(m.getDeclaringClass().getClassLoader());
        try {
            m.trySetAccessible();
            return m.invoke(obj, args);
        } catch (InvocationTargetException e) {
            Throwable e0 = e;
            while (e0 instanceof InvocationTargetException) {
                e0 = e0.getCause();
            }
            throw e0;
        } finally {
            popTccl(savedTccl);
        }
    }

    //! invokes the given method on the given object and returns the return value
    public static Object invokeMethodNonvirtual(Method m, Object obj, Object... args) throws Throwable {
        Class<?> c = m.getDeclaringClass();
        m.trySetAccessible();
        // works for all cases but generates a warning on the console if used with system classes
        return MethodHandles.privateLookupIn(c, MethodHandles.lookup()).unreflectSpecial(m,
            c).bindTo(obj).invokeWithArguments(args);
    }

    //! invokes the given method on the given object and returns the return value
    public static Object getField(Field f, Object obj) throws Throwable {
        f.setAccessible(true);
        return f.get(obj);
    }

    //! returns a lookup object for the program's context
    public static MethodHandles.Lookup lookup() {
        return MethodHandles.lookup();
    }

    //! sets the Java caller context and runs ServiceLoader.load()
    public static <S> ServiceLoader<S> loadServiceLoader(Class<S> c, ClassLoader cl) {
        return ServiceLoader.load(c, cl);
    }

    //! sets the Java caller context and runs DriverManager.getConnection()
    public static Connection getConnection(String url, Properties props) throws SQLException {
        return DriverManager.getConnection(url, props);
    }
}
