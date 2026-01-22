# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore WordWriteDataProvider class definition

/*  WordWriteDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the WordDataProvider namespace. All classes used in the WordDataProvider module should be inside this namespace
public namespace WordDataProvider {
#! Provides a data provider for writing Word documents (.docx format)
public class WordWriteDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "WordWriteDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "path": <DataProviderOptionInfo>{
                "display_name": "Word Output Path",
                "short_desc": "The path to write the Word document",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The path to write the Word document; mutually exclusive with `stream`",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "Word Output Stream",
                "short_desc": "An output stream for Word data",
                "type": AbstractDataProviderType::get(new Type("OutputStream")),
                "desc": "An output stream for Word data; mutually exclusive with `path`",
            },
            "content_type": <DataProviderOptionInfo>{
                "display_name": "Content Type",
                "short_desc": "Type of content to write",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Type of content to write: 'paragraphs' (default) or 'table'",
                "default_value": "paragraphs",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "paragraphs", "desc": "Write paragraphs"},
                    <AllowedValueInfo>{"value": "table", "desc": "Write a table"},
                ),
            },
            "headers": <DataProviderOptionInfo>{
                "display_name": "Column Headers",
                "short_desc": "List of column headers for table mode",
                "type": AbstractDataProviderType::get(new Type("softlist<string>")),
                "desc": "List of column headers for table mode; if not provided, headers will be "
                    "auto-detected from the first record",
            },
            "title": <DataProviderOptionInfo>{
                "display_name": "Document Title",
                "short_desc": "Title to add at the beginning",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Optional title to add as a heading at the beginning of the document",
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

        #! The Word writer object
        WordWriter writer;

        #! The options
        *hash<auto> opts;

        #! Records created count
        int created = 0;
    }

    #! Creates the WordWriteDataProvider with the output path and optionally an option hash
    /** @param path the output file path
        @param opts a hash of optional options

        @throw WORD-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("WORD-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.stream) {
            error("WORD-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when path is given as first "
                "argument; use either one or the other");
        }
        if (copts.path) {
            error("WORD-WRITE-OPTION-ERROR", "cannot provide \"path\" option when path is given as first "
                "argument");
        }
        self.path = substitute_env_vars(path);
        name = self.path;
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the WordWriteDataProvider with an output stream and optionally an option hash
    /** @param stream the output stream to write to
        @param opts a hash of optional options

        @throw WORD-WRITE-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(OutputStream stream, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("WORD-WRITE-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.path) {
            error("WORD-WRITE-OPTION-ERROR", "cannot provide \"path\" option when stream is given as first "
                "argument; use either one or the other");
        }
        if (copts.stream) {
            error("WORD-WRITE-OPTION-ERROR", "cannot provide \"stream\" option when stream is given as first "
                "argument");
        }
        self.stream = stream;
        name = sprintf("word-stream=%s", stream.uniqueHash());
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Creates the object from constructor options
    /**
        @throw WORD-WRITE-OPTION-ERROR invalid or unknown option

        @note If neither \c path nor \c stream is provided, use getData() to retrieve the Word data as binary
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("WORD-WRITE-OPTION-ERROR", ConstructorOptions, options);
        if (copts.path) {
            if (copts.stream) {
                error("WORD-WRITE-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            path = substitute_env_vars(copts.path);
            name = path;
        } else if (copts.stream) {
            stream = copts.stream;
            name = sprintf("word-stream=%s", stream.uniqueHash());
        } else {
            # No path or stream - user will use getData() to retrieve binary data
            name = "word-data";
        }
        self.opts = copts - ("path", "stream");
        setupWriter();
    }

    #! Sets up the writer
    private setupWriter() {
        writer = new WordWriter(opts.content_type ?? "paragraphs");
        if (opts.headers) {
            writer.setHeaders(opts.headers);
        }
        if (opts.title) {
            writer.writeTitle(opts.title);
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

        @note For paragraphs mode, the record should have "text" and optionally "style" keys.
        For table mode, the record should have key-value pairs matching the table columns.
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        string content_type = opts.content_type ?? "paragraphs";
        if (content_type == "paragraphs") {
            writer.writeParagraph(rec.text ?? "", rec.style ?? "Normal");
        } else {
            writer.writeRow(rec);
        }
        ++created;
        return rec;
    }

    #! Commits (writes) the Word file
    /** Call this method when you are done writing records to ensure the file is written.
        This is also called automatically in the destructor.
    */
    commit() {
        if (!writer) {
            return;
        }
        if (path) {
            writer.writeToFile(path);
        } else if (stream) {
            writer.writeToStream(stream);
        }
        writer.close();
        delete writer;
    }

    #! Returns the data as binary
    /** @return the Word document contents as binary data

        @note This is an alternative to using path or stream - get the data directly
    */
    binary getData() {
        if (!writer) {
            throw "WORD-WRITE-ERROR", "writer has already been committed";
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
} # WordDataProvider namespace
