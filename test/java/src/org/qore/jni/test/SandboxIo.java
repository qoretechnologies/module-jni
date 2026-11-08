// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreSandbox;

//! Java code that checks its I/O with QoreSandbox, called by test/jni-sandbox-io.qtest
public class SandboxIo {
    //! Returns the numeric addresses of checked addresses
    private static String[] hostAddresses(InetAddress[] addrs) {
        String[] rv = new String[addrs.length];
        for (int i = 0; i < addrs.length; ++i) {
            rv[i] = addrs[i].getHostAddress();
        }
        return rv;
    }

    //! Runs the task on a new Java thread, which has no Qore caller, and returns its result or throws its exception
    @SuppressWarnings("unchecked")
    private static <T> T onJavaThread(Callable<T> task) throws Throwable {
        Object[] result = new Object[1];
        Throwable[] error = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                result[0] = task.call();
            } catch (Throwable e) {
                error[0] = e;
            }
        });
        t.start();
        t.join();
        if (error[0] != null) {
            throw error[0];
        }
        return (T)result[0];
    }

    public static String[] checkNetwork(String host, int port) {
        return hostAddresses(QoreSandbox.checkNetworkAccess(host, port));
    }

    public static String[] checkNetworkProto(String host, int port, int proto) {
        return hostAddresses(QoreSandbox.checkNetworkAccess(host, port, proto));
    }

    //! Returns the host name that the checked addresses carry
    public static String checkedHostName(String host, int port) {
        return QoreSandbox.checkNetworkAccess(host, port)[0].getHostName();
    }

    public static void checkFile(String path, boolean write) {
        QoreSandbox.checkFileAccess(path, write);
    }

    public static void checkFileMode(String path, int mode) {
        QoreSandbox.checkFileAccess(path, mode);
    }

    public static boolean isSandboxed() {
        return QoreSandbox.isSandboxed();
    }

    //! Checks on a Java thread without a captured policy
    public static String[] checkNetworkOnJavaThread(String host, int port) throws Throwable {
        return onJavaThread(() -> checkNetwork(host, port));
    }

    //! Checks on a Java thread without a captured policy
    public static void checkFileOnJavaThread(String path, boolean write) throws Throwable {
        onJavaThread(() -> {
            checkFile(path, write);
            return null;
        });
    }

    //! Returns isSandboxed() on a Java thread without a captured policy
    public static boolean isSandboxedOnJavaThread() throws Throwable {
        return onJavaThread(QoreSandbox::isSandboxed);
    }

    //! Captures the policy without a Qore caller
    public static void captureOnJavaThread() throws Throwable {
        onJavaThread(() -> {
            QoreSandbox.capture().close();
            return null;
        });
    }

    //! Captures the policy here and checks with the Policy object on a Java thread
    public static String[] checkNetworkWithPolicy(String host, int port) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(() -> hostAddresses(policy.checkNetworkAccess(host, port)));
        }
    }

    //! Captures the policy here and checks with the Policy object on a Java thread
    public static void checkFileWithPolicy(String path, boolean write) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            onJavaThread(() -> {
                policy.checkFileAccess(path, write);
                return null;
            });
        }
    }

    //! Captures the policy here and makes the static check in a wrapped task on a Java thread
    public static String[] checkNetworkWrapped(String host, int port) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(policy.wrap(() -> checkNetwork(host, port)));
        }
    }

    //! Captures the policy here and makes the static check in a wrapped Runnable on a Java thread
    public static void checkFileWrappedRunnable(String path, int mode) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            Runnable r = policy.wrap(() -> checkFileMode(path, mode));
            onJavaThread(() -> {
                r.run();
                return null;
            });
        }
    }

    //! Returns isSandboxed() of the captured policy and of the static method in a wrapped task on a Java thread
    public static boolean[] isSandboxedCaptured() throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(() -> new boolean[] {policy.isSandboxed(),
                policy.call(QoreSandbox::isSandboxed)});
        }
    }

    //! A task with a policy captures it again on its Java thread for another Java thread
    public static String[] checkNetworkNestedCapture(String host, int port) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(policy.wrap(() -> {
                try (QoreSandbox.Policy nested = QoreSandbox.capture()) {
                    return onJavaThread(() -> hostAddresses(nested.checkNetworkAccess(host, port)));
                } catch (Exception e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }

    //! After a task with a policy returns, the static methods on the thread no longer use the policy
    public static String[] checkNetworkAfterRun(String host, int port) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(() -> {
                policy.run(() -> checkNetwork(host, port));
                return checkNetwork(host, port);
            });
        }
    }

    //! An inner task with another policy uses its own; the outer policy is restored when it returns
    /** @param outer_host checked with the outer policy before and after the inner task
        @param inner_host checked with the trusted policy of \a trusted in the inner task
    */
    public static String[] checkNetworkNestedRun(QoreSandbox.Policy trusted, String outer_host, String inner_host,
            int port) throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return onJavaThread(() -> policy.call(() -> {
                List<String> rv = new ArrayList<>();
                rv.add(describe(() -> checkNetwork(outer_host, port)));
                rv.add(trusted.call(() -> describe(() -> checkNetwork(inner_host, port))));
                rv.add(describe(() -> checkNetwork(outer_host, port)));
                return rv.toArray(new String[0]);
            }));
        }
    }

    //! Returns the first address that the check returns, or the message of its exception
    private static String describe(Callable<String[]> check) {
        try {
            return check.call()[0];
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    //! Captures a trusted policy for checkNetworkNestedRun()
    public static QoreSandbox.Policy capture() {
        return QoreSandbox.capture();
    }

    //! Checks with a closed policy
    public static void checkClosedPolicy(String host, int port) {
        QoreSandbox.Policy policy = QoreSandbox.capture();
        policy.close();
        // closing a closed policy has no effect
        policy.close();
        policy.checkNetworkAccess(host, port);
    }

    //! Captures in a task of a closed policy
    public static void captureFromClosedPolicy() {
        QoreSandbox.Policy policy = QoreSandbox.capture();
        policy.close();
        policy.run(() -> QoreSandbox.capture().close());
    }

    //! Many Java threads check with one policy at the same time; returns the number of checks that passed
    public static int checkNetworkConcurrently(String host, int port, int threads) throws Throwable {
        AtomicInteger passed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            for (int i = 0; i < threads; ++i) {
                Thread t = new Thread(() -> {
                    try {
                        start.await();
                        policy.checkNetworkAccess(host, port);
                        passed.incrementAndGet();
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    }
                });
                list.add(t);
                t.start();
            }
            start.countDown();
            for (Thread t : list) {
                t.join();
            }
        }
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return passed.get();
    }

    //! Policies that are never closed are released when they are garbage-collected
    public static void leakPolicies(int count) {
        for (int i = 0; i < count; ++i) {
            QoreSandbox.capture();
        }
    }

    //! Checks the host, connects to the first checked address, and returns the address the socket is connected to
    /** The server runs on a Java thread that accepts one connection; the result is the remote address of the client
        socket and the local address of the accepted socket, which must both be the checked address.
    */
    public static String[] connectChecked(String host) throws Throwable {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            int port = server.getLocalPort();
            InetAddress[] addrs = QoreSandbox.checkNetworkAccess(host, port);
            InetAddress checked = null;
            for (InetAddress a : addrs) {
                if (a.getHostAddress().equals("127.0.0.1")) {
                    checked = a;
                    break;
                }
            }
            if (checked == null) {
                throw new IllegalStateException(host + " was not checked as 127.0.0.1");
            }
            String[] accepted = new String[1];
            Thread acceptor = new Thread(() -> {
                try (Socket s = server.accept()) {
                    accepted[0] = s.getLocalAddress().getHostAddress();
                } catch (Exception e) {
                    accepted[0] = "error: " + e;
                }
            });
            acceptor.start();
            String connected;
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(checked, port));
                connected = client.getInetAddress().getHostAddress();
            }
            acceptor.join();
            return new String[] {checked.getHostAddress(), connected, accepted[0]};
        }
    }

    //! Checks a host given as null
    public static void checkNullHost() {
        QoreSandbox.checkNetworkAccess(null, 80);
    }

    //! Checks a path given as null
    public static void checkNullPath() {
        QoreSandbox.checkFileAccess(null, false);
    }

    //! Checks a path with a NUL character
    public static void checkNulPath() {
        QoreSandbox.checkFileAccess("/tmp/a\0b", false);
    }

    //! Calls the Qore closure on a Java thread and returns its result or throws its exception
    /** The closure runs under the sandbox of the code that handed it to Java; a check that it makes through Java is
        made with that sandbox.
    */
    public static Object callOnJavaThread(QoreClosure c) throws Throwable {
        return onJavaThread(() -> {
            try {
                return c.call();
            } catch (Exception e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    //! Wraps a null task
    public static void wrapNull() {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            policy.wrap((Runnable)null);
        }
    }
}
