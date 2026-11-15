/*
    QoreGuardSocketFactory.java

    Qore Programming Language JNI Module

    Copyright (C) 2026 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package com.singlestore.jdbc.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import com.singlestore.jdbc.Configuration;
import org.qore.jni.jdbc.JdbcConnectGuard;

//! The socket factory of restricted SingleStore connections (qore#5786)
/** The jni module defines this class at run time in the class loader of the SingleStore driver (PROVISIONAL, see
    org.qore.jni.jdbc.SingleStoreProfile), which loads it by name for every socket and gives it the connection's
    configuration; the configuration's unmapped options hold the JdbcConnectGuard object that the module put in the
    connection's properties.  A value that a URL sets there is a String, which is refused.

    It is compiled against signature-only stubs of the driver's classes, so the module's build needs no driver jar.
*/
public final class QoreGuardSocketFactory extends ConfigurableSocketFactory {
    //! the guard object from the configuration
    private Object guard;

    public QoreGuardSocketFactory() {
    }

    @Override
    public void setConfiguration(Configuration conf, String host) {
        guard = conf.nonMappedOptions().get(JdbcConnectGuard.PROPERTY);
    }

    @Override
    public Socket createSocket() throws IOException {
        return JdbcConnectGuard.socketFor(guard);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connect(InetSocketAddress.createUnresolved(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        if (localHost != null || localPort != 0) {
            throw new SocketException("NETWORK-ACCESS-DENIED: a sandboxed JDBC connection cannot bind to a local "
                + "address");
        }
        return createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        if (localAddress != null || localPort != 0) {
            throw new SocketException("NETWORK-ACCESS-DENIED: a sandboxed JDBC connection cannot bind to a local "
                + "address");
        }
        return createSocket(address, port);
    }

    private Socket connect(InetSocketAddress addr) throws IOException {
        Socket s = JdbcConnectGuard.socketFor(guard);
        try {
            s.connect(addr);
        } catch (IOException | RuntimeException | Error e) {
            try {
                s.close();
            } catch (IOException ce) {
                e.addSuppressed(ce);
            }
            throw e;
        }
        return s;
    }
}
