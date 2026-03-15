# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaDataChangeEventDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! OPC UA data change event data type
public class OpcUaDataChangeEventDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "node_id": {
                "display_name": "Node ID",
                "type": StringType,
                "desc": "The OPC UA node ID string (e.g., `ns=2;s=Temperature`)",
                "example_value": "ns=2;s=Temperature",
            },
            "display_name": {
                "display_name": "Display Name",
                "type": SoftStringOrNothingType,
                "desc": "The display name of the node, if available",
                "example_value": "Temperature",
            },
            "value": {
                "display_name": "Value",
                "type": AutoType,
                "desc": "The new value of the node; the type depends on the OPC UA data type "
                    "of the node",
                "example_value": 22.5,
            },
            "status_code": {
                "display_name": "Status Code",
                "type": IntType,
                "desc": "The OPC UA status code; `0` indicates good quality",
                "example_value": 0,
            },
            "status_text": {
                "display_name": "Status Text",
                "type": StringType,
                "desc": "A human-readable description of the status code",
                "example_value": "Good",
            },
            "source_timestamp": {
                "display_name": "Source Timestamp",
                "type": SoftDateOrNothingType,
                "desc": "The timestamp from the data source when the value was generated",
            },
            "server_timestamp": {
                "display_name": "Server Timestamp",
                "type": SoftDateOrNothingType,
                "desc": "The timestamp from the OPC UA server when the value was received",
            },
            "namespace_index": {
                "display_name": "Namespace Index",
                "type": IntType,
                "desc": "The namespace index of the node",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the OPC UA data change event data type
public const OpcUaDataChangeEventDataType = new OpcUaDataChangeEventDataType();
}
