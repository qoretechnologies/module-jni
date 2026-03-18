# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsMessageDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JMS outbound message data type
public class JmsMessageDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "destination": {
                "display_name": "Destination",
                "type": StringType,
                "desc": "The destination to send the message to, in format `queue://name` or `topic://name`",
                "example_value": "queue://orders",
            },
            "body": {
                "display_name": "Message Body",
                "type": AutoType,
                "desc": "The message body / payload; the type determines the JMS message type:\n"
                    "- `string` \u2192 `TextMessage`\n"
                    "- `hash` \u2192 `MapMessage`\n"
                    "- `binary` \u2192 `BytesMessage`\n"
                    "- other \u2192 `BytesMessage` with JSON serialization",
                "example_value": "Hello, JMS World!",
            },
            "delivery_mode": {
                "display_name": "Delivery Mode",
                "type": SoftStringOrNothingType,
                "desc": "The delivery mode for the message",
                "default_value": "PERSISTENT",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Persistent",
                        "short_desc": "Message is persisted to stable storage",
                        "desc": "The JMS provider persists the message to stable storage",
                        "value": "PERSISTENT",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Non-Persistent",
                        "short_desc": "Message is not persisted",
                        "desc": "The message is not persisted and may be lost in transit",
                        "value": "NON_PERSISTENT",
                    },
                ),
            },
            "priority": {
                "display_name": "Priority",
                "type": SoftIntOrNothingType,
                "desc": "The JMS message priority; levels `0`\u2013`4` are normal, "
                    "levels `5`\u2013`9` are expedited",
                "default_value": 4,
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "0 - Lowest",
                        "short_desc": "Lowest priority",
                        "value": 0,
                    },
                    <AllowedValueInfo>{
                        "display_name": "1 - Low",
                        "short_desc": "Low priority",
                        "value": 1,
                    },
                    <AllowedValueInfo>{
                        "display_name": "2 - Below Normal",
                        "short_desc": "Below normal priority",
                        "value": 2,
                    },
                    <AllowedValueInfo>{
                        "display_name": "3 - Normal Low",
                        "short_desc": "Normal low priority",
                        "value": 3,
                    },
                    <AllowedValueInfo>{
                        "display_name": "4 - Normal (Default)",
                        "short_desc": "Default JMS priority",
                        "value": 4,
                    },
                    <AllowedValueInfo>{
                        "display_name": "5 - Above Normal",
                        "short_desc": "Above normal (expedited)",
                        "value": 5,
                    },
                    <AllowedValueInfo>{
                        "display_name": "6 - High",
                        "short_desc": "High priority (expedited)",
                        "value": 6,
                    },
                    <AllowedValueInfo>{
                        "display_name": "7 - Very High",
                        "short_desc": "Very high priority (expedited)",
                        "value": 7,
                    },
                    <AllowedValueInfo>{
                        "display_name": "8 - Critical",
                        "short_desc": "Critical priority (expedited)",
                        "value": 8,
                    },
                    <AllowedValueInfo>{
                        "display_name": "9 - Highest",
                        "short_desc": "Highest priority (expedited)",
                        "value": 9,
                    },
                ),
            },
            "time_to_live": {
                "display_name": "Time to Live",
                "type": SoftIntOrNothingType,
                "desc": "The message time-to-live in milliseconds; 0 means the message does not expire",
                "default_value": 0,
            },
            "correlation_id": {
                "display_name": "Correlation ID",
                "type": SoftStringOrNothingType,
                "desc": "The correlation ID to set on the message; used to link related messages",
                "example_value": "order-456",
            },
            "type": {
                "display_name": "Type",
                "type": SoftStringOrNothingType,
                "desc": "An optional application-defined message type identifier",
            },
            "headers": {
                "display_name": "Headers",
                "type": AutoHashOrNothingType,
                "short_desc": "User-defined string properties to set on the message",
                "desc": "User-defined string properties to set on the message.\n\n"
                    "Provide a hash of string key-value pairs.\n\n"
                    "**Example**: `{\"source\": \"order-system\", \"region\": \"us-east\"}`",
                "example_value": {"source": "order-system", "region": "us-east"},
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JMS message data type
public const JmsMessageDataType = new JmsMessageDataType();
}
