/*  OdsGridReader.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qore.jni.Hash;

/**
 * Reads an OpenDocument spreadsheet (.ods) as a raw grid of cells for profiling.
 *
 * The result has the shape of the Qore DataProvider::TabularWorkbookGrid hashdecl: the cell values of every
 * sheet row by row, whether each sheet is hidden, its merged ranges, and its formula text in A1 notation, plus the
 * document's named ranges.  Repeated rows and cells are expanded only up to the last cell with content, so the
 * million empty rows an ODS sheet typically ends with are never materialized.
 */
public class OdsGridReader {
    private static final String TABLE_NS = OdfDocumentNamespace.TABLE.getUri();
    private static final String OFFICE_NS = OdfDocumentNamespace.OFFICE.getUri();
    private static final String STYLE_NS = OdfDocumentNamespace.STYLE.getUri();

    /** an ODF cell reference such as [.B2] or [.B2:.C7], or one naming a sheet such as [$Sheet2.A1] */
    private static final Pattern ODF_REF = Pattern.compile("\\[\\$?([^\\]\\.]*)\\.\\$?([A-Z]+)\\$?([0-9]+)"
        + "(?::\\$?[^\\]\\.]*\\.\\$?([A-Z]+)\\$?([0-9]+))?\\]");

    /**
     * Reads a spreadsheet document as a raw grid.
     *
     * @param stream the document content; closed when the document has been read
     * @param max_rows the maximum number of rows read per sheet; a sheet with more rows reports its row count
     * @return a hash with the keys format, sheets, and named_ranges
     */
    public static Hash readGrid(qore.Qore.InputStream stream, int max_rows) throws Throwable {
        return readGrid(new org.qore.jni.QoreInputStreamWrapper(stream), max_rows);
    }

    /**
     * Reads a spreadsheet document as a raw grid.
     *
     * @param stream the document content; closed when the document has been read
     * @param max_rows the maximum number of rows read per sheet; a sheet with more rows reports its row count
     * @return a hash with the keys format, sheets, and named_ranges
     */
    public static Hash readGrid(java.io.InputStream stream, int max_rows) throws Throwable {
        if (max_rows <= 0) {
            stream.close();
            throw new IllegalArgumentException("max_rows must be positive; got " + max_rows);
        }
        OdfSpreadsheetDocument document;
        try {
            document = OdfSpreadsheetDocument.loadDocument(stream);
        } finally {
            stream.close();
        }
        try {
            ZoneId zone = ZoneId.systemDefault();
            Document content = document.getContentDom();
            ArrayList<Hash> sheets = new ArrayList<Hash>();
            List<OdfTable> tables = document.getTableList();
            if (tables != null) {
                for (OdfTable table : tables) {
                    sheets.add(readSheet(content, table, max_rows, zone));
                }
            }

            Hash named_ranges = new Hash();
            NodeList ranges = content.getElementsByTagNameNS(TABLE_NS, "named-range");
            for (int i = 0; i < ranges.getLength(); ++i) {
                Element range = (Element)ranges.item(i);
                String name = range.getAttributeNS(TABLE_NS, "name");
                String address = range.getAttributeNS(TABLE_NS, "cell-range-address");
                if (!name.isEmpty() && !address.isEmpty()) {
                    named_ranges.put(name, address);
                }
            }

            Hash rv = new Hash();
            rv.put("format", "ods");
            rv.put("sheets", sheets);
            rv.put("named_ranges", named_ranges);
            return rv;
        } finally {
            document.close();
        }
    }

    private static Hash readSheet(Document content, OdfTable table, int max_rows, ZoneId zone) {
        TableTableElement table_element = table.getOdfElement();
        NodeList row_nodes = table_element.getElementsByTagNameNS(TABLE_NS, "table-row");

        // the rows of this table, not of tables nested in cells, and the 0-based index after the last row with
        // content
        ArrayList<Element> row_elements = new ArrayList<Element>();
        int row_count = 0;
        int row = 0;
        for (int i = 0; i < row_nodes.getLength(); ++i) {
            Element row_element = (Element)row_nodes.item(i);
            if (getOwnerTable(row_element) != table_element) {
                continue;
            }
            row_elements.add(row_element);
            int n = repeated(row_element, "number-rows-repeated");
            row += n;
            if (rowHasContent(row_element)) {
                row_count = row;
            }
        }

        ArrayList<ArrayList<Object>> rows = new ArrayList<ArrayList<Object>>();
        ArrayList<Hash> merged = new ArrayList<Hash>();
        Hash formulas = new Hash();
        row = 0;
        for (Element row_element : row_elements) {
            if (row >= row_count || row >= max_rows) {
                break;
            }
            int n = repeated(row_element, "number-rows-repeated");
            for (int r = 0; r < n && row < row_count && row < max_rows; ++r, ++row) {
                rows.add(readRow(row_element, row, r == 0, merged, formulas, zone));
            }
        }

        Hash rv = new Hash();
        rv.put("name", table.getTableName());
        rv.put("hidden", isHidden(content, table_element));
        rv.put("rows", rows);
        if (row_count > max_rows) {
            rv.put("row_count", row_count);
        }
        rv.put("merged", merged);
        rv.put("formulas", formulas);
        return rv;
    }

    /**
     * Reads the cell values of one row; merged ranges and formulas are recorded only for the first of repeated rows
     */
    private static ArrayList<Object> readRow(Element row_element, int row, boolean first_repeat,
            ArrayList<Hash> merged, Hash formulas, ZoneId zone) {
        ArrayList<Object> values = new ArrayList<Object>();
        // empty cells are added only when a later cell has content
        int pending_empty = 0;
        int col = 0;
        for (Node child = row_element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof TableTableCellElementBase)) {
                continue;
            }
            Element cell = (Element)child;
            int n = repeated(cell, "number-columns-repeated");
            if (!cellHasContent(cell)) {
                pending_empty += n;
                col += n;
                continue;
            }
            for (; pending_empty > 0; --pending_empty) {
                values.add(null);
            }
            Object value = cellToValue(OdfTableCell.getInstance((TableTableCellElementBase)child), zone);
            for (int c = 0; c < n; ++c) {
                values.add(value);
            }
            if (first_repeat) {
                int rows_spanned = repeated(cell, "number-rows-spanned");
                int cols_spanned = repeated(cell, "number-columns-spanned");
                if (rows_spanned > 1 || cols_spanned > 1) {
                    Hash range = new Hash();
                    range.put("first_row", row);
                    range.put("last_row", row + rows_spanned - 1);
                    range.put("first_col", col);
                    range.put("last_col", col + cols_spanned - 1);
                    merged.add(range);
                }
                String formula = cell.getAttributeNS(TABLE_NS, "formula");
                if (!formula.isEmpty()) {
                    formulas.put(columnLetter(col) + (row + 1), toA1(formula));
                }
            }
            col += n;
        }
        return values;
    }

    /**
     * Converts ODF formula syntax ("of:=SUM([.B2:.B7])") to A1 notation ("SUM(B2:B7)")
     */
    static String toA1(String formula) {
        String f = formula;
        int prefix = f.indexOf(":=");
        if (prefix >= 0 && prefix < 6) {
            f = f.substring(prefix + 2);
        } else if (f.startsWith("=")) {
            f = f.substring(1);
        }
        Matcher m = ODF_REF.matcher(f);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String sheet = m.group(1);
            String ref = m.group(2) + m.group(3);
            if (m.group(4) != null) {
                ref += ":" + m.group(4) + m.group(5);
            }
            if (!sheet.isEmpty()) {
                ref = sheet + "!" + ref;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(ref));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isHidden(Document content, TableTableElement table_element) {
        String style_name = table_element.getAttributeNS(TABLE_NS, "style-name");
        if (style_name.isEmpty()) {
            return false;
        }
        NodeList styles = content.getElementsByTagNameNS(STYLE_NS, "style");
        for (int i = 0; i < styles.getLength(); ++i) {
            Element style = (Element)styles.item(i);
            if (!style_name.equals(style.getAttributeNS(STYLE_NS, "name"))) {
                continue;
            }
            NodeList props = style.getElementsByTagNameNS(STYLE_NS, "table-properties");
            for (int j = 0; j < props.getLength(); ++j) {
                if ("false".equals(((Element)props.item(j)).getAttributeNS(TABLE_NS, "display"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int repeated(Element element, String attribute) {
        String value = element.getAttributeNS(TABLE_NS, attribute);
        if (value.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 1;
        }
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
        return !cell.getAttributeNS(OFFICE_NS, "value-type").isEmpty()
            || !cell.getTextContent().trim().isEmpty();
    }

    private static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        while (index >= 0) {
            sb.insert(0, (char)('A' + (index % 26)));
            index = index / 26 - 1;
        }
        return sb.toString();
    }

    private static Object cellToValue(OdfTableCell cell, ZoneId zone) {
        String value_type = cell.getValueType();
        if (value_type == null || value_type.isEmpty()) {
            // text content without a value type
            String text = cell.getDisplayText();
            return text == null || text.isEmpty() ? null : text;
        }
        switch (value_type) {
            case "string":
                return cell.getStringValue();
            case "float":
                return cell.getDoubleValue();
            case "currency":
            case "percentage":
                // OdfTableCell.getDoubleValue() only accepts float cells; these have their number in office:value
                return cell.getOdfElement().getOfficeValueAttribute();
            case "boolean":
                return cell.getBooleanValue();
            case "date":
                // the clock time written in the file, as the record reader reads it
                return OdsCell.toDateTime(cell.getOdfElement().getOfficeDateValueAttribute(), zone);
            case "time":
                return OdsCell.toTime(cell.getOdfElement().getOfficeTimeValueAttribute(), zone);
            default:
                return cell.getDisplayText();
        }
    }
}
