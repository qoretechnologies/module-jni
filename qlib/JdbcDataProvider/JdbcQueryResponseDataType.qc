# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcQueryResponseDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JDBC query response data type
public class JdbcQueryResponseDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "rows": {
                "display_name": "Result Rows",
                "type": AutoListOrNothingType,
                "desc": "The query result rows as a list of hashes, where each hash represents "
                    "one row with column names as keys",
            },
            "row_count": {
                "display_name": "Row Count",
                "type": IntType,
                "desc": "The number of rows returned (for queries) or affected (for DML statements)",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JDBC query response data type
public const JdbcQueryResponseDataType = new JdbcQueryResponseDataType();
}
