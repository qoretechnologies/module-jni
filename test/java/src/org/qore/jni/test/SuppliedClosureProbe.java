/* -*- mode: java; indent-tabs-mode: nil -*- */
/** Copyright (C) 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
    documentation files (the "Software"), to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of
    the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
    THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
    TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

package org.qore.jni.test;

import java.util.function.Function;

import org.qore.jni.QoreClosureMarkerImpl;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreURLClassLoader;

//! A Java object used as a Qore closure, whose call into Qore is chosen by the code that creates it (qore#7081)
/** The class is defined by the class loader of the Program that loads the test jar first (the host), so its code is
    attributed to that Program whatever code creates the object: the object keeps the Program of the code that hands
    it across a Program boundary as its provenance
*/
public class SuppliedClosureProbe implements QoreClosureMarkerImpl {
    //! the Qore function that the call calls
    private final String function;

    //! the Java context of the call, or null for the thread's
    private final QoreURLClassLoader context;

    //! creates the object for the Qore function given
    public SuppliedClosureProbe(String function) {
        this(function, null);
    }

    //! creates the object for the Qore function given, called in the Program of the class loader given
    public SuppliedClosureProbe(String function, QoreURLClassLoader context) {
        this.function = function;
        this.context = context;
    }

    //! calls the Qore function with the arguments given, in the Program of the Java context
    public Object call(Object... args) throws Throwable {
        if (context == null) {
            return QoreJavaApi.callFunction(function, args);
        }
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        context.setContext();
        try {
            return QoreJavaApi.callFunction(function, args);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }

    //! applies \a f to \a arg on a new Java thread whose Java context is \a context, and returns the result
    /** @throw Throwable the exception that the call threw on the thread
    */
    public static Object applyOnThread(Function<Object, Object> f, Object arg, QoreURLClassLoader context)
            throws Throwable {
        Object[] rv = new Object[1];
        Throwable[] err = new Throwable[1];
        Thread t = new Thread(() -> {
            context.setContext();
            try {
                rv[0] = f.apply(arg);
            } catch (Throwable e) {
                err[0] = e;
            }
        });
        t.start();
        t.join();
        if (err[0] != null) {
            throw err[0];
        }
        return rv[0];
    }
}
