# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** MqttEventDataType.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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
    public {
        #! Field descriptions
        const Fields = {
            "topic": {
                "display_name": "Topic",
                "type": StringType,
                "desc": "The topic the message was received on",
            },
            "id": {
                "display_name": "Message ID",
                "type": IntType,
                "desc": "The MQTT id of the message",
            },
            "qos": {
                "display_name": "Quality of Service",
                "type": IntType,
                "desc": "The quality of service value for this message",
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
            "content_type": {
                "display_name": "Content Type",
                "type": SoftStringOrNothingType,
                "desc": "The MIME content type of the message payload (e.g., `application/json`)",
            },
            "response_topic": {
                "display_name": "Response Topic",
                "type": SoftStringOrNothingType,
                "desc": "The topic to which responses should be published (MQTT 5 request/response pattern)",
            },
            "correlation_data": {
                "display_name": "Correlation Data",
                "type": SoftBinaryOrNothingType,
                "desc": "Correlation data for matching responses to requests (MQTT 5 request/response pattern)",
            },
            "message_expiry_interval": {
                "display_name": "Message Expiry Interval",
                "type": SoftIntOrNothingType,
                "desc": "Remaining lifetime of the message in seconds; set by the publisher, decremented by "
                    "the broker before delivery",
            },
            "payload_format": {
                "display_name": "Payload Format Indicator",
                "type": SoftBoolOrNothingType,
                "desc": "Payload format: `True` = UTF-8 encoded text, `False` = unspecified binary data",
            },
            "user_properties": {
                "display_name": "User Properties",
                "type": AutoHashOrNothingType,
                "short_desc": "Application-level key-value string metadata from the publisher",
                "desc": "Application-level key-value string metadata attached to the message by the "
                    "publisher.\n\n"
                    "**Note**: MQTT 5.0 allows duplicate keys in user properties. If duplicate keys exist, "
                    "the last value for each key is used in this hash representation",
            },
            "subscription_identifiers": {
                "display_name": "Subscription Identifiers",
                "type": SoftListOrNothingType,
                "desc": "List of subscription identifiers indicating which subscriptions matched this message",
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
