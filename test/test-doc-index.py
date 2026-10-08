#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Validate the generated JNI documentation and strict-warning gate."""
import argparse
import os
from html.parser import HTMLParser
from pathlib import Path
import subprocess
import tempfile
import unittest
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET

BUILD = None


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.current = None
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.current = [dict(attrs).get("href", ""), ""]

    def handle_data(self, text):
        if self.current is not None:
            self.current[1] += text

    def handle_endtag(self, tag):
        if tag == "a" and self.current is not None:
            self.links.append(self.current)
            self.current = None


class TableHeaders(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_header = False
        self.headers = []

    def handle_starttag(self, tag, attrs):
        if tag == "th" or (tag == "td" and dict(attrs).get("class") == "qore"):
            self.in_header = True
            self.headers.append("")

    def handle_data(self, text):
        if self.in_header:
            self.headers[-1] += text

    def handle_endtag(self, tag):
        if tag in ("th", "td"):
            self.in_header = False


class Headings(HTMLParser):
    def __init__(self):
        super().__init__()
        self.headings = []
        self.current = None

    def handle_starttag(self, tag, attrs):
        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self.current = [tag, ""]

    def handle_data(self, text):
        if self.current is not None:
            self.current[1] += text

    def handle_endtag(self, tag):
        if self.current is not None and tag == self.current[0]:
            self.headings.append((tag, self.current[1].strip()))
            self.current = None


class JniDocIndexTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = ET.parse(BUILD / "jni.tag").getroot()

    def class_page(self, name):
        for compound in self.index.findall("compound"):
            if compound.findtext("name") == name.replace(".", "::"):
                return (BUILD / "docs/jni/html" / compound.findtext("filename")).read_text()
        self.fail("Missing documented class: " + name)

    def test_public_java_and_qore_apis(self):
        names = {c.findtext("name") for c in self.index.findall("compound")}
        self.assertIn("org::qore::jni::QoreURLClassLoader", names)
        self.assertIn("Jni::org::qore::jni::JavaArray", names)
        files = [c.findtext("name") for c in self.index.findall("compound") if c.get("kind") == "file"]
        self.assertFalse(any(name.endswith(".cpp") for name in files), files)

    def test_legacy_dynamic_import_anchor(self):
        page = (BUILD / "docs/jni/html/jni_from_javaguide.html").read_text()
        self.assertIn('id="jni_dynamic_import_qore_in_java"', page)
        self.assertIn('id="jni_dynamic_import_in_java"', page)

    def test_mainpage_covers_kotlin_with_resolved_links(self):
        path = BUILD / "docs/jni/html/index.html"
        page = path.read_text()
        intro = page.split('id="jniintro"', 1)[1].split('id="jniqlibmodules"', 1)[0]
        self.assertIn("Java and Kotlin", intro)
        self.assertIn('id="jnikotlin"', intro)
        for text in ("Use Kotlin libraries from", "Call Qore from Kotlin", "Evaluate Kotlin scripts from",
                     "qkotlinc", "download-kotlin-scripting-jars"):
            self.assertIn(text, intro)
        parser = Links()
        parser.feed(intro)
        for label in ("Kotlin interoperability example", "class-loading directives", "compilation guide",
                      "collection type conversions", "kotlin_eval()", "kotlin_scripting_available()",
                      "kotlin_scripting_retry()"):
            with self.subTest(label=label):
                links = [href for href, text in parser.links if text == label]
                self.assertTrue(links, "Missing linked documentation: " + label)
                for href in links:
                    url = urlsplit(href)
                    self.assertFalse(url.scheme or url.netloc, href)
                    target = path.parent / unquote(url.path) if url.path else path
                    self.assertTrue(target.is_file(), href)
                    if url.fragment:
                        self.assertIn('id="' + unquote(url.fragment) + '"', target.read_text(), href)
        navigation = page.split('id="jnidocumentation"', 1)[1]
        self.assertIn("Kotlin", navigation)

    def test_unreleased_versions_are_consolidated_into_3_0(self):
        page = (BUILD / "docs/jni/html/jnireleasenotesguide.html").read_text()
        self.assertIn('id="jni_3_0_0"', page)
        self.assertIn('jni Module Version 3.0.0', page)
        self.assertIn('jni Module Version 2.4.0', page)
        self.assertIn('Kotlin language integration', page)
        self.assertIn('interruptible I/O', page)
        for version in (5, 6, 7):
            self.assertIn(f'id="jni_2_{version}_0"', page)
            self.assertNotIn(f'jni Module Version 2.{version}.0', page)
        parser = Links()
        parser.feed((BUILD / "docs/jni/html/index.html").read_text())
        self.assertIn(["jnireleasenotesguide.html", "jni Module Release Notes"], parser.links)

    def test_major_release_outline_and_related_documentation(self):
        path = BUILD / "docs/jni/html/jnireleasenotesguide.html"
        page = path.read_text()
        current = page.split('id="jni_3_0_0"', 1)[1].split('id="jni_2_4_0"', 1)[0]
        headings = Headings()
        headings.feed(current)
        self.assertEqual(["Overview", "New Features", "Bugfixes"],
                         [title for level, title in headings.headings if level == "h2"])
        self.assertEqual([
            "Kotlin Integration", "Data Provider Integrations", "Columnar JDBC Results",
            "Java API Generation and Interoperability", "Runtime and Resource Management",
        ], [title for level, title in headings.headings if level == "h3"])
        parser = Links()
        parser.feed(current)
        labels = {label for _, label in parser.links}
        for label in ("Java import migration", "Kotlin example", "kotlin_eval()",
                      "JakartaJmsDataProvider", "OpcUaDataProvider", "columnar result support",
                      "generic signatures", "JDBC transaction boundaries"):
            self.assertIn(label, labels)
        for href, label in parser.links:
            with self.subTest(link=label, href=href):
                url = urlsplit(href)
                if url.scheme or url.netloc:
                    continue
                target = Path(os.path.abspath(path.parent / unquote(url.path))) if url.path else path
                self.assertTrue(target.is_file(), href)
                if url.fragment:
                    self.assertIn('id="' + unquote(url.fragment) + '"', target.read_text(), href)
        migration = (path.parent / "jni_from_javaguide.html").read_text()
        self.assertIn("qjava-migrate-imports --dry-run", migration)
        self.assertIn('id="jni_dynamic_import_generics"', migration)

    def test_core_module_links(self):
        for name, label, target in [
            ("org.qore.lang.sqlutil.AbstractTable", "ColumnOperatorInfo",
             "https://docs.qore.org/current/modules/SqlUtil/html/"),
            ("org.qore.lang.HTTPClient", "encode_url", "https://docs.qore.org/current/lang/html/"),
        ]:
            with self.subTest(name=name, label=label):
                parser = Links()
                parser.feed(self.class_page(name))
                matching = [href for href, text in parser.links if label in text]
                self.assertTrue(matching, "Missing linked symbol: " + label)
                self.assertTrue(all(href.startswith(target) and href.partition("#")[0].endswith(".html")
                                    for href in matching), matching)
                if label == "encode_url":
                    self.assertTrue(all("#" in href for href in matching), matching)

    def test_jms_tables_have_real_headers(self):
        parser = TableHeaders()
        parser.feed((BUILD / "docs/JakartaJmsDataProvider/html/index.html").read_text())
        headers = {text.strip() for text in parser.headers}
        for expected in ["Provider", "Path", "Purpose", "Qore Type", "JMS Message Type", "Action", "Type"]:
            self.assertIn(expected, headers)
        self.assertNotIn("", headers)

    def test_strict_docs_reject_bad_reference(self):
        # Use the actual final-pass configuration, with a deliberately broken
        # isolated page; never alter or overwrite the real documentation output.
        config = (BUILD / "Doxyfile.final").read_text()
        self.assertIn("WARN_AS_ERROR = FAIL_ON_WARNINGS", config)
        with tempfile.TemporaryDirectory(prefix="jni-doc-negative-") as directory:
            root = Path(directory)
            source = root / "invalid.dox"
            source.write_text("/** @page invalid Invalid reference\n@ref jni_missing_doc_symbol\n*/\n")
            probe = root / "Doxyfile"
            probe.write_text(config + f'\nINPUT = "{source}"\nOUTPUT_DIRECTORY = "{root / "output"}"\n'
                             'GENERATE_TAGFILE =\nTAGFILES =\n')
            result = subprocess.run(["doxygen", str(probe)], cwd=BUILD, capture_output=True,
                                    text=True, timeout=60)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("jni_missing_doc_symbol", result.stderr)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-dir", type=Path, required=True)
    args, tests = parser.parse_known_args()
    BUILD = args.build_dir.resolve()
    unittest.main(argv=[__file__, *tests])
