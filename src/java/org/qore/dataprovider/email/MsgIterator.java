/*  MsgIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.time.ZonedDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Calendar;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Outlook .msg files using Apache POI HSMF.
 * Yields a single record per MSG file with the shared email record schema.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class MsgIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private MAPIMessage message;
    private boolean consumed = false;
    private Hash currentRecord = null;

    /**
     * Creates a MsgIterator from a Java InputStream.
     * The stream is fully read into memory since POI HSMF requires random access.
     *
     * @param stream The input stream containing MSG data
     */
    public MsgIterator(java.io.InputStream stream) throws Throwable {
        try {
            byte[] data = readAllBytes(stream);
            this.message = new MAPIMessage(new ByteArrayInputStream(data));
        } catch (Throwable t) {
            stream.close();
            throw t;
        } finally {
            stream.close();
        }
    }

    /**
     * Creates a MsgIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing MSG data
     */
    public MsgIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates a MsgIterator from a file path.
     *
     * @param path The path to the .msg file
     */
    public MsgIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)));
    }

    /**
     * Advances to the next record. Returns true once (single message per MSG), then false.
     *
     * @return True if there is a record available, false otherwise
     */
    public boolean next() {
        if (consumed) {
            currentRecord = null;
            return false;
        }
        consumed = true;
        try {
            currentRecord = parseMessage();
        } catch (Exception e) {
            currentRecord = null;
            return false;
        }
        return currentRecord != null;
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
     * Parses the MAPIMessage into a Hash record with the shared email schema.
     *
     * @return A Hash containing the email record fields
     */
    private Hash parseMessage() {
        Hash record = new Hash();

        // From
        String from = null;
        try {
            from = message.getDisplayFrom();
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("from", from);

        // To
        ArrayList<String> toList = null;
        try {
            String displayTo = message.getDisplayTo();
            if (displayTo != null && !displayTo.isEmpty()) {
                toList = splitAddresses(displayTo);
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("to", toList);

        // CC
        ArrayList<String> ccList = null;
        try {
            String displayCC = message.getDisplayCC();
            if (displayCC != null && !displayCC.isEmpty()) {
                ccList = splitAddresses(displayCC);
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("cc", ccList);

        // BCC
        ArrayList<String> bccList = null;
        try {
            String displayBCC = message.getDisplayBCC();
            if (displayBCC != null && !displayBCC.isEmpty()) {
                bccList = splitAddresses(displayBCC);
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("bcc", bccList);

        // Subject
        String subject = null;
        try {
            subject = message.getSubject();
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("subject", subject);

        // Date
        ZonedDateTime date = null;
        try {
            Calendar cal = message.getMessageDate();
            if (cal != null) {
                date = ZonedDateTime.ofInstant(cal.toInstant(), ZoneId.systemDefault());
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("date", date);

        // Body text
        String bodyText = null;
        try {
            bodyText = message.getTextBody();
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("body_text", bodyText);

        // Body HTML
        String bodyHtml = null;
        try {
            bodyHtml = message.getHtmlBody();
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("body_html", bodyHtml);

        // Attachments
        ArrayList<Hash> attachments = new ArrayList<>();
        try {
            for (org.apache.poi.hsmf.datatypes.AttachmentChunks att : message.getAttachmentFiles()) {
                Hash attachment = new Hash();
                String name = null;
                if (att.getAttachLongFileName() != null) {
                    name = att.getAttachLongFileName().getValue();
                }
                if ((name == null || name.isEmpty()) && att.getAttachFileName() != null) {
                    name = att.getAttachFileName().getValue();
                }
                attachment.put("name", name != null ? name : "unnamed");

                String contentType = null;
                if (att.getAttachMimeTag() != null) {
                    contentType = att.getAttachMimeTag().getValue();
                }
                attachment.put("content_type", contentType != null ? contentType : "application/octet-stream");

                int size = 0;
                if (att.getAttachData() != null) {
                    size = att.getAttachData().getValue().length;
                }
                attachment.put("size", size);

                attachments.add(attachment);
            }
        } catch (Exception e) {
            // ignore attachment errors
        }
        record.put("attachments", attachments.isEmpty() ? null : attachments);

        // Message-ID and In-Reply-To (not directly available in HSMF; try headers)
        String messageId = null;
        String inReplyTo = null;
        try {
            String[] headers = message.getHeaders();
            if (headers != null) {
                messageId = extractHeader(headers, "Message-ID");
                inReplyTo = extractHeader(headers, "In-Reply-To");
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        record.put("message_id", messageId);
        record.put("in_reply_to", inReplyTo);

        return record;
    }

    /**
     * Splits a semicolon-delimited address string into an ArrayList.
     *
     * @param addresses The semicolon-delimited address string
     * @return ArrayList of trimmed address strings
     */
    private static ArrayList<String> splitAddresses(String addresses) {
        ArrayList<String> list = new ArrayList<>();
        for (String addr : addresses.split(";")) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list.isEmpty() ? null : list;
    }

    /**
     * Extracts a header value from a raw headers array.
     *
     * @param headers The raw headers array (each element is a header line)
     * @param headerName The name of the header to extract
     * @return The header value, or null if not found
     */
    private static String extractHeader(String[] headers, String headerName) {
        String prefix = headerName + ":";
        for (String header : headers) {
            if (header == null) {
                continue;
            }
            // Each header entry may contain multiple lines; split and check each
            for (String line : header.split("\r?\n")) {
                if (line.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    return line.substring(prefix.length()).trim();
                }
            }
        }
        return null;
    }

    /**
     * Reads all bytes from an InputStream.
     *
     * @param stream The input stream to read
     * @return The byte array containing all data from the stream
     * @throws IOException if there is an I/O error
     */
    private static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Closes the iterator and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (message != null) {
            message.close();
            message = null;
        }
    }
}
