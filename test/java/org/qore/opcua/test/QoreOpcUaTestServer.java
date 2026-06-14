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

    /** The custom test namespace: a folder with typed variables. */
    static class TestNamespace extends ManagedNamespaceWithLifecycle {
        TestNamespace(OpcUaServer server) {
            super(server, NAMESPACE_URI);
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

        // Subscription hooks; no-op (the test server exposes static values for read/write/browse/call).
        @Override public void onDataItemsCreated(List<DataItem> dataItems) {}
        @Override public void onDataItemsModified(List<DataItem> dataItems) {}
        @Override public void onDataItemsDeleted(List<DataItem> dataItems) {}
        @Override public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {}

        private void addVariable(UaFolderNode folder, int idx, String name, NodeId dataType,
                Variant value, boolean writable) {
            UaVariableNode node = UaVariableNode.builder(getNodeContext())
                .setNodeId(new NodeId(idx, name))
                .setBrowseName(new QualifiedName(idx, name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(dataType)
                .setTypeDefinition(NodeIds.BaseDataVariableType)
                .build();
            // AccessLevel bitmask: CurrentRead = 1, CurrentWrite = 2
            UByte access = UByte.valueOf(writable ? 3 : 1);
            node.setAccessLevel(access);
            node.setUserAccessLevel(access);
            node.setValue(new DataValue(value));
            getNodeManager().addNode(node);
            folder.addOrganizes(node);
        }
    }
}
