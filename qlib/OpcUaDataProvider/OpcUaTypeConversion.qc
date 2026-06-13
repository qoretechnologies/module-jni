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
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Generic helper that converts Qore values to Milo \c Variant values for OPC UA writes
/** This is the seed of the OPC UA value codec used for client writes. It honors an optional OPC UA
    built-in type hint so the correct OPC UA type is sent to the server instead of relying on inference
    from the Qore value type.

    @note Narrow signed integer types (\c SByte, \c Int16, \c Int32) and \c Float cannot be constructed
    exactly through the Qore/JNI boundary with the bundled OPC UA stack (Java boxed signed primitives
    are widened), so they are rejected with an actionable error rather than silently widened. Exact
    typing for these will be provided by the Java conversion codec.
*/
public class OpcUaTypeConversion {
    public {
        #! OPC UA write data type hints that can be constructed directly (without the Java codec)
        const SupportedWriteHints = {
            "Boolean": True,
            "Byte": True,
            "UInt16": True,
            "UInt32": True,
            "UInt64": True,
            "Int64": True,
            "Double": True,
            "String": True,
            "DateTime": True,
            "ByteString": True,
        };

        #! OPC UA write data type hints that require the (not-yet-available) Java conversion codec
        const UnsupportedWriteHints = ("SByte", "Int16", "Int32", "Float");
    }

    #! Builds a Milo \c Variant from a Qore value, honoring an optional OPC UA data type hint
    /** When no hint is given, the type is inferred from the Qore value (legacy behavior). When a hint
        is given, the value is constructed as the requested OPC UA built-in type.

        @param value the Qore value to write
        @param data_type an optional OPC UA built-in type name (see @ref SupportedWriteHints)

        @return a Milo \c Variant carrying the value with the requested type

        @throw OPCUA-TYPE-CONVERSION-ERROR the hint is unknown, or names a narrow signed/single-precision
        type (\c SByte, \c Int16, \c Int32, \c Float) that cannot be constructed exactly without the Java
        conversion codec; the value is never silently widened
    */
    static Variant getVariant(auto value, *string data_type) {
        if (!exists data_type || data_type == "") {
            # no hint: infer the type from the Qore value (legacy behavior)
            return new Variant(value);
        }
        switch (data_type) {
            case "Boolean":     return new Variant(boolean(value));
            case "Byte":        return new Variant(UByte::valueOf(int(value)));
            case "UInt16":      return new Variant(UShort::valueOf(int(value)));
            case "UInt32":      return new Variant(UInteger::valueOf(int(value)));
            case "UInt64":      return new Variant(ULong::valueOf(int(value)));
            case "Int64":       return new Variant(int(value));
            case "Double":      return new Variant(float(value));
            case "String":      return new Variant(string(value));
            case "DateTime":    return new Variant(new DateTime(int(get_epoch_seconds(date(value))) * 1000));
            case "ByteString":  return new Variant(ByteString::of(binary(value)));
        }
        if (inlist(data_type, UnsupportedWriteHints)) {
            throw "OPCUA-TYPE-CONVERSION-ERROR", sprintf("the OPC UA write data type hint %y requires "
                "exact narrow/single-precision typing that the current client cannot construct without "
                "the Java conversion codec; the value would otherwise be widened and rejected by the "
                "server. Types requiring the codec: %y", data_type, UnsupportedWriteHints);
        }
        throw "OPCUA-TYPE-CONVERSION-ERROR", sprintf("unknown OPC UA write data type hint %y; supported "
            "hints: %y", data_type, keys SupportedWriteHints);
    }
}
}
