package org.qore.jni;

//! wrapper class for a %Qore object; this class holds a weak reference to the %Qore object
/** Due to the different in garbage collecting approaches (%Qore's garbage collector being
    <a href="https://github.com/qorelanguage/qore/wiki/Prompt-Collection">deterministic</a> and Java's not),
    strong references to %Qore objects must be managed outside of Java.

    @note API usage errors such as with releasing / deleting the object and then calling methods
    on the object will cause a crash

    @since 1.2
 */
public class QoreObjectBase {
    //! a pointer to the Qore object - kept for ByteBuddy compatibility
    protected long obj;

    /** Native cleanup handle for the weak-reference pointer.  See
        {@link NativeCleanup}.  The C++ cleanup thread dispatches
        pointerRelease0 (tDeref) when the wrapper becomes phantom-reachable. */
    private final NativeCleanup.Ref ref;

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long qcptr, long mptr, long vptr, Object... args) throws Throwable {
        // Register first with ptr=0 so phantom dispatch is safe even if create0 throws.
        this.ref = NativeCleanup.register(this, 0, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        long ptr = create0(qcptr, mptr, vptr, this, args);
        this.obj = ptr;
        this.ref.ptr = ptr;
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long obj) {
        this.obj = obj;
        this.ref = NativeCleanup.register(this, obj, NativeCleanup.KIND_OBJECT_BASE_WEAK);
    }

    //! returns the pointer to the object
    public long get() {
        return obj;
    }

    //! releases the Qore object without destroying it
    /** @note if the object is returned to Qore, do not release it; allow the weak reference
        to be released when finalized
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
    public void destroy() {
        long x = ref.acquireAndClear();
        obj = 0;
        NativeCleanup.unregister(ref);
        if (x != 0) {
            destroy0(x);
        }
    }

    private native long create0(long qcptr, long mptr, long vptr, Object self, Object... args);
    private native void release0(long obj_ptr);
    private native void destroy0(long obj_ptr);
}
