# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayMigrateDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the FlywayDataProvider module
public namespace FlywayDataProvider {
#! Data provider for running Flyway database migrations
/**
*/
public class FlywayMigrateDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "migrate",
            "desc": "Data provider for running Flyway database migrations",
            "supports_request": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_request",
        });

        #! Request type - no input needed
        const RequestType = AbstractDataProviderTypeMap."nothing";

        #! Response fields
        const ResponseFields = {
            "version": {
                "display_name": "Version",
                "short_desc": "The migration version",
                "desc": "The version of the applied migration",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "description": {
                "display_name": "Description",
                "short_desc": "The migration description",
                "desc": "The description of the applied migration",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "type": {
                "display_name": "Type",
                "short_desc": "The migration type",
                "desc": "The type of the migration (e.g. SQL, JDBC)",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "executionTime": {
                "display_name": "Execution Time",
                "short_desc": "The execution time in milliseconds",
                "desc": "The execution time of the migration in milliseconds",
                "type": AbstractDataProviderTypeMap."*int",
            },
            "success": {
                "display_name": "Success",
                "short_desc": "Whether the migration was successful",
                "desc": "Whether the migration was applied successfully",
                "type": AbstractDataProviderTypeMap."bool",
            },
        };
    }

    private {
        #! The Flyway runner
        QoreFlywayRunner runner;
    }

    #! Creates the object from the runner
    constructor(QoreFlywayRunner runner) {
        self.runner = runner;
    }

    string getName() {
        return "migrate";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request info (ignored; no input needed)
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the migration results as a list of hashes with version, description, type,
        executionTime, and success fields
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        return runner.migrate();
    }

    #! Returns the description of a successful request message, if any
    /** @return the request type for this provider
    */
    private *AbstractDataProviderType getRequestTypeImpl() {
        return RequestType;
    }

    #! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message
    */
    private *AbstractDataProviderType getResponseTypeImpl() {
        return new HashDataType("FlywayMigrateResponse", ResponseFields);
    }
}
}
