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

//! A Java object whose call into Qore is chosen by the code that creates it, in a Program that its caller chooses
/** The class is defined by the class loader of the Program that loads the test jar first (qore#7081)
*/
public class SuppliedTask {
    //! Java state that every Program can reach: a value that one Program leaves for another to read
    public static Object shared;

    //! the Qore function that the call calls
    private final String function;

    //! creates the object for the Qore function given
    public SuppliedTask(String function) {
        this.function = function;
    }

    //! calls the Qore function in the Program of the class loader given, and restores the thread's Java context
    public Object callIn(QoreURLClassLoader context) throws Throwable {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        context.setContext();
        try {
            return QoreJavaApi.callFunction(function);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }
}
