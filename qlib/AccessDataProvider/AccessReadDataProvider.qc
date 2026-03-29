# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore AccessReadDataProvider class definition

/*  AccessReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Provides a data provider for reading Access databases (.mdb/.accdb format)
public class AccessReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "AccessReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "supports_children": True,
            "constructor_options": ConstructorOptions,
            "search_options": SearchOptions,
            "children_can_support_records": True,
        };

        #! Search options
        const SearchOptions = GenericRecordSearchOptions{"requires_result", "max_records"} + {
            "limit": <DataProviderOptionInfo>{
                "display_name": "Record Limit",
                "short_desc": "The maximum number of records to return",
                "type": AbstractDataProviderType::get(IntType),
                "desc": "The maximum number of records to return",
            },
        };

        #! Constructor options
        const ConstructorOptions = {
            "data": <DataProviderOptionInfo>{
                "display_name": "Input Data",
                "short_desc": "Access database data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "Access database data to process; conflicts with `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "Access Input Path",
                "short_desc": "The path to the Access database file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The path to the Access database file; mutually exclusive with `data`; "
                    "resolved with a call to "
                    "[FileLocationHandler::getBinaryFileFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
                "example_value": "/path/to/database.accdb",
            },
            "table_name": <DataProviderOptionInfo>{
                "display_name": "Table Name",
                "short_desc": "The name of the table to read",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The name of the table to read; if not provided, use child providers to access tables",
                "example_value": "Users",
            },
        };
    }

    private {
        #! The path to the database file (or temp file path)
        *string db_path;

        #! Binary data for temp file creation
        *binary db_data;

        #! the iterator object
        AccessIterator it;

        #! The name of the object
        string name;

        #! The table name to iterate
        *string table_name;

        #! dummy iterator
        bool dummy;

        #! Temp file path for binary data
        *string temp_path;
    }

    #! Creates the AccessReadDataProvider with the input data path and optionally an option hash
    /** @param path the path to the Access database file
        @param opts a hash of optional options

        @throw ACCESS-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ACCESS-READ-OPTION-ERROR", ConstructorOptions, opts);
        db_path = path;
        name = path;
        table_name = copts.table_name;
    }

    #! Creates the object from constructor options
    /**
        @throw ACCESS-READ-OPTION-ERROR invalid or unknown option; invalid data type for option
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ACCESS-READ-OPTION-ERROR", ConstructorOptions, options);
        table_name = copts.table_name;
        if (copts.path) {
            if (copts.data) {
                error("ACCESS-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            db_path = copts.path;
            name = copts.path;
        } else if (copts.data) {
            # Store binary data for writing to temp file when iterator is created
            db_data = copts.data;
            name = "access-data";
        }
        if (!db_path && !db_data) {
            # create a dummy iterator with no input
            name = "access";
            dummy = True;
        }
    }

    #! Destructor - clean up temp file if created
    destructor() {
        cleanupTempFile();
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
        return name;
    }

    #! Returns the path to the database file, creating a temp file from binary data if needed
    private string getDbPath() {
        if (db_path) {
            return db_path;
        }
        if (db_data) {
            # Write binary data to a temp file
            temp_path = tmp_location() + "/qore-access-" + get_random_string(16) + ".accdb";
            File f();
            f.open(temp_path, O_CREAT | O_WRONLY | O_TRUNC);
            f.write(db_data);
            return temp_path;
        }
        throw "ACCESS-READ-OPTION-ERROR", "no path or data provided";
    }

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        if (!table_name) {
            throw "ACCESS-READ-OPTION-ERROR", "table_name option is required for direct iteration; use child "
                "providers to browse tables";
        }
        it = new AccessIterator(getDbPath(), table_name);
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond, *hash<auto> search_options) {
        checkIterator();
        return new DefaultRecordIterator(it, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (dummy) {
            return {};
        }
        checkIterator();
        return map {
            $1: new QoreDataField($1, "Access table column " + $1, AbstractDataProviderType::anyDataType)
        }, it.getColumnNames();
    }

    #! Returns a list of child data provider names, if any
    /** @return a list of child data provider names (table names), if any
    */
    private *list<string> getChildProviderNamesImpl() {
        if (dummy) {
            return;
        }
        return AccessIterator::getTableNames(getDbPath());
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown
    */
    private *AbstractDataProvider getChildProviderImpl(string child_name) {
        *list<string> tables = getChildProviderNamesImpl();
        if (tables) {
            *hash<string, bool> table_map = map {$1: True}, tables;
            if (table_map{child_name}) {
                return new AccessTableReadDataProvider(getDbPath(), name, child_name);
            }
        }
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # AccessDataProvider namespace
