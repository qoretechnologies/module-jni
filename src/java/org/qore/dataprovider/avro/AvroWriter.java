/*  AvroWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qore.jni.Hash;

/**
 * Writer for creating Apache Avro data container files.
 * Accumulates records and writes them as an Avro container file.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class AvroWriter implements Closeable {
    private Schema schema;
    private ArrayList<GenericRecord> records = new ArrayList<>();

    /**
     * Creates a new AvroWriter with the given Avro schema.
     *
     * @param schemaJson The Avro schema as a JSON string
     */
    public AvroWriter(String schemaJson) {
        this.schema = new Schema.Parser().parse(schemaJson);
    }

    /**
     * Writes a record from a Hash to the internal record list.
     *
     * @param data The record data as a Hash
     */
    public void writeRecord(Hash data) {
        GenericRecord record = hashToGenericRecord(data, schema);
        records.add(record);
    }

    /**
     * Recursively converts a Hash to a GenericRecord using the given schema.
     *
     * @param data The Hash data to convert
     * @param schema The Avro schema for the record
     * @return A GenericRecord containing the data
     */
    @SuppressWarnings("unchecked")
    public static GenericRecord hashToGenericRecord(Hash data, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            Object value = data.get(field.name());
            record.put(field.name(), convertToAvroValue(value, field.schema()));
        }
        return record;
    }

    /**
     * Recursively converts a Java value to its Avro equivalent.
     *
     * @param value The Java value to convert
     * @param schema The target Avro schema
     * @return The converted Avro value
     */
    @SuppressWarnings("unchecked")
    private static Object convertToAvroValue(Object value, Schema schema) {
        if (value == null) {
            return null;
        }

        switch (schema.getType()) {
            case STRING:
                return value.toString();

            case INT:
                if (value instanceof Integer) {
                    return (Integer) value;
                }
                if (value instanceof Long) {
                    return ((Long) value).intValue();
                }
                return ((Number) value).intValue();

            case LONG:
                if (value instanceof Long) {
                    return (Long) value;
                }
                return ((Number) value).longValue();

            case FLOAT:
                if (value instanceof Float) {
                    return (Float) value;
                }
                if (value instanceof Double) {
                    return ((Double) value).floatValue();
                }
                return ((Number) value).floatValue();

            case DOUBLE:
                if (value instanceof Double) {
                    return (Double) value;
                }
                return ((Number) value).doubleValue();

            case BOOLEAN:
                return (Boolean) value;

            case BYTES:
                if (value instanceof byte[]) {
                    return ByteBuffer.wrap((byte[]) value);
                }
                if (value instanceof ByteBuffer) {
                    return (ByteBuffer) value;
                }
                return value;

            case RECORD:
                if (value instanceof Hash) {
                    return hashToGenericRecord((Hash) value, schema);
                }
                if (value instanceof Map) {
                    Hash h = new Hash();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        h.put(entry.getKey().toString(), entry.getValue());
                    }
                    return hashToGenericRecord(h, schema);
                }
                return value;

            case ARRAY:
                Schema elementSchema = schema.getElementType();
                Iterable<?> iterable = toIterable(value);
                ArrayList<Object> list = new ArrayList<>();
                for (Object item : iterable) {
                    list.add(convertToAvroValue(item, elementSchema));
                }
                return list;

            case MAP:
                Schema valueSchema = schema.getValueType();
                Map<?, ?> inputMap;
                if (value instanceof Hash) {
                    inputMap = (Hash) value;
                } else if (value instanceof Map) {
                    inputMap = (Map<?, ?>) value;
                } else {
                    return value;
                }
                HashMap<String, Object> resultMap = new HashMap<>();
                for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                    resultMap.put(entry.getKey().toString(), convertToAvroValue(entry.getValue(), valueSchema));
                }
                return resultMap;

            case UNION:
                // Handle nullable unions: check for null first
                List<Schema> types = schema.getTypes();
                if (value == null) {
                    return null;
                }
                // Find the non-null branch and convert
                for (Schema unionType : types) {
                    if (unionType.getType() == Schema.Type.NULL) {
                        continue;
                    }
                    return convertToAvroValue(value, unionType);
                }
                return value;

            case ENUM:
                return new GenericData.EnumSymbol(schema, value.toString());

            case FIXED:
                if (value instanceof byte[]) {
                    return new GenericData.Fixed(schema, (byte[]) value);
                }
                return value;

            case NULL:
                return null;

            default:
                return value;
        }
    }

    /**
     * Converts an object to an iterable of Objects.
     * Handles both Object[] arrays (from Qore lists) and java.util.List.
     *
     * @param obj The object to convert
     * @return An iterable of objects
     */
    private static Iterable<?> toIterable(Object obj) {
        if (obj instanceof Object[]) {
            return Arrays.asList((Object[]) obj);
        } else if (obj instanceof Collection) {
            return (Collection<?>) obj;
        }
        // Single item
        return Collections.singletonList(obj);
    }

    /**
     * Writes all accumulated records to a Java OutputStream.
     *
     * @param stream The output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(OutputStream stream) throws IOException {
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        try (DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
            dataFileWriter.create(schema, stream);
            for (GenericRecord record : records) {
                dataFileWriter.append(record);
            }
        }
    }

    /**
     * Writes all accumulated records to a Qore OutputStream.
     *
     * @param stream The Qore output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns all accumulated records as an Avro container file byte array.
     *
     * @return The Avro container file data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeToStream(out);
            return out.toByteArray();
        }
    }

    /**
     * Returns the Avro schema as a JSON string.
     *
     * @return the schema JSON
     */
    public String getSchemaJson() {
        return schema.toString(true);
    }

    /**
     * Returns the number of records accumulated.
     *
     * @return the record count
     */
    public int getRecordCount() {
        return records.size();
    }

    /**
     * Closes the writer and releases resources.
     */
    @Override
    public void close() throws IOException {
        records = null;
        schema = null;
    }
}
