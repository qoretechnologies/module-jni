// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.lang.ref.Reference;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//! Applies the sandbox policy of the calling %Qore code to I/O that Java code performs itself
/** %Qore enforces its sandbox network and filesystem policy (see \c SandboxManager) for the I/O that goes through
    %Qore: sockets, QUIC and file operations.  Java code called through the \c jni module performs its own I/O
    through the JVM (\c java.net, \c java.nio, \c java.io, JDBC drivers, ...), which %Qore never sees; the JDK has no
    in-process replacement for the removed Security Manager.  Java code that may be called by sandboxed %Qore code
    must therefore check every network destination and every file it uses with this class before using it.

    The checks apply exactly the policy that %Qore applies to its own I/O:
    - checkNetworkAccess() resolves the host name once with the %Qore resolver and checks every address it resolves
      to with the network policy of %Qore sockets (denied ranges such as those of
      \c SandboxManager::blockPrivateNetworks(), allowed hosts, ports and ranges, and the default policy); it returns
      the checked addresses, and the caller must connect to one of them and not resolve the host name again, so that
      no DNS rebinding can occur between the check and the connection
    - checkFileAccess() checks a path with the filesystem policy of %Qore \c File and \c Dir operations

    A denied network destination raises \c NETWORK-ACCESS-DENIED, and a denied path \c FILESYSTEM-ACCESS-DENIED, as
    a QoreExceptionWrapper (a \c RuntimeException) that becomes the original %Qore exception when it reaches %Qore
    code.  Code that is not sandboxed passes every check.

    <b>Java threads</b>

    The static methods use the policy of the %Qore code that called into Java on the current thread.  Work that Java
    runs on threads of its own (an executor, a connection pool, a library's I/O threads) has no %Qore caller: capture
    the policy with capture() in the call from %Qore, before the work is handed to another thread, and make the
    checks through the QoreSandbox.Policy object, or run the work with QoreSandbox.Policy.run(),
    QoreSandbox.Policy.call() or QoreSandbox.Policy.wrap(), which make the static methods use the captured policy on
    that thread.  A check made on a thread with no %Qore caller and no captured policy cannot know the policy and
    raises \c JNI-SANDBOX-ERROR instead of allowing the operation.

    @par Example:
    @code{.java}
// in the call from Qore
QoreSandbox.Policy policy = QoreSandbox.capture();
executor.submit(policy.wrap(() -> {
    // on the executor thread: checked with the policy of the Qore code that submitted the work
    InetAddress[] addrs = QoreSandbox.checkNetworkAccess(host, port);
    try (Socket s = new Socket()) {
        s.connect(new InetSocketAddress(addrs[0], port));
        ...
    }
}));
    @endcode

    @note The checks only make Java code subject to the policy if the Java code makes them: Java classes imported
    through the \c jni module are tagged with the \c QDOM_UNCONTROLLED_API functional domain because %Qore cannot
    control what they do; see @ref jni_sandbox "Sandboxing Java I/O" for the rules for Java code that is declared to
    be sandbox-aware

    @since jni 3.0
*/
public final class QoreSandbox {
    //! Read access to a file or the contents of a directory (\c QSEC_READ)
    public static final int READ = 1 << 0;
    //! Write access to an existing file (\c QSEC_WRITE)
    public static final int WRITE = 1 << 1;
    //! Execute access (\c QSEC_EXECUTE)
    public static final int EXECUTE = 1 << 2;
    //! Deletion of a file or directory (\c QSEC_DELETE)
    public static final int DELETE = 1 << 3;
    //! Creation of a file or directory (\c QSEC_CREATE)
    public static final int CREATE = 1 << 4;
    //! All filesystem access modes
    public static final int ALL = READ | WRITE | EXECUTE | DELETE | CREATE;

    //! A TCP connection (\c QSEC_NET_TCP)
    public static final int TCP = 1 << 0;
    //! UDP datagrams (\c QSEC_NET_UDP)
    public static final int UDP = 1 << 1;

    //! The policy that the static methods use on the current thread instead of the thread's %Qore caller
    private static final ThreadLocal<Policy> current = new ThreadLocal<>();

    private QoreSandbox() {
    }

    //! The sandbox policy of %Qore code, captured so that it can be applied on another thread
    /** Created with QoreSandbox.capture() in the call from %Qore.  The object can be shared between threads; its
        checks can run concurrently.  Close it when the work it governs is finished; a policy that is not closed is
        released when it is garbage-collected.  A check made with a closed policy raises \c JNI-SANDBOX-ERROR.

        The captured policy applies to every check made through it, on any thread: also on a %Qore thread that runs
        other sandboxed code, and inside a system-policy capability (\c SandboxManager::createSystemPolicyCall()).
        The policy of the %Qore code that runs on the thread, if any, applies as well, so that the stricter one wins.
        A policy captured inside run(), call() or wrap() is the policy of the run.
    */
    public static final class Policy implements AutoCloseable {
        //! the native capture; released by close() or by NativeCleanup
        private final NativeCleanup.Ref ref;
        //! checks hold the read lock while they use the native capture, close() the write lock to release it
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private Policy(long ptr) {
            ref = NativeCleanup.register(this, ptr, NativeCleanup.KIND_SANDBOX_POLICY);
        }

        //! Returns true if a check made with the policy on the current thread is restricted
        /** That is if the captured policy is that of sandboxed %Qore code, or if the %Qore code that runs on the
            current thread is sandboxed, as its policy applies as well
        */
        public boolean isSandboxed() {
            lock.readLock().lock();
            try {
                return isSandboxed0(ptr());
            } finally {
                lock.readLock().unlock();
                Reference.reachabilityFence(this);
            }
        }

        //! Checks a TCP connection with the captured policy; see QoreSandbox.checkNetworkAccess(String, int, int)
        public InetAddress[] checkNetworkAccess(String host, int port) {
            return checkNetworkAccess(host, port, TCP);
        }

        //! Checks a network destination with the captured policy; see QoreSandbox.checkNetworkAccess(String, int, int)
        public InetAddress[] checkNetworkAccess(String host, int port, int proto) {
            String name = hostName(host);
            byte[] hostBytes = encode(name, "host");
            lock.readLock().lock();
            try {
                return makeAddresses(name, checkNetworkAccess0(ptr(), hostBytes, port, proto));
            } finally {
                lock.readLock().unlock();
                Reference.reachabilityFence(this);
            }
        }

        //! Checks a path with the captured policy; see QoreSandbox.checkFileAccess(String, boolean)
        public void checkFileAccess(String path, boolean write) {
            checkFileAccess(path, write ? WRITE : READ);
        }

        //! Checks a path with the captured policy; see QoreSandbox.checkFileAccess(String, int)
        public void checkFileAccess(String path, int mode) {
            byte[] pathBytes = encode(path, "path");
            lock.readLock().lock();
            try {
                checkFileAccess0(ptr(), pathBytes, mode);
            } finally {
                lock.readLock().unlock();
                Reference.reachabilityFence(this);
            }
        }

        //! Runs the task on the current thread with the static methods of QoreSandbox using this policy
        /** The policy that the static methods used on the thread before is restored when the task returns.
        */
        public void run(Runnable task) {
            Policy prev = current.get();
            current.set(this);
            try {
                task.run();
            } finally {
                restore(prev);
            }
        }

        //! Calls the task on the current thread with the static methods of QoreSandbox using this policy
        /** The policy that the static methods used on the thread before is restored when the task returns.

            @return the value that the task returns
        */
        public <T> T call(Callable<T> task) throws Exception {
            Policy prev = current.get();
            current.set(this);
            try {
                return task.call();
            } finally {
                restore(prev);
            }
        }

        //! Returns a task that runs \a task with the static methods of QoreSandbox using this policy
        /** For tasks handed to an executor or another thread; see run(Runnable)
        */
        public Runnable wrap(Runnable task) {
            if (task == null) {
                throw new NullPointerException("task");
            }
            return () -> run(task);
        }

        //! Returns a task that calls \a task with the static methods of QoreSandbox using this policy
        /** For tasks handed to an executor or another thread; see call(Callable)
        */
        public <T> Callable<T> wrap(Callable<T> task) {
            if (task == null) {
                throw new NullPointerException("task");
            }
            return () -> call(task);
        }

        //! Releases the captured policy; waits for checks that are running with it to finish
        /** Further checks with this object raise \c JNI-SANDBOX-ERROR.  Closing a closed policy has no effect.
        */
        @Override
        public void close() {
            long ptr;
            lock.writeLock().lock();
            try {
                ptr = ref.acquireAndClear();
                NativeCleanup.unregister(ref);
                if (ptr != 0) {
                    release0(ptr);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        //! Returns the native capture; raises JNI-SANDBOX-ERROR if the policy has been closed
        private long ptr() {
            long ptr = ref.ptr;
            if (ptr == 0) {
                // a native call with no policy and an unknown thread raises the error
                throw closedError0();
            }
            return ptr;
        }
    }

    //! Captures the sandbox policy of the %Qore code that called into Java on the current thread
    /** Call it in the call from %Qore, before handing work to another thread; on a thread that runs a task with a
        policy (QoreSandbox.Policy.run(), ...), the policy of the task is captured.

        @return the captured policy

        @throw JNI-SANDBOX-ERROR the current thread has no %Qore caller and runs no task with a policy, so the policy
        is unknown
    */
    public static Policy capture() {
        Policy p = current.get();
        long ptr;
        if (p != null) {
            p.lock.readLock().lock();
            try {
                ptr = capture0(p.ptr());
            } finally {
                p.lock.readLock().unlock();
                Reference.reachabilityFence(p);
            }
        } else {
            ptr = capture0(0);
        }
        try {
            return new Policy(ptr);
        } catch (Throwable e) {
            release0(ptr);
            throw e;
        }
    }

    //! Returns true if the code governing the current thread is sandboxed
    /** @throw JNI-SANDBOX-ERROR the current thread has no %Qore caller and runs no task with a policy
    */
    public static boolean isSandboxed() {
        Policy p = current.get();
        return p != null ? p.isSandboxed() : isSandboxed0(0);
    }

    //! Checks a TCP connection to \a host and \a port; see checkNetworkAccess(String, int, int)
    public static InetAddress[] checkNetworkAccess(String host, int port) {
        return checkNetworkAccess(host, port, TCP);
    }

    //! Checks that the sandbox policy allows a connection to \a host and \a port, and returns the checked addresses
    /** The host name is resolved once with the %Qore resolver, and every address it resolves to is checked with the
        network policy of %Qore sockets, with the host name, so that allowed host patterns apply.  Connect to one of
        the returned addresses, and do not resolve the host name again: a new lookup can return another address than
        the one that was checked (DNS rebinding).  The returned addresses carry \a host as their host name, so TLS
        host name verification and SNI still use it.

        The host name is resolved also when no sandbox applies, so that the code is the same in both cases.

        @param host the host name or address literal; an IPv6 literal may be enclosed in brackets
        @param port the port, \c 1 - \c 65535; the caller must use this port
        @param proto TCP or UDP

        @return the checked addresses, in the order of the resolver; at least one

        @throw NETWORK-ACCESS-DENIED the policy denies the connection to the host name or to one of its addresses,
        which the message names
        @throw JNI-SANDBOX-ERROR invalid arguments, the host name has no IPv4 or IPv6 address, or the policy is
        unknown (the current thread has no %Qore caller and runs no task with a policy)
        @throw QOREADDRINFO-GETINFO-ERROR the host name cannot be resolved
    */
    public static InetAddress[] checkNetworkAccess(String host, int port, int proto) {
        Policy p = current.get();
        if (p != null) {
            return p.checkNetworkAccess(host, port, proto);
        }
        String name = hostName(host);
        return makeAddresses(name, checkNetworkAccess0(0, encode(name, "host"), port, proto));
    }

    //! Returns the host name or address literal without the brackets of an IPv6 address literal (\c "[::1]")
    private static String hostName(String host) {
        if (host != null && host.length() > 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    //! Checks that the sandbox policy allows reading (\a write is false) or writing (\a write is true) a file
    /** @see checkFileAccess(String, int)
    */
    public static void checkFileAccess(String path, boolean write) {
        checkFileAccess(path, write ? WRITE : READ);
    }

    //! Checks that the sandbox filesystem policy allows the access \a mode to \a path
    /** The path is checked like the paths of %Qore \c File and \c Dir operations: it is canonicalized (symbolic links
        are resolved, also for a path that does not exist yet), denied paths take precedence, and the sandbox root,
        the allowed paths and the default policy apply.  Pass the path exactly as it is then opened; a relative path
        is resolved against the current directory of the process.

        Use the modes that %Qore uses for the same operation: READ to read a file or list a directory, WRITE to write
        an existing file, CREATE | WRITE to create a file, CREATE to create a directory, DELETE to delete a file or
        directory, CREATE | DELETE for the target of a rename, READ | EXECUTE to execute a file.

        @param path the path
        @param mode the access mode: a combination of READ, WRITE, EXECUTE, DELETE and CREATE

        @throw FILESYSTEM-ACCESS-DENIED the policy denies the access
        @throw SANDBOX-PATH-ERROR the path cannot be resolved
        @throw JNI-SANDBOX-ERROR invalid arguments, or the policy is unknown (the current thread has no %Qore caller and
        runs no task with a policy)
    */
    public static void checkFileAccess(String path, int mode) {
        Policy p = current.get();
        if (p != null) {
            p.checkFileAccess(path, mode);
            return;
        }
        checkFileAccess0(0, encode(path, "path"), mode);
    }

    //! Restores the policy that the static methods used on the current thread before a task
    private static void restore(Policy prev) {
        if (prev == null) {
            current.remove();
        } else {
            current.set(prev);
        }
    }

    //! Encodes a host name or path as the bytes that the JVM passes to the operating system for it
    /** A string that the encoding cannot represent exactly, or that contains a NUL character, raises
        JNI-SANDBOX-ERROR, because the bytes checked would not be the bytes used.
    */
    private static byte[] encode(String str, String what) {
        if (str == null) {
            throw argumentError0(what + " must not be null");
        }
        if (str.indexOf('\0') >= 0) {
            throw argumentError0(what + " must not contain a NUL character");
        }
        try {
            ByteBuffer buf = nameCharset().newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(str));
            byte[] rv = new byte[buf.remaining()];
            buf.get(rv);
            return rv;
        } catch (CharacterCodingException e) {
            throw argumentError0(what + " cannot be represented in the file name encoding of the JVM ("
                + nameCharset().name() + ")");
        }
    }

    //! Returns the encoding that the JVM uses for file names and other strings passed to the operating system
    private static Charset nameCharset() {
        String name = System.getProperty("sun.jnu.encoding");
        if (name != null) {
            try {
                return Charset.forName(name);
            } catch (IllegalArgumentException e) {
                // fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    //! Makes the addresses returned by checkNetworkAccess0()
    /** Each element is an IPv4 address (4 bytes) or an IPv6 address (16 bytes) followed by its scope ID (4 bytes, in
        network byte order).
    */
    private static InetAddress[] makeAddresses(String host, byte[][] addrs) {
        InetAddress[] rv = new InetAddress[addrs.length];
        try {
            for (int i = 0; i < addrs.length; ++i) {
                byte[] a = addrs[i];
                if (a.length == 4) {
                    rv[i] = InetAddress.getByAddress(host, a);
                    continue;
                }
                byte[] addr = new byte[16];
                System.arraycopy(a, 0, addr, 0, 16);
                int scope = ByteBuffer.wrap(a, 16, 4).getInt();
                rv[i] = scope != 0 ? Inet6Address.getByAddress(host, addr, scope) : InetAddress.getByAddress(host, addr);
            }
        } catch (UnknownHostException e) {
            // not possible with addresses of a valid length
            throw new IllegalStateException(e);
        }
        return rv;
    }

    //! Captures the policy of the current thread, or of the policy \a ptr if not 0
    private static native long capture0(long ptr);

    //! Releases a policy captured by capture0()
    private static native void release0(long ptr);

    //! Returns true if the policy \a ptr, or of the current thread if 0, is sandboxed
    private static native boolean isSandboxed0(long ptr);

    //! Checks a network destination; returns the checked addresses (see makeAddresses())
    private static native byte[][] checkNetworkAccess0(long ptr, byte[] host, int port, int proto);

    //! Checks a path
    private static native void checkFileAccess0(long ptr, byte[] path, int mode);

    //! Returns the JNI-SANDBOX-ERROR exception for invalid arguments
    private static native RuntimeException argumentError0(String msg);

    //! Returns the JNI-SANDBOX-ERROR exception for a closed policy
    private static native RuntimeException closedError0();
}
