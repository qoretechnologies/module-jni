/*  OdtWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.odt;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.dom.element.office.OfficeTextElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.element.text.TextHElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Closeable;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;

import org.qore.jni.Hash;

/**
 * Helper class for writing ODT (OpenDocument Text) documents.
 */
public class OdtWriter implements Closeable {
    private OdfTextDocument document;
    private OdfTable currentTable;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headersWritten = false;
    private String mode;
    private int recordCount = 0;
    private int tableRowIndex = 0;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new OdtWriter.
     *
     * @param mode The write mode: "paragraphs" or "table"
     */
    public OdtWriter(String mode) {
        try {
            this.document = OdfTextDocument.newTextDocument();
            // Remove the default empty paragraph that ODFDOM creates
            OfficeTextElement contentRoot = document.getContentRoot();
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ODT document: " + e.getMessage(), e);
        }
        this.mode = mode != null ? mode.toLowerCase() : "paragraphs";
    }

    /**
     * Creates a new OdtWriter with default mode (paragraphs).
     */
    public OdtWriter() {
        this("paragraphs");
    }

    /**
     * Sets the headers for table mode.
     *
     * @param headers Array of header names
     */
    public void setHeaders(String[] headers) {
        this.headers.clear();
        for (String h : headers) {
            this.headers.add(h);
        }
    }

    /**
     * Sets the headers for table mode.
     *
     * @param headers ArrayList of header names
     */
    public void setHeaders(ArrayList<String> headers) {
        this.headers = new ArrayList<String>(headers);
    }

    /**
     * Gets the current headers.
     *
     * @return The list of headers
     */
    public ArrayList<String> getHeaders() {
        return headers;
    }

    /**
     * Writes a title heading (Heading level 1).
     *
     * @param title The title text
     */
    public void writeTitle(String title) {
        if (title != null && !title.isEmpty()) {
            writeHeading(title, 1);
        }
    }

    /**
     * Writes a heading with the specified outline level.
     *
     * @param text The heading text
     * @param level The outline level (1-6)
     */
    public void writeHeading(String text, int level) {
        try {
            OfficeTextElement contentRoot = document.getContentRoot();
            TextHElement heading = document.getContentDom().newOdfElement(TextHElement.class);
            heading.setTextOutlineLevelAttribute(level);
            heading.setTextContent(text != null ? text : "");
            contentRoot.appendChild(heading);
            ++recordCount;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write heading: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a paragraph with the specified style.
     *
     * @param text The paragraph text
     * @param style The paragraph style (e.g., "Normal", "Heading1", "Heading2")
     */
    public void writeParagraph(String text, String style) {
        if (style != null && style.matches("Heading(\\d+)")) {
            // Extract level from style name like "Heading1", "Heading2", etc.
            int level = Integer.parseInt(style.replaceFirst("Heading", ""));
            writeHeading(text, level);
            return;
        }

        try {
            OfficeTextElement contentRoot = document.getContentRoot();
            TextPElement para = document.getContentDom().newOdfElement(TextPElement.class);
            para.setTextContent(text != null ? text : "");
            contentRoot.appendChild(para);
            ++recordCount;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write paragraph: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a data row. For table mode, adds a row to the table.
     * For paragraphs mode, writes as a paragraph.
     *
     * @param data The hash containing the row data
     */
    public void writeRow(Hash data) {
        if (mode.equals("table")) {
            writeTableRow(data);
        } else {
            // For paragraphs mode, convert hash to text
            StringBuilder text = new StringBuilder();
            for (Object key : data.keySet()) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(key).append(": ").append(valueToString(data.get(key)));
            }
            writeParagraph(text.toString(), "Normal");
        }
    }

    private void writeTableRow(Hash data) {
        // Initialize table if needed
        if (currentTable == null) {
            // Auto-detect headers from first record if not set
            if (headers.isEmpty()) {
                for (Object key : data.keySet()) {
                    headers.add(key.toString());
                }
            }

            // Create table with 1 row (header) and the correct number of columns
            currentTable = OdfTable.newTable(document, 1, headers.size());

            // Write header row
            OdfTableRow headerRow = currentTable.getRowByIndex(0);
            for (int i = 0; i < headers.size(); i++) {
                OdfTableCell cell = headerRow.getCellByIndex(i);
                cell.setStringValue(headers.get(i));
            }
            headersWritten = true;
            tableRowIndex = 1;
        }

        // Add data row
        currentTable.appendRow();
        OdfTableRow row = currentTable.getRowByIndex(tableRowIndex);
        for (int i = 0; i < headers.size(); i++) {
            OdfTableCell cell = row.getCellByIndex(i);
            Object value = data.get(headers.get(i));
            cell.setStringValue(valueToString(value));
        }
        ++tableRowIndex;
        ++recordCount;
    }

    /**
     * Converts a value to string representation.
     */
    private String valueToString(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).format(DATE_FORMAT);
        } else if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
        } else if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        } else {
            return value.toString();
        }
    }

    /**
     * Writes the document to a file.
     *
     * @param path The file path to write to
     */
    public void writeToFile(String path) throws Exception {
        document.save(path);
    }

    /**
     * Writes the document to an output stream.
     *
     * @param stream The output stream to write to
     */
    public void writeToStream(OutputStream stream) throws Exception {
        document.save(stream);
    }

    /**
     * Writes the document to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws Exception {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the document contents as bytes.
     *
     * @return The document as a byte array
     */
    public byte[] getBytes() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Gets the number of records written.
     */
    public int getRecordCount() {
        return recordCount;
    }

    /**
     * Closes the document and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (document != null) {
            document.close();
            document = null;
        }
    }
}
