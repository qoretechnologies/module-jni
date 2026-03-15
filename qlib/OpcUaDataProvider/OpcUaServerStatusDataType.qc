# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaServerStatusDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! OPC UA server status response data type
public class OpcUaServerStatusDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "state": {
                "display_name": "Server State",
                "type": StringType,
                "desc": "The current server state",
            },
            "start_time": {
                "display_name": "Start Time",
                "type": SoftDateOrNothingType,
                "desc": "The time when the server was started",
            },
            "current_time": {
                "display_name": "Current Time",
                "type": SoftDateOrNothingType,
                "desc": "The current time on the server",
            },
            "build_info_product_name": {
                "display_name": "Product Name",
                "type": SoftStringOrNothingType,
                "desc": "The name of the server product",
            },
            "build_info_product_uri": {
                "display_name": "Product URI",
                "type": SoftStringOrNothingType,
                "desc": "The URI of the server product",
            },
            "build_info_manufacturer": {
                "display_name": "Manufacturer",
                "type": SoftStringOrNothingType,
                "desc": "The manufacturer of the server",
            },
            "build_info_software_version": {
                "display_name": "Software Version",
                "type": SoftStringOrNothingType,
                "desc": "The software version of the server",
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

#! Constant giving the OPC UA server status data type
public const OpcUaServerStatusDataType = new OpcUaServerStatusDataType();
}
