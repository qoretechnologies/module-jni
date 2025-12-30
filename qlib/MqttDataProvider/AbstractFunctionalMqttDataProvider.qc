# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** AbstractFunctionalMqttDataProvider.qc Copyright 2022 - 2025 Qore Technologies, s.r.o.

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
            "clean_session": <DataProviderOptionInfo>{
                "display_name": "Start With Clean Session",
                "short_desc": "Sets whether the client and server should remember state across restarts and "
                    "reconnects",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "Sets whether the client and server should remember state across restarts and reconnects.\n"
                    "- If set to `False` both the client and server will maintain state across restarts of the "
                    "client, the server and the connection. As state is maintained:\n"
                    "  - Message delivery will be reliable meeting the specified QOS even if the client, server or "
                    "connection are restarted.\n"
                    "  - The server will treat a subscription as durable.\n"
                    "- If set to `True` the client and server will not maintain state across restarts of the client, "
                    "the server or the connection. This means:\n"
                    "  - Message delivery to the specified QOS cannot be maintained if the client, server or "
                    "connection are restarted\n"
                    "  - The server will treat a subscription as non-durable",
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
            "max_in_flight": <DataProviderOptionInfo>{
                "display_name": "Max In Flight",
                "short_desc": "Set the max inflight value",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Sets the \"max inflight\" value; please increase this value in a high traffic environment",
                "default_value": 10,
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
%ifndef NoJson
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
            "password": <DataProviderOptionInfo>{
                "display_name": "Password",
                "short_desc": "The password to use",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The password to use for the connection; if any",
                "sensitive": True,
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
        };

        #! MQTT message event constant
        const EVENT_MQTT_MESSAGE = "mqtt-message-event";

        #! MQTT message type
        /** Publishes an MQTT message to the given topic
        */
        const MESSAGE_MQTT_MESSAGE = "message";
    }

    #! Creates the object from another object of the same class
    constructor(AbstractMqttDataProvider prev) : AbstractMqttDataProvider(prev) {
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        @debug {
            LoggerWrapper::debug("AbstractFunctionalMqttDataProvider::constructor() options: %y", options);
        }
        hash<auto> copts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions, options);
        hash<auto> cinfo = getClient(copts);
        client = cinfo.client;
        opts = cinfo.opts;
        topic = copts.subscription_topic;
        qos = copts.subscription_qos;
        message_format = copts.message_format;
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
            connect();

            # get MessageListener interface class
            lang::Class ml = load_class("org/eclipse/paho/client/mqttv3/IMqttMessageListener");
            client.subscribe(topic, qos,
                cast<IMqttMessageListener>(implement_interface(new QoreInvocationHandler(\messageReceived()), ml)));
            debug("MQTT: Listening on topic: %y", topic);
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

    #! Qore callback for <tt>IMqttMessageListener.messageArrived(java.lang.String topic, MqttMessage message)</tt>
    private messageReceived(Method method, *list<auto> args) {
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
                    payload = parse_json(payload.toString());
%else
                    throw "MESSAGE-FORMAT-ERROR", "cannot support format \"yaml\" without the yaml module";
%endif
                    break;
            }
        } catch (hash<ExceptionInfo> ex) {
            LoggerWrapper::error("MQTT: Error decoding message payload as %y: %s", message_format,
                get_exception_string(ex));
        }
        hash<auto> msghash = {
            "id": message.getId(),
            "qos": message.getQos(),
            "payload": payload,
            "duplicate": message.isDuplicate(),
            "retained": message.isRetained(),
        };
        debug("MQTT: Message received: %y", msghash);
        notifyObservers(EVENT_MQTT_MESSAGE, msghash);
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
        };
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
                    client.publish(msg.topic, payload, msg.qos ?? 2, msg.retained ?? False);
                    debug("MQTT: Published topic: %y qos: %y payload: %y retained: %y", msg.topic, msg.qos,
                        msg.payload, msg.retained);
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
}
}
