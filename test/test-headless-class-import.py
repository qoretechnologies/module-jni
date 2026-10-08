#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check headless class resolution with JNI method validation enabled."""
import argparse
import os
from pathlib import Path
import subprocess
import unittest

COMMAND = None


class HeadlessClassImportTest(unittest.TestCase):
    def test_checked_static_headless_query(self):
        env = dict(os.environ, QORE_JNI_JVM_ARGS="-Xcheck:jni -Djava.awt.headless=true")
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
            env.pop(name, None)
        result = subprocess.run(COMMAND, env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("headless class import completed\n", result.stdout)
        self.assertEqual("", result.stderr)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qore", required=True)
    parser.add_argument("--module", type=Path, required=True)
    args = parser.parse_args()
    COMMAND = [args.qore, "-b", "--enable-debug", "-l", str(args.module.resolve()),
               str(Path(__file__).with_name("headless-class-import.qr"))]
    unittest.main(argv=[__file__, "-v"])
