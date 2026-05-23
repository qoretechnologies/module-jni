/*
    JdbcColumnarBatch.java

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

package org.qore.jni;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public final class JdbcColumnarBatch {
    public static final int MODE_BYTE = 1;
    public static final int MODE_SHORT = 2;
    public static final int MODE_INT = 3;
    public static final int MODE_LONG = 4;
    public static final int MODE_FLOAT = 5;
    public static final int MODE_DOUBLE = 6;
    public static final int MODE_BOOLEAN = 7;
    public static final int MODE_STRING = 8;
    public static final int MODE_DECIMAL_STRING = 9;

    public final int rows;
    public final Object[] data;
    public final byte[][] validity;
    public final long[] nullCounts;

    private JdbcColumnarBatch(int rows, Object[] data, byte[][] validity, long[] nullCounts) {
        this.rows = rows;
        this.data = data;
        this.validity = validity;
        this.nullCounts = nullCounts;
    }

    public static JdbcColumnarBatch read(ResultSet rs, int[] columns, int[] modes, boolean[] trim, int maxRows)
            throws SQLException {
        int capacity = maxRows > 0 ? maxRows : 1024;
        Object[] data = new Object[modes.length];
        byte[][] validity = new byte[modes.length][];
        long[] nullCounts = new long[modes.length];

        for (int i = 0; i < modes.length; ++i) {
            data[i] = createDataArray(modes[i], capacity);
            validity[i] = createValidity(capacity);
        }

        int rows = 0;
        while (maxRows <= 0 || rows < maxRows) {
            if (!rs.next()) {
                break;
            }
            if (rows == capacity) {
                int newCapacity = capacity * 2;
                if (maxRows > 0 && newCapacity > maxRows) {
                    newCapacity = maxRows;
                }
                grow(data, validity, modes, capacity, newCapacity);
                capacity = newCapacity;
            }

            for (int c = 0; c < modes.length; ++c) {
                boolean isNull = readValue(rs, columns[c], modes[c], data[c], rows, trim[c]);
                if (isNull) {
                    clearValid(validity[c], rows);
                    ++nullCounts[c];
                }
            }
            ++rows;
        }

        return new JdbcColumnarBatch(rows, data, validity, nullCounts);
    }

    private static Object createDataArray(int mode, int capacity) {
        switch (mode) {
            case MODE_BYTE:
                return new byte[capacity];
            case MODE_SHORT:
                return new short[capacity];
            case MODE_INT:
                return new int[capacity];
            case MODE_LONG:
                return new long[capacity];
            case MODE_FLOAT:
                return new float[capacity];
            case MODE_DOUBLE:
                return new double[capacity];
            case MODE_BOOLEAN:
                return new byte[bitmapBytes(capacity)];
            case MODE_STRING:
            case MODE_DECIMAL_STRING:
                return new String[capacity];
            default:
                throw new IllegalArgumentException("unsupported JDBC columnar batch mode: " + mode);
        }
    }

    private static byte[] createValidity(int capacity) {
        byte[] rv = new byte[bitmapBytes(capacity)];
        Arrays.fill(rv, (byte)0xff);
        return rv;
    }

    private static void grow(Object[] data, byte[][] validity, int[] modes, int oldCapacity, int newCapacity) {
        for (int i = 0; i < modes.length; ++i) {
            switch (modes[i]) {
                case MODE_BYTE:
                    data[i] = Arrays.copyOf((byte[])data[i], newCapacity);
                    break;
                case MODE_SHORT:
                    data[i] = Arrays.copyOf((short[])data[i], newCapacity);
                    break;
                case MODE_INT:
                    data[i] = Arrays.copyOf((int[])data[i], newCapacity);
                    break;
                case MODE_LONG:
                    data[i] = Arrays.copyOf((long[])data[i], newCapacity);
                    break;
                case MODE_FLOAT:
                    data[i] = Arrays.copyOf((float[])data[i], newCapacity);
                    break;
                case MODE_DOUBLE:
                    data[i] = Arrays.copyOf((double[])data[i], newCapacity);
                    break;
                case MODE_BOOLEAN:
                    data[i] = Arrays.copyOf((byte[])data[i], bitmapBytes(newCapacity));
                    break;
                case MODE_STRING:
                case MODE_DECIMAL_STRING:
                    data[i] = Arrays.copyOf((String[])data[i], newCapacity);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported JDBC columnar batch mode: " + modes[i]);
            }

            int oldBytes = bitmapBytes(oldCapacity);
            int newBytes = bitmapBytes(newCapacity);
            validity[i] = Arrays.copyOf(validity[i], newBytes);
            Arrays.fill(validity[i], oldBytes, newBytes, (byte)0xff);
        }
    }

    private static boolean readValue(ResultSet rs, int column, int mode, Object data, int row, boolean trim)
            throws SQLException {
        switch (mode) {
            case MODE_BYTE: {
                byte value = rs.getByte(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((byte[])data)[row] = value;
                return false;
            }
            case MODE_SHORT: {
                short value = rs.getShort(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((short[])data)[row] = value;
                return false;
            }
            case MODE_INT: {
                int value = rs.getInt(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((int[])data)[row] = value;
                return false;
            }
            case MODE_LONG: {
                long value = rs.getLong(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((long[])data)[row] = value;
                return false;
            }
            case MODE_FLOAT: {
                float value = rs.getFloat(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((float[])data)[row] = value;
                return false;
            }
            case MODE_DOUBLE: {
                double value = rs.getDouble(column);
                if (rs.wasNull()) {
                    return true;
                }
                ((double[])data)[row] = value;
                return false;
            }
            case MODE_BOOLEAN: {
                boolean value = rs.getBoolean(column);
                if (rs.wasNull()) {
                    return true;
                }
                if (value) {
                    setTrue((byte[])data, row);
                }
                return false;
            }
            case MODE_STRING: {
                String value = rs.getString(column);
                if (value == null) {
                    return true;
                }
                ((String[])data)[row] = trim ? trimTrailingSpaces(value) : value;
                return false;
            }
            case MODE_DECIMAL_STRING: {
                BigDecimal value = rs.getBigDecimal(column);
                if (value == null) {
                    return true;
                }
                ((String[])data)[row] = value.toString();
                return false;
            }
            default:
                throw new IllegalArgumentException("unsupported JDBC columnar batch mode: " + mode);
        }
    }

    private static int bitmapBytes(int elements) {
        return ((elements + 63) / 64) * 8;
    }

    private static void clearValid(byte[] bitmap, int row) {
        bitmap[row / 8] = (byte)(bitmap[row / 8] & ~(1 << (row % 8)));
    }

    private static void setTrue(byte[] bitmap, int row) {
        bitmap[row / 8] = (byte)(bitmap[row / 8] | (1 << (row % 8)));
    }

    private static String trimTrailingSpaces(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            --end;
        }
        return end == value.length() ? value : value.substring(0, end);
    }
}
