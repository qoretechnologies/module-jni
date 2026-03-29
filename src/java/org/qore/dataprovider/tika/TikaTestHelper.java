/*  TikaTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.tika;

import java.nio.charset.StandardCharsets;

/**
 * Static utility class providing test data for Tika extraction tests.
 */
public final class TikaTestHelper {
    /**
     * Private constructor to prevent instantiation.
     */
    private TikaTestHelper() {
    }

    /**
     * Creates a small HTML file as bytes for testing.
     *
     * @return HTML content as UTF-8 bytes
     */
    public static byte[] createTestHtml() {
        return "<html><head><title>Test</title></head><body><p>Hello World</p></body></html>"
            .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a small XML file as bytes for testing.
     *
     * @return XML content as UTF-8 bytes
     */
    public static byte[] createTestXml() {
        return "<?xml version=\"1.0\"?><root><item>Test Data</item></root>"
            .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a plain text file as bytes for testing.
     *
     * @return Plain text content as UTF-8 bytes
     */
    public static byte[] createTestPlainText() {
        return "This is a plain text test file.\nLine 2.".getBytes(StandardCharsets.UTF_8);
    }
}
