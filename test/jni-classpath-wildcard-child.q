#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-classpath-wildcard.qtest in a directory that holds the test jar, which is then the JVM's user.dir,
# with a subdirectory "rel" that holds another copy of the test jar and an empty subdirectory "other"; the argument is
# the case to run, which prints "result: <name>: OK" for each check that passes, or the values found
#
# each case adds a classpath entry that is only a wildcard with no directory part ("*.jar"), which the class loader
# could not add (qore#6740)

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) import java.net.URLClassLoader
%module-cmd(jni) import java.net.URL

#! a class that is only in the test jar
const TestClass = "org.qore.jni.test.Utf8Boundary";

#! the name of the test jar
const TestJarName = "qore-jni-test.jar";

#! the source of a Program that lists the URLs of its own class loader and loads the class of the test jar
const LoaderSource = "%requires jni
%module-cmd(jni) import java.net.URLClassLoader
%module-cmd(jni) import java.net.URL
list<string> sub loader_urls() {
    list<string> rv = ();
    foreach Jni::java::net::URL url in (cast<Jni::java::net::URLClassLoader>(
            Jni::org::qore::jni::get_class_loader()).getURLs()) {
        rv += url.toString();
    }
    return rv;
}
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
string sub load_test_class() {
    return Jni::org::qore::jni::load_class(\"" + TestClass + "\").getName();
}
";

#! the source of a sandboxed Program that adds a bare wildcard classpath entry and imports the class of the test jar
const SandboxSource = "%requires jni
%module-cmd(jni) add-classpath *.jar
%module-cmd(jni) import " + TestClass + "
hash<auto> sub map_of() {
    return Utf8Boundary::mapOf(\"key\", 1);
}
";

#! prints the result of a check
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

#! returns a new Program with the jni module, loader_urls() and load_test_class()
Program sub get_program(*string script_path) {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    if (script_path) {
        p.setScriptPath(script_path);
    }
    p.parse(LoaderSource, "loader");
    return p;
}

#! returns the URLs of the test jar in the classpath of the Program's own class loader
list<string> sub jar_urls(Program p) {
    return select p.callFunction("loader_urls"), $1 =~ /qore-jni-test\.jar/;
}

#! returns the URLs of the test jar in the classpath of the Program's class loader and of its parent class loaders
list<string> sub chain_jar_urls(Program p) {
    return select p.callFunction("chain_urls"), $1 =~ /qore-jni-test\.jar/;
}

#! returns the URLs of the test jar in the classpath of this Program's class loader and of its parent class loaders
list<string> sub chain_jar_urls_here() {
    list<string> rv = ();
    for (*Jni::java::lang::ClassLoader cl = Jni::org::qore::jni::get_class_loader(); cl; cl = cl.getParent()) {
        if (cl instanceof Jni::java::net::URLClassLoader) {
            foreach Jni::java::net::URL url in (cast<Jni::java::net::URLClassLoader>(cl).getURLs()) {
                if (url.toString() =~ /qore-jni-test\.jar/) {
                    rv += url.toString();
                }
            }
        }
    }
    return rv;
}

#! returns the URL that the class loader uses for the test jar in the given directory of the JVM's user.dir
/** the class loader adds the canonical path of a jar as a \c jar: URL

    @param dir the directory, relative to the JVM's user.dir, with a trailing \c "/", or an empty string for user.dir
*/
string sub jar_url(string dir) {
    return "jar:" + (new Jni::java::io::File(get_user_dir() + "/" + dir + TestJarName)).getCanonicalFile().toURI()
        .toString() + "!/";
}

#! returns the JVM's user.dir as a canonical path
string sub get_user_dir() {
    return (new Jni::java::io::File(Jni::java::lang::System::getProperty("user.dir"))).getCanonicalPath();
}

#! returns the name of the class of the test jar that the Program loads, or the error of loading it
string sub load_result(Program p) {
    try {
        return p.callFunction("load_test_class");
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
}

#! returns the name of the class of the test jar that this Program loads, or the error of loading it
string sub load_result_here() {
    try {
        return Jni::org::qore::jni::load_class(TestClass).getName();
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
}

#! changes the current directory of the process, which is not the JVM's user.dir
sub change_dir(string dir) {
    if (chdir(dir)) {
        throw "TEST-ERROR", sprintf("chdir %y: %s", dir, strerror());
    }
}

#! a bare wildcard entry is the matching files in the JVM's user.dir, like any relative entry
sub add_classpath_case() {
    Program p = get_program();
    p.issueModuleCmd("jni", "add-classpath *.jar");
    result("add-classpath: the jar in user.dir is added", (jar_url(""),), jar_urls(p));
    result("add-classpath: the class is loaded", TestClass, load_result(p));

    # the same with the current directory as a directory part, which was always resolved
    p = get_program();
    p.issueModuleCmd("jni", "add-classpath ./*.jar");
    result("add-classpath with ./: the jar in user.dir is added", (jar_url(""),), jar_urls(p));

    # the JVM's user.dir, not the current directory of the process, which has no jar here
    change_dir("other");
    p = get_program();
    p.issueModuleCmd("jni", "add-classpath *.jar");
    result("add-classpath after a change of the current directory: the jar in user.dir is added", (jar_url(""),),
        jar_urls(p));

    # with other entries; a bare wildcard that matches no file adds nothing
    p = get_program();
    p.issueModuleCmd("jni", "add-classpath *.zip" + PathSep + "*.jar");
    result("add-classpath with a wildcard that matches no file: the jar in user.dir is added", (jar_url(""),),
        jar_urls(p));
}

#! a bare wildcard entry of a relative classpath is the matching files in the Program's script directory
sub add_relative_classpath_case() {
    Program p = get_program(get_user_dir() + "/rel/script.q");
    p.issueModuleCmd("jni", "add-relative-classpath *.jar");
    result("add-relative-classpath: the jar in the script directory is added", (jar_url("rel/"),),
        jar_urls(p));
    result("add-relative-classpath: the class is loaded", TestClass, load_result(p));
}

#! the global classpath is the classpath of every Program
sub global_add_classpath_case() {
    # no class loader of a Program has the test jar before
    result("global-add-classpath: no test jar before", (), chain_jar_urls(get_program()));
    get_program().issueModuleCmd("jni", "global-add-classpath *.jar");
    Program p = get_program();
    result("global-add-classpath: the jar in user.dir is added for a new Program", (jar_url(""),),
        chain_jar_urls(p));
    result("global-add-classpath: the class is loaded in a new Program", TestClass, load_result(p));
}

sub global_add_relative_classpath_case() {
    result("global-add-relative-classpath: no test jar before", (), chain_jar_urls(get_program()));
    get_program(get_user_dir() + "/rel/script.q").issueModuleCmd("jni", "global-add-relative-classpath *.jar");
    Program p = get_program();
    result("global-add-relative-classpath: the jar in the script directory is added for a new Program",
        (jar_url("rel/"),), chain_jar_urls(p));
    result("global-add-relative-classpath: the class is loaded in a new Program", TestClass, load_result(p));
}

#! the classpath option of the jdbc driver is added to this Program's class loader when a connection is opened
sub jdbc_case() {
    result("jdbc classpath option: no test jar before", (), chain_jar_urls_here());
    Datasource ds({"type": "jdbc", "user": "u", "pass": "p", "db": "qore-none:x", "options": {"classpath": "*.jar"}});
    # no driver accepts the URL: the classpath is added before the driver is looked up
    try {
        ds.open();
    } catch (hash<ExceptionInfo> ex) {
    }
    result("jdbc classpath option: the jar in user.dir is added", (jar_url(""),), chain_jar_urls_here());
    result("jdbc classpath option: the class is loaded", TestClass, load_result_here());
}

#! QORE_JNI_CLASSPATH is added to this Program's class loader when the module is loaded; the parent test removes any
#! inherited value, so the variable is only the bare wildcard
sub env_case() {
    result("QORE_JNI_CLASSPATH: the jar in user.dir is added", (jar_url(""),), chain_jar_urls_here());
    result("QORE_JNI_CLASSPATH: the class is loaded", TestClass, load_result_here());
}

#! returns the error code of parsing SandboxSource in a new Program with a sandbox that allows reading only the given
#! directory, or "parsed: " and the result of calling the class of the test jar
string sub sandbox_result(string allowed) {
    SandboxManager sm();
    call_object_method(sm, "setUncontrolledApiPolicy", True);
    sm.setFilesystemDefaultPolicy(False);
    sm.addFilesystemAllowedPath(allowed, QSEC_READ);
    Program p(PO_NEW_STYLE);
    p.setSandboxManager(sm);
    try {
        p.parse(SandboxSource, "sandboxed");
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
    return "parsed: " + p.callFunction("map_of").key;
}

#! a bare wildcard entry is checked against the sandbox as it is used: in the JVM's user.dir
sub sandbox_case() {
    if (!(new SandboxManager()).hasCallableMethod("setUncontrolledApiPolicy")) {
        printf("result: sandbox: skipped (this Qore library cannot allow uncontrolled APIs in a sandbox)\n");
        return;
    }
    string user_dir = get_user_dir();
    string other = user_dir + "/other";
    result("sandbox: allowed user.dir", "parsed: 1", sandbox_result(user_dir));
    result("sandbox: user.dir not allowed", "FILESYSTEM-ACCESS-DENIED", sandbox_result(other));
    # the JVM's user.dir is checked, not the current directory of the process
    change_dir(other);
    result("sandbox: user.dir not allowed, the current directory allowed", "FILESYSTEM-ACCESS-DENIED",
        sandbox_result(other));
    result("sandbox: user.dir allowed, the current directory not allowed", "parsed: 1", sandbox_result(user_dir));
}

sub main() {
    switch (ARGV[0]) {
        case "add-classpath": add_classpath_case(); break;
        case "add-relative-classpath": add_relative_classpath_case(); break;
        case "global-add-classpath": global_add_classpath_case(); break;
        case "global-add-relative-classpath": global_add_relative_classpath_case(); break;
        case "jdbc": jdbc_case(); break;
        case "env": env_case(); break;
        case "sandbox": sandbox_case(); break;
        default:
            printf("unknown case %y\n", ARGV[0]);
            exit(1);
    }
}

main();
