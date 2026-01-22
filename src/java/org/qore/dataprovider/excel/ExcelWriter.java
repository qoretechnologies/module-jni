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
import java.util.Map;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Helper class for writing Excel files from Qore.
 */
public class ExcelWriter implements Closeable {
    private Workbook workbook;
    private Sheet sheet;
    private CellStyle dateStyle;
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
        DataFormat df = workbook.createDataFormat();
        dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd hh:mm:ss"));
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
        // Write headers first if not done and we have headers defined
        if (!headersWritten && !headers.isEmpty()) {
            writeHeaders();
        }

        Row row = sheet.createRow(currentRow++);

        if (headers.isEmpty()) {
            // No predefined headers - use keys from the hash
            int col = 0;
            for (Object key : data.keySet()) {
                if (!headersWritten) {
                    headers.add(key.toString());
                }
                Cell cell = row.createCell(col++);
                setCellValue(cell, data.get(key));
            }
            // Write headers on first row if we collected them
            if (!headersWritten && !headers.isEmpty()) {
                // Shift all data down by one row
                sheet.shiftRows(0, currentRow - 1, 1);
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    headerRow.createCell(i).setCellValue(headers.get(i));
                }
                currentRow++;
                headersWritten = true;
            }
        } else {
            // Use predefined headers
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.createCell(i);
                Object value = data.get(headers.get(i));
                setCellValue(cell, value);
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
