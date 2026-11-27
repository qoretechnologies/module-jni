/*  PathMarker.java Copyright 2026 Qore Technologies, s.r.o.

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

    SPDX-License-Identifier: MIT
*/

package org.qore.jni.test.provpath;

//! the class of the jar that test/jni-provider-path-restricted.qtest gives provider classpath options
/** The jar is on no classpath, so the class can only be loaded through an option that adds the jar; its static
    initializer records each class loader that ran it in a system property, so that the test sees whether the option
    made Java load and run the jar's code.
*/
public class PathMarker {
    //! the system property that counts the class loaders that ran the static initializer
    public static final String PROPERTY = "org.qore.jni.test.provpath.loaded";

    static {
        // the system properties are shared by the class loaders, each of which has its own PathMarker class
        synchronized (System.getProperties()) {
            int count = Integer.getInteger(PROPERTY, 0);
            System.setProperty(PROPERTY, Integer.toString(count + 1));
        }
    }

    //! creates the object, as a JDBC driver class is instantiated
    public PathMarker() {
    }

    //! creates the object, as a JMS connection factory class is instantiated with its URL
    public PathMarker(String url) {
    }
}
