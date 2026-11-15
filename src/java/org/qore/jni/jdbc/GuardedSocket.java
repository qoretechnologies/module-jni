/*
    GuardedSocket.java

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

//! A socket that connects only to the checked addresses of its JdbcConnectGuard (qore#5786)
/** A JDBC driver creates the socket unconnected and connects it itself.  connect() ignores the address that the
    driver resolved and uses the host string of the endpoint only, so that a second name lookup cannot reach an
    address that was not checked (DNS rebinding):
    - a host name and port that the guard pinned connects to its checked addresses in order; the next address is
      tried only after an unreachable error (the connection was refused or there is no route), and the timeout is
      shared by all attempts
    - an address literal connects only if the address was checked with the port
    - anything else, an endpoint that is not an \c InetSocketAddress, a socket of a closed guard, and a bind to a
      local address are refused with a \c SocketException whose message starts with \c NETWORK-ACCESS-DENIED

    Each attempt is made with a new plain socket without a proxy (\c Proxy.NO_PROXY, so the \c socksProxyHost
    system property does not apply), because a socket whose connection failed is closed; once connected, this socket
    delegates to it.  The options set before the connection are applied to each attempt.
*/
final class GuardedSocket extends Socket {
    //! the guard
    private final JdbcConnectGuard guard;
    //! the connected socket, if any
    private volatile Socket inner;
    //! the socket of the attempt in progress, if any
    private volatile Socket attempt;
    //! true if the socket was closed
    private volatile boolean closed;

    //! the options set before the connection was made
    private final Map<SocketOption<?>, Object> options = new LinkedHashMap<>();
    //! SO_TIMEOUT set before the connection was made
    private Integer soTimeout;
    //! OOBINLINE set before the connection was made
    private Boolean oobInline;
    //! the performance preferences set before the connection was made
    private int[] performance;

    GuardedSocket(JdbcConnectGuard guard) {
        super(Proxy.NO_PROXY);
        this.guard = guard;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (timeout < 0) {
            throw new IllegalArgumentException("connect: timeout can't be negative");
        }
        if (!(endpoint instanceof InetSocketAddress)) {
            throw new SocketException(guard.refuse("a sandboxed JDBC connection cannot connect to "
                + (endpoint == null ? "no address" : "a " + endpoint.getClass().getName() + " address")));
        }
        InetSocketAddress isa = (InetSocketAddress)endpoint;
        // the host name or literal that the driver connects to; never the address that it resolved
        String host = isa.getHostString();
        int port = isa.getPort();
        synchronized (this) {
            if (closed) {
                throw new SocketException("Socket is closed");
            }
            if (inner != null || attempt != null) {
                throw new SocketException("already connected");
            }
        }
        if (guard.isClosed()) {
            throw new ConnectException(guard.refuse("the connection to '" + host + "' port " + port
                + " was closed; its guard allows no new socket"));
        }
        InetAddress[] addrs = guard.resolve(host, port);
        if (addrs == null) {
            throw new ConnectException(guard.refuse("'" + host + "' port " + port + " was not checked against the "
                + "sandbox for this connection"));
        }
        long deadline = timeout > 0 ? System.nanoTime() + timeout * 1000000L : 0;
        IOException last = null;
        for (InetAddress addr : addrs) {
            int remaining = 0;
            if (deadline != 0) {
                long ms = (deadline - System.nanoTime()) / 1000000L;
                if (ms <= 0) {
                    break;
                }
                remaining = (int)Math.min(Integer.MAX_VALUE, ms);
            }
            Socket s = new Socket(Proxy.NO_PROXY);
            synchronized (this) {
                if (closed) {
                    s.close();
                    throw new SocketException("Socket is closed");
                }
                attempt = s;
            }
            try {
                applyOptions(s);
                s.connect(new InetSocketAddress(addr, port), remaining);
            } catch (ConnectException | NoRouteToHostException e) {
                // unreachable: the next checked address
                closeQuietly(s);
                last = e;
                continue;
            } catch (IOException | RuntimeException | Error e) {
                closeQuietly(s);
                throw e;
            } finally {
                synchronized (this) {
                    attempt = null;
                }
            }
            synchronized (this) {
                if (closed || guard.isClosed()) {
                    closeQuietly(s);
                    throw closed ? new SocketException("Socket is closed")
                        : new ConnectException(guard.refuse("the connection to '" + host + "' port " + port
                            + " was closed while connecting"));
                }
                inner = s;
            }
            guard.connected();
            return;
        }
        if (last != null) {
            throw last;
        }
        throw new SocketTimeoutException("connect timed out");
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException e) {
            // the attempt failed already
        }
    }

    //! applies the options set before the connection to an attempt
    @SuppressWarnings({"unchecked", "rawtypes"})
    private synchronized void applyOptions(Socket s) throws IOException {
        for (Map.Entry<SocketOption<?>, Object> e : options.entrySet()) {
            s.setOption((SocketOption)e.getKey(), e.getValue());
        }
        if (soTimeout != null) {
            s.setSoTimeout(soTimeout);
        }
        if (oobInline != null) {
            s.setOOBInline(oobInline);
        }
        if (performance != null) {
            s.setPerformancePreferences(performance[0], performance[1], performance[2]);
        }
    }

    //! returns the connected socket
    private Socket connected() throws SocketException {
        Socket s = inner;
        if (s == null) {
            throw new SocketException(closed ? "Socket is closed" : "Socket is not connected");
        }
        return s;
    }

    //! returns the connected socket, or null; throws if the socket was closed
    private Socket current() throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        return inner;
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        if (bindpoint != null) {
            throw new SocketException(guard.refuse("a sandboxed JDBC connection cannot bind to a local address"));
        }
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        // the socket is bound to an ephemeral port when it connects
    }

    @Override
    public InetAddress getInetAddress() {
        Socket s = inner;
        return s == null ? null : s.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        Socket s = inner;
        return s == null ? null : s.getLocalAddress();
    }

    @Override
    public int getPort() {
        Socket s = inner;
        return s == null ? 0 : s.getPort();
    }

    @Override
    public int getLocalPort() {
        Socket s = inner;
        return s == null ? -1 : s.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        Socket s = inner;
        return s == null ? null : s.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        Socket s = inner;
        return s == null ? null : s.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connected().getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return connected().getOutputStream();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        setOptionIntern(StandardSocketOptions.TCP_NODELAY, on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getTcpNoDelay();
        }
        return Boolean.TRUE.equals(pending(StandardSocketOptions.TCP_NODELAY));
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        Socket s = current();
        if (s != null) {
            s.setSoLinger(on, linger);
            return;
        }
        if (on && linger < 0) {
            throw new IllegalArgumentException("invalid value for SO_LINGER");
        }
        synchronized (this) {
            options.put(StandardSocketOptions.SO_LINGER, on ? Math.min(linger, 65535) : -1);
        }
    }

    @Override
    public int getSoLinger() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getSoLinger();
        }
        Object v = pending(StandardSocketOptions.SO_LINGER);
        return v == null ? -1 : (Integer)v;
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        connected().sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        Socket s = current();
        if (s != null) {
            s.setOOBInline(on);
            return;
        }
        synchronized (this) {
            oobInline = on;
        }
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getOOBInline();
        }
        synchronized (this) {
            return Boolean.TRUE.equals(oobInline);
        }
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout can't be negative");
        }
        Socket s = current();
        if (s != null) {
            s.setSoTimeout(timeout);
            return;
        }
        synchronized (this) {
            soTimeout = timeout;
        }
    }

    @Override
    public int getSoTimeout() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getSoTimeout();
        }
        synchronized (this) {
            return soTimeout == null ? 0 : soTimeout;
        }
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("negative send size");
        }
        setOptionIntern(StandardSocketOptions.SO_SNDBUF, size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getSendBufferSize();
        }
        Object v = pending(StandardSocketOptions.SO_SNDBUF);
        return v == null ? super.getSendBufferSize() : (Integer)v;
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        setOptionIntern(StandardSocketOptions.SO_RCVBUF, size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getReceiveBufferSize();
        }
        Object v = pending(StandardSocketOptions.SO_RCVBUF);
        return v == null ? super.getReceiveBufferSize() : (Integer)v;
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        setOptionIntern(StandardSocketOptions.SO_KEEPALIVE, on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getKeepAlive();
        }
        return Boolean.TRUE.equals(pending(StandardSocketOptions.SO_KEEPALIVE));
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        }
        setOptionIntern(StandardSocketOptions.IP_TOS, tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getTrafficClass();
        }
        Object v = pending(StandardSocketOptions.IP_TOS);
        return v == null ? 0 : (Integer)v;
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        setOptionIntern(StandardSocketOptions.SO_REUSEADDR, on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        Socket s = current();
        if (s != null) {
            return s.getReuseAddress();
        }
        return Boolean.TRUE.equals(pending(StandardSocketOptions.SO_REUSEADDR));
    }

    @Override
    public void close() throws IOException {
        Socket s;
        Socket a;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            s = inner;
            a = attempt;
        }
        // a connection attempt in progress is aborted
        if (a != null) {
            closeQuietly(a);
        }
        try {
            if (s != null) {
                s.close();
            }
        } finally {
            super.close();
        }
    }

    @Override
    public void shutdownInput() throws IOException {
        connected().shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        connected().shutdownOutput();
    }

    @Override
    public String toString() {
        Socket s = inner;
        return "GuardedSocket[" + (s == null ? "unconnected" : s.toString()) + "]";
    }

    @Override
    public boolean isConnected() {
        Socket s = inner;
        return s != null && s.isConnected();
    }

    @Override
    public boolean isBound() {
        Socket s = inner;
        return s != null && s.isBound();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isInputShutdown() {
        Socket s = inner;
        return s != null && s.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        Socket s = inner;
        return s != null && s.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        Socket s = inner;
        if (s != null) {
            s.setPerformancePreferences(connectionTime, latency, bandwidth);
            return;
        }
        synchronized (this) {
            performance = new int[]{connectionTime, latency, bandwidth};
        }
    }

    @Override
    public <T> Socket setOption(SocketOption<T> name, T value) throws IOException {
        setOptionIntern(name, value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name) throws IOException {
        Socket s = current();
        if (s != null) {
            return s.getOption(name);
        }
        Object v = pending(name);
        return v != null ? (T)v : super.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return super.supportedOptions();
    }

    //! sets an option on the connected socket, or records it for the connection
    private <T> void setOptionIntern(SocketOption<T> name, T value) throws SocketException {
        Socket s = current();
        if (s != null) {
            try {
                s.setOption(name, value);
            } catch (SocketException e) {
                throw e;
            } catch (IOException e) {
                throw new SocketException(e.getMessage());
            }
            return;
        }
        if (!super.supportedOptions().contains(name)) {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }
        synchronized (this) {
            options.put(name, value);
        }
    }

    private synchronized Object pending(SocketOption<?> name) {
        return options.get(name);
    }
}
