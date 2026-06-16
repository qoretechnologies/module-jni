# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaServerRuntime.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Generic OPC UA server runtime materialized from an OPC UA schema snapshot
/** This class is Qorus-agnostic. It wraps the compiled Milo server helper and exposes lifecycle,
    endpoint, schema, data-change, event, and NodeSet2 export operations to Qore embedders.
*/
public class OpcUaServerRuntime {
    private {
        GenericServer server;
        *code<hash<auto>(string, hash<auto>)> callback;
    }

    #! Creates a generic OPC UA server runtime
    /** @param options server options; required schema data can be passed as \c schema, \c schema_snapshot, or
        \c endpoints. Common options are \c port, \c bind_address, \c hostname, \c path, \c namespace_uri,
        \c application_name, \c application_uri, \c product_uri, and \c max_history_values.
        @param runtime_callback optional dispatcher called as \c runtime_callback(operation, info)
        @note The embedded runtime uses an anonymous \c SecurityPolicy.None endpoint and therefore accepts only
            loopback \c bind_address and \c hostname values.
        @note \c max_history_values bounds implicit in-memory history retained per historizing variable; the
            default is 1024 and 0 disables implicit history retention.
    */
    constructor(hash<auto> options, *code<hash<auto>(string, hash<auto>)> runtime_callback) {
        callback = runtime_callback;
        if (callback) {
            server = new GenericServer(options, callback);
        } else {
            server = new GenericServer(options);
        }
    }

    destructor() {
        try {
            stop();
        } catch () {
        }
        delete server;
    }

    #! Starts the server endpoint
    /** @throw NETWORK-ACCESS-DENIED if an active Qore network sandbox denies the configured TCP bind address or port.
    */
    start() {
        Jni::org::qore::jni::check_network_bind(server.getBindAddress(), server.getPort(), self);
        server.start();
    }

    #! Stops the server endpoint
    stop() {
        if (server) {
            server.stop();
        }
    }

    #! Returns the OPC TCP endpoint URL
    string getEndpointUrl() {
        return server.getEndpointUrl();
    }

    #! Returns the materialized schema snapshot with actual server node ids
    hash<auto> getSchemaSnapshot() {
        return server.getSchemaSnapshot();
    }

    #! Returns a stable content hash for the materialized schema snapshot
    /** @return SHA-256 hash of the JSON serialization of @ref getSchemaSnapshot()
    */
    string getSchemaSnapshotHash() {
        return OpcUaSchemaProjection::getSnapshotHash(getSchemaSnapshot());
    }

    #! Returns a JSON Schema projection of the materialized schema snapshot
    /** @param options optional projection options such as \c title, \c schema_id, \c service_name, and
        \c version

        @return JSON Schema 2020-12 projection hash
    */
    hash<auto> getJsonSchemaProjection(*hash<auto> options) {
        return OpcUaSchemaProjection::getJsonSchemaProjection(getSchemaSnapshot(), options);
    }

    #! Returns an OpenAPI projection of the materialized schema snapshot
    /** @param options optional projection options such as \c title, \c service_name, \c version, and
        \c base_path

        @return OpenAPI 3.1 projection hash
    */
    hash<auto> getOpenApiProjection(*hash<auto> options) {
        return OpcUaSchemaProjection::getOpenApiProjection(getSchemaSnapshot(), options);
    }

    #! Returns publishable schema projection artifacts for the materialized schema snapshot
    /** @param options optional projection options; \c artifact_types can limit output to
        \c "json-schema" and/or \c "openapi"

        @return list of projection artifact hashes with content hashes and content payloads
    */
    list<hash<auto>> getSchemaProjectionArtifacts(*hash<auto> options) {
        return OpcUaSchemaProjection::getSchemaProjectionArtifacts(getSchemaSnapshot(), options);
    }

    #! Returns one materialized endpoint by endpoint id
    *hash<auto> getEndpoint(string endpoint_id) {
        return server.getEndpoint(endpoint_id);
    }

    #! Sets a variable endpoint value and publishes a data change to subscribers
    setValue(string endpoint_id, auto value) {
        server.setValue(endpoint_id, value);
    }

    #! Publishes a data change for a variable endpoint
    publishDataChange(string endpoint_id, auto value) {
        server.publishDataChange(endpoint_id, value);
    }

    #! Dispatches an application event to the runtime callback
    hash<auto> publishEvent(hash<auto> event) {
        return server.publishEvent(event);
    }

    #! Exports the materialized endpoints to a small OPC UA NodeSet2 XML subset
    string exportNodeSet2() {
        return server.exportNodeSet2();
    }
}
}
