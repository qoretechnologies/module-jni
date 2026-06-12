# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** AbstractFunctionalMqttDataProvider.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.paho.mqttv5.common.MqttSubscription

#! contains all public definitions in the MqttDataProvider module
public namespace MqttDataProvider {
#! Abstract base data provider for MQTT data provider classes providing MQTT messaging functionality
/**
*/
public class AbstractFunctionalMqttDataProvider inherits AbstractMqttDataProvider, DataProvider::DelayedObservable {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "supports_messages": MSG_Async,
            "supports_observable": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "auth_data": <DataProviderOptionInfo>{
                "display_name": "Auth Data",
                "short_desc": "Enhanced authentication data",
                "type": AbstractDataProviderTypeMap."binary",
                "desc": "Authentication data for MQTT 5 enhanced authentication. Used in conjunction with "
                    "`auth_method` for challenge-response authentication flows",
                "sensitive": True,
            },
            "auth_method": <DataProviderOptionInfo>{
                "display_name": "Auth Method",
                "short_desc": "Enhanced authentication method name",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The name of the MQTT 5 enhanced authentication method (e.g., `SCRAM-SHA-256`). "
                    "When set, the broker and client will perform an extended authentication exchange",
            },
            "asyncapi": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Schema",
                "short_desc": "AsyncAPI schema URL or file path",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI schema URL or file path used to validate outgoing and incoming MQTT payloads. "
                    "This option conflicts with `validator`",
            },
            "asyncapi_channel": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Channel",
                "short_desc": "AsyncAPI channel for validation",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI channel name to use when `asyncapi` or `validator` is configured",
            },
            "auto_reconnect": <DataProviderOptionInfo>{
                "display_name": "Auto Reconnect",
                "short_desc": "Sets whether the client will automatically attempt to reconnect",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "Sets whether the client will automatically attempt to reconnect to the server if the "
                    "connection is lost\n"
                    "- If set to `False`, the client will not attempt to automatically reconnect to the server in "
                    "the event that the connection is lost\n"
                    "- If set to `True`, in the event that the connection is lost, the client will attempt to "
                        "reconnect to the server. It will initially wait 1 second before it attempts to reconnect, "
                        "for every failed reconnect attempt, the delay will double until it is at 2 minutes at "
                        "which point the delay will stay at 2 minutes",
            },
            "clean_start": <DataProviderOptionInfo>{
                "display_name": "Clean Start",
                "short_desc": "Controls whether the server should use an existing session",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "Controls whether the server should discard any existing session and start a new one.\n"
                    "- If set to `True`, the server will discard any existing session state and start a new clean "
                    "session. Subscriptions and undelivered messages from a previous session will be lost.\n"
                    "- If set to `False`, the server will resume an existing session if one is available. "
                    "Subscriptions and undelivered QoS 1/2 messages from the previous session will be retained.\n\n"
                    "Use in combination with `session_expiry_interval` to control how long the broker retains "
                    "session state after disconnect",
                "default_value": True,
            },
            "client_id": <DataProviderOptionInfo>{
                "display_name": "Client ID",
                "short_desc": "The MQTT client ID",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The MQTT client ID",
                "default_value": "QoreMqttClient",
            },
            "connect_timeout": <DataProviderOptionInfo>{
                "display_name": "Connect Timeout",
                "short_desc": "The connect timeout to the MQTT server in seconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The connect timeout to the MQTT server in seconds",
                "default_value": 30,
            },
            "connect_user_properties": <DataProviderOptionInfo>{
                "display_name": "Connect User Properties",
                "short_desc": "User properties on the CONNECT packet",
                "type": AbstractDataProviderTypeMap."hash",
                "desc": "Application-level key-value string metadata to include in the MQTT 5 CONNECT "
                    "packet. These properties are sent to the broker during connection establishment",
            },
            "encoding": <DataProviderOptionInfo>{
                "display_name": "String Character Encoding",
                "short_desc": "The character encoding for the string messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Character encoding for string messages; only applicable if the `message_format` is `string`",
                "default_value": "UTF-8",
            },
            "keep_alive_interval": <DataProviderOptionInfo>{
                "display_name": "Keep-Alive Interval",
                "short_desc": "Sets the keep-alive interval in seconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Sets the \"keep alive\" interval. This value, measured in seconds, defines the maximum "
                    "interval between messages sent or received. It enables the client to detect if the server is "
                    "no longer available, without having to wait for the TCP/IP timeout. The client will ensure "
                    "that at least one message travels across the network within each keep alive period. In the "
                    "absence of a data-related message during the time period, the client sends a very small "
                    "\"ping\" message, which the server will acknowledge. A value of `0` disables keepalive "
                    "processing in the client",
                "default_value": 60,
            },
            "max_reconnect_delay": <DataProviderOptionInfo>{
                "display_name": "Max Reconnect Delay",
                "short_desc": "Maximum delay in milliseconds for auto-reconnect backoff",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Maximum delay in milliseconds between automatic reconnection attempts. The client uses "
                    "exponential backoff starting from 1 second up to this maximum value",
                "default_value": 128000,
            },
            "maximum_packet_size": <DataProviderOptionInfo>{
                "display_name": "Maximum Packet Size",
                "short_desc": "Maximum MQTT packet size in bytes",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum MQTT packet size in bytes that the client is willing to accept. "
                    "The server will not send packets larger than this value",
            },
            "message_format": <DataProviderOptionInfo>{
                "display_name": "Message Format",
                "short_desc": "The data format for messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The data format for messages",
                "allowed_values": (
%ifndef NoJson
                    <AllowedValueInfo>{
                        "display_name": "JSON",
                        "short_desc": "Parse messages as JSON strings",
                        "desc": "Parse messages as JSON strings",
                        "value": "json",
                    },
%endif
                    <AllowedValueInfo>{
                        "display_name": "None",
                        "short_desc": "No decoding; return message payloads as binary data",
                        "desc": "No decoding; return message payloads as binary data",
                        "value": "none",
                    },
                    <AllowedValueInfo>{
                        "display_name": "String",
                        "short_desc": "Convert messages to strings with no deserialization",
                        "desc": "Convert messages to strings with no deserialization",
                        "value": "string",
                    },
%ifndef NoYaml
                    <AllowedValueInfo>{
                        "display_name": "YAML",
                        "short_desc": "Parse messages as YAML strings",
                        "desc": "Parse messages as YAML strings",
                        "value": "yaml",
                    },
%endif
                ),
%ifndef NoJson
                "default_value": "json",
%else
                "default_value": "none",
%endif
            },
            "no_local": <DataProviderOptionInfo>{
                "display_name": "No Local",
                "short_desc": "Do not receive own published messages",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, messages published by this client will not be forwarded back to it on "
                    "the subscribed topic. Only applicable to non-shared subscriptions",
                "default_value": False,
            },
            "password": <DataProviderOptionInfo>{
                "display_name": "Password",
                "short_desc": "The password to use",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The password to use for the connection; if any",
                "sensitive": True,
            },
            "receive_maximum": <DataProviderOptionInfo>{
                "display_name": "Receive Maximum",
                "short_desc": "Maximum concurrent unacknowledged QoS 1/2 messages",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Maximum number of concurrent QoS 1 and QoS 2 messages that the client is willing to "
                    "process simultaneously. The server will not send more than this number of unacknowledged "
                    "messages to the client",
                "default_value": 65535,
            },
            "request_problem_info": <DataProviderOptionInfo>{
                "display_name": "Request Problem Info",
                "short_desc": "Request problem information from the server",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, the server may send a reason string and user properties in case of "
                    "failures. If `False`, the server will not send this information",
            },
            "request_response_info": <DataProviderOptionInfo>{
                "display_name": "Request Response Info",
                "short_desc": "Request response information from the server",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, the server may return response information in the CONNACK packet. "
                    "This is used in the request/response interaction model",
            },
            "retain_as_published": <DataProviderOptionInfo>{
                "display_name": "Retain As Published",
                "short_desc": "Keep the retained flag as set by the publisher",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, messages forwarded to this subscription will keep the retained flag as "
                    "set by the publisher. If `False`, the retained flag will be cleared for forwarded messages",
                "default_value": False,
            },
            "retain_handling": <DataProviderOptionInfo>{
                "display_name": "Retain Handling",
                "short_desc": "Controls when retained messages are sent on subscribe",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Controls when the server sends retained messages to the subscription:\n"
                    "- `0`: Send retained messages at subscribe time\n"
                    "- `1`: Send retained messages only if the subscription does not already exist\n"
                    "- `2`: Do not send retained messages at subscribe time",
                "default_value": 0,
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Send at Subscribe",
                        "short_desc": "Send retained messages at subscribe time",
                        "desc": "Send retained messages at subscribe time",
                        "value": 0,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Send if New Subscription",
                        "short_desc": "Send retained messages only if the subscription is new",
                        "desc": "Send retained messages only if the subscription does not already exist",
                        "value": 1,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Never Send",
                        "short_desc": "Do not send retained messages at subscribe time",
                        "desc": "Do not send retained messages at subscribe time",
                        "value": 2,
                    },
                ),
            },
            "server_uris": <DataProviderOptionInfo>{
                "display_name": "Server URIs",
                "short_desc": "List of failover server URIs",
                "type": AbstractDataProviderTypeMap."list",
                "desc": "A list of failover server URIs. If the primary URL connection fails, the client will "
                    "try each URI in this list in order. Each URI should use the format `tcp://host:port` or "
                    "`ssl://host:port`",
            },
            "session_expiry_interval": <DataProviderOptionInfo>{
                "display_name": "Session Expiry Interval",
                "short_desc": "How long the broker retains session state after disconnect",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "How long (in seconds) the broker retains session state after the client disconnects.\n"
                    "- `0`: Session ends immediately on disconnect\n"
                    "- Omitted/`NOTHING`: Server default applies\n\n"
                    "Use with `clean_start` set to `False` to resume sessions across reconnections",
            },
            "ssl.cipher.suites": <DataProviderOptionInfo>{
                "display_name": "SSL Cipher Suites",
                "short_desc": "Comma-separated list of enabled cipher suites",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Comma-separated list of enabled SSL/TLS cipher suites for the connection",
            },
            "ssl.keystore.location": <DataProviderOptionInfo>{
                "display_name": "SSL Keystore Location",
                "short_desc": "Path to the client keystore file",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Path to the client keystore file containing the client certificate and private key "
                    "for mutual TLS authentication",
            },
            "ssl.keystore.password": <DataProviderOptionInfo>{
                "display_name": "SSL Keystore Password",
                "short_desc": "Password for the client keystore",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Password for the client keystore file",
                "sensitive": True,
            },
            "ssl.keystore.type": <DataProviderOptionInfo>{
                "display_name": "SSL Keystore Type",
                "short_desc": "Type of the client keystore",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Type of the client keystore (e.g., `PKCS12`, `JKS`)",
                "default_value": "PKCS12",
            },
            "ssl.protocol": <DataProviderOptionInfo>{
                "display_name": "SSL Protocol",
                "short_desc": "SSL/TLS protocol version to use",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "SSL/TLS protocol version to use for the connection (e.g., `TLSv1.3`, `TLSv1.2`)",
                "default_value": "TLSv1.3",
            },
            "ssl.truststore.location": <DataProviderOptionInfo>{
                "display_name": "SSL Truststore Location",
                "short_desc": "Path to the truststore file",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Path to the truststore file containing trusted CA certificates for verifying the "
                    "server's certificate",
            },
            "ssl.truststore.password": <DataProviderOptionInfo>{
                "display_name": "SSL Truststore Password",
                "short_desc": "Password for the truststore",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Password for the truststore file",
                "sensitive": True,
            },
            "ssl.truststore.type": <DataProviderOptionInfo>{
                "display_name": "SSL Truststore Type",
                "short_desc": "Type of the truststore",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Type of the truststore (e.g., `PKCS12`, `JKS`)",
                "default_value": "PKCS12",
            },
            "subscription_qos": <DataProviderOptionInfo>{
                "display_name": "Subscription QoS",
                "short_desc": "The maximum quality of service at which to subscribe",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum quality of service at which to subscribe. Messages published at a lower "
                    "quality of service will be received at the published QoS. Messages published at a higher "
                    "quality of service will be received using the QoS specified on the subscribe",
                "required": True,
                "default_value": 2,
            },
            "subscription_topic": <DataProviderOptionInfo>{
                "display_name": "Topic",
                "short_desc": "The topic to use when subscribing",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The topic to use when subscribing to messages",
                "required": True,
                "default_value": "#",
            },
            "topic_alias_maximum": <DataProviderOptionInfo>{
                "display_name": "Topic Alias Maximum",
                "short_desc": "Maximum number of topic aliases the client accepts",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum number of topic aliases that the client is willing to accept from the "
                    "server. Topic aliases reduce the size of PUBLISH packets by replacing topic strings with "
                    "short integer identifiers",
            },
            "url": <DataProviderOptionInfo>{
                "display_name": "URL",
                "short_desc": "The URL to the MQTT server",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The URL to the MQTT server; `tcp://` for unencrypted connections, `ssl://` for encrypted "
                    "connections",
                "required": True,
            },
            "username": <DataProviderOptionInfo>{
                "display_name": "Username",
                "short_desc": "The username to use",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The username to use for the connection; if any",
            },
            "validator": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Validator",
                "short_desc": "AsyncAPI validator object",
                "type": AbstractDataProviderTypeMap."object",
                "desc": "Preloaded `AsyncApi::AsyncApi3Schema` object used to validate outgoing and incoming MQTT "
                    "payloads. This option conflicts with `asyncapi` and requires `asyncapi_channel`",
            },
            "will_content_type": <DataProviderOptionInfo>{
                "display_name": "Will Content Type",
                "short_desc": "MQTT 5 content type for the will message",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The MIME content type for the will message payload (e.g., `application/json`)",
            },
            "will_delay_interval": <DataProviderOptionInfo>{
                "display_name": "Will Delay Interval",
                "short_desc": "Delay in seconds before broker sends will message",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Delay in seconds before the broker publishes the will message after the client "
                    "disconnects unexpectedly. This allows a grace period for the client to reconnect before "
                    "the will message is sent",
            },
            "will_message_expiry_interval": <DataProviderOptionInfo>{
                "display_name": "Will Message Expiry Interval",
                "short_desc": "Will message expiry in seconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The lifetime of the will message in seconds; the broker will discard the will message "
                    "after this interval if it has not been delivered",
            },
            "will_payload": <DataProviderOptionInfo>{
                "display_name": "Will Payload",
                "short_desc": "The will message payload",
                "type": AbstractDataProviderTypeMap."auto",
                "desc": "The payload of the will message; a string or binary type is sent as-is, all other "
                    "types are serialized as JSON",
            },
            "will_qos": <DataProviderOptionInfo>{
                "display_name": "Will QoS",
                "short_desc": "Quality of service for the will message",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The quality of service level for the will message",
                "default_value": 0,
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "At Most Once",
                        "short_desc": "QoS 0: fire and forget",
                        "desc": "QoS 0: message is delivered at most once with no acknowledgment",
                        "value": 0,
                    },
                    <AllowedValueInfo>{
                        "display_name": "At Least Once",
                        "short_desc": "QoS 1: acknowledged delivery",
                        "desc": "QoS 1: message is delivered at least once with acknowledgment",
                        "value": 1,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Exactly Once",
                        "short_desc": "QoS 2: assured delivery",
                        "desc": "QoS 2: message is delivered exactly once using a four-step handshake",
                        "value": 2,
                    },
                ),
            },
            "will_retained": <DataProviderOptionInfo>{
                "display_name": "Will Retained",
                "short_desc": "Whether the will message should be retained",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, the will message will be retained by the broker",
                "default_value": False,
            },
            "will_topic": <DataProviderOptionInfo>{
                "display_name": "Will Topic",
                "short_desc": "Topic for the will message",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The topic on which the broker will publish the will message when the client "
                    "disconnects unexpectedly",
            },
            "will_user_properties": <DataProviderOptionInfo>{
                "display_name": "Will User Properties",
                "short_desc": "MQTT 5 user properties on the will message",
                "type": AbstractDataProviderTypeMap."hash",
                "desc": "Application-level key-value string metadata to attach to the will message",
            },
        };

        #! MQTT message event constant
        const EVENT_MQTT_MESSAGE = "mqtt-message-event";

        #! MQTT connected event constant
        const EVENT_MQTT_CONNECTED = "mqtt-connected-event";

        #! MQTT disconnected event constant
        const EVENT_MQTT_DISCONNECTED = "mqtt-disconnected-event";

        #! MQTT message type
        /** Publishes an MQTT message to the given topic
        */
        const MESSAGE_MQTT_MESSAGE = "message";
    }

    private {
        #! Optional AsyncAPI validator
        *AsyncApi::AsyncApi3Schema asyncapi_validator;

        #! AsyncAPI channel name for validation
        *string asyncapi_channel;
    }

    #! Creates the object from another object of the same class
    constructor(AbstractMqttDataProvider prev) : AbstractMqttDataProvider(prev) {
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        @debug {
            LoggerWrapper::debug("AbstractFunctionalMqttDataProvider::constructor() url: %y client_id: %y",
                options.url, options.client_id);
        }
        hash<auto> copts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions, options);
        configureAsyncApiOptions(copts);
        hash<auto> cinfo = getClient(copts, logger);
        client = cinfo.client;
        opts = cinfo.opts;
        topic = copts.subscription_topic;
        qos = copts.subscription_qos;
        message_format = copts.message_format;
        no_local = copts.no_local ?? False;
        retain_as_published = copts.retain_as_published ?? False;
        retain_handling = copts.retain_handling ?? 0;
%ifdef NoJson
        if (message_format == "json") {
            throw "MESSAGE-FORMAT-ERROR", "cannot support format \"json\" without the json module";
        }
%endif
%ifdef NoYaml
        if (message_format == "yaml") {
            throw "MESSAGE-FORMAT-ERROR", "cannot support format \"yaml\" without the yaml module";
        }
%endif
        encoding = copts.encoding;
    }

    #! Sets an AsyncAPI validator for MQTT payload validation
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

    #! Validates an incoming MQTT payload against the AsyncAPI schema, if configured
    /** @param payload MQTT message payload to validate; may be modified by schema default handling
        @param headers MQTT user properties used as AsyncAPI message headers

        @throw SCHEMA-VALIDATION-ERROR the message payload or headers do not match the configured schema
    */
    validateIncomingMessage(reference<auto> payload, hash<auto> headers = {}) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateIncomingMessage(asyncapi_channel, \payload, headers);
        }
    }

    #! Validates an outgoing MQTT payload against the AsyncAPI schema, if configured
    /** @param payload MQTT message payload to validate; may be modified by schema default handling
        @param headers MQTT user properties used as AsyncAPI message headers; may be modified by validation

        @throw SCHEMA-VALIDATION-ERROR the message payload or headers do not match the configured schema
    */
    validateOutgoingMessage(reference<auto> payload, reference<hash<auto>> headers) {
        if (asyncapi_validator && asyncapi_channel) {
            asyncapi_validator.validateOutgoingMessage(asyncapi_channel, \payload, \headers);
        }
    }

    destructor() {
        stop();
    }

    #! Called when all observers have been added to the object
    /** This method is meant to trigger event generation
    */
    observersReady() {
        start();
    }

    #! Called to stop generating events
    /** Stops polling for new events
    */
    stopEvents() {
        stop();
    }

    #! Disconnects the client and stops generating events
    private stop() {
        if (client && client.isConnected()) {
            client.disconnect();
            debug("MQTT: Disconnected from %y", client.getServerURI());
        }
        delete client;
    }

    #! Connects and starts receiving messages
    private start() {
        try {
            # set MqttCallback to handle incoming messages; store as member to prevent GC
            lang::Class cb_cls = load_class("org/eclipse/paho/mqttv5/client/MqttCallback");
            mqtt_callback = cast<MqttCallback>(
                implement_interface(new QoreInvocationHandler(\mqttCallbackHandler()), cb_cls));
            client.setCallback(mqtt_callback);

            connect();

            # subscribe with MQTT 5 subscription options
            MqttSubscription mqtt_sub(topic, qos);
            mqtt_sub.setNoLocal(no_local);
            mqtt_sub.setRetainAsPublished(retain_as_published);
            mqtt_sub.setRetainHandling(retain_handling);
            client.subscribe((mqtt_sub,));
            debug("MQTT: Listening on topic: %y (no_local: %y, retain_as_published: %y, retain_handling: %d)",
                topic, no_local, retain_as_published, retain_handling);
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("MQTT: Error subscribing to topic: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            LoggerWrapper::error("MQTT: Error subscribing to topic: %s", ex.err, ex.desc);
            rethrow;
        }
    }

    #! Connects to the server if necessary
    private connect() {
        if (client.isConnected()) {
            return;
        }
        debug("MQTT: Connecting to server %y", client.getServerURI());
        client.connect(opts);
        debug("MQTT: Connected to server %y", client.getServerURI());
    }

    #! Qore callback for the MqttCallback interface
    private mqttCallbackHandler(Method method, *list<auto> args) {
        string method_name = method.getName();
        debug("MQTT callback: method=%y", method_name);
        try {
            switch (method_name) {
                case "connectComplete":
                    handleConnectComplete(args);
                    return;
                case "disconnected":
                    handleDisconnected(args);
                    return;
                case "messageArrived":
                    break;
                default:
                    return;
            }
        } catch (hash<ExceptionInfo> ex) {
            LoggerWrapper::error("MQTT: Error in %y callback: %s", method_name, get_exception_string(ex));
            return;
        }
        try {
            string recv_topic = args[0];
            MqttMessage message = args[1];
            auto payload = message.getPayload();
            try {
                switch (message_format) {
                    case "string":
                        payload = payload.toString(encoding);
                        break;

                    case "json":
%ifndef NoJson
                        payload = parse_json(payload.toString());
%else
                        throw "MESSAGE-FORMAT-ERROR", "cannot support format \"json\" without the json module";
%endif
                        break;

                    case "yaml":
%ifndef NoYaml
                        payload = parse_yaml(payload.toString());
%else
                        throw "MESSAGE-FORMAT-ERROR", "cannot support format \"yaml\" without the yaml module";
%endif
                        break;
                }
            } catch (hash<ExceptionInfo> ex) {
                LoggerWrapper::error("MQTT: Error decoding message payload as %y: %s", message_format,
                    get_exception_string(ex));
            }

            # extract MQTT 5 properties
            MqttProperties props = message.getProperties();
            hash<auto> msghash = {
                "topic": recv_topic,
                "id": message.getId(),
                "qos": message.getQos(),
                "payload": payload,
                "duplicate": message.isDuplicate(),
                "retained": message.isRetained(),
                "content_type": props ? props.getContentType() : NOTHING,
                "response_topic": props ? props.getResponseTopic() : NOTHING,
                "correlation_data": props ? props.getCorrelationData() : NOTHING,
                "message_expiry_interval": props ? props.getMessageExpiryInterval() : NOTHING,
                "payload_format": props ? props.getPayloadFormat() : NOTHING,
                "user_properties": props ? getUserPropertiesHash(props) : NOTHING,
                "subscription_identifiers": props ? props.getSubscriptionIdentifiers() : NOTHING,
            };
            payload = msghash.payload;
            validateIncomingMessage(\payload, getAsyncApiHeaders(msghash));
            msghash.payload = payload;
            debug("MQTT: Message received: %y", msghash);
            notifyObservers(EVENT_MQTT_MESSAGE, msghash);
        } catch (hash<ExceptionInfo> ex) {
            LoggerWrapper::error("MQTT: Error in message callback: %s", get_exception_string(ex));
        }
    }

    #! Converts Java UserProperty list to a Qore hash
    private static *hash<string, string> getUserPropertiesHash(MqttProperties props) {
        auto user_props = props.getUserProperties();
        if (!user_props) {
            return;
        }
        hash<string, string> rv;
        for (int i = 0, int size = user_props.size(); i < size; ++i) {
            UserProperty up = user_props.get(i);
            rv{up.getKey()} = up.getValue();
        }
        return rv;
    }

    #! Handles the connectComplete callback
    private handleConnectComplete(*list<auto> args) {
        bool reconnect = args[0] ?? False;
        string server_uri = args[1] ?? "";
        hash<auto> event_data = {
            "reconnect": reconnect,
            "server_uri": server_uri,
        };
        debug("MQTT: Connected (reconnect: %y, server: %y)", reconnect, server_uri);
        notifyObservers(EVENT_MQTT_CONNECTED, event_data);
    }

    #! Handles the disconnected callback
    private handleDisconnected(*list<auto> args) {
        hash<auto> event_data;
        if (args[0] instanceof MqttDisconnectResponse) {
            MqttDisconnectResponse resp = args[0];
            event_data = {
                "reason_code": resp.getReturnCode(),
                "reason": resp.getReasonString(),
                "server_reference": resp.getServerReference(),
            };
        } else {
            event_data = {
                "reason_code": -1,
            };
        }
        debug("MQTT: Disconnected (reason_code: %y, reason: %y)", event_data.reason_code,
            event_data.reason);
        notifyObservers(EVENT_MQTT_DISCONNECTED, event_data);
    }

    #! Returns a hash of all supported event types
    /** @return a hash of all supported event types

        @note only called if the provider supports the observer pattern / event API
    */
    private hash<string, hash<DataProviderMessageInfo>> getEventTypesImpl() {
        return {
            EVENT_MQTT_MESSAGE: <DataProviderMessageInfo>{
                "desc": "This event is raised when an incoming MQTT message matching the subscription topic arrives",
                "type": MqttEventDataType,
            },
            EVENT_MQTT_CONNECTED: <DataProviderMessageInfo>{
                "desc": "This event is raised when the client connects or reconnects to the MQTT broker",
                "type": MqttConnectedEventDataType,
            },
            EVENT_MQTT_DISCONNECTED: <DataProviderMessageInfo>{
                "desc": "This event is raised when the client is disconnected from the MQTT broker",
                "type": MqttDisconnectedEventDataType,
            },
        };
    }

    #! Returns example event data for the given event
    /** @param event_id the event ID

        @return example data for the given event
    */
    private auto getExampleEventDataImpl(string event_id) {
        switch (event_id) {
            case EVENT_MQTT_MESSAGE:
                return {
                    "topic": "sensors/temperature",
                    "id": 1,
                    "qos": 1,
                    "payload": {"temperature": 22.5, "unit": "celsius"},
                    "duplicate": False,
                    "retained": False,
                    "content_type": "application/json",
                    "response_topic": "sensors/temperature/response",
                    "correlation_data": <abcd>,
                    "message_expiry_interval": 3600,
                    "payload_format": True,
                    "user_properties": {"source": "sensor-1", "region": "us-east"},
                };

            case EVENT_MQTT_CONNECTED:
                return {
                    "reconnect": False,
                    "server_uri": "ssl://broker.example.com:8883",
                };

            case EVENT_MQTT_DISCONNECTED:
                return {
                    "reason_code": 0,
                    "reason": "Normal disconnect",
                };
        }
    }

    #! Returns the description of an outbound message, if any
    /** @return the message type for this provider

        @note only called if the provider supports sending messages
    */
    private hash<string, hash<DataProviderMessageInfo>> getMessageTypesImpl() {
        return {
            MESSAGE_MQTT_MESSAGE: <DataProviderMessageInfo>{
                "desc": "An outgoing MQTT message that will be published according to the message contents",
                "type": MqttMessageDataType,
            },
        };
    }

    #! Sends a message from message-capable data providers
    /**
        @param msg the message to send
        @param send_message_options the options for sending the message
    */
    private sendMessageImpl(string message_id, auto msg, *hash<auto> send_message_options) {
        try {
            switch (message_id) {
                case MESSAGE_MQTT_MESSAGE: {
                    auto out_payload = msg.payload;
                    hash<auto> headers = getAsyncApiHeaders(msg);
                    validateOutgoingMessage(\out_payload, \headers);
                    msg.payload = out_payload;
                    {
                        int pt = msg.payload.typeCode();
                        if (pt != NT_STRING && pt != NT_BINARY) {
%ifndef NoJson
                            msg.payload = make_json(msg.payload);
%else
                            throw "MESSAGE-SERIALIZATION-ERROR", sprintf("cannot serialize a message of type %y to "
                                "JSON without the json module", msg.payload.fullType());
%endif
                        }
                    }
                    softbinary payload = msg.payload;
                    connect();

                    MqttMessage mqtt_msg(payload);
                    mqtt_msg.setQos(msg.qos ?? 2);
                    mqtt_msg.setRetained(msg.retained ?? False);

                    # set MQTT 5 properties if any are provided
                    if (msg.content_type || msg.response_topic || msg.correlation_data
                            || exists msg.message_expiry_interval || exists msg.payload_format
                            || msg.user_properties) {
                        MqttProperties props();
                        if (msg.content_type) {
                            props.setContentType(msg.content_type);
                        }
                        if (msg.response_topic) {
                            props.setResponseTopic(msg.response_topic);
                        }
                        if (msg.correlation_data) {
                            props.setCorrelationData(binary(msg.correlation_data));
                        }
                        if (exists msg.message_expiry_interval) {
                            props.setMessageExpiryInterval(int(msg.message_expiry_interval));
                        }
                        if (exists msg.payload_format) {
                            props.setPayloadFormat(msg.payload_format);
                        }
                        if (msg.user_properties) {
                            ArrayList up_list();
                            map up_list.add(new UserProperty($1.key, $1.value)),
                                msg.user_properties.pairIterator();
                            props.setUserProperties(up_list);
                        }
                        mqtt_msg.setProperties(props);
                    }

                    client.publish(msg.topic, mqtt_msg);
                    debug("MQTT: Published topic: %y qos: %y payload: %y retained: %y", msg.topic,
                        msg.qos, msg.payload, msg.retained);
                    break;
                }

                default:
                    throw "UNSUPPORTED-MESSAGE", sprintf("message_id %y is unsupported; supported messages: %y",
                        keys getMessageTypesImpl());
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("MQTT: Error sending message: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            LoggerWrapper::error("MQTT: Error sending message: %s", ex.err, ex.desc);
            rethrow;
        }
    }

    private configureAsyncApiOptions(hash<auto> opts) {
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

    private static hash<auto> getAsyncApiHeaders(hash<auto> msg) {
        return msg.user_properties ?? {};
    }
}
}
