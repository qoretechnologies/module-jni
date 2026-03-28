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
    }

    #! Creates the OdsWriteDataProvider with the output path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
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

    #! Sets up the writer
    private setupWriter() {
        writer = new OdsWriter(opts.worksheet ?? "Sheet1");
        if (opts.headers) {
            writer.setHeaders(opts.headers);
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
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        writer.writeRow(rec);
        ++created;
        return rec;
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

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # OdsDataProvider namespace
