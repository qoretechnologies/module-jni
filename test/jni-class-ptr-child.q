#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
# The child of jni-class-ptr.qtest: Java code gives the class loader a forged class pointer, which must be refused
# instead of being used as a class (qore#6854); before the fix, the process crashed

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.ClassPtrProbe

foreach string method in (("loadClassWithPtr", "generateByteCode")) {
    printf("%s: %s\n", method, ClassPtrProbe::call(get_class_loader(), method, "qore.ForgedClass", 0x1000));
}
