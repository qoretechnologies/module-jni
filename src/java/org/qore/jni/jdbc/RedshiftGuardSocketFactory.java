/*
    RedshiftGuardSocketFactory.java

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

package org.qore.jni.jdbc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Properties;
import javax.net.SocketFactory;

//! The socket factory of restricted Amazon Redshift connections (qore#5786)
/** The driver instantiates the class named by its \c socketFactory property with the merged connection properties
    (its \c ObjectFactory prefers a \c Properties constructor, which is the only one), once per connection attempt,
    and uses the instance for every host of the URL, for reconnects and for query cancels.  The jni module sets
    \c socketFactoryArg to the registry token of the connection's JdbcConnectGuard; the factory looks it up once and
    keeps the guard, whose closed flag stops a late cancel after the connection was closed.

    @since jni 3.0
*/
public final class RedshiftGuardSocketFactory extends SocketFactory {
    //! the guard of the connection
    private final JdbcConnectGuard guard;

    //! Creates the factory of the connection whose guard is registered with the token in \c socketfactoryarg
    /** @throw IllegalStateException no guard is registered with the token
    */
    public RedshiftGuardSocketFactory(Properties info) {
        // the driver's properties lowercase their keys
        String token = info == null ? null : info.getProperty("socketfactoryarg");
        JdbcConnectGuard g = JdbcConnectGuard.lookup(token);
        if (g == null) {
            throw new IllegalStateException("NETWORK-ACCESS-DENIED: the Redshift connection has no connection "
                + "guard of the jni module");
        }
        guard = g;
    }

    @Override
    public Socket createSocket() {
        return guard.newSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connect(InetSocketAddress.createUnresolved(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        if (localHost != null || localPort != 0) {
            throw new SocketException(guard.refuse("a sandboxed JDBC connection cannot bind to a local address"));
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
            throw new SocketException(guard.refuse("a sandboxed JDBC connection cannot bind to a local address"));
        }
        return createSocket(address, port);
    }

    private Socket connect(InetSocketAddress addr) throws IOException {
        Socket s = guard.newSocket();
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
