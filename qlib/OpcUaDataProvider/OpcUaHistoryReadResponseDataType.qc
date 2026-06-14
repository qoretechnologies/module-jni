# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaHistoryReadResponseDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! OPC UA history read response data type
public class OpcUaHistoryReadResponseDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "node_id": {
                "display_name": "Node ID",
                "type": StringType,
                "desc": "The OPC UA node ID that was read",
            },
            "data_values": {
                "display_name": "Data Values",
                "type": SoftAutoListType,
                "desc": "List of historical data values, each containing `value`, `status_code`, "
                    "`source_timestamp`, and `server_timestamp`",
            },
            "status_code": {
                "display_name": "Status Code",
                "type": IntType,
                "desc": "The OPC UA status code; `0` indicates success",
            },
            "status_text": {
                "display_name": "Status Text",
                "type": StringType,
                "desc": "The OPC UA status severity (`Good`, `Uncertain`, or `Bad`); retained under "
                    "this name for backwards compatibility (identical to `severity`)",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "Good", "display_name": "Good"},
                    <AllowedValueInfo>{"value": "Uncertain", "display_name": "Uncertain"},
                    <AllowedValueInfo>{"value": "Bad", "display_name": "Bad"},
                ),
            },
            "symbolic_name": {
                "display_name": "Status Symbolic Name",
                "type": StringType,
                "desc": "The OPC UA symbolic status name (e.g., `Good`, `BadNodeIdUnknown`), or the "
                    "hexadecimal status code if the symbolic name is not known",
                "example_value": "Good",
            },
            "severity": {
                "display_name": "Status Severity",
                "type": StringType,
                "desc": "The OPC UA status severity: `Good`, `Uncertain`, or `Bad`",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "Good", "display_name": "Good"},
                    <AllowedValueInfo>{"value": "Uncertain", "display_name": "Uncertain"},
                    <AllowedValueInfo>{"value": "Bad", "display_name": "Bad"},
                ),
                "example_value": "Good",
            },
            "diagnostics": {
                "display_name": "Status Diagnostics",
                "type": SoftStringOrNothingType,
                "desc": "A human-readable description of the status code, if known",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the OPC UA history read response data type
public const OpcUaHistoryReadResponseDataType = new OpcUaHistoryReadResponseDataType();
}
