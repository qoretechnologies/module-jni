#!/usr/bin/python3
# Copyright (C) 2026 David Nichols
# SPDX-License-Identifier: MIT
"""Exercise offline integrity checks and archive boundaries with real files."""

import hashlib
import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location("vendor", Path(__file__).resolve().parents[1] / "prepare-vendor.py")
vendor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vendor)


class VendorTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.inputs = self.root / "vendor"
        self.inputs.mkdir()
        self.binary = self.repo / "provider.jar"
        self.binary.write_bytes(b"pinned upstream binary")
        self.source = self.inputs / "source.jar"
        self.source.write_bytes(b"corresponding source")
        self.record = lambda path: {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                                    "size": path.stat().st_size}
        self.manifest = {
            "dependencies": {"provider": {"paths": ["provider.jar"], **self.record(self.binary)}},
            "vendor_artifacts": {"source.jar": {"url": "https://example.invalid/source.jar",
                                               **self.record(self.source)}},
        }

    def test_offline_verification_and_changed_binary(self):
        with patch.object(vendor.urllib.request, "urlopen", side_effect=AssertionError("Network access")):
            vendor.verify(self.repo, self.inputs, self.manifest)
            self.binary.write_bytes(b"changed upstream binary")
            with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
                vendor.verify(self.repo, self.inputs, self.manifest)

    def test_missing_extra_and_symlink_inputs(self):
        self.source.unlink()
        with self.assertRaisesRegex(ValueError, "inventory differs"):
            vendor.verify(self.repo, self.inputs, self.manifest)
        self.source.symlink_to(self.binary)
        with self.assertRaisesRegex(ValueError, "regular input"):
            vendor.verify(self.repo, self.inputs, self.manifest)
        self.source.unlink()
        self.source.write_bytes(b"corresponding source")
        (self.inputs / "undeclared.jar").write_bytes(b"unreviewed")
        with self.assertRaisesRegex(ValueError, "inventory differs"):
            vendor.verify(self.repo, self.inputs, self.manifest)

    def test_cached_fetch_verifies_content_and_refuses_existing_output(self):
        out = self.root / "fetched"
        with patch.object(vendor.urllib.request, "urlopen", side_effect=AssertionError("Network access")):
            vendor.fetch(out, self.manifest, {"source.jar": str(self.source)})
            vendor.verify(self.repo, out, self.manifest)
            with self.assertRaises(FileExistsError):
                vendor.fetch(out, self.manifest, {})
            self.source.write_bytes(b"corrupted cache")
            with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
                vendor.fetch(self.root / "bad-cache", self.manifest, {"source.jar": str(self.source)})

    def test_insecure_download_url_rejected(self):
        self.manifest["vendor_artifacts"]["source.jar"]["url"] = "http://example.invalid/source.jar"
        with patch.object(vendor.urllib.request, "urlopen", side_effect=AssertionError("Network access")):
            with self.assertRaisesRegex(ValueError, "HTTPS"):
                vendor.fetch(self.root / "insecure", self.manifest, {})

    def archive(self, name, symlink=False):
        path = self.inputs / "kotlin/kotlin-compiler-2.3.0.zip"
        path.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            info = zipfile.ZipInfo(name)
            info.external_attr = ((stat.S_IFLNK | 0o777) if symlink else (stat.S_IFREG | 0o755)) << 16
            archive.writestr(info, b"compiler fixture")
        (path.parent / "scripting").mkdir(exist_ok=True)

    def test_zip_paths_and_symlinks_rejected(self):
        for number, name in enumerate(["../outside", "/outside", "kotlinc/../../outside", "other/file"]):
            with self.subTest(name=name):
                self.archive(name)
                with self.assertRaises(ValueError):
                    vendor.unpack_kotlin(self.inputs, self.root / ("bad-zip-" + str(number)))
        self.archive("kotlinc/bin/kotlinc", symlink=True)
        with self.assertRaisesRegex(ValueError, "symlinks"):
            vendor.unpack_kotlin(self.inputs, self.root / "symlink-zip")

    def test_offline_unpack_preserves_executable_and_scripting(self):
        self.archive("kotlinc/bin/kotlinc")
        (self.inputs / "kotlin/scripting/engine.jar").write_bytes(b"pinned engine")
        out = self.root / "unpacked"
        with patch.object(vendor.urllib.request, "urlopen", side_effect=AssertionError("Network access")):
            vendor.unpack_kotlin(self.inputs, out)
        self.assertEqual((out / "kotlinc/bin/kotlinc").stat().st_mode & 0o777, 0o755)
        self.assertEqual((out / "kotlinc/lib/scripting/engine.jar").read_bytes(), b"pinned engine")
        vendor.unpack_kotlin(self.inputs, out)
        (out / "kotlinc/bin/kotlinc").write_bytes(b"tampered compiler")
        with self.assertRaisesRegex(ValueError, "differs from pinned inputs"):
            vendor.unpack_kotlin(self.inputs, out)

    def test_existing_unpack_rejects_changed_modes_and_symlinks(self):
        self.archive("kotlinc/bin/kotlinc")
        out = self.root / "unpacked"
        vendor.unpack_kotlin(self.inputs, out)
        compiler = out / "kotlinc/bin/kotlinc"
        compiler.chmod(0o644)
        with self.assertRaisesRegex(ValueError, "differs from pinned inputs"):
            vendor.unpack_kotlin(self.inputs, out)
        compiler.unlink()
        compiler.symlink_to(self.binary)
        with self.assertRaisesRegex(ValueError, "symlinks"):
            vendor.unpack_kotlin(self.inputs, out)


if __name__ == "__main__":
    unittest.main()
