/*  SheetSource.java Copyright 2026 Qore Technologies, s.r.o.

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

import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * The rows of one worksheet, read in order one pass at a time.
 *
 * A pass reads the physical rows of the sheet (the rows stored in the file) from the first one; rows are not kept
 * after they are read, so a pass reads a sheet of any size in bounded memory.  Each pass is independent, so a sheet
 * can be read any number of times.
 */
interface SheetSource extends Closeable {
    /**
     * A cell of a physical row, with the same semantics as the POI usermodel cell methods of the same names
     */
    interface Cell {
        CellType getCellType();

        CellType getCachedFormulaResultType();

        boolean getBooleanCellValue();

        double getNumericCellValue();

        String getStringCellValue();

        /**
         * Returns true if the cell is a numeric cell with a date format, as POI's DateUtil.isCellDateFormatted()
         * determines it
         */
        boolean isCellDateFormatted();

        LocalDateTime getLocalDateTimeCellValue();

        /**
         * Returns the formula of a formula cell as POI's usermodel Cell.getCellFormula() gives it (without the
         * leading "="), or null if the cell is not a formula cell
         *
         * Formula text is only available after formulas are enabled with SheetSource.enableFormulas()
         */
        String getCellFormula();
    }

    /**
     * A physical row of the sheet
     */
    interface Row {
        /** Returns the 0-based row number */
        int getRowNum();

        /** Returns the cell in the given 0-based column, or null if the row has no cell there */
        Cell getCell(int col);

        /** Returns the 0-based column after the last cell of the row, or -1 if the row has no cells */
        int getLastCellNum();
    }

    /**
     * One pass over the physical rows of the sheet, in ascending row order
     */
    interface RowReader extends Closeable {
        /** Returns the next physical row, or null if there are no more rows */
        Row next() throws IOException;
    }

    /**
     * Starts a new pass over the physical rows of the sheet; the caller must close it
     */
    RowReader openRows() throws IOException;

    /**
     * Makes the formula text of the cells of the passes started afterwards available from Cell.getCellFormula(); a
     * source that streams its rows reads formula text only when it is enabled
     */
    void enableFormulas();
}
