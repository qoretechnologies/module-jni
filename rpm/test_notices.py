# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.util.spec_from_file_location('jni_notices', ROOT / 'debian/install-notices.py')
notices = importlib.util.module_from_spec(loader)
loader.loader.exec_module(notices)


class NoticeInstallTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.repo = self.root / 'repo'
        (self.repo / 'debian').mkdir(parents=True)
        self.destination = self.root / 'package'
        self.destination.mkdir()
        self.jar = self.repo / 'dependency.jar'
        self.manifest = {'dependencies': {'dependency.jar': {
            'coordinate': 'example:library:1.0', 'paths': ['dependency.jar'],
            'upstream': {'url': 'https://example.org/library'}}}}
        (self.repo / 'debian/java-dependencies.json').write_text(json.dumps(self.manifest))

    def test_runtime_notices_and_provenance_preserved_without_rewriting_jar(self):
        with zipfile.ZipFile(self.jar, 'w') as archive:
            archive.writestr('META-INF/LICENSE', 'Copyright 2026 example authors\nMIT notice\n')
            archive.writestr('META-INF/NOTICE', 'Retain this attribution\n')
        before = self.jar.read_bytes()
        notices.install_runtime(self.repo, self.destination)
        out = self.destination / 'usr/share/doc/qore-jni-module/third-party-notices'
        text = (out / 'dependency.jar.txt').read_text()
        self.assertIn('Retain this attribution\n', text)
        self.assertIn('MIT notice\n', text)
        self.assertIn('example:library:1.0', text)
        self.assertEqual(json.loads((out / 'provenance.json').read_text()), self.manifest)
        self.assertEqual(self.jar.read_bytes(), before)

    def test_missing_notice_is_an_error(self):
        with zipfile.ZipFile(self.jar, 'w') as archive:
            archive.writestr('Class.class', b'class data')
        with self.assertRaisesRegex(ValueError, 'No upstream notices'):
            notices.install_runtime(self.repo, self.destination)

    def test_kotlin_notice_directory_is_required_and_preserved(self):
        with self.assertRaisesRegex(ValueError, 'lacks upstream license'):
            notices.install_kotlin(self.destination)
        source = self.destination / 'usr/share/qore/java/kotlin/license'
        source.mkdir(parents=True)
        (source / 'LICENSE.txt').write_text('Kotlin full license text')
        notices.install_kotlin(self.destination)
        self.assertEqual((self.destination / 'usr/share/doc/qore-jni-kotlin/upstream-licenses/LICENSE.txt').read_text(),
                         'Kotlin full license text')

    def test_debian_defaults_remain_unchanged(self):
        with patch.object(sys, 'argv', ['install-notices.py']), \
                patch.object(notices, 'install_runtime') as runtime, \
                patch.object(notices, 'install_kotlin') as kotlin:
            notices.main()
        runtime.assert_called_once_with(ROOT, ROOT / 'debian/qore-jni-module')
        kotlin.assert_called_once_with(ROOT / 'debian/qore-jni-kotlin')

    def test_rpm_can_select_one_shared_staging_root(self):
        with patch.object(sys, 'argv', ['install-notices.py', '--runtime-root', str(self.destination),
                                       '--kotlin-root', str(self.destination)]), \
                patch.object(notices, 'install_runtime') as runtime, \
                patch.object(notices, 'install_kotlin') as kotlin:
            notices.main()
        runtime.assert_called_once_with(ROOT, self.destination)
        kotlin.assert_called_once_with(self.destination)

    def test_absent_debian_subpackages_remain_optional(self):
        absent = self.root / 'not-built'
        notices.install_runtime(self.repo, absent)
        notices.install_kotlin(absent)
        self.assertFalse(absent.exists())


if __name__ == '__main__':
    unittest.main()
