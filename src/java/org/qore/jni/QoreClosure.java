/** Java wrapper for the %Qore QoreClosure class
 *
 */
package org.qore.jni;

import java.lang.ref.Reference;

//! Java QoreClosure class
/**
    @since jni 1.2
*/
public class QoreClosure implements QoreClosureMarkerImpl {
    /** Native cleanup handle.  Holds the Qore object pointer and the kind
        discriminator used by module-jni's C++ cleanup thread to dispatch
        the native release directly (avoiding the JIT-trampoline race in
        the legacy java.lang.ref.Cleaner path).  See {@link NativeCleanup}. */
    private final NativeCleanup.Ref ref;

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreClosure(long obj) {
        this.ref = NativeCleanup.register(this, obj, NativeCleanup.KIND_CLOSURE);
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
    /** The shutdown of the jni module, which releases the handle, leaves the handle of a call in progress
        unreleased (qore#6864).  A call to qoreJniExitHandle() must follow every call that returns, on the same
        thread.

        @throws IllegalStateException if the handle has been released
    */
    public final long qoreJniEnterHandle() {
        long p = ref.enter();
        if (p == 0) {
            throw new IllegalStateException("the Qore closure has been released");
        }
        return p;
    }

    //! Ends a use of the native handle that qoreJniEnterHandle() began on this thread
    /** @throws IllegalStateException if no use is in progress on this thread
    */
    public final void qoreJniExitHandle() {
        ref.exit();
    }

    //! calls the closure / call reference with the given arguments and returns the result
    /**
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see callSave()
    */
    public Object call(Object... args) throws Throwable {
        long p = qoreJniEnterHandle();
        try {
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                return call0(QoreURLClassLoader.getProgramPtr(), p, args);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        } finally {
            qoreJniExitHandle();
            // the wrapper stays reachable until its use has ended (see NativeCleanup.Ref.acquireForCleanup())
            Reference.reachabilityFence(this);
        }
    }

    //! calls the closure / call reference with the given arguments and returns the result
    /**
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see callMethodArgsSave()
    */
    public Object callArgs(Object[] args) throws Throwable {
        long p = qoreJniEnterHandle();
        try {
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                return call0(QoreURLClassLoader.getProgramPtr(), p, args);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        } finally {
            qoreJniExitHandle();
            // the wrapper stays reachable until its use has ended (see NativeCleanup.Ref.acquireForCleanup())
            Reference.reachabilityFence(this);
        }
    }

    //! Calls the closure / call reference with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public Object callSave(Object... args) throws Throwable {
        long p = qoreJniEnterHandle();
        try {
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                return callSave0(QoreURLClassLoader.getProgramPtr(), p, args);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        } finally {
            qoreJniExitHandle();
            // the wrapper stays reachable until its use has ended (see NativeCleanup.Ref.acquireForCleanup())
            Reference.reachabilityFence(this);
        }
    }

    //! Calls the closure / call reference with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param name reserved for compatibility; the closure has no method name
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public Object callArgsSave(String name, Object[] args) throws Throwable {
        long p = qoreJniEnterHandle();
        try {
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                return callSave0(QoreURLClassLoader.getProgramPtr(), p, args);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        } finally {
            qoreJniExitHandle();
            // the wrapper stays reachable until its use has ended (see NativeCleanup.Ref.acquireForCleanup())
            Reference.reachabilityFence(this);
        }
    }

    private native Object call0(long pgm_ptr, long obj_ptr, Object... args);
    private native Object callSave0(long pgm_ptr, long obj_ptr, Object... args);
}
