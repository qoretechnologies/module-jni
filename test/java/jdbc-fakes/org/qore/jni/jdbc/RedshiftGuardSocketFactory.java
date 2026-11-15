// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package org.qore.jni.jdbc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.Properties;
import javax.net.SocketFactory;

//! A class with the name of the jni module's Redshift socket factory (qore#5786 tests): it does not check anything
public final class RedshiftGuardSocketFactory extends SocketFactory {
    public RedshiftGuardSocketFactory(Properties info) {
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
