/*  ExcelIterator.java Copyright 2021 - 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.Collections;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.zone.ZoneRulesException;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Excel files (both .xlsx and .xls formats).
 * Implements Closeable to ensure proper resource cleanup.
 */
public class ExcelIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private Workbook workbook;
    private Sheet sheet;
    private ArrayList<String> headers = new ArrayList<String>();
    private ZoneId zone = ZoneId.systemDefault();
    private int header_row_end = -1;
    private int current_row = -1;
    private int start_row = -1;
    private int end_row = -1;
    private String start_column = "-";
    private String end_column = "-";
    private Hash row_data = null;
    private long count = 0;

    public ExcelIterator(java.io.InputStream stream, String sheet_name) throws Throwable {
        // WorkbookFactory.create() auto-detects the format (XSSF for .xlsx, HSSF for .xls)
        workbook = WorkbookFactory.create(stream);
        if (sheet_name == null || sheet_name.isEmpty()) {
            sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("the spreadsheet has no worksheets");
            }
        } else {
            sheet = workbook.getSheet(sheet_name);
            if (sheet == null) {
                try {
                    int sheet_no = Integer.parseInt(sheet_name);
                    sheet = workbook.getSheetAt(sheet_no);
                } catch (NumberFormatException e) {
                    // ignore exception
                }
            }
            if (sheet == null) {
                throw new RuntimeException(String.format("sheet %s is unknown", sheet_name));
            }
        }
    }

    public ExcelIterator(qore.Qore.InputStream stream, String sheet_name) throws Throwable {
        this(new QoreInputStreamWrapper(stream), sheet_name);
    }

    public ExcelIterator(String path, String sheet_name) throws Throwable {
        this(new FileInputStream(new File(path)), sheet_name);
    }

    public void setZone(String zonestr) throws DateTimeException, ZoneRulesException {
        zone = ZoneId.of(zonestr);
    }

    public ArrayList<String> getHeaders() {
        return headers;
    }

    public void setHeadersToLower() {
        for (int i = 0; i < headers.size(); ++i) {
            headers.set(i, headers.get(i).toLowerCase());
        }
    }

    public void setHeaders(ArrayList<String> headers) {
        this.headers = headers;
    }

    public void setHeaders(String[] headers) {
        Collections.addAll(this.headers, headers);
    }

    public void setHeaderCells(String col_start, int row_start, String col_end, int row_end) {
        header_row_end = row_end == -1 ? row_start : row_end;
        Row row = sheet.getRow(row_start - 1);
        if (row == null) {
            return;
        }

        // get start cell
        int cell_no = 0;
        if (!col_start.equals("-")) {
            cell_no = CellReference.convertColStringToIndex(col_start);
        }
        int end_cell = -1;
        if (!col_end.equals("-")) {
            end_cell = CellReference.convertColStringToIndex(col_end);
        }
        while (true) {
            Cell cell = row.getCell(cell_no);
            if (cell == null) {
                break;
            }
            CellType type = cell.getCellType();
            if (type == CellType.BLANK) {
                headers.add("BLANK");
            } else {
                if (type == CellType.FORMULA) {
                    type = cell.getCachedFormulaResultType();
                }
                switch (type) {
                    case BOOLEAN:
                        headers.add(cell.getBooleanCellValue() ? "true" : "false");
                        break;

                    case ERROR:
                        headers.add("ERROR");
                        break;

                    case NUMERIC:
                        headers.add(String.format("%s", cell.getNumericCellValue()));
                        break;

                    case STRING: {
                        headers.add(cell.getStringCellValue().trim());
                        break;
                    }

                    default:
                        headers.add(String.format("column-%d-unknown", cell_no + 1));
                        break;
                }
            }

            if (end_cell >= 0 && (cell_no >= end_cell)) {
                break;
            }
            ++cell_no;
        }
    }

    public void setDataCells(String col_start, int row_start, String col_end, int row_end) {
        start_column = col_start;
        start_row = row_start;
        end_column = col_end;
        end_row = row_end;
    }

    public long getCount() {
        return count;
    }

    public boolean next() {
        if (current_row == -1) {
            if (start_row == -1) {
                if (header_row_end != -1) {
                    current_row = header_row_end + 1;
                } else {
                    ++current_row;
                }
            } else {
                current_row = start_row;
            }
        } else {
            ++current_row;
        }
        if (current_row != -1 && end_row != -1 && current_row > end_row) {
            current_row = -1;
        }
        if (current_row != -1) {
            row_data = getRowData(current_row);
            if (row_data == null) {
                current_row = -1;
            } else {
                ++count;
            }
        } else if (row_data != null) {
            row_data = null;
        }
        return current_row != -1;
    }

    public Hash getValue() throws QoreException {
        if (row_data == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return row_data;
    }

    public boolean valid() {
        return row_data != null;
    }

    private Hash getRowData(int rownum) {
        Row row = sheet.getRow(rownum - 1);
        if (row == null) {
            return null;
        }

        // get start cell
        int cell_no = 0;
        boolean auto_detect_data;
        if (!start_column.equals("-")) {
            cell_no = CellReference.convertColStringToIndex(start_column);
            auto_detect_data = false;
        } else {
            auto_detect_data = headers.isEmpty() ? true : false;
        }
        int end_cell = -1;
        if (!end_column.equals("-")) {
            end_cell = CellReference.convertColStringToIndex(end_column);
        }
        int col_no = 0;
        boolean found_data = false;
        Hash row_data = null;
        while (true) {
            Cell cell = row.getCell(cell_no);
            Object val;

            if (cell == null) {
                if (auto_detect_data) {
                    break;
                }
                val = null;
            } else {
                val = cellToValue(cell);
                if (val != null && !found_data) {
                    found_data = true;
                }
            }

            String key;
            if (!headers.isEmpty()) {
                key = headers.get(col_no);
            } else {
                key = String.format("%s%d", CellReference.convertNumToColString(col_no), rownum);
            }

            if (row_data == null) {
                row_data = new Hash();
            }
            row_data.put(key, val);

            if (end_cell >= 0) {
                if (cell_no >= end_cell) {
                    break;
                }
            } else if (!headers.isEmpty() && (col_no >= (headers.size() - 1))) {
                break;
            }
            ++cell_no;
            ++col_no;
        }
        if (!found_data) {
            return null;
        }
        return row_data;
    }

    private Object cellToValue(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.BLANK) {
            return null;
        }
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        switch (type) {
            case BOOLEAN:
                return cell.getBooleanCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return ZonedDateTime.of(cell.getLocalDateTimeCellValue(), zone);
                }
                return cell.getNumericCellValue();

            case STRING: {
                return cell.getStringCellValue();
            }

            default:
                break;
        }

        return null;
    }

    public static ArrayList<String> getWorksheets(qore.Qore.InputStream stream) throws IOException {
        return getWorksheets(new QoreInputStreamWrapper(stream));
    }

    public static ArrayList<String> getWorksheets(java.io.InputStream stream) throws IOException {
        try (Workbook wb = WorkbookFactory.create(stream)) {
            ArrayList<String> rv = new ArrayList<String>();
            for (int i = 0; i < wb.getNumberOfSheets(); ++i) {
                rv.add(wb.getSheetName(i));
            }
            return rv;
        }
    }

    public static ArrayList<String> getWorksheets(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getWorksheets(fis);
        }
    }

    /**
     * Closes the workbook and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (workbook != null) {
            workbook.close();
            workbook = null;
        }
    }
}

class QoreInputStreamWrapper extends java.io.InputStream {
    private qore.Qore.InputStream stream;

    public QoreInputStreamWrapper(qore.Qore.InputStream stream) {
        this.stream = stream;
    }

    public int read() throws IOException {
        byte[] data;
        try {
            data = stream.read(1);
        } catch (Throwable e) {
            throw new IOException(e);
        }
        if (data == null) {
            return -1;
        }
        return data[0];
    }

    public int read(byte[] b, int off, int len) throws IOException {
        byte[] data;
        try {
            data = stream.read(len);
        } catch (Throwable e) {
            throw new IOException(e);
        }
        if (data == null) {
            return -1;
        }
        System.arraycopy(data, 0, b, off, data.length);
        return data.length;
    }

    public long skip(long n) throws IOException {
        byte[] data;
        try {
            data = stream.read(n);
        } catch (Throwable e) {
            throw new IOException(e);
        }
        if (data == null) {
            return 0;
        }
        return data.length;
    }

    public int available() throws IOException {
        return 1;
    }
}
