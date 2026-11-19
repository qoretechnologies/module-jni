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
state.  A C++ exception other than `jni::JavaException` replaces a pending Java
exception.

The handler is the last resort: native methods still handle the errors that they expect themselves (ex: a Qore
exception raised while the thread is attached and the Program is current), and the handler covers everything that
escapes them, including C++ exceptions thrown from their catch handlers.

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

## Tests

`test/jni-native-exception.qtest` uses two functions of a debug build of the module (`src/ql_jni_debug.cpp`):
`dbg_jni_native_throw()` throws a chosen C++ exception at the start of the next call of a Qore function, closure or
method from Java on the thread, and `dbg_jni_fail_find_class()` makes `jni::Env::findClass()` of a class fail.

`test/test-jni-native-catch.py` (ctest `jni_native_catch`) checks the sources: every function in a `JNINativeMethod`
table is a function-try-block that ends with `JNI_NATIVE_CATCH(jenv, ...)`, and no error path calls
`throwNew(env.findClass(...))`.  A new native method that lacks the handler fails it.
