/*  WordTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Helper class for creating test Word files for the WordDataProvider tests.
 */
public class WordTestHelper {

    /**
     * Creates a simple Word document with paragraphs.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleDocument(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Create title
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Sample Document");
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // Create paragraphs
            XWPFParagraph para1 = document.createParagraph();
            para1.createRun().setText("This is the first paragraph of the document.");

            XWPFParagraph para2 = document.createParagraph();
            para2.createRun().setText("This is the second paragraph with more content.");

            XWPFParagraph para3 = document.createParagraph();
            para3.createRun().setText("This is the third and final paragraph.");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a simple Word document and returns the bytes.
     *
     * @return The document as a byte array
     */
    public static byte[] createSimpleDocumentBytes() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Sample Document");

            XWPFParagraph para1 = document.createParagraph();
            para1.createRun().setText("This is the first paragraph.");

            XWPFParagraph para2 = document.createParagraph();
            para2.createRun().setText("This is the second paragraph.");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates a Word document with a table.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithTable(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Add intro paragraph
            XWPFParagraph intro = document.createParagraph();
            intro.createRun().setText("The following table contains employee data:");

            // Create table with 4 rows and 3 columns
            XWPFTable table = document.createTable(4, 3);

            // Header row
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Name");
            headerRow.getCell(1).setText("Department");
            headerRow.getCell(2).setText("Salary");

            // Data rows
            XWPFTableRow row1 = table.getRow(1);
            row1.getCell(0).setText("Alice Smith");
            row1.getCell(1).setText("Engineering");
            row1.getCell(2).setText("75000");

            XWPFTableRow row2 = table.getRow(2);
            row2.getCell(0).setText("Bob Johnson");
            row2.getCell(1).setText("Marketing");
            row2.getCell(2).setText("65000");

            XWPFTableRow row3 = table.getRow(3);
            row3.getCell(0).setText("Carol Williams");
            row3.getCell(1).setText("Finance");
            row3.getCell(2).setText("70000");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a Word document with multiple tables.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithMultipleTables(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // First section
            XWPFParagraph section1 = document.createParagraph();
            section1.setStyle("Heading1");
            section1.createRun().setText("Products");

            // First table - Products
            XWPFTable table1 = document.createTable(3, 2);
            table1.getRow(0).getCell(0).setText("SKU");
            table1.getRow(0).getCell(1).setText("Product");
            table1.getRow(1).getCell(0).setText("A001");
            table1.getRow(1).getCell(1).setText("Widget");
            table1.getRow(2).getCell(0).setText("A002");
            table1.getRow(2).getCell(1).setText("Gadget");

            // Second section
            XWPFParagraph section2 = document.createParagraph();
            section2.setStyle("Heading1");
            section2.createRun().setText("Customers");

            // Second table - Customers
            XWPFTable table2 = document.createTable(3, 3);
            table2.getRow(0).getCell(0).setText("ID");
            table2.getRow(0).getCell(1).setText("Name");
            table2.getRow(0).getCell(2).setText("Country");
            table2.getRow(1).getCell(0).setText("1");
            table2.getRow(1).getCell(1).setText("Acme Corp");
            table2.getRow(1).getCell(2).setText("USA");
            table2.getRow(2).getCell(0).setText("2");
            table2.getRow(2).getCell(1).setText("Global Ltd");
            table2.getRow(2).getCell(2).setText("UK");

            // Third section
            XWPFParagraph section3 = document.createParagraph();
            section3.setStyle("Heading1");
            section3.createRun().setText("Orders");

            // Third table - Orders
            XWPFTable table3 = document.createTable(2, 4);
            table3.getRow(0).getCell(0).setText("OrderID");
            table3.getRow(0).getCell(1).setText("CustomerID");
            table3.getRow(0).getCell(2).setText("SKU");
            table3.getRow(0).getCell(3).setText("Quantity");
            table3.getRow(1).getCell(0).setText("1001");
            table3.getRow(1).getCell(1).setText("1");
            table3.getRow(1).getCell(2).setText("A001");
            table3.getRow(1).getCell(3).setText("5");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates an empty Word document.
     *
     * @param path The path to create the file at
     */
    public static void createEmptyDocument(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Just create an empty document
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a Word document with various heading styles.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithStyles(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Heading 1
            XWPFParagraph h1 = document.createParagraph();
            h1.setStyle("Heading1");
            XWPFRun h1Run = h1.createRun();
            h1Run.setText("Main Title");
            h1Run.setBold(true);
            h1Run.setFontSize(16);

            // Normal paragraph
            XWPFParagraph p1 = document.createParagraph();
            p1.createRun().setText("This is a normal paragraph under the main title.");

            // Heading 2
            XWPFParagraph h2 = document.createParagraph();
            h2.setStyle("Heading2");
            XWPFRun h2Run = h2.createRun();
            h2Run.setText("Section One");
            h2Run.setBold(true);
            h2Run.setFontSize(14);

            // Normal paragraph
            XWPFParagraph p2 = document.createParagraph();
            p2.createRun().setText("Content for section one goes here.");

            // Heading 3
            XWPFParagraph h3 = document.createParagraph();
            h3.setStyle("Heading3");
            XWPFRun h3Run = h3.createRun();
            h3Run.setText("Subsection A");
            h3Run.setBold(true);
            h3Run.setFontSize(12);

            // Normal paragraph
            XWPFParagraph p3 = document.createParagraph();
            p3.createRun().setText("Content for subsection A.");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a Word document with special characters and unicode.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithSpecialChars(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText("Special Characters Test");

            XWPFParagraph p1 = document.createParagraph();
            p1.createRun().setText("Caf\u00e9 - French word with accent");

            XWPFParagraph p2 = document.createParagraph();
            p2.createRun().setText("\u6771\u4eac - Tokyo in Japanese");

            XWPFParagraph p3 = document.createParagraph();
            p3.createRun().setText("Currency symbols: \u20ac \u00a3 \u00a5 $");

            XWPFParagraph p4 = document.createParagraph();
            p4.createRun().setText("Quotes: \"Hello\" and 'World'");

            XWPFParagraph p5 = document.createParagraph();
            p5.createRun().setText("Math: \u03c0 \u00d7 r\u00b2");

            // Table with special characters
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Name");
            table.getRow(0).getCell(1).setText("Value");
            table.getRow(1).getCell(0).setText("Caf\u00e9");
            table.getRow(1).getCell(1).setText("\u20ac100");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a Word document with a table that has empty cells.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithEmptyCells(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph intro = document.createParagraph();
            intro.createRun().setText("Table with empty cells:");

            XWPFTable table = document.createTable(3, 3);

            // Header row
            table.getRow(0).getCell(0).setText("Col1");
            table.getRow(0).getCell(1).setText("Col2");
            table.getRow(0).getCell(2).setText("Col3");

            // Data row with all values
            table.getRow(1).getCell(0).setText("A");
            table.getRow(1).getCell(1).setText("B");
            table.getRow(1).getCell(2).setText("C");

            // Data row with empty cells (middle cell empty)
            table.getRow(2).getCell(0).setText("X");
            // getCell(1) left empty
            table.getRow(2).getCell(2).setText("Z");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }

    /**
     * Creates a Word document with a table that has no header row.
     *
     * @param path The path to create the file at
     */
    public static void createDocumentWithTableNoHeaders(String path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Create table without a header row - just data
            XWPFTable table = document.createTable(2, 3);

            table.getRow(0).getCell(0).setText("Value1");
            table.getRow(0).getCell(1).setText("100");
            table.getRow(0).getCell(2).setText("true");

            table.getRow(1).getCell(0).setText("Value2");
            table.getRow(1).getCell(1).setText("200");
            table.getRow(1).getCell(2).setText("false");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                document.write(out);
            }
        }
    }
}
