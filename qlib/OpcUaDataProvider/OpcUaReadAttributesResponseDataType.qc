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
            "user_access_level": {
                "display_name": "User Access Level",
                "type": SoftIntOrNothingType,
                "desc": "The access level bitmask for the node taking user permissions into account",
            },
            "user_writable": {
                "display_name": "User Writable",
                "type": SoftBoolOrNothingType,
                "desc": "Whether the node value can be written by the current user",
            },
            "historizing": {
                "display_name": "Historizing",
                "type": SoftBoolOrNothingType,
                "desc": "Whether the node is currently historizing data",
            },
            "value_rank": {
                "display_name": "Value Rank",
                "type": SoftIntOrNothingType,
                "desc": "The value rank of the node: `-1` = scalar, `0` = one or more dimensions, "
                    "`1` = one dimension, `n` = n dimensions; only present for Variable nodes",
            },
            "array_dimensions": {
                "display_name": "Array Dimensions",
                "type": SoftListOrNothingType,
                "desc": "The length of each dimension for an array value, if specified by the node",
            },
            "minimum_sampling_interval": {
                "display_name": "Minimum Sampling Interval",
                "type": SoftFloatOrNothingType,
                "desc": "The minimum sampling interval (in milliseconds) supported by the node, if "
                    "specified",
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

#! Constant giving the OPC UA read attributes response data type
public const OpcUaReadAttributesResponseDataType = new OpcUaReadAttributesResponseDataType();
}
