package org.qore.jni;

import java.lang.ref.Cleaner;

public class QoreExceptionWrapper extends RuntimeException {
    // Use volatile + double-checked locking for truly lazy initialization
    // The holder pattern doesn't work during bootstrap because native library loading
    // triggers class initialization. This pattern ensures the Cleaner is only created
    // when getCleaner() is actually called from a constructor.
    // Cleaner.create() calls getSystemClassLoader() which fails during system class loader setup
    private static volatile Cleaner cleanerInstance;

    private static Cleaner getCleaner() {
        Cleaner c = cleanerInstance;
        if (c == null) {
            synchronized (QoreExceptionWrapper.class) {
                c = cleanerInstance;
                if (c == null) {
                    c = Cleaner.create();
                    cleanerInstance = c;
                }
            }
        }
        return c;
    }

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
        this.cleanable = getCleaner().register(this, state);
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
