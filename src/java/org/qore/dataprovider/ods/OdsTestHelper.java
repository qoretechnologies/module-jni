/*  OdsTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.w3c.dom.Element;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Helper class for creating test ODS files for the OdsDataProvider tests.
 */
public class OdsTestHelper {

    /**
     * Creates a simple ODS file with headers and data.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            // Create header row
            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Age");
            table.getCellByPosition(2, 0).setStringValue("City");

            // Create data rows
            table.getCellByPosition(0, 1).setStringValue("Alice");
            table.getCellByPosition(1, 1).setDoubleValue(30.0);
            table.getCellByPosition(2, 1).setStringValue("New York");

            table.getCellByPosition(0, 2).setStringValue("Bob");
            table.getCellByPosition(1, 2).setDoubleValue(25.0);
            table.getCellByPosition(2, 2).setStringValue("Los Angeles");

            table.getCellByPosition(0, 3).setStringValue("Charlie");
            table.getCellByPosition(1, 3).setDoubleValue(35.0);
            table.getCellByPosition(2, 3).setStringValue("Chicago");

            doc.save(path);
        }
    }

    /**
     * Creates a simple ODS file and returns the bytes.
     */
    public static byte[] createSimpleOdsBytes() throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Age");
            table.getCellByPosition(2, 0).setStringValue("City");

            table.getCellByPosition(0, 1).setStringValue("Alice");
            table.getCellByPosition(1, 1).setDoubleValue(30.0);
            table.getCellByPosition(2, 1).setStringValue("New York");

            table.getCellByPosition(0, 2).setStringValue("Bob");
            table.getCellByPosition(1, 2).setDoubleValue(25.0);
            table.getCellByPosition(2, 2).setStringValue("Los Angeles");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.save(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Creates an ODS file with multiple worksheets.
     */
    public static void createMultiSheetOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            // Sheet 1: Users
            OdfTable sheet1 = doc.getTableList().get(0);
            sheet1.setTableName("Users");
            sheet1.getCellByPosition(0, 0).setStringValue("ID");
            sheet1.getCellByPosition(1, 0).setStringValue("Username");
            sheet1.getCellByPosition(0, 1).setDoubleValue(1.0);
            sheet1.getCellByPosition(1, 1).setStringValue("alice");
            sheet1.getCellByPosition(0, 2).setDoubleValue(2.0);
            sheet1.getCellByPosition(1, 2).setStringValue("bob");

            // Sheet 2: Products
            OdfTable sheet2 = OdfTable.newTable(doc);
            sheet2.setTableName("Products");
            sheet2.getCellByPosition(0, 0).setStringValue("SKU");
            sheet2.getCellByPosition(1, 0).setStringValue("Product");
            sheet2.getCellByPosition(2, 0).setStringValue("Price");
            sheet2.getCellByPosition(0, 1).setStringValue("A001");
            sheet2.getCellByPosition(1, 1).setStringValue("Widget");
            sheet2.getCellByPosition(2, 1).setDoubleValue(9.99);

            // Sheet 3: Orders
            OdfTable sheet3 = OdfTable.newTable(doc);
            sheet3.setTableName("Orders");
            sheet3.getCellByPosition(0, 0).setStringValue("OrderID");
            sheet3.getCellByPosition(1, 0).setStringValue("UserID");
            sheet3.getCellByPosition(2, 0).setStringValue("Total");
            sheet3.getCellByPosition(0, 1).setDoubleValue(1001.0);
            sheet3.getCellByPosition(1, 1).setDoubleValue(1.0);
            sheet3.getCellByPosition(2, 1).setDoubleValue(99.99);

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with date values.
     */
    public static void createDatesOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Dates");

            table.getCellByPosition(0, 0).setStringValue("Event");
            table.getCellByPosition(1, 0).setStringValue("Date");

            table.getCellByPosition(0, 1).setStringValue("Start");
            GregorianCalendar cal1 = new GregorianCalendar(2025, Calendar.JANUARY, 15);
            table.getCellByPosition(1, 1).setDateValue(cal1);

            table.getCellByPosition(0, 2).setStringValue("End");
            GregorianCalendar cal2 = new GregorianCalendar(2025, Calendar.JUNE, 30);
            table.getCellByPosition(1, 2).setDateValue(cal2);

            doc.save(path);
        }
    }

    /**
     * Creates an ODS workbook with a date, a date with a time, and a time: the clock times written in
     * the file, without a zone.
     */
    public static void createDateTimesOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Times");

            table.getCellByPosition(0, 0).setStringValue("Date");
            table.getCellByPosition(1, 0).setStringValue("DateTime");
            table.getCellByPosition(2, 0).setStringValue("Time");

            table.getCellByPosition(0, 1).setLocalDateValue(java.time.LocalDate.of(2025, 1, 15));
            table.getCellByPosition(1, 1).setLocalDateTimeValue(java.time.LocalDateTime.of(2025, 7, 1, 14, 30, 5));
            GregorianCalendar time = new GregorianCalendar(1970, Calendar.JANUARY, 1, 9, 45, 0);
            table.getCellByPosition(2, 1).setTimeValue(time);

            doc.save(path);
        }
    }

    /**
     * Creates an empty ODS workbook.
     */
    public static void createEmptyOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Empty");
            doc.save(path);
        }
    }

    /**
     * Creates an ODS file without headers (just raw data).
     */
    public static void createNoHeadersOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Data");

            table.getCellByPosition(0, 0).setStringValue("value1");
            table.getCellByPosition(1, 0).setDoubleValue(100.0);
            table.getCellByPosition(2, 0).setBooleanValue(true);

            table.getCellByPosition(0, 1).setStringValue("value2");
            table.getCellByPosition(1, 1).setDoubleValue(200.0);
            table.getCellByPosition(2, 1).setBooleanValue(false);

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file stored the way spreadsheet applications store sparse sheets: runs of empty rows are
     * single row elements with a repeat count.
     *
     * Rows: headers (Sku, Qty), A-1/5, 1,000,000 repeated empty rows, C-3/9, then 1,048,000 repeated empty rows.
     */
    public static void createRepeatedEmptyRowsOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Counts");
            table.getCellByPosition(0, 0).setStringValue("Sku");
            table.getCellByPosition(1, 0).setStringValue("Qty");
            table.getCellByPosition(0, 1).setStringValue("A-1");
            table.getCellByPosition(1, 1).setDoubleValue(5.0);

            OdfFileDom dom = doc.getContentDom();
            Element table_element = table.getOdfElement();
            table_element.appendChild(newEmptyRows(dom, 1000000));
            Element row = newRow(dom);
            row.appendChild(newCell(dom, "string", "C-3"));
            row.appendChild(newCell(dom, "float", "9"));
            table_element.appendChild(row);
            table_element.appendChild(newEmptyRows(dom, 1048000));
            doc.save(path);
        }
    }

    private static Element newRow(OdfFileDom dom) {
        return dom.createElementNS(OdfDocumentNamespace.TABLE.getUri(), "table:table-row");
    }

    private static Element newEmptyRows(OdfFileDom dom, int repeated) {
        Element row = newRow(dom);
        row.setAttributeNS(OdfDocumentNamespace.TABLE.getUri(), "table:number-rows-repeated",
            Integer.toString(repeated));
        // as written by LibreOffice: one empty cell repeated to the last column
        Element cell = dom.createElementNS(OdfDocumentNamespace.TABLE.getUri(), "table:table-cell");
        cell.setAttributeNS(OdfDocumentNamespace.TABLE.getUri(), "table:number-columns-repeated", "1024");
        row.appendChild(cell);
        return row;
    }

    private static Element newCell(OdfFileDom dom, String value_type, String value) {
        String office_ns = OdfDocumentNamespace.OFFICE.getUri();
        Element cell = dom.createElementNS(OdfDocumentNamespace.TABLE.getUri(), "table:table-cell");
        cell.setAttributeNS(office_ns, "office:value-type", value_type);
        if (!value_type.equals("string")) {
            cell.setAttributeNS(office_ns, "office:value", value);
        }
        Element text = dom.createElementNS(OdfDocumentNamespace.TEXT.getUri(), "text:p");
        text.setTextContent(value);
        cell.appendChild(text);
        return cell;
    }

    /**
     * Creates an ODS file with special characters and unicode.
     */
    public static void createSpecialCharsOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Special");

            table.getCellByPosition(0, 0).setStringValue("Name");
            table.getCellByPosition(1, 0).setStringValue("Description");

            table.getCellByPosition(0, 1).setStringValue("Cafe");
            table.getCellByPosition(1, 1).setStringValue("A nice place to have coffee");

            table.getCellByPosition(0, 2).setStringValue("Tokyo");
            table.getCellByPosition(1, 2).setStringValue("Capital of Japan");

            table.getCellByPosition(0, 3).setStringValue("Quotes");
            table.getCellByPosition(1, 3).setStringValue("He said \"hello\"");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with mixed case headers for tolwr testing.
     */
    public static void createMixedCaseHeadersOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            table.getCellByPosition(0, 0).setStringValue("FirstName");
            table.getCellByPosition(1, 0).setStringValue("LAST_NAME");
            table.getCellByPosition(2, 0).setStringValue("Email_Address");

            table.getCellByPosition(0, 1).setStringValue("John");
            table.getCellByPosition(1, 1).setStringValue("Doe");
            table.getCellByPosition(2, 1).setStringValue("john.doe@example.com");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with data starting at a specific cell range.
     */
    public static void createOffsetDataOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Sheet1");

            // Some text in upper left
            table.getCellByPosition(0, 0).setStringValue("Report Title");

            // Headers starting at B3 (col 1, row 2 in 0-based)
            table.getCellByPosition(1, 2).setStringValue("Col1");
            table.getCellByPosition(2, 2).setStringValue("Col2");
            table.getCellByPosition(3, 2).setStringValue("Col3");

            // Data starting at B4
            table.getCellByPosition(1, 3).setStringValue("A");
            table.getCellByPosition(2, 3).setStringValue("B");
            table.getCellByPosition(3, 3).setStringValue("C");

            table.getCellByPosition(1, 4).setStringValue("D");
            table.getCellByPosition(2, 4).setStringValue("E");
            table.getCellByPosition(3, 4).setStringValue("F");

            doc.save(path);
        }
    }

    /**
     * Creates an ODS file with various data types for testing.
     */
    public static void createDataTypesOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("DataTypes");

            // Headers
            table.getCellByPosition(0, 0).setStringValue("String");
            table.getCellByPosition(1, 0).setStringValue("Integer");
            table.getCellByPosition(2, 0).setStringValue("Float");
            table.getCellByPosition(3, 0).setStringValue("Boolean");
            table.getCellByPosition(4, 0).setStringValue("Date");

            // Data row 1
            table.getCellByPosition(0, 1).setStringValue("text");
            table.getCellByPosition(1, 1).setDoubleValue(42.0);
            table.getCellByPosition(2, 1).setDoubleValue(3.14159);
            table.getCellByPosition(3, 1).setBooleanValue(true);
            GregorianCalendar dtCal1 = new GregorianCalendar(2025, Calendar.MARCH, 15, 10, 30, 0);
            table.getCellByPosition(4, 1).setDateValue(dtCal1);

            // Data row 2
            table.getCellByPosition(0, 2).setStringValue("another");
            table.getCellByPosition(1, 2).setDoubleValue(-100.0);
            table.getCellByPosition(2, 2).setDoubleValue(2.71828);
            table.getCellByPosition(3, 2).setBooleanValue(false);
            GregorianCalendar dtCal2 = new GregorianCalendar(2024, Calendar.DECEMBER, 25, 0, 0, 0);
            table.getCellByPosition(4, 2).setDateValue(dtCal2);

            doc.save(path);
        }
    }

    /**
     * Creates a spreadsheet laid out the way supplier PO confirmations are maintained by hand: a merged title
     * banner, a blank row, the header, identifiers with leading zeros, dates stored as text, a totals row with a SUM
     * formula, a hidden lookup sheet, and a named range on it.
     *
     * @param path The path to create the file at
     */
    public static void createProfileOds(String path) throws Exception {
        try (OdfSpreadsheetDocument doc = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
            String table_ns = OdfDocumentNamespace.TABLE.getUri();
            OdfTable table = doc.getTableList().get(0);
            table.setTableName("Confirmations");
            table.getCellByPosition(0, 0).setStringValue("Supplier PO confirmations - week 41");
            String[] headers = {"PO", "SKU", "Qty", "Ship date"};
            for (int i = 0; i < headers.length; ++i) {
                table.getCellByPosition(i, 2).setStringValue(headers[i]);
            }
            Object[][] data = {
                {"PO-1001", "000123", 5.0, "10.10.2026"},
                {"PO-1002", "000456", 7.0, "24.10.2026"},
            };
            for (int r = 0; r < data.length; ++r) {
                for (int c = 0; c < data[r].length; ++c) {
                    OdfTableCell cell = table.getCellByPosition(c, 3 + r);
                    if (data[r][c] instanceof Double) {
                        cell.setDoubleValue((Double)data[r][c]);
                    } else {
                        cell.setStringValue((String)data[r][c]);
                    }
                }
            }
            table.getCellByPosition(0, 5).setStringValue("Total");
            OdfTableCell sum = table.getCellByPosition(2, 5);
            sum.setDoubleValue(12.0);
            sum.setFormula("of:=SUM([.C4:.C5])");

            // the banner is merged once every row exists: ODFDOM copies a row's cell attributes to the rows it
            // creates after it
            table.getCellByPosition(0, 0).getOdfElement().setAttributeNS(table_ns, "table:number-columns-spanned",
                "4");

            OdfTable lookup = OdfTable.newTable(doc, 2, 2);
            lookup.setTableName("Lookup");
            lookup.getCellByPosition(0, 0).setStringValue("Code");
            lookup.getCellByPosition(1, 0).setStringValue("Rate");
            lookup.getCellByPosition(0, 1).setStringValue("X");
            lookup.getCellByPosition(1, 1).setDoubleValue(1.5);

            OdfFileDom dom = doc.getContentDom();
            String office_ns = OdfDocumentNamespace.OFFICE.getUri();
            String style_ns = OdfDocumentNamespace.STYLE.getUri();
            Element styles = (Element)dom.getElementsByTagNameNS(office_ns, "automatic-styles").item(0);
            if (styles == null) {
                styles = dom.createElementNS(office_ns, "office:automatic-styles");
                dom.getDocumentElement().insertBefore(styles,
                    dom.getElementsByTagNameNS(office_ns, "body").item(0));
            }
            Element hidden = dom.createElementNS(style_ns, "style:style");
            hidden.setAttributeNS(style_ns, "style:name", "hidden_table");
            hidden.setAttributeNS(style_ns, "style:family", "table");
            Element props = dom.createElementNS(style_ns, "style:table-properties");
            props.setAttributeNS(table_ns, "table:display", "false");
            hidden.appendChild(props);
            styles.appendChild(hidden);
            lookup.getOdfElement().setAttributeNS(table_ns, "table:style-name", "hidden_table");

            Element spreadsheet = (Element)dom.getElementsByTagNameNS(OdfDocumentNamespace.OFFICE.getUri(),
                "spreadsheet").item(0);
            Element expressions = dom.createElementNS(table_ns, "table:named-expressions");
            Element range = dom.createElementNS(table_ns, "table:named-range");
            range.setAttributeNS(table_ns, "table:name", "Rates");
            range.setAttributeNS(table_ns, "table:cell-range-address", "$Lookup.$A$1:.$B$2");
            expressions.appendChild(range);
            spreadsheet.appendChild(expressions);

            doc.save(path);
        }
    }

    private static final String CONTENT_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
        + "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" "
        + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" office:version=\"1.3\">"
        + "<office:body><office:spreadsheet>";
    private static final String CONTENT_END = "</office:spreadsheet></office:body></office:document-content>";

    /**
     * Writes an ODS document whose content.xml is written by the given writer, so a document of any size is written
     * in bounded memory
     */
    private interface ContentWriter {
        void write(Writer out) throws IOException;
    }

    private static void writeOds(String path, ContentWriter content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(path))) {
            // the media type is the first entry, stored without compression
            byte[] mimetype = "application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("mimetype");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(mimetype);
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest "
                + "xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.3\">"
                + "<manifest:file-entry manifest:full-path=\"/\" "
                + "manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\"/>"
                + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                + "</manifest:manifest>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("content.xml"));
            // the writer is not closed, as that would close the zip stream
            Writer out = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
            out.write(CONTENT_START);
            content.write(out);
            out.write(CONTENT_END);
            out.flush();
            zip.closeEntry();
        }
    }

    /**
     * Creates a large ODS document as spreadsheet applications write it, written in bounded memory.
     *
     * The table "Large" has 5 columns followed by repeated empty columns to column 16384, a header row (Id, Name,
     * Amount, When, Flag) and the given number of data rows; data row i (1-based) has Id i, Name "name-i", Amount
     * i / 4, When 2025-01-01T00:00 plus i seconds (a date cell), and Flag true for even i.  The data rows are followed
     * by one row element repeated 3 times (Id -1, Name "repeated", Amount 0.5, When 2025-06-30T12:00, Flag false) and
     * then by 1048000 repeated empty rows.
     *
     * @param path The path to create the file at
     * @param rows The number of data rows
     */
    public static void createLargeOds(String path, int rows) throws IOException {
        writeOds(path, out -> {
            out.write("<table:table table:name=\"Large\">"
                + "<table:table-column table:number-columns-repeated=\"5\"/>"
                + "<table:table-column table:number-columns-repeated=\"16379\"/>"
                + "<table:table-row>");
            for (String header : new String[]{"Id", "Name", "Amount", "When", "Flag"}) {
                out.write("<table:table-cell office:value-type=\"string\"><text:p>" + header
                    + "</text:p></table:table-cell>");
            }
            out.write("<table:table-cell table:number-columns-repeated=\"16379\"/></table:table-row>");
            LocalDateTime base = LocalDateTime.of(2025, 1, 1, 0, 0);
            for (int i = 1; i <= rows; ++i) {
                writeLargeRow(out, 1, i, "name-" + i, i / 4.0, base.plusSeconds(i), i % 2 == 0);
            }
            writeLargeRow(out, 3, -1, "repeated", 0.5, LocalDateTime.of(2025, 6, 30, 12, 0), false);
            out.write("<table:table-row table:number-rows-repeated=\"1048000\">"
                + "<table:table-cell table:number-columns-repeated=\"16384\"/></table:table-row>");
            out.write("</table:table>");
        });
    }

    private static void writeLargeRow(Writer out, int repeated, int id, String name, double amount,
            LocalDateTime when, boolean flag) throws IOException {
        out.write("<table:table-row" + (repeated > 1 ? " table:number-rows-repeated=\"" + repeated + "\"" : "")
            + "><table:table-cell office:value-type=\"float\" office:value=\"" + id + "\"><text:p>" + id
            + "</text:p></table:table-cell><table:table-cell office:value-type=\"string\"><text:p>" + name
            + "</text:p></table:table-cell><table:table-cell office:value-type=\"float\" office:value=\"" + amount
            + "\"><text:p>" + amount + "</text:p></table:table-cell>"
            + "<table:table-cell office:value-type=\"date\" office:date-value=\"" + when + "\"><text:p>" + when
            + "</text:p></table:table-cell><table:table-cell office:value-type=\"boolean\" office:boolean-value=\""
            + flag + "\"><text:p>" + (flag ? "TRUE" : "FALSE") + "</text:p></table:table-cell>"
            + "<table:table-cell table:number-columns-repeated=\"16379\"/></table:table-row>");
    }

    /**
     * Creates an ODS document with the table structures that spreadsheet applications write: header rows to repeat on
     * printed pages and row groups, repeated rows and cells, covered (merged) cells, and formatted text.
     *
     * The table "Structured" has the columns Sku, Qty, Note, and Day; after the header row:
     * - row 2 is in table:table-header-rows: A-1, 5, "x", 2025-03-01
     * - rows 3 to 5 are in a row group, rows 4 and 5 being one row element repeated twice: B-2, 6, "y", 2025-03-02
     *   and C-3, 7, "z", 2025-03-03
     * - row 6: D-4, then one cell repeated over Qty and Note: 8, and a covered cell (merged) for Day
     * - row 7 is empty
     * - row 8: E-5, 9, text with spaces, a tab, and a line break ("a   b", tab, "c", newline, "d"), 2025-03-08
     * followed by 1048000 repeated empty rows.
     *
     * @param path The path to create the file at
     */
    public static void createStructuredOds(String path) throws IOException {
        writeOds(path, out -> {
            out.write("<table:table table:name=\"Structured\">"
                + "<table:table-column table:number-columns-repeated=\"4\"/>"
                + "<table:table-column table:number-columns-repeated=\"1020\"/>"
                + "<table:table-row>" + str("Sku") + str("Qty") + str("Note") + str("Day")
                + "<table:table-cell table:number-columns-repeated=\"1020\"/></table:table-row>"
                + "<table:table-header-rows><table:table-row>" + str("A-1") + num(5) + str("x") + day("2025-03-01")
                + "</table:table-row></table:table-header-rows>"
                + "<table:table-row-group><table:table-row>" + str("B-2") + num(6) + str("y") + day("2025-03-02")
                + "</table:table-row><table:table-row table:number-rows-repeated=\"2\">" + str("C-3") + num(7)
                + str("z") + day("2025-03-03") + "</table:table-row></table:table-row-group>"
                + "<table:table-row>" + str("D-4") + "<table:table-cell office:value-type=\"float\" office:value=\"8\" "
                + "table:number-columns-repeated=\"2\"><text:p>8</text:p></table:table-cell>"
                + "<table:covered-table-cell/></table:table-row>"
                + "<table:table-row><table:table-cell table:number-columns-repeated=\"1024\"/></table:table-row>"
                + "<table:table-row>" + str("E-5") + num(9) + "<table:table-cell office:value-type=\"string\">"
                + "<text:p>a<text:s text:c=\"3\"/>b<text:tab/>c<text:line-break/>d</text:p></table:table-cell>"
                + day("2025-03-08") + "</table:table-row>"
                + "<table:table-row table:number-rows-repeated=\"1048000\">"
                + "<table:table-cell table:number-columns-repeated=\"1024\"/></table:table-row>"
                + "</table:table>");
        });
    }

    private static String str(String value) {
        return "<table:table-cell office:value-type=\"string\"><text:p>" + value + "</text:p></table:table-cell>";
    }

    private static String num(int value) {
        return "<table:table-cell office:value-type=\"float\" office:value=\"" + value + "\"><text:p>" + value
            + "</text:p></table:table-cell>";
    }

    private static String day(String value) {
        return "<table:table-cell office:value-type=\"date\" office:date-value=\"" + value + "\"><text:p>" + value
            + "</text:p></table:table-cell>";
    }

    /**
     * Creates an ODS document with currency and percentage cells.
     *
     * The table "Prices" has the columns Item, Price (currency cells in USD), and Share (percentage cells): A, 3.5,
     * 0.25 (25%); B, -1250.75, 1.5 (150%); and C, a currency cell without a value displayed as "n/a", and 0.
     *
     * @param path The path to create the file at
     */
    public static void createNumberFormatsOds(String path) throws IOException {
        writeOds(path, out -> {
            out.write("<table:table table:name=\"Prices\">"
                + "<table:table-column table:number-columns-repeated=\"3\"/>"
                + "<table:table-row>" + str("Item") + str("Price") + str("Share") + "</table:table-row>"
                + "<table:table-row>" + str("A") + currency("3.5", "$3.50") + percentage("0.25", "25%")
                + "</table:table-row>"
                + "<table:table-row>" + str("B") + currency("-1250.75", "-$1,250.75") + percentage("1.5", "150%")
                + "</table:table-row>"
                + "<table:table-row>" + str("C")
                + "<table:table-cell office:value-type=\"currency\" office:currency=\"USD\"><text:p>n/a</text:p>"
                + "</table:table-cell>" + percentage("0", "0%") + "</table:table-row>"
                + "</table:table>");
        });
    }

    private static String currency(String value, String display) {
        return "<table:table-cell office:value-type=\"currency\" office:currency=\"USD\" office:value=\"" + value
            + "\"><text:p>" + display + "</text:p></table:table-cell>";
    }

    private static String percentage(String value, String display) {
        return "<table:table-cell office:value-type=\"percentage\" office:value=\"" + value + "\"><text:p>" + display
            + "</text:p></table:table-cell>";
    }
}
