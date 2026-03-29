# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore AvroDataProvider module definition

/** AvroRecordDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the AvroDataProvider module
public namespace AvroDataProvider {
#! Avro record data type
public class AvroRecordDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "record": {
                "display_name": "Record Data",
                "type": AutoHashType,
                "desc": "The Avro record data as a hash.\n\nField names and types are determined by "
                    "the Avro schema embedded in the data file. Common Avro type mappings:\n"
                    "- `string` → string\n"
                    "- `int`/`long` → integer\n"
                    "- `float`/`double` → float\n"
                    "- `boolean` → bool\n"
                    "- `bytes` → binary\n"
                    "- `record` → nested hash\n"
                    "- `array` → list\n"
                    "- `map` → hash",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the Avro record data type
public const AvroRecordDataType = new AvroRecordDataType();
}
