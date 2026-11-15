// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

//! A fake database server for the jdbc sandbox tests (qore#5786): no real database is needed
/** Listens on a loopback address and port 0, and records every accept.  The modes:
    - \c pg: the PostgreSQL (Redshift) protocol: answers an SSLRequest with \c N, reads the StartupMessage and sends
      the ErrorResponse \c qore-fake-pg
    - \c mysql: the MySQL (SingleStore) protocol: sends the error packet \c qore-fake-mysql on accept
    - \c mysql-ok: a minimal handshake that accepts any password, answers the driver's initialization query and
      session and transaction control (\c set, \c commit, \c rollback) with OK, and every other query with the error
      \c qore-fake-mysql-query; those queries are recorded
    - \c mysql-infile: a minimal handshake that accepts any password, then answers the first query with a LOCAL
      INFILE request for a file name; the bytes of file data that the client sends are recorded

    Created and controlled by trusted test code only.
*/
public final class FakeDbServer implements AutoCloseable {
    private final String mode;
    private final ServerSocket server;
    private final Thread thread;
    private final List<String> accepts = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private final List<String> startupParams = new ArrayList<>();
    private final List<Socket> clients = new ArrayList<>();
    private volatile boolean closing = false;
    private javax.net.ssl.SSLContext tlsContext;
    private int tlsHandshakes = 0;
    private long infileBytes = 0;
    private int infileRequests = 0;
    private String infileName = "/etc/hostname";

    //! Starts a server in the given mode on the given loopback address and port 0
    public FakeDbServer(String mode, String address) throws IOException {
        if (!mode.equals("pg") && !mode.equals("mysql") && !mode.equals("mysql-ok") && !mode.equals("mysql-infile")) {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
        this.mode = mode;
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName(address), 0), 50);
        thread = new Thread(this::serve, "qore-fake-db-" + mode);
        thread.setDaemon(true);
        thread.start();
    }

    //! Returns the port the server listens on
    public int getPort() {
        return server.getLocalPort();
    }

    //! Returns the number of connections accepted
    public synchronized int acceptCount() {
        return accepts.size();
    }

    //! Waits until the server has accepted at least \a n connections, at most \a timeoutMs, and returns the count
    /** A client's connect() can return before the server thread has accepted the connection: a test that does not
        read from the server waits for the accept with this method.
    */
    public synchronized int awaitAcceptCount(int n, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1000000L;
        while (accepts.size() < n) {
            long ms = (deadline - System.nanoTime()) / 1000000L;
            if (ms <= 0) {
                break;
            }
            wait(ms);
        }
        return accepts.size();
    }

    //! Returns the local address of each accepted connection, in order
    public synchronized String[] acceptAddresses() {
        return accepts.toArray(new String[0]);
    }

    //! Returns the queries received in the mysql-ok mode, in order
    public synchronized String[] getQueries() {
        return queries.toArray(new String[0]);
    }

    //! Returns the StartupMessage parameters of the pg mode (key=value), in order
    public synchronized String[] getStartupParams() {
        return startupParams.toArray(new String[0]);
    }

    //! Returns the number of LOCAL INFILE requests sent in the mysql-infile mode
    public synchronized int infileRequests() {
        return infileRequests;
    }

    //! Returns the number of bytes of file data received after LOCAL INFILE requests
    public synchronized long infileBytes() {
        return infileBytes;
    }

    //! Makes the mysql modes offer TLS with the key and certificate of a PKCS12 key store
    public synchronized void setTls(String keyStore, String password) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.FileInputStream fis = new java.io.FileInputStream(keyStore)) {
            ks.load(fis, password.toCharArray());
        }
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
            javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        tlsContext = ctx;
    }

    //! Returns the number of TLS handshakes made in the mysql modes
    public synchronized int tlsHandshakes() {
        return tlsHandshakes;
    }

    //! Closes every connection that the server accepted, as a lost connection
    public void dropClients() {
        List<Socket> l;
        synchronized (this) {
            l = new ArrayList<>(clients);
        }
        for (Socket c : l) {
            try {
                // shutdown() wakes the server thread's read and sends FIN to the client
                c.shutdownInput();
                c.shutdownOutput();
            } catch (IOException e) {
                // closed already
            }
            try {
                c.close();
            } catch (IOException e) {
                // closed already
            }
        }
    }

    //! Sets the file name of the LOCAL INFILE request
    public synchronized void setInfileName(String name) {
        infileName = name;
    }

    //! Stops the server
    @Override
    public void close() throws IOException {
        if (closing) {
            return;
        }
        closing = true;
        // a blocked accept() is not woken by close() in a process whose threads block the JDK's wakeup signal (Qore
        // does): a connection of its own wakes it, and the server thread then sees the flag and ends
        try (Socket wake = new Socket()) {
            wake.connect(server.getLocalSocketAddress(), 5000);
        } catch (IOException e) {
            // the server thread ended already
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.close();
        }
    }

    private void serve() {
        while (!closing) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                // the server was closed
                break;
            }
            if (closing) {
                try {
                    s.close();
                } catch (IOException e) {
                    // closed already
                }
                break;
            }
            synchronized (this) {
                accepts.add(s.getLocalAddress().getHostAddress());
                clients.add(s);
                notifyAll();
            }
            Thread t = new Thread(() -> {
                try (Socket c = s) {
                    c.setSoTimeout(30000);
                    handle(c);
                } catch (IOException e) {
                    // the client closed the connection
                }
            }, "qore-fake-db-conn-" + mode);
            t.setDaemon(true);
            t.start();
        }
        synchronized (this) {
            for (Socket c : clients) {
                try {
                    c.close();
                } catch (IOException e) {
                    // already closed
                }
            }
        }
    }

    private void handle(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();
        switch (mode) {
            case "pg":
                handlePg(in, out);
                break;
            case "mysql":
                writePacket(out, 0, errorPacket("qore-fake-mysql"));
                break;
            default:
                handleMysql(s, in, out);
                break;
        }
        // let the client read the reply before the connection is closed
        try {
            while (in.read() >= 0) {
            }
        } catch (IOException e) {
            // closed by the client
        }
    }

    private void handlePg(DataInputStream in, OutputStream out) throws IOException {
        int len = in.readInt();
        byte[] b = new byte[len - 4];
        in.readFully(b);
        int code = ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16) | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
        if (code == 80877103) {
            // SSLRequest: no TLS
            out.write('N');
            out.flush();
            try {
                len = in.readInt();
            } catch (EOFException e) {
                // the client requires TLS and gave up
                return;
            }
            b = new byte[len - 4];
            in.readFully(b);
        }
        // StartupMessage: protocol version, then key\0value\0 pairs
        List<String> params = new ArrayList<>();
        int i = 4;
        while (i < b.length && b[i] != 0) {
            int ks = i;
            while (b[i] != 0) {
                ++i;
            }
            String key = new String(b, ks, i - ks, StandardCharsets.UTF_8);
            int vs = ++i;
            while (i < b.length && b[i] != 0) {
                ++i;
            }
            String val = new String(b, vs, i - vs, StandardCharsets.UTF_8);
            ++i;
            params.add(key + "=" + val);
        }
        synchronized (this) {
            startupParams.clear();
            startupParams.addAll(params);
        }
        byte[] msg = "SFATAL\0C28000\0Mqore-fake-pg\0\0".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write('E');
        int ml = msg.length + 4;
        bo.write((ml >> 24) & 0xff);
        bo.write((ml >> 16) & 0xff);
        bo.write((ml >> 8) & 0xff);
        bo.write(ml & 0xff);
        bo.write(msg);
        out.write(bo.toByteArray());
        out.flush();
    }

    private void handleMysql(Socket s, DataInputStream in, OutputStream out) throws IOException {
        javax.net.ssl.SSLContext tls;
        synchronized (this) {
            tls = tlsContext;
        }
        // initial handshake v10
        ByteArrayOutputStream hs = new ByteArrayOutputStream();
        hs.write(0x0a);
        hs.write("5.7.32-qore-fake".getBytes(StandardCharsets.US_ASCII));
        hs.write(0);
        writeInt(hs, 4, 1);
        byte[] seed = "abcdefgh".getBytes(StandardCharsets.US_ASCII);
        hs.write(seed);
        hs.write(0);
        // LONG_PASSWORD | FOUND_ROWS | LONG_FLAG | CONNECT_WITH_DB | LOCAL_FILES | PROTOCOL_41 | TRANSACTIONS
        // | SECURE_CONNECTION | MULTI_STATEMENTS | MULTI_RESULTS | PLUGIN_AUTH
        int caps = 0x1 | 0x2 | 0x4 | 0x8 | 0x80 | 0x200 | 0x2000 | 0x8000 | 0x10000 | 0x20000 | 0x80000;
        if (tls != null) {
            // CLIENT_SSL
            caps |= 0x800;
        }
        writeInt(hs, 2, caps & 0xffff);
        hs.write(33);
        writeInt(hs, 2, 2);
        writeInt(hs, 2, (caps >> 16) & 0xffff);
        hs.write(21);
        hs.write(new byte[10]);
        hs.write("ijklmnopqrst".getBytes(StandardCharsets.US_ASCII));
        hs.write(0);
        hs.write("mysql_native_password".getBytes(StandardCharsets.US_ASCII));
        hs.write(0);
        writePacket(out, 0, hs.toByteArray());

        // the handshake response, or an SSL request (32 bytes) before it; any password is accepted
        int[] seq = new int[1];
        byte[] resp = readPacket(in, seq);
        if (tls != null && resp.length == 32 && (resp[1] & 0x08) != 0) {
            javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket)tls.getSocketFactory().createSocket(s,
                s.getInetAddress().getHostAddress(), s.getPort(), true);
            ssl.setUseClientMode(false);
            ssl.startHandshake();
            synchronized (this) {
                ++tlsHandshakes;
            }
            in = new DataInputStream(ssl.getInputStream());
            out = ssl.getOutputStream();
            readPacket(in, seq);
        }
        writePacket(out, seq[0] + 1, okPacket());

        boolean first = true;
        while (true) {
            byte[] cmd;
            try {
                cmd = readPacket(in, seq);
            } catch (EOFException e) {
                return;
            }
            if (cmd.length == 0) {
                continue;
            }
            if (cmd[0] == 0x01) {
                // COM_QUIT
                return;
            }
            if (cmd[0] != 0x03) {
                writePacket(out, seq[0] + 1, errorPacket("qore-fake-mysql-command"));
                continue;
            }
            String sql = new String(cmd, 1, cmd.length - 1, StandardCharsets.UTF_8);
            if (mode.equals("mysql-infile")) {
                String name;
                synchronized (this) {
                    ++infileRequests;
                    name = infileName;
                }
                ByteArrayOutputStream req = new ByteArrayOutputStream();
                req.write(0xfb);
                req.write(name.getBytes(StandardCharsets.UTF_8));
                writePacket(out, seq[0] + 1, req.toByteArray());
                // the file data, ending with an empty packet
                while (true) {
                    byte[] data;
                    try {
                        data = readPacket(in, seq);
                    } catch (EOFException e) {
                        return;
                    }
                    if (data.length == 0) {
                        break;
                    }
                    synchronized (this) {
                        infileBytes += data.length;
                    }
                }
                writePacket(out, seq[0] + 1, errorPacket("qore-fake-mysql-infile"));
                continue;
            }
            if (first && sql.startsWith("SELECT @@max_allowed_packet")) {
                first = false;
                writeInitResult(out, seq[0] + 1);
                continue;
            }
            // session and transaction control (ex: "set autocommit=0" when a transaction is started) succeeds
            String lsql = sql.trim().toLowerCase(java.util.Locale.ROOT);
            if (lsql.startsWith("set ") || lsql.equals("commit") || lsql.equals("rollback")) {
                writePacket(out, seq[0] + 1, okPacket());
                continue;
            }
            synchronized (this) {
                queries.add(sql);
            }
            writePacket(out, seq[0] + 1, errorPacket("qore-fake-mysql-query"));
        }
    }

    //! the result of "SELECT @@max_allowed_packet, @@aggregator_id, @@sql_select_limit, @@memsql_version"
    private static void writeInitResult(OutputStream out, int seq) throws IOException {
        String[] names = {"@@max_allowed_packet", "@@aggregator_id", "@@sql_select_limit", "@@memsql_version"};
        String[] values = {"16777216", "0", "18446744073709551615", "8.5.0"};
        writePacket(out, seq++, new byte[]{(byte)names.length});
        for (String n : names) {
            ByteArrayOutputStream col = new ByteArrayOutputStream();
            writeLenencString(col, "def");
            writeLenencString(col, "");
            writeLenencString(col, "");
            writeLenencString(col, "");
            writeLenencString(col, n);
            writeLenencString(col, "");
            col.write(0x0c);
            writeInt(col, 2, 33);
            writeInt(col, 4, 255);
            // MYSQL_TYPE_VAR_STRING
            col.write(0xfd);
            writeInt(col, 2, 0);
            col.write(0);
            writeInt(col, 2, 0);
            writePacket(out, seq++, col.toByteArray());
        }
        writePacket(out, seq++, eofPacket());
        ByteArrayOutputStream row = new ByteArrayOutputStream();
        for (String v : values) {
            writeLenencString(row, v);
        }
        writePacket(out, seq++, row.toByteArray());
        writePacket(out, seq, eofPacket());
    }

    private static byte[] okPacket() {
        // OK, 0 affected rows, 0 last insert id, autocommit status, 0 warnings
        return new byte[]{0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00};
    }

    private static byte[] eofPacket() {
        return new byte[]{(byte)0xfe, 0x00, 0x00, 0x02, 0x00};
    }

    private static byte[] errorPacket(String msg) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(0xff);
        // ER_ACCESS_DENIED_ERROR
        writeInt(bo, 2, 1045);
        bo.write('#');
        bo.write("28000".getBytes(StandardCharsets.US_ASCII), 0, 5);
        byte[] m = msg.getBytes(StandardCharsets.UTF_8);
        bo.write(m, 0, m.length);
        return bo.toByteArray();
    }

    private static void writeLenencString(ByteArrayOutputStream bo, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length >= 251) {
            throw new IllegalArgumentException("string too long");
        }
        bo.write(b.length);
        bo.write(b, 0, b.length);
    }

    private static void writeInt(ByteArrayOutputStream bo, int bytes, long v) {
        for (int i = 0; i < bytes; ++i) {
            bo.write((int)((v >> (8 * i)) & 0xff));
        }
    }

    private static void writePacket(OutputStream out, int seq, byte[] payload) throws IOException {
        byte[] h = {(byte)(payload.length & 0xff), (byte)((payload.length >> 8) & 0xff),
            (byte)((payload.length >> 16) & 0xff), (byte)(seq & 0xff)};
        out.write(h);
        out.write(payload);
        out.flush();
    }

    private static byte[] readPacket(InputStream in, int[] seq) throws IOException {
        byte[] h = new byte[4];
        readFully(in, h);
        int len = (h[0] & 0xff) | ((h[1] & 0xff) << 8) | ((h[2] & 0xff) << 16);
        seq[0] = h[3] & 0xff;
        byte[] payload = new byte[len];
        readFully(in, payload);
        return payload;
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) {
                throw new EOFException();
            }
            off += n;
        }
    }
}
