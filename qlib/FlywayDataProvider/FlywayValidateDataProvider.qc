# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayValidateDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for validating Flyway migrations
/**
*/
public class FlywayValidateDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "validate",
            "desc": "Data provider for validating Flyway migrations against available scripts",
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
            "validationSuccessful": {
                "display_name": "Validation Successful",
                "short_desc": "Whether validation was successful",
                "desc": "Whether the validation of applied migrations against available scripts "
                    "was successful",
                "type": AbstractDataProviderTypeMap."bool",
            },
            "invalidMigrations": {
                "display_name": "Invalid Migrations",
                "short_desc": "A list of invalid migrations",
                "desc": "A list of invalid migrations, each with version, description, and "
                    "errorDetails fields",
                "type": AbstractDataProviderTypeMap."list",
            },
            "errorMessage": {
                "display_name": "Error Message",
                "short_desc": "The validation error message",
                "desc": "The overall validation error message, if validation failed",
                "type": AbstractDataProviderTypeMap."*string",
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
        return "validate";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request info (ignored; no input needed)
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the validation result with validationSuccessful, invalidMigrations, and
        errorMessage fields
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        return runner.validate();
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
        return new HashDataType("FlywayValidateResponse", ResponseFields);
    }
}
}
