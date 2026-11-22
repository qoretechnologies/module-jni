#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-classpath-lookup-hardening.qtest in a UTF-8 locale and with a temporary directory of its own; the
# arguments are the case and the root of the test tree.  It prints "result: <case>: OK", the values found, or
# "result: skipped: <reason>" if the JVM's file names are not in UTF-8

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) add-relative-classpath ../build/qore-jni-compiler.jar
%module-cmd(jni) import org.qore.jni.test.ClasspathLookupProbe

const TenantSource = "%modern
%requires jni
%module-cmd(jni) import org.qore.jni.test.ClasspathLookupProbe
object sub own_loader() {
    return get_class_loader();
}
string sub lookup(string op, string name) {
    return ClasspathLookupProbe::lookup(get_class_loader(), op, name);
}
string sub locations_hold(string rel) {
    return ClasspathLookupProbe::locationsHold(get_class_loader(), \"\", rel);
}
";

const EntriesProp = "qore.jni.compiler.maxEntries";
const EncodingProp = "sun.jnu.encoding";

list<string> sub module_dirs() {
    list<string> rv = (get_script_dir() + "/../build", get_script_dir() + "/../qlib");
    rv += map $1, (ENV.QORE_MODULE_DIR ?? "").split(PathSep), $1.val();
    *string fn = get_module_hash().jni.filename;
    if (fn) {
        rv += dirname(fn);
    }
    return rv;
}

SandboxManager sub get_policy(string root, list<string> dirs) {
    SandboxManager sm();
    sm.setUncontrolledApiPolicy(True);
    sm.setFilesystemDefaultPolicy(False);
    foreach string d in (dirs) {
        sm.addFilesystemAllowedPath(root + "/" + d, QSEC_READ);
    }
    foreach string d in (module_dirs()) {
        sm.addFilesystemAllowedPath(d, QSEC_READ);
    }
    sm.addFilesystemDeniedPath(root + "/allowed/denied");
    return sm;
}

Program sub new_program(SandboxManager sm, string classpath) {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.parse(TenantSource, "tenant");
    p.setSandboxManager(sm);
    p.issueModuleCmd("jni", "add-classpath " + classpath);
    return p;
}

sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

sub write_file(string path, string data) {
    File f();
    f.open2(path, O_CREAT | O_WRONLY | O_TRUNC, 0600);
    f.write(data);
    f.close();
}

#! a real path that the file name encoding cannot represent is denied
sub lossy_case(string root) {
    string lossy = root + "/bytes/" + binary_to_string(<64656eff>, "UTF-8");
    mkdir(lossy, 0700);
    on_exit rmdir(lossy);
    write_file(lossy + "/s.txt", "denied");
    on_exit unlink(lossy + "/s.txt");
    symlink(lossy + "/s.txt", root + "/allowed/bytes-link.txt");
    on_exit unlink(root + "/allowed/bytes-link.txt");
    SandboxManager sm = get_policy(root, ("allowed", "bytes"));
    sm.addFilesystemDeniedPath(lossy);
    Program p = new_program(sm, root + "/allowed");
    result("lossy", "missing", p.callFunction("lookup", "getResource", "bytes-link.txt"));
}

#! a class with a non-ASCII name from a later entry has the location of that entry
sub non_ascii_case(string root) {
    Program p = new_program(get_policy(root, ("allowed", "second")), root + "/allowed:" + root + "/second");
    string location = p.callFunction("lookup", "location", "denied.Bóth");
    result("nonascii", True, location =~ /\/second\/$/);
}

#! the file name encoding is fixed at startup
sub charset_case(string root) {
    string dir = root + "/allowed/dé";
    mkdir(dir, 0700);
    on_exit rmdir(dir);
    write_file(dir + "/n.txt", "denied");
    on_exit unlink(dir + "/n.txt");
    SandboxManager sm = get_policy(root, ("allowed",));
    sm.addFilesystemDeniedPath(dir);
    Program p = new_program(sm, root + "/allowed");
    result("charset before", "missing", p.callFunction("lookup", "getResource", "dé/n.txt"));
    ClasspathLookupProbe::setProperty(EncodingProp, "ISO-8859-1");
    on_exit ClasspathLookupProbe::setProperty(EncodingProp, "UTF-8");
    result("charset jdk", True, ClasspathLookupProbe::pathExists(dir + "/n.txt"));
    result("charset", "missing", p.callFunction("lookup", "getResource", "dé/n.txt"));
}

#! a compiler copy that goes over a limit leaves nothing
sub budget_case(string root) {
    Program p = new_program(get_policy(root, ("links",)), root + "/links/l0");
    ClasspathLookupProbe::setProperty(EntriesProp, "1000");
    on_exit ClasspathLookupProbe::setProperty(EntriesProp, NOTHING);
    result("budget refused", "refused: IOException", p.callFunction("locations_hold", "x.class"));
    result("budget", 0, ClasspathLookupProbe::copyDirectories());
}

string case_name = ARGV[0];
string root = ARGV[1];
if (ClasspathLookupProbe::fileNameEncoding() != "UTF-8") {
    printf("result: skipped: the file names of the JVM are in %s\n", ClasspathLookupProbe::fileNameEncoding());
    exit(0);
}
switch (case_name) {
    case "lossy": lossy_case(root); break;
    case "nonascii": non_ascii_case(root); break;
    case "charset": charset_case(root); break;
    case "budget": budget_case(root); break;
    default: throw "TEST-ERROR", sprintf("unknown case %y", case_name);
}
