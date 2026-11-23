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
anonymous class is used, so the class files are `QoreSandbox`, `QoreSandbox$Policy`, `QoreSandbox$ClasspathPolicy`,
`QoreClasspathGuard`, `QoreCompilerClasspath`, `QoreCompilerClasspath$Budget` and `QoreCompilerClasspath$Copy`.

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

## Classpath lookups (qore#7276)

`QoreClasspathGuard` applies a filesystem policy to each class and resource lookup below a classpath entry.

- **Binding.** A class loader has a guard only once restricted code has added an entry
  (`addResolvedPath()` of a restricted `ResolvedClasspath`).  The policy travels with the resolved classpath: the Java
  routes (`addPath()`, `addParentPath()`, `addPathOrig()`, `addWildcard()`, a subclass's `addURL()`) bind the managers
  that a check by the caller applies (`QoreSandbox.boundClasspath()`, native `classpathManagers0()`), and the module
  routes bind the manager that checked the classpath (`jni_check_classpath_policy()`, `jni_bind_classpath_policy()`).
  **Operator rule: whoever adds a classpath entry owns its policy.**  An entry that restricted code adds carries that
  code's policy and fails closed; an entry that a trusted host deliberately adds is a host grant, and is not also
  checked against the policy of the Program that owns the class loader.
  A restricted classpath with no policy is refused, because **an entry with no policy is not checked**: it was added by
  trusted code (a grant of the host), or before a filesystem policy restricted the code that added it (no sandbox yet,
  or one that restricted nothing then).  Setting or tightening a policy later does not reach such an entry, which is
  an operator rule in the guide; a per-lookup call for permissive Programs is deliberately not made.  The `global-*`
  commands, which add no policy, are refused under a filesystem policy.  Each path that a classpath reaches, also
  through a manifest, is recorded as the class loader's URLs name it.
- **Lookup.** `findClass()`, `findResource()` and `findResources()`, and the methods that called `super.findClass()`
  directly, enumerate the matches and take the first that `allows()`: the file's real path must pass every policy of
  the entries that contain it, up to the nearest entry of trusted code.  A real path that the JVM's file name encoding
  cannot represent exactly is refused, and a name with a parent element must resolve to the file of its URL.  A denied
  file is skipped, so a later entry can provide it, and a denial and a missing file have the same outcome.  A URL in no
  known entry is refused; on an unverified JDK this includes what the manifests of trusted jars name, in a class loader
  that also has entries of restricted code.
- **Whose policy.** Only the captured manager is consulted (`checkClasspathRead0()`), never the thread's caller, so the
  result does not depend on the thread and a stricter thread cannot affect another lookup.
- **No decision cache.** A `SandboxManager` can be changed at any time and has no change indicator, so a cached answer
  could outlive the rule it came from.  The policy is read for every lookup.
- **Fast path.** With no guard (trusted code, sandboxes that restrict nothing) a lookup is the plain JDK lookup plus one
  volatile read.
- **Kotlin.** The compiler reads directories itself, so a restricted compile gets a private copy of the allowed class
  and `.kotlin_module` files of each guarded directory (`QoreClasspathGuard.compilerLocations()`), judged per entry by
  the same enclosing policies as a lookup, in a private directory that the class loader makes, for code that can reach
  its Program.  A compilation opens one `QoreCompilerClasspath` for its class loader chain and closes it on every path;
  there is no release by path.  Each `Copy` has an explicit state (making, ready, ending, done) and one owner: a release
  while it is being made only marks it, its next charge fails, and the making thread removes it; only the transition to
  done removes the directory and returns its charges, so that happens once.  Directories are created only one level
  below a root that the copy owns.  What the live copies use (copies, files, bytes, directory entries visited) is
  charged to a `Budget` object that the class loader of a Program shares with the class loaders derived from it, so the
  limits hold across a chain, no Program pointer is a key, and clearing the Program ends the copies of all of them.
  Derived loaders keep the Program's copy budget also when their own pointer is cleared; only a new Program gets a new
  one, and a budget whose Program has ended refuses new copies.  A copy's charges are returned only once its directory
  is confirmed to be gone: a failed removal leaves it charged and live in a retryable state, the other copies are still
  ended, and the removal is tried again when its classpath or class loader is closed and at the end of its Program; a
  release that arrives during a removal makes the removing thread try again if that attempt fails.  Every copy's root
  is inside one private parent directory of the JVM (`PrivateParent`, mode 0700), so other local users cannot create,
  rename or replace anything below it, and a root needs no identity of its own.  A retry only removes the directory it
  created: before anything below the parent is removed, the parent is verified to be a directory and not a link, owned
  by the JVM's user, with mode 0700 and the `fileKey()` that it had when it was created.  If a check fails or cannot be
  made, nothing below it is touched, its copies stay charged, and a new copy uses a new parent.  The check and the
  removal use one opened directory (`SecureDirectoryStream`), and only a directory is removed at the name of a root.  Where the platform has no POSIX attributes or no secure directory stream, only
  the first removal walks the copy by path, and a retry leaves it charged until the process ends.  The parent is
  removed with its last copy, under the lock that a new copy takes; only an empty directory can be removed this way.
  That needs a secure directory stream on the temporary directory, so the empty parent stays, and the next copy reuses
  it, where the JVM cannot read that directory (for example mode 1733 of another owner, or 0300 owned by the JVM's user) or
  the JDK has no such stream.  A copy is made and read by path, so a restricted
  copy is refused unless the real path of the JVM's `java.io.tmpdir`, read once, is a directory owned by the JVM's
  numeric UID or by root that is not writable by group or others or that has the sticky bit.  The JVM's UID comes from
  the JDK (`com.sun.security.auth.module.UnixSystem`), so no user name is needed and nothing is created to find it; a new
  parent must then have that UID, which is recorded and used when the parent is reused.  This is checked when a parent
  is created and each time one is used for a new copy, and the refusal names no path.  If a UID cannot be read or the
  `unix` attribute view is missing on a POSIX platform, the copy is refused; this includes a runtime image without the
  `jdk.security.auth` module.  A new parent and the root of each new copy must have the expected UID, so a process
  whose effective UID differs, or changed before this copy's root is created, makes no copy under another identity.
  The removal of a refused root is attempted through the verified parent; a root that cannot be removed stays inside
  the 0700 parent, keeps that parent in place, and stops new copies there, so such roots cannot accumulate.  Trusted and permissive compiles make no
  copy and are not affected.  Only where the platform has no POSIX attributes at all are the mode and owner not checked:
  a copy is made, and a retry stays fail-closed as above.  The directories above it and ACLs are not judged.  Code in the same JVM is not
  defended against.  A
  removal that keeps failing is logged once, with no path, through the class loader's error log.  A cleanup failure is suppressed in an error that is being raised and never replaces it;
  a compile does not raise a failure to remove its own copy, which stays charged and is retried.  Closing a class loader ends
  its own copies, except once the module's shutdown has started, when `close()` does nothing and a running compile
  removes its copies when it ends.  A process that ends with `exit()` while a handle is open leaves that copy in the
  JVM's temporary directory.  A copy over
  a limit fails closed (the system property
  `qore.jni.compiler.maxEntries` can only lower the entries limit).  The default scripting plugin
  is disabled for a restricted compile, as it discovers script definitions on the classpath and runs their code.  The
  JSR-223 script engine takes its classpath from the class loaders and cannot be filtered, so a script of a restricted
  caller is refused while a guarded directory, added directly or named by a manifest, is on its classpath.  A jar is
  used whole once its policy has allowed it, and a directory with no bound policy does not refuse a script.
- **File name encoding.** The bytes that a policy judges use the encoding that is read once when the class is
  initialized, as the file name encoding is fixed at startup.  In a JVM started first with Qore as a library, Java code
  that runs before the module is initialized is part of the trusted host.
- **Limit.** A check precedes the open, so a file replaced in between is not covered.

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
boundary: `sandbox_native()` converts them to Java exceptions, and every native method ends with `JNI_NATIVE_CATCH()`
as the last resort (see `jni-native-exceptions.md`).

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
- `external_io` because their Java calls are uncontrolled APIs (qore#5741): Avro, Email, Excel, ICS, ODP, ODS, ODT,
  PowerPoint, Tika, VCF, Visio, Word. Qore opens the locations (`FileLocationHandler`) and Java only transforms the
  streams it is given; Excel and ODS also create temporary files of their own in `java.io.tmpdir`, with names that no
  caller chooses. A governing sandbox refuses `QDOM_UNCONTROLLED_API` (Java) calls, also from library code running
  for a sandboxed caller, so these apps declare `external_io` to refuse sandboxed callers cleanly up front instead of
  failing in the middle of a call.

The connection and data provider classes of all `external_io` modules call
`DataProviderActionCatalog::checkSandboxAccess(<AppName>)` as the first statement of every constructor, so sandboxed
code that loads such a module and creates its classes directly is refused before any I/O; a class that inherits
another module's class (Busylight from MQTT) is refused by both.  A connection that trusted code created is refused to
a sandboxed caller by `ConnectionProvider::AbstractConnection` itself (`get()`, `getAsync()`, `ping()` and the file
location methods check the modules of the connection's class hierarchy), so no connection method of these modules
does I/O for a sandboxed caller.

`test/data-provider-external-io.qtest` loads every app and checks its declaration against this list, checks that every
constructor of these classes starts with the check, and constructs the classes from sandboxed code.

## Module commands

### By the issuer (module API 2.3, qore#5699)

Built against a Qore with module API 2.3 (`QORE_JNI_HAS_COMMAND_CONTEXT`), the module sets
`QoreModuleInfo::cmd_caller_aware` and `jni_module_parse_cmd()` calls `jni_check_command_issuer()` before it runs a
command. It looks the command up in `jni_command_issuer_rules`, which lists every command of `mcmap`
(`set-compat-types` and `mark-module-injected` are exempt: they change only flags of the target Program; a command
with no entry is refused to every restricted issuer), and calls `QoreModuleCommandContext::check()` with the rule's
mask (`getRestrictingOptions()` for the commands that change state that every Program shares) and
`sandboxed_denied = true`. The context holds the issuer's restrictions, captured by Qore before it switches to the
target Program; code of a library module has no parse options, so only a sandbox restricts the commands that the
provider modules issue at run time and their own directives. A missing context fails closed.

The target-side parse-option checks below (`jni_check_uncontrolled_cmd()`, `jni_check_define_class_restricted()`) are
made only when `jni_check_target_options()` says so: for a command issued into another Program
(`getIssuerProgram() != getTargetProgram()`). For a Program's own command the issuer check covers its options, and a
library module's Program inherits the options of its loader, which must not refuse the module's directives (decision
A). The sandbox checks and the filesystem policy of classpath entries are always made.

"Sandboxed" in this section and the next one means governed by a sandbox that does not trust the code with
uncontrolled APIs (qore#5741): with a Qore that has the uncontrolled-API policy of sandboxes
(`_QORE_HAS_SANDBOX_UNCONTROLLED_API_POLICY`), a sandbox that allows them (`SandboxManager::setUncontrolledApiPolicy()`,
for example one that a host attaches to trusted code only for its interrupts or limits) refuses nothing here.
`QoreModuleCommandRestrictions::isSandboxed()` (the issuer check and `getCallerRestrictions()`) is computed so by Qore,
and the module's own sandbox checks use `jni_sandbox_restricts_caller()` (`src/defs.h`), which is
`!qore_sandbox_allows_uncontrolled_apis()`; without the policy, any governing sandbox refuses. The filesystem policy of
classpath entries is checked under any sandbox.

The exported functions `jni_module_import()` and `jni_module_find_create_java_namespace()` (module-python's
`JavaLoader`) bypass `jni_module_parse_cmd()`; `jni_exported_import_allowed()` applies the `import` rule to them
(`PO_NO_UNCONTROLLED_APIS`; a governing sandbox). With module API 2.3 the caller's restrictions come from
`QoreModuleCommandRestrictions::getCallerRestrictions()`, as for the jdbc option below: the issuer's within a module
command, else the calling code's, so library module code is judged by its sandbox only; before API 2.3 from the declared
parse options of the Program they import into and `jni_sandbox_restricts_caller()`.
`jni_module_find_create_java_namespace()` has no exception sink,
so it refuses with `nullptr`, which `JavaLoader` reports; the `import pkg.*` command uses
`jni_find_create_java_namespace_intern()`, which has no check (the command has been checked). The jdbc DBI driver's
`classpath` option (`QoreJdbcConnection::setOption()`) applies the `add-classpath` rule to the caller
(`PO_NO_UNCONTROLLED_APIS`, `PO_NO_FILESYSTEM`; a governing sandbox). With module API 2.3 the caller's restrictions come
from `QoreModuleCommandRestrictions::getCallerRestrictions()`, which gives library module code only its sandbox (its
Program's parse options are only those inherited from its loader); before API 2.3 from `QoreReflectionCaller` and
`jni_sandbox_restricts_caller()`. These checks are made before module API 2.3 as well.

The option is applied when a connection is opened (`QoreJdbcConnection::parseOptions()` over
`Datasource::getConnectOptions()`), which can be on another thread and for other code than the code that set it: a
`DatasourcePool` opens a connection on the thread that first needs one, and trusted code can open a datasource that
sandboxed code configured. With a Qore library that records the setters of datasource options
(`_QORE_HAS_DATASOURCE_OPTION_RESTRICTIONS`, qore#5741), whoever configured the datasource decides: the option is judged
by `Datasource::getOptionRestrictions()` (the restrictions that `getCallerRestrictions()` gave the code that set it:
`Datasource::setOption()`, the options of a constructor or connection string, or for the connections of a pool the code
that created the pool; copies keep them), and its entries are checked against the setter's filesystem policy
(`Datasource::getOptionPolicyManagerRef()`, `jni_check_classpath_policy()`) instead of the policy of the code that opens
the connection. A setter that a refusing sandbox governs is refused before the entries are checked, so that policy is
the one of trusted code that a sandbox governs only for its limits (it allows uncontrolled APIs): it reads only what its
own policy allows, also when other code opens the connection, and the policy of the opener does not refuse a
configuration that is not its own. An option with no recorded setter, and every option with an older Qore library, is
judged by the code that is running, as before. The jars are added to the class loader of the Program given by
`qore_get_call_program_context()` when the connection is created, which for a method of a `Datasource` or
`DatasourcePool` object is the Program that the object belongs to, not the Program of the code that opens the
connection: a trusted pool that grows on a sandboxed caller's thread adds the jars of its configuration to the trusted
Program's class loader, and a datasource that sandboxed code created adds them to the sandboxed Program's class loader
also when trusted code opens it or a copy of it. The module reports the behavior in its module hash
(`get_module_hash().jni.info.jdbc_option_setter`). `test/jni-jdbc-option-setter.qtest`.

`test/jni-module-cmd-issuer.qtest` (skipped for a module built for an API before 2.3).

### By the target

A module command runs in the Program that it is issued in, also when trusted code issues it with
`Program::issueModuleCmd()` (libqore puts that Program in context), so the checks also follow that Program's sandbox
and parse options (`src/jni-module.cpp`); before module API 2.3 they are the only checks of the module commands:

- `jni_check_classpath_sandbox()`: the entries of `add-classpath` and `add-relative-classpath`, and of the `classpath`
  option of the jdbc DBI driver (`QoreJdbcConnection::setOption()`), split on the path separator and `;` as
  `QoreURLClassLoader.addPath()` splits them, are checked with the filesystem policy as reads
  (`QoreSandboxManagerHelper(Policy)`; for a jdbc option with a recorded setter, the setter's policy, see above)
- `jni_check_uncontrolled_cmd()`: `global-add-classpath`, `global-add-relative-classpath` (the parent classpath that
  every Program shares), `set-property` (JVM system properties), `register-shared-class` (the owning loader of a
  shared dynamic class), `set-java-thread-policy` (the policy of the Java threads of the process, qore#7023) and
  `kotlin-eval` (runs code) are refused (`JNI-SANDBOX-ERROR`) in a Program with
  `PO_NO_UNCONTROLLED_APIS` and to any governing sandbox that does not allow uncontrolled APIs
  (`jni_sandbox_restricts_caller(QoreSandboxManagerHelper::All)`, so a policy barrier or a system-policy capability
  cannot use them either); the `global-*` classpath commands are also refused when the governing policy restricts the
  filesystem, as an entry of the shared classpath carries no policy (qore#7276)
- `jni_check_define_class_restricted()`: `define-class` and `define-pending-class` are refused in a Program with
  `PO_NO_UNCONTROLLED_APIS`, because defining a class runs its static initializer; `define-class` refuses a governing
  sandbox that does not allow uncontrolled APIs (`jni_sandbox_restricts_caller(QoreSandboxManagerHelper::All)`) a
  class whose canonical loader (`Globals::getCanonicalLoader()`) is not the Program's own, so a sandboxed Program
  cannot define the class that another Program loads under that name

`test/jni-module-cmd-sandbox.qtest`.

## Java's calls into Qore from code that a sandbox governs (qore#6845, qore#7015, qore#7023)

Java code is not governed by a sandbox, and the Program pointers that it passes to the native methods are not trusted.
`JniJavaProgramRef::acquire()` (`src/QoreJniClassMap.cpp`) validates a pointer that Java passes (the Program must
still have jni data, and the pointer must still be its class loader's) and, for code that a sandbox governs, accepts
only the Program of the calling code (`jni_governed_caller_reaches()`, below).  Any other Program, such as the host's,
another tenant's or the global Java context Program, is refused with `JNI-ERROR` ("Java code called by code that a
sandbox governs cannot call into a Qore Program other than the one of the calling code").  This applies to calls
(`QoreJavaApi.callFunction()` etc.) and to the generation and listing of the Java classes of Qore classes
(`generateByteCode0()`, `getClassesInNamespace0()`), so an explicit class load through the class loader of another
Program is refused loudly, not reported as a missing class.

### The Program of the calling code (qore#7023)

A call of a Java method of an object runs in the Program that owns the object (`exec_java_method()` enters it, and
sets the thread's Java context to its class loader), and libqore makes the object's Program the Program context of
every method frame (`CodeContextHelperBase`), so neither the current Program nor the Program context of the call
names the caller while the Java method runs.  The module therefore records the caller itself, in a thread-local record
(`JniJavaCallerScope`, `src/defs.h`), at every call from Qore into Java, before it enters any other Program:

| Call from Qore into Java | Recorded caller |
|--------------------------|-----------------|
| builtin calls: a Java method of an object, a static Java method, a constructor (`exec_java_method()`, `exec_java_static_method()`, `exec_java_constructor()`), a member read of a Java object (`JniQoreClass::memberGate()`), the module's functions and `JavaArray` methods that run Java code (`load_class()`, `invoke()`, `invoke_nonvirtual()`, `implement_interface()`, `new_array()`, `define_class()`, `get_byte_code()`, `kotlin_eval()`, `JavaArray::get()`/`set()`/`toString()`/`deepToString()`/`getClass()`) | `callSite()`: the Program that libqore records in the frame of the builtin call, which it pushes before it calls the module (read by pushing a stack location for a moment, `JniCallSiteProbe`); libqore enters the Program of a Java class of another Program for a call of its methods, and makes the object's Program the Program context of the call, so neither names the caller then |
| member accessors of Java fields (`jni_field_get()`/`jni_field_set()`, `jni_static_field_get()`/`jni_static_field_set()`), a Java object called as a Qore callable (`QoreJniFunctionalInterface::execValue()`, before the Program that created the callable is entered) | `current()`: the current Program, which these calls do not change |

`callSite()` has one limit (qore#7028): for code that another Program called directly (ex: a host calling a tenant's
closure), libqore records in the frame of a call that enters another Program the Program context of the call, which is
then the Program that called that code, not the Program of the code.  So a call of a method or static method of a Java
class of the host, made by a tenant closure that the host calls directly, is judged with the host's Program.  With a
Qore library that reports the Program of the call site of the innermost builtin call
(`_QORE_HAS_BUILTIN_CALL_SITE_PROGRAM`, `qore_get_builtin_call_site_program()`), `callSite()` uses it, and the module
hash reports `get_module_hash().jni.info.builtin_call_site_program`; `test/jni-sandbox-host-object.qtest` skips its
qore#7028 case without it.

A module command records the Program that it is issued in (`jni_module_parse_cmd()`, after the issuer checks): it
acts for that Program, also when trusted code issues it into a Program that a sandbox governs (qore#7041).

A record can also reach no Program (`JniJavaCallerRecord::deny`): it is made for the Java code of a call of a Java
method of an object of another Program that has a restricting sandbox of its own (`exec_java_method()`: a tenant's object that
a host calls, or another tenant's object), which is neither the caller's code nor code that the object's sandbox
governs as its caller, and for a Java object used as a Qore closure whose owner is not known (below).

`jni_governed_caller_reaches(pgm)` decides as follows:

1. A record that reaches no Program: no Program.
2. A restricting sandbox governs the thread (`jni_restricting_sandbox_governs()`: the governing sandbox,
   `QoreSandboxManagerHelper(All)`, checked with `jni_sandbox_restricts_io()`): one that restricts filesystem or network
   access, or refuses uncontrolled APIs.  A sandbox that restricts nothing, such as the one that Qorus attaches to
   trusted interfaces only for interrupts, or one that sets only resource limits, does not make its code sandboxed
   (qore#7041):
   with a record, only the recorded Program
   (a record with no Program, when the call has no frame, reaches none); without one, the Program context of the call,
   or the current Program without one, as before.
3. No sandbox governs the thread, and it has a record, or it is a thread of Qore (not attached by the module): any
   Program, as before.
4. A thread that Java started (attached to Qore by the module, `QoreThreadAttacher`, or registered with
   `QoreJavaApi.registerJavaThread()`), which no Qore code calls: its Qore caller is not known.  It reaches any
   Program until code that a sandbox governs has called Java in the process (`jni_governed_java_used`, set by
   `JniJavaCallerScope` on such a call, or by the `set-java-thread-policy strict` module command), and from then on
   no Program.

The fourth rule is process-wide on purpose.  The work that such a thread runs can come from any code: a pool reuses
its threads for the work of every caller, and a thread created during the call of one Program runs the work of others
later, so the thread cannot carry its caller (an `InheritableThreadLocal` that gave the threads created by code that a
sandbox governs the token of its Program let the work of another tenant on such a pool thread reach that Program).
Code that a sandbox governs can reach every Program's class loader from the JVM's state without calling that Program
(the parent of its own class loader, the context class loaders of all threads, `Class.getClassLoader()` of shared
classes), so restricting only Programs that such code "touched" is not sound either.  The latch is set only by a call
into Java from code whose sandbox allows uncontrolled APIs, as Java calls are refused to any other sandboxed code, so
only an embedder that gives Java to sandboxed code enables it; such an embedder enables it up front with the module
command, so that its trusted code runs under the same rule before and after the first tenant call
(`test/jni-java-thread-strict.qtest`).  A process with trusted code only is unchanged
(`test/jni-java-thread-trusted.qtest`).

The work that Qore code hands to Java carries its caller: the callbacks capture the sandbox of the code that created
them and run in their own Program (`Dispatcher`, `JniObjectHandle`, `JniClosureHandle`).  The methods of an existing
Qore object and Qore closures that Java calls with a Program pointer (`QoreObject.callMethod()`,
`QoreClosure.call()`) are not refused when the calling code cannot reach that Program: the Program is not used, and
the call is made in the context of the callback's own Program (`qore_object_closure_call_java_ptr()`, which enters it
first with `jni_enter_call_program()`, so that its jni data cannot be destroyed during the call), so a Program pointer
that Java chooses never becomes the context in which Qore values are converted.

A Java object used as a Qore closure (`QoreJniFunctionalInterface`) is a callback of the code that obtained it from
Java (`jni_java_caller_owner()`): the code that called Java; in a call from Java into Qore, which clears the record,
the code that called that Java code (the record that the clearing scope saved, `jni_java_supplier`); and otherwise
none, which reaches no Program.  So a Java closure that a tenant hands to a host listener, or passes through a Java
object of the host, stays the tenant's.

### The Java code on the stack

The record names the Qore code that called Java, not the Java code that runs: a host can run Java code that a
sandboxed Program defined (a method of a tenant's object, also through a wrapper of the host for it; a tenant's listener
that Java code of the host calls; `toString()` of a tenant's object; a static method or the static initializer of a
tenant's class), and that code then runs with the host's record.  So every check of `jni_governed_caller_reaches()`
also walks the Java stack (`jni_stack_allows()`, `QoreJavaApi.getStackProgramPtrs()`, a `StackWalker` with class
references), and refuses the call if a frame's class is attributed to a sandboxed Program other than the target
(`jni_program_ptr_sandboxed()`: a Program whose own sandbox restricts; with a Qore library that has
`qore_program_get_creator_sandbox()` (`_QORE_HAS_PROGRAM_CREATOR_SANDBOX`), also a Program one of whose creator's
managers restricts, by the same rule: the governing one, which walks out to a sandboxed caller through a policy barrier
(a Program that trusted code created for a sandboxed caller), or the policy one, so a sandbox of its own that restricts nothing does not hide it, and a creator's sandbox
that restricts nothing does not make it sandboxed; a pointer that names no Program with jni data, as of a Program
that has been deleted).  The
class loader that defined a frame's class is attributed by `QoreJavaApi.getLoaderProgramPtr()`:

- the class loaders of the JDK and of the JVM's classpath (the bootstrap, platform and system class loaders): trusted,
  ignored;
- otherwise, the nearest object of this module's `QoreURLClassLoader` class (by identity: a class of another class
  loader with the same name does not count) among the class loader and its parents that has a Program: its Program,
  or, if its pointer has been cleared (its Program has been deleted, or `clearProgramPtr()`), the first Program that it
  had (`getOriginPtr()`; a derived class loader keeps the origin of its parent), so the classes of a deleted tenant
  stay a deleted, sandboxed Program's;
- any other class loader (one that Java code created, with no class loader of a Program among its parents): unknown,
  sandboxed.  Trusted libraries that define classes in class loaders of their own are therefore refused calls into
  Qore by a Program pointer from those classes once the latch is set.

The jars of the classpath that every Program shares are defined by the class loader of the first Program, which is
trusted.  The stack is walked only once the latch of the fourth rule is set, as no sandboxed Program has Java code of
its own before; a process with trusted code only pays nothing.  Without `qore_program_get_creator_sandbox()`, the Java
code of a Program that a tenant created, which has no restricting sandbox of its own, is not attributed (qore#7028).

### The class loaders cannot be changed for another Program

The attribution relies on the Program pointers of the class loaders, which the public Java API of `QoreURLClassLoader`
could set: Java code of a tenant could create a class loader for the global Java context Program
(`new QoreURLClassLoader(name, parent)`, which takes the pointer of `getContextProgram0()`), one with no Program
(`createWithoutProgram()`), clear the pointer of its own class loader, or add classes and classpath entries to
another Program's class loader.  Each such operation is now checked with `jni_governed_caller_reaches()` for the
Program of the class loader (`QoreURLClassLoader.checkAccess()`, native `checkProgramAccess0()`):
`getContextProgram0()` (the public constructors), `createWithoutProgram()` (Program 0), `createDerived()`, `add()`,
`addPath()`, `addParentPath()`, `addPathOrig()`, `addWildcard()`, `addPendingClass()`, `removePendingByteCode()`,
`clearCache()`, `clearPendingClassesCache()`, `clearAllCaches()`, `defineClassUnconditional()`,
`defineResolveClass()` and `clearProgramPtr()`.  The module's own code uses package-private variants (`*Intern()`),
also from C++ (module commands, the teardown of a Program, class definition), which are not checked.  So a tenant
changes only its own class loaders, and cannot clear the pointer of the host's class loader either, which would break
the host's calls into Qore.

### A recorded caller that is a sandboxed Program

The record of a Java closure's call is its owner (`QoreJniFunctionalInterface`), which can be a sandboxed Program while
no sandbox governs the thread: a tenant's Java closure that it obtained through a Java object of the host runs in the
host's Program.  Once the latch is set, a recorded Program that is sandboxed reaches only itself, also on a thread that
no sandbox governs.

### What the module cannot attribute: Java objects that sandboxed code supplies

A Java object has no Program: when host code runs a Java object or closure that sandboxed code supplied or could have
built (a listener or `Runnable` in a host data structure, `toString()`, a closure that host Java code forwards), its
calls into Qore are governed by the host.  If it is made of classes of a sandboxed Program, the attribution of the
stack refuses them; if it is made only of classes of the JDK or of trusted class loaders (ex: a `MethodHandleProxies`
or `Proxy` instance over `QoreJavaApi.callFunction()`), nothing on the stack names the tenant.  So a host must not run
Java objects or closures that sandboxed code supplied or could have built; it wraps them in a Qore closure that it
controls, or does not accept them.  A generic Java closure that host Java code passes on to a callback of the
sandboxed Program gets the host as its owner, as the code that called that Java code; the same rule applies.

What changes for trusted code: in a process in which code that a sandbox governs has called Java, or after
`set-java-thread-policy strict`, a Java thread that no Qore code calls (a pool thread, a listener thread) cannot call
into Qore by a Program pointer (`QoreJavaApi`, the class loader of a Program, the generated classes); it uses
callbacks, which are not affected.

Every call from Java into Qore code clears the record for the Qore code that it runs (`QoreJniStackLocationHelper`,
which every such native constructs, holds a clearing `JniJavaCallerScope`; `qore_object_create()`,
`qore_object_destroy()` and `qore_object_release()`, which run constructors and destructors, clear it explicitly;
`QoreJniFunctionalInterface::execValue()`, a call from Qore into Java that also constructs the helper, records its
caller after it): that code records its own Program when it calls Java in turn, and a call into Java that records nothing is
judged by the rule without a record, never with the caller of an enclosing call.  The checks of a native are made
before it clears the record.  The scopes restore the previous record when they end, also when an exception
propagates, so nested and re-entrant calls (host code, Java, a callback into the host, a call of the tenant, the tenant
calling Java) each have their own caller.

Objects that the calls return are created in the object's Program, as before, so an object reached from an object of
the host is also the host's; that gives no reach, as each call of its methods records the tenant's Program again.

### Which calls from Java into Qore are checked

Directed at a Program, and checked with `jni_governed_caller_reaches()` (`jni_check_governed_caller_reaches()` throws
the refusal as `JNI-ERROR`):

- a Program pointer that Java passes (`JniJavaProgramRef`): `QoreJavaApi` calls, class generation and listing;
- the fallbacks of `QoreJavaApi.callFunction*()` and `callStaticMethod*()` to the Program context of the call, for a
  function or class that the class loader's Program does not have (Qorus Java workflow steps): only into the Program
  of the calling code (`jni_java_api_fallback_program()`, decided before the record is cleared);
- `QoreJavaApi.newObjectSave()`, which creates the object in the Program context of the call;
- the Java classes generated for the Qore definitions of a Program (`JavaClassBuilder`): constructors
  (`qore_object_create()`: the Program of the class, and the Program that the object is created in), static methods
  (`doStaticCall0()`), functions (`doFunctionCall0()`, the `$Functions` classes) and constants
  (`getConstantValue0()`) of the binding's Program, checked by `GeneratedBindingContext` before it enters the
  Program, so a refused call runs none of its code (an entry handler, thread initialization code).  A generated class can be reached without any object of the other
  Program: the parent of a Program's class loader is the class loader of another Program, which defined the classes
  that its Program generated.

Not checked, by design: Qore code that a Program hands to other code as a callback runs in its own Program, as a
closure or an object method does in Qore.  This covers the methods of an existing Qore object called from Java
(`doNormalCall0()`, `QoreObject.callMethod()`, member reads), Qore closures (`QoreClosure`), and interfaces that a
Program implements with a `QoreInvocationHandler` (`invocation_handler_invoke()`, `Dispatcher`).  The Program has
handed the object or closure to the code that uses it; code that a sandbox governs cannot create one of another
Program, as the constructors and static methods of that Program's generated classes are refused to it.  A Java object
of another Program whose own Java code calls into Qore by a Program pointer (ex: `QoreJavaApi.callFunction()`) is not
such a callback: its call is governed by the calling code.  A host that wants to give sandboxed code a callback into
its own Program hands it a Qore closure or object.

The values that a Program has already published in Java (ex: an initialized constant of a generated class, a Qore object
stored in a static Java field) remain readable to Java code, as any Java state is: the module does not hide Java
state, it governs only Java's calls into Qore.  A Qore object or closure that Java code can find (in a static field,
or in any field read by reflection) is also **callable**: all of its methods run in its own Program, under the
sandbox captured when it was handed to Java, as for any callback.  A host must not leave Qore objects or closures in
Java state that a tenant's Java code can reach.

### The limit of these checks: Java code itself

A sandbox that allows uncontrolled APIs, and so Java, is not an isolation boundary against a malicious tenant.  The
Java code that such a tenant runs has the JVM's rights (by default the module starts the JVM with native access
allowed for unnamed modules and, from Java 23, `sun.misc.Unsafe` memory access allowed), and can read or change any
state of the process.  The checks described
here govern Java's calls into Qore through this module's APIs; they are defense in depth for code that a sandbox
trusts with Java, and do not contain Java code written to escape.  Code that must be isolated is not given
uncontrolled APIs.

`test/jni-sandbox-host-object.qtest` covers each entry point (a class loader of another Program, any Java object of
another Program, chains of returned objects, fields, static members, constructors, exceptions, the generated classes of
another Program), the callbacks that stay allowed, re-entrant calls, and the same code without a sandbox.

A module command acts for its target Program when trusted code issued it (qore#7041): the command runs with the
target Program current, but the Program context of the call stays the one of the issuing code (ex: the Program in
which the `Program` object that `Program::issueModuleCmd()` was called on was created), so the class loader of the
target Program generating the byte code of a `qore.*` superclass for `define-class` was "another Program".
`jni_module_parse_cmd()` (`src/jni-module.cpp`) records the target Program as the caller of the Java code that the
command runs (`JniJavaCallerScope`), after the checks of the issuer (`jni_check_command_issuer()`).  When a
restricting sandbox governs the command (the target's own, or the issuing code's), the record reaches only the target
Program, never the issuing code's Program (or the parent class loader's Program), also from Java code of the target
Program that the command runs, such as a static initializer of a class that `import` loads.  When no restricting
sandbox governs it (no sandbox, or one that restricts nothing), the command's Java code is trusted code and is not
restricted (rule 3).  The record grants nothing new to governed code: the core refuses a command from a sandboxed
Program into another Program, and when governed code runs a command into another Program through code of a trusted
Program (ex: a closure of the host that it calls), the record reaches only that Program, and
`jni_check_command_issuer()` refuses commands to code whose sandbox refuses uncontrolled APIs.  Other code that runs
during the command (a method or closure of another Program that Java calls) clears the record at its Java→Qore
boundary and is judged by its own Program; Java threads have no record
(`test/jni-sandbox-trusted-define-class.qtest`).  A command into a Program whose sandbox restricts sets the latch of
the fourth rule, like any call into Java from governed code.

Two class lookups are not directed at another Program by the calling code, and make no native call with that
Program's pointer; `QoreURLClassLoader` asks first with `programReachable0()`, which only compares the pointer with
the Program of the calling code (`test/jni-sandbox-compiler-fallback.qtest`):

- the parent fallback: the parent of the class loader of a Program is the class loader of another Program (the
  Program that the module was loaded in first, or the global Java context Program).  A dynamic class or namespace that
  the class loader's own Program does not have is not looked up in the parent when the calling code cannot reach the
  parent's Program (`parentProgramReachable()`): `loadClass()` searches only the class loader's own classpath and
  then throws `ClassNotFoundException`, and `getClassesInNamespace()` returns no classes.  The class is not found
  because the calling code cannot see the parent's Program (a class that only the parent's Program has is not found
  either), so optional lookups fail as optional lookups do, not with the refusal: `QoreKotlinCompiler` loads
  optional `qore.*` base classes for its stubs, `javac` lists the packages of on-demand imports, and Java code can
  probe for a class with `Class.forName()`.
- `kotlin.*` classes (`kotlinProgramReachable()`): the `kotlin` package holds the dynamic classes for the Kotlin
  language and also the Kotlin standard library, so a `kotlin.*` class is generated from the class loader's Program
  first and is otherwise loaded from the classpath.  The Kotlin compiler's jars are usually on the classpath that every
  Program shares (`global-add-classpath`, as in Qorus), so its classes are defined by the class loader of another
  Program, and the JVM loads the standard library classes that they link to with that class loader.  When the calling
  code cannot reach that class loader's Program, the class is not generated there at all and is loaded from the
  classpath.  Only `kotlin.*` names are treated so, as only they collide with ordinary JVM classes; `qore.*`,
  `qoremod.*` and the other dynamic names are still generated with the Program's pointer, and refused for another
  Program.  A `kotlin.*` class that a class loader shared by several Programs defines so from the classpath stays
  defined there, as for any class that a class loader has loaded.
