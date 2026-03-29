# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore CamelDataProvider module definition

/** CamelRoutesDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the CamelDataProvider module
public namespace CamelDataProvider {
#! Data provider for listing Apache Camel routes
/**
*/
public class CamelRoutesDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "routes",
            "desc": "Data provider for listing active Apache Camel routes",
            "supports_read": True,
            "has_record": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            "name", "desc", "supports_read",
        });

        #! Record field descriptions
        const RecordFields = {
            "id": {
                "display_name": "Route ID",
                "short_desc": "The route identifier",
                "desc": "The unique identifier of the Camel route",
                "type": AbstractDataProviderTypeMap."string",
            },
            "endpoint_uri": {
                "display_name": "Endpoint URI",
                "short_desc": "The route endpoint URI",
                "desc": "The endpoint URI that the route consumes from",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "status": {
                "display_name": "Status",
                "short_desc": "The route status",
                "desc": "The current status of the route (e.g. Started, Stopped)",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "uptime": {
                "display_name": "Uptime",
                "short_desc": "The route uptime",
                "desc": "The uptime of the route as a human-readable string",
                "type": AbstractDataProviderTypeMap."*string",
            },
            "description": {
                "display_name": "Description",
                "short_desc": "The route description",
                "desc": "The description of the route, if any",
                "type": AbstractDataProviderTypeMap."*string",
            },
        };
    }

    private {
        #! The Camel context
        QoreCamelContext ctx;
    }

    #! Creates the object from the Camel context
    constructor(QoreCamelContext ctx) {
        self.ctx = ctx;
    }

    string getName() {
        return "routes";
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
        auto records = ctx.getRoutes();
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
