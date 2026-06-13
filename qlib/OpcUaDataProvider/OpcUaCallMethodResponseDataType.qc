# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaCallMethodResponseDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! OPC UA call method response data type
public class OpcUaCallMethodResponseDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "status_code": {
                "display_name": "Status Code",
                "type": IntType,
                "desc": "The OPC UA status code for the method call; `0` indicates success",
            },
            "status_text": {
                "display_name": "Status Text",
                "type": StringType,
                "desc": "A human-readable description of the status code",
            },
            "output_arguments": {
                "display_name": "Output Arguments",
                "type": SoftListOrNothingType,
                "desc": "The output arguments returned by the method call",
            },
            "input_argument_metadata": {
                "display_name": "Input Argument Metadata",
                "type": SoftListOrNothingType,
                "desc": "Resolved metadata (name, description, data type, value rank) for each declared "
                    "input argument of the method, when the method's `InputArguments` property can be "
                    "resolved",
            },
            "output_argument_metadata": {
                "display_name": "Output Argument Metadata",
                "type": SoftListOrNothingType,
                "desc": "Resolved metadata (name, description, data type, value rank) for each declared "
                    "output argument of the method, when the method's `OutputArguments` property can be "
                    "resolved",
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

#! Constant giving the OPC UA call method response data type
public const OpcUaCallMethodResponseDataType = new OpcUaCallMethodResponseDataType();
}
