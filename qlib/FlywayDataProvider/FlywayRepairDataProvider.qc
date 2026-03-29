# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayRepairDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for repairing the Flyway schema history table
/**
*/
public class FlywayRepairDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "repair",
            "desc": "Data provider for repairing the Flyway schema history table by removing "
                "failed migration entries and aligning checksums",
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
            "success": {
                "display_name": "Success",
                "short_desc": "Whether the repair operation was successful",
                "desc": "Whether the repair operation completed successfully",
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
        return "repair";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request info (ignored; no input needed)
        @param request_options the request options; will be processed by validateRequestOptions()

        @return a hash with a success field indicating the repair operation completed
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        runner.repair();
        return {
            "success": True,
        };
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
        return new HashDataType("FlywayRepairResponse", ResponseFields);
    }
}
}
