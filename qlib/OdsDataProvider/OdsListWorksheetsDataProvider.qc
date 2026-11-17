# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OdsListWorksheetsDataProvider class definition

/*  OdsListWorksheetsDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! The request of the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public class OdsWorksheetListRequestDataType inherits HashDataType {
    public {
        #! Field descriptions
        const Fields = {
            "path": {
                "display_name": "Spreadsheet Location",
                "short_desc": "The location of the ODS spreadsheet",
                "type": StringOrNothingType,
                "desc": "The location of the OpenDocument spreadsheet (`.ods`), for example "
                    "`file:///data/stock.ods` or an `sftp://` location; conflicts with `data`",
                "example_value": "file:///data/po-confirmations.ods",
            },
            "data": {
                "display_name": "Spreadsheet Data",
                "short_desc": "The ODS spreadsheet content",
                "type": AbstractDataProviderTypeMap."*base64binary",
                "desc": "The OpenDocument spreadsheet content (`.ods`), as bytes or as a Base64 string; "
                    "conflicts with `path`",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "OdsWorksheetListRequest") {
        addQoreFields(Fields);
    }
}

#! Describes a worksheet listed by the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public class OdsWorksheetInfoDataType inherits HashDataType {
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
                "desc": "The 0-based position of the worksheet in the spreadsheet; the `worksheet` read option "
                    "accepts it as well as the name",
            },
            "hidden": {
                "display_name": "Hidden",
                "type": BoolType,
                "desc": "`True` if the worksheet is hidden: its table style does not display it",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "OdsWorksheetInfo") {
        addQoreFields(Fields);
    }
}

#! The response of the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public class OdsWorksheetListDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "worksheets": {
                "display_name": "Worksheets",
                "type": new ListDataType("OdsWorksheetInfoList", OdsWorksheetInfoDataType),
                "desc": "The worksheets in spreadsheet order, hidden worksheets included",
            },
        };
    }

    #! Creates the type
    constructor() : HashDataType(AutoHashType, "OdsWorksheetList") {
        addQoreFields(Fields);
    }
}

#! The request of the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public const OdsWorksheetListRequestDataType = new OdsWorksheetListRequestDataType();

#! Describes a worksheet listed by the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public const OdsWorksheetInfoDataType = new OdsWorksheetInfoDataType();

#! The response of the \c list-worksheets action
/** @since OdsDataProvider 1.0
*/
public const OdsWorksheetListDataType = new OdsWorksheetListDataType();

#! Lists the worksheets of an ODS spreadsheet
/** Lists the worksheets (tables) of an \c .ods spreadsheet in spreadsheet order, with each worksheet's 0-based index
    and whether it is hidden.  The input is copied to a temporary file in bounded memory, which is deleted when the
    request returns; the worksheet list is read without reading the rows of the worksheets.

    @par Example
    @code{.py}
    # find the worksheet of a supplier's order confirmations, which some suppliers send as the second worksheet
    OdsListWorksheetsDataProvider lister();
    hash<auto> rv = lister.doRequest({"path": "file:///data/inbound/po-confirmations.ods"});
    *hash<auto> sheet = (select rv.worksheets, !$1.hidden && $1.name =~ /confirm/i)[0];
    @endcode

    @since OdsDataProvider 1.0
*/
public class OdsListWorksheetsDataProvider inherits AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "list-worksheets",
            "desc": "Lists the worksheets of an ODS spreadsheet with their position and whether they are hidden",
            "type": "OdsListWorksheetsDataProvider",
            "supports_request": True,
        };

        #! Request type
        const RequestType = OdsWorksheetListRequestDataType;

        #! Response type
        const ResponseType = OdsWorksheetListDataType;
    }

    #! Creates the object
    constructor(*hash<auto> options) {
        # uncontrolled Java calls (QDOM_UNCONTROLLED_API): sandboxed code is refused up front (qore#5741)
        DataProviderActionCatalog::checkSandboxAccess(OdsDataProvider::AppName);
    }

    #! Returns the data provider name
    string getName() {
        return ProviderInfo.name;
    }

    #! Returns data provider static info
    hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Lists the worksheets of a spreadsheet
    /** @param req the request; see @ref OdsWorksheetListRequestDataType
        @param request_options request options

        @return the worksheets; see @ref OdsWorksheetListDataType

        @throw ODS-READ-OPTION-ERROR neither or both of \c path and \c data were given
    */
    private hash<auto> doRequestImpl(auto req, *hash<auto> request_options) {
        if (exists req.path == exists req.data) {
            throw "ODS-READ-OPTION-ERROR", "exactly one of the \"path\" and \"data\" options must be given";
        }
        OdsFile file(exists req.data
            ? new BinaryInputStream(req.data)
            : FileLocationHandler::getBinaryStreamFromLocation(req.path));
        on_exit file.close();
        return {"worksheets": OdsIterator::getWorksheetInfo(file.getPath())};
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
