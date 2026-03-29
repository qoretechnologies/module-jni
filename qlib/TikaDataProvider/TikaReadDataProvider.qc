# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore TikaReadDataProvider class definition

/*  TikaReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the TikaDataProvider namespace. All classes used in the TikaDataProvider module should be inside this namespace
public namespace TikaDataProvider {
#! Provides a data provider for extracting content from files using Apache Tika
public class TikaReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "TikaReadDataProvider",
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
                "short_desc": "Binary data to process with Tika",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "Binary data to process with Tika; mutually exclusive with `stream` and `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "Input Path",
                "short_desc": "The location of the file to process",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the file to process with Tika; mutually exclusive with `data` "
                    "and `stream`.\n\n"
                    "Resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
                "example_value": "document.html",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "Input Stream",
                "short_desc": "An input stream for data to process",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for data to process with Tika; mutually exclusive with `data` "
                    "and `path`",
            },
            "metadata_only": <DataProviderOptionInfo>{
                "display_name": "Metadata Only",
                "short_desc": "Detect MIME type and metadata without extracting content",
                "type": AbstractDataProviderType::get(SoftBoolType),
                "desc": "If `True`, only detect the MIME type and extract metadata without performing "
                    "full content extraction. The `content` field will be empty and `content_length` "
                    "will be `0`",
                "default_value": False,
            },
        };
    }

    private {
        #! The input stream for the data
        InputStream i;

        #! The extractor object
        TikaExtractor extractor;

        #! The name of the object
        string name;

        #! dummy iterator
        bool dummy;

        #! metadata only mode
        bool metadataOnly;
    }

    #! Creates the TikaReadDataProvider with the input data path and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::getBinaryStreamFromLocation()
        @param opts a hash of optional options

        @throw TIKA-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        *hash<auto> copts = checkOptions("TIKA-READ-OPTION-ERROR", ConstructorOptions, opts);
        metadataOnly = copts.metadata_only ?? False;
    }

    #! Creates the TikaReadDataProvider with the input data stream and optionally an option hash
    /** @param stream the input stream to process
        @param opts a hash of optional options

        @throw TIKA-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        *hash<auto> copts = checkOptions("TIKA-READ-OPTION-ERROR", ConstructorOptions, opts);
        name = sprintf("stream=%s", stream.uniqueHash());
        metadataOnly = copts.metadata_only ?? False;
    }

    #! Creates the object from constructor options
    /**
        @throw TIKA-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("TIKA-READ-OPTION-ERROR", ConstructorOptions, options);
        metadataOnly = copts.metadata_only ?? False;
        if (copts.path) {
            if (copts.stream) {
                error("TIKA-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; "
                    "use either one or the other");
            }
            if (copts.data) {
                error("TIKA-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; "
                    "use either one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("TIKA-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; "
                    "use either one or the other");
            }
            i = copts.stream;
            name = sprintf("tika-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            # Handle binary data input
            i = new BinaryInputStream(copts.data);
            name = sprintf("tika-data=%s", i.uniqueHash());
        }
        if (!i) {
            # create a dummy provider with no input
            i = new StringInputStream("");
            name = "tika";
            dummy = True;
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Ensures the extractor is created
    private checkExtractor() {
        if (extractor) {
            return;
        }
        extractor = new TikaExtractor(i);
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        checkExtractor();
        hash<auto> result = metadataOnly ? extractor.extractMetadata() : extractor.extractContent();
        return new DefaultRecordIterator(new ListIterator((result,)), where_cond, search_options,
            getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (dummy) {
            return {};
        }
        return {
            "content": new QoreDataField("content", "The extracted text content",
                AbstractDataProviderType::get(StringType)),
            "content_type": new QoreDataField("content_type", "The detected MIME type",
                AbstractDataProviderType::get(StringType)),
            "metadata": new QoreDataField("metadata", "A hash of extracted metadata",
                AbstractDataProviderType::get(AutoHashType)),
            "content_length": new QoreDataField("content_length",
                "The length of the extracted text content",
                AbstractDataProviderType::get(IntType)),
        };
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # TikaDataProvider namespace
