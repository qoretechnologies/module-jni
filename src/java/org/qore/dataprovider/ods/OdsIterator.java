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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.Collections;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.zone.ZoneRulesException;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading ODS (OpenDocument Spreadsheet) files.
 *
 * Rows are read from the document's content.xml as they are iterated (see OdsSheetReader), so a sheet of any number
 * of rows is read in bounded memory.  Each iteration (and reading the header row) is a pass over the sheet from its
 * first row; when an iteration ends, the next one starts again from the first data row.
 *
 * Implements Closeable to ensure proper resource cleanup.
 */
public class OdsIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    // the document; null once closed
    private OdsSheetReader reader;
    // the current pass over the rows of the sheet, if any
    private RowSeeker rows;
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
    private boolean ignore_empty = false;
    // the number of columns of the table, as ODFDOM gives it
    private final int cachedColCount;

    public OdsIterator(java.io.InputStream stream, String sheet_name) throws Throwable {
        this(openReader(stream, sheet_name));
    }

    public OdsIterator(qore.Qore.InputStream stream, String sheet_name) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), sheet_name);
    }

    public OdsIterator(String path, String sheet_name) throws Throwable {
        this(OdsSheetReader.open(new File(path), sheet_name));
    }

    private OdsIterator(OdsSheetReader reader) throws Throwable {
        this.reader = reader;
        cachedColCount = reader.getColumnCount();
    }

    /**
     * Opens the sheet of a spreadsheet in an input stream; the stream is closed if this fails
     */
    private static OdsSheetReader openReader(java.io.InputStream stream, String sheet_name) throws Throwable {
        try {
            return OdsSheetReader.open(stream, sheet_name);
        } catch (Throwable t) {
            try {
                stream.close();
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
    }

    /**
     * Sets whether empty rows are skipped; if false (the default), iteration stops at the first empty row.
     */
    public void setIgnoreEmpty(boolean ignore_empty) {
        this.ignore_empty = ignore_empty;
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
        // the header row is read in a pass of its own
        try (RowSeeker header_rows = new RowSeeker(getReader().openRows())) {
            OdsSheetReader.RowRun row = header_rows.getRun(row_start - 1);
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
            while (cell_no < cachedColCount) {
                OdsCell cell = row.getCell(cell_no);
                String valueType = cell == null ? null : cell.getValueType();
                if (valueType == null || valueType.isEmpty()) {
                    // Check if the cell has any text content
                    String text = cell == null ? null : cell.getDisplayText();
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
        getReader();
        if (current_row == -1) {
            // each iteration is a new pass from the first data row
            closeRows();
            if (start_row == -1) {
                // rows are 1-based; with no header row, data starts on the first row
                current_row = header_row_end != -1 ? header_row_end + 1 : 1;
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
            if (row_data == null && ignore_empty) {
                // skip empty rows up to the end of the data range or the last row with content
                while (row_data == null) {
                    // jump over runs of empty rows without visiting each row in them
                    int next_row = getNextRowWithContent(current_row);
                    if (next_row == -1 || (end_row != -1 && next_row > end_row)) {
                        break;
                    }
                    current_row = next_row;
                    row_data = getRowData(current_row);
                }
            }
            if (row_data == null) {
                current_row = -1;
            } else {
                ++count;
            }
        } else if (row_data != null) {
            row_data = null;
        }
        if (current_row == -1) {
            // release the pass as soon as the iteration ends
            closeRows();
        }
        return current_row != -1;
    }

    /**
     * Returns the document; throws an exception if the iterator is closed
     */
    private OdsSheetReader getReader() {
        OdsSheetReader r = reader;
        if (r == null) {
            throw new IllegalStateException("the ODS iterator is closed");
        }
        return r;
    }

    /**
     * Returns the row run holding the given 0-based row in the current pass, starting a new pass if necessary, or
     * null if the row is past the end of the table
     */
    private OdsSheetReader.RowRun getRun(int row_idx) {
        try {
            if (rows == null || row_idx < rows.getLastIndex()) {
                closeRows();
                rows = new RowSeeker(getReader().openRows());
            }
            return rows.getRun(row_idx);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the 1-based number of the first row after the given 1-based row in a row element with content, or -1
     * if there is none
     */
    private int getNextRowWithContent(int row) {
        try {
            // the 0-based index of the next row is the given 1-based row number
            for (OdsSheetReader.RowRun run = getRun(row); run != null; run = rows.nextRun()) {
                if (run.has_content) {
                    return Math.max(row, run.start) + 1;
                }
            }
            return -1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void closeRows() {
        if (rows != null) {
            RowSeeker r = rows;
            rows = null;
            try {
                r.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
        // our API uses 1-based rows
        int rowIdx = rownum - 1;
        if (rowIdx < 0) {
            return null;
        }
        OdsSheetReader.RowRun row = getRun(rowIdx);
        if (row == null) {
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
        } else if (!auto_detect_data && headers.isEmpty()) {
            // a data range without an end column and without headers ends at the last cell with a value in the row
            end_cell = getLastValueColumn(row, cell_no);
            if (end_cell == -1) {
                return null;
            }
        }
        int col_no = 0;
        boolean found_data = false;
        Hash row_data = null;
        while (true) {
            if (cell_no >= cachedColCount) {
                break;
            }
            OdsCell cell = row.getCell(cell_no);
            Object val;

            // a row element can have fewer cells than the table has columns, in which case there is no cell
            if (isEmpty(cell)) {
                if (auto_detect_data) {
                    break;
                }
                val = null;
            } else {
                val = getCellValue(cell);
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

    /**
     * Returns true if there is no cell or the cell has neither a value type nor text
     */
    private static boolean isEmpty(OdsCell cell) {
        if (cell == null) {
            return true;
        }
        String valueType = cell.getValueType();
        if (valueType != null && !valueType.isEmpty()) {
            return false;
        }
        String display = cell.getDisplayText();
        return display == null || display.trim().isEmpty();
    }

    /**
     * Returns the value of a cell that is not empty, or null if it has none
     */
    private Object getCellValue(OdsCell cell) {
        Object val = cellToValue(cell);
        if (val == null) {
            // Cell has a valueType but cellToValue didn't handle it - use display text
            String display = cell.getDisplayText();
            if (display != null && !display.trim().isEmpty()) {
                val = display.trim();
            }
        }
        return val;
    }

    /**
     * Returns the last 0-based column from the given column, within the columns of the table, with a cell with a
     * value in the row, or -1 if there is none
     */
    private int getLastValueColumn(OdsSheetReader.RowRun row, int first_col) {
        for (int i = row.getCellRunCount() - 1; i >= 0; --i) {
            OdsCell cell = row.getCellRunCell(i);
            int start = row.getCellRunStart(i);
            int last = Math.min(start + row.getCellRunColumns(i), cachedColCount) - 1;
            if (last < first_col) {
                return -1;
            }
            if (last < start || isEmpty(cell) || getCellValue(cell) == null) {
                continue;
            }
            return last;
        }
        return -1;
    }

    private Object cellToValue(OdsCell cell) {
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

            case "date":
                // the clock time written in the file, in the reader's zone
                return cell.getDateValue(zone);

            case "time":
                return cell.getTimeValue(zone);

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
        return OdsSheetReader.getSheetNames(stream);
    }

    public static ArrayList<String> getWorksheets(String path) throws Exception {
        return OdsSheetReader.getSheetNames(new File(path));
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
        Throwable error = null;
        try {
            closeRows();
        } catch (Throwable t) {
            error = t;
        }
        if (reader != null) {
            OdsSheetReader r = reader;
            reader = null;
            try {
                r.close();
            } catch (Throwable t) {
                if (error == null) {
                    error = t;
                } else {
                    error.addSuppressed(t);
                }
            }
        }
        if (error != null) {
            if (error instanceof UncheckedIOException) {
                throw ((UncheckedIOException)error).getCause();
            }
            if (error instanceof IOException) {
                throw (IOException)error;
            }
            if (error instanceof RuntimeException) {
                throw (RuntimeException)error;
            }
            if (error instanceof Error) {
                throw (Error)error;
            }
            throw new IOException(error);
        }
    }

    /**
     * Locates the row runs of one pass by 0-based row number, which must not decrease
     */
    private static final class RowSeeker implements java.io.Closeable {
        private final OdsSheetReader.RowPass pass;
        // the current run; null before the first one is read and at the end of the table
        private OdsSheetReader.RowRun run = null;
        private boolean started = false;
        // the last row number requested
        private int last_index = -1;

        RowSeeker(OdsSheetReader.RowPass pass) {
            this.pass = pass;
        }

        int getLastIndex() {
            return last_index;
        }

        /**
         * Returns the run holding the given 0-based row, or null if the row is past the end of the table
         */
        OdsSheetReader.RowRun getRun(int idx) throws IOException {
            if (idx < 0) {
                return null;
            }
            last_index = idx;
            if (!started) {
                started = true;
                run = pass.next();
            }
            // the runs cover the rows of the table without gaps
            while (run != null && idx >= run.start + run.count) {
                run = pass.next();
            }
            return run;
        }

        /**
         * Returns the run after the current one, or null at the end of the table
         */
        OdsSheetReader.RowRun nextRun() throws IOException {
            if (run != null) {
                run = pass.next();
                // the rows before the new run can no longer be located in this pass
                if (run != null && run.start > last_index) {
                    last_index = run.start;
                }
            }
            return run;
        }

        @Override
        public void close() throws IOException {
            pass.close();
        }
    }
}
