/*  AvroIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.util.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading Apache Avro data container files.
 * Uses DataFileReader with GenericDatumReader to read Avro container files.
 * The schema is read from the Avro container file header.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class AvroIterator extends qore.Qore.AbstractIterator implements Closeable {
    private DataFileReader<GenericRecord> reader;
    private GenericRecord currentRecord = null;
    private Hash currentValue = null;
    private long count = 0;

    /**
     * Creates an AvroIterator from a Java InputStream.
     * Reads all bytes from the stream first since DataFileReader needs SeekableInput.
     *
     * @param stream The input stream containing Avro data
     * @throws Throwable if an error occurs reading or parsing Avro data
     */
    public AvroIterator(InputStream stream) throws Throwable {
        try {
            byte[] data = readAllBytes(stream);
            initReader(data);
        } finally {
            stream.close();
        }
    }

    /**
     * Creates an AvroIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing Avro data
     * @throws Throwable if an error occurs reading or parsing Avro data
     */
    public AvroIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates an AvroIterator from a file path.
     *
     * @param path The path to the Avro container file
     * @throws Throwable if an error occurs reading or parsing Avro data
     */
    public AvroIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)));
    }

    /**
     * Creates an AvroIterator from raw byte data.
     *
     * @param data The Avro container file data as bytes
     * @throws Throwable if an error occurs reading or parsing Avro data
     */
    public AvroIterator(byte[] data) throws Throwable {
        initReader(data);
    }

    /**
     * Initializes the DataFileReader from a byte array.
     *
     * @param data The Avro container file data as bytes
     * @throws IOException if an error occurs reading Avro data
     */
    private void initReader(byte[] data) throws IOException {
        SeekableByteArrayInput input = new SeekableByteArrayInput(data);
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        reader = new DataFileReader<>(input, datumReader);
    }

    /**
     * Reads all bytes from an InputStream into a byte array.
     *
     * @param stream The input stream to read
     * @return All bytes from the stream
     * @throws IOException if an I/O error occurs
     */
    private static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Returns the Avro schema as a JSON string.
     *
     * @return the Avro schema JSON, or null if the reader is closed
     */
    public String getSchema() {
        if (reader == null) {
            return null;
        }
        return reader.getSchema().toString(true);
    }

    /**
     * Returns the number of records iterated so far.
     *
     * @return the count of iterated records
     */
    public long getCount() {
        return count;
    }

    @Override
    public boolean next() {
        if (reader == null) {
            currentValue = null;
            return false;
        }

        if (!reader.hasNext()) {
            currentRecord = null;
            currentValue = null;
            return false;
        }

        try {
            currentRecord = reader.next(currentRecord);
        } catch (IOException e) {
            throw new RuntimeException("Error reading Avro record: " + e.getMessage(), e);
        }
        currentValue = genericRecordToHash(currentRecord);
        ++count;
        return true;
    }

    @Override
    public Hash getValue() throws QoreException {
        if (currentValue == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return currentValue;
    }

    @Override
    public boolean valid() {
        return currentValue != null;
    }

    /**
     * Recursively converts a GenericRecord to a Hash.
     *
     * @param record The GenericRecord to convert
     * @return A Hash containing the record fields
     */
    public static Hash genericRecordToHash(GenericRecord record) {
        Hash h = new Hash();
        Schema schema = record.getSchema();
        for (Schema.Field field : schema.getFields()) {
            Object value = record.get(field.name());
            h.put(field.name(), convertAvroValue(value, field.schema()));
        }
        return h;
    }

    /**
     * Recursively converts an Avro value to its Java equivalent.
     *
     * @param value The Avro value to convert
     * @param schema The Avro schema for the value
     * @return The converted Java value
     */
    @SuppressWarnings("unchecked")
    private static Object convertAvroValue(Object value, Schema schema) {
        if (value == null) {
            return null;
        }

        switch (schema.getType()) {
            case STRING:
            case ENUM:
                return value.toString();

            case INT:
                if (value instanceof Integer) {
                    return (Integer) value;
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
                return ((Number) value).floatValue();

            case DOUBLE:
                if (value instanceof Double) {
                    return (Double) value;
                }
                return ((Number) value).doubleValue();

            case BOOLEAN:
                return (Boolean) value;

            case BYTES:
                if (value instanceof ByteBuffer) {
                    ByteBuffer bb = (ByteBuffer) value;
                    byte[] bytes = new byte[bb.remaining()];
                    bb.get(bytes);
                    return bytes;
                }
                return value;

            case FIXED:
                if (value instanceof GenericData.Fixed) {
                    return ((GenericData.Fixed) value).bytes().clone();
                }
                return value;

            case NULL:
                return null;

            case RECORD:
                if (value instanceof GenericRecord) {
                    return genericRecordToHash((GenericRecord) value);
                }
                return value;

            case ARRAY:
                Schema elementSchema = schema.getElementType();
                Collection<?> collection;
                if (value instanceof Collection) {
                    collection = (Collection<?>) value;
                } else {
                    return value;
                }
                ArrayList<Object> list = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    list.add(convertAvroValue(item, elementSchema));
                }
                return list;

            case MAP:
                Schema valueSchema = schema.getValueType();
                Map<?, ?> map = (Map<?, ?>) value;
                Hash mapHash = new Hash();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    mapHash.put(entry.getKey().toString(), convertAvroValue(entry.getValue(), valueSchema));
                }
                return mapHash;

            case UNION:
                List<Schema> types = schema.getTypes();
                // Find the non-null branch and convert
                for (Schema unionType : types) {
                    if (unionType.getType() == Schema.Type.NULL) {
                        continue;
                    }
                    // Try to match the value to this branch
                    if (isMatchingType(value, unionType)) {
                        return convertAvroValue(value, unionType);
                    }
                }
                // Fallback: try the first non-null type
                for (Schema unionType : types) {
                    if (unionType.getType() != Schema.Type.NULL) {
                        return convertAvroValue(value, unionType);
                    }
                }
                return value;

            default:
                return value;
        }
    }

    /**
     * Checks if a value matches a given Avro schema type.
     *
     * @param value The value to check
     * @param schema The schema to match against
     * @return true if the value matches the schema type
     */
    private static boolean isMatchingType(Object value, Schema schema) {
        switch (schema.getType()) {
            case STRING:
                return value instanceof CharSequence || value instanceof Utf8;
            case INT:
                return value instanceof Integer;
            case LONG:
                return value instanceof Long;
            case FLOAT:
                return value instanceof Float;
            case DOUBLE:
                return value instanceof Double;
            case BOOLEAN:
                return value instanceof Boolean;
            case BYTES:
                return value instanceof ByteBuffer;
            case FIXED:
                return value instanceof GenericData.Fixed;
            case RECORD:
                return value instanceof GenericRecord;
            case ARRAY:
                return value instanceof Collection;
            case MAP:
                return value instanceof Map;
            case ENUM:
                return value instanceof GenericData.EnumSymbol;
            case NULL:
                return value == null;
            default:
                return false;
        }
    }

    /**
     * Closes the iterator and releases resources.
     */
    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
        currentRecord = null;
        currentValue = null;
    }
}
