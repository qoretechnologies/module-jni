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

import java.util.AbstractList;

//! A List whose Java code calls into Qore when Qore reads it (test/jni-context-direct-calls.qtest, qore#6551)
/** Its only element, computed by get(), which Qore calls to convert the List to a list, is the answer of
    ContextProbe.which(); with \c fail set, get() raises an exception instead
*/
public class ContextProbeList extends AbstractList<Object> {
    private final boolean fail;

    public ContextProbeList() {
        this(false);
    }

    public ContextProbeList(boolean fail) {
        this.fail = fail;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public Object get(int index) {
        if (fail) {
            throw new IllegalStateException("the List raised an exception");
        }
        return ContextProbe.whichUnchecked();
    }
}
