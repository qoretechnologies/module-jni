/*  AvroTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import java.io.IOException;

/**
 * Helper class for creating test Avro data for the AvroDataProvider tests.
 */
public class AvroTestHelper {

    /**
     * Returns a simple Avro schema JSON string with fields: name (string), age (int),
     * and email (nullable string).
     *
     * @return the simple schema as a JSON string
     */
    public static String getSimpleSchema() {
        return "{"
            + "\"type\": \"record\","
            + "\"name\": \"Person\","
            + "\"namespace\": \"org.qore.test\","
            + "\"fields\": ["
            + "  {\"name\": \"name\", \"type\": \"string\"},"
            + "  {\"name\": \"age\", \"type\": \"int\"},"
            + "  {\"name\": \"email\", \"type\": [\"null\", \"string\"], \"default\": null}"
            + "]"
            + "}";
    }

    /**
     * Returns a nested Avro schema JSON string with a nested record (address with
     * street/city/zip), an array field (tags: array of string), and nullable fields.
     *
     * @return the nested schema as a JSON string
     */
    public static String getNestedSchema() {
        return "{"
            + "\"type\": \"record\","
            + "\"name\": \"Employee\","
            + "\"namespace\": \"org.qore.test\","
            + "\"fields\": ["
            + "  {\"name\": \"name\", \"type\": \"string\"},"
            + "  {\"name\": \"age\", \"type\": \"int\"},"
            + "  {\"name\": \"email\", \"type\": [\"null\", \"string\"], \"default\": null},"
            + "  {\"name\": \"address\", \"type\": {"
            + "    \"type\": \"record\","
            + "    \"name\": \"Address\","
            + "    \"fields\": ["
            + "      {\"name\": \"street\", \"type\": \"string\"},"
            + "      {\"name\": \"city\", \"type\": \"string\"},"
            + "      {\"name\": \"zip\", \"type\": \"string\"}"
            + "    ]"
            + "  }},"
            + "  {\"name\": \"tags\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},"
            + "  {\"name\": \"notes\", \"type\": [\"null\", \"string\"], \"default\": null}"
            + "]"
            + "}";
    }

    /**
     * Creates a small Avro container file with 3 records using the simple schema
     * and returns the data as a byte array.
     *
     * @return The Avro container file data as bytes
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createTestAvroData() throws IOException {
        Schema schema = new Schema.Parser().parse(getSimpleSchema());
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {

            dataFileWriter.create(schema, out);

            // Record 1: Alice with email
            GenericRecord alice = new GenericData.Record(schema);
            alice.put("name", "Alice Smith");
            alice.put("age", 30);
            alice.put("email", "alice@example.com");
            dataFileWriter.append(alice);

            // Record 2: Bob without email (null)
            GenericRecord bob = new GenericData.Record(schema);
            bob.put("name", "Bob Johnson");
            bob.put("age", 25);
            bob.put("email", null);
            dataFileWriter.append(bob);

            // Record 3: Charlie with email
            GenericRecord charlie = new GenericData.Record(schema);
            charlie.put("name", "Charlie Brown");
            charlie.put("age", 35);
            charlie.put("email", "charlie@example.com");
            dataFileWriter.append(charlie);

            dataFileWriter.flush();
            return out.toByteArray();
        }
    }
}
