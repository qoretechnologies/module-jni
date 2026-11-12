# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore ExcelListWorksheetsDataProvider class definition

/*  ExcelListWorksheetsDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the ExcelDataProvider module
public namespace ExcelDataProvider {
#! The request of the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public class ExcelWorksheetListRequestDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "path": {
                "display_name": "Spreadsheet Location",
                "short_desc": "The location of the Excel spreadsheet",
                "type": StringOrNothingType,
                "desc": "The location of the Excel spreadsheet (`.xlsx` or `.xls`), for example "
                    "`file:///data/stock.xlsx` or an `sftp://` location; conflicts with `data`",
                "example_value": "file:///data/po-confirmations.xlsx",
            },
            "data": {
                "display_name": "Spreadsheet Data",
                "short_desc": "The Excel spreadsheet content",
                "type": AbstractDataProviderTypeMap."*base64binary",
                "desc": "The Excel spreadsheet content (`.xlsx` or `.xls`), as bytes or as a Base64 string; "
                    "conflicts with `path`",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "ExcelWorksheetListRequest") {
        addQoreFields(Fields);
    }
}

#! Describes a worksheet listed by the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public class ExcelWorksheetInfoDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "name": {
                "display_name": "Name",
                "type": StringType,
                "desc": "The worksheet name",
            },
            "index": {
                "display_name": "Index",
                "type": IntType,
                "desc": "The 0-based position of the worksheet in the workbook; the `worksheet` read option accepts "
                    "it as well as the name",
            },
            "hidden": {
                "display_name": "Hidden",
                "type": BoolType,
                "desc": "`True` if the worksheet is hidden, including a very hidden worksheet, which only a macro "
                    "can show",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "ExcelWorksheetInfo") {
        addQoreFields(Fields);
    }
}

#! The response of the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public class ExcelWorksheetListDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "worksheets": {
                "display_name": "Worksheets",
                "type": new ListDataType("ExcelWorksheetInfoList", ExcelWorksheetInfoDataType),
                "desc": "The worksheets in workbook order, hidden worksheets included",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "ExcelWorksheetList") {
        addQoreFields(Fields);
    }
}

#! The request of the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public const ExcelWorksheetListRequestDataType = new ExcelWorksheetListRequestDataType();

#! Describes a worksheet listed by the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public const ExcelWorksheetInfoDataType = new ExcelWorksheetInfoDataType();

#! The response of the \c list-worksheets action
/** @since ExcelDataProvider 1.1
*/
public const ExcelWorksheetListDataType = new ExcelWorksheetListDataType();

#! Lists the worksheets of an Excel spreadsheet
/** Lists the worksheets of an \c .xlsx or \c .xls workbook in workbook order, with each worksheet's 0-based index and
    whether it is hidden.  The input is copied to a temporary file in bounded memory, which is deleted when the
    request returns; an \c .xlsx workbook's worksheet list is read without reading its worksheets.

    @par Example
    @code{.py}
    # find the worksheet of a supplier's order confirmations, which some suppliers send as the second worksheet
    ExcelListWorksheetsDataProvider lister();
    hash<auto> rv = lister.doRequest({"path": "file:///data/inbound/po-confirmations.xlsx"});
    *hash<auto> sheet = (select rv.worksheets, !$1.hidden && $1.name =~ /confirm/i)[0];
    @endcode

    @since ExcelDataProvider 1.1
*/
public class ExcelListWorksheetsDataProvider inherits AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "list-worksheets",
            "desc": "Lists the worksheets of an Excel spreadsheet with their position and whether they are hidden",
            "type": "ExcelListWorksheetsDataProvider",
            "supports_request": True,
        };

        #! Request type
        const RequestType = ExcelWorksheetListRequestDataType;

        #! Response type
        const ResponseType = ExcelWorksheetListDataType;
    }

    #! Creates the object
    constructor(*hash<auto> options) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(ExcelDataProvider::AppName);
    }

    #! Returns the data provider name
    string getName() {
        return ProviderInfo.name;
    }

    #! Returns data provider static info
    hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Lists the worksheets of a workbook
    /** @param req the request; see @ref ExcelWorksheetListRequestDataType
        @param request_options request options

        @return the worksheets; see @ref ExcelWorksheetListDataType

        @throw EXCEL-READ-OPTION-ERROR neither or both of \c path and \c data were given
    */
    private hash<auto> doRequestImpl(auto req, *hash<auto> request_options) {
        if (exists req.path == exists req.data) {
            throw "EXCEL-READ-OPTION-ERROR", "exactly one of the \"path\" and \"data\" options must be given";
        }
        ExcelFile file(exists req.data
            ? new BinaryInputStream(req.data)
            : FileLocationHandler::getBinaryStreamFromLocation(req.path));
        on_exit file.close();
        return {"worksheets": ExcelIterator::getWorksheetInfo(file.getPath())};
    }

    #! Returns the request type
    private *AbstractDataProviderType getRequestTypeImpl() {
        return RequestType;
    }

    #! Returns the response type
    private *AbstractDataProviderType getResponseTypeImpl() {
        return ResponseType;
    }
}
}
