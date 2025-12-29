package org.qore.jni;

import java.lang.ref.Cleaner;

public class QoreExceptionWrapper extends RuntimeException {
    private static final Cleaner cleaner = Cleaner.create();

    private final CleanupState state;
    private final Cleaner.Cleanable cleanable;

    // Separate class to hold state - must NOT reference outer object
    private static class CleanupState implements Runnable {
        private long xsink;

        CleanupState(long xsink) {
            this.xsink = xsink;
        }

        @Override
        public synchronized void run() {
            long ptr = xsink;
            if (ptr != 0) {
                xsink = 0;
                release0(ptr);
            }
        }
    }

    QoreExceptionWrapper(long xsink) {
        this.state = new CleanupState(xsink);
        this.cleanable = cleaner.register(this, state);
    }

    @Override
    public String getMessage() {
        synchronized (state) {
            return getMessage0(state.xsink);
        }
    }

    long get() {
        synchronized (state) {
            long x = state.xsink;
            state.xsink = 0;
            cleanable.clean();  // Deregister since pointer was taken
            return x;
        }
    }

    private static native void release0(long ptr);
    private native String getMessage0(long ptr);
}
