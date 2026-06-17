/*  OpcUaDataChangeListener.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.util.Objects;

import org.qore.jni.QoreClosure;

import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

/** Concrete Milo data-change listener forwarding notifications to a Qore callback. */
public final class OpcUaDataChangeListener implements OpcUaMonitoredItem.DataValueListener {
    private final String nodeId;
    private final QoreClosure callback;

    /**
     * Creates a data-change listener for one monitored node.
     *
     * @param nodeId monitored node ID to forward to Qore
     * @param callback Qore callback receiving {@code nodeId} and the Milo {@link DataValue}
     */
    public OpcUaDataChangeListener(String nodeId, QoreClosure callback) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /**
     * Forwards a Milo data-change notification to Qore.
     *
     * @param item monitored item that produced the notification
     * @param value received data value
     */
    @Override
    public void onDataReceived(OpcUaMonitoredItem item, DataValue value) {
        try {
            callback.call(nodeId, value);
        } catch (Throwable t) {
            throw new RuntimeException("OPC UA data-change callback failed", t);
        }
    }
}
