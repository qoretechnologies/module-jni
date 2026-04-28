package org.qore.jni;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//! Native-pointer cleanup for module-jni wrapper classes.
/** module-jni's wrapper classes (QoreInvocationHandler, QoreClosure,
    QoreExceptionWrapper, QoreObjectBase) historically used
    java.lang.ref.Cleaner to dispatch native release0() calls when a wrapper
    becomes phantom-reachable.  That pattern lights up two JVM races:

    1. The default Cleaner runs callbacks on a daemon Common-Cleaner thread.
       At JVM shutdown the JIT codecache is torn down before daemons are
       joined; if a callback is mid-flight through a JIT-compiled
       CleanupState.run() that calls release0 via a JIT trampoline, the
       trampoline page is unmapped and the dispatch faults with
       SIGSEGV (SEGV_ACCERR).

    2. JNI native-method invocation adapters live in codecache regions that
       can be reclaimed when the defining classloader is unloaded — even
       outside JIT flushing.  We have observed the faulting page reused for
       JVM heap data (e.g. java.security.PermissionCollection bytes) while
       the cleaner thread holds a stale adapter address.  Compile-time JVM
       tuning (-XX:CompileCommand=exclude, -XX:-UseCodeCacheFlushing) does
       not eliminate either race in practice.

    NativeCleanup replaces the Cleaner-based path with a direct
    PhantomReference + ReferenceQueue model:

      - Each wrapper allocates a {@link Ref} pinning the (native ptr, kind)
        pair via a PhantomReference to the wrapper.
      - A dedicated C++ thread (spawned from Jvm::createVM in module-jni)
        blocks on queue.remove() and dispatches the native cleanup directly
        in C++ code via a switch on kind.  The dispatch never goes through
        a JNI native-method invocation adapter, so it is immune to JIT
        codecache reorganisation and class-unloading-driven adapter eviction.

    Wrapper classes obtain a Ref from {@link #register} and store it for the
    instance's lifetime.  Explicit teardown paths (e.g. QoreObjectBase.destroy)
    call {@link #unregister} to clear the Ref so the C++ thread does not also
    fire on the same pointer.

    Shutdown: Jvm::destroyVM in module-jni calls {@link #shutdown}, which
    enqueues a sentinel Ref (kind=-1) onto the queue.  The C++ thread drains
    any remaining real Refs, sees the sentinel, and exits.  Module-jni then
    joins the thread before tearing down the JVM.
 */
public final class NativeCleanup {
    /** PhantomReference subclass carrying the native pointer and a kind
        discriminator the C++ dispatcher uses to pick the right release path.
        Fields are exposed so the C++ thread can read them via JNI field IDs
        without a Java method round-trip. */
    public static final class Ref extends PhantomReference<Object> {
        /** The native pointer.  Volatile so the C++ thread sees the latest
            value if {@link #acquireAndClear} runs concurrently with phantom
            enqueue (the C++ thread will see 0 and skip the native call). */
        public volatile long ptr;
        public final int kind;

        Ref(Object outer, long ptr, int kind, ReferenceQueue<? super Object> q) {
            super(outer, q);
            this.ptr = ptr;
            this.kind = kind;
        }

        /** Atomically take ownership of the native pointer.  Returns the
            previous value and resets {@link #ptr} to 0; subsequent phantom
            dispatch will see 0 and skip the native release.  Used by
            explicit teardown methods (release / destroy / get) that take
            ownership of the pointer themselves. */
        public synchronized long acquireAndClear() {
            long p = ptr;
            ptr = 0;
            return p;
        }
    }

    /** Sentinel kind used at shutdown to wake the C++ thread blocked on
        queue.remove().  See {@link #shutdown}. */
    public static final int KIND_SENTINEL = -1;

    /** invocation_handler_finalize: delete (Dispatcher*)ptr. */
    public static final int KIND_INVOCATION_HANDLER = 0;

    /** qore_closure_finalize: ((ResolvedCallReferenceNode*)ptr)->deref(&xsink). */
    public static final int KIND_CLOSURE = 1;

    /** qore_exception_wrapper_finalize: xsink->clear(); delete xsink. */
    public static final int KIND_EXCEPTION_WRAPPER = 2;

    /** qore_object_finalize / pointerRelease0: ((QoreObject*)ptr)->tDeref(). */
    public static final int KIND_OBJECT_BASE_WEAK = 3;

    /** Shared ReferenceQueue.  The C++ cleanup thread blocks on
        {@code queue.remove()} and dispatches by Ref.kind. */
    public static final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    /** Strong refs to live {@link Ref} instances.  PhantomReference itself
        must remain strongly reachable until GC enqueues it; a Ref whose only
        reference came from the Cleanable would be eligible for collection
        before its referent and silently disappear.  Holding a strong ref
        here does NOT pin the wrapper outer (the Ref does not reference its
        referent strongly — that is the whole point of PhantomReference). */
    private static final Set<Ref> live = ConcurrentHashMap.newKeySet();

    /** Register a wrapper instance for phantom-driven native cleanup.
        @param outer the wrapper instance (held only via PhantomReference)
        @param ptr the native pointer to release when {@code outer} becomes
            phantom-reachable
        @param kind one of the KIND_* constants; selects the C++ dispatch path
        @return a {@link Ref} the wrapper should hold for explicit-teardown
            handoff via {@link #unregister}
    */
    public static Ref register(Object outer, long ptr, int kind) {
        Ref r = new Ref(outer, ptr, kind, queue);
        live.add(r);
        return r;
    }

    /** Suppress phantom cleanup for a Ref whose native pointer has already
        been released by an explicit teardown path.  Clears the Ref's
        pointer (so any concurrent phantom dispatch becomes a no-op),
        deregisters the Ref from the queue, and drops the strong ref. */
    public static void unregister(Ref r) {
        if (r == null) {
            return;
        }
        r.acquireAndClear();
        r.clear();
        live.remove(r);
    }

    /** Called by the C++ cleanup thread after dispatching native release
        for {@code r}.  Drops the strong ref so the Ref itself is GC-eligible. */
    public static void markProcessed(Ref r) {
        live.remove(r);
    }

    private static final Object SENTINEL_REFERENT = new Object();
    private static final Ref SENTINEL = new Ref(SENTINEL_REFERENT, 0, KIND_SENTINEL, queue);
    static { live.add(SENTINEL); }

    /** Wake the C++ cleanup thread by enqueueing the sentinel.  Called from
        Jvm::destroyVM in module-jni's C++ shutdown path; the C++ thread sees
        kind = KIND_SENTINEL and exits its main loop. */
    @SuppressWarnings("deprecation")  // Reference.enqueue() is documented and stable
    public static void shutdown() {
        SENTINEL.enqueue();
    }

    private NativeCleanup() {}
}
