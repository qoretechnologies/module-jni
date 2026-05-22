# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcConnection.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the JdbcDataProvider module
public namespace JdbcDataProvider {
#! Class for JDBC database connections
public class JdbcConnection inherits ConnectionProvider::AbstractConnectionWithInfo {
    public {
        #! Connection entry info
        const ConnectionScheme = <ConnectionSchemeInfo>{
            "display_name": "JDBC Database Connection",
            "short_desc": "A connection for JDBC database access",
            "desc": "A connection for JDBC database access providing JDBC URL, credentials, and "
                "optional driver classpath",
            "cls": Class::forName("JdbcConnection"),
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
                "classpath": <ConnectionOptionInfo>{
                    "display_name": "Driver Classpath",
                    "short_desc": "Classpath for JDBC driver JAR files",
                    "type": "string",
                    "desc": "JDBC driver JAR file, directory, or classpath list to add to the "
                        "JNI classpath before opening the connection. Comma-separated entries "
                        "are also accepted",
                    "example_value": "/usr/share/java/postgresql.jar",
                },
                "driver_class": <ConnectionOptionInfo>{
                    "display_name": "Driver Class",
                    "short_desc": "Java JDBC driver class name",
                    "type": "string",
                    "desc": "Optional Java JDBC driver class to instantiate before connecting, "
                        "for example `org.postgresql.Driver`",
                    "example_value": "org.postgresql.Driver",
                },
            },
            "schemes": {
                "jdbc-dp": True,
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
        return "jdbc-dp";
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
        return "JdbcDP";
    }

    #! Returns a @ref JdbcDataProvider object for this connection
    /** @param constructor_options any additional constructor options for the data provider

        @return a data provider object for this connection, if supported
    */
    AbstractDataProvider getDataProvider(*hash<auto> constructor_options) {
        return new JdbcDataProvider(self, constructor_options);
    }

    #! Returns options that can be used to create JDBC data providers
    hash<auto> getJdbcOptions() {
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
        hash<auto> copts = getJdbcOptions();
        if (rtopts) {
            copts += rtopts;
        }
        Priv::prepareJdbcDriver(copts);
        return DriverManager::getConnection(copts.url, copts.user, copts.password);
    }
}
}
