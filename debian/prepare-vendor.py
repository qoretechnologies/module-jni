#!/usr/bin/python3
# Copyright (C) 2026 David Nichols
# SPDX-License-Identifier: MIT
"""Prepare pinned PPA inputs before upload; verify them offline during builds."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import tempfile
import urllib.request
import zipfile


def relative(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError("Unsafe archive path: " + name)
    return path


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_file(path, record):
    if path.is_symlink() or not path.is_file():
        raise ValueError("Missing regular input: " + str(path))
    if path.stat().st_size != record.get("size", path.stat().st_size) or digest(path) != record["sha256"]:
        raise ValueError("Checksum mismatch: " + str(path))


def verify(repo, vendor, manifest):
    for record in manifest["dependencies"].values():
        for name in record["paths"]:
            verify_file(repo / relative(name), record)
    expected = set(manifest["vendor_artifacts"])
    actual = {p.relative_to(vendor).as_posix() for p in vendor.rglob("*") if not p.is_dir()}
    if actual != expected:
        raise ValueError("Vendor inventory differs: " + repr(sorted(actual ^ expected)))
    for name, record in manifest["vendor_artifacts"].items():
        verify_file(vendor / relative(name), record)


def fetch(vendor, manifest, cache):
    # Refuse an existing output, including a partial one; the caller owns cleanup.
    vendor.mkdir(parents=True, exist_ok=False)
    for name, record in manifest["vendor_artifacts"].items():
        target = vendor / relative(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        cached = cache.get(name)
        with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
            temp = Path(stream.name)
            try:
                if cached:
                    verify_file(Path(cached), record)
                    with Path(cached).open("rb") as source:
                        shutil.copyfileobj(source, stream)
                else:
                    if not record["url"].startswith("https://"):
                        raise ValueError("An HTTPS upstream URL is required")
                    with urllib.request.urlopen(record["url"], timeout=120) as source:
                        if not source.url.startswith("https://"):
                            raise ValueError("Insecure upstream redirect")
                        shutil.copyfileobj(source, stream)
                stream.flush()
                verify_file(temp, record)
                os.replace(temp, target)
            finally:
                temp.unlink(missing_ok=True)


def extract_kotlin(vendor, destination):
    destination.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(vendor / "kotlin/kotlin-compiler-2.3.0.zip") as archive:
        for member in archive.infolist():
            name = relative(member.filename)
            if name.parts[0] != "kotlinc":
                raise ValueError("Unexpected Kotlin archive root")
            mode = member.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise ValueError("Kotlin archive must not contain symlinks")
            target = destination / name
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)
                target.chmod(0o755 if mode & 0o111 else 0o644)
    scripting = destination / "kotlinc/lib/scripting"
    scripting.mkdir(parents=True)
    for source in sorted((vendor / "kotlin/scripting").glob("*.jar")):
        shutil.copyfile(source, scripting / source.name)


def tree_inventory(root):
    if root.is_symlink() or not root.is_dir():
        raise ValueError("Kotlin destination must be a regular directory")
    inventory = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError("Kotlin destination must not contain symlinks")
        name = path.relative_to(root).as_posix()
        if path.is_dir():
            inventory[name] = ("directory",)
        elif path.is_file():
            inventory[name] = ("file", stat.S_IMODE(path.stat().st_mode), digest(path))
        else:
            raise ValueError("Unexpected Kotlin destination entry: " + name)
    return inventory


def unpack_kotlin(vendor, destination):
    # Debhelper may configure more than once. Verify existing content against a
    # fresh extraction instead of silently trusting or overwriting it.
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".kotlin-", dir=destination.parent) as temporary:
        staged = Path(temporary) / "contents"
        extract_kotlin(vendor, staged)
        if destination.exists() or destination.is_symlink():
            if tree_inventory(destination) != tree_inventory(staged):
                raise ValueError("Existing Kotlin destination differs from pinned inputs")
        else:
            staged.rename(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["fetch", "verify", "unpack-kotlin"])
    parser.add_argument("--vendor", type=Path, default=Path("vendor"))
    parser.add_argument("--cache", type=Path, help="Optional local name-to-file JSON cache")
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    manifest = json.loads((repo / "debian/java-dependencies.json").read_text())
    if manifest["format"] != 1:
        raise ValueError("Unsupported vendor manifest format")
    if args.action == "fetch":
        fetch(args.vendor, manifest, json.loads(args.cache.read_text()) if args.cache else {})
    verify(repo, args.vendor, manifest)
    if args.action == "unpack-kotlin":
        if args.destination is None:
            parser.error("--destination is required for unpack-kotlin")
        unpack_kotlin(args.vendor, args.destination)


if __name__ == "__main__":
    main()
