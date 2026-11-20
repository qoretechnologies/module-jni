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

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreURLClassLoader;

//! Calls into Qore with the class loader context of another Program (qoretechnologies/qore#6845)
/** Java code is not governed by a sandbox: Java code that sandboxed code calls must not be able to run Qore code in a
    Program other than the one of the calling code, as when it makes the class loader of another Program its context
*/
public class GovernedProgramProbe {
    //! returns the class loader context of the calling thread, the class loader of the calling Program
    public static QoreURLClassLoader currentLoader() {
        return QoreURLClassLoader.getCurrent();
    }

    //! calls the Qore function \a function with \a loader as the thread's class loader context; returns "ok: <the
    //! result>" or the exception
    public static String callIn(QoreURLClassLoader loader, String function) {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        loader.setContext();
        try {
            return "ok: " + QoreJavaApi.callFunction(function);
        } catch (Throwable e) {
            return e.toString();
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }
}
