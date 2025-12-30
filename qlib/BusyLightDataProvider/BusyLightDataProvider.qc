# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore BusyLightDataProvider module definition

/** BusyLightDataProvider.qc Copyright 2022 - 2025 Qore Technologies, s.r.o.

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

%requires jni

#! contains all public definitions in the BusyLightDataProvider module
public namespace BusyLightDataProvider {
#! Event- and message-based data provider for BusyLight client-side events
/**
*/
public class BusyLightDataProvider inherits MqttDataProvider::AbstractFunctionalMqttDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "busylight",
            "desc": "Data provider for Kuando Busylight messaging functionality",
        } + AbstractFunctionalMqttDataProvider::ProviderInfo;

        #! BusyLight update message type
        /** Publishes an MQTT message to the given topic to update a BusyLight
        */
        const MESSAGE_BL_UPDATE = "update";
    }

    #! Creates the object from another object of the same class
    constructor(AbstractMqttDataProvider prev) : AbstractFunctionalMqttDataProvider(prev) {
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) : AbstractFunctionalMqttDataProvider(options) {
        @debug {
            LoggerWrapper::debug("BusyLightDataProvider::constructor() url: %y client_id: %y",
                options.url, options.client_id);
        }
    }

    string getName() {
        return "busylight";
    }

    #! Returns the description of an outbound message, if any
    /** @return the message type for this provider

        @note only called if the provider supports sending messages
    */
    private hash<string, hash<DataProviderMessageInfo>> getMessageTypesImpl() {
        return {
            MESSAGE_BL_UPDATE: <DataProviderMessageInfo>{
                "desc": "Sends a message to update a Busylight",
                "type": BusyLightUpdateMessageDataType,
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
                case MESSAGE_BL_UPDATE: {
                    binary bin = msg.color.r.encodeLsb()[0..0]
                        + msg.color.b.encodeLsb()[0..0]
                        + msg.color.g.encodeLsb()[0..0]
                        + msg.on.encodeLsb()[0..0]
                        + msg.off.encodeLsb()[0..0];

                    hash<auto> downlink = {
                        "f_port": 15,
                        "frm_payload": make_base64_string(bin),
                        "priority": "NORMAL",
                    };
                    string json = make_json({"downlinks": (downlink,)});
                    softbinary payload = json;
                    connect();
                    client.publish(msg.topic, payload, 2, False);
                    info("Busylight: published topic: %y R: 0x%x G: 0x%x B: 0x%x ON: 0x%x OFF: 0x%x payload: %y",
                        msg.topic, msg.red, msg.green, msg.blue, msg.on, msg.off, json);
                    return;
                }
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("Busylight: Error sending MQTT message: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            LoggerWrapper::error("Busylight: Error sending MQTT message: %s", ex.err, ex.desc);
            rethrow;
        }
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}

public class BusyLightUpdateMessageDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "topic": {
                "display_name": "Topic",
                "desc": "The topic to publish the message on",
                "short_desc": "The topic to publish the message on",
                "type": StringType,
            },
            "color": {
                "display_name": "Color",
                "desc": "The color of the light",
                "short_desc": "The color of the light (note that the `Alpha` value is ignored)",
                "type": RgbColorDataType,
            },
            "on": {
                "desc": "The length of time ON from 0 - 255",
                "short_desc": "The length of time ON from 0 - 255",
                "type": IntType,
                "default_value": 255,
            },
            "off": {
                "desc": "The length of time OFF from 0 - 255",
                "short_desc": "The length of time OFF from 0 - 255",
                "type": IntType,
                "default_value": 0,
            },
        };
    }

    #! Creates the object
    constructor() {
        map addField(new QoreDataField($1.key, $1.value.desc, $1.value.type, $1.value.default_value)),
            Fields.pairIterator();
    }
}

public const BusyLightUpdateMessageDataType = new BusyLightUpdateMessageDataType();
}
