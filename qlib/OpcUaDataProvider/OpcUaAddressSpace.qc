# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaAddressSpace.qc Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Validates and describes a native OPC UA address-space document without starting a server
/** The returned snapshot is an independent normalized hash. It always carries the root discriminator
    \c opcua: "1", contract version 1, a namespace table, and normalized variable or method endpoints.
    Missing endpoint IDs and node IDs are derived deterministically.

    The \c endpoints list may be empty, for example while authoring a new address space.  The field is
    required and must be a list; a missing or null value is invalid.

    A \c data_type may be written as an OPC UA built-in type name (\c "Double") or as a NodeId
    (\c "i=11", \c "ns=0;i=11"); the snapshot reports it in canonical form - the built-in type name when
    the type is a built-in one, otherwise the NodeId - so that a hand-written node, an imported one, and
    an introspected one describing the same type report the same value.

    @param document the native OPC UA address-space document

    @return the normalized schema snapshot

    @throw OPCUA-SCHEMA-ERROR if the document is invalid; the exception argument carries a \c pointer
        JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Validate an industrial machine address space while it is being authored, before the service binds a port:
    @code{.py}
    hash<auto> snapshot = OpcUaDataProvider::describeAddressSpace({
        "opcua": "1",
        "contract_version": 1,
        "namespaces": {"1": "urn:factory:machine"},
        "endpoints": ({
            "endpoint_id": "temperature",
            "node_id": "ns=1;s=Temperature",
            "browse_name": "1:Temperature",
            "browse_path": "/1:Machine/1:Temperature",
            "namespace_uri": "urn:factory:machine",
            "kind": "variable",
            "data_type": "Double",
            "value_rank": -1,
        },),
    });
    @endcode

    An address space without nodes can also be described and exported:
    @code{.py}
    hash<auto> empty_document = {
        "opcua": "1",
        "contract_version": 1,
        "namespaces": {"1": "urn:factory:machine"},
        "endpoints": (),
    };
    hash<auto> empty_snapshot = OpcUaDataProvider::describeAddressSpace(empty_document);
    string empty_nodeset2 = OpcUaDataProvider::exportNodeSet2(empty_document);
    @endcode
*/
public hash<auto> sub describeAddressSpace(hash<auto> document) {
    try {
        return AddressSpaceSchema::describe(document);
    } catch (hash<ExceptionInfo> ex) {
        hash<auto> info = AddressSpaceSchema::exceptionInfo(ex.arg);
        throw "OPCUA-SCHEMA-ERROR", sprintf("%s at %s", info.message, info.pointer), info;
    }
}

#! Converts NodeSet2 XML to the native OPC UA address-space document without starting a server
/** Each node's browse path is built from its \c ParentNodeId - or, when that attribute is absent, from
    its inverse hierarchical reference (\c HasComponent, \c HasProperty, \c Organizes) - up to the model's
    top, so nodes sharing a browse name under different parents remain distinct and the model hierarchy is
    preserved.  Every path segment carries its namespace index (e.g.
    \c "/1:MeasurementContainerType/1:Temperature/0:EngineeringUnits"), and \c endpoint_id is derived from
    the full path exactly as it is for a live server.  \c HasSubtype does not contribute a segment: it
    relates a type to its supertype rather than placing a node inside another node.  A node whose parent
    lies outside the model keeps a segment for that parent - its standard browse name for a well-known
    address-space node, otherwise its NodeId - except for the Root and Objects folders, which are the
    browse root.

    A method's \c InputArguments and \c OutputArguments properties are folded into the method endpoint's
    \c input_arguments and \c output_arguments rather than imported as variable endpoints of their own,
    and data types written as \c Aliases are resolved.  An imported \c data_type is reported in the same
    canonical form as a live or hand-written one: the built-in type name (\c "Double") for an OPC UA
    built-in type, and the NodeId (\c "i=887", \c "ns=1;i=31") for any other type.  A leading UTF-8 byte
    order mark is ignored.

    @param xml the complete OPC UA NodeSet2 XML document

    @return the validated native address-space document carrying \c opcua: "1"

    @throw OPCUA-SCHEMA-ERROR if the XML or imported address space is invalid; the exception argument carries
        a \c pointer JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Convert a vendor NodeSet2 model before storing it as a Qorus API schema:
    @code{.py}
    hash<auto> document = OpcUaDataProvider::nodeSet2ToAddressSpace(nodeset2_xml);
    @endcode
*/
public hash<auto> sub nodeSet2ToAddressSpace(string xml) {
    try {
        return AddressSpaceSchema::nodeSet2ToAddressSpace(xml);
    } catch (hash<ExceptionInfo> ex) {
        hash<auto> info = AddressSpaceSchema::exceptionInfo(ex.arg);
        throw "OPCUA-SCHEMA-ERROR", sprintf("%s at %s", info.message, info.pointer), info;
    }
}

#! Exports a native OPC UA address-space document to NodeSet2 XML without starting a server
/** The output is the same NodeSet2 subset emitted by @ref OpcUaServerRuntime::exportNodeSet2().
    An empty \c endpoints list produces a NodeSet2 document containing the namespace table without
    variable or method nodes.

    The nodes named by an endpoint's browse path are exported as well - as objects, unless the path
    segment is itself an exported endpoint - along with the parent references that hold the hierarchy
    together and the \c InputArguments / \c OutputArguments properties carrying method argument metadata,
    so that importing the exported document with @ref OpcUaDataProvider::nodeSet2ToAddressSpace()
    reproduces the same browse paths and the same \c endpoint_id values.

    @param document the native OPC UA address-space document

    @return the address space serialized as NodeSet2 XML

    @throw OPCUA-SCHEMA-ERROR if the document is invalid; the exception argument carries a \c pointer
        JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Export an authored machine address space for an OPC UA engineering tool:
    @code{.py}
    string nodeset2 = OpcUaDataProvider::exportNodeSet2(document);
    @endcode
*/
public string sub exportNodeSet2(hash<auto> document) {
    try {
        return AddressSpaceSchema::exportNodeSet2(document);
    } catch (hash<ExceptionInfo> ex) {
        hash<auto> info = AddressSpaceSchema::exceptionInfo(ex.arg);
        throw "OPCUA-SCHEMA-ERROR", sprintf("%s at %s", info.message, info.pointer), info;
    }
}
}
