/*  ExcelTestHelper.java Copyright 2021 - 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.DataFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDateTime;

/**
 * Helper class for creating test Excel files for the ExcelDataProvider tests.
 */
public class ExcelTestHelper {

    /**
     * Creates a simple Excel file with headers and data.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");

            // Create header row
            XSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Age");
            headerRow.createCell(2).setCellValue("City");

            // Create data rows
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Alice");
            row1.createCell(1).setCellValue(30);
            row1.createCell(2).setCellValue("New York");

            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Bob");
            row2.createCell(1).setCellValue(25);
            row2.createCell(2).setCellValue("Los Angeles");

            XSSFRow row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("Charlie");
            row3.createCell(1).setCellValue(35);
            row3.createCell(2).setCellValue("Chicago");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates a simple Excel file with headers and data and returns the bytes.
     *
     * @return The Excel file as bytes
     */
    public static byte[] createSimpleExcelBytes() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");

            // Create header row
            XSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Age");
            headerRow.createCell(2).setCellValue("City");

            // Create data rows
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Alice");
            row1.createCell(1).setCellValue(30);
            row1.createCell(2).setCellValue("New York");

            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Bob");
            row2.createCell(1).setCellValue(25);
            row2.createCell(2).setCellValue("Los Angeles");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates an Excel file with multiple worksheets.
     *
     * @param path The path to create the file at
     */
    public static void createMultiSheetExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Sheet 1: Users
            XSSFSheet sheet1 = workbook.createSheet("Users");
            XSSFRow header1 = sheet1.createRow(0);
            header1.createCell(0).setCellValue("ID");
            header1.createCell(1).setCellValue("Username");
            XSSFRow data1 = sheet1.createRow(1);
            data1.createCell(0).setCellValue(1);
            data1.createCell(1).setCellValue("alice");
            XSSFRow data2 = sheet1.createRow(2);
            data2.createCell(0).setCellValue(2);
            data2.createCell(1).setCellValue("bob");

            // Sheet 2: Products
            XSSFSheet sheet2 = workbook.createSheet("Products");
            XSSFRow header2 = sheet2.createRow(0);
            header2.createCell(0).setCellValue("SKU");
            header2.createCell(1).setCellValue("Product");
            header2.createCell(2).setCellValue("Price");
            XSSFRow prod1 = sheet2.createRow(1);
            prod1.createCell(0).setCellValue("A001");
            prod1.createCell(1).setCellValue("Widget");
            prod1.createCell(2).setCellValue(9.99);

            // Sheet 3: Orders
            XSSFSheet sheet3 = workbook.createSheet("Orders");
            XSSFRow header3 = sheet3.createRow(0);
            header3.createCell(0).setCellValue("OrderID");
            header3.createCell(1).setCellValue("UserID");
            header3.createCell(2).setCellValue("Total");
            XSSFRow order1 = sheet3.createRow(1);
            order1.createCell(0).setCellValue(1001);
            order1.createCell(1).setCellValue(1);
            order1.createCell(2).setCellValue(99.99);

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with formulas.
     *
     * @param path The path to create the file at
     */
    public static void createFormulasExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Calculations");

            // Headers
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("A");
            header.createCell(1).setCellValue("B");
            header.createCell(2).setCellValue("Sum");
            header.createCell(3).setCellValue("Product");

            // Data row 1
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(10);
            row1.createCell(1).setCellValue(20);
            row1.createCell(2).setCellFormula("A2+B2");
            row1.createCell(3).setCellFormula("A2*B2");

            // Data row 2
            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(5);
            row2.createCell(1).setCellValue(3);
            row2.createCell(2).setCellFormula("A3+B3");
            row2.createCell(3).setCellFormula("A3*B3");

            // Force formula evaluation
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with date values.
     *
     * @param path The path to create the file at
     */
    public static void createDatesExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Dates");

            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            DataFormat df = workbook.createDataFormat();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));

            // Headers
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Event");
            header.createCell(1).setCellValue("Date");

            // Data
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Start");
            XSSFCell dateCell1 = row1.createCell(1);
            dateCell1.setCellValue(LocalDateTime.of(2025, 1, 15, 0, 0));
            dateCell1.setCellStyle(dateStyle);

            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("End");
            XSSFCell dateCell2 = row2.createCell(1);
            dateCell2.setCellValue(LocalDateTime.of(2025, 6, 30, 0, 0));
            dateCell2.setCellStyle(dateStyle);

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an empty Excel workbook.
     *
     * @param path The path to create the file at
     */
    public static void createEmptyExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Empty");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file without headers (just raw data).
     *
     * @param path The path to create the file at
     */
    public static void createNoHeadersExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");

            // Just data rows, no headers
            XSSFRow row1 = sheet.createRow(0);
            row1.createCell(0).setCellValue("value1");
            row1.createCell(1).setCellValue(100);
            row1.createCell(2).setCellValue(true);

            XSSFRow row2 = sheet.createRow(1);
            row2.createCell(0).setCellValue("value2");
            row2.createCell(1).setCellValue(200);
            row2.createCell(2).setCellValue(false);

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with special characters and unicode.
     *
     * @param path The path to create the file at
     */
    public static void createSpecialCharsExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Special");

            // Headers with special characters
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Description");

            // Data with unicode and special chars
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Cafe");
            row1.createCell(1).setCellValue("A nice place to have coffee");

            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Tokyo");
            row2.createCell(1).setCellValue("Capital of Japan");

            XSSFRow row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("Quotes");
            row3.createCell(1).setCellValue("He said \"hello\"");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with mixed case headers for tolwr testing.
     *
     * @param path The path to create the file at
     */
    public static void createMixedCaseHeadersExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");

            // Headers with mixed case
            XSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("FirstName");
            headerRow.createCell(1).setCellValue("LAST_NAME");
            headerRow.createCell(2).setCellValue("Email_Address");

            // Data
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("John");
            row1.createCell(1).setCellValue("Doe");
            row1.createCell(2).setCellValue("john.doe@example.com");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with data starting at a specific cell range.
     *
     * @param path The path to create the file at
     */
    public static void createOffsetDataExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");

            // Some text in upper left
            XSSFRow titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Report Title");

            // Empty row 1

            // Headers starting at B3
            XSSFRow headerRow = sheet.createRow(2);
            headerRow.createCell(1).setCellValue("Col1");
            headerRow.createCell(2).setCellValue("Col2");
            headerRow.createCell(3).setCellValue("Col3");

            // Data starting at B4
            XSSFRow row1 = sheet.createRow(3);
            row1.createCell(1).setCellValue("A");
            row1.createCell(2).setCellValue("B");
            row1.createCell(3).setCellValue("C");

            XSSFRow row2 = sheet.createRow(4);
            row2.createCell(1).setCellValue("D");
            row2.createCell(2).setCellValue("E");
            row2.createCell(3).setCellValue("F");

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an Excel file with various data types for testing.
     *
     * @param path The path to create the file at
     */
    public static void createDataTypesExcel(String path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("DataTypes");

            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            DataFormat df = workbook.createDataFormat();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd hh:mm:ss"));

            // Headers
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("String");
            header.createCell(1).setCellValue("Integer");
            header.createCell(2).setCellValue("Float");
            header.createCell(3).setCellValue("Boolean");
            header.createCell(4).setCellValue("Date");

            // Data row 1
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("text");
            row1.createCell(1).setCellValue(42);
            row1.createCell(2).setCellValue(3.14159);
            row1.createCell(3).setCellValue(true);
            XSSFCell dateCell1 = row1.createCell(4);
            dateCell1.setCellValue(LocalDateTime.of(2025, 3, 15, 10, 30, 0));
            dateCell1.setCellStyle(dateStyle);

            // Data row 2
            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("another");
            row2.createCell(1).setCellValue(-100);
            row2.createCell(2).setCellValue(2.71828);
            row2.createCell(3).setCellValue(false);
            XSSFCell dateCell2 = row2.createCell(4);
            dateCell2.setCellValue(LocalDateTime.of(2024, 12, 25, 0, 0, 0));
            dateCell2.setCellStyle(dateStyle);

            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates a workbook laid out the way supplier PO confirmations are maintained by hand: a merged title banner,
     * a blank row, the header, identifiers with leading zeros, dates stored as text, a totals row with a SUM
     * formula, a hidden lookup sheet, and a named range on it.
     *
     * @param path The path to create the file at
     * @param xls true for the Excel 97-2003 (.xls) format, false for .xlsx
     */
    public static void createProfileExcel(String path, boolean xls) throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Confirmations");
            sheet.createRow(0).createCell(0).setCellValue("Supplier PO confirmations - week 41");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
            Row header = sheet.createRow(2);
            String[] headers = {"PO", "SKU", "Qty", "Ship date"};
            for (int i = 0; i < headers.length; ++i) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Object[][] data = {
                {"PO-1001", "000123", 5.0, "10.10.2026"},
                {"PO-1002", "000456", 7.0, "24.10.2026"},
            };
            for (int r = 0; r < data.length; ++r) {
                Row row = sheet.createRow(3 + r);
                for (int c = 0; c < data[r].length; ++c) {
                    if (data[r][c] instanceof Double) {
                        row.createCell(c).setCellValue((Double)data[r][c]);
                    } else {
                        row.createCell(c).setCellValue((String)data[r][c]);
                    }
                }
            }
            Row total = sheet.createRow(5);
            total.createCell(0).setCellValue("Total");
            total.createCell(2).setCellFormula("SUM(C4:C5)");

            Sheet lookup = workbook.createSheet("Lookup");
            Row lh = lookup.createRow(0);
            lh.createCell(0).setCellValue("Code");
            lh.createCell(1).setCellValue("Rate");
            Row lr = lookup.createRow(1);
            lr.createCell(0).setCellValue("X");
            lr.createCell(1).setCellValue(1.5);
            workbook.setSheetHidden(1, true);

            Name name = workbook.createName();
            name.setNameName("Rates");
            name.setRefersToFormula("Lookup!$A$1:$B$2");

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates a large .xlsx workbook, written with SXSSF in bounded memory.
     *
     * The worksheet "Large" has a header row (Id, Name, Amount, When, Flag) and the given number of data rows; data
     * row i (1-based) has Id i, Name "name-i", Amount i / 4, When 2025-01-01 00:00 plus i seconds in a cell with a
     * date format, and Flag true for even i.
     *
     * @param path The path to create the file at
     * @param rows The number of data rows
     * @param shared_strings true to write the strings to the shared strings table, false to write them inline
     */
    public static void createLargeExcel(String path, int rows, boolean shared_strings) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(null, 100, true, shared_strings)) {
            CellStyle date_style = workbook.createCellStyle();
            date_style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            Sheet sheet = workbook.createSheet("Large");
            Row header = sheet.createRow(0);
            String[] headers = {"Id", "Name", "Amount", "When", "Flag"};
            for (int i = 0; i < headers.length; ++i) {
                header.createCell(i).setCellValue(headers[i]);
            }
            LocalDateTime base = LocalDateTime.of(2025, 1, 1, 0, 0);
            for (int i = 1; i <= rows; ++i) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue("name-" + i);
                row.createCell(2).setCellValue(i / 4.0);
                Cell when = row.createCell(3);
                when.setCellValue(base.plusSeconds(i));
                when.setCellStyle(date_style);
                row.createCell(4).setCellValue(i % 2 == 0);
            }
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an .xlsx workbook from raw SpreadsheetML with the cell encodings that the streaming reader decodes
     * itself.
     *
     * The worksheet "Main" has:
     * - row 1 (headers): shared strings "Name" and " Val " (with spaces), "When", and the inline string "Inline"
     * - row 2: a rich shared string with a phonetic run ("rich"), 42.5, 45000.25 in column C, which has a date
     *   column style (2023-03-15 06:00), and an inline rich string with a phonetic run ("a b")
     * - row 3, whose cells have no references: a shared string with an _xHHHH_ escape ("escA"), 7, 1 with an
     *   explicit non-date style in the date column (1.0), and an inline string with an escape ("B")
     * - row 4 is missing
     * - row 5: boolean cells "1" (true) and "true" (false, as POI reads it), 45001 (2023-03-16), and an error cell
     * - row 6, without a row number: formulas with cached results: 2, "xy_x0043_" ("xyC"), TRUE, and an error
     * - row 7: an array formula over A7:B8 (1), a cell of the array without a cached value (0.0), 45000.5 with a
     *   built-in date and time style (2023-03-15 12:00), and an invalid shared string index ("")
     *
     * The second sheet, "Chart", is a chart sheet, which has no cells; the third, "Sparse", has rows and cells
     * without references: row 1: 1, 2; row 2: C2 = 3, D2 = 4; row 20: A20 = "far"; row 21: A21 = 5.
     *
     * @param path The path to create the file at
     */
    public static void createStreamingFeaturesExcel(String path) throws IOException {
        final String main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        final String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
        final String ct = "application/vnd.openxmlformats-officedocument.spreadsheetml.";

        String content_types = xml
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"" + ct + "sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"" + ct + "worksheet+xml\"/>"
            + "<Override PartName=\"/xl/chartsheets/sheet2.xml\" ContentType=\"" + ct + "chartsheet+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"" + ct + "worksheet+xml\"/>"
            + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"" + ct + "sharedStrings+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"" + ct + "styles+xml\"/>"
            + "</Types>";
        String root_rels = xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + rel + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";
        String workbook = xml + "<workbook xmlns=\"" + main + "\" xmlns:r=\"" + rel + "\"><workbookPr/><sheets>"
            + "<sheet name=\"Main\" sheetId=\"1\" r:id=\"rId1\"/>"
            + "<sheet name=\"Chart\" sheetId=\"2\" r:id=\"rId2\"/>"
            + "<sheet name=\"Sparse\" sheetId=\"3\" r:id=\"rId3\"/>"
            + "</sheets></workbook>";
        String workbook_rels = xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + rel + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"" + rel + "/chartsheet\" Target=\"chartsheets/sheet2.xml\"/>"
            + "<Relationship Id=\"rId3\" Type=\"" + rel + "/worksheet\" Target=\"worksheets/sheet3.xml\"/>"
            + "<Relationship Id=\"rId4\" Type=\"" + rel + "/sharedStrings\" Target=\"sharedStrings.xml\"/>"
            + "<Relationship Id=\"rId5\" Type=\"" + rel + "/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";
        String shared_strings = xml + "<sst xmlns=\"" + main + "\" count=\"5\" uniqueCount=\"5\">"
            + "<si><t>Name</t></si>"
            + "<si><t xml:space=\"preserve\"> Val </t></si>"
            + "<si><r><t>ri</t></r><r><rPr><b/></rPr><t>ch</t></r><rPh sb=\"0\" eb=\"1\"><t>PH</t></rPh></si>"
            + "<si><t>esc_x0041_</t></si>"
            + "<si><t>When</t></si>"
            + "</sst>";
        String styles = xml + "<styleSheet xmlns=\"" + main + "\">"
            + "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"yyyy\\-mm\\-dd\"/>"
            + "<numFmt numFmtId=\"165\" formatCode=\"0.000\"/></numFmts>"
            + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"22\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
        String sheet1 = xml + "<worksheet xmlns=\"" + main + "\" xmlns:r=\"" + rel + "\">"
            + "<cols><col min=\"3\" max=\"3\" width=\"20\" style=\"1\" customWidth=\"1\"/></cols><sheetData>"
            + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
            + "<c r=\"C1\" t=\"s\"><v>4</v></c><c r=\"D1\" t=\"inlineStr\"><is><t>Inline</t></is></c></row>"
            + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>42.5</v></c><c r=\"C2\"><v>45000.25</v></c>"
            + "<c r=\"D2\" t=\"inlineStr\"><is><r><t>a</t></r><r><t xml:space=\"preserve\"> b</t></r>"
            + "<rPh sb=\"0\" eb=\"1\"><t>x</t></rPh></is></c></row>"
            + "<row r=\"3\"><c t=\"s\"><v>3</v></c><c><v>7</v></c><c s=\"2\"><v>1</v></c>"
            + "<c t=\"inlineStr\"><is><t>_x0042_</t></is></c></row>"
            + "<row r=\"5\"><c r=\"A5\" t=\"b\"><v>1</v></c><c r=\"B5\" t=\"b\"><v>true</v></c>"
            + "<c r=\"C5\"><v>45001</v></c><c r=\"D5\" t=\"e\"><v>#N/A</v></c></row>"
            + "<row><c r=\"A6\"><f>1+1</f><v>2</v></c><c r=\"B6\" t=\"str\"><f>\"xy\"</f><v>xy_x0043_</v></c>"
            + "<c r=\"C6\" t=\"b\"><f>TRUE()</f><v>1</v></c><c r=\"D6\" t=\"e\"><f>1/0</f><v>#DIV/0!</v></c></row>"
            + "<row r=\"7\"><c r=\"A7\"><f t=\"array\" ref=\"A7:B8\">1</f><v>1</v></c><c r=\"B7\"/>"
            + "<c r=\"C7\" s=\"3\"><v>45000.5</v></c><c r=\"D7\" t=\"s\"><v>99</v></c></row>"
            + "</sheetData></worksheet>";
        String sheet2 = xml + "<chartsheet xmlns=\"" + main + "\" xmlns:r=\"" + rel + "\">"
            + "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews></chartsheet>";
        String sheet3 = xml + "<worksheet xmlns=\"" + main + "\" xmlns:r=\"" + rel + "\"><sheetData>"
            + "<row><c><v>1</v></c><c><v>2</v></c></row>"
            + "<row><c r=\"C2\"><v>3</v></c><c><v>4</v></c></row>"
            + "<row r=\"20\"><c r=\"A20\" t=\"inlineStr\"><is><t>far</t></is></c></row>"
            + "<row><c><v>5</v></c></row>"
            + "</sheetData></worksheet>";

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(path)))) {
            writeEntry(zip, "[Content_Types].xml", content_types);
            writeEntry(zip, "_rels/.rels", root_rels);
            writeEntry(zip, "xl/workbook.xml", workbook);
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbook_rels);
            writeEntry(zip, "xl/sharedStrings.xml", shared_strings);
            writeEntry(zip, "xl/styles.xml", styles);
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheet1);
            writeEntry(zip, "xl/chartsheets/sheet2.xml", sheet2);
            writeEntry(zip, "xl/worksheets/sheet3.xml", sheet3);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
