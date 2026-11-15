// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import javax.net.SocketFactory;

//! A socket factory that records the properties a JDBC driver gives it (qore#5786 tests)
/** A trusted connection names it in its URL (Redshift: \c socketFactory=org.qore.jni.test.RecordingSocketFactory),
    so that the test can show that the jdbc driver adds no property to a trusted connection.
*/
public final class RecordingSocketFactory extends SocketFactory {
    private static final List<String> keys = new ArrayList<>();

    public RecordingSocketFactory(Properties info) {
        synchronized (keys) {
            keys.clear();
            keys.addAll(new TreeSet<>(info.stringPropertyNames()));
        }
    }

    //! Returns the property names of the last factory created, sorted
    public static String[] lastKeys() {
        synchronized (keys) {
            return keys.toArray(new String[0]);
        }
    }

    @Override
    public Socket createSocket() {
        return new Socket(Proxy.NO_PROXY);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return new Socket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return new Socket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return new Socket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return new Socket(address, port, localAddress, localPort);
    }
}
