# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore OdsWriteDataProvider class definition

/*  OdsWriteDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the OdsDataProvider namespace
public namespace OdsDataProvider {
#! A worksheet of the \c sheets option of OdsWriteDataProvider
hashdecl OdsWriteSheetInfo {
    #! The name of the worksheet
    string name;

    #! The columns of the worksheet, if known: given, or the fields of the first record written to it
    *list<string> columns;

    #! The DPQL condition of the records written to the worksheet, as given, if any
    *string when;

    #! The parsed condition, if any
    *hash<DataProviderExpression> condition;

    #! The \c column_headers, \c column_formats and \c column_masks of the worksheet, as given
    hash<auto> column_opts = {};

    #! The display format code of each column with a format code or a mask
    hash<string, string> formats = {};

    #! False if no header row is written
    bool write_headers = True;

    #! The 1-based header row; preceding rows are blank
    int header_row = 1;

    #! The number of records written to the worksheet
    int count = 0;
}

#! Provides a data provider for writing ODS files
public class OdsWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "OdsWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "ODS Output Path",
                "short_desc": "The location to write the ODS file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location for the output ODS data file; mutually exclusive with `stream`.\n\n"
                    "The output location is handled by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
                "example_value": "file:///path/to/output.ods",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ODS Output Stream",
                "short_desc": "An output stream for ODS data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for ODS data; mutually exclusive with `path`",
            },
            "worksheet": <DataProviderOptionInfo>{
                "display_name": "Worksheet Name",
                "short_desc": "The name of the worksheet to create",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of the worksheet to create; if not provided, defaults to `Sheet1`",
                "example_value": "Sheet1",
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Column Headers",
                "short_desc": "List of header / column names for the data",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of header / column names for the data; if not provided, headers will be "
                    "auto-detected from the first record",
            },
            "column_formats": <DataProviderOptionInfo>{
                "display_name": "Column Display Formats",
                "short_desc": "The display format of each column, such as dd.mm.yyyy or #,##0.00",
                "type": AbstractDataProviderType::get(new Type("hash<string, string>")),
                "desc": "The display format of columns by their header name, so that whoever opens the file sees "
                    "each value as intended: a date as `dd.mm.yyyy`, an amount as `#,##0.00`, or a share as "
                    "`0.0%`.  The cells keep their real values (a date stays a date and a number a number); only "
                    "the way they are shown changes.  Each format is written as an OpenDocument data style, which "
                    "LibreOffice and Excel show the same way.  A column without a format is written as without "
                    "this option.\n\n"
                    "Format codes are written as in LibreOffice and Excel:\n"
                    "- **numbers**: `0` (a digit that is always shown), `#` (a digit shown when significant), `,` "
                    "between digits (thousands grouping), `,` after the last digit (divide by 1000), `.` (the "
                    "decimal point), and `E+00` (scientific notation); up to three sections for positive, negative "
                    "and zero values separated by `;`, such as `#,##0.00;(#,##0.00)` or `#,##0.00;#,##0.00-`\n"
                    "- **percentages**: a `%` in each section, such as `0.0%`; the value is a fraction, so `0.25` "
                    "is shown as `25.0%`, and it is written as a percentage cell, which is read back as a number\n"
                    "- **dates and times**: `yy` or `yyyy` (year), `m`, `mm`, `mmm` or `mmmm` (month as a number "
                    "or a name), `d` or `dd` (day), `ddd` or `dddd` (weekday name), `h` or `hh` (hour), `m` or "
                    "`mm` directly after an hour or before a second (minute), `s` or `ss` (second), `.0` to "
                    "`.000` after the second (fractions of a second), and `AM/PM`, such as `dd.mm.yyyy hh:mm`\n"
                    "- **text**: `@`\n\n"
                    "Text in a code is written in double quotes (`\"EUR\"`), escaped with a backslash (`\\x`), as "
                    "a currency symbol in brackets (`[$€-407]`), or as itself for `$ - + / ( ) :`, spaces and "
                    "symbols such as `€`; a section can start with a color such as `[Red]`.\n\n"
                    "A value that its column's code does not show, such as a string in a number column, is "
                    "written without the format.  An invalid or unsupported code (`General`, fractions, fill "
                    "characters, conditions, or elapsed times), or a column that is not written, raises "
                    "`ODS-WRITE-OPTION-ERROR` naming the column and the problem",
                "example_value": {"Date": "dd.mm.yyyy", "Amount": "#,##0.00;(#,##0.00)", "Share": "0.0%"},
            },
            "column_masks": <DataProviderOptionInfo>{
                "display_name": "Column Masks",
                "short_desc": "The display format of each column as a mask, such as DD.MM.YYYY or #.##0,00 €",
                "type": AbstractDataProviderType::get(new Type("hash<string, string>")),
                "desc": "The display format of columns by column name as masks of the "
                    "[ValueMask](https://qoretechnologies.com/manual/qorus/current/qore/modules/ValueMask/html/"
                    "index.html) module, the masks that CSV files and sheet "
                    "contracts are read and written with: a date as `DD.MM.YYYY`, a time as `hh:mm AM`, an amount "
                    "as `#.##0,00 €` or a share as `0.#%`.  Each mask becomes the display format code of its column "
                    "(`DD.MM.YYYY` is `dd\\.mm\\.yyyy`, `#.##0,00 €` is `#,##0.00\" €\"`), so the cells keep their "
                    "real values (a date stays a date and a number a number) and are shown as the mask describes, "
                    "with these differences from text:\n"
                    "- the group separator and decimal mark are shown as the language of whoever opens the file "
                    "uses them; the mask's separators only say whether digits are grouped and how many decimals "
                    "are shown\n"
                    "- `Mon` shows the month name in the language of whoever opens the file\n"
                    "- a final `#` shows at most 10 decimals, and a whole number with the decimal mark after it "
                    "(`12.`)\n\n"
                    "A mask without a display format raises `ODS-WRITE-OPTION-ERROR` naming the column and the "
                    "reason: `ZZ` (a cell holds no UTC offset), `MON` (there is no upper-case month name), `sss` "
                    "not directly after `SS.`, a minute that is not directly after an hour or before the seconds, "
                    "and a numeric month that is.  A column cannot have both a mask and a format code in "
                    "`column_formats`",
                "example_value": {"Date": "DD.MM.YYYY", "Amount": "#.##0,00 €", "Share": "0.#%"},
            },
            "column_headers": <DataProviderOptionInfo>{
                "display_name": "Column Header Text",
                "short_desc": "The header text of each column whose header is not its name",
                "type": AbstractDataProviderType::get(new Type("hash<string, string>")),
                "desc": "The header text of columns by column name, for columns whose header is not their name.  "
                    "The records are written by column name, so `headers` and the other column options name the "
                    "columns as the records do; this option only changes the text of the header row",
                "example_value": {"order_id": "Order No.", "amount": "Amount (EUR)"},
            },
            "write_headers": <DataProviderOptionInfo>{
                "display_name": "Write Header Row",
                "short_desc": "Write the column headers as the first row",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "Set to `false` to write no header row: the first record is written in the first row of "
                    "the worksheet; the default is `true`",
                "default_value": True,
            },
            "header_row": <DataProviderOptionInfo>{
                "display_name": "Header Row Number",
                "short_desc": "The row containing the column headers",
                "type": AbstractDataProviderType::get(IntType),
                "desc": "The 1-based row containing the column headers; `1` by default. Earlier rows are blank, "
                    "and records follow the header row. A value after row `1` requires `write_headers` to be `true`.",
                "example_value": 3,
            },
            "timezone": <DataProviderOptionInfo>{
                "display_name": "Time Zone",
                "short_desc": "The time zone whose wall-clock time dates are written in",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The time zone region (such as `Europe/Prague` or `America/New_York`) whose wall-clock "
                    "time dates are written in, as a cell holds a date and time without a time zone; if not given, "
                    "a date is written as its own wall-clock time",
                "example_value": "Europe/Prague",
            },
            "sheets": <DataProviderOptionInfo>{
                "display_name": "Worksheets",
                "short_desc": "The worksheets to write, each with the condition of its records",
                "type": AbstractDataProviderType::get(new Type("list<hash<auto>>")),
                "desc": "The worksheets to write, in document order, to write the records to several worksheets: "
                    "each record is written to the worksheet whose condition it matches.  Each worksheet is a hash "
                    "with these keys:\n"
                    "- `worksheet`: the name of the worksheet (required), different from the names of the other "
                    "worksheets in more than case\n"
                    "- `columns`: the columns of the worksheet, in order; a column that a record has no value for is "
                    "an empty cell, and the other fields of a record are not written to the worksheet; without "
                    "`columns`, the fields of the first record written to the worksheet are its columns\n"
                    "- `when`: the condition of the records written to the worksheet, in DPQL with the fields of the "
                    "record, such as `@kind == \"order\"`; a worksheet without a condition takes every record\n"
                    "- `column_headers`, `column_formats`, `column_masks`, `header_row` and `write_headers`: as the "
                    "options of the same name, for the worksheet\n\n"
                    "**Routing**: a record is written to the first worksheet whose condition it matches, or with "
                    "`route` set to `all`, to every worksheet it matches; a record that matches no worksheet raises "
                    "`ODS-WRITE-ROUTE-ERROR`, unless `unmatched` is `skip`.  Every worksheet is created, also one "
                    "without records, which has its header row if its columns are given.  The `write-data` action "
                    "returns the number of records written to each worksheet in `sheet_counts`.\n\n"
                    "**Other options**: with this option, the `worksheet`, `headers`, `column_headers`, "
                    "`column_formats`, `column_masks`, `header_row` and `write_headers` options are given for each "
                    "worksheet instead; `timezone` applies to the document.  An invalid worksheet or condition raises "
                    "`ODS-WRITE-OPTION-ERROR` naming the worksheet",
                "example_value": (
                    {"worksheet": "Orders", "columns": ("Id", "Amount"), "when": "@kind == \"order\""},
                    {"worksheet": "Returns", "columns": ("Id", "Reason"), "when": "@kind == \"return\""},
                    {"worksheet": "Other"},
                ),
            },
            "route": <DataProviderOptionInfo>{
                "display_name": "Worksheets of a Record",
                "short_desc": "Whether a record is written to the first worksheet it matches or to every one",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The worksheets of the `sheets` option that a record is written to: `first` (the default), "
                    "the first worksheet whose condition it matches, so a worksheet without a condition takes every "
                    "record that no worksheet before it takes; or `all`, every worksheet whose condition it matches, "
                    "so a record can be written to several, such as to a worksheet with every record and to one "
                    "with the returns",
                "default_value": "first",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "first", "display_name": "First Match",
                        "short_desc": "The first worksheet whose condition the record matches",
                        "desc": "A record is written to the first worksheet whose condition it matches"},
                    <AllowedValueInfo>{"value": "all", "display_name": "Every Match",
                        "short_desc": "Every worksheet whose condition the record matches",
                        "desc": "A record is written to every worksheet whose condition it matches"},
                ),
            },
            "unmatched": <DataProviderOptionInfo>{
                "display_name": "Records Without a Worksheet",
                "short_desc": "Whether a record that matches no worksheet fails the write or is skipped",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "What happens to a record that matches the condition of no worksheet of the `sheets` "
                    "option: `fail` (the default) raises `ODS-WRITE-ROUTE-ERROR`, naming the record and the "
                    "conditions of the worksheets; `skip` writes the record to no worksheet and does not count it",
                "default_value": "fail",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "fail", "display_name": "Fail",
                        "short_desc": "The write fails",
                        "desc": "A record that matches no worksheet raises `ODS-WRITE-ROUTE-ERROR`"},
                    <AllowedValueInfo>{"value": "skip", "display_name": "Skip",
                        "short_desc": "The record is not written",
                        "desc": "A record that matches no worksheet is not written and not counted"},
                ),
            },
        };

        #! The options of a single worksheet, which are given for each worksheet with \c sheets, and the key that
        #! each one is given with
        const SingleSheetOptions = {
            "worksheet": "worksheet",
            "headers": "columns",
            "column_formats": "column_formats",
            "column_masks": "column_masks",
            "column_headers": "column_headers",
            "write_headers": "write_headers",
            "header_row": "header_row",
        };

        #! The type of each key of a worksheet of the \c sheets option, in the order they are listed in messages
        const SheetKeys = {
            "worksheet": AbstractDataProviderType::get(StringType),
            "columns": AbstractDataProviderType::get(new Type("softlist<string>")),
            "when": AbstractDataProviderType::get(StringType),
            "column_headers": AbstractDataProviderType::get(new Type("hash<string, string>")),
            "column_formats": AbstractDataProviderType::get(new Type("hash<string, string>")),
            "column_masks": AbstractDataProviderType::get(new Type("hash<string, string>")),
            "write_headers": AbstractDataProviderType::get(SoftBoolType),
            "header_row": AbstractDataProviderType::get(IntType),
        };
    }

    private {
        #! The output path
        *string path;

        #! The output stream
        *OutputStream stream;

        #! The name of the object
        string name;

        #! The ODS writer object
        OdsWriter writer;

        #! The options
        *hash<auto> opts;

        #! Records created count
        int created = 0;

        #! The time zone whose wall-clock time dates are written in, if any
        *TimeZone timezone;

        #! The worksheets of the \c sheets option, in document order, if given
        list<hash<OdsWriteSheetInfo>> sheets = ();

        #! The number of records given to be written with \c sheets, including skipped records
        int record_no = 0;
    }

    #! Creates the OdsWriteDataProvider with the output path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        *hash<auto> copts = checkOptions("ODS-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("ODS-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("ODS-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = path;
        name = path;
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the OdsWriteDataProvider with an output stream and optionally an option hash
    constructor(OutputStream stream, *hash<auto> opts) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        *hash<auto> copts = checkOptions("ODS-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("ODS-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("ODS-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("ods-stream=%s", stream.uniqueHash());
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        *hash<auto> copts = checkOptions("ODS-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("ODS-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            path = copts.path;
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("ods-stream=%s", stream.uniqueHash());
        } else {
            name = "ods-data";
        }
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Validates header placement before creating the workbook
    private checkHeaderRow(int row, bool write_headers) {
        int maximum = 1048576;
        if (row < 1 || row > maximum || (!write_headers && row != 1)) {
            error("ODS-WRITE-OPTION-ERROR", "invalid header_row %d: expected a row from 1 to %d; "
                "a header row after row 1 requires write_headers", row, maximum);
        }
    }

    #! Sets up the writer
    /** @throw ODS-WRITE-OPTION-ERROR an invalid format code in \c column_formats, a mask without a display format
        in \c column_masks, a column with both, an invalid time zone, an invalid worksheet in \c sheets or an option
        that cannot be used with it, or with \c headers, a column option for a column that is not written
    */
    private setupWriter() {
        if (opts.timezone) {
            try {
                timezone = new TimeZone(opts.timezone);
            } catch (hash<ExceptionInfo> ex) {
                error("ODS-WRITE-OPTION-ERROR", "invalid \"timezone\" option %y: %s: %s", opts.timezone, ex.err,
                    ex.desc);
            }
        }
        if (exists opts.sheets) {
            setupSheets();
            return;
        }
        foreach string opt in ("route", "unmatched") {
            if (opts{opt} != ConstructorOptions{opt}.default_value) {
                error("ODS-WRITE-OPTION-ERROR", "the %y option is used only with the \"sheets\" option", opt);
            }
        }
        hash<string, string> formats = {};
        if (opts.column_formats) {
            checkColumnFormats(opts.column_formats);
            formats = opts.column_formats;
        }
        if (opts.column_masks) {
            formats += getMaskFormats(opts.column_masks, opts.column_formats);
        }
        if (opts.headers) {
            checkColumns(opts{"column_formats", "column_masks", "column_headers"}, opts.headers);
        }
        checkHeaderRow(opts.header_row ?? 1, opts.write_headers ?? True);
        writer = new OdsWriter(opts.worksheet ?? "Sheet1");
        # the writer is released if it cannot be set up, as the destructor does not run when a constructor fails
        on_error {
            writer.close();
            delete writer;
        }
        if (opts.headers) {
            writer.setHeaders(opts.headers);
        }
        if (formats) {
            writer.setColumnFormats(formats);
        }
        if (opts.column_headers) {
            writer.setColumnHeaders(opts.column_headers);
        }
        if (exists opts.header_row) {
            writer.setHeaderRow(opts.header_row);
        }
        if (exists opts.write_headers && !opts.write_headers) {
            writer.setWriteHeaders(False);
        }
    }

    #! Sets up the writer for the worksheets of the \c sheets option
    /** @throw ODS-WRITE-OPTION-ERROR an invalid worksheet or condition, or an option of a single worksheet
    */
    private setupSheets() {
        # the options of a single worksheet are given for each worksheet instead; write_headers is true by default
        foreach hash<auto> i in (SingleSheetOptions.pairIterator()) {
            if (i.key == "write_headers" ? (exists opts.write_headers && !opts.write_headers) : exists opts{i.key}) {
                error("ODS-WRITE-OPTION-ERROR", "cannot use the %y option with the \"sheets\" option; give each "
                    "worksheet its %y instead", i.key, i.value);
            }
        }
        if (!opts.sheets) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: no worksheets are given");
        }
        # the index of each worksheet by the key of its name
        hash<string, int> names = {};
        foreach hash<auto> sheet in (opts.sheets) {
            sheets += getSheet(sheet, $#, \names);
        }
        writer = new OdsWriter(sheets[0].name);
        # the writer is released if it cannot be set up, as the destructor does not run when a constructor fails
        on_error {
            writer.close();
            delete writer;
        }
        foreach hash<OdsWriteSheetInfo> sheet in (sheets) {
            int i = $# ? writer.addSheet(sheet.name) : 0;
            if (sheet.columns) {
                writer.setHeaders(i, sheet.columns);
            }
            if (sheet.formats) {
                writer.setColumnFormats(i, sheet.formats);
            }
            if (sheet.column_opts.column_headers) {
                writer.setColumnHeaders(i, sheet.column_opts.column_headers);
            }
            writer.setHeaderRow(i, sheet.header_row);
            if (!sheet.write_headers) {
                writer.setWriteHeaders(i, False);
            }
        }
    }

    #! Returns a worksheet of the \c sheets option
    /** @param sheet the worksheet as given
        @param i the 0-based position of the worksheet
        @param names the index of each worksheet so far by the key of its name; the worksheet is added

        @return the worksheet

        @throw ODS-WRITE-OPTION-ERROR an invalid worksheet or condition
    */
    private hash<OdsWriteSheetInfo> getSheet(hash<auto> sheet, int i, reference<hash<string, int>> names) {
        if (!exists sheet.worksheet) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: worksheet %d has no \"worksheet\" name",
                i + 1);
        }
        hash<auto> values = getSheetValues(sheet, sprintf("worksheet %d", i + 1), ("worksheet",));
        string name = values.worksheet;
        if (*string issue = OdsWriter::sheetNameIssue(name)) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: worksheet %d: invalid worksheet name %y: %s",
                i + 1, name, issue);
        }
        string key = OdsWriter::sheetNameKey(name);
        if (exists names{key}) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: worksheet %d: the worksheet name %y is "
                "already the name of worksheet %d (%y); worksheet names must be unique, regardless of case", i + 1,
                name, names{key} + 1, sheets[names{key}].name);
        }
        names{key} = i;

        string label = sprintf("worksheet %y", name);
        if (*list<string> unknown = keys (sheet - keys SheetKeys)) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: %s: unknown %s %s; the keys of a worksheet "
                "are: %s", label, unknown.size() == 1 ? "key" : "keys", (foldl $1 + ", " + $2,
                (map sprintf("%y", $1), unknown)), (foldl $1 + ", " + $2, keys SheetKeys));
        }
        values += getSheetValues(sheet, label, keys (SheetKeys - "worksheet"));
        if (exists values.columns && !values.columns) {
            error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: %s: \"columns\" is empty; leave it out to "
                "take the columns from the first record written to the worksheet", label);
        }

        hash<OdsWriteSheetInfo> rv = <OdsWriteSheetInfo>{
            "name": name,
            "columns": values.columns,
            "when": values.when,
            "column_opts": values{"column_headers", "column_formats", "column_masks"},
            "write_headers": values.write_headers ?? True,
            "header_row": values.header_row ?? 1,
        };
        checkHeaderRow(rv.header_row, rv.write_headers);
        if (exists values.when) {
            try {
                rv.condition = DataProvider::parseDpqlExpression(values.when);
            } catch (hash<ExceptionInfo> ex) {
                error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: %s: invalid \"when\" condition %y: %s: "
                    "%s", label, values.when, ex.err, ex.desc);
            }
        }
        if (values.column_formats) {
            checkColumnFormats(values.column_formats, label);
            rv.formats = values.column_formats;
        }
        if (values.column_masks) {
            rv.formats += getMaskFormats(values.column_masks, values.column_formats, label);
        }
        if (values.columns) {
            checkColumns(rv.column_opts, values.columns, label);
        }
        return rv;
    }

    #! Returns the values of keys of a worksheet of the \c sheets option, with their types
    /** @param sheet the worksheet as given
        @param label the worksheet in messages
        @param key_list the keys to return

        @return the values of the keys that have a value

        @throw ODS-WRITE-OPTION-ERROR a value of the wrong type
    */
    private hash<auto> getSheetValues(hash<auto> sheet, string label, list<string> key_list) {
        hash<auto> rv = {};
        foreach string key in (key_list) {
            if (!exists sheet{key}) {
                continue;
            }
            AbstractDataProviderType type = SheetKeys{key};
            try {
                rv{key} = type.acceptsValue(sheet{key});
            } catch (hash<ExceptionInfo> ex) {
                # only a value that the type refuses is an invalid option; any other error, including a
                # RUNTIME-TYPE-ERROR, means that the check itself failed and is not reported as the user's error
                if (ex.err != "TYPE-VALUE-ERROR") {
                    rethrow;
                }
                error("ODS-WRITE-OPTION-ERROR", "invalid \"sheets\" option: %s: %y must be a value of type %y, not "
                    "%y", label, key, type.getName(), sheet{key}.fullType());
            }
        }
        return rv;
    }

    #! Returns the beginning of the message of an invalid column option
    /** @param opt the column option
        @param label the worksheet of the \c sheets option the option is given for, if any
    */
    private static string getColumnOptionError(string opt, *string label) {
        return label
            ? sprintf("invalid \"sheets\" option: %s: invalid %y", label, opt)
            : sprintf("invalid %y option", opt);
    }

    #! Checks column format codes: each format code is valid
    /** @param formats the format code of each column
        @param label the worksheet of the \c sheets option the formats are given for, if any

        @throw ODS-WRITE-OPTION-ERROR an invalid format code
    */
    private checkColumnFormats(hash<string, string> formats, *string label) {
        foreach hash<auto> i in (formats.pairIterator()) {
            *string issue = OdsWriter::formatCodeIssue(i.value);
            if (issue) {
                error("ODS-WRITE-OPTION-ERROR", "%s: column %y: %s", getColumnOptionError("column_formats", label),
                    i.key, issue);
            }
        }
    }

    #! Returns the display format codes of column masks
    /** @param masks the mask of each column
        @param formats the format code of each column, if any
        @param label the worksheet of the \c sheets option the masks are given for, if any

        @throw ODS-WRITE-OPTION-ERROR a mask without a display format, or a column with both a mask and a format
        code
    */
    private hash<string, string> getMaskFormats(hash<string, string> masks, *hash<string, string> formats,
            *string label) {
        hash<string, string> rv = {};
        foreach hash<auto> i in (masks.pairIterator()) {
            if (exists formats{i.key}) {
                error("ODS-WRITE-OPTION-ERROR", "%s: column %y also has a format code in %s; a column can have one "
                    "or the other", getColumnOptionError("column_masks", label), i.key,
                    label ? "\"column_formats\"" : "the \"column_formats\" option");
            }
            if (*string issue = ValueMask::spreadsheetFormatIssue(i.value)) {
                error("ODS-WRITE-OPTION-ERROR", "%s: column %y: %s", getColumnOptionError("column_masks", label),
                    i.key, issue);
            }
            rv{i.key} = ValueMask::toSpreadsheetFormat(i.value);
        }
        return rv;
    }

    #! Checks that each column in the \c column_formats, \c column_masks and \c column_headers options is written
    /** @param column_opts the column options
        @param columns the columns written
        @param label the worksheet of the \c sheets option the options are given for, if any

        @throw ODS-WRITE-OPTION-ERROR a column option for a column that is not written
    */
    private checkColumns(*hash<auto> column_opts, list<string> columns, *string label) {
        hash<string, bool> written = map {$1: True}, columns;
        foreach hash<auto> i in ({
                "column_formats": "a format",
                "column_masks": "a mask",
                "column_headers": "a header",
            }.pairIterator()) {
            *list<string> unknown = select keys column_opts{i.key}, !written{$1};
            if (unknown) {
                error("ODS-WRITE-OPTION-ERROR", "%s: %s is given for %s %y, which %s not written; the columns "
                    "written are: %y", getColumnOptionError(i.key, label), i.value,
                    unknown.size() == 1 ? "column" : "columns", unknown.size() == 1 ? unknown[0] : unknown,
                    unknown.size() == 1 ? "is" : "are", columns);
            }
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Destructor - ensures file is written
    destructor() {
        commit();
    }

    #! Creates a single record in the data provider
    /** @param rec the record to create
        @param create_options no options are currently supported

        @return the record created (echoes back the input)

        @throw ODS-WRITE-OPTION-ERROR without \c headers, the first record has no key for a column in
        \c column_formats, \c column_masks or \c column_headers; with \c sheets, the same for the first record written
        to a worksheet without \c columns
        @throw ODS-WRITE-ROUTE-ERROR with \c sheets, the record matches no worksheet and \c unmatched is \c "fail",
        or the condition of a worksheet cannot be evaluated for the record
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        if (sheets) {
            return createSheetRecord(rec);
        }
        # without headers, the first record's keys are the columns
        if (!created && !opts.headers && (opts.column_formats || opts.column_masks || opts.column_headers)) {
            checkColumns(opts{"column_formats", "column_masks", "column_headers"}, keys rec);
        }
        writer.writeRow(getRow(rec));
        ++created;
        return rec;
    }

    #! Writes a record to the worksheets of the \c sheets option whose conditions it matches
    /** @param rec the record to write

        @return the record

        @throw ODS-WRITE-OPTION-ERROR the first record written to a worksheet without \c columns has no key for a
        column in its \c column_formats, \c column_masks or \c column_headers
        @throw ODS-WRITE-ROUTE-ERROR the record matches no worksheet and \c unmatched is \c "fail", or the
        condition of a worksheet cannot be evaluated for the record
    */
    private hash<auto> createSheetRecord(hash<auto> rec) {
        ++record_no;
        list<int> targets = getRecordSheets(rec);
        if (!targets) {
            if (opts.unmatched == "skip") {
                return rec;
            }
            error("ODS-WRITE-ROUTE-ERROR", "record %d matches no worksheet; the conditions of the worksheets are: "
                "%s; with \"unmatched\": \"skip\", records that match no worksheet are skipped", record_no,
                (foldl $1 + "; " + $2, (map sprintf("%y: %s", $1.name, $1.when), sheets)));
        }
        hash<auto> row = getRow(rec);
        foreach int i in (targets) {
            # the first record written to a worksheet without columns: its keys are the columns
            if (!sheets[i].columns) {
                list<string> columns = keys rec;
                checkColumns(sheets[i].column_opts, columns, sprintf("worksheet %y", sheets[i].name));
                sheets[i].columns = columns;
            }
            writer.writeRow(i, row);
            ++sheets[i].count;
        }
        ++created;
        return rec;
    }

    #! Returns the index of each worksheet of the \c sheets option that a record is written to
    /** @param rec the record

        @return the index of the first worksheet whose condition the record matches, or with \c route \c "all", of
        each one; no value if it matches none

        @throw ODS-WRITE-ROUTE-ERROR the condition of a worksheet cannot be evaluated for the record
    */
    private list<int> getRecordSheets(hash<auto> rec) {
        list<int> rv = ();
        foreach hash<OdsWriteSheetInfo> sheet in (sheets) {
            if (sheet.condition) {
                bool match;
                try {
                    match = boolean(AbstractDataProvider::evalGenericExpression(rec, sheet.condition));
                } catch (hash<ExceptionInfo> ex) {
                    error("ODS-WRITE-ROUTE-ERROR", "record %d: the condition of worksheet %y (%s) cannot be "
                        "evaluated: %s: %s", record_no, sheet.name, sheet.when, ex.err, ex.desc);
                }
                if (!match) {
                    continue;
                }
            }
            rv += $#;
            if (opts.route != "all") {
                break;
            }
        }
        return rv;
    }

    #! Returns the row written for a record: its dates as the wall-clock time in the \c timezone, if given
    private hash<auto> getRow(hash<auto> rec) {
        if (!timezone) {
            return rec;
        }
        return map {$1.key: $1.value.typeCode() == NT_DATE ? timezone.date($1.value) : $1.value}, rec.pairIterator();
    }

    #! Commits (writes) the ODS file
    commit() {
        if (!writer) {
            return;
        }
        on_exit {
            writer.close();
            delete writer;
        }
        if (path) {
            OutputStreamWrapper os = FileLocationHandler::getOutputStreamForLocation(path);
            on_exit {
                os.close();
                os.waitForIo();
            }
            writer.writeToStream(os);
        } else if (stream) {
            writer.writeToStream(stream);
        }
    }

    #! Completes the create operations: writes the ODS data to its path or stream, if any
    /** @return the generated ODS data and the number of records in it, if there is no output path or stream;
        otherwise no value is returned; with \c sheets, an @ref OdsGeneratedDataInfo hash that also gives the
        number of records written to each worksheet, and \c record_count counts the records written to one worksheet
        or more
    */
    private *hash<GeneratedDataInfo> finishCreateImpl() {
        if (path || stream) {
            commit();
            return;
        }
        # the data must be read before commit() closes the writer
        on_exit commit();
        if (sheets) {
            hash<string, int> sheet_counts = {};
            foreach hash<OdsWriteSheetInfo> sheet in (sheets) {
                sheet_counts{sheet.name} = sheet.count;
            }
            return <OdsGeneratedDataInfo>{
                "data": getData(),
                "record_count": created,
                "sheet_counts": sheet_counts,
            };
        }
        return <GeneratedDataInfo>{
            "data": getData(),
            "record_count": created,
        };
    }

    #! Returns the data as binary
    binary getData() {
        if (!writer) {
            throw "ODS-WRITE-ERROR", "writer has already been committed";
        }
        return writer.getBytes();
    }

    #! Returns the number of records created
    int getCreatedCount() {
        return created;
    }

    #! Returns the record type: the \c headers columns, or an open record type if headers are taken from the first record
    /** @param search_options ignored

        @return a field for each column in the \c headers option, or @ref NOTHING (an open record shape) if no
        headers are given, in which case the first record's keys are used as the columns, or with \c sheets, as the
        conditions of the worksheets can use fields that are not written
    */
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {
            $1: new QoreDataField($1, "ODS column " + $1, AbstractDataProviderType::anyDataType)
        }, opts.headers;
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # OdsDataProvider namespace
