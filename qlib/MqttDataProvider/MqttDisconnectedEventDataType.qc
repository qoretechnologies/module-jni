# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** MqttDisconnectedEventDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the MqttDataProvider module
public namespace MqttDataProvider {
#! MQTT Disconnected event data type
public class MqttDisconnectedEventDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "reason_code": {
                "display_name": "Reason Code",
                "type": IntType,
                "desc": "The MQTT 5 disconnect reason code indicating why the connection was lost",
            },
            "reason": {
                "display_name": "Reason",
                "type": SoftStringOrNothingType,
                "desc": "A human-readable description of the disconnect reason",
            },
            "server_reference": {
                "display_name": "Server Reference",
                "type": SoftStringOrNothingType,
                "desc": "A server reference string indicating a server the client should redirect to, "
                    "if provided by the broker",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the MQTT disconnected event data type
public const MqttDisconnectedEventDataType = new MqttDisconnectedEventDataType();
}
