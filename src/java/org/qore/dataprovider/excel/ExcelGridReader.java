/*  ExcelGridReader.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.qore.jni.Hash;

/**
 * Reads an Excel workbook (.xlsx or .xls) as a raw grid of cells for profiling.
 *
 * The result has the shape of the Qore DataProvider::TabularWorkbookGrid hashdecl: the cell values of every
 * sheet row by row, whether each sheet is hidden, its merged ranges, and its formula text, plus the workbook's
 * named ranges.  No header or data range is assumed; the profiler finds them.
 */
public class ExcelGridReader {
    /**
     * Reads a workbook as a raw grid.
     *
     * @param stream the workbook content; closed when the workbook has been read
     * @param max_rows the maximum number of rows read per sheet; a sheet with more rows reports its row count
     * @return a hash with the keys format, sheets, and named_ranges
     */
    public static Hash readGrid(qore.Qore.InputStream stream, int max_rows) throws Throwable {
        return readGrid(new org.qore.jni.QoreInputStreamWrapper(stream), max_rows);
    }

    /**
     * Reads a workbook as a raw grid.
     *
     * @param stream the workbook content; closed when the workbook has been read
     * @param max_rows the maximum number of rows read per sheet; a sheet with more rows reports its row count
     * @return a hash with the keys format, sheets, and named_ranges
     */
    public static Hash readGrid(java.io.InputStream stream, int max_rows) throws Throwable {
        if (max_rows <= 0) {
            stream.close();
            throw new IllegalArgumentException("max_rows must be positive; got " + max_rows);
        }
        try (java.io.InputStream in = stream; Workbook wb = WorkbookFactory.create(in)) {
            ZoneId zone = ZoneId.systemDefault();
            ArrayList<Hash> sheets = new ArrayList<Hash>();
            for (int i = 0; i < wb.getNumberOfSheets(); ++i) {
                sheets.add(readSheet(wb, i, max_rows, zone));
            }

            Hash named_ranges = new Hash();
            for (Name name : wb.getAllNames()) {
                String refers_to = name.getRefersToFormula();
                if (name.getNameName() != null && refers_to != null && !name.isFunctionName()) {
                    named_ranges.put(name.getNameName(), refers_to);
                }
            }

            Hash rv = new Hash();
            rv.put("format", wb instanceof XSSFWorkbook ? "xlsx" : "xls");
            rv.put("sheets", sheets);
            rv.put("named_ranges", named_ranges);
            return rv;
        }
    }

    private static Hash readSheet(Workbook wb, int index, int max_rows, ZoneId zone) {
        Sheet sheet = wb.getSheetAt(index);
        int row_count = sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1;
        int read_rows = Math.min(row_count, max_rows);

        ArrayList<ArrayList<Object>> rows = new ArrayList<ArrayList<Object>>(read_rows);
        Hash formulas = new Hash();
        for (int r = 0; r < read_rows; ++r) {
            ArrayList<Object> values = new ArrayList<Object>();
            Row row = sheet.getRow(r);
            if (row != null) {
                int last_cell = row.getLastCellNum();
                for (int c = 0; c < last_cell; ++c) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        values.add(null);
                        continue;
                    }
                    if (cell.getCellType() == CellType.FORMULA) {
                        formulas.put(new CellReference(r, c).formatAsString(), cell.getCellFormula());
                    }
                    values.add(cellToValue(cell, zone));
                }
            }
            rows.add(values);
        }

        ArrayList<Hash> merged = new ArrayList<Hash>();
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            Hash h = new Hash();
            h.put("first_row", range.getFirstRow());
            h.put("last_row", range.getLastRow());
            h.put("first_col", range.getFirstColumn());
            h.put("last_col", range.getLastColumn());
            merged.add(h);
        }

        Hash rv = new Hash();
        rv.put("name", wb.getSheetName(index));
        rv.put("hidden", wb.isSheetHidden(index) || wb.isSheetVeryHidden(index));
        rv.put("rows", rows);
        if (row_count > read_rows) {
            rv.put("row_count", row_count);
        }
        rv.put("merged", merged);
        rv.put("formulas", formulas);
        return rv;
    }

    private static Object cellToValue(Cell cell, ZoneId zone) {
        CellType type = cell.getCellType();
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
            case STRING:
                return cell.getStringCellValue();
            default:
                return null;
        }
    }
}
