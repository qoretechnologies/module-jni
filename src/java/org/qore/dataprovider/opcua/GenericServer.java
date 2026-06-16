/*  GenericServer.java Copyright 2026 Qore Technologies, s.r.o.

    Generic, Qorus-agnostic OPC UA server runtime (Epic B1).

    Builds a Milo 1.1.4 server address space from the schema snapshot contract used by the
    OPC UA client provider. Runtime behavior is driven by an optional Qore callback dispatcher;
    the Java side remains generic and contains no Qorus references.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.qore.jni.Hash;
import org.qore.jni.QoreClosure;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.BrowseContext;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.CallContext;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.HistoryReadContext;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.ReadContext;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.ReferenceResult;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.WriteContext;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryData;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;

/** Generic Milo OPC UA server materialized from a Qore OPC UA schema snapshot. */
public class GenericServer {
    /** Default custom namespace URI used when the schema does not carry one. */
    public static final String DEFAULT_NAMESPACE_URI = "urn:qore:opcua:server";

    /** Default per-node cap for implicit in-memory history values. */
    public static final int DEFAULT_MAX_HISTORY_VALUES = 1024;

    private static final String UA_NAMESPACE = "http://opcfoundation.org/UA/";

    private final Map<String, Object> options;
    private final QoreClosure callback;
    private final String bindAddress;
    private final String hostname;
    private final int port;
    private final String path;
    private final String namespaceUri;
    private final int maxHistoryValues;
    private final OpcUaServer server;
    private final RuntimeNamespace namespace;
    private final Hash schemaSnapshot = new Hash();
    private volatile boolean started;

    /** Creates a server without a runtime callback dispatcher. */
    public GenericServer(Map<String, Object> options) throws Exception {
        this(options, null);
    }

    /** Creates a server with an optional Qore callback dispatcher. */
    public GenericServer(Map<String, Object> options, QoreClosure callback) throws Exception {
        this.options = options != null ? new LinkedHashMap<>(options) : Collections.emptyMap();
        this.callback = callback;
        this.bindAddress = stringOption("bind_address", "127.0.0.1");
        this.hostname = stringOption("hostname", bindAddress);
        this.port = intOption("port", 48400);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.path = normalizePath(stringOption("path", "/qore"));
        this.namespaceUri = resolveNamespaceUri(this.options);
        this.maxHistoryValues = intOption("max_history_values", DEFAULT_MAX_HISTORY_VALUES);
        if (maxHistoryValues < 0) {
            throw new IllegalArgumentException("max_history_values must be greater than or equal to 0");
        }
        validateLoopbackOnlyEndpoint();

        MemoryCertificateQuarantine quarantine = new MemoryCertificateQuarantine();
        DefaultCertificateManager certificateManager = new DefaultCertificateManager(quarantine);

        EndpointConfig endpoint = EndpointConfig.newBuilder()
            .setBindAddress(bindAddress)
            .setBindPort(port)
            .setHostname(hostname)
            .setPath(path)
            .setSecurityPolicy(SecurityPolicy.None)
            .setSecurityMode(MessageSecurityMode.None)
            .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
            .build();

        OpcUaServerConfig config = OpcUaServerConfig.builder()
            .setApplicationName(LocalizedText.english(stringOption("application_name", "Qore OPC UA Server")))
            .setApplicationUri(stringOption("application_uri", "urn:qore:opcua:server"))
            .setProductUri(stringOption("product_uri", "urn:qore:opcua:server"))
            .setEndpoints(Set.of(endpoint))
            .setCertificateManager(certificateManager)
            .setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
            .build();

        this.server = new OpcUaServer(config, transportProfile ->
            new OpcTcpServerTransport(OpcTcpServerTransportConfig.newBuilder().build()));
        this.namespace = new RuntimeNamespace(server);
        this.namespace.startup();
    }

    /** Starts the server and blocks until the endpoint is listening. */
    public synchronized void start() throws Exception {
        if (!started) {
            server.startup().get();
            started = true;
        }
    }

    /** Stops the server and blocks until shutdown is complete. */
    public synchronized void stop() throws Exception {
        if (started) {
            server.shutdown().get();
            started = false;
        }
    }

    /** Returns the configured endpoint URL. */
    public String getEndpointUrl() {
        return "opc.tcp://" + formatHostForUrl(hostname) + ":" + port + path;
    }

    /** Wraps unbracketed IPv6 literals in {@code [...]} so the host is valid in a URL. */
    private static String formatHostForUrl(String host) {
        if (host.indexOf(':') >= 0 && host.indexOf('[') < 0) {
            return "[" + host + "]";
        }
        return host;
    }

    /** Returns the local address used for the server socket bind. */
    public String getBindAddress() {
        return bindAddress;
    }

    /** Returns the TCP port used for the server socket bind. */
    public int getPort() {
        return port;
    }

    /** Returns the materialized schema snapshot with actual server NodeIds. */
    public Hash getSchemaSnapshot() {
        synchronized (schemaSnapshot) {
            return deepCopyHash(schemaSnapshot);
        }
    }

    /** Returns one endpoint by stable endpoint id, or null if it does not exist. */
    public Hash getEndpoint(String endpointId) {
        Endpoint endpoint = namespace.byEndpointId.get(endpointId);
        return endpoint != null ? deepCopyHash(endpoint.snapshot) : null;
    }

    /** Sets a variable endpoint value and notifies monitored items. */
    public void setValue(String endpointId, Object value) throws Exception {
        namespace.setEndpointValue(endpointId, value, true);
    }

    /** Alias for setValue() used by embedders to publish a data change explicitly. */
    public void publishDataChange(String endpointId, Object value) throws Exception {
        setValue(endpointId, value);
    }

    /** Dispatches an application event to the runtime callback. */
    public Hash publishEvent(Map<String, Object> event) throws Throwable {
        Hash info = new Hash();
        info.put("event", event != null ? new Hash(event) : new Hash());
        Hash rv = invokeCallback("event", info);
        return rv != null ? rv : new Hash();
    }

    /** Exports the materialized variable/method endpoints to a small NodeSet2 subset. */
    public String exportNodeSet2() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<UANodeSet xmlns=\"http://opcfoundation.org/UA/2011/03/UANodeSet.xsd\">\n");
        xml.append("  <NamespaceUris>\n");
        xml.append("    <Uri>").append(xmlEscape(namespaceUri)).append("</Uri>\n");
        xml.append("  </NamespaceUris>\n");
        for (Endpoint endpoint : namespace.orderedEndpoints) {
            String tag = endpoint.method ? "UAMethod" : "UAVariable";
            xml.append("  <").append(tag)
                .append(" NodeId=\"").append(xmlEscape(exportNodeId(endpoint.nodeId))).append("\"")
                .append(" BrowseName=\"1:").append(xmlEscape(endpoint.localName)).append("\"");
            if (!endpoint.method) {
                xml.append(" DataType=\"").append(xmlEscape(exportDataType(endpoint.dataType))).append("\"")
                    .append(" ValueRank=\"").append(endpoint.valueRank).append("\"");
            }
            xml.append(">\n");
            xml.append("    <DisplayName>").append(xmlEscape(endpoint.displayName)).append("</DisplayName>\n");
            xml.append("  </").append(tag).append(">\n");
        }
        xml.append("</UANodeSet>\n");
        return xml.toString();
    }

    private void validateLoopbackOnlyEndpoint() {
        if (!isLoopbackAddress(bindAddress) || !isLoopbackAddress(hostname)) {
            throw new IllegalArgumentException("GenericServer uses anonymous SecurityPolicy.None endpoints and "
                + "therefore accepts only loopback bind_address and hostname values; got bind_address="
                + bindAddress + ", hostname=" + hostname);
        }
    }

    private static boolean isLoopbackAddress(String address) {
        if (address == null) {
            return false;
        }
        String normalized = address.trim().toLowerCase();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized)
            || "::1".equals(normalized)
            || "0:0:0:0:0:0:0:1".equals(normalized)
            || isIpv4LoopbackAddress(normalized);
    }

    private static boolean isIpv4LoopbackAddress(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4 || !"127".equals(parts[0])) {
            return false;
        }
        for (int i = 1; i < parts.length; ++i) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int j = 0; j < part.length(); ++j) {
                if (!Character.isDigit(part.charAt(j))) {
                    return false;
                }
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private String stringOption(String key, String defaultValue) {
        Object value = options.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int intOption(String key, int defaultValue) {
        Object value = options.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String resolveNamespaceUri(Map<String, Object> options) {
        Object value = options.get("namespace_uri");
        if (value != null) {
            return String.valueOf(value);
        }
        Map<String, Object> snapshot = mapOption(options, "schema", "schema_snapshot");
        if (snapshot != null) {
            Map<String, Object> namespaces = asMap(snapshot.get("namespaces"));
            if (namespaces != null) {
                for (Object uri : namespaces.values()) {
                    if (uri != null && !UA_NAMESPACE.equals(String.valueOf(uri))) {
                        return String.valueOf(uri);
                    }
                }
            }
        }
        return DEFAULT_NAMESPACE_URI;
    }

    @SafeVarargs
    private static Map<String, Object> mapOption(Map<String, Object> options, String... keys) {
        for (String key : keys) {
            Map<String, Object> map = asMap(options.get(key));
            if (map != null) {
                return map;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static List<Object> asList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            List<Object> rv = new ArrayList<>(array.length);
            Collections.addAll(rv, array);
            return rv;
        }
        return Collections.emptyList();
    }

    private Hash invokeCallback(String operation, Hash info) throws Throwable {
        if (callback == null) {
            return null;
        }
        Object rv = callback.call(operation, info);
        if (rv instanceof Hash) {
            return (Hash) rv;
        }
        if (rv instanceof Map) {
            return new Hash(asMap(rv));
        }
        return null;
    }

    private StatusCode authorize(String operation, Endpoint endpoint) {
        if (callback == null) {
            return StatusCode.GOOD;
        }
        try {
            Hash info = endpoint != null ? endpointInfo(endpoint) : new Hash();
            info.put("requested_operation", operation);
            Hash rv = invokeCallback("authorize", info);
            return statusFromCallback(rv, StatusCode.GOOD);
        } catch (Throwable t) {
            return new StatusCode(StatusCodes.Bad_UserAccessDenied);
        }
    }

    private static StatusCode statusFromCallback(Hash rv, StatusCode defaultStatus) {
        if (rv == null) {
            return defaultStatus;
        }
        if (rv.containsKey("allow") && Boolean.FALSE.equals(rv.get("allow"))) {
            return new StatusCode(StatusCodes.Bad_UserAccessDenied);
        }
        Object code = rv.get("status_code");
        if (code instanceof Number) {
            return new StatusCode(((Number) code).longValue());
        }
        return defaultStatus;
    }

    private static Hash endpointInfo(Endpoint endpoint) {
        Hash info = deepCopyHash(endpoint.snapshot);
        if (!endpoint.method && endpoint.node != null) {
            info.put("value", ValueCodec.readVariant(endpoint.node.getValue().getValue()));
        }
        return info;
    }

    private static Hash deepCopyHash(Map<String, Object> src) {
        Hash rv = new Hash();
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            rv.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return rv;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            return deepCopyHash(asMap(value));
        }
        if (value instanceof List) {
            List<Object> rv = new ArrayList<>();
            for (Object element : (List<?>) value) {
                rv.add(deepCopyValue(element));
            }
            return rv;
        }
        if (value instanceof Object[]) {
            List<Object> rv = new ArrayList<>();
            for (Object element : (Object[]) value) {
                rv.add(deepCopyValue(element));
            }
            return rv;
        }
        return value;
    }

    private static String xmlEscape(Object value) {
        String s = value != null ? String.valueOf(value) : "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static String exportNodeId(NodeId nodeId) {
        Object id = nodeId.getIdentifier();
        if (id instanceof Number) {
            return "ns=1;i=" + ((Number) id).longValue();
        }
        return "ns=1;s=" + String.valueOf(id);
    }

    private static String exportDataType(NodeId dataType) {
        String name = builtinName(dataType);
        return name != null ? name : dataType.toParseableString();
    }

    private final class RuntimeNamespace extends ManagedNamespaceWithLifecycle {
        private final SubscriptionModel subscriptionModel;
        private final List<Endpoint> orderedEndpoints = new ArrayList<>();
        private final Map<String, Endpoint> byEndpointId = new ConcurrentHashMap<>();
        private final Map<NodeId, Endpoint> byNodeId = new ConcurrentHashMap<>();
        private final Map<NodeId, Deque<DataValue>> history = new ConcurrentHashMap<>();

        RuntimeNamespace(OpcUaServer server) {
            super(server, namespaceUri);
            subscriptionModel = new SubscriptionModel(server, this);
            getLifecycleManager().addLifecycle(subscriptionModel);
            getLifecycleManager().addLifecycle(new Lifecycle() {
                @Override public void startup() { addNodes(); }
                @Override public void shutdown() {}
            });
        }

        @Override public List<ReferenceResult> browse(BrowseContext context, ViewDescription view,
                List<NodeId> nodeIds) {
            callbackOnly("browse", null, "node_ids", nodeIds);
            return super.browse(context, view, nodeIds);
        }

        @Override public List<DataValue> read(ReadContext context, Double maxAge,
                TimestampsToReturn timestamps, List<ReadValueId> readValueIds) {
            List<DataValue> rv = new ArrayList<>(readValueIds.size());
            List<ReadValueId> superReads = new ArrayList<>();
            List<Integer> superIndexes = new ArrayList<>();

            for (int i = 0; i < readValueIds.size(); ++i) {
                ReadValueId read = readValueIds.get(i);
                Endpoint endpoint = byNodeId.get(read.getNodeId());
                if (endpoint != null
                        && read.getAttributeId().intValue() == AttributeId.Value.uid().intValue()) {
                    StatusCode auth = authorize("read", endpoint);
                    if (auth.isBad()) {
                        rv.add(new DataValue(auth));
                        continue;
                    }
                    try {
                        Hash info = endpointInfo(endpoint);
                        Hash cb = invokeCallback("read", info);
                        StatusCode status = statusFromCallback(cb, StatusCode.GOOD);
                        if (status.isBad()) {
                            rv.add(new DataValue(status));
                            continue;
                        }
                        if (cb != null && cb.containsKey("value")) {
                            setEndpointValue(endpoint, cb.get("value"), false);
                        }
                    } catch (Throwable t) {
                        rv.add(new DataValue(new StatusCode(StatusCodes.Bad_InternalError)));
                        continue;
                    }
                }
                rv.add(null);
                superReads.add(read);
                superIndexes.add(i);
            }

            if (!superReads.isEmpty()) {
                List<DataValue> values = super.read(context, maxAge, timestamps, superReads);
                for (int i = 0; i < values.size(); ++i) {
                    rv.set(superIndexes.get(i), values.get(i));
                }
            }
            return rv;
        }

        @Override public List<StatusCode> write(WriteContext context, List<WriteValue> writeValues) {
            List<WriteValue> effectiveWrites = new ArrayList<>(writeValues.size());
            List<Integer> superIndexes = new ArrayList<>();
            List<StatusCode> rv = new ArrayList<>(Collections.nCopies(writeValues.size(), null));

            for (int i = 0; i < writeValues.size(); ++i) {
                WriteValue write = writeValues.get(i);
                Endpoint endpoint = byNodeId.get(write.getNodeId());
                if (endpoint != null
                        && write.getAttributeId().intValue() == AttributeId.Value.uid().intValue()) {
                    StatusCode auth = authorize("write", endpoint);
                    if (auth.isBad()) {
                        rv.set(i, auth);
                        continue;
                    }
                    try {
                        Object requested = ValueCodec.readVariant(write.getValue().getValue());
                        Hash info = endpointInfo(endpoint);
                        info.put("value", requested);
                        Hash cb = invokeCallback("write", info);
                        StatusCode status = statusFromCallback(cb, StatusCode.GOOD);
                        if (status.isBad()) {
                            rv.set(i, status);
                            continue;
                        }
                        if (cb != null && cb.containsKey("value")) {
                            Variant variant = variantFor(endpoint, cb.get("value"));
                            write = new WriteValue(write.getNodeId(), write.getAttributeId(), write.getIndexRange(),
                                new DataValue(variant));
                        }
                    } catch (Throwable t) {
                        rv.set(i, new StatusCode(StatusCodes.Bad_InternalError));
                        continue;
                    }
                }
                effectiveWrites.add(write);
                superIndexes.add(i);
            }

            if (!effectiveWrites.isEmpty()) {
                List<StatusCode> statuses = super.write(context, effectiveWrites);
                for (int i = 0; i < statuses.size(); ++i) {
                    StatusCode status = statuses.get(i);
                    int originalIndex = superIndexes.get(i);
                    rv.set(originalIndex, status);
                    if (status.isGood()) {
                        Endpoint endpoint = byNodeId.get(effectiveWrites.get(i).getNodeId());
                        if (endpoint != null) {
                            addHistory(endpoint, endpoint.node.getValue());
                        }
                    }
                }
            }
            return rv;
        }

        @Override public List<CallMethodResult> call(CallContext context, List<CallMethodRequest> requests) {
            callbackOnly("call-service", null, "count", requests.size());
            return super.call(context, requests);
        }

        @Override public List<HistoryReadResult> historyRead(HistoryReadContext context,
                HistoryReadDetails details, TimestampsToReturn timestamps,
                List<HistoryReadValueId> nodesToRead) {
            EncodingContext encodingContext = getServer().getStaticEncodingContext();
            List<HistoryReadResult> rv = new ArrayList<>();
            for (HistoryReadValueId id : nodesToRead) {
                Endpoint endpoint = byNodeId.get(id.getNodeId());
                if (endpoint == null) {
                    rv.add(new HistoryReadResult(new StatusCode(StatusCodes.Bad_NodeIdUnknown),
                        ByteString.NULL_VALUE, null));
                    continue;
                }
                StatusCode auth = authorize("history-read", endpoint);
                if (auth.isBad()) {
                    rv.add(new HistoryReadResult(auth, ByteString.NULL_VALUE, null));
                    continue;
                }
                try {
                    List<DataValue> values = historySnapshot(id.getNodeId());
                    Hash cb = invokeCallback("history-read", endpointInfo(endpoint));
                    StatusCode status = statusFromCallback(cb, StatusCode.GOOD);
                    if (status.isBad()) {
                        rv.add(new HistoryReadResult(status, ByteString.NULL_VALUE, null));
                        continue;
                    }
                    if (cb != null && cb.containsKey("values")) {
                        values = new ArrayList<>();
                        for (Object value : asList(cb.get("values"))) {
                            values.add(new DataValue(variantFor(endpoint, value), StatusCode.GOOD,
                                new DateTime(Instant.now())));
                        }
                    }
                    ExtensionObject encoded = ExtensionObject.encode(encodingContext,
                        new HistoryData(values.toArray(new DataValue[0])));
                    rv.add(new HistoryReadResult(StatusCode.GOOD, ByteString.NULL_VALUE, encoded));
                } catch (Throwable t) {
                    rv.add(new HistoryReadResult(new StatusCode(StatusCodes.Bad_InternalError),
                        ByteString.NULL_VALUE, null));
                }
            }
            return rv;
        }

        @Override public void onDataItemsCreated(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsCreated(dataItems);
            callbackOnly("subscription", null, "event", "created", "count", dataItems.size());
        }

        @Override public void onDataItemsModified(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsModified(dataItems);
            callbackOnly("subscription", null, "event", "modified", "count", dataItems.size());
        }

        @Override public void onDataItemsDeleted(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsDeleted(dataItems);
            callbackOnly("subscription", null, "event", "deleted", "count", dataItems.size());
        }

        @Override public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
            subscriptionModel.onMonitoringModeChanged(monitoredItems);
            callbackOnly("subscription", null, "event", "mode-changed", "count", monitoredItems.size());
        }

        void setEndpointValue(String endpointId, Object value, boolean invokeEventCallback)
                throws Exception {
            Endpoint endpoint = byEndpointId.get(endpointId);
            if (endpoint == null) {
                throw new IllegalArgumentException("unknown OPC UA endpoint id: " + endpointId);
            }
            setEndpointValue(endpoint, value, invokeEventCallback);
        }

        private void setEndpointValue(Endpoint endpoint, Object value, boolean invokeEventCallback)
                throws Exception {
            if (endpoint.method || endpoint.node == null) {
                throw new IllegalArgumentException("OPC UA endpoint id " + endpoint.endpointId
                    + " is a method endpoint and has no variable value to set");
            }
            endpoint.node.setValue(new DataValue(variantFor(endpoint, value), StatusCode.GOOD,
                new DateTime(Instant.now())));
            addHistory(endpoint, endpoint.node.getValue());
            if (invokeEventCallback) {
                callbackOnly("data-change", endpoint, "value", value);
            }
        }

        private void addNodes() {
            int idx = getNamespaceIndex().intValue();
            Map<String, UaFolderNode> folders = new HashMap<>();
            List<Object> endpointSnapshots = new ArrayList<>();

            Map<String, Object> snapshot = mapOption(options, "schema", "schema_snapshot");
            RootConfig rootConfig = rootConfigFor(idx, snapshot);
            List<Object> endpointSpecs = snapshot != null ? asList(snapshot.get("endpoints"))
                : asList(options.get("endpoints"));
            if (endpointSpecs.isEmpty()) {
                throw new IllegalArgumentException("GenericServer requires schema.endpoints or endpoints");
            }

            for (Object specObj : endpointSpecs) {
                Map<String, Object> spec = asMap(specObj);
                if (spec == null) {
                    continue;
                }
                try {
                    Endpoint endpoint = buildEndpoint(idx, folders, rootConfig, spec);
                    orderedEndpoints.add(endpoint);
                    byEndpointId.put(endpoint.endpointId, endpoint);
                    if (!endpoint.method) {
                        byNodeId.put(endpoint.nodeId, endpoint);
                        addHistory(endpoint, endpoint.node.getValue());
                    }
                    endpointSnapshots.add(endpoint.snapshot);
                } catch (Exception e) {
                    throw new IllegalArgumentException("error materializing OPC UA endpoint " + spec, e);
                }
            }

            Hash namespaces = new Hash();
            namespaces.put("0", UA_NAMESPACE);
            namespaces.put(String.valueOf(idx), namespaceUri);

            synchronized (schemaSnapshot) {
                schemaSnapshot.clear();
                schemaSnapshot.put("contract_version", SchemaResolver.CONTRACT_VERSION);
                schemaSnapshot.put("source", "server");
                schemaSnapshot.put("namespaces", namespaces);
                if (rootConfig != null) {
                    schemaSnapshot.put("root_node_id", rootConfig.materializedNodeId.toParseableString());
                    schemaSnapshot.put("root_browse_path", rootConfig.materializedBrowsePath);
                }
                schemaSnapshot.put("endpoints", endpointSnapshots);
            }
        }

        private Endpoint buildEndpoint(int idx, Map<String, UaFolderNode> folders,
                RootConfig rootConfig, Map<String, Object> spec) throws Exception {
            String kind = stringValue(spec.get("kind"), "variable");
            boolean method = kind.startsWith("method");
            String localName = endpointLocalName(spec);
            String parentPath = parentPath(spec);
            UaFolderNode parent = parentPath.isEmpty() ? null : folderFor(idx, folders, rootConfig, parentPath);
            String actualBrowsePath = actualBrowsePath(idx, parentPath, localName);
            String endpointId = stringValue(spec.get("endpoint_id"), null);
            if (endpointId == null || endpointId.isEmpty()) {
                endpointId = SchemaResolver.deriveEndpointId(namespaceUri, actualBrowsePath,
                    method ? "method-call" : "variable");
            }
            NodeId nodeId = nodeIdFor(idx, spec, localName, endpointId, method);
            String displayName = stringValue(spec.get("display_name"), localName);

            Hash endpointSnapshot = new Hash();
            endpointSnapshot.put("endpoint_id", endpointId);
            endpointSnapshot.put("node_id", nodeId.toParseableString());
            endpointSnapshot.put("browse_name", idx + ":" + localName);
            endpointSnapshot.put("browse_path", actualBrowsePath);
            endpointSnapshot.put("namespace_uri", namespaceUri);
            endpointSnapshot.put("kind", method ? "method-call" : "variable");
            endpointSnapshot.put("node_class", method ? "Method" : "Variable");
            endpointSnapshot.put("display_name", displayName);

            if (method) {
                UaMethodNode node = UaMethodNode.builder(getNodeContext())
                    .setNodeId(nodeId)
                    .setBrowseName(new QualifiedName(idx, localName))
                    .setDisplayName(LocalizedText.english(displayName))
                    .build();
                Endpoint endpoint = new Endpoint(endpointId, nodeId, localName, displayName, true,
                    null, -1, null, false, null, endpointSnapshot);
                MethodHandler handler = new MethodHandler(node, endpoint, spec);
                node.setInputArguments(handler.getInputArguments());
                node.setOutputArguments(handler.getOutputArguments());
                node.setInvocationHandler(handler);
                getNodeManager().addNode(node);
                if (parent != null) {
                    parent.addReference(new Reference(parent.getNodeId(), NodeIds.HasComponent, nodeId.expanded(),
                        true));
                    endpointSnapshot.put("object_node_id", parent.getNodeId().toParseableString());
                } else {
                    node.addReference(new Reference(nodeId, NodeIds.HasComponent, NodeIds.ObjectsFolder.expanded(),
                        false));
                    endpointSnapshot.put("object_node_id", NodeIds.ObjectsFolder.toParseableString());
                }
                endpointSnapshot.put("input_arguments", argumentsToSnapshot(handler.getInputArguments()));
                endpointSnapshot.put("output_arguments", argumentsToSnapshot(handler.getOutputArguments()));
                return endpoint;
            }

            NodeId dataType = dataTypeNodeId(spec.get("data_type"));
            int valueRank = intValue(spec.get("value_rank"), -1);
            List<Object> declaredDirections = asList(spec.get("directions"));
            boolean writable = boolValue(spec.get("writable"), declaredDirections.contains("write"));
            boolean historizing = boolValue(spec.get("historizing"), declaredDirections.contains("history-read"));
            int accessLevel = writable ? 3 : 1;
            if (historizing) {
                accessLevel |= 4;
            }
            Object initialValue = spec.containsKey("value") ? spec.get("value")
                : spec.containsKey("default_value") ? spec.get("default_value") : defaultValue(dataType);

            UaVariableNode node = UaVariableNode.builder(getNodeContext())
                .setNodeId(nodeId)
                .setBrowseName(new QualifiedName(idx, localName))
                .setDisplayName(LocalizedText.english(displayName))
                .setDataType(dataType)
                .setTypeDefinition(NodeIds.BaseDataVariableType)
                .build();
            node.setValueRank(valueRank);
            node.setAccessLevel(UByte.valueOf(accessLevel));
            node.setUserAccessLevel(UByte.valueOf(accessLevel));
            node.setHistorizing((accessLevel & 4) != 0);
            node.setValue(new DataValue(variantFor(dataType, initialValue), StatusCode.GOOD,
                new DateTime(Instant.now())));
            getNodeManager().addNode(node);
            if (parent != null) {
                parent.addOrganizes(node);
            } else {
                node.addReference(new Reference(nodeId, NodeIds.Organizes, NodeIds.ObjectsFolder.expanded(),
                    false));
            }

            List<Object> directions = new ArrayList<>();
            directions.add("read");
            directions.add("observe");
            if (writable) {
                directions.add("write");
            }
            if (historizing) {
                directions.add("history-read");
            }
            endpointSnapshot.put("data_type", dataType.toParseableString());
            endpointSnapshot.put("value_rank", valueRank);
            endpointSnapshot.put("array_dimensions", null);
            endpointSnapshot.put("access_level", accessLevel);
            endpointSnapshot.put("user_access_level", accessLevel);
            endpointSnapshot.put("readable", true);
            endpointSnapshot.put("writable", writable);
            endpointSnapshot.put("user_writable", writable);
            endpointSnapshot.put("directions", directions);
            endpointSnapshot.put("historizing", historizing);
            endpointSnapshot.put("minimum_sampling_interval", 0.0);

            return new Endpoint(endpointId, nodeId, localName, displayName, false, dataType, valueRank,
                builtinName(dataType), historizing, node, endpointSnapshot);
        }

        private UaFolderNode folderFor(int idx, Map<String, UaFolderNode> folders, RootConfig rootConfig,
                String path) {
            UaFolderNode existing = folders.get(path);
            if (existing != null) {
                return existing;
            }

            String parentPath = "";
            String local = path;
            int slash = path.lastIndexOf('/');
            if (slash >= 0) {
                parentPath = path.substring(0, slash);
                local = path.substring(slash + 1);
            }
            UaFolderNode parent = parentPath.isEmpty() ? null : folderFor(idx, folders, rootConfig, parentPath);
            NodeId folderNodeId = rootConfig != null && path.equals(rootConfig.localPath)
                ? rootConfig.materializedNodeId : new NodeId(idx, "folder:" + path);
            UaFolderNode folder = new UaFolderNode(getNodeContext(),
                folderNodeId,
                new QualifiedName(idx, local),
                LocalizedText.english(local));
            getNodeManager().addNode(folder);
            if (parentPath.isEmpty()) {
                folder.addReference(new Reference(folder.getNodeId(), NodeIds.Organizes,
                    NodeIds.ObjectsFolder.expanded(), false));
            } else {
                parent.addOrganizes(folder);
            }
            folders.put(path, folder);
            return folder;
        }
    }

    private final class MethodHandler extends AbstractMethodInvocationHandler {
        private final Endpoint endpoint;
        private final Argument[] inputArguments;
        private final Argument[] outputArguments;

        MethodHandler(UaMethodNode node, Endpoint endpoint, Map<String, Object> spec) {
            super(node);
            this.endpoint = endpoint;
            this.inputArguments = argumentsFromSpec(spec.get("input_arguments"));
            this.outputArguments = argumentsFromSpec(spec.get("output_arguments"));
        }

        @Override public Argument[] getInputArguments() {
            return inputArguments;
        }

        @Override public Argument[] getOutputArguments() {
            return outputArguments;
        }

        @Override protected Variant[] invoke(InvocationContext context, Variant[] inputs) throws UaException {
            StatusCode auth = authorize("call", endpoint);
            if (auth.isBad()) {
                throw new UaException(auth);
            }
            try {
                Hash info = endpointInfo(endpoint);
                List<Object> args = new ArrayList<>();
                for (Variant input : inputs) {
                    args.add(ValueCodec.readVariant(input));
                }
                info.put("arguments", args);
                Hash rv = invokeCallback("call", info);
                StatusCode status = statusFromCallback(rv, StatusCode.GOOD);
                if (status.isBad()) {
                    throw new UaException(status);
                }
                List<Object> outputs = rv != null && rv.containsKey("output_arguments")
                    ? asList(rv.get("output_arguments")) : Collections.emptyList();
                Variant[] variants = new Variant[outputArguments.length];
                for (int i = 0; i < variants.length; ++i) {
                    Object value = i < outputs.size() ? outputs.get(i) : null;
                    variants[i] = variantFor(outputArguments[i].getDataType(), value);
                }
                return variants;
            } catch (UaException e) {
                throw e;
            } catch (Throwable t) {
                throw new UaException(StatusCodes.Bad_InternalError, t);
            }
        }
    }

    private static final class Endpoint {
        final String endpointId;
        final NodeId nodeId;
        final String localName;
        final String displayName;
        final boolean method;
        final NodeId dataType;
        final int valueRank;
        final String dataTypeName;
        final boolean historizing;
        final UaVariableNode node;
        final Hash snapshot;

        Endpoint(String endpointId, NodeId nodeId, String localName, String displayName, boolean method,
                NodeId dataType, int valueRank, String dataTypeName, boolean historizing, UaVariableNode node,
                Hash snapshot) {
            this.endpointId = endpointId;
            this.nodeId = nodeId;
            this.localName = localName;
            this.displayName = displayName;
            this.method = method;
            this.dataType = dataType;
            this.valueRank = valueRank;
            this.dataTypeName = dataTypeName;
            this.historizing = historizing;
            this.node = node;
            this.snapshot = snapshot;
        }
    }

    private static final class RootConfig {
        final String localPath;
        final NodeId materializedNodeId;
        final String materializedBrowsePath;

        RootConfig(String localPath, NodeId materializedNodeId, String materializedBrowsePath) {
            this.localPath = localPath;
            this.materializedNodeId = materializedNodeId;
            this.materializedBrowsePath = materializedBrowsePath;
        }
    }

    private void callbackOnly(String operation, Endpoint endpoint, Object... pairs) {
        if (callback == null) {
            return;
        }
        try {
            Hash info = endpoint != null ? endpointInfo(endpoint) : new Hash();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                info.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
            invokeCallback(operation, info);
        } catch (Throwable ignored) {
            // Runtime callbacks must not escape into Milo service threads.
        }
    }

    private static String endpointLocalName(Map<String, Object> spec) {
        String browseName = stringValue(spec.get("browse_name"), null);
        if (browseName != null && !browseName.isEmpty()) {
            return stripQualifiedName(browseName);
        }
        String browsePath = stringValue(spec.get("browse_path"), null);
        if (browsePath != null && !browsePath.isEmpty()) {
            String[] parts = browsePath.split("/");
            for (int i = parts.length - 1; i >= 0; --i) {
                if (!parts[i].isEmpty()) {
                    return stripQualifiedName(parts[i]);
                }
            }
        }
        String nodeId = stringValue(spec.get("node_id"), null);
        if (nodeId != null) {
            int s = nodeId.indexOf(";s=");
            if (s >= 0) {
                return nodeId.substring(s + 3);
            }
        }
        return stringValue(spec.get("endpoint_id"), "Endpoint");
    }

    private static String parentPath(Map<String, Object> spec) {
        String browsePath = stringValue(spec.get("browse_path"), null);
        if (browsePath == null || browsePath.isEmpty()) {
            return "";
        }
        List<String> local = localBrowsePathParts(browsePath);
        if (local.size() <= 1) {
            return "";
        }
        return String.join("/", local.subList(0, local.size() - 1));
    }

    private static RootConfig rootConfigFor(int idx, Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        String rootNodeId = stringValue(snapshot.get("root_node_id"), null);
        String rootBrowsePath = stringValue(snapshot.get("root_browse_path"), null);
        if (rootNodeId == null || rootNodeId.isEmpty() || rootBrowsePath == null
                || rootBrowsePath.isEmpty()) {
            return null;
        }

        List<String> local = localBrowsePathParts(rootBrowsePath);
        if (local.isEmpty()) {
            throw new IllegalArgumentException("schema root_browse_path has no local path: " + rootBrowsePath);
        }
        NodeId materializedNodeId = configuredNodeIdFor(idx, rootNodeId);
        if (materializedNodeId == null) {
            throw new IllegalArgumentException("schema root_node_id is not supported: " + rootNodeId);
        }
        String localPath = String.join("/", local);
        return new RootConfig(localPath, materializedNodeId, qualifiedBrowsePath(idx, local));
    }

    private static List<String> localBrowsePathParts(String browsePath) {
        List<String> local = new ArrayList<>();
        for (String part : browsePath.split("/")) {
            if (!part.isEmpty()) {
                local.add(stripQualifiedName(part));
            }
        }
        return local;
    }

    private static String qualifiedBrowsePath(int idx, List<String> localParts) {
        StringBuilder rv = new StringBuilder();
        for (String part : localParts) {
            if (!part.isEmpty()) {
                rv.append('/').append(idx).append(':').append(part);
            }
        }
        return rv.length() == 0 ? "/" : rv.toString();
    }

    private static String actualBrowsePath(int idx, String parentPath, String localName) {
        StringBuilder rv = new StringBuilder();
        if (parentPath != null && !parentPath.isEmpty()) {
            for (String part : parentPath.split("/")) {
                if (!part.isEmpty()) {
                    rv.append('/').append(idx).append(':').append(part);
                }
            }
        }
        rv.append('/').append(idx).append(':').append(localName);
        return rv.toString();
    }

    private static String stripQualifiedName(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static NodeId nodeIdFor(int idx, Map<String, Object> spec, String localName,
            String endpointId, boolean method) {
        String nodeId = stringValue(spec.get("node_id"), null);
        NodeId configuredNodeId = configuredNodeIdFor(idx, nodeId);
        if (configuredNodeId != null) {
            return configuredNodeId;
        }
        return new NodeId(idx, (method ? "method:" : "var:") + localName + ":" + endpointId);
    }

    private static NodeId configuredNodeIdFor(int idx, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        int s = nodeId.indexOf(";s=");
        if (s >= 0) {
            return new NodeId(idx, nodeId.substring(s + 3));
        }
        int i = nodeId.indexOf(";i=");
        if (i >= 0) {
            try {
                return new NodeId(idx, UInteger.valueOf(Long.parseLong(nodeId.substring(i + 3))));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Argument[] argumentsFromSpec(Object spec) {
        List<Object> list = asList(spec);
        Argument[] rv = new Argument[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            Map<String, Object> arg = asMap(list.get(i));
            String name = arg != null ? stringValue(arg.get("name"), "arg" + i) : "arg" + i;
            String desc = arg != null ? stringValue(arg.get("description"), name) : name;
            NodeId dataType = arg != null ? dataTypeNodeId(arg.get("data_type")) : NodeIds.String;
            int valueRank = arg != null ? intValue(arg.get("value_rank"), -1) : -1;
            rv[i] = new Argument(name, dataType, valueRank, null, LocalizedText.english(desc));
        }
        return rv;
    }

    private static List<Object> argumentsToSnapshot(Argument[] args) {
        List<Object> rv = new ArrayList<>(args.length);
        for (Argument arg : args) {
            Hash h = new Hash();
            h.put("name", arg.getName());
            h.put("description", arg.getDescription() != null ? arg.getDescription().getText() : null);
            h.put("data_type", arg.getDataType().toParseableString());
            h.put("value_rank", arg.getValueRank());
            rv.add(h);
        }
        return rv;
    }

    private static Variant variantFor(Endpoint endpoint, Object value) {
        return variantFor(endpoint.dataType, value);
    }

    private static Variant variantFor(NodeId dataType, Object value) {
        String name = builtinName(dataType);
        if (name != null && ValueCodec.isSupportedScalarType(name)) {
            return ValueCodec.toVariant(value, name);
        }
        return new Variant(value);
    }

    private static NodeId dataTypeNodeId(Object dataType) {
        if (dataType == null) {
            return NodeIds.String;
        }
        String s = String.valueOf(dataType);
        switch (s) {
            case "Boolean": return NodeIds.Boolean;
            case "SByte": return NodeIds.SByte;
            case "Byte": return NodeIds.Byte;
            case "Int16": return NodeIds.Int16;
            case "UInt16": return NodeIds.UInt16;
            case "Int32": return NodeIds.Int32;
            case "UInt32": return NodeIds.UInt32;
            case "Int64": return NodeIds.Int64;
            case "UInt64": return NodeIds.UInt64;
            case "Float": return NodeIds.Float;
            case "Double": return NodeIds.Double;
            case "String": return NodeIds.String;
            case "ByteString": return NodeIds.ByteString;
            default: return NodeId.parse(s);
        }
    }

    private static String builtinName(NodeId dataType) {
        if (Objects.equals(dataType, NodeIds.Boolean)) return "Boolean";
        if (Objects.equals(dataType, NodeIds.SByte)) return "SByte";
        if (Objects.equals(dataType, NodeIds.Byte)) return "Byte";
        if (Objects.equals(dataType, NodeIds.Int16)) return "Int16";
        if (Objects.equals(dataType, NodeIds.UInt16)) return "UInt16";
        if (Objects.equals(dataType, NodeIds.Int32)) return "Int32";
        if (Objects.equals(dataType, NodeIds.UInt32)) return "UInt32";
        if (Objects.equals(dataType, NodeIds.Int64)) return "Int64";
        if (Objects.equals(dataType, NodeIds.UInt64)) return "UInt64";
        if (Objects.equals(dataType, NodeIds.Float)) return "Float";
        if (Objects.equals(dataType, NodeIds.Double)) return "Double";
        if (Objects.equals(dataType, NodeIds.String)) return "String";
        if (Objects.equals(dataType, NodeIds.ByteString)) return "ByteString";
        return null;
    }

    private static Object defaultValue(NodeId dataType) {
        String name = builtinName(dataType);
        if (name == null) {
            return null;
        }
        switch (name) {
            case "Boolean": return Boolean.FALSE;
            case "Float":
            case "Double": return 0.0;
            case "String": return "";
            case "ByteString": return new byte[0];
            default: return 0L;
        }
    }

    private static String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int intValue(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private List<DataValue> historySnapshot(NodeId nodeId) {
        Deque<DataValue> values = namespace.history.get(nodeId);
        if (values == null) {
            return Collections.emptyList();
        }
        synchronized (values) {
            return new ArrayList<>(values);
        }
    }

    private void addHistory(Endpoint endpoint, DataValue value) {
        if (!endpoint.historizing || maxHistoryValues == 0) {
            return;
        }
        Deque<DataValue> values = namespace.history.computeIfAbsent(endpoint.nodeId, k -> new ArrayDeque<>());
        synchronized (values) {
            while (values.size() >= maxHistoryValues) {
                values.removeFirst();
            }
            values.addLast(value);
        }
    }
}
