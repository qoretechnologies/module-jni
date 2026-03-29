# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore CamelDataProvider module definition

/** CamelDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Data provider for Apache Camel enterprise integration patterns
/**
*/
public class CamelDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "camel",
            "desc": "Parent data provider for Apache Camel enterprise integration patterns",
            "supports_children": True,
            "children_can_support_apis": True,
            "children_can_support_records": True,
        };

        #! Child data provider names
        const ChildList = ("producer", "routes");
    }

    private {
        #! The Camel context object
        QoreCamelContext ctx;

        #! The name of the provider
        string name = "camel";
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        ctx = new QoreCamelContext();
        ctx.start();
    }

    #! Creates the object from a connection object
    constructor(CamelConnection conn, *hash<auto> options) {
        ctx = new QoreCamelContext();
        ctx.start();
    }

    #! Destructor: stops and closes the Camel context
    destructor() {
        ctx.stop();
        ctx.close();
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
            case "producer":
                return new CamelProducerDataProvider(ctx);
            case "routes":
                return new CamelRoutesDataProvider(ctx);
        }
    }
}
}
