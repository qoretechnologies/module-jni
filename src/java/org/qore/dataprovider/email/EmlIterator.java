/*  EmlIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.BodyPart;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.time.ZonedDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading .eml (RFC 822) email files.
 * Yields a single record per EML file with the shared email record schema.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class EmlIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private MimeMessage message;
    private InputStream inputStream;
    private boolean consumed = false;
    private Hash currentRecord = null;

    /**
     * Creates an EmlIterator from a Java InputStream.
     *
     * @param stream The input stream containing EML data
     */
    public EmlIterator(java.io.InputStream stream) throws Throwable {
        this.inputStream = stream;
        try {
            Session session = Session.getInstance(new Properties());
            this.message = new MimeMessage(session, stream);
        } catch (Throwable t) {
            stream.close();
            throw t;
        }
    }

    /**
     * Creates an EmlIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing EML data
     */
    public EmlIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates an EmlIterator from a file path.
     *
     * @param path The path to the .eml file
     */
    public EmlIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)));
    }

    /**
     * Advances to the next record. Returns true once (single message per EML), then false.
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
            currentRecord = parseMessage(message);
        } catch (Exception e) {
            currentRecord = null;
            return false;
        }
        return true;
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
     * Parses a MimeMessage into a Hash record with the shared email schema.
     *
     * @param msg The MimeMessage to parse
     * @return A Hash containing the email record fields
     * @throws MessagingException if there is an error reading the message
     * @throws IOException if there is an I/O error
     */
    static Hash parseMessage(MimeMessage msg) throws MessagingException, IOException {
        Hash record = new Hash();

        // From
        Address[] fromAddrs = msg.getFrom();
        if (fromAddrs != null && fromAddrs.length > 0) {
            record.put("from", ((InternetAddress) fromAddrs[0]).toString());
        } else {
            record.put("from", null);
        }

        // To
        record.put("to", getAddressList(msg.getRecipients(Message.RecipientType.TO)));

        // CC
        record.put("cc", getAddressList(msg.getRecipients(Message.RecipientType.CC)));

        // BCC
        record.put("bcc", getAddressList(msg.getRecipients(Message.RecipientType.BCC)));

        // Subject
        record.put("subject", msg.getSubject());

        // Date
        Date sentDate = msg.getSentDate();
        if (sentDate != null) {
            record.put("date", ZonedDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault()));
        } else {
            record.put("date", null);
        }

        // Body and attachments
        String bodyText = null;
        String bodyHtml = null;
        ArrayList<Hash> attachments = new ArrayList<>();

        Object content = msg.getContent();
        if (content instanceof String) {
            // Simple text message
            if (msg.isMimeType("text/html")) {
                bodyHtml = (String) content;
            } else {
                bodyText = (String) content;
            }
        } else if (content instanceof Multipart) {
            BodyContent bc = parseMultipart((Multipart) content);
            bodyText = bc.text;
            bodyHtml = bc.html;
            attachments = bc.attachments;
        }

        record.put("body_text", bodyText);
        record.put("body_html", bodyHtml);
        record.put("attachments", attachments.isEmpty() ? null : attachments);

        // Message-ID
        String[] messageIdHeader = msg.getHeader("Message-ID");
        record.put("message_id", (messageIdHeader != null && messageIdHeader.length > 0)
            ? messageIdHeader[0] : null);

        // In-Reply-To
        String[] inReplyToHeader = msg.getHeader("In-Reply-To");
        record.put("in_reply_to", (inReplyToHeader != null && inReplyToHeader.length > 0)
            ? inReplyToHeader[0] : null);

        return record;
    }

    /**
     * Converts an array of Address objects to an ArrayList of strings.
     *
     * @param addresses The addresses to convert
     * @return ArrayList of address strings, or null if no addresses
     */
    private static ArrayList<String> getAddressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        ArrayList<String> list = new ArrayList<>();
        for (Address addr : addresses) {
            list.add(((InternetAddress) addr).toString());
        }
        return list;
    }

    /**
     * Parses a Multipart MIME structure to extract text, HTML, and attachments.
     *
     * @param multipart The Multipart to parse
     * @return A BodyContent object with the extracted content
     * @throws MessagingException if there is an error reading the message
     * @throws IOException if there is an I/O error
     */
    private static BodyContent parseMultipart(Multipart multipart) throws MessagingException, IOException {
        BodyContent result = new BodyContent();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            String contentType = part.getContentType().toLowerCase();

            if (part.getContent() instanceof Multipart) {
                // Handle nested multipart (e.g., multipart/alternative inside multipart/mixed)
                BodyContent nested = parseMultipart((Multipart) part.getContent());
                if (result.text == null && nested.text != null) {
                    result.text = nested.text;
                }
                if (result.html == null && nested.html != null) {
                    result.html = nested.html;
                }
                result.attachments.addAll(nested.attachments);
            } else if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                       || Part.INLINE.equalsIgnoreCase(disposition)
                       || (disposition == null && !contentType.startsWith("text/"))) {
                // Attachment
                if (disposition != null || !contentType.startsWith("text/")) {
                    Hash attachment = new Hash();
                    String fileName = part.getFileName();
                    attachment.put("name", fileName != null ? fileName : "unnamed");
                    attachment.put("content_type", part.getContentType().split(";")[0].trim());
                    attachment.put("size", part.getSize());
                    result.attachments.add(attachment);
                }
            } else if (contentType.startsWith("text/plain") && result.text == null) {
                Object content = part.getContent();
                if (content instanceof String) {
                    result.text = (String) content;
                }
            } else if (contentType.startsWith("text/html") && result.html == null) {
                Object content = part.getContent();
                if (content instanceof String) {
                    result.html = (String) content;
                }
            }
        }

        return result;
    }

    /**
     * Closes the iterator and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
        message = null;
    }

    /**
     * Internal helper class for accumulating body content during multipart parsing.
     */
    private static class BodyContent {
        String text = null;
        String html = null;
        ArrayList<Hash> attachments = new ArrayList<>();
    }
}
