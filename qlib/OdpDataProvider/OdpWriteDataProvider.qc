# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore OdpWriteDataProvider class definition

/*  OdpWriteDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Provides a data provider for writing ODP presentations (.odp format)
public class OdpWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "OdpWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "ODP Output Path",
                "short_desc": "The location to write the ODP file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location for the output ODP data file; mutually exclusive with `stream`; "
                    "the output location is handled by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
                "example_value": "presentation.odp",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ODP Output Stream",
                "short_desc": "An output stream for ODP data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for writing ODP data; mutually exclusive with `path`",
            },
            "content_type": <DataProviderOptionInfo>{
                "display_name": "Content Type",
                "short_desc": "Type of content to write",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Type of content to write: `slides` (default) to write slide records with "
                    "`title`, `body`, and `notes` fields, or `table` to write tabular data rows",
                "default_value": "slides",
                "example_value": "slides",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "slides", "display_name": "Slides",
                        "short_desc": "Write slides",
                        "desc": "Write slides"},
                    <AllowedValueInfo>{"value": "table", "display_name": "Table",
                        "short_desc": "Write a table",
                        "desc": "Write a table"},
                ),
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Column Headers",
                "short_desc": "List of column headers for table mode",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of column headers when `content_type` is `table`; if not provided, "
                    "headers will be auto-detected from the keys of the first record",
            },
            "title": <DataProviderOptionInfo>{
                "display_name": "Presentation Title",
                "short_desc": "Title for the first slide or table slide",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Optional title for the presentation; when `content_type` is `slides`, adds "
                    "a title-only slide at the beginning; when `content_type` is `table`, sets the "
                    "title on the table slide",
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

        #! The ODP writer object
        OdpWriter writer;

        #! The options
        *hash<auto> opts;

        #! Records created count
        int created = 0;
    }

    #! Creates the OdpWriteDataProvider with the output path and optionally an option hash
    /** @param path the output file path
        @param opts a hash of optional options

        @throw ODP-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ODP-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("ODP-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("ODP-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = path;
        name = path;
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the OdpWriteDataProvider with an output stream and optionally an option hash
    /** @param stream the output stream to write to
        @param opts a hash of optional options

        @throw ODP-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(OutputStream stream, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ODP-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("ODP-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("ODP-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("odp-stream=%s", stream.uniqueHash());
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the object from constructor options
    /**
        @throw ODP-WRITE-OPTION-ERROR invalid or unknown option

        @note If neither \c path nor \c stream is provided, use getData() to retrieve the ODP data
        as binary
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ODP-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("ODP-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use "
                    "either one or the other");
            }
            path = copts.path;
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("odp-stream=%s", stream.uniqueHash());
        } else {
            # No path or stream - user will use getData() to retrieve binary data
            name = "odp-data";
        }
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Sets up the writer
    private setupWriter() {
        writer = new OdpWriter(opts.content_type ?? "slides");
        if (opts.headers) {
            writer.setHeaders(opts.headers);
        }
        if (opts.title) {
            string content_type = opts.content_type ?? "slides";
            if (content_type == "table") {
                writer.writeTableTitle(opts.title);
            } else {
                writer.writeTitle(opts.title);
            }
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

        @note For slides mode, the record should have "title", "body", optionally "notes" and "layout" keys.
        For table mode, the record should have key-value pairs matching the table columns.
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        string content_type = opts.content_type ?? "slides";
        if (content_type == "slides") {
            writer.writeSlide(rec.title ?? "", rec.body ?? "", rec.notes ?? "", rec.layout ?? "TITLE_AND_CONTENT");
        } else {
            writer.writeRow(rec);
        }
        ++created;
        return rec;
    }

    #! Commits (writes) the ODP file
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

    #! Returns the data as binary
    /** @return the ODP presentation contents as binary data

        @note This is an alternative to using path or stream - get the data directly
    */
    binary getData() {
        if (!writer) {
            throw "ODP-WRITE-ERROR", "writer has already been committed";
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
} # OdpDataProvider namespace
