/*
    JdbcLob.java

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Reads the value of a large object of a JDBC result set, which the jdbc driver returns as a Qore value

    The value is read in chunks of Chunk bytes or characters, and the large object is freed afterwards
*/
public final class JdbcLob {
    //! The number of bytes or characters read at a time
    public static final int Chunk = 65536;

    private JdbcLob() {
    }

    /** Returns the bytes of a BLOB and frees it
        @throws SQLException the BLOB cannot be read or freed, or is too large for a Java array
    */
    public static byte[] readBlob(Blob blob) throws SQLException {
        byte[] rv = null;
        SQLException error = null;
        try {
            long len = blob.length();
            if (len > Integer.MAX_VALUE - 8) {
                throw new SQLException("BLOB of " + len + " bytes is too large to be read as a value");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream((int)len);
            try (InputStream in = blob.getBinaryStream()) {
                byte[] buf = new byte[Chunk];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            rv = out.toByteArray();
        } catch (IOException e) {
            error = new SQLException("cannot read BLOB: " + e.getMessage(), e);
        } catch (SQLException e) {
            error = e;
        }
        error = free(blob, error);
        if (error != null) {
            throw error;
        }
        return rv;
    }

    /** Returns the characters of a CLOB or NCLOB and frees it
        @throws SQLException the CLOB cannot be read or freed, or is too large for a Java string
    */
    public static String readClob(Clob clob) throws SQLException {
        String rv = null;
        SQLException error = null;
        try {
            long len = clob.length();
            if (len > Integer.MAX_VALUE - 8) {
                throw new SQLException("CLOB of " + len + " characters is too large to be read as a value");
            }
            StringBuilder out = new StringBuilder((int)len);
            // a Reader keeps a character outside the Basic Multilingual Plane (a surrogate pair) together
            try (Reader in = clob.getCharacterStream()) {
                char[] buf = new char[Chunk];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.append(buf, 0, n);
                }
            }
            rv = out.toString();
        } catch (IOException e) {
            error = new SQLException("cannot read CLOB: " + e.getMessage(), e);
        } catch (SQLException e) {
            error = e;
        }
        error = free(clob, error);
        if (error != null) {
            throw error;
        }
        return rv;
    }

    // frees a large object; returns the error to raise: the error of reading it, else the error of freeing it
    private static SQLException free(Object lob, SQLException error) {
        try {
            if (lob instanceof Blob) {
                ((Blob)lob).free();
            } else {
                ((Clob)lob).free();
            }
        } catch (SQLFeatureNotSupportedException e) {
            // a driver that does not free large objects releases them with the result set
        } catch (SQLException e) {
            if (error == null) {
                return e;
            }
            error.addSuppressed(e);
        }
        return error;
    }
}
