/*  SpreadsheetFormatCode.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A validated spreadsheet display format code, written as Excel and LibreOffice write them, for example
 * {@code dd.mm.yyyy}, {@code #,##0.00}, {@code 0.0%} or {@code #,##0.00;(#,##0.00)}
 *
 * Only the part of the format code language that an Excel cell style and an OpenDocument data style both show
 * the same way is accepted, so a code means the same in an .xlsx, .xls and .ods file; anything else is refused
 * with an IllegalArgumentException that says what is wrong and where, rather than being written as a format that
 * a spreadsheet application would show differently or refuse to open.
 *
 * A code is one of these kinds:
 * - a number code: one to three sections separated by {@code ;} (positive;negative;zero), each with one group of
 *   digit placeholders: {@code 0} (a digit that is always shown), {@code #} (a digit shown when significant),
 *   {@code ,} between digit placeholders (thousands grouping), {@code ,} after the last digit placeholder
 *   (divide by 1000 for each comma), {@code .} (the decimal point) and {@code E+} followed by {@code 0}s
 *   (scientific notation); a section may also hold only text, such as {@code "n/a"}
 * - a percent code: a number code with one {@code %} in each section with digits; the value is a fraction
 *   (0.25 is shown as 25%)
 * - a date code: one section of date and time parts: {@code yy} and {@code yyyy} (year), {@code m}, {@code mm},
 *   {@code mmm} and {@code mmmm} (month as a number, abbreviated name and full name), {@code d} and {@code dd}
 *   (day), {@code ddd} and {@code dddd} (weekday name), {@code h} and {@code hh} (hour), {@code m} and
 *   {@code mm} (minute, when directly after an hour or before a second), {@code s} and {@code ss} (second),
 *   {@code .0} to {@code .000} after the second (fractions of a second) and {@code AM/PM} (a 12-hour clock);
 *   letters are not case sensitive
 * - a text code: one section with one {@code @} (the text)
 *
 * In every kind of code, text can be written in double quotes ({@code "text"}), as a character escaped with a
 * backslash ({@code \x}), as a currency symbol in brackets ({@code [$€-407]}: the text between {@code $} and
 * {@code -}), or, for the characters {@code $ - + / ( ) : ! ^ & ' ~ { } < > =}, a space and any non-ASCII character
 * such as {@code €}, as itself; {@code _x} is a space as wide as {@code x}.  A section can start with a color in
 * brackets: {@code [Black]}, {@code [Blue]}, {@code [Cyan]}, {@code [Green]}, {@code [Magenta]}, {@code [Red]},
 * {@code [White]} or {@code [Yellow]}.
 *
 * Not supported: {@code General} (the default display, so a column without a format code), fractions and the
 * {@code ?} placeholder, fill characters ({@code *x}), conditions such as {@code [>100]}, elapsed times such as
 * {@code [h]}, a fourth (text) section, {@code A/P}, era and Buddhist years, and first-letter month names
 * ({@code mmmmm}).
 */
public final class SpreadsheetFormatCode {
    /** The kind of value a format code shows */
    public enum Kind {
        /** a number */
        NUMBER,
        /** a number shown as a percentage */
        PERCENT,
        /** a date and/or time */
        DATE,
        /** text */
        TEXT,
    }

    /** A part of a date code */
    public enum DateField {
        YEAR, MONTH, DAY, DAY_OF_WEEK, HOURS, MINUTES, SECONDS, AM_PM,
    }

    /** A part of a section */
    public abstract static class Part {
    }

    /** Text shown as it is */
    public static final class Literal extends Part {
        public final String text;

        Literal(String text) {
            this.text = text;
        }
    }

    /** The group of digit placeholders of a number section */
    public static final class Digits extends Part {
        /** the number of integer digits that are always shown */
        public final int minIntegerDigits;
        /** the maximum number of decimal places */
        public final int decimalPlaces;
        /** the number of decimal places that are always shown */
        public final int minDecimalPlaces;
        /** whether thousands are grouped */
        public final boolean grouping;
        /** the number of times the value is divided by 1000 before it is shown */
        public final int thousandsScale;
        /** whether the number is shown in scientific notation */
        public final boolean scientific;
        /** the number of exponent digits that are always shown, for scientific notation */
        public final int minExponentDigits;

        Digits(int minIntegerDigits, int decimalPlaces, int minDecimalPlaces, boolean grouping, int thousandsScale,
                boolean scientific, int minExponentDigits) {
            this.minIntegerDigits = minIntegerDigits;
            this.decimalPlaces = decimalPlaces;
            this.minDecimalPlaces = minDecimalPlaces;
            this.grouping = grouping;
            this.thousandsScale = thousandsScale;
            this.scientific = scientific;
            this.minExponentDigits = minExponentDigits;
        }
    }

    /** A part of a date or time */
    public static final class DatePart extends Part {
        public final DateField field;
        /** whether the long form is shown: two digits, a four-digit year, or a full name */
        public final boolean longForm;
        /** for a month: whether its name is shown instead of its number */
        public final boolean textual;
        /** for seconds: the number of decimal places */
        public final int decimalPlaces;

        DatePart(DateField field, boolean longForm, boolean textual, int decimalPlaces) {
            this.field = field;
            this.longForm = longForm;
            this.textual = textual;
            this.decimalPlaces = decimalPlaces;
        }
    }

    /** The text value of a text code ({@code @}) */
    public static final class TextValue extends Part {
    }

    /** One section of a format code */
    public static final class Section {
        /** the color of the section in lower case, or null if none */
        public final String color;
        /** the parts of the section, in order */
        public final List<Part> parts;

        Section(String color, List<Part> parts) {
            this.color = color;
            this.parts = Collections.unmodifiableList(parts);
        }
    }

    /** The colors a section can have, with their RGB value */
    public static final Map<String, String> COLORS = Map.of(
        "black", "#000000",
        "blue", "#0000ff",
        "cyan", "#00ffff",
        "green", "#00ff00",
        "magenta", "#ff00ff",
        "red", "#ff0000",
        "white", "#ffffff",
        "yellow", "#ffff00"
    );

    /** The characters that are text without being quoted or escaped */
    private static final String PLAIN_TEXT = "$-+/():!^&'~{}<>= ";

    private final String code;
    private final Kind kind;
    private final List<Section> sections;

    private SpreadsheetFormatCode(String code, Kind kind, List<Section> sections) {
        this.code = code;
        this.kind = kind;
        this.sections = Collections.unmodifiableList(sections);
    }

    /** Returns the format code as it was given */
    public String getCode() {
        return code;
    }

    /** Returns the kind of value the format code shows */
    public Kind getKind() {
        return kind;
    }

    /** Returns the sections of the format code: one, or for a number or percent code up to three */
    public List<Section> getSections() {
        return sections;
    }

    /**
     * Returns whether the format code shows the given cell value: a number for a number or percent code, a date
     * for a date code, and a string for a text code
     */
    public boolean shows(Object value) {
        switch (kind) {
            case NUMBER:
            case PERCENT:
                return value instanceof Number;
            case DATE:
                return value instanceof java.time.ZonedDateTime || value instanceof java.util.Date;
            default:
                return value instanceof String;
        }
    }

    /**
     * Returns why a format code is not accepted, or null if it is
     *
     * @param code the format code
     * @return the reason the format code is not accepted, or null if it is valid
     */
    public static String issue(String code) {
        try {
            parse(code);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Parses and validates a format code
     *
     * @param code the format code
     * @return the parsed format code
     * @throws IllegalArgumentException the format code is not accepted; the message says why
     */
    public static SpreadsheetFormatCode parse(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("the format code is empty");
        }
        if (code.trim().equalsIgnoreCase("general")) {
            throw new IllegalArgumentException("\"General\" is the default display; leave the column without a "
                + "format code instead");
        }
        List<List<Token>> raw_sections = new Lexer(code).tokenize();
        if (raw_sections.size() > 3) {
            throw error(code, "has " + raw_sections.size() + " sections; at most three are supported "
                + "(positive;negative;zero)");
        }
        ArrayList<RawSection> raw = new ArrayList<RawSection>();
        for (int i = 0; i < raw_sections.size(); ++i) {
            RawSection s = new RawSection(code, i + 1, raw_sections.get(i));
            s.checkDateInNumber();
            raw.add(s);
        }

        boolean has_date = false;
        boolean has_text = false;
        boolean has_digits = false;
        for (RawSection s : raw) {
            has_date |= s.hasDate;
            has_text |= s.hasText;
            has_digits |= s.hasDigits;
        }
        if (has_date || has_text) {
            String what = has_date ? "a date" : "a text";
            if (raw.size() > 1) {
                throw error(code, "is " + what + " format code, which has one section only");
            }
            if (has_date && has_text) {
                throw error(code, "mixes date parts and the text placeholder @");
            }
        }

        ArrayList<Section> sections = new ArrayList<Section>();
        if (has_date) {
            sections.add(raw.get(0).toDateSection());
            return new SpreadsheetFormatCode(code, Kind.DATE, sections);
        }
        if (has_text) {
            sections.add(raw.get(0).toTextSection());
            return new SpreadsheetFormatCode(code, Kind.TEXT, sections);
        }
        if (!has_digits) {
            throw error(code, "has no digit, date or text placeholder");
        }
        boolean percent = false;
        for (RawSection s : raw) {
            percent |= s.percentCount > 0;
        }
        for (RawSection s : raw) {
            sections.add(s.toNumberSection(percent));
        }
        return new SpreadsheetFormatCode(code, percent ? Kind.PERCENT : Kind.NUMBER, sections);
    }

    private static IllegalArgumentException error(String code, String msg) {
        return new IllegalArgumentException("format code \"" + code + "\" " + msg);
    }

    private enum TokenType {
        /** text */
        TEXT,
        /** a color */
        COLOR,
        /** one of 0 # , . % @ */
        CHAR,
        /** E+ */
        EXPONENT,
        /** a run of one date letter (y, m, d, h or s) */
        RUN,
        /** AM/PM */
        AM_PM,
    }

    private static final class Token {
        final TokenType type;
        /** the text, the color, the character or the date letter */
        final String value;
        /** the length of a run of date letters */
        final int count;
        /** the 1-based position of the token in the code */
        final int pos;

        Token(TokenType type, String value, int count, int pos) {
            this.type = type;
            this.value = value;
            this.count = count;
            this.pos = pos;
        }

        boolean isChar(char c) {
            return type == TokenType.CHAR && value.charAt(0) == c;
        }

        boolean isRun(char c) {
            return type == TokenType.RUN && value.charAt(0) == c;
        }
    }

    /** Splits a format code into sections of tokens */
    private static final class Lexer {
        private final String code;
        private int i = 0;
        private final ArrayList<List<Token>> sections = new ArrayList<List<Token>>();
        private ArrayList<Token> current = new ArrayList<Token>();

        Lexer(String code) {
            this.code = code;
        }

        List<List<Token>> tokenize() {
            while (i < code.length()) {
                int pos = i + 1;
                int cp = code.codePointAt(i);
                int len = Character.charCount(cp);
                char c = code.charAt(i);
                if (c == '"') {
                    int end = code.indexOf('"', i + 1);
                    if (end < 0) {
                        throw error(code, "has an unterminated quoted text starting at position " + pos);
                    }
                    addText(code.substring(i + 1, end), pos);
                    i = end + 1;
                } else if (c == '\\' || c == '_') {
                    if (i + 1 >= code.length()) {
                        throw error(code, "ends with \"" + c + "\", which needs a character after it");
                    }
                    int next = code.codePointAt(i + 1);
                    addText(c == '\\' ? new String(Character.toChars(next)) : " ", pos);
                    i += 1 + Character.charCount(next);
                } else if (c == '*') {
                    throw error(code, "has a fill character at position " + pos + "; fill characters (*) are not "
                        + "supported");
                } else if (c == '[') {
                    int end = code.indexOf(']', i + 1);
                    if (end < 0) {
                        throw error(code, "has an unterminated \"[\" at position " + pos);
                    }
                    bracket(code.substring(i + 1, end), pos);
                    i = end + 1;
                } else if (c == ';') {
                    endSection(pos);
                    ++i;
                } else if ("0#,.%@".indexOf(c) >= 0) {
                    current.add(new Token(TokenType.CHAR, String.valueOf(c), 1, pos));
                    ++i;
                } else if (c == '?') {
                    throw error(code, "has \"?\" at position " + pos + "; the ? digit placeholder and fractions are "
                        + "not supported");
                } else if (c == 'E' || c == 'e') {
                    char next = i + 1 < code.length() ? code.charAt(i + 1) : 0;
                    if (next == '+') {
                        current.add(new Token(TokenType.EXPONENT, "E+", 1, pos));
                        i += 2;
                    } else if (next == '-') {
                        throw error(code, "has \"" + c + "-\" at position " + pos + "; use \"E+\" for scientific "
                            + "notation");
                    } else {
                        throw error(code, "has \"" + c + "\" at position " + pos + ", which must be quoted (\"" + c
                            + "\") or escaped (\\" + c + "); scientific notation is written \"E+\"");
                    }
                } else if ("ymdhsYMDHS".indexOf(c) >= 0) {
                    char lc = Character.toLowerCase(c);
                    int start = i;
                    while (i < code.length() && Character.toLowerCase(code.charAt(i)) == lc) {
                        ++i;
                    }
                    current.add(new Token(TokenType.RUN, String.valueOf(lc), i - start, pos));
                } else if (code.regionMatches(true, i, "AM/PM", 0, 5)) {
                    current.add(new Token(TokenType.AM_PM, "AM/PM", 1, pos));
                    i += 5;
                } else if (code.regionMatches(true, i, "A/P", 0, 3)) {
                    throw error(code, "has \"A/P\" at position " + pos + "; use \"AM/PM\"");
                } else if (PLAIN_TEXT.indexOf(c) >= 0 || cp > 127) {
                    addText(code.substring(i, i + len), pos);
                    i += len;
                } else {
                    String ch = code.substring(i, i + len);
                    throw error(code, "has \"" + ch + "\" at position " + pos + ", which must be quoted (\"" + ch
                        + "\") or escaped (\\" + ch + ")");
                }
            }
            endSection(code.length() + 1);
            return sections;
        }

        private void addText(String text, int pos) {
            if (text.isEmpty()) {
                return;
            }
            current.add(new Token(TokenType.TEXT, text, 1, pos));
        }

        private void bracket(String content, int pos) {
            if (content.startsWith("$")) {
                int dash = content.indexOf('-');
                addText(dash < 0 ? content.substring(1) : content.substring(1, dash), pos);
                return;
            }
            String color = content.toLowerCase(Locale.ROOT);
            if (COLORS.containsKey(color)) {
                current.add(new Token(TokenType.COLOR, color, 1, pos));
                return;
            }
            throw error(code, "has \"[" + content + "]\" at position " + pos + "; only colors such as [Red] and "
                + "currency symbols such as [$€-407] are supported in brackets");
        }

        private void endSection(int pos) {
            if (current.isEmpty()) {
                throw error(code, "has an empty section " + (sections.size() + 1)
                    + (pos > code.length() ? " at its end" : " before position " + pos));
            }
            sections.add(current);
            current = new ArrayList<Token>();
        }
    }

    /** The tokens of one section, before they are interpreted */
    private static final class RawSection {
        final String code;
        final int number;
        final List<Token> tokens;
        boolean hasDate = false;
        boolean hasText = false;
        boolean hasDigits = false;
        int percentCount = 0;

        RawSection(String code, int number, List<Token> tokens) {
            this.code = code;
            this.number = number;
            this.tokens = tokens;
            // zeros directly after a second and a decimal point are fractions of a second, not digit placeholders
            boolean second_fraction = false;
            for (int i = 0; i < tokens.size(); ++i) {
                Token t = tokens.get(i);
                if (second_fraction && t.isChar('0')) {
                    continue;
                }
                second_fraction = t.isChar('.') && i > 0 && tokens.get(i - 1).isRun('s');
                switch (t.type) {
                    case RUN:
                    case AM_PM:
                        hasDate = true;
                        break;
                    case EXPONENT:
                        hasDigits = true;
                        break;
                    case CHAR:
                        char c = t.value.charAt(0);
                        if (c == '@') {
                            hasText = true;
                        } else if (c == '0' || c == '#') {
                            hasDigits = true;
                        } else if (c == '%') {
                            ++percentCount;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        private IllegalArgumentException error(Token t, String msg) {
            return SpreadsheetFormatCode.error(code, "has " + msg + " at position " + t.pos);
        }

        /**
         * Refuses date letters in a section with digit placeholders or a percent sign, which is most often text
         * that is not quoted, such as {@code 0.00 days}
         */
        void checkDateInNumber() {
            if (!hasDate || (!hasDigits && percentCount == 0)) {
                return;
            }
            for (Token t : tokens) {
                if (t.type == TokenType.RUN || t.type == TokenType.AM_PM) {
                    String text = t.type == TokenType.AM_PM
                        ? t.value
                        : code.substring(t.pos - 1, t.pos - 1 + t.count);
                    throw SpreadsheetFormatCode.error(code, "has \"" + text + "\" at position " + t.pos
                        + " in a section with digit placeholders; date parts cannot be used with digits, and text "
                        + "must be quoted (\"" + text + "\") or escaped (\\" + text.charAt(0) + ")");
                }
            }
        }

        /** Returns the color of the section, or null if it has none */
        private String color() {
            String color = null;
            for (Token t : tokens) {
                if (t.type == TokenType.COLOR) {
                    if (color != null) {
                        throw error(t, "a second color in section " + number);
                    }
                    color = t.value;
                }
            }
            return color;
        }

        /** Adds text to the parts, joining it to text directly before it */
        private static void addText(List<Part> parts, String text) {
            if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof Literal) {
                Literal last = (Literal) parts.remove(parts.size() - 1);
                text = last.text + text;
            }
            parts.add(new Literal(text));
        }

        Section toTextSection() {
            ArrayList<Part> parts = new ArrayList<Part>();
            boolean have_value = false;
            for (Token t : tokens) {
                if (t.type == TokenType.TEXT) {
                    addText(parts, t.value);
                } else if (t.isChar('@')) {
                    if (have_value) {
                        throw error(t, "a second @");
                    }
                    have_value = true;
                    parts.add(new TextValue());
                } else if (t.type == TokenType.CHAR && (t.isChar(',') || t.isChar('.'))) {
                    addText(parts, t.value);
                } else if (t.type != TokenType.COLOR) {
                    throw error(t, "\"" + t.value + "\", which is not text, in a text format code");
                }
            }
            return new Section(color(), parts);
        }

        Section toDateSection() {
            ArrayList<Part> parts = new ArrayList<Part>();
            for (int i = 0; i < tokens.size(); ++i) {
                Token t = tokens.get(i);
                switch (t.type) {
                    case TEXT:
                        addText(parts, t.value);
                        break;
                    case COLOR:
                        break;
                    case AM_PM:
                        parts.add(new DatePart(DateField.AM_PM, false, false, 0));
                        break;
                    case CHAR:
                        if (t.isChar(',') || t.isChar('.')) {
                            addText(parts, t.value);
                            break;
                        }
                        throw error(t, "\"" + t.value + "\", which is not a date part, in a date format code");
                    case RUN: {
                        char c = t.value.charAt(0);
                        if (c == 'y') {
                            if (t.count > 4) {
                                throw error(t, "a year of " + t.count + " letters; use yy or yyyy");
                            }
                            parts.add(new DatePart(DateField.YEAR, t.count > 2, false, 0));
                        } else if (c == 'd') {
                            if (t.count > 4) {
                                throw error(t, "a day of " + t.count + " letters; use d, dd, ddd or dddd");
                            }
                            parts.add(new DatePart(t.count > 2 ? DateField.DAY_OF_WEEK : DateField.DAY,
                                t.count == 2 || t.count == 4, false, 0));
                        } else if (c == 'h') {
                            if (t.count > 2) {
                                throw error(t, "an hour of " + t.count + " letters; use h or hh");
                            }
                            parts.add(new DatePart(DateField.HOURS, t.count == 2, false, 0));
                        } else if (c == 's') {
                            if (t.count > 2) {
                                throw error(t, "a second of " + t.count + " letters; use s or ss");
                            }
                            // fractions of a second: a decimal point and zeros directly after the seconds
                            int decimals = 0;
                            if (i + 1 < tokens.size() && tokens.get(i + 1).isChar('.')) {
                                int j = i + 2;
                                while (j < tokens.size() && tokens.get(j).isChar('0')) {
                                    ++j;
                                }
                                decimals = j - (i + 2);
                                if (decimals > 3) {
                                    throw error(t, decimals + " decimal places of a second; at most 3 are "
                                        + "supported");
                                }
                                if (decimals > 0) {
                                    i = j - 1;
                                }
                            }
                            parts.add(new DatePart(DateField.SECONDS, t.count == 2, false, decimals));
                        } else {
                            // m: minutes directly after an hour or before a second, ignoring text; otherwise a month
                            if (t.count > 4) {
                                throw error(t, "a month of " + t.count + " letters; first-letter month names are "
                                    + "not supported");
                            }
                            if (t.count <= 2 && isMinutes(i)) {
                                parts.add(new DatePart(DateField.MINUTES, t.count == 2, false, 0));
                            } else {
                                parts.add(new DatePart(DateField.MONTH, t.count == 2 || t.count == 4, t.count > 2,
                                    0));
                            }
                        }
                        break;
                    }
                    default:
                        throw error(t, "\"" + t.value + "\", which is not a date part, in a date format code");
                }
            }
            return new Section(color(), parts);
        }

        /** Returns whether the run of m at the given index is minutes */
        private boolean isMinutes(int index) {
            for (int j = index - 1; j >= 0; --j) {
                Token t = tokens.get(j);
                if (t.type == TokenType.RUN || t.type == TokenType.AM_PM) {
                    if (t.isRun('h')) {
                        return true;
                    }
                    break;
                }
            }
            for (int j = index + 1; j < tokens.size(); ++j) {
                Token t = tokens.get(j);
                if (t.type == TokenType.RUN || t.type == TokenType.AM_PM) {
                    return t.isRun('s');
                }
            }
            return false;
        }

        Section toNumberSection(boolean percent) {
            if (percentCount > 1) {
                throw SpreadsheetFormatCode.error(code, "has " + percentCount + " percent signs in section "
                    + number + "; at most one is supported");
            }
            if (percent && hasDigits && percentCount == 0) {
                throw SpreadsheetFormatCode.error(code, "has a percent sign in one section but not in section "
                    + number + "; every section with digits needs one");
            }
            ArrayList<Part> parts = new ArrayList<Part>();
            boolean have_digits = false;
            for (int i = 0; i < tokens.size(); ++i) {
                Token t = tokens.get(i);
                if (t.type == TokenType.TEXT) {
                    addText(parts, t.value);
                } else if (t.type == TokenType.COLOR) {
                    continue;
                } else if (t.isChar('%')) {
                    addText(parts, "%");
                } else if (t.type == TokenType.CHAR || t.type == TokenType.EXPONENT) {
                    if (t.isChar('@')) {
                        throw error(t, "the text placeholder @ in a number format code");
                    }
                    // the group of digit placeholders runs to the first token that is not part of it
                    int end = i;
                    while (end < tokens.size() && (tokens.get(end).type == TokenType.EXPONENT
                            || (tokens.get(end).type == TokenType.CHAR && "0#,.".indexOf(tokens.get(end).value)
                                >= 0))) {
                        ++end;
                    }
                    if (have_digits) {
                        throw error(t, "a second group of digit placeholders in section " + number
                            + "; a number section has one group");
                    }
                    have_digits = true;
                    parts.add(digits(tokens.subList(i, end)));
                    i = end - 1;
                } else {
                    throw error(t, "\"" + t.value + "\", which is not a number part, in a number format code");
                }
            }
            return new Section(color(), parts);
        }

        /** Interprets a group of digit placeholders */
        private Digits digits(List<Token> group) {
            Token first = group.get(0);
            StringBuilder integer = new StringBuilder();
            StringBuilder decimals = null;
            StringBuilder exponent = null;
            for (Token t : group) {
                if (t.type == TokenType.EXPONENT) {
                    if (exponent != null) {
                        throw error(t, "a second exponent");
                    }
                    exponent = new StringBuilder();
                } else if (exponent != null) {
                    if (!t.isChar('0')) {
                        throw error(t, "\"" + t.value + "\" in an exponent, which has only 0s");
                    }
                    exponent.append('0');
                } else if (t.isChar('.')) {
                    if (decimals != null) {
                        throw error(t, "a second decimal point");
                    }
                    decimals = new StringBuilder();
                } else if (decimals != null) {
                    decimals.append(t.value);
                } else {
                    integer.append(t.value);
                }
            }

            // commas after the last digit placeholder divide by 1000; commas between digit placeholders group
            int scale = 0;
            StringBuilder last_part = decimals != null && decimals.length() > 0 ? decimals : integer;
            while (last_part.length() > 0 && last_part.charAt(last_part.length() - 1) == ',') {
                last_part.setLength(last_part.length() - 1);
                ++scale;
            }
            if (decimals != null && last_part == decimals) {
                // commas directly before the decimal point also divide by 1000
                while (integer.length() > 0 && integer.charAt(integer.length() - 1) == ',') {
                    integer.setLength(integer.length() - 1);
                    ++scale;
                }
            }
            if (integer.length() > 0 && integer.charAt(0) == ',') {
                throw error(first, "a comma before the first digit placeholder");
            }
            boolean grouping = integer.indexOf(",") >= 0;
            int min_integer = 0;
            int integer_digits = 0;
            for (int i = 0; i < integer.length(); ++i) {
                char c = integer.charAt(i);
                if (c == '0') {
                    ++min_integer;
                }
                if (c != ',') {
                    ++integer_digits;
                }
            }
            int decimal_places = 0;
            int min_decimals = 0;
            if (decimals != null) {
                for (int i = 0; i < decimals.length(); ++i) {
                    char c = decimals.charAt(i);
                    if (c == ',') {
                        throw error(first, "a comma between decimal places");
                    }
                    if (c == '0') {
                        if (min_decimals != decimal_places) {
                            throw error(first, "a 0 after a # in the decimal places; the decimal places that are "
                                + "always shown (0) come first");
                        }
                        ++min_decimals;
                    }
                    ++decimal_places;
                }
            }
            if (integer_digits == 0 && decimal_places == 0) {
                throw error(first, "a number without digit placeholders (0 or #)");
            }
            if (decimals != null && decimal_places == 0) {
                throw error(first, "a decimal point without decimal places (0 or #) after it");
            }
            if (exponent != null) {
                if (exponent.length() == 0) {
                    throw error(first, "\"E+\" without exponent digits (0)");
                }
                if (scale > 0) {
                    throw error(first, "a comma after the last digit placeholder (dividing by 1000) together with "
                        + "scientific notation");
                }
                if (percentCount > 0) {
                    throw error(first, "a percent sign together with scientific notation");
                }
            }
            return new Digits(min_integer, decimal_places, min_decimals, grouping, scale, exponent != null,
                exponent != null ? exponent.length() : 0);
        }
    }
}
