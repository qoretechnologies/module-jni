// Copyright 2026 Qore Technologies, s.r.o.  MIT license.
package org.qore.dataprovider.word;

import java.io.IOException;
import java.util.*;
import org.qore.jni.Hash;
import org.w3c.dom.*;

import static org.qore.dataprovider.word.WordTemplatePackage.*;

/** Fills tagged plain text runs and single-row Word 2013 repeating sections. */
public final class WordTemplate {
    private static final String W15 = "http://schemas.microsoft.com/office/word/2012/wordml";
    private static final String W14 = "http://schemas.microsoft.com/office/word/2010/wordml";
    private final WordTemplatePackage pkg;
    private final Hash fields = new Hash();

    public WordTemplate(byte[] template) throws Exception {
        pkg = new WordTemplatePackage(template);
        inspect(pkg.document.getDocumentElement(), fields, true);
        require(!fields.isEmpty(), "the document has no supported template fields");
    }

    public Hash getFields() {
        return fields;
    }

    public byte[] fill(Hash values) throws Exception {
        require(values != null, "missing template mappings");
        // Work on a clone so a repeated request or a failed mapping leaves the template intact.
        Element root = pkg.document.getDocumentElement();
        Element copy = (Element)root.cloneNode(true);
        render(copy, fields, values);
        pkg.document.replaceChild(copy, root);
        try {
            return pkg.write();
        } finally {
            pkg.document.replaceChild(root, copy);
        }
    }

    private static List<Element> controls(Element root) {
        NodeList all = root.getElementsByTagNameNS(W, "sdt");
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < all.getLength(); ++i) {
            Element control = (Element)all.item(i);
            Node parent = control.getParentNode();
            while (parent != root && !is(parent, W, "sdt")) parent = parent.getParentNode();
            if (parent == root) result.add(control);
        }
        return result;
    }

    private static Element child(Element parent, String ns, String name) throws IOException {
        Element found = null;
        for (Element el : children(parent)) {
            if (is(el, ns, name)) {
                require(found == null, "duplicate " + name + " in Word control");
                found = el;
            }
        }
        require(found != null, "missing " + name + " in Word control");
        return found;
    }

    private static Element onlyChild(Element parent, String name) throws IOException {
        require(children(parent).size() == 1, "expected exactly one " + name + " in template control");
        return child(parent, W, name);
    }

    private static String tag(Element control) throws IOException {
        String name = child(child(control, W, "sdtPr"), W, "tag").getAttributeNS(W, "val");
        require(name.matches("[A-Za-z_][A-Za-z0-9_]*"), "expected a named Word control tag");
        return name;
    }

    private static void properties(Element control, String ns, String kind) throws IOException {
        Element props = child(control, W, "sdtPr");
        child(props, ns, kind);
        for (Element prop : children(props)) {
            if (is(prop, ns, kind)) continue;
            require(W.equals(prop.getNamespaceURI()) && Arrays.asList("tag", "id", "alias", "rPr",
                "placeholder", "showingPlcHdr").contains(prop.getLocalName()),
                "unsupported Word control property: " + prop.getLocalName());
        }
        for (Element part : children(control)) {
            require(is(part, W, "sdtPr") || is(part, W, "sdtContent"), "unsupported control part: " + part.getLocalName());
        }
    }

    private static Element textRun(Element control) throws IOException {
        properties(control, W, "text");
        require(is(control.getParentNode(), W, "p"), "plain-text field must be inside a paragraph");
        Element run = onlyChild(child(control, W, "sdtContent"), "r");
        child(run, W, "t");
        for (Element el : children(run)) {
            require(is(el, W, "rPr") || is(el, W, "t"), "unsupported content in a plain-text field");
        }
        return run;
    }

    private static Element templateRow(Element control) throws IOException {
        properties(control, W15, "repeatingSection");
        require(is(control.getParentNode(), W, "tbl"), "repeating section must be directly inside a table");
        Element item = onlyChild(child(control, W, "sdtContent"), "sdt");
        properties(item, W15, "repeatingSectionItem");
        Element row = onlyChild(child(item, W, "sdtContent"), "tr");
        // A cloned identifier or relationship in a row can point to the wrong drawing, range or note.
        NodeList elements = row.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); ++i) {
            Element el = (Element)elements.item(i);
            require(W.equals(el.getNamespaceURI()), "unsupported markup in repeating row: " + el.getNodeName());
            String name = el.getLocalName();
            require(!el.hasAttributeNS(W, "id") && !name.endsWith("Change")
                && !Arrays.asList("drawing", "pict", "vMerge", "tbl", "sectPr", "hyperlink", "fldChar").contains(name),
                "unsupported element in repeating row: " + name);
        }
        return row;
    }

    private static void inspect(Element root, Hash schema, boolean allowRows) throws IOException {
        for (Element control : controls(root)) {
            String name = tag(control);
            Hash field = new Hash();
            field.put("required", true);
            if (child(control, W, "sdtPr").getElementsByTagNameNS(W15, "repeatingSection").getLength() != 0) {
                require(allowRows, "nested repeating sections are unsupported");
                Hash columns = new Hash();
                inspect(templateRow(control), columns, false);
                require(!columns.isEmpty(), "repeating row has no named fields");
                field.put("type", "rows");
                field.put("fields", columns);
            } else {
                textRun(control);
                field.put("type", "scalar");
            }
            Object prev = schema.put(name, field);
            require(prev == null || prev.equals(field), "conflicting definitions for " + name);
        }
    }

    private static void render(Element root, Hash schema, Map<?, ?> values) throws IOException {
        for (String name : schema.keySet()) require(values.containsKey(name), "missing required mapping: " + name);
        for (Object name : values.keySet()) require(schema.containsKey(name), "unknown mapping: " + name);
        for (Element control : controls(root)) {
            String name = tag(control);
            Hash field = (Hash)schema.get(name);
            Object value = values.get(name);
            Node parent = control.getParentNode();
            if (field.containsKey("fields")) {
                if (value instanceof Object[]) value = Arrays.asList((Object[])value);
                require(value instanceof List, "expected a list of records for " + name);
                Element prototype = templateRow(control);
                for (Object record : (List<?>)value) {
                    require(record instanceof Map, "expected a row mapping for " + name);
                    Element row = (Element)prototype.cloneNode(true);
                    NodeList paragraphs = row.getElementsByTagNameNS(W, "p");
                    for (int i = 0; i < paragraphs.getLength(); ++i) {
                        Element p = (Element)paragraphs.item(i);
                        p.removeAttributeNS(W14, "paraId");
                        p.removeAttributeNS(W14, "textId");
                    }
                    render(row, (Hash)field.get("fields"), (Map<?, ?>)record);
                    parent.insertBefore(row, control);
                }
                parent.removeChild(control);
            } else {
                require(value instanceof String || value instanceof Number || value instanceof Boolean,
                    "expected text, a number or a boolean for " + name);
                String text = value.toString();
                require(text.codePoints().allMatch(c -> (c >= 32 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd)
                    || (c >= 0x10000 && c <= 0x10ffff)), "expected single-line XML text for " + name);
                Element run = textRun(control);
                Element node = child(run, W, "t");
                node.setTextContent(text);
                node.setAttributeNS(javax.xml.XMLConstants.XML_NS_URI, "xml:space", "preserve");
                parent.replaceChild(run, control);
            }
        }
    }
}
