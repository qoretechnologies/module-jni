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
        #! the spreadsheet file: the file of the parent provider or a copy of the input; no value without input
        *OdsFile file;

        #! the configured iterator of the next search
        /** It is created in advance so that an unknown worksheet or invalid options fail when the provider is
            created; a search releases its iterator when it ends, so each search has an iterator of its own
        */
        *OdsIterator i;

        #! the record type, kept once it is read
        *hash<string, AbstractDataField> record_type;

        #! serializes access to the iterator of the next search and to the record type
        Mutex lck();

        #! the name of the object
        string name;

        #! the name of the worksheet
        string sheet;

        #! ODS iteration options
        *hash<auto> opts;
    }

    #! Creates the object with the input stream, name, sheet name, and optionally an option hash
    /** @param i the input stream; it is copied to a temporary file, which is deleted with this object
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(InputStream i, string name, string sheet, *hash<auto> opts) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        file = new OdsFile(i);
        setup(name, sheet, opts);
    }

    #! Creates the object with the input data path, name, sheet name, and optionally an option hash
    /** @param path the location of the input file; resolved with a call to
        @ref FileLocationHandler::FileLocationHandler::getBinaryStreamFromLocation() and copied to a temporary file,
        which is deleted with this object
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(string path, string name, string sheet, *hash<auto> opts) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        file = new OdsFile(FileLocationHandler::getBinaryStreamFromLocation(path));
        setup(name, sheet, opts);
    }

    #! Creates the object with the workbook file of a parent provider
    /** @param file the workbook file; it is retained until this object is destroyed
        @param name the name of the object
        @param sheet the name of the current worksheet
        @param opts a hash of optional options, must be already processed for validity
    */
    constructor(OdsFile file, string name, string sheet, *hash<auto> opts) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        self.file = file.retain();
        setup(name, sheet, opts);
    }

    #! Releases the workbook file, if any
    destructor() {
        if (file) {
            file.close();
        }
    }

    #! Creates the object from constructor options
    /** Without input (\c path, \c stream, or \c data), the provider has no records
    */
    constructor(*hash<auto> options) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
        *hash<auto> copts = OdsReadDataProvider::processCellOptions(checkOptions("ODS-READ-OPTION-ERROR",
            OdsReadDataProvider::ConstructorOptions, options));
        if ((copts.path ? 1 : 0) + (copts.stream ? 1 : 0) + (copts.data ? 1 : 0) > 1) {
            error("ODS-READ-OPTION-ERROR", "only one of the \"path\", \"stream\", and \"data\" options can be "
                "given");
        }
        if (copts.path) {
            file = new OdsFile(FileLocationHandler::getBinaryStreamFromLocation(copts.path));
        } else if (copts.stream) {
            file = new OdsFile(copts.stream);
        } else if (copts.data) {
            file = new OdsFile(new BinaryInputStream(copts.data));
        }
        setup(copts.worksheet ?? "", copts.worksheet ?? "", copts - ("stream", "path", "worksheet", "data"));
    }

    #! Common constructor code
    /** Creates the iterator of the first search, which checks the worksheet and the options; the file is released
        if this fails, as the destructor is not run when a constructor fails
    */
    private setup(string name, string sheet, *hash<auto> opts) {
        self.name = name;
        self.sheet = sheet;
        self.opts = opts;
        if (file) {
            try {
                i = getIterator();
            } catch (hash<ExceptionInfo> ex) {
                file.close();
                remove file;
                rethrow;
            }
        }
    }

    #! Returns a new iterator over the worksheet, configured with the options
    private OdsIterator getIterator() {
        OdsIterator rv(file.getPath(), sheet);
        try {
            OdsReadDataProvider::configureIterator(rv, opts);
        } catch (hash<ExceptionInfo> ex) {
            # release the document now rather than when the object is collected
            rv.close();
            rethrow;
        }
        return rv;
    }

    #! Returns the object name
    string getName() {
        return sheet;
    }

    #! Returns an iterator for zero or more records matching the search options
    /** Each search reads the worksheet with an iterator of its own, which the search releases when it ends

        @param where_cond the search criteria
        @param search_options the search options after processing by validateSearchOptions()
    */
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond, *hash<auto> search_options) {
        *hash<string, AbstractDataField> rec_type = getRecordType();
        if (!file) {
            # a provider without input has no records
            return new DefaultRecordIterator(new ListIterator(), where_cond, search_options, rec_type);
        }
        *OdsIterator it;
        {
            AutoLock al(lck);
            it = remove i;
        }
        return new DefaultRecordIterator(it ?? getIterator(), where_cond, search_options, rec_type);
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (!file) {
            return {};
        }
        AutoLock al(lck);
        if (!record_type) {
            # the iterator of the next search
            if (!i) {
                i = getIterator();
            }
            record_type = OdsReadDataProvider::getIteratorRecordType(i, sheet, opts);
        }
        return record_type;
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
}
