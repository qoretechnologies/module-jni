# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore OdsReadDataProvider class definition

/*  OdsReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Designates a cell location in a worksheet
public hashdecl OdsCellLocationInfo {
    #! The column name
    string column;
    #! The row number
    int row;
}

#! Designates a range of cells
public hashdecl OdsCellRangeInfo {
    #! The start cell
    hash<OdsCellLocationInfo> start;
    #! The end cell
    hash<OdsCellLocationInfo> end;
}

#! Provides a data provider for reading ODS files
public class OdsReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "OdsReadDataProvider",
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
                "short_desc": "ODS data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "ODS data to process; conflicts with `stream` and `path`",
            },
            "data_cells": <DataProviderOptionInfo>{
                "display_name": "Data Cells",
                "short_desc": "The range of cells for data to process",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The range of cells for data to process",
                "example_value": "b2:f10",
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
                "display_name": "ODS Input Path",
                "short_desc": "The location of the ODS data",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the ODS data; mutually exclusive with `data` and `stream`.\n\n"
                    "Resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
                "example_value": "file:///path/to/data.ods",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ODS Input Stream",
                "short_desc": "An input stream for ODS data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for ODS data; mutually exclusive with `data` and `path`",
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
                "desc": "The name of the worksheet to iterate, matched exactly, with case, or its 0-based position as a string; if missing, the first worksheet is processed; conflicts with `worksheet_pattern`.  An unknown worksheet raises `ODS-WORKSHEET-NOT-FOUND`, which lists the worksheets of the file",
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
                    "`ODS-WORKSHEET-NOT-FOUND` and more than one match raises `ODS-WORKSHEET-AMBIGUOUS`, both "
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
        #! The input stream for the ODS data; copied to a temporary file on first use, then released
        *InputStream i;

        #! The ODS data given with the \c data option; copied to a temporary file on first use, then released
        *binary data;

        #! The ODS data in a temporary file, created on first use
        /** The document is opened once for each use (record type and records, worksheet names, worksheet providers),
            and an input stream can only be read once, so it is copied to a file, in bounded memory, and opened from
            there
        */
        *OdsFile file;

        #! Serializes copying the input on first use
        Mutex data_lock();

        #! the iterator object
        OdsIterator it;

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

        #! ODS iteration options
        *hash<auto> opts;
    }

    #! Creates the OdsReadDataProvider with the input data path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        self.opts = processCellOptions(checkOptions("ODS-READ-OPTION-ERROR", ConstructorOptions, opts));
        sheet = self.opts.worksheet ?? "";
        sheet_pattern = self.opts.worksheet_pattern;
    }

    #! Creates the OdsReadDataProvider with the input data stream and optionally an option hash
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        self.opts = processCellOptions(checkOptions("ODS-READ-OPTION-ERROR", ConstructorOptions, opts));
        name = sprintf("stream=%s", stream.uniqueHash());
        sheet = self.opts.worksheet ?? "";
        sheet_pattern = self.opts.worksheet_pattern;
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = processCellOptions(checkOptions("ODS-READ-OPTION-ERROR", ConstructorOptions, options));
        sheet = copts.worksheet ?? "";
        sheet_pattern = copts.worksheet_pattern;
        if (copts.path) {
            if (copts.stream) {
                error("ODS-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("ODS-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("ODS-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("ods-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            data = binary(copts.data);
            name = sprintf("ods-data=%s", self.uniqueHash());
        }
        self.opts = copts - ("stream", "path", "worksheet", "worksheet_pattern", "data");
        if (!i && !exists data) {
            # no input: a provider without worksheets or records
            data = binary();
            name = "ods";
            dummy = True;
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Validates option combinations and parses cell range options
    /** @throw ODS-READ-OPTION-ERROR \c "headers" and \c "header_names" are both given
        @throw ODS-INVALID-LOCATION invalid location or range string
    */
    static *hash<auto> processCellOptions(*hash<auto> opts) {
        if (opts) {
            # parsed ranges replace the range strings, so the hash must accept any value type
            opts = {} + opts;
        }
        if (opts.worksheet.val() && exists opts.worksheet_pattern) {
            throw "ODS-READ-OPTION-ERROR", "cannot provide both \"worksheet\" and \"worksheet_pattern\" "
                "options; use either one or the other";
        }
        if (opts.headers && opts.header_names) {
            throw "ODS-READ-OPTION-ERROR", "cannot provide both \"headers\" and \"header_names\" options; use "
                "either one or the other";
        }
        if (opts.header_cells) {
            opts.header_cells = OdsReadDataProvider::parseRange(opts.header_cells);
        }
        if (opts.data_cells) {
            opts.data_cells = OdsReadDataProvider::parseRange(opts.data_cells);
            if (opts.header_cells) {
                if (OdsReadDataProvider::getColumnIndex(opts.header_cells.start.column)
                        > OdsReadDataProvider::getColumnIndex(opts.data_cells.start.column)) {
                    throw "ODS-INVALID-LOCATION", sprintf("start column for header cells (%y) is after the start "
                        "column for data cells (%y)", opts.header_cells.start, opts.data_cells.start);
                }
                if (opts.header_cells.start.row > opts.data_cells.start.row) {
                    throw "ODS-INVALID-LOCATION", sprintf("start row for header cells (%y) is after the start row "
                        "for data cells  (%y)", opts.header_cells.start, opts.data_cells.start);
                }
            }
        }
        return opts;
    }

    #! Parses a string that designates a range of cells (ex: \c "a1:e10")
    static hash<OdsCellRangeInfo> parseRange(string range) {
        (*string start, *string end) = (range =~ x/^((?:[a-z]+)?[0-9]+)(?::((?:[a-z]+)?[0-9]+))?$/i);
        if (!start) {
            throw "ODS-INVALID-LOCATION", sprintf("range %y does not have the format [a-z]+[0-9]+:[a-z]+[0-9]+",
                range);
        }
        hash<OdsCellRangeInfo> rv = <OdsCellRangeInfo>{
            "start": OdsReadDataProvider::parseCellLocation(start),
        };
        if (end) {
            rv.end = OdsReadDataProvider::parseCellLocation(end);
            if (OdsReadDataProvider::getColumnIndex(rv.start.column)
                    > OdsReadDataProvider::getColumnIndex(rv.end.column)) {
                throw "ODS-INVALID-LOCATION", sprintf("start column (%y) is after the end column (%y)", start, end);
            }
            if (rv.start.row > rv.end.row) {
                throw "ODS-INVALID-LOCATION", sprintf("start row (%y) is after the end row (%y)", start, end);
            }
        } else {
            rv.end = <OdsCellLocationInfo>{
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
    static hash<OdsCellLocationInfo> parseCellLocation(string location) {
        *list<*string> row_values = (location =~ x/^(?:[a-z]+)?([0-9]+)$/i);
        hash<OdsCellLocationInfo> rv = <OdsCellLocationInfo>{
            "column": (location =~ x/^([a-z]+)[0-9]+/i)[0] ?? "-",
            "row": (row_values[1] ?? row_values[0] ?? -1).toInt(),
        };
        if (rv.row < 1) {
            throw "ODS-INVALID-LOCATION", sprintf("row %y is invalid, rows numbers must be >= 1", rv.row);
        }
        return rv;
    }

    #! Returns the document file, copying the input to it on first use
    /** The input is copied once and released; every later use of the document opens the file, so the record type,
        the records, the worksheet names, and the worksheet providers can be acquired in any order

        @return the document file
    */
    private OdsFile getWorkbookFile() {
        AutoLock al(data_lock);
        if (!file) {
            file = new OdsFile(exists data ? new BinaryInputStream(data) : i);
            remove i;
            remove data;
        }
        return file;
    }

    #! Deletes the document file once the worksheet providers using it are also destroyed
    destructor() {
        if (file) {
            file.close();
        }
    }

    #! Resolves the worksheet to iterate against the worksheets of the file
    /** @throw ODS-WORKSHEET-NOT-FOUND no worksheet has the name or position, or matches the pattern
        @throw ODS-WORKSHEET-AMBIGUOUS more than one worksheet matches the pattern
    */
    private resolveSheet() {
        if (sheet_resolved) {
            return;
        }
        sheet = OdsReadDataProvider::resolveWorksheet(getChildProviderNamesImpl() ?? (), sheet, sheet_pattern);
        sheet_resolved = True;
    }

    #! Returns the name of the worksheet to read, given the worksheets of a file
    /** @param names the names of the worksheets of the file, in order
        @param sheet the worksheet option: a name, matched exactly, or a 0-based position
        as a string; an empty string means the first worksheet
        @param pattern a regular expression that the name of exactly one worksheet must match, if any

        @return the name of the worksheet to read, or an empty string for the first worksheet

        @throw ODS-WORKSHEET-NOT-FOUND no worksheet has the name or position, or matches the pattern; \c arg has
        the \c worksheet or \c worksheet_pattern and the \c available worksheet names
        @throw ODS-READ-OPTION-ERROR the pattern is not a valid regular expression
        @throw ODS-WORKSHEET-AMBIGUOUS more than one worksheet matches the pattern; \c arg has the
        \c worksheet_pattern, the \c matches, and the \c available worksheet names
    */
    static string resolveWorksheet(list<string> names, string sheet, *string pattern) {
        if (exists pattern) {
            list<string> matches;
            try {
                matches = select names, regex($1, pattern);
            } catch (hash<ExceptionInfo> ex) {
                throw "ODS-READ-OPTION-ERROR", sprintf("invalid \"worksheet_pattern\" regular expression %y: "
                    "%s", pattern, ex.desc);
            }
            if (!matches) {
                throw "ODS-WORKSHEET-NOT-FOUND", sprintf("no worksheet name matches %y; the worksheets of the file "
                    "are: %y", pattern, names), {"worksheet_pattern": pattern, "available": names};
            }
            if (matches.size() > 1) {
                throw "ODS-WORKSHEET-AMBIGUOUS", sprintf("%d worksheet names match %y: %y; the pattern must match "
                    "exactly one of the worksheets of the file: %y", matches.size(), pattern, matches, names),
                    {"worksheet_pattern": pattern, "matches": matches, "available": names};
            }
            return matches[0];
        }
        if (!sheet.val()) {
            return sheet;
        }
        foreach string name in (names) {
            if (name == sheet) {
                return name;
            }
        }
        # a 0-based position
        if (sheet =~ /^[0-9]+$/ && sheet.toInt() < names.size()) {
            return names[sheet.toInt()];
        }
        throw "ODS-WORKSHEET-NOT-FOUND", sprintf("worksheet %y does not exist; the worksheets of the file are: %y",
            sheet, names), {"worksheet": sheet, "available": names};
    }

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        resolveSheet();
        it = new OdsIterator(getWorkbookFile().getPath(), sheet);
        OdsReadDataProvider::configureIterator(it, opts);
    }

    #! Configures an iterator from processed options
    /** @param it the iterator to configure
        @param opts options already processed by processCellOptions()
    */
    static configureIterator(OdsIterator it, *hash<auto> opts) {
        if (opts.header_cells) {
            it.setHeaderCells(opts.header_cells.start.column, opts.header_cells.start.row,
                opts.header_cells.end.column, opts.header_cells.end.row);
        } else if (opts.header_names) {
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
    }

    #! Returns an iterator for zero or more records matching the search options
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        checkIterator();
        return new DefaultRecordIterator(it, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (dummy) {
            return {};
        }
        checkIterator();
        return map {
            $1: new QoreDataField($1, "ODS sheet " + sheet + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, it.getHeaders();
    }

    #! Returns a list of child data provider names, if any
    private *list<string> getChildProviderNamesImpl() {
        if (dummy) {
            return;
        }
        return OdsIterator::getWorksheets(getWorkbookFile().getPath());
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    private *AbstractDataProvider getChildProviderImpl(string child_name) {
        *hash<string, bool> sheets = map {$1: True}, getChildProviderNamesImpl();
        if (sheets{child_name}) {
            return new OdsWorksheetReadDataProvider(getWorkbookFile(), name, child_name,
                opts);
        }
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # OdsDataProvider namespace
