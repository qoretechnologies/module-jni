/*  NodeSet2Importer.java Copyright 2026 Qore Technologies, s.r.o.

    Generic offline OPC UA NodeSet2 importer (Epic A Phase 4).

    Parses an OPC UA NodeSet2 XML document into the same schema snapshot shape produced by
    SchemaResolver, using the JDK's built-in XML parser (no external dependency). File retrieval is
    done by the caller (Qore-side, via FileLocationHandler); this class parses the XML content.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction. THE
    SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.qore.jni.Hash;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Imports an OPC UA NodeSet2 XML document into a generic schema snapshot. */
public class NodeSet2Importer {
    /** The standard OPC UA namespace, always index 0. */
    private static final String UA_NAMESPACE = "http://opcfoundation.org/UA/";

    /**
     * Parses a NodeSet2 XML document into a schema snapshot (same shape as SchemaResolver).
     *
     * @param xml the NodeSet2 XML content
     * @return the snapshot as an org.qore.jni.Hash; \c source is "imported" and it additionally carries
     *     a \c required_models list (the namespace URIs the model depends on)
     * @throws Exception on a parse failure
     */
    public static Hash parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // NodeSet2 declares a default namespace; local-name lookups are simplest with awareness off
        factory.setNamespaceAware(false);
        // harden the parser against XXE: enable secure processing, forbid DOCTYPE entirely, and deny
        // all external DTD/schema resolution and entity expansion
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();

        // namespace table: index 0 is the standard UA namespace, then the declared NamespaceUris in order
        List<String> uris = new ArrayList<>();
        uris.add(UA_NAMESPACE);
        Element namespaceUris = firstChild(root, "NamespaceUris");
        if (namespaceUris != null) {
            for (Element uri : childElements(namespaceUris, "Uri")) {
                uris.add(uri.getTextContent().trim());
            }
        }

        Hash snapshot = new Hash();
        snapshot.put("contract_version", SchemaResolver.CONTRACT_VERSION);
        snapshot.put("source", "imported");

        Hash namespaces = new Hash();
        for (int i = 0; i < uris.size(); ++i) {
            namespaces.put(String.valueOf(i), uris.get(i));
        }
        snapshot.put("namespaces", namespaces);

        // required model dependencies (Models/Model/RequiredModel ModelUri)
        List<Object> requiredModels = new ArrayList<>();
        Element models = firstChild(root, "Models");
        if (models != null) {
            for (Element model : childElements(models, "Model")) {
                for (Element required : childElements(model, "RequiredModel")) {
                    String modelUri = required.getAttribute("ModelUri");
                    if (!modelUri.isEmpty()) {
                        requiredModels.add(modelUri);
                    }
                }
            }
        }
        snapshot.put("required_models", requiredModels);

        // dependency check: required model namespaces not present in this model's namespace table
        // (the standard UA namespace is always considered present)
        Set<String> known = new HashSet<>(uris);
        known.add(UA_NAMESPACE);
        List<Object> missing = new ArrayList<>();
        for (Object modelUri : requiredModels) {
            if (!known.contains(String.valueOf(modelUri))) {
                missing.add(modelUri);
            }
        }
        snapshot.put("missing_dependencies", missing);

        // endpoints: UAVariable -> variable, UAMethod -> method-call
        List<Object> endpoints = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String tag = localName(element.getTagName());
            String kind;
            if ("UAVariable".equals(tag)) {
                kind = "variable";
            } else if ("UAMethod".equals(tag)) {
                kind = "method-call";
            } else {
                continue;
            }
            addEndpoint(endpoints, seen, uris, element, kind);
        }
        snapshot.put("endpoints", endpoints);

        return snapshot;
    }

    private static void addEndpoint(List<Object> endpoints, Set<String> seen, List<String> uris,
            Element element, String kind) throws Exception {
        String nodeId = element.getAttribute("NodeId");
        String browseName = element.getAttribute("BrowseName");
        int nsIndex = namespaceIndexOf(nodeId);
        if (nsIndex >= uris.size()) {
            // fail with a clear parse error rather than deriving an endpoint id from a null namespace
            throw new IllegalStateException("NodeSet2 node '" + nodeId + "' references namespace index "
                + nsIndex + ", which is not declared in the model's NamespaceUris table");
        }
        String namespaceUri = uris.get(nsIndex);
        String browsePath = "/" + browseName;
        String endpointId = SchemaResolver.deriveEndpointId(namespaceUri, browsePath, kind);
        if (!seen.add(endpointId)) {
            throw new IllegalStateException("duplicate imported endpoint id " + endpointId);
        }

        Hash endpoint = new Hash();
        endpoint.put("endpoint_id", endpointId);
        endpoint.put("node_id", nodeId);
        endpoint.put("browse_name", browseName);
        endpoint.put("browse_path", browsePath);
        endpoint.put("namespace_uri", namespaceUri);
        endpoint.put("kind", kind);
        endpoint.put("node_class", "variable".equals(kind) ? "Variable" : "Method");
        Element displayName = firstChild(element, "DisplayName");
        endpoint.put("display_name", displayName != null ? displayName.getTextContent().trim() : null);
        if ("variable".equals(kind)) {
            String dataType = element.getAttribute("DataType");
            endpoint.put("data_type", dataType.isEmpty() ? null : dataType);
            String valueRank = element.getAttribute("ValueRank");
            endpoint.put("value_rank", valueRank.isEmpty() ? -1 : Integer.parseInt(valueRank));
        }
        endpoints.add(endpoint);
    }

    /** Returns the namespace index of a NodeId string (\c ns=N;... ; defaults to 0). */
    private static int namespaceIndexOf(String nodeId) {
        if (nodeId != null && nodeId.startsWith("ns=")) {
            int semi = nodeId.indexOf(';');
            if (semi > 3) {
                try {
                    return Integer.parseInt(nodeId.substring(3, semi));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static Element firstChild(Element parent, String tag) {
        List<Element> elements = childElements(parent, tag);
        return elements.isEmpty() ? null : elements.get(0);
    }

    private static List<Element> childElements(Element parent, String tag) {
        List<Element> rv = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && localName(((Element) node).getTagName()).equals(tag)) {
                rv.add((Element) node);
            }
        }
        return rv;
    }

    /**
     * Returns the local name of an XML tag (the part after any {@code prefix:}).
     *
     * <p>Namespace awareness is disabled while parsing, so a tag may arrive either bare
     * (e.g. {@code UAVariable}) or prefixed (e.g. {@code u:UAVariable}) depending on how the NodeSet2
     * document is serialized. Matching on the local name makes import robust across both forms.
     *
     * @param tagName the raw tag name as returned by {@link Element#getTagName()}
     * @return the local name with any namespace prefix removed
     */
    private static String localName(String tagName) {
        int colon = tagName.indexOf(':');
        return colon >= 0 ? tagName.substring(colon + 1) : tagName;
    }
}
