# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayConnection.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Class for Flyway connections
public class FlywayConnection inherits ConnectionProvider::AbstractConnectionWithInfo {
    public {
        #! Connection entry info
        const ConnectionScheme = <ConnectionSchemeInfo>{
            "display_name": "Flyway Connection",
            "short_desc": "A connection for Flyway database schema migration",
            "desc": "A connection for Flyway database schema migration providing JDBC URL, "
                "credentials, and migration locations",
            "cls": Class::forName("FlywayConnection"),
            "options": {
                "url": <ConnectionOptionInfo>{
                    "display_name": "JDBC URL",
                    "short_desc": "The JDBC URL for the database connection",
                    "type": "string",
                    "desc": "The JDBC connection URL for the database (e.g. "
                        "`jdbc:postgresql://localhost:5432/mydb`)",
                    "required": True,
                },
                "user": <ConnectionOptionInfo>{
                    "display_name": "Username",
                    "short_desc": "The database username",
                    "type": "string",
                    "desc": "The username for the database connection",
                    "required": True,
                },
                "password": <ConnectionOptionInfo>{
                    "display_name": "Password",
                    "short_desc": "The database password",
                    "type": "string",
                    "desc": "The password for the database connection",
                    "sensitive": True,
                    "required": True,
                },
                "locations": <ConnectionOptionInfo>{
                    "display_name": "Migration Locations",
                    "short_desc": "Filesystem paths to SQL migration directories",
                    "type": "list",
                    "desc": "A list of filesystem paths to directories containing SQL migration "
                        "files.\n\nPaths are automatically prefixed with `filesystem:` if no prefix "
                        "is given",
                },
                "schemas": <ConnectionOptionInfo>{
                    "display_name": "Schemas",
                    "short_desc": "Database schemas to manage",
                    "type": "list",
                    "desc": "An optional list of database schema names to manage with Flyway",
                },
                "table": <ConnectionOptionInfo>{
                    "display_name": "History Table",
                    "short_desc": "The name of the Flyway schema history table",
                    "type": "string",
                    "desc": "The name of the Flyway schema history table; default is "
                        "`flyway_schema_history`",
                },
            },
            "schemes": {
                "flyway": True,
            },
        };
    }

    #! Creates the object from the arguments
    constructor(string name, string desc, string url, hash<auto> attr = {}, hash<auto> options = {},
            *LoggerInterface logger) : AbstractConnectionWithInfo(name, desc, url, attr, options, logger) {
    }

    #! Creates the object from the arguments
    /** @param config with the following keys:
        - name (required string): the connection name
        - display_name (optional string): the display name
        - short_desc (optional string): a short description in plain text
        - desc (optional string): a long description with markdown formatting
        - url (required string): the connection URL
        - opts (optional hash): connection options
        - logger (optional LoggerInterface object): logger for the connection
        @param attr optional connection attributes
        - monitor (optional bool): should the connection be monitored? Default: True
        - enabled (optional bool): is the connection enabled? Default: True
        - locked (optional bool): is the connection locked? Default: False
        - debug_data (optional bool): debug data? Default: False
        - tags (optional hash): tags for the connection; no default value

        @throw CONNECTION-OPTION-ERROR missing or invalid connection option or attribute
    */
    constructor(hash<auto> config, *hash<auto> attr) : AbstractConnectionWithInfo(config, attr) {
    }

    #! Returns the string type name for the connection object
    string getType() {
        return "flyway";
    }

    #! Returns @ref True, as this connection class returns a data provider with the @ref getDataProvider() method
    /** @return @ref True, as this connection class returns a data provider with the @ref getDataProvider() method

        @see @ref getDataProvider()
    */
    bool hasDataProvider() {
        return True;
    }

    #! Returns the data provider application name this connection serves
    /** @return the data provider application name this connection serves
    */
    *string getAppName() {
        return "Flyway";
    }

    #! Returns a @ref FlywayDataProvider object for this connection
    /** @param constructor_options any additional constructor options for the data provider

        @return a data provider object for this connection, if supported
    */
    AbstractDataProvider getDataProvider(*hash<auto> constructor_options) {
        return new FlywayDataProvider(self, constructor_options);
    }

    #! Returns options that can be used to create Flyway data providers
    hash<auto> getFlywayOptions() {
        return self.opts + {
            "url": real_url ?? url,
        };
    }

    #! Returns the ConnectionSchemeInfo hash for this object
    private hash<ConnectionSchemeInfo> getConnectionSchemeInfoImpl() {
        return ConnectionScheme;
    }

    #! Override to set child data provider capabilities once per child class
    private setChildCapabilities() {
        children_can_support_apis = True;
        children_can_support_records = True;
    }

    #! This method must return the connection object corresponding to the object's configuration
    /** @param connect if @ref True "True" then the connection should be returned connected (and if
        possible a round-trip "ping" of the server should be made)
        @param rtopts options to override connection options

        @return the connection object
    */
    private object getImpl(bool connect = True, *hash<auto> rtopts) {
        hash<auto> copts = getFlywayOptions();
        if (rtopts) {
            copts += rtopts;
        }
        list<string> locations = map ($1 =~ /:/ ? $1 : "filesystem:" + $1), copts.locations ?? ();
        return new QoreFlywayRunner(copts.url, copts.user, copts.password, locations);
    }
}
}
