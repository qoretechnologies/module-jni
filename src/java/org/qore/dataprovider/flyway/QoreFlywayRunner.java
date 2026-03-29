/*  QoreFlywayRunner.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateOutput;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;

import org.qore.jni.Hash;

/**
 * Wraps the Flyway database migration library to provide migration operations.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class QoreFlywayRunner implements Closeable {
    private Flyway flyway;

    /**
     * Creates a new QoreFlywayRunner.
     *
     * @param jdbcUrl   The JDBC connection URL
     * @param user      The database username
     * @param password  The database password
     * @param locations The migration script locations (e.g., "filesystem:/path/to/sql")
     */
    public QoreFlywayRunner(String jdbcUrl, String user, String password, String[] locations) {
        flyway = Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations(locations)
            .cleanDisabled(false)
            .load();
    }

    /**
     * Runs all pending migrations.
     *
     * @return a list of Hash entries with version, description, type, filepath, executionTime
     */
    public ArrayList<Hash> migrate() {
        MigrateResult result = flyway.migrate();
        ArrayList<Hash> list = new ArrayList<>();
        if (result.migrations != null) {
            for (MigrateOutput output : result.migrations) {
                Hash h = new Hash();
                h.put("version", output.version);
                h.put("description", output.description);
                h.put("type", output.type);
                h.put("filepath", output.filepath);
                h.put("executionTime", output.executionTime);
                list.add(h);
            }
        }
        return list;
    }

    /**
     * Returns information about all migrations.
     *
     * @return a list of Hash entries with version, description, type, state, installedOn, executionTime
     */
    public ArrayList<Hash> info() {
        MigrationInfoService infoService = flyway.info();
        MigrationInfo[] all = infoService.all();
        ArrayList<Hash> list = new ArrayList<>();
        for (MigrationInfo mi : all) {
            Hash h = new Hash();
            h.put("version", mi.getVersion() != null ? mi.getVersion().toString() : null);
            h.put("description", mi.getDescription());
            h.put("type", mi.getType() != null ? mi.getType().toString() : null);
            h.put("state", mi.getState() != null ? mi.getState().toString() : null);
            h.put("installedOn", mi.getInstalledOn() != null ? mi.getInstalledOn().toString() : null);
            h.put("executionTime", mi.getExecutionTime());
            list.add(h);
        }
        return list;
    }

    /**
     * Validates applied migrations against available ones.
     *
     * @return a Hash with validationSuccessful, invalidMigrations, errorDetails
     */
    public Hash validate() {
        ValidateResult result = flyway.validateWithResult();
        Hash h = new Hash();
        h.put("validationSuccessful", result.validationSuccessful);
        ArrayList<Hash> invalidList = new ArrayList<>();
        if (result.invalidMigrations != null) {
            for (ValidateOutput invalid : result.invalidMigrations) {
                Hash entry = new Hash();
                entry.put("version", invalid.version);
                entry.put("description", invalid.description);
                entry.put("filepath", invalid.filepath);
                entry.put("errorDetails", invalid.errorDetails != null
                    ? invalid.errorDetails.errorMessage : null);
                invalidList.add(entry);
            }
        }
        h.put("invalidMigrations", invalidList);
        h.put("errorDetails", result.errorDetails != null ? result.errorDetails.errorMessage : null);
        return h;
    }

    /**
     * Drops all objects in the configured schemas.
     * This is a destructive operation and should be used with caution.
     *
     * @return a Hash with schemasDropped
     */
    public Hash clean() {
        CleanResult result = flyway.clean();
        Hash h = new Hash();
        h.put("schemasDropped", result.schemasCleaned != null
            ? new ArrayList<>(result.schemasCleaned) : new ArrayList<>());
        return h;
    }

    /**
     * Repairs the schema history table.
     *
     * @return a Hash with repairActions
     */
    public Hash repair() {
        RepairResult result = flyway.repair();
        Hash h = new Hash();
        h.put("repairActions", result.repairActions != null
            ? new ArrayList<>(result.repairActions) : new ArrayList<>());
        return h;
    }

    /**
     * Closes the runner and releases resources.
     */
    @Override
    public void close() throws IOException {
        flyway = null;
    }
}
