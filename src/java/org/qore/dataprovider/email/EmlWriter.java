/*  EmlWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.time.ZonedDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.qore.jni.Hash;

/**
 * Writer for creating EML (RFC 822 / MIME) email messages.
 * Accumulates MimeMessage objects and writes them to a file, stream, or byte array.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class EmlWriter implements Closeable {
    private ArrayList<MimeMessage> messages = new ArrayList<>();
    private Session session;

    /**
     * Creates a new EmlWriter with a default mail session.
     */
    public EmlWriter() {
        Properties props = new Properties();
        session = Session.getInstance(props);
    }

    /**
     * Writes an email message from a Hash record.
     * EML is a single-message format; only one message can be written.
     * Calling this method more than once replaces the previous message.
     *
     * Expected keys: from, to, cc, bcc, subject, date, body_text, body_html,
     * attachments, message_id, in_reply_to, reply_to, references
     *
     * @param data the email data as a Hash
     * @throws Exception if there is an error creating the message
     */
    public void writeMessage(Hash data) throws Exception {
        // EML is single-message format; replace any previous message
        messages.clear();
        MimeMessage msg = new MimeMessage(session);

        // from
        Object fromVal = data.get("from");
        if (fromVal != null && !fromVal.toString().isEmpty()) {
            msg.setFrom(new InternetAddress(fromVal.toString()));
        }

        // to (list of strings)
        Object toVal = data.get("to");
        if (toVal != null) {
            Object[] toArr = toArray(toVal);
            for (Object addr : toArr) {
                if (addr != null) {
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(addr.toString()));
                }
            }
        }

        // cc (optional list)
        Object ccVal = data.get("cc");
        if (ccVal != null) {
            Object[] ccArr = toArray(ccVal);
            for (Object addr : ccArr) {
                if (addr != null) {
                    msg.addRecipient(Message.RecipientType.CC, new InternetAddress(addr.toString()));
                }
            }
        }

        // bcc (optional list)
        Object bccVal = data.get("bcc");
        if (bccVal != null) {
            Object[] bccArr = toArray(bccVal);
            for (Object addr : bccArr) {
                if (addr != null) {
                    msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(addr.toString()));
                }
            }
        }

        // subject
        Object subjectVal = data.get("subject");
        if (subjectVal != null) {
            msg.setSubject(subjectVal.toString());
        }

        // date
        Object dateVal = data.get("date");
        if (dateVal != null) {
            if (dateVal instanceof ZonedDateTime) {
                msg.setSentDate(java.util.Date.from(((ZonedDateTime) dateVal).toInstant()));
            } else if (dateVal instanceof java.util.Date) {
                msg.setSentDate((java.util.Date) dateVal);
            }
        } else {
            msg.setSentDate(new java.util.Date());
        }

        // headers: in_reply_to
        Object inReplyToVal = data.get("in_reply_to");
        if (inReplyToVal != null && !inReplyToVal.toString().isEmpty()) {
            msg.setHeader("In-Reply-To", inReplyToVal.toString());
        }

        // headers: reply_to
        Object replyToVal = data.get("reply_to");
        if (replyToVal != null && !replyToVal.toString().isEmpty()) {
            msg.setReplyTo(new Address[]{new InternetAddress(replyToVal.toString())});
        }

        // headers: references
        Object referencesVal = data.get("references");
        if (referencesVal != null && !referencesVal.toString().isEmpty()) {
            msg.setHeader("References", referencesVal.toString());
        }

        // body and attachments
        String bodyText = null;
        String bodyHtml = null;
        Object bodyTextVal = data.get("body_text");
        if (bodyTextVal != null) {
            bodyText = bodyTextVal.toString();
        }
        Object bodyHtmlVal = data.get("body_html");
        if (bodyHtmlVal != null) {
            bodyHtml = bodyHtmlVal.toString();
        }

        Object attachmentsVal = data.get("attachments");
        Object[] attachments = null;
        if (attachmentsVal != null) {
            attachments = toArray(attachmentsVal);
        }

        boolean hasAttachments = attachments != null && attachments.length > 0;
        boolean hasHtml = bodyHtml != null && !bodyHtml.isEmpty();
        boolean hasText = bodyText != null && !bodyText.isEmpty();

        if (!hasAttachments && !hasHtml) {
            // Simple text-only message
            msg.setText(bodyText != null ? bodyText : "", "UTF-8");
        } else if (!hasAttachments) {
            // Text + HTML, no attachments -> multipart/alternative
            MimeMultipart alternative = new MimeMultipart("alternative");
            if (hasText) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(bodyText, "UTF-8");
                alternative.addBodyPart(textPart);
            }
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(bodyHtml, "text/html; charset=UTF-8");
            alternative.addBodyPart(htmlPart);
            msg.setContent(alternative);
        } else {
            // Has attachments -> multipart/mixed
            MimeMultipart mixed = new MimeMultipart("mixed");

            // Body part(s)
            if (hasHtml) {
                MimeMultipart alternative = new MimeMultipart("alternative");
                if (hasText) {
                    MimeBodyPart textPart = new MimeBodyPart();
                    textPart.setText(bodyText, "UTF-8");
                    alternative.addBodyPart(textPart);
                }
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(bodyHtml, "text/html; charset=UTF-8");
                alternative.addBodyPart(htmlPart);
                MimeBodyPart bodyWrapper = new MimeBodyPart();
                bodyWrapper.setContent(alternative);
                mixed.addBodyPart(bodyWrapper);
            } else if (hasText) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(bodyText, "UTF-8");
                mixed.addBodyPart(textPart);
            }

            // Attachment parts
            for (Object attObj : attachments) {
                if (attObj == null) {
                    continue;
                }
                if (!(attObj instanceof Hash)) {
                    continue;
                }
                Hash attHash = (Hash) attObj;

                MimeBodyPart attPart = new MimeBodyPart();
                String name = "attachment";
                Object nameVal = attHash.get("name");
                if (nameVal != null) {
                    name = nameVal.toString();
                }

                String contentType = "application/octet-stream";
                Object ctVal = attHash.get("content_type");
                if (ctVal != null) {
                    contentType = ctVal.toString();
                }

                Object dataVal = attHash.get("data");
                if (dataVal instanceof byte[]) {
                    DataSource ds = new ByteArrayDataSource((byte[]) dataVal, contentType);
                    attPart.setDataHandler(new DataHandler(ds));
                } else if (dataVal != null) {
                    DataSource ds = new ByteArrayDataSource(
                        dataVal.toString().getBytes("UTF-8"), contentType);
                    attPart.setDataHandler(new DataHandler(ds));
                }
                attPart.setFileName(name);
                attPart.setDisposition(Part.ATTACHMENT);
                mixed.addBodyPart(attPart);
            }

            msg.setContent(mixed);
        }

        // Set message-id AFTER saveChanges if provided
        Object messageIdVal = data.get("message_id");
        msg.saveChanges();
        if (messageIdVal != null && !messageIdVal.toString().isEmpty()) {
            msg.setHeader("Message-ID", messageIdVal.toString());
        }

        messages.add(msg);
    }

    /**
     * Writes the message to a file.
     *
     * @param path the output file path
     * @throws IOException if an I/O error occurs
     * @throws MessagingException if there is an error writing the message
     */
    public void writeToFile(String path) throws IOException, MessagingException {
        if (messages.isEmpty()) {
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(path)) {
            messages.get(0).writeTo(fos);
        }
    }

    /**
     * Writes the message to a Java OutputStream.
     *
     * @param stream the output stream
     * @throws IOException if an I/O error occurs
     * @throws MessagingException if there is an error writing the message
     */
    public void writeToStream(OutputStream stream) throws IOException, MessagingException {
        if (!messages.isEmpty()) {
            messages.get(0).writeTo(stream);
        }
    }

    /**
     * Writes all accumulated messages to a Qore OutputStream.
     *
     * @param stream the Qore output stream
     * @throws IOException if an I/O error occurs
     * @throws MessagingException if there is an error writing the message
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException, MessagingException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the message as bytes.
     *
     * @return the EML data as a byte array
     * @throws IOException if an I/O error occurs
     * @throws MessagingException if there is an error writing the message
     */
    public byte[] getBytes() throws IOException, MessagingException {
        if (messages.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messages.get(0).writeTo(out);
        return out.toByteArray();
    }

    /**
     * Returns the number of messages accumulated.
     *
     * @return the message count
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Closes the writer and releases resources.
     */
    @Override
    public void close() throws IOException {
        messages = null;
        session = null;
    }

    /**
     * Converts an object to an array of Objects.
     * Handles both Object[] arrays (from Qore lists) and java.util.List.
     *
     * @param val the value to convert
     * @return an array of objects
     */
    private static Object[] toArray(Object val) {
        if (val instanceof Object[]) {
            return (Object[]) val;
        } else if (val instanceof List) {
            return ((List<?>) val).toArray();
        }
        return new Object[]{val};
    }
}
