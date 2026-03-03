/*  WordIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Word documents (.docx format).
 * Supports reading paragraphs or table data.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class WordIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private XWPFDocument document;
    private String mode;
    private int tableIndex;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headerRow;
    private Hash currentRecord = null;
    private long count = 0;

    // For paragraphs mode
    private Iterator<XWPFParagraph> paragraphIterator;

    // For table mode
    private Iterator<XWPFTableRow> rowIterator;
    private boolean firstRowSkipped;

    /**
     * Creates a WordIterator from a Java InputStream.
     *
     * @param stream The input stream containing Word document data
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public WordIterator(java.io.InputStream stream, String mode, int tableIndex) throws Throwable {
        try {
            this.document = new XWPFDocument(stream);
        } catch (Throwable t) {
            stream.close();
            throw t;
        }
        this.mode = mode != null ? mode : "paragraphs";
        this.tableIndex = tableIndex;
        this.headerRow = false;
        this.firstRowSkipped = false;
        initializeIterator();
    }

    /**
     * Creates a WordIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing Word document data
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public WordIterator(qore.Qore.InputStream stream, String mode, int tableIndex) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), mode, tableIndex);
    }

    /**
     * Creates a WordIterator from a file path.
     *
     * @param path The path to the Word document
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public WordIterator(String path, String mode, int tableIndex) throws Throwable {
        this(new FileInputStream(new File(path)), mode, tableIndex);
    }

    private void initializeIterator() {
        if (mode.equals("paragraphs")) {
            paragraphIterator = document.getParagraphs().iterator();
        } else if (mode.equals("table")) {
            List<XWPFTable> tables = document.getTables();
            if (tableIndex >= 0 && tableIndex < tables.size()) {
                XWPFTable table = tables.get(tableIndex);
                rowIterator = table.getRows().iterator();
            }
        }
    }

    /**
     * Sets whether the first row of a table contains headers.
     *
     * @param hasHeader True if first row contains headers
     */
    public void setHeaderRow(boolean hasHeader) {
        this.headerRow = hasHeader;
    }

    /**
     * Sets explicit headers for table mode.
     *
     * @param headers ArrayList of header names
     */
    public void setHeaders(ArrayList<String> headers) {
        this.headers = new ArrayList<String>(headers);
    }

    /**
     * Sets explicit headers for table mode.
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
     * Gets the current headers.
     *
     * @return The list of headers
     */
    public ArrayList<String> getHeaders() {
        return headers;
    }

    /**
     * Returns the number of records read.
     */
    public long getCount() {
        return count;
    }

    /**
     * Advances to the next record.
     *
     * @return True if there is another record, false otherwise
     */
    public boolean next() {
        if (mode.equals("paragraphs")) {
            return nextParagraph();
        } else if (mode.equals("table")) {
            return nextTableRow();
        }
        return false;
    }

    private boolean nextParagraph() {
        if (paragraphIterator == null) {
            return false;
        }

        while (paragraphIterator.hasNext()) {
            XWPFParagraph para = paragraphIterator.next();
            String text = para.getText();

            // Skip empty paragraphs
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            currentRecord = new Hash();
            currentRecord.put("text", text);
            currentRecord.put("style", para.getStyle() != null ? para.getStyle() : "Normal");
            ++count;
            return true;
        }

        currentRecord = null;
        return false;
    }

    private boolean nextTableRow() {
        if (rowIterator == null) {
            return false;
        }

        // Handle header row if needed
        if (headerRow && !firstRowSkipped && rowIterator.hasNext()) {
            XWPFTableRow headerRowData = rowIterator.next();
            if (headers.isEmpty()) {
                // Extract headers from first row
                for (XWPFTableCell cell : headerRowData.getTableCells()) {
                    String text = cell.getText();
                    headers.add(text != null ? text.trim() : "");
                }
            }
            firstRowSkipped = true;
        }

        if (rowIterator.hasNext()) {
            XWPFTableRow row = rowIterator.next();
            List<XWPFTableCell> cells = row.getTableCells();

            currentRecord = new Hash();

            for (int i = 0; i < cells.size(); i++) {
                String key;
                if (i < headers.size()) {
                    key = headers.get(i);
                } else {
                    key = "Column" + (i + 1);
                }
                String value = cells.get(i).getText();
                currentRecord.put(key, value != null ? value : "");
            }

            ++count;
            return true;
        }

        currentRecord = null;
        return false;
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
     * Returns the number of tables in the document.
     *
     * @param stream The input stream containing Word document data
     * @return The number of tables
     */
    public static int getTableCount(java.io.InputStream stream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(stream)) {
            return doc.getTables().size();
        }
    }

    /**
     * Returns the number of tables in the document.
     *
     * @param stream The Qore input stream containing Word document data
     * @return The number of tables
     */
    public static int getTableCount(qore.Qore.InputStream stream) throws IOException {
        return getTableCount(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of tables in the document.
     *
     * @param path The path to the Word document
     * @return The number of tables
     */
    public static int getTableCount(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getTableCount(fis);
        }
    }

    /**
     * Extracts all text from a Word document.
     *
     * @param stream The input stream containing Word document data
     * @return The full text content
     */
    public static String getFullText(java.io.InputStream stream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(stream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /**
     * Extracts all text from a Word document.
     *
     * @param stream The Qore input stream containing Word document data
     * @return The full text content
     */
    public static String getFullText(qore.Qore.InputStream stream) throws IOException {
        return getFullText(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Extracts all text from a Word document.
     *
     * @param path The path to the Word document
     * @return The full text content
     */
    public static String getFullText(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getFullText(fis);
        }
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

