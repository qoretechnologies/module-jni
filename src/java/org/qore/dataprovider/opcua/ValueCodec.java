/*  ValueCodec.java Copyright 2026 Qore Technologies, s.r.o.

    Generic OPC UA value codec (Epic A Phase 3).

    Converts Qore values to correctly-typed Milo Variant values and unwraps Milo values for reading.
    Running in compiled Java lets the codec construct narrow signed (SByte/Int16/Int32) and single-
    precision (Float) values that the Qore/JNI boundary would otherwise widen, and unwrap the OPC UA
    unsigned wrapper types to plain integers so Qore receives native values.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction. THE
    SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

/** Converts between Qore values and Milo Variant values for OPC UA reads and writes. */
public class ValueCodec {
    /**
     * Builds a Milo Variant carrying the value as the requested OPC UA built-in scalar type.
     *
     * @param value the value (as delivered from Qore: Long for ints, Double for floats, String,
     *     Boolean, or byte[] for ByteString)
     * @param dataType the OPC UA built-in type name
     * @return a Variant with the value constructed as the requested type
     * @throws IllegalArgumentException if the type name is not a supported scalar built-in type
     */
    public static Variant toVariant(Object value, String dataType) {
        if (value == null) {
            return new Variant(null);
        }
        switch (dataType) {
            case "Boolean":    return new Variant(toBoolean(value));
            case "SByte":      return new Variant(Byte.valueOf(((Number) value).byteValue()));
            case "Byte":       return new Variant(UByte.valueOf(((Number) value).shortValue()));
            case "Int16":      return new Variant(Short.valueOf(((Number) value).shortValue()));
            case "UInt16":     return new Variant(UShort.valueOf(((Number) value).intValue()));
            case "Int32":      return new Variant(Integer.valueOf(((Number) value).intValue()));
            case "UInt32":     return new Variant(UInteger.valueOf(((Number) value).longValue()));
            case "Int64":      return new Variant(Long.valueOf(((Number) value).longValue()));
            case "UInt64":     return new Variant(ULong.valueOf(((Number) value).longValue()));
            case "Float":      return new Variant(Float.valueOf(((Number) value).floatValue()));
            case "Double":     return new Variant(Double.valueOf(((Number) value).doubleValue()));
            case "String":     return new Variant(value.toString());
            case "ByteString": return new Variant(ByteString.of((byte[]) value));
            default:
                throw new IllegalArgumentException("unsupported OPC UA scalar data type: " + dataType);
        }
    }

    /** Returns @c true if the given type name is a scalar built-in type that {@link #toVariant} builds. */
    public static boolean isSupportedScalarType(String dataType) {
        switch (dataType) {
            case "Boolean": case "SByte": case "Byte": case "Int16": case "UInt16": case "Int32":
            case "UInt32": case "Int64": case "UInt64": case "Float": case "Double": case "String":
            case "ByteString":
                return true;
            default:
                return false;
        }
    }

    /**
     * Unwraps a Milo value for delivery to Qore: OPC UA unsigned wrapper types become plain integers
     * so Qore receives a native value instead of a Java object; other values are returned unchanged.
     */
    public static Object unwrap(Object value) {
        if (value instanceof UByte) {
            return (long) ((UByte) value).intValue();
        }
        if (value instanceof UShort) {
            return (long) ((UShort) value).intValue();
        }
        if (value instanceof UInteger) {
            return ((UInteger) value).longValue();
        }
        if (value instanceof ULong) {
            return ((ULong) value).longValue();
        }
        return value;
    }

    /** Unwraps the value carried by a Variant for delivery to Qore (see {@link #unwrap}). */
    public static Object readVariant(Variant variant) {
        if (variant == null) {
            return null;
        }
        return unwrap(variant.getValue());
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return ((Number) value).longValue() != 0;
    }
}
