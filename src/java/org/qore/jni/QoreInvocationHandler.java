// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.lang.ref.Reference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class QoreInvocationHandler implements InvocationHandler {
    /** Native cleanup handle for phantom-driven release.  See
        {@link NativeCleanup}.  A call holds the handle for its duration
        (NativeCleanup.Ref.enter()): destroy() waits until the calls on other
        threads have returned, and the shutdown of the jni module leaves the
        handle of a call in progress unreleased (qore#6864). */
    private final NativeCleanup.Ref ref;

    QoreInvocationHandler(long ptr) {
        this.ref = NativeCleanup.register(this, ptr, NativeCleanup.KIND_INVOCATION_HANDLER);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long p = ref.enter();
        if (p == 0) {
            throw new IllegalStateException("Invocation handler has already been destroyed");
        }
        try {
            // the thread's classloader context is restored when the callback returns to Java (qore#6485)
            Object[] ctx = QoreURLClassLoader.saveContext();
            try {
                return invoke0(p, proxy, method, args);
            } finally {
                QoreURLClassLoader.restoreContext(ctx);
            }
        } finally {
            ref.exit();
            // the handler stays reachable until the use has ended, so that the C++ cleanup thread never sees a use
            // (see NativeCleanup.Ref.acquireForCleanup())
            Reference.reachabilityFence(this);
        }
    }

    //! waits until the calls of the handler on other threads have returned, and releases it
    /** @throws IllegalStateException if a call of the handler on the current thread is in progress
    */
    private void destroy() {
        long p = ref.acquireAndClear();
        NativeCleanup.unregister(ref);
        if (p != 0) {
            release0(p);
        }
    }

    private static native void release0(long ptr);
    private native Object invoke0(long ptr, Object proxy, Method method, Object[] args) throws Throwable;
}
