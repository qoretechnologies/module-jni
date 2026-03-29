/*  VisioIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.visio;

import org.apache.poi.xdgf.usermodel.XmlVisioDocument;
import org.apache.poi.xdgf.usermodel.XDGFPage;
import org.apache.poi.xdgf.usermodel.XDGFShape;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Visio documents (.vsdx format).
 * Iterates over pages, extracting all text from shapes on each page.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class VisioIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private XmlVisioDocument document;
    private List<XDGFPage> pages;
    private int currentPageIndex = -1;
    private Hash currentRecord = null;
    private long count = 0;

    /**
     * Creates a VisioIterator from a Java InputStream.
     *
     * @param stream The input stream containing Visio document data
     */
    public VisioIterator(java.io.InputStream stream) throws Throwable {
        try {
            this.document = new XmlVisioDocument(stream);
        } catch (Throwable t) {
            stream.close();
            throw t;
        }
        initializePages();
    }

    /**
     * Creates a VisioIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing Visio document data
     */
    public VisioIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates a VisioIterator from a file path.
     *
     * @param path The path to the Visio document
     */
    public VisioIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)));
    }

    private void initializePages() {
        pages = new ArrayList<XDGFPage>();
        Collection<XDGFPage> xdgfPages = document.getPages();
        if (xdgfPages != null) {
            pages.addAll(xdgfPages);
        }
    }

    /**
     * Extracts all text from shapes on a page.
     *
     * @param page The page to extract text from
     * @return The concatenated text from all shapes on the page
     */
    private String extractPageText(XDGFPage page) {
        StringBuilder sb = new StringBuilder();
        if (page.getContent() != null && page.getContent().getShapes() != null) {
            for (XDGFShape shape : page.getContent().getShapes()) {
                extractShapeText(shape, sb);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Recursively extracts text from a shape and its sub-shapes.
     *
     * @param shape The shape to extract text from
     * @param sb The StringBuilder to append text to
     */
    private void extractShapeText(XDGFShape shape, StringBuilder sb) {
        if (shape.hasText()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(shape.getTextAsString().trim());
        }
        // Recurse into sub-shapes
        if (shape.getShapes() != null) {
            for (XDGFShape subShape : shape.getShapes()) {
                extractShapeText(subShape, sb);
            }
        }
    }

    /**
     * Returns the number of records read.
     */
    public long getCount() {
        return count;
    }

    /**
     * Returns the number of pages in the document.
     *
     * @return The number of pages
     */
    public int getPageCount() {
        return pages.size();
    }

    /**
     * Advances to the next record (page).
     *
     * @return True if there is another record, false otherwise
     */
    public boolean next() {
        ++currentPageIndex;
        if (currentPageIndex < pages.size()) {
            XDGFPage page = pages.get(currentPageIndex);
            currentRecord = new Hash();
            currentRecord.put("page_number", currentPageIndex + 1);
            currentRecord.put("page_name", page.getName());
            currentRecord.put("text", extractPageText(page));
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
     * Returns the number of pages in a Visio document.
     *
     * @param stream The input stream containing Visio document data
     * @return The number of pages
     */
    public static int getPageCount(java.io.InputStream stream) throws IOException {
        try (XmlVisioDocument doc = new XmlVisioDocument(stream)) {
            Collection<XDGFPage> pages = doc.getPages();
            if (pages != null) {
                return pages.size();
            }
            return 0;
        }
    }

    /**
     * Returns the number of pages in a Visio document.
     *
     * @param stream The Qore input stream containing Visio document data
     * @return The number of pages
     */
    public static int getPageCount(qore.Qore.InputStream stream) throws IOException {
        return getPageCount(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of pages in a Visio document.
     *
     * @param path The path to the Visio document
     * @return The number of pages
     */
    public static int getPageCount(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getPageCount(fis);
        }
    }

    /**
     * Extracts all text from a Visio document.
     *
     * @param stream The input stream containing Visio document data
     * @return The full text content
     */
    public static String getFullText(java.io.InputStream stream) throws IOException {
        try (XmlVisioDocument doc = new XmlVisioDocument(stream)) {
            StringBuilder sb = new StringBuilder();
            Collection<XDGFPage> pages = doc.getPages();
            if (pages != null) {
                for (XDGFPage page : pages) {
                    if (page.getContent() != null && page.getContent().getShapes() != null) {
                        for (XDGFShape shape : page.getContent().getShapes()) {
                            appendShapeText(shape, sb);
                        }
                    }
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * Extracts all text from a Visio document.
     *
     * @param stream The Qore input stream containing Visio document data
     * @return The full text content
     */
    public static String getFullText(qore.Qore.InputStream stream) throws IOException {
        return getFullText(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Extracts all text from a Visio document.
     *
     * @param path The path to the Visio document
     * @return The full text content
     */
    public static String getFullText(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getFullText(fis);
        }
    }

    /**
     * Helper for getFullText - recursively appends shape text.
     */
    private static void appendShapeText(XDGFShape shape, StringBuilder sb) {
        if (shape.hasText()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(shape.getTextAsString().trim());
        }
        if (shape.getShapes() != null) {
            for (XDGFShape subShape : shape.getShapes()) {
                appendShapeText(subShape, sb);
            }
        }
    }

    /**
     * Returns page names from a Visio document.
     *
     * @param stream The input stream containing Visio document data
     * @return List of page names
     */
    public static ArrayList<String> getPageNames(java.io.InputStream stream) throws IOException {
        try (XmlVisioDocument doc = new XmlVisioDocument(stream)) {
            ArrayList<String> names = new ArrayList<String>();
            Collection<XDGFPage> pages = doc.getPages();
            if (pages != null) {
                for (XDGFPage page : pages) {
                    names.add(page.getName());
                }
            }
            return names;
        }
    }

    /**
     * Returns page names from a Visio document.
     *
     * @param stream The Qore input stream containing Visio document data
     * @return List of page names
     */
    public static ArrayList<String> getPageNames(qore.Qore.InputStream stream) throws IOException {
        return getPageNames(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns page names from a Visio document.
     *
     * @param path The path to the Visio document
     * @return List of page names
     */
    public static ArrayList<String> getPageNames(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getPageNames(fis);
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
