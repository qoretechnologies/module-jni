# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore MqttDataProvider module definition

/** MqttDataProvider.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.paho.mqttv5.client.MqttClient
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.IMqttMessageListener
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
%module-cmd(jni) import org.eclipse.paho.mqttv5.common.MqttMessage
%module-cmd(jni) import org.eclipse.paho.mqttv5.common.MqttException
%module-cmd(jni) import org.eclipse.paho.mqttv5.common.packet.MqttProperties
%module-cmd(jni) import org.eclipse.paho.mqttv5.common.packet.UserProperty
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.MqttCallback
%module-cmd(jni) import org.eclipse.paho.mqttv5.client.IMqttToken
%module-cmd(jni) import java.util.ArrayList
%module-cmd(jni) import java.util.Properties

%try-module json >= 1.5
%define NoJson
%endtry

%try-module yaml
%define NoYaml
%endtry

#! contains all public definitions in the MqttDataProvider module
public namespace MqttDataProvider {
#! Event- and message-based data provider for MQTT client-side events
/**
*/
public class MqttDataProvider inherits AbstractFunctionalMqttDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "mqtt",
            "desc": "Parent data provider for MQTT messaging functionality",
            "supports_children": True,
        } + AbstractFunctionalMqttDataProvider::ProviderInfo - "supports_children";

        #! Child data provider map
        const ChildMap = {
            "devices": Class::forName("MqttDevicesDataProvider"),
        };
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) : AbstractFunctionalMqttDataProvider(options) {
    }

    string getName() {
        return "mqtt";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Return data provider summary info
    *list<hash<DataProvider::DataProviderSummaryInfo>> getChildProviderSummaryInfo() {
        return map $1.getConstant("ProviderSummaryInfo").getValue(), ChildMap.iterator();
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        return keys ChildMap;
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown

        @see getChildProviderEx()
    */
    private *DataProvider::AbstractDataProvider getChildProviderImpl(string name) {
        if (*Class cls = ChildMap{name}) {
            return cls.newObject(self);
        }
    }
}
}
