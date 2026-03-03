/*  WordWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

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
 * Helper class for writing Word documents (.docx format).
 */
public class WordWriter implements Closeable {
    private XWPFDocument document;
    private XWPFTable currentTable;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headersWritten = false;
    private String mode;
    private int recordCount = 0;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new WordWriter.
     *
     * @param mode The write mode: "paragraphs" or "table"
     */
    public WordWriter(String mode) {
        this.document = new XWPFDocument();
        this.mode = mode != null ? mode.toLowerCase() : "paragraphs";
    }

    /**
     * Creates a new WordWriter with default mode (paragraphs).
     */
    public WordWriter() {
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
     * Writes a title paragraph with Heading1 style.
     *
     * @param title The title text
     */
    public void writeTitle(String title) {
        if (title != null && !title.isEmpty()) {
            writeParagraph(title, "Heading1");
        }
    }

    /**
     * Writes a paragraph with the specified style.
     *
     * @param text The paragraph text
     * @param style The paragraph style (e.g., "Normal", "Heading1", "Heading2")
     */
    public void writeParagraph(String text, String style) {
        XWPFParagraph para = document.createParagraph();
        if (style != null && !style.isEmpty()) {
            para.setStyle(style);
        }
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");

        // Apply basic formatting based on style
        if (style != null) {
            if (style.equals("Heading1")) {
                run.setBold(true);
                run.setFontSize(16);
            } else if (style.equals("Heading2")) {
                run.setBold(true);
                run.setFontSize(14);
            } else if (style.equals("Heading3")) {
                run.setBold(true);
                run.setFontSize(12);
            }
        }

        ++recordCount;
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

            // Create table with header row
            currentTable = document.createTable(1, headers.size());

            // Write header row
            XWPFTableRow headerRow = currentTable.getRow(0);
            for (int i = 0; i < headers.size(); i++) {
                XWPFTableCell cell = headerRow.getCell(i);
                cell.setText(headers.get(i));
            }
            headersWritten = true;
        }

        // Add data row
        XWPFTableRow row = currentTable.createRow();
        for (int i = 0; i < headers.size(); i++) {
            XWPFTableCell cell = row.getCell(i);
            Object value = data.get(headers.get(i));
            cell.setText(valueToString(value));
        }

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
    public void writeToFile(String path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            document.write(out);
        }
    }

    /**
     * Writes the document to an output stream.
     *
     * @param stream The output stream to write to
     */
    public void writeToStream(OutputStream stream) throws IOException {
        document.write(stream);
    }

    /**
     * Writes the document to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the document contents as bytes.
     *
     * @return The document as a byte array
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
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
