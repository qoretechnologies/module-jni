# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcQueryDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for executing JDBC SQL queries
/**
*/
public class JdbcQueryDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "query",
            "desc": "Data provider for executing SQL SELECT queries via JDBC",
            "supports_request": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_request",
        });

        #! Request fields
        const RequestFields = {
            "sql": {
                "display_name": "SQL Query",
                "short_desc": "The SQL SELECT query to execute",
                "desc": "The SQL SELECT query to execute against the database",
                "type": AbstractDataProviderTypeMap."string",
            },
            "args": {
                "display_name": "Query Arguments",
                "short_desc": "Optional list of query parameter values",
                "desc": "An optional list of parameter values for prepared statement placeholders "
                    "in the SQL query",
                "type": AbstractDataProviderTypeMap."*list",
            },
        };

        #! Response fields
        const ResponseFields = {
            "rows": {
                "display_name": "Result Rows",
                "short_desc": "The query result rows",
                "desc": "The list of result rows returned by the SQL query, where each row is a "
                    "hash of column name to value mappings",
                "type": AbstractDataProviderTypeMap."list",
            },
            "row_count": {
                "display_name": "Row Count",
                "short_desc": "The number of rows returned",
                "desc": "The number of rows returned by the query",
                "type": AbstractDataProviderTypeMap."int",
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
        return "query";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request info with "sql" and optional "args" keys
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the query results as a hash with "rows" and "row_count" keys
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        string sql = req.sql;
        auto rows;
        if (req.args) {
            rows = executor.executeQueryWithParams(sql, req.args);
        } else {
            rows = executor.executeQuery(sql);
        }
        return {
            "rows": rows ?? (),
            "row_count": elements (rows ?? ()),
        };
    }

    #! Returns the description of a successful request message, if any
    /** @return the request type for this provider
    */
    private *AbstractDataProviderType getRequestTypeImpl() {
        return new HashDataType("JdbcQueryRequest", RequestFields);
    }

    #! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message
    */
    private *AbstractDataProviderType getResponseTypeImpl() {
        return new HashDataType("JdbcQueryResponse", ResponseFields);
    }
}
}
