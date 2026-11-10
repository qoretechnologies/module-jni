# Sandbox policy for Java I/O (`org.qore.jni.QoreSandbox`)

Qore enforces the network and filesystem policy of a `SandboxManager` only for I/O that goes through Qore
(sockets, QUIC, `File`/`Dir`).  Java code reached through module-jni performs its own I/O through the JVM, and the
JDK has no in-process Security Manager any more (JEP 411, JEP 486), so the policy can only be applied by the Java
code itself.  `org.qore.jni.QoreSandbox` gives Java code the checks; this document describes how they are built.

User documentation: `docs/guide-jni_use_java_in_qore.dox.tmpl`, section `jni_sandbox`, and the Javadoc of
`src/java/org/qore/jni/QoreSandbox.java`.

## Components

| Part | File |
|------|------|
| Java API (`QoreSandbox`, `QoreSandbox.Policy`) | `src/java/org/qore/jni/QoreSandbox.java` |
| Native methods | `src/JavaSandbox.cpp`, `src/JavaSandbox.h` |
| Class definition and native registration | `Globals::init()` in `src/Globals.cpp` |
| Release of an unclosed `Policy` | `NativeCleanup.KIND_SANDBOX_POLICY` (4), `native_cleanup_dispatch()` in `src/Globals.cpp` |
| Tests | `test/jni-sandbox-io.qtest`, fixture `test/java/src/org/qore/jni/test/SandboxIo.java` |

The classes are embedded in the module like the other `org.qore.jni` classes (`generate_java()` in
`CMakeLists.txt`) and listed in the module's class map, so user class loaders define the same classes.  No enum or
anonymous class is used, so the only class files are `QoreSandbox` and `QoreSandbox$Policy`.

## Which policy a check applies

Every native method runs in a `SandboxCallScope`:

1. The calling thread is attached to Qore (`QoreThreadAttacher`) for the duration of the call if it is not a Qore
   thread, so that the Qore library, and `QoreSandboxCaptureHelper`, which does nothing on a thread without Qore
   thread data, work on a pure Java thread.
2. With a captured policy (a `Policy` object, or the policy of the task a thread runs with `Policy.run()`/`call()`/
   `wrap()`), the check is made against `QoreSandboxCapture::getPolicyManagerRef()` and against the thread's policy
   (step 4), and must pass both.  The capture is also applied with `QoreSandboxCaptureHelper` for the call, so that
   the check, and the host name lookup of a network check, run under the call budgets of the capturing code and
   end when its sandbox interrupts or terminates its calls.
3. Without one, the thread must have a Qore caller: it must not have been attached by this call, and either a
   sandbox governs it (`QoreSandboxCapture::isSandboxed()`) or it runs the code of a Program
   (`qore_get_call_program_context()`).  Otherwise `JNI-SANDBOX-ERROR` is raised: the policy is unknown, and
   allowing the operation would turn a missing capture into a sandbox bypass.  `capture()` follows the same rule, so
   a policy cannot be captured lazily on a worker thread.
4. The thread's policy is resolved with `QoreSandboxManagerHelper(QoreSandboxManagerHelper::Policy)`, the
   resolution that Qore's own policy checks use, so module code in between inherits the caller's sandbox and a
   system-policy capability (`SandboxManager::createSystemPolicyCall()`) is checked with the system policy.

The Java side keeps the task policy in a `ThreadLocal` and restores the previous value when the task returns, so
nested tasks with different policies work and a pooled thread does not keep a policy after its task.

## Network check

`checkNetworkAccess0(ptr, host, port, proto)`:

- the host is passed as bytes (see "Strings" below); Java removes one pair of brackets around an IPv6 literal
  first, so the returned addresses carry the literal itself as their host name; port 0 is rejected, as it is no
  destination and the policy does not restrict it
- `QoreNetworkSecurityManager::checkHostname()` denies a destination that no address could make allowed (a denied
  port, no matching host pattern under a default-deny policy) before any lookup, so a denied name is never sent to
  DNS
- the name is resolved with `QoreAddrInfo::getInfo()`, which uses the async I/O resolver of Qore sockets (c-ares)
  and so the same hosts file and servers, including the debug overrides set with `dbg_cares_set_hosts_file()`
- every IPv4 and IPv6 address is checked with `QoreSandboxManager::checkNetworkAccess(host, addr, ...)` with the
  host name, so allowed host patterns apply; one denied address denies the call, because the caller may connect to
  any returned address
- the distinct addresses are returned in resolver order, IPv4 as 4 bytes and IPv6 as 16 bytes followed by the scope
  ID in network byte order; Java turns them into `InetAddress` objects with `InetAddress.getByAddress(host, ...)`,
  which performs no lookup and keeps the host name for TLS host name verification and SNI

The host name is resolved also when no sandbox applies, so Java code has one code path.

## Filesystem check

`checkFileAccess0(ptr, path, mode)` validates `mode` (a non-empty subset of the `QSEC_*` bits; the Java constants
are `static_assert`ed to match) and calls `QoreSandboxManager::checkFilesystemAccess()`, which canonicalizes the path
(symbolic links included, also for paths that do not exist yet) as Qore's `File` and `Dir` operations do.

## Strings

JNI's `GetStringUTFChars()` returns modified UTF-8 (NUL as `C0 80`, supplementary characters as surrogate pairs),
which would make the check see a different name than the one Java opens.  Java therefore encodes the host and path
itself with the JVM's file name charset (`sun.jnu.encoding`, falling back to UTF-8), the bytes the JVM passes to the
operating system, with `REPORT` for unmappable characters; a string with a NUL character or one that the charset
cannot represent raises `JNI-SANDBOX-ERROR`.

## Errors

Qore exceptions raised by a check are thrown in Java as `QoreExceptionWrapper` (a `RuntimeException`) and become the
original Qore exception when they reach Qore.  Argument errors detected in Java (`argumentError0()`) and the use of
a closed policy (`closedError0()`) are created natively with `QoreToJava::newExceptionWrapper()`, which builds the
wrapper without throwing it, so Java code throws them with normal control flow.  C++ exceptions never cross the JNI
boundary: `sandbox_native()` converts them to Java exceptions.

## Lifetime and concurrency of `Policy`

A `Policy` owns a heap-allocated `QoreSandboxCapture`.  Checks hold the read lock of a `ReentrantReadWriteLock`
while they use it, and `close()` takes the write lock, so closing waits for running checks and no check can use a
released capture; `Reference.reachabilityFence()` keeps the object reachable during a native call.  A policy that
is never closed is released by the NativeCleanup thread (`KIND_SANDBOX_POLICY`), which runs the release as a
registered Qore thread; at JVM shutdown NativeCleanup releases every outstanding handle.

## Why a captured policy is checked explicitly (qore#5644)

An applied capture (`QoreSandboxCaptureHelper`) sets only the thread's inherited sandbox context, which libqore
consults last (`find_thread_sandbox_stack_ref_intern()`): on a Qore thread, a sandboxed Program on the stack would
decide instead, and inside a system-policy capability no sandbox would.  So a check with a captured policy is made
against `QoreSandboxCapture::getPolicyManagerRef()` (`SandboxCallScope::managers()`), which does not depend on the
thread, and against the thread's policy, so that the stricter one wins and a captured policy never lowers the
sandbox of the code that uses it.  `capture0()` with a parent policy copies the parent capture with the
`QoreSandboxCapture` copy constructor: a policy captured in a task is the policy of the task.

## Declarations of the data provider apps

The checks are cooperative: they constrain only Java code that makes them.  A data provider app declares in its
`DataProviderAppInfo` (DataProvider 3.12) `external_io` when its actions do I/O that the sandbox does not see, and
`sandbox_aware` only after an audit against the rules in the `jni_sandbox` documentation; data provider consumers
refuse sandboxed callers an app with `external_io` and without `sandbox_aware`
(`DataProviderActionCatalog::checkSandboxAccess()`).

The apps of this module, from the audit for qore#5632 (the table is also in the `jni_sandbox` documentation):

- `external_io`: Access, Busylight, Camel, Flyway, JakartaJMS, JasperReports, JdbcDP, Kafka, MQTT, OpcUA. In each
  of them a Java library opens a path or connects to a destination given in an option, and most of them also reach
  destinations that the library resolves or discovers itself (cluster metadata, failover lists, server endpoint
  lists, report resources, linked database files), so none is `sandbox_aware`.
- no declaration: Avro, Email, Excel, ICS, ODP, ODS, ODT, PowerPoint, Tika, VCF, Visio, Word. Qore opens the
  locations (`FileLocationHandler`) and Java only transforms the streams it is given; Excel and ODS also create
  temporary files of their own in `java.io.tmpdir`, with names that no caller chooses.

The connection and data provider classes of the `external_io` modules call
`DataProviderActionCatalog::checkSandboxAccess(<AppName>)` as the first statement of every constructor, so sandboxed
code that loads such a module and creates its classes directly is refused before any I/O; a class that inherits
another module's class (Busylight from MQTT) is refused by both.

`test/data-provider-external-io.qtest` loads every app and checks its declaration against this list, checks that every
constructor of these classes starts with the check, and constructs the classes from sandboxed code.

## Classpath module commands

`jni_check_classpath_sandbox()` (`src/jni-module.cpp`): the `global-add-classpath` and
`global-add-relative-classpath` commands are refused to any governing sandbox (`QoreSandboxManagerHelper(All)`, so a
system-policy capability cannot use them either), because they change the parent classpath that every Program
shares; the entries of `add-classpath` and `add-relative-classpath` (split on the path separator and `;`, as
`QoreURLClassLoader.addPath()` splits them) are checked with the filesystem policy as reads
(`QoreSandboxManagerHelper(Policy)`).  `test/jni-classpath-sandbox.qtest`.
