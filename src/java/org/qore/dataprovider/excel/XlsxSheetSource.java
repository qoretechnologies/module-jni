/*  XlsxSheetSource.java Copyright 2026 Qore Technologies, s.r.o.

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

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.ExcelNumberFormat;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.XLSBUnsupportedException;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Streams the rows of a worksheet of an OOXML (.xlsx) workbook.
 *
 * The workbook part, the shared strings table and the styles are read when the sheet is selected; the sheet's XML is
 * then pulled with StAX one row at a time, so the memory used does not depend on the number of rows (only the
 * shared strings table, which the cells refer to by index, is held in memory).
 *
 * Cell values have the semantics of the POI usermodel (XSSFCell) that the record reader used before it streamed:
 * cell types, formula cells giving their cached result, shared, inline and rich text strings, date formats from the
 * cell or column style, the 1904 date system, and the cells of array formulas.
 *
 * The package is read from a file; an input stream is first copied to a temporary file, which is deleted as soon as
 * the package is open where the platform allows it, and otherwise when the source is closed.
 */
final class XlsxSheetSource implements SheetSource {
    static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String TEMP_PREFIX = "qore-xlsx-";

    // the package; null once closed
    private OPCPackage pkg;
    // the temporary copy of an input stream if it still has to be deleted
    private Path temp_file;
    // the sheet's part; null for a chart sheet, which has no cells
    private PackagePart sheet_part;
    // the shared strings, decoded as XSSFRichTextString.getString() decodes them
    private ArrayList<String> strings = new ArrayList<String>();
    // the cell styles; null if the workbook has none
    private StylesTable styles;
    private boolean date1904;
    // whether each cell style has a date format, by style index
    private final HashMap<Integer, Boolean> date_styles = new HashMap<Integer, Boolean>();
    private final XMLInputFactory xml_factory = XMLHelper.newXMLInputFactory();

    /**
     * A worksheet of the workbook
     */
    private static final class SheetInfo {
        final String name;
        // null for a chart sheet
        final PackagePart part;

        SheetInfo(String name, PackagePart part) {
            this.name = name;
            this.part = part;
        }
    }

    /**
     * The workbook part's information
     */
    private static final class WorkbookInfo {
        PackagePart part;
        boolean date1904;
        ArrayList<SheetInfo> sheets = new ArrayList<SheetInfo>();
    }

    private XlsxSheetSource(OPCPackage pkg, Path temp_file) {
        this.pkg = pkg;
        this.temp_file = temp_file;
    }

    /**
     * Opens the given worksheet of a workbook in an input stream; the stream is read to its end
     *
     * @param in the workbook data
     * @param sheet_name the sheet name or 0-based index as a string; null or empty for the first sheet
     */
    static XlsxSheetSource open(InputStream in, String sheet_name) throws IOException, InvalidFormatException {
        Path tmp = spool(in);
        return open(tmp.toFile(), tmp, sheet_name);
    }

    /**
     * Opens the given worksheet of a workbook file
     *
     * @param file the workbook file
     * @param sheet_name the sheet name or 0-based index as a string; null or empty for the first sheet
     */
    static XlsxSheetSource open(File file, String sheet_name) throws IOException, InvalidFormatException {
        return open(file, null, sheet_name);
    }

    private static XlsxSheetSource open(File file, Path tmp, String sheet_name) throws IOException,
            InvalidFormatException {
        XlsxSheetSource rv = null;
        try {
            rv = new XlsxSheetSource(openPackage(file), tmp);
            rv.deleteTempFile(true);
            rv.selectSheet(sheet_name);
            return rv;
        } catch (Throwable t) {
            try {
                if (rv != null) {
                    rv.close();
                } else if (tmp != null) {
                    Files.deleteIfExists(tmp);
                }
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
    }

    /**
     * Returns the names of the worksheets of a workbook in an input stream; the stream is read to its end
     */
    static ArrayList<String> getSheetNames(InputStream in) throws IOException, InvalidFormatException {
        Path tmp = spool(in);
        try {
            return getSheetNames(tmp.toFile());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Returns the names of the worksheets of a workbook file
     */
    static ArrayList<String> getSheetNames(File file) throws IOException, InvalidFormatException {
        OPCPackage pkg = openPackage(file);
        try {
            WorkbookInfo info = readWorkbook(pkg, XMLHelper.newXMLInputFactory());
            ArrayList<String> rv = new ArrayList<String>();
            for (SheetInfo sheet : info.sheets) {
                rv.add(sheet.name);
            }
            return rv;
        } finally {
            pkg.revert();
        }
    }

    private static OPCPackage openPackage(File file) throws InvalidFormatException {
        return OPCPackage.open(file, PackageAccess.READ);
    }

    /**
     * Copies an input stream to a temporary file
     */
    private static Path spool(InputStream in) throws IOException {
        Path tmp = Files.createTempFile(TEMP_PREFIX, ".xlsx");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
        return tmp;
    }

    /**
     * Deletes the temporary file if there is one
     *
     * @param while_open true if the package is open: a platform that does not allow open files to be deleted then
     * keeps the file until the source is closed
     */
    private void deleteTempFile(boolean while_open) throws IOException {
        if (temp_file == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp_file);
        } catch (IOException e) {
            if (while_open) {
                // the file is deleted when the package is closed
                return;
            }
            throw e;
        }
        temp_file = null;
    }

    /**
     * Reads the workbook part and selects the sheet as XSSFWorkbook.getSheet() and getSheetAt() do
     */
    private void selectSheet(String sheet_name) throws IOException, InvalidFormatException {
        WorkbookInfo info = readWorkbook(pkg, xml_factory);
        SheetInfo sheet = null;
        if (sheet_name == null || sheet_name.isEmpty()) {
            sheet = getSheetAt(info, 0);
        } else {
            // sheet names are matched without regard to case, as XSSFWorkbook.getSheet() does
            for (SheetInfo s : info.sheets) {
                if (sheet_name.equalsIgnoreCase(s.name)) {
                    sheet = s;
                    break;
                }
            }
            if (sheet == null) {
                try {
                    int sheet_no = Integer.parseInt(sheet_name);
                    sheet = getSheetAt(info, sheet_no);
                } catch (NumberFormatException e) {
                    // ignore exception
                }
            }
            if (sheet == null) {
                throw new RuntimeException(String.format("sheet %s is unknown", sheet_name));
            }
        }
        sheet_part = sheet.part;
        date1904 = info.date1904;

        // the shared strings and styles related to the workbook part; as with XSSFWorkbook, the last one wins
        PackagePart sst_part = null;
        PackagePart styles_part = null;
        for (PackageRelationship rel : info.part.getRelationships()) {
            if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                continue;
            }
            String type = rel.getRelationshipType();
            if (XSSFRelation.SHARED_STRINGS.getRelation().equals(type)) {
                PackagePart part = info.part.getRelatedPart(rel);
                if (part != null) {
                    sst_part = part;
                }
            } else if (XSSFRelation.STYLES.getRelation().equals(type)) {
                PackagePart part = info.part.getRelatedPart(rel);
                if (part != null) {
                    styles_part = part;
                }
            }
        }
        if (sst_part != null) {
            readSharedStrings(sst_part);
        }
        if (styles_part != null) {
            styles = new StylesTable(styles_part);
        }
    }

    /**
     * Returns the sheet at the given index; throws the exception that XSSFWorkbook.getSheetAt() throws for an
     * invalid index
     */
    private static SheetInfo getSheetAt(WorkbookInfo info, int index) {
        int last = info.sheets.size() - 1;
        if (index < 0 || index > last) {
            String range = last == -1 ? "(no sheets)" : "(0.." + last + ")";
            throw new IllegalArgumentException("Sheet index (" + index + ") is out of range " + range);
        }
        return info.sheets.get(index);
    }

    /**
     * Reads the workbook part: the date system and the worksheets and chart sheets in order, as XSSFWorkbook
     * lists them
     */
    private static WorkbookInfo readWorkbook(OPCPackage pkg, XMLInputFactory factory) throws IOException,
            InvalidFormatException {
        PackageRelationship core = pkg.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT)
            .getRelationship(0);
        if (core == null) {
            if (pkg.getRelationshipsByType(PackageRelationshipTypes.STRICT_CORE_DOCUMENT).getRelationship(0)
                    != null) {
                throw new POIXMLException("Strict OOXML isn't currently supported, please see bug #57699");
            }
            throw new POIXMLException("OOXML file structure broken/invalid - no core document found!");
        }
        WorkbookInfo info = new WorkbookInfo();
        info.part = pkg.getPart(core);
        if (info.part == null) {
            throw new POIXMLException("OOXML file structure broken/invalid - core document '" + core.getTargetURI()
                + "' not found.");
        }
        if (XSSFRelation.XLSB_BINARY_WORKBOOK.getContentType().equals(info.part.getContentType())) {
            throw new XLSBUnsupportedException();
        }

        ArrayList<String[]> sheets = new ArrayList<String[]>();
        try (InputStream in = info.part.getInputStream()) {
            XMLStreamReader xr = factory.createXMLStreamReader(in);
            try {
                int depth = 0;
                boolean in_sheets = false;
                while (xr.hasNext()) {
                    int ev = xr.next();
                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        ++depth;
                        String ln = xr.getLocalName();
                        boolean main = NS_MAIN.equals(xr.getNamespaceURI());
                        if (depth == 1) {
                            if (!main || !"workbook".equals(ln)) {
                                throw new POIXMLException(String.format("the OOXML package is not a spreadsheet "
                                    + "workbook (its main part is {%s}%s)", xr.getNamespaceURI(), ln));
                            }
                        } else if (depth == 2 && main && "workbookPr".equals(ln)) {
                            String v = xr.getAttributeValue(null, "date1904");
                            if (v != null) {
                                v = v.trim();
                                info.date1904 = "1".equals(v) || "true".equals(v);
                            }
                        } else if (depth == 2 && main && "sheets".equals(ln)) {
                            in_sheets = true;
                        } else if (depth == 3 && in_sheets && main && "sheet".equals(ln)) {
                            sheets.add(new String[]{xr.getAttributeValue(null, "name"),
                                xr.getAttributeValue(NS_REL, "id")});
                        }
                    } else if (ev == XMLStreamConstants.END_ELEMENT) {
                        if (depth == 2) {
                            in_sheets = false;
                        }
                        --depth;
                    }
                }
            } finally {
                xr.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("invalid workbook XML: " + e.getMessage(), e);
        }

        // only sheets whose parts exist are part of the workbook, as with XSSFWorkbook
        for (String[] sheet : sheets) {
            if (sheet[1] == null) {
                continue;
            }
            PackageRelationship rel = info.part.getRelationship(sheet[1]);
            if (rel == null || rel.getTargetMode() == TargetMode.EXTERNAL) {
                continue;
            }
            String type = rel.getRelationshipType();
            boolean chart = XSSFRelation.CHARTSHEET.getRelation().equals(type);
            if (!chart && !XSSFRelation.WORKSHEET.getRelation().equals(type)) {
                continue;
            }
            PackagePart part = info.part.getRelatedPart(rel);
            if (part == null) {
                continue;
            }
            info.sheets.add(new SheetInfo(sheet[0], chart ? null : part));
        }
        return info;
    }

    /**
     * Reads the shared strings table; each string has the value that XSSFRichTextString.getString() gives for it
     */
    private void readSharedStrings(PackagePart part) throws IOException {
        try (InputStream in = part.getInputStream()) {
            XMLStreamReader xr = xml_factory.createXMLStreamReader(in);
            try {
                while (xr.hasNext()) {
                    if (xr.next() == XMLStreamConstants.START_ELEMENT && NS_MAIN.equals(xr.getNamespaceURI())
                            && "si".equals(xr.getLocalName())) {
                        strings.add(decode(readRichText(xr)));
                    }
                }
            } finally {
                xr.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("invalid shared strings XML: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a rich text string element (CT_Rst: shared string items and inline strings) up to its end tag
     *
     * @return the text as CTRst gives it to XSSFRichTextString.getString() before decoding: the text of the runs if
     * there are any, otherwise the plain text, which is null if missing; phonetic runs are not part of the text
     */
    private static String readRichText(XMLStreamReader xr) throws XMLStreamException {
        String t = null;
        StringBuilder runs = null;
        int depth = 1;
        while (depth > 0) {
            int ev = xr.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (depth == 1 && NS_MAIN.equals(xr.getNamespaceURI())) {
                    String ln = xr.getLocalName();
                    if ("t".equals(ln)) {
                        t = xr.getElementText();
                        continue;
                    }
                    if ("r".equals(ln)) {
                        if (runs == null) {
                            runs = new StringBuilder();
                        }
                        runs.append(readRunText(xr));
                        continue;
                    }
                }
                skipElement(xr);
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                --depth;
            }
        }
        return runs != null ? runs.toString() : t;
    }

    /**
     * Reads a rich text run up to its end tag and returns its text; a run without text gives "null", as appending
     * the text of such a run does in XSSFRichTextString.getString()
     */
    private static String readRunText(XMLStreamReader xr) throws XMLStreamException {
        String t = null;
        while (true) {
            int ev = xr.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (NS_MAIN.equals(xr.getNamespaceURI()) && "t".equals(xr.getLocalName())) {
                    t = xr.getElementText();
                } else {
                    skipElement(xr);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                return String.valueOf(t);
            }
        }
    }

    /**
     * Skips the current element, which has just been started, up to and including its end tag
     */
    private static void skipElement(XMLStreamReader xr) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int ev = xr.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                ++depth;
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                --depth;
            }
        }
    }

    /**
     * Decodes the _xHHHH_ escapes of OOXML strings as XSSFRichTextString.getString() does
     */
    private static String decode(String str) {
        if (str == null || !str.contains("_x")) {
            return str;
        }
        return new XSSFRichTextString(str).getString();
    }

    /**
     * Returns true if the given cell style has a date format, as DateUtil.isCellDateFormatted() determines it
     */
    private boolean isDateStyle(int style_idx) {
        Boolean rv = date_styles.get(style_idx);
        if (rv == null) {
            XSSFCellStyle style = styles.getStyleAt(style_idx);
            ExcelNumberFormat nf = ExcelNumberFormat.from(style);
            rv = nf != null && DateUtil.isADateFormat(nf);
            date_styles.put(style_idx, rv);
        }
        return rv;
    }

    @Override
    public RowReader openRows() throws IOException {
        if (pkg == null) {
            throw new IOException("the workbook is closed");
        }
        if (sheet_part == null) {
            // a chart sheet has no cells
            return new RowReader() {
                @Override
                public Row next() {
                    return null;
                }

                @Override
                public void close() {
                }
            };
        }
        return new XlsxRowReader();
    }

    @Override
    public void close() throws IOException {
        Throwable error = null;
        if (pkg != null) {
            OPCPackage p = pkg;
            pkg = null;
            try {
                // a read-only package is closed without saving
                p.revert();
            } catch (Throwable t) {
                error = t;
            }
        }
        try {
            deleteTempFile(false);
        } catch (Throwable t) {
            if (error == null) {
                error = t;
            } else {
                error.addSuppressed(t);
            }
        }
        strings = null;
        styles = null;
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
     * One pass over the rows of the sheet
     */
    private final class XlsxRowReader implements RowReader {
        private InputStream in;
        private XMLStreamReader xr;
        private boolean in_sheet_data = false;
        // the 0-based number of the last row read
        private int last_row = -1;
        // column definitions: {min, max, style}, 1-based columns, style -1 if not set
        private final ArrayList<long[]> cols = new ArrayList<long[]>();
        // the ranges of the array formulas of the rows read that may hold the cells of later rows
        private final ArrayList<CellRangeAddress> array_ranges = new ArrayList<CellRangeAddress>();

        XlsxRowReader() throws IOException {
            in = sheet_part.getInputStream();
            try {
                xr = xml_factory.createXMLStreamReader(in);
            } catch (XMLStreamException e) {
                closeInput(e);
                throw new IOException("invalid worksheet XML: " + e.getMessage(), e);
            } catch (RuntimeException | Error e) {
                closeInput(e);
                throw e;
            }
        }

        /**
         * Closes the input stream after an error opening the reader
         */
        private void closeInput(Throwable error) {
            try {
                in.close();
            } catch (Throwable t) {
                error.addSuppressed(t);
            }
            in = null;
        }

        @Override
        public Row next() throws IOException {
            if (xr == null) {
                return null;
            }
            try {
                while (xr.hasNext()) {
                    int ev = xr.next();
                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        if (!NS_MAIN.equals(xr.getNamespaceURI())) {
                            skipElement(xr);
                            continue;
                        }
                        String ln = xr.getLocalName();
                        if (in_sheet_data) {
                            if ("row".equals(ln)) {
                                return readRow();
                            }
                            skipElement(xr);
                        } else if ("sheetData".equals(ln)) {
                            in_sheet_data = true;
                        } else if ("col".equals(ln)) {
                            readCol();
                        }
                        // other elements before the sheet data are descended into; only column definitions
                        // are used from them
                    } else if (ev == XMLStreamConstants.END_ELEMENT && in_sheet_data) {
                        // the end of the sheet data: the rest of the sheet has no cells
                        close();
                        return null;
                    }
                }
                close();
                return null;
            } catch (XMLStreamException e) {
                throw new IOException("invalid worksheet XML: " + e.getMessage(), e);
            }
        }

        private void readCol() throws IOException {
            String style = xr.getAttributeValue(null, "style");
            cols.add(new long[]{parseLong(xr.getAttributeValue(null, "min"), "col min"),
                parseLong(xr.getAttributeValue(null, "max"), "col max"),
                style == null ? -1 : parseLong(style, "col style")});
        }

        /**
         * Returns the style index of the given 0-based column, or -1 if the column has no definition, as
         * ColumnHelper.getColDefaultStyle() gives it after merging overlapping column definitions
         */
        private int getColumnStyle(int col) {
            long col1 = col + 1L;
            boolean found = false;
            long style = 0;
            for (long[] c : cols) {
                if (c[0] <= col1 && c[1] >= col1) {
                    found = true;
                    if (c[2] != -1) {
                        style = c[2];
                    }
                }
            }
            return found ? Math.toIntExact(style) : -1;
        }

        private XlsxRow readRow() throws XMLStreamException, IOException {
            String r = xr.getAttributeValue(null, "r");
            int row = r == null ? last_row + 1 : Math.toIntExact(parseLong(r, "row number") - 1);
            if (row <= last_row) {
                throw new IOException(String.format("worksheet row %d follows row %d; the rows of a worksheet must "
                    + "be in ascending order to be read", row + 1, last_row + 1));
            }
            last_row = row;
            // array formulas that end before this row hold no more cells
            for (Iterator<CellRangeAddress> i = array_ranges.iterator(); i.hasNext(); ) {
                if (i.next().getLastRow() < row) {
                    i.remove();
                }
            }

            XlsxRow rv = new XlsxRow(row);
            int max_col = -1;
            while (true) {
                int ev = xr.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if (NS_MAIN.equals(xr.getNamespaceURI()) && "c".equals(xr.getLocalName())) {
                        XlsxCell cell = readCell(row, max_col + 1);
                        rv.cells.put(cell.col, cell);
                        if (cell.col > max_col) {
                            max_col = cell.col;
                        }
                    } else {
                        skipElement(xr);
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    return rv;
                }
            }
        }

        private XlsxCell readCell(int row, int next_col) throws XMLStreamException, IOException {
            String r = xr.getAttributeValue(null, "r");
            String t = xr.getAttributeValue(null, "t");
            String s = xr.getAttributeValue(null, "s");
            XlsxCell cell = new XlsxCell(r == null ? next_col : getColumn(r), t == null ? "n" : t.trim(),
                s == null ? -1 : parseLong(s, "cell style"));
            while (true) {
                int ev = xr.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if (NS_MAIN.equals(xr.getNamespaceURI())) {
                        String ln = xr.getLocalName();
                        if ("v".equals(ln)) {
                            cell.v = xr.getElementText();
                            continue;
                        }
                        if ("f".equals(ln)) {
                            String ft = xr.getAttributeValue(null, "t");
                            if (ft != null) {
                                ft = ft.trim();
                            }
                            // POI does not support data table formulas; their cells are typed by their values
                            if (!"dataTable".equals(ft)) {
                                cell.formula = true;
                            }
                            String ref = xr.getAttributeValue(null, "ref");
                            if ("array".equals(ft) && ref != null) {
                                array_ranges.add(CellRangeAddress.valueOf(ref));
                            }
                            skipElement(xr);
                            continue;
                        }
                        if ("is".equals(ln)) {
                            cell.has_is = true;
                            cell.is = readRichText(xr);
                            continue;
                        }
                    }
                    skipElement(xr);
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    break;
                }
            }
            if (!cell.formula) {
                // the cells of an array formula other than the one holding it are formula cells as well
                for (CellRangeAddress range : array_ranges) {
                    if (range.isInRange(row, cell.col)) {
                        cell.formula = true;
                        break;
                    }
                }
            }
            return cell;
        }

        @Override
        public void close() throws IOException {
            Throwable error = null;
            if (xr != null) {
                try {
                    xr.close();
                } catch (Throwable t) {
                    error = t;
                }
                xr = null;
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable t) {
                    if (error == null) {
                        error = t;
                    } else {
                        error.addSuppressed(t);
                    }
                }
                in = null;
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

        private final class XlsxRow implements Row {
            private final int row;
            private final HashMap<Integer, XlsxCell> cells = new HashMap<Integer, XlsxCell>();

            XlsxRow(int row) {
                this.row = row;
            }

            @Override
            public int getRowNum() {
                return row;
            }

            @Override
            public Cell getCell(int col) {
                return cells.get(col);
            }
        }

        /**
         * A cell with the semantics of XSSFCell
         */
        private final class XlsxCell implements Cell {
            final int col;
            // the cell type attribute: b, n, e, s, str, or inlineStr
            final String t;
            // the style index; -1 if not set
            final long s;
            // the value; null if not set
            String v;
            // true for a cell with a formula or in the range of an array formula
            boolean formula = false;
            // the inline string, if set
            boolean has_is = false;
            String is;

            XlsxCell(int col, String t, long s) {
                this.col = col;
                this.t = t;
                this.s = s;
            }

            private CellType getBaseCellType(boolean blank_cells) {
                switch (t) {
                    case "b":
                        return CellType.BOOLEAN;
                    case "n":
                        if (v == null && blank_cells) {
                            return CellType.BLANK;
                        }
                        return CellType.NUMERIC;
                    case "e":
                        return CellType.ERROR;
                    case "s":
                    case "inlineStr":
                    case "str":
                        return CellType.STRING;
                    default:
                        throw new IllegalStateException("Illegal cell type: " + t);
                }
            }

            @Override
            public CellType getCellType() {
                return formula ? CellType.FORMULA : getBaseCellType(true);
            }

            @Override
            public CellType getCachedFormulaResultType() {
                if (!formula) {
                    throw new IllegalStateException("Only formula cells have cached results");
                }
                return getBaseCellType(false);
            }

            @Override
            public boolean getBooleanCellValue() {
                CellType type = getCellType();
                switch (type) {
                    case BLANK:
                        return false;
                    case BOOLEAN:
                    case FORMULA:
                        return "1".equals(v);
                    default:
                        throw typeMismatch(CellType.BOOLEAN, type, false);
                }
            }

            @Override
            public double getNumericCellValue() {
                CellType type = formula ? getBaseCellType(false) : getCellType();
                switch (type) {
                    case BLANK:
                        return 0.0;
                    case NUMERIC:
                        if (v == null || v.isEmpty()) {
                            return 0.0;
                        }
                        try {
                            return Double.parseDouble(v.trim());
                        } catch (NumberFormatException e) {
                            throw typeMismatch(CellType.NUMERIC, CellType.STRING, false);
                        }
                    default:
                        throw typeMismatch(CellType.NUMERIC, type, false);
                }
            }

            @Override
            public String getStringCellValue() {
                CellType type = getCellType();
                switch (type) {
                    case BLANK:
                        return "";
                    case STRING:
                        return findStringValue();
                    case FORMULA: {
                        CellType cached = getBaseCellType(false);
                        if (cached != CellType.STRING) {
                            throw typeMismatch(CellType.STRING, cached, true);
                        }
                        return findStringValue();
                    }
                    default:
                        throw typeMismatch(CellType.STRING, type, false);
                }
            }

            private String findStringValue() {
                if ("inlineStr".equals(t)) {
                    if (has_is) {
                        return decode(is);
                    }
                    return v != null ? decode(v) : "";
                }
                if ("str".equals(t)) {
                    return v != null ? decode(v) : "";
                }
                if (v == null) {
                    return "";
                }
                // an invalid shared string index gives an empty string, as in XSSFCell
                try {
                    return strings.get(Integer.parseInt(v.trim()));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return "";
                }
            }

            /**
             * Returns the index of the cell's style as XSSFCell.getCellStyle() determines it: its own style, else
             * the column's style, else the default style
             */
            private int getStyleIndex() {
                if (s != -1 && styles.getNumCellStyles() > 0) {
                    int idx = Math.toIntExact(s);
                    // StylesTable.getStyleAt() has no style for an index out of range
                    if (idx >= 0 && idx < styles.getNumCellStyles()) {
                        return idx;
                    }
                }
                int idx = getColumnStyle(col);
                return idx == -1 ? 0 : idx;
            }

            @Override
            public boolean isCellDateFormatted() {
                double d = getNumericCellValue();
                if (!DateUtil.isValidExcelDate(d) || styles == null) {
                    return false;
                }
                return isDateStyle(getStyleIndex());
            }

            @Override
            public LocalDateTime getLocalDateTimeCellValue() {
                if (getCellType() == CellType.BLANK) {
                    return null;
                }
                return DateUtil.getLocalDateTime(getNumericCellValue(), date1904);
            }
        }
    }

    private static RuntimeException typeMismatch(CellType expected, CellType actual, boolean formula) {
        return new IllegalStateException("Cannot get a " + expected + " value from a " + actual + " "
            + (formula ? "formula " : "") + "cell");
    }

    private static long parseLong(String str, String what) throws IOException {
        if (str == null) {
            throw new IOException(String.format("invalid worksheet XML: missing %s", what));
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            throw new IOException(String.format("invalid worksheet XML: invalid %s %s", what, str), e);
        }
    }

    /**
     * Returns the 0-based column of a cell reference such as "B12"
     */
    private static int getColumn(String ref) {
        int col = 0;
        int i = 0;
        int len = ref.length();
        while (i < len) {
            char c = ref.charAt(i);
            if (c < 'A' || c > 'Z') {
                break;
            }
            col = col * 26 + (c - 'A' + 1);
            ++i;
        }
        if (i == 0 || i == len || i > 3) {
            // not a plain reference: let POI parse it
            return new CellReference(ref).getCol();
        }
        for (int j = i; j < len; ++j) {
            char c = ref.charAt(j);
            if (c < '0' || c > '9') {
                return new CellReference(ref).getCol();
            }
        }
        return col - 1;
    }
}
