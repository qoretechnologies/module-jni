# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JdbcDataProvider module definition

/** JdbcTablesDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for listing JDBC database tables
/**
*/
public class JdbcTablesDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "tables",
            "desc": "Data provider for listing JDBC database tables; each table is a child provider",
            "supports_children": True,
            "supports_read": True,
            "has_record": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_children", "supports_read",
        });

        #! Record field descriptions for table listing
        const RecordFields = {
            "table_name": {
                "display_name": "Table Name",
                "short_desc": "The name of the database table",
                "desc": "The name of the database table",
                "type": AbstractDataProviderTypeMap."string",
            },
            "table_type": {
                "display_name": "Table Type",
                "short_desc": "The type of the table (e.g. TABLE, VIEW)",
                "desc": "The type of the table (e.g. TABLE, VIEW, SYSTEM TABLE)",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "schema": {
                "display_name": "Schema",
                "short_desc": "The schema containing the table",
                "desc": "The database schema containing the table",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "catalog": {
                "display_name": "Catalog",
                "short_desc": "The catalog containing the table",
                "desc": "The database catalog containing the table",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "remarks": {
                "display_name": "Remarks",
                "short_desc": "Comments or remarks about the table",
                "desc": "Comments or remarks about the table",
                "type": AbstractDataProviderTypeMap."*string",
            },
        };
    }

    private {
        #! The schema introspector
        JdbcSchemaIntrospector introspector;

        #! The query executor
        JdbcQueryExecutor executor;
    }

    #! Creates the object from the introspector and executor
    constructor(JdbcSchemaIntrospector introspector, JdbcQueryExecutor executor) {
        self.introspector = introspector;
        self.executor = executor;
    }

    string getName() {
        return "tables";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        auto tables = introspector.getTables();
        return map $1.table_name, tables;
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown

        @see getChildProviderEx()
    */
    private *DataProvider::AbstractDataProvider getChildProviderImpl(string name) {
        auto tables = introspector.getTables();
        foreach auto table in (tables) {
            if (table.table_name == name) {
                return new JdbcTableDataProvider(name, introspector, executor);
            }
        }
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()

        @return an iterator for matching records
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        auto tables = introspector.getTables();
        return new DefaultRecordIterator(new ListIterator(tables), where_cond, search_options,
            getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {$1.key: new QoreDataField($1.key, $1.value.desc, $1.value.type)},
            RecordFields.pairIterator();
    }
}

#! Data provider for a single JDBC database table
/**
*/
public class JdbcTableDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "table",
            "desc": "Data provider for a single JDBC database table",
            "supports_read": True,
            "has_record": True,
        };
    }

    private {
        #! The table name
        string table_name;

        #! The schema introspector
        JdbcSchemaIntrospector introspector;

        #! The query executor
        JdbcQueryExecutor executor;
    }

    #! Creates the object for the given table
    constructor(string table_name, JdbcSchemaIntrospector introspector, JdbcQueryExecutor executor) {
        self.table_name = table_name;
        self.introspector = introspector;
        self.executor = executor;
    }

    string getName() {
        return table_name;
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo + {"name": table_name};
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()

        @return an iterator for matching records
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        string sql = "SELECT * FROM " + table_name;
        auto records = executor.executeQuery(sql);
        return new DefaultRecordIterator(new ListIterator(records), where_cond, search_options,
            getRecordType());
    }

    #! Returns the description of the record type, if any
    /** The record type is dynamically determined from the table's column metadata
    */
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        auto columns = introspector.getColumns(table_name);
        hash<string, AbstractDataField> rv;
        foreach auto col in (columns) {
            string col_name = col.column_name;
            string qore_type = JdbcTypeMapper::sqlTypeToQoreType(col.data_type);
            # Map nullable columns to optional types
            if (col.nullable) {
                qore_type = "*" + qore_type;
            }
            rv{col_name} = new QoreDataField(col_name, col.type_name ?? col_name,
                AbstractDataProviderType::get(qore_type));
        }
        return rv;
    }
}
}
