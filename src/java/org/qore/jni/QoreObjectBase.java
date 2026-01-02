package org.qore.jni;

import java.lang.ref.Cleaner;

//! wrapper class for a %Qore object; this class holds a weak reference to the %Qore object
/** Due to the different in garbage collecting approaches (%Qore's garbage collector being
    <a href="https://github.com/qorelanguage/qore/wiki/Prompt-Collection">deterministic</a> and Java's not),
    strong references to %Qore objects must be managed outside of Java.

    @note API usage errors such as with releasing / deleting the object and then calling methods
    on the object will cause a crash

    @since 1.2
 */
public class QoreObjectBase {
    // Use volatile + double-checked locking for truly lazy initialization
    // The holder pattern doesn't work during bootstrap because native library loading
    // triggers class initialization. This pattern ensures the Cleaner is only created
    // when getCleaner() is actually called from a constructor.
    // Cleaner.create() calls getSystemClassLoader() which fails during system class loader setup
    private static volatile Cleaner cleanerInstance;

    private static Cleaner getCleaner() {
        Cleaner c = cleanerInstance;
        if (c == null) {
            synchronized (QoreObjectBase.class) {
                c = cleanerInstance;
                if (c == null) {
                    c = Cleaner.create();
                    cleanerInstance = c;
                }
            }
        }
        return c;
    }

    //! a pointer to the Qore object - kept for ByteBuddy compatibility
    protected long obj;

    private final CleanupState state;
    private final Cleaner.Cleanable cleanable;

    // Separate class to hold state - must NOT reference outer object
    private static class CleanupState implements Runnable {
        private long ptr;

        CleanupState(long ptr) {
            this.ptr = ptr;
        }

        @Override
        public synchronized void run() {
            if (ptr != 0) {
                pointerRelease0(ptr);
                ptr = 0;
            }
        }

        synchronized long getAndClear() {
            long x = ptr;
            ptr = 0;
            return x;
        }
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long qcptr, long mptr, long vptr, Object... args) throws Throwable {
        this.state = new CleanupState(0);
        this.cleanable = getCleaner().register(this, state);
        long ptr = create0(qcptr, mptr, vptr, this, args);
        this.obj = ptr;
        synchronized (state) {
            state.ptr = ptr;
        }
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObjectBase(long obj) {
        this.obj = obj;
        this.state = new CleanupState(obj);
        this.cleanable = getCleaner().register(this, state);
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
        long x = state.getAndClear();
        obj = 0;
        cleanable.clean();  // Deregister from cleaner
        if (x != 0) {
            release0(x);
        }
    }

    //! runs the destructor
    public void destroy() {
        long x = state.getAndClear();
        obj = 0;
        cleanable.clean();  // Deregister from cleaner
        if (x != 0) {
            destroy0(x);
        }
    }

    private native long create0(long qcptr, long mptr, long vptr, Object self, Object... args);
    private native void release0(long obj_ptr);
    private native void destroy0(long obj_ptr);
    // Static native method for releasing weak reference (called by Cleaner via CleanupState)
    private static native void pointerRelease0(long obj_ptr);
}
