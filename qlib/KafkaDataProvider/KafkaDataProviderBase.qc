# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaDataProviderBase.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the KafkaDataProvider module
public namespace KafkaDataProvider {
#! Event- and message-based data provider for Kafka client-side events
/**
*/
public class KafkaDataProviderBase inherits AbstractDataProvider {
    private {
        KafkaConnection conn;

        #! Optional AsyncAPI validator
        *AsyncApi::AsyncApi3Schema asyncapi_validator;

        #! AsyncAPI channel name for validation
        *string asyncapi_channel;
    }

    #! Creates the object from a connection object
    constructor(KafkaConnection conn) {
        self.conn = conn;
    }

    #! Sets an AsyncAPI validator for Kafka value validation
    /** @param validator the AsyncAPI schema validator
        @param channel the channel name to use for validation
    */
    setAsyncApiValidator(AsyncApi::AsyncApi3Schema validator, string channel) {
        asyncapi_validator = validator;
        asyncapi_channel = channel;
    }

    #! Returns the current AsyncAPI validator, if any
    *AsyncApi::AsyncApi3Schema getAsyncApiValidator() {
        return asyncapi_validator;
    }

    #! Returns the current AsyncAPI channel name, if any
    *string getAsyncApiChannel() {
        return asyncapi_channel;
    }

    #! Clears the AsyncAPI validator
    clearAsyncApiValidator() {
        delete asyncapi_validator;
        delete asyncapi_channel;
    }

    #! Configures AsyncAPI validation from provider options
    configureAsyncApiOptions(hash<auto> opts) {
        *AsyncApi::AsyncApi3Schema new_validator = asyncapi_validator;
        *string new_channel = asyncapi_channel;
        *string asyncapi_url;
        if (exists opts.validator && exists opts.asyncapi) {
            throw "CONSTRUCTOR-ERROR", "options 'validator' and 'asyncapi' cannot be used together";
        }
        if (exists opts.validator) {
            if (!(opts.validator instanceof AsyncApi::AsyncApi3Schema)) {
                throw "CONSTRUCTOR-ERROR", sprintf("option 'validator' has type %y; expecting "
                    "AsyncApi::AsyncApi3Schema", opts.validator.fullType());
            }
            new_validator = opts.validator;
        }
        if (exists opts.asyncapi) {
            if (opts.asyncapi.typeCode() != NT_STRING || !opts.asyncapi.val()) {
                throw "CONSTRUCTOR-ERROR", sprintf("option 'asyncapi' has type %y and value %y; expecting a "
                    "non-empty string URL or file path", opts.asyncapi.fullType(), opts.asyncapi);
            }
            asyncapi_url = opts.asyncapi;
        }
        if (exists opts.asyncapi_channel) {
            if (opts.asyncapi_channel.typeCode() != NT_STRING || !opts.asyncapi_channel.val()) {
                throw "CONSTRUCTOR-ERROR", sprintf("option 'asyncapi_channel' has type %y and value %y; expecting "
                    "a non-empty string channel name", opts.asyncapi_channel.fullType(), opts.asyncapi_channel);
            }
            new_channel = opts.asyncapi_channel;
        }
        if (asyncapi_url) {
            new_validator = AsyncApi::AsyncApi3Loader::fromUrl(asyncapi_url);
        }
        if (new_validator && !new_channel) {
            throw "CONSTRUCTOR-ERROR", "option 'asyncapi_channel' is required when 'asyncapi' or 'validator' is "
                "configured";
        }
        if (!new_validator && new_channel) {
            throw "CONSTRUCTOR-ERROR", "option 'asyncapi_channel' requires 'asyncapi' or 'validator'";
        }
        asyncapi_validator = new_validator;
        asyncapi_channel = new_channel;
    }

    #! Validates an incoming Kafka value against the AsyncAPI schema, if configured
    validateIncomingMessage(reference<auto> value, hash<auto> headers = {}) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateIncomingMessage(asyncapi_channel, \value, headers);
        }
    }

    #! Validates an outgoing Kafka value against the AsyncAPI schema, if configured
    validateOutgoingMessage(reference<auto> value, reference<hash<auto>> headers) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateOutgoingMessage(asyncapi_channel, \value, \headers);
        }
    }

    #! Removes AsyncAPI-only options before passing hashes to Java Kafka clients
    static removeAsyncApiOptions(reference<hash<auto>> opts) {
        remove opts{"asyncapi", "asyncapi_channel", "validator"};
    }
}
}
