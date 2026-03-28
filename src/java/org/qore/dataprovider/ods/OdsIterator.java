/*  OdsIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.zone.ZoneRulesException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading ODS (OpenDocument Spreadsheet) files.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class OdsIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private OdfSpreadsheetDocument document;
    private OdfTable table;
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
    // Cached table dimensions - must be saved before any getCellByPosition() call
    // because ODFDOM auto-expands tables when accessing non-existent cells
    private int cachedRowCount = 0;
    private int cachedColCount = 0;

    public OdsIterator(java.io.InputStream stream, String sheet_name) throws Throwable {
        try {
            document = OdfSpreadsheetDocument.loadDocument(stream);
        } catch (Throwable t) {
            stream.close();
            throw t;
        }
        selectTable(sheet_name);
    }

    public OdsIterator(qore.Qore.InputStream stream, String sheet_name) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), sheet_name);
    }

    public OdsIterator(String path, String sheet_name) throws Throwable {
        this(new FileInputStream(new File(path)), sheet_name);
    }

    private void selectTable(String sheet_name) {
        List<OdfTable> tables = document.getTableList();
        if (tables == null || tables.isEmpty()) {
            throw new RuntimeException("the spreadsheet has no worksheets");
        }
        if (sheet_name == null || sheet_name.isEmpty()) {
            table = tables.get(0);
        } else {
            // Try by name first
            table = document.getTableByName(sheet_name);
            if (table == null) {
                // Try by index
                try {
                    int sheet_no = Integer.parseInt(sheet_name);
                    if (sheet_no >= 0 && sheet_no < tables.size()) {
                        table = tables.get(sheet_no);
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            if (table == null) {
                throw new RuntimeException(String.format("sheet %s is unknown", sheet_name));
            }
        }
        // Cache dimensions immediately - before any getCellByPosition() which auto-expands tables
        cachedRowCount = table.getRowCount();
        cachedColCount = table.getColumnCount();
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
        OdfTableRow row = table.getRowByIndex(row_start - 1);
        if (row == null) {
            return;
        }

        int cell_no = 0;
        if (!col_start.equals("-")) {
            cell_no = colStringToIndex(col_start);
        }
        int end_cell = -1;
        if (!col_end.equals("-")) {
            end_cell = colStringToIndex(col_end);
        }
        // Use cached column count; getCellByPosition() auto-expands tables in ODFDOM
        while (cell_no < cachedColCount) {
            OdfTableCell cell = table.getCellByPosition(cell_no, row_start - 1);
            String valueType = cell.getValueType();
            if (valueType == null || valueType.isEmpty()) {
                // Check if the cell has any text content
                String text = cell.getDisplayText();
                if (text == null || text.trim().isEmpty()) {
                    break;
                }
                headers.add(text.trim());
            } else {
                switch (valueType) {
                    case "string":
                        headers.add(cell.getStringValue() != null ? cell.getStringValue().trim() : "");
                        break;
                    case "float":
                    case "currency":
                    case "percentage":
                        headers.add(String.format("%s", cell.getDoubleValue()));
                        break;
                    case "boolean":
                        headers.add(cell.getBooleanValue() ? "true" : "false");
                        break;
                    default:
                        String display = cell.getDisplayText();
                        if (display != null && !display.trim().isEmpty()) {
                            headers.add(display.trim());
                        } else {
                            headers.add(String.format("column-%d-unknown", cell_no + 1));
                        }
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
        // OdfTable uses 0-based indexing, our API uses 1-based
        int rowIdx = rownum - 1;
        if (rowIdx < 0 || rowIdx >= cachedRowCount) {
            return null;
        }

        int cell_no = 0;
        boolean auto_detect_data;
        if (!start_column.equals("-")) {
            cell_no = colStringToIndex(start_column);
            auto_detect_data = false;
        } else {
            auto_detect_data = headers.isEmpty();
        }
        int end_cell = -1;
        if (!end_column.equals("-")) {
            end_cell = colStringToIndex(end_column);
        }
        int col_no = 0;
        boolean found_data = false;
        Hash row_data = null;
        // Use cached column count; ODFDOM's getCellByPosition() auto-expands tables
        while (true) {
            if (cell_no >= cachedColCount) {
                break;
            }
            OdfTableCell cell = table.getCellByPosition(cell_no, rowIdx);
            Object val;

            // In ODFDOM, getCellByPosition never returns null - check for truly empty cells
            String valueType = cell.getValueType();
            boolean cellEmpty = (valueType == null || valueType.isEmpty());
            if (cellEmpty) {
                String display = cell.getDisplayText();
                cellEmpty = (display == null || display.trim().isEmpty());
            }

            if (cellEmpty) {
                if (auto_detect_data) {
                    break;
                }
                val = null;
            } else {
                val = cellToValue(cell);
                if (val == null) {
                    // Cell has a valueType but cellToValue didn't handle it - use display text
                    String display = cell.getDisplayText();
                    if (display != null && !display.trim().isEmpty()) {
                        val = display.trim();
                    }
                }
                if (val != null && !found_data) {
                    found_data = true;
                }
            }

            String key;
            if (!headers.isEmpty()) {
                if (col_no >= headers.size()) {
                    break;
                }
                key = headers.get(col_no);
            } else {
                key = String.format("%s%d", colIndexToString(col_no), rownum);
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

    private Object cellToValue(OdfTableCell cell) {
        String valueType = cell.getValueType();
        if (valueType == null || valueType.isEmpty()) {
            return null;
        }

        switch (valueType) {
            case "string":
                return cell.getStringValue();

            case "float":
            case "currency":
            case "percentage": {
                Double val = cell.getDoubleValue();
                return val != null ? val : null;
            }

            case "boolean":
                return cell.getBooleanValue();

            case "date": {
                Calendar cal = cell.getDateValue();
                if (cal != null) {
                    try {
                        Instant instant = cal.toInstant();
                        return ZonedDateTime.ofInstant(instant, zone);
                    } catch (Exception e) {
                        // Fall back to display text
                        return cell.getDisplayText();
                    }
                }
                return null;
            }

            case "time": {
                Calendar cal = cell.getTimeValue();
                if (cal != null) {
                    try {
                        Instant instant = cal.toInstant();
                        return ZonedDateTime.ofInstant(instant, zone);
                    } catch (Exception e) {
                        return cell.getDisplayText();
                    }
                }
                return null;
            }

            default:
                break;
        }

        return null;
    }

    /**
     * Returns the list of worksheet names.
     */
    public static ArrayList<String> getWorksheets(qore.Qore.InputStream stream) throws Exception {
        return getWorksheets(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    public static ArrayList<String> getWorksheets(java.io.InputStream stream) throws Exception {
        try (OdsDocHolder holder = new OdsDocHolder(stream)) {
            ArrayList<String> rv = new ArrayList<String>();
            List<OdfTable> tables = holder.doc.getTableList();
            if (tables != null) {
                for (OdfTable t : tables) {
                    rv.add(t.getTableName());
                }
            }
            return rv;
        }
    }

    public static ArrayList<String> getWorksheets(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return getWorksheets(fis);
        }
    }

    /**
     * Converts a column string (e.g., "A", "B", "AA") to a 0-based index.
     */
    private static int colStringToIndex(String col) {
        col = col.toUpperCase();
        int result = 0;
        for (int i = 0; i < col.length(); ++i) {
            result = result * 26 + (col.charAt(i) - 'A' + 1);
        }
        return result - 1;
    }

    /**
     * Converts a 0-based column index to a column string (e.g., 0 -> "A").
     */
    private static String colIndexToString(int index) {
        StringBuilder sb = new StringBuilder();
        while (index >= 0) {
            sb.insert(0, (char) ('A' + (index % 26)));
            index = index / 26 - 1;
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        if (document != null) {
            document.close();
            document = null;
        }
    }

    /**
     * Helper class to hold a document for auto-closing in getWorksheets.
     */
    private static class OdsDocHolder implements AutoCloseable {
        OdfSpreadsheetDocument doc;

        OdsDocHolder(java.io.InputStream stream) throws Exception {
            doc = OdfSpreadsheetDocument.loadDocument(stream);
        }

        @Override
        public void close() {
            if (doc != null) {
                doc.close();
                doc = null;
            }
        }
    }
}
