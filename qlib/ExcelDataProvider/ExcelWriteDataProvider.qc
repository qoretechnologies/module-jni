# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore ExcelWriteDataProvider class definition

/*  ExcelWriteDataProvider.qc Copyright 2021 - 2026 Qore Technologies, s.r.o.

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

#! the ExcelDataProvider namespace. All classes used in the ExcelDataProvider module should be inside this namespace
public namespace ExcelDataProvider {
#! Provides a data provider for writing EXCEL files
public class ExcelWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "ExcelWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "Excel Output Path",
                "short_desc": "The location to write the Excel file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location for the output Excel data file; mutually exclusive with `stream`; "
                    "the output location is handled by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "Excel Output Stream",
                "short_desc": "An output stream for Excel data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for Excel data; mutually exclusive with `path`",
            },
            "worksheet": <DataProviderOptionInfo>{
                "display_name": "Worksheet Name",
                "short_desc": "The name of the worksheet to create",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of the worksheet to create; default is `Sheet1`",
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Column Headers",
                "short_desc": "List of header / column names for the data",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of header / column names for the data; if not provided, headers will be "
                    "auto-detected from the first record",
            },
            "format": <DataProviderOptionInfo>{
                "display_name": "Output Format",
                "short_desc": "Excel format to use",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Excel format to use: `xlsx` (default, Excel 2007+) or `xls` (Excel 97-2003)",
                "default_value": "xlsx",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "xlsx", "display_name": "XLSX",
                        "short_desc": "Excel 2007+ format (.xlsx)",
                        "desc": "Excel 2007+ format (`.xlsx`); supports streaming mode for large files"},
                    <AllowedValueInfo>{"value": "xls", "display_name": "XLS",
                        "short_desc": "Excel 97-2003 format (.xls)",
                        "desc": "Legacy Excel 97-2003 binary format (`.xls`); limited to 65,536 rows "
                            "and 256 columns per sheet"},
                ),
            },
            "streaming": <DataProviderOptionInfo>{
                "display_name": "Streaming Mode",
                "short_desc": "Use streaming mode for large files",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `True`, use SXSSF streaming mode for writing large files with reduced memory "
                    "usage; only applies to `xlsx` format",
            },
            "column_formats": <DataProviderOptionInfo>{
                "display_name": "Column Display Formats",
                "short_desc": "The display format of each column, such as dd.mm.yyyy or #,##0.00",
                "type": AbstractDataProviderType::get(new Type("hash<string, string>")),
                "desc": "The display format of columns by their header name, so that whoever opens the file sees "
                    "each value as intended: a date as `dd.mm.yyyy`, an amount as `#,##0.00`, or a share as "
                    "`0.0%`.  The cells keep their real values (a date stays a date and a number a number); only "
                    "the way they are shown changes.  A column without a format is written as without this "
                    "option: dates as `yyyy-mm-dd hh:mm:ss` and numbers without a format.\n\n"
                    "Format codes are written as in Excel and LibreOffice:\n"
                    "- **numbers**: `0` (a digit that is always shown), `#` (a digit shown when significant), `,` "
                    "between digits (thousands grouping), `,` after the last digit (divide by 1000), `.` (the "
                    "decimal point), and `E+00` (scientific notation); up to three sections for positive, negative "
                    "and zero values separated by `;`, such as `#,##0.00;(#,##0.00)` or `#,##0.00;#,##0.00-`\n"
                    "- **percentages**: a `%` in each section, such as `0.0%`; the value is a fraction, so `0.25` "
                    "is shown as `25.0%`\n"
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
                    "`EXCEL-WRITE-OPTION-ERROR` naming the column and the problem",
                "example_value": {"Date": "dd.mm.yyyy", "Amount": "#,##0.00;(#,##0.00)", "Share": "0.0%"},
            },
        };
    }

    private {
        #! The output path
        *string path;

        #! The output stream
        *OutputStream stream;

        #! The name of the object
        string name;

        #! The Excel writer object
        ExcelWriter writer;

        #! The options
        *hash<auto> opts;

        #! Records created count
        int created = 0;
    }

    #! Creates the ExcelWriteDataProvider with the output path and optionally an option hash
    /** @param path the output file path
        @param opts a hash of optional options

        @throw EXCEL-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("EXCEL-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("EXCEL-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("EXCEL-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = path;
        name = path;
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the ExcelWriteDataProvider with an output stream and optionally an option hash
    /** @param stream the output stream to write to
        @param opts a hash of optional options

        @throw EXCEL-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(OutputStream stream, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("EXCEL-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("EXCEL-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("EXCEL-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("excel-stream=%s", stream.uniqueHash());
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the object from constructor options
    /**
        @throw EXCEL-WRITE-OPTION-ERROR invalid or unknown option

        @note If neither \c path nor \c stream is provided, use getData() to retrieve the Excel data as binary
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("EXCEL-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("EXCEL-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            path = copts.path;
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("excel-stream=%s", stream.uniqueHash());
        } else {
            # No path or stream - user will use getData() to retrieve binary data
            name = "excel-data";
        }
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Sets up the writer
    /** @throw EXCEL-WRITE-OPTION-ERROR an invalid format code in \c column_formats, or a format for a column that
        is not in \c headers
    */
    private setupWriter() {
        if (opts.column_formats) {
            checkColumnFormats();
        }
        writer = new ExcelWriter(opts.worksheet ?? "Sheet1", opts.format ?? "xlsx", opts.streaming ?? False);
        if (opts.headers) {
            writer.setHeaders(opts.headers);
        }
        if (opts.column_formats) {
            writer.setColumnFormats(opts.column_formats);
        }
    }

    #! Checks the \c column_formats option: each format code is valid, and with \c headers, each column is written
    /** @throw EXCEL-WRITE-OPTION-ERROR an invalid format code, or a format for a column that is not in \c headers
    */
    private checkColumnFormats() {
        foreach hash<auto> i in (opts.column_formats.pairIterator()) {
            *string issue = ExcelWriter::formatCodeIssue(i.value);
            if (issue) {
                error("EXCEL-WRITE-OPTION-ERROR", "invalid \"column_formats\" option: column %y: %s", i.key, issue);
            }
        }
        if (opts.headers) {
            checkFormatColumns(opts.headers);
        }
    }

    #! Checks that each column in the \c column_formats option is written
    /** @param columns the columns written

        @throw EXCEL-WRITE-OPTION-ERROR a format for a column that is not written
    */
    private checkFormatColumns(*list<string> columns) {
        hash<string, bool> written = map {$1: True}, columns;
        *list<string> unknown = select keys opts.column_formats, !written{$1};
        if (unknown) {
            error("EXCEL-WRITE-OPTION-ERROR", "invalid \"column_formats\" option: a format is given for %s %y, "
                "which %s not written; the columns written are: %y", unknown.size() == 1 ? "column" : "columns",
                unknown.size() == 1 ? unknown[0] : unknown, unknown.size() == 1 ? "is" : "are", columns);
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

        @throw EXCEL-WRITE-OPTION-ERROR without \c headers, the first record has no key for a column in
        \c column_formats
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        # without headers, the first record's keys are the columns
        if (!created && !opts.headers && opts.column_formats) {
            checkFormatColumns(keys rec);
        }
        writer.writeRow(rec);
        ++created;
        return rec;
    }

    #! Commits (writes) the Excel file
    /** Call this method when you are done writing records to ensure the file is written.
        This is also called automatically in the destructor.
    */
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

    #! Completes the create operations: writes the Excel data to its path or stream, if any
    /** @return the generated Excel data and the number of records in it, if there is no output path or stream;
        otherwise no value is returned
    */
    private *hash<GeneratedDataInfo> finishCreateImpl() {
        if (path || stream) {
            commit();
            return;
        }
        # the data must be read before commit() closes the writer
        on_exit commit();
        return <GeneratedDataInfo>{
            "data": getData(),
            "record_count": created,
        };
    }

    #! Returns the data as binary
    /** @return the Excel file contents as binary data

        @note This is an alternative to using path or stream - get the data directly
    */
    binary getData() {
        if (!writer) {
            throw "EXCEL-WRITE-ERROR", "writer has already been committed";
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
        headers are given, in which case the first record's keys are used as the columns
    */
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {
            $1: new QoreDataField($1, "Excel column " + $1, AbstractDataProviderType::anyDataType)
        }, opts.headers;
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # ExcelDataProvider namespace
