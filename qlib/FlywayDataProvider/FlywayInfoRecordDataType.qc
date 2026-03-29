# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayInfoRecordDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Flyway info record data type
public class FlywayInfoRecordDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "version": {
                "display_name": "Version",
                "type": SoftStringOrNothingType,
                "desc": "The version number of the migration",
            },
            "description": {
                "display_name": "Description",
                "type": SoftStringOrNothingType,
                "desc": "The description of the migration",
            },
            "type": {
                "display_name": "Migration Type",
                "type": SoftStringOrNothingType,
                "desc": "The type of migration (`SQL`, `JAVA`, or `SCRIPT`)",
            },
            "state": {
                "display_name": "State",
                "type": SoftStringOrNothingType,
                "desc": "The current state of the migration",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Pending",
                        "value": "PENDING",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Applied",
                        "short_desc": "Migration has been successfully applied",
                        "value": "APPLIED",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Failed",
                        "value": "FAILED",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Missing (Success)",
                        "value": "MISSING_SUCCESS",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Missing (Failed)",
                        "value": "MISSING_FAILED",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Outdated",
                        "value": "OUTDATED",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Future (Success)",
                        "value": "FUTURE_SUCCESS",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Future (Failed)",
                        "value": "FUTURE_FAILED",
                    },
                ),
            },
            "installedOn": {
                "display_name": "Installed On",
                "type": SoftStringOrNothingType,
                "desc": "The timestamp when the migration was applied",
            },
            "executionTime": {
                "display_name": "Execution Time",
                "type": IntOrNothingType,
                "desc": "The execution time of the migration in milliseconds",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the Flyway info record data type
public const FlywayInfoRecordDataType = new FlywayInfoRecordDataType();
}
