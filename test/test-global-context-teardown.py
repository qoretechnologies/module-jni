#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check that the exceptions raised when the global context Program is deleted are reported without a crash.

The jni module deletes its global context Program when the module is deleted at process exit.  The destructors of the
objects that the Program holds can raise exceptions; they were kept in a static exception sink that reported them only
when static objects were destroyed, after qore_cleanup() had freed the library's thread data, which crashed the
process (SIGSEGV in currentTZ(); the JVM then aborted).  They must be reported while the library is initialized, as
unhandled exceptions at teardown, which make the exit status 3.

A Qore library that releases the data of the Programs that binary modules own before it deletes the modules raises
and reports the exception itself, before the jni module is deleted; either way it must be reported once, without a
crash.
"""
import argparse
import os
import re
from pathlib import Path
import subprocess
import unittest

COMMAND = None


class GlobalContextTeardownTest(unittest.TestCase):
    def test_throwing_destructor_in_global_context(self):
        env = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
            env.pop(name, None)
        # the global context Program loads the test module with the module path of the process
        mod_dir = str(Path(__file__).with_name("qore-modules"))
        env["QORE_MODULE_DIR"] = mod_dir + os.pathsep + env["QORE_MODULE_DIR"] if env.get("QORE_MODULE_DIR") \
            else mod_dir
        result = subprocess.run(COMMAND, env=env, capture_output=True, text=True, timeout=60)
        output = result.stdout + result.stderr
        self.assertEqual("global context teardown fixture ready\n", result.stdout, output)
        self.assertNotIn("A fatal error has been detected", result.stderr, output)
        # the exception raised by the destructor is reported once as an unhandled exception at teardown: the one it
        # throws, or PROGRAM-ERROR from a library that deletes the module owning its class first
        self.assertEqual(1, len(re.findall(r"unhandled QORE (System|User) exception", result.stderr)), output)
        self.assertRegex(result.stderr, r"\n(TD-JNI-HOLDER-ERROR: raised by the destructor|PROGRAM-ERROR: the Program "
                         r"accessed has already been deleted)", output)
        self.assertEqual(3, result.returncode, output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qore", required=True)
    parser.add_argument("--module", type=Path, required=True)
    args = parser.parse_args()
    COMMAND = [args.qore, "-b", "--enable-debug", "-l", str(args.module.resolve()),
               str(Path(__file__).with_name("global-context-teardown.qr"))]
    unittest.main(argv=[__file__, "-v"])
