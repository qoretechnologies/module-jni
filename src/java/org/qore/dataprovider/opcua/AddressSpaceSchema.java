/*  AddressSpaceSchema.java Copyright 2026 Qore Technologies, s.r.o.

    Pure OPC UA native address-space document validation and conversion.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction. THE
    SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.qore.jni.Hash;

/** Validates, describes, imports, and exports native OPC UA address-space documents without a server. */
public final class AddressSpaceSchema {
    /** The root discriminator value for the native OPC UA document format. */
    public static final String OPCUA_VERSION = "1";

    /** The standard OPC UA namespace, always at namespace index 0. */
    public static final String UA_NAMESPACE = "http://opcfoundation.org/UA/";

    private AddressSpaceSchema() {
    }

    /**
     * Validates and normalizes a native OPC UA address-space document without constructing a server.
     *
     * @param document the native address-space document
     * @return an independent normalized snapshot carrying the {@code opcua: "1"} discriminator
     * @throws SchemaException if the document is invalid
     */
    public static Hash describe(Map<String, Object> document) {
        if (document == null) {
            throw error("/", null, "the OPC UA address-space document is required");
        }

        Object marker = document.get("opcua");
        if (marker != null && (!(marker instanceof String) || !OPCUA_VERSION.equals(marker))) {
            throw error("/opcua", null, "the `opcua` root marker must be the string \"1\"");
        }

        Object contractVersion = document.get("contract_version");
        if (contractVersion != null && (!(contractVersion instanceof Number)
                || ((Number) contractVersion).intValue() != SchemaResolver.CONTRACT_VERSION
                || ((Number) contractVersion).doubleValue() != SchemaResolver.CONTRACT_VERSION)) {
            throw error("/contract_version", null, "unsupported OPC UA contract version; expected 1");
        }

        Map<?, ?> sourceNamespaces = requireMap(document.get("namespaces"), "/namespaces", null,
            "`namespaces` must be a hash keyed by namespace index");
        TreeMap<Integer, String> namespaceTable = normalizeNamespaces(sourceNamespaces);
        Hash namespaces = new Hash();
        for (Map.Entry<Integer, String> entry : namespaceTable.entrySet()) {
            namespaces.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        Map<String, Integer> namespaceIndexes = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : namespaceTable.entrySet()) {
            Integer previous = namespaceIndexes.put(entry.getValue(), entry.getKey());
            if (previous != null) {
                throw error("/namespaces/" + entry.getKey(), null,
                    "namespace URI `" + entry.getValue() + "` is already declared at index " + previous);
            }
        }

        List<Object> endpointSpecs = requireList(document.get("endpoints"), "/endpoints", null,
            "`endpoints` must be a list");
        List<Object> endpoints = new ArrayList<>(endpointSpecs.size());
        Set<String> endpointIds = new HashSet<>();
        for (int i = 0; i < endpointSpecs.size(); ++i) {
            String pointer = "/endpoints/" + i;
            Map<?, ?> endpoint = requireMap(endpointSpecs.get(i), pointer, null,
                "each endpoint must be a hash");
            Hash normalized = normalizeEndpoint(endpoint, namespaceTable, namespaceIndexes, pointer);
            String endpointId = String.valueOf(normalized.get("endpoint_id"));
            if (!endpointIds.add(endpointId)) {
                throw error(pointer + "/endpoint_id", endpointId,
                    "duplicate endpoint_id `" + endpointId + "`");
            }
            endpoints.add(normalized);
        }

        Hash snapshot = deepCopyHash(document);
        snapshot.put("opcua", OPCUA_VERSION);
        snapshot.put("contract_version", SchemaResolver.CONTRACT_VERSION);
        String source = optionalStringField(document, "source", "/source", null);
        snapshot.put("source", source != null ? source : "document");
        snapshot.put("namespaces", namespaces);
        snapshot.put("endpoints", endpoints);
        normalizeRoot(snapshot);
        normalizeRequiredModels(snapshot, namespaceTable);
        return snapshot;
    }

    /**
     * Converts NodeSet2 XML to a validated native address-space document without starting a server.
     *
     * @param xml the NodeSet2 XML document
     * @return the validated native address-space document
     * @throws Exception if XML parsing or address-space validation fails
     */
    public static Hash nodeSet2ToAddressSpace(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) {
            throw error("/", null, "the NodeSet2 XML document is required");
        }
        return describe(NodeSet2Importer.parse(xml));
    }

    /**
     * Exports a validated native address-space document to the NodeSet2 subset supported by the runtime.
     *
     * @param document the native address-space document
     * @return NodeSet2 XML
     * @throws SchemaException if the document is invalid
     */
    public static String exportNodeSet2(Map<String, Object> document) {
        Hash snapshot = describe(document);
        Map<?, ?> namespaces = requireMap(snapshot.get("namespaces"), "/namespaces", null,
            "`namespaces` must be a hash keyed by namespace index");

        TreeMap<Integer, String> customNamespaces = new TreeMap<>();
        for (Map.Entry<?, ?> entry : namespaces.entrySet()) {
            int index = parseNamespaceIndex(entry.getKey(), "/namespaces");
            if (index > 0) {
                customNamespaces.put(index, String.valueOf(entry.getValue()));
            }
        }
        Map<Integer, Integer> exportedIndexes = new LinkedHashMap<>();
        int exportIndex = 1;
        for (Integer sourceIndex : customNamespaces.keySet()) {
            exportedIndexes.put(sourceIndex, exportIndex++);
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<UANodeSet xmlns=\"http://opcfoundation.org/UA/2011/03/UANodeSet.xsd\">\n");
        xml.append("  <NamespaceUris>\n");
        for (String uri : customNamespaces.values()) {
            xml.append("    <Uri>").append(xmlEscape(uri)).append("</Uri>\n");
        }
        xml.append("  </NamespaceUris>\n");

        List<Object> requiredModels = asList(snapshot.get("required_models"));
        if (!requiredModels.isEmpty() && !customNamespaces.isEmpty()) {
            xml.append("  <Models>\n");
            xml.append("    <Model ModelUri=\"").append(xmlEscape(customNamespaces.firstEntry().getValue()))
                .append("\">\n");
            for (Object model : requiredModels) {
                xml.append("      <RequiredModel ModelUri=\"").append(xmlEscape(model)).append("\"/>\n");
            }
            xml.append("    </Model>\n");
            xml.append("  </Models>\n");
        }

        List<Object> endpoints = asList(snapshot.get("endpoints"));
        for (Object endpointObject : endpoints) {
            Map<?, ?> endpoint = asMap(endpointObject);
            String kind = String.valueOf(endpoint.get("kind"));
            boolean method = "method-call".equals(kind);
            String tag = method ? "UAMethod" : "UAVariable";
            String nodeId = remapNodeId(String.valueOf(endpoint.get("node_id")), exportedIndexes);
            String browseName = remapQualifiedName(String.valueOf(endpoint.get("browse_name")), exportedIndexes);
            xml.append("  <").append(tag)
                .append(" NodeId=\"").append(xmlEscape(nodeId)).append("\"")
                .append(" BrowseName=\"").append(xmlEscape(browseName)).append("\"");
            if (!method) {
                xml.append(" DataType=\"")
                    .append(xmlEscape(exportDataType(endpoint.get("data_type"), exportedIndexes)))
                    .append("\"")
                    .append(" ValueRank=\"").append(endpoint.get("value_rank")).append("\"")
                    .append(" AccessLevel=\"").append(endpoint.get("access_level")).append("\"")
                    .append(" UserAccessLevel=\"").append(endpoint.get("user_access_level")).append("\"")
                    .append(" Historizing=\"").append(endpoint.get("historizing")).append("\"");
                List<Object> dimensions = asList(endpoint.get("array_dimensions"));
                if (!dimensions.isEmpty()) {
                    xml.append(" ArrayDimensions=\"").append(xmlEscape(join(dimensions, ","))).append("\"");
                }
            }
            xml.append(">\n");
            xml.append("    <DisplayName>").append(xmlEscape(endpoint.get("display_name")))
                .append("</DisplayName>\n");
            xml.append("  </").append(tag).append(">\n");
        }
        xml.append("</UANodeSet>\n");
        return xml.toString();
    }

    /**
     * Converts a Java-side exception to stable details for the Qore public API.
     *
     * @param exception the caught Java exception object
     * @return a hash with {@code message}, {@code pointer}, and optional {@code endpoint_id}
     */
    public static Hash exceptionInfo(Object exception) {
        Throwable cause = exception instanceof Throwable ? (Throwable) exception : null;
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SchemaException) {
                SchemaException schemaException = (SchemaException) current;
                Hash rv = new Hash();
                rv.put("message", schemaException.getMessage());
                rv.put("pointer", schemaException.getPointer());
                if (schemaException.getEndpointId() != null) {
                    rv.put("endpoint_id", schemaException.getEndpointId());
                }
                return rv;
            }
            current = current.getCause();
        }

        Hash rv = new Hash();
        String message = cause != null && cause.getMessage() != null ? cause.getMessage()
            : String.valueOf(exception);
        rv.put("message", message);
        rv.put("pointer", "/");
        return rv;
    }

    private static TreeMap<Integer, String> normalizeNamespaces(Map<?, ?> source) {
        TreeMap<Integer, String> namespaces = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String pointer = "/namespaces/" + jsonPointerEscape(String.valueOf(entry.getKey()));
            int index = parseNamespaceIndex(entry.getKey(), pointer);
            String uri = requireString(entry.getValue(), pointer, null,
                "namespace URIs must be non-empty strings");
            if (namespaces.put(index, uri) != null) {
                throw error(pointer, null, "duplicate numeric namespace index " + index);
            }
        }
        String standard = namespaces.get(0);
        if (standard != null && !UA_NAMESPACE.equals(standard)) {
            throw error("/namespaces/0", null, "namespace index 0 must be `" + UA_NAMESPACE + "`");
        }
        namespaces.put(0, UA_NAMESPACE);
        return namespaces;
    }

    private static Hash normalizeEndpoint(Map<?, ?> source, TreeMap<Integer, String> namespaces,
            Map<String, Integer> namespaceIndexes, String pointer) {
        Hash endpoint = deepCopyHash(source);
        String preliminaryId = optionalStringField(source, "endpoint_id", pointer + "/endpoint_id", null);
        String kind = requireString(source.get("kind"), pointer + "/kind", preliminaryId,
            "endpoint `kind` must be `variable` or `method-call`");
        if (!"variable".equals(kind) && !"method-call".equals(kind)) {
            throw error(pointer + "/kind", preliminaryId,
                "endpoint `kind` must be `variable` or `method-call`");
        }

        String browseName = optionalStringField(source, "browse_name", pointer + "/browse_name", preliminaryId);
        String browsePath = optionalStringField(source, "browse_path", pointer + "/browse_path", preliminaryId);
        if (browseName == null && browsePath == null) {
            throw error(pointer + "/browse_path", preliminaryId,
                "an endpoint requires `browse_path` or `browse_name`");
        }
        if (browseName == null) {
            browseName = lastBrowsePathPart(browsePath);
        }

        String nodeId = optionalStringField(source, "node_id", pointer + "/node_id", preliminaryId);
        if (nodeId != null) {
            validateNodeId(nodeId, pointer + "/node_id", preliminaryId);
        }
        Integer declaredIndex = namespaceIndex(nodeId);
        if (declaredIndex == null) {
            declaredIndex = qualifiedNameIndex(browseName);
        }
        if (declaredIndex == null && browsePath != null) {
            declaredIndex = browsePathNamespaceIndex(browsePath);
        }

        String namespaceUri = optionalStringField(source, "namespace_uri", pointer + "/namespace_uri",
            preliminaryId);
        Integer uriIndex = namespaceUri != null ? namespaceIndexes.get(namespaceUri) : null;
        if (namespaceUri != null && uriIndex == null) {
            throw error(pointer + "/namespace_uri", preliminaryId,
                "endpoint namespace_uri `" + namespaceUri + "` is not declared in `namespaces`");
        }
        if (declaredIndex != null && !namespaces.containsKey(declaredIndex)) {
            throw error(pointer + "/node_id", preliminaryId,
                "endpoint references undeclared namespace index " + declaredIndex);
        }
        if (declaredIndex != null && uriIndex != null && !declaredIndex.equals(uriIndex)) {
            throw error(pointer + "/namespace_uri", preliminaryId,
                "endpoint namespace_uri does not match its node or browse namespace index");
        }

        int namespaceIndex = uriIndex != null ? uriIndex
            : declaredIndex != null ? declaredIndex : defaultNamespaceIndex(namespaces);
        namespaceUri = namespaces.get(namespaceIndex);
        if (browseName.indexOf(':') < 0) {
            browseName = namespaceIndex + ":" + browseName;
        }
        if (browsePath == null) {
            browsePath = "/" + browseName;
        } else if (!browsePath.startsWith("/")) {
            throw error(pointer + "/browse_path", preliminaryId, "endpoint browse_path must start with `/`");
        }

        String endpointId = preliminaryId;
        if (endpointId == null) {
            try {
                endpointId = SchemaResolver.deriveEndpointId(namespaceUri, browsePath, kind);
            } catch (Exception e) {
                throw error(pointer + "/endpoint_id", null,
                    "could not derive a stable endpoint_id: " + e.getMessage());
            }
        }
        String localName = stripQualifiedName(browseName);
        if (nodeId == null) {
            nodeId = new NodeId(namespaceIndex,
                ("method-call".equals(kind) ? "method:" : "var:") + localName + ":" + endpointId)
                .toParseableString();
        }

        endpoint.put("endpoint_id", endpointId);
        endpoint.put("node_id", nodeId);
        endpoint.put("browse_name", browseName);
        endpoint.put("browse_path", browsePath);
        endpoint.put("namespace_uri", namespaceUri);
        endpoint.put("kind", kind);
        endpoint.put("node_class", "method-call".equals(kind) ? "Method" : "Variable");
        String displayName = optionalStringField(source, "display_name", pointer + "/display_name", endpointId);
        endpoint.put("display_name", displayName != null ? displayName : localName);

        if ("variable".equals(kind)) {
            normalizeVariable(endpoint, source, pointer, endpointId);
        } else {
            normalizeMethod(endpoint, source, pointer, endpointId);
        }
        return endpoint;
    }

    private static void normalizeVariable(Hash endpoint, Map<?, ?> source, String pointer,
            String endpointId) {
        String dataType = requireString(source.get("data_type"), pointer + "/data_type", endpointId,
            "variable endpoints require a non-empty `data_type`");
        validateDataType(dataType, pointer + "/data_type", endpointId);
        int valueRank = optionalInteger(source.get("value_rank"), -1, pointer + "/value_rank", endpointId);
        if (valueRank < -3) {
            throw error(pointer + "/value_rank", endpointId,
                "value_rank must be -3, -2, -1, or a non-negative integer");
        }
        boolean readable = optionalBoolean(source.get("readable"), true, pointer + "/readable", endpointId);
        boolean writable = optionalBoolean(source.get("writable"), false, pointer + "/writable", endpointId);
        boolean historizing = optionalBoolean(source.get("historizing"), false,
            pointer + "/historizing", endpointId);
        int accessLevel = optionalInteger(source.get("access_level"),
            (readable ? 1 : 0) | (writable ? 2 : 0) | (historizing ? 4 : 0),
            pointer + "/access_level", endpointId);
        int userAccessLevel = optionalInteger(source.get("user_access_level"), accessLevel,
            pointer + "/user_access_level", endpointId);
        if (accessLevel < 0 || accessLevel > 255) {
            throw error(pointer + "/access_level", endpointId, "access_level must be between 0 and 255");
        }
        if (userAccessLevel < 0 || userAccessLevel > 255) {
            throw error(pointer + "/user_access_level", endpointId,
                "user_access_level must be between 0 and 255");
        }

        List<Object> dimensions = optionalDimensions(source.get("array_dimensions"),
            pointer + "/array_dimensions", endpointId);
        Object validation = source.get("validation");
        if (validation != null) {
            requireMap(validation, pointer + "/validation", endpointId,
                "endpoint `validation` must be a hash");
        }
        validateRange(source, validation, pointer, endpointId);

        List<Object> directions = new ArrayList<>();
        if (readable) {
            directions.add("read");
        }
        if (writable) {
            directions.add("write");
        }
        if (historizing) {
            directions.add("history-read");
        }
        directions.add("observe");

        endpoint.put("data_type", dataType);
        endpoint.put("value_rank", valueRank);
        endpoint.put("array_dimensions", dimensions.isEmpty() ? null : dimensions);
        endpoint.put("access_level", accessLevel);
        endpoint.put("user_access_level", userAccessLevel);
        endpoint.put("readable", readable);
        endpoint.put("writable", writable);
        endpoint.put("user_writable", optionalBoolean(source.get("user_writable"), writable,
            pointer + "/user_writable", endpointId));
        endpoint.put("directions", directions);
        endpoint.put("historizing", historizing);
        if (!endpoint.containsKey("minimum_sampling_interval")) {
            endpoint.put("minimum_sampling_interval", 0.0);
        }
    }

    private static void normalizeMethod(Hash endpoint, Map<?, ?> source, String pointer,
            String endpointId) {
        endpoint.put("input_arguments", normalizeArguments(source.get("input_arguments"),
            pointer + "/input_arguments", endpointId));
        endpoint.put("output_arguments", normalizeArguments(source.get("output_arguments"),
            pointer + "/output_arguments", endpointId));
        String objectNodeId = optionalStringField(source, "object_node_id", pointer + "/object_node_id",
            endpointId);
        if (objectNodeId != null) {
            validateNodeId(objectNodeId, pointer + "/object_node_id", endpointId);
        }
    }

    private static List<Object> normalizeArguments(Object value, String pointer, String endpointId) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> source = requireList(value, pointer, endpointId,
            "method arguments must be a list");
        List<Object> arguments = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); ++i) {
            String argumentPointer = pointer + "/" + i;
            Map<?, ?> sourceArgument = requireMap(source.get(i), argumentPointer, endpointId,
                "each method argument must be a hash");
            Hash argument = deepCopyHash(sourceArgument);
            String name = requireString(sourceArgument.get("name"), argumentPointer + "/name", endpointId,
                "method arguments require a non-empty `name`");
            String dataType = requireString(sourceArgument.get("data_type"),
                argumentPointer + "/data_type", endpointId,
                "method arguments require a non-empty `data_type`");
            validateDataType(dataType, argumentPointer + "/data_type", endpointId);
            argument.put("name", name);
            argument.put("data_type", dataType);
            int valueRank = optionalInteger(sourceArgument.get("value_rank"), -1,
                argumentPointer + "/value_rank", endpointId);
            if (valueRank < -3) {
                throw error(argumentPointer + "/value_rank", endpointId,
                    "value_rank must be -3, -2, -1, or a non-negative integer");
            }
            argument.put("value_rank", valueRank);
            arguments.add(argument);
        }
        return arguments;
    }

    private static void normalizeRoot(Hash snapshot) {
        String rootNodeId = optionalStringField(snapshot, "root_node_id", "/root_node_id", null);
        String rootBrowsePath = optionalStringField(snapshot, "root_browse_path", "/root_browse_path", null);
        if ((rootNodeId == null) != (rootBrowsePath == null)) {
            String pointer = rootNodeId == null ? "/root_node_id" : "/root_browse_path";
            throw error(pointer, null, "`root_node_id` and `root_browse_path` must be provided together");
        }
        if (rootNodeId != null) {
            validateNodeId(rootNodeId, "/root_node_id", null);
            if (!rootBrowsePath.startsWith("/")) {
                throw error("/root_browse_path", null, "root_browse_path must start with `/`");
            }
        }
    }

    private static void normalizeRequiredModels(Hash snapshot, TreeMap<Integer, String> namespaces) {
        Object value = snapshot.get("required_models");
        if (value == null) {
            return;
        }
        List<Object> source = requireList(value, "/required_models", null,
            "`required_models` must be a list of namespace URI strings");
        List<Object> required = new ArrayList<>(source.size());
        List<Object> missing = new ArrayList<>();
        Set<String> known = new HashSet<>(namespaces.values());
        for (int i = 0; i < source.size(); ++i) {
            String model = requireString(source.get(i), "/required_models/" + i, null,
                "required model namespace URIs must be non-empty strings");
            required.add(model);
            if (!known.contains(model)) {
                missing.add(model);
            }
        }
        snapshot.put("required_models", required);
        snapshot.put("missing_dependencies", missing);
    }

    private static void validateRange(Map<?, ?> endpoint, Object validationObject, String pointer,
            String endpointId) {
        Map<?, ?> validation = asMap(validationObject);
        Object minimum = validation != null && validation.containsKey("minimum")
            ? validation.get("minimum") : endpoint.get("minimum");
        Object maximum = validation != null && validation.containsKey("maximum")
            ? validation.get("maximum") : endpoint.get("maximum");
        if (minimum != null && !(minimum instanceof Number)) {
            throw error(pointer + "/validation/minimum", endpointId, "minimum must be numeric");
        }
        if (maximum != null && !(maximum instanceof Number)) {
            throw error(pointer + "/validation/maximum", endpointId, "maximum must be numeric");
        }
        if (minimum instanceof Number && maximum instanceof Number
                && ((Number) minimum).doubleValue() > ((Number) maximum).doubleValue()) {
            throw error(pointer + "/validation/maximum", endpointId,
                "maximum must be greater than or equal to minimum");
        }
    }

    private static List<Object> optionalDimensions(Object value, String pointer, String endpointId) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<Object> source = requireList(value, pointer, endpointId,
            "array_dimensions must be a list of non-negative integers");
        List<Object> dimensions = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); ++i) {
            int dimension = optionalInteger(source.get(i), -1, pointer + "/" + i, endpointId);
            if (dimension < 0) {
                throw error(pointer + "/" + i, endpointId,
                    "array dimensions must be non-negative integers");
            }
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private static int parseNamespaceIndex(Object value, String pointer) {
        String text = String.valueOf(value);
        try {
            int index = Integer.parseInt(text);
            if (index < 0 || index > 65535) {
                throw new NumberFormatException();
            }
            return index;
        } catch (NumberFormatException e) {
            throw error(pointer, null, "namespace indexes must be integers from 0 through 65535");
        }
    }

    private static Integer namespaceIndex(Object nodeId) {
        String value = optionalString(nodeId);
        if (value == null) {
            return null;
        }
        try {
            return NodeId.parse(value).getNamespaceIndex().intValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Integer qualifiedNameIndex(String name) {
        int colon = name != null ? name.indexOf(':') : -1;
        if (colon <= 0) {
            return null;
        }
        try {
            return Integer.valueOf(name.substring(0, colon));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer browsePathNamespaceIndex(String path) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; --i) {
            Integer index = qualifiedNameIndex(parts[i]);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private static int defaultNamespaceIndex(TreeMap<Integer, String> namespaces) {
        for (Integer index : namespaces.keySet()) {
            if (index > 0) {
                return index;
            }
        }
        return 0;
    }

    private static String lastBrowsePathPart(String browsePath) {
        String[] parts = browsePath.split("/");
        for (int i = parts.length - 1; i >= 0; --i) {
            if (!parts[i].isEmpty()) {
                return parts[i];
            }
        }
        return null;
    }

    private static String stripQualifiedName(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static void validateNodeId(String value, String pointer, String endpointId) {
        try {
            NodeId.parse(value);
        } catch (RuntimeException e) {
            throw error(pointer, endpointId, "invalid OPC UA NodeId `" + value + "`");
        }
    }

    private static void validateDataType(String value, String pointer, String endpointId) {
        try {
            dataTypeNodeId(value);
        } catch (RuntimeException e) {
            throw error(pointer, endpointId, "invalid OPC UA data_type `" + value + "`");
        }
    }

    private static NodeId dataTypeNodeId(Object dataType) {
        String value = String.valueOf(dataType);
        switch (value) {
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
            default: return NodeId.parse(value);
        }
    }

    private static String exportDataType(Object dataType, Map<Integer, Integer> exportedIndexes) {
        return remapNodeId(dataTypeNodeId(dataType).toParseableString(), exportedIndexes);
    }

    private static String remapNodeId(String value, Map<Integer, Integer> exportedIndexes) {
        if (!value.startsWith("ns=")) {
            return value;
        }
        int semicolon = value.indexOf(';');
        if (semicolon < 4) {
            return value;
        }
        try {
            int sourceIndex = Integer.parseInt(value.substring(3, semicolon));
            Integer targetIndex = exportedIndexes.get(sourceIndex);
            return targetIndex != null ? "ns=" + targetIndex + value.substring(semicolon) : value;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String remapQualifiedName(String value, Map<Integer, Integer> exportedIndexes) {
        Integer sourceIndex = qualifiedNameIndex(value);
        if (sourceIndex == null) {
            return value;
        }
        Integer targetIndex = exportedIndexes.get(sourceIndex);
        return targetIndex != null ? targetIndex + value.substring(value.indexOf(':')) : value;
    }

    private static String join(List<Object> values, String separator) {
        StringBuilder rv = new StringBuilder();
        for (Object value : values) {
            if (rv.length() > 0) {
                rv.append(separator);
            }
            rv.append(value);
        }
        return rv.toString();
    }

    private static String xmlEscape(Object value) {
        String text = value != null ? String.valueOf(value) : "";
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static int optionalInteger(Object value, int defaultValue, String pointer, String endpointId) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number)) {
            throw error(pointer, endpointId, "expected an integer");
        }
        Number number = (Number) value;
        int result = number.intValue();
        if (number.doubleValue() != result) {
            throw error(pointer, endpointId, "expected an integer");
        }
        return result;
    }

    private static boolean optionalBoolean(Object value, boolean defaultValue, String pointer,
            String endpointId) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean)) {
            throw error(pointer, endpointId, "expected a boolean");
        }
        return (Boolean) value;
    }

    private static String requireString(Object value, String pointer, String endpointId, String message) {
        String text = optionalString(value);
        if (text == null) {
            throw error(pointer, endpointId, message);
        }
        return text;
    }

    private static String optionalString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String optionalStringField(Map<?, ?> source, String key, String pointer,
            String endpointId) {
        if (!source.containsKey(key) || source.get(key) == null) {
            return null;
        }
        Object value = source.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw error(pointer, endpointId, "expected a non-empty string");
        }
        return ((String) value).trim();
    }

    private static Map<?, ?> requireMap(Object value, String pointer, String endpointId, String message) {
        Map<?, ?> map = asMap(value);
        if (map == null) {
            throw error(pointer, endpointId, message);
        }
        return map;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static List<Object> requireList(Object value, String pointer, String endpointId, String message) {
        if (!(value instanceof List) && !(value instanceof Object[])) {
            throw error(pointer, endpointId, message);
        }
        return asList(value);
    }

    private static List<Object> asList(Object value) {
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

    private static Hash deepCopyHash(Map<?, ?> source) {
        Hash rv = new Hash();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            rv.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return rv;
    }

    private static Object deepCopyValue(Object value) {
        Map<?, ?> map = asMap(value);
        if (map != null) {
            return deepCopyHash(map);
        }
        if (value instanceof List || value instanceof Object[]) {
            List<Object> rv = new ArrayList<>();
            for (Object element : asList(value)) {
                rv.add(deepCopyValue(element));
            }
            return rv;
        }
        return value;
    }

    private static String jsonPointerEscape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static SchemaException error(String pointer, String endpointId, String message) {
        return new SchemaException(pointer, endpointId, message);
    }

    /** A validation error carrying the exact JSON pointer and endpoint identity. */
    public static final class SchemaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final String pointer;
        private final String endpointId;

        SchemaException(String pointer, String endpointId, String message) {
            super(endpointId != null ? "endpoint `" + endpointId + "`: " + message : message);
            this.pointer = pointer;
            this.endpointId = endpointId;
        }

        /** @return the JSON pointer to the invalid value */
        public String getPointer() {
            return pointer;
        }

        /** @return the endpoint id, or {@code null} for document-level errors */
        public String getEndpointId() {
            return endpointId;
        }
    }
}
