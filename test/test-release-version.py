#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check release, package, and vendor-bundle version consistency."""

import hashlib
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ReleaseVersionTest(unittest.TestCase):
    def test_native_and_package_versions_match_the_release_notes(self):
        cmake = (ROOT / 'CMakeLists.txt').read_text()
        version = '.'.join(re.search(rf'set\s*\(VERSION_{part}\s+(\d+)\)', cmake)[1]
                           for part in ('MAJOR', 'MINOR', 'PATCH'))
        self.assertEqual('3.0.0', version)
        self.assertIn('Version: ' + version + '\n', (ROOT / 'qore-jni-module.spec').read_text())
        self.assertTrue((ROOT / 'debian/changelog').read_text().startswith('qore-jni-module (' + version + '~'))
        notes = (ROOT / 'docs/guide-jnireleasenotes.dox.tmpl').read_text()
        sections = re.findall(r'@section\s+(jni_\S+)\s+jni Module Version (\S+)', notes)
        self.assertEqual([('jni_3_0_0', version), ('jni_2_4_0', '2.4.0')], sections[:2])
        self.assertFalse(any(v.startswith(('2.5', '2.6', '2.7')) for _, v in sections))

    def test_api_since_and_migration_help_use_the_released_version(self):
        qpp = (ROOT / 'src/ql_jni.qpp').read_text()
        self.assertEqual(4, qpp.count('@since jni 3.0'))
        self.assertNotRegex(qpp, r'@since jni 2\.[567]')
        migration = (ROOT / 'bin/qjava-migrate-imports').read_text()
        self.assertIn('jni 3.0.0+', migration)
        self.assertNotIn('jni 2.7.0', migration)

    def test_vendor_manifest_pins_the_current_generator(self):
        manifest = json.loads((ROOT / 'rpm/vendor-sources.json').read_text())
        component, = manifest['components']
        self.assertEqual('qore-jni-vendor-3.0.0', component['top'])
        self.assertEqual(component['top'] + '.tar.xz', component['archive'])
        for name, expected in component['generated_from'].items():
            with self.subTest(input=name):
                self.assertEqual(expected, hashlib.sha256((ROOT / name).read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
