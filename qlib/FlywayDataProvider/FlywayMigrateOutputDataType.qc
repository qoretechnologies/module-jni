# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayMigrateOutputDataType.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Flyway migrate output data type
public class FlywayMigrateOutputDataType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "version": {
                "display_name": "Version",
                "type": SoftStringOrNothingType,
                "desc": "The version number of the applied migration (e.g., `1`, `2.1`)",
            },
            "description": {
                "display_name": "Description",
                "type": SoftStringOrNothingType,
                "desc": "The description of the migration extracted from the filename",
            },
            "type": {
                "display_name": "Migration Type",
                "type": SoftStringOrNothingType,
                "desc": "The type of migration",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "SQL Script",
                        "value": "SQL",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Java Migration",
                        "value": "JAVA",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Script Migration",
                        "value": "SCRIPT",
                    },
                ),
            },
            "filepath": {
                "display_name": "File Path",
                "type": SoftStringOrNothingType,
                "desc": "The file path of the migration script",
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

#! Constant giving the Flyway migrate output data type
public const FlywayMigrateOutputDataType = new FlywayMigrateOutputDataType();
}
