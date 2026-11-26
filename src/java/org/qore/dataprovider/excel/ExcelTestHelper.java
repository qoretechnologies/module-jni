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
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FormulaError;

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
     * Reads a workbook file into memory as a raw grid with POI's usermodel, as the profile read every workbook before
     * .xlsx workbooks were streamed; the grid of an .xlsx workbook read as it is streamed must be the same
     *
     * @param path the path of the workbook file
     * @param max_rows the maximum number of rows read per sheet
     */
    public static org.qore.jni.Hash readGridInMemory(String path, int max_rows) throws Throwable {
        return ExcelGridReader.readWorkbookGrid(new File(path), max_rows);
    }


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
     * Creates a workbook whose worksheets have each visibility state.
     *
     * The worksheets, in workbook order, are "Visible", "Hidden" (hidden: the user can unhide it), "Very Hidden"
     * (very hidden: only a macro can unhide it), and "Last" (visible); each has a header row (Sheet) and one data row
     * with the worksheet's name.
     *
     * @param path The path to create the file at
     * @param xls true for the Excel 97-2003 (.xls) format, false for .xlsx
     */
    public static void createSheetVisibilityExcel(String path, boolean xls) throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            String[] names = {"Visible", "Hidden", "Very Hidden", "Last"};
            for (String name : names) {
                Sheet sheet = workbook.createSheet(name);
                sheet.createRow(0).createCell(0).setCellValue("Sheet");
                sheet.createRow(1).createCell(0).setCellValue(name);
            }
            workbook.setSheetVisibility(1, SheetVisibility.HIDDEN);
            workbook.setSheetVisibility(2, SheetVisibility.VERY_HIDDEN);
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates a workbook of orders whose columns have a type each, for typed reading.
     *
     * The worksheet "Orders" has a header row (Id, Price, Paid, Shipped, Name, Note, Empty) and the given number of
     * data rows; data row i (1-based) has Id i, Price i * 1.25, Paid true for even i, Shipped 2026-01-01 plus i days in
     * a cell with a date format, Name "item-i", Note "n-i" for odd i and the number i for even i, and no Empty cell.
     * If mismatch_row is positive, the Id of that data row is the text "N/A" and its Price the text "free".
     *
     * @param path The path to create the file at
     * @param xls true for the Excel 97-2003 (.xls) format, false for .xlsx
     * @param rows The number of data rows
     * @param mismatch_row The 1-based data row with text in the number columns, or 0 for none
     */
    public static void createTypedColumnsExcel(String path, boolean xls, int rows, int mismatch_row)
            throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Orders");
            CellStyle date_style = workbook.createCellStyle();
            date_style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            String[] headers = {"Id", "Price", "Paid", "Shipped", "Name", "Note", "Empty"};
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; ++c) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int i = 1; i <= rows; ++i) {
                Row row = sheet.createRow(i);
                if (i == mismatch_row) {
                    row.createCell(0).setCellValue("N/A");
                    row.createCell(1).setCellValue("free");
                } else {
                    row.createCell(0).setCellValue(i);
                    row.createCell(1).setCellValue(i * 1.25);
                }
                row.createCell(2).setCellValue(i % 2 == 0);
                Cell date = row.createCell(3);
                date.setCellValue(LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(i));
                date.setCellStyle(date_style);
                row.createCell(4).setCellValue("item-" + i);
                if (i % 2 == 1) {
                    row.createCell(5).setCellValue("n-" + i);
                } else {
                    row.createCell(5).setCellValue(i);
                }
            }
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates a workbook with error cells.
     *
     * The worksheet "Rates" has a header row (Item, Ratio, Lookup) and three data rows: ("a", =1/0 (#DIV/0!), the
     * error value #N/A), ("b", 0.5, "x"), and ("c", =NA() (#N/A), "y").  The formulas are evaluated, so their cached
     * results are the errors.
     *
     * @param path The path to create the file at
     * @param xls true for the Excel 97-2003 (.xls) format, false for .xlsx
     */
    public static void createErrorCellsExcel(String path, boolean xls) throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Rates");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Item");
            header.createCell(1).setCellValue("Ratio");
            header.createCell(2).setCellValue("Lookup");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("a");
            r1.createCell(1).setCellFormula("1/0");
            r1.createCell(2).setCellErrorValue(FormulaError.NA.getCode());
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("b");
            r2.createCell(1).setCellValue(0.5);
            r2.createCell(2).setCellValue("x");
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("c");
            r3.createCell(1).setCellFormula("NA()");
            r3.createCell(2).setCellValue("y");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
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
     * Creates a workbook with a header of two rows and merged cells (qore#7751): "Artikel" merged down over A1:A2,
     * "Menge" merged over B1:C1 above "bestätigt" and "offen", "Datum" merged down over D1:D2; two data rows, whose
     * "Lager" value "L1" is merged down over E3:E4; then a row "Summe" with the formula B3+C3 merged over B5:C5; a second sheet "Leer" has the header x, an empty
     * merged range B1:C1 and y, and one data row; a third sheet "Rand" has merged first cells holding " " (B1:C1), the
     * formula ="" (D1:E1), 0 (F1:G1) and false (H1:I1)
     *
     * @param path The path to create the file at
     * @param xls true for the Excel 97-2003 (.xls) format, false for .xlsx
     */
    public static void createMergedHeaderExcel(String path, boolean xls) throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bestellungen");
            Row group = sheet.createRow(0);
            group.createCell(0).setCellValue("Artikel");
            group.createCell(1).setCellValue("Menge");
            group.createCell(2);
            group.createCell(3).setCellValue("Datum");
            group.createCell(4).setCellValue("Lager");
            Row names = sheet.createRow(1);
            names.createCell(0);
            names.createCell(1).setCellValue("bestätigt");
            names.createCell(2).setCellValue("offen");
            names.createCell(3);
            Row r1 = sheet.createRow(2);
            r1.createCell(0).setCellValue("A-1");
            r1.createCell(1).setCellValue(5.0);
            r1.createCell(2).setCellValue(2.0);
            r1.createCell(3).setCellValue("10.10.2026");
            r1.createCell(4).setCellValue("L1");
            Row r2 = sheet.createRow(3);
            r2.createCell(0).setCellValue("A-2");
            r2.createCell(1).setCellValue(7.0);
            r2.createCell(2).setCellValue(0.0);
            r2.createCell(3).setCellValue("12.10.2026");
            r2.createCell(4);
            sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));
            sheet.addMergedRegion(new CellRangeAddress(0, 1, 3, 3));
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 4, 4));
            // a formula merged over B5:C5
            Row r3 = sheet.createRow(4);
            r3.createCell(0).setCellValue("Summe");
            r3.createCell(1).setCellFormula("B3+C3");
            r3.createCell(2);
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 1, 2));
            // a sheet whose header row has an empty merged range B1:C1
            Sheet blank = workbook.createSheet("Leer");
            Row bh = blank.createRow(0);
            bh.createCell(0).setCellValue("x");
            bh.createCell(1);
            bh.createCell(2);
            bh.createCell(3).setCellValue("y");
            Row bd = blank.createRow(1);
            bd.createCell(0).setCellValue("1");
            bd.createCell(3).setCellValue("2");
            blank.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));
            // a sheet whose merged first cells hold whitespace, a formula with a blank result, 0 and false
            Sheet edge = workbook.createSheet("Rand");
            Row er = edge.createRow(0);
            er.createCell(0).setCellValue("id");
            er.createCell(1).setCellValue(" ");
            er.createCell(2);
            er.createCell(3).setCellFormula("\"\"");
            er.createCell(4);
            er.createCell(5).setCellValue(0.0);
            er.createCell(6);
            er.createCell(7).setCellValue(false);
            er.createCell(8);
            edge.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));
            edge.addMergedRegion(new CellRangeAddress(0, 0, 3, 4));
            edge.addMergedRegion(new CellRangeAddress(0, 0, 5, 6));
            edge.addMergedRegion(new CellRangeAddress(0, 0, 7, 8));
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

    /**
     * Returns the display format code of each cell of a row of the first sheet of a workbook, read with POI
     *
     * @param path the workbook file (.xlsx or .xls)
     * @param row the 0-based row
     * @return the format code of each cell in the row, or null for a cell that does not exist
     */
    public static String[] getCellFormats(String path, int row) throws IOException {
        return getCellFormats(path, 0, row);
    }

    /**
     * Returns the display format code of each cell of a row of a sheet of a workbook, read with POI
     *
     * @param path the workbook file (.xlsx or .xls)
     * @param sheet the 0-based sheet
     * @param row the 0-based row
     * @return the format code of each cell in the row, or null for a cell that does not exist
     */
    public static String[] getCellFormats(String path, int sheet, int row) throws IOException {
        try (Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new File(path), null, true)) {
            Row r = wb.getSheetAt(sheet).getRow(row);
            String[] formats = new String[r.getLastCellNum()];
            for (int i = 0; i < formats.length; ++i) {
                Cell cell = r.getCell(i);
                formats[i] = cell == null ? null : cell.getCellStyle().getDataFormatString();
            }
            return formats;
        }
    }

    /**
     * Returns the number of cell styles in a workbook
     *
     * @param path the workbook file (.xlsx or .xls)
     * @return the number of cell styles in the workbook
     */
    public static int getCellStyleCount(String path) throws IOException {
        try (Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new File(path), null, true)) {
            return wb.getNumCellStyles();
        }
    }

    /**
     * Creates an order workbook whose rows hold formulas, in the sheet "Order":
     * - row 1: the headers Item, Qty, Price, Line
     * - rows 2-4: data rows; D2 = B2*C2 (a product), D3 = SUM(B3:C3) and D4 = SUM(B4:C4) (line totals of their own
     *   row)
     * - row 5: a totals row without a label: B5 = SUM(B2:B4), D5 = SUBTOTAL(9,D2:D4)
     * - row 6: a labelled totals row: "Total", 6, empty, 29 (values)
     *
     * The formulas are evaluated, so each formula cell has its result.
     *
     * @param path The path to create the file at
     * @param xls true for an .xls (BIFF8) workbook, false for .xlsx
     */
    public static void createFormulaRowsExcel(String path, boolean xls) throws IOException {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Order");
            String[] headers = {"Item", "Qty", "Price", "Line"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; ++i) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Object[][] data = {
                {"A-1", 2.0, 5.0, "B2*C2"},
                {"B-2", 3.0, 4.0, "SUM(B3:C3)"},
                {"C-3", 1.0, 7.0, "SUM(B4:C4)"},
            };
            for (int r = 0; r < data.length; ++r) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue((String)data[r][0]);
                row.createCell(1).setCellValue((Double)data[r][1]);
                row.createCell(2).setCellValue((Double)data[r][2]);
                row.createCell(3).setCellFormula((String)data[r][3]);
            }
            Row totals = sheet.createRow(4);
            totals.createCell(1).setCellFormula("SUM(B2:B4)");
            totals.createCell(3).setCellFormula("SUBTOTAL(9,D2:D4)");
            Row labelled = sheet.createRow(5);
            labelled.createCell(0).setCellValue("Total");
            labelled.createCell(1).setCellValue(6.0);
            labelled.createCell(3).setCellValue(29.0);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (FileOutputStream out = new FileOutputStream(new File(path))) {
                workbook.write(out);
            }
        }
    }

    /**
     * Creates an .xlsx workbook with shared formulas, as Excel writes a formula filled down a column, in the sheet
     * "Order" of the sheets "Rates" and "Order", with the workbook-scoped defined name "Rate" and the name "Local"
     * scoped to "Order":
     * - row 1: the headers Item, Qty, Price, Line, Gross, Array
     * - rows 2-5: Item, Qty, Price; D2:D5 hold the shared formula "SUM(B2:C2)" (the master in D2), E2:E5 the shared
     *   formula "D2*Rate+$B$1+Rates!A1+Local" whose master is in E3 (E2 is a formula of its own: "D2"), F2:F3 an
     *   array formula "B2:B3*2", and G4 a data table cell
     * - row 6: a totals row of its own formulas: B6 = SUM(B2:B5), D6 = SUM(D2:D5)
     *
     * @param path The path to create the file at
     */
    public static void createSharedFormulaExcel(String path) throws IOException {
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
            + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"" + ct + "worksheet+xml\"/>"
            + "</Types>";
        String root_rels = xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + rel + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";
        String workbook = xml + "<workbook xmlns=\"" + main + "\" xmlns:r=\"" + rel + "\"><sheets>"
            + "<sheet name=\"Rates\" sheetId=\"1\" r:id=\"rId1\"/>"
            + "<sheet name=\"Order\" sheetId=\"2\" r:id=\"rId2\"/>"
            + "</sheets><definedNames><definedName name=\"Rate\">Rates!$A$1</definedName>"
            + "<definedName name=\"Local\" localSheetId=\"1\">Order!$C$2</definedName></definedNames></workbook>";
        String workbook_rels = xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + rel + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"" + rel + "/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
            + "</Relationships>";
        String rates = xml + "<worksheet xmlns=\"" + main + "\"><sheetData>"
            + "<row r=\"1\"><c r=\"A1\"><v>1.5</v></c></row></sheetData></worksheet>";
        StringBuilder order = new StringBuilder(xml + "<worksheet xmlns=\"" + main + "\"><sheetData><row r=\"1\">");
        String[] headers = {"Item", "Qty", "Price", "Line", "Gross", "Array", "Table"};
        for (int i = 0; i < headers.length; ++i) {
            order.append("<c r=\"" + (char)('A' + i) + "1\" t=\"inlineStr\"><is><t>" + headers[i] + "</t></is></c>");
        }
        order.append("</row>");
        for (int r = 2; r <= 5; ++r) {
            order.append("<row r=\"" + r + "\"><c r=\"A" + r + "\" t=\"inlineStr\"><is><t>I-" + r + "</t></is></c>"
                + "<c r=\"B" + r + "\"><v>" + r + "</v></c><c r=\"C" + r + "\"><v>10</v></c>");
            if (r == 2) {
                order.append("<c r=\"D2\"><f t=\"shared\" ref=\"D2:D5\" si=\"0\">SUM(B2:C2)</f><v>12</v></c>"
                    + "<c r=\"E2\"><f>D2</f><v>12</v></c>"
                    + "<c r=\"F2\"><f t=\"array\" ref=\"F2:F3\">B2:B3*2</f><v>4</v></c>");
            } else {
                order.append("<c r=\"D" + r + "\"><f t=\"shared\" si=\"0\"/><v>" + (r + 10) + "</v></c>");
                if (r == 3) {
                    order.append("<c r=\"E3\"><f t=\"shared\" ref=\"E3:E5\" si=\"1\">D3*Rate+$B$1+Rates!A1+Local</f>"
                        + "<v>1</v></c><c r=\"F3\"><v>6</v></c>");
                } else {
                    order.append("<c r=\"E" + r + "\"><f t=\"shared\" si=\"1\"/><v>1</v></c>");
                }
                if (r == 4) {
                    order.append("<c r=\"G4\"><f t=\"dataTable\" ref=\"G4\" dt2D=\"0\" dtr=\"0\" r1=\"B2\"/>"
                        + "<v>3</v></c>");
                }
            }
            order.append("</row>");
        }
        order.append("<row r=\"6\"><c r=\"B6\"><f>SUM(B2:B5)</f><v>14</v></c>"
            + "<c r=\"D6\"><f>SUM(D2:D5)</f><v>54</v></c></row></sheetData></worksheet>");

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(path)))) {
            writeEntry(zip, "[Content_Types].xml", content_types);
            writeEntry(zip, "_rels/.rels", root_rels);
            writeEntry(zip, "xl/workbook.xml", workbook);
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbook_rels);
            writeEntry(zip, "xl/worksheets/sheet1.xml", rates);
            writeEntry(zip, "xl/worksheets/sheet2.xml", order.toString());
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
