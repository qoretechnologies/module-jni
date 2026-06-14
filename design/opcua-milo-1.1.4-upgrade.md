# OPC UA: Milo 1.1.4 Upgrade and Epic A Phases 1-5 Plan

Status: Phase 0 and the Milo 1.1.4 upgrade + client migration are DONE (module v1.2). The client is
migrated to Milo 1.1.4, a deterministic in-process test server is in place, and the integration
regression baseline passes. Epic A Phases 1-4 (schema snapshot resolver, live introspection, value
codec, NodeSet2 import) proceed next on the 1.1.4 base. This document remains the migration reference.

Tracking issues: qoretechnologies/qorus#312 (umbrella), #313 (Epic A), #314, #315.

## Why an upgrade

Decision 8 of the umbrella commits the program to the latest stable Milo, **1.1.4**. Phases 1-4 of
Epic A (schema snapshot contract, live introspection, value codec, NodeSet2 import) are built on
1.1.4 and must not be started on 0.6.13, whose `DataTypeDefinition` / structured-DataType /
`ExtensionObject` / NodeSet2 surface differs substantially.

## Resolved 1.1.4 dependency set (verified)

`maven/opcua/pom.xml` declares `org.eclipse.milo:milo-sdk-client` and `milo-sdk-server` `1.1.4`.
`mvn -f maven/opcua/pom.xml dependency:copy-dependencies -DincludeScope=runtime` resolves the full
transitive runtime set (verified against Maven Central):

```
milo-sdk-client-1.1.4   milo-sdk-core-1.1.4   milo-sdk-server-1.1.4
milo-stack-core-1.1.4   milo-transport-1.1.4
netty-buffer/codec/common/handler/resolver/transport/transport-native-unix-common-4.1.133.Final
netty-channel-fsm-1.0.2
bcpkix/bcprov/bcutil-jdk18on-1.84
slf4j-api-2.0.18   jspecify-1.0.0   strict-machine-1.0.0
```

Add `slf4j-nop-2.0.18` (as today) to suppress the no-SLF4J-provider warning.

Footprint changes vs the 0.6.13 set:
- Artifacts renamed: `sdk-client`/`sdk-core`/`stack-client`/`stack-core` -> `milo-sdk-*`/`milo-stack-core`;
  new `milo-transport`.
- **Dropped**: Guava, failureaccess, the entire JAXB stack (jaxb-runtime, jakarta.xml.bind,
  jakarta.activation, txw2, istack-commons), netty-codec-http.
- **Added**: jspecify (annotations), strict-machine 1.0.0.
- JDK baseline: 17+ (build/CI use JDK 17 or later; local toolchain verified with javac/openjdk).

## Constraint: no two Milo majors in one JVM

The 0.6.13 and 1.1.4 jars both define `org.eclipse.milo.*` classes and cannot coexist on one
classpath. Therefore:
- The migration is **atomic**: jars + `.qm` classpath directives + all client code move together.
- The deterministic test server (in-process, Milo 1.1.4) can only verify the client once the client is
  also on 1.1.4. An interim "1.1.4 server vs 0.6.13 client" check would require a separate JVM/process.

## Verified API delta (0.6.13 -> 1.1.4)

All confirmed via `javap` against the 1.1.4 jars.

### Client construction / lifecycle
- `OpcUaClient.create(String endpointUrl)` and `create(OpcUaClientConfig, Consumer<transportCfg>)`.
- **Synchronous + async variants** for every service: `connect()`/`connectAsync()`,
  `read(...)`/`readAsync(...)`, `write`/`writeAsync`, `call`/`callAsync`, `historyRead`/`historyReadAsync`.
  Keep using the `*Async()` futures with the cooperative `awaitFuture()` helper to preserve
  interruptible I/O (the synchronous methods block uninterruptibly).
- Connect/disconnect no longer return a `CompletableFuture<Void>` from a manager; use `connectAsync()`.

### Packages moved
- Identity providers: `sdk.client.api.identity.*` -> `sdk.client.identity.*`
  (`AnonymousProvider`, `UsernameProvider`, `X509IdentityProvider`).
- Config builder: `OpcUaClientConfigBuilder` / `OpcUaClientConfig` (no longer under `api.config`).

### Service signature changes
- `call`: now `call(List<CallMethodRequest>)` -> `CallResponse` (was single `CallMethodRequest` ->
  `CallMethodResult`). Wrap the single request in a list and read `response.getResults()[0]`.
- Encoding context: `getStaticSerializationContext()` -> `getStaticEncodingContext()`
  (`EncodingContext`); `getDynamicEncodingContext()` reads the server type system. `Argument` decode
  uses the encoding context.

### Subscriptions — full rewrite required
0.6.x `getSubscriptionManager().createSubscription()` + `UaSubscription.NotificationListener` is gone.
1.1.4 uses `org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription`:
- `new OpcUaSubscription(client[, publishingIntervalMs])`, `.create()`/`.createAsync()`.
- `OpcUaMonitoredItem` objects added via `addMonitoredItem(...)` then `createMonitoredItems()`.
- Data-change callbacks are attached per monitored item / via a subscription listener
  (`setSubscriptionListener`), not a single `NotificationListener` interface.

### Unchanged / available
- `org.eclipse.milo.opcua.stack.core.StatusCodes.lookup(long)` -> `Optional<String[]>`: unchanged, so
  `OpcUaStatusMapping` (Phase 0) ports as-is.
- Unsigned types unchanged (`UShort`/`UByte`/`UInteger`/`ULong`), but watch `valueOf` overloads:
  `UInteger` has `valueOf(int)` and `valueOf(long)`; `UShort`/`UByte` expose `valueOf(short)`/
  `valueOf(String)` (and a byte/short range) — `OpcUaTypeConversion` must coerce accordingly.
- `ByteString.of(byte[])` unchanged.
- `AttributeId.DataTypeDefinition` **now exists** — the Phase 0 read-attributes omission can be added.
- New high-level type trees usable for Phases 1-2: `readDataTypeTree()`, `readObjectTypeTree()`,
  `readVariableTypeTree()`, `readNamespaceTable()` on `OpcUaClient` — these directly back the schema
  snapshot resolver and reduce hand-rolled browse/read.

## Remaining plan (each: implement -> verify against test server -> audit -> commit)

1. **Embedded test server** (`test/java/org/qore/opcua/test/QoreOpcUaTestServer.java`, in-process Milo
   1.1.4; `ManagedNamespaceWithLifecycle` with a fixed custom namespace: typed variables incl. writable
   narrow/unsigned/float, an `Add` method with typed args, a `SubscriptionModel` so monitored items are
   sampled and data-change notifications fire, and a historizing node served by a `historyRead` override).
   This closes the two paths that were migrated-but-not-round-trip-verified during the Milo upgrade
   (data-change subscription delivery and `read-history`): the integration suite now verifies both
   against the server, the data-change test using an event-signaled queue wait (no polling).
   Note: server-side history/sampling here is a **throwaway test fixture**, not a product abstraction;
   the generic, abstract history/data-change provider (extended in Qorus) belongs in Epic B's server
   runtime (#314) as additional callbacks on the B1 contract.
2. **Milo 1.1.4 upgrade / client migration** (atomic): swap jars + `.qm` classpath + CMake/spec; migrate
   `getClient`/config/identity/security, `call`, encoding context, and the subscription path; re-run the
   Phase 0 regression baseline (decision 9). Port `OpcUaStatusMapping` (unchanged) and adjust
   `OpcUaTypeConversion` `valueOf` coercion.
3. **Phase 1 (DONE, module v1.3)** — compiled Java schema-snapshot resolver
   (`org.qore.dataprovider.opcua.SchemaResolver`) returning an `org.qore.jni.Hash`; contract-versioned
   snapshot with namespace table + variable/method endpoints; deterministic `endpoint_id` =
   SHA-256(namespace URI, browse path, kind) (#312 decision 5). Exposed via `getSchemaSnapshot()`;
   verified against the test server.
4. **Phase 2 (DONE, module v1.4)** — live introspection enriches each snapshot endpoint: variables
   get data type / value rank / array dimensions / access levels / directions / historizing /
   min-sampling-interval; methods get resolved input/output argument metadata. Verified vs the test server.
5. **Phase 3 (DONE, module v1.5)** — Java value codec (`org.qore.dataprovider.opcua.ValueCodec`):
   typed writes for all built-in scalars incl. narrow signed (SByte/Int16/Int32) and Float (no longer
   rejected), and unsigned-value unwrapping to native Qore ints on read. Verified vs the test server.
6. **Phase 4 (DONE, module v1.6)** — offline NodeSet2 import. Milo 1.1.4 ships no general NodeSet2 XML
   parser (and the 1.x set drops JAXB), so `org.qore.dataprovider.opcua.NodeSet2Importer` parses it with
   the JDK's built-in XML parser (no new dependency); file retrieval is Qore-side via `FileLocationHandler`.
   Produces the same snapshot shape (matching `endpoint_id`s) plus `required_models` /
   `missing_dependencies`. Exposed via `importNodeSet2()`; verified with a minimal NodeSet2 file.
7. **Phase 5** (Qorus repo) — client design-time integration.

## Build/packaging tasks for the migration commit

- Replace `qlib/OpcUaDataProvider/jar/*` with the 1.1.4 set; `git add -f` the jars.
- Update the `%module-cmd(jni) global-add-relative-classpath` directives in `OpcUaDataProvider.qm`.
- Update jar install rules / lists in `CMakeLists.txt` and `qore-jni-module.spec`.
- Wire the new `src/java/org/qore/opcua/**` sources through the existing `make-jar` / Java build.
