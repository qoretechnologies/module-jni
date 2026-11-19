#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-strict-utf8.qtest with ISO-8859-1 as the default encoding (QORE_CHARSET=ISO-8859-1), which is the
# encoding of hash keys, exception codes and the connection values of a jdbc datasource; prints
# "result: <case>: OK" for each case that passes, or the values found; this file has ASCII text only, so that its
# string literals mean the same in any default encoding
#
# with the argument "load-error", it loads a Java class of a module that does not exist instead (in any default
# encoding), so that the test can check its whole output; with "classpath" or "classpath-unmappable", it checks
# classpath entries against a sandbox, in the current directory, which must be empty (see classpath_case())

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.Utf8Boundary
%module-cmd(jni) import org.qore.jni.test.Utf8Driver
%module-cmd(jni) import java.lang.System

#! "f", a-umlaut, sharp s in ISO-8859-1
const Latin1 = <66e4df>;
#! the same in UTF-8
const Utf8 = <66c3a4c39f>;
#! the same as UTF-16 units
const Units = (0x66, 0xe4, 0xdf);

#! U+1F600 in UTF-8, which ISO-8859-1 cannot represent
const EmojiUtf8 = <f09f9880>;

#! returns the code of the exception that the closure raises, or "no error"
string sub error_of(code c) {
    try {
        c();
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
    return "no error";
}

#! prints the result of a case
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

#! returns the error code of parsing the source in a new Program with the sandbox, or "parsed"
string sub parse_sandboxed(SandboxManager sm, string src, *reference<Program> pgm) {
    Program p(PO_NEW_STYLE);
    p.setSandboxManager(sm);
    try {
        p.parse(src, "jni-strict-utf8-classpath");
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
    pgm = p;
    return "parsed";
}

#! the source of a sandboxed Program that adds a classpath and reads the resource "res.txt" through its class loader
const ClasspathSource = "%requires jni
%module-cmd(jni) import org.qore.jni.QoreURLClassLoader
%module-cmd(jni) add-classpath %PATH%
string sub res() {
    return QoreURLClassLoader::getCurrent().getResourceAsStream(\"res.txt\").readAllBytes().toString();
}
";

#! returns the error of adding the classpath in a sandboxed Program, or "parsed: " and the resource it reads (or the
#! error of reading it)
string sub classpath_result(SandboxManager sm, string classpath) {
    Program p;
    string rc = parse_sandboxed(sm, replace(ClasspathSource, "%PATH%", classpath), \p);
    if (rc != "parsed") {
        return rc;
    }
    try {
        return "parsed: " + p.callFunction("res");
    } catch (hash<ExceptionInfo> ex) {
        return "parsed: " + ex.err;
    }
}

#! returns a sandbox that allows reading the given directory only, and the uncontrolled APIs that the Program's code
#! needs to read the resource through Java; NOTHING if this Qore library cannot allow them
*SandboxManager sub classpath_sandbox(string allowed) {
    SandboxManager sm();
    if (!sm.hasCallableMethod("setUncontrolledApiPolicy")) {
        return;
    }
    call_object_method(sm, "setUncontrolledApiPolicy", True);
    sm.setFilesystemDefaultPolicy(False);
    sm.addFilesystemAllowedPath(allowed, QSEC_READ);
    return sm;
}

#! makes a directory with the resource "res.txt" with the given content
sub make_res_dir(string dir, string content) {
    mkdir(dir);
    File f();
    f.open2(dir + DirSep + "res.txt", O_CREAT | O_WRONLY | O_TRUNC);
    f.write(content);
}

#! removes the directories made by make_res_dir(), the innermost first, ignoring errors
sub remove_res_dirs(list<string> dirs) {
    foreach string dir in (dirs) {
        try {
            unlink(dir + DirSep + "res.txt");
        } catch () {
        }
        try {
            rmdir(dir);
        } catch () {
        }
    }
}

#! classpath entries in the default encoding, with a non-ASCII character, are checked against the sandbox policy as
#! the JVM uses them
/** Run with ISO-8859-1 as the default encoding and as the JVM's file name encoding (LC_ALL=en_US.iso88591), in an
    empty directory, which is the JVM's \c user.dir
*/
sub classpath_case() {
    string jnu = System::getProperty("sun.jnu.encoding") ?? "<none>";
    if (jnu != "ISO-8859-1") {
        printf("result: classpath: skipped (the JVM's file name encoding is %s)\n", jnu);
        return;
    }
    # "e" with an acute accent in ISO-8859-1
    string e = binary_to_string(<e9>, "ISO-8859-1");
    string base = getcwd();
    string allowed = base + DirSep + "allowed" + e;
    string outside = base + DirSep + "outside" + e;
    string allowed_sub = allowed + DirSep + "sub";
    string outside_sub = base + DirSep + "sub";
    on_exit remove_res_dirs((allowed_sub, outside_sub, allowed, outside));
    make_res_dir(allowed, "allowed");
    make_res_dir(outside, "outside");
    make_res_dir(allowed_sub, "allowed-sub");
    make_res_dir(outside_sub, "outside-sub");

    *SandboxManager sm = classpath_sandbox(allowed);
    if (!sm) {
        printf("result: classpath: skipped (this Qore library cannot allow uncontrolled APIs in a sandbox)\n");
        return;
    }
    result("classpath entry in an allowed directory", "parsed: allowed", classpath_result(sm, allowed));
    result("classpath entry outside the allowed directory", "FILESYSTEM-ACCESS-DENIED",
        classpath_result(sm, outside));
    # the entries of one classpath are checked each
    result("classpath with an allowed and a refused entry", "FILESYSTEM-ACCESS-DENIED",
        classpath_result(sm, allowed + ":" + outside));
    # a wildcard entry is checked as its directory with the pattern
    result("wildcard classpath entry in an allowed directory", "parsed: <no resource>",
        replace(classpath_result(sm, allowed + DirSep + "*.jar"), "parsed: PSEUDO-METHOD-DOES-NOT-EXIST",
            "parsed: <no resource>"));
    result("wildcard classpath entry outside the allowed directory", "FILESYSTEM-ACCESS-DENIED",
        classpath_result(sm, outside + DirSep + "*.jar"));
    # a relative entry is resolved as the JVM resolves it (against its user.dir, this directory), not against the
    # current directory of the process: in the allowed directory, "sub" is the JVM's ./sub, outside it
    chdir(allowed);
    on_exit chdir(base);
    result("relative classpath entry after a change of the current directory", "FILESYSTEM-ACCESS-DENIED",
        classpath_result(sm, "sub"));
}

#! a classpath entry that the JVM's file name encoding cannot represent exactly is refused under a sandbox
/** Run with UTF-8 as the default encoding and ISO-8859-1 as the JVM's file name encoding, in an empty directory
*/
sub classpath_unmappable_case() {
    string jnu = System::getProperty("sun.jnu.encoding") ?? "<none>";
    if (jnu != "ISO-8859-1") {
        printf("result: classpath: skipped (the JVM's file name encoding is %s)\n", jnu);
        return;
    }
    string allowed = getcwd() + DirSep + "allowed";
    on_exit remove_res_dirs((allowed,));
    make_res_dir(allowed, "allowed");
    *SandboxManager sm = classpath_sandbox(allowed);
    if (!sm) {
        printf("result: classpath: skipped (this Qore library cannot allow uncontrolled APIs in a sandbox)\n");
        return;
    }
    result("classpath entry that the JVM cannot represent", "JNI-SANDBOX-ERROR",
        classpath_result(sm, allowed + DirSep + binary_to_string(EmojiUtf8, "UTF-8")));
}

sub main() {
    if (ARGV[0] == "classpath") {
        classpath_case();
        return;
    }
    if (ARGV[0] == "classpath-unmappable") {
        classpath_unmappable_case();
        return;
    }
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
    string emoji = binary_to_string(EmojiUtf8, "UTF-8");

    # a hash key, in the default encoding, as a Java Map key
    hash<auto> h = {fass: 1};
    result("Qore hash key to Java", Units, Utf8Boundary::firstKeyUnits(h));

    # a Java Map key as a hash key, in the default encoding
    h = Utf8Boundary::mapOf(fass, 1);
    result("Java Map key to Qore", Latin1, binary((keys h)[0]));

    # a Java Map key that the default encoding cannot represent (U+1F600) cannot be a hash key
    result("Java Map key that the default encoding cannot represent", "ENCODING-CONVERSION-ERROR",
        error_of(sub () { Utf8Boundary::mapOf(emoji, 1); }));

    # the code of a QoreException thrown in Java, in the default encoding
    try {
        Utf8Boundary::throwQore(fass, "desc");
    } catch (hash<ExceptionInfo> ex) {
        result("QoreException code", Latin1, binary(ex.err));
    }

    # the code of a QoreException that the default encoding cannot represent is kept in UTF-8
    try {
        Utf8Boundary::throwQore(emoji, "desc");
    } catch (hash<ExceptionInfo> ex) {
        result("QoreException code that the default encoding cannot represent", EmojiUtf8, binary(ex.err));
    }

    # a module command, which Program::issueModuleCmd() passes in the default encoding
    {
        Program p(PO_NEW_STYLE);
        p.loadModule("jni");
        p.issueModuleCmd("jni", "set-property qore.test.utf8.child " + fass);
        result("module command", Units, Utf8Boundary::propertyUnits("qore.test.utf8.child"));

        # the error of a module command shows its argument as given, in the default encoding, as the command that
        # Qore appends to the description is
        *string desc;
        try {
            p.issueModuleCmd("jni", "import x" + fass + "*");
        } catch (hash<ExceptionInfo> ex) {
            desc = ex.desc;
        }
        string expected = "invalid import argument: 'x" + fass + "*'";
        result("module command error text", expected, (desc ?? "").substr(0, expected.length()));
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

    # a JDBC column label that the default encoding cannot represent cannot be a hash key
    Utf8Driver::labels = (emoji,);
    result("JDBC column label that the default encoding cannot represent", "ENCODING-CONVERSION-ERROR",
        error_of(sub () { ds.selectRow("select 1"); }));

    # a JDBC metadata name, a UTF-8 string
    Utf8Driver::productName = fass;
    result("JDBC metadata name", Utf8, binary(convert_encoding(ds.getServerVersion().name, "UTF-8")));
}

main();
