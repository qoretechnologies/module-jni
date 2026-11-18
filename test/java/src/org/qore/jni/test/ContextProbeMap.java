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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

//! A Map whose Java code calls into Qore when Qore reads it (test/jni-context-direct-calls.qtest, qore#6551)
/** Its only entry, computed by entrySet(), which Qore calls to convert the Map to a hash, is \c "which", with the
    answer of ContextProbe.which(); toString() also returns that answer
*/
public class ContextProbeMap extends AbstractMap<String, Object> {
    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return Collections.singletonMap("which", ContextProbe.whichUnchecked()).entrySet();
    }

    @Override
    public String toString() {
        return "which:" + ContextProbe.whichUnchecked();
    }
}
