/*  EmailTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.util.Properties;

/**
 * Helper class for creating test email files for the EmailDataProvider tests.
 * Provides methods to create .eml, .msg, and .mbox test fixtures.
 */
public class EmailTestHelper {

    private static Session getSession() {
        return Session.getInstance(new Properties());
    }

    /**
     * Creates a simple plain-text EML file.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleEml(String path) throws Exception {
        byte[] bytes = createSimpleEmlBytes();
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            fos.write(bytes);
        }
    }

    /**
     * Creates a simple plain-text EML and returns the raw bytes.
     *
     * @return The EML file as bytes
     */
    public static byte[] createSimpleEmlBytes() throws Exception {
        MimeMessage msg = new MimeMessage(getSession());
        msg.setFrom(new InternetAddress("sender@example.com", "Test Sender"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("recipient@example.com", "Test Recipient"));
        msg.setSubject("Test Subject");
        msg.setSentDate(new Date(1735689600000L)); // 2025-01-01T00:00:00 UTC
        msg.setText("This is a plain text test email body.", "utf-8");
        msg.setHeader("Message-ID", "<test-001@example.com>");
        msg.saveChanges();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            msg.writeTo(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Creates an EML file with an HTML body.
     *
     * @param path The path to create the file at
     */
    public static void createHtmlEml(String path) throws Exception {
        MimeMessage msg = new MimeMessage(getSession());
        msg.setFrom(new InternetAddress("html-sender@example.com"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("html-recipient@example.com"));
        msg.setSubject("HTML Email Test");
        msg.setSentDate(new Date(1735689600000L));

        // Create multipart/alternative with text and HTML
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("This is the plain text version.", "utf-8");
        alternative.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<html><body><h1>Hello</h1><p>This is the HTML version.</p></body></html>",
            "text/html; charset=utf-8");
        alternative.addBodyPart(htmlPart);

        msg.setContent(alternative);
        msg.setHeader("Message-ID", "<test-002@example.com>");
        msg.saveChanges();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream(new File(path))) {
            msg.writeTo(baos);
            fos.write(baos.toByteArray());
        }
    }

    /**
     * Creates a multipart/mixed EML file with text, HTML, and an attachment.
     *
     * @param path The path to create the file at
     */
    public static void createMultipartEml(String path) throws Exception {
        MimeMessage msg = new MimeMessage(getSession());
        msg.setFrom(new InternetAddress("multipart-sender@example.com", "Multipart Sender"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("multipart-recipient@example.com"));
        msg.addRecipient(Message.RecipientType.CC, new InternetAddress("cc-user@example.com"));
        msg.setSubject("Multipart Email Test");
        msg.setSentDate(new Date(1735689600000L));
        msg.setHeader("Message-ID", "<test-003@example.com>");
        msg.setHeader("In-Reply-To", "<original-001@example.com>");

        // Outer multipart/mixed
        MimeMultipart mixed = new MimeMultipart("mixed");

        // Inner multipart/alternative for body
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("This is the plain text body of the multipart email.", "utf-8");
        alternative.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(
            "<html><body><p>This is the <b>HTML</b> body of the multipart email.</p></body></html>",
            "text/html; charset=utf-8");
        alternative.addBodyPart(htmlPart);

        MimeBodyPart alternativeWrapper = new MimeBodyPart();
        alternativeWrapper.setContent(alternative);
        mixed.addBodyPart(alternativeWrapper);

        // Attachment
        MimeBodyPart attachmentPart = new MimeBodyPart();
        byte[] attachmentData = "This is a test attachment content.".getBytes(StandardCharsets.UTF_8);
        attachmentPart.setContent(attachmentData, "application/octet-stream");
        attachmentPart.setFileName("test-attachment.txt");
        attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
        mixed.addBodyPart(attachmentPart);

        msg.setContent(mixed);
        msg.saveChanges();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream(new File(path))) {
            msg.writeTo(baos);
            fos.write(baos.toByteArray());
        }
    }

    /**
     * Returns the bytes of a minimal .msg (Outlook) file built using POI's OLE2 APIs.
     * Since POI HSMF does not have a high-level write API, this constructs a minimal
     * OLE2 Compound Document with MAPI property streams.
     *
     * @return The MSG file as bytes
     */
    public static byte[] createSimpleMsgBytes() throws Exception {
        try (org.apache.poi.poifs.filesystem.POIFSFileSystem fs =
                 new org.apache.poi.poifs.filesystem.POIFSFileSystem()) {
            populateMsgFileSystem(fs);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                fs.writeFilesystem(baos);
                return baos.toByteArray();
            }
        }
    }

    /**
     * Creates a minimal .msg file at the given path.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleMsg(String path) throws Exception {
        byte[] bytes = createSimpleMsgBytes();
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            fos.write(bytes);
        }
    }

    /**
     * Creates an MBOX file with 3 test messages.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleMbox(String path) throws Exception {
        StringBuilder mbox = new StringBuilder();

        // Message 1
        mbox.append("From sender1@example.com Wed Jan 01 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender1@example.com", "recipient1@example.com",
            "First Message", "Body of the first message."));
        mbox.append("\r\n");

        // Message 2
        mbox.append("From sender2@example.com Thu Jan 02 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender2@example.com", "recipient2@example.com",
            "Second Message", "Body of the second message."));
        mbox.append("\r\n");

        // Message 3
        mbox.append("From sender3@example.com Fri Jan 03 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender3@example.com", "recipient3@example.com",
            "Third Message", "Body of the third message."));
        mbox.append("\r\n");

        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            fos.write(mbox.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns the bytes of an MBOX file with 3 test messages.
     *
     * @return The MBOX file as bytes
     */
    public static byte[] createSimpleMboxBytes() throws Exception {
        StringBuilder mbox = new StringBuilder();

        mbox.append("From sender1@example.com Wed Jan 01 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender1@example.com", "recipient1@example.com",
            "First Message", "Body of the first message."));
        mbox.append("\r\n");

        mbox.append("From sender2@example.com Thu Jan 02 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender2@example.com", "recipient2@example.com",
            "Second Message", "Body of the second message."));
        mbox.append("\r\n");

        mbox.append("From sender3@example.com Fri Jan 03 00:00:00 2025\r\n");
        mbox.append(createEmlContent("sender3@example.com", "recipient3@example.com",
            "Third Message", "Body of the third message."));
        mbox.append("\r\n");

        return mbox.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a raw EML content string (headers + body) suitable for embedding in an MBOX file.
     *
     * @param from The From address
     * @param to The To address
     * @param subject The subject line
     * @param body The plain text body
     * @return The raw EML content string
     */
    private static String createEmlContent(String from, String to, String subject, String body) {
        StringBuilder eml = new StringBuilder();
        eml.append("From: ").append(from).append("\r\n");
        eml.append("To: ").append(to).append("\r\n");
        eml.append("Subject: ").append(subject).append("\r\n");
        eml.append("Date: Wed, 01 Jan 2025 00:00:00 +0000\r\n");
        eml.append("MIME-Version: 1.0\r\n");
        eml.append("Content-Type: text/plain; charset=utf-8\r\n");
        eml.append("\r\n");
        eml.append(body).append("\r\n");
        return eml.toString();
    }

    /**
     * Populates a POIFSFileSystem with minimal MAPI property streams to create a valid MSG file.
     * Uses POI's lower-level OLE2 APIs since HSMF does not provide a write API.
     *
     * MSG files store MAPI properties as streams in the OLE2 container.
     * Property IDs (from MS-OXPROPS):
     *   PidTagMessageClass = 0x001A (PT_UNICODE = 0x001F) = "IPM.Note"
     *   PidTagSubject = 0x0037 (PT_UNICODE = 0x001F)
     *   PidTagBody = 0x1000 (PT_UNICODE = 0x001F)
     *   PidTagSenderName = 0x0C1A (PT_UNICODE = 0x001F)
     *   PidTagDisplayTo = 0x0E04 (PT_UNICODE = 0x001F)
     *   PidTagSentRepresentingName = 0x0042 (PT_UNICODE = 0x001F)
     *
     * Stream naming convention: "__substg1.0_PPPPTTTT" where PPPP=property ID, TTTT=property type
     *
     * @param fs The POIFSFileSystem to populate
     */
    private static void populateMsgFileSystem(org.apache.poi.poifs.filesystem.POIFSFileSystem fs)
            throws IOException {
        org.apache.poi.poifs.filesystem.DirectoryEntry root = fs.getRoot();

        // Message class (required for MAPI)
        createUnicodeStream(root, "__substg1.0_001A001F", "IPM.Note");

        // Subject
        createUnicodeStream(root, "__substg1.0_0037001F", "Test MSG Subject");

        // Body
        createUnicodeStream(root, "__substg1.0_1000001F", "This is a test MSG body.");

        // Sender name (PidTagSenderName)
        createUnicodeStream(root, "__substg1.0_0C1A001F", "Test Sender");

        // Display To (PidTagDisplayTo)
        createUnicodeStream(root, "__substg1.0_0E04001F", "test@example.com");

        // Display From (PidTagSentRepresentingName)
        createUnicodeStream(root, "__substg1.0_0042001F", "Test Sender");

        // Create the properties stream (__properties_version1.0)
        // This is a binary stream containing a header and property entries.
        // Header: 32 bytes (reserved) for root; each property entry is 16 bytes.
        byte[] propsHeader = new byte[32];
        root.createDocument("__properties_version1.0",
            new java.io.ByteArrayInputStream(propsHeader));
    }

    /**
     * Creates a UTF-16LE encoded Unicode stream in an OLE2 directory.
     *
     * @param dir The directory entry to create the stream in
     * @param name The stream name
     * @param value The string value to encode
     */
    private static void createUnicodeStream(org.apache.poi.poifs.filesystem.DirectoryEntry dir,
                                             String name, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
        dir.createDocument(name, new java.io.ByteArrayInputStream(bytes));
    }
}
