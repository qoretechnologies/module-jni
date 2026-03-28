/*  OdtTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.dom.element.office.OfficeTextElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.element.text.TextHElement;

import java.io.ByteArrayOutputStream;

/**
 * Helper class for creating test ODT files for the OdtDataProvider tests.
 */
public class OdtTestHelper {

    /**
     * Creates a simple ODT document with paragraphs using Normal and Heading styles.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleOdt(String path) throws Exception {
        try (OdfTextDocument doc = OdfTextDocument.newTextDocument()) {
            OfficeTextElement contentRoot = doc.getContentRoot();
            // Remove the default empty paragraph
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }

            // Create a Heading1
            TextHElement heading1 = doc.getContentDom().newOdfElement(TextHElement.class);
            heading1.setTextOutlineLevelAttribute(1);
            heading1.setTextContent("Sample Document");
            contentRoot.appendChild(heading1);

            // Create normal paragraphs
            TextPElement para1 = doc.getContentDom().newOdfElement(TextPElement.class);
            para1.setTextContent("This is the first paragraph of the document.");
            contentRoot.appendChild(para1);

            TextPElement para2 = doc.getContentDom().newOdfElement(TextPElement.class);
            para2.setTextContent("This is the second paragraph with more content.");
            contentRoot.appendChild(para2);

            // Create a Heading2
            TextHElement heading2 = doc.getContentDom().newOdfElement(TextHElement.class);
            heading2.setTextOutlineLevelAttribute(2);
            heading2.setTextContent("Section One");
            contentRoot.appendChild(heading2);

            TextPElement para3 = doc.getContentDom().newOdfElement(TextPElement.class);
            para3.setTextContent("This is the third and final paragraph.");
            contentRoot.appendChild(para3);

            doc.save(path);
        }
    }

    /**
     * Creates a simple ODT document and returns the bytes.
     *
     * @return The document as a byte array
     */
    public static byte[] createSimpleOdtBytes() throws Exception {
        try (OdfTextDocument doc = OdfTextDocument.newTextDocument()) {
            OfficeTextElement contentRoot = doc.getContentRoot();
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }

            TextHElement heading = doc.getContentDom().newOdfElement(TextHElement.class);
            heading.setTextOutlineLevelAttribute(1);
            heading.setTextContent("Sample Document");
            contentRoot.appendChild(heading);

            TextPElement para1 = doc.getContentDom().newOdfElement(TextPElement.class);
            para1.setTextContent("This is the first paragraph.");
            contentRoot.appendChild(para1);

            TextPElement para2 = doc.getContentDom().newOdfElement(TextPElement.class);
            para2.setTextContent("This is the second paragraph.");
            contentRoot.appendChild(para2);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.save(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates an ODT document with a table containing headers and data rows.
     *
     * @param path The path to create the file at
     */
    public static void createTableOdt(String path) throws Exception {
        try (OdfTextDocument doc = OdfTextDocument.newTextDocument()) {
            OfficeTextElement contentRoot = doc.getContentRoot();
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }

            // Add intro paragraph
            TextPElement intro = doc.getContentDom().newOdfElement(TextPElement.class);
            intro.setTextContent("The following table contains employee data:");
            contentRoot.appendChild(intro);

            // Create table with 4 rows and 3 columns
            OdfTable table = OdfTable.newTable(doc, 4, 3);

            // Header row
            OdfTableRow headerRow = table.getRowByIndex(0);
            headerRow.getCellByIndex(0).setStringValue("Name");
            headerRow.getCellByIndex(1).setStringValue("Department");
            headerRow.getCellByIndex(2).setStringValue("Salary");

            // Data rows
            OdfTableRow row1 = table.getRowByIndex(1);
            row1.getCellByIndex(0).setStringValue("Alice Smith");
            row1.getCellByIndex(1).setStringValue("Engineering");
            row1.getCellByIndex(2).setStringValue("75000");

            OdfTableRow row2 = table.getRowByIndex(2);
            row2.getCellByIndex(0).setStringValue("Bob Johnson");
            row2.getCellByIndex(1).setStringValue("Marketing");
            row2.getCellByIndex(2).setStringValue("65000");

            OdfTableRow row3 = table.getRowByIndex(3);
            row3.getCellByIndex(0).setStringValue("Carol Williams");
            row3.getCellByIndex(1).setStringValue("Finance");
            row3.getCellByIndex(2).setStringValue("70000");

            doc.save(path);
        }
    }

    /**
     * Creates an empty ODT document.
     *
     * @param path The path to create the file at
     */
    public static void createEmptyOdt(String path) throws Exception {
        try (OdfTextDocument doc = OdfTextDocument.newTextDocument()) {
            OfficeTextElement contentRoot = doc.getContentRoot();
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }
            doc.save(path);
        }
    }

    /**
     * Creates an ODT document with unicode and special characters in paragraphs.
     *
     * @param path The path to create the file at
     */
    public static void createSpecialCharsOdt(String path) throws Exception {
        try (OdfTextDocument doc = OdfTextDocument.newTextDocument()) {
            OfficeTextElement contentRoot = doc.getContentRoot();
            while (contentRoot.hasChildNodes()) {
                contentRoot.removeChild(contentRoot.getFirstChild());
            }

            TextHElement title = doc.getContentDom().newOdfElement(TextHElement.class);
            title.setTextOutlineLevelAttribute(1);
            title.setTextContent("Special Characters Test");
            contentRoot.appendChild(title);

            TextPElement p1 = doc.getContentDom().newOdfElement(TextPElement.class);
            p1.setTextContent("Caf\u00e9 - French word with accent");
            contentRoot.appendChild(p1);

            TextPElement p2 = doc.getContentDom().newOdfElement(TextPElement.class);
            p2.setTextContent("\u6771\u4eac - Tokyo in Japanese");
            contentRoot.appendChild(p2);

            TextPElement p3 = doc.getContentDom().newOdfElement(TextPElement.class);
            p3.setTextContent("Currency symbols: \u20ac \u00a3 \u00a5 $");
            contentRoot.appendChild(p3);

            TextPElement p4 = doc.getContentDom().newOdfElement(TextPElement.class);
            p4.setTextContent("Quotes: \"Hello\" and 'World'");
            contentRoot.appendChild(p4);

            TextPElement p5 = doc.getContentDom().newOdfElement(TextPElement.class);
            p5.setTextContent("Math: \u03c0 \u00d7 r\u00b2");
            contentRoot.appendChild(p5);

            doc.save(path);
        }
    }
}
