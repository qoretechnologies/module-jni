// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnixDomainSocketAddress;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

//! Java helpers of the jdbc sandbox tests (qore#5786), called by trusted test code only
/** The helpers take the guarded sockets as plain sockets, so that they do not depend on the classes of the jni
    module's org.qore.jni.jdbc package.
*/
public final class JdbcGuardTestHelper {
    private JdbcGuardTestHelper() {
    }

    //! Returns the text of an exception and its causes
    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage());
        }
        return sb.toString();
    }

    private static byte[] literal(String addr) throws IOException {
        // a numeric address only: no name is looked up
        return InetAddress.getByName(addr).getAddress();
    }

    //! Connects a socket to an address that carries a host name and a resolved address, as a driver resolves it
    /** @return "ok" or the error
    */
    public static String connectResolved(Socket s, String hostString, String resolved, int port, int timeout) {
        try {
            s.connect(new InetSocketAddress(InetAddress.getByAddress(hostString, literal(resolved)), port), timeout);
            return "ok";
        } catch (Throwable e) {
            return describe(e);
        }
    }

    //! Connects a socket to an unresolved host name
    public static String connectUnresolved(Socket s, String host, int port, int timeout) {
        try {
            s.connect(InetSocketAddress.createUnresolved(host, port), timeout);
            return "ok";
        } catch (Throwable e) {
            return describe(e);
        }
    }

    //! Connects a socket to a UNIX domain socket address
    public static String connectUnix(Socket s, String path) {
        try {
            s.connect(UnixDomainSocketAddress.of(path));
            return "ok";
        } catch (Throwable e) {
            return describe(e);
        }
    }

    //! Binds a socket to a local address
    public static String bind(Socket s, String addr, int port) {
        try {
            s.bind(addr == null ? null : new InetSocketAddress(InetAddress.getByAddress(literal(addr)), port));
            return "ok";
        } catch (Throwable e) {
            return describe(e);
        }
    }

    //! Returns the remote address of a connected socket
    public static String remoteAddress(Socket s) {
        InetAddress a = s.getInetAddress();
        return a == null ? null : a.getHostAddress();
    }

    //! Reads up to \a max bytes from a connected socket and returns how many were read
    public static int read(Socket s, int max) throws IOException {
        InputStream in = s.getInputStream();
        byte[] buf = new byte[max];
        int total = 0;
        while (total < max) {
            int n = in.read(buf, total, max - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    //! Connects a socket on a thread of the given kind: "platform", "virtual" or "timer"
    /** None of them has a Qore caller.

        @return "ok" or the error
    */
    public static String connectOnThread(Socket s, String host, int port, String kind) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        Runnable r = () -> result.complete(connectUnresolved(s, host, port, 5000));
        switch (kind) {
            case "platform": {
                Thread t = new Thread(r, "jdbc-guard-test-platform");
                t.start();
                t.join();
                break;
            }
            case "virtual": {
                Thread t = Thread.ofVirtual().name("jdbc-guard-test-virtual").start(r);
                t.join();
                break;
            }
            case "timer": {
                Timer timer = new Timer("jdbc-guard-test-timer", true);
                try {
                    timer.schedule(new TimerRunnable(r), 0);
                    return result.get(30, TimeUnit.SECONDS);
                } finally {
                    timer.cancel();
                }
            }
            default:
                throw new IllegalArgumentException(kind);
        }
        return result.get(30, TimeUnit.SECONDS);
    }

    //! Runs a Runnable as a TimerTask
    private static final class TimerRunnable extends TimerTask {
        private final Runnable r;

        TimerRunnable(Runnable r) {
            this.r = r;
        }

        @Override
        public void run() {
            r.run();
        }
    }

    //! Returns a class loader over the given jars, with the platform class loader as its parent
    public static ClassLoader loader(String[] jars) throws IOException {
        return new URLClassLoader(urls(jars), ClassLoader.getPlatformClassLoader());
    }

    //! Returns a class loader that loads the classes of its jars before asking its parent
    /** The classes named in \a preload are loaded right away; the classes named in \a parentFirst are loaded from the
        parent first.
    */
    public static ClassLoader childFirstLoader(String[] jars, String[] preload, String[] parentFirst)
            throws Exception {
        ChildFirstLoader l = new ChildFirstLoader(urls(jars), parentFirst);
        if (preload != null) {
            for (String name : preload) {
                Class.forName(name, false, l);
            }
        }
        return l;
    }

    private static URL[] urls(String[] jars) throws IOException {
        URL[] rv = new URL[jars.length];
        for (int i = 0; i < jars.length; ++i) {
            rv[i] = new File(jars[i]).toURI().toURL();
        }
        return rv;
    }

    //! A loader that loads the classes of its own jars first, also for the names of the jni module's classes
    private static final class ChildFirstLoader extends URLClassLoader {
        private final java.util.Set<String> parentFirst = new java.util.HashSet<>();

        ChildFirstLoader(URL[] urls, String[] parentFirst) {
            super("jdbc-guard-test-child-first", urls, ClassLoader.getPlatformClassLoader());
            if (parentFirst != null) {
                this.parentFirst.addAll(java.util.Arrays.asList(parentFirst));
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && parentFirst.contains(name)) {
                    c = super.loadClass(name, false);
                }
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        c = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }

    //! Returns the bytes of an entry of a jar
    public static byte[] classBytes(String jar, String entry) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            ZipEntry e = jf.getEntry(entry);
            if (e == null) {
                throw new IOException("no entry " + entry + " in " + jar);
            }
            try (InputStream in = jf.getInputStream(e)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                in.transferTo(bo);
                return bo.toByteArray();
            }
        }
    }

    //! Returns the loader of a class as a string, to compare loaders in tests
    public static String loaderOf(String className, ClassLoader loader) throws ClassNotFoundException {
        return String.valueOf(Class.forName(className, false, loader).getClassLoader());
    }

    //! An AutoCloseable that records that it was closed
    public static final class CloseRecorder implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    //! Returns a new CloseRecorder
    public static CloseRecorder closeRecorder() {
        return new CloseRecorder();
    }

    //! Gives the SingleStore adapter of \a loader a Configuration whose guard is a String forged in the URL
    /** The adapter must have been defined in the loader (by a guarded connection that was prepared with it).

        @return "socket" if the adapter returned a socket, or the error
    */
    public static String forgedAdapterSocket(ClassLoader loader, String url) throws Exception {
        Class<?> confClass = Class.forName("com.singlestore.jdbc.Configuration", true, loader);
        Class<?> adapterClass = Class.forName("com.singlestore.jdbc.util.QoreGuardSocketFactory", false, loader);
        Properties p = new Properties();
        p.setProperty("socketFactory", adapterClass.getName());
        Method parse = confClass.getMethod("parse", String.class, Properties.class);
        Object conf = parse.invoke(null, url, p);
        Object adapter = adapterClass.getConstructor().newInstance();
        Method set = adapterClass.getMethod("setConfiguration", confClass, String.class);
        try {
            set.invoke(adapter, conf, "127.0.0.1");
            Socket s = (Socket)adapterClass.getMethod("createSocket").invoke(adapter);
            s.close();
            return "socket";
        } catch (InvocationTargetException e) {
            return describe(e.getCause());
        }
    }
}
