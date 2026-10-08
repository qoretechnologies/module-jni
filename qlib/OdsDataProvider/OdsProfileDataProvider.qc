# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OdsProfileDataProvider class definition

/*  OdsProfileDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the OdsDataProvider module
public namespace OdsDataProvider {
#! Profiles ODS spreadsheets: describes each sheet's layout and column types
/** Reads every sheet of an OpenDocument spreadsheet (\c .ods) as raw cells, including hidden sheets, merged
    regions, formula text, and named ranges, and profiles it with @ref DataProvider::TabularProfiler "TabularProfiler".

    @since OdsDataProvider 1.0
*/
public class OdsProfileDataProvider inherits AbstractTabularProfileDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "profile",
            "desc": "Profiles ODS spreadsheets: describes each sheet's header, data region, totals rows, and "
                "column types",
            "type": "OdsProfileDataProvider",
            "supports_request": True,
        };
    }

    #! Creates the object
    constructor(*hash<auto> options) : AbstractTabularProfileDataProvider(options) {
    }

    #! Returns data provider static info
    hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Reads a spreadsheet document as a raw grid of cells
    /** @param content the document content
        @param max_rows the maximum number of rows read per sheet

        @return the raw grid of every sheet
    */
    static hash<TabularWorkbookGrid> readWorkbookGrid(binary content, int max_rows) {
        return TabularProfiler::gridFromHash(OdsGridReader::readGrid(new BinaryInputStream(content), max_rows));
    }

    #! Reads the content as a raw grid of cells
    private hash<TabularWorkbookGrid> readGrid(binary content, int max_rows) {
        return readWorkbookGrid(content, max_rows);
    }

    #! Returns the content of the file at a location
    private binary readLocation(string location) {
        return FileLocationHandler::getBinaryFileFromLocation(location);
    }
}
}
