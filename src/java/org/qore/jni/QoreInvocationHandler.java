package org.qore.jni;

import java.lang.ref.Cleaner;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class QoreInvocationHandler implements InvocationHandler {
    // Use volatile + double-checked locking for truly lazy initialization
    // The holder pattern doesn't work during bootstrap because native library loading
    // triggers class initialization. This pattern ensures the Cleaner is only created
    // when getCleaner() is actually called from a constructor.
    // Cleaner.create() calls getSystemClassLoader() which fails during system class loader setup
    private static volatile Cleaner cleanerInstance;

    private static Cleaner getCleaner() {
        Cleaner c = cleanerInstance;
        if (c == null) {
            synchronized (QoreInvocationHandler.class) {
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
        private long ptr;
        private int counter;

        CleanupState(long ptr) {
            this.ptr = ptr;
            this.counter = 1;
        }

        @Override
        public synchronized void run() {
            if (ptr != 0) {
                release0(ptr);
                ptr = 0;
                counter = 0;
            }
        }

        synchronized long ref() {
            if (counter == 0) {
                throw new IllegalStateException("Invocation handler has already been destroyed");
            }
            ++counter;
            return ptr;
        }

        synchronized void deref() {
            --counter;
            if (counter == 1) {
                notifyAll();
            }
        }

        synchronized void destroy() {
            while (true) {
                if (counter == 0) {
                    return;
                }
                if (counter == 1) {
                    run();
                    return;
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignored
                }
            }
        }
    }

    QoreInvocationHandler(long ptr) {
        this.state = new CleanupState(ptr);
        this.cleanable = getCleaner().register(this, state);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long p = state.ref();
        try {
            return invoke0(p, proxy, method, args);
        } finally {
            state.deref();
        }
    }

    private void destroy() {
        state.destroy();
        cleanable.clean();  // Deregister from cleaner
    }

    private static native void release0(long ptr);
    private native Object invoke0(long ptr, Object proxy, Method method, Object[] args) throws Throwable;
}
