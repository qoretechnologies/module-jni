// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.lang.ref.Reference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic cleanup ownership tests; fake pointers never reach native code. */
public final class NativeCleanupLifecycleTest {
    private NativeCleanupLifecycleTest() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rejectsRegistration() {
        try {
            NativeCleanup.register(new Object(), 999, NativeCleanup.KIND_OBJECT_BASE_WEAK);
            throw new AssertionError("registration must fail after shutdown");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("shut down"), "shutdown error must explain the failure");
        }
    }

    private static void empty() {
        check(NativeCleanup.shutdown().length == 0, "empty shutdown must not return a sentinel");
        check(NativeCleanup.shutdown().length == 0, "empty shutdown must be repeatable");
        rejectsRegistration();
    }

    @SuppressWarnings("deprecation")
    private static void live() throws Exception {
        Object[] owners = {new Object(), new Object(), new Object(), new Object()};
        NativeCleanup.Ref retained = NativeCleanup.register(owners[0], 11, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        NativeCleanup.Ref queued = NativeCleanup.register(owners[1], 22, NativeCleanup.KIND_CLOSURE);
        NativeCleanup.Ref released = NativeCleanup.register(owners[2], 33, NativeCleanup.KIND_EXCEPTION_WRAPPER);
        NativeCleanup.Ref delayed = NativeCleanup.register(owners[3], 0, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        check(queued.enqueue(), "explicit enqueue must succeed without a garbage collection");
        check(released.acquireAndClear() == 33, "explicit release must own its pointer");
        NativeCleanup.unregister(released);
        check(released.acquireAndClear() == 0, "released pointers cannot be claimed twice");
        check(delayed.publish(44), "construction must publish into an open handle");

        QoreInvocationHandler handler = new QoreInvocationHandler(55);
        NativeCleanup.Ref[] pending = NativeCleanup.shutdown();
        Set<NativeCleanup.Ref> refs = new HashSet<>(Arrays.asList(pending));
        check(pending.length == 4 && refs.size() == 4, "snapshot must contain each live handle once");
        check(refs.contains(retained) && refs.contains(queued) && refs.contains(delayed),
            "snapshot must include both uncollected and enqueued handles");
        check(!refs.contains(released), "explicitly released handles must not remain live");
        long total = 0;
        for (NativeCleanup.Ref ref : pending) {
            total += ref.acquireAndClear();
            check(ref.acquireAndClear() == 0, "shutdown ownership must be exclusive");
            NativeCleanup.markProcessed(ref);
        }
        check(total == 132, "shutdown must claim exactly the unreleased native pointers");
        check(!delayed.publish(88), "cleanup must prevent late pointer publication");
        // Shutdown already claimed the handler. Its explicit destroy must not
        // call native release0 again (there is deliberately no native library).
        Method destroy = QoreInvocationHandler.class.getDeclaredMethod("destroy");
        destroy.setAccessible(true);
        destroy.invoke(handler);
        check(NativeCleanup.shutdown().length == 0, "processed handles must leave the live set");
        rejectsRegistration();
        Reference.reachabilityFence(owners);
        Reference.reachabilityFence(handler);
    }

    private static void claimRace() throws Exception {
        Object owner = new Object();
        NativeCleanup.Ref ref = NativeCleanup.register(owner, 123, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        NativeCleanup.Ref incomplete = NativeCleanup.register(owner, 0, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        check(incomplete.acquireAndClear() == 0, "an unfinished constructor has no pointer yet");
        check(!incomplete.publish(456), "an already claimed handle must reject publication before shutdown");
        NativeCleanup.unregister(incomplete);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        AtomicLong total = new AtomicLong();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; ++i) {
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    long ptr = ref.acquireAndClear();
                    if (ptr != 0) {
                        winners.incrementAndGet();
                        total.addAndGet(ptr);
                    }
                    NativeCleanup.unregister(ref);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            threads[i].start();
        }
        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        check(failure.get() == null, "concurrent claims must not throw: " + failure.get());
        check(winners.get() == 1 && total.get() == 123, "exactly one cleanup path must own the pointer");
        check(NativeCleanup.shutdown().length == 0, "claimed handles must be unregistered");
        Reference.reachabilityFence(owner);
    }

    private static void registrationRace() throws Exception {
        Object[] owners = new Object[16];
        NativeCleanup.Ref[] registered = new NativeCleanup.Ref[16];
        Thread[] threads = new Thread[16];
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < threads.length; ++i) {
            final int index = i;
            owners[i] = new Object();
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    registered[index] = NativeCleanup.register(owners[index], index + 1,
                        NativeCleanup.KIND_OBJECT_BASE_WEAK);
                } catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("shut down")) {
                        failure.compareAndSet(null, expected);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            threads[i].start();
        }
        Object constructing = new Object();
        NativeCleanup.Ref delayed = NativeCleanup.register(constructing, 0, NativeCleanup.KIND_OBJECT_BASE_WEAK);
        ready.await();
        start.countDown();
        Set<NativeCleanup.Ref> snapshot = new HashSet<>(Arrays.asList(NativeCleanup.shutdown()));
        for (Thread thread : threads) {
            thread.join();
        }
        check(failure.get() == null, "registration must complete or reject cleanly: " + failure.get());
        int accepted = 0;
        for (NativeCleanup.Ref ref : registered) {
            if (ref != null) {
                ++accepted;
                check(snapshot.contains(ref), "shutdown must capture every accepted registration");
            }
        }
        check(snapshot.size() == accepted + 1, "snapshot must match accepted registrations");
        check(!delayed.publish(500), "construction completing after closure retains ownership of its pointer");
        for (NativeCleanup.Ref ref : snapshot) {
            ref.acquireAndClear();
            NativeCleanup.markProcessed(ref);
        }
        check(!delayed.publish(501), "claimed zero-pointer handles cannot be republished");
        check(NativeCleanup.shutdown().length == 0, "final drain must be complete");
        rejectsRegistration();
        Reference.reachabilityFence(owners);
        Reference.reachabilityFence(constructing);
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "empty": empty(); break;
            case "live": live(); break;
            case "claim_race": claimRace(); break;
            case "registration_race": registrationRace(); break;
            default: throw new IllegalArgumentException("Unknown lifecycle case: " + args[0]);
        }
        System.out.println("NativeCleanup lifecycle passed: " + args[0]);
    }
}
