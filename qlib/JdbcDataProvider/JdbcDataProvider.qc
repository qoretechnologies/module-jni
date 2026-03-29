# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for JDBC database access
/**
*/
public class JdbcDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "jdbc-dp",
            "desc": "Parent data provider for JDBC database access",
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
        };

        #! Child data provider names
        const ChildList = ("tables", "query", "exec");
    }

    private {
        #! The JDBC connection
        auto connection;

        #! The schema introspector
        JdbcSchemaIntrospector introspector;

        #! The query executor
        JdbcQueryExecutor executor;

        #! The name of the provider
        string name = "jdbc-dp";
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("JDBC-CONSTRUCTOR-ERROR", ConstructorOptions, options);
        if (!copts.url) {
            error("JDBC-CONSTRUCTOR-ERROR", "the \"url\" option is required");
        }
        if (!copts.user) {
            error("JDBC-CONSTRUCTOR-ERROR", "the \"user\" option is required");
        }
        if (!copts.password) {
            error("JDBC-CONSTRUCTOR-ERROR", "the \"password\" option is required");
        }
        connection = DriverManager::getConnection(copts.url, copts.user, copts.password);
        introspector = new JdbcSchemaIntrospector(connection);
        executor = new JdbcQueryExecutor(connection);
    }

    #! Creates the object from a connection object
    constructor(JdbcConnection conn, *hash<auto> options) {
        hash<auto> copts = conn.getJdbcOptions();
        if (options) {
            copts += options;
        }
        connection = DriverManager::getConnection(copts.url, copts.user, copts.password);
        introspector = new JdbcSchemaIntrospector(connection);
        executor = new JdbcQueryExecutor(connection);
    }

    #! Closes the JDBC connection
    destructor() {
        if (connection) {
            connection.close();
        }
    }

    string getName() {
        return name;
    }

    #! Returns the JDBC connection
    auto getConnection() {
        return connection;
    }

    #! Returns the schema introspector
    JdbcSchemaIntrospector getIntrospector() {
        return introspector;
    }

    #! Returns the query executor
    JdbcQueryExecutor getExecutor() {
        return executor;
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
            case "tables":
                return new JdbcTablesDataProvider(introspector, executor);
            case "query":
                return new JdbcQueryDataProvider(executor);
            case "exec":
                return new JdbcExecDataProvider(executor);
        }
    }
}
}
