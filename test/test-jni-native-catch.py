#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Checks that every JNI native method of the module ends with JNI_NATIVE_CATCH() (qore#6739).

No C++ exception may leave a JNI native method: every function in a JNINativeMethod table must be a function-try-block
whose handler is JNI_NATIVE_CATCH(jenv, ...), see design/jni-native-exceptions.md.

usage: test-jni-native-catch.py <source dir>
"""
import re
import sys
import unittest
from pathlib import Path

SRC = None


def natives(text):
    """returns the names of the functions in the JNINativeMethod tables of a source file"""
    rv = []
    for table in re.finditer(r'JNINativeMethod\s+\w+\[\d*\]\s*=\s*\{(.*?)\n\};', text, re.S):
        rv += re.findall(r'reinterpret_cast<void\*>\((\w+)\)', table.group(1))
    return rv


def body_end(text, start):
    """returns the index of the brace that closes the block opened at or after start, skipping comments and literals"""
    depth = 0
    i = start
    n = len(text)
    while i < n:
        if text.startswith('//', i):
            i = text.index('\n', i)
            continue
        if text.startswith('/*', i):
            i = text.index('*/', i) + 2
            continue
        c = text[i]
        if c in '"\'':
            j = i + 1
            while text[j] != c:
                j += 2 if text[j] == '\\' else 1
            i = j + 1
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if not depth:
                return i
        i += 1
    raise ValueError('unbalanced braces')


class NativeCatchTest(unittest.TestCase):
    def test_every_native_has_the_handler(self):
        files = sorted(SRC.glob('src/*.cpp'))
        found = 0
        for path in files:
            text = path.read_text()
            for name in natives(text):
                found += 1
                with self.subTest(native=name, file=path.name):
                    m = re.search(r'\nstatic (\w+) (?:JNICALL )?' + name + r'\(JNIEnv\* jenv,[^{;]*?\) try \{', text)
                    self.assertIsNotNone(m, '%s is not a function-try-block with a JNIEnv* jenv parameter' % name)
                    end = body_end(text, m.end() - 1)
                    tail = text[end:end + 40]
                    self.assertRegex(tail, r'^\} JNI_NATIVE_CATCH\(jenv[,)]',
                        '%s does not end with JNI_NATIVE_CATCH(jenv, ...)' % name)
        # the module registers natives for its Java API, class loader, objects, closures, generated classes and sandbox
        self.assertGreaterEqual(found, 49)

    def test_no_throwing_class_lookup_in_error_paths(self):
        # jni::Env::findClass() throws if the lookup fails: error paths use the classes cached at initialization
        for path in sorted(SRC.glob('src/*.cpp')):
            with self.subTest(file=path.name):
                self.assertNotIn('throwNew(env.findClass(', path.read_text())


if __name__ == '__main__':
    SRC = Path(sys.argv.pop(1)) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    unittest.main()
