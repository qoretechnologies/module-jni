# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaTypeConversion.qc Copyright 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
%module-cmd(jni) import org.qore.dataprovider.opcua.ValueCodec

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Generic helper that converts Qore values to Milo \c Variant values for OPC UA writes
/** Honors an optional OPC UA built-in type hint so the correct OPC UA type is sent to the server
    instead of relying on inference from the Qore value type. Scalar built-in types (including the
    narrow signed types \c SByte / \c Int16 / \c Int32 and single-precision \c Float, which cannot be
    typed exactly across the Qore/JNI boundary) are constructed by the compiled Java
    @ref OpcUaDataProvider::ValueCodec "ValueCodec"; \c DateTime is constructed from a Qore date.
*/
public class OpcUaTypeConversion {
    public {
        #! OPC UA write data type hints supported by this helper
        const SupportedWriteHints = {
            "Boolean": True, "SByte": True, "Byte": True, "Int16": True, "UInt16": True,
            "Int32": True, "UInt32": True, "Int64": True, "UInt64": True, "Float": True,
            "Double": True, "String": True, "DateTime": True, "ByteString": True,
        };

        #! Standard OPC UA built-in data type NodeIds supported by this helper
        const SupportedBuiltinNodeIdHints = {
            "i=1": "Boolean",
            "ns=0;i=1": "Boolean",
            "i=2": "SByte",
            "ns=0;i=2": "SByte",
            "i=3": "Byte",
            "ns=0;i=3": "Byte",
            "i=4": "Int16",
            "ns=0;i=4": "Int16",
            "i=5": "UInt16",
            "ns=0;i=5": "UInt16",
            "i=6": "Int32",
            "ns=0;i=6": "Int32",
            "i=7": "UInt32",
            "ns=0;i=7": "UInt32",
            "i=8": "Int64",
            "ns=0;i=8": "Int64",
            "i=9": "UInt64",
            "ns=0;i=9": "UInt64",
            "i=10": "Float",
            "ns=0;i=10": "Float",
            "i=11": "Double",
            "ns=0;i=11": "Double",
            "i=12": "String",
            "ns=0;i=12": "String",
            "i=13": "DateTime",
            "ns=0;i=13": "DateTime",
            "i=15": "ByteString",
            "ns=0;i=15": "ByteString",
        };
    }

    #! Normalizes a write/call data type hint to a supported OPC UA built-in type name
    /** Accepts either a built-in type name (for write requests) or the standard namespace NodeId form
        returned by OPC UA \c Argument metadata (for method calls).

        @param data_type the optional data type hint

        @return a supported built-in type name, or \c NOTHING if the hint is empty or unsupported
    */
    static *string normalizeDataTypeHint(*string data_type) {
        if (!exists data_type || data_type == "") {
            return NOTHING;
        }
        if (exists SupportedWriteHints{data_type}) {
            return data_type;
        }
        return SupportedBuiltinNodeIdHints{data_type};
    }

    #! Builds a Milo \c Variant from a Qore value, honoring an optional OPC UA data type hint
    /** When no hint is given, the type is inferred from the Qore value (legacy behavior). When a hint
        is given, the value is constructed as the requested OPC UA built-in type.

        @param value the Qore value to write
        @param data_type an optional OPC UA built-in type name (see @ref SupportedWriteHints)

        @return a Milo \c Variant carrying the value with the requested type

        @throw OPCUA-TYPE-CONVERSION-ERROR the hint is not a known OPC UA built-in scalar type name
    */
    static Variant getVariant(auto value, *string data_type) {
        *string hint = normalizeDataTypeHint(data_type);
        if (!hint) {
            if (!exists data_type || data_type == "") {
                # no hint: infer the type from the Qore value (legacy behavior)
                return new Variant(value);
            }
            throw "OPCUA-TYPE-CONVERSION-ERROR", sprintf("unknown OPC UA write data type hint %y; supported "
                "hints: %y", data_type, keys SupportedWriteHints);
        }
        data_type = hint;
        # DateTime and ByteString are built from Qore-native date/binary values
        if (data_type == "DateTime") {
            return new Variant(new DateTime(int(get_epoch_seconds(date(value))) * 1000));
        }
        if (data_type == "ByteString") {
            return new Variant(ByteString::of(binary(value)));
        }
        # all other scalar built-in types are constructed by the Java value codec, which can type
        # narrow signed and single-precision values exactly
        if (ValueCodec::isSupportedScalarType(data_type)) {
            return ValueCodec::toVariant(value, data_type);
        }
        throw "OPCUA-TYPE-CONVERSION-ERROR", sprintf("unsupported OPC UA write data type hint %y; supported "
            "hints: %y", data_type, keys SupportedWriteHints);
    }
}
}
