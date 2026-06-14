/*  QoreOpcUaTestServer.java Copyright 2026 Qore Technologies, s.r.o.

    Deterministic in-process OPC UA test server (Eclipse Milo 1.1.4) for the OpcUaDataProvider
    integration tests. Exposes a fixed custom namespace with typed variables (including a writable
    UInt16) so client read/write/browse/attribute behavior can be verified deterministically.

    This is test infrastructure; it is not part of the shipped module.
*/

package org.qore.opcua.test;

import java.util.Set;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.HistoryReadContext;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryData;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A minimal, deterministic Milo 1.1.4 OPC UA test server. */
public class QoreOpcUaTestServer {
    /** Namespace URI for the custom test address space. */
    public static final String NAMESPACE_URI = "urn:qore:opcua:test";

    private final OpcUaServer server;
    private final int port;

    public QoreOpcUaTestServer(int port) throws Exception {
        this.port = port;

        MemoryCertificateQuarantine quarantine = new MemoryCertificateQuarantine();
        DefaultCertificateManager certificateManager = new DefaultCertificateManager(quarantine);

        EndpointConfig endpoint = EndpointConfig.newBuilder()
            .setBindAddress("127.0.0.1")
            .setBindPort(port)
            .setHostname("127.0.0.1")
            .setPath("/qore")
            .setSecurityPolicy(SecurityPolicy.None)
            .setSecurityMode(MessageSecurityMode.None)
            .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
            .build();

        OpcUaServerConfig config = OpcUaServerConfig.builder()
            .setApplicationName(LocalizedText.english("Qore OPC UA Test Server"))
            .setApplicationUri("urn:qore:opcua:test-server")
            .setProductUri("urn:qore:opcua:test-server")
            .setEndpoints(Set.of(endpoint))
            .setCertificateManager(certificateManager)
            .setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
            .build();

        this.server = new OpcUaServer(config, transportProfile ->
            new OpcTcpServerTransport(OpcTcpServerTransportConfig.newBuilder().build()));

        TestNamespace namespace = new TestNamespace(server);
        namespace.startup();
    }

    /** Returns the opc.tcp endpoint URL clients should connect to. */
    public String getEndpointUrl() {
        return "opc.tcp://127.0.0.1:" + port + "/qore";
    }

    /** Starts the server (blocks until ready). */
    public void start() throws Exception {
        server.startup().get();
    }

    /** Stops the server (blocks until shut down). */
    public void stop() throws Exception {
        server.shutdown().get();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 54840;
        QoreOpcUaTestServer s = new QoreOpcUaTestServer(port);
        s.start();
        System.out.println("READY " + s.getEndpointUrl());
        System.out.flush();
        Thread.currentThread().join();
    }

    /** The custom test namespace: a folder with typed variables, a method, and a history node. */
    static class TestNamespace extends ManagedNamespaceWithLifecycle {
        // drives sampling/notification for monitored items so data-change subscriptions fire
        private final SubscriptionModel subscriptionModel;

        TestNamespace(OpcUaServer server) {
            super(server, NAMESPACE_URI);
            subscriptionModel = new SubscriptionModel(server, this);
            getLifecycleManager().addLifecycle(subscriptionModel);
            getLifecycleManager().addLifecycle(new Lifecycle() {
                @Override public void startup() { addNodes(); }
                @Override public void shutdown() {}
            });
        }

        private void addNodes() {
            int idx = getNamespaceIndex().intValue();

            UaFolderNode folder = new UaFolderNode(
                getNodeContext(),
                new NodeId(idx, "TestFolder"),
                new QualifiedName(idx, "TestFolder"),
                LocalizedText.english("TestFolder"));
            getNodeManager().addNode(folder);
            folder.addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                folder.getNodeId(),
                NodeIds.Organizes,
                NodeIds.ObjectsFolder.expanded(),
                false));

            addVariable(folder, idx, "Int32Var", NodeIds.Int32, new Variant(42), true);
            addVariable(folder, idx, "DoubleVar", NodeIds.Double, new Variant(3.5), false);
            addVariable(folder, idx, "StringVar", NodeIds.String, new Variant("hello"), false);
            addVariable(folder, idx, "BoolVar", NodeIds.Boolean, new Variant(true), false);
            addVariable(folder, idx, "UInt16Var", NodeIds.UInt16, new Variant(UShort.valueOf(7)), true);
            addVariable(folder, idx, "Int64Var", NodeIds.Int64, new Variant(100L), true);
            addVariable(folder, idx, "Int16Var", NodeIds.Int16, new Variant((short) 11), true);
            addVariable(folder, idx, "FloatVar", NodeIds.Float, new Variant(1.5f), true);
            // a historizing variable whose history is served by the historyRead override below;
            // AccessLevel includes CurrentRead (1) + HistoryRead (4)
            UaVariableNode hist = addVariable(folder, idx, "HistoryVar", NodeIds.Int32,
                new Variant(0), false);
            hist.setHistorizing(true);
            hist.setAccessLevel(UByte.valueOf(5));
            hist.setUserAccessLevel(UByte.valueOf(5));

            // a method: Add(a: Int64, b: Int64) -> sum: Int64, owned by the folder
            UaMethodNode addMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(new NodeId(idx, "Add"))
                .setBrowseName(new QualifiedName(idx, "Add"))
                .setDisplayName(LocalizedText.english("Add"))
                .build();
            AddMethod handler = new AddMethod(addMethod);
            addMethod.setInputArguments(handler.getInputArguments());
            addMethod.setOutputArguments(handler.getOutputArguments());
            addMethod.setInvocationHandler(handler);
            getNodeManager().addNode(addMethod);
            folder.addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                folder.getNodeId(),
                NodeIds.HasComponent,
                addMethod.getNodeId().expanded(),
                true));
        }

        /** Add(a, b) -> a + b, two Int64 inputs and one Int64 output. */
        static class AddMethod extends AbstractMethodInvocationHandler {
            AddMethod(UaMethodNode node) {
                super(node);
            }

            @Override public Argument[] getInputArguments() {
                return new Argument[] {
                    new Argument("a", NodeIds.Int64, -1, null, LocalizedText.english("first addend")),
                    new Argument("b", NodeIds.Int64, -1, null, LocalizedText.english("second addend")),
                };
            }

            @Override public Argument[] getOutputArguments() {
                return new Argument[] {
                    new Argument("sum", NodeIds.Int64, -1, null, LocalizedText.english("the sum")),
                };
            }

            @Override protected Variant[] invoke(InvocationContext context, Variant[] inputs) {
                long a = (Long) inputs[0].getValue();
                long b = (Long) inputs[1].getValue();
                return new Variant[] { new Variant(a + b) };
            }
        }

        // Subscription hooks delegate to the SubscriptionModel so monitored items are sampled and
        // data-change notifications are delivered to clients.
        @Override public void onDataItemsCreated(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsCreated(dataItems);
        }
        @Override public void onDataItemsModified(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsModified(dataItems);
        }
        @Override public void onDataItemsDeleted(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsDeleted(dataItems);
        }
        @Override public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
            subscriptionModel.onMonitoringModeChanged(monitoredItems);
        }

        // Serves a small canned history for HistoryVar so the client read-history path can be verified.
        @Override public List<HistoryReadResult> historyRead(HistoryReadContext context,
                HistoryReadDetails details, TimestampsToReturn timestamps,
                List<HistoryReadValueId> nodesToRead) {
            EncodingContext ctx = getServer().getStaticEncodingContext();
            NodeId historyNode = new NodeId(getNamespaceIndex(), "HistoryVar");
            List<HistoryReadResult> results = new ArrayList<>();
            for (HistoryReadValueId id : nodesToRead) {
                if (historyNode.equals(id.getNodeId())) {
                    Instant now = Instant.now();
                    DataValue[] values = {
                        new DataValue(new Variant(10), StatusCode.GOOD, new DateTime(now.minusSeconds(30))),
                        new DataValue(new Variant(20), StatusCode.GOOD, new DateTime(now.minusSeconds(20))),
                        new DataValue(new Variant(30), StatusCode.GOOD, new DateTime(now.minusSeconds(10))),
                    };
                    ExtensionObject encoded = ExtensionObject.encode(ctx, new HistoryData(values));
                    results.add(new HistoryReadResult(StatusCode.GOOD, ByteString.NULL_VALUE, encoded));
                } else {
                    results.add(new HistoryReadResult(new StatusCode(0x80340000L),
                        ByteString.NULL_VALUE, null));
                }
            }
            return results;
        }

        private UaVariableNode addVariable(UaFolderNode folder, int idx, String name, NodeId dataType,
                Variant value, boolean writable) {
            UaVariableNode node = UaVariableNode.builder(getNodeContext())
                .setNodeId(new NodeId(idx, name))
                .setBrowseName(new QualifiedName(idx, name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(dataType)
                .setTypeDefinition(NodeIds.BaseDataVariableType)
                .build();
            // AccessLevel bitmask: CurrentRead = 1, CurrentWrite = 2, HistoryRead = 4
            UByte access = UByte.valueOf(writable ? 3 : 1);
            node.setAccessLevel(access);
            node.setUserAccessLevel(access);
            node.setValue(new DataValue(value));
            getNodeManager().addNode(node);
            folder.addOrganizes(node);
            return node;
        }
    }
}
