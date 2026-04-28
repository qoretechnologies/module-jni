package org.qore.jni;

public class QoreExceptionWrapper extends RuntimeException {
    /** Native cleanup handle for the wrapped ExceptionSink pointer.  See
        {@link NativeCleanup}. */
    private final NativeCleanup.Ref ref;

    QoreExceptionWrapper(long xsink) {
        this.ref = NativeCleanup.register(this, xsink, NativeCleanup.KIND_EXCEPTION_WRAPPER);
    }

    @Override
    public String getMessage() {
        return getMessage0(ref.ptr);
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
