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
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.dom.OdfDocumentNamespace;
import org.odftoolkit.odfdom.dom.element.table.TableTableCellElementBase;
import org.odftoolkit.odfdom.dom.element.table.TableTableElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableRowElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
    private boolean ignore_empty = false;
    // the 1-based number of the last row with content
    private int last_data_row = 0;
    // the row elements of the table and the 0-based number of the first row each one represents; runs of identical
    // rows are single elements with a repeat count, so rows are located without expanding them
    private ArrayList<TableTableRowElement> row_elements = new ArrayList<TableTableRowElement>();
    private ArrayList<Integer> row_starts = new ArrayList<Integer>();
    // Cached table dimensions
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
        // Cache dimensions immediately
        indexRows();
        cachedColCount = table.getColumnCount();
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
        if (getRowElement(row_start - 1) == null) {
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
            OdfTableCell cell = getCell(cell_no, row_start - 1);
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
                int last_row = getLastDataRow();
                if (end_row != -1 && end_row < last_row) {
                    last_row = end_row;
                }
                while (row_data == null && current_row < last_row) {
                    // jump over runs of empty rows without visiting each row in them
                    int next_row = getNextRowWithContent(current_row);
                    if (next_row == -1 || next_row > last_row) {
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
        return current_row != -1;
    }

    /**
     * Indexes the row elements of the table.
     *
     * Spreadsheet applications store runs of identical rows, such as the empty rows to the end of the sheet, as a
     * single row element with a repeat count; ODFDOM's positional cell access expands such runs, which is
     * prohibitively slow for a sheet ending in a million repeated empty rows, so rows and cells are located through
     * this index instead.  The number of rows and the last row with content are determined at the same time.
     */
    private void indexRows() {
        TableTableElement table_element = table.getOdfElement();
        NodeList rows = table_element.getElementsByTagNameNS(OdfDocumentNamespace.TABLE.getUri(), "table-row");
        int row = 0;
        for (int i = 0; i < rows.getLength(); ++i) {
            TableTableRowElement row_element = (TableTableRowElement)rows.item(i);
            // ignore the rows of tables nested in cells
            if (getOwnerTable(row_element) != table_element) {
                continue;
            }
            Integer repeated = row_element.getTableNumberRowsRepeatedAttribute();
            int n = (repeated == null || repeated < 1) ? 1 : repeated;
            row_elements.add(row_element);
            row_starts.add(row);
            row += n;
            if (rowHasContent(row_element)) {
                last_data_row = row;
            }
        }
        cachedRowCount = row;
    }

    private int getLastDataRow() {
        return last_data_row;
    }

    /**
     * Returns the 1-based number of the first row after the given 1-based row in a row element with content, or -1
     * if there is none
     */
    private int getNextRowWithContent(int row) {
        // the 0-based index of the next row is the given 1-based row number
        for (int i = getRowElementIndex(row); i >= 0 && i < row_elements.size(); ++i) {
            if (rowHasContent(row_elements.get(i))) {
                return Math.max(row, row_starts.get(i)) + 1;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the row element holding the given 0-based row, or -1 if the row is past the end of the
     * table
     */
    private int getRowElementIndex(int row_idx) {
        if (row_idx < 0 || row_idx >= cachedRowCount) {
            return -1;
        }
        // the last row element starting at or before the row
        int lo = 0;
        int hi = row_starts.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (row_starts.get(mid) <= row_idx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * Returns the row element holding the given 0-based row, or null if the row is past the end of the table
     */
    private TableTableRowElement getRowElement(int row_idx) {
        int i = getRowElementIndex(row_idx);
        return i == -1 ? null : row_elements.get(i);
    }

    /**
     * Returns the cell at the given 0-based position, or null if the row has no cell in the column
     */
    private OdfTableCell getCell(int col_idx, int row_idx) {
        TableTableRowElement row_element = getRowElement(row_idx);
        if (row_element == null) {
            return null;
        }
        String table_ns = OdfDocumentNamespace.TABLE.getUri();
        int col = 0;
        for (Node child = row_element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof TableTableCellElementBase)) {
                continue;
            }
            Element cell = (Element)child;
            String repeated = cell.getAttributeNS(table_ns, "number-columns-repeated");
            int n = 1;
            if (!repeated.isEmpty()) {
                try {
                    n = Math.max(1, Integer.parseInt(repeated));
                } catch (NumberFormatException e) {
                    n = 1;
                }
            }
            if (col_idx < col + n) {
                // an empty cell has no value; empty cells are mostly repeated, and ODFDOM logs warnings for each
                // repeated cell it wraps
                if (!cellHasContent(cell)) {
                    return null;
                }
                return OdfTableCell.getInstance((TableTableCellElementBase)child);
            }
            col += n;
        }
        return null;
    }

    private static Node getOwnerTable(Node node) {
        for (Node parent = node.getParentNode(); parent != null; parent = parent.getParentNode()) {
            if (parent instanceof TableTableElement) {
                return parent;
            }
        }
        return null;
    }

    private static boolean rowHasContent(Element row_element) {
        for (Node child = row_element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "table-cell".equals(child.getLocalName())
                && cellHasContent((Element)child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cellHasContent(Element cell) {
        return !cell.getAttributeNS(OdfDocumentNamespace.OFFICE.getUri(), "value-type").isEmpty()
            || !cell.getTextContent().trim().isEmpty();
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
        while (true) {
            if (cell_no >= cachedColCount) {
                break;
            }
            OdfTableCell cell = getCell(cell_no, rowIdx);
            Object val;

            // a row element can have fewer cells than the table has columns, in which case there is no cell
            String valueType = cell == null ? null : cell.getValueType();
            boolean cellEmpty = (valueType == null || valueType.isEmpty());
            if (cellEmpty && cell != null) {
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
