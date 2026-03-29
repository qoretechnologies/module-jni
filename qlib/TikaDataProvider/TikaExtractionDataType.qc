# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore TikaDataProvider module definition

/** TikaExtractionDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the TikaDataProvider module
public namespace TikaDataProvider {
#! Tika extraction data type
public class TikaExtractionDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "content": {
                "display_name": "Extracted Text",
                "type": StringType,
                "desc": "The extracted text content from the document.\n\n**Note**: the quality and "
                    "completeness of extraction depends on the document format and the available "
                    "Tika parsers on the classpath",
            },
            "content_type": {
                "display_name": "Content Type",
                "type": StringType,
                "desc": "The detected MIME content type of the input document (e.g., `text/html`, "
                    "`application/xml`, `text/plain`)",
                "example_value": "text/html",
            },
            "metadata": {
                "display_name": "Document Metadata",
                "type": AutoHashType,
                "desc": "A hash of all extracted metadata key-value pairs from the document.\n\n"
                    "Common metadata keys include:\n"
                    "- `Content-Type` — the MIME type\n"
                    "- `Content-Length` — the document size\n"
                    "- `title` — the document title (if available)\n"
                    "- `dc:creator` — the document author",
            },
            "content_length": {
                "display_name": "Content Length",
                "type": IntType,
                "desc": "The size of the original input data in bytes",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the Tika extraction data type
public const TikaExtractionDataType = new TikaExtractionDataType();
}
