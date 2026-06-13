# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaStatusMapping.qc Copyright 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.StatusCodes

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Detailed information about an OPC UA \c StatusCode
/** Provides a deterministic, generic decomposition of an OPC UA \c StatusCode into its numeric value,
    spec-defined severity, symbolic name, and (where known) diagnostic description.

    The severity is always derived from the two most-significant bits of the status code as defined by
    the OPC UA specification, so it is correct even for codes with no known symbolic name. Unknown codes
    yield a deterministic hexadecimal symbolic name (e.g. \c "0x80AB0000") rather than an error.
*/
public hashdecl OpcUaStatusInfo {
    #! the numeric OPC UA \c StatusCode value (an unsigned 32-bit integer)
    int code;

    #! the symbolic name (e.g. \c "Good", \c "BadNodeIdUnknown", or \c "0x80340000" if unknown)
    string symbolic_name;

    #! the spec-defined severity: \c "Good", \c "Uncertain", or \c "Bad"
    string severity;

    #! @c True if the status indicates good quality
    bool is_good;

    #! @c True if the status indicates uncertain quality
    bool is_uncertain;

    #! @c True if the status indicates bad quality
    bool is_bad;

    #! a human-readable description of the status code, if known
    *string description;
}

#! Generic helper that maps OPC UA \c StatusCode values to symbolic names, severities, and diagnostics
/** This is the single, generic OPC UA status mapping used by client validation, response/event
    decoding, and tests. It is reused wherever an OPC UA status code must be reported. It contains no
    Qorus-specific behavior.

    Symbolic names and descriptions are resolved from the OPC UA stack's authoritative status-code
    registry; the severity is derived directly from the spec's bit layout.
*/
public class OpcUaStatusMapping {
    public {
        #! mask selecting the two severity bits (bits 30-31) of an OPC UA status code
        const SeverityMask = 0xC0000000;

        #! mask selecting the low 32 bits (an OPC UA status code is an unsigned 32-bit value)
        const Uint32Mask = 0xFFFFFFFF;

        #! severity name by the value of the two severity bits (index 3 is reserved, treated as Bad)
        const SeverityNames = ("Good", "Uncertain", "Bad", "Bad");
    }

    #! Returns detailed status information for the given numeric OPC UA status code
    /** @param status_code the numeric OPC UA \c StatusCode value; only the low 32 bits are significant

        @return an @ref OpcUaStatusInfo hash describing the code; unknown codes yield a deterministic
        hexadecimal symbolic name and no description, never an exception
    */
    static hash<OpcUaStatusInfo> get(softint status_code) {
        int code = status_code & Uint32Mask;
        int severity_index = (code >> 30) & 3;
        string severity = SeverityNames[severity_index];

        string symbolic_name;
        *string description;
        # resolve the symbolic name and description from the authoritative OPC UA status-code registry
        auto entry = StatusCodes::lookup(code);
        if (entry.isPresent()) {
            auto info = entry.get();
            # the registry uses underscore-separated names (e.g. "Bad_NodeIdUnknown"); the canonical
            # OPC UA symbolic name has no underscores (e.g. "BadNodeIdUnknown")
            symbolic_name = replace(info[0], "_", "");
            description = info[1];
        } else {
            symbolic_name = sprintf("0x%08X", code);
        }

        return <OpcUaStatusInfo>{
            "code": code,
            "symbolic_name": symbolic_name,
            "severity": severity,
            "is_good": severity == "Good",
            "is_uncertain": severity == "Uncertain",
            "is_bad": severity == "Bad",
            "description": description,
        };
    }

    #! Returns detailed status information for the given Milo \c StatusCode object
    /** @param status_code a Milo \c org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode object

        @return an @ref OpcUaStatusInfo hash describing the code
    */
    static hash<OpcUaStatusInfo> getFromStatusCode(auto status_code) {
        return get(int(status_code.getValue()));
    }
}
}
