/** Java wrapper for the %Qore QoreClosure class
 *
 */
package org.qore.jni;

import java.lang.ref.Cleaner;

//! Java QoreClosure class
/**
    @since jni 1.2
*/
public class QoreClosure implements QoreClosureMarkerImpl {
    private static final Cleaner cleaner = Cleaner.create();

    private final CleanupState state;
    private final Cleaner.Cleanable cleanable;

    // Separate class to hold state - must NOT reference outer object
    private static class CleanupState implements Runnable {
        private long obj;

        CleanupState(long obj) {
            this.obj = obj;
        }

        @Override
        public synchronized void run() {
            if (obj != 0) {
                release0(obj);
                obj = 0;
            }
        }

        synchronized long get() {
            return obj;
        }
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreClosure(long obj) {
        this.state = new CleanupState(obj);
        this.cleanable = cleaner.register(this, state);
    }

    //! returns the pointer to the object
    public long get() {
        return state.get();
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
        return call0(QoreURLClassLoader.getProgramPtr(), state.get(), args);
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
        return call0(QoreURLClassLoader.getProgramPtr(), state.get(), args);
    }

    //! Calls the closure / call reference with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see jni_qore_object_lifecycle_management
     */
    public Object callSave(Object... args) throws Throwable {
        return callSave0(QoreURLClassLoader.getProgramPtr(), state.get(), args);
    }

    //! Calls the closure / call reference with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see jni_qore_object_lifecycle_management
     */
    public Object callArgsSave(String name, Object[] args) throws Throwable {
        return callSave0(QoreURLClassLoader.getProgramPtr(), state.get(), args);
    }

    private native Object call0(long pgm_ptr, long obj_ptr, Object... args);
    private native Object callSave0(long pgm_ptr, long obj_ptr, Object... args);
    private static native void release0(long obj_ptr);
}
