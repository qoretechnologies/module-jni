# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** MqttEventDataType.qc Copyright 2022 - 2025 Qore Technologies, s.r.o.

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
#! MQTT Event data type
public class MqttEventDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "id": {
                "display_name": "Message ID",
                "type": IntType,
                "desc": "The MQTT id of the message",
            },
            "qos": {
                "display_name": "Quality of Service",
                "type": IntType,
                "desc": "The quality of service value for this message",
                "allowed_values": (0, 1, 2),
            },
            "payload": {
                "display_name": "Message Body",
                "type": AutoType,
                "short_desc": "The payload / message body",
                "desc": "The payload / message body, deserialized from JSON or YAML according to the "
                    "`message_format` option",
            },
            "duplicate": {
                "display_name": "Duplicate?",
                "type": BoolType,
                "desc": "If this message might be a duplicate of one which has already been received",
            },
            "retained": {
                "display_name": "Retained?",
                "type": BoolType,
                "desc": "If this message was retained by the server",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the MQTT event data type
public const MqttEventDataType = new MqttEventDataType();
}
