#!/usr/bin/python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Assemble verified Java/Kotlin inputs for an offline RPM source component."""
import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile

INPUTS = ('debian/java-dependencies.json', 'debian/prepare-vendor.py',
          'debian/copyright', 'rpm/prepare-vendor.py')


def prepare(repo, vendor, cache):
    repo, vendor, cache = Path(repo), Path(vendor), Path(cache)
    loader = importlib.util.spec_from_file_location('jni_vendor', repo / 'debian/prepare-vendor.py')
    helper = importlib.util.module_from_spec(loader)
    loader.loader.exec_module(helper)
    manifest = json.loads((repo / 'debian/java-dependencies.json').read_text())
    if manifest.get('format') != 1:
        raise ValueError('Unsupported Java dependency manifest')
    helper.verify(repo, vendor, manifest)
    inputs = {}
    for name in INPUTS:
        path = repo / name
        if path.is_symlink() or not path.is_file():
            raise ValueError('Missing regular generator input: ' + name)
        inputs[name] = path.read_bytes()
    cache.mkdir(parents=True, exist_ok=True)
    top = 'qore-jni-vendor-3.0.0'
    with tempfile.TemporaryDirectory(prefix='.jni-vendor-', dir=cache) as temporary:
        archive_path = Path(temporary) / 'vendor.tar.xz'
        with tarfile.open(archive_path, 'w:xz', format=tarfile.PAX_FORMAT) as archive:
            files = {'COPYRIGHT': inputs['debian/copyright'],
                     'java-dependencies.json': inputs['debian/java-dependencies.json']}
            for name in sorted([*files, *('vendor/' + name for name in manifest['vendor_artifacts'])]):
                if name in files:
                    data = files[name]
                else:
                    relative = name.removeprefix('vendor/')
                    data = (vendor / relative).read_bytes()
                    if hashlib.sha256(data).hexdigest() != manifest['vendor_artifacts'][relative]['sha256']:
                        raise ValueError('Vendor input changed during archive creation: ' + relative)
                member = tarfile.TarInfo(top + '/' + name)
                member.size, member.mode, member.mtime = len(data), 0o644, 0
                member.uid = member.gid = 0
                member.uname = member.gname = 'root'
                archive.addfile(member, io.BytesIO(data))
        digest = helper.digest(archive_path)
        destination = cache / digest
        try:
            os.link(archive_path, destination)
        except FileExistsError:
            helper.verify_file(destination, {'sha256': digest})
    return {'schema': 1, 'components': [{
        'archive': top + '.tar.xz', 'top': top, 'sha256': digest,
        'generated_from': {name: hashlib.sha256(data).hexdigest() for name, data in inputs.items()},
        'licenses': ['COPYRIGHT'],
    }]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--vendor', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    result = prepare(args.repo, args.vendor, args.cache)
    with args.manifest.open('x') as output:
        output.write(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
