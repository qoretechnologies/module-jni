# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore FlywayDataProvider module definition

/** FlywayActionResponseDataTypes.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! Contains public Flyway action response descriptions
public namespace FlywayDataProvider {
#! Validation results from Flyway, including failures with their migration details
public class FlywayValidateOutputDataType inherits HashDataType {
    private {
        const Fields = {
            "validationSuccessful": {
                "display_name": "Validation Successful",
                "type": BoolType,
                "desc": "Whether every applied migration matches the available migration scripts",
            },
            "invalidMigrations": {
                "display_name": "Invalid Migrations",
                "type": ListType,
                "desc": "Invalid migrations, each with `version`, `description`, `filepath`, and `errorDetails`",
            },
            "errorDetails": {
                "display_name": "Error Details",
                "type": StringOrNothingType,
                "desc": "The overall validation error message, or `NOTHING` when validation succeeds",
            },
        };
    }

    constructor() {
        addQoreFields(Fields);
    }
}

#! Successful completion of a clean or repair request
public class FlywaySuccessOutputDataType inherits HashDataType {
    private {
        const Fields = {
            "success": {
                "display_name": "Success",
                "type": BoolType,
                "desc": "Whether the requested operation completed successfully",
            },
        };
    }

    constructor() {
        addQoreFields(Fields);
    }
}

#! Flyway validation response type
public const FlywayValidateOutputDataType = new FlywayValidateOutputDataType();
#! Flyway clean and repair response type
public const FlywaySuccessOutputDataType = new FlywaySuccessOutputDataType();
}
