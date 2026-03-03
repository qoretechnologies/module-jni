/*  PowerPointTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import java.io.IOException;

import java.awt.geom.Rectangle2D;

/**
 * Helper class for creating test PowerPoint files for the PowerPointDataProvider tests.
 */
public class PowerPointTestHelper {

    /**
     * Creates a simple presentation with 3 slides.
     *
     * @param path The path to create the file at
     */
    public static void createSimplePresentation(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlideMaster master = pptx.getSlideMasters().get(0);

            // Slide 1 - Title slide
            XSLFSlideLayout titleLayout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide1 = pptx.createSlide(titleLayout);
            setPlaceholderText(slide1, Placeholder.TITLE, "Introduction");
            setPlaceholderText(slide1, Placeholder.BODY, "Welcome to the presentation");
            setPlaceholderText(slide1, Placeholder.CONTENT, "Welcome to the presentation");

            // Slide 2
            XSLFSlide slide2 = pptx.createSlide(titleLayout);
            setPlaceholderText(slide2, Placeholder.TITLE, "Main Content");
            setPlaceholderText(slide2, Placeholder.BODY, "This is the main content slide");
            setPlaceholderText(slide2, Placeholder.CONTENT, "This is the main content slide");

            // Slide 3
            XSLFSlide slide3 = pptx.createSlide(titleLayout);
            setPlaceholderText(slide3, Placeholder.TITLE, "Conclusion");
            setPlaceholderText(slide3, Placeholder.BODY, "Thank you for your attention");
            setPlaceholderText(slide3, Placeholder.CONTENT, "Thank you for your attention");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a simple presentation and returns the bytes.
     *
     * @return The presentation as a byte array
     */
    public static byte[] createSimplePresentationBytes() throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlideMaster master = pptx.getSlideMasters().get(0);
            XSLFSlideLayout titleLayout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);

            XSLFSlide slide1 = pptx.createSlide(titleLayout);
            setPlaceholderText(slide1, Placeholder.TITLE, "Sample Slide");
            setPlaceholderText(slide1, Placeholder.BODY, "Sample body text");
            setPlaceholderText(slide1, Placeholder.CONTENT, "Sample body text");

            XSLFSlide slide2 = pptx.createSlide(titleLayout);
            setPlaceholderText(slide2, Placeholder.TITLE, "Second Slide");
            setPlaceholderText(slide2, Placeholder.BODY, "More content here");
            setPlaceholderText(slide2, Placeholder.CONTENT, "More content here");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                pptx.write(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates a presentation with a table on a slide.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithTable(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlide slide = pptx.createSlide();

            // Create table with 4 rows and 3 columns
            XSLFTable table = slide.createTable(4, 3);
            table.setAnchor(new Rectangle2D.Double(50, 50, 620, 200));

            // Header row
            table.getRows().get(0).getCells().get(0).setText("Name");
            table.getRows().get(0).getCells().get(1).setText("Department");
            table.getRows().get(0).getCells().get(2).setText("Salary");

            // Data rows
            table.getRows().get(1).getCells().get(0).setText("Alice Smith");
            table.getRows().get(1).getCells().get(1).setText("Engineering");
            table.getRows().get(1).getCells().get(2).setText("75000");

            table.getRows().get(2).getCells().get(0).setText("Bob Johnson");
            table.getRows().get(2).getCells().get(1).setText("Marketing");
            table.getRows().get(2).getCells().get(2).setText("65000");

            table.getRows().get(3).getCells().get(0).setText("Carol Williams");
            table.getRows().get(3).getCells().get(1).setText("Finance");
            table.getRows().get(3).getCells().get(2).setText("70000");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with multiple slides containing tables.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithMultipleTables(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            // Slide 1 - Products table
            XSLFSlide slide1 = pptx.createSlide();
            XSLFTable table1 = slide1.createTable(3, 2);
            table1.setAnchor(new Rectangle2D.Double(50, 50, 620, 150));
            table1.getRows().get(0).getCells().get(0).setText("SKU");
            table1.getRows().get(0).getCells().get(1).setText("Product");
            table1.getRows().get(1).getCells().get(0).setText("A001");
            table1.getRows().get(1).getCells().get(1).setText("Widget");
            table1.getRows().get(2).getCells().get(0).setText("A002");
            table1.getRows().get(2).getCells().get(1).setText("Gadget");

            // Slide 2 - Customers table
            XSLFSlide slide2 = pptx.createSlide();
            XSLFTable table2 = slide2.createTable(3, 3);
            table2.setAnchor(new Rectangle2D.Double(50, 50, 620, 150));
            table2.getRows().get(0).getCells().get(0).setText("ID");
            table2.getRows().get(0).getCells().get(1).setText("Name");
            table2.getRows().get(0).getCells().get(2).setText("Country");
            table2.getRows().get(1).getCells().get(0).setText("1");
            table2.getRows().get(1).getCells().get(1).setText("Acme Corp");
            table2.getRows().get(1).getCells().get(2).setText("USA");
            table2.getRows().get(2).getCells().get(0).setText("2");
            table2.getRows().get(2).getCells().get(1).setText("Global Ltd");
            table2.getRows().get(2).getCells().get(2).setText("UK");

            // Slide 3 - Orders table
            XSLFSlide slide3 = pptx.createSlide();
            XSLFTable table3 = slide3.createTable(2, 4);
            table3.setAnchor(new Rectangle2D.Double(50, 50, 620, 100));
            table3.getRows().get(0).getCells().get(0).setText("OrderID");
            table3.getRows().get(0).getCells().get(1).setText("CustomerID");
            table3.getRows().get(0).getCells().get(2).setText("SKU");
            table3.getRows().get(0).getCells().get(3).setText("Quantity");
            table3.getRows().get(1).getCells().get(0).setText("1001");
            table3.getRows().get(1).getCells().get(1).setText("1");
            table3.getRows().get(1).getCells().get(2).setText("A001");
            table3.getRows().get(1).getCells().get(3).setText("5");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates an empty presentation.
     *
     * @param path The path to create the file at
     */
    public static void createEmptyPresentation(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with different slide layouts.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithStyles(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlideMaster master = pptx.getSlideMasters().get(0);

            // Title and content slide
            XSLFSlideLayout tcLayout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide1 = pptx.createSlide(tcLayout);
            setPlaceholderText(slide1, Placeholder.TITLE, "Title And Content Layout");
            setPlaceholderText(slide1, Placeholder.BODY, "Body text here");
            setPlaceholderText(slide1, Placeholder.CONTENT, "Body text here");

            // Title only slide
            XSLFSlideLayout toLayout = master.getLayout(SlideLayout.TITLE_ONLY);
            XSLFSlide slide2 = pptx.createSlide(toLayout);
            setPlaceholderText(slide2, Placeholder.TITLE, "Title Only Layout");

            // Blank slide
            XSLFSlideLayout blankLayout = master.getLayout(SlideLayout.BLANK);
            pptx.createSlide(blankLayout);

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with special characters and unicode.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithSpecialChars(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlideMaster master = pptx.getSlideMasters().get(0);
            XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);

            XSLFSlide slide1 = pptx.createSlide(layout);
            setPlaceholderText(slide1, Placeholder.TITLE, "Special Characters Test");
            setPlaceholderText(slide1, Placeholder.BODY,
                "Caf\u00e9 - French word with accent");
            setPlaceholderText(slide1, Placeholder.CONTENT,
                "Caf\u00e9 - French word with accent");

            XSLFSlide slide2 = pptx.createSlide(layout);
            setPlaceholderText(slide2, Placeholder.TITLE, "\u6771\u4eac - Tokyo in Japanese");
            setPlaceholderText(slide2, Placeholder.BODY,
                "Currency symbols: \u20ac \u00a3 \u00a5 $");
            setPlaceholderText(slide2, Placeholder.CONTENT,
                "Currency symbols: \u20ac \u00a3 \u00a5 $");

            XSLFSlide slide3 = pptx.createSlide(layout);
            setPlaceholderText(slide3, Placeholder.TITLE, "Math: \u03c0 \u00d7 r\u00b2");
            setPlaceholderText(slide3, Placeholder.BODY,
                "Quotes: \"Hello\" and 'World'");
            setPlaceholderText(slide3, Placeholder.CONTENT,
                "Quotes: \"Hello\" and 'World'");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with speaker notes.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithNotes(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlideMaster master = pptx.getSlideMasters().get(0);
            XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);

            // Slide 1 with notes
            XSLFSlide slide1 = pptx.createSlide(layout);
            setPlaceholderText(slide1, Placeholder.TITLE, "Slide With Notes");
            setPlaceholderText(slide1, Placeholder.BODY, "This slide has speaker notes");
            setPlaceholderText(slide1, Placeholder.CONTENT, "This slide has speaker notes");

            XSLFNotes notes1 = pptx.getNotesSlide(slide1);
            for (XSLFTextShape shape : notes1.getPlaceholders()) {
                if (shape.getTextType() == Placeholder.BODY) {
                    shape.setText("Remember to explain the key points here");
                    break;
                }
            }

            // Slide 2 with notes
            XSLFSlide slide2 = pptx.createSlide(layout);
            setPlaceholderText(slide2, Placeholder.TITLE, "Another Slide");
            setPlaceholderText(slide2, Placeholder.BODY, "More content");
            setPlaceholderText(slide2, Placeholder.CONTENT, "More content");

            XSLFNotes notes2 = pptx.getNotesSlide(slide2);
            for (XSLFTextShape shape : notes2.getPlaceholders()) {
                if (shape.getTextType() == Placeholder.BODY) {
                    shape.setText("These are notes for slide 2");
                    break;
                }
            }

            // Slide 3 without notes
            XSLFSlide slide3 = pptx.createSlide(layout);
            setPlaceholderText(slide3, Placeholder.TITLE, "No Notes Slide");
            setPlaceholderText(slide3, Placeholder.BODY, "This slide has no notes");
            setPlaceholderText(slide3, Placeholder.CONTENT, "This slide has no notes");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with a table that has empty cells.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithTableEmptyCells(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlide slide = pptx.createSlide();

            XSLFTable table = slide.createTable(3, 3);
            table.setAnchor(new Rectangle2D.Double(50, 50, 620, 150));

            // Header row
            table.getRows().get(0).getCells().get(0).setText("Col1");
            table.getRows().get(0).getCells().get(1).setText("Col2");
            table.getRows().get(0).getCells().get(2).setText("Col3");

            // Data row with all values
            table.getRows().get(1).getCells().get(0).setText("A");
            table.getRows().get(1).getCells().get(1).setText("B");
            table.getRows().get(1).getCells().get(2).setText("C");

            // Data row with empty cells (middle cell empty)
            table.getRows().get(2).getCells().get(0).setText("X");
            // getCells().get(1) left with default empty text
            table.getRows().get(2).getCells().get(2).setText("Z");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Creates a presentation with a table that has no header row.
     *
     * @param path The path to create the file at
     */
    public static void createPresentationWithTableNoHeaders(String path) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlide slide = pptx.createSlide();

            XSLFTable table = slide.createTable(2, 3);
            table.setAnchor(new Rectangle2D.Double(50, 50, 620, 100));

            table.getRows().get(0).getCells().get(0).setText("Value1");
            table.getRows().get(0).getCells().get(1).setText("100");
            table.getRows().get(0).getCells().get(2).setText("true");

            table.getRows().get(1).getCells().get(0).setText("Value2");
            table.getRows().get(1).getCells().get(1).setText("200");
            table.getRows().get(1).getCells().get(2).setText("false");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                pptx.write(out);
            }
        }
    }

    /**
     * Helper method to set text on a placeholder shape.
     */
    private static void setPlaceholderText(XSLFSlide slide, Placeholder type, String text) {
        for (XSLFTextShape shape : slide.getPlaceholders()) {
            if (shape.getTextType() == type) {
                shape.setText(text);
                return;
            }
        }
    }
}
