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
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.ReadRawModifiedDetails
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.AttributeId
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.Identifiers
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
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
                "desc": "If set to `True`, the client will automatically attempt to reconnect "
                    "to the server if the connection is lost",
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
                "desc": "If set to `True`, the oldest notification in the monitored item "
                    "queue is discarded when the queue is full. If `False`, the newest "
                    "value is discarded",
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

        #! OPC UA write multiple values message type
        const MESSAGE_WRITE_VALUES = "write-values";

        #! Read value request type
        const REQUEST_READ_VALUE = "read-value";

        #! Read multiple values request type
        const REQUEST_READ_VALUES = "read-values";

        #! Read node attributes request type
        const REQUEST_READ_ATTRIBUTES = "read-attributes";

        #! Browse request type
        const REQUEST_BROWSE = "browse-nodes";

        #! Call method request type
        const REQUEST_CALL_METHOD = "call-method";

        #! Read history request type
        const REQUEST_READ_HISTORY = "read-history";

        #! Discover endpoints request type
        const REQUEST_DISCOVER_ENDPOINTS = "discover-endpoints";

        #! Get server status request type
        const REQUEST_GET_SERVER_STATUS = "get-server-status";

        #! Request type for the provider
        const RequestType = new HashDataType("OpcUaRequest", {
            "action": {
                "display_name": "Action",
                "type": StringType,
                "desc": "The action to perform",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Read Value",
                        "short_desc": "Read a node value",
                        "desc": "Read a value from an OPC UA node",
                        "value": REQUEST_READ_VALUE,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Read Values",
                        "short_desc": "Read multiple node values",
                        "desc": "Read values from multiple OPC UA nodes in a single request",
                        "value": REQUEST_READ_VALUES,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Read Attributes",
                        "short_desc": "Read node attributes",
                        "desc": "Read attributes (display name, data type, etc.) from an OPC UA node",
                        "value": REQUEST_READ_ATTRIBUTES,
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
                    <AllowedValueInfo>{
                        "display_name": "Read History",
                        "short_desc": "Read historical data",
                        "desc": "Read historical data from an OPC UA node",
                        "value": REQUEST_READ_HISTORY,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Discover Endpoints",
                        "short_desc": "Discover server endpoints",
                        "desc": "Discover available endpoints on the OPC UA server",
                        "value": REQUEST_DISCOVER_ENDPOINTS,
                    },
                    <AllowedValueInfo>{
                        "display_name": "Get Server Status",
                        "short_desc": "Get server status",
                        "desc": "Get the current status of the OPC UA server",
                        "value": REQUEST_GET_SERVER_STATUS,
                    },
                ),
            },
            "node_id": {
                "display_name": "Node ID",
                "type": SoftStringOrNothingType,
                "desc": "The OPC UA node ID (e.g., `ns=2;s=Temperature`); used by `read-value`, "
                    "`read-attributes`, `read-history`, and `browse` actions",
            },
            "node_ids": {
                "display_name": "Node IDs",
                "type": SoftListOrNothingType,
                "desc": "List of OPC UA node IDs; used by the `read-values` action",
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
                "desc": "Maximum number of results to return; only used by the `browse` action",
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
            "start_time": {
                "display_name": "Start Time",
                "type": SoftDateOrNothingType,
                "desc": "The start time for historical data reads; used by the `read-history` action",
            },
            "end_time": {
                "display_name": "End Time",
                "type": SoftDateOrNothingType,
                "desc": "The end time for historical data reads; used by the `read-history` action",
            },
            "max_values": {
                "display_name": "Max Values",
                "type": SoftIntOrNothingType,
                "desc": "Maximum number of historical values to return per node; used by the "
                    "`read-history` action; `0` or omitted means no limit",
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
            MESSAGE_WRITE_VALUES: <DataProviderMessageInfo>{
                "desc": "Write values to multiple OPC UA nodes in a single request",
                "type": OpcUaWriteValuesMessageDataType,
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

                case MESSAGE_WRITE_VALUES: {
                    ensureConnected();
                    ArrayList write_values();
                    foreach hash<auto> item in (msg.values) {
                        NodeId node_id = NodeId::parse(item.node_id);
                        Variant variant(item.value);
                        DataValue data_value(variant);
                        write_values.add(new WriteValue(
                            node_id,
                            AttributeId::Value.uid(),
                            NOTHING,
                            data_value
                        ));
                    }
                    auto response = client.write(write_values).get();
                    auto results = response.getResults();
                    for (int i = 0; i < results.size(); ++i) {
                        if (results[i].isBad()) {
                            throw "OPCUA-WRITE-ERROR", sprintf("failed to write value to node %y: "
                                "status code %d", msg.values[i].node_id,
                                int(results[i].getValue()));
                        }
                    }
                    debug("OPC UA: Wrote %d values", msg.values.size());
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
                case REQUEST_READ_VALUES:
                    return doReadValues(req);
                case REQUEST_READ_ATTRIBUTES:
                    return doReadAttributes(req);
                case REQUEST_BROWSE:
                    return doBrowse(req);
                case REQUEST_CALL_METHOD:
                    return doCallMethod(req);
                case REQUEST_READ_HISTORY:
                    return doReadHistory(req);
                case REQUEST_DISCOVER_ENDPOINTS:
                    return doDiscoverEndpoints(req);
                case REQUEST_GET_SERVER_STATUS:
                    return doGetServerStatus(req);
                default:
                    throw "UNSUPPORTED-REQUEST", sprintf("action %y is unsupported; supported "
                        "actions: %y", action, (REQUEST_READ_VALUE, REQUEST_READ_VALUES,
                        REQUEST_READ_ATTRIBUTES, REQUEST_BROWSE, REQUEST_CALL_METHOD,
                        REQUEST_READ_HISTORY, REQUEST_DISCOVER_ENDPOINTS,
                        REQUEST_GET_SERVER_STATUS));
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

        # use server-side pagination via maxReferencesPerNode
        ArrayList browse_descs();
        browse_descs.add(browse_desc);
        auto response = client.browse(
            ViewDescription::builder().build(),
            UInteger::valueOf(max_results),
            browse_descs
        ).get();
        auto browse_results = response.getResults();
        auto result = browse_results[0];

        auto status = result.getStatusCode();
        list<hash<auto>> refs = ();
        auto references = result.getReferences();
        if (references) {
            foreach auto ref in (references) {
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

    #! Reads values from multiple OPC UA nodes in a single request
    private hash<auto> doReadValues(auto request) {
        ArrayList read_value_ids();
        foreach string node_id_str in (request.node_ids) {
            read_value_ids.add(new ReadValueId(
                NodeId::parse(node_id_str),
                AttributeId::Value.uid(),
                NOTHING,
                QualifiedName::NULL_VALUE
            ));
        }
        auto response = client.read(0.0, TimestampsToReturn::Both, read_value_ids).get();
        auto results_list = response.getResults();

        list<hash<auto>> results = ();
        for (int i = 0; i < results_list.size(); ++i) {
            auto dv = results_list[i];
            auto status = dv.getStatusCode();
            results += {
                "node_id": request.node_ids[i],
                "value": convertValue(dv.getValue()),
                "status_code": int(status.getValue()),
                "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
                "source_timestamp": convertDateTime(dv.getSourceTime()),
                "server_timestamp": convertDateTime(dv.getServerTime()),
            };
        }
        return {"results": results};
    }

    #! Reads node attributes from an OPC UA node
    private hash<auto> doReadAttributes(auto request) {
        NodeId node_id = NodeId::parse(request.node_id);

        # read multiple attributes in one request
        ArrayList read_value_ids();
        read_value_ids.add(new ReadValueId(node_id, AttributeId::Value.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::DisplayName.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::Description.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::NodeClass.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::DataType.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::AccessLevel.uid(), NOTHING,
            QualifiedName::NULL_VALUE));
        read_value_ids.add(new ReadValueId(node_id, AttributeId::Historizing.uid(), NOTHING,
            QualifiedName::NULL_VALUE));

        auto response = client.read(0.0, TimestampsToReturn::Both, read_value_ids).get();
        auto results = response.getResults();

        # the overall status is from the Value attribute read
        auto status = results[0].getStatusCode();

        # extract display name text
        *string display_name;
        if (results[1].getStatusCode().isGood()) {
            auto dn_val = results[1].getValue();
            if (dn_val) {
                display_name = dn_val.getValue().getText();
            }
        }

        # extract description text
        *string desc_text;
        if (results[2].getStatusCode().isGood()) {
            auto desc_val = results[2].getValue();
            if (desc_val) {
                desc_text = desc_val.getValue().getText();
            }
        }

        # extract node class
        *string node_class;
        if (results[3].getStatusCode().isGood()) {
            auto nc_val = results[3].getValue();
            if (nc_val) {
                node_class = nc_val.getValue().toString();
            }
        }

        # extract data type
        *string data_type;
        if (results[4].getStatusCode().isGood()) {
            auto dt_val = results[4].getValue();
            if (dt_val) {
                data_type = dt_val.getValue().toParseableString();
            }
        }

        # extract access level
        *int access_level;
        *bool writable;
        if (results[5].getStatusCode().isGood()) {
            auto al_val = results[5].getValue();
            if (al_val) {
                access_level = int(al_val.getValue().intValue());
                # bit 1 = CurrentWrite
                writable = boolean(access_level & 2);
            }
        }

        # extract historizing flag
        *bool historizing;
        if (results[6].getStatusCode().isGood()) {
            auto hist_val = results[6].getValue();
            if (hist_val) {
                historizing = boolean(hist_val.getValue());
            }
        }

        return {
            "node_id": request.node_id,
            "display_name": display_name,
            "description": desc_text,
            "node_class": node_class,
            "data_type": data_type,
            "value": convertValue(results[0].getValue()),
            "access_level": access_level,
            "writable": writable,
            "historizing": historizing,
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
        };
    }

    #! Reads historical data from an OPC UA node
    private hash<auto> doReadHistory(auto request) {
        NodeId node_id = NodeId::parse(request.node_id);

        # build read details using Milo DateTime
        DateTime start_date = request.start_time
            ? new DateTime(int(get_epoch_seconds(request.start_time)) * 1000)
            : new DateTime(0);
        DateTime end_date = request.end_time
            ? new DateTime(int(get_epoch_seconds(request.end_time)) * 1000)
            : DateTime::now();
        int max_values = int(request.max_values ?? 0);

        auto details = ReadRawModifiedDetails::builder()
            .setIsReadModified(False)
            .setStartTime(start_date)
            .setEndTime(end_date)
            .setNumValuesPerNode(UInteger::valueOf(max_values))
            .setReturnBounds(True)
            .build();

        ArrayList hrvids();
        hrvids.add(new HistoryReadValueId(node_id, NOTHING, QualifiedName::NULL_VALUE, NOTHING));

        auto response = client.historyRead(details, TimestampsToReturn::Both, False, hrvids).get();
        auto results = response.getResults();
        auto result = results[0];
        auto status = result.getStatusCode();

        list<hash<auto>> data_values = ();
        auto history_data = result.getHistoryData().decode();
        if (history_data) {
            auto dv_list = history_data.getDataValues();
            if (dv_list) {
                foreach auto dv in (dv_list) {
                    auto dv_status = dv.getStatusCode();
                    data_values += {
                        "value": convertValue(dv.getValue()),
                        "status_code": int(dv_status.getValue()),
                        "source_timestamp": convertDateTime(dv.getSourceTime()),
                        "server_timestamp": convertDateTime(dv.getServerTime()),
                    };
                }
            }
        }

        return {
            "node_id": request.node_id,
            "data_values": data_values,
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
        };
    }

    #! Discovers available endpoints on the OPC UA server
    private hash<auto> doDiscoverEndpoints(auto request) {
        string discover_url = request.node_id ?? url;
        auto endpoints = DiscoveryClient::getEndpoints(discover_url).get();

        list<hash<auto>> endpoint_list = ();
        foreach auto ep in (endpoints) {
            endpoint_list += {
                "endpoint_url": ep.getEndpointUrl(),
                "security_policy_uri": ep.getSecurityPolicyUri(),
                "security_mode": ep.getSecurityMode().toString(),
                "server_name": ep.getServer().getApplicationName().getText(),
                "transport_profile_uri": ep.getTransportProfileUri(),
            };
        }
        return {"endpoints": endpoint_list};
    }

    #! Gets the server status
    private hash<auto> doGetServerStatus(auto request) {
        # read server status nodes
        ArrayList read_value_ids();
        # ServerStatus_State (ns=0;i=2259)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2259),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_StartTime (ns=0;i=2257)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2257),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_CurrentTime (ns=0;i=2258)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2258),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_BuildInfo_ProductName (ns=0;i=2261)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2261),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_BuildInfo_ProductUri (ns=0;i=2262)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2262),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_BuildInfo_ManufacturerName (ns=0;i=2263)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2263),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));
        # ServerStatus_BuildInfo_SoftwareVersion (ns=0;i=2264)
        read_value_ids.add(new ReadValueId(new NodeId(0, 2264),
            AttributeId::Value.uid(), NOTHING, QualifiedName::NULL_VALUE));

        auto response = client.read(0.0, TimestampsToReturn::Both, read_value_ids).get();
        auto results = response.getResults();

        auto status = results[0].getStatusCode();

        # ServerState enum: 0=Running, 1=Failed, 2=NoConfiguration, etc.
        *string state;
        if (results[0].getStatusCode().isGood()) {
            auto sv = results[0].getValue();
            if (sv) {
                int state_val = int(sv.getValue());
                switch (state_val) {
                    case 0: state = "Running"; break;
                    case 1: state = "Failed"; break;
                    case 2: state = "NoConfiguration"; break;
                    case 3: state = "Suspended"; break;
                    case 4: state = "Shutdown"; break;
                    case 5: state = "Test"; break;
                    case 6: state = "CommunicationFault"; break;
                    case 7: state = "Unknown"; break;
                    default: state = sprintf("Unknown(%d)", state_val); break;
                }
            }
        }

        return {
            "state": state ?? "Unknown",
            "start_time": results[1].getStatusCode().isGood()
                ? convertDateTime(results[1].getValue() ? results[1].getValue().getValue() : NOTHING)
                : NOTHING,
            "current_time": results[2].getStatusCode().isGood()
                ? convertDateTime(results[2].getValue() ? results[2].getValue().getValue() : NOTHING)
                : NOTHING,
            "build_info_product_name": results[3].getStatusCode().isGood()
                ? convertValue(results[3].getValue()) : NOTHING,
            "build_info_product_uri": results[4].getStatusCode().isGood()
                ? convertValue(results[4].getValue()) : NOTHING,
            "build_info_manufacturer": results[5].getStatusCode().isGood()
                ? convertValue(results[5].getValue()) : NOTHING,
            "build_info_software_version": results[6].getStatusCode().isGood()
                ? convertValue(results[6].getValue()) : NOTHING,
            "status_code": int(status.getValue()),
            "status_text": status.isGood() ? "Good" : (status.isBad() ? "Bad" : "Uncertain"),
        };
    }

    #! Ensures the client is connected; reconnects if auto_reconnect is enabled
    private ensureConnected() {
        if (!client) {
            client = getClient(copts, logger);
            client.connect().get();
            debug("OPC UA: Connected to %y", url);
            return;
        }
        # check if session is still active; reconnect if needed
        bool auto_reconnect = copts.auto_reconnect ?? True;
        if (auto_reconnect) {
            try {
                # test the connection by reading the server state
                client.readValue(0.0, TimestampsToReturn::Neither, new NodeId(0, 2259)).get();
            } catch (hash<ExceptionInfo> ex) {
                debug("OPC UA: Connection lost, reconnecting to %y", url);
                try {
                    client.disconnect().get();
                } catch () {
                    # ignore disconnect errors
                }
                delete client;
                client = getClient(copts, logger);
                client.connect().get();
                debug("OPC UA: Reconnected to %y", url);
            }
        }
    }
}
}
