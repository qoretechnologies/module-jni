# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore IcsWriteDataProvider class definition

/*  IcsWriteDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the IcsDataProvider namespace
public namespace IcsDataProvider {
#! Provides a data provider for writing ICS (iCalendar) files
public class IcsWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "IcsWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "ICS Output Path",
                "short_desc": "The location to write the ICS file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location for the output ICS data file; mutually exclusive with `stream`.\n\n"
                    "The output location is handled by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
                "example_value": "file:///path/to/output.ics",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ICS Output Stream",
                "short_desc": "An output stream for ICS data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for ICS data; mutually exclusive with `path`",
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

        #! The ICS writer object
        IcsWriter writer;

        #! Records created count
        int created = 0;
    }

    #! Creates the IcsWriteDataProvider with the output path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ICS-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("ICS-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("ICS-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = path;
        name = path;
        writer = new IcsWriter();
    }

    #! Creates the IcsWriteDataProvider with an output stream and optionally an option hash
    constructor(OutputStream stream, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ICS-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("ICS-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("ICS-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("ics-stream=%s", stream.uniqueHash());
        writer = new IcsWriter();
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ICS-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("ICS-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            path = copts.path;
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("ics-stream=%s", stream.uniqueHash());
        } else {
            name = "ics-data";
        }
        writer = new IcsWriter();
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Destructor - ensures data is written
    destructor() {
        commit();
    }

    #! Creates a single record (event) in the data provider
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        writer.writeEvent(rec);
        ++created;
        return rec;
    }

    #! Commits (writes) the ICS file
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

    #! Returns the data as binary
    binary getData() {
        if (!writer) {
            throw "ICS-WRITE-ERROR", "writer has already been committed";
        }
        return writer.getBytes();
    }

    #! Returns the number of records created
    int getCreatedCount() {
        return created;
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return IcsDataProvider::getIcsRecordType();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # IcsDataProvider namespace
