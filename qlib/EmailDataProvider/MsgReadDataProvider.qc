# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore MsgReadDataProvider class definition

/*  MsgReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the EmailDataProvider namespace. All classes used in the EmailDataProvider module should be inside this namespace
public namespace EmailDataProvider {
#! Provides a data provider for reading MSG (Microsoft Outlook) email files
public class MsgReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "MsgReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "data": <DataProviderOptionInfo>{
                "display_name": "Input Data",
                "short_desc": "MSG email data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "MSG email data to process; conflicts with `stream` and `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "MSG Input Path",
                "short_desc": "The location of the MSG email file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the MSG email file; mutually exclusive with `data` and `stream`; "
                    "resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "MSG Input Stream",
                "short_desc": "An input stream for MSG data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for MSG data; mutually exclusive with `data` and `path`",
            },
        };
    }

    private {
        #! The input stream for the MSG data
        InputStream i;

        #! The iterator object
        MsgIterator it;

        #! The name of the object
        string name;

        #! Dummy iterator flag
        bool dummy;
    }

    #! Creates the MsgReadDataProvider with the input data path and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::getBinaryStreamFromLocation()
        @param opts a hash of optional options

        @throw MSG-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        checkOptions("MSG-READ-OPTION-ERROR", ConstructorOptions, opts);
    }

    #! Creates the MsgReadDataProvider with the input data stream
    /** @param stream the input stream to iterate

        @throw MSG-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        checkOptions("MSG-READ-OPTION-ERROR", ConstructorOptions, opts);
        name = sprintf("msg-stream=%s", stream.uniqueHash());
    }

    #! Creates the object from constructor options
    /**
        @throw MSG-READ-OPTION-ERROR invalid or unknown option; invalid data type for option;
        option conflict
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("MSG-READ-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("MSG-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("MSG-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("MSG-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("msg-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            i = new BinaryInputStream(copts.data);
            name = sprintf("msg-data=%s", i.uniqueHash());
        }
        if (!i) {
            # create a dummy iterator with no input
            i = new StringInputStream("");
            name = "msg";
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
        it = new MsgIterator(i);
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
        return EmailDataProvider::getEmailRecordType();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # EmailDataProvider namespace
