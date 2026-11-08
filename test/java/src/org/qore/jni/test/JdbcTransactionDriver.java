// Copyright (C) 2026 Qore Technologies, s.r.o.
// SPDX-License-Identifier: MIT
package org.qore.jni.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.sql.Blob;
import java.sql.Clob;
import java.time.Instant;
import java.util.logging.Logger;

/** Instrumented JDBC driver for transaction-boundary and failure-path tests. */
public final class JdbcTransactionDriver implements Driver {
    // All fixture state and proxy callbacks are serialized on this class's monitor.
    private static final ArrayList<String> events = new ArrayList<>();
    private static String failure = "";
    // a scripted connection loss: the event at which the connection is lost, and for an execution, whether the
    // server applied the statement first
    private static final class Loss {
        final String operation;
        final boolean applied;

        Loss(String operation, boolean applied) {
            this.operation = operation;
            this.applied = applied;
        }
    }
    // scripted connection losses, in order
    private static final ArrayList<Loss> losses = new ArrayList<>();
    private static int durable;
    private static boolean installed;

    public static synchronized void reset() throws SQLException {
        if (!installed) {
            DriverManager.registerDriver(new JdbcTransactionDriver());
            installed = true;
        }
        events.clear();
        failure = "";
        losses.clear();
        durable = 0;
    }

    /** Returns a new connection of this driver without DriverManager (for helpers loaded by other class loaders) */
    public static synchronized Connection open() {
        return new Session().connect();
    }

    public static synchronized void failNext(String operation) {
        failure = operation;
    }

    /** The connection is lost at the next "execute:<sql>:<auto-commit>" event, or at the next "auto:<value>" event
        (a request to change the auto-commit mode, which finds the session gone); with {@code applied} the server
        executed the statement before the connection dropped (committing it in auto-commit mode).  Each call adds a
        loss; the losses happen in the order they were added. */
    public static synchronized void loseConnectionAt(String operation, boolean applied) {
        losses.add(new Loss(operation, applied));
    }

    // returns null if the connection is not lost at this event, otherwise the loss, which is removed
    private static Loss takeLoss(String operation) {
        if (losses.isEmpty() || !losses.get(0).operation.equals(operation)) {
            return null;
        }
        return losses.remove(0);
    }

    public static synchronized int count(String operation) {
        int rv = 0;
        for (String e : events) {
            if (e.equals(operation)) {
                ++rv;
            }
        }
        return rv;
    }

    public static synchronized String[] events() {
        return events.toArray(new String[0]);
    }

    public static synchronized int durable() {
        return durable;
    }

    private static void event(String operation) throws SQLException {
        events.add(operation);
        if (failure.equals(operation)) {
            failure = "";
            throw new SQLException("injected " + operation);
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, String name) {
        switch (method.getName()) {
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == args[0];
            case "toString": return name;
            default: return null;
        }
    }

    /** A result set with one INTEGER column; with {@code failure}, next() raises it (a later statement's error
        reported when its rows are read). */
    private static ResultSet resultSet(String column, int[] values, String failure) {
        ResultSetMetaData metadata = (ResultSetMetaData) Proxy.newProxyInstance(
            JdbcTransactionDriver.class.getClassLoader(), new Class<?>[] {ResultSetMetaData.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getColumnCount": return 1;
                    case "getColumnLabel":
                    case "getColumnName": return column;
                    case "getColumnType": return Types.INTEGER;
                    case "getColumnTypeName": return "integer";
                    case "getPrecision": return 10;
                    case "getScale": return 0;
                    case "isNullable": return ResultSetMetaData.columnNoNulls;
                    case "hashCode":
                    case "equals":
                    case "toString": return objectMethod(proxy, method, args, "test result set metadata");
                    default: throw new SQLFeatureNotSupportedException(method.getName());
                }
            });
        // 0 = before the first row; values.length + 1 = after the last row
        int[] row = {0};
        boolean[] closed = {false};
        return (ResultSet) Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
            new Class<?>[] {ResultSet.class}, (proxy, method, args) -> {
                synchronized (JdbcTransactionDriver.class) {
                    switch (method.getName()) {
                        case "next":
                            if (closed[0]) {
                                throw new SQLException("result set is closed");
                            }
                            if (failure != null) {
                                throw new SQLException(failure);
                            }
                            if (row[0] < values.length) {
                                ++row[0];
                                return true;
                            }
                            row[0] = values.length + 1;
                            return false;
                        case "getObject":
                        case "getInt":
                            if (closed[0] || row[0] < 1 || row[0] > values.length) {
                                throw new SQLException("no current row");
                            }
                            return values[row[0] - 1];
                        case "wasNull": return false;
                        case "getMetaData": return metadata;
                        case "close": closed[0] = true; return null;
                        case "isClosed": return closed[0];
                        case "hashCode":
                        case "equals":
                        case "toString": return objectMethod(proxy, method, args, "test result set");
                        default: throw new SQLFeatureNotSupportedException(method.getName());
                    }
                }
            });
    }

    /** A column of a typed result set: its metadata, and its value in the only row; without a class name,
        getColumnClassName() is not supported for the column */
    private static final class Column {
        final String label;
        final int type;
        final String typeName;
        final int precision;
        final int scale;
        final int nullable;
        final String className;
        // the value, or a Supplier of a new value for each row (ex: a large object, which is freed)
        final Object value;
        // getObject(int, Class) raises SQLFeatureNotSupportedException for the column (an older driver)
        final boolean noGetObjectAs;
        // the values of getString(int) and getTimestamp(int)
        final String stringValue;
        final Timestamp timestampValue;
        // getObject(int, Class) raises this error for the column (ex: a lost connection)
        SQLException getObjectAsError;
        // getObject(int, Class) raises an IllegalArgumentException for the column
        boolean getObjectAsRuntimeError;

        Column(String label, int type, String typeName, int precision, int scale, int nullable, String className,
                Object value) {
            this(label, type, typeName, precision, scale, nullable, className, value, false, null, null);
        }

        Column(String label, int type, String typeName, int precision, int scale, int nullable, String className,
                Object value, boolean noGetObjectAs, String stringValue, Timestamp timestampValue) {
            this.label = label;
            this.type = type;
            this.typeName = typeName;
            this.precision = precision;
            this.scale = scale;
            this.nullable = nullable;
            this.className = className;
            this.value = value;
            this.noGetObjectAs = noGetObjectAs;
            this.stringValue = stringValue;
            this.timestampValue = timestampValue;
        }
    }

    // the latches of the cancellation of a large object read: the reader enters the second chunk, and waits there
    // until it is released
    private static CountDownLatch lobEntered = new CountDownLatch(1);
    private static CountDownLatch lobRelease = new CountDownLatch(1);

    /** Resets the latches of the large object that blocks in its second chunk ("typed=lobcancel") */
    public static void resetLobLatches() {
        lobEntered = new CountDownLatch(1);
        lobRelease = new CountDownLatch(1);
    }

    /** Waits until a reader reads the second chunk of the blocking large object */
    public static void awaitLobEntered() throws InterruptedException {
        lobEntered.await();
    }

    /** Lets the reader of the blocking large object continue */
    public static void releaseLob() {
        lobRelease.countDown();
    }

    /** Waits in this Java call until releaseLob(), after making awaitLobEntered() return: the caller is in the call
        when it is cancelled */
    public static void blockUntilReleased() throws InterruptedException {
        lobEntered.countDown();
        lobRelease.await();
    }

    // records an event without the class monitor, which a typed result set holds while it gives a value
    private static void lobEvent(String operation) {
        synchronized (JdbcTransactionDriver.class) {
            events.add(operation);
        }
    }

    /** A large object of the given bytes or characters (Blob for a byte[], Clob for a String); {@code freeError} is
        thrown by free(); {@code readError} by any read; with {@code block}, the read of the second chunk waits for
        releaseLob() */
    private static Object lob(String name, Object data, Throwable freeError, SQLException readError, boolean block) {
        return lob(name, data, freeError, readError, block, null);
    }

    /** A large object as above whose length() returns {@code length} instead of its length (null: its length) */
    private static Object lob(String name, Object data, Throwable freeError, SQLException readError, boolean block,
            Long length) {
        boolean blob = data instanceof byte[];
        return Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
            new Class<?>[] {blob ? Blob.class : Clob.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "length":
                        if (length != null) {
                            return length;
                        }
                        return blob ? (long) ((byte[]) data).length : (long) ((String) data).length();
                    case "getBytes":
                    case "getSubString": {
                        if (readError != null) {
                            throw readError;
                        }
                        int pos = (int) (long) (Long) args[0] - 1;
                        int n = (Integer) args[1];
                        lobEvent("lob.read:" + name + ":" + (pos + 1));
                        if (block && pos > 0) {
                            lobEntered.countDown();
                            lobRelease.await();
                        }
                        if (blob) {
                            byte[] b = (byte[]) data;
                            return java.util.Arrays.copyOfRange(b, Math.min(b.length, pos),
                                Math.min(b.length, pos + n));
                        }
                        String str = (String) data;
                        return str.substring(Math.min(str.length(), pos), Math.min(str.length(), pos + n));
                    }
                    case "free":
                        lobEvent("lob.free:" + name);
                        if (freeError != null) {
                            throw freeError;
                        }
                        return null;
                    case "hashCode":
                    case "equals":
                    case "toString": return objectMethod(proxy, method, args, "test large object " + name);
                    default: throw new SQLFeatureNotSupportedException(method.getName());
                }
            });
    }

    // the bytes of a BLOB of an unknown length: more than two chunks
    private static byte[] unknownBytes() {
        byte[] b = new byte[150000];
        for (int i = 0; i < b.length; ++i) {
            b[i] = (byte) (i % 251);
        }
        return b;
    }

    // a CLOB of a surrogate pair whose getSubString() gives one character at a time
    private static Object oneUnitClob() {
        String data = new StringBuilder().appendCodePoint(0x1f600).toString();
        return Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(), new Class<?>[] {Clob.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "length": return (long) data.length();
                    case "getSubString": {
                        int pos = (int) (long) (Long) args[0] - 1;
                        return data.substring(pos, pos + 1);
                    }
                    case "free": return null;
                    case "hashCode":
                    case "equals":
                    case "toString": return objectMethod(proxy, method, args, "test one-unit CLOB");
                    default: throw new SQLFeatureNotSupportedException(method.getName());
                }
            });
    }

    // a TIME column whose getObject(int, LocalTime.class) raises the given error (null: an IllegalArgumentException,
    // which is not an SQLException)
    private static Column asFailColumn(String label, SQLException error) {
        Column col = new Column(label, Types.TIME, "time", 8, 0, ResultSetMetaData.columnNullable, "java.sql.Time",
            java.sql.Time.valueOf("10:15:30"));
        col.getObjectAsError = error;
        col.getObjectAsRuntimeError = error == null;
        return col;
    }

    // a string of the given length whose character at index 65535 (the last of the first 64 KiB chunk) is the high
    // surrogate of a surrogate pair
    private static String splitSurrogates() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 65535) {
            sb.append('a');
        }
        sb.appendCodePoint(0x1f600);
        while (sb.length() < 140000) {
            sb.append("ř€");
            sb.appendCodePoint(0x1f600);
        }
        return sb.toString();
    }

    /** The typed result sets, by name ("typed=NAME" in a scripted statement) */
    private static final Map<String, Column[]> typedResultSets = new HashMap<>();
    static {
        typedResultSets.put("all", new Column[] {
            new Column("Id", Types.INTEGER, "int4", 10, 0, ResultSetMetaData.columnNoNulls, "java.lang.Integer", 1),
            new Column("Name", Types.VARCHAR, "varchar", 20, 0, ResultSetMetaData.columnNullable,
                "java.lang.String", "name"),
            new Column("Price", Types.DECIMAL, "numeric", 10, 2, ResultSetMetaData.columnNullable,
                "java.math.BigDecimal", new BigDecimal("1.50")),
            new Column("Units", Types.NUMERIC, "numeric", 12, 0, ResultSetMetaData.columnNullableUnknown,
                "java.math.BigDecimal", new BigDecimal("12")),
            new Column("Flag", Types.BIT, "bool", 1, 0, ResultSetMetaData.columnNullable, "java.lang.Boolean",
                Boolean.TRUE),
            new Column("Ratio", Types.DOUBLE, "float8", 17, 17, ResultSetMetaData.columnNullable,
                "java.lang.Double", 0.5),
            new Column("Data", Types.VARBINARY, "bytea", 16, 0, ResultSetMetaData.columnNullable, "[B",
                new byte[] {1, 2}),
            new Column("Created", Types.TIMESTAMP, "timestamp", 29, 6, ResultSetMetaData.columnNullable,
                "java.sql.Timestamp", Timestamp.valueOf("2026-10-21 10:15:30")),
            new Column("Uuid", Types.OTHER, "uuid", 0, 0, ResultSetMetaData.columnNullable, "java.util.UUID",
                UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")),
            // the driver does not report the class: the type is given by the SQL type
            new Column("Legacy", Types.VARCHAR, "varchar", 8, 0, ResultSetMetaData.columnNullable, null, "legacy"),
            // the class is not available to the program: the type is given by the SQL type
            new Column("Hidden", Types.BIGINT, "int8", 19, 0, ResultSetMetaData.columnNullable,
                "org.qore.jni.test.NoSuchValueClass", 5L),
        });
        // the java.time values that JDBC 4.2 drivers return (ex: Jaybird for TIMESTAMP WITH TIME ZONE)
        typedResultSets.put("times", new Column[] {
            new Column("Odt", Types.TIMESTAMP_WITH_TIMEZONE, "timestamp with time zone", 35, 6,
                ResultSetMetaData.columnNullable, "java.time.OffsetDateTime",
                OffsetDateTime.of(2026, 10, 21, 10, 15, 30, 123456000, ZoneOffset.ofHoursMinutes(5, 30))),
            new Column("Ot", Types.TIME_WITH_TIMEZONE, "time with time zone", 21, 6, ResultSetMetaData.columnNullable,
                "java.time.OffsetTime", OffsetTime.of(10, 15, 30, 0, ZoneOffset.ofHours(-3))),
            new Column("Ld", Types.DATE, "date", 10, 0, ResultSetMetaData.columnNullable, "java.time.LocalDate",
                LocalDate.of(2026, 10, 21)),
            new Column("Lt", Types.TIME, "time", 8, 0, ResultSetMetaData.columnNullable, "java.time.LocalTime",
                LocalTime.of(10, 15, 30)),
            // the driver does not report the class: the SQL type gives the type
            new Column("Unreported", Types.TIMESTAMP_WITH_TIMEZONE, "timestamp with time zone", 35, 6,
                ResultSetMetaData.columnNullable, null,
                OffsetDateTime.of(2026, 10, 21, 10, 15, 30, 0, ZoneOffset.UTC)),
        });
        // large objects: a driver that returns a byte[] or a String for a LOB column (SQLite), a free() that fails
        // (jTDS: AbstractMethodError), an empty large object that cannot be read (Oracle's empty_blob():
        // ORA-22275), and a CLOB with a surrogate pair across the first chunk
        typedResultSets.put("lobs", new Column[] {
            new Column("BlobBytes", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "[B",
                new byte[] {1, 2, 3}),
            new Column("ClobString", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.lang.String",
                "abc"),
            new Column("FreeFails", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Blob",
                (Supplier<Object>) () -> lob("FreeFails", new byte[] {4, 5}, new AbstractMethodError("free"), null,
                    false)),
            new Column("ClobFreeFails", Types.NCLOB, "nclob", 0, 0, ResultSetMetaData.columnNullable,
                "java.sql.NClob", (Supplier<Object>) () -> lob("ClobFreeFails", "x😀", new RuntimeException("free"),
                    null, false)),
            new Column("Empty", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Blob",
                (Supplier<Object>) () -> lob("Empty", new byte[0], null, new SQLException("ORA-22275: invalid LOB "
                    + "locator specified"), false)),
            new Column("EmptyClob", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Clob",
                (Supplier<Object>) () -> lob("EmptyClob", "", null, new SQLException("ORA-22275: invalid LOB "
                    + "locator specified"), false)),
            new Column("Split", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Clob",
                (Supplier<Object>) () -> lob("Split", splitSurrogates(), null, null, false)),
        });
        // a large object whose read fails, and whose free() fails too: the read error is raised
        typedResultSets.put("lobfail", new Column[] {
            new Column("ReadFails", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Blob",
                (Supplier<Object>) () -> lob("ReadFails", new byte[] {1}, new AbstractMethodError("free"),
                    new SQLException("injected read error"), false)),
        });
        // large objects of an unknown length (length() < 0), which are read until a chunk is shorter than requested
        typedResultSets.put("lobunknown", new Column[] {
            new Column("UnknownBlob", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Blob",
                (Supplier<Object>) () -> lob("UnknownBlob", unknownBytes(), null, null, false, -1L)),
            new Column("UnknownClob", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Clob",
                (Supplier<Object>) () -> lob("UnknownClob", splitSurrogates(), null, null, false, -1L)),
            // a chunk of one character, the high surrogate of a pair: the length is 2, read as 1 + 1
            new Column("OneUnit", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Clob",
                (Supplier<Object>) () -> oneUnitClob()),
        });
        // a CLOB whose second chunk waits for releaseLob(), to cancel the reader
        typedResultSets.put("lobcancelclob", new Column[] {
            new Column("BlockingClob", Types.CLOB, "clob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Clob",
                (Supplier<Object>) () -> lob("BlockingClob", "x".repeat(140000), null, null, true)),
        });
        // getObject(int, LocalTime.class) raises an SQL error that is not an unsupported conversion: it is raised
        typedResultSets.put("asfail", new Column[] {
            asFailColumn("T", new SQLException("injected connection reset", "08006")),
        });
        // getObject(int, LocalTime.class) raises other errors: a type mismatch (Derby), a message in another language;
        // the column is read with getObject(int)
        typedResultSets.put("asfallback", new Column[] {
            asFailColumn("Mismatch", new SQLException("An attempt was made to get a data value of type "
                + "'java.time.LocalTime' from a data value of type 'TIME'.", "22005")),
            asFailColumn("Localized", new SQLException("Type de colonne non valide", "99999")),
            asFailColumn("Runtime", null),
        });
        // a large object whose second chunk waits for releaseLob(), to cancel the reader
        typedResultSets.put("lobcancel", new Column[] {
            new Column("Blocking", Types.BLOB, "blob", 0, 0, ResultSetMetaData.columnNullable, "java.sql.Blob",
                (Supplier<Object>) () -> lob("Blocking", new byte[140000], null, null, true)),
        });
        // Oracle's TIMESTAMP WITH TIME ZONE with a driver without getObject(int, OffsetDateTime.class): the string
        // of the value, with an offset or with a time zone region
        typedResultSets.put("oldtstz", new Column[] {
            new Column("Tstz", -101, "TIMESTAMP WITH TIME ZONE", 35, 6, ResultSetMetaData.columnNullable,
                "oracle.sql.TIMESTAMPTZ", "unexpected getObject()", true, "2026-03-04 05:06:07.123456 +2:00",
                null),
            new Column("TstzNegative", -101, "TIMESTAMP WITH TIME ZONE", 35, 6, ResultSetMetaData.columnNullable,
                "oracle.sql.TIMESTAMPTZ", "unexpected getObject()", true, "2026-03-04 05:06:07 -5:30", null),
            new Column("TstzRegion", -101, "TIMESTAMP WITH TIME ZONE", 35, 6, ResultSetMetaData.columnNullable,
                "oracle.sql.TIMESTAMPTZ", "unexpected getObject()", true,
                "2026-03-04 05:06:07.123456 Europe/Prague",
                Timestamp.from(Instant.parse("2026-03-04T04:06:07.123456Z"))),
            // Oracle's TIMESTAMP with a driver without getObject(int, LocalDateTime.class): getObject() gives
            // Oracle's own class, getTimestamp() a java.sql.Timestamp
            new Column("Ts", Types.TIMESTAMP, "TIMESTAMP", 29, 6, ResultSetMetaData.columnNullable,
                "oracle.sql.TIMESTAMP", "unexpected getObject()", true, null,
                Timestamp.valueOf("2026-07-04 12:00:00.123456")),
        });
        // duplicate column labels, in another case than the first
        typedResultSets.put("duplicates", new Column[] {
            new Column("Id", Types.INTEGER, "int4", 10, 0, ResultSetMetaData.columnNoNulls, "java.lang.Integer", 1),
            new Column("ID", Types.INTEGER, "int4", 10, 0, ResultSetMetaData.columnNoNulls, "java.lang.Integer", 2),
            new Column("id", Types.INTEGER, "int4", 10, 0, ResultSetMetaData.columnNoNulls, "java.lang.Integer", 3),
        });
    }

    /** A result set with the given columns and one row */
    private static ResultSet typedResultSet(Column[] columns) {
        ResultSetMetaData metadata = (ResultSetMetaData) Proxy.newProxyInstance(
            JdbcTransactionDriver.class.getClassLoader(), new Class<?>[] {ResultSetMetaData.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getColumnCount": return columns.length;
                    case "hashCode":
                    case "equals":
                    case "toString": return objectMethod(proxy, method, args, "test typed result set metadata");
                    default: break;
                }
                if (args == null || args.length != 1 || !(args[0] instanceof Integer)) {
                    throw new SQLFeatureNotSupportedException(method.getName());
                }
                Column col = columns[(Integer) args[0] - 1];
                switch (method.getName()) {
                    case "getColumnLabel":
                    case "getColumnName": return col.label;
                    case "getColumnType": return col.type;
                    case "getColumnTypeName": return col.typeName;
                    case "getPrecision": return col.precision;
                    case "getScale": return col.scale;
                    case "isNullable": return col.nullable;
                    case "getColumnClassName":
                        if (col.className == null) {
                            throw new SQLFeatureNotSupportedException("getColumnClassName");
                        }
                        return col.className;
                    default: throw new SQLFeatureNotSupportedException(method.getName());
                }
            });
        // 0 = before the row; 1 = on the row; 2 = after the row
        int[] row = {0};
        boolean[] closed = {false};
        return (ResultSet) Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
            new Class<?>[] {ResultSet.class}, (proxy, method, args) -> {
                synchronized (JdbcTransactionDriver.class) {
                    switch (method.getName()) {
                        case "next":
                            if (closed[0]) {
                                throw new SQLException("result set is closed");
                            }
                            if (row[0] < 2) {
                                ++row[0];
                            }
                            return row[0] == 1;
                        case "getObject": {
                            if (closed[0] || row[0] != 1) {
                                throw new SQLException("no current row");
                            }
                            Column col = columns[(Integer) args[0] - 1];
                            if (args.length == 2 && col.getObjectAsError != null) {
                                throw col.getObjectAsError;
                            }
                            if (args.length == 2 && col.getObjectAsRuntimeError) {
                                throw new IllegalArgumentException("unsupported class: " + args[1]);
                            }
                            if (args.length == 2 && col.noGetObjectAs) {
                                throw new SQLFeatureNotSupportedException("getObject(int, Class)");
                            }
                            return col.value instanceof Supplier ? ((Supplier<?>) col.value).get() : col.value;
                        }
                        case "getString":
                            if (closed[0] || row[0] != 1) {
                                throw new SQLException("no current row");
                            }
                            return columns[(Integer) args[0] - 1].stringValue;
                        case "getTimestamp":
                            if (closed[0] || row[0] != 1) {
                                throw new SQLException("no current row");
                            }
                            return columns[(Integer) args[0] - 1].timestampValue;
                        case "wasNull": return false;
                        case "getMetaData": return metadata;
                        case "close": closed[0] = true; return null;
                        case "isClosed": return closed[0];
                        case "hashCode":
                        case "equals":
                        case "toString": return objectMethod(proxy, method, args, "test typed result set");
                        default: throw new SQLFeatureNotSupportedException(method.getName());
                    }
                }
            });
    }

    /** A statement with scripted results: "multi:" followed by results separated by ";":
        - {@code count=N}: an update count
        - {@code rs=COL:V,V}: a result set with an INTEGER column (no values: an empty result set)
        - {@code fail=MSG}: executing, or moving to this result, raises an SQLException
        - {@code rowsfail=COL:MSG}: a result set whose rows raise an SQLException when read
        - {@code typed=NAME}: a typed result set with one row (see typedResultSets)
        Moving past the last result records the event "results-read:<sql>".  Like the SQL Server driver,
        executeQuery() and executeUpdate() look only at the first result. */
    private static final class ScriptedStatement implements InvocationHandler {
        private String sql;
        private String[] results;
        private int index = -1;
        private ResultSet current;

        // sql is null for a Statement, which receives its SQL when executed
        ScriptedStatement(String sql) {
            if (sql != null) {
                load(sql);
            }
        }

        private void load(String sql) throws IllegalArgumentException {
            if (!sql.startsWith("multi:")) {
                throw new IllegalArgumentException("unsupported SQL: " + sql);
            }
            this.sql = sql;
            results = sql.substring("multi:".length()).split(";");
            index = -1;
        }

        private int updateCount() {
            return index >= 0 && index < results.length && results[index].startsWith("count=")
                ? Integer.parseInt(results[index].substring(6)) : -1;
        }

        // moves to the next result; returns true if it is a result set
        private boolean advance() throws SQLException {
            if (current != null) {
                current.close();
                current = null;
            }
            if (index >= results.length) {
                return false;
            }
            if (++index == results.length) {
                event("results-read:" + sql);
                return false;
            }
            String result = results[index];
            if (result.startsWith("fail=")) {
                // an error ends the results
                index = results.length;
                throw new SQLException(result.substring(5));
            }
            if (result.startsWith("typed=")) {
                Column[] columns = typedResultSets.get(result.substring(6));
                if (columns == null) {
                    throw new SQLException("unknown typed result set: " + result.substring(6));
                }
                current = typedResultSet(columns);
                return true;
            }
            if (result.startsWith("rs=") || result.startsWith("rowsfail=")) {
                boolean fail = result.startsWith("rowsfail=");
                String spec = result.substring(fail ? 9 : 3);
                int colon = spec.indexOf(':');
                String rest = spec.substring(colon + 1);
                int[] values = fail || rest.isEmpty() ? new int[0]
                    : java.util.Arrays.stream(rest.split(",")).mapToInt(Integer::parseInt).toArray();
                current = resultSet(spec.substring(0, colon), values, fail ? rest : null);
                return true;
            }
            return false;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            synchronized (JdbcTransactionDriver.class) {
                String name = method.getName();
                if (name.startsWith("execute") && args != null && args.length == 1 && args[0] instanceof String) {
                    if (current != null) {
                        current.close();
                        current = null;
                    }
                    load((String) args[0]);
                }
                switch (name) {
                    case "execute":
                        event("execute:" + sql);
                        index = -1;
                        return advance();
                    case "executeQuery":
                        event("executeQuery:" + sql);
                        index = -1;
                        if (!advance()) {
                            throw new SQLException("the statement did not return a result set");
                        }
                        return current;
                    case "executeUpdate": {
                        event("executeUpdate:" + sql);
                        index = -1;
                        if (advance()) {
                            throw new SQLException("a result set was returned for an update");
                        }
                        int count = updateCount();
                        return count < 0 ? 0 : count;
                    }
                    case "getMoreResults": return advance();
                    case "getResultSet": return current;
                    case "getUpdateCount": return updateCount();
                    case "setString":
                    case "setInt":
                    case "setLong":
                    case "setNull": return null;
                    case "close":
                        if (current != null) {
                            current.close();
                            current = null;
                        }
                        event("statement.close");
                        return null;
                    case "hashCode":
                    case "equals":
                    case "toString": return objectMethod(proxy, method, args, "test statement " + sql);
                    default: throw new SQLFeatureNotSupportedException(method.getName());
                }
            }
        }
    }

    /** A plain Statement: "multi:" SQL has scripted results (see ScriptedStatement); any other SQL is executed as a
        prepared statement of the session is (with its events, injected connection losses and transaction effects) */
    private static final class PlainStatement implements InvocationHandler {
        private final Session session;
        private final ScriptedStatement scripted = new ScriptedStatement(null);
        // the prepared statement executing the last SQL that is not "multi:" SQL
        private PreparedStatement prepared;

        PlainStatement(Session session) {
            this.session = session;
        }

        private void closePrepared() throws SQLException {
            if (prepared != null) {
                PreparedStatement closing = prepared;
                prepared = null;
                closing.close();
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            synchronized (JdbcTransactionDriver.class) {
                String name = method.getName();
                if (name.startsWith("execute") && args != null && args.length == 1 && args[0] instanceof String) {
                    String sql = (String) args[0];
                    if (!sql.startsWith("multi:")) {
                        if (!name.equals("execute")) {
                            throw new SQLFeatureNotSupportedException(name + " of SQL that is not \"multi:\" SQL");
                        }
                        closePrepared();
                        // recorded, so that tests can tell SQL executed by a plain Statement from prepared SQL
                        event("plain:" + sql);
                        prepared = session.prepare(sql);
                        return prepared.execute();
                    }
                    closePrepared();
                }
                if (prepared != null) {
                    switch (name) {
                        case "getUpdateCount": return prepared.getUpdateCount();
                        case "getMoreResults": return prepared.getMoreResults();
                        case "getResultSet": return null;
                        case "close": closePrepared(); return null;
                        default: break;
                    }
                }
                return scripted.invoke(proxy, method, args);
            }
        }
    }

    private static final class Session {
        boolean autoCommit = true;
        boolean closed;
        int pending;

        PreparedStatement prepare(String sql) {
            if (sql.startsWith("multi:")) {
                return (PreparedStatement) Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, new ScriptedStatement(sql));
            }
            // Accessed only by the synchronized proxy callbacks below.
            int[] batchSize = {0};
            // the update count is only available after a successful execution
            int[] updateCount = {-1};
            return (PreparedStatement) Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    synchronized (JdbcTransactionDriver.class) {
                        // Statement.execute(String) and the like raise an SQLException on a PreparedStatement
                        if (method.getName().startsWith("execute") && args != null && args.length > 0) {
                            throw new SQLException(method.getName() + "(String) called on a PreparedStatement");
                        }
                        switch (method.getName()) {
                            case "execute": {
                                String operation = "execute:" + sql + ":" + autoCommit;
                                Loss loss = takeLoss(operation);
                                boolean lose = loss != null;
                                event(operation);
                                if (sql.equals("write") && (!lose || loss.applied)) {
                                    if (autoCommit) {
                                        ++durable;
                                    } else {
                                        ++pending;
                                    }
                                }
                                if (lose) {
                                    // the server rolls back the open transaction of a lost session
                                    pending = 0;
                                    closed = true;
                                    throw new SQLException("injected connection loss", "08006");
                                }
                                updateCount[0] = 1;
                                return false;
                            }
                            case "getUpdateCount": return updateCount[0];
                            // a single result: there are no more
                            case "getMoreResults": updateCount[0] = -1; return false;
                            case "setByte":
                            case "setShort":
                            case "setInt":
                            case "setLong":
                            case "setNull": return null;
                            case "clearBatch": batchSize[0] = 0; return null;
                            case "addBatch": ++batchSize[0]; return null;
                            case "executeBatch":
                                int[] counts = new int[batchSize[0]];
                                batchSize[0] = 0;
                                for (int i = 0; i < counts.length; ++i) {
                                    counts[i] = sql.startsWith("unknown") ? java.sql.Statement.SUCCESS_NO_INFO
                                        : sql.startsWith("overflow") ? Integer.MAX_VALUE
                                        : sql.startsWith("invalid") ? java.sql.Statement.EXECUTE_FAILED : i + 1;
                                }
                                return counts;
                            case "close": event("statement.close"); return null;
                            default: throw new SQLFeatureNotSupportedException(method.getName());
                        }
                    }
                });
        }

        Connection connect() {
            return (Connection) Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    synchronized (JdbcTransactionDriver.class) {
                        switch (method.getName()) {
                            case "setAutoCommit":
                                boolean value = (Boolean) args[0];
                                String operation = "auto:" + value;
                                boolean lose = takeLoss(operation) != null;
                                event(operation);
                                if (lose) {
                                    // the request finds the session gone; the server rolled back its transaction
                                    pending = 0;
                                    closed = true;
                                    throw new SQLException("injected connection loss", "08006");
                                }
                                if (value && !autoCommit) {
                                    durable += pending;
                                    pending = 0;
                                }
                                autoCommit = value;
                                return null;
                            case "getAutoCommit": return autoCommit;
                            case "prepareStatement": return prepare((String) args[0]);
                            case "createStatement":
                                return Proxy.newProxyInstance(JdbcTransactionDriver.class.getClassLoader(),
                                    new Class<?>[] {Statement.class}, new PlainStatement(this));
                            case "commit":
                                event("commit");
                                if (autoCommit) {
                                    throw new SQLException("commit in auto-commit mode");
                                }
                                durable += pending;
                                pending = 0;
                                return null;
                            case "rollback":
                                event("rollback");
                                if (autoCommit) {
                                    throw new SQLException("rollback in auto-commit mode");
                                }
                                pending = 0;
                                return null;
                            case "close":
                                pending = 0;
                                closed = true;
                                event("close");
                                return null;
                            case "isClosed": return closed;
                            case "isValid":
                                // not recorded as an event, so that the validity checks of the client do not change
                                // the event sequence; it fails only when scripted
                                if (failure.equals("isValid")) {
                                    failure = "";
                                    throw new SQLException("injected isValid");
                                }
                                return !closed;
                            case "getMetaData": return null;
                            default: throw new SQLFeatureNotSupportedException(method.getName());
                        }
                    }
                });
        }
    }

    @Override
    public synchronized Connection connect(String url, Properties info) throws SQLException {
        synchronized (JdbcTransactionDriver.class) {
            if (!acceptsURL(url)) {
                return null;
            }
            event("connect");
            return new Session().connect();
        }
    }

    @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:qore-transaction-test:"); }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }
    @Override public int getMajorVersion() { return 1; }
    @Override public int getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant() { return false; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
