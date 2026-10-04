/*  OdsSheetReader.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.ods;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Streams the rows of a table (worksheet) of an ODF spreadsheet.
 *
 * The document's content.xml is pulled with StAX: a pass over a table yields its rows one row element at a time, so
 * the memory used does not depend on the number of rows.  A row element repeated with table:number-rows-repeated
 * and a cell repeated with table:number-columns-repeated are kept as one row run and one cell run, so the runs of
 * empty rows and columns that spreadsheet applications write to the end of a sheet are not expanded.
 *
 * The tables, rows, cells, and cell values have the semantics of the ODFDOM document model that the record reader
 * used before it streamed (see OdsCell).
 *
 * The document is read from a file; an input stream is first copied to a temporary file, which the platform deletes
 * when it is opened or, where open files cannot be deleted, when the reader is closed.
 */
final class OdsSheetReader implements Closeable {
    static final String NS_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    static final String NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    static final String NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    static final String NS_MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";

    private static final String CONTENT = "content.xml";
    private static final String TEMP_PREFIX = "qore-ods-";

    // the document; null once closed
    private ZipFile zip;
    private final XMLInputFactory factory = newXMLInputFactory();
    // the 0-based position of the selected table among the tables of the spreadsheet
    private int table_index = -1;
    // the number of columns of the selected table, as OdfTable.getColumnCount() gives it
    private int column_count = 0;

    private OdsSheetReader(ZipFile zip) {
        this.zip = zip;
    }

    /**
     * Opens the given table of a spreadsheet in an input stream; the stream is read to its end
     *
     * @param in the document data
     * @param sheet_name the table name or 0-based index as a string; null or empty for the first table
     */
    static OdsSheetReader open(InputStream in, String sheet_name) throws IOException, XMLStreamException {
        return open(openZip(in), sheet_name);
    }

    /**
     * Opens the given table of a spreadsheet file
     *
     * @param file the document file
     * @param sheet_name the table name or 0-based index as a string; null or empty for the first table
     */
    static OdsSheetReader open(File file, String sheet_name) throws IOException, XMLStreamException {
        return open(new ZipFile(file), sheet_name);
    }

    private static OdsSheetReader open(ZipFile zip, String sheet_name) throws IOException, XMLStreamException {
        OdsSheetReader rv = new OdsSheetReader(zip);
        try {
            rv.checkMediaType();
            rv.selectTable(sheet_name);
            return rv;
        } catch (Throwable t) {
            try {
                rv.close();
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
    }

    /**
     * Returns the names of the tables of a spreadsheet in an input stream; the stream is read to its end
     */
    static ArrayList<String> getSheetNames(InputStream in) throws IOException, XMLStreamException {
        try (OdsSheetReader reader = new OdsSheetReader(openZip(in))) {
            reader.checkMediaType();
            return reader.getTableNames();
        }
    }

    /**
     * Returns the names of the tables of a spreadsheet file
     */
    static ArrayList<String> getSheetNames(File file) throws IOException, XMLStreamException {
        try (OdsSheetReader reader = new OdsSheetReader(new ZipFile(file))) {
            reader.checkMediaType();
            return reader.getTableNames();
        }
    }

    /**
     * Copies an input stream to a temporary file and opens it; the file is deleted when it is opened or, on
     * platforms where open files cannot be deleted, when it is closed
     */
    private static ZipFile openZip(InputStream in) throws IOException {
        Path tmp = Files.createTempFile(TEMP_PREFIX, ".ods");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                in.transferTo(out);
            }
            return new ZipFile(tmp.toFile(), ZipFile.OPEN_READ | ZipFile.OPEN_DELETE);
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            throw t;
        }
    }

    private static XMLInputFactory newXMLInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    /**
     * Returns the number of columns of the selected table, as OdfTable.getColumnCount() gives it
     */
    int getColumnCount() {
        return column_count;
    }

    /**
     * Ensures that the document is a spreadsheet, as loading it as an OdfSpreadsheetDocument does: its media type is
     * that of the "mimetype" file, or that of the root document in the manifest if there is no such file
     */
    private void checkMediaType() throws IOException, XMLStreamException {
        String media_type = null;
        ZipEntry entry = zip.getEntry("mimetype");
        if (entry != null) {
            try (InputStream in = zip.getInputStream(entry)) {
                media_type = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        if (media_type == null || media_type.isEmpty()) {
            media_type = getManifestMediaType();
        }
        if (!"application/vnd.oasis.opendocument.spreadsheet".equals(media_type)
                && !"application/vnd.oasis.opendocument.spreadsheet-template".equals(media_type)) {
            throw new IllegalArgumentException(String.format("the document is not an ODF spreadsheet; its media "
                + "type is %s", media_type == null ? "unknown" : "\"" + media_type + "\""));
        }
    }

    private String getManifestMediaType() throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("META-INF/manifest.xml");
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            XMLStreamReader xr = factory.createXMLStreamReader(in);
            try {
                while (xr.hasNext()) {
                    if (xr.next() == XMLStreamConstants.START_ELEMENT && NS_MANIFEST.equals(xr.getNamespaceURI())
                            && "file-entry".equals(xr.getLocalName())
                            && "/".equals(xr.getAttributeValue(NS_MANIFEST, "full-path"))) {
                        return xr.getAttributeValue(NS_MANIFEST, "media-type");
                    }
                }
                return null;
            } finally {
                xr.close();
            }
        }
    }

    /**
     * Selects the table as OdfDocument.getTableByName() and getTableList() do: by name, else by 0-based index
     */
    private void selectTable(String sheet_name) throws IOException, XMLStreamException {
        int wanted_index = -1;
        boolean first = sheet_name == null || sheet_name.isEmpty();
        if (!first) {
            try {
                wanted_index = Integer.parseInt(sheet_name);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        // the table at the wanted index and its column count, used if no table has the name
        int index_match = -1;
        int index_match_columns = 0;
        int count = 0;
        try (Content content = openContent()) {
            if (content.xr != null) {
                while (content.nextTable()) {
                    XMLStreamReader xr = content.xr;
                    int idx = count++;
                    if (first || sheet_name.equals(xr.getAttributeValue(NS_TABLE, "name"))) {
                        table_index = idx;
                        column_count = countColumns(xr);
                        return;
                    }
                    if (idx == wanted_index) {
                        index_match = idx;
                        index_match_columns = countColumns(xr);
                    } else {
                        skipElement(xr);
                    }
                }
            }
        }
        if (count == 0) {
            throw new RuntimeException("the spreadsheet has no worksheets");
        }
        if (index_match == -1) {
            throw new RuntimeException(String.format("sheet %s is unknown", sheet_name));
        }
        table_index = index_match;
        column_count = index_match_columns;
    }

    private ArrayList<String> getTableNames() throws IOException, XMLStreamException {
        ArrayList<String> rv = new ArrayList<String>();
        try (Content content = openContent()) {
            if (content.xr != null) {
                while (content.nextTable()) {
                    rv.add(content.xr.getAttributeValue(NS_TABLE, "name"));
                    skipElement(content.xr);
                }
            }
        }
        return rv;
    }

    /**
     * Counts the columns of a table as OdfTable.getColumnCount() does, reading the table to its end tag: the column
     * elements before and in the first row that is a child of the table, at any depth
     *
     * @param xr the reader, at the start of the table element
     */
    private static int countColumns(XMLStreamReader xr) throws XMLStreamException {
        int rv = 0;
        int depth = 0;
        while (true) {
            int ev = xr.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                ++depth;
                if (NS_TABLE.equals(xr.getNamespaceURI()) && "table-column".equals(xr.getLocalName())) {
                    String repeated = xr.getAttributeValue(NS_TABLE, "number-columns-repeated");
                    rv += (repeated != null && !repeated.isEmpty()) ? Integer.parseInt(repeated) : 1;
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (depth == 0) {
                    // the end of the table
                    return rv;
                }
                if (--depth == 0 && NS_TABLE.equals(xr.getNamespaceURI())
                        && "table-row".equals(xr.getLocalName())) {
                    // the end of the first row that is a child of the table
                    skipElement(xr);
                    return rv;
                }
            }
        }
    }

    /**
     * Skips the current element, which has just been started, up to and including its end tag
     */
    static void skipElement(XMLStreamReader xr) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int ev = xr.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                ++depth;
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                --depth;
            }
        }
    }

    private Content openContent() throws IOException, XMLStreamException {
        if (zip == null) {
            throw new IOException("the spreadsheet is closed");
        }
        ZipEntry entry = zip.getEntry(CONTENT);
        if (entry == null) {
            throw new IOException("the spreadsheet has no content.xml");
        }
        return new Content(zip.getInputStream(entry));
    }

    /**
     * Starts a new pass over the rows of the selected table; the caller must close it
     */
    RowPass openRows() throws IOException {
        try {
            Content content = openContent();
            try {
                int idx = 0;
                while (content.xr != null && content.nextTable()) {
                    if (idx++ == table_index) {
                        return new RowPass(content);
                    }
                    skipElement(content.xr);
                }
                throw new IOException("the spreadsheet's table has disappeared");
            } catch (Throwable t) {
                try {
                    content.close();
                } catch (Throwable t1) {
                    t.addSuppressed(t1);
                }
                throw t;
            }
        } catch (XMLStreamException e) {
            throw new IOException("invalid spreadsheet XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            ZipFile z = zip;
            zip = null;
            z.close();
        }
    }

    /**
     * A pass over content.xml, positioned at the content of the spreadsheet (the first child element of office:body)
     */
    private final class Content implements Closeable {
        private InputStream in;
        // null if the document has no spreadsheet content
        XMLStreamReader xr;

        Content(InputStream in) throws XMLStreamException, IOException {
            this.in = in;
            try {
                xr = factory.createXMLStreamReader(in);
                if (!toSpreadsheet()) {
                    xr.close();
                    xr = null;
                }
            } catch (Throwable t) {
                try {
                    close();
                } catch (Throwable t1) {
                    t.addSuppressed(t1);
                }
                throw t;
            }
        }

        /**
         * Moves to the start of the first child element of office:body, the first child of the root element with
         * that name, as OdfDocument does; returns false if there is none
         */
        private boolean toSpreadsheet() throws XMLStreamException {
            // the root element
            if (!toChildElement()) {
                return false;
            }
            while (true) {
                if (!toChildElement()) {
                    return false;
                }
                if (NS_OFFICE.equals(xr.getNamespaceURI()) && "body".equals(xr.getLocalName())) {
                    return toChildElement();
                }
                skipElement(xr);
            }
        }

        /**
         * Moves to the start of the next child element of the current element; returns false at the end of the
         * current element
         */
        private boolean toChildElement() throws XMLStreamException {
            while (xr.hasNext()) {
                int ev = xr.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    return true;
                }
                if (ev == XMLStreamConstants.END_ELEMENT) {
                    return false;
                }
            }
            return false;
        }

        /**
         * Moves to the start of the next table:table child of the spreadsheet content, skipping other elements;
         * returns false at the end of the content
         */
        boolean nextTable() throws XMLStreamException {
            while (toChildElement()) {
                if (NS_TABLE.equals(xr.getNamespaceURI()) && "table".equals(xr.getLocalName())) {
                    return true;
                }
                skipElement(xr);
            }
            return false;
        }

        @Override
        public void close() throws IOException {
            Throwable error = null;
            if (xr != null) {
                try {
                    xr.close();
                } catch (Throwable t) {
                    error = t;
                }
                xr = null;
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable t) {
                    if (error == null) {
                        error = t;
                    } else {
                        error.addSuppressed(t);
                    }
                }
                in = null;
            }
            if (error != null) {
                if (error instanceof IOException) {
                    throw (IOException)error;
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException)error;
                }
                if (error instanceof Error) {
                    throw (Error)error;
                }
                throw new IOException(error);
            }
        }
    }

    /**
     * A run of identical rows: a row element and the number of rows it represents
     */
    static final class RowRun {
        // the 0-based number of the first row of the run
        final int start;
        // the number of rows in the run
        final int count;
        // true if a table:table-cell of the row has content, as the record reader determined it with the DOM
        boolean has_content = false;
        // the cell runs of the row: the 0-based column of each and the number of columns it covers
        private int[] col_starts = new int[8];
        private int[] col_counts = new int[8];
        // the cell of each cell run; null for a cell without content
        private OdsCell[] cells = new OdsCell[8];
        private int size = 0;

        RowRun(int start, int count) {
            this.start = start;
            this.count = count;
        }

        void addCells(int col_start, int col_count, OdsCell cell) {
            if (size == cells.length) {
                int n = size * 2;
                col_starts = Arrays.copyOf(col_starts, n);
                col_counts = Arrays.copyOf(col_counts, n);
                cells = Arrays.copyOf(cells, n);
            }
            col_starts[size] = col_start;
            col_counts[size] = col_count;
            cells[size] = cell;
            ++size;
        }

        /**
         * Returns the cell in the given 0-based column, or null if the row has no cell with content there
         */
        OdsCell getCell(int col) {
            // the last cell run starting at or before the column
            int lo = 0;
            int hi = size - 1;
            int found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (col_starts[mid] <= col) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (found == -1 || col >= col_starts[found] + col_counts[found]) {
                return null;
            }
            return cells[found];
        }
    }

    /**
     * One pass over the rows of the selected table, as row runs in order
     */
    final class RowPass implements Closeable {
        private Content content;
        // the 0-based number of the first row of the next run
        private int next_start = 0;
        // the depth of the reader in the table: in row groups, header rows, and other container elements
        private int depth = 0;

        private RowPass(Content content) {
            this.content = content;
        }

        /**
         * Returns the next row run of the table, or null at the end of the table
         *
         * The rows of the table are its table:table-row elements at any depth (in row groups and header rows), but
         * not those of tables nested in it, as the record reader determined them with the DOM.
         */
        RowRun next() throws IOException {
            if (content == null) {
                return null;
            }
            XMLStreamReader xr = content.xr;
            try {
                while (true) {
                    int ev = xr.next();
                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        if (NS_TABLE.equals(xr.getNamespaceURI())) {
                            String ln = xr.getLocalName();
                            if ("table-row".equals(ln)) {
                                return readRow(xr);
                            }
                            if ("table".equals(ln)) {
                                // the rows of a nested table are not rows of this table
                                skipElement(xr);
                                continue;
                            }
                        }
                        // descend into row groups, header rows, and other elements of the table
                        ++depth;
                    } else if (ev == XMLStreamConstants.END_ELEMENT) {
                        if (depth == 0) {
                            // the end of the table
                            close();
                            return null;
                        }
                        --depth;
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("invalid spreadsheet XML: " + e.getMessage(), e);
            }
        }

        /**
         * Reads a row element, at whose start the reader is, up to and including its end tag
         */
        private RowRun readRow(XMLStreamReader xr) throws XMLStreamException {
            String repeated = xr.getAttributeValue(NS_TABLE, "number-rows-repeated");
            int n = (repeated != null && !repeated.isEmpty()) ? Integer.parseInt(repeated) : 1;
            if (n < 1) {
                n = 1;
            }
            RowRun run = new RowRun(next_start, n);
            next_start += n;

            int col = 0;
            while (true) {
                int ev = xr.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String ns = xr.getNamespaceURI();
                    String ln = xr.getLocalName();
                    boolean table_ns = NS_TABLE.equals(ns);
                    // the cells of the row: table:table-cell and table:covered-table-cell
                    boolean cell = table_ns && ("table-cell".equals(ln) || "covered-table-cell".equals(ln));
                    // the content of the row was determined from its table-cell elements in any namespace
                    boolean content_cell = "table-cell".equals(ln);
                    if (!cell && !content_cell) {
                        skipElement(xr);
                        continue;
                    }
                    int cols = 1;
                    if (cell) {
                        String c_repeated = xr.getAttributeValue(NS_TABLE, "number-columns-repeated");
                        if (c_repeated != null && !c_repeated.isEmpty()) {
                            try {
                                cols = Math.max(1, Integer.parseInt(c_repeated));
                            } catch (NumberFormatException e) {
                                cols = 1;
                            }
                        }
                    }
                    OdsCell c = OdsCell.read(xr);
                    if (cell) {
                        run.addCells(col, cols, c.hasContent() ? c : null);
                        col += cols;
                    }
                    if (content_cell && c.hasContent()) {
                        run.has_content = true;
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    return run;
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                Content c = content;
                content = null;
                c.close();
            }
        }
    }
}
