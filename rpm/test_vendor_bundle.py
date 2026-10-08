# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.util.spec_from_file_location('rpm_vendor', ROOT / 'rpm/prepare-vendor.py')
bundle = importlib.util.module_from_spec(loader)
loader.loader.exec_module(bundle)


class VendorBundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / 'repo'
        (self.repo / 'debian').mkdir(parents=True)
        (self.repo / 'rpm').mkdir()
        shutil.copyfile(ROOT / 'debian/prepare-vendor.py', self.repo / 'debian/prepare-vendor.py')
        shutil.copyfile(ROOT / 'rpm/prepare-vendor.py', self.repo / 'rpm/prepare-vendor.py')
        (self.repo / 'debian/copyright').write_text('Copyright 2026 fixture authors\nLicense: MIT\n')
        self.vendor = self.root / 'vendor'
        self.vendor.mkdir()
        self.payload = b'verified third-party input'
        (self.vendor / 'source.jar').write_bytes(self.payload)
        (self.repo / 'dependency.jar').write_bytes(self.payload)
        digest = hashlib.sha256(self.payload).hexdigest()
        self.manifest = {'format': 1,
                         'dependencies': {'dependency.jar': {'paths': ['dependency.jar'], 'sha256': digest}},
                         'vendor_artifacts': {'source.jar': {'sha256': digest}}}
        (self.repo / 'debian/java-dependencies.json').write_text(json.dumps(self.manifest))
        self.cache = self.root / 'cache'

    def prepare(self):
        return bundle.prepare(self.repo, self.vendor, self.cache)

    def test_reproducible_archive_preserves_inputs_notices_and_provenance(self):
        first = self.prepare()
        self.assertEqual(first, self.prepare())
        component, = first['components']
        cached = self.cache / component['sha256']
        self.assertEqual(hashlib.sha256(cached.read_bytes()).hexdigest(), component['sha256'])
        with tarfile.open(cached) as archive:
            top = component['top']
            self.assertEqual(archive.getnames(), [top + '/COPYRIGHT', top + '/java-dependencies.json',
                                                  top + '/vendor/source.jar'])
            self.assertEqual(archive.extractfile(top + '/vendor/source.jar').read(), self.payload)
            self.assertIn(b'License: MIT', archive.extractfile(top + '/COPYRIGHT').read())
            for member in archive:
                self.assertEqual((member.uid, member.gid, member.mode, member.mtime), (0, 0, 0o644, 0))
        for name, expected in component['generated_from'].items():
            self.assertEqual(hashlib.sha256((self.repo / name).read_bytes()).hexdigest(), expected)
        self.assertEqual(list(self.cache.iterdir()), [cached])

    def test_corrupt_runtime_dependency_is_rejected_before_cache_creation(self):
        (self.repo / 'dependency.jar').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
            self.prepare()
        self.assertFalse(self.cache.exists())

    def test_unknown_vendor_file_and_symlink_are_rejected(self):
        extra = self.vendor / 'unexpected'
        extra.write_text('not pinned')
        with self.assertRaisesRegex(ValueError, 'inventory differs'):
            self.prepare()
        extra.unlink()
        source = self.vendor / 'source.jar'
        source.unlink()
        source.symlink_to(self.repo / 'dependency.jar')
        with self.assertRaisesRegex(ValueError, 'Missing regular'):
            self.prepare()
        self.assertFalse(self.cache.exists())

    def test_incomplete_archive_is_never_published(self):
        with patch.object(bundle.tarfile.TarFile, 'addfile', side_effect=OSError('write failed')):
            with self.assertRaisesRegex(OSError, 'write failed'):
                self.prepare()
        self.assertEqual(list(self.cache.iterdir()), [])

    def test_existing_cache_corruption_is_detected_and_not_overwritten(self):
        result = self.prepare()
        cached = self.cache / result['components'][0]['sha256']
        cached.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
            self.prepare()
        self.assertEqual(cached.read_bytes(), b'corrupt')
        self.assertEqual(list(self.cache.iterdir()), [cached])

    def test_updated_notice_changes_both_archive_and_input_pin(self):
        first = self.prepare()['components'][0]
        (self.repo / 'debian/copyright').write_text('Copyright 2026 updated authors\nLicense: MIT\n')
        second = self.prepare()['components'][0]
        self.assertNotEqual(first['sha256'], second['sha256'])
        self.assertNotEqual(first['generated_from']['debian/copyright'],
                            second['generated_from']['debian/copyright'])
        self.assertTrue((self.cache / first['sha256']).is_file())


if __name__ == '__main__':
    unittest.main()
