# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsSelectorBuilder.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the JakartaJmsDataProvider module
public namespace JakartaJmsDataProvider {
#! Builds JMS SQL-92 message selectors from Qore where hash expressions
/** Converts a Qore hash to a JMS message selector string.

    Simple equality example:
    @code
    hash where = {"color": "red", "size": 42};
    # produces: "color = 'red' AND size = 42"
    @endcode

    Operator example:
    @code
    hash where = {"price": {"op": ">", "value": 100}};
    # produces: "price > 100"
    @endcode

    Supported operators: =, >, <, >=, <=, <>, LIKE, IN, BETWEEN, IS NULL, IS NOT NULL
*/
public class JmsSelectorBuilder {
    #! Builds a JMS message selector string from a where hash
    /** @param wh the where clause hash

        @return the SQL-92 selector string or NOTHING if wh is empty/null
    */
    static *string build(*hash<auto> wh) {
        if (!wh) {
            return NOTHING;
        }

        list<string> clauses = ();
        foreach hash<auto> entry in (wh.pairIterator()) {
            string name = entry.key;
            auto val = entry.value;

            if (val instanceof hash) {
                # operator form: {"op": ">", "value": 100}
                if (!val.hasKey("op") || val.op.typeCode() != NT_STRING) {
                    throw "JMS-SELECTOR-ERROR",
                        sprintf("operator hash for field %y must have a string 'op' key", name);
                }
                string op = val.op.upr();
                switch (op) {
                    case "=":
                    case ">":
                    case "<":
                    case ">=":
                    case "<=":
                    case "<>":
                        push clauses, sprintf("%s %s %s", name, op,
                            JmsSelectorBuilder::quoteValue(val.value));
                        break;

                    case "LIKE":
                        push clauses, sprintf("%s LIKE %s", name,
                            JmsSelectorBuilder::quoteValue(val.value));
                        break;

                    case "IN": {
                        if (!(val.value instanceof list)) {
                            throw "JMS-SELECTOR-ERROR",
                                sprintf("IN operator for field %y requires a list value", name);
                        }
                        list<string> vals = map JmsSelectorBuilder::quoteValue($1), val.value;
                        push clauses, sprintf("%s IN (%s)", name, vals.join(", "));
                        break;
                    }

                    case "BETWEEN": {
                        if (!(val.value instanceof list) || val.value.size() < 2) {
                            throw "JMS-SELECTOR-ERROR",
                                sprintf("BETWEEN operator for field %y requires a list of 2 values",
                                    name);
                        }
                        push clauses, sprintf("%s BETWEEN %s AND %s", name,
                            JmsSelectorBuilder::quoteValue(val.value[0]),
                            JmsSelectorBuilder::quoteValue(val.value[1]));
                        break;
                    }

                    case "IS NULL":
                        push clauses, sprintf("%s IS NULL", name);
                        break;

                    case "IS NOT NULL":
                        push clauses, sprintf("%s IS NOT NULL", name);
                        break;

                    default:
                        throw "JMS-SELECTOR-ERROR", sprintf("unsupported operator %y", val.op);
                }
            } else if (!exists val || val === NULL) {
                # NULL/NOTHING equality maps to IS NULL in SQL-92
                push clauses, sprintf("%s IS NULL", name);
            } else {
                # simple equality
                push clauses, sprintf("%s = %s", name, JmsSelectorBuilder::quoteValue(val));
            }
        }

        return clauses ? clauses.join(" AND ") : NOTHING;
    }

    #! Quotes a value for use in a JMS selector expression
    /** @param val the value to quote

        @return the quoted string representation
    */
    static string quoteValue(auto val) {
        switch (val.typeCode()) {
            case NT_STRING: {
                # escape single quotes by doubling them (SQL-92 standard)
                string escaped = val;
                escaped = replace(escaped, "'", "''");
                return sprintf("'%s'", escaped);
            }

            case NT_INT:
            case NT_FLOAT:
            case NT_NUMBER:
                return string(val);

            case NT_BOOLEAN:
                return val ? "TRUE" : "FALSE";

            case NT_NULL:
            case NT_NOTHING:
                return "NULL";

            default:
                return sprintf("'%s'", string(val));
        }
    }
}
}
