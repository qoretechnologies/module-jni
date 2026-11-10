# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore CamelDataProvider module definition

/** CamelProducerDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for sending messages to Apache Camel endpoints
/**
*/
public class CamelProducerDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "producer",
            "desc": "Data provider for sending messages to Apache Camel endpoints",
            "supports_request": True,
        };

        #! The endpoint schemes of components that only pass messages in memory, which every caller may use with a
        #! plain endpoint name and no options
        const InMemorySchemes = {
            "direct": True,
            "seda": True,
            "log": True,
            "mock": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_request",
        });

        #! Request fields
        const RequestFields = {
            "endpoint": {
                "display_name": "Endpoint URI",
                "short_desc": "The Camel endpoint URI",
                "desc": "The Camel endpoint URI to send the message to (e.g. `direct:test`, "
                    "`seda:queue`, `file:outdir`)",
                "type": AbstractDataProviderTypeMap."string",
                "required": True,
            },
            "body": {
                "display_name": "Message Body",
                "short_desc": "The message body",
                "desc": "The body of the message to send to the endpoint",
                "type": AbstractDataProviderTypeMap."auto",
                "required": True,
            },
            "headers": {
                "display_name": "Message Headers",
                "short_desc": "Optional message headers",
                "desc": "Optional headers to include with the message",
                "type": AbstractDataProviderTypeMap."*hash",
            },
        };

        #! Response fields
        const ResponseFields = {
            "body": {
                "display_name": "Response Body",
                "short_desc": "The response message body",
                "desc": "The body of the response message from the endpoint",
                "type": AbstractDataProviderTypeMap."auto",
            },
            "headers": {
                "display_name": "Response Headers",
                "short_desc": "The response message headers",
                "desc": "The headers of the response message from the endpoint",
                "type": AbstractDataProviderTypeMap."*hash",
            },
        };
    }

    private {
        #! The Camel context
        QoreCamelContext ctx;
    }

    #! Creates the object from the Camel context
    constructor(QoreCamelContext ctx) {
        self.ctx = ctx;
    }

    string getName() {
        return "producer";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request hash with "endpoint" (string, required), "body" (auto, required),
        and "headers" (*hash, optional)
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the response as a hash with "body" and "headers" keys
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        # an endpoint's component, its options and its property placeholders can load classes, run code or access
        # files that the sandbox does not control; code that may not load arbitrary classes may use only a plain
        # endpoint name of an in-memory component, with no options (qore#5742)
        *string scheme = (req.endpoint =~ x/^([a-z]+):[A-Za-z0-9_.\-]+\z/)[0];
        if (!scheme || !InMemorySchemes{scheme}) {
            Jni::org::qore::jni::check_class_option("camel", "endpoint", "selects a Camel component that can load "
                "classes, run code or access files");
        }
        return ctx.sendBodyAndHeaders(req.endpoint, req.body, req.headers);
    }

    #! Returns the description of a successful request message, if any
    /** @return the request type for this provider
    */
    private *AbstractDataProviderType getRequestTypeImpl() {
        return new HashDataType("CamelProducerRequest", RequestFields);
    }

    #! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message
    */
    private *AbstractDataProviderType getResponseTypeImpl() {
        return new HashDataType("CamelProducerResponse", ResponseFields);
    }
}
}
