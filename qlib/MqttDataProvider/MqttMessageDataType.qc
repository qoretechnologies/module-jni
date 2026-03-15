# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** MqttMessageDataType.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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
#! MQTT message data type
public class MqttMessageDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "topic": {
                "display_name": "Message Topic",
                "type": StringType,
                "desc": "The message topic",
            },
            "qos": {
                "display_name": "Quality of Service",
                "type": IntType,
                "desc": "The quality of service value for this message",
                "default_value": 2,
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "At Most Once",
                        "short_desc": "QoS 0: fire and forget",
                        "desc": "QoS 0: message is delivered at most once with no acknowledgment",
                        "value": 0,
                    },
                    <AllowedValueInfo>{
                        "display_name": "At Least Once",
                        "short_desc": "QoS 1: acknowledged delivery",
                        "desc": "QoS 1: message is delivered at least once with acknowledgment",
                        "value": 1,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Exactly Once",
                        "short_desc": "QoS 2: assured delivery",
                        "desc": "QoS 2: message is delivered exactly once using a four-step handshake",
                        "value": 2,
                    },
                ),
            },
            "payload": {
                "display_name": "Message Body",
                "type": AutoType,
                "desc": "The payload / message body; a string or binary type is sent as-is, all others are "
                    "serialized with JSON and sent as a JSON string",
            },
            "retained": {
                "display_name": "Retained?",
                "type": BoolType,
                "desc": "If this message should be retained by the server",
                "default_value": False,
            },
            "content_type": {
                "display_name": "Content Type",
                "type": SoftStringOrNothingType,
                "desc": "The MIME content type of the message payload (e.g., `application/json`, `text/plain`)",
            },
            "response_topic": {
                "display_name": "Response Topic",
                "type": SoftStringOrNothingType,
                "desc": "The topic to which the receiver should publish a response "
                    "(MQTT 5 request/response pattern)",
            },
            "correlation_data": {
                "display_name": "Correlation Data",
                "type": SoftBinaryOrNothingType,
                "desc": "Correlation data for matching responses to requests; echoed back by the responder "
                    "(MQTT 5 request/response pattern)",
            },
            "message_expiry_interval": {
                "display_name": "Message Expiry Interval",
                "type": SoftIntOrNothingType,
                "desc": "Message lifetime in seconds; the broker will discard the message after this interval "
                    "if it has not been delivered",
            },
            "payload_format": {
                "display_name": "Payload Format Indicator",
                "type": SoftBoolOrNothingType,
                "desc": "Payload format indicator: `True` = UTF-8 encoded text, `False` or omitted = "
                    "unspecified binary data",
            },
            "user_properties": {
                "display_name": "User Properties",
                "type": AutoHashOrNothingType,
                "short_desc": "Application-level key-value string metadata to attach to the message",
                "desc": "Application-level key-value string metadata to attach to the message.\n\n"
                    "Provide a hash of string key-value pairs (e.g., `{\"source\": \"sensor-1\", "
                    "\"region\": \"us-east\"}`)",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the MQTT message data type
public const MqttMessageDataType = new MqttMessageDataType();
}
