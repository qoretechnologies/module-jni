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

    @param document the native OPC UA address-space document

    @return the normalized schema snapshot

    @throw OPCUA-SCHEMA-ERROR if the document is invalid; the exception argument carries a \c pointer
        JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Validate an industrial machine address space while it is being authored, before the service binds a port:
    @code
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
/** @param xml the complete OPC UA NodeSet2 XML document

    @return the validated native address-space document carrying \c opcua: "1"

    @throw OPCUA-SCHEMA-ERROR if the XML or imported address space is invalid; the exception argument carries
        a \c pointer JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Convert a vendor NodeSet2 model before storing it as a Qorus API schema:
    @code
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

    @param document the native OPC UA address-space document

    @return the address space serialized as NodeSet2 XML

    @throw OPCUA-SCHEMA-ERROR if the document is invalid; the exception argument carries a \c pointer
        JSON pointer and, for endpoint errors, \c endpoint_id

    @par Example
    Export an authored machine address space for an OPC UA engineering tool:
    @code
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
