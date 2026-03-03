/*  QoreInputStreamWrapper.java Copyright 2026 Qore Technologies, s.r.o.

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
 * Adapter class that bridges a Qore InputStream to a Java InputStream.
 * This allows Java code (e.g., Apache POI) to read directly from a Qore input stream.
 */
public class QoreInputStreamWrapper extends java.io.InputStream {
    private qore.Qore.InputStream stream;

    public QoreInputStreamWrapper(qore.Qore.InputStream stream) {
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        byte[] data;
        try {
            data = stream.read(1);
        } catch (Throwable e) {
            throw new IOException(e);
        }
        if (data == null) {
            return -1;
        }
        return data[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        byte[] data;
        try {
            data = stream.read(len);
        } catch (Throwable e) {
            throw new IOException(e);
        }
        if (data == null) {
            return -1;
        }
        System.arraycopy(data, 0, b, off, data.length);
        return data.length;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = 0;
        while (skipped < n) {
            int chunkSize = (int) Math.min(n - skipped, 8192);
            byte[] data;
            try {
                data = stream.read(chunkSize);
            } catch (Throwable e) {
                throw new IOException(e);
            }
            if (data == null) {
                break;
            }
            skipped += data.length;
            if (data.length < chunkSize) {
                break;
            }
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return 1;
    }
}
