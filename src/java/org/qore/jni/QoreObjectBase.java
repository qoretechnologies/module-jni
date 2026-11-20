// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

//! wrapper class for a %Qore object; this class holds a weak reference to the %Qore object
/** Due to the different in garbage collecting approaches (%Qore's garbage collector being
    <a href="https://github.com/qorelanguage/qore/wiki/Prompt-Collection">deterministic</a> and Java's not),
    strong references to %Qore objects must be managed outside of Java.

    The native handle is held for the duration of each call that uses it: release() and destroy() wait until the
    calls on other threads that use it have returned, and the shutdown of the jni module leaves the handle of a call
    in progress unreleased; a call made after the handle has been released raises an \c IllegalStateException
    (qore#6864)

    @since 1.2
 */
public class QoreObjectBase {
    //! the native handle of the Qore object (a weak reference and the sandbox of the code that handed it to Java) -
    //! kept for ByteBuddy compatibility
    protected long obj;

    /** Native cleanup handle for the weak-reference pointer.  See
        {@link NativeCleanup}.  The C++ cleanup thread dispatches
        pointerRelease0 (tDeref) when the wrapper becomes phantom-reachable. */
    private final NativeCleanup.Ref ref;

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long qcptr, long mptr, long vptr, Object... args) throws Throwable {
        // Register first with ptr=0 so phantom dispatch is safe even if create0 throws.
        this.ref = NativeCleanup.register(this, 0, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        // the Qore constructor can make Java calls for other Programs: the thread's classloader context is restored
        // when it returns to Java (qore#6551)
        Object[] ctx = QoreURLClassLoader.saveContext();
        long ptr;
        try {
            ptr = create0(qcptr, mptr, vptr, this, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
        if (!this.ref.publish(ptr)) {
            if (ptr != 0) {
                release0(ptr);
            }
            throw new IllegalStateException("Native cleanup ended during object construction");
        }
        this.obj = ptr;
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long obj) {
        this.obj = obj;
        this.ref = NativeCleanup.register(this, obj, NativeCleanup.KIND_OBJECT_BASE_WEAK);
    }

    //! returns the pointer to the object, or 0 if it has been released
    /** @note the pointer can be released as soon as this method returns; native code holds it with
        qoreJniEnterHandle()
        and qoreJniExitHandle() instead
    */
    public long get() {
        return ref.ptr;
    }

    //! Holds the native handle for a call that uses it, until qoreJniExitHandle(); for module-jni and the
    //! generated classes; see NativeCleanup.Ref.enter()
    /** A call to qoreJniExitHandle() must follow every call that returns, on the same thread: a use that is never
        ended keeps release() and destroy() waiting.  The names of this method and of qoreJniExitHandle() are
        reserved: a %Qore class cannot have a method of either name that
        overrides them.

        @throws IllegalStateException if the handle has been released
    */
    public final long qoreJniEnterHandle() {
        long p = ref.enter();
        if (p == 0) {
            throw new IllegalStateException("the Qore object has been released");
        }
        return p;
    }

    //! Ends a use of the native handle that qoreJniEnterHandle() began on this thread
    /** @throws IllegalStateException if no use is in progress on this thread
    */
    public final void qoreJniExitHandle() {
        ref.exit();
    }

    //! releases the Qore object without destroying it
    /** Waits until the calls on other threads that use the object have returned.

        @note if the object is returned to Qore, do not release it; allow the weak reference
        to be released when finalized

        @throws IllegalStateException if a call on the current thread uses the object
     */
    public void release() {
        long x = ref.acquireAndClear();
        obj = 0;
        NativeCleanup.unregister(ref);
        if (x != 0) {
            release0(x);
        }
    }

    //! runs the destructor
    /** Waits until the calls on other threads that use the object have returned.

        @throws IllegalStateException if a call on the current thread uses the object
    */
    public void destroy() {
        long x = ref.acquireAndClear();
        obj = 0;
        NativeCleanup.unregister(ref);
        if (x != 0) {
            // the Qore destructor can make Java calls for other Programs: the thread's classloader context is
            // restored when it returns to Java (qore#6551)
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                destroy0(x);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        }
    }

    private native long create0(long qcptr, long mptr, long vptr, Object self, Object... args);
    private native void release0(long obj_ptr);
    private native void destroy0(long obj_ptr);
}
