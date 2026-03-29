# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore VcfReadDataProvider class definition

/*  VcfReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the VcfDataProvider namespace. All classes used in the VcfDataProvider module should be inside this namespace
public namespace VcfDataProvider {
#! Provides a data provider for reading vCard (.vcf) contact files
public class VcfReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "VcfReadDataProvider",
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
                "short_desc": "VCF data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "VCF data to process; conflicts with `stream` and `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "VCF Input Path",
                "short_desc": "The location of the VCF data",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the VCF data; mutually exclusive with `data` and `stream`; "
                    "resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "VCF Input Stream",
                "short_desc": "An input stream for VCF data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for VCF data; mutually exclusive with `data` and `path`",
            },
        };
    }

    private {
        #! The input stream for the VCF data
        InputStream i;

        #! the iterator object
        VcfIterator it;

        #! The name of the object
        string name;

        #! dummy iterator
        bool dummy;
    }

    #! Creates the VcfReadDataProvider with the input data path and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::getBinaryStreamFromLocation()
        @param opts a hash of optional options

        @throw VCF-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        checkOptions("VCF-READ-OPTION-ERROR", ConstructorOptions, opts);
    }

    #! Creates the VcfReadDataProvider with the input data stream and optionally an option hash
    /** @param stream the input stream to iterate
        @param opts a hash of optional options

        @throw VCF-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        checkOptions("VCF-READ-OPTION-ERROR", ConstructorOptions, opts);
        name = sprintf("stream=%s", stream.uniqueHash());
    }

    #! Creates the object from constructor options
    /**
        @throw VCF-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("VCF-READ-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("VCF-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("VCF-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("VCF-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("vcf-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            # Handle binary data input
            i = new BinaryInputStream(copts.data);
            name = sprintf("vcf-data=%s", i.uniqueHash());
        }
        if (!i) {
            # create a dummy iterator with no input
            i = new StringInputStream("");
            name = "vcf";
            dummy = True;
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
        it = new VcfIterator(i);
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
        return {
            "formatted_name": new QoreDataField("formatted_name", "The display name of the contact",
                AbstractDataProviderType::get(StringType)),
            "given_name": new QoreDataField("given_name", "The first/given name of the contact",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
            "family_name": new QoreDataField("family_name", "The last/family name of the contact",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
            "emails": new QoreDataField("emails", "List of email addresses with type and value",
                AbstractDataProviderType::get(AutoListOrNothingType)),
            "phones": new QoreDataField("phones", "List of phone numbers with type and value",
                AbstractDataProviderType::get(AutoListOrNothingType)),
            "addresses": new QoreDataField("addresses",
                "List of postal addresses with type and address components",
                AbstractDataProviderType::get(AutoListOrNothingType)),
            "organization": new QoreDataField("organization", "The company or organization name",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
            "title": new QoreDataField("title", "The job title",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
            "note": new QoreDataField("note", "Free-text notes about the contact",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
            "uid": new QoreDataField("uid", "The unique identifier of the contact",
                AbstractDataProviderType::get(SoftStringOrNothingType)),
        };
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # VcfDataProvider namespace
