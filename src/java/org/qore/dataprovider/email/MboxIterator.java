/*  MboxIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading MBOX mailbox files.
 * Splits the stream on "From " line boundaries and yields one record per message.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class MboxIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private static final Pattern FROM_LINE_PATTERN = Pattern.compile("^From \\S+.*\\d{4}$|^From \\S+.*");

    private BufferedReader reader;
    private Session session;
    private int maxMessages;
    private int messageCount = 0;
    private Hash currentRecord = null;
    private String pendingLine = null;
    private boolean eof = false;

    /**
     * Creates an MboxIterator from a Java InputStream.
     *
     * @param stream The input stream containing MBOX data
     * @param maxMessages Maximum number of messages to read (0 for unlimited)
     */
    public MboxIterator(java.io.InputStream stream, int maxMessages) throws Throwable {
        this.reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.session = Session.getInstance(new Properties());
        this.maxMessages = maxMessages;

        // Read the first line to prime the parser
        skipToFirstMessage();
    }

    /**
     * Creates an MboxIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing MBOX data
     * @param maxMessages Maximum number of messages to read (0 for unlimited)
     */
    public MboxIterator(qore.Qore.InputStream stream, int maxMessages) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), maxMessages);
    }

    /**
     * Creates an MboxIterator from a file path.
     *
     * @param path The path to the .mbox file
     * @param maxMessages Maximum number of messages to read (0 for unlimited)
     */
    public MboxIterator(String path, int maxMessages) throws Throwable {
        this(new FileInputStream(new File(path)), maxMessages);
    }

    /**
     * Skips any leading blank lines and finds the first "From " line.
     */
    private void skipToFirstMessage() throws IOException {
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                eof = true;
                return;
            }
            if (isFromLine(line)) {
                pendingLine = line;
                return;
            }
        }
    }

    /**
     * Checks if a line is an MBOX "From " separator line.
     *
     * @param line The line to check
     * @return True if the line starts with "From "
     */
    private static boolean isFromLine(String line) {
        return line.startsWith("From ");
    }

    /**
     * Advances to the next message in the MBOX file.
     *
     * @return True if there is another message, false otherwise
     */
    public boolean next() {
        if (eof) {
            currentRecord = null;
            return false;
        }

        if (maxMessages > 0 && messageCount >= maxMessages) {
            currentRecord = null;
            return false;
        }

        try {
            byte[] messageBytes = readNextMessage();
            if (messageBytes == null || messageBytes.length == 0) {
                currentRecord = null;
                return false;
            }

            MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(messageBytes));
            currentRecord = EmlIterator.parseMessage(msg);
            messageCount++;
            return true;
        } catch (Exception e) {
            currentRecord = null;
            return false;
        }
    }

    /**
     * Reads the next message from the MBOX stream, accumulating lines until the next "From " separator
     * or end of stream.
     *
     * @return The raw message bytes, or null if no more messages
     * @throws IOException if there is an I/O error
     */
    private byte[] readNextMessage() throws IOException {
        if (pendingLine == null || !isFromLine(pendingLine)) {
            return null;
        }

        // Skip the "From " separator line
        StringBuilder messageBuilder = new StringBuilder();
        pendingLine = null;

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                eof = true;
                break;
            }
            if (isFromLine(line) && messageBuilder.length() > 0) {
                // Found the start of the next message
                pendingLine = line;
                break;
            }
            // Un-escape ">From " lines (MBOX escaping convention)
            if (line.startsWith(">From ")) {
                line = line.substring(1);
            }
            messageBuilder.append(line);
            messageBuilder.append("\r\n");
        }

        if (messageBuilder.length() == 0) {
            return null;
        }

        return messageBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the current record.
     *
     * @return The current record as a Hash
     * @throws QoreException if the iterator is not valid
     */
    public Hash getValue() throws QoreException {
        if (currentRecord == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return currentRecord;
    }

    /**
     * Checks if the iterator is in a valid state.
     *
     * @return True if the iterator is valid
     */
    public boolean valid() {
        return currentRecord != null;
    }

    /**
     * Returns the number of messages read so far.
     *
     * @return The message count
     */
    public int getCount() {
        return messageCount;
    }

    /**
     * Closes the reader and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
