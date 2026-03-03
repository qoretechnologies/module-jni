/*  QoreOutputStreamWrapper.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.jni;

import java.io.IOException;

/**
 * Adapter class that bridges a Qore OutputStream to a Java OutputStream.
 * This allows Java code (e.g., Apache POI) to write directly to a Qore output stream.
 */
public class QoreOutputStreamWrapper extends java.io.OutputStream {
    private qore.Qore.OutputStream stream;

    public QoreOutputStreamWrapper(qore.Qore.OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            stream.write(new byte[] {(byte) b});
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            if (off == 0 && len == b.length) {
                stream.write(b);
            } else {
                byte[] data = new byte[len];
                System.arraycopy(b, off, data, 0, len);
                stream.write(data);
            }
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        // No-op: the Qore caller manages the stream lifecycle
    }
}
