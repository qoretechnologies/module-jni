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
    public {
        #! The client options that name a class for the Kafka client to instantiate, with Kafka's own classes, which
        #! every caller may use (the connection sets them as defaults); see checkClassOptions()
        const ClassOptions = {
            "ssl.engine.factory.class": {
                "org.apache.kafka.common.security.ssl.DefaultSslEngineFactory": True,
            },
            "sasl.client.callback.handler.class": {},
            "sasl.login.callback.handler.class": {},
            "sasl.login.class": {},
        };

        #! The client options that name a command for the Kafka client to run, with the values that every caller may
        #! use (the connection sets its default); see checkClassOptions()
        const CommandOptions = {
            "sasl.kerberos.kinit.cmd": {
                "/usr/bin/kinit": True,
            },
        };

        #! The serializer and deserializer options; see checkClassOptions()
        const SerdeOptions = (
            "key.serializer",
            "value.serializer",
            "key.deserializer",
            "value.deserializer",
        );

        #! Kafka's own serializers and deserializers, which every caller may use
        const BuiltinSerdeClasses = map {"org.apache.kafka.common.serialization." + $1: True}, (
            "StringSerializer", "StringDeserializer", "ByteArraySerializer", "ByteArrayDeserializer",
            "ByteBufferSerializer", "ByteBufferDeserializer", "BytesSerializer", "BytesDeserializer",
            "IntegerSerializer", "IntegerDeserializer", "LongSerializer", "LongDeserializer",
            "ShortSerializer", "ShortDeserializer", "FloatSerializer", "FloatDeserializer",
            "DoubleSerializer", "DoubleDeserializer", "UUIDSerializer", "UUIDDeserializer",
            "VoidSerializer", "VoidDeserializer", "BooleanSerializer", "BooleanDeserializer",
        );

        #! Kafka's own login modules, which every caller may name in \c sasl.jaas.config
        const BuiltinLoginModules = {
            "org.apache.kafka.common.security.plain.PlainLoginModule": True,
            "org.apache.kafka.common.security.scram.ScramLoginModule": True,
            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule": True,
        };

        #! One JAAS entry and nothing else: LoginModuleClass Flag (Name=Value)* ; with optionally quoted values
        const JaasEntryRegex = "^\\s*([A-Za-z_$][A-Za-z0-9_$.]*)\\s+(?:required|requisite|sufficient|optional)"
            "(?:\\s+[A-Za-z_][A-Za-z0-9_.]*\\s*=\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"|[^\\s;\"=]+))*\\s*;\\s*$";
    }

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
    /** @return the configured AsyncAPI schema validator or NOTHING
    */
    *AsyncApi::AsyncApi3Schema getAsyncApiValidator() {
        return asyncapi_validator;
    }

    #! Returns the current AsyncAPI channel name, if any
    /** @return the configured AsyncAPI channel name or NOTHING
    */
    *string getAsyncApiChannel() {
        return asyncapi_channel;
    }

    #! Clears the AsyncAPI validator
    clearAsyncApiValidator() {
        delete asyncapi_validator;
        delete asyncapi_channel;
    }

    #! Configures AsyncAPI validation from provider options
    /** @param opts provider options that may contain \c asyncapi, \c validator, and \c asyncapi_channel

        @throw CONSTRUCTOR-ERROR invalid or conflicting AsyncAPI options
        @throw an exception from @ref AsyncApi::AsyncApi3Loader::fromUrl() if loading \c asyncapi fails
        @throw an exception from @ref FileLocationHandler::FileLocationHandler::getTextFileFromLocation() if loading
            a \c validator location fails
    */
    configureAsyncApiOptions(hash<auto> opts) {
        *AsyncApi::AsyncApi3Schema new_validator = asyncapi_validator;
        *string new_channel = asyncapi_channel;
        *string asyncapi_url;
        if (exists opts.validator && exists opts.asyncapi) {
            throw "CONSTRUCTOR-ERROR", "options 'validator' and 'asyncapi' cannot be used together";
        }
        if (exists opts.validator) {
            if (opts.validator.typeCode() != NT_STRING || !opts.validator.val()) {
                throw "CONSTRUCTOR-ERROR", sprintf("option 'validator' has type %y and value %y; expecting a "
                    "non-empty string AsyncAPI schema location or schema file data", opts.validator.fullType(),
                    opts.validator);
            }
            new_validator = KafkaDataProviderBase::loadAsyncApiValidator(opts.validator);
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
    /** @param value Kafka message value to validate; may be modified by schema default handling
        @param headers Kafka message headers used as AsyncAPI message headers

        @throw SCHEMA-VALIDATION-ERROR the message value or headers do not match the configured schema
    */
    validateIncomingMessage(reference<auto> value, hash<auto> headers = {}) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateIncomingMessage(asyncapi_channel, \value, headers);
        }
    }

    #! Validates an outgoing Kafka value against the AsyncAPI schema, if configured
    /** @param value Kafka message value to validate; may be modified by schema default handling
        @param headers Kafka message headers used as AsyncAPI message headers; may be modified by validation

        @throw SCHEMA-VALIDATION-ERROR the message value or headers do not match the configured schema
    */
    validateOutgoingMessage(reference<auto> value, reference<hash<auto>> headers) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateOutgoingMessage(asyncapi_channel, \value, \headers);
        }
    }

    #! Removes AsyncAPI-only options before passing hashes to Java Kafka clients
    /** @param opts option hash to modify in place
    */
    static removeAsyncApiOptions(reference<hash<auto>> opts) {
        remove opts{"asyncapi", "asyncapi_channel", "validator"};
    }

    #! Refuses the client options that make the Kafka client load a class to code that may not load arbitrary classes
    /** Called with the options of the connection and the provider before the Kafka client is created (qore#5742).
        For code that a sandbox governs, or that does not allow uncontrolled APIs (see
        \c Jni::org::qore::jni::check_class_option()):
        - the options that name a class (@ref ClassOptions) are refused if they are set
        - a serializer or deserializer must be one of Kafka's own (@ref BuiltinSerdeClasses)
        - \c sasl.jaas.config must be a single entry for one of Kafka's own login modules
          (@ref BuiltinLoginModules), as it names the login module class to instantiate

        Trusted code is not restricted.

        @param opts the client options

        @throw JNI-SANDBOX-ERROR the caller may not load arbitrary classes, and an option names a class
    */
    static checkClassOptions(hash<auto> opts) {
        foreach hash<auto> i in (ClassOptions.pairIterator()) {
            if (opts{i.key}.val() && !i.value{opts{i.key}}) {
                Jni::org::qore::jni::check_class_option("kafka", i.key);
            }
        }
        # the Kafka client runs this command in Java, outside of the process controls of Qore (qore#5743)
        foreach hash<auto> i in (CommandOptions.pairIterator()) {
            if (opts{i.key}.val() && !i.value{opts{i.key}}) {
                Jni::org::qore::jni::check_class_option("kafka", i.key, "names a command for the Kafka client to run");
            }
        }
        foreach string opt in (SerdeOptions) {
            if (opts{opt}.val() && !BuiltinSerdeClasses{opts{opt}}) {
                Jni::org::qore::jni::check_class_option("kafka", opt);
            }
        }
        if (opts."sasl.jaas.config".val() && !KafkaDataProviderBase::isBuiltinJaasConfig(opts."sasl.jaas.config")) {
            Jni::org::qore::jni::check_class_option("kafka", "sasl.jaas.config");
        }
    }

    #! Returns @ref True if the JAAS configuration is a single entry for one of Kafka's own login modules
    private static bool isBuiltinJaasConfig(string config) {
        *list<*string> m = regex_extract(config, JaasEntryRegex);
        return m && BuiltinLoginModules{m[0]};
    }

    private static AsyncApi::AsyncApi3Schema loadAsyncApiValidator(string validator) {
        if (KafkaDataProviderBase::isAsyncApiSchemaData(validator)) {
            return AsyncApi::AsyncApi3Loader::fromString(validator);
        }
        return AsyncApi::AsyncApi3Loader::fromString(
            FileLocationHandler::FileLocationHandler::getTextFileFromLocation(validator));
    }

    private static bool isAsyncApiSchemaData(string validator) {
        return validator =~ /[\r\n]/ || validator =~ /^\s*asyncapi:/ || validator =~ /^\s*{/
            || validator =~ /^\s*"asyncapi":/;
    }
}
}
