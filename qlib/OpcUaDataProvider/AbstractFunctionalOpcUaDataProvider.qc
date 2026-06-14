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
            "schema": <DataProviderOptionInfo>{
                "display_name": "OPC UA Schema",
                "short_desc": "Schema snapshot for API management",
                "type": AbstractDataProviderTypeMap."hash",
                "desc": "A generic OPC UA schema snapshot hash used for schema-driven API management. "
                    "When this option is provided, the provider can expose API-management endpoints without "
                    "connecting to a remote OPC UA server. The hash must follow the module's schema snapshot "
                    "contract with `namespaces` and `endpoints` keys",
            },
            "schema_location": <DataProviderOptionInfo>{
                "display_name": "OPC UA Schema Location",
                "short_desc": "Schema snapshot or NodeSet2 location",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "A `FileLocationHandler` location containing a `NodeSet2` XML document for "
                    "schema-driven API management.\n\n"
                    "**Sandbox note**: sandboxed embedders should restrict this option to `resource://` "
                    "service resources",
                "example_value": "resource://opcua/nodeset2.xml",
            },
            "schema-location": <DataProviderOptionInfo>{
                "display_name": "OPC UA Schema Location",
                "short_desc": "Schema snapshot or NodeSet2 location",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Deprecated hyphenated alias for `schema_location`; accepted for declarative "
                    "service metadata compatibility",
                "example_value": "resource://opcua/nodeset2.xml",
            },
            "nodeset2": <DataProviderOptionInfo>{
                "display_name": "OPC UA NodeSet2",
                "short_desc": "Inline NodeSet2 XML document",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Inline OPC UA `NodeSet2` XML to import into the generic schema snapshot contract",
            },
            "nodeset2_location": <DataProviderOptionInfo>{
                "display_name": "OPC UA NodeSet2 Location",
                "short_desc": "NodeSet2 XML location",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "A `FileLocationHandler` location containing an OPC UA `NodeSet2` XML document",
                "example_value": "resource://opcua/nodeset2.xml",
            },
            "nodeset2-location": <DataProviderOptionInfo>{
                "display_name": "OPC UA NodeSet2 Location",
                "short_desc": "NodeSet2 XML location",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Deprecated hyphenated alias for `nodeset2_location`; accepted for declarative "
                    "service metadata compatibility",
                "example_value": "resource://opcua/nodeset2.xml",
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
                "desc": "The OPC UA server endpoint URL (e.g., `opc.tcp://host:4840/path`). Required for "
                    "client requests and subscriptions; schema-only API-management use may provide `schema`, "
                    "`schema_location`, or `nodeset2_location` instead",
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
                "desc": "Browse direction; defaults to `Forward`; only used by the "
                    "`browse-nodes` action",
                "allowed_values": (
                    <AllowedValueInfo>{"value": "Forward", "display_name": "Forward"},
                    <AllowedValueInfo>{"value": "Inverse", "display_name": "Inverse"},
                    <AllowedValueInfo>{"value": "Both", "display_name": "Both"},
                ),
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

        #! OPC UA symbolic status names that indicate a lost/invalid connection or session
        /** Used by @ref isConnectionException() to decide whether a failed operation should trigger
            an automatic reconnect; based on the exception's OPC UA status code rather than fragile
            text matching.
        */
        const ConnectionStatusNames = {
            "BadConnectionClosed": True,
            "BadConnectionRejected": True,
            "BadDisconnect": True,
            "BadServerNotConnected": True,
            "BadServerHalted": True,
            "BadNotConnected": True,
            "BadSessionClosed": True,
            "BadSessionIdInvalid": True,
            "BadSessionNotActivated": True,
            "BadSecureChannelClosed": True,
            "BadSecureChannelIdInvalid": True,
            "BadCommunicationError": True,
            "BadTcpInternalError": True,
        };
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
        *OpcUaSubscription subscription;

        #! Per-monitored-item data value listener callbacks (prevent GC)
        list<auto> monitored_item_listeners;

        #! Mutex serializing access to the node metadata cache
        Qore::Thread::Mutex schema_cache_lock();

        #! NodeId-keyed cache of resolved node attribute metadata (the `read-attributes` response hash)
        /** Populated on demand and invalidated deterministically on reconnect / session recreation;
            accessed from both request and subscription callback threads, so all access is serialized
            with @ref schema_cache_lock.
        */
        hash<string, hash<auto>> schema_cache;

        #! Schema snapshot supplied for schema-driven API management; avoids live server connection during scanning
        *hash<auto> api_schema_snapshot;
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
        api_schema_snapshot = resolveApiManagementSchema(checked_opts);
        if (!checked_opts.url && !api_schema_snapshot) {
            throw "CONSTRUCTOR-ERROR", "OPC UA data provider requires `url` for client operations or a schema "
                "source (`schema`, `schema_location`, `nodeset2`, or `nodeset2_location`) for API management";
        }
        url = checked_opts.url ?? "";
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
                client.disconnectAsync().get();
                debug("OPC UA: Disconnected from %y", url);
            } catch () {
                # ignore all disconnect errors during stop/destructor
            }
        }
        delete subscription;
        # clear to an empty list (not NOTHING) so a restart can append listeners again
        monitored_item_listeners = ();
        delete client;
    }

    #! Connects, creates subscription, and starts monitoring
    private start() {
        try {
            client = getClient(copts, logger);
            awaitFuture(client.connectAsync(), "connect");
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

    #! Creates a subscription and adds monitored items (Milo 1.1.x OpcUaSubscription API)
    private createSubscription() {
        subscription = new OpcUaSubscription(client);
        subscription.setPublishingInterval(publishing_interval * 1.0);
        awaitFuture(subscription.createAsync(), "create subscription");

        foreach string node_id_str in (monitored_nodes) {
            OpcUaMonitoredItem item = OpcUaMonitoredItem::newDataItem(NodeId::parse(node_id_str));
            item.setSamplingInterval(sampling_interval * 1.0);
            item.setQueueSize(UInteger::valueOf(queue_size));
            # apply the configured queue-overflow policy (discard oldest vs. newest sample) rather than
            # leaving it at the Milo default
            item.setDiscardOldest(discard_oldest);

            # bind the node id into a per-item data value listener; capturing the id avoids relying on
            # the item to report it back and gives a stable namespace index for the event
            string captured_node_id = node_id_str;
            code listener_cb = sub (Method method, *list<auto> args) {
                if (method.getName() == "onDataReceived") {
                    handleDataChange(captured_node_id, args[1]);
                }
            };
            auto listener = implement_interface(
                new QoreInvocationHandler(listener_cb),
                load_class(
                    "org/eclipse/milo/opcua/sdk/client/subscriptions/OpcUaMonitoredItem$DataValueListener")
            );
            item.setDataValueListener(listener);
            # retain the listener to prevent garbage collection while the subscription is active
            monitored_item_listeners += listener;
            subscription.addMonitoredItem(item);
        }

        subscription.createMonitoredItems();
        debug("OPC UA: Created subscription with %d monitored items", monitored_nodes.size());
    }

    #! Handles a data change for a monitored item (callback from the OPC UA subscription thread)
    /** @param node_id_str the monitored node id (captured when the item was created)
        @param value the Milo \c DataValue received from the server
    */
    private handleDataChange(string node_id_str, auto value) {
        try {
            auto status = value.getStatusCode();
            hash<auto> event_data = {
                "node_id": node_id_str,
                "display_name": NOTHING,
                "value": convertValue(value.getValue()),
                "source_timestamp": convertDateTime(value.getSourceTime()),
                "server_timestamp": convertDateTime(value.getServerTime()),
                "namespace_index": int(NodeId::parse(node_id_str).getNamespaceIndex().intValue()),
            } + statusFields(status);
            debug("OPC UA: Data change: %y", event_data);
            notifyObservers(EVENT_OPCUA_DATA_CHANGE, event_data);
        } catch (hash<ExceptionInfo> ex) {
            # exceptions in the subscription callback must not escape into the Milo thread
            debug("OPC UA CALLBACK ERROR: %s", get_exception_string(ex));
        }
    }

    #! Converts a Milo Variant value to a Qore value
    /** OPC UA unsigned wrapper types are unwrapped to native integers by the Java value codec so Qore
        receives native values rather than Java objects.
    */
    private static auto convertValue(auto variant) {
        if (!variant) {
            return NOTHING;
        }
        return ValueCodec::readVariant(variant);
    }

    #! Converts a Milo DateTime to a Qore date with millisecond precision
    private static *date convertDateTime(auto dt) {
        if (!dt) {
            return NOTHING;
        }
        # get epoch milliseconds from the Java Date and convert to Qore date
        auto java_date = dt.getJavaDate();
        if (!java_date) {
            return NOTHING;
        }
        int epoch_ms = int(java_date.getTime());
        return date(epoch_ms / 1000) + milliseconds(epoch_ms % 1000);
    }

    #! Returns the standard status fields for an OPC UA response from a Milo \c StatusCode object
    /** The returned hash preserves the existing \c status_code (numeric) and \c status_text
        (severity: \c "Good" / \c "Uncertain" / \c "Bad") fields for backwards compatibility and adds
        the richer \c symbolic_name, \c severity, and \c diagnostics fields derived from
        @ref OpcUaStatusMapping. Status information is therefore no longer collapsed to three values.

        @param status_code_obj a Milo \c StatusCode object
    */
    private static hash<auto> statusFields(auto status_code_obj) {
        hash<OpcUaStatusInfo> si = OpcUaStatusMapping::getFromStatusCode(status_code_obj);
        return {
            # preserved for backwards compatibility
            "status_code": si.code,
            "status_text": si.severity,
            # additive richer status information
            "symbolic_name": si.symbolic_name,
            "severity": si.severity,
            "diagnostics": si.description,
        };
    }

    #! Returns @ref True if the given exception indicates a lost or invalid OPC UA connection/session
    /** Walks the Java exception cause chain and inspects OPC UA status codes (via
        @ref OpcUaStatusMapping) rather than matching exception text, so detection does not depend on
        locale or message formatting. Used to decide whether to auto-reconnect.

        @param ex the Qore exception raised by a failed OPC UA operation
    */
    private static bool isConnectionException(hash<ExceptionInfo> ex) {
        auto t = ex.arg;
        int depth = 0;
        # bound the walk to avoid pathological/cyclic cause chains
        while (t instanceof object && depth < 16) {
            if (t.hasCallableMethod("getStatusCode")) {
                try {
                    auto sc = t.getStatusCode();
                    if (sc && isConnectionStatusName(OpcUaStatusMapping::getFromStatusCode(sc).symbolic_name)) {
                        return True;
                    }
                } catch (hash<ExceptionInfo> inner) {
                    # not all objects exposing getStatusCode() return an OPC UA StatusCode; ignore
                }
            }
            if (!t.hasCallableMethod("getCause")) {
                break;
            }
            auto cause = t.getCause();
            if (!(cause instanceof object) || cause === t) {
                break;
            }
            t = cause;
            ++depth;
        }
        return False;
    }

    #! Returns @ref True if the given OPC UA symbolic status name indicates a connection/session loss
    /** Pure decision function (no Java dependency) used by @ref isConnectionException().
    */
    static bool isConnectionStatusName(string symbolic_name) {
        return ConnectionStatusNames{symbolic_name} ?? False;
    }

    #! Clears the node metadata cache (thread-safe); called on reconnect / session recreation
    private clearSchemaCache() {
        schema_cache_lock.lock();
        on_exit schema_cache_lock.unlock();
        schema_cache = {};
    }

    #! Returns the cached node metadata for the given node id, or @ref nothing if not cached
    private *hash<auto> getCachedMetadata(string node_id_str) {
        schema_cache_lock.lock();
        on_exit schema_cache_lock.unlock();
        return schema_cache{node_id_str};
    }

    #! Stores node metadata in the cache for the given node id (thread-safe)
    private putCachedMetadata(string node_id_str, hash<auto> metadata) {
        schema_cache_lock.lock();
        on_exit schema_cache_lock.unlock();
        schema_cache{node_id_str} = metadata;
    }

    #! Returns the raw value of a read attribute result, or @ref nothing if the read was not good
    /** @param result a Milo \c DataValue from a read response
        @return the unwrapped attribute value (a Java object), or @ref nothing
    */
    private static auto attrValue(auto result) {
        if (!result.getStatusCode().isGood()) {
            return NOTHING;
        }
        auto variant = result.getValue();
        return variant ? variant.getValue() : NOTHING;
    }

    #! Resolves a Milo \c ExpandedNodeId to a parseable absolute NodeId string via the namespace table
    /** Browse results return \c ExpandedNodeIds, which may use the \c nsu= (namespace URI) form rather
        than \c ns= (namespace index); this resolves them to an absolute NodeId so the string can be
        parsed by \c NodeId::parse().

        @param expanded_node_id a Milo \c ExpandedNodeId
        @param namespace_table the client's Milo \c NamespaceTable

        @return a parseable NodeId string, or @ref nothing if the id cannot be resolved
    */
    private static *string expandedNodeIdToString(auto expanded_node_id, auto namespace_table) {
        auto resolved = expanded_node_id.toNodeId(namespace_table);
        return resolved.isPresent() ? resolved.get().toParseableString() : NOTHING;
    }

    #! Awaits a CompletableFuture with cooperative cancellation support
    /** Polls with 500ms timeout intervals, checking for thread cancellation between polls.
        This ensures that blocking operations can be interrupted by the Qore runtime.
        @param future the CompletableFuture to await
        @param operation a description of the operation for error messages
        @return the result of the future
        @throw PROGRAM-INTERRUPTED if the thread is cancelled during the wait
    */
    private static auto awaitFuture(auto future, string operation = "OPC UA operation") {
        while (True) {
            try {
                return future.get(500, TimeUnit::MILLISECONDS);
            } catch (hash<ExceptionInfo> ex) {
                # TimeoutException means the future hasn't completed yet; loop and check cancel
                if (ex.desc =~ /TimeoutException/) {
                    # check for thread/program cancellation
                    # this will throw PROGRAM-INTERRUPTED or THREAD-CANCELLED if active
                    # NOTE: sleep(0) is a cancellation point in Qore
                    sleep(0);
                    continue;
                }
                rethrow;
            }
        }
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

                    # create Variant from the value, honoring the optional data type hint; an invalid
                    # hint throws here, before any write is sent
                    Variant variant = OpcUaTypeConversion::getVariant(msg.value, msg.data_type);
                    DataValue data_value(variant);

                    # Milo 1.1.x: write a single WriteValue via the batch write service
                    ArrayList write_values();
                    write_values.add(new WriteValue(node_id, AttributeId::Value.uid(), NOTHING,
                        data_value));
                    auto response = awaitFuture(client.writeAsync(write_values), "write value");
                    auto result = response.getResults()[0];
                    if (result.isBad()) {
                        throw "OPCUA-WRITE-ERROR", sprintf("failed to write value to node %y: "
                            "status code %d", msg.node_id, int(result.getValue()));
                    }
                    debug("OPC UA: Wrote value to node %y: %y", msg.node_id, msg.value);
                    break;
                }

                case MESSAGE_WRITE_VALUES: {
                    ensureConnected();
                    # pre-pass: build and validate every WriteValue (node id parse + type hint) before
                    # sending any write, so an invalid item aborts the whole batch with no partial writes
                    ArrayList write_values();
                    foreach hash<auto> item in (msg.values) {
                        NodeId node_id = NodeId::parse(item.node_id);
                        Variant variant = OpcUaTypeConversion::getVariant(item.value, item.data_type);
                        DataValue data_value(variant);
                        write_values.add(new WriteValue(
                            node_id,
                            AttributeId::Value.uid(),
                            NOTHING,
                            data_value
                        ));
                    }
                    auto response = awaitFuture(
                        client.writeAsync(write_values), "write values");
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

    #! Returns a generic, versioned OPC UA schema snapshot of the connected server's address space
    /** Browses the address space starting at the given root node and returns a stable schema snapshot
        (contract version, namespace table, and the resolved variable/method endpoints with deterministic
        @ref OpcUaDataProvider::OpcUaStatusInfo "endpoint_id" values). The multi-step browse runs in the
        compiled Java resolver and returns the whole snapshot in a single JNI call.

        @param root_node_id the node id to browse from; defaults to the standard Objects folder
        (\c ns=0;i=85)

        @return the schema snapshot as a Qore hash (see @ref OpcUaDataProvider::SchemaResolver)
    */
    hash<auto> getSchemaSnapshot(*string root_node_id) {
        ensureConnected();
        return SchemaResolver::resolve(client, root_node_id ?? "ns=0;i=85");
    }

    #! Imports an offline OPC UA NodeSet2 model into a generic schema snapshot
    /** Retrieves the NodeSet2 XML from the given location with @ref FileLocationHandler (so file/resource
        access honors the standard location handling and any sandbox restrictions) and parses it with the
        compiled Java importer. No server connection is required.

        @param location a \c FileLocationHandler location (e.g. a path, \c file://..., or \c resource://...)

        @return an imported schema snapshot (\c source is \c "imported"); it carries the namespace table,
        the resolved variable/method endpoints, the \c required_models, and \c missing_dependencies (the
        required model namespaces not present in the imported model's namespace table)
    */
    hash<auto> importNodeSet2(string location) {
        string xml = FileLocationHandler::getTextFileFromLocation(location);
        return NodeSet2Importer::parse(xml);
    }

    #! Returns the schema snapshot used for generic API management
    /** If a schema source was provided in constructor options, the snapshot is returned without opening a client
        connection. Otherwise this method resolves the connected server schema through @ref getSchemaSnapshot().

        @return a generic OPC UA schema snapshot
    */
    hash<auto> getApiManagementSchemaSnapshot() {
        if (api_schema_snapshot) {
            return api_schema_snapshot;
        }
        return getSchemaSnapshot();
    }

    #! Returns schema-defined API-management endpoints keyed by stable \c endpoint_id
    /** Each returned endpoint includes the schema snapshot endpoint metadata plus an \c endpoint key for generic
        scanners, an \c expanded_node_id diagnostic alias, and normalized \c direction / \c directions metadata.

        @return list of generic API-management endpoint hashes
    */
    list<hash<auto>> getApiManagementEndpoints() {
        hash<auto> snapshot = getApiManagementSchemaSnapshot();
        list<hash<auto>> rv = ();
        foreach hash<auto> endpoint in (snapshot.endpoints) {
            *string endpoint_id = endpoint.endpoint_id;
            if (!endpoint_id) {
                continue;
            }
            list<string> directions = getApiEndpointDirections(endpoint);
            hash<auto> item = endpoint + {
                "endpoint": endpoint_id,
                "expanded_node_id": endpoint.expanded_node_id ?? endpoint.node_id,
                "directions": directions,
                "direction": directions ? directions[0] : NOTHING,
            };
            rv += item;
        }
        return rv;
    }

    #! Returns one API-management endpoint by stable \c endpoint_id
    *hash<auto> getApiManagementEndpoint(string endpoint_id) {
        foreach hash<auto> endpoint in (getApiManagementEndpoints()) {
            if (endpoint.endpoint_id == endpoint_id) {
                return endpoint;
            }
        }
    }

    private static *hash<auto> resolveApiManagementSchema(hash<auto> opts) {
        if (opts.schema.typeCode() == NT_HASH) {
            return opts.schema;
        }
        if (opts.nodeset2.typeCode() == NT_STRING) {
            return NodeSet2Importer::parse(opts.nodeset2);
        }

        *string location = opts.schema_location ?? opts."schema-location"
            ?? opts.nodeset2_location ?? opts."nodeset2-location";
        if (location) {
            return NodeSet2Importer::parse(FileLocationHandler::getTextFileFromLocation(location));
        }
    }

    private static list<string> getApiEndpointDirections(hash<auto> endpoint) {
        list<string> rv = ();
        if (endpoint.directions.typeCode() == NT_LIST) {
            foreach auto dir in (endpoint.directions) {
                rv += dir.toString();
            }
            return rv;
        }

        string kind = endpoint.kind ?? "";
        if (kind =~ /^method/) {
            return ("call",);
        }
        if (kind =~ /event/) {
            return ("observe",);
        }
        if (endpoint.readable ?? True) {
            rv += "read";
        }
        if (endpoint.writable ?? endpoint.user_writable ?? False) {
            rv += "write";
        }
        if (endpoint.historizing ?? False) {
            rv += "history-read";
        }
        rv += "observe";
        return rv;
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

    #! Processes API requests with auto-reconnect on connection failure
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        ensureConnected();
        bool auto_reconnect = copts.auto_reconnect ?? True;
        int attempts = auto_reconnect ? 2 : 1;
        for (int attempt = 0; attempt < attempts; ++attempt) {
            try {
                return doRequestIntern(req);
            } catch (hash<ExceptionInfo> ex) {
                # retry on connection errors if auto_reconnect is enabled; detection is based on the
                # OPC UA status code in the exception chain, not fragile text matching
                if (attempt == 0 && auto_reconnect && isConnectionException(ex)) {
                    reconnect();
                    continue;
                }
                if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                    LoggerWrapper::error("OPC UA: Error in request %y: %s", req.action,
                        ex.arg.toString());
                    rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
                }
                rethrow;
            }
        }
    }

    #! Internal request dispatcher
    private auto doRequestIntern(auto req) {
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
    }

    #! Reads a value from an OPC UA node
    private hash<auto> doReadValue(auto request) {
        if (!request.node_id) {
            throw "OPCUA-REQUEST-ERROR", "`node_id` is required for the `read-value` action";
        }
        auto node_id = NodeId::parse(request.node_id);
        # Milo 1.1.x has no async single readValue; read a one-node list and take the first result
        ArrayList read_nodes();
        read_nodes.add(node_id);
        auto values = awaitFuture(
            client.readValuesAsync(0.0, TimestampsToReturn::Both, read_nodes), "read value");
        auto data_value = values[0];

        auto status = data_value.getStatusCode();
        return {
            "node_id": request.node_id,
            "value": convertValue(data_value.getValue()),
            "source_timestamp": convertDateTime(data_value.getSourceTime()),
            "server_timestamp": convertDateTime(data_value.getServerTime()),
        } + statusFields(status);
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
            NodeIds::References,
            True,  # include subtypes
            UInteger::valueOf(0),  # node class mask (all)
            UInteger::valueOf(63)  # result mask (all)
        );

        # use server-side pagination via maxReferencesPerNode
        ArrayList browse_descs();
        browse_descs.add(browse_desc);
        auto response = awaitFuture(client.browseAsync(
            new ViewDescription(NodeId::NULL_VALUE, DateTime::MIN_VALUE, UInteger::valueOf(0)),
            UInteger::valueOf(max_results),
            browse_descs
        ), "browse nodes");
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
        } + statusFields(status);
    }

    #! Calls a method on an OPC UA server
    /** When the method's \c InputArguments / \c OutputArguments metadata can be resolved, the input
        argument count is validated before the call and the resolved argument metadata is included in
        the response. Argument resolution is best-effort: any failure to resolve metadata leaves the
        call behaving exactly as if no metadata were available, so it never breaks a valid call.
    */
    private hash<auto> doCallMethod(auto request) {
        if (!request.object_id) {
            throw "OPCUA-REQUEST-ERROR", "`object_id` is required for the `call-method` action";
        }
        if (!request.method_id) {
            throw "OPCUA-REQUEST-ERROR", "`method_id` is required for the `call-method` action";
        }
        auto object_id = NodeId::parse(request.object_id);
        auto method_id = NodeId::parse(request.method_id);

        # resolve method argument metadata (best-effort, cached); validate input arg count if available
        *hash<auto> arg_meta = resolveMethodArguments(method_id);
        if (arg_meta) {
            int provided = request.arguments.size();
            int expected = arg_meta.input.size();
            if (provided != expected) {
                throw "OPCUA-METHOD-ARG-ERROR", sprintf("method %y expects %d input argument%s, but %d "
                    "%s provided", request.method_id, expected, expected == 1 ? "" : "s", provided,
                    provided == 1 ? "was" : "were");
            }
        }

        # convert arguments to Variant array
        auto input_args;
        if (request.arguments) {
            if (arg_meta) {
                input_args = ();
                for (int i = 0; i < request.arguments.size(); ++i) {
                    *string data_type = OpcUaTypeConversion::normalizeDataTypeHint(arg_meta.input[i].data_type);
                    input_args += OpcUaTypeConversion::getVariant(request.arguments[i], data_type);
                }
            } else {
                input_args = map new Variant($1), request.arguments;
            }
        }

        # Milo 1.1.x: call() takes a list of requests and returns a CallResponse
        ArrayList call_requests();
        call_requests.add(new CallMethodRequest(object_id, method_id, input_args));
        auto response = awaitFuture(client.callAsync(call_requests), "call method");
        auto result = response.getResults()[0];

        auto status = result.getStatusCode();
        list<auto> output_args = ();
        auto outputs = result.getOutputArguments();
        if (outputs) {
            foreach auto out_val in (outputs) {
                output_args += convertValue(out_val);
            }
        }

        return {
            "output_arguments": output_args,
        } + (arg_meta ? {
            "input_argument_metadata": arg_meta.input,
            "output_argument_metadata": arg_meta.output,
        } : {}) + statusFields(status);
    }

    #! Resolves the input/output argument metadata for an OPC UA method (best-effort, cached)
    /** Browses the method node for its \c InputArguments and \c OutputArguments property nodes and
        decodes their \c Argument values. Returns @ref nothing on any failure so the caller can proceed
        without argument validation.

        @param method_id the method node id

        @return a hash with \c "input" and \c "output" lists of argument metadata, or @ref nothing
    */
    private *hash<auto> resolveMethodArguments(NodeId method_id) {
        string cache_key = "method:" + method_id.toParseableString();
        *hash<auto> cached = getCachedMetadata(cache_key);
        if (cached) {
            return cached;
        }
        hash<auto> resolved;
        try {
            # browse the method node for its HasProperty children
            BrowseDescription bd(method_id, BrowseDirection::Forward, NodeIds::HasProperty, True,
                UInteger::valueOf(0), UInteger::valueOf(63));
            ArrayList bds();
            bds.add(bd);
            auto resp = awaitFuture(client.browseAsync(new ViewDescription(NodeId::NULL_VALUE, DateTime::MIN_VALUE, UInteger::valueOf(0)),
                UInteger::valueOf(0), bds), "browse method properties");
            auto refs = resp.getResults()[0].getReferences();
            # browse results return ExpandedNodeIds, which may use the `nsu=` (namespace URI) form that
            # NodeId::parse() cannot parse; resolve them to absolute NodeIds via the namespace table
            auto namespace_table = client.getNamespaceTable();
            *string input_id;
            *string output_id;
            if (refs) {
                foreach auto ref in (refs) {
                    *string node_id_str = expandedNodeIdToString(ref.getNodeId(), namespace_table);
                    if (!node_id_str) {
                        continue;
                    }
                    switch (ref.getBrowseName().getName()) {
                        case "InputArguments": input_id = node_id_str; break;
                        case "OutputArguments": output_id = node_id_str; break;
                    }
                }
            }
            resolved = {
                "input": input_id ? readArgumentArray(input_id) : (),
                "output": output_id ? readArgumentArray(output_id) : (),
            };
        } catch (hash<ExceptionInfo> ex) {
            # best-effort: resolution failures must not break the method call
            debug("OPC UA: could not resolve method arguments: %s: %s", ex.err, ex.desc);
            return NOTHING;
        }
        putCachedMetadata(cache_key, resolved);
        return resolved;
    }

    #! Reads and decodes an OPC UA \c Argument[] property value into a list of argument metadata hashes
    private list<hash<auto>> readArgumentArray(string node_id_str) {
        NodeId nid = NodeId::parse(node_id_str);
        ArrayList arg_nodes();
        arg_nodes.add(nid);
        auto values = awaitFuture(client.readValuesAsync(0.0, TimestampsToReturn::Neither, arg_nodes),
            "read method arguments");
        auto dv = values[0];
        auto variant = dv.getValue();
        if (!variant) {
            return ();
        }
        auto arr = variant.getValue();
        if (!arr) {
            return ();
        }
        auto ctx = client.getStaticEncodingContext();
        list<hash<auto>> rv = ();
        foreach auto eo in (arr) {
            Argument arg = eo.decode(ctx);
            auto arg_desc = arg.getDescription();
            auto arg_dt = arg.getDataType();
            rv += {
                "name": arg.getName(),
                "description": arg_desc ? arg_desc.getText() : NOTHING,
                "data_type": arg_dt ? arg_dt.toParseableString() : NOTHING,
                "value_rank": int(arg.getValueRank()),
            };
        }
        return rv;
    }

    #! Reads values from multiple OPC UA nodes in a single request
    private hash<auto> doReadValues(auto request) {
        if (!request.node_ids || !request.node_ids.size()) {
            throw "OPCUA-REQUEST-ERROR", "`node_ids` is required for the `read-values` action";
        }
        ArrayList read_value_ids();
        foreach string node_id_str in (request.node_ids) {
            read_value_ids.add(new ReadValueId(
                NodeId::parse(node_id_str),
                AttributeId::Value.uid(),
                NOTHING,
                QualifiedName::NULL_VALUE
            ));
        }
        auto response = awaitFuture(
            client.readAsync(0.0, TimestampsToReturn::Both, read_value_ids), "read");
        auto results_list = response.getResults();

        list<hash<auto>> results = ();
        for (int i = 0; i < results_list.size(); ++i) {
            auto dv = results_list[i];
            auto status = dv.getStatusCode();
            results += {
                "node_id": request.node_ids[i],
                "value": convertValue(dv.getValue()),
                "source_timestamp": convertDateTime(dv.getSourceTime()),
                "server_timestamp": convertDateTime(dv.getServerTime()),
            } + statusFields(status);
        }
        return {"results": results};
    }

    #! Reads node attributes from an OPC UA node
    /** The live \c value attribute is always read fresh. The static node metadata (display name, data
        type, value rank, array dimensions, access levels, etc.) is resolved through the NodeId-keyed
        metadata cache: on a cache hit only the live value is read; on a miss the full attribute set is
        read and the metadata is cached. The cache is invalidated on reconnect/session recreation.

        @note The \c DataTypeDefinition attribute is not exposed by the bundled OPC UA stack and is
        therefore omitted here; it becomes available with the Milo upgrade.
    */
    private hash<auto> doReadAttributes(auto request) {
        if (!request.node_id) {
            throw "OPCUA-REQUEST-ERROR", "`node_id` is required for the `read-attributes` action";
        }
        string node_id_str = request.node_id;
        NodeId node_id = NodeId::parse(node_id_str);

        *hash<auto> metadata = getCachedMetadata(node_id_str);
        if (metadata) {
            # cache hit: read only the live value attribute; metadata served from cache
            ArrayList value_ids();
            value_ids.add(new ReadValueId(node_id, AttributeId::Value.uid(), NOTHING,
                QualifiedName::NULL_VALUE));
            auto value_response = awaitFuture(
                client.readAsync(0.0, TimestampsToReturn::Both, value_ids), "read");
            auto value_results = value_response.getResults();
            auto value_status = value_results[0].getStatusCode();
            return {"node_id": node_id_str, "value": convertValue(value_results[0].getValue())}
                + metadata + statusFields(value_status);
        }

        # cache miss: read the live value plus all metadata attributes in a single request
        ArrayList read_value_ids();
        list<auto> attribute_ids = (
            AttributeId::Value,                     # 0
            AttributeId::DisplayName,               # 1
            AttributeId::Description,               # 2
            AttributeId::NodeClass,                 # 3
            AttributeId::DataType,                  # 4
            AttributeId::AccessLevel,               # 5
            AttributeId::UserAccessLevel,           # 6
            AttributeId::Historizing,               # 7
            AttributeId::ValueRank,                 # 8
            AttributeId::ArrayDimensions,           # 9
            AttributeId::MinimumSamplingInterval,   # 10
        );
        foreach auto attr_id in (attribute_ids) {
            read_value_ids.add(new ReadValueId(node_id, attr_id.uid(), NOTHING,
                QualifiedName::NULL_VALUE));
        }

        auto response = awaitFuture(
            client.readAsync(0.0, TimestampsToReturn::Both, read_value_ids), "read");
        auto results = response.getResults();

        # the overall status is from the Value attribute read
        auto status = results[0].getStatusCode();

        # display name / description are LocalizedText
        auto dn = attrValue(results[1]);
        *string display_name = dn ? dn.getText() : NOTHING;
        auto de = attrValue(results[2]);
        *string desc_text = de ? de.getText() : NOTHING;

        # node class is an enum; data type is a NodeId
        auto nc = attrValue(results[3]);
        *string node_class = nc ? nc.toString() : NOTHING;
        auto dt = attrValue(results[4]);
        *string data_type = dt ? dt.toParseableString() : NOTHING;

        # access level / user access level are unsigned byte bitmasks (bit 1 = CurrentWrite)
        auto al = attrValue(results[5]);
        *int access_level = al ? int(al.intValue()) : NOTHING;
        *bool writable = exists access_level ? boolean(access_level & 2) : NOTHING;
        auto ual = attrValue(results[6]);
        *int user_access_level = ual ? int(ual.intValue()) : NOTHING;
        *bool user_writable = exists user_access_level ? boolean(user_access_level & 2) : NOTHING;

        # historizing flag
        auto hist = attrValue(results[7]);
        *bool historizing = exists hist ? boolean(hist) : NOTHING;

        # value rank is a signed Int32 (-3..-1 special, >=0 fixed rank)
        auto vr = attrValue(results[8]);
        *int value_rank = exists vr ? int(vr) : NOTHING;

        # array dimensions is an array of UInt32 values
        *list<int> array_dimensions;
        auto ad = attrValue(results[9]);
        if (ad) {
            array_dimensions = map int($1.longValue()), ad;
        }

        # minimum sampling interval is a Double (milliseconds)
        auto msi = attrValue(results[10]);
        *float minimum_sampling_interval = exists msi ? float(msi) : NOTHING;

        metadata = {
            "display_name": display_name,
            "description": desc_text,
            "node_class": node_class,
            "data_type": data_type,
            "value_rank": value_rank,
            "array_dimensions": array_dimensions,
            "access_level": access_level,
            "user_access_level": user_access_level,
            "writable": writable,
            "user_writable": user_writable,
            "historizing": historizing,
            "minimum_sampling_interval": minimum_sampling_interval,
        };
        putCachedMetadata(node_id_str, metadata);

        return {"node_id": node_id_str, "value": convertValue(results[0].getValue())}
            + metadata + statusFields(status);
    }

    #! Reads historical data from an OPC UA node
    private hash<auto> doReadHistory(auto request) {
        if (!request.node_id) {
            throw "OPCUA-REQUEST-ERROR", "`node_id` is required for the `read-history` action";
        }
        NodeId node_id = NodeId::parse(request.node_id);

        # build read details using Milo DateTime
        DateTime start_date = request.start_time
            ? new DateTime(int(get_epoch_seconds(request.start_time)) * 1000)
            : new DateTime(0);
        DateTime end_date = request.end_time
            ? new DateTime(int(get_epoch_seconds(request.end_time)) * 1000)
            : DateTime::now();
        int max_values = int(request.max_values ?? 0);

        # Milo 1.1.x: ReadRawModifiedDetails(isReadModified, startTime, endTime, numValuesPerNode,
        # returnBounds)
        auto details = new ReadRawModifiedDetails(
            False,
            start_date,
            end_date,
            UInteger::valueOf(max_values),
            True);

        ArrayList hrvids();
        hrvids.add(new HistoryReadValueId(node_id, NOTHING, QualifiedName::NULL_VALUE, NOTHING));

        auto response = awaitFuture(
            client.historyReadAsync(details, TimestampsToReturn::Both, False, hrvids),
            "read history");
        auto results = response.getResults();
        auto result = results[0];
        auto status = result.getStatusCode();

        list<hash<auto>> data_values = ();
        auto history_data = result.getHistoryData().decode(client.getStaticEncodingContext());
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
        } + statusFields(status);
    }

    #! Discovers available endpoints on the OPC UA server
    private hash<auto> doDiscoverEndpoints(auto request) {
        string discover_url = request.node_id ?? url;
        auto endpoints = awaitFuture(
            DiscoveryClient::getEndpoints(discover_url), "discover endpoints");

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

        auto response = awaitFuture(
            client.readAsync(0.0, TimestampsToReturn::Both, read_value_ids), "read");
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
        } + statusFields(status);
    }

    #! Ensures the client is connected; reconnects on failure if auto_reconnect is enabled
    private ensureConnected() {
        if (!url) {
            throw "OPCUA-REQUEST-ERROR", "this OPC UA provider was created without a `url`; it is configured "
                "for schema/API-management use only and cannot perform client operations";
        }
        if (!client) {
            client = getClient(copts, logger);
            awaitFuture(client.connectAsync(), "connect");
            debug("OPC UA: Connected to %y", url);
        }
    }

    #! Reconnects the client after a failed operation
    /** Only called when auto_reconnect is enabled and an operation has failed
        with a connection-related error.
    */
    private reconnect() {
        debug("OPC UA: Connection lost, reconnecting to %y", url);
        # the session is being recreated; cached node metadata may be stale, so invalidate it
        clearSchemaCache();
        try {
            client.disconnectAsync().get();
        } catch () {
            # ignore disconnect errors
        }
        delete client;
        client = getClient(copts, logger);
        awaitFuture(client.connectAsync(), "reconnect");
        debug("OPC UA: Reconnected to %y", url);
    }
}
}
