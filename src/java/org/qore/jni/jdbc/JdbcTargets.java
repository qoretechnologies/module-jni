/*
    JdbcTargets.java

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

import java.util.ArrayList;
import java.util.List;

//! The destinations of a JDBC URL, in the order of the URL (qore#5786)
/** Each host is kept as the driver's parser gives it (\c host) and as the sandbox checks it (\c key: without IPv6
    brackets, lowercased).
*/
final class JdbcTargets {
    //! the host strings, as the driver's parser gives them
    final List<String> hosts = new ArrayList<>();
    //! the ports
    final List<Integer> ports = new ArrayList<>();

    void add(String host, int port) {
        hosts.add(host);
        ports.add(port);
    }

    int size() {
        return hosts.size();
    }

    //! Returns the host names for the sandbox check: without IPv6 brackets, lowercased
    String[] checkHosts() {
        String[] rv = new String[hosts.size()];
        for (int i = 0; i < rv.length; ++i) {
            rv[i] = JdbcConnectGuard.normalizeHost(hosts.get(i));
        }
        return rv;
    }

    int[] portArray() {
        int[] rv = new int[ports.size()];
        for (int i = 0; i < rv.length; ++i) {
            rv[i] = ports.get(i);
        }
        return rv;
    }

    //! Returns true if both have the same destinations in the same order; hosts are compared as the sandbox sees them
    boolean sameAs(JdbcTargets other) {
        if (other.size() != size()) {
            return false;
        }
        for (int i = 0; i < size(); ++i) {
            if (!JdbcConnectGuard.normalizeHost(hosts.get(i)).equals(JdbcConnectGuard.normalizeHost(other.hosts.get(i)))
                || !ports.get(i).equals(other.ports.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(hosts.get(i)).append(':').append(ports.get(i));
        }
        return sb.toString();
    }
}
