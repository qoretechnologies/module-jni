/*  OdsTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.ods;

import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Helper class for creating test ODS files for the OdsDataProvider tests.
 */
public class OdsTestHelper {

    /**
     * Creates a simple ODS file with headers and data.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            // Create header row
            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Age");
            table.getCellByPosition(2, 0).setStringValue("City");

            // Create data rows
            table.getCellByPosition(0, 1).setStringValue("Alice");
            table.getCellByPosition(1, 1).setDoubleValue(30.0);
            table.getCellByPosition(2, 1).setStringValue("New York");

            table.getCellByPosition(0, 2).setStringValue("Bob");
            table.getCellByPosition(1, 2).setDoubleValue(25.0);
            table.getCellByPosition(2, 2).setStringValue("Los Angeles");

            table.getCellByPosition(0, 3).setStringValue("Charlie");
            table.getCellByPosition(1, 3).setDoubleValue(35.0);
            table.getCellByPosition(2, 3).setStringValue("Chicago");

            doc.save(path);
        }
    }

    /**
     * Creates a simple ODS file and returns the bytes.
     */
    public static byte[] createSimpleOdsBytes() throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Age");
            table.getCellByPosition(2, 0).setStringValue("City");

            table.getCellByPosition(0, 1).setStringValue("Alice");
            table.getCellByPosition(1, 1).setDoubleValue(30.0);
            table.getCellByPosition(2, 1).setStringValue("New York");

            table.getCellByPosition(0, 2).setStringValue("Bob");
            table.getCellByPosition(1, 2).setDoubleValue(25.0);
            table.getCellByPosition(2, 2).setStringValue("Los Angeles");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.save(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates an ODS file with multiple worksheets.
     */
    public static void createMultiSheetOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            // Sheet 1: Users
            OdfTable sheet1 = doc.getTableList().get(0);
            sheet1.setTableName("Users");
            sheet1.getCellByPosition(0, 0).setStringValue("ID");
            sheet1.getCellByPosition(1, 0).setStringValue("Username");
            sheet1.getCellByPosition(0, 1).setDoubleValue(1.0);
            sheet1.getCellByPosition(1, 1).setStringValue("alice");
            sheet1.getCellByPosition(0, 2).setDoubleValue(2.0);
            sheet1.getCellByPosition(1, 2).setStringValue("bob");

            // Sheet 2: Products
            OdfTable sheet2 = OdfTable.newTable(doc);
            sheet2.setTableName("Products");
            sheet2.getCellByPosition(0, 0).setStringValue("SKU");
            sheet2.getCellByPosition(1, 0).setStringValue("Product");
            sheet2.getCellByPosition(2, 0).setStringValue("Price");
            sheet2.getCellByPosition(0, 1).setStringValue("A001");
            sheet2.getCellByPosition(1, 1).setStringValue("Widget");
            sheet2.getCellByPosition(2, 1).setDoubleValue(9.99);

            // Sheet 3: Orders
            OdfTable sheet3 = OdfTable.newTable(doc);
            sheet3.setTableName("Orders");
            sheet3.getCellByPosition(0, 0).setStringValue("OrderID");
            sheet3.getCellByPosition(1, 0).setStringValue("UserID");
            sheet3.getCellByPosition(2, 0).setStringValue("Total");
            sheet3.getCellByPosition(0, 1).setDoubleValue(1001.0);
            sheet3.getCellByPosition(1, 1).setDoubleValue(1.0);
            sheet3.getCellByPosition(2, 1).setDoubleValue(99.99);

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with date values.
     */
    public static void createDatesOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Dates");

            table.getCellByPosition(0, 0).setStringValue("Event");
            table.getCellByPosition(1, 0).setStringValue("Date");

            table.getCellByPosition(0, 1).setStringValue("Start");
            GregorianCalendar cal1 = new GregorianCalendar(2025, Calendar.JANUARY, 15);
            table.getCellByPosition(1, 1).setDateValue(cal1);

            table.getCellByPosition(0, 2).setStringValue("End");
            GregorianCalendar cal2 = new GregorianCalendar(2025, Calendar.JUNE, 30);
            table.getCellByPosition(1, 2).setDateValue(cal2);

            doc.save(path);
        }
    }

    /**
     * Creates an empty ODS workbook.
     */
    public static void createEmptyOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Empty");
            doc.save(path);
        }
    }

    /**
     * Creates an ODS file without headers (just raw data).
     */
    public static void createNoHeadersOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Data");

            table.getCellByPosition(0, 0).setStringValue("value1");
            table.getCellByPosition(1, 0).setDoubleValue(100.0);
            table.getCellByPosition(2, 0).setBooleanValue(true);

            table.getCellByPosition(0, 1).setStringValue("value2");
            table.getCellByPosition(1, 1).setDoubleValue(200.0);
            table.getCellByPosition(2, 1).setBooleanValue(false);

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with special characters and unicode.
     */
    public static void createSpecialCharsOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Special");

            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Description");

            table.getCellByPosition(0, 1).setStringValue("Cafe");
            table.getCellByPosition(1, 1).setStringValue("A nice place to have coffee");

            table.getCellByPosition(0, 2).setStringValue("Tokyo");
            table.getCellByPosition(1, 2).setStringValue("Capital of Japan");

            table.getCellByPosition(0, 3).setStringValue("Quotes");
            table.getCellByPosition(1, 3).setStringValue("He said \"hello\"");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with mixed case headers for tolwr testing.
     */
    public static void createMixedCaseHeadersOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            table.getCellByPosition(0, 0).setStringValue("FirstName");
            table.getCellByPosition(1, 0).setStringValue("LAST_NAME");
            table.getCellByPosition(2, 0).setStringValue("Email_Address");

            table.getCellByPosition(0, 1).setStringValue("John");
            table.getCellByPosition(1, 1).setStringValue("Doe");
            table.getCellByPosition(2, 1).setStringValue("john.doe@example.com");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with data starting at a specific cell range.
     */
    public static void createOffsetDataOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            // Some text in upper left
            table.getCellByPosition(0, 0).setStringValue("Report Title");

            // Headers starting at B3 (col 1, row 2 in 0-based)
            table.getCellByPosition(1, 2).setStringValue("Col1");
            table.getCellByPosition(2, 2).setStringValue("Col2");
            table.getCellByPosition(3, 2).setStringValue("Col3");

            // Data starting at B4
            table.getCellByPosition(1, 3).setStringValue("A");
            table.getCellByPosition(2, 3).setStringValue("B");
            table.getCellByPosition(3, 3).setStringValue("C");

            table.getCellByPosition(1, 4).setStringValue("D");
            table.getCellByPosition(2, 4).setStringValue("E");
            table.getCellByPosition(3, 4).setStringValue("F");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with various data types for testing.
     */
    public static void createDataTypesOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("DataTypes");

            // Headers
            table.getCellByPosition(0, 0).setStringValue("String");
            table.getCellByPosition(1, 0).setStringValue("Integer");
            table.getCellByPosition(2, 0).setStringValue("Float");
            table.getCellByPosition(3, 0).setStringValue("Boolean");
            table.getCellByPosition(4, 0).setStringValue("Date");

            // Data row 1
            table.getCellByPosition(0, 1).setStringValue("text");
            table.getCellByPosition(1, 1).setDoubleValue(42.0);
            table.getCellByPosition(2, 1).setDoubleValue(3.14159);
            table.getCellByPosition(3, 1).setBooleanValue(true);
            GregorianCalendar dtCal1 = new GregorianCalendar(2025, Calendar.MARCH, 15, 10, 30, 0);
            table.getCellByPosition(4, 1).setDateValue(dtCal1);

            // Data row 2
            table.getCellByPosition(0, 2).setStringValue("another");
            table.getCellByPosition(1, 2).setDoubleValue(-100.0);
            table.getCellByPosition(2, 2).setDoubleValue(2.71828);
            table.getCellByPosition(3, 2).setBooleanValue(false);
            GregorianCalendar dtCal2 = new GregorianCalendar(2024, Calendar.DECEMBER, 25, 0, 0, 0);
            table.getCellByPosition(4, 2).setDateValue(dtCal2);

            doc.save(path);
        }
    }
}
