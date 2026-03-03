/*  PowerPointIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.powerpoint;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.extractor.XSLFExtractor;

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
 * Iterator for reading PowerPoint presentations (.pptx format).
 * Supports reading slides or table data.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class PowerPointIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private XMLSlideShow presentation;
    private String mode;
    private int slideIndex;
    private int tableIndex;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headerRow;
    private Hash currentRecord = null;
    private long count = 0;

    // For slides mode
    private Iterator<XSLFSlide> slideIterator;
    private int currentSlideNumber = 0;

    // For table mode
    private Iterator<XSLFTableRow> rowIterator;
    private boolean firstRowSkipped;

    /**
     * Creates a PowerPointIterator from a Java InputStream.
     *
     * @param stream The input stream containing PowerPoint data
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public PowerPointIterator(java.io.InputStream stream, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        try {
            this.presentation = new XMLSlideShow(stream);
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
     * Creates a PowerPointIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing PowerPoint data
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public PowerPointIterator(qore.Qore.InputStream stream, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), mode, slideIndex, tableIndex);
    }

    /**
     * Creates a PowerPointIterator from a file path.
     *
     * @param path The path to the PowerPoint file
     * @param mode The read mode: "slides" or "table"
     * @param slideIndex The index of the slide for table mode (0-based)
     * @param tableIndex The index of the table to read (0-based, for table mode)
     */
    public PowerPointIterator(String path, String mode, int slideIndex,
            int tableIndex) throws Throwable {
        this(new FileInputStream(new File(path)), mode, slideIndex, tableIndex);
    }

    private void initializeIterator() {
        if (mode.equals("slides")) {
            slideIterator = presentation.getSlides().iterator();
        } else if (mode.equals("table")) {
            List<XSLFSlide> slides = presentation.getSlides();
            if (slideIndex >= 0 && slideIndex < slides.size()) {
                XSLFSlide slide = slides.get(slideIndex);
                XSLFTable table = findTable(slide, tableIndex);
                if (table != null) {
                    rowIterator = table.getRows().iterator();
                }
            }
        }
    }

    /**
     * Finds a table on a slide by index.
     */
    private static XSLFTable findTable(XSLFSlide slide, int index) {
        int tableCount = 0;
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTable) {
                if (tableCount == index) {
                    return (XSLFTable) shape;
                }
                tableCount++;
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
        if (slideIterator == null) {
            return false;
        }

        if (slideIterator.hasNext()) {
            XSLFSlide slide = slideIterator.next();
            ++currentSlideNumber;

            currentRecord = new Hash();
            currentRecord.put("slide_number", currentSlideNumber);
            currentRecord.put("title", getSlideTitle(slide));
            currentRecord.put("body", getSlideBody(slide));
            currentRecord.put("notes", getSlideNotes(slide));
            currentRecord.put("layout", getSlideLayout(slide));
            ++count;
            return true;
        }

        currentRecord = null;
        return false;
    }

    /**
     * Extracts the title from a slide.
     */
    private String getSlideTitle(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                Placeholder ph = textShape.getTextType();
                if (ph == Placeholder.TITLE || ph == Placeholder.CENTERED_TITLE) {
                    return textShape.getText();
                }
            }
        }
        return "";
    }

    /**
     * Extracts the body text from a slide (all non-title text shapes).
     */
    private String getSlideBody(XSLFSlide slide) {
        StringBuilder body = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape && !(shape instanceof XSLFTable)) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                Placeholder ph = textShape.getTextType();
                if (ph != Placeholder.TITLE && ph != Placeholder.CENTERED_TITLE) {
                    String text = textShape.getText();
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
     * Extracts speaker notes from a slide.
     */
    private String getSlideNotes(XSLFSlide slide) {
        XSLFNotes notes = slide.getNotes();
        if (notes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                Placeholder ph = textShape.getTextType();
                // Only get the body placeholder from notes (skip slide number, header/footer)
                if (ph == Placeholder.BODY) {
                    String text = textShape.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Gets the layout name of a slide.
     */
    private String getSlideLayout(XSLFSlide slide) {
        if (slide.getSlideLayout() != null) {
            return slide.getSlideLayout().getName();
        }
        return "";
    }

    private boolean nextTableRow() {
        if (rowIterator == null) {
            return false;
        }

        // Handle header row if needed
        if (headerRow && !firstRowSkipped && rowIterator.hasNext()) {
            XSLFTableRow headerRowData = rowIterator.next();
            if (headers.isEmpty()) {
                // Extract headers from first row
                for (int i = 0; i < headerRowData.getCells().size(); i++) {
                    XSLFTableCell cell = headerRowData.getCells().get(i);
                    String text = cell.getText();
                    headers.add(text != null ? text.trim() : "");
                }
            }
            firstRowSkipped = true;
        }

        if (rowIterator.hasNext()) {
            XSLFTableRow row = rowIterator.next();
            List<XSLFTableCell> cells = row.getCells();

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
     * Returns the number of slides in the presentation.
     *
     * @param stream The input stream containing PowerPoint data
     * @return The number of slides
     */
    public static int getSlideCount(java.io.InputStream stream) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow(stream)) {
            return pptx.getSlides().size();
        }
    }

    /**
     * Returns the number of slides in the presentation.
     *
     * @param stream The Qore input stream containing PowerPoint data
     * @return The number of slides
     */
    public static int getSlideCount(qore.Qore.InputStream stream) throws IOException {
        return getSlideCount(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of slides in the presentation.
     *
     * @param path The path to the PowerPoint file
     * @return The number of slides
     */
    public static int getSlideCount(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getSlideCount(fis);
        }
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param stream The input stream containing PowerPoint data
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(java.io.InputStream stream, int slideIndex) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow(stream)) {
            List<XSLFSlide> slides = pptx.getSlides();
            if (slideIndex < 0 || slideIndex >= slides.size()) {
                return 0;
            }
            int count = 0;
            for (XSLFShape shape : slides.get(slideIndex).getShapes()) {
                if (shape instanceof XSLFTable) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param stream The Qore input stream containing PowerPoint data
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(qore.Qore.InputStream stream, int slideIndex) throws IOException {
        return getTableCount(new org.qore.jni.QoreInputStreamWrapper(stream), slideIndex);
    }

    /**
     * Returns the number of tables on a specific slide.
     *
     * @param path The path to the PowerPoint file
     * @param slideIndex The 0-based index of the slide
     * @return The number of tables on the slide
     */
    public static int getTableCount(String path, int slideIndex) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getTableCount(fis, slideIndex);
        }
    }

    /**
     * Extracts all text from a PowerPoint presentation.
     *
     * @param stream The input stream containing PowerPoint data
     * @return The full text content
     */
    public static String getFullText(java.io.InputStream stream) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow(stream);
             XSLFExtractor extractor = new XSLFExtractor(pptx)) {
            return extractor.getText();
        }
    }

    /**
     * Extracts all text from a PowerPoint presentation.
     *
     * @param stream The Qore input stream containing PowerPoint data
     * @return The full text content
     */
    public static String getFullText(qore.Qore.InputStream stream) throws IOException {
        return getFullText(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Extracts all text from a PowerPoint presentation.
     *
     * @param path The path to the PowerPoint file
     * @return The full text content
     */
    public static String getFullText(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getFullText(fis);
        }
    }

    /**
     * Closes the presentation and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (presentation != null) {
            presentation.close();
            presentation = null;
        }
    }
}

