# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcQueryRequestDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the JdbcDataProvider module
public namespace JdbcDataProvider {
#! JDBC query request data type
public class JdbcQueryRequestDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "sql": {
                "display_name": "SQL Query",
                "type": StringType,
                "desc": "The SQL query to execute.\n\n"
                    "Use `?` placeholders for parameterized queries with the `args` field.\n\n"
                    "**Example**: `SELECT * FROM users WHERE status = ? AND created_at > ?`",
                "example_value": "SELECT * FROM users WHERE active = true",
                "required": True,
                "preselected": True,
            },
            "args": {
                "display_name": "Query Arguments",
                "type": SoftListOrNothingType,
                "desc": "Optional list of arguments for parameterized queries.\n\n"
                    "Arguments are bound to `?` placeholders in the SQL query in order.\n\n"
                    "**Example**: for `WHERE status = ? AND id > ?`, pass `(\"active\", 100)`",
                "example_value": ("active", 100),
                "preselected": True,
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JDBC query request data type
public const JdbcQueryRequestDataType = new JdbcQueryRequestDataType();
}
