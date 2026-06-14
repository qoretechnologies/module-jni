/*  SchemaResolver.java Copyright 2026 Qore Technologies, s.r.o.

    Generic, Qorus-agnostic OPC UA schema snapshot resolver (Epic A Phase 1).

    Browses a connected Eclipse Milo OPC UA client and produces a stable, versioned schema snapshot as
    an org.qore.jni.Hash (which the Qore/JNI bridge converts to a Qore hash). Performing the multi-step
    browse in compiled Java and returning the whole snapshot in one call minimizes JNI round-trips.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction. THE
    SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.qore.jni.Hash;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

/** Resolves an OPC UA address-space subtree into a generic schema snapshot. */
public class SchemaResolver {
    /** The schema snapshot contract version. */
    public static final int CONTRACT_VERSION = 1;

    /** Maximum browse recursion depth (cycle/runaway guard). */
    private static final int MAX_DEPTH = 24;

    /**
     * Resolves the schema snapshot for the address-space subtree under the given root node.
     *
     * @param client the connected OPC UA client
     * @param rootNodeId the root node id to browse from (e.g. the Objects folder, "i=85")
     * @return the schema snapshot as an org.qore.jni.Hash (converted to a Qore hash)
     * @throws Exception on a fatal browse/read failure
     */
    public static Hash resolve(OpcUaClient client, String rootNodeId) throws Exception {
        NamespaceTable namespaceTable = client.readNamespaceTable();

        Hash snapshot = new Hash();
        snapshot.put("contract_version", CONTRACT_VERSION);
        snapshot.put("source", "live");

        Hash namespaces = new Hash();
        String[] uris = namespaceTable.toArray();
        for (int i = 0; i < uris.length; ++i) {
            namespaces.put(String.valueOf(i), uris[i]);
        }
        snapshot.put("namespaces", namespaces);

        List<Object> endpoints = new ArrayList<>();
        Set<String> seenEndpointIds = new HashSet<>();
        walk(client, namespaceTable, uris, NodeId.parse(rootNodeId), "", endpoints, seenEndpointIds, 0);
        snapshot.put("endpoints", endpoints);

        return snapshot;
    }

    /** Recursively browses hierarchical references, collecting variable and method endpoints. */
    private static void walk(OpcUaClient client, NamespaceTable namespaceTable, String[] uris, NodeId nodeId,
            String parentPath, List<Object> endpoints, Set<String> seenEndpointIds, int depth)
            throws Exception {
        if (depth > MAX_DEPTH) {
            return;
        }
        BrowseDescription browse = new BrowseDescription(
            nodeId,
            BrowseDirection.Forward,
            NodeIds.HierarchicalReferences,
            true,
            UInteger.valueOf(0),    // all node classes
            UInteger.valueOf(63));  // all result fields
        BrowseResult result = client.browse(browse);
        ReferenceDescription[] references = result.getReferences();
        if (references == null) {
            return;
        }
        for (ReferenceDescription reference : references) {
            Optional<NodeId> childOpt = reference.getNodeId().toNodeId(namespaceTable);
            if (childOpt.isEmpty()) {
                continue;
            }
            NodeId child = childOpt.get();
            QualifiedName browseName = reference.getBrowseName();
            int childNsIndex = child.getNamespaceIndex().intValue();
            if (childNsIndex >= uris.length) {
                // the child references a namespace index outside the server's namespace table (e.g. the
                // table changed mid-walk); skip it rather than deriving an endpoint id from a null
                // namespace uri, consistent with the unresolvable-child skip above
                continue;
            }
            String namespaceUri = uris[childNsIndex];
            String qualifiedName = browseName.getNamespaceIndex().intValue() + ":" + browseName.getName();
            String browsePath = parentPath + "/" + qualifiedName;
            NodeClass nodeClass = reference.getNodeClass();

            if (nodeClass == NodeClass.Variable) {
                Hash endpoint = addEndpoint(endpoints, seenEndpointIds, namespaceUri, browsePath,
                    "variable", child, qualifiedName, "Variable", reference);
                enrichVariable(client, endpoint, child);
            } else if (nodeClass == NodeClass.Method) {
                Hash endpoint = addEndpoint(endpoints, seenEndpointIds, namespaceUri, browsePath,
                    "method-call", child, qualifiedName, "Method", reference);
                endpoint.put("object_node_id", nodeId.toParseableString());
                enrichMethod(client, namespaceTable, endpoint, child);
            } else if (nodeClass == NodeClass.Object) {
                walk(client, namespaceTable, uris, child, browsePath, endpoints, seenEndpointIds, depth + 1);
            }
        }
    }

    /** Adds a resolved endpoint to the snapshot, rejecting duplicate endpoint ids; returns it. */
    private static Hash addEndpoint(List<Object> endpoints, Set<String> seenEndpointIds,
            String namespaceUri, String browsePath, String kind, NodeId nodeId, String browseName,
            String nodeClass, ReferenceDescription reference) throws Exception {
        String endpointId = deriveEndpointId(namespaceUri, browsePath, kind);
        if (!seenEndpointIds.add(endpointId)) {
            throw new IllegalStateException("duplicate OPC UA endpoint id " + endpointId
                + " for namespace " + namespaceUri + " path " + browsePath + " kind " + kind);
        }
        Hash endpoint = new Hash();
        endpoint.put("endpoint_id", endpointId);
        endpoint.put("node_id", nodeId.toParseableString());
        endpoint.put("browse_name", browseName);
        endpoint.put("browse_path", browsePath);
        endpoint.put("namespace_uri", namespaceUri);
        endpoint.put("kind", kind);
        endpoint.put("node_class", nodeClass);
        endpoint.put("display_name",
            reference.getDisplayName() != null ? reference.getDisplayName().getText() : null);
        endpoints.add(endpoint);
        return endpoint;
    }

    /** Returns the raw value of a read attribute, or null if the read was not good. */
    private static Object attrValue(DataValue dv) {
        if (dv == null || !dv.getStatusCode().isGood()) {
            return null;
        }
        Variant v = dv.getValue();
        return v != null ? v.getValue() : null;
    }

    /** Enriches a variable endpoint with its data type, value rank, dimensions, and access metadata. */
    private static void enrichVariable(OpcUaClient client, Hash endpoint, NodeId nodeId)
            throws Exception {
        AttributeId[] attrs = {
            AttributeId.DataType, AttributeId.ValueRank, AttributeId.ArrayDimensions,
            AttributeId.AccessLevel, AttributeId.UserAccessLevel, AttributeId.Historizing,
            AttributeId.MinimumSamplingInterval,
        };
        List<ReadValueId> ids = new ArrayList<>();
        for (AttributeId a : attrs) {
            ids.add(new ReadValueId(nodeId, a.uid(), null, QualifiedName.NULL_VALUE));
        }
        ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, ids);
        DataValue[] results = response.getResults();

        Object dataType = attrValue(results[0]);
        endpoint.put("data_type",
            dataType instanceof NodeId ? ((NodeId) dataType).toParseableString() : null);
        Object valueRank = attrValue(results[1]);
        endpoint.put("value_rank", valueRank != null ? ((Number) valueRank).intValue() : null);
        Object arrayDims = attrValue(results[2]);
        if (arrayDims instanceof UInteger[]) {
            List<Object> dims = new ArrayList<>();
            for (UInteger u : (UInteger[]) arrayDims) {
                dims.add((int) u.longValue());
            }
            endpoint.put("array_dimensions", dims);
        } else {
            endpoint.put("array_dimensions", null);
        }
        Object accessLevel = attrValue(results[3]);
        List<Object> directions = new ArrayList<>();
        if (accessLevel != null) {
            int al = ((Number) accessLevel).intValue();
            endpoint.put("access_level", al);
            endpoint.put("readable", (al & 1) != 0);
            endpoint.put("writable", (al & 2) != 0);
            if ((al & 1) != 0) {
                directions.add("read");
                directions.add("observe");
            }
            if ((al & 2) != 0) {
                directions.add("write");
            }
        }
        endpoint.put("directions", directions);
        Object userAccess = attrValue(results[4]);
        if (userAccess != null) {
            int ual = ((Number) userAccess).intValue();
            endpoint.put("user_access_level", ual);
            endpoint.put("user_writable", (ual & 2) != 0);
        }
        Object historizing = attrValue(results[5]);
        endpoint.put("historizing", historizing instanceof Boolean ? (Boolean) historizing : null);
        Object minSampling = attrValue(results[6]);
        endpoint.put("minimum_sampling_interval",
            minSampling != null ? ((Number) minSampling).doubleValue() : null);
    }

    /** Enriches a method endpoint with its resolved input/output argument metadata. */
    private static void enrichMethod(OpcUaClient client, NamespaceTable namespaceTable, Hash endpoint,
            NodeId methodNode) throws Exception {
        BrowseDescription browse = new BrowseDescription(methodNode, BrowseDirection.Forward,
            NodeIds.HasProperty, true, UInteger.valueOf(0), UInteger.valueOf(63));
        ReferenceDescription[] refs = client.browse(browse).getReferences();
        NodeId inputArgs = null;
        NodeId outputArgs = null;
        if (refs != null) {
            for (ReferenceDescription ref : refs) {
                Optional<NodeId> idOpt = ref.getNodeId().toNodeId(namespaceTable);
                if (idOpt.isEmpty()) {
                    continue;
                }
                String name = ref.getBrowseName().getName();
                if ("InputArguments".equals(name)) {
                    inputArgs = idOpt.get();
                } else if ("OutputArguments".equals(name)) {
                    outputArgs = idOpt.get();
                }
            }
        }
        endpoint.put("input_arguments", readArguments(client, inputArgs));
        endpoint.put("output_arguments", readArguments(client, outputArgs));
    }

    /** Reads and decodes an OPC UA Argument[] property value into a list of argument metadata. */
    private static List<Object> readArguments(OpcUaClient client, NodeId argumentsNode)
            throws Exception {
        List<Object> rv = new ArrayList<>();
        if (argumentsNode == null) {
            return rv;
        }
        DataValue dv = client.readValue(0.0, TimestampsToReturn.Neither, argumentsNode);
        Object value = attrValue(dv);
        if (!(value instanceof Object[])) {
            return rv;
        }
        EncodingContext ctx = client.getStaticEncodingContext();
        for (Object element : (Object[]) value) {
            Argument arg = (Argument) ((org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject)
                element).decode(ctx);
            Hash a = new Hash();
            a.put("name", arg.getName());
            a.put("description", arg.getDescription() != null ? arg.getDescription().getText() : null);
            a.put("data_type", arg.getDataType() != null ? arg.getDataType().toParseableString() : null);
            a.put("value_rank", arg.getValueRank());
            rv.add(a);
        }
        return rv;
    }

    /**
     * Derives a stable, URL-safe endpoint id from the namespace URI, browse path, and endpoint kind.
     *
     * <p>The id is the lowercase hex SHA-256 of the canonical tuple. It depends only on the namespace
     * URI (never the session-local namespace index), the namespace-qualified browse path, and the kind,
     * so the same model yields the same id across deployments and server restarts.
     */
    public static String deriveEndpointId(String namespaceUri, String browsePath, String kind)
            throws Exception {
        String canonical = namespaceUri.length() + ":" + namespaceUri + browsePath.length() + ":" + browsePath + kind.length() + ":" + kind;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
