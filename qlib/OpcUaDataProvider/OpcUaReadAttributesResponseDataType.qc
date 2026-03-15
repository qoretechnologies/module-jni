# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaReadAttributesResponseDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! OPC UA node attributes response data type
public class OpcUaReadAttributesResponseDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "node_id": {
                "display_name": "Node ID",
                "type": StringType,
                "desc": "The OPC UA node ID that was read",
            },
            "display_name": {
                "display_name": "Display Name",
                "type": SoftStringOrNothingType,
                "desc": "The human-readable display name of the node",
            },
            "description": {
                "display_name": "Description",
                "type": SoftStringOrNothingType,
                "desc": "The description of the node",
            },
            "node_class": {
                "display_name": "Node Class",
                "type": SoftStringOrNothingType,
                "desc": "The node class (Variable, Object, Method, etc.)",
            },
            "data_type": {
                "display_name": "Data Type",
                "type": SoftStringOrNothingType,
                "desc": "The data type of the node value",
            },
            "value": {
                "display_name": "Value",
                "type": AutoType,
                "desc": "The current value of the node",
            },
            "access_level": {
                "display_name": "Access Level",
                "type": SoftIntOrNothingType,
                "desc": "The access level bitmask for the node",
            },
            "writable": {
                "display_name": "Writable",
                "type": SoftBoolOrNothingType,
                "desc": "Whether the node value can be written",
            },
            "historizing": {
                "display_name": "Historizing",
                "type": SoftBoolOrNothingType,
                "desc": "Whether the node is currently historizing data",
            },
            "status_code": {
                "display_name": "Status Code",
                "type": IntType,
                "desc": "The OPC UA status code; `0` indicates success",
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

#! Constant giving the OPC UA read attributes response data type
public const OpcUaReadAttributesResponseDataType = new OpcUaReadAttributesResponseDataType();
}
