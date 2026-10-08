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

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;

import java.lang.ref.Cleaner;

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
 *
 * Rows are read from the file as they are iterated, so a worksheet of any number of rows is read in bounded memory:
 * an .xlsx worksheet is streamed (see XlsxSheetSource); an .xls workbook, whose format holds at most 65536 rows, is
 * loaded with the POI usermodel (see WorkbookSheetSource).  Each iteration (and reading the header row) is a pass over
 * the worksheet from its first row; when an iteration ends, the next one starts again from the first data row.
 *
 * Implements Closeable to release the workbook; an iterator that is not closed releases it when it is garbage
 * collected.
 */
public class ExcelIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    // closes the workbooks of iterators that are garbage collected without being closed
    private static final Cleaner CLEANER = Cleaner.create();

    private final Resources res;
    private final Cleaner.Cleanable cleanable;
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
    // the name of the field holding the formulas of each record's cells; null if formulas are not returned
    private String formula_field = null;

    public ExcelIterator(java.io.InputStream stream, String sheet_name) throws Throwable {
        this(openSource(stream, sheet_name));
    }

    public ExcelIterator(qore.Qore.InputStream stream, String sheet_name) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), sheet_name);
    }

    public ExcelIterator(String path, String sheet_name) throws Throwable {
        this(openSource(new File(path), sheet_name));
    }

    private ExcelIterator(SheetSource source) throws Throwable {
        res = new Resources(source);
        Cleaner.Cleanable c;
        try {
            c = CLEANER.register(this, res);
        } catch (RuntimeException | Error t) {
            res.run();
            throw t;
        }
        cleanable = c;
    }

    /**
     * Opens the worksheet of a workbook in an input stream; the stream is closed if this fails
     */
    private static SheetSource openSource(java.io.InputStream stream, String sheet_name) throws Throwable {
        try {
            InputStream is = FileMagic.prepareToCheckMagic(stream);
            if (FileMagic.valueOf(is) == FileMagic.OOXML) {
                return XlsxSheetSource.open(is, sheet_name);
            }
            // WorkbookFactory.create() auto-detects the binary formats (HSSF for .xls)
            return new WorkbookSheetSource(WorkbookFactory.create(is), sheet_name);
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
     * Opens the worksheet of a workbook file
     */
    private static SheetSource openSource(File file, String sheet_name) throws Throwable {
        FileMagic magic;
        try (FileInputStream fis = new FileInputStream(file)) {
            magic = FileMagic.valueOf(FileMagic.prepareToCheckMagic(fis));
        }
        if (magic == FileMagic.OOXML) {
            return XlsxSheetSource.open(file, sheet_name);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return new WorkbookSheetSource(WorkbookFactory.create(fis), sheet_name);
        }
    }

    /**
     * Sets whether empty rows are skipped; if false (the default), iteration stops at the first empty row.
     */
    public void setIgnoreEmpty(boolean ignore_empty) {
        this.ignore_empty = ignore_empty;
    }

    /**
     * Returns the formulas of the cells of each record in a field with the given name: a hash of the formula text,
     * as POI's Cell.getCellFormula() gives it, by the cell's A1 reference, or null if none of the record's cells has
     * a formula
     *
     * Call this after the headers are set; the name must not be one of the headers.
     *
     * @param name the name of the field
     *
     * @throws QoreException EXCEL-FORMULA-FIELD-ERROR if the name is empty or one of the headers
     */
    public void setFormulaField(String name) throws QoreException {
        if (name == null || name.isEmpty()) {
            throw new QoreException("EXCEL-FORMULA-FIELD-ERROR", "the formula field name must not be empty");
        }
        if (headers.contains(name)) {
            throw new QoreException("EXCEL-FORMULA-FIELD-ERROR", String.format("the formula field name '%s' is "
                + "the name of a column of the worksheet; choose a name that is not a header: %s", name, headers));
        }
        getSource().enableFormulas();
        formula_field = name;
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
        try (RowSeeker rows = new RowSeeker(getSource().openRows())) {
            SheetSource.Row row = rows.getRow(row_start - 1);
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
                SheetSource.Cell cell = row.getCell(cell_no);
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
        getSource();
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
                // skip empty rows up to the end of the data range or the last physical row of the sheet; the rows
                // between two physical rows are empty, so the search continues at the next physical row
                while (row_data == null) {
                    SheetSource.Row next = getNextRow(current_row);
                    if (next == null) {
                        break;
                    }
                    int next_row = next.getRowNum() + 1;
                    if (end_row != -1 && next_row > end_row) {
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

    /**
     * Returns the source of the rows; throws an exception if the iterator is closed
     */
    private SheetSource getSource() {
        SheetSource source = res.source;
        if (source == null) {
            throw new IllegalStateException("the Excel iterator is closed");
        }
        return source;
    }

    /**
     * Returns the physical row with the given 1-based number in the current pass, starting a new pass if necessary,
     * or null if the row is not in the file
     */
    private SheetSource.Row getRow(int rownum) {
        try {
            if (res.rows == null || rownum - 1 < res.rows.getLastIndex()) {
                closeRows();
                res.rows = new RowSeeker(getSource().openRows());
            }
            return res.rows.getRow(rownum - 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the first physical row after the given 1-based row number in the current pass, or null if there are
     * no more rows
     */
    private SheetSource.Row getNextRow(int rownum) {
        try {
            return res.rows.getNextRow(rownum - 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void closeRows() {
        try {
            res.closeRows();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Hash getRowData(int rownum) {
        SheetSource.Row row = getRow(rownum);
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
        // the formulas of the record's cells by A1 reference, if formulas are returned
        Hash formulas = null;
        while (true) {
            SheetSource.Cell cell = row.getCell(cell_no);
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
                // a data range wider than the headers ends at the last header column
                if (col_no >= headers.size()) {
                    break;
                }
                key = headers.get(col_no);
            } else {
                key = String.format("%s%d", CellReference.convertNumToColString(col_no), rownum);
            }

            if (row_data == null) {
                row_data = new Hash();
            }
            row_data.put(key, val);

            if (formula_field != null && cell != null) {
                String formula = cell.getCellFormula();
                if (formula != null) {
                    if (formulas == null) {
                        formulas = new Hash();
                    }
                    // the reference as ExcelGridReader gives it, so a record's formulas match the sheet's profile
                    formulas.put(new CellReference(rownum - 1, cell_no).formatAsString(), formula);
                }
            }

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
        if (formula_field != null) {
            if (row_data.containsKey(formula_field)) {
                throw new QoreException("EXCEL-FORMULA-FIELD-ERROR", String.format("the formula field name '%s' "
                    + "is the name of a field of the record of row %d; choose another name", formula_field,
                    rownum));
            }
            row_data.put(formula_field, formulas);
        }
        return row_data;
    }

    /**
     * Returns the last 0-based column from the given column with a cell with a value in the row, or -1 if there is
     * none
     */
    private int getLastValueColumn(SheetSource.Row row, int first_col) {
        for (int col = row.getLastCellNum() - 1; col >= first_col; --col) {
            SheetSource.Cell cell = row.getCell(col);
            if (cell != null && cellToValue(cell) != null) {
                return col;
            }
        }
        return -1;
    }

    private Object cellToValue(SheetSource.Cell cell) {
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
                if (cell.isCellDateFormatted()) {
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
        return getWorksheets(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    public static ArrayList<String> getWorksheets(java.io.InputStream stream) throws IOException {
        InputStream is = FileMagic.prepareToCheckMagic(stream);
        if (FileMagic.valueOf(is) == FileMagic.OOXML) {
            try {
                return XlsxSheetSource.getSheetNames(is);
            } catch (InvalidFormatException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
        try (Workbook wb = WorkbookFactory.create(is)) {
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
        try {
            res.close();
        } finally {
            cleanable.clean();
        }
    }

    /**
     * The resources of an iterator: the workbook and the current pass over its rows.
     *
     * This does not refer to the iterator, so it can close the resources of an iterator that is garbage collected
     * without being closed.
     */
    private static final class Resources implements Runnable {
        SheetSource source;
        // the current data pass, if any
        RowSeeker rows;

        Resources(SheetSource source) {
            this.source = source;
        }

        void closeRows() throws IOException {
            if (rows != null) {
                RowSeeker r = rows;
                rows = null;
                r.close();
            }
        }

        void close() throws IOException {
            Throwable error = null;
            try {
                closeRows();
            } catch (Throwable t) {
                error = t;
            }
            if (source != null) {
                SheetSource s = source;
                source = null;
                try {
                    s.close();
                } catch (Throwable t) {
                    if (error == null) {
                        error = t;
                    } else {
                        error.addSuppressed(t);
                    }
                }
            }
            if (error != null) {
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
         * Closes the resources of an iterator that was garbage collected without being closed; there is no caller
         * to report an error to
         */
        @Override
        public void run() {
            try {
                close();
            } catch (Throwable t) {
                // ignored: the iterator is no longer reachable
            }
        }
    }

    /**
     * Locates the physical rows of one pass by their 0-based row numbers, which must not decrease
     */
    private static final class RowSeeker implements java.io.Closeable {
        private final SheetSource.RowReader reader;
        // the next row of the pass that has been read but not passed
        private SheetSource.Row next_row = null;
        private boolean end = false;
        // the last row number requested
        private int last_index = -1;

        RowSeeker(SheetSource.RowReader reader) {
            this.reader = reader;
        }

        int getLastIndex() {
            return last_index;
        }

        private SheetSource.Row peek() throws IOException {
            if (next_row == null && !end) {
                next_row = reader.next();
                if (next_row == null) {
                    end = true;
                }
            }
            return next_row;
        }

        /**
         * Returns the physical row with the given 0-based number, or null if the row is not in the file
         */
        SheetSource.Row getRow(int idx) throws IOException {
            if (idx < 0) {
                return null;
            }
            last_index = idx;
            while (true) {
                SheetSource.Row row = peek();
                if (row == null || row.getRowNum() > idx) {
                    return null;
                }
                if (row.getRowNum() == idx) {
                    return row;
                }
                next_row = null;
            }
        }

        /**
         * Returns the first physical row after the given 0-based row number, or null if there are no more rows
         */
        SheetSource.Row getNextRow(int idx) throws IOException {
            while (true) {
                SheetSource.Row row = peek();
                if (row == null || row.getRowNum() > idx) {
                    return row;
                }
                next_row = null;
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
