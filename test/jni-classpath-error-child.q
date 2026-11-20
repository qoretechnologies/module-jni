#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-classpath-error.qtest with the QORE_JNI_CLASSPATH environment variable set; the arguments are the
# case to run and the directory of the test jars.  This Program does not load the jni module itself, so that the
# module is loaded, and the variable is processed, when a Program that it creates requires the module: an error is
# then raised to this Program, which prints it as "exception: <err>: <desc>".  Each check prints
# "result: <name>: OK", or the values found, and the child prints "end" when it reaches its end.

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"

#! the source of a Program that loads the jni module and lists the classpath of its class loaders
const LoaderSource = "%requires jni
%module-cmd(jni) import java.net.URLClassLoader
%module-cmd(jni) import java.net.URL
list<string> sub chain_urls() {
    list<string> rv = ();
    for (*Jni::java::lang::ClassLoader cl = Jni::org::qore::jni::get_class_loader(); cl; cl = cl.getParent()) {
        if (cl instanceof Jni::java::net::URLClassLoader) {
            foreach Jni::java::net::URL url in (cast<Jni::java::net::URLClassLoader>(cl).getURLs()) {
                rv += url.toString();
            }
        }
    }
    return rv;
}
nothing sub fail_classpath() {
    Jni::org::qore::jni::dbg_jni_fail_point(\"classpath\");
}
";

#! prints the result of a check
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

#! returns a new Program
Program sub new_program() {
    return new Program(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
}

#! parses the source in a new Program and returns it, or prints the exception raised and returns nothing
*Program sub parse_program(string source, string label) {
    Program p = new_program();
    try {
        p.parse(source, label);
    } catch (hash<ExceptionInfo> ex) {
        printf("exception: %s: %s\n", ex.err, ex.desc);
        return;
    }
    return p;
}

#! returns the sorted unique names of the jar files of the directory in the classpath of the Program's class loaders
list<string> sub jar_names(Program p, string dir) {
    hash<string, bool> names;
    foreach string url in (p.callFunction("chain_urls")) {
        *string path = (url =~ x/^jar:file:(.*)!\/$/)[0];
        if (!path) {
            continue;
        }
        path = decode_url(path);
        if (dirname(path) == dir) {
            names{basename(path)} = True;
        }
    }
    return names ? sort(keys names) : ();
}

#! a wildcard pattern with regular expression metacharacters in QORE_JNI_CLASSPATH (qore#6777, qore#6778): the
#! module is loaded, and only the file that the pattern matches literally is added
sub env_wildcard_case(string dir) {
    *Program p = parse_program(LoaderSource, "loader");
    printf("result: env-wildcard: the module is loaded: %y\n", exists p);
    if (p) {
        result("env-wildcard: the matching jar is added", ("a[1.jar",), jar_names(p, dir));
    }
}

#! an error while QORE_JNI_CLASSPATH is processed fails the module's initialization (qore#6778, qore#6779); the
#! parent sets the fail point with QORE_JNI_DBG_FAIL_POINT
sub init_error_case(string dir) {
    *Program p = parse_program("%requires jni\n", "init");
    printf("result: init-error: the module is loaded: %y\n", exists p);
}

#! an error while QORE_JNI_CLASSPATH is processed for a Program that requires the module once it is loaded fails the
#! initialization of the module in that Program (qore#6779)
sub ns_init_error_case(string dir) {
    *Program p = parse_program(LoaderSource, "loader");
    if (!p) {
        return;
    }
    p.callFunction("fail_classpath");
    *Program p2 = parse_program("%requires jni\n", "ns-init");
    printf("result: ns-init-error: the module is initialized in the Program: %y\n", exists p2);
    # without the fail point, the module is initialized in a Program, with the classpath of the variable
    *Program p3 = parse_program(LoaderSource, "loader-ok");
    printf("result: ns-init-error: without an error, the module is initialized in a Program: %y\n", exists p3);
    if (p3) {
        result("ns-init-error: the jar of the variable is added", ("a[1.jar",), jar_names(p3, dir));
    }
}

sub main() {
    string dir = ARGV[1];
    switch (ARGV[0]) {
        case "env-wildcard": env_wildcard_case(dir); break;
        case "init-error": init_error_case(dir); break;
        case "ns-init-error": ns_init_error_case(dir); break;
        default:
            printf("unknown case %y\n", ARGV[0]);
            exit(1);
    }
    printf("end\n");
}

main();
