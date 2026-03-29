/*  CamelTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.camel;

/**
 * Static utility class providing common endpoint URIs for testing Camel routes.
 */
public class CamelTestHelper {
    /**
     * Returns the direct test endpoint URI.
     *
     * @return "direct:test"
     */
    public static String getDirectEndpoint() {
        return "direct:test";
    }

    /**
     * Returns the SEDA test endpoint URI.
     *
     * @return "seda:test"
     */
    public static String getSedaEndpoint() {
        return "seda:test";
    }

    /**
     * Returns the log test endpoint URI.
     *
     * @return "log:test"
     */
    public static String getLogEndpoint() {
        return "log:test";
    }
}
