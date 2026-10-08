/*  ExcelWriter.java Copyright 2021 - 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Closeable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.qore.jni.Hash;
import org.qore.dataprovider.format.SpreadsheetFormatCode;

/**
 * Helper class for writing Excel files from Qore.
 */
public class ExcelWriter implements Closeable {
    private Workbook workbook;
    private Sheet sheet;
    private CellStyle dateStyle;
    private DataFormat dataFormat;
    // the display format of each column by header name
    private LinkedHashMap<String, SpreadsheetFormatCode> columnFormats =
        new LinkedHashMap<String, SpreadsheetFormatCode>();
    // one cell style per distinct format code, because a workbook can hold only a limited number of cell styles
    private HashMap<String, CellStyle> formatStyles = new HashMap<String, CellStyle>();
    // the format code and cell style of each column by position, set when the columns are known
    private SpreadsheetFormatCode[] columnCodes;
    private CellStyle[] columnStyles;
    private ArrayList<String> headers = new ArrayList<String>();
    private int currentRow = 0;
    private boolean headersWritten = false;
    private String sheetName;
    private String format;
    private boolean streaming;

    /**
     * Creates a new ExcelWriter.
     *
     * @param sheet_name The name of the worksheet to create
     * @param format The output format: "xlsx" (default) or "xls"
     * @param streaming Whether to use streaming mode (SXSSF) for large files
     */
    public ExcelWriter(String sheet_name, String format, boolean streaming) {
        this.sheetName = sheet_name != null && !sheet_name.isEmpty() ? sheet_name : "Sheet1";
        this.format = format != null ? format.toLowerCase() : "xlsx";
        this.streaming = streaming;

        if (this.format.equals("xls")) {
            workbook = new HSSFWorkbook();
        } else if (streaming) {
            // SXSSF for streaming large xlsx files - keeps only 100 rows in memory
            workbook = new SXSSFWorkbook(100);
        } else {
            workbook = new XSSFWorkbook();
        }

        sheet = workbook.createSheet(this.sheetName);

        // Create date style
        dateStyle = workbook.createCellStyle();
        dataFormat = workbook.createDataFormat();
        dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm:ss"));
    }

    /**
     * Returns why a display format code is not accepted, or null if it is
     *
     * @param code the format code, such as {@code dd.mm.yyyy}, {@code #,##0.00} or {@code 0.0%}
     * @return the reason the format code is not accepted, or null if it is valid
     */
    public static String formatCodeIssue(String code) {
        return SpreadsheetFormatCode.issue(code);
    }

    /**
     * Sets the display format of columns
     *
     * Each value in a column is written as its type (a number stays a number, a date a date) and shown with the
     * column's format code; a value that the code does not show (a string in a number column, for example) is
     * written as it is without the format.  A column without a format code is written as before: a date with the
     * default date and time format and a number without a format.
     *
     * @param formats the format code of each column by its header name; see {@link SpreadsheetFormatCode}
     * @throws IllegalArgumentException a format code is not valid, or rows have already been written; the message
     * names the column
     */
    public void setColumnFormats(Hash formats) {
        if (currentRow > 0) {
            throw new IllegalArgumentException("column formats must be set before any row is written");
        }
        LinkedHashMap<String, SpreadsheetFormatCode> codes = new LinkedHashMap<String, SpreadsheetFormatCode>();
        for (Map.Entry<String, Object> e : formats.entrySet()) {
            Object value = e.getValue();
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(String.format("the format of column \"%s\" is not a string",
                    e.getKey()));
            }
            try {
                codes.put(e.getKey(), SpreadsheetFormatCode.parse((String) value));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(String.format("column \"%s\": %s", e.getKey(), ex.getMessage()),
                    ex);
            }
        }
        columnFormats = codes;
    }

    /**
     * Resolves the format code and cell style of each column once the columns are known
     *
     * @throws IllegalArgumentException a column format names a column that is not written
     */
    private void resolveColumnStyles() {
        if (columnStyles != null) {
            return;
        }
        for (String column : columnFormats.keySet()) {
            if (!headers.contains(column)) {
                throw new IllegalArgumentException(String.format("a format is given for column \"%s\", which is not "
                    + "one of the columns written: %s", column, headers));
            }
        }
        SpreadsheetFormatCode[] codes = new SpreadsheetFormatCode[headers.size()];
        CellStyle[] styles = new CellStyle[headers.size()];
        for (int i = 0; i < headers.size(); ++i) {
            SpreadsheetFormatCode code = columnFormats.get(headers.get(i));
            if (code == null) {
                continue;
            }
            CellStyle style = formatStyles.get(code.getCode());
            if (style == null) {
                style = workbook.createCellStyle();
                style.setDataFormat(dataFormat.getFormat(code.getCode()));
                formatStyles.put(code.getCode(), style);
            }
            codes[i] = code;
            styles[i] = style;
        }
        columnCodes = codes;
        columnStyles = styles;
    }

    /**
     * Creates a new ExcelWriter with default settings.
     */
    public ExcelWriter() {
        this("Sheet1", "xlsx", false);
    }

    /**
     * Sets the headers for the worksheet.
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
     * Sets the headers for the worksheet.
     *
     * @param headers ArrayList of header names
     */
    public void setHeaders(ArrayList<String> headers) {
        this.headers = new ArrayList<String>(headers);
    }

    /**
     * Writes the header row.
     */
    public void writeHeaders() {
        if (headersWritten || headers.isEmpty()) {
            return;
        }
        Row row = sheet.createRow(currentRow++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
        }
        headersWritten = true;
    }

    /**
     * Writes a data row from a hash.
     *
     * @param data The hash containing the row data
     */
    public void writeRow(Hash data) {
        // With no predefined headers, the first record's keys define the columns; they are taken before any row is
        // written, so the header row is always written first and no rows ever need to be shifted (SXSSF streaming
        // sheets cannot shift rows that were already flushed)
        if (headers.isEmpty()) {
            for (Object key : data.keySet()) {
                headers.add(key.toString());
            }
        }
        resolveColumnStyles();
        writeHeaders();

        Row row = sheet.createRow(currentRow++);
        for (int i = 0; i < headers.size(); i++) {
            Object value = data.get(headers.get(i));
            Cell cell = row.createCell(i);
            setCellValue(cell, value);
            if (columnCodes[i] != null && columnCodes[i].shows(value)) {
                cell.setCellStyle(columnStyles[i]);
            }
        }
    }

    /**
     * Sets the cell value based on the object type.
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof ZonedDateTime) {
            cell.setCellValue(Date.from(((ZonedDateTime) value).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Writes the workbook to a file.
     *
     * @param path The file path to write to
     */
    public void writeToFile(String path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            workbook.write(out);
        }
    }

    /**
     * Writes the workbook to an output stream.
     *
     * @param stream The output stream to write to
     */
    public void writeToStream(OutputStream stream) throws IOException {
        workbook.write(stream);
    }

    /**
     * Writes the workbook to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the workbook contents as bytes.
     *
     * @return The workbook as a byte array
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Gets the current row count (including header if written).
     */
    public int getRowCount() {
        return currentRow;
    }

    /**
     * Closes the workbook and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (workbook != null) {
            // For SXSSF, dispose of temporary files
            if (workbook instanceof SXSSFWorkbook) {
                ((SXSSFWorkbook) workbook).dispose();
            }
            workbook.close();
            workbook = null;
        }
    }
}
