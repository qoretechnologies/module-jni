/*  AccessIterator.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.dataprovider.access;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Column;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Microsoft Access database files (.mdb/.accdb format).
 * Uses Jackcess library to read table data.
 * Implements Closeable to ensure proper resource cleanup.
 *
 * Note: Jackcess requires a File (not InputStream). When constructing from a
 * stream, the data is written to a temporary file which is cleaned up on close.
 */
public class AccessIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private Database database;
    private Table table;
    private Iterator<Row> rowIterator;
    private Hash currentRecord = null;
    private long count = 0;
    private ArrayList<String> columnNames;
    private Path tempFile = null;

    /**
     * Creates an AccessIterator from a file path and table name.
     *
     * @param path The path to the Access database file
     * @param tableName The name of the table to iterate
     */
    public AccessIterator(String path, String tableName) throws Throwable {
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("No such file or directory: " + path);
        }
        this.database = DatabaseBuilder.open(file);
        try {
            initTable(tableName);
        } catch (Throwable t) {
            database.close();
            throw t;
        }
    }

    /**
     * Creates an AccessIterator from a File and table name.
     *
     * @param file The Access database file
     * @param tableName The name of the table to iterate
     */
    public AccessIterator(File file, String tableName) throws Throwable {
        if (!file.exists()) {
            throw new FileNotFoundException("No such file or directory: " + file.getPath());
        }
        this.database = DatabaseBuilder.open(file);
        try {
            initTable(tableName);
        } catch (Throwable t) {
            database.close();
            throw t;
        }
    }

    /**
     * Creates an AccessIterator from an InputStream and table name.
     * The stream data is written to a temporary file since Jackcess requires File access.
     *
     * @param stream The input stream containing Access database data
     * @param tableName The name of the table to iterate
     */
    public AccessIterator(InputStream stream, String tableName) throws Throwable {
        try {
            tempFile = Files.createTempFile("qore-access-", ".accdb");
            Files.copy(stream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            this.database = DatabaseBuilder.open(tempFile.toFile());
        } catch (Throwable t) {
            stream.close();
            cleanupTempFile();
            throw t;
        } finally {
            stream.close();
        }
        try {
            initTable(tableName);
        } catch (Throwable t) {
            database.close();
            cleanupTempFile();
            throw t;
        }
    }

    private void initTable(String tableName) throws IOException {
        this.table = database.getTable(tableName);
        if (this.table == null) {
            throw new IOException("Table '" + tableName + "' not found in database; available tables: "
                + getTableNamesFromDb(database));
        }
        this.rowIterator = table.iterator();
        this.columnNames = new ArrayList<String>();
        for (Column col : table.getColumns()) {
            this.columnNames.add(col.getName());
        }
    }

    private static String getTableNamesFromDb(Database db) throws IOException {
        Set<String> names = db.getTableNames();
        return names.toString();
    }

    private void cleanupTempFile() {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                // ignore cleanup errors
            }
            tempFile = null;
        }
    }

    /**
     * Returns the column names for the current table.
     *
     * @return The list of column names
     */
    public ArrayList<String> getColumnNames() {
        return columnNames;
    }

    /**
     * Returns the number of records read.
     */
    public long getCount() {
        return count;
    }

    /**
     * Advances to the next record.
     *
     * @return True if there is another record, false otherwise
     */
    public boolean next() {
        if (rowIterator != null && rowIterator.hasNext()) {
            Row row = rowIterator.next();
            currentRecord = new Hash();
            for (String colName : columnNames) {
                Object value = row.get(colName);
                currentRecord.put(colName, convertValue(value));
            }
            ++count;
            return true;
        }
        currentRecord = null;
        return false;
    }

    /**
     * Converts a Jackcess value to an appropriate type for Qore.
     *
     * @param value The value to convert
     * @return The converted value
     */
    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return ZonedDateTime.of((LocalDateTime) value, ZoneId.systemDefault());
        }
        if (value instanceof java.util.Date) {
            java.util.Date d = (java.util.Date) value;
            return ZonedDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                return ((Number) value).longValue();
            }
            if (value instanceof Long) {
                return value;
            }
            if (value instanceof Float) {
                return ((Float) value).doubleValue();
            }
            if (value instanceof Double) {
                return value;
            }
            // BigDecimal and other Number types
            return ((Number) value).doubleValue();
        }
        if (value instanceof byte[]) {
            return value;
        }
        return value.toString();
    }

    /**
     * Returns the current record.
     *
     * @return The current record as a Hash
     * @throws QoreException if the iterator is not valid
     */
    public Hash getValue() throws QoreException {
        if (currentRecord == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return currentRecord;
    }

    /**
     * Checks if the iterator is in a valid state.
     *
     * @return True if the iterator is valid
     */
    public boolean valid() {
        return currentRecord != null;
    }

    /**
     * Returns a list of table names in the database.
     *
     * @param path The path to the Access database file
     * @return The list of table names
     */
    public static ArrayList<String> getTableNames(String path) throws IOException {
        try (Database db = DatabaseBuilder.open(new File(path))) {
            return new ArrayList<String>(db.getTableNames());
        }
    }

    /**
     * Returns a list of table names in the database.
     *
     * @param file The Access database file
     * @return The list of table names
     */
    public static ArrayList<String> getTableNames(File file) throws IOException {
        try (Database db = DatabaseBuilder.open(file)) {
            return new ArrayList<String>(db.getTableNames());
        }
    }

    /**
     * Returns a list of table names in the database from a stream.
     * Writes to a temp file since Jackcess requires File access.
     *
     * @param stream The input stream containing Access database data
     * @return The list of table names
     */
    public static ArrayList<String> getTableNames(InputStream stream) throws IOException {
        Path tmpFile = Files.createTempFile("qore-access-names-", ".accdb");
        try {
            Files.copy(stream, tmpFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (Database db = DatabaseBuilder.open(tmpFile.toFile())) {
                return new ArrayList<String>(db.getTableNames());
            }
        } finally {
            stream.close();
            Files.deleteIfExists(tmpFile);
        }
    }

    /**
     * Returns a list of table names in the database from a Qore stream.
     *
     * @param stream The Qore input stream containing Access database data
     * @return The list of table names
     */
    public static ArrayList<String> getTableNames(qore.Qore.InputStream stream) throws IOException {
        return getTableNames(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Returns the number of rows in a table.
     *
     * @param path The path to the Access database file
     * @param tableName The name of the table
     * @return The number of rows
     */
    public static int getRowCount(String path, String tableName) throws IOException {
        try (Database db = DatabaseBuilder.open(new File(path))) {
            Table t = db.getTable(tableName);
            if (t == null) {
                throw new IOException("Table '" + tableName + "' not found");
            }
            return t.getRowCount();
        }
    }

    /**
     * Closes the database and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (database != null) {
            database.close();
            database = null;
        }
        cleanupTempFile();
    }
}
