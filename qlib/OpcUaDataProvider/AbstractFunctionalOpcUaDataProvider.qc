# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** AbstractFunctionalOpcUaDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.AttributeId
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.Identifiers
%module-cmd(jni) import java.util.ArrayList
%module-cmd(jni) import java.util.List
%module-cmd(jni) import java.lang.reflect.Method

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Abstract base data provider for OPC UA data provider classes providing event, message, and API functionality
/**
*/
public class AbstractFunctionalOpcUaDataProvider inherits AbstractOpcUaDataProvider,
        DataProvider::DelayedObservable {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "supports_messages": MSG_Async,
            "supports_observable": True,
            "supports_request": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "application_name": <DataProviderOptionInfo>{
                "display_name": "Application Name",
                "short_desc": "The OPC UA client application name",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The application name used to identify this client to the OPC UA server",
                "default_value": "QoreOpcUaClient",
            },
            "application_uri": <DataProviderOptionInfo>{
                "display_name": "Application URI",
                "short_desc": "The OPC UA client application URI",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The application URI used to identify this client to the OPC UA server",
                "default_value": "urn:qore:opcua:client",
            },
            "auth_type": <DataProviderOptionInfo>{
                "display_name": "Authentication Type",
                "short_desc": "Authentication method for the OPC UA server",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The authentication method to use when connecting to the OPC UA server",
                "default_value": "Anonymous",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Anonymous",
                        "short_desc": "No authentication",
                        "desc": "Connect without authentication credentials",
                        "value": "Anonymous",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Username/Password",
                        "short_desc": "Authenticate with username and password",
                        "desc": "Authenticate using a username and password",
                        "value": "UsernamePassword",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Certificate",
                        "short_desc": "Authenticate with an X.509 certificate",
                        "desc": "Authenticate using an X.509 client certificate",
                        "value": "Certificate",
                    },
                ),
            },
            "auto_reconnect": <DataProviderOptionInfo>{
                "display_name": "Auto Reconnect",
                "short_desc": "Automatically reconnect on connection loss",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, the client will automatically attempt to reconnect to the "
                    "server if the connection is lost",
                "default_value": True,
            },
            "connect_timeout": <DataProviderOptionInfo>{
                "display_name": "Connect Timeout",
                "short_desc": "Connection timeout in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The connection timeout to the OPC UA server in milliseconds",
                "default_value": 10000,
            },
            "discard_oldest": <DataProviderOptionInfo>{
                "display_name": "Discard Oldest",
                "short_desc": "Discard oldest values when the monitored item queue is full",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If set to `True`, the oldest notification in the monitored item queue is "
                    "discarded when the queue is full. If `False`, the newest value is discarded",
                "default_value": True,
            },
            "message_security_mode": <DataProviderOptionInfo>{
                "display_name": "Message Security Mode",
                "short_desc": "Message security mode for the connection",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The message security mode for the OPC UA connection",
                "default_value": "None",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "None",
                        "short_desc": "No message security",
                        "desc": "Messages are neither signed nor encrypted",
                        "value": "None",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Sign",
                        "short_desc": "Messages are signed",
                        "desc": "Messages are signed but not encrypted",
                        "value": "Sign",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Sign and Encrypt",
                        "short_desc": "Messages are signed and encrypted",
                        "desc": "Messages are both signed and encrypted",
                        "value": "SignAndEncrypt",
                    },
                ),
            },
            "monitored_nodes": <DataProviderOptionInfo>{
                "display_name": "Monitored Nodes",
                "short_desc": "List of node IDs to monitor for data changes",
                "type": AbstractDataProviderTypeMap."list",
                "desc": "List of OPC UA node ID strings to monitor for data changes "
                    "(e.g., `ns=2;s=Temperature`). Standard node ID formats are supported: "
                    "numeric (`ns=0;i=2258`), string (`ns=2;s=MyVar`), GUID, and opaque",
                "required": True,
                "default_value": (),
            },
            "password": <DataProviderOptionInfo>{
                "display_name": "Password",
                "short_desc": "The password for username/password authentication",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The password for username/password authentication",
                "sensitive": True,
            },
            "publishing_interval": <DataProviderOptionInfo>{
                "display_name": "Publishing Interval",
                "short_desc": "Subscription publishing interval in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The interval in milliseconds at which the OPC UA server sends notifications "
                    "for monitored data changes",
                "default_value": 1000,
            },
            "queue_size": <DataProviderOptionInfo>{
                "display_name": "Queue Size",
                "short_desc": "Monitored item queue size",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The size of the queue for each monitored item. Notifications are queued when "
                    "the publishing interval has not yet elapsed",
                "default_value": 10,
            },
            "request_timeout": <DataProviderOptionInfo>{
                "display_name": "Request Timeout",
                "short_desc": "Request timeout in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The timeout in milliseconds for OPC UA service requests",
                "default_value": 10000,
            },
            "sampling_interval": <DataProviderOptionInfo>{
                "display_name": "Sampling Interval",
                "short_desc": "Monitored item sampling interval in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The interval in milliseconds at which the OPC UA server samples the data source "
                    "for changes",
                "default_value": 500,
            },
            "security_policy": <DataProviderOptionInfo>{
                "display_name": "Security Policy",
                "short_desc": "Security policy for the OPC UA connection",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The security policy for the OPC UA connection",
                "default_value": "None",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "None",
                        "short_desc": "No security",
                        "desc": "No message security is applied",
                        "value": "None",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Basic256Sha256",
                        "short_desc": "Basic256Sha256 security policy",
                        "desc": "AES-256 encryption with SHA-256 signatures",
                        "value": "Basic256Sha256",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Aes128_Sha256_RsaOaep",
                        "short_desc": "Aes128_Sha256_RsaOaep security policy",
                        "desc": "AES-128 encryption with SHA-256 signatures and RSA-OAEP key wrapping",
                        "value": "Aes128_Sha256_RsaOaep",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Aes256_Sha256_RsaPss",
                        "short_desc": "Aes256_Sha256_RsaPss security policy",
                        "desc": "AES-256 encryption with SHA-256 signatures and RSA-PSS key wrapping",
                        "value": "Aes256_Sha256_RsaPss",
                    },
                ),
            },
            "session_timeout": <DataProviderOptionInfo>{
                "display_name": "Session Timeout",
                "short_desc": "Session timeout in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The session timeout in milliseconds; the server will close the session if no "
                    "activity is detected within this interval",
                "default_value": 120000,
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
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "PKCS12",
                        "short_desc": "PKCS#12 keystore format",
                        "desc": "PKCS#12 keystore format (.p12 / .pfx files)",
                        "value": "PKCS12",
                    },
                    <AllowedValueInfo>{
                        "display_name": "JKS",
                        "short_desc": "Java KeyStore format",
                        "desc": "Java KeyStore format (.jks files)",
                        "value": "JKS",
                    },
                ),
            },
            "ssl.truststore.location": <DataProviderOptionInfo>{
                "display_name": "SSL Truststore Location",
                "short_desc": "Path to the truststore file",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Path to the truststore file containing trusted CA certificates for verifying "
                    "the server's certificate",
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
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "PKCS12",
                        "short_desc": "PKCS#12 truststore format",
                        "desc": "PKCS#12 truststore format (.p12 / .pfx files)",
                        "value": "PKCS12",
                    },
                    <AllowedValueInfo>{
                        "display_name": "JKS",
                        "short_desc": "Java KeyStore format",
                        "desc": "Java KeyStore format (.jks files)",
                        "value": "JKS",
                    },
                ),
            },
            "url": <DataProviderOptionInfo>{
                "display_name": "URL",
                "short_desc": "The OPC UA server endpoint URL",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The OPC UA server endpoint URL (e.g., `opc.tcp://host:4840/path`)",
                "required": True,
            },
            "username": <DataProviderOptionInfo>{
                "display_name": "Username",
                "short_desc": "The username for authentication",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The username for username/password authentication",
            },
        };

        #! OPC UA data change event constant
        const EVENT_OPCUA_DATA_CHANGE = "opcua-data-change-event";

        #! OPC UA connected event constant
        const EVENT_OPCUA_CONNECTED = "opcua-connected-event";

        #! OPC UA disconnected event constant
        const EVENT_OPCUA_DISCONNECTED = "opcua-disconnected-event";

        #! OPC UA write value message type
        const MESSAGE_WRITE_VALUE = "write-value";

        #! Read value request type
        const REQUEST_READ_VALUE = "read-value";

        #! Browse request type
        const REQUEST_BROWSE = "browse";

        #! Call method request type
        const REQUEST_CALL_METHOD = "call-method";

        #! Request type for the provider
        const RequestType = new HashDataType("OpcUaRequest", {
            "action": {
                "display_name": "Action",
                "type": StringType,
                "desc": "The action to perform: `read-value`, `browse`, or `call-method`",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Read Value",
                        "short_desc": "Read a node value",
                        "desc": "Read a value from an OPC UA node",
                        "value": REQUEST_READ_VALUE,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Browse",
                        "short_desc": "Browse the address space",
                        "desc": "Browse the OPC UA server address space",
                        "value": REQUEST_BROWSE,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Call Method",
                        "short_desc": "Call a method",
                        "desc": "Call a method on an OPC UA server object",
                        "value": REQUEST_CALL_METHOD,
                    },
                ),
            },
            "node_id": {
                "display_name": "Node ID",
                "type": SoftStringOrNothingType,
                "desc": "The OPC UA node ID (e.g., `ns=2;s=Temperature`); used by `read-value` and "
                    "`browse` actions",
            },
            "direction": {
                "display_name": "Browse Direction",
                "type": SoftStringOrNothingType,
                "desc": "Browse direction: `Forward`, `Inverse`, or `Both`; defaults to `Forward`; "
                    "only used by the `browse` action",
            },
            "max_results": {
                "display_name": "Max Results",
                "type": SoftIntOrNothingType,
                "desc": "Maximum number of references to return; `0` or omitted means no limit; "
                    "only used by the `browse` action",
            },
            "object_id": {
                "display_name": "Object ID",
                "type": SoftStringOrNothingType,
                "desc": "The node ID of the object that owns the method; used by the `call-method` "
                    "action",
            },
            "method_id": {
                "display_name": "Method ID",
                "type": SoftStringOrNothingType,
                "desc": "The node ID of the method to call; used by the `call-method` action",
            },
            "arguments": {
                "display_name": "Arguments",
                "type": SoftListOrNothingType,
                "desc": "The input arguments for the method call; used by the `call-method` action",
            },
        });
    }

    private {
        #! Monitored node IDs
        list<string> monitored_nodes;

        #! Publishing interval in ms
        int publishing_interval;

        #! Sampling interval in ms
        int sampling_interval;

        #! Queue size
        int queue_size;

        #! Discard oldest flag
        bool discard_oldest;

        #! OPC UA subscription (prevent GC)
        auto subscription;

        #! Notification listener callback (prevent GC)
        auto notification_listener;
    }

    #! Creates the object from another object of the same class
    constructor(AbstractOpcUaDataProvider prev) : AbstractOpcUaDataProvider(prev) {
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        @debug {
            LoggerWrapper::debug("AbstractFunctionalOpcUaDataProvider::constructor() url: %y",
                options.url);
        }
        hash<auto> checked_opts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions, options);
        copts = checked_opts;
        url = checked_opts.url;
        monitored_nodes = checked_opts.monitored_nodes ?? ();
        publishing_interval = checked_opts.publishing_interval ?? 1000;
        sampling_interval = checked_opts.sampling_interval ?? 500;
        queue_size = checked_opts.queue_size ?? 10;
        discard_oldest = checked_opts.discard_oldest ?? True;
    }

    destructor() {
        stop();
    }

    #! Called when all observers have been added to the object
    observersReady() {
        start();
    }

    #! Called to stop generating events
    stopEvents() {
        stop();
    }

    #! Disconnects the client and stops generating events
    private stop() {
        if (client) {
            try {
                client.disconnect().get();
                debug("OPC UA: Disconnected from %y", url);
            } catch (hash<ExceptionInfo> ex) {
                # ignore disconnect errors during stop
            }
        }
        delete subscription;
        delete notification_listener;
        delete client;
    }

    #! Connects, creates subscription, and starts monitoring
    private start() {
        try {
            client = getClient(copts, logger);
            client.connect().get();
            debug("OPC UA: Connected to %y", url);

            # notify connected event
            notifyObservers(EVENT_OPCUA_CONNECTED, {
                "reconnect": False,
                "endpoint_url": url,
                "security_policy": copts.security_policy ?? "None",
                "security_mode": copts.message_security_mode ?? "None",
            });

            # create subscription if we have monitored nodes
            if (monitored_nodes) {
                createSubscription();
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("OPC UA: Error connecting: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            LoggerWrapper::error("OPC UA: Error connecting: %s: %s", ex.err, ex.desc);
            rethrow;
        }
    }

    #! Creates a subscription and adds monitored items
    private createSubscription() {
        # create subscription
        subscription = client.getSubscriptionManager()
            .createSubscription(publishing_interval * 1.0).get();

        # create monitored item requests
        ArrayList requests();
        foreach string node_id_str in (monitored_nodes) {
            NodeId node_id = NodeId::parse(node_id_str);
            ReadValueId read_value_id(
                node_id,
                AttributeId::Value.uid(),
                NOTHING,
                QualifiedName::NULL_VALUE
            );
            MonitoringParameters params(
                subscription.nextClientHandle(),
                sampling_interval * 1.0,    # sampling interval
                NOTHING,                    # filter
                UInteger::valueOf(queue_size),
                discard_oldest
            );
            requests.add(new MonitoredItemCreateRequest(
                read_value_id,
                MonitoringMode::Reporting,
                params
            ));
        }

        # add notification listener to subscription before creating items
        notification_listener = implement_interface(
            new QoreInvocationHandler(\notificationHandler()),
            load_class(
                "org/eclipse/milo/opcua/sdk/client/api/subscriptions/UaSubscription$NotificationListener")
        );
        subscription.addNotificationListener(notification_listener);

        # create monitored items
        auto monitored_items = subscription.createMonitoredItems(
            TimestampsToReturn::Both,
            requests
        ).get();

        debug("OPC UA: Created subscription with %d monitored items", monitored_items.size());
    }

    #! Qore callback for the NotificationListener interface
    private notificationHandler(Method method, *list<auto> args) {
        string method_name = method.getName();
        debug("OPC UA: notification callback: %s", method_name);
        try {
            if (method_name == "onDataChangeNotification") {
                handleDataChangeNotification(args);
            }
        } catch (hash<ExceptionInfo> ex) {
            debug("OPC UA CALLBACK ERROR: %s", get_exception_string(ex));
        }
    }

    #! Handles data change notifications from the subscription NotificationListener
    /** @param args [0]=subscription, [1]=items (list), [2]=values (list), [3]=publishTime
    */
    private handleDataChangeNotification(*list<auto> args) {
        auto items = args[1];
        auto values = args[2];
        debug("OPC UA: data change notification: %d items", items.size());
        for (int i = 0; i < items.size(); ++i) {
            auto item = items[i];
            auto value = values[i];
            auto status = value.getStatusCode();
            auto node_id_obj = item.getReadValueId().getNodeId();
            hash<auto> event_data = {
                "node_id": node_id_obj.toParseableString(),
                "display_name": NOTHING,
                "value": convertValue(value.getValue()),
                "status_code": int(status.getValue()),
                "status_text": status.isGood() ? "Good"
                    : (status.isBad() ? "Bad" : "Uncertain"),
                "source_timestamp": convertDateTime(value.getSourceTime()),
                "server_timestamp": convertDateTime(value.getServerTime()),
                "namespace_index": int(node_id_obj.getNamespaceIndex().intValue()),
            };
            debug("OPC UA: Data change: %y", event_data);
            notifyObservers(EVENT_OPCUA_DATA_CHANGE, event_data);
        }
    }

    #! Converts a Milo Variant value to a Qore value
    private static auto convertValue(auto variant) {
        if (!variant) {
            return NOTHING;
        }
        return variant.getValue();
    }

    #! Converts a Milo DateTime to a Qore date
    private static *date convertDateTime(auto dt) {
        if (!dt) {
            return NOTHING;
        }
        # get epoch milliseconds from the Java Date and convert to Qore date
        auto java_date = dt.getJavaDate();
        if (!java_date) {
            return NOTHING;
        }
        return date(int(java_date.getTime() / 1000));
    }

    #! Returns a hash of all supported event types
    private hash<string, hash<DataProviderMessageInfo>> getEventTypesImpl() {
        return {
            EVENT_OPCUA_DATA_CHANGE: <DataProviderMessageInfo>{
                "desc": "This event is raised when a monitored OPC UA node value changes",
                "type": OpcUaDataChangeEventDataType,
            },
            EVENT_OPCUA_CONNECTED: <DataProviderMessageInfo>{
                "desc": "This event is raised when the client connects or reconnects to the OPC UA server",
                "type": OpcUaConnectedEventDataType,
            },
            EVENT_OPCUA_DISCONNECTED: <DataProviderMessageInfo>{
                "desc": "This event is raised when the client is disconnected from the OPC UA server",
                "type": OpcUaDisconnectedEventDataType,
            },
        };
    }

    #! Returns example event data for the given event
    private auto getExampleEventDataImpl(string event_id) {
        switch (event_id) {
            case EVENT_OPCUA_DATA_CHANGE:
                return {
                    "node_id": "ns=2;s=Temperature",
                    "display_name": "Temperature",
                    "value": 22.5,
                    "status_code": 0,
                    "status_text": "Good",
                    "source_timestamp": now_us(),
                    "server_timestamp": now_us(),
                    "namespace_index": 2,
                };

            case EVENT_OPCUA_CONNECTED:
                return {
                    "reconnect": False,
                    "endpoint_url": "opc.tcp://server.example.com:4840",
                    "security_policy": "None",
                    "security_mode": "None",
                };

            case EVENT_OPCUA_DISCONNECTED:
                return {
                    "status_code": 0,
                    "reason": "Normal disconnect",
                };
        }
    }

    #! Returns the description of outbound message types
    private hash<string, hash<DataProviderMessageInfo>> getMessageTypesImpl() {
        return {
            MESSAGE_WRITE_VALUE: <DataProviderMessageInfo>{
                "desc": "Write a value to an OPC UA node",
                "type": OpcUaWriteMessageDataType,
            },
        };
    }

    #! Sends a message (writes a value to an OPC UA node)
    private sendMessageImpl(string message_id, auto msg, *hash<auto> send_message_options) {
        try {
            switch (message_id) {
                case MESSAGE_WRITE_VALUE: {
                    ensureConnected();
                    NodeId node_id = NodeId::parse(msg.node_id);

                    # create Variant from the value
                    Variant variant(msg.value);
                    DataValue data_value(variant);

                    StatusCode result = client.writeValue(node_id, data_value).get();
                    if (result.isBad()) {
                        throw "OPCUA-WRITE-ERROR", sprintf("failed to write value to node %y: "
                            "status code %d", msg.node_id, int(result.getValue()));
                    }
                    debug("OPC UA: Wrote value to node %y: %y", msg.node_id, msg.value);
                    break;
                }

                default:
                    throw "UNSUPPORTED-MESSAGE", sprintf("message_id %y is unsupported; supported "
                        "messages: %y", message_id, keys getMessageTypesImpl());
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("OPC UA: Error sending message: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            LoggerWrapper::error("OPC UA: Error sending message: %s: %s", ex.err, ex.desc);
            rethrow;
        }
    }

    #! Returns the request type for this provider
    private *AbstractDataProviderType getRequestTypeImpl() {
        return RequestType;
    }

    #! Returns the response type for this provider
    private *AbstractDataProviderType getResponseTypeImpl() {
        # response type varies by action; return auto
        return AbstractDataProviderTypeMap."auto";
    }

    #! Processes API requests
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        try {
            ensureConnected();
            string action = req.action;
            switch (action) {
                case REQUEST_READ_VALUE:
                    return doReadValue(req);
                case REQUEST_BROWSE:
                    return doBrowse(req);
                case REQUEST_CALL_METHOD:
                    return doCallMethod(req);
                default:
                    throw "UNSUPPORTED-REQUEST", sprintf("action %y is unsupported; supported "
                        "actions: %y", action, (REQUEST_READ_VALUE, REQUEST_BROWSE,
                        REQUEST_CALL_METHOD));
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("OPC UA: Error in request %y: %s", req.action,
                    ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            rethrow;
        }
    }

    #! Reads a value from an OPC UA node
    private hash<auto> doReadValue(auto request) {
        auto node_id = NodeId::parse(request.node_id);
        auto data_value = client.readValue(0.0, TimestampsToReturn::Both, node_id).get();

        auto status = data_value.getStatusCode();
        return {
            "node_id": request.node_id,
            "value": convertValue(data_value.getValue()),
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
            "source_timestamp": convertDateTime(data_value.getSourceTime()),
            "server_timestamp": convertDateTime(data_value.getServerTime()),
        };
    }

    #! Browses the OPC UA address space
    private hash<auto> doBrowse(auto request) {
        string node_id_str = request.node_id ?? "ns=0;i=84";
        NodeId node_id = NodeId::parse(node_id_str);

        # determine browse direction
        auto direction = BrowseDirection::Forward;
        if (exists request.direction) {
            switch (request.direction) {
                case "Inverse":
                    direction = BrowseDirection::Inverse;
                    break;
                case "Both":
                    direction = BrowseDirection::Both;
                    break;
            }
        }

        int max_results = int(request.max_results ?? 0);

        BrowseDescription browse_desc(
            node_id,
            direction,
            Identifiers::References,
            True,  # include subtypes
            UInteger::valueOf(0),  # node class mask (all)
            UInteger::valueOf(63)  # result mask (all)
        );

        auto result = client.browse(browse_desc).get();

        auto status = result.getStatusCode();
        list<hash<auto>> refs = ();
        auto references = result.getReferences();
        if (references) {
            foreach auto ref in (references) {
                if (max_results > 0 && refs.size() >= max_results) {
                    break;
                }
                refs += {
                    "node_id": ref.getNodeId().toParseableString(),
                    "display_name": ref.getDisplayName().getText(),
                    "node_class": ref.getNodeClass().toString(),
                    "browse_name": ref.getBrowseName().getName(),
                    "reference_type": ref.getReferenceTypeId().toParseableString(),
                    "is_forward": ref.getIsForward(),
                };
            }
        }

        return {
            "references": refs,
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
        };
    }

    #! Calls a method on an OPC UA server
    private hash<auto> doCallMethod(auto request) {
        auto object_id = NodeId::parse(request.object_id);
        auto method_id = NodeId::parse(request.method_id);

        # convert arguments to Variant array
        auto input_args;
        if (request.arguments) {
            input_args = map new Variant($1), request.arguments;
        }

        auto result = client.call(
            new CallMethodRequest(object_id, method_id, input_args)
        ).get();

        auto status = result.getStatusCode();
        list<auto> output_args = ();
        auto outputs = result.getOutputArguments();
        if (outputs) {
            foreach auto out_val in (outputs) {
                output_args += convertValue(out_val);
            }
        }

        return {
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
            "output_arguments": output_args,
        };
    }

    #! Ensures the client is connected
    private ensureConnected() {
        if (!client) {
            client = getClient(copts, logger);
            client.connect().get();
            debug("OPC UA: Connected to %y", url);
        }
    }
}
}
