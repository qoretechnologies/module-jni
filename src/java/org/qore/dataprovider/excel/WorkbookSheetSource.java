/*  WorkbookSheetSource.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;

/**
 * The rows of a worksheet of a workbook loaded in memory with the POI usermodel.
 *
 * This is used for the binary (OLE2) formats: an .xls workbook (BIFF8) holds at most 65536 rows by 256 columns, so
 * the memory needed to load it is bounded by the format.  The OOXML (.xlsx) format, which holds up to 1048576 rows,
 * is read by streaming with XlsxSheetSource instead.
 */
final class WorkbookSheetSource implements SheetSource {
    private Workbook workbook;
    private final Sheet sheet;

    /**
     * Selects the worksheet to read; takes ownership of the workbook, which is closed if the worksheet cannot be
     * selected
     *
     * @param workbook the workbook
     * @param sheet_name the sheet name or 0-based index as a string; null or empty for the first sheet
     */
    WorkbookSheetSource(Workbook workbook, String sheet_name) throws IOException {
        try {
            Sheet sheet;
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
            this.sheet = sheet;
        } catch (Throwable t) {
            try {
                workbook.close();
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
        this.workbook = workbook;
    }

    @Override
    public RowReader openRows() throws IOException {
        if (workbook == null) {
            throw new IOException("the workbook is closed");
        }
        final Iterator<org.apache.poi.ss.usermodel.Row> i = sheet.rowIterator();
        return new RowReader() {
            @Override
            public Row next() {
                return i.hasNext() ? new WorkbookRow(i.next()) : null;
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void enableFormulas() {
        // the usermodel holds the formulas of the cells
    }

    @Override
    public void close() throws IOException {
        if (workbook != null) {
            Workbook wb = workbook;
            workbook = null;
            wb.close();
        }
    }

    private static final class WorkbookRow implements Row {
        private final org.apache.poi.ss.usermodel.Row row;

        WorkbookRow(org.apache.poi.ss.usermodel.Row row) {
            this.row = row;
        }

        @Override
        public int getRowNum() {
            return row.getRowNum();
        }

        @Override
        public Cell getCell(int col) {
            org.apache.poi.ss.usermodel.Cell cell = row.getCell(col);
            return cell == null ? null : new WorkbookCell(cell);
        }

        @Override
        public int getLastCellNum() {
            return row.getLastCellNum();
        }
    }

    private static final class WorkbookCell implements Cell {
        private final org.apache.poi.ss.usermodel.Cell cell;

        WorkbookCell(org.apache.poi.ss.usermodel.Cell cell) {
            this.cell = cell;
        }

        @Override
        public CellType getCellType() {
            return cell.getCellType();
        }

        @Override
        public CellType getCachedFormulaResultType() {
            return cell.getCachedFormulaResultType();
        }

        @Override
        public boolean getBooleanCellValue() {
            return cell.getBooleanCellValue();
        }

        @Override
        public double getNumericCellValue() {
            return cell.getNumericCellValue();
        }

        @Override
        public String getStringCellValue() {
            return cell.getStringCellValue();
        }

        @Override
        public boolean isCellDateFormatted() {
            return DateUtil.isCellDateFormatted(cell);
        }

        @Override
        public LocalDateTime getLocalDateTimeCellValue() {
            return cell.getLocalDateTimeCellValue();
        }

        @Override
        public String getCellFormula() {
            // as ExcelGridReader reads the formulas of a sheet
            return cell.getCellType() == CellType.FORMULA ? cell.getCellFormula() : null;
        }
    }
}
