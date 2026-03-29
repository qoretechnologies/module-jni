/*  AccessTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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

import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.Database.FileFormat;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Helper class for creating test Access database files for the AccessDataProvider tests.
 */
public class AccessTestHelper {

    /**
     * Creates a simple Access database with two tables: Users and Products.
     *
     * @param path The path to create the database at
     */
    public static void createSimpleAccdb(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try (Database db = DatabaseBuilder.create(FileFormat.V2010, file)) {
            createUsersTable(db);
            createProductsTable(db);
        }
    }

    /**
     * Creates a simple Access database and returns the bytes.
     *
     * @return The database file as a byte array
     */
    public static byte[] createSimpleAccdbBytes() throws IOException {
        Path tmpFile = Files.createTempFile("qore-access-test-", ".accdb");
        try {
            createSimpleAccdb(tmpFile.toString());
            return Files.readAllBytes(tmpFile);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /**
     * Creates an Access database with a single empty table.
     *
     * @param path The path to create the database at
     */
    public static void createEmptyTableAccdb(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try (Database db = DatabaseBuilder.create(FileFormat.V2010, file)) {
            new TableBuilder("EmptyTable")
                .addColumn(new ColumnBuilder("id", DataType.LONG))
                .addColumn(new ColumnBuilder("name", DataType.TEXT))
                .toTable(db);
        }
    }

    /**
     * Creates an Access database with various data types.
     *
     * @param path The path to create the database at
     */
    public static void createDataTypesAccdb(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try (Database db = DatabaseBuilder.create(FileFormat.V2010, file)) {
            Table table = new TableBuilder("DataTypes")
                .addColumn(new ColumnBuilder("int_col", DataType.LONG))
                .addColumn(new ColumnBuilder("text_col", DataType.TEXT))
                .addColumn(new ColumnBuilder("bool_col", DataType.BOOLEAN))
                .addColumn(new ColumnBuilder("double_col", DataType.DOUBLE))
                .addColumn(new ColumnBuilder("datetime_col", DataType.SHORT_DATE_TIME))
                .toTable(db);

            table.addRow(1, "hello", true, 3.14, LocalDateTime.of(2025, 6, 15, 10, 30, 0));
            table.addRow(2, "world", false, 2.71, LocalDateTime.of(2024, 12, 25, 0, 0, 0));
            table.addRow(3, null, true, 0.0, null);
        }
    }

    /**
     * Creates an Access database with special characters in text fields.
     *
     * @param path The path to create the database at
     */
    public static void createSpecialCharsAccdb(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try (Database db = DatabaseBuilder.create(FileFormat.V2010, file)) {
            Table table = new TableBuilder("SpecialChars")
                .addColumn(new ColumnBuilder("id", DataType.LONG))
                .addColumn(new ColumnBuilder("text_col", DataType.TEXT))
                .toTable(db);

            table.addRow(1, "Caf\u00e9");
            table.addRow(2, "\u6771\u4eac");
            table.addRow(3, "He said \"hello\"");
            table.addRow(4, "Currency: \u20ac \u00a3 \u00a5 $");
        }
    }

    /**
     * Creates an Access database with multiple tables for testing child providers.
     *
     * @param path The path to create the database at
     */
    public static void createMultiTableAccdb(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try (Database db = DatabaseBuilder.create(FileFormat.V2010, file)) {
            // Employees table
            Table employees = new TableBuilder("Employees")
                .addColumn(new ColumnBuilder("id", DataType.LONG))
                .addColumn(new ColumnBuilder("name", DataType.TEXT))
                .addColumn(new ColumnBuilder("department", DataType.TEXT))
                .toTable(db);

            employees.addRow(1, "Alice Smith", "Engineering");
            employees.addRow(2, "Bob Johnson", "Marketing");
            employees.addRow(3, "Carol Williams", "Finance");

            // Departments table
            Table departments = new TableBuilder("Departments")
                .addColumn(new ColumnBuilder("id", DataType.LONG))
                .addColumn(new ColumnBuilder("name", DataType.TEXT))
                .addColumn(new ColumnBuilder("budget", DataType.DOUBLE))
                .toTable(db);

            departments.addRow(1, "Engineering", 500000.0);
            departments.addRow(2, "Marketing", 300000.0);
            departments.addRow(3, "Finance", 200000.0);

            // Projects table
            Table projects = new TableBuilder("Projects")
                .addColumn(new ColumnBuilder("id", DataType.LONG))
                .addColumn(new ColumnBuilder("title", DataType.TEXT))
                .addColumn(new ColumnBuilder("lead_id", DataType.LONG))
                .toTable(db);

            projects.addRow(1, "Project Alpha", 1);
            projects.addRow(2, "Project Beta", 2);
        }
    }

    private static void createUsersTable(Database db) throws IOException {
        Table table = new TableBuilder("Users")
            .addColumn(new ColumnBuilder("id", DataType.LONG))
            .addColumn(new ColumnBuilder("name", DataType.TEXT))
            .addColumn(new ColumnBuilder("email", DataType.TEXT))
            .toTable(db);

        table.addRow(1, "Alice Smith", "alice@example.com");
        table.addRow(2, "Bob Johnson", "bob@example.com");
        table.addRow(3, "Carol Williams", "carol@example.com");
    }

    private static void createProductsTable(Database db) throws IOException {
        Table table = new TableBuilder("Products")
            .addColumn(new ColumnBuilder("id", DataType.LONG))
            .addColumn(new ColumnBuilder("name", DataType.TEXT))
            .addColumn(new ColumnBuilder("price", DataType.DOUBLE))
            .toTable(db);

        table.addRow(1, "Widget", 9.99);
        table.addRow(2, "Gadget", 19.99);
        table.addRow(3, "Gizmo", 29.99);
    }
}
