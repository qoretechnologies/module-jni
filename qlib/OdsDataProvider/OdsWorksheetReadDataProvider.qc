# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore OdsWorksheetReadDataProvider class definition

/*  OdsWorksheetReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the OdsDataProvider namespace
public namespace OdsDataProvider {
#! Provides a data provider for reading a specific worksheet from an ODS file
public class OdsWorksheetReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "OdsWorksheetReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": OdsReadDataProvider::ConstructorOptions,
            "search_options": OdsReadDataProvider::SearchOptions,
        };
    }

    private {
        #! the iterator object
        OdsIterator i;

        #! the name of the object
        string name;

        #! the name of the worksheet
        string sheet;

        #! ODS iteration options
        *hash<auto> opts;
    }

    #! Creates the OdsWorksheetReadDataProvider with the input stream, name, sheet name, and optionally an option hash
    constructor(InputStream i, string name, string sheet, *hash<auto> opts) {
        self.i = new OdsIterator(i, sheet);
        setup(name, sheet, opts);
    }

    #! Creates the OdsWorksheetReadDataProvider with the input data path, name, sheet name, and optionally an option hash
    constructor(string path, string name, string sheet, *hash<auto> opts) {
        self.i = new OdsIterator(FileLocationHandler::getBinaryStreamFromLocation(path), sheet);
        setup(name, sheet, opts);
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = OdsReadDataProvider::processCellOptions(checkOptions("ODS-READ-OPTION-ERROR",
            OdsReadDataProvider::ConstructorOptions, options));
        sheet = copts.worksheet ?? "";
        if ((copts.path ? 1 : 0) + (copts.stream ? 1 : 0) + (copts.data ? 1 : 0) > 1) {
            error("ODS-READ-OPTION-ERROR", "only one of the \"path\", \"stream\", and \"data\" options can be "
                "given");
        }
        if (copts.path) {
            i = new OdsIterator(FileLocationHandler::getBinaryStreamFromLocation(copts.path), sheet);
        } else if (copts.stream) {
            i = new OdsIterator(copts.stream, sheet);
        } else if (copts.data) {
            i = new OdsIterator(new BinaryInputStream(copts.data), sheet);
        }
        self.opts = copts - ("stream", "path", "worksheet", "data");
        if (!i) {
            i = new OdsIterator(new StringInputStream(""), sheet);
        }
        setOptions();
    }

    #! Common constructor code
    private setup(string name, string sheet, *hash<auto> opts) {
        self.name = name;
        self.sheet = sheet;
        self.opts = opts;
        setOptions();
    }

    #! Common setup code
    private setOptions() {
        OdsReadDataProvider::configureIterator(i, opts);
    }

    #! Returns the object name
    string getName() {
        return sheet;
    }

    #! Returns an iterator for zero or more records matching the search options
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        return new DefaultRecordIterator(i, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return map {
            $1: new QoreDataField($1, "ODS sheet " + sheet + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, i.getHeaders();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
}
