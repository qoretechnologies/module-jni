# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsMessageResponseType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JMS message response data type (for request-reply)
public class JmsMessageResponseType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "message_id": {
                "display_name": "Message ID",
                "type": SoftStringOrNothingType,
                "desc": "The message ID of the sent message",
            },
            "correlation_id": {
                "display_name": "Correlation ID",
                "type": SoftStringOrNothingType,
                "desc": "The correlation ID of the response message",
            },
            "destination": {
                "display_name": "Destination",
                "type": SoftStringOrNothingType,
                "desc": "The destination the response was received from",
            },
            "body": {
                "display_name": "Response Body",
                "type": AutoType,
                "desc": "The response message body",
            },
            "headers": {
                "display_name": "Headers",
                "type": AutoHashOrNothingType,
                "desc": "User-defined string properties on the response message",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JMS message response data type
public const JmsMessageResponseType = new JmsMessageResponseType();
}
