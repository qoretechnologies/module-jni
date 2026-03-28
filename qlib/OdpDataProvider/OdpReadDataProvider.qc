# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore OdpReadDataProvider class definition

/*  OdpReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the OdpDataProvider namespace. All classes used in the OdpDataProvider module should be inside this namespace
public namespace OdpDataProvider {
#! Provides a data provider for reading ODP presentations (.odp format)
public class OdpReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "OdpReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
            "search_options": SearchOptions,
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
                "short_desc": "ODP presentation data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "ODP presentation binary data to process; mutually exclusive with `stream` "
                    "and `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "ODP Input Path",
                "short_desc": "The location of the ODP file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the ODP data; mutually exclusive with `data` and `stream`; "
                    "resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
                "example_value": "presentation.odp",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ODP Input Stream",
                "short_desc": "An input stream for ODP data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for reading ODP data; mutually exclusive with `data` and "
                    "`path`",
            },
            "content_type": <DataProviderOptionInfo>{
                "display_name": "Content Type",
                "short_desc": "Type of content to read",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Type of content to read: `slides` (default) to iterate over presentation "
                    "slides, or `table` to read tabular data from a specific slide",
                "default_value": "slides",
                "example_value": "slides",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "slides", "display_name": "Slides",
                        "desc": "Read presentation slides"},
                    <AllowedValueInfo>{"value": "table", "display_name": "Table",
                        "desc": "Read a table as row data"},
                ),
            },
            "slide_index": <DataProviderOptionInfo>{
                "display_name": "Slide Index",
                "short_desc": "Index of slide for table mode (0-based)",
                "type": AbstractDataProviderType::get(SoftIntType),
                "desc": "Index of the slide containing the table when `content_type` is `table`; "
                    "0-based, default is `0`",
                "default_value": 0,
                "example_value": 0,
            },
            "table_index": <DataProviderOptionInfo>{
                "display_name": "Table Index",
                "short_desc": "Index of table to read (0-based)",
                "type": AbstractDataProviderType::get(SoftIntType),
                "desc": "Index of the table to read on the specified slide when `content_type` is "
                    "`table`; 0-based, default is `0`",
                "default_value": 0,
                "example_value": 0,
            },
            "header_row": <DataProviderOptionInfo>{
                "display_name": "Header Row",
                "short_desc": "First row contains headers",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `true`, the first row of the table contains column headers; only applies "
                    "when `content_type` is `table`; mutually exclusive with `headers`",
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Column Headers",
                "short_desc": "Explicit list of column headers",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "Explicit list of column headers for table mode; only applies when "
                    "`content_type` is `table`; mutually exclusive with `header_row`",
            },
        };
    }

    private {
        #! The input stream for the ODP data
        InputStream i;

        #! the iterator object
        OdpIterator it;

        #! The name of the object
        string name;

        #! Content type (slides or table)
        string content_type;

        #! Slide index for table mode
        int slide_index;

        #! Table index for table mode
        int table_index;

        #! dummy iterator
        bool dummy;

        #! ODP iteration options
        *hash<auto> opts;
    }

    #! Creates the OdpReadDataProvider with the input data path and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::getBinaryStreamFromLocation()
        @param opts a hash of optional options

        @throw ODP-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_row"
        is @ref True "True" and \c "headers" is also present
    */
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        self.opts = checkOptions("ODP-READ-OPTION-ERROR", ConstructorOptions, opts);
        content_type = self.opts.content_type ?? "slides";
        slide_index = self.opts.slide_index ?? 0;
        table_index = self.opts.table_index ?? 0;
        validateOptions();
    }

    #! Creates the OdpReadDataProvider with the input data stream and optionally an option hash
    /** @param stream the input stream to iterate
        @param opts a hash of optional options

        @throw ODP-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_row"
        is @ref True "True" and \c "headers" is also present
    */
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        self.opts = checkOptions("ODP-READ-OPTION-ERROR", ConstructorOptions, opts);
        name = sprintf("stream=%s", stream.uniqueHash());
        content_type = self.opts.content_type ?? "slides";
        slide_index = self.opts.slide_index ?? 0;
        table_index = self.opts.table_index ?? 0;
        validateOptions();
    }

    #! Creates the object from constructor options
    /**
        @throw ODP-READ-OPTION-ERROR invalid or unknown option; invalid data type for option; \c "header_row"
        is @ref True "True" and \c "headers" is also present
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ODP-READ-OPTION-ERROR", ConstructorOptions, options);
        # Validate mutual exclusion of headers and header_row
        if (copts.headers && copts.header_row) {
            error("ODP-READ-OPTION-ERROR", "cannot provide both \"headers\" and \"header_row\" options; use "
                "either one or the other");
        }
        content_type = copts.content_type ?? "slides";
        slide_index = copts.slide_index ?? 0;
        table_index = copts.table_index ?? 0;
        if (copts.path) {
            if (copts.stream) {
                error("ODP-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("ODP-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("ODP-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("odp-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            # Handle binary data input
            i = new BinaryInputStream(copts.data);
            name = sprintf("odp-data=%s", i.uniqueHash());
        }
        self.opts = copts - ("stream", "path", "data", "content_type", "slide_index", "table_index");
        if (!i) {
            # create a dummy iterator with no input
            i = new StringInputStream("");
            name = "odp";
            dummy = True;
        }
    }

    #! Validates options for mutual exclusivity
    private validateOptions() {
        if (opts.headers && opts.header_row) {
            error("ODP-READ-OPTION-ERROR", "cannot provide both \"headers\" and \"header_row\" options; use "
                "either one or the other");
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        it = new OdpIterator(i, content_type, slide_index, table_index);
        if (opts.header_row) {
            it.setHeaderRow(True);
        }
        if (opts.headers) {
            it.setHeaders(opts.headers);
        }
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
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
        if (content_type == "slides") {
            return {
                "slide_number": new QoreDataField("slide_number", "Slide number (1-based)",
                    AbstractDataProviderType::get(IntType)),
                "title": new QoreDataField("title", "Slide title text",
                    AbstractDataProviderType::get(StringType)),
                "body": new QoreDataField("body", "Slide body text",
                    AbstractDataProviderType::get(StringType)),
                "notes": new QoreDataField("notes", "Speaker notes",
                    AbstractDataProviderType::get(StringType)),
                "layout": new QoreDataField("layout", "Slide layout name",
                    AbstractDataProviderType::get(StringType)),
            };
        } else {
            # Table mode - return headers as fields
            return map {
                $1: new QoreDataField($1, "ODP table column " + $1,
                    AbstractDataProviderType::anyDataType)
            }, it.getHeaders();
        }
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # OdpDataProvider namespace
