# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore CamelDataProvider module definition

/** CamelMessageDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the CamelDataProvider module
public namespace CamelDataProvider {
#! Camel message request data type
public class CamelMessageDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "endpoint": {
                "display_name": "Endpoint URI",
                "type": StringType,
                "desc": "The Camel endpoint URI to send the message to.\n\n"
                    "Built-in components include:\n"
                    "- `direct:name` \u2014 synchronous in-memory messaging\n"
                    "- `seda:name` \u2014 asynchronous in-memory queue\n"
                    "- `file:directory` \u2014 file system integration\n"
                    "- `log:category` \u2014 logging\n"
                    "- `timer:name` \u2014 periodic timer events\n\n"
                    "Additional components can be loaded by adding their JARs to the Java classpath.",
                "example_value": "seda:orders",
                "required": True,
                "preselected": True,
            },
            "body": {
                "display_name": "Message Body",
                "type": AutoType,
                "desc": "The message body / payload to send.\n\n"
                    "The body type depends on the target endpoint \u2014 most endpoints accept "
                    "`string` or `binary` data.",
                "example_value": "Hello, Camel!",
                "required": True,
                "preselected": True,
            },
            "headers": {
                "display_name": "Message Headers",
                "type": AutoHashOrNothingType,
                "desc": "Optional message headers as a hash of key-value pairs.\n\n"
                    "**Example**: `{\"source\": \"order-system\", \"priority\": \"high\"}`",
                "example_value": {"source": "order-system", "priority": "high"},
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the Camel message data type
public const CamelMessageDataType = new CamelMessageDataType();
}
