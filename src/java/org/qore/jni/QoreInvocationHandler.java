package org.qore.jni;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class QoreInvocationHandler implements InvocationHandler {
    /** State holder for the explicit invoke()/destroy() ref-count protocol.
        Phantom-driven cleanup goes through {@link NativeCleanup}; this state
        only protects the explicit-destroy path against concurrent invokers. */
    private static class State {
        private long ptr;
        private int counter;

        State(long ptr) {
            this.ptr = ptr;
            this.counter = 1;
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

        /** Wait for in-flight invokers, then take ownership of the pointer
            and reset state.  Returns the pointer to release (0 if already
            destroyed); caller is responsible for the native release.  Does
            not call release0 itself so the explicit-destroy path can also
            inform NativeCleanup to suppress phantom dispatch. */
        synchronized long acquireAndClearForDestroy() {
            while (counter > 1) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignored — re-enter the wait loop
                }
            }
            long p = ptr;
            ptr = 0;
            counter = 0;
            return p;
        }
    }

    private final State state;

    /** Native cleanup handle for phantom-driven release.  See
        {@link NativeCleanup}. */
    private final NativeCleanup.Ref ref;

    QoreInvocationHandler(long ptr) {
        this.state = new State(ptr);
        this.ref = NativeCleanup.register(this, ptr, NativeCleanup.KIND_INVOCATION_HANDLER);
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
        long p = state.acquireAndClearForDestroy();
        NativeCleanup.unregister(ref);
        if (p != 0) {
            release0(p);
        }
    }

    private static native void release0(long ptr);
    private native Object invoke0(long ptr, Object proxy, Method method, Object[] args) throws Throwable;
}
