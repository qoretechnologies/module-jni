# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore AccessTableReadDataProvider class definition

/*  AccessTableReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the AccessDataProvider namespace. All classes used in the AccessDataProvider module should be inside this namespace
public namespace AccessDataProvider {
#! Provides a data provider for reading a specific table from an Access database
public class AccessTableReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "AccessTableReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": AccessReadDataProvider::ConstructorOptions,
            "search_options": AccessReadDataProvider::SearchOptions,
        };
    }

    private {
        #! the iterator object
        AccessIterator i;

        #! the name of the parent data source
        string name;

        #! the name of the table
        string table_name;

        #! Temp file path for binary data cleanup
        *string temp_path;
    }

    #! Creates the AccessTableReadDataProvider with the database path, name, and table name
    /** @param db_path the path to the Access database file
        @param name the name of the parent data source
        @param table_name the name of the table to iterate
    */
    constructor(string db_path, string name, string table_name) {
        self.i = new AccessIterator(db_path, table_name);
        self.name = name;
        self.table_name = table_name;
    }

    #! Destructor - clean up temp file if created
    destructor() {
        cleanupTempFile();
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ACCESS-READ-OPTION-ERROR", AccessReadDataProvider::ConstructorOptions,
            options);
        table_name = copts.table_name;
        if (!table_name) {
            throw "ACCESS-READ-OPTION-ERROR", "table_name option is required for AccessTableReadDataProvider";
        }
        if (copts.path) {
            i = new AccessIterator(copts.path, table_name);
            name = copts.path;
        } else if (copts.data) {
            # Write data to temp file (Jackcess requires a File)
            temp_path = tmp_location() + "/qore-access-" + get_random_string(16) + ".accdb";
            File f();
            f.open(temp_path, O_CREAT | O_WRONLY | O_TRUNC);
            f.write(copts.data);
            try {
                i = new AccessIterator(temp_path, table_name);
            } catch (hash<ExceptionInfo> ex) {
                cleanupTempFile();
                rethrow;
            }
            name = "access-data";
        } else {
            throw "ACCESS-READ-OPTION-ERROR", "either \"path\" or \"data\" option is required";
        }
    }

    #! Cleans up the temp file if one was created
    private cleanupTempFile() {
        if (temp_path && is_file(temp_path)) {
            unlink(temp_path);
            temp_path = NOTHING;
        }
    }

    #! Returns the object name
    string getName() {
        return table_name;
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond, *hash<auto> search_options) {
        return new DefaultRecordIterator(i, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {
            $1: new QoreDataField($1, "Access table " + table_name + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, i.getColumnNames();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
}
