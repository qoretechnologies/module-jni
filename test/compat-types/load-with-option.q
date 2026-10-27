#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.

# Run by JniCompatTypesOption.qtest: sets the "compat-types" module option of the jni module to the value named by
# the argument (true, false, null-literal, null-variable), loads the jni module, and prints "RESULT: " and the type of
# the value of a Java byte[]: "list" with compat types, "binary" without

%modern

%prepend-module-path "${SCRIPT_DIR}/../../build"

switch (ARGV[0]) {
    case "true": set_module_option("jni", "compat-types", True); break;
    case "false": set_module_option("jni", "compat-types", False); break;
    case "null-literal": set_module_option("jni", "compat-types", NULL); break;
    case "null-variable": {
        auto n = NULL;
        set_module_option("jni", "compat-types", n);
        break;
    }
    default: throw "ARG-ERROR", sprintf("unknown argument %y", ARGV[0]);
}

load_module("jni");

Program pgm();
pgm.parse("%modern\n%requires jni\n%module-cmd(jni) import java.lang.String\n"
    "string sub get() { return (new String(\"ab\")).getBytes().type(); }\n", "compat-types");
printf("RESULT: %s\n", pgm.callFunction("get"));
