/*  PowerPointWriter.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.sl.usermodel.Placeholder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Closeable;

import java.awt.geom.Rectangle2D;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;

import org.qore.jni.Hash;

/**
 * Helper class for writing PowerPoint presentations (.pptx format).
 */
public class PowerPointWriter implements Closeable {
    private XMLSlideShow presentation;
    private XSLFSlideMaster defaultMaster;
    private ArrayList<String> headers = new ArrayList<String>();
    private boolean headersWritten = false;
    private String mode;
    private int recordCount = 0;

    // For table mode
    private XSLFSlide tableSlide;
    private XSLFTable currentTable;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new PowerPointWriter.
     *
     * @param mode The write mode: "slides" or "table"
     */
    public PowerPointWriter(String mode) {
        this.presentation = new XMLSlideShow();
        this.mode = mode != null ? mode.toLowerCase() : "slides";
        // Get the default slide master
        if (!presentation.getSlideMasters().isEmpty()) {
            this.defaultMaster = presentation.getSlideMasters().get(0);
        }
    }

    /**
     * Creates a new PowerPointWriter with default mode (slides).
     */
    public PowerPointWriter() {
        this("slides");
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
            writeSlide(title, "", null, "TITLE_ONLY");
        }
    }

    /**
     * Creates a new slide with the given content.
     *
     * @param title The slide title
     * @param body The slide body text
     * @param notes Optional speaker notes
     * @param layout The layout name (e.g., "TITLE_AND_CONTENT", "TITLE_ONLY", "BLANK")
     */
    public void writeSlide(String title, String body, String notes, String layout) {
        XSLFSlideLayout slideLayout = findLayout(layout);
        XSLFSlide slide;
        if (slideLayout != null) {
            slide = presentation.createSlide(slideLayout);
        } else {
            slide = presentation.createSlide();
        }

        // Set title
        if (title != null && !title.isEmpty()) {
            XSLFTextShape titleShape = findPlaceholder(slide, Placeholder.TITLE);
            if (titleShape == null) {
                titleShape = findPlaceholder(slide, Placeholder.CENTERED_TITLE);
            }
            if (titleShape != null) {
                titleShape.setText(title);
            }
        }

        // Set body text
        if (body != null && !body.isEmpty()) {
            XSLFTextShape bodyShape = findPlaceholder(slide, Placeholder.BODY);
            if (bodyShape == null) {
                bodyShape = findPlaceholder(slide, Placeholder.CONTENT);
            }
            if (bodyShape != null) {
                bodyShape.setText(body);
            }
        }

        // Set notes
        if (notes != null && !notes.isEmpty()) {
            XSLFNotes slideNotes = presentation.getNotesSlide(slide);
            for (XSLFTextShape shape : slideNotes.getPlaceholders()) {
                if (shape.getTextType() == Placeholder.BODY) {
                    shape.setText(notes);
                    break;
                }
            }
        }

        ++recordCount;
    }

    /**
     * Finds a placeholder shape on a slide.
     */
    private XSLFTextShape findPlaceholder(XSLFSlide slide, Placeholder type) {
        for (XSLFTextShape shape : slide.getPlaceholders()) {
            if (shape.getTextType() == type) {
                return shape;
            }
        }
        return null;
    }

    /**
     * Finds a slide layout by name.
     */
    private XSLFSlideLayout findLayout(String layoutName) {
        if (layoutName == null || layoutName.isEmpty() || defaultMaster == null) {
            return null;
        }

        // Try to match by SlideLayout enum name
        try {
            SlideLayout sl = SlideLayout.valueOf(layoutName);
            return defaultMaster.getLayout(sl);
        } catch (IllegalArgumentException e) {
            // Not a valid enum name; try by display name
        }

        // Try to find by layout name
        for (XSLFSlideLayout layout : defaultMaster.getSlideLayouts()) {
            if (layoutName.equalsIgnoreCase(layout.getName())) {
                return layout;
            }
        }

        return null;
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
            String layout = "TITLE_AND_CONTENT";
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
        // Initialize table if needed
        if (currentTable == null) {
            // Auto-detect headers from first record if not set
            if (headers.isEmpty()) {
                for (Object key : data.keySet()) {
                    headers.add(key.toString());
                }
            }

            // Create a blank slide for the table
            if (tableSlide == null) {
                XSLFSlideLayout blankLayout = findLayout("BLANK");
                if (blankLayout != null) {
                    tableSlide = presentation.createSlide(blankLayout);
                } else {
                    tableSlide = presentation.createSlide();
                }
            }

            // Create table with header row
            // Position the table on the slide
            currentTable = tableSlide.createTable(1, headers.size());
            currentTable.setAnchor(new Rectangle2D.Double(50, 50, 620, 100));

            // Write header row
            XSLFTableRow headerRow = currentTable.getRows().get(0);
            for (int i = 0; i < headers.size(); i++) {
                XSLFTableCell cell = headerRow.getCells().get(i);
                cell.setText(headers.get(i));
                // Bold header cells
                for (XSLFTextParagraph p : cell.getTextParagraphs()) {
                    for (XSLFTextRun r : p.getTextRuns()) {
                        r.setBold(true);
                    }
                }
            }
            headersWritten = true;
        }

        // Add data row
        XSLFTableRow row = currentTable.addRow();
        for (int i = 0; i < headers.size(); i++) {
            XSLFTableCell cell = row.addCell();
            Object value = data.get(headers.get(i));
            cell.setText(valueToString(value));
        }

        ++recordCount;
    }

    /**
     * Writes a title slide for table mode.
     *
     * @param title The title text
     */
    public void writeTableTitle(String title) {
        if (title != null && !title.isEmpty()) {
            // Create the table slide with a title layout instead of blank
            XSLFSlideLayout layout = findLayout("TITLE_ONLY");
            if (layout != null) {
                tableSlide = presentation.createSlide(layout);
            } else {
                tableSlide = presentation.createSlide();
            }
            // Set the title
            XSLFTextShape titleShape = findPlaceholder(tableSlide, Placeholder.TITLE);
            if (titleShape != null) {
                titleShape.setText(title);
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
    public void writeToFile(String path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            presentation.write(out);
        }
    }

    /**
     * Writes the presentation to an output stream.
     *
     * @param stream The output stream to write to
     */
    public void writeToStream(OutputStream stream) throws IOException {
        presentation.write(stream);
    }

    /**
     * Writes the presentation to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the presentation contents as bytes.
     *
     * @return The presentation as a byte array
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            presentation.write(out);
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
        if (presentation != null) {
            presentation.close();
            presentation = null;
        }
    }
}
