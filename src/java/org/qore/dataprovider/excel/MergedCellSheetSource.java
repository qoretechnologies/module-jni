/*  MergedCellSheetSource.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ss.util.CellRangeAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A sheet whose merged cells give their value to the cells that they cover (qore#7751)
 *
 * A merged cell keeps its value in its first cell, and the other cells of its range are empty.  This source gives
 * each cell of a merged range in the first row of the range the value of the range's first cell, and with "fill"
 * also each cell of the range's other rows, so that a group name merged over several columns names each of them.
 *
 * Only the rows of the sheet are read: a row that the sheet does not hold, such as an empty row under a merged
 * cell, is not added.
 */
final class MergedCellSheetSource implements SheetSource {
    private final SheetSource source;
    // the merged ranges with more than one cell
    private final List<CellRangeAddress> regions;
    // true if a merged cell also gives its value to the rows of its range below its first row
    private final boolean down;

    /**
     * Creates the source
     *
     * @param source the source whose merged cells give their value; it is closed with this source
     * @param regions the merged ranges of the sheet
     * @param down true if a merged cell also gives its value to the rows of its range below its first row
     */
    MergedCellSheetSource(SheetSource source, List<CellRangeAddress> regions, boolean down) {
        this.source = source;
        this.regions = new ArrayList<CellRangeAddress>();
        for (CellRangeAddress region : regions) {
            if (region.getNumberOfCells() > 1) {
                this.regions.add(region);
            }
        }
        // by first row, so that a pass finds the ranges of each row without checking the others
        this.regions.sort((a, b) -> Integer.compare(a.getFirstRow(), b.getFirstRow()));
        this.down = down;
    }

    /**
     * Returns the source whose merged cells give their value
     */
    SheetSource getSource() {
        return source;
    }

    @Override
    public RowReader openRows() throws IOException {
        return new MergedRowReader(source.openRows());
    }

    @Override
    public void enableFormulas() {
        source.enableFormulas();
    }

    @Override
    public String getSheetName() {
        return source.getSheetName();
    }

    @Override
    public List<CellRangeAddress> readMergedRegions() throws IOException {
        return source.readMergedRegions();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    /**
     * A pass over the rows, in increasing row order; it keeps the merged ranges that cover the current row, and the
     * first cell of each range whose first row it has read, for the rows of the range below
     */
    private final class MergedRowReader implements RowReader {
        private final RowReader rows;
        // the index of the next range by first row that the pass has not reached
        private int next_region = 0;
        // the ranges that the pass has reached and that have not ended
        private final ArrayList<CellRangeAddress> open = new ArrayList<CellRangeAddress>();
        // the first cell of each open range whose first row was read in this pass
        private final HashMap<CellRangeAddress, Cell> anchors = new HashMap<CellRangeAddress, Cell>();

        MergedRowReader(RowReader rows) {
            this.rows = rows;
        }

        @Override
        public Row next() throws IOException {
            Row row = rows.next();
            if (row == null) {
                return null;
            }
            int r = row.getRowNum();
            // the ranges that have ended
            for (int i = open.size() - 1; i >= 0; --i) {
                if (open.get(i).getLastRow() < r) {
                    anchors.remove(open.remove(i));
                }
            }
            // the ranges that start at or before the row
            while (next_region < regions.size() && regions.get(next_region).getFirstRow() <= r) {
                CellRangeAddress region = regions.get(next_region++);
                if (region.getLastRow() >= r) {
                    open.add(region);
                }
            }
            ArrayList<CellRangeAddress> active = null;
            for (CellRangeAddress region : open) {
                if (r == region.getFirstRow()) {
                    if (down && region.getLastRow() > r) {
                        anchors.put(region, row.getCell(region.getFirstColumn()));
                    }
                } else if (!down) {
                    continue;
                }
                if (active == null) {
                    active = new ArrayList<CellRangeAddress>();
                }
                active.add(region);
            }
            return active == null ? row : new MergedRow(row, active, anchors);
        }

        @Override
        public void close() throws IOException {
            rows.close();
        }
    }

    /**
     * Returns true if the first cell of a merged range has a value to give its other cells: not a missing or blank
     * cell, nor blank text, also as the cached result of a formula
     */
    static boolean hasValue(Cell anchor) {
        if (anchor == null) {
            return false;
        }
        org.apache.poi.ss.usermodel.CellType type = anchor.getCellType();
        if (type == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            type = anchor.getCachedFormulaResultType();
        }
        switch (type) {
            case BLANK:
                return false;
            case STRING: {
                String v = anchor.getStringCellValue();
                return v != null && !v.trim().isEmpty();
            }
            default:
                return true;
        }
    }

    /**
     * A row with cells of merged ranges
     */
    private static final class MergedRow implements Row {
        private final Row row;
        // the merged ranges that cover cells of the row
        private final List<CellRangeAddress> active;
        private final HashMap<CellRangeAddress, Cell> anchors;

        MergedRow(Row row, List<CellRangeAddress> active, HashMap<CellRangeAddress, Cell> anchors) {
            this.row = row;
            this.active = active;
            this.anchors = anchors;
        }

        @Override
        public int getRowNum() {
            return row.getRowNum();
        }

        @Override
        public Cell getCell(int col) {
            int r = row.getRowNum();
            for (CellRangeAddress region : active) {
                if (col < region.getFirstColumn() || col > region.getLastColumn()) {
                    continue;
                }
                Cell anchor = r == region.getFirstRow() ? row.getCell(region.getFirstColumn()) : anchors.get(region);
                // a range without a value gives none: its cells are read as without the option
                return hasValue(anchor) ? anchor : row.getCell(col);
            }
            return row.getCell(col);
        }

        @Override
        public Cell getOwnCell(int col) {
            return row.getCell(col);
        }

        @Override
        public boolean isFilled(int col) {
            int r = row.getRowNum();
            for (CellRangeAddress region : active) {
                if (col < region.getFirstColumn() || col > region.getLastColumn()) {
                    continue;
                }
                if (r == region.getFirstRow() && col == region.getFirstColumn()) {
                    return false;
                }
                Cell anchor = r == region.getFirstRow() ? row.getCell(region.getFirstColumn()) : anchors.get(region);
                // a range without a value gives none
                return hasValue(anchor);
            }
            return false;
        }

        @Override
        public int getLastCellNum() {
            // one more than the last column with a cell, as Row.getLastCellNum() gives it
            int rv = row.getLastCellNum();
            for (CellRangeAddress region : active) {
                if (region.getLastColumn() + 1 > rv) {
                    rv = region.getLastColumn() + 1;
                }
            }
            return rv;
        }
    }
}
