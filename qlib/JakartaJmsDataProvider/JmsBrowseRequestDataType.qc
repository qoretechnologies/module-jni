# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsBrowseRequestDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JMS browse request data type
public class JmsBrowseRequestDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "destination": {
                "display_name": "Destination",
                "type": SoftStringOrNothingType,
                "desc": "The queue to browse, in format `queue://name`; if not provided, the default "
                    "destination from the connection options is used",
                "example_value": "queue://orders",
            },
            "selector": {
                "display_name": "Selector",
                "type": AutoHashOrNothingType,
                "desc": "A hash of property filters to apply as a JMS SQL-92 message selector "
                    "(e.g., `{\"color\": \"red\"}` becomes `color = 'red'`)",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JMS browse request data type
public const JmsBrowseRequestDataType = new JmsBrowseRequestDataType();
}
