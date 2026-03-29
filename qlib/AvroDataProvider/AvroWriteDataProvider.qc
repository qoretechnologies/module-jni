# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore AvroWriteDataProvider class definition

/*  AvroWriteDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the AvroDataProvider namespace. All classes used in the AvroDataProvider module should be inside this namespace
public namespace AvroDataProvider {
#! Provides a data provider for writing Apache Avro data files
public class AvroWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "AvroWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "Avro Output Path",
                "short_desc": "The location to write the Avro file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location for the output Avro data file; mutually exclusive with `stream`; "
                    "the output location is handled by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "Avro Output Stream",
                "short_desc": "An output stream for Avro data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for Avro data; mutually exclusive with `path`",
            },
            "schema": <DataProviderOptionInfo>{
                "display_name": "Avro Schema",
                "short_desc": "The Avro JSON schema for the records",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The Avro JSON schema string that defines the record structure; this option "
                    "is required for writing Avro data",
                "required": True,
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

        #! The Avro writer object
        AvroWriter writer;

        #! Records created count
        int created = 0;

        #! The Avro JSON schema
        string schemaJson;
    }

    #! Creates the AvroWriteDataProvider with the output path and optionally an option hash
    /** @param path the output file path
        @param schema the Avro JSON schema string
        @param opts a hash of optional options

        @throw AVRO-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(string path, string schema, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("AVRO-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("AVRO-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("AVRO-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = path;
        name = path;
        schemaJson = schema;
        setupWriter();
    }

    #! Creates the AvroWriteDataProvider with an output stream and optionally an option hash
    /** @param stream the output stream to write to
        @param schema the Avro JSON schema string
        @param opts a hash of optional options

        @throw AVRO-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(OutputStream stream, string schema, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("AVRO-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("AVRO-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("AVRO-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("avro-stream=%s", stream.uniqueHash());
        schemaJson = schema;
        setupWriter();
    }

    #! Creates the object from constructor options
    /**
        @throw AVRO-WRITE-OPTION-ERROR invalid or unknown option; missing required schema option

        @note If neither \c path nor \c stream is provided, use getData() to retrieve the Avro data as binary
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("AVRO-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (!copts.schema) {
            error("AVRO-WRITE-OPTION-ERROR", "the \"schema\" option is required for writing Avro data");
        }
        schemaJson = copts.schema;
        if (copts.path) {
            if (copts.stream) {
                error("AVRO-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            path = copts.path;
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("avro-stream=%s", stream.uniqueHash());
        } else {
            # No path or stream - user will use getData() to retrieve binary data
            name = "avro-data";
        }
        setupWriter();
    }

    #! Sets up the writer
    private setupWriter() {
        writer = new AvroWriter(schemaJson);
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
    /** @param rec the record to create; must conform to the Avro schema provided in the constructor
        @param create_options no options are currently supported

        @return the record created (echoes back the input)
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        writer.writeRecord(rec);
        ++created;
        return rec;
    }

    #! Commits (writes) the Avro file
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
    /** @return the Avro file contents as binary data

        @note This is an alternative to using path or stream - get the data directly
    */
    binary getData() {
        if (!writer) {
            throw "AVRO-WRITE-ERROR", "writer has already been committed";
        }
        return writer.getBytes();
    }

    #! Returns the number of records created
    int getCreatedCount() {
        return created;
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return {};
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # AvroDataProvider namespace
