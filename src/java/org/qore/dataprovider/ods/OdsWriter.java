/*  OdsWriter.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.dom.OdfDocumentNamespace;
import org.odftoolkit.odfdom.dom.element.number.NumberAmPmElement;
import org.odftoolkit.odfdom.dom.element.number.NumberDateStyleElement;
import org.odftoolkit.odfdom.dom.element.number.NumberDayElement;
import org.odftoolkit.odfdom.dom.element.number.NumberDayOfWeekElement;
import org.odftoolkit.odfdom.dom.element.number.NumberHoursElement;
import org.odftoolkit.odfdom.dom.element.number.NumberMinutesElement;
import org.odftoolkit.odfdom.dom.element.number.NumberMonthElement;
import org.odftoolkit.odfdom.dom.element.number.NumberNumberElement;
import org.odftoolkit.odfdom.dom.element.number.NumberScientificNumberElement;
import org.odftoolkit.odfdom.dom.element.number.NumberSecondsElement;
import org.odftoolkit.odfdom.dom.element.number.NumberTextContentElement;
import org.odftoolkit.odfdom.dom.element.number.NumberTextElement;
import org.odftoolkit.odfdom.dom.element.number.NumberYearElement;
import org.odftoolkit.odfdom.dom.element.style.StyleMapElement;
import org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement;
import org.odftoolkit.odfdom.dom.style.OdfStyleFamily;
import org.odftoolkit.odfdom.incubator.doc.office.OdfOfficeAutomaticStyles;
import org.odftoolkit.odfdom.incubator.doc.style.OdfStyle;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.odftoolkit.odfdom.pkg.OdfFileDom;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Closeable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.qore.jni.Hash;
import org.qore.dataprovider.format.SpreadsheetFormatCode;

/**
 * Helper class for writing ODS (OpenDocument Spreadsheet) files from Qore.
 */
public class OdsWriter implements Closeable {
    private OdfSpreadsheetDocument document;
    private OdfTable table;
    private ArrayList<String> headers = new ArrayList<String>();
    private int currentRow = 0;
    private boolean headersWritten = false;
    private String sheetName;
    // the display format of each column by header name
    private LinkedHashMap<String, SpreadsheetFormatCode> columnFormats =
        new LinkedHashMap<String, SpreadsheetFormatCode>();
    // the name of the cell style of each distinct format code, so each data style is written once
    private HashMap<String, String> formatStyles = new HashMap<String, String>();
    // the number of format codes written as data styles, for unique data style names
    private int dataStyleCount = 0;
    // the format code and cell style name of each column by position, set when the columns are known
    private SpreadsheetFormatCode[] columnCodes;
    private String[] columnStyles;

    /**
     * Creates a new OdsWriter.
     *
     * @param sheet_name The name of the worksheet to create
     */
    public OdsWriter(String sheet_name) {
        this.sheetName = sheet_name != null && !sheet_name.isEmpty() ? sheet_name : "Sheet1";
        try {
            document = OdfSpreadsheetDocument.newSpreadsheetDocument();
            // Remove the default empty table and create our own
            java.util.List<OdfTable> tables = document.getTableList();
            if (tables != null && !tables.isEmpty()) {
                // Rename the first table
                table = tables.get(0);
                table.setTableName(this.sheetName);
            } else {
                table = OdfTable.newTable(document);
                table.setTableName(this.sheetName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ODS document: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new OdsWriter with default settings.
     */
    public OdsWriter() {
        this("Sheet1");
    }

    /**
     * Sets the headers for the worksheet.
     */
    public void setHeaders(String[] headers) {
        this.headers.clear();
        for (String h : headers) {
            this.headers.add(h);
        }
    }

    /**
     * Sets the headers for the worksheet.
     */
    public void setHeaders(ArrayList<String> headers) {
        this.headers = new ArrayList<String>(headers);
    }

    /**
     * Writes the header row.
     */
    public void writeHeaders() {
        if (headersWritten || headers.isEmpty()) {
            return;
        }
        for (int i = 0; i < headers.size(); i++) {
            OdfTableCell cell = table.getCellByPosition(i, currentRow);
            cell.setStringValue(headers.get(i));
        }
        currentRow++;
        headersWritten = true;
    }

    /**
     * Writes a data row from a hash.
     */
    public void writeRow(Hash data) {
        // With no predefined headers, the first record's keys define the columns; they are taken before any row is
        // written, so the header row is always written first and the first record never has to be copied down a row
        // (copying lost the value types of the first record, e.g. dates became strings)
        if (headers.isEmpty()) {
            for (Object key : data.keySet()) {
                headers.add(key.toString());
            }
        }
        resolveColumnStyles();
        writeHeaders();

        for (int i = 0; i < headers.size(); i++) {
            Object value = data.get(headers.get(i));
            OdfTableCell cell = table.getCellByPosition(i, currentRow);
            SpreadsheetFormatCode code = columnCodes[i] != null && columnCodes[i].shows(value) ? columnCodes[i] : null;
            setCellValue(cell, value, code);
            if (code != null) {
                cell.getOdfElement().setTableStyleNameAttribute(columnStyles[i]);
            }
        }
        currentRow++;
    }

    /**
     * Returns why a display format code is not accepted, or null if it is
     *
     * @param code the format code, such as {@code dd.mm.yyyy}, {@code #,##0.00} or {@code 0.0%}
     * @return the reason the format code is not accepted, or null if it is valid
     */
    public static String formatCodeIssue(String code) {
        return SpreadsheetFormatCode.issue(code);
    }

    /**
     * Sets the display format of columns
     *
     * Each value in a column is written as its type (a number stays a number, a date a date) and shown with the
     * column's format code, which is written as an OpenDocument data style; a value that the code does not show (a
     * string in a number column, for example) is written as it is without the format.  A number in a column with a
     * percent code is written as a percentage cell, which holds the fraction and is read back as a number.  A
     * column without a format code is written as before.
     *
     * @param formats the format code of each column by its header name; see {@link SpreadsheetFormatCode}
     * @throws IllegalArgumentException a format code is not valid, or rows have already been written; the message
     * names the column
     */
    public void setColumnFormats(Hash formats) {
        if (currentRow > 0) {
            throw new IllegalArgumentException("column formats must be set before any row is written");
        }
        LinkedHashMap<String, SpreadsheetFormatCode> codes = new LinkedHashMap<String, SpreadsheetFormatCode>();
        for (Map.Entry<String, Object> e : formats.entrySet()) {
            Object value = e.getValue();
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(String.format("the format of column \"%s\" is not a string",
                    e.getKey()));
            }
            try {
                codes.put(e.getKey(), SpreadsheetFormatCode.parse((String) value));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(String.format("column \"%s\": %s", e.getKey(), ex.getMessage()),
                    ex);
            }
        }
        columnFormats = codes;
    }

    /**
     * Resolves the format code and cell style of each column once the columns are known
     *
     * @throws IllegalArgumentException a column format names a column that is not written
     */
    private void resolveColumnStyles() {
        if (columnStyles != null) {
            return;
        }
        for (String column : columnFormats.keySet()) {
            if (!headers.contains(column)) {
                throw new IllegalArgumentException(String.format("a format is given for column \"%s\", which is not "
                    + "one of the columns written: %s", column, headers));
            }
        }
        SpreadsheetFormatCode[] codes = new SpreadsheetFormatCode[headers.size()];
        String[] styles = new String[headers.size()];
        for (int i = 0; i < headers.size(); ++i) {
            SpreadsheetFormatCode code = columnFormats.get(headers.get(i));
            if (code == null) {
                continue;
            }
            String style = formatStyles.get(code.getCode());
            if (style == null) {
                style = createCellStyle(code);
                formatStyles.put(code.getCode(), style);
            }
            codes[i] = code;
            styles[i] = style;
        }
        columnCodes = codes;
        columnStyles = styles;
    }

    /**
     * Creates the data style of a format code and a cell style using it, and returns the name of the cell style
     *
     * The data style is built from the parsed format code rather than with odfdom's format string parsers, which
     * do not support sections and loop forever on some invalid codes.  A code with more than one section is a data
     * style per section: the last one (negative, or zero with three sections) is the one the cell style uses, with
     * style:map conditions selecting the others, as LibreOffice writes them.
     */
    private String createCellStyle(SpreadsheetFormatCode code) {
        OdfContentDom dom;
        try {
            dom = document.getContentDom();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get the ODS content: " + e.getMessage(), e);
        }
        OdfOfficeAutomaticStyles auto_styles = dom.getOrCreateAutomaticStyles();
        String name = "QoreFmt" + (++dataStyleCount);

        List<SpreadsheetFormatCode.Section> sections = code.getSections();
        int last = sections.size() - 1;
        ArrayList<String> section_names = new ArrayList<String>();
        for (int i = 0; i < last; ++i) {
            String section_name = name + "P" + i;
            createDataStyle(dom, auto_styles, code.getKind(), sections.get(i), section_name);
            section_names.add(section_name);
        }
        OdfElement main = createDataStyle(dom, auto_styles, code.getKind(), sections.get(last), name);
        if (sections.size() == 2) {
            addMap(dom, main, "value()>=0", section_names.get(0));
        } else if (sections.size() == 3) {
            addMap(dom, main, "value()>0", section_names.get(0));
            addMap(dom, main, "value()<0", section_names.get(1));
        }

        OdfStyle cell_style = auto_styles.newStyle(OdfStyleFamily.TableCell);
        cell_style.setStyleDataStyleNameAttribute(name);
        return cell_style.getStyleNameAttribute();
    }

    /** Creates the data style of one section of a format code */
    private static OdfElement createDataStyle(OdfFileDom dom, OdfOfficeAutomaticStyles auto_styles,
            SpreadsheetFormatCode.Kind kind, SpreadsheetFormatCode.Section section, String name) {
        OdfElement style;
        switch (kind) {
            case NUMBER:
                style = auto_styles.newNumberNumberStyleElement(name);
                break;
            case PERCENT:
                style = auto_styles.newNumberPercentageStyleElement(name);
                break;
            case DATE: {
                NumberDateStyleElement date_style = auto_styles.newNumberDateStyleElement(name);
                // the parts are shown as given, not as the language would show them
                date_style.setNumberFormatSourceAttribute("fixed");
                style = date_style;
                break;
            }
            default:
                style = auto_styles.newNumberTextStyleElement(name);
                break;
        }
        if (section.color != null) {
            StyleTextPropertiesElement props = new StyleTextPropertiesElement(dom);
            props.setFoColorAttribute(SpreadsheetFormatCode.COLORS.get(section.color));
            style.appendChild(props);
        }
        for (SpreadsheetFormatCode.Part part : section.parts) {
            style.appendChild(createPart(dom, part));
        }
        return style;
    }

    /** Creates the data style element of a part of a format code section */
    private static OdfElement createPart(OdfFileDom dom, SpreadsheetFormatCode.Part part) {
        if (part instanceof SpreadsheetFormatCode.Literal) {
            NumberTextElement text = new NumberTextElement(dom);
            text.setTextContent(((SpreadsheetFormatCode.Literal) part).text);
            return text;
        }
        if (part instanceof SpreadsheetFormatCode.TextValue) {
            return new NumberTextContentElement(dom);
        }
        if (part instanceof SpreadsheetFormatCode.Digits) {
            SpreadsheetFormatCode.Digits digits = (SpreadsheetFormatCode.Digits) part;
            if (digits.scientific) {
                NumberScientificNumberElement number = new NumberScientificNumberElement(dom);
                number.setNumberDecimalPlacesAttribute(digits.decimalPlaces);
                number.setNumberMinIntegerDigitsAttribute(digits.minIntegerDigits);
                number.setNumberMinExponentDigitsAttribute(digits.minExponentDigits);
                if (digits.grouping) {
                    number.setNumberGroupingAttribute(true);
                }
                setNumberAttribute(number, "min-decimal-places", digits.minDecimalPlaces);
                return number;
            }
            NumberNumberElement number = new NumberNumberElement(dom);
            number.setNumberDecimalPlacesAttribute(digits.decimalPlaces);
            number.setNumberMinIntegerDigitsAttribute(digits.minIntegerDigits);
            if (digits.grouping) {
                number.setNumberGroupingAttribute(true);
            }
            if (digits.thousandsScale > 0) {
                number.setNumberDisplayFactorAttribute(Math.pow(1000, digits.thousandsScale));
            }
            setNumberAttribute(number, "min-decimal-places", digits.minDecimalPlaces);
            return number;
        }
        SpreadsheetFormatCode.DatePart date_part = (SpreadsheetFormatCode.DatePart) part;
        OdfElement element;
        switch (date_part.field) {
            case YEAR:
                element = new NumberYearElement(dom);
                break;
            case MONTH:
                element = new NumberMonthElement(dom);
                if (date_part.textual) {
                    ((NumberMonthElement) element).setNumberTextualAttribute(true);
                }
                break;
            case DAY:
                element = new NumberDayElement(dom);
                break;
            case DAY_OF_WEEK:
                element = new NumberDayOfWeekElement(dom);
                break;
            case HOURS:
                element = new NumberHoursElement(dom);
                break;
            case MINUTES:
                element = new NumberMinutesElement(dom);
                break;
            case SECONDS:
                element = new NumberSecondsElement(dom);
                if (date_part.decimalPlaces > 0) {
                    ((NumberSecondsElement) element).setNumberDecimalPlacesAttribute(date_part.decimalPlaces);
                }
                break;
            default:
                return new NumberAmPmElement(dom);
        }
        element.setAttributeNS(OdfDocumentNamespace.NUMBER.getUri(), "number:style",
            date_part.longForm ? "long" : "short");
        return element;
    }

    /** Sets a number attribute that odfdom has no setter for */
    private static void setNumberAttribute(OdfElement element, String name, int value) {
        element.setAttributeNS(OdfDocumentNamespace.NUMBER.getUri(), "number:" + name, String.valueOf(value));
    }

    /** Adds a style:map selecting the data style of another section */
    private static void addMap(OdfFileDom dom, OdfElement style, String condition, String apply_style) {
        StyleMapElement map = new StyleMapElement(dom);
        map.setStyleConditionAttribute(condition);
        map.setStyleApplyStyleNameAttribute(apply_style);
        style.appendChild(map);
    }

    /**
     * Sets the cell value based on the object type.
     */
    private void setCellValue(OdfTableCell cell, Object value, SpreadsheetFormatCode code) {
        if (value == null) {
            // leave the cell empty so that it is read back as no value rather than as an empty string
            return;
        } else if (value instanceof Boolean) {
            cell.setBooleanValue((Boolean) value);
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (code != null && code.getKind() == SpreadsheetFormatCode.Kind.PERCENT) {
                // a percentage cell holds the fraction (0.25 for 25%) and is read back as a number
                cell.setPercentageValue(number);
                cell.setDisplayText(String.valueOf(number));
            } else {
                cell.setDoubleValue(number);
            }
        } else if (value instanceof ZonedDateTime) {
            setDateTimeValue(cell, ((ZonedDateTime) value).toLocalDateTime());
        } else if (value instanceof Date) {
            setDateTimeValue(cell, LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault()));
        } else {
            cell.setStringValue(value.toString());
        }
    }

    /**
     * Sets a date cell to a date and time: the clock time of the value, as office:date-value has no zone
     *
     * odfdom's setDateValue() writes only the date, and setLocalDateTimeValue() writes LocalDateTime.toString(),
     * which leaves out zero seconds and is then not a valid xsd:dateTime, so the value is set with the seconds
     * always written
     */
    private static void setDateTimeValue(OdfTableCell cell, LocalDateTime datetime) {
        String value = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        cell.setLocalDateTimeValue(datetime);
        cell.getOdfElement().setOfficeDateValueAttribute(value);
        cell.setDisplayText(value);
    }

    /**
     * Writes the document to a file.
     */
    public void writeToFile(String path) throws Exception {
        document.save(path);
    }

    /**
     * Writes the document to an output stream.
     */
    public void writeToStream(OutputStream stream) throws Exception {
        document.save(stream);
    }

    /**
     * Writes the document to a Qore output stream.
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws Exception {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the document contents as bytes.
     */
    public byte[] getBytes() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Gets the current row count (including header if written).
     */
    public int getRowCount() {
        return currentRow;
    }

    @Override
    public void close() throws IOException {
        if (document != null) {
            document.close();
            document = null;
        }
    }
}
