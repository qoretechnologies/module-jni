// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
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

    Shutdown: once native clients are quiescent, Jvm::destroyVM calls
    {@link #shutdown} to close registration, snapshot all outstanding handles,
    and wake the C++ thread with a sentinel. After joining the thread, it
    claims and releases every remaining handle, including wrappers not yet
    collected by Java. Queue ordering and garbage collection are irrelevant.
 */
public final class NativeCleanup {
    /** PhantomReference subclass carrying the native pointer and a kind
        discriminator the C++ dispatcher uses to pick the right release path.
        Every release path must claim the pointer with acquireAndClear() (explicit releases) or
        acquireForCleanup() (module-jni's cleanup thread and shutdown). */
    static final class Ref extends PhantomReference<Object> {
        /** The native pointer; volatile for wrapper accessors. Release paths
            must claim it with {@link #acquireAndClear} or {@link #acquireForCleanup},
            never with a separate read and clear.
            A native call that uses the pointer must hold it with
            {@link #enter} and {@link #exit} instead of reading this field. */
        volatile long ptr;
        final int kind;
        private boolean claimed;
        /** The number of uses of the pointer in progress, on all threads (see {@link #enter}) */
        private int users;
        /** The number of uses of the pointer in progress on each thread that has one */
        private HashMap<Thread, Integer> userThreads;

        Ref(Object outer, long ptr, int kind, ReferenceQueue<? super Object> q) {
            super(outer, q);
            this.ptr = ptr;
            this.kind = kind;
        }

        /** Holds the native pointer for a use (a native call with it), until {@link #exit}

            An explicit release waits until no other thread uses the pointer, and module-jni's
            cleanup leaves a pointer in use unreleased (qore#6864), so the pointer stays valid until
            the matching {@link #exit}; no lock is held during the use, which can run any %Qore and
            Java code.  A call to exit() must follow every call that returns a
            pointer, in a finally block.
            @return the pointer, or zero if it has been released (exit() must not be called then)
        */
        synchronized long enter() {
            if (ptr == 0) {
                return 0;
            }
            if (userThreads == null) {
                userThreads = new HashMap<>();
            }
            userThreads.merge(Thread.currentThread(), 1, Integer::sum);
            ++users;
            return ptr;
        }

        /** Ends a use of the pointer that {@link #enter} began on this thread
            @throws IllegalStateException if this thread has no use of the pointer in progress; nothing is
                changed, so that the uses of other threads stay counted
        */
        synchronized void exit() {
            Thread t = Thread.currentThread();
            Integer n = userThreads == null ? null : userThreads.get(t);
            if (n == null) {
                throw new IllegalStateException("no use of the Qore handle is in progress on this thread");
            }
            if (n == 1) {
                userThreads.remove(t);
            } else {
                userThreads.put(t, n - 1);
            }
            if (--users == 0) {
                notifyAll();
            }
        }

        /** returns the number of uses of the pointer in progress on the current thread */
        private int ownUses() {
            if (userThreads == null) {
                return 0;
            }
            Integer n = userThreads.get(Thread.currentThread());
            return n == null ? 0 : n;
        }

        /** waits until no thread uses the pointer; the caller has none */
        private void waitForUsers() {
            if (users > 0) {
                // a test can let a use end here, once a release has seen it and before it waits for it; the hook
                // runs with the monitor held, so the use ends only once wait() releases it
                Runnable hook = releaseWaitHook;
                if (hook != null) {
                    releaseWaitHook = null;
                    hook.run();
                }
            }
            boolean interrupted = false;
            while (users > 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /** Atomically take ownership of the native pointer.  Returns the
            previous value and resets {@link #ptr} to 0; subsequent phantom
            dispatch will see 0 and skip the native release.  Used by
            explicit teardown methods (release / destroy / get) that take
            ownership of the pointer themselves.  Waits until no other thread
            uses the pointer ({@link #enter}).
            @return the pointer owned by this caller, or zero if already claimed
            @throws IllegalStateException if the current thread uses the pointer:
                it cannot be released under the call that uses it, and nothing
                is changed
        */
        synchronized long acquireAndClear() {
            if (ownUses() > 0) {
                throw new IllegalStateException("the Qore handle cannot be released while a call on the same "
                    + "thread uses it");
            }
            claimed = true;
            long p = ptr;
            ptr = 0;
            waitForUsers();
            return p;
        }

        /** Takes ownership of the native pointer for module-jni's cleanup (the C++ cleanup thread
            and the module's shutdown): the pointer is cleared, so that no new use begins, and returned
            to be released, unless a use of it is in progress on any thread; such a pointer is left
            unreleased, and zero is returned, with no wait and no exception (qore#6864)

            The module's shutdown must not wait for a use: a call in progress can block until the
            process ends (ex: a Java listener thread blocked in the %Qore code of a module, which the
            %Qore library also leaves running at its shutdown), and waiting for it kept the process
            from exiting.  Leaving the pointer unreleased keeps it valid for the call; it is released
            with the process.  The C++ cleanup thread sees no use: a wrapper is phantom reachable only
            once no call on it is in progress, as every call keeps the wrapper reachable until its use
            has ended.
            @return the pointer to release, or zero
        */
        synchronized long acquireForCleanup() {
            claimed = true;
            long p = ptr;
            ptr = 0;
            return users > 0 ? 0 : p;
        }

        /** Publish a pointer created after registration with a zero pointer.
            @param value the newly created pointer
            @return false if cleanup already claimed this handle or shutdown began;
                the caller must then release value itself
        */
        synchronized boolean publish(long value) {
            if (claimed || closed) {
                return false;
            }
            ptr = value;
            return true;
        }
    }

    /** A one-shot test hook; see {@link #setReleaseWaitHook} */
    private static volatile Runnable releaseWaitHook;

    /** Sets a one-shot hook that the next release of a pointer in use on another thread runs, once it has
        seen the use and before it waits for it to end, with the monitor of the Ref held (for tests: a
        hook that lets the use end makes the release wait deterministically; qore#6864)
        @param hook the hook, or null to clear it
    */
    public static void setReleaseWaitHook(Runnable hook) {
        releaseWaitHook = hook;
    }

    /** Sentinel kind used at shutdown to wake the C++ thread blocked on
        queue.remove().  See {@link #shutdown}. */
    public static final int KIND_SENTINEL = -1;

    /** invocation_handler_finalize: delete (Dispatcher*)ptr. */
    public static final int KIND_INVOCATION_HANDLER = 0;

    /** delete (JniClosureHandle*)ptr: releases the closure under the sandbox of the code that handed it to Java. */
    public static final int KIND_CLOSURE = 1;

    /** qore_exception_wrapper_finalize: xsink->clear(); delete xsink. */
    public static final int KIND_EXCEPTION_WRAPPER = 2;

    /** delete (JniObjectHandle*)ptr: releases the weak reference to the Qore object. */
    public static final int KIND_OBJECT_BASE_WEAK = 3;

    /** delete (QoreSandboxCapture*)ptr: releases a QoreSandbox.Policy that was not closed. */
    public static final int KIND_SANDBOX_POLICY = 4;

    /** QoreSandboxManager::deref(): releases a policy bound to classpath entries of a class loader. */
    public static final int KIND_CLASSPATH_POLICY = 5;

    /** Shared ReferenceQueue.  The C++ cleanup thread blocks on
        {@code queue.remove()} and dispatches by Ref.kind. */
    static final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    /** Strong refs to live {@link Ref} instances.  PhantomReference itself
        must remain strongly reachable until GC enqueues it; a Ref whose only
        reference came from the Cleanable would be eligible for collection
        before its referent and silently disappear.  Holding a strong ref
        here does NOT pin the wrapper outer (the Ref does not reference its
        referent strongly — that is the whole point of PhantomReference). */
    private static final Set<Ref> live = ConcurrentHashMap.newKeySet();
    private static volatile boolean closed;

    /** Register a wrapper instance for phantom-driven native cleanup.
        @param outer the wrapper instance (held only via PhantomReference)
        @param ptr the native pointer to release when {@code outer} becomes
            phantom-reachable
        @param kind one of the KIND_* constants; selects the C++ dispatch path
        @return a {@link Ref} the wrapper should hold for explicit-teardown
            handoff via {@link #unregister}
        @throws IllegalStateException if native cleanup has shut down
    */
    static synchronized Ref register(Object outer, long ptr, int kind) {
        if (closed) {
            throw new IllegalStateException("Native cleanup has shut down");
        }
        Ref r = new Ref(outer, ptr, kind, queue);
        live.add(r);
        return r;
    }

    /** Suppress phantom cleanup for a Ref whose native pointer has already
        been released by an explicit teardown path.  Clears the Ref's
        pointer (so any concurrent phantom dispatch becomes a no-op),
        deregisters the Ref from the queue, and drops the strong ref. */
    static void unregister(Ref r) {
        if (r == null) {
            return;
        }
        r.acquireAndClear();
        r.clear();
        live.remove(r);
    }

    /** Called by the C++ cleanup thread after dispatching native release
        for {@code r}.  Drops the strong ref so the Ref itself is GC-eligible. */
    static void markProcessed(Ref r) {
        live.remove(r);
    }

    private static final Object SENTINEL_REFERENT = new Object();
    private static final Ref SENTINEL = new Ref(SENTINEL_REFERENT, 0, KIND_SENTINEL, queue);

    /** Close registration and wake the C++ cleanup thread. Native clients must
        be quiescent before calling this method. The caller joins the thread,
        then claims and dispatches every returned handle, and marks it processed.
        Snapshot allocation precedes any state change so allocation failure
        leaves registration and the worker operational.
        @return outstanding handles, including wrappers not yet garbage collected
    */
    @SuppressWarnings("deprecation")  // Reference.enqueue() is documented and stable
    static synchronized Ref[] shutdown() {
        Ref[] pending = live.toArray(new Ref[0]);
        closed = true;
        SENTINEL.enqueue();
        return pending;
    }

    private NativeCleanup() {}
}
