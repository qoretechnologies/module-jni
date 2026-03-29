# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcExecDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for executing JDBC SQL statements (INSERT, UPDATE, DELETE, DDL)
/**
*/
public class JdbcExecDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "exec",
            "desc": "Data provider for executing SQL statements (INSERT, UPDATE, DELETE, DDL) via JDBC",
            "supports_request": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_request",
        });

        #! Request fields
        const RequestFields = {
            "sql": {
                "display_name": "SQL Statement",
                "short_desc": "The SQL statement to execute",
                "desc": "The SQL statement to execute against the database (INSERT, UPDATE, DELETE, "
                    "DDL, etc.)",
                "type": AbstractDataProviderTypeMap."string",
            },
            "args": {
                "display_name": "Statement Arguments",
                "short_desc": "Optional list of statement parameter values",
                "desc": "An optional list of parameter values for prepared statement placeholders "
                    "in the SQL statement",
                "type": AbstractDataProviderTypeMap."*list",
            },
        };

        #! Response fields
        const ResponseFields = {
            "affected_rows": {
                "display_name": "Affected Rows",
                "short_desc": "The number of rows affected by the statement",
                "desc": "The number of rows affected by the SQL statement",
                "type": AbstractDataProviderTypeMap."int",
            },
            "success": {
                "display_name": "Success",
                "short_desc": "Whether the statement executed successfully",
                "desc": "Whether the SQL statement executed successfully",
                "type": AbstractDataProviderTypeMap."bool",
            },
        };
    }

    private {
        #! The query executor
        JdbcQueryExecutor executor;
    }

    #! Creates the object from the executor
    constructor(JdbcQueryExecutor executor) {
        self.executor = executor;
    }

    string getName() {
        return "exec";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request info with "sql" and optional "args" keys
        @param request_options the request options; will be processed by validateRequestOptions()

        @return a hash with "affected_rows" and "success" keys
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        string sql = req.sql;
        int affected;
        if (req.args) {
            affected = executor.executeUpdateWithParams(sql, req.args);
        } else {
            affected = executor.executeUpdate(sql);
        }
        return {
            "affected_rows": affected,
            "success": True,
        };
    }

    #! Returns the description of a successful request message, if any
    /** @return the request type for this provider
    */
    private *AbstractDataProviderType getRequestTypeImpl() {
        return new HashDataType("JdbcExecRequest", RequestFields);
    }

    #! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message
    */
    private *AbstractDataProviderType getResponseTypeImpl() {
        return new HashDataType("JdbcExecResponse", ResponseFields);
    }
}
}
