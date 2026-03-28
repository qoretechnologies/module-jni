/*  OdtIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.incubator.doc.text.OdfEditableTextExtractor;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.element.text.TextHElement;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.List;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading ODT (OpenDocument Text) documents.
 * Supports reading paragraphs/headings or table data.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class OdtIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private OdfTextDocument document;
    private String mode;
    private int tableIndex;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headerRow;
    private Hash currentRecord = null;
    private long count = 0;

    // For paragraphs mode
    private NodeList contentNodes;
    private int nodeIndex;

    // For table mode
    private OdfTable currentTable;
    private int currentRowIndex;
    private int tableRowCount;
    // Cached column count - must be saved before any cell access
    // because ODFDOM auto-expands tables when accessing non-existent cells
    private int tableColCount;
    private boolean firstRowSkipped;

    /**
     * Creates an OdtIterator from a Java InputStream.
     *
     * @param stream The input stream containing ODT document data
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdtIterator(java.io.InputStream stream, String mode, int tableIndex) throws Throwable {
        try {
            this.document = OdfTextDocument.loadDocument(stream);
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
     * Creates an OdtIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing ODT document data
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdtIterator(qore.Qore.InputStream stream, String mode, int tableIndex) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), mode, tableIndex);
    }

    /**
     * Creates an OdtIterator from a file path.
     *
     * @param path The path to the ODT document
     * @param mode The read mode: "paragraphs" or "table"
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdtIterator(String path, String mode, int tableIndex) throws Throwable {
        this(new FileInputStream(new File(path)), mode, tableIndex);
    }

    private void initializeIterator() throws Exception {
        if (mode.equals("paragraphs")) {
            contentNodes = document.getContentRoot().getChildNodes();
            nodeIndex = 0;
        } else if (mode.equals("table")) {
            List<OdfTable> tables = document.getTableList();
            if (tableIndex >= 0 && tableIndex < tables.size()) {
                currentTable = tables.get(tableIndex);
                // Cache dimensions immediately - before any cell access which auto-expands tables
                tableRowCount = currentTable.getRowCount();
                tableColCount = currentTable.getColumnCount();
                currentRowIndex = 0;
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
        if (contentNodes == null) {
            return false;
        }

        while (nodeIndex < contentNodes.getLength()) {
            Node node = contentNodes.item(nodeIndex);
            ++nodeIndex;

            String text = null;
            String style = null;

            if (node instanceof TextHElement) {
                TextHElement heading = (TextHElement) node;
                text = heading.getTextContent();
                Integer level = heading.getTextOutlineLevelAttribute();
                if (level != null) {
                    style = "Heading" + level;
                } else {
                    style = "Heading1";
                }
            } else if (node instanceof TextPElement) {
                text = node.getTextContent();
                style = "Normal";
            } else {
                continue;
            }

            // Skip empty paragraphs
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            currentRecord = new Hash();
            currentRecord.put("text", text);
            currentRecord.put("style", style);
            ++count;
            return true;
        }

        currentRecord = null;
        return false;
    }

    private boolean nextTableRow() {
        if (currentTable == null) {
            return false;
        }

        // Handle header row if needed
        if (headerRow && !firstRowSkipped && currentRowIndex < tableRowCount) {
            OdfTableRow headerRowData = currentTable.getRowByIndex(currentRowIndex);
            ++currentRowIndex;
            if (headers.isEmpty() && headerRowData != null) {
                // Use cached column count as upper bound; getCellCount() can trigger
                // auto-expansion in ODFDOM
                int cellCount = Math.min(headerRowData.getCellCount(), tableColCount);
                for (int i = 0; i < cellCount; i++) {
                    OdfTableCell cell = headerRowData.getCellByIndex(i);
                    String text = cell != null ? cell.getDisplayText() : "";
                    headers.add(text != null ? text.trim() : "");
                }
            }
            firstRowSkipped = true;
        }

        if (currentRowIndex < tableRowCount) {
            OdfTableRow row = currentTable.getRowByIndex(currentRowIndex);
            ++currentRowIndex;

            if (row == null) {
                currentRecord = null;
                return false;
            }

            // Use cached column count as upper bound; getCellCount() can trigger
            // auto-expansion in ODFDOM
            int cellCount = Math.min(row.getCellCount(), tableColCount);
            currentRecord = new Hash();

            for (int i = 0; i < cellCount; i++) {
                String key;
                if (i < headers.size()) {
                    key = headers.get(i);
                } else {
                    key = "Column" + (i + 1);
                }
                OdfTableCell cell = row.getCellByIndex(i);
                String value = cell != null ? cell.getDisplayText() : "";
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
     * @param stream The input stream containing ODT document data
     * @return The number of tables
     */
    public static int getTableCount(java.io.InputStream stream) throws Exception {
        try (OdtDocHolder holder = new OdtDocHolder(stream)) {
            return holder.doc.getTableList().size();
        }
    }

    /**
     * Returns the number of tables in the document.
     *
     * @param stream The Qore input stream containing ODT document data
     * @return The number of tables
     */
    public static int getTableCount(qore.Qore.InputStream stream) throws Exception {
        return getTableCount(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of tables in the document.
     *
     * @param path The path to the ODT document
     * @return The number of tables
     */
    public static int getTableCount(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getTableCount(fis);
        }
    }

    /**
     * Extracts all text from an ODT document.
     *
     * @param stream The input stream containing ODT document data
     * @return The full text content
     */
    public static String getFullText(java.io.InputStream stream) throws Exception {
        try (OdtDocHolder holder = new OdtDocHolder(stream)) {
            OdfEditableTextExtractor extractor = OdfEditableTextExtractor.newOdfEditableTextExtractor(holder.doc);
            return extractor.getText();
        }
    }

    /**
     * Extracts all text from an ODT document.
     *
     * @param stream The Qore input stream containing ODT document data
     * @return The full text content
     */
    public static String getFullText(qore.Qore.InputStream stream) throws Exception {
        return getFullText(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Extracts all text from an ODT document.
     *
     * @param path The path to the ODT document
     * @return The full text content
     */
    public static String getFullText(String path) throws Exception {
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

    /**
     * Helper class to hold a document for auto-closing in static methods.
     */
    private static class OdtDocHolder implements AutoCloseable {
        OdfTextDocument doc;

        OdtDocHolder(java.io.InputStream stream) throws Exception {
            doc = OdfTextDocument.loadDocument(stream);
        }

        @Override
        public void close() {
            if (doc != null) {
                doc.close();
                doc = null;
            }
        }
    }
}
