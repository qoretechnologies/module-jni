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

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreClosureMarker;
import org.qore.jni.QoreSandbox;

//! Java closures (QoreClosureMarker objects) for the cross-Program call tests (test/jni-cross-program-call.qtest)
public class CrossProgramCalls {
    //! A Java closure that calls the Qore closure that it was created with
    public static class Forwarder implements QoreClosureMarker {
        private final QoreClosure cb;

        public Forwarder(QoreClosure cb) {
            this.cb = cb;
        }

        public Object call() throws Throwable {
            return cb.call();
        }
    }

    //! A Java closure that returns QoreSandbox.isSandboxed() for the code that calls it
    public static class SandboxQuery implements QoreClosureMarker {
        public boolean call() {
            return QoreSandbox.isSandboxed();
        }
    }

    //! Returns a Java closure that calls the given Qore closure; it becomes a Qore closure in the calling Program
    public static QoreClosureMarker makeForwarder(QoreClosure cb) {
        return new Forwarder(cb);
    }

    //! Returns a Java closure that queries the sandbox of the code that calls it
    public static QoreClosureMarker makeSandboxQuery() {
        return new SandboxQuery();
    }
}
