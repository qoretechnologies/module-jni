#!/usr/bin/python3
# Copyright (C) 2026 David Nichols
# SPDX-License-Identifier: MIT
"""Install verbatim upstream notices without changing any runtime JAR."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tarfile
import zipfile


def notices(path):
    """Read notice files and copyright/license comments in source archives."""
    found = {}
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith("/"):
                continue
            base = Path(name).name.lower()
            if (re.search(r"licen[cs]e|notice|copying|copyright", base)
                    and not base.endswith((".class", ".java", ".kt"))) or base == "about.html":
                found[name] = archive.read(name).decode("utf-8", errors="replace")
            elif name.endswith((".java", ".kt")):
                source = archive.read(name).decode("utf-8", errors="replace")
                # Some projects put the complete BSD notice at the end of a file.
                for comment in re.findall(r"/\*.*?\*/", source, re.DOTALL):
                    if re.search(r"copyright|SPDX-License-Identifier|redistribution and use|public domain", comment, re.I):
                        found[name + "#" + hashlib.sha256(comment.encode()).hexdigest()[:12]] = comment
    return found


def archive_notices(path):
    found = {}
    with tarfile.open(path) as archive:
        for member in archive:
            if not member.isfile():
                continue
            base = Path(member.name).name.lower()
            if (re.search(r"licen[cs]e|notice|copying|copyright|authors", base)
                    and not base.endswith((".class", ".java", ".kt"))):
                with archive.extractfile(member) as source:
                    found[member.name] = source.read().decode("utf-8", errors="replace")
    return found


def documentation_root(package, docdir):
    """Resolve an absolute distribution documentation prefix inside a staging root."""
    docdir = Path(docdir)
    if not docdir.is_absolute() or docdir == Path('/') or '..' in docdir.parts:
        raise ValueError('Documentation directory must be an absolute non-root path without parent traversal')
    return package / docdir.relative_to('/')


def install_runtime(repo, package, docdir=Path('/usr/share/doc')):
    """Install runtime notices below a Debian or RPM staging root."""
    out = documentation_root(package, docdir) / 'qore-jni-module/third-party-notices'
    manifest = json.loads((repo / "debian/java-dependencies.json").read_text())
    if package.is_dir():
        out.mkdir(parents=True, exist_ok=True)
        for name, record in sorted(manifest["dependencies"].items()):
            inputs = [repo / record["paths"][0]]
            source = repo / "vendor/sources" / (name[:-4] + "-sources.jar")
            if source.is_file():
                inputs.append(source)
            blocks = [record["coordinate"], "Upstream: " + record["upstream"]["url"]]
            if "modification" in record:
                blocks.append(record["modification"])
            seen = set()
            for path in inputs:
                for member, text in notices(path).items():
                    if text not in seen:
                        seen.add(text)
                        blocks.append(path.name + ":" + member + "\n\n" + text)
            for name_in_vendor in record.get("notice_archives", []):
                path = repo / "vendor" / name_in_vendor
                for member, text in archive_notices(path).items():
                    if text not in seen:
                        seen.add(text)
                        blocks.append(path.name + ":" + member + "\n\n" + text)
            for sibling in record.get("notice_siblings", []):
                sibling_inputs = [repo / manifest["dependencies"][sibling]["paths"][0]]
                source = repo / "vendor/sources" / (sibling[:-4] + "-sources.jar")
                if source.is_file():
                    sibling_inputs.append(source)
                for path in sibling_inputs:
                    for member, text in notices(path).items():
                        if text not in seen:
                            seen.add(text)
                            blocks.append(path.name + ":" + member + "\n\n" + text)
            if not seen:
                raise ValueError("No upstream notices found for " + name)
            (out / (name + ".txt")).write_text("\n\n".join(blocks) + "\n")
        shutil.copyfile(repo / "debian/java-dependencies.json", out / "provenance.json")


def install_kotlin(kotlin, docdir=Path('/usr/share/doc')):
    """Retain the compiler's complete upstream notice directory."""
    out = documentation_root(kotlin, docdir) / 'qore-jni-kotlin/upstream-licenses'
    if kotlin.is_dir():
        source = kotlin / "usr/share/qore/java/kotlin/license"
        if not source.is_dir():
            raise ValueError("Kotlin package lacks upstream license notices")
        shutil.copytree(source, out, dirs_exist_ok=True)


def main():
    repo = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-root", type=Path, default=repo / "debian/qore-jni-module")
    parser.add_argument("--kotlin-root", type=Path, default=repo / "debian/qore-jni-kotlin")
    parser.add_argument("--docdir", type=Path, default=Path('/usr/share/doc'),
                        help="absolute documentation prefix inside the staging roots")
    args = parser.parse_args()
    install_runtime(repo, args.runtime_root, args.docdir)
    install_kotlin(args.kotlin_root, args.docdir)


if __name__ == "__main__":
    main()
