# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore ExcelWorksheetReadDataProvider class definition

/*  ExcelWorksheetReadDataProvider.qc Copyright 2021 - 2026 Qore Technologies, s.r.o.

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

#! the ExcelDataProvider namespace. All classes used in the ExcelDataProvider module should be inside this namespace
public namespace ExcelDataProvider {
#! Provides a data provider for reading EXCEL files
public class ExcelWorksheetReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "ExcelWorksheetReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": ExcelReadDataProvider::ConstructorOptions,
            "search_options": ExcelReadDataProvider::SearchOptions,
        };
    }

    private {
        #! the iterator object
        ExcelIterator i;

        #! the workbook file of the parent provider, if the provider was created from it
        *ExcelFile file;

        #! the name of the object
        string name;

        #! the name of the worksheet
        string sheet;

        #! Excel iteration options
        *hash<auto> opts;
    }

    #! Creates the ExcelReadDataProvider with the input stream, name, sheet name, and optionally an option hash
    /** @param i the input stream
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(InputStream i, string name, string sheet, *hash<auto> opts) {
        self.i = new ExcelIterator(i, sheet);
        setup(name, sheet, opts);
    }

    #! Creates the ExcelReadDataProvider with the input data path, name, sheet name, and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::FileLocationHandler::getBinaryStreamFromLocation()
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(string path, string name, string sheet, *hash<auto> opts) {
        self.i = new ExcelIterator(FileLocationHandler::getBinaryStreamFromLocation(path), sheet);
        setup(name, sheet, opts);
    }

    #! Creates the ExcelWorksheetReadDataProvider with the workbook file of a parent provider
    /** @param file the workbook file; it is retained until this object is destroyed
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(ExcelFile file, string name, string sheet, *hash<auto> opts) {
        self.i = new ExcelIterator(file.getPath(), sheet);
        self.file = file.retain();
        setup(name, sheet, opts);
    }

    #! Releases the workbook file of the parent provider, if any
    destructor() {
        if (file) {
            file.close();
        }
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = ExcelReadDataProvider::processCellOptions(checkOptions("EXCEL-READ-OPTION-ERROR",
            ExcelReadDataProvider::ConstructorOptions, options));
        sheet = copts.worksheet ?? "";
        if ((copts.path ? 1 : 0) + (copts.stream ? 1 : 0) + (copts.data ? 1 : 0) > 1) {
            error("EXCEL-READ-OPTION-ERROR", "only one of the \"path\", \"stream\", and \"data\" options can be "
                "given");
        }
        if (copts.path) {
            i = new ExcelIterator(FileLocationHandler::getBinaryStreamFromLocation(copts.path), sheet);
        } else if (copts.stream) {
            i = new ExcelIterator(copts.stream, sheet);
        } else if (copts.data) {
            i = new ExcelIterator(new BinaryInputStream(copts.data), sheet);
        }
        self.opts = copts - ("stream", "path", "worksheet", "data");
        if (!i) {
            # create a dummy interator with no input
            i = new ExcelIterator(new StringInputStream(""), sheet);
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
        ExcelReadDataProvider::configureIterator(i, opts);
    }

    #! Returns the object name
    string getName() {
        return sheet;
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
            $1: new QoreDataField($1, "Excel sheet " + sheet + " column " + $1,
                AbstractDataProviderType::anyDataType)
        }, i.getHeaders();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
}
