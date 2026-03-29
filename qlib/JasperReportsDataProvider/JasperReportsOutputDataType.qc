# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JasperReportsDataProvider module definition

/** JasperReportsOutputDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the JasperReportsDataProvider module
public namespace JasperReportsDataProvider {
#! JasperReports output data type
public class JasperReportsOutputDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "data": {
                "display_name": "Report Data",
                "type": DataType,
                "desc": "The generated report as binary data in the requested output format "
                    "(`pdf`, `xlsx`, `html`, or `csv`)",
            },
            "format": {
                "display_name": "Output Format",
                "type": StringType,
                "desc": "The output format of the generated report",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "value": "pdf",
                        "display_name": "PDF",
                    },
                    <AllowedValueInfo>{
                        "value": "xlsx",
                        "display_name": "Excel (XLSX)",
                    },
                    <AllowedValueInfo>{
                        "value": "html",
                        "display_name": "HTML",
                    },
                    <AllowedValueInfo>{
                        "value": "csv",
                        "display_name": "CSV",
                    },
                ),
            },
            "record_count": {
                "display_name": "Record Count",
                "type": IntType,
                "desc": "The number of data records included in the report",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the JasperReports output data type
public const JasperReportsOutputDataType = new JasperReportsOutputDataType();
}
