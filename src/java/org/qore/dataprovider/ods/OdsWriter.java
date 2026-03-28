/*  OdsWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Closeable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Helper class for writing ODS (OpenDocument Spreadsheet) files from Qore.
 */
public class OdsWriter implements Closeable {
    private OdfSpreadsheetDocument document;
    private OdfTable table;
    private ArrayList<String> headers = new ArrayList<String>();
    private int currentRow = 0;
    private boolean headersWritten = false;
    private String sheetName;

    /**
     * Creates a new OdsWriter.
     *
     * @param sheet_name The name of the worksheet to create
     */
    public OdsWriter(String sheet_name) {
        this.sheetName = sheet_name != null && !sheet_name.isEmpty() ? sheet_name : "Sheet1";
        try {
            document = OdfSpreadsheetDocument.newSpreadsheetDocument();
            // Remove the default empty table and create our own
            java.util.List<OdfTable> tables = document.getTableList();
            if (tables != null && !tables.isEmpty()) {
                // Rename the first table
                table = tables.get(0);
                table.setTableName(this.sheetName);
            } else {
                table = OdfTable.newTable(document);
                table.setTableName(this.sheetName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ODS document: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new OdsWriter with default settings.
     */
    public OdsWriter() {
        this("Sheet1");
    }

    /**
     * Sets the headers for the worksheet.
     */
    public void setHeaders(String[] headers) {
        this.headers.clear();
        for (String h : headers) {
            this.headers.add(h);
        }
    }

    /**
     * Sets the headers for the worksheet.
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
        for (int i = 0; i < headers.size(); i++) {
            OdfTableCell cell = table.getCellByPosition(i, currentRow);
            cell.setStringValue(headers.get(i));
        }
        currentRow++;
        headersWritten = true;
    }

    /**
     * Writes a data row from a hash.
     */
    public void writeRow(Hash data) {
        if (!headersWritten && !headers.isEmpty()) {
            writeHeaders();
        }

        if (headers.isEmpty()) {
            // No predefined headers - use keys from the hash
            int col = 0;
            for (Object key : data.keySet()) {
                if (!headersWritten) {
                    headers.add(key.toString());
                }
                OdfTableCell cell = table.getCellByPosition(col++, currentRow);
                setCellValue(cell, data.get(key));
            }
            // Write headers on first row if we collected them
            if (!headersWritten && !headers.isEmpty()) {
                // Move current data down - we need to insert headers at row 0
                // Since OdfTable doesn't support shiftRows, we write headers first next time
                // Actually for first row, let's just write headers at row 0 and data at row 1
                // We need to shift: copy row currentRow to currentRow+1, then write headers at row 0
                for (int i = 0; i < headers.size(); i++) {
                    // Copy data from row 0 to row 1
                    OdfTableCell srcCell = table.getCellByPosition(i, 0);
                    OdfTableCell destCell = table.getCellByPosition(i, 1);
                    copyCell(srcCell, destCell);
                    // Write header at row 0
                    srcCell.setStringValue(headers.get(i));
                }
                currentRow++;
                headersWritten = true;
            }
        } else {
            // Use predefined headers
            for (int i = 0; i < headers.size(); i++) {
                OdfTableCell cell = table.getCellByPosition(i, currentRow);
                Object value = data.get(headers.get(i));
                setCellValue(cell, value);
            }
        }
        currentRow++;
    }

    /**
     * Copies cell value from one cell to another.
     */
    private void copyCell(OdfTableCell src, OdfTableCell dest) {
        String valueType = src.getValueType();
        if (valueType == null) {
            return;
        }
        switch (valueType) {
            case "string":
                dest.setStringValue(src.getStringValue());
                break;
            case "float":
                dest.setDoubleValue(src.getDoubleValue());
                break;
            case "boolean":
                dest.setBooleanValue(src.getBooleanValue());
                break;
            default:
                dest.setStringValue(src.getDisplayText());
                break;
        }
    }

    /**
     * Sets the cell value based on the object type.
     */
    private void setCellValue(OdfTableCell cell, Object value) {
        if (value == null) {
            cell.setStringValue("");
        } else if (value instanceof Boolean) {
            cell.setBooleanValue((Boolean) value);
        } else if (value instanceof Number) {
            cell.setDoubleValue(((Number) value).doubleValue());
        } else if (value instanceof ZonedDateTime) {
            ZonedDateTime zdt = (ZonedDateTime) value;
            GregorianCalendar cal = GregorianCalendar.from(zdt);
            cell.setDateValue(cal);
        } else if (value instanceof Date) {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime((Date) value);
            cell.setDateValue(cal);
        } else {
            cell.setStringValue(value.toString());
        }
    }

    /**
     * Writes the document to a file.
     */
    public void writeToFile(String path) throws Exception {
        document.save(path);
    }

    /**
     * Writes the document to an output stream.
     */
    public void writeToStream(OutputStream stream) throws Exception {
        document.save(stream);
    }

    /**
     * Writes the document to a Qore output stream.
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws Exception {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the document contents as bytes.
     */
    public byte[] getBytes() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Gets the current row count (including header if written).
     */
    public int getRowCount() {
        return currentRow;
    }

    @Override
    public void close() throws IOException {
        if (document != null) {
            document.close();
            document = null;
        }
    }
}
