# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsEventDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the JakartaJmsDataProvider module
public namespace JakartaJmsDataProvider {
#! JMS event data type for received messages
public class JmsEventDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "message_id": {
                "display_name": "Message ID",
                "type": SoftStringOrNothingType,
                "desc": "The unique identifier for the message assigned by the JMS provider",
            },
            "correlation_id": {
                "display_name": "Correlation ID",
                "type": SoftStringOrNothingType,
                "desc": "The correlation ID for the message, used to link messages in request-reply patterns",
            },
            "destination": {
                "display_name": "Destination",
                "type": SoftStringOrNothingType,
                "desc": "The destination (queue or topic) from which the message was received, in the "
                    "format `queue://name` or `topic://name`",
            },
            "delivery_mode": {
                "display_name": "Delivery Mode",
                "type": StringType,
                "desc": "The delivery mode of the message",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Persistent",
                        "short_desc": "Message is persisted to stable storage",
                        "desc": "The JMS provider logs the message to stable storage as part of the client's "
                            "send operation to ensure it is not lost in transit due to a JMS provider failure",
                        "value": "PERSISTENT",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Non-Persistent",
                        "short_desc": "Message is not persisted",
                        "desc": "The message is not logged to stable storage and may be lost in transit "
                            "due to a JMS provider failure",
                        "value": "NON_PERSISTENT",
                    },
                ),
            },
            "priority": {
                "display_name": "Priority",
                "type": IntType,
                "desc": "The JMS message priority; levels `0`\u2013`4` are normal, "
                    "levels `5`\u2013`9` are expedited",
                "allowed_values": (
                    <AllowedValueInfo>{"display_name": "0 - Lowest", "value": 0},
                    <AllowedValueInfo>{"display_name": "1 - Low", "value": 1},
                    <AllowedValueInfo>{"display_name": "2 - Below Normal", "value": 2},
                    <AllowedValueInfo>{"display_name": "3 - Normal Low", "value": 3},
                    <AllowedValueInfo>{"display_name": "4 - Normal (Default)", "value": 4},
                    <AllowedValueInfo>{"display_name": "5 - Above Normal", "value": 5},
                    <AllowedValueInfo>{"display_name": "6 - High", "value": 6},
                    <AllowedValueInfo>{"display_name": "7 - Very High", "value": 7},
                    <AllowedValueInfo>{"display_name": "8 - Critical", "value": 8},
                    <AllowedValueInfo>{"display_name": "9 - Highest", "value": 9},
                ),
            },
            "timestamp": {
                "display_name": "Timestamp",
                "type": SoftIntOrNothingType,
                "desc": "The time the message was handed off to the provider to be sent, "
                    "in milliseconds since the Unix epoch",
            },
            "expiration": {
                "display_name": "Expiration",
                "type": SoftIntOrNothingType,
                "desc": "The message expiration time in milliseconds since the Unix epoch; "
                    "0 means the message does not expire",
            },
            "type": {
                "display_name": "Type",
                "type": SoftStringOrNothingType,
                "desc": "The JMS type header; an optional application-defined message type identifier",
            },
            "redelivered": {
                "display_name": "Redelivered",
                "type": BoolType,
                "desc": "Whether this message is being redelivered",
            },
            "headers": {
                "display_name": "Headers",
                "type": AutoHashOrNothingType,
                "short_desc": "User-defined string properties on the message",
                "desc": "User-defined string properties set on the message by the sender",
            },
            "body": {
                "display_name": "Body",
                "type": AutoType,
                "short_desc": "The message body / payload",
                "desc": "The message body / payload; the type depends on the JMS message type:\n"
                    "- `TextMessage` \u2192 `string`\n"
                    "- `MapMessage` \u2192 `hash`\n"
                    "- `BytesMessage` \u2192 `binary` (or auto if `content_type` is "
                    "`application/json`)",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JMS event data type
public const JmsEventDataType = new JmsEventDataType();
}
