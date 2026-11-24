# C++ Exceptions in JNI Native Methods

No C++ exception may leave a JNI native method: it would unwind through JVM frames, and the process ends in
`std::terminate()` (qore#6739).

## Top-level handler

Every native method that the module registers with `RegisterNatives()` (the `JNINativeMethod` tables in
`src/Globals.cpp` and `src/JavaSandbox.cpp`) is a function-try-block that ends with `JNI_NATIVE_CATCH()`
(`src/Globals.h`):

```cpp
static jobject JNICALL my_native(JNIEnv* jenv, jclass cls) try {
    // ...
} JNI_NATIVE_CATCH(jenv, nullptr)
```

The handler calls `jni_native_exception()`, which rethrows the C++ exception and raises it as a pending Java exception
without throwing:

| C++ exception | Java exception |
|---|---|
| `jni::JavaException` | the pending Java exception (`java.lang.Error` if none is pending) |
| any other `jni::Exception`, or other Qore exception (`AbstractException`) | the Qore exception as `org.qore.jni.QoreExceptionWrapper` |
| `std::bad_alloc` | `java.lang.OutOfMemoryError` |
| any other `std::exception` | `java.lang.Error` with `what()` |
| anything else | `java.lang.Error` |

The locals of the native method, including the `QoreThreadAttachHelper` that attached the thread to Qore, are
destroyed before the handler runs, so the conversion of a Qore exception attaches the thread itself if it is not
attached.  It is not attempted once libqore or the module is shut down, or if the thread cannot be attached; a
`java.lang.Error` is raised instead, and the Qore exception of a `jni::XsinkException` is deliberately leaked
(`XsinkException::leak()`), as freeing it there would report it as unhandled and free Qore values in an invalid
state.  Only `jni::XsinkException` holds Qore values among the C++ exceptions that the module throws by value;
libqore's `QoreXSinkException` cannot be leaked this way.  A C++ exception other than `jni::JavaException` replaces a pending Java
exception.

The handler is the last resort: native methods still handle the errors that they expect themselves (ex: a Qore
exception raised while the thread is attached and the Program is current), and the handler covers everything that
escapes them, including C++ exceptions thrown from their catch handlers.

## The lifetime of the Qore exception of a `QoreExceptionWrapper`

A `QoreExceptionWrapper` holds its Qore exception (a heap `ExceptionSink`, `QoreToJava::newExceptionWrapper()`)
through a `NativeCleanup.Ref`.  The exception is released when Qore takes it (`QoreExceptionWrapper.get()`, when the
wrapper is thrown back to Qore: `JavaException::convert()`), when the wrapper is collected, and at the module's
shutdown (`Globals::stopNativeCleanupThread()` releases the exception of every wrapper that still exists).  Each
release claims the pointer with `Ref.acquireAndClear()`, which sets it to 0 under the monitor of the `Ref`.  Java can
keep the wrapper after its exception has been released, so `QoreExceptionWrapper.getMessage()` reads the pointer, and
calls the native method with it, under that monitor, and gives a fixed message for a released exception (0) instead
of calling the native method; the native method also refuses 0 (qore#6864).  `JavaException::toString()`, which
describes a pending Java exception, describes a wrapper with its message and never takes its exception.

## The native handles of the Java wrappers

The value that a Java wrapper holds is an opaque handle of the registry in `src/JniHandleRegistry.h`, never a native
address (qore#7068):

- A handle is a keyed permutation of a counter, so it is never issued twice in a process and cannot be derived from
  another handle.  An entry has a kind (object, closure, invocation handler, exception, sandbox policy, connection
  waiter); a lookup of another kind finds nothing.
- A lookup returns a `std::shared_ptr` that the native method holds for the call, so a release on another thread
  cannot free the state during its use.  A release removes the entry, and only one caller gets it; the state is
  destroyed with its last reference, outside of the registry's locks, on a thread attached to Qore and with any
  pending Java exception kept (`jni_release_native_state()`).
- A value that is not a live handle of the expected kind raises a Java exception.  The cleanup thread ignores it.
- The constructors that take a handle are not public.  `JavaClassBuilder` can be defined by another class loader than
  the wrapper classes, so it cannot use their package-private methods: the native method of a generated call holds
  the handle of the wrapper itself.
- A generated method runs only on an object whose class hierarchy has the method's class ID, with two exceptions, in
  which the object's own method of the bound name runs, never a name from Java: a class that a declared injection
  (`Program::importClass()` with injection) relates to the method's class, also when the object's class stands in for
  a class that inherits the method's class, and the same class of another Program when both Programs registered its
  Java name with the `register-shared-class` module command.  The module records those registrations natively; a
  registration made only through the Java class loader method grants neither.  For a shared class, the
  registrations are checked first, outside any entry, so that a call on the object of a Program that did not register,
  or that has been deleted, runs no code of that Program.  The class itself is then compared within the call's own
  entry of the object's Program, before any of the call's code: the class of the path is looked up in that Program and
  compared with the object's class by ID, so that nothing is read from a namespace that the teardown of a Program can
  delete.  An accepted call enters the object's Program once, as any call of a method of the object; a call that passes
  the registration check and is then refused has run the Program's entry and exit handlers once, and its thread
  initialization code if it was the thread's first entry, and none of the call's code.  A class of the same name and
  signature is otherwise another class.  In these two cases, the Java varargs array of a variadic bound method is
  passed as separate arguments if the object's class, or a parent of it, has any variadic variant of a normal or
  static method of that name, and otherwise as one list; the choice is made by method name before Qore selects the
  variant, so a class that overloads that name with both variadic and list variants can get separate arguments where
  its list variant was meant.  An object is constructed only with the constructor
  of the Java class's own binding, or for a Java subclass, of its nearest generated superclass, as the private data of
  an object is typed by class.  Each Qore class of a Java class records its superclass when it is made, and these links
  are followed to the generated class when an object is constructed.
- The handles do not make Java code an isolation boundary: code that a host allows to use Java can use reflection.
  The registry ensures that a value from Java is used as a native handle only if it is a live handle of the expected
  kind.  It does not cover the lifetime of what libqore's class metadata refers to, such as the target of an injected
  class.  Program pointers are not handles: a class loader and the native calls that name a Program still pass its
  raw address in Java, and the module validates it against the Programs that have jni data, and holds the Program,
  before it dereferences it (see `JniJavaProgramRef`).

Every Java wrapper with a native handle holds it in a `NativeCleanup.Ref`: `QoreObjectBase` (and `QoreObject`, and
the generated classes), `QoreClosure`, `QoreInvocationHandler`, `QoreExceptionWrapper` and `QoreSandbox.Policy`.  The
pointer is released by an explicit release (`release()`, `destroy()`, `close()`, `get()` of a wrapper), by the C++
cleanup thread when the wrapper is collected, and by the module's shutdown, which releases the pointer of every
wrapper that still exists; Java can keep a wrapper after any of them (qore#6864).

- A call that uses the pointer holds it for its duration: `Ref.enter()` returns the pointer (0 once it has been
  released, which the wrapper raises as `IllegalStateException`, or `JNI-SANDBOX-ERROR` for a closed policy) and
  counts the use per thread, and `Ref.exit()` ends it, in a `finally` block; no lock is held during the call, which
  can run any Qore and Java code.  Native code that reads the pointer of a wrapper (`JavaToQore`) holds it the same
  way, with `qoreJniEnterHandle()` and `qoreJniExitHandle()`.  The generated classes pass the object itself to
  `JavaClassBuilder.doNormalCall()`, whose native method holds its handle; the overloads that take the handle remain
  for classes generated before, and validate it.
- An explicit release (`release()`, `destroy()`, `close()`) claims the pointer (`Ref.acquireAndClear()`: 0
  afterwards, so no new use begins) and then waits until the uses on other threads have ended; a release on a
  thread that uses the pointer cannot wait for itself, and is refused with `IllegalStateException`, changing
  nothing.  So a release must not be made while a call that uses the object waits for the releasing thread.
- The cleanup thread and the module's shutdown never wait (`Ref.acquireForCleanup()`): a pointer with a use in
  progress on any thread is cleared, so that no new use begins, and left unreleased, valid for the call; it is
  released with the process.  The module's shutdown must not wait: a call in progress can block until the process
  ends (ex: a Java listener thread blocked in the Qore code of a module, which the Qore library leaves running at
  its shutdown, see `qore_program_private::leaveForeignThreads()`), and waiting for it kept the process from exiting
  (test/jni-released-handles.qtest, the "shutdown-in-use" case).  The cleanup thread sees no use: every call keeps
  its wrapper reachable until its use has ended (it calls the wrapper's `qoreJniExitHandle()`, or
  `Reference.reachabilityFence()`), so a wrapper is phantom reachable only once no call on it is in progress.
  A call left running is valid only as far as its handle goes: if it returns while the module is torn down, it
  returns into the module's state as it is then, as the Qore code that the Qore library leaves running does.
- The module's shutdown still waits in two places, by design: for the callers that hold the global context Program
  (qore#6829, `Globals::clearGlobalContext()`), and in an explicit release made during the teardown (ex: a Qore
  `QoreInvocationHandler` object destroyed while one of its proxy calls is in progress on another thread).
- `QoreExceptionWrapper.getMessage()` holds the monitor of the `Ref` instead, as its native method runs no Qore code
  that could release the wrapper.
- `NativeCleanup.setReleaseWaitHook()` lets a test make a release wait deterministically.

## Raising Java exceptions without throwing

The error paths of native methods raise Java exceptions with `jni_throw_new()` and its wrappers
(`jni_throw_error()`, `jni_throw_runtime_exception()`, `jni_throw_out_of_memory()`,
`jni_throw_class_not_found()`), which are `noexcept`.  They use the classes that `Globals::init()` looks up before
any native method is registered (`Globals::classError`, `classRuntimeException`, `classOutOfMemoryError`,
`classClassNotFoundException`), never `jni::Env::findClass()`, which throws `jni::JavaException` if the lookup
fails.  These are `std::atomic<jclass>` global references that are never deleted (the bootstrap classes are never
unloaded), so that a native method that runs while the module is cleaned up on another thread never uses a deleted
reference.  Before initialization, the class is looked up with JNI's `FindClass()`; if that fails, its
Java exception is pending instead.  Only if no Java exception at all can be made pending, which JNI does not allow,
is the process ended with `FatalError()`, as returning with no exception pending would report a failed call as a
call that succeeded.

## Code that libqore calls

libqore does not expect C++ exceptions from the code of a module that it calls either; one that escapes there ends
the process in `std::terminate()`.

- Builtin functions and methods (`src/*.qpp`) convert a `jni::Exception` to a Qore exception with
  `catch (jni::Exception& e) { e.convert(xsink); }` around all code that can reach JNI (qore#6789).
- The teardown paths, where there is no exception sink: the module's delete function (`jni_module_delete()`), its
  `atexit()` handler (`jni_atexit_destroy_vm()`), the Program cleanup callback (`jni_program_cleanup()`), the
  rollback of a failed initialization (`jni_module_init_rollback()`) and `Jvm::destroyVM()` are `noexcept`; each
  step that can throw runs in `jni_teardown_step()` (`src/defs.h`), which reports a C++ exception on stderr with
  `jni_report_current_exception()` (`jni module: <step> failed: <err>: <desc>`) and continues with the next step
  (qore#6788).  The `atexit()` handler, which can run after the Qore library has been cleaned up, reports a
  `jni::Exception` without converting it to a Qore exception (`jni_report_set_plain()`).  If stopping the
  NativeCleanup thread fails before the worker is woken, the worker stays blocked in the JVM (a daemon thread, which
  `DestroyJavaVM()` does not wait for; with a JVM passed in with `jvm-ptr`, the JVM is not destroyed); the failure
  sets `native_cleanup_abandoned`, so that a reference that wakes it later is dropped (its native object is leaked)
  instead of dispatched during or after the teardown; a dispatch already in progress when the stop failed still runs.
  If it fails after the sentinel was queued, the worker ends on the sentinel or drops it.  If it fails after the
  worker was joined, the handles not yet released are leaked.  The test covers the failure before the worker is
  woken.
- `JniExternalProgramData::copy()`, which libqore calls from the constructor of a child Program, is `noexcept`:
  `AbstractQoreProgramExternalData::copy()` cannot report an error, so a failure is reported on stderr, and
  `nullptr` is returned, with which the child Program is created without the module's data, as with
  `PO_NO_INHERIT_PROGRAM_DATA` (qore#6787; failing the creation of the child Program needs a sink-taking
  `copy()` in libqore, qoretechnologies/qore#6786).
- A Program without jni data (above, or created with `PO_NO_INHERIT_PROGRAM_DATA`) still has the module's feature
  and namespaces, so its code can call the jni builtins; `jni_get_program_context()` returns `nullptr` there.  The
  builtins get their Program with `jni_get_create_context()` (the Program found as by `jni_get_context()`, else the
  calling code's) or `jni_get_create_call_context()` (always the calling code's), which create the Program's jni
  data on demand with `JniExternalProgramData::getCreateJniProgramData()`, as a jni module command issued in the
  Program does; the creation can throw a `jni::Exception`, which the builtin converts (qore#6801).

## Tests

`test/jni-native-exception.qtest` uses two functions of a debug build of the module (`src/ql_jni_debug.cpp`):
`dbg_jni_native_throw()` throws a chosen C++ exception at the start of the next call of a Qore function, closure or
method from Java on the thread, and `dbg_jni_fail_find_class()` makes `jni::Env::findClass()` of a class fail.

`test/test-jni-native-catch.py` (ctest `jni_native_catch`) checks the sources: every function in a `JNINativeMethod`
table is a function-try-block that ends with `JNI_NATIVE_CATCH(jenv, ...)`, and no error path calls
`throwNew(env.findClass(...))`.  A new native method that lacks the handler fails it.

`test/jni-cpp-exception-escape.qtest` uses `dbg_jni_fail_point()` of a debug build, which makes the code at a point
(attaching a thread in `jni::Env`, creating or copying a Program's jni data, stopping the NativeCleanup thread) throw
the C++ exception that it throws when it fails, once, on the thread that set it; each case runs in a child process.
