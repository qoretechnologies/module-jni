/*  OdpTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.dom.element.draw.DrawFrameElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawPageElement;
import org.odftoolkit.odfdom.dom.element.draw.DrawTextBoxElement;
import org.odftoolkit.odfdom.dom.element.presentation.PresentationNotesElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Helper class for creating test ODP files for the OdpDataProvider tests.
 */
public class OdpTestHelper {

    /**
     * Creates a simple ODP presentation with 3 slides containing title, body, and notes.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1 - Introduction
            DrawPageElement slide1 = createSlide(doc, contentDom, "Slide1");
            addTitleFrame(contentDom, slide1, "Introduction");
            addBodyFrame(contentDom, slide1, "Welcome to the presentation");
            addNotesElement(contentDom, slide1, "Opening remarks go here");

            // Slide 2 - Main Content
            DrawPageElement slide2 = createSlide(doc, contentDom, "Slide2");
            addTitleFrame(contentDom, slide2, "Main Content");
            addBodyFrame(contentDom, slide2, "This is the main content slide");
            addNotesElement(contentDom, slide2, "Discuss the key points");

            // Slide 3 - Conclusion
            DrawPageElement slide3 = createSlide(doc, contentDom, "Slide3");
            addTitleFrame(contentDom, slide3, "Conclusion");
            addBodyFrame(contentDom, slide3, "Thank you for your attention");
            addNotesElement(contentDom, slide3, "Wrap up and take questions");

            doc.save(path);
        }
    }

    /**
     * Creates a simple ODP presentation and returns the bytes.
     *
     * @return The presentation as a byte array
     */
    public static byte[] createSimpleOdpBytes() throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1
            DrawPageElement slide1 = createSlide(doc, contentDom, "Slide1");
            addTitleFrame(contentDom, slide1, "Sample Slide");
            addBodyFrame(contentDom, slide1, "Sample body text");

            // Slide 2
            DrawPageElement slide2 = createSlide(doc, contentDom, "Slide2");
            addTitleFrame(contentDom, slide2, "Second Slide");
            addBodyFrame(contentDom, slide2, "More content here");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.save(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates an ODP presentation with a table on a slide.
     *
     * @param path The path to create the file at
     */
    public static void createTableOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            DrawPageElement slide = createSlide(doc, contentDom, "TableSlide");
            addTitleFrame(contentDom, slide, "Data Table");

            // Create a table with 4 rows and 3 columns
            OdfTable table = OdfTable.newTable(doc, 4, 3);
            Node tableNode = table.getOdfElement();
            if (tableNode.getParentNode() != null) {
                tableNode.getParentNode().removeChild(tableNode);
            }

            // Wrap the table in a draw:frame on the slide
            DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
            frame.setSvgXAttribute("2cm");
            frame.setSvgYAttribute("5cm");
            frame.setSvgWidthAttribute("22cm");
            frame.setSvgHeightAttribute("10cm");
            slide.appendChild(frame);
            frame.appendChild(tableNode);

            // Header row
            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Department");
            table.getCellByPosition(2, 0).setStringValue("Salary");

            // Data rows
            table.getCellByPosition(0, 1).setStringValue("Alice Smith");
            table.getCellByPosition(1, 1).setStringValue("Engineering");
            table.getCellByPosition(2, 1).setStringValue("75000");

            table.getCellByPosition(0, 2).setStringValue("Bob Johnson");
            table.getCellByPosition(1, 2).setStringValue("Marketing");
            table.getCellByPosition(2, 2).setStringValue("65000");

            table.getCellByPosition(0, 3).setStringValue("Carol Williams");
            table.getCellByPosition(1, 3).setStringValue("Finance");
            table.getCellByPosition(2, 3).setStringValue("70000");

            doc.save(path);
        }
    }

    /**
     * Creates an empty ODP presentation (no slides).
     *
     * @param path The path to create the file at
     */
    public static void createEmptyOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            removeDefaultSlides(doc);
            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with special characters and unicode.
     *
     * @param path The path to create the file at
     */
    public static void createSpecialCharsOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1 - accented characters
            DrawPageElement slide1 = createSlide(doc, contentDom, "Special1");
            addTitleFrame(contentDom, slide1, "Special Characters Test");
            addBodyFrame(contentDom, slide1, "Caf\u00e9 - French word with accent");

            // Slide 2 - CJK characters and currency symbols
            DrawPageElement slide2 = createSlide(doc, contentDom, "Special2");
            addTitleFrame(contentDom, slide2, "\u6771\u4eac - Tokyo in Japanese");
            addBodyFrame(contentDom, slide2, "Currency symbols: \u20ac \u00a3 \u00a5 $");

            // Slide 3 - math symbols and quotes
            DrawPageElement slide3 = createSlide(doc, contentDom, "Special3");
            addTitleFrame(contentDom, slide3, "Math: \u03c0 \u00d7 r\u00b2");
            addBodyFrame(contentDom, slide3, "Quotes: \"Hello\" and 'World'");

            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with multiple slides each containing a table.
     *
     * @param path The path to create the file at
     */
    public static void createMultiTableOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1 - Products table
            DrawPageElement slide1 = createSlide(doc, contentDom, "Products");
            addTitleFrame(contentDom, slide1, "Products");
            OdfTable table1 = OdfTable.newTable(doc, 3, 2);
            Node tableNode1 = table1.getOdfElement();
            if (tableNode1.getParentNode() != null) {
                tableNode1.getParentNode().removeChild(tableNode1);
            }
            DrawFrameElement frame1 = contentDom.newOdfElement(DrawFrameElement.class);
            frame1.setSvgXAttribute("2cm");
            frame1.setSvgYAttribute("5cm");
            frame1.setSvgWidthAttribute("22cm");
            frame1.setSvgHeightAttribute("10cm");
            slide1.appendChild(frame1);
            frame1.appendChild(tableNode1);
            table1.getCellByPosition(0, 0).setStringValue("SKU");
            table1.getCellByPosition(1, 0).setStringValue("Product");
            table1.getCellByPosition(0, 1).setStringValue("A001");
            table1.getCellByPosition(1, 1).setStringValue("Widget");
            table1.getCellByPosition(0, 2).setStringValue("A002");
            table1.getCellByPosition(1, 2).setStringValue("Gadget");

            // Slide 2 - Customers table
            DrawPageElement slide2 = createSlide(doc, contentDom, "Customers");
            addTitleFrame(contentDom, slide2, "Customers");
            OdfTable table2 = OdfTable.newTable(doc, 3, 3);
            Node tableNode2 = table2.getOdfElement();
            if (tableNode2.getParentNode() != null) {
                tableNode2.getParentNode().removeChild(tableNode2);
            }
            DrawFrameElement frame2 = contentDom.newOdfElement(DrawFrameElement.class);
            frame2.setSvgXAttribute("2cm");
            frame2.setSvgYAttribute("5cm");
            frame2.setSvgWidthAttribute("22cm");
            frame2.setSvgHeightAttribute("10cm");
            slide2.appendChild(frame2);
            frame2.appendChild(tableNode2);
            table2.getCellByPosition(0, 0).setStringValue("ID");
            table2.getCellByPosition(1, 0).setStringValue("Name");
            table2.getCellByPosition(2, 0).setStringValue("Country");
            table2.getCellByPosition(0, 1).setStringValue("1");
            table2.getCellByPosition(1, 1).setStringValue("Acme Corp");
            table2.getCellByPosition(2, 1).setStringValue("USA");
            table2.getCellByPosition(0, 2).setStringValue("2");
            table2.getCellByPosition(1, 2).setStringValue("Global Ltd");
            table2.getCellByPosition(2, 2).setStringValue("UK");

            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with different slide styles/layouts.
     *
     * @param path The path to create the file at
     */
    public static void createStyledOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1 - Title and content
            DrawPageElement slide1 = createSlide(doc, contentDom, "TitleContent");
            addTitleFrame(contentDom, slide1, "Title And Content Layout");
            addBodyFrame(contentDom, slide1, "Body text here");

            // Slide 2 - Title only
            DrawPageElement slide2 = createSlide(doc, contentDom, "TitleOnly");
            addTitleFrame(contentDom, slide2, "Title Only Layout");

            // Slide 3 - Blank
            createSlide(doc, contentDom, "Blank");

            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with speaker notes on some slides.
     *
     * @param path The path to create the file at
     */
    public static void createNotesOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            // Slide 1 with notes
            DrawPageElement slide1 = createSlide(doc, contentDom, "NotesSlide1");
            addTitleFrame(contentDom, slide1, "Slide With Notes");
            addBodyFrame(contentDom, slide1, "This slide has speaker notes");
            addNotesElement(contentDom, slide1, "Remember to explain the key points here");

            // Slide 2 with notes
            DrawPageElement slide2 = createSlide(doc, contentDom, "NotesSlide2");
            addTitleFrame(contentDom, slide2, "Another Slide");
            addBodyFrame(contentDom, slide2, "More content");
            addNotesElement(contentDom, slide2, "These are notes for slide 2");

            // Slide 3 without notes
            DrawPageElement slide3 = createSlide(doc, contentDom, "NoNotes");
            addTitleFrame(contentDom, slide3, "No Notes Slide");
            addBodyFrame(contentDom, slide3, "This slide has no notes");

            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with a table that has empty cells.
     *
     * @param path The path to create the file at
     */
    public static void createTableEmptyCellsOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            DrawPageElement slide = createSlide(doc, contentDom, "EmptyCells");

            OdfTable table = OdfTable.newTable(doc, 3, 3);
            Node tableNode = table.getOdfElement();
            if (tableNode.getParentNode() != null) {
                tableNode.getParentNode().removeChild(tableNode);
            }
            DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
            frame.setSvgXAttribute("2cm");
            frame.setSvgYAttribute("2cm");
            frame.setSvgWidthAttribute("22cm");
            frame.setSvgHeightAttribute("10cm");
            slide.appendChild(frame);
            frame.appendChild(tableNode);

            // Header row
            table.getCellByPosition(0, 0).setStringValue("Col1");
            table.getCellByPosition(1, 0).setStringValue("Col2");
            table.getCellByPosition(2, 0).setStringValue("Col3");

            // Data row with all values
            table.getCellByPosition(0, 1).setStringValue("A");
            table.getCellByPosition(1, 1).setStringValue("B");
            table.getCellByPosition(2, 1).setStringValue("C");

            // Data row with empty cells (middle cell left empty)
            table.getCellByPosition(0, 2).setStringValue("X");
            // getCellByPosition(1, 2) left with default empty text
            table.getCellByPosition(2, 2).setStringValue("Z");

            doc.save(path);
        }
    }

    /**
     * Creates an ODP presentation with a table that has no header row.
     *
     * @param path The path to create the file at
     */
    public static void createTableNoHeadersOdp(String path) throws Exception {
        try (OdfPresentationDocument doc = OdfPresentationDocument.newPresentationDocument()) {
            OdfContentDom contentDom = doc.getContentDom();
            removeDefaultSlides(doc);

            DrawPageElement slide = createSlide(doc, contentDom, "NoHeaders");

            OdfTable table = OdfTable.newTable(doc, 2, 3);
            Node tableNode = table.getOdfElement();
            if (tableNode.getParentNode() != null) {
                tableNode.getParentNode().removeChild(tableNode);
            }
            DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
            frame.setSvgXAttribute("2cm");
            frame.setSvgYAttribute("2cm");
            frame.setSvgWidthAttribute("22cm");
            frame.setSvgHeightAttribute("10cm");
            slide.appendChild(frame);
            frame.appendChild(tableNode);

            table.getCellByPosition(0, 0).setStringValue("Value1");
            table.getCellByPosition(1, 0).setStringValue("100");
            table.getCellByPosition(2, 0).setStringValue("true");

            table.getCellByPosition(0, 1).setStringValue("Value2");
            table.getCellByPosition(1, 1).setStringValue("200");
            table.getCellByPosition(2, 1).setStringValue("false");

            doc.save(path);
        }
    }

    /**
     * Creates a new DrawPageElement and appends it to the presentation content root.
     */
    private static DrawPageElement createSlide(OdfPresentationDocument doc, OdfContentDom contentDom,
            String name) throws Exception {
        DrawPageElement page = contentDom.newOdfElement(DrawPageElement.class);
        page.setDrawNameAttribute(name);
        doc.getContentRoot().appendChild(page);
        return page;
    }

    /**
     * Removes any default slides created by newPresentationDocument().
     */
    private static void removeDefaultSlides(OdfPresentationDocument doc) throws Exception {
        Node contentRoot = doc.getContentRoot();
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
     * Adds a title frame (presentation:class="title") to a slide.
     */
    private static void addTitleFrame(OdfContentDom contentDom, DrawPageElement page, String text) {
        DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
        frame.setPresentationClassAttribute("title");
        frame.setSvgXAttribute("1cm");
        frame.setSvgYAttribute("1cm");
        frame.setSvgWidthAttribute("23cm");
        frame.setSvgHeightAttribute("3cm");
        page.appendChild(frame);

        DrawTextBoxElement textBox = contentDom.newOdfElement(DrawTextBoxElement.class);
        frame.appendChild(textBox);

        TextPElement para = contentDom.newOdfElement(TextPElement.class);
        para.setTextContent(text);
        textBox.appendChild(para);
    }

    /**
     * Adds a body frame (presentation:class="outline") to a slide.
     */
    private static void addBodyFrame(OdfContentDom contentDom, DrawPageElement page, String text) {
        DrawFrameElement frame = contentDom.newOdfElement(DrawFrameElement.class);
        frame.setPresentationClassAttribute("outline");
        frame.setSvgXAttribute("1cm");
        frame.setSvgYAttribute("5cm");
        frame.setSvgWidthAttribute("23cm");
        frame.setSvgHeightAttribute("12cm");
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
     * Adds a presentation:notes element with text to a slide.
     */
    private static void addNotesElement(OdfContentDom contentDom, DrawPageElement page, String notesText) {
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
}
