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

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.zone.ZoneRulesException;

import org.qore.dataprovider.format.SpreadsheetColumnType;
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
    private boolean preserve_empty_rows = false;
    // the name of the field holding the formulas of each record's cells; null if formulas are not returned
    private String formula_field = null;
    // how error cells are read: "fail", "null" or "text"
    private String error_cells = "fail";
    // the name of the field holding the errors of each record's cells; null if errors are not returned
    private String error_field = null;
    // the type of each column with a type; null if values are returned as read
    private HashMap<String, String> column_types = null;
    // the columns whose types were given (not inferred)
    private HashSet<String> declared_types = new HashSet<String>();
    // the number of data rows that the inferred column types were sampled from
    private int type_sample_rows = 0;
    // true while rows are sampled to infer column types: error cells are read as no value, and values are not
    // converted
    private boolean sampling = false;

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
     * Keeps empty rows through the last stored row instead of skipping them or ending the iteration.
     *
     * @param preserve_empty_rows whether empty rows are returned; takes precedence over ignore_empty
     */
    public void setPreserveEmptyRows(boolean preserve_empty_rows) {
        this.preserve_empty_rows = preserve_empty_rows;
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

    public void setHeaderCells(String col_start, int row_start, String col_end, int row_end) throws QoreException {
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
            // for each header, true if a merged cell gave it its value (qore#7751)
            ArrayList<Boolean> filled = new ArrayList<Boolean>();
            while (true) {
                SheetSource.Cell cell = row.getCell(cell_no);
                if (cell == null) {
                    break;
                }
                filled.add(row.isFilled(cell_no));
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
            // a merged cell whose value fills the header row names several columns, whose values would replace each
            // other in the records (qore#7751)
            checkFilledHeaders("EXCEL-READ-OPTION-ERROR", headers, filled);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sets how merged cells are read (qore#7751)
     *
     * Call this before the header row is read and before the first iteration.
     *
     * @param mode "first" (the default): a merged cell's value is in its first cell only, as the sheet stores it;
     * "fill_right": each cell of a merged range in the range's first row has its value, so that a group name merged
     * over several columns names each of them; "fill": each cell of the range has its value
     *
     * @throws QoreException EXCEL-READ-OPTION-ERROR if the mode is unknown
     * @throws IOException if the merged ranges of a worksheet cannot be read
     */
    public void setMergedCells(String mode) throws QoreException, IOException {
        if (!"first".equals(mode) && !"fill_right".equals(mode) && !"fill".equals(mode)) {
            throw new QoreException("EXCEL-READ-OPTION-ERROR", String.format("invalid \"merged_cells\" option "
                + "\"%s\"; expecting \"first\", \"fill_right\", or \"fill\"", mode));
        }
        SheetSource source = getSource();
        closeRows();
        // a mode set before is replaced
        if (source instanceof MergedCellSheetSource) {
            source = ((MergedCellSheetSource)source).getSource();
            res.source = source;
        }
        if ("first".equals(mode)) {
            return;
        }
        java.util.List<org.apache.poi.ss.util.CellRangeAddress> regions = source.readMergedRegions();
        // the source is closed with the iterator through the new source
        res.source = new MergedCellSheetSource(source, regions, "fill".equals(mode));
    }

    /**
     * Raises an error if a header name is repeated in a column that a merged cell gave its value, as a group name
     * that fills the header row: the values of the columns would replace each other in the records (qore#7751)
     *
     * @param err the error code
     * @param headers the headers
     * @param filled for each header, true if a merged cell gave it its value
     */
    static void checkFilledHeaders(String err, java.util.List<String> headers, java.util.List<Boolean> filled)
            throws QoreException {
        java.util.HashMap<String, Integer> count = new java.util.HashMap<String, Integer>();
        for (String header : headers) {
            count.merge(header, 1, Integer::sum);
        }
        for (int i = 0; i < headers.size() && i < filled.size(); ++i) {
            if (filled.get(i) && count.get(headers.get(i)) > 1) {
                throw new QoreException(err, String.format("the header row names more than one column \"%s\" "
                    + "with the \"merged_cells\" option, as a merged cell gives its value to several header cells; "
                    + "the values of the columns would replace each other in the records; read the header row as "
                    + "data, or give the column names with \"headers\"; headers: %s", headers.get(i), headers));
            }
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

    /**
     * Sets how error cells (and formula cells whose cached result is an error) are read
     *
     * @param mode "fail" (the default): an error cell raises EXCEL-CELL-ERROR, unless the errors are returned in an
     * error field; "null": no value; "text": the error text, such as "#DIV/0!"
     *
     * @throws QoreException EXCEL-READ-OPTION-ERROR if the mode is unknown
     */
    public void setErrorCells(String mode) throws QoreException {
        if (!"fail".equals(mode) && !"null".equals(mode) && !"text".equals(mode)) {
            throw new QoreException("EXCEL-READ-OPTION-ERROR", String.format("invalid \"error_cells\" option "
                + "\"%s\"; expecting \"fail\", \"null\", or \"text\"", mode));
        }
        error_cells = mode;
    }

    /**
     * Returns the errors of the cells of each record in a field with the given name: a hash of the error text (ex:
     * "#DIV/0!") by column name, or null if none of the record's cells is an error; the error cells are then read as
     * no value, or as their text with setErrorCells("text")
     *
     * Call this after the headers are set; the name must not be one of the headers.
     *
     * @throws QoreException EXCEL-READ-OPTION-ERROR if the name is empty or one of the headers
     */
    public void setErrorField(String name) throws QoreException {
        if (name == null || name.isEmpty()) {
            throw new QoreException("EXCEL-READ-OPTION-ERROR", "the \"error_field\" option must not be empty");
        }
        if (headers.contains(name) || name.equals(formula_field)) {
            throw new QoreException("EXCEL-READ-OPTION-ERROR", String.format("the \"error_field\" option \"%s\" is "
                + "the name of a column of the worksheet or of the formula field; choose another name; columns: %s",
                name, headers));
        }
        error_field = name;
    }

    /**
     * Returns the first data records of the worksheet, read as stored, to infer the types of the columns; error
     * cells are read as no value
     *
     * The iteration is reset before and after the sample is read.
     *
     * @param rows the maximum number of records
     */
    public Hash[] sample(int rows) {
        ArrayList<Hash> rv = new ArrayList<Hash>();
        reset();
        sampling = true;
        try {
            while (rv.size() < rows && next()) {
                rv.add(row_data);
            }
        } finally {
            sampling = false;
            reset();
        }
        return rv.toArray(new Hash[0]);
    }

    /**
     * Returns the type of each column with a type, or null if values are returned as read
     */
    public Hash getColumnTypes() {
        if (column_types == null) {
            return null;
        }
        Hash rv = new Hash();
        for (String header : headers) {
            String type = column_types.get(header);
            if (type != null) {
                rv.put(header, type);
            }
        }
        return rv;
    }

    /**
     * Restarts the iteration from the first data row
     */
    public void reset() {
        current_row = -1;
        row_data = null;
        count = 0;
        closeRows();
    }

    /**
     * Sets the types that the values of columns are converted to
     *
     * @param types the type of each column with a type: "string", "int", "float", "number", "bool", "date", or
     * "any"; columns without a type are returned as read
     * @param declared the columns whose types were given rather than inferred
     * @param sample_rows the number of data rows the other types were inferred from
     *
     * @throws QoreException EXCEL-READ-OPTION-ERROR if a type is unknown
     */
    public void setColumnTypes(Hash types, String[] declared, int sample_rows) throws QoreException {
        HashMap<String, String> map = new HashMap<String, String>();
        for (java.util.Map.Entry<String, Object> e : types.entrySet()) {
            String type = e.getValue() == null ? null : e.getValue().toString();
            if (!SpreadsheetColumnType.isType(type)) {
                throw new QoreException("EXCEL-READ-OPTION-ERROR", String.format("column \"%s\" has an invalid "
                    + "type \"%s\"; expecting one of: %s", e.getKey(), type, SpreadsheetColumnType.TYPE_LIST));
            }
            map.put(e.getKey(), type);
        }
        column_types = map;
        declared_types.clear();
        if (declared != null) {
            Collections.addAll(declared_types, declared);
        }
        type_sample_rows = sample_rows;
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
            if (!preserve_empty_rows || getNextRow(rownum) == null) {
                return null;
            }
            // A gap before the next stored row is an empty row within the worksheet's used range.
            Hash empty = new Hash();
            for (String header : headers) {
                empty.put(header, null);
            }
            return empty;
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
                return preserve_empty_rows ? new Hash() : null;
            }
        }
        int col_no = 0;
        boolean found_data = false;
        Hash row_data = null;
        // the formulas of the record's cells by A1 reference, if formulas are returned
        Hash formulas = null;
        // the errors of the record's cells by column, if errors are returned
        Hash errors = null;
        while (true) {
            SheetSource.Cell cell = row.getCell(cell_no);
            Object val = null;
            // the error text of an error cell
            String error = null;

            if (cell == null) {
                if (auto_detect_data) {
                    break;
                }
            } else if (isErrorCell(cell)) {
                // an error is content of the row
                error = cell.getErrorCellString();
                found_data = true;
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

            if (error != null) {
                if (!sampling) {
                    if (error_field != null) {
                        if (errors == null) {
                            errors = new Hash();
                        }
                        errors.put(key, error);
                    }
                    if ("text".equals(error_cells)) {
                        val = error;
                    } else if (!"null".equals(error_cells) && error_field == null) {
                        throw cellError(error, key, rownum, cell_no);
                    }
                }
            } else if (val != null && column_types != null && !sampling) {
                String type = column_types.get(key);
                if (type != null) {
                    Object v = SpreadsheetColumnType.convert(val, type);
                    if (v == SpreadsheetColumnType.MISMATCH) {
                        throw typeError(val, type, key, rownum);
                    }
                    val = v;
                }
            }

            if (row_data == null) {
                row_data = new Hash();
            }
            row_data.put(key, val);

            // the formula of the row's own cell, not of the merged cell whose value fills it (qore#7751)
            SheetSource.Cell own = formula_field == null || cell == null ? null : row.getOwnCell(cell_no);
            if (own != null) {
                String formula = own.getCellFormula();
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
            if (!preserve_empty_rows) {
                return null;
            }
            if (row_data == null) {
                row_data = new Hash();
            }
        }
        if (formula_field != null) {
            if (row_data.containsKey(formula_field)) {
                throw new QoreException("EXCEL-FORMULA-FIELD-ERROR", String.format("the formula field name '%s' "
                    + "is the name of a field of the record of row %d; choose another name", formula_field,
                    rownum));
            }
            row_data.put(formula_field, formulas);
        }
        if (error_field != null && !sampling) {
            if (row_data.containsKey(error_field)) {
                throw new QoreException("EXCEL-READ-OPTION-ERROR", String.format("the \"error_field\" option "
                    + "\"%s\" is the name of a field of the record of row %d; choose another name", error_field,
                    rownum));
            }
            row_data.put(error_field, errors);
        }
        return row_data;
    }

    /**
     * Returns true if the cell is an error, or a formula whose cached result is an error
     */
    private static boolean isErrorCell(SheetSource.Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return type == CellType.ERROR;
    }

    /**
     * Returns the name of the worksheet for messages
     */
    private String getSheetName() {
        return getSource().getSheetName();
    }

    /**
     * Returns the exception for an error cell that the read does not accept
     */
    private QoreException cellError(String error, String column, int rownum, int col) {
        String ref = new CellReference(rownum - 1, col).formatAsString();
        String sheet = getSheetName();
        Hash arg = new Hash();
        arg.put("worksheet", sheet);
        arg.put("cell", ref);
        arg.put("row", rownum);
        arg.put("column", column);
        arg.put("error", error);
        return new QoreException("EXCEL-CELL-ERROR", String.format("worksheet \"%s\" cell %s (column \"%s\") is the "
            + "error %s; set \"error_cells\" to \"null\" to read error cells as no value or to \"text\" to read them "
            + "as their text, or set \"error_field\" to return the errors of each record in a field of their own",
            sheet, ref, column, error), arg);
    }

    /**
     * Returns the exception for a value that is not of the type of its column
     */
    private QoreException typeError(Object val, String type, String column, int rownum) {
        String sheet = getSheetName();
        Hash arg = new Hash();
        arg.put("worksheet", sheet);
        arg.put("row", rownum);
        arg.put("column", column);
        arg.put("value", val);
        arg.put("type", type);
        String source = declared_types.contains(column)
            ? "given by \"column_types\""
            : String.format("inferred from the first %d data rows", type_sample_rows);
        return new QoreException("EXCEL-COLUMN-TYPE-ERROR", String.format("worksheet \"%s\" row %d column \"%s\": "
            + "the value %s is not a value of the column's type %s, %s; give the column's type with \"column_types\" "
            + "(ex: {\"%s\": \"any\"}) or infer the types from more rows with \"type_sample_rows\"", sheet, rownum,
            column, SpreadsheetColumnType.describe(val), type, source, column), arg);
    }

    /**
     * Returns the last 0-based column from the given column with a cell with a value in the row, or -1 if there is
     * none
     */
    private int getLastValueColumn(SheetSource.Row row, int first_col) {
        for (int col = row.getLastCellNum() - 1; col >= first_col; --col) {
            SheetSource.Cell cell = row.getCell(col);
            if (cell != null && (isErrorCell(cell) || cellToValue(cell) != null)) {
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
     * Returns the worksheets of a workbook file in workbook order
     *
     * An .xlsx workbook's worksheet list is read without loading its worksheets; an .xls workbook is loaded in
     * memory, as its format holds at most 65536 rows.
     *
     * @param path the path of the workbook file
     *
     * @return a hash for each worksheet with its \c name, its 0-based \c index, which the \c worksheet read option
     * accepts, and \c hidden: true if the worksheet is hidden or very hidden; an array, which the jni module declares
     * as the Qore list it converts to
     */
    public static Hash[] getWorksheetInfo(String path) throws IOException {
        File file = new File(path);
        FileMagic magic;
        try (FileInputStream fis = new FileInputStream(file)) {
            magic = FileMagic.valueOf(FileMagic.prepareToCheckMagic(fis));
        }
        if (magic == FileMagic.OOXML) {
            try {
                return XlsxSheetSource.getWorksheetInfo(file).toArray(new Hash[0]);
            } catch (InvalidFormatException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
        try (FileInputStream fis = new FileInputStream(file); Workbook wb = WorkbookFactory.create(fis)) {
            ArrayList<Hash> rv = new ArrayList<Hash>();
            for (int i = 0; i < wb.getNumberOfSheets(); ++i) {
                Hash h = new Hash();
                h.put("name", wb.getSheetName(i));
                h.put("index", i);
                h.put("hidden", wb.isSheetHidden(i) || wb.isSheetVeryHidden(i));
                rv.add(h);
            }
            return rv.toArray(new Hash[0]);
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
