# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaBrowseResponseDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! OPC UA browse response data type
public class OpcUaBrowseResponseDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "references": {
                "display_name": "References",
                "type": SoftListOrNothingType,
                "short_desc": "List of browsed node references",
                "desc": "List of reference descriptions found by browsing; each entry is a hash with "
                    "`node_id`, `display_name`, `node_class`, `browse_name`, `reference_type`, "
                    "and `is_forward` fields",
            },
            "status_code": {
                "display_name": "Status Code",
                "type": IntType,
                "desc": "The OPC UA status code for the browse operation; `0` indicates success",
            },
            "status_text": {
                "display_name": "Status Text",
                "type": StringType,
                "desc": "A human-readable description of the status code",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the OPC UA browse response data type
public const OpcUaBrowseResponseDataType = new OpcUaBrowseResponseDataType();
}
