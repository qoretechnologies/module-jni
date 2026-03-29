# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayInfoDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for retrieving Flyway migration information
/**
*/
public class FlywayInfoDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "info",
            "desc": "Data provider for retrieving Flyway migration information",
            "supports_read": True,
            "has_record": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_read",
        });

        #! Record field descriptions
        const RecordFields = {
            "version": {
                "display_name": "Version",
                "short_desc": "The migration version",
                "desc": "The version of the migration",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "description": {
                "display_name": "Description",
                "short_desc": "The migration description",
                "desc": "The description of the migration",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "type": {
                "display_name": "Type",
                "short_desc": "The migration type",
                "desc": "The type of the migration (e.g. SQL, JDBC, BASELINE)",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "state": {
                "display_name": "State",
                "short_desc": "The migration state",
                "desc": "The current state of the migration (e.g. PENDING, SUCCESS, FAILED)",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "installedOn": {
                "display_name": "Installed On",
                "short_desc": "The date/time the migration was installed",
                "desc": "The date and time when the migration was applied to the database",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "executionTime": {
                "display_name": "Execution Time",
                "short_desc": "The execution time in milliseconds",
                "desc": "The execution time of the migration in milliseconds",
                "type": AbstractDataProviderTypeMap."*int",
            },
        };
    }

    private {
        #! The Flyway runner
        QoreFlywayRunner runner;
    }

    #! Creates the object from the runner
    constructor(QoreFlywayRunner runner) {
        self.runner = runner;
    }

    string getName() {
        return "info";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()

        @return an iterator for matching records
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        auto records = runner.info();
        return new DefaultRecordIterator(new ListIterator(records), where_cond, search_options,
            getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {$1.key: new QoreDataField($1.key, $1.value.desc, $1.value.type)},
            RecordFields.pairIterator();
    }
}
}
