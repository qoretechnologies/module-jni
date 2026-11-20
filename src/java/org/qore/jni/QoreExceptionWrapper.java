package org.qore.jni;

public class QoreExceptionWrapper extends RuntimeException {
    /** Native cleanup handle for the wrapped ExceptionSink pointer.  See
        {@link NativeCleanup}. */
    private final NativeCleanup.Ref ref;

    QoreExceptionWrapper(long xsink) {
        this.ref = NativeCleanup.register(this, xsink, NativeCleanup.KIND_EXCEPTION_WRAPPER);
    }

    /** The message of a wrapper whose %Qore exception has been released */
    static final String RELEASED_MESSAGE = "Qore exception (the message is unavailable: the exception has been "
        + "taken by Qore, or released by the shutdown of the jni module)";

    /** Returns the code and the description of the %Qore exception

        The %Qore exception is released when %Qore takes it (get(), when the wrapper is thrown back to %Qore) and at
        the shutdown of the jni module, which releases the exception of every wrapper that still exists; Java can
        keep the wrapper after either, so a released exception gives a fixed message.  Each release claims the
        pointer under the monitor of the reference (NativeCleanup.Ref.acquireAndClear()), which is held here while
        the message is read, so that the exception is not released, on another thread, while it is read.
    */
    @Override
    public String getMessage() {
        synchronized (ref) {
            long ptr = ref.ptr;
            if (ptr == 0) {
                return RELEASED_MESSAGE;
            }
            return getMessage0(ptr);
        }
    }

    /** Take ownership of the underlying ExceptionSink pointer.  After this
        call the C++ cleanup thread will not release the pointer; the caller
        is responsible. */
    long get() {
        long x = ref.acquireAndClear();
        NativeCleanup.unregister(ref);
        return x;
    }

    private native String getMessage0(long ptr);
}
