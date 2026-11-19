/* -*- mode: java; indent-tabs-mode: nil -*- */
/** Copyright (C) 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.jni.test;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObjectBase;
import org.qore.jni.QoreURLClassLoader;

//! Java code that reports the Program that Java's calls into Qore resolve against (test/jni-context-direct-calls.qtest,
//! qore#6551)
/** which() calls the Qore function \c which() through QoreJavaApi.callFunction(), which resolves the function in
    the Program of the thread's Java context; each Program of the test defines \c which() to return its own name
*/
public class ContextProbe {
    //! a field holding a Map whose Java code calls into Qore when Qore reads the field
    public Object probe = new ContextProbeMap();

    //! a String field, whose value runs no Java code when Qore reads it
    public String name = "probe";

    //! a field holding a List whose Java code calls into Qore when Qore reads the field
    public Object probeList = new ContextProbeList();

    //! a field holding a List whose Java code raises an exception when Qore reads the field
    public Object failingList = new ContextProbeList(true);

    //! a primitive field, whose value runs no Java code when Qore reads it
    public int number = 7;

    //! the answer of which() in the static initializer of ContextProbeInit
    public static Object initWhich;

    //! the answer of which() in the static initializer of ContextProbeSysInit
    public static Object sysInitWhich;

    //! the answer of which() in the static initializer of ContextProbeInvokeInit
    public static Object invokeInitWhich;

    //! the answer of which() in the static initializer of ContextProbeInvokeNvInit
    public static Object invokeNvInitWhich;

    //! a static field holding a Map whose Java code calls into Qore when Qore reads the field
    public static Object staticProbe = new ContextProbeMap();

    //! Returns the answer of the Qore function \c which() in the Program of the thread's Java context
    public static Object which() throws Throwable {
        return QoreJavaApi.callFunction("which");
    }

    //! Returns the answer of the Qore function \c get_script_path() in the Program of the thread's Java context
    /** Every Program has the function, so the answer identifies the Program (QoreJavaApi.callFunction() falls back
        to the calling code's Program for a function that the context's Program does not have)
    */
    public static Object scriptPath() throws Throwable {
        return QoreJavaApi.callFunction("get_script_path");
    }

    //! Loads the given class with the thread's context class loader, and returns scriptPath() after it
    public static Object loadThenScriptPath(String name) throws Throwable {
        Class.forName(name, true, Thread.currentThread().getContextClassLoader());
        return scriptPath();
    }

    //! Lists the classes of the given package with the thread's context class loader (a QoreURLClassLoader), and
    //! returns scriptPath() after it
    public static Object listThenScriptPath(String packageName) throws Throwable {
        ((QoreURLClassLoader) Thread.currentThread().getContextClassLoader()).getClassesInNamespace(packageName);
        return scriptPath();
    }

    //! Runs the given Runnable, and returns which() after it
    public static Object runThenWhich(Runnable r) throws Throwable {
        r.run();
        return which();
    }

    //! which(), as an instance method
    public Object whichSelf() throws Throwable {
        return which();
    }

    //! which(), for Java code that cannot throw checked exceptions
    static Object whichUnchecked() {
        try {
            return which();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // defines the classes generated from Qore classes
    private static class Loader extends ClassLoader {
        Loader() {
            super(ContextProbe.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    //! Calls into Qore through Java classes generated from Qore classes, and returns which() after each call
    /** @param calls the bytecode of a class generated from a Qore class with a constructor, a destructor, a method
        \c value(), a method \c fail() that raises an exception, and a static method \c twice()
        @param functions the bytecode of the class generated for the functions of the class's namespace, with a function
        \c plus()

        @return a Map of the answers of which(): before the calls (\c "control"), and after the constructor, the
        method, a method that raises an exception, the static method, the function and the destructor
    */
    public static Map<String, Object> callGenerated(byte[] calls, byte[] functions) throws Throwable {
        Loader loader = new Loader();
        Class<?> cls = loader.define(calls);
        Class<?> fns = loader.define(functions);
        Map<String, Object> rv = new LinkedHashMap<>();
        rv.put("control", which());
        QoreObjectBase obj = (QoreObjectBase) cls.getConstructor().newInstance();
        try {
            rv.put("constructor", which());
            cls.getMethod("value").invoke(obj);
            rv.put("method", which());
            try {
                cls.getMethod("fail").invoke(obj);
                rv.put("exception", "no exception");
            } catch (InvocationTargetException e) {
                rv.put("exception", which());
            }
            cls.getMethod("twice").invoke(null);
            rv.put("static method", which());
            fns.getMethod("plus").invoke(null);
            rv.put("function", which());
        } finally {
            obj.destroy();
        }
        rv.put("destructor", which());
        return rv;
    }
}
