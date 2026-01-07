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
    private {
        #! The MQTT client itself
        MqttClient client;

        #! Connection options
        MqttConnectOptions opts;

        #! Subscription topic filter
        string topic;

        #! Subscription QoS
        int qos;

        #! Message payload format
        string message_format;

        #! Encoding for message_format "string"
        string encoding;
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
        MqttConnectOptions opts();
        opts.setConnectionTimeout(copts.connect_timeout);
        opts.setKeepAliveInterval(copts.keep_alive_interval);
        opts.setMaxInflight(copts.max_in_flight);
        opts.setCleanSession(copts.clean_session);

        if (exists copts.auto_reconnect) {
            opts.setAutomaticReconnect(copts.auto_reconnect);
        }
        if (exists copts.username) {
            opts.setUserName(copts.username);
        }
        if (exists copts.password) {
            opts.setPassword(new String(copts.password).toCharArray());
        }

        return {
            "client": client,
            "opts": opts,
        };
    }
}
}
