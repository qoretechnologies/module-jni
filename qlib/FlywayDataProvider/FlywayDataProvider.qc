# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the FlywayDataProvider module
public namespace FlywayDataProvider {
#! Data provider for Flyway database schema migration
/**
*/
public class FlywayDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "flyway",
            "desc": "Parent data provider for Flyway database schema migration",
            "supports_children": True,
            "children_can_support_apis": True,
            "children_can_support_records": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "url": <DataProviderOptionInfo>{
                "display_name": "JDBC URL",
                "short_desc": "The JDBC URL for the database connection",
                "desc": "The JDBC connection URL for the database (e.g. "
                    "`jdbc:postgresql://localhost:5432/mydb`)",
                "type": AbstractDataProviderTypeMap."string",
                "required": True,
            },
            "user": <DataProviderOptionInfo>{
                "display_name": "Username",
                "short_desc": "The database username",
                "desc": "The username for the database connection",
                "type": AbstractDataProviderTypeMap."string",
                "required": True,
            },
            "password": <DataProviderOptionInfo>{
                "display_name": "Password",
                "short_desc": "The database password",
                "desc": "The password for the database connection",
                "type": AbstractDataProviderTypeMap."string",
                "required": True,
                "sensitive": True,
            },
            "locations": <DataProviderOptionInfo>{
                "display_name": "Migration Locations",
                "short_desc": "Filesystem paths to SQL migration directories",
                "desc": "A list of filesystem paths to directories containing SQL migration "
                    "files.\n\nPaths are automatically prefixed with `filesystem:` if no prefix is "
                    "given.\n\nExample: `(\"/path/to/sql/migrations\",)`",
                "type": AbstractDataProviderTypeMap."list",
            },
            "schemas": <DataProviderOptionInfo>{
                "display_name": "Schemas",
                "short_desc": "Database schemas to manage",
                "desc": "An optional list of database schema names to manage with Flyway",
                "type": AbstractDataProviderTypeMap."list",
            },
            "table": <DataProviderOptionInfo>{
                "display_name": "History Table",
                "short_desc": "The name of the Flyway schema history table",
                "desc": "The name of the Flyway schema history table; default is "
                    "`flyway_schema_history`",
                "type": AbstractDataProviderTypeMap."string",
            },
        };

        #! Child data provider names
        const ChildList = ("migrate", "info", "validate", "clean", "repair");
    }

    private {
        #! The Flyway runner object
        QoreFlywayRunner runner;

        #! The name of the provider
        string name = "flyway";
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("FLYWAY-CONSTRUCTOR-ERROR", ConstructorOptions, options);
        if (!copts.url) {
            error("FLYWAY-CONSTRUCTOR-ERROR", "the \"url\" option is required");
        }
        if (!copts.user) {
            error("FLYWAY-CONSTRUCTOR-ERROR", "the \"user\" option is required");
        }
        if (!copts.password) {
            error("FLYWAY-CONSTRUCTOR-ERROR", "the \"password\" option is required");
        }
        # prepare locations: prefix with "filesystem:" if no prefix is present
        list<string> locations = map ($1 =~ /:/ ? $1 : "filesystem:" + $1), copts.locations ?? ();
        runner = new QoreFlywayRunner(copts.url, copts.user, copts.password, locations);
    }

    #! Creates the object from a connection object
    constructor(FlywayConnection conn, *hash<auto> options) {
        hash<auto> copts = conn.getFlywayOptions();
        if (options) {
            copts += options;
        }
        list<string> locations = map ($1 =~ /:/ ? $1 : "filesystem:" + $1), copts.locations ?? ();
        runner = new QoreFlywayRunner(copts.url, copts.user, copts.password, locations);
    }

    string getName() {
        return name;
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        return ChildList;
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown

        @see getChildProviderEx()
    */
    private *DataProvider::AbstractDataProvider getChildProviderImpl(string name) {
        switch (name) {
            case "migrate":
                return new FlywayMigrateDataProvider(runner);
            case "info":
                return new FlywayInfoDataProvider(runner);
            case "validate":
                return new FlywayValidateDataProvider(runner);
            case "clean":
                return new FlywayCleanDataProvider(runner);
            case "repair":
                return new FlywayRepairDataProvider(runner);
        }
    }
}
}
