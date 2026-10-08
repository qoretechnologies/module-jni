# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore ExcelReadDataProvider class definition

/*  ExcelReadDataProvider.qc Copyright 2021 - 2026 Qore Technologies, s.r.o.

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
#! Designates a cell location in a worksheet
public hashdecl CellLocationInfo {
    #! The column name
    string column;
    #! The row number
    int row;
}

#! Designates a range of cells
public hashdecl CellRangeInfo {
    #! The start cell
    hash<CellLocationInfo> start;
    #! The end cell
    hash<CellLocationInfo> end;
}

#! Provides a data provider for reading EXCEL files
public class ExcelReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "ExcelReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "supports_children": True,
            "constructor_options": ConstructorOptions,
            "search_options": SearchOptions,
            "children_can_support_records": True,
        };

        #! Search options
        const SearchOptions = GenericRecordSearchOptions{"requires_result", "max_records"} + {
            "limit": <DataProviderOptionInfo>{
                "display_name": "Record Limit",
                "short_desc": "The maximum number of records to return",
                "type": AbstractDataProviderType::get(IntType),
                "desc": "The maximum number of records to return",
            },
        };

        #! Constructor options
        const ConstructorOptions = {
            "data": <DataProviderOptionInfo>{
                "display_name": "Input Data",
                "short_desc": "Excel data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "Excel data to process; conflicts with `stream` and `path`",
            },
            "data_cells": <DataProviderOptionInfo>{
                "display_name": "Data Cells",
                "short_desc": "The range of cells for data to process",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The range of cells for data to process",
                "example_value": "b2:f10",
            },
            "formula_field": <DataProviderOptionInfo>{
                "display_name": "Formula Field",
                "short_desc": "Return the formulas of each row's cells in a field with this name",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of an extra field added to every record with the formulas of the row's cells, "
                    "so the process reading the file can tell rows calculated by formulas from rows of data.  The "
                    "most common use is recognizing a **totals row made only of `SUM` formulas**, with no "
                    "`Total` label, at run time in the same way as the spreadsheet profile does at design time: "
                    "pass the field's value to `DataProvider::TabularProfiler::hasTotalsFormula()`.\n\n"
                    "**Value:** a hash of the formula text (as Excel shows it, without the leading `=`) by the "
                    "cell's reference in the worksheet (for example `{\"B5\": \"SUM(B2:B4)\"}`), with one "
                    "entry for each cell of the record that holds a formula; `NOTHING` if none of the record's "
                    "cells holds a formula.  References are those of the worksheet, whatever the `data_cells` "
                    "range.\n\n"
                    "Values of formula cells are still returned as their calculated results.  The name must not "
                    "be the name of a column of the worksheet; a name that is raises "
                    "`EXCEL-FORMULA-FIELD-ERROR`.  If not set, records have no formula field and formulas are "
                    "not read.",
                "example_value": "_formulas",
            },
            "header_cells": <DataProviderOptionInfo>{
                "display_name": "Header Cells",
                "short_desc": "The range of cells for headers or a row number",
                "type": AbstractDataProviderType::get(SoftStringType),
                "desc": "The range of cells for headers or a row number",
                "example_value": "a1:f10",
            },
            "header_names": <DataProviderOptionInfo>{
                "display_name": "Header Names?",
                "short_desc": "Should header rows be used as output field names?",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `True` then the object will parse the header names from the first header row, in this "
                    "case if `header_cells` is not set explicitly, it will be assumed to be the first row",
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Output Headers",
                "short_desc": "List of header / column names for the data iterated",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of header / column names for the data iterated; if this is present, then "
                    "`header_names` must be `False`",
            },
            "ignore_empty": <DataProviderOptionInfo>{
                "display_name": "Ignore Empty Rows",
                "short_desc": "Skip empty rows instead of stopping at the first one",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `True`, empty rows are skipped and reading continues to the end of `data_cells` or the "
                    "last row of the worksheet; if `False` (the default), reading stops at the first empty row",
                "default_value": False,
                "metadata": {
                    # what each value does with an empty row: with the default, an empty row ends the data
                    "empty_row_handling": {
                        "false": "stop",
                        "true": "skip",
                    },
                },
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "Excel Input Path",
                "short_desc": "The location of the Excel data",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the Excel data; mutually exclusive with `data` and `stream`; "
                    "resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "Excel Input Stream",
                "short_desc": "An input stream for Excel data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for Excel data; mutually exclusive with `data` and `path`",
            },
            "timezone": <DataProviderOptionInfo>{
                "display_name": "Timezone Region",
                "short_desc": "The timezone region to use when parsing dates",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The timezone region to use when parsing dates",
                "example_value": "Europe/Prague",
            },
            "tolwr": <DataProviderOptionInfo>{
                "display_name": "Convert Headers to Lower Case",
                "short_desc": "Convert all header names to lower case",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `True` (default `False`), all automatically-detected header names will be converted to "
                    "lower case letters",
            },
            "worksheet": <DataProviderOptionInfo>{
                "display_name": "Worksheet Name",
                "short_desc": "The name of the worksheet to iterate",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of the worksheet to iterate, matched without regard to case, or its 0-based position as a string; if missing, the first worksheet is processed; conflicts with `worksheet_pattern`.  An unknown worksheet raises `EXCEL-WORKSHEET-NOT-FOUND`, which lists the worksheets of the file",
                "metadata": {
                    OptionInteractionsKey: (<DataProviderOptionInteraction>{
                        "kind": OI_Conflicts,
                        "option": "worksheet_pattern",
                        "desc": "a worksheet is chosen either by name or by pattern",
                    },),
                },
            },
            "worksheet_pattern": <DataProviderOptionInfo>{
                "display_name": "Worksheet Name Pattern",
                "short_desc": "A regular expression matching the name of the worksheet to iterate",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "A regular expression that the name of exactly one worksheet must match, for files whose "
                    "worksheet name changes from file to file, such as `Jan 2026` and `Feb 2026`: use `^\\w+ \\d{4}$`, "
                    "or `(?i)^export` to match without regard to case; the pattern is searched in the name, so "
                    "anchor it with `^` and `$` to match the whole name.  No match raises "
                    "`EXCEL-WORKSHEET-NOT-FOUND` and more than one match raises `EXCEL-WORKSHEET-AMBIGUOUS`, both "
                    "listing the worksheets of the file; conflicts with `worksheet`",
                "example_value": "^Export_\\d{8}$",
                "metadata": {
                    OptionInteractionsKey: (<DataProviderOptionInteraction>{
                        "kind": OI_Conflicts,
                        "option": "worksheet",
                        "desc": "a worksheet is chosen either by name or by pattern",
                    },),
                },
            },
        };
    }

    private {
        #! The input stream for the Excel data; copied to a temporary file on first use, then released
        *InputStream i;

        #! The Excel data given with the \c data option; copied to a temporary file on first use, then released
        *binary data;

        #! The Excel data in a temporary file, created on first use
        /** The workbook is opened once for each use (record type and records, worksheet names, worksheet providers),
            and an input stream can only be read once, so it is copied to a file, in bounded memory, and opened from
            there
        */
        *ExcelFile file;

        #! Serializes copying the input on first use
        Mutex data_lock();

        #! the iterator object
        ExcelIterator it;

        #! The name of the object
        string name;

        #! the name of the worksheet
        string sheet;

        #! the regular expression that the name of the worksheet to iterate must match, if any
        *string sheet_pattern;

        #! True once the worksheet to iterate has been resolved against the worksheets of the file
        bool sheet_resolved;

        #! dummy iterator
        bool dummy;

        #! Excel iteration options
        *hash<auto> opts;
    }

    #! Creates the ExcelReadDataProvider with the input data path and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::FileLocationHandler::getBinaryStreamFromLocation()
        @param opts a hash of optional options

        @throw EXCEL-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_names"
        is @ref True "True" and \c "headers" is also present; unknown field type
        @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        self.opts = processCellOptions(checkOptions("EXCEL-READ-OPTION-ERROR", ConstructorOptions, opts));
        sheet = self.opts.worksheet ?? "";
        sheet_pattern = self.opts.worksheet_pattern;
    }

    #! Creates the ExcelReadDataProvider with the input data stream and optionally an option hash
    /** @param stream the input stream to iterate
        @param opts a hash of optional options

        @throw EXCEL-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_names"
        is @ref True "True" and \c "headers" is also present; unknown field type
        @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        self.opts = processCellOptions(checkOptions("EXCEL-READ-OPTION-ERROR", ConstructorOptions, opts));
        name = sprintf("stream=%s", stream.uniqueHash());
        sheet = self.opts.worksheet ?? "";
        sheet_pattern = self.opts.worksheet_pattern;
    }

    #! Creates the object from constructor options
    /**
        @throw EXCEL-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_names"
        is @ref True "True" and \c "headers" is also present; unknown field type
        @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    constructor(*hash<auto> options) {
        @debug {
            LoggerWrapper::debug("ExcelReadDataProvider::constructor() options: %y", options);
        }
        *hash<auto> copts = processCellOptions(checkOptions("EXCEL-READ-OPTION-ERROR", ConstructorOptions, options));
        sheet = copts.worksheet ?? "";
        sheet_pattern = copts.worksheet_pattern;
        if (copts.path) {
            if (copts.stream) {
                error("EXCEL-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("EXCEL-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("EXCEL-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("excel-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            # Handle binary data input
            data = binary(copts.data);
            name = sprintf("excel-data=%s", self.uniqueHash());
        }
        self.opts = copts - ("stream", "path", "worksheet", "worksheet_pattern", "data");
        if (!i && !exists data) {
            # no input: a provider without worksheets or records
            data = binary();
            name = "excel";
            dummy = True;
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Validates option combinations and parses cell range options
    /** @throw EXCEL-READ-OPTION-ERROR \c "headers" and \c "header_names" are both given
        @throw EXCEL-INVALID-LOCATION invalid excel location or range string
        @throw EXCEL-FORMULA-FIELD-ERROR the \c formula_field option is an empty string
    */
    static *hash<auto> processCellOptions(*hash<auto> opts) {
        if (opts) {
            # parsed ranges replace the range strings, so the hash must accept any value type
            opts = {} + opts;
        }
        if (opts.worksheet.val() && exists opts.worksheet_pattern) {
            throw "EXCEL-READ-OPTION-ERROR", "cannot provide both \"worksheet\" and \"worksheet_pattern\" "
                "options; use either one or the other";
        }
        if (exists opts.formula_field && !opts.formula_field.val()) {
            throw "EXCEL-FORMULA-FIELD-ERROR", "the \"formula_field\" option must not be empty";
        }
        if (opts.headers && opts.header_names) {
            throw "EXCEL-READ-OPTION-ERROR", "cannot provide both \"headers\" and \"header_names\" options; use "
                "either one or the other";
        }
        if (opts.header_cells) {
            opts.header_cells = ExcelReadDataProvider::parseRange(opts.header_cells);
        }
        if (opts.data_cells) {
            opts.data_cells = ExcelReadDataProvider::parseRange(opts.data_cells);
            if (opts.header_cells) {
                if (ExcelReadDataProvider::getColumnIndex(opts.header_cells.start.column)
                        > ExcelReadDataProvider::getColumnIndex(opts.data_cells.start.column)) {
                    throw "EXCEL-INVALID-LOCATION", sprintf("start column for header cells (%y) is after the start "
                        "column for data cells (%y)", opts.header_cells.start, opts.data_cells.start);
                }
                if (opts.header_cells.start.row > opts.data_cells.start.row) {
                    throw "EXCEL-INVALID-LOCATION", sprintf("start row for header cells (%y) is after the start row "
                        "for data cells  (%y)", opts.header_cells.start, opts.data_cells.start);
                }
            }
        }
        return opts;
    }

    #! Parses a string that designates a range of cells (ex: \c "a1:e10")
    /** @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    static hash<CellRangeInfo> parseRange(string range) {
        (*string start, *string end) = (range =~ x/^((?:[a-z]+)?[0-9]+)(?::((?:[a-z]+)?[0-9]+))?$/i);
        if (!start) {
            throw "EXCEL-INVALID-LOCATION", sprintf("range %y does not have the format [a-z]+[0-9]+:[a-z]+[0-9]+", range);
        }
        hash<CellRangeInfo> rv = <CellRangeInfo>{
            "start": ExcelReadDataProvider::parseCellLocation(start),
        };
        if (end) {
            rv.end = ExcelReadDataProvider::parseCellLocation(end);
            if (ExcelReadDataProvider::getColumnIndex(rv.start.column)
                    > ExcelReadDataProvider::getColumnIndex(rv.end.column)) {
                throw "EXCEL-INVALID-LOCATION", sprintf("start column (%y) is after the end column (%y)", start, end);
            }
            if (rv.start.row > rv.end.row) {
                throw "EXCEL-INVALID-LOCATION", sprintf("start row (%y) is after the end row (%y)", start, end);
            }
        } else {
            rv.end = <CellLocationInfo>{
                "column": "-",
                "row": -1,
            };
        }
        return rv;
    }

    #! Returns the zero-based index of a column name (ex: \c "a" -> 0, \c "z" -> 25, \c "aa" -> 26)
    /** Column names are case-insensitive; \c "-" (no column given) is the first column
    */
    static int getColumnIndex(string column) {
        if (column == "-") {
            return 0;
        }
        string col = column.upr();
        int rv = 0;
        for (int n = 0; n < col.length(); ++n) {
            rv = rv * 26 + (ord(col[n]) - ord("A") + 1);
        }
        return rv - 1;
    }

    #! Parses a string that designates a cell location (ex: \c "a1")
    /** @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    static hash<CellLocationInfo> parseCellLocation(string location) {
        *list<*string> row_values = (location =~ x/^(?:[a-z]+)?([0-9]+)$/i);
        hash<CellLocationInfo> rv = <CellLocationInfo>{
            "column": (location =~ x/^([a-z]+)[0-9]+/i)[0] ?? "-",
            "row": (row_values[1] ?? row_values[0] ?? -1).toInt(),
        };
        if (rv.row < 1) {
            throw "EXCEL-INVALID-LOCATION", sprintf("row %y is invalid, rows numbers must be >= 1", rv.row);
        }
        return rv;
    }

    #! Returns the workbook file, copying the input to it on first use
    /** The input is copied once and released; every later use of the workbook opens the file, so the record type,
        the records, the worksheet names, and the worksheet providers can be acquired in any order

        @return the workbook file
    */
    private ExcelFile getWorkbookFile() {
        AutoLock al(data_lock);
        if (!file) {
            file = new ExcelFile(exists data ? new BinaryInputStream(data) : i);
            remove i;
            remove data;
        }
        return file;
    }

    #! Deletes the workbook file once the worksheet providers using it are also destroyed
    destructor() {
        if (file) {
            file.close();
        }
    }

    #! Resolves the worksheet to iterate against the worksheets of the file
    /** @throw EXCEL-WORKSHEET-NOT-FOUND no worksheet has the name or position, or matches the pattern
        @throw EXCEL-WORKSHEET-AMBIGUOUS more than one worksheet matches the pattern
    */
    private resolveSheet() {
        if (sheet_resolved) {
            return;
        }
        sheet = ExcelReadDataProvider::resolveWorksheet(getChildProviderNamesImpl() ?? (), sheet, sheet_pattern);
        sheet_resolved = True;
    }

    #! Returns the name of the worksheet to read, given the worksheets of a file
    /** @param names the names of the worksheets of the file, in order
        @param sheet the worksheet option: a name, matched without regard to case, or a 0-based position
        as a string; an empty string means the first worksheet
        @param pattern a regular expression that the name of exactly one worksheet must match, if any

        @return the name of the worksheet to read, or an empty string for the first worksheet

        @throw EXCEL-WORKSHEET-NOT-FOUND no worksheet has the name or position, or matches the pattern; \c arg has
        the \c worksheet or \c worksheet_pattern and the \c available worksheet names
        @throw EXCEL-READ-OPTION-ERROR the pattern is not a valid regular expression
        @throw EXCEL-WORKSHEET-AMBIGUOUS more than one worksheet matches the pattern; \c arg has the
        \c worksheet_pattern, the \c matches, and the \c available worksheet names
    */
    static string resolveWorksheet(list<string> names, string sheet, *string pattern) {
        if (exists pattern) {
            list<string> matches;
            try {
                matches = select names, regex($1, pattern);
            } catch (hash<ExceptionInfo> ex) {
                throw "EXCEL-READ-OPTION-ERROR", sprintf("invalid \"worksheet_pattern\" regular expression %y: "
                    "%s", pattern, ex.desc);
            }
            if (!matches) {
                throw "EXCEL-WORKSHEET-NOT-FOUND", sprintf("no worksheet name matches %y; the worksheets of the file "
                    "are: %y", pattern, names), {"worksheet_pattern": pattern, "available": names};
            }
            if (matches.size() > 1) {
                throw "EXCEL-WORKSHEET-AMBIGUOUS", sprintf("%d worksheet names match %y: %y; the pattern must match "
                    "exactly one of the worksheets of the file: %y", matches.size(), pattern, matches, names),
                    {"worksheet_pattern": pattern, "matches": matches, "available": names};
            }
            return matches[0];
        }
        if (!sheet.val()) {
            return sheet;
        }
        foreach string name in (names) {
            if (name.lwr() == sheet.lwr()) {
                return name;
            }
        }
        # a 0-based position
        if (sheet =~ /^[0-9]+$/ && sheet.toInt() < names.size()) {
            return names[sheet.toInt()];
        }
        throw "EXCEL-WORKSHEET-NOT-FOUND", sprintf("worksheet %y does not exist; the worksheets of the file are: %y",
            sheet, names), {"worksheet": sheet, "available": names};
    }

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        resolveSheet();
        it = new ExcelIterator(getWorkbookFile().getPath(), sheet);
        ExcelReadDataProvider::configureIterator(it, opts);
    }

    #! Configures an iterator from processed options
    /** @param it the iterator to configure
        @param opts options already processed by processCellOptions()
    */
    static configureIterator(ExcelIterator it, *hash<auto> opts) {
        if (opts.header_cells) {
            it.setHeaderCells(opts.header_cells.start.column, opts.header_cells.start.row,
                opts.header_cells.end.column, opts.header_cells.end.row);
        } else if (opts.header_names) {
            # Default to row 1 for headers when header_names is true and no header_cells specified
            it.setHeaderCells("-", 1, "-", 1);
        } else if (opts.headers) {
            it.setHeaders(opts.headers);
        }
        if (opts.tolwr) {
            it.setHeadersToLower();
        }
        if (opts.timezone) {
            it.setZone(opts.timezone);
        }
        if (opts.data_cells) {
            it.setDataCells(opts.data_cells.start.column, opts.data_cells.start.row, opts.data_cells.end.column,
                opts.data_cells.end.row);
        }
        if (opts.ignore_empty) {
            it.setIgnoreEmpty(True);
        }
        # set last: the name is checked against the headers
        if (exists opts.formula_field) {
            it.setFormulaField(opts.formula_field);
        }
    }

    #! Returns the record type of an iterator configured from processed options
    /** @param it the configured iterator
        @param sheet the name of the worksheet
        @param opts options already processed by processCellOptions()

        @return the fields of the records: the columns, and the formula field if the \c formula_field option is set
    */
    static hash<string, AbstractDataField> getIteratorRecordType(ExcelIterator it, string sheet, *hash<auto> opts) {
        hash<string, AbstractDataField> rv = map {
            $1: new QoreDataField($1, "Excel sheet " + sheet + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, it.getHeaders();
        if (rv && exists opts.formula_field) {
            rv{opts.formula_field} = new QoreDataField(opts.formula_field, "The formulas of the cells of the row "
                "by cell reference, or NOTHING if none of them holds a formula",
                AbstractDataProviderType::get(AutoHashOrNothingType));
        }
        return rv;
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond, *hash<auto> search_options) {
        checkIterator();
        return new DefaultRecordIterator(it, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (dummy) {
            return {};
        }
        checkIterator();
        return ExcelReadDataProvider::getIteratorRecordType(it, sheet, opts);
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        if (dummy) {
            return;
        }
        return ExcelIterator::getWorksheets(getWorkbookFile().getPath());
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown
    */
    private *AbstractDataProvider getChildProviderImpl(string child_name) {
        *hash<string, bool> sheets = map {$1: True}, getChildProviderNamesImpl();
        if (sheets{child_name}) {
            return new ExcelWorksheetReadDataProvider(getWorkbookFile(), name, child_name,
                opts);
        }
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # ExcelDataProvider namespace
