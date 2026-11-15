/*
    JdbcConnectGuard.java

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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//! The destinations that one restricted JDBC connection may connect to (qore#5786)
/** A guard holds the destinations of a connection's URL as they were checked against the sandbox before the driver
    ran: each host name and port, pinned to the addresses that were checked, and every checked address with its port.
    Every socket of the connection is a GuardedSocket that connects only to those addresses, on any thread, without
    asking %Qore again: the guard is a table, not a policy, so the driver's own threads (login timeouts, cancel
    timers, failover on statement threads) need no %Qore caller.

    A guard is pinned once and is immutable afterwards.  It is bound to its connection by a registry token (Redshift,
    whose socket factory gets a string argument) or by the object itself (SingleStore, whose factory gets the
    driver's configuration).  When the connection is closed, the guard is closed: its sockets refuse to connect, which
    stops a late cancel or reconnect of a factory that still holds it.

    @note No class of this package may be declared sandbox-aware or made available to sandboxed code: a guard opens
    sockets to the destinations it holds without a check.

    @since jni 3.0
*/
public final class JdbcConnectGuard {
    //! The key of the guard object in the driver properties of a SingleStore connection
    public static final String PROPERTY = "org.qore.jni.jdbc.guard";

    //! the guards of open connections by token
    private static final ConcurrentHashMap<String, JdbcConnectGuard> registry = new ConcurrentHashMap<>();
    //! the source of the tokens
    private static final SecureRandom random = new SecureRandom();

    //! lower(host) ":" port -> the checked addresses, in the order to try them; null until pinned
    private volatile Map<String, InetAddress[]> pinned;
    //! the checked addresses with their ports, as address ":" port
    private volatile Set<String> checked;
    //! true once the connection was closed
    private volatile boolean closed;
    //! the registry token, if registered
    private String token;
    //! the number of sockets that connected
    private final AtomicInteger sockets = new AtomicInteger();
    //! the last refusal, for the error of the connection
    private volatile String lastRefusal;

    //! Creates a guard that refuses every destination until it is pinned
    JdbcConnectGuard() {
    }

    //! Creates a pinned guard for the given destinations, which must have been checked already
    /** @param hosts the host names (or address literals) of the destinations
        @param ports the ports of the destinations
        @param addrs the checked numeric addresses of each destination, in the order to try them

        @return a pinned guard; it is not registered
    */
    public static JdbcConnectGuard forTargets(String[] hosts, int[] ports, String[][] addrs)
            throws UnknownHostException {
        if (hosts.length != ports.length || hosts.length != addrs.length) {
            throw new IllegalArgumentException("the destination arrays differ in length");
        }
        InetAddress[][] a = new InetAddress[addrs.length][];
        for (int i = 0; i < addrs.length; ++i) {
            a[i] = new InetAddress[addrs[i].length];
            for (int j = 0; j < addrs[i].length; ++j) {
                a[i][j] = literal(addrs[i][j]);
            }
        }
        JdbcConnectGuard g = new JdbcConnectGuard();
        g.pin(hosts, ports, a);
        return g;
    }

    //! Returns the address of a numeric address literal; no name is looked up
    static InetAddress literal(String addr) throws UnknownHostException {
        if (!isLiteral(addr)) {
            throw new UnknownHostException("not a numeric address: " + addr);
        }
        return InetAddress.getByName(addr);
    }

    //! Returns true if the string is an IPv4 or IPv6 address literal (without brackets)
    static boolean isLiteral(String host) {
        if (host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            // IPv6, optionally with an embedded IPv4 address and a numeric scope
            return host.matches("[0-9A-Fa-f:.]+(%[0-9]+)?");
        }
        return host.matches("(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])"
            + "(\\.(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])){3}");
    }

    //! Returns the key of a host name: IPv6 brackets are removed, and names are lowercased
    static String normalizeHost(String host) {
        if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            host = host.substring(1, host.length() - 1);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    //! Returns the key of an address with a port
    private static String addressKey(InetAddress a, int port) {
        String s = a.getHostAddress();
        if (a instanceof Inet6Address) {
            // a numeric scope is part of the address; a named one is the interface's
            int pct = s.indexOf('%');
            Inet6Address a6 = (Inet6Address)a;
            if (pct >= 0) {
                s = s.substring(0, pct) + (a6.getScopeId() != 0 ? "%" + a6.getScopeId() : "");
            }
        }
        return s + ":" + port;
    }

    //! Pins the checked destinations; a guard is pinned once
    synchronized void pin(String[] hosts, int[] ports, InetAddress[][] addrs) {
        if (pinned != null) {
            throw new IllegalStateException("the destinations of the connection guard are pinned already");
        }
        if (hosts.length != ports.length || hosts.length != addrs.length || hosts.length == 0) {
            throw new IllegalArgumentException("invalid destinations of the connection guard");
        }
        Map<String, InetAddress[]> p = new HashMap<>();
        Set<String> c = new HashSet<>();
        for (int i = 0; i < hosts.length; ++i) {
            if (addrs[i].length == 0 || ports[i] < 1 || ports[i] > 65535) {
                throw new IllegalArgumentException("invalid destination of the connection guard");
            }
            String key = normalizeHost(hosts[i]) + ":" + ports[i];
            InetAddress[] a = addrs[i].clone();
            InetAddress[] old = p.get(key);
            if (old != null && !Arrays.equals(old, a)) {
                throw new IllegalArgumentException("destination " + key + " was checked twice with other addresses");
            }
            p.put(key, a);
            for (InetAddress addr : a) {
                c.add(addressKey(addr, ports[i]));
            }
        }
        checked = Set.copyOf(c);
        pinned = Map.copyOf(p);
    }

    //! Returns true if the guard is pinned
    boolean isPinned() {
        return pinned != null;
    }

    //! Registers the guard and returns its token: 128 random bits as hex
    public synchronized String register() {
        if (token != null) {
            return token;
        }
        byte[] b = new byte[16];
        while (true) {
            random.nextBytes(b);
            String t = HexFormat.of().formatHex(b);
            if (registry.putIfAbsent(t, this) == null) {
                token = t;
                return t;
            }
        }
    }

    //! Removes the guard from the registry
    public synchronized void unregister() {
        if (token != null) {
            registry.remove(token, this);
            token = null;
        }
    }

    //! Returns the guard registered with the token, or null
    static JdbcConnectGuard lookup(String token) {
        return token == null ? null : registry.get(token);
    }

    //! Returns true if a guard is registered with the token
    public static boolean isRegistered(String token) {
        return lookup(token) != null;
    }

    //! Returns the number of registered guards
    public static int registeredCount() {
        return registry.size();
    }

    //! Returns a new socket that connects only to the destinations of the guard
    public Socket newSocket() {
        return new GuardedSocket(this);
    }

    //! Returns a new socket of the guard given as an object
    /** For the SingleStore adapter, which gets the guard from the driver's configuration: a value that a URL sets
        there is a String, never a guard, and is refused.

        @throw SocketException NETWORK-ACCESS-DENIED if \a guard is not a guard
    */
    public static Socket socketFor(Object guard) throws SocketException {
        if (!(guard instanceof JdbcConnectGuard)) {
            throw new SocketException("NETWORK-ACCESS-DENIED: the connection has no connection guard of the jni "
                + "module (" + (guard == null ? "none" : guard.getClass().getName()) + ")");
        }
        return ((JdbcConnectGuard)guard).newSocket();
    }

    //! Closes the guard: its sockets refuse to connect from now on
    public void close() {
        closed = true;
        unregister();
    }

    //! Returns true if the guard was closed
    public boolean isClosed() {
        return closed;
    }

    //! Returns the number of sockets that connected through the guard
    public int socketCount() {
        return sockets.get();
    }

    //! Returns the last destination that the guard refused, or null
    public String lastRefusal() {
        return lastRefusal;
    }

    //! Records a refusal and returns its message
    String refuse(String msg) {
        lastRefusal = msg;
        return "NETWORK-ACCESS-DENIED: " + msg;
    }

    //! Counts a connected socket
    void connected() {
        sockets.incrementAndGet();
    }

    //! Returns the checked addresses of a destination, or null if it was not checked
    /** A host name gets the addresses it was pinned to; an address literal is accepted only if it was checked with
        the port (the SingleStore cancel connects to the address of the current socket).
    */
    InetAddress[] resolve(String host, int port) {
        Map<String, InetAddress[]> p = pinned;
        if (p == null) {
            return null;
        }
        String h = normalizeHost(host);
        InetAddress[] a = p.get(h + ":" + port);
        if (a != null) {
            return a.clone();
        }
        if (isLiteral(h)) {
            try {
                InetAddress addr = literal(h);
                if (checked.contains(addressKey(addr, port))) {
                    return new InetAddress[]{addr};
                }
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }
}
