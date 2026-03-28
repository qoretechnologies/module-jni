/*  OdpWriter.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.dom.element.draw.DrawFrameElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawPageElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawTextBoxElement;
import org.odftoolkit.odfdom.dom.element.presentation.PresentationNotesElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * Helper class for writing ODP (LibreOffice Impress) presentations.
 */
public class OdpWriter implements Closeable {
    private OdfPresentationDocument document;
    private OdfContentDom contentDom;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headersWritten = false;
    private String mode;
    private int recordCount = 0;

    // For table mode
    private DrawPageElement tableSlide;
    private OdfTable currentTable;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new OdpWriter.
     *
     * @param mode The write mode: "slides" or "table"
     */
    public OdpWriter(String mode) {
        this.mode = mode != null ? mode.toLowerCase() : "slides";
        try {
            this.document = OdfPresentationDocument.newPresentationDocument();
            this.contentDom = document.getContentDom();
            // Remove any default slides that may have been created
            removeDefaultSlides();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ODP document: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new OdpWriter with default mode (slides).
     */
    public OdpWriter() {
        this("slides");
    }

    /**
     * Removes any default slides created by newPresentationDocument().
     */
    private void removeDefaultSlides() throws Exception {
        Node contentRoot = document.getContentRoot();
        NodeList children = contentRoot.getChildNodes();
        ArrayList<Node> toRemove = new ArrayList<Node>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof DrawPageElement) {
                toRemove.add(child);
            }
        }
        for (Node node : toRemove) {
            contentRoot.removeChild(node);
        }
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
     * Writes a title slide as the first slide in the presentation.
     *
     * @param title The title text
     */
    public void writeTitle(String title) {
        if (title != null && !title.isEmpty()) {
            writeSlide(title, "", null, "");
        }
    }

    /**
     * Creates a new slide with the given content.
     *
     * @param title The slide title
     * @param body The slide body text
     * @param notes Optional speaker notes
     * @param layout The layout name for the slide
     */
    public void writeSlide(String title, String body, String notes, String layout) {
        try {
            DrawPageElement page = createNewSlide(layout);

            // Set title
            if (title != null && !title.isEmpty()) {
                addFrameWithText(page, "title", title);
            }

            // Set body text
            if (body != null && !body.isEmpty()) {
                addFrameWithText(page, "outline", body);
            }

            // Set notes
            if (notes != null && !notes.isEmpty()) {
                addNotes(page, notes);
            }

            ++recordCount;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create slide: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new DrawPageElement and appends it to the presentation.
     */
    private DrawPageElement createNewSlide(String layout) throws Exception {
        DrawPageElement page = contentDom.newOdfElement(DrawPageElement.class);
        String slideName = layout != null && !layout.isEmpty() ? layout : "page" + (recordCount + 1);
        page.setDrawNameAttribute(slideName);
        document.getContentRoot().appendChild(page);
        return page;
    }

    /**
     * Adds a draw:frame with a draw:text-box containing text to a slide.
     *
     * @param page The slide page element
     * @param presClass The presentation:class attribute (e.g., "title", "outline")
     * @param text The text content
     */
    private void addFrameWithText(DrawPageElement page, String presClass, String text) {
        DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
        frame.setPresentationClassAttribute(presClass);
        // Set reasonable default dimensions
        if ("title".equals(presClass)) {
            frame.setSvgXAttribute("1cm");
            frame.setSvgYAttribute("1cm");
            frame.setSvgWidthAttribute("23cm");
            frame.setSvgHeightAttribute("3cm");
        } else {
            frame.setSvgXAttribute("1cm");
            frame.setSvgYAttribute("5cm");
            frame.setSvgWidthAttribute("23cm");
            frame.setSvgHeightAttribute("12cm");
        }
        page.appendChild(frame);

        DrawTextBoxElement textBox = contentDom.newOdfElement(DrawTextBoxElement.class);
        frame.appendChild(textBox);

        // Split text by newlines and create separate paragraphs
        String[] lines = text.split("\n");
        for (String line : lines) {
            TextPElement para = contentDom.newOdfElement(TextPElement.class);
            para.setTextContent(line);
            textBox.appendChild(para);
        }
    }

    /**
     * Adds speaker notes to a slide.
     *
     * @param page The slide page element
     * @param notesText The notes text content
     */
    private void addNotes(DrawPageElement page, String notesText) {
        PresentationNotesElement notesElem = contentDom.newOdfElement(PresentationNotesElement.class);
        page.appendChild(notesElem);

        DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
        frame.setPresentationClassAttribute("notes");
        frame.setSvgXAttribute("1cm");
        frame.setSvgYAttribute("1cm");
        frame.setSvgWidthAttribute("15cm");
        frame.setSvgHeightAttribute("10cm");
        notesElem.appendChild(frame);

        DrawTextBoxElement textBox = contentDom.newOdfElement(DrawTextBoxElement.class);
        frame.appendChild(textBox);

        String[] lines = notesText.split("\n");
        for (String line : lines) {
            TextPElement para = contentDom.newOdfElement(TextPElement.class);
            para.setTextContent(line);
            textBox.appendChild(para);
        }
    }

    /**
     * Writes a data row. For table mode, adds a row to the table.
     * For slides mode, creates a new slide with the hash data.
     *
     * @param data The hash containing the row data
     */
    public void writeRow(Hash data) {
        if (mode.equals("table")) {
            writeTableRow(data);
        } else {
            // For slides mode, create a slide from the hash
            String title = "";
            String body = "";
            String notes = null;
            String layout = "";
            Object titleObj = data.get("title");
            if (titleObj != null) {
                title = titleObj.toString();
            }
            Object bodyObj = data.get("body");
            if (bodyObj != null) {
                body = bodyObj.toString();
            }
            Object notesObj = data.get("notes");
            if (notesObj != null) {
                notes = notesObj.toString();
            }
            Object layoutObj = data.get("layout");
            if (layoutObj != null) {
                layout = layoutObj.toString();
            }
            writeSlide(title, body, notes, layout);
        }
    }

    private void writeTableRow(Hash data) {
        try {
            // Initialize table if needed
            if (currentTable == null) {
                // Auto-detect headers from first record if not set
                if (headers.isEmpty()) {
                    for (Object key : data.keySet()) {
                        headers.add(key.toString());
                    }
                }

                // Create a slide for the table
                if (tableSlide == null) {
                    tableSlide = createNewSlide("table");
                }

                // Create table with header row using OdfTable
                currentTable = OdfTable.newTable(document, 1, headers.size());
                // Move the table element from its default location to our slide
                // OdfTable.newTable adds the table to the document body; we need it on a slide
                Node tableNode = currentTable.getOdfElement();
                if (tableNode.getParentNode() != null) {
                    tableNode.getParentNode().removeChild(tableNode);
                }

                // Wrap table in a draw:frame on the slide
                DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
                frame.setSvgXAttribute("1cm");
                frame.setSvgYAttribute("1cm");
                frame.setSvgWidthAttribute("23cm");
                frame.setSvgHeightAttribute("15cm");
                tableSlide.appendChild(frame);
                frame.appendChild(tableNode);

                // Write header row
                OdfTableRow headerRow = currentTable.getRowByIndex(0);
                for (int i = 0; i < headers.size(); i++) {
                    OdfTableCell cell = currentTable.getCellByPosition(i, 0);
                    cell.setStringValue(headers.get(i));
                }
                headersWritten = true;
            }

            // Add data row
            currentTable.appendRow();
            int rowIdx = currentTable.getRowCount() - 1;
            for (int i = 0; i < headers.size(); i++) {
                OdfTableCell cell = currentTable.getCellByPosition(i, rowIdx);
                Object value = data.get(headers.get(i));
                cell.setStringValue(valueToString(value));
            }

            ++recordCount;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write table row: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a title slide for table mode.
     *
     * @param title The title text
     */
    public void writeTableTitle(String title) {
        if (title != null && !title.isEmpty()) {
            try {
                tableSlide = createNewSlide("table-title");
                addFrameWithText(tableSlide, "title", title);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create table title slide: " + e.getMessage(), e);
            }
        }
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
     * Writes the presentation to a file.
     *
     * @param path The file path to write to
     */
    public void writeToFile(String path) throws Exception {
        document.save(path);
    }

    /**
     * Writes the presentation to an output stream.
     *
     * @param stream The output stream to write to
     */
    public void writeToStream(OutputStream stream) throws Exception {
        document.save(stream);
    }

    /**
     * Writes the presentation to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws Exception {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the presentation contents as bytes.
     *
     * @return The presentation as a byte array
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
     * Closes the presentation and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (document != null) {
            document.close();
            document = null;
        }
    }
}
