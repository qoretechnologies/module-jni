/*  OdsCell.java Copyright 2026 Qore Technologies, s.r.o.

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

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * A cell of an ODF spreadsheet table read from content.xml.
 *
 * The value methods have the semantics of the ODFDOM OdfTableCell methods of the same names, which the record reader
 * used before it streamed.
 */
final class OdsCell {
    // the office:value-type attribute and the value attributes; null if not set
    private final String value_type;
    private final String value;
    private final String boolean_value;
    private final String date_value;
    private final String time_value;
    // the text of the cell as OdfWhitespaceProcessor.getText() gives it
    private final String display_text;
    // true if the cell's text content as OdfElement.getTextContent() gives it is not only whitespace
    private final boolean has_text;
    // the table:formula attribute in ODF syntax; null if not set or empty
    private final String formula;

    private OdsCell(String value_type, String value, String boolean_value, String date_value, String time_value,
            String display_text, boolean has_text, String formula) {
        this.value_type = value_type;
        this.value = value;
        this.boolean_value = boolean_value;
        this.date_value = date_value;
        this.time_value = time_value;
        this.display_text = display_text;
        this.has_text = has_text;
        this.formula = formula;
    }

    /**
     * Reads a cell element, at whose start the reader is, up to and including its end tag
     *
     * The display text is the text of the cell's descendants, where text:s is its number of spaces (text:c, 1 if
     * missing or invalid), text:line-break is a newline, and text:tab a tab, as OdfWhitespaceProcessor.getText() gives
     * it; this includes the text of annotations and of the whitespace between elements, as in the DOM.
     *
     * Whether the cell has text is determined from its text content as ODFDOM's OdfElement.getTextContent() gives it,
     * which the record reader used before it streamed: the text of the cell element itself and of the text:span and
     * text:a elements in it, but not that of other elements such as paragraphs (text:p); a cell has content if it has
     * a value type or such text.
     */
    static OdsCell read(XMLStreamReader xr) throws XMLStreamException {
        String value_type = xr.getAttributeValue(OdsSheetReader.NS_OFFICE, "value-type");
        String value = xr.getAttributeValue(OdsSheetReader.NS_OFFICE, "value");
        String boolean_value = xr.getAttributeValue(OdsSheetReader.NS_OFFICE, "boolean-value");
        String date_value = xr.getAttributeValue(OdsSheetReader.NS_OFFICE, "date-value");
        String time_value = xr.getAttributeValue(OdsSheetReader.NS_OFFICE, "time-value");
        String formula = xr.getAttributeValue(OdsSheetReader.NS_TABLE, "formula");
        if (formula != null && formula.isEmpty()) {
            formula = null;
        }

        StringBuilder display = new StringBuilder();
        boolean has_text = false;
        int depth = 1;
        // the depth of an element whose text is not part of the display text (text:s, text:line-break, text:tab)
        // that the reader is in; 0 if none
        int hidden_depth = 0;
        // the depth up to which the elements are text:span and text:a elements from the cell, whose text is part of
        // the cell's text content
        int content_depth = 1;
        while (depth > 0) {
            int ev = xr.next();
            switch (ev) {
                case XMLStreamConstants.START_ELEMENT: {
                    ++depth;
                    if (content_depth == depth - 1 && OdsSheetReader.NS_TEXT.equals(xr.getNamespaceURI())
                            && ("span".equals(xr.getLocalName()) || "a".equals(xr.getLocalName()))) {
                        content_depth = depth;
                    }
                    if (hidden_depth != 0) {
                        break;
                    }
                    String ln = xr.getLocalName();
                    if ("s".equals(ln)) {
                        int n;
                        try {
                            n = Integer.parseInt(xr.getAttributeValue(OdsSheetReader.NS_TEXT, "c"));
                        } catch (Exception e) {
                            n = 1;
                        }
                        for (int i = 0; i < n; ++i) {
                            display.append(' ');
                        }
                        hidden_depth = depth;
                    } else if ("line-break".equals(ln)) {
                        display.append('\n');
                        hidden_depth = depth;
                    } else if ("tab".equals(ln)) {
                        display.append('\t');
                        hidden_depth = depth;
                    }
                    break;
                }

                case XMLStreamConstants.END_ELEMENT:
                    if (depth == hidden_depth) {
                        hidden_depth = 0;
                    }
                    if (depth == content_depth) {
                        --content_depth;
                    }
                    --depth;
                    break;

                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE: {
                    String text = xr.getText();
                    if (hidden_depth == 0) {
                        display.append(text);
                    }
                    if (!has_text && depth == content_depth && !text.trim().isEmpty()) {
                        has_text = true;
                    }
                    break;
                }

                default:
                    break;
            }
        }
        return new OdsCell(value_type, value, boolean_value, date_value, time_value, display.toString(), has_text,
            formula);
    }

    /**
     * Returns true if the cell has a value type or text that is not only whitespace
     */
    boolean hasContent() {
        return (value_type != null && !value_type.isEmpty()) || has_text;
    }

    /**
     * Returns the cell's formula in ODF syntax as written (for example "of:=SUM([.B2:.B4])"), or null if the cell
     * has no formula
     */
    String getFormula() {
        return formula;
    }

    String getValueType() {
        return value_type;
    }

    String getDisplayText() {
        return display_text;
    }

    String getStringValue() {
        return display_text;
    }

    /**
     * Returns the office:value of a float, currency, or percentage cell, null if not set; a percentage is its
     * fraction (0.25 for 25%), and a currency amount does not include its currency
     */
    Double getDoubleValue() {
        if (!"float".equals(value_type) && !"currency".equals(value_type) && !"percentage".equals(value_type)) {
            throw new IllegalArgumentException(String.format("a cell with value type %s has no number", value_type));
        }
        if (value != null && !value.isEmpty()) {
            return Double.parseDouble(value);
        }
        return null;
    }

    Boolean getBooleanValue() {
        if (!"boolean".equals(value_type)) {
            throw new IllegalArgumentException();
        }
        if (boolean_value != null && !boolean_value.isEmpty()) {
            return Boolean.parseBoolean(boolean_value);
        }
        return null;
    }

    /**
     * Returns the date and time of a date cell as its clock time in the given zone, or null if the cell has no valid
     * office:date-value
     *
     * The value has no zone in the file (as Excel's date cells have none): it is that clock time in the reader's
     * zone.  A date without a time is midnight.  A clock time that does not exist in the zone, because it falls in
     * a gap when clocks are put forward, is resolved as java.time resolves it: moved later by the length of the gap
     * (02:30 on the day clocks go from 02:00 to 03:00 is 03:30); a clock time that occurs twice, when clocks are put
     * back, is the earlier of the two.
     */
    ZonedDateTime getDateValue(ZoneId zone) {
        if (!"date".equals(value_type)) {
            throw new IllegalArgumentException(String.format("a cell with value type %s has no date", value_type));
        }
        return toDateTime(date_value, zone);
    }

    /**
     * Returns the time of a time cell as its clock time in the given zone, or null if the cell has no valid
     * office:time-value
     *
     * A time cell is a duration (such as PT13H45M) without a date; as Excel's time cells, it is that duration after
     * midnight on Excel's day zero, 1899-12-31, so a duration of more than 24 hours moves to the following days.
     */
    ZonedDateTime getTimeValue(ZoneId zone) {
        if (!"time".equals(value_type)) {
            throw new IllegalArgumentException(String.format("a cell with value type %s has no time", value_type));
        }
        return toTime(time_value, zone);
    }

    /**
     * Returns the date and time of an office:date-value in the given zone, or null if it is missing or invalid
     */
    static ZonedDateTime toDateTime(String date_value, ZoneId zone) {
        if (date_value == null) {
            return null;
        }
        LocalDateTime datetime;
        try {
            if (date_value.contains("T")) {
                datetime = LocalDateTime.parse(date_value);
            } else {
                datetime = LocalDateTime.of(LocalDate.parse(date_value), LocalTime.MIN);
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return ZonedDateTime.of(datetime, zone);
    }

    /**
     * Returns the time of an office:time-value in the given zone, or null if it is missing or invalid
     */
    static ZonedDateTime toTime(String time_value, ZoneId zone) {
        if (time_value == null) {
            return null;
        }
        Duration duration;
        try {
            duration = Duration.parse(time_value);
        } catch (DateTimeParseException e) {
            return null;
        }
        return ZonedDateTime.of(TIME_BASE.plus(duration), zone);
    }

    // the date of time cells: Excel's day zero, which is the date of its time cells
    private static final LocalDateTime TIME_BASE = LocalDateTime.of(1899, 12, 31, 0, 0);
}
