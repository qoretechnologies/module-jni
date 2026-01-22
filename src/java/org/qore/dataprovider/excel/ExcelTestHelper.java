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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
}
