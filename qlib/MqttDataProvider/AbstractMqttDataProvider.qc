# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** AbstractMqttDataProvider.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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
#! Abstract base class for MQTT data provider classes
/**
*/
public class AbstractMqttDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! SSL option name mapping from Qore options to Paho Java client properties
        const SslOptionMap = {
            "ssl.keystore.location": "com.ibm.ssl.keyStore",
            "ssl.keystore.password": "com.ibm.ssl.keyStorePassword",
            "ssl.keystore.type": "com.ibm.ssl.keyStoreType",
            "ssl.truststore.location": "com.ibm.ssl.trustStore",
            "ssl.truststore.password": "com.ibm.ssl.trustStorePassword",
            "ssl.truststore.type": "com.ibm.ssl.trustStoreType",
            "ssl.protocol": "com.ibm.ssl.protocol",
            "ssl.cipher.suites": "com.ibm.ssl.enabledCipherSuites",
        };
    }

    private {
        #! The MQTT client itself
        MqttClient client;

        #! Connection options
        MqttConnectionOptions opts;

        #! Subscription topic filter
        string topic;

        #! Subscription QoS
        int qos;

        #! Message payload format
        string message_format;

        #! Encoding for message_format "string"
        string encoding;

        #! MQTT 5 callback reference (prevent GC)
        auto mqtt_callback;

        #! No local subscription option (MQTT 5)
        bool no_local = False;

        #! Retain as published subscription option (MQTT 5)
        bool retain_as_published = False;

        #! Retain handling subscription option (MQTT 5)
        int retain_handling = 0;
    }

    #! Creates the object without any arguments for child classes
    private constructor() {
    }

    #! Creates the object from another object of the same class for child classes
    private constructor(AbstractMqttDataProvider prev) {
        client = remove prev.client;
        opts = remove prev.opts;
        topic = prev.topic;
        qos = prev.qos;
        message_format = prev.message_format;
        encoding = prev.encoding;
        no_local = prev.no_local;
        retain_as_published = prev.retain_as_published;
        retain_handling = prev.retain_handling;
        if (prev.logger) {
            setLogger(prev.logger);
        }
    }

    destructor() {
        if (client && client.isConnected()) {
            client.disconnect();
            debug("MQTT: Disconnected from %y", client.getServerURI());
        }
        delete client;
    }

    #! Returns a client for the given options
    static hash<auto> getClient(hash<auto> copts, *LoggerInterface logger) {
        @debug {
            if (logger) {
                logger.debug("AbstractMqttDataProvider::getClient() url: %y client_id: %y", copts.url,
                    copts.client_id);
            }
        }
        # create the client object
        MemoryPersistence persistence();
        MqttClient client = new MqttClient(copts.url, copts.client_id, persistence);

        # set connection options
        MqttConnectionOptions opts();
        opts.setConnectionTimeout(copts.connect_timeout);
        opts.setKeepAliveInterval(copts.keep_alive_interval);
        opts.setCleanStart(copts.clean_start);

        if (exists copts.receive_maximum) {
            opts.setReceiveMaximum(copts.receive_maximum);
        }
        if (exists copts.session_expiry_interval) {
            opts.setSessionExpiryInterval(int(copts.session_expiry_interval));
        }
        if (exists copts.auto_reconnect) {
            opts.setAutomaticReconnect(copts.auto_reconnect);
        }
        if (exists copts.max_reconnect_delay) {
            opts.setMaxReconnectDelay(copts.max_reconnect_delay);
        }
        if (exists copts.username) {
            opts.setUserName(copts.username);
        }
        if (exists copts.password) {
            opts.setPassword(binary(copts.password));
        }

        if (exists copts.maximum_packet_size) {
            opts.setMaximumPacketSize(int(copts.maximum_packet_size));
        }
        if (exists copts.topic_alias_maximum) {
            opts.setTopicAliasMaximum(int(copts.topic_alias_maximum));
        }
        if (exists copts.server_uris) {
            opts.setServerURIs(copts.server_uris);
        }
        if (exists copts.request_response_info) {
            opts.setRequestResponseInfo(boolean(copts.request_response_info));
        }
        if (exists copts.request_problem_info) {
            opts.setRequestProblemInfo(boolean(copts.request_problem_info));
        }
        if (copts.connect_user_properties) {
            ArrayList up_list();
            map up_list.add(new UserProperty($1.key, $1.value)),
                copts.connect_user_properties.pairIterator();
            opts.setUserProperties(up_list);
        }
        if (exists copts.auth_method) {
            opts.setAuthMethod(copts.auth_method);
        }
        if (exists copts.auth_data) {
            opts.setAuthData(binary(copts.auth_data));
        }

        # Will message (Last Will and Testament)
        if (exists copts.will_topic) {
            # serialize non-string/binary payloads as JSON
            auto will_payload = copts.will_payload;
            if (exists will_payload) {
                int pt = will_payload.typeCode();
                if (pt != NT_STRING && pt != NT_BINARY) {
%ifndef NoJson
                    will_payload = make_json(will_payload);
%else
                    throw "WILL-SERIALIZATION-ERROR", sprintf("cannot serialize will payload of type %y to "
                        "JSON without the json module", will_payload.fullType());
%endif
                }
            }
            MqttMessage will_msg(binary(will_payload ?? ""));
            will_msg.setQos(copts.will_qos ?? 0);
            will_msg.setRetained(copts.will_retained ?? False);

            # set MQTT 5 will properties if any are provided
            if (copts.will_content_type || exists copts.will_message_expiry_interval
                    || exists copts.will_delay_interval || copts.will_user_properties) {
                MqttProperties will_props();
                if (copts.will_content_type) {
                    will_props.setContentType(copts.will_content_type);
                }
                if (exists copts.will_message_expiry_interval) {
                    will_props.setMessageExpiryInterval(int(copts.will_message_expiry_interval));
                }
                if (exists copts.will_delay_interval) {
                    will_props.setWillDelayInterval(int(copts.will_delay_interval));
                }
                if (copts.will_user_properties) {
                    ArrayList up_list();
                    map up_list.add(new UserProperty($1.key, $1.value)),
                        copts.will_user_properties.pairIterator();
                    will_props.setUserProperties(up_list);
                }
                will_msg.setProperties(will_props);
            }
            opts.setWill(copts.will_topic, will_msg);
        }

        # SSL/TLS configuration
        {
            bool has_ssl;
            foreach hash<auto> entry in (SslOptionMap.pairIterator()) {
                if (exists copts{entry.key}) {
                    has_ssl = True;
                    break;
                }
            }
            if (has_ssl) {
                Properties ssl_props();
                foreach hash<auto> entry in (SslOptionMap.pairIterator()) {
                    if (exists copts{entry.key}) {
                        ssl_props.setProperty(entry.value, string(copts{entry.key}));
                    }
                }
                opts.setSSLProperties(ssl_props);
            }
        }

        return {
            "client": client,
            "opts": opts,
        };
    }
}
}
