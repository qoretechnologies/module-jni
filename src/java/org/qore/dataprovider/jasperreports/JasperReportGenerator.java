/*  JasperReportGenerator.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.jasperreports;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.qore.jni.Hash;

/**
 * Wraps JasperReports to compile templates and generate reports in various formats.
 * Accepts JRXML source or pre-compiled .jasper bytes and produces output as PDF, XLSX,
 * HTML, or CSV.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class JasperReportGenerator implements Closeable {
    private JasperReport jasperReport;
    private ArrayList<Hash> records = new ArrayList<>();
    private HashMap<String, Object> parameters = new HashMap<>();

    /**
     * Creates a new JasperReportGenerator from template data.
     * Auto-detects whether the data is a compiled .jasper file (serialized Java object)
     * or JRXML source that needs compilation.
     *
     * @param templateData JRXML source bytes or compiled .jasper bytes
     * @throws JRException if compilation or deserialization fails
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the serialized class is not found
     */
    public JasperReportGenerator(byte[] templateData) throws JRException, IOException, ClassNotFoundException {
        if (templateData.length >= 2
                && (templateData[0] & 0xFF) == 0xAC
                && (templateData[1] & 0xFF) == 0xED) {
            // Compiled .jasper file (serialized Java object)
            try (ByteArrayInputStream bais = new ByteArrayInputStream(templateData);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                jasperReport = (JasperReport) ois.readObject();
            }
        } else {
            // JRXML source - compile it
            try (ByteArrayInputStream bais = new ByteArrayInputStream(templateData)) {
                jasperReport = JasperCompileManager.compileReport(bais);
            } catch (JRException e) {
                // Print full stack trace to stderr for debugging
                System.err.println("=== JasperReports JRXML compilation error ===");
                e.printStackTrace(System.err);
                System.err.println("=== End JasperReports error ===");
                throw new RuntimeException("Failed to compile JRXML template ("
                    + templateData.length + " bytes): " + e.getMessage(), e);
            }
        }
    }

    /**
     * Adds a data record to the internal list for report filling.
     *
     * @param data The record data as a Hash
     */
    public void addRecord(Hash data) {
        records.add(data);
    }

    /**
     * Sets a report parameter.
     *
     * @param name The parameter name
     * @param value The parameter value
     */
    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }

    /**
     * Fills the report and exports it to the given format, returning the result as bytes.
     *
     * @param format The output format: "pdf", "xlsx", "html", or "csv"
     * @return The exported report as a byte array
     * @throws JRException if report filling or exporting fails
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the format is not supported
     */
    public byte[] generate(String format) throws JRException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            generateToStream(format, out);
            return out.toByteArray();
        }
    }

    /**
     * Fills the report and exports it directly to a Java OutputStream.
     *
     * @param format The output format: "pdf", "xlsx", "html", or "csv"
     * @param stream The output stream to write to
     * @throws JRException if report filling or exporting fails
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the format is not supported
     */
    public void generateToStream(String format, OutputStream stream) throws JRException, IOException {
        HashListDataSource dataSource = new HashListDataSource(records);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        String fmt = format.toLowerCase();
        switch (fmt) {
            case "pdf": {
                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));
                exporter.exportReport();
                break;
            }
            case "xlsx": {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));
                exporter.exportReport();
                break;
            }
            case "html": {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(stream));
                exporter.exportReport();
                break;
            }
            case "csv": {
                JRCsvExporter exporter = new JRCsvExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(stream));
                exporter.exportReport();
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported export format: " + format
                    + "; supported formats are: pdf, xlsx, html, csv");
        }
    }

    /**
     * Fills the report and exports it directly to a Qore OutputStream.
     *
     * @param format The output format: "pdf", "xlsx", "html", or "csv"
     * @param stream The Qore output stream to write to
     * @throws JRException if report filling or exporting fails
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the format is not supported
     */
    public void generateToStream(String format, qore.Qore.OutputStream stream) throws JRException, IOException {
        generateToStream(format, new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Closes the generator and releases resources.
     */
    @Override
    public void close() throws IOException {
        jasperReport = null;
        records = null;
        parameters = null;
    }

    /**
     * JRDataSource implementation that iterates over an ArrayList of Hash records.
     * Each Hash represents a row of data, with keys corresponding to field names.
     */
    private static class HashListDataSource implements JRDataSource {
        private final ArrayList<Hash> data;
        private int index = -1;

        /**
         * Creates a new HashListDataSource.
         *
         * @param data The list of Hash records
         */
        HashListDataSource(ArrayList<Hash> data) {
            this.data = data;
        }

        /**
         * Advances to the next record.
         *
         * @return true if there is a next record, false otherwise
         */
        @Override
        public boolean next() {
            index++;
            return index < data.size();
        }

        /**
         * Returns the value of a field from the current record, with type coercion
         * to match the expected field type.
         *
         * @param jrField The field descriptor
         * @return The field value from the current Hash, coerced to the expected type
         */
        @Override
        public Object getFieldValue(JRField jrField) {
            Object value = data.get(index).get(jrField.getName());
            if (value == null) {
                return null;
            }
            // Coerce types to match what JasperReports expects
            Class<?> valueClass = jrField.getValueClass();
            if (valueClass == Integer.class && value instanceof Number) {
                return ((Number) value).intValue();
            } else if (valueClass == Long.class && value instanceof Number) {
                return ((Number) value).longValue();
            } else if (valueClass == Float.class && value instanceof Number) {
                return ((Number) value).floatValue();
            } else if (valueClass == Double.class && value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (valueClass == Short.class && value instanceof Number) {
                return ((Number) value).shortValue();
            } else if (valueClass == Byte.class && value instanceof Number) {
                return ((Number) value).byteValue();
            } else if (valueClass == java.math.BigDecimal.class && value instanceof Number) {
                return new java.math.BigDecimal(value.toString());
            } else if (valueClass == String.class && !(value instanceof String)) {
                return value.toString();
            }
            return value;
        }
    }
}
