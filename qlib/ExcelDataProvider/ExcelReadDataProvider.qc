# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore ExcelReadDataProvider class definition

/*  ExcelReadDataProvider.qc Copyright 2021 - 2025 Qore Technologies, s.r.o.

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
                "desc": "If `true` then the object will parse the header names from the first header row, in this "
                    "case if `header_cells` is not set explicitly, it will be assumed to be the first row",
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Output Headers",
                "short_desc": "List of header / column names for the data iterated",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of header / column names for the data iterated; if this is present, then "
                    "`header_names` must be `false`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "Excel Input Path",
                "short_desc": "The path to the Excel data",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The path to the Excel data; mutually exclusive with `data` and `stream`",
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
                "desc": "The timezone region to use when parsing dates (ex: `Europe/Prague`)",
            },
            "tolwr": <DataProviderOptionInfo>{
                "display_name": "Convert Headers to Lower Case",
                "short_desc": "Convert all header names to lower case",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `true` (default `false`), all automatically-detected header names will be converted to "
                    "lower case letters",
            },
            "worksheet": <DataProviderOptionInfo>{
                "display_name": "Worksheet Name",
                "short_desc": "The name of the worksheet to iterate",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of the worksheet to iterate; if missing, the first worksheet is processed",
            },
        };
    }

    private {
        #! The inpjut stream for the Excel data
        InputStream i;

        #! the iterator object
        ExcelIterator it;

        #! The path to the Excel file
        string path;

        #! The name of the object
        string name;

        #! the name of the worksheet
        string sheet;

        #! dummy iterator
        bool dummy;

        #! Excel iteration options
        *hash<auto> opts;
    }

    #! Creates the ExcelReadDataProvider with the input data path and optionally an option hash
    /** @param path the input file to iterate; will be subjected to environment variable substitution
        @param opts a hash of optional options

        @throw EXCEL-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_names"
        is @ref True "True" and \c "headers" is also present; unknown field type
        @throw EXCEL-INVALID-LOCATION invalid excel location or range string
    */
    constructor(string path, *hash<auto> opts) {
        name = path;
        self.path = substitute_env_vars(path);
        self.opts = processCellOptions(checkOptions("EXCEL-READ-OPTION-ERROR", ConstructorOptions, opts));
        sheet = self.opts.worksheet ?? "";
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
        if (copts.path) {
            if (copts.stream) {
                error("EXCEL-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            copts.path = substitute_env_vars(copts.path);
            name = path = copts.path;
        } else if (copts.stream) {
            i = copts.stream;
            name = sprintf("excel-stream=%s", i.uniqueHash());
        }
        self.opts = copts - ("stream", "path", "worksheet");
        if (!i && !path) {
            # create a dummy interator with no input
            i = new StringInputStream("");
            name = "excel";
            dummy = True;
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Process cell options
    static *hash<auto> processCellOptions(*hash<auto> opts) {
        if (opts.header_cells) {
            opts.header_cells = ExcelReadDataProvider::parseRange(opts.header_cells);
        }
        if (opts.data_cells) {
            opts.data_cells = ExcelReadDataProvider::parseRange(opts.data_cells);
            if (opts.header_cells) {
                if (opts.header_cells.start.column > opts.data_cells.start.column) {
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
            if (rv.start.column > rv.end.column) {
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

    #! Parses a string that designates a cell location (ex: \c "a1")
    /** @throw INVALID-LOCATION invalid excel location or range string
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

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        if (path) {
            it = new ExcelIterator(path, sheet);
        } else {
            it = new ExcelIterator(i, sheet);
        }
        if (opts.header_cells) {
            it.setHeaderCells(opts.header_cells.start.column, opts.header_cells.start.row,
                opts.header_cells.end.column, opts.header_cells.end.row);
        } else if (opts.headers) {
            it.setHeaders(opts.headers);
        }
        if (opts.timezone) {
            it.setZone(opts.timezone);
        }
        if (opts.data_cells) {
            it.setDataCells(opts.data_cells.start.column, opts.data_cells.start.row, opts.data_cells.end.column,
                opts.data_cells.end.row);
        }
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
        return map {
            $1: new QoreDataField($1, "Excel sheet " + sheet + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, it.getHeaders();
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        if (dummy) {
            return;
        }
        # if we are dealing with a FileInputStream, then we have to reset it after reading the stream
        # otherwise we have to read the entire stream into binary object and reset it with that
        binary b;
        if (!path) {
            b = i.read(999999999) ?? binary();
            i = new BinaryInputStream(b);
        }
        on_exit if (!path) {
            i = new BinaryInputStream(b);
        }
        return ExcelIterator::getWorksheets(i ?? path);
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown
    */
    private *AbstractDataProvider getChildProviderImpl(string child_name) {
        *hash<string, bool> sheets = map {$1: True}, getChildProviderNamesImpl();
        if (sheets{child_name}) {
            return new ExcelWorksheetReadDataProvider(i ?? path, name, child_name, opts);
        }
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # ExcelDataProvider namespace
