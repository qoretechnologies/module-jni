/*  ExcelFile.java Copyright 2026 Qore Technologies, s.r.o.

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


package org.qore.dataprovider.excel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A copy of a workbook in a temporary file.
 *
 * The data providers read their input stream once into this file, in bounded memory, and then open the file as
 * often as they need (for the record type, the records, the worksheet names, and the worksheet providers).  The file
 * is deleted when its creator and every user registered with retain() have closed it, or when it is garbage
 * collected without being closed; iterators that are still open on the file keep reading it on platforms that allow
 * open files to be deleted.
 */
public class ExcelFile implements java.io.Closeable {
    // deletes the files of objects that are garbage collected without being closed
    private static final Cleaner CLEANER = Cleaner.create();

    private final Path path;
    private final Cleaner.Cleanable cleanable;
    // the number of users of the file: 1 for its creator plus one for each call to retain() not yet closed
    private int refs = 1;

    /**
     * Copies the remaining data of a Qore input stream to a temporary file
     */
    public ExcelFile(qore.Qore.InputStream stream) throws IOException {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Copies the remaining data of an input stream to a temporary file; the stream is not closed
     */
    public ExcelFile(InputStream stream) throws IOException {
        Path tmp = Files.createTempFile("qore-excelfile-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                stream.transferTo(out);
            }
            cleanable = CLEANER.register(this, new Deleter(tmp));
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
        path = tmp;
    }

    /**
     * Returns the path of the temporary file
     */
    public String getPath() {
        return path.toString();
    }

    /**
     * Registers another user of the file, which must close it when done
     *
     * @return this object
     */
    public synchronized ExcelFile retain() {
        if (refs == 0) {
            throw new IllegalStateException("the file is closed");
        }
        ++refs;
        return this;
    }

    /**
     * Releases the file for its creator or a user registered with retain(); the file is deleted when the last one
     * releases it
     */
    @Override
    public synchronized void close() throws IOException {
        if (refs == 0 || --refs > 0) {
            return;
        }
        Files.deleteIfExists(path);
        cleanable.clean();
    }

    /**
     * Deletes the file of an object that is no longer reachable; this does not refer to the object
     */
    private static final class Deleter implements Runnable {
        private final Path path;

        Deleter(Path path) {
            this.path = path;
        }

        @Override
        public void run() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // ignored: the object is no longer reachable, so there is no caller to report the error to
            }
        }
    }
}
