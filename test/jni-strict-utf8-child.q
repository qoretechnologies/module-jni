#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-strict-utf8.qtest with ISO-8859-1 as the default encoding (QORE_CHARSET=ISO-8859-1), which is the
# encoding of hash keys, exception codes and the connection values of a jdbc datasource; prints
# "result: <case>: OK" for each case that passes, or the values found; this file has ASCII text only, so that its
# string literals mean the same in any default encoding
#
# with the argument "load-error", it loads a Java class of a module that does not exist instead (in any default
# encoding), so that the test can check its whole output

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.Utf8Boundary
%module-cmd(jni) import org.qore.jni.test.Utf8Driver

#! "f", a-umlaut, sharp s in ISO-8859-1
const Latin1 = <66e4df>;
#! the same in UTF-8
const Utf8 = <66c3a4c39f>;
#! the same as UTF-16 units
const Units = (0x66, 0xe4, 0xdf);

#! prints the result of a case
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

sub main() {
    if (ARGV[0] == "load-error") {
        # Java loads a class of a module that does not exist: the class is not found, and the error of the module
        # is raised in Java only
        *string msg = Utf8Boundary::loadError("qoremod.JniUtf8NoSuchModule.Cls");
        printf("result: load error: %s\n", msg ? "OK" : sprintf("%y", msg));
        return;
    }
    if (get_default_encoding() != "ISO-8859-1") {
        printf("the default encoding is %y; expected ISO-8859-1\n", get_default_encoding());
        exit(1);
    }

    string fass = binary_to_string(Latin1, "ISO-8859-1");

    # a hash key, in the default encoding, as a Java Map key
    hash<auto> h = {fass: 1};
    result("Qore hash key to Java", Units, Utf8Boundary::firstKeyUnits(h));

    # a Java Map key as a hash key, in the default encoding
    h = Utf8Boundary::mapOf(fass, 1);
    result("Java Map key to Qore", Latin1, binary((keys h)[0]));

    # the code of a QoreException thrown in Java, in the default encoding
    try {
        Utf8Boundary::throwQore(fass, "desc");
    } catch (hash<ExceptionInfo> ex) {
        result("QoreException code", Latin1, binary(ex.err));
    }

    # a module command, which Program::issueModuleCmd() passes in the default encoding
    {
        Program p(PO_NEW_STYLE);
        p.loadModule("jni");
        p.issueModuleCmd("jni", "set-property qore.test.utf8.child " + fass);
        result("module command", Units, Utf8Boundary::propertyUnits("qore.test.utf8.child"));
    }

    # the connection values of a jdbc datasource, in the default encoding
    Utf8Driver::install();
    Datasource ds("jdbc", fass, fass, "qore-utf8:" + fass);
    on_exit ds.rollback();
    ds.open();
    result("JDBC user", Units, Utf8Driver::recorded("user"));
    result("JDBC password", Units, Utf8Driver::recorded("password"));
    # "jdbc:qore-utf8:" is 15 characters
    result("JDBC URL", Units, Utf8Driver::recorded("url")[15..]);

    # a JDBC column label, which is a hash key of the results
    Utf8Driver::labels = (fass,);
    hash<auto> row = ds.selectRow("select 1");
    result("JDBC column label", Latin1, binary((keys row)[0]));

    # a JDBC metadata name, a UTF-8 string
    Utf8Driver::productName = fass;
    result("JDBC metadata name", Utf8, binary(convert_encoding(ds.getServerVersion().name, "UTF-8")));
}

main();
