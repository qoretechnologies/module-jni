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
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;

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

    private OdsCell(String value_type, String value, String boolean_value, String date_value, String time_value,
            String display_text, boolean has_text) {
        this.value_type = value_type;
        this.value = value;
        this.boolean_value = boolean_value;
        this.date_value = date_value;
        this.time_value = time_value;
        this.display_text = display_text;
        this.has_text = has_text;
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
        return new OdsCell(value_type, value, boolean_value, date_value, time_value, display.toString(), has_text);
    }

    /**
     * Returns true if the cell has a value type or text that is not only whitespace
     */
    boolean hasContent() {
        return (value_type != null && !value_type.isEmpty()) || has_text;
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
     * Returns the value of a float cell, null if not set; throws IllegalArgumentException for any other value type,
     * including currency and percentage cells, as OdfTableCell.getDoubleValue() does
     */
    Double getDoubleValue() {
        if (!"float".equals(value_type)) {
            throw new IllegalArgumentException();
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
     * Returns the date of a date cell in the JVM's default zone, as OdfTableCell.getDateValue() does; null if the
     * date is not set or invalid
     */
    Calendar getDateValue() {
        if (!"date".equals(value_type)) {
            throw new IllegalArgumentException();
        }
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
        return GregorianCalendar.from(datetime.atZone(ZoneId.systemDefault()));
    }

    /**
     * Returns the duration of a time cell from the epoch, as OdfTableCell.getTimeValue() does: a calendar in the
     * JVM's default zone at that instant
     */
    Calendar getTimeValue() {
        if (!"time".equals(value_type)) {
            throw new IllegalArgumentException();
        }
        Duration duration = Duration.parse(time_value);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(duration.toMillis());
        return calendar;
    }
}
