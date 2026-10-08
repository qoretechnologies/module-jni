#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check that embedded Java classes do not depend on JAR extraction order."""
import argparse
import bz2
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
import zipfile

COMMAND = None


class MakeIncTest(unittest.TestCase):
    def generate(self, root, entries):
        jar = root / "input.jar"
        output = root / "classes.inc"
        with zipfile.ZipFile(jar, "w") as archive:
            for name, data in entries:
                archive.writestr(name, data)
        result = subprocess.run(COMMAND + [str(jar), str(output)],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        # Generated comments contain the temporary extraction directory. They are
        # not compiled; compare all declarations, data and class-map entries.
        return re.sub(r"^// generated from .*\n", "", output.read_text(), flags=re.M)

    def test_directory_and_class_order(self):
        expected = [
            "alpha/deep/A$Inner.class", "alpha/deep/Z$Inner.class",
            "alpha/deep/A.class", "alpha/deep/Z.class",
            "alpha/A$Inner.class", "alpha/Z$Inner.class",
            "alpha/A.class", "alpha/Z.class",
            "zeta/A$Inner.class", "zeta/Z$Inner.class",
            "zeta/A.class", "zeta/Z.class",
        ]
        entries = [(name, b"\xca\xfe\xba\xbe" + name.encode()) for name in expected]
        entries += [("META-INF/ignored.class", b"excluded"), ("README", b"ignored")]
        shuffled = entries.copy()
        random.Random(12345).shuffle(shuffled)
        outputs = []
        for order in (entries, list(reversed(entries)), shuffled):
            with tempfile.TemporaryDirectory(prefix="make-inc-order-") as directory:
                output = self.generate(Path(directory), order)
            outputs.append(output)
            names = re.findall(r'^    \{"([^"]+)",', output, flags=re.M)
            self.assertEqual([name[:-6].replace("/", ".") for name in expected], names)
            arrays = dict(re.findall(r"static unsigned char (\w+)\[\] = \{(.*?)\};",
                                     output, flags=re.S))
            self.assertEqual(len(expected), len(arrays))
            for name, data in entries[:len(expected)]:
                variable = "java_" + re.sub(r"[-/$]", "_", name[:-6]) + "_class_data"
                compressed = bytes(int(x, 16) for x in re.findall(r"0x([0-9a-fA-F]{2})",
                                                                 arrays[variable]))
                self.assertEqual(data, bz2.decompress(compressed))
        self.assertEqual(outputs[0], outputs[1])
        self.assertEqual(outputs[0], outputs[2])

    def test_jar_without_classes(self):
        with tempfile.TemporaryDirectory(prefix="make-inc-empty-") as directory:
            output = self.generate(Path(directory), [("META-INF/ignored.class", b"ignored")])
        self.assertEqual("DLLLOCAL cmap_t jar_cmap = {\n};\n", output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qore", required=True)
    parser.add_argument("--make-inc", type=Path, required=True)
    args = parser.parse_args()
    COMMAND = [args.qore, str(args.make_inc.resolve())]
    unittest.main(argv=[__file__, "-v"])
