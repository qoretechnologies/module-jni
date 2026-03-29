/*  VisioTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.visio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Helper class for creating test Visio (.vsdx) files for the VisioDataProvider tests.
 *
 * Since POI XDGF is read-only, this class builds minimal .vsdx files using raw OPC/XML
 * package structure (a .vsdx file is a ZIP archive with specific XML content).
 */
public class VisioTestHelper {

    private static final String CONTENT_TYPES_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
        "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
        "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
        "  <Override PartName=\"/visio/document.xml\" ContentType=\"application/vnd.ms-visio.drawing.main+xml\"/>\n" +
        "  <Override PartName=\"/visio/pages/pages.xml\" ContentType=\"application/vnd.ms-visio.pages+xml\"/>\n" +
        "  <Override PartName=\"/visio/pages/page1.xml\" ContentType=\"application/vnd.ms-visio.page+xml\"/>\n" +
        "</Types>";

    private static final String ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.microsoft.com/visio/2010/relationships/document\" " +
        "Target=\"visio/document.xml\"/>\n" +
        "</Relationships>";

    private static final String DOCUMENT_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<VisioDocument xmlns=\"http://schemas.microsoft.com/office/visio/2012/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
        "  <DocumentSettings/>\n" +
        "</VisioDocument>";

    private static final String DOCUMENT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.microsoft.com/visio/2010/relationships/pages\" " +
        "Target=\"pages/pages.xml\"/>\n" +
        "</Relationships>";

    /**
     * Creates a simple Visio document with one page containing text shapes.
     *
     * @param path The path to create the file at
     */
    public static void createSimpleVisio(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            writeSimpleVisio(fos, new String[]{"Hello World", "Sample Shape"}, "Page-1");
        }
    }

    /**
     * Creates a simple Visio document and returns the bytes.
     *
     * @return The document as a byte array
     */
    public static byte[] createSimpleVisioBytes() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeSimpleVisio(baos, new String[]{"Hello World", "Sample Shape"}, "Page-1");
            return baos.toByteArray();
        }
    }

    /**
     * Creates a Visio document with multiple pages.
     *
     * @param path The path to create the file at
     */
    public static void createMultiPageVisio(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            writeMultiPageVisio(fos);
        }
    }

    /**
     * Creates an empty Visio document with one page and no shapes.
     *
     * @param path The path to create the file at
     */
    public static void createEmptyVisio(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            writeSimpleVisio(fos, new String[]{}, "Page-1");
        }
    }

    /**
     * Creates a Visio document with special characters in shape text.
     *
     * @param path The path to create the file at
     */
    public static void createSpecialCharsVisio(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            writeSimpleVisio(fos, new String[]{
                "Caf\u00e9 &amp; Restaurant",
                "Price: \u20ac100",
                "Tokyo \u6771\u4eac"
            }, "Special-Page");
        }
    }

    private static void writeSimpleVisio(OutputStream out, String[] shapeTexts, String pageName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML);

            // _rels/.rels
            addZipEntry(zos, "_rels/.rels", ROOT_RELS);

            // visio/document.xml
            addZipEntry(zos, "visio/document.xml", DOCUMENT_XML);

            // visio/_rels/document.xml.rels
            addZipEntry(zos, "visio/_rels/document.xml.rels", DOCUMENT_RELS);

            // visio/pages/pages.xml
            addZipEntry(zos, "visio/pages/pages.xml", buildPagesXml(new String[]{pageName}));

            // visio/pages/_rels/pages.xml.rels
            addZipEntry(zos, "visio/pages/_rels/pages.xml.rels", buildPagesRels(1));

            // visio/pages/page1.xml
            addZipEntry(zos, "visio/pages/page1.xml", buildPageXml(shapeTexts));
        }
    }

    private static void writeMultiPageVisio(OutputStream out) throws IOException {
        String[] pageNames = {"Overview", "Details", "Summary"};
        String[][] pageTexts = {
            {"Network Diagram", "Server A", "Server B"},
            {"Connection Details", "Port 8080", "Protocol TCP"},
            {"Summary", "Total: 2 servers"}
        };

        String contentTypes =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
            "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
            "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
            "  <Override PartName=\"/visio/document.xml\" ContentType=\"application/vnd.ms-visio.drawing.main+xml\"/>\n" +
            "  <Override PartName=\"/visio/pages/pages.xml\" ContentType=\"application/vnd.ms-visio.pages+xml\"/>\n" +
            "  <Override PartName=\"/visio/pages/page1.xml\" ContentType=\"application/vnd.ms-visio.page+xml\"/>\n" +
            "  <Override PartName=\"/visio/pages/page2.xml\" ContentType=\"application/vnd.ms-visio.page+xml\"/>\n" +
            "  <Override PartName=\"/visio/pages/page3.xml\" ContentType=\"application/vnd.ms-visio.page+xml\"/>\n" +
            "</Types>";

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            addZipEntry(zos, "[Content_Types].xml", contentTypes);
            addZipEntry(zos, "_rels/.rels", ROOT_RELS);
            addZipEntry(zos, "visio/document.xml", DOCUMENT_XML);
            addZipEntry(zos, "visio/_rels/document.xml.rels", DOCUMENT_RELS);
            addZipEntry(zos, "visio/pages/pages.xml", buildPagesXml(pageNames));
            addZipEntry(zos, "visio/pages/_rels/pages.xml.rels", buildPagesRels(pageNames.length));

            for (int i = 0; i < pageNames.length; i++) {
                addZipEntry(zos, "visio/pages/page" + (i + 1) + ".xml", buildPageXml(pageTexts[i]));
            }
        }
    }

    private static String buildPagesXml(String[] pageNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Pages xmlns=\"http://schemas.microsoft.com/office/visio/2012/main\" ");
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
        for (int i = 0; i < pageNames.length; i++) {
            sb.append("  <Page ID=\"").append(i).append("\" Name=\"").append(escapeXml(pageNames[i]));
            sb.append("\" NameU=\"").append(escapeXml(pageNames[i])).append("\">\n");
            sb.append("    <Rel r:id=\"rId").append(i + 1).append("\"/>\n");
            sb.append("  </Page>\n");
        }
        sb.append("</Pages>");
        return sb.toString();
    }

    private static String buildPagesRels(int pageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
        for (int i = 0; i < pageCount; i++) {
            sb.append("  <Relationship Id=\"rId").append(i + 1);
            sb.append("\" Type=\"http://schemas.microsoft.com/visio/2010/relationships/page\" ");
            sb.append("Target=\"page").append(i + 1).append(".xml\"/>\n");
        }
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static String buildPageXml(String[] shapeTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<PageContents xmlns=\"http://schemas.microsoft.com/office/visio/2012/main\" ");
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
        // Shapes must be wrapped in a <Shapes> container element per the Visio 2012 XML schema
        sb.append("  <Shapes>\n");
        for (int i = 0; i < shapeTexts.length; i++) {
            sb.append("    <Shape ID=\"").append(i + 1).append("\" Type=\"Shape\" NameU=\"Shape.");
            sb.append(i + 1).append("\">\n");
            sb.append("      <Cell N=\"PinX\" V=\"").append(i + 1).append("\"/>\n");
            sb.append("      <Cell N=\"PinY\" V=\"").append(i + 1).append("\"/>\n");
            sb.append("      <Cell N=\"Width\" V=\"2\"/>\n");
            sb.append("      <Cell N=\"Height\" V=\"1\"/>\n");
            sb.append("      <Text>").append(escapeXml(shapeTexts[i])).append("</Text>\n");
            sb.append("    </Shape>\n");
        }
        sb.append("  </Shapes>\n");
        sb.append("</PageContents>");
        return sb.toString();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }
}
