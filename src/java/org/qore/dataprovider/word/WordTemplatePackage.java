// Copyright 2026 Qore Technologies, s.r.o.  MIT license.
package org.qore.dataprovider.word;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.w3c.dom.*;

/** Keeps original part contents, replacing only the main document when rendering. */
final class WordTemplatePackage {
    static final String MAIN = "word/document.xml";
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    static final String OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    static final int MAX_BYTES = 64 * 1024 * 1024;
    final Map<String, byte[]> parts = new LinkedHashMap<>();
    final Document document;

    WordTemplatePackage(byte[] input) throws Exception {
        require(input.length <= MAX_BYTES, "DOCX exceeds 64 MiB");
        int remaining = MAX_BYTES;
        try (ZipFile file = ZipFile.builder().setByteArray(input).get()) {
            Enumeration<ZipArchiveEntry> entries = file.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                require(parts.size() < 4096 && !parts.containsKey(name), "duplicate part or too many DOCX parts");
                require(entry.getSize() >= 0 && entry.getSize() <= remaining, "expanded DOCX exceeds 64 MiB");
                try (InputStream in = file.getInputStream(entry)) {
                    byte[] data = in.readNBytes((int)entry.getSize() + 1);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    require(data.length == entry.getSize() && crc.getValue() == entry.getCrc(), "invalid DOCX part");
                    remaining -= data.length;
                    parts.put(name, data);
                }
            }
        }
        Document types = parse(part("[Content_Types].xml"));
        require(is(types.getDocumentElement(), TYPES, "Types"), "invalid DOCX content types");
        boolean docx = false;
        for (Element type : children(types.getDocumentElement())) {
            String mime = type.getAttribute("ContentType").toLowerCase(Locale.ROOT);
            require(!mime.contains("macroenabled") && !mime.contains("vba") && !mime.contains("activex")
                && !mime.contains("oleobject") && !mime.contains("digital-signature"), "unsupported active or signed DOCX");
            if (is(type, TYPES, "Override") && ("/" + MAIN).equals(type.getAttribute("PartName"))) {
                docx = DOCX.equals(mime);
            }
        }
        require(docx, "expected a Word .docx document");
        boolean mainRel = false;
        for (Map.Entry<String, byte[]> entry : parts.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith(".rels")) {
                Document rels = parse(entry.getValue());
                require(is(rels.getDocumentElement(), REL, "Relationships"), "invalid DOCX relationships");
                for (Element rel : children(rels.getDocumentElement())) {
                    String target = rel.getAttribute("Target");
                    String mode = rel.getAttribute("TargetMode");
                    URI uri = new URI(target);
                    require(is(rel, REL, "Relationship") && (mode.isEmpty() || mode.equals("Internal"))
                        && !uri.isAbsolute() && uri.getRawAuthority() == null, "external relationship is unsupported");
                    String relType = rel.getAttribute("Type").toLowerCase(Locale.ROOT);
                    require(!relType.contains("vbaproject") && !relType.contains("activex")
                        && !relType.endsWith("/oleobject") && !relType.endsWith("/attachedtemplate"),
                        "unsupported active DOCX relationship");
                    int slash = name.lastIndexOf("_rels/");
                    require(slash >= 0, "invalid DOCX relationships part");
                    URI resolved = new URI("/" + name.substring(0, slash)).resolve(uri).normalize();
                    require(resolved.getRawPath().startsWith("/") && parts.containsKey(resolved.getRawPath().substring(1)),
                        "missing internal relationship target: " + target);
                    if (name.equals("_rels/.rels") && OFFICE.equals(rel.getAttribute("Type"))) {
                        mainRel = MAIN.equals(target) || ("/" + MAIN).equals(target);
                    }
                }
            } else if (name.startsWith("word/") && name.endsWith(".xml") && !MAIN.equals(name)) {
                Document xml = parse(entry.getValue());
                require(xml.getElementsByTagNameNS(W, "sdt").getLength() == 0,
                    "template controls outside the document body are unsupported");
                checkFields(xml);
            }
        }
        require(mainRel, "missing DOCX office document relationship");
        document = parse(part(MAIN));
        require(is(document.getDocumentElement(), W, "document")
            && document.getElementsByTagNameNS(W, "body").getLength() == 1, "invalid Word document body");
        checkFields(document);
    }

    private static void checkFields(Document xml) throws IOException {
        for (String name : Arrays.asList("instrText", "fldSimple", "altChunk", "object", "control", "subDoc")) {
            require(xml.getElementsByTagNameNS(W, name).getLength() == 0, "unsupported Word field or embedded content: " + name);
        }
    }

    private byte[] part(String name) throws IOException {
        require(parts.containsKey(name), "missing DOCX part: " + name);
        return parts.get(name);
    }

    private static Document parse(byte[] data) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void error(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
            @Override
            public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
        });
        return builder.parse(new ByteArrayInputStream(data));
    }

    static void require(boolean ok, String message) throws IOException {
        if (!ok) throw new IOException(message);
    }

    static boolean is(Node node, String uri, String name) {
        return node != null && uri.equals(node.getNamespaceURI()) && name.equals(node.getLocalName());
    }

    static List<Element> children(Node node) {
        List<Element> children = new ArrayList<>();
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) children.add((Element)n);
        }
        return children;
    }

    byte[] write() throws Exception {
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> part : parts.entrySet()) {
                ZipEntry entry = new ZipEntry(part.getKey());
                entry.setTime(0);
                out.putNextEntry(entry);
                if (MAIN.equals(part.getKey())) {
                    factory.newTransformer().transform(new DOMSource(document), new StreamResult(out));
                } else {
                    out.write(part.getValue());
                }
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
