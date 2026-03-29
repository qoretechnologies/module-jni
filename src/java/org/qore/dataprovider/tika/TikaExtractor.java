/*  TikaExtractor.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.qore.jni.Hash;

/**
 * Wraps Apache Tika for content extraction, MIME type detection, and metadata extraction.
 * Supports construction from Java InputStreams, Qore InputStreams, byte arrays, and file paths.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class TikaExtractor implements Closeable {
    private byte[] data;

    /**
     * Creates a TikaExtractor from a Java InputStream.
     * Reads all bytes from the stream and stores them internally.
     *
     * @param stream The input stream containing data to extract
     * @throws IOException if an error occurs reading from the stream
     */
    public TikaExtractor(InputStream stream) throws IOException {
        try {
            this.data = readAllBytes(stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Creates a TikaExtractor from a Qore InputStream.
     *
     * @param stream The Qore input stream containing data to extract
     * @throws IOException if an error occurs reading from the stream
     */
    public TikaExtractor(qore.Qore.InputStream stream) throws IOException {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates a TikaExtractor from raw byte data.
     *
     * @param data The data to extract as bytes
     */
    public TikaExtractor(byte[] data) {
        this.data = data;
    }

    /**
     * Creates a TikaExtractor from a file path.
     *
     * @param path The path to the file to extract
     * @throws IOException if an error occurs reading the file
     */
    public TikaExtractor(String path) throws IOException {
        this(new FileInputStream(new File(path)));
    }

    /**
     * Extracts content, MIME type, and metadata from the stored data.
     *
     * @return A Hash containing:
     *         - content (String): extracted text
     *         - content_type (String): detected MIME type
     *         - metadata (Hash): all metadata key-value pairs
     *         - content_length (Long): original data size in bytes
     * @throws Exception if an error occurs during extraction
     */
    public Hash extractContent() throws Exception {
        Tika tika = new Tika();
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // -1 means no write limit
        BodyContentHandler handler = new BodyContentHandler(-1);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            parser.parse(inputStream, handler, metadata, new ParseContext());
        }

        String contentType;
        try (ByteArrayInputStream detectStream = new ByteArrayInputStream(data)) {
            contentType = tika.detect(detectStream);
        }

        Hash metadataHash = new Hash();
        for (String name : metadata.names()) {
            metadataHash.put(name, metadata.get(name));
        }

        Hash result = new Hash();
        result.put("content", handler.toString());
        result.put("content_type", contentType);
        result.put("metadata", metadataHash);
        result.put("content_length", (long) data.length);
        return result;
    }

    /**
     * Detects the MIME type of the stored data without full parsing.
     *
     * @return The detected MIME type string
     * @throws IOException if an error occurs during detection
     */
    public String detect() throws IOException {
        Tika tika = new Tika();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            return tika.detect(inputStream);
        }
    }

    /**
     * Extracts metadata only (no content body) from the stored data.
     *
     * @return A Hash containing all metadata key-value pairs
     * @throws Exception if an error occurs during metadata extraction
     */
    public Hash extractMetadata() throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // Use a handler that discards the content body
        org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler();

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            parser.parse(inputStream, handler, metadata, new ParseContext());
        }

        Hash metadataHash = new Hash();
        for (String name : metadata.names()) {
            metadataHash.put(name, metadata.get(name));
        }
        return metadataHash;
    }

    /**
     * Reads all bytes from an InputStream into a byte array.
     *
     * @param stream The input stream to read
     * @return All bytes from the stream
     * @throws IOException if an I/O error occurs
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
     * Closes the extractor and releases resources.
     */
    @Override
    public void close() throws IOException {
        data = null;
    }
}
