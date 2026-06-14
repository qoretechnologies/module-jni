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

%module-cmd(jni) import org.qore.dataprovider.opcua.GenericServer

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Generic OPC UA server runtime materialized from an OPC UA schema snapshot
/** This class is Qorus-agnostic. It wraps the compiled Milo server helper and exposes lifecycle,
    endpoint, schema, data-change, event, and NodeSet2 export operations to Qore embedders.
*/
public class OpcUaServerRuntime {
    private {
        GenericServer server;
        *code callback;
    }

    #! Creates a generic OPC UA server runtime
    /** @param options server options; required schema data can be passed as \c schema, \c schema_snapshot, or
        \c endpoints. Common options are \c port, \c bind_address, \c hostname, \c path, \c namespace_uri,
        \c application_name, \c application_uri, and \c product_uri.
        @param runtime_callback optional dispatcher called as \c runtime_callback(operation, info)
    */
    constructor(hash<auto> options, *code runtime_callback) {
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
    start() {
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
