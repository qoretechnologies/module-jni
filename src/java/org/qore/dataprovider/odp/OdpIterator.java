/*  OdpIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.odp;

import org.odftoolkit.odfdom.doc.OdfPresentationDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.dom.element.draw.DrawFrameElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawPageElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawTextBoxElement;
import org.odftoolkit.odfdom.dom.element.presentation.PresentationNotesElement;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.incubator.doc.text.OdfEditableTextExtractor;

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
 * Iterator for reading ODP (LibreOffice Impress) presentations.
 * Supports reading slides or table data.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class OdpIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private OdfPresentationDocument document;
    private String mode;
    private int slideIndex;
    private int tableIndex;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headerRow;
    private Hash currentRecord = null;
    private long count = 0;

    // For slides mode
    private List<DrawPageElement> slidePages;
    private int slideIteratorIndex = 0;
    private int currentSlideNumber = 0;

    // For table mode
    private OdfTable currentTable;
    private int currentRowIndex;
    // Cached table dimensions - must be saved before any cell access
    // because ODFDOM auto-expands tables when accessing non-existent cells
    private int tableRowCount;
    private int tableColCount;
    private boolean firstRowSkipped;

    /**
     * Creates an OdpIterator from a Java InputStream.
     *
     * @param stream The input stream containing ODP data
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdpIterator(java.io.InputStream stream, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        try {
            this.document = OdfPresentationDocument.loadDocument(stream);
        } catch (Throwable t) {
            stream.close();
            throw t;
        }
        this.mode = mode != null ? mode : "slides";
        this.slideIndex = slideIndex;
        this.tableIndex = tableIndex;
        this.headerRow = false;
        this.firstRowSkipped = false;
        initializeIterator();
    }

    /**
     * Creates an OdpIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing ODP data
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdpIterator(qore.Qore.InputStream stream, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), mode, slideIndex, tableIndex);
    }

    /**
     * Creates an OdpIterator from a file path.
     *
     * @param path The path to the ODP file
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public OdpIterator(String path, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        this(new FileInputStream(new File(path)), mode, slideIndex, tableIndex);
    }

    private void initializeIterator() throws Exception {
        if (mode.equals("slides")) {
            slidePages = getSlidePages();
        } else if (mode.equals("table")) {
            List<DrawPageElement> pages = getSlidePages();
            if (slideIndex >= 0 && slideIndex < pages.size()) {
                DrawPageElement page = pages.get(slideIndex);
                OdfTable table = findTable(page, tableIndex);
                if (table != null) {
                    currentTable = table;
                    // Cache dimensions immediately - before any cell access which auto-expands tables
                    tableRowCount = table.getRowCount();
                    tableColCount = table.getColumnCount();
                    currentRowIndex = 0;
                }
            }
        }
    }

    /**
     * Extracts all DrawPageElement children from the presentation content root.
     */
    private List<DrawPageElement> getSlidePages() throws Exception {
        List<DrawPageElement> pages = new ArrayList<DrawPageElement>();
        Node contentRoot = document.getContentRoot();
        NodeList children = contentRoot.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawPageElement) {
                pages.add((DrawPageElement) child);
            }
        }
        return pages;
    }

    /**
     * Finds a table on a slide (DrawPageElement) by index.
     */
    private static OdfTable findTable(DrawPageElement page, int index) {
        int tableCount = 0;
        NodeList children = page.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawFrameElement) {
                DrawFrameElement frame = (DrawFrameElement) child;
                NodeList frameChildren = frame.getChildNodes();
                for (int j = 0; j < frameChildren.getLength(); j++) {
                    Node frameChild = frameChildren.item(j);
                    // Look for table elements inside draw:frame
                    if (frameChild.getLocalName() != null && frameChild.getLocalName().equals("table")) {
                        if (tableCount == index) {
                            // Get the OdfTable wrapper for this element
                            if (frameChild instanceof org.odftoolkit.odfdom.dom.element.table.TableTableElement) {
                                return OdfTable.getInstance(
                                    (org.odftoolkit.odfdom.dom.element.table.TableTableElement) frameChild);
                            }
                        }
                        tableCount++;
                    }
                }
            }
        }
        return null;
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
        if (mode.equals("slides")) {
            return nextSlide();
        } else if (mode.equals("table")) {
            return nextTableRow();
        }
        return false;
    }

    private boolean nextSlide() {
        if (slidePages == null) {
            return false;
        }

        if (slideIteratorIndex < slidePages.size()) {
            DrawPageElement page = slidePages.get(slideIteratorIndex);
            ++slideIteratorIndex;
            ++currentSlideNumber;

            currentRecord = new Hash();
            currentRecord.put("slide_number", currentSlideNumber);
            currentRecord.put("title", getSlideTitle(page));
            currentRecord.put("body", getSlideBody(page));
            currentRecord.put("notes", getSlideNotes(page));
            currentRecord.put("layout", getSlideLayout(page));
            ++count;
            return true;
        }

        currentRecord = null;
        return false;
    }

    /**
     * Extracts the title from a slide by looking for frames with presentation:class="title".
     */
    private String getSlideTitle(DrawPageElement page) {
        NodeList children = page.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawFrameElement) {
                DrawFrameElement frame = (DrawFrameElement) child;
                String presClass = frame.getPresentationClassAttribute();
                if ("title".equals(presClass)) {
                    return extractFrameText(frame);
                }
            }
        }
        return "";
    }

    /**
     * Extracts the body text from a slide (non-title text frames).
     * Looks for frames with presentation:class="outline", "subtitle", "body",
     * or other text frames that are not title frames.
     */
    private String getSlideBody(DrawPageElement page) {
        StringBuilder body = new StringBuilder();
        NodeList children = page.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawFrameElement) {
                DrawFrameElement frame = (DrawFrameElement) child;
                String presClass = frame.getPresentationClassAttribute();
                if (presClass != null && (presClass.equals("outline") || presClass.equals("subtitle")
                        || presClass.equals("body") || presClass.equals("text"))) {
                    String text = extractFrameText(frame);
                    if (text != null && !text.trim().isEmpty()) {
                        if (body.length() > 0) {
                            body.append("\n");
                        }
                        body.append(text);
                    }
                } else if (presClass == null || presClass.isEmpty()) {
                    // Non-presentation frame; include text if it has a text box
                    String text = extractFrameText(frame);
                    if (text != null && !text.trim().isEmpty()) {
                        if (body.length() > 0) {
                            body.append("\n");
                        }
                        body.append(text);
                    }
                }
            }
        }
        return body.toString();
    }

    /**
     * Extracts speaker notes from a slide by looking for PresentationNotesElement children.
     */
    private String getSlideNotes(DrawPageElement page) {
        NodeList children = page.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof PresentationNotesElement) {
                return extractNodeText((PresentationNotesElement) child).trim();
            }
        }
        return "";
    }

    /**
     * Gets the layout/name of a slide via getDrawNameAttribute().
     */
    private String getSlideLayout(DrawPageElement page) {
        String name = page.getDrawNameAttribute();
        return name != null ? name : "";
    }

    /**
     * Extracts text content from a DrawFrameElement by iterating its text boxes.
     */
    private String extractFrameText(DrawFrameElement frame) {
        StringBuilder sb = new StringBuilder();
        NodeList children = frame.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawTextBoxElement) {
                String text = extractNodeText(child);
                if (text != null && !text.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Recursively extracts text content from a DOM node.
     * Concatenates text from all child text:p and text:span elements.
     */
    private String extractNodeText(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (text != null) {
                    sb.append(text);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String localName = child.getLocalName();
                if ("p".equals(localName) || "h".equals(localName)) {
                    // Paragraph or heading element
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(extractNodeText(child));
                } else if ("span".equals(localName) || "a".equals(localName)) {
                    sb.append(extractNodeText(child));
                } else if ("line-break".equals(localName)) {
                    sb.append("\n");
                } else if ("tab".equals(localName)) {
                    sb.append("\t");
                } else if ("s".equals(localName)) {
                    sb.append(" ");
                } else if ("frame".equals(localName) || "text-box".equals(localName)) {
                    // Nested frame/text-box - extract recursively
                    String text = extractNodeText(child);
                    if (text != null && !text.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                } else {
                    // For other elements, recurse to pick up any text children
                    sb.append(extractNodeText(child));
                }
            }
        }
        return sb.toString();
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
     * Returns the number of slides in the presentation.
     *
     * @param stream The input stream containing ODP data
     * @return The number of slides
     */
    public static int getSlideCount(java.io.InputStream stream) throws Exception {
        try (OdpDocHolder holder = new OdpDocHolder(stream)) {
            return holder.getSlidePageCount();
        }
    }

    /**
     * Returns the number of slides in the presentation.
     *
     * @param stream The Qore input stream containing ODP data
     * @return The number of slides
     */
    public static int getSlideCount(qore.Qore.InputStream stream) throws Exception {
        return getSlideCount(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of slides in the presentation.
     *
     * @param path The path to the ODP file
     * @return The number of slides
     */
    public static int getSlideCount(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getSlideCount(fis);
        }
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param stream The input stream containing ODP data
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(java.io.InputStream stream, int slideIndex) throws Exception {
        try (OdpDocHolder holder = new OdpDocHolder(stream)) {
            List<DrawPageElement> pages = holder.getSlidePages();
            if (slideIndex < 0 || slideIndex >= pages.size()) {
                return 0;
            }
            DrawPageElement page = pages.get(slideIndex);
            int tableCount = 0;
            NodeList children = page.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof DrawFrameElement) {
                    NodeList frameChildren = child.getChildNodes();
                    for (int j = 0; j < frameChildren.getLength(); j++) {
                        Node frameChild = frameChildren.item(j);
                        if (frameChild.getLocalName() != null && frameChild.getLocalName().equals("table")) {
                            tableCount++;
                        }
                    }
                }
            }
            return tableCount;
        }
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param stream The Qore input stream containing ODP data
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(qore.Qore.InputStream stream, int slideIndex) throws Exception {
        return getTableCount(new org.qore.jni.QoreInputStreamWrapper(stream), slideIndex);
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param path The path to the ODP file
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(String path, int slideIndex) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getTableCount(fis, slideIndex);
        }
    }

    /**
     * Extracts all text from an ODP presentation.
     *
     * @param stream The input stream containing ODP data
     * @return The full text content
     */
    public static String getFullText(java.io.InputStream stream) throws Exception {
        try (OdpDocHolder holder = new OdpDocHolder(stream)) {
            OdfEditableTextExtractor extractor = OdfEditableTextExtractor.newOdfEditableTextExtractor(holder.doc);
            return extractor.getText();
        }
    }

    /**
     * Extracts all text from an ODP presentation.
     *
     * @param stream The Qore input stream containing ODP data
     * @return The full text content
     */
    public static String getFullText(qore.Qore.InputStream stream) throws Exception {
        return getFullText(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Extracts all text from an ODP presentation.
     *
     * @param path The path to the ODP file
     * @return The full text content
     */
    public static String getFullText(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getFullText(fis);
        }
    }

    /**
     * Closes the presentation and releases resources.
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
    private static class OdpDocHolder implements AutoCloseable {
        OdfPresentationDocument doc;

        OdpDocHolder(java.io.InputStream stream) throws Exception {
            doc = OdfPresentationDocument.loadDocument(stream);
        }

        List<DrawPageElement> getSlidePages() throws Exception {
            List<DrawPageElement> pages = new ArrayList<DrawPageElement>();
            Node contentRoot = doc.getContentRoot();
            NodeList children = contentRoot.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof DrawPageElement) {
                    pages.add((DrawPageElement) child);
                }
            }
            return pages;
        }

        int getSlidePageCount() throws Exception {
            return getSlidePages().size();
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
