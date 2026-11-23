#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-classpath-manifest-sandbox.qtest; the arguments are the case to run and the root of the test tree
# that the parent made (see the parent for its layout).  Each check prints "result: <name>: OK", or the values found,
# and the child prints "end" when it reaches its end.

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
# the jni module of this tree is loaded here, by trusted code: sandboxed code cannot load a module from outside the
# module path under the name of a module in the module path (qore#6776), and the sandboxed Programs here use the
# module, they do not test loading it
%requires jni

#! the package of the classes of the test jars
const Pkg = "org.qore.jni.test.cpman.";

#! the JDK feature versions on which manifest Class-Path attributes are expected to be checked and followed
/** Deliberately not read from the product (QoreURLClassLoader.VERIFIED_CLASS_PATH_JDK_FEATURES): a change to that set
    must fail this test until the expectation is updated with it.  On any other JDK, a sandboxed classpath with a
    manifest Class-Path attribute is expected to be refused (qore#7204).
*/
const VerifiedJdkFeatures = (21, 25);

#! a sandboxed classpath that is expected to be refused on a JDK outside VerifiedJdkFeatures (qore#7204)
hashdecl RefusedClasspath {
    #! the name of the check
    string label;
    #! the directories of the test tree that the policy allows
    list<string> allowed;
    #! the paths of the test tree that the policy denies
    list<string> denied = ();
    #! the classpath entries, relative to the root of the test tree
    list<string> classpath;
    #! the jars, relative to the root, of which the refusal can name one (a wildcard's matches are in no fixed order)
    list<string> jars;
    #! the simple names of the classes that must not be loaded
    list<string> classes;
    #! True if the policy also allows /dev/null
    bool device = False;
}

#! the source of a Program that lists the classpath of its class loaders and loads classes
const LoaderSource = "%requires jni
%module-cmd(jni) import java.lang.ClassLoader
%module-cmd(jni) import java.lang.Class
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
bool sub can_load(string cls) {
    try {
        Jni::java::lang::ClassLoader cl = Jni::org::qore::jni::get_class_loader();
        cl.loadClass(cls);
        return True;
    } catch (hash<ExceptionInfo> ex) {
        return False;
    }
}
string sub class_location(string cls) {
    try {
        Jni::java::lang::ClassLoader cl = Jni::org::qore::jni::get_class_loader();
        Jni::java::lang::Class c = cl.loadClass(cls);
        return c.getProtectionDomain().getCodeSource().getLocation().toString();
    } catch (hash<ExceptionInfo> ex) {
        return ex.err;
    }
}
";

#! prints the result of a check
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

#! returns the directories of the module path, which the sandbox allows reading
list<string> sub module_dirs() {
    list<string> rv = (get_script_dir() + "/../build", get_script_dir() + "/../qlib");
    rv += map $1, (ENV.QORE_MODULE_DIR ?? "").split(PathSep), $1.val();
    *string fn = get_module_hash().jni.filename;
    if (fn) {
        rv += dirname(fn);
    }
    return rv;
}

#! returns a sandbox that allows reading the given directories of the test tree, and denies the given paths
SandboxManager sub get_sandbox(string root, list<string> allowed, list<string> denied = ()) {
    SandboxManager sm();
    # the Program inspects its class loader with Java calls; the filesystem policy is what is tested
    call_object_method(sm, "setUncontrolledApiPolicy", True);
    sm.setFilesystemDefaultPolicy(False);
    foreach string dir in (allowed) {
        sm.addFilesystemAllowedPath(root + DirSep + dir, QSEC_READ);
    }
    foreach string dir in (module_dirs()) {
        sm.addFilesystemAllowedPath(dir, QSEC_READ);
    }
    foreach string path in (denied) {
        sm.addFilesystemDeniedPath(root + DirSep + path);
    }
    return sm;
}

#! returns a new Program with the loader source, in the sandbox if given
Program sub new_program(*SandboxManager sm) {
    Program p(PO_NEW_STYLE);
    if (sm) {
        p.setSandboxManager(sm);
    }
    p.parse(LoaderSource, "loader");
    return p;
}

#! issues add-classpath in the Program as trusted code; returns "ok" or the error and its description
hash<auto> sub add_classpath(Program p, string cp) {
    try {
        p.issueModuleCmd("jni", "add-classpath " + cp);
        return {"err": "ok", "desc": "", "cp": cp};
    } catch (hash<ExceptionInfo> ex) {
        return {"err": ex.err, "desc": ex.desc, "cp": cp};
    }
}

#! returns the sorted paths, relative to the root, of the jar files under the root in the Program's classpath
list<string> sub jar_paths(Program p, string root) {
    hash<string, bool> paths;
    foreach string url in (p.callFunction("chain_urls")) {
        *string path = (url =~ x/^jar:file:(.*)!\/$/)[0] ?? (url =~ x/^file:(.*)$/)[0];
        if (!path) {
            continue;
        }
        path = decode_url(path);
        if (path.equalPartial(root + DirSep)) {
            paths{path.substr(root.size() + 1)} = True;
        }
    }
    return paths ? sort(keys paths) : ();
}

#! returns True if the Program can load the class of the test jars with the given simple name
bool sub loads(Program p, string cls) {
    return p.callFunction("can_load", Pkg + cls);
}

#! returns the path, relative to the root, of the jar that the Program loads the given class from, or the error
string sub location(Program p, string root, string cls) {
    string loc = p.callFunction("class_location", Pkg + cls);
    *string path = (loc =~ x/^file:(.*)$/)[0];
    if (!path) {
        return loc;
    }
    path = decode_url(path);
    return path.equalPartial(root + DirSep) ? path.substr(root.size() + 1) : path;
}

#! a manifest Class-Path that names a jar that the policy denies refuses the whole classpath
sub deny_case(string root) {
    SandboxManager sm = get_sandbox(root, ("d",), ("d/lib",));
    Program p = new_program(sm);
    hash<auto> h = add_classpath(p, root + "/d/cp-ref.jar");
    result("deny: the classpath is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: the error names no resolved path", -1, h.desc.find(root + "/d/lib/cp-target.jar"));
    result("deny: the error names the entry as it was given", True, h.desc.find("'" + root + "/d/cp-ref.jar'") >= 0);
    result("deny: nothing is added", (), jar_paths(p, root));
    result("deny: the class of the referring jar is not loaded", False, loads(p, "CpRef"));
    result("deny: the class of the denied jar is not loaded", False, loads(p, "CpTarget"));

    # a jar that a wildcard selects is expanded in the same way
    p = new_program(sm);
    h = add_classpath(p, root + "/d/*.jar");
    result("deny: a wildcard that selects the referring jar is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error names no resolved path", -1, h.desc.find(root + "/d/lib/cp-target.jar"));
    result("deny: its error names no file that the wildcard selects", -1, h.desc.find(root + "/d/cp-ref.jar"));
    result("deny: its error names the wildcard as it was given", True, h.desc.find("'" + root + "/d/*.jar'") >= 0);
    result("deny: the wildcard adds nothing", (), jar_paths(p, root));

    # an allowed entry before the referring jar is not added either
    p = new_program(get_sandbox(root, ("a", "d"), ("d/lib",)));
    h = add_classpath(p, root + "/a/cp-ref.jar" + PathSep + root + "/d/cp-ref.jar");
    result("deny: a classpath with a refused entry is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: no entry of it is added", (), jar_paths(p, root));
    result("deny: the class of its allowed entry is not loaded", False, loads(p, "CpRef"));

    # a denied jar deeper in the manifest chain refuses the classpath
    p = new_program(get_sandbox(root, ("n",), ("n/b/c",)));
    h = add_classpath(p, root + "/n/cp-nesta.jar");
    result("deny: a denied jar at the end of a chain is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: the error names no jar of the chain", -1, h.desc.find(root + "/n/b/"));
    result("deny: the error names the entry as it was given", True, h.desc.find("'" + root + "/n/cp-nesta.jar'") >= 0);
    result("deny: no jar of the chain is added", (), jar_paths(p, root));
    result("deny: the class of the first jar of the chain is not loaded", False, loads(p, "NestA"));

    # a Class-Path entry that is not a file cannot be checked, and refuses the classpath
    p = new_program(get_sandbox(root, ("h",)));
    h = add_classpath(p, root + "/h/cp-http.jar");
    result("deny: a Class-Path entry that is not a file is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: the error names the jar whose manifest names it", True, h.desc.find(root + "/h/cp-http.jar") >= 0);
    result("deny: the jar with the entry is not added", (), jar_paths(p, root));
    result("deny: the class of the jar with the entry is not loaded", False, loads(p, "CpHttp"));

    # a .zip file, which the class loader opens with a file URL rather than a jar URL, is expanded in the same way
    p = new_program(sm);
    h = add_classpath(p, root + "/d/cp-ref.zip");
    result("deny: a zip file whose manifest names the denied jar is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error names no resolved path", -1, h.desc.find(root + "/d/lib/cp-target.jar"));
    result("deny: its error names the zip file as it was given", True, h.desc.find("'" + root + "/d/cp-ref.zip'") >= 0);
    result("deny: the zip file is not added", (), jar_paths(p, root));

    # a named jar whose manifest cannot be read cannot be checked, and refuses the classpath
    p = new_program(get_sandbox(root, ("j",)));
    h = add_classpath(p, root + "/j/cp-ref.jar");
    result("deny: a named file that is not a jar is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error names the file", True, h.desc.find(root + "/j/lib/cp-target.jar") >= 0);
    result("deny: the jar that names it is not added", (), jar_paths(p, root));

    # a named file that is not a regular file is not opened, and refuses the classpath, even if the policy allows it
    SandboxManager smv = get_sandbox(root, ("v",));
    smv.addFilesystemAllowedPath("/dev/null", QSEC_READ);
    p = new_program(smv);
    h = add_classpath(p, root + "/v/cp-ref.jar");
    result("deny: a named file that is not a regular file is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error says so", True, h.desc =~ /not a regular file/);
    result("deny: the jar that names the special file is not added", (), jar_paths(p, root));

    deny_not_regular(root, smv);
}

#! a classpath entry or a wildcard match that is not a regular file is refused, on any JDK, and not opened
/** The refusal of a file that is not a regular file is reached before the refusal of any manifest Class-Path of the
    same classpath, as the latter is added after the elements that were listed.
*/
sub deny_not_regular(string root, SandboxManager smv) {
    Program p = new_program(smv);
    hash<auto> h = add_classpath(p, root + "/v/fifo.jar");
    result("deny: an entry that is not a regular file is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error says that it is not a regular file", True, h.desc =~ /not a regular file/);
    result("deny: the entry is not added", (), jar_paths(p, root));
    # v/*.jar selects the regular jar cp-ref.jar and the FIFO
    p = new_program(smv);
    h = add_classpath(p, root + "/v/*.jar");
    result("deny: a wildcard that selects a file that is not a regular file is refused", "JNI-CLASSPATH-ERROR",
        h.err);
    result("deny: its error says that the match is not a regular file", True, h.desc =~ /not a regular file/);
    result("deny: the regular jar of the wildcard is not added either", (), jar_paths(p, root));
    result("deny: the class of the regular jar of the wildcard is not loaded", False, loads(p, "CpRef"));
    # a match that is a link to a device is refused in the same way
    p = new_program(smv);
    h = add_classpath(p, root + "/v/lib/*.jar");
    result("deny: a wildcard that selects a link to a device is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: its error says that the device is not a regular file", True, h.desc =~ /not a regular file/);
}

#! a manifest Class-Path through a symbolic link out of the allowed directory refuses the classpath
sub escape_case(string root) {
    Program p = new_program(get_sandbox(root, ("s",)));
    hash<auto> h = add_classpath(p, root + "/s/cp-ref.jar");
    result("escape: the classpath is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("escape: the error does not name the file that the link points to", -1, h.desc.find(root + "/outside"));
    result("escape: the error names the entry as it was given", True, h.desc.find("'" + root + "/s/cp-ref.jar'") >= 0);
    result("escape: nothing is added", (), jar_paths(p, root));
    result("escape: the class of the referring jar is not loaded", False, loads(p, "CpRef"));
    result("escape: the class of the jar outside is not loaded", False, loads(p, "CpTarget"));
}

#! a relative Class-Path entry is resolved against the location of the jar as the class loader opens it
sub relative_case(string root) {
    # w/up is a link to o/deep, whose jar names ../lib/cp-target.jar: the class loader resolves that against the
    # URL of w/up/cp-up.jar, as w/lib/cp-target.jar, and not against the real path of the jar (o/lib/cp-target.jar)
    Program p = new_program(get_sandbox(root, ("w", "o"), ("w/lib",)));
    hash<auto> h = add_classpath(p, root + "/w/cp-upref.jar");
    result("relative: the jar that the class loader would open is checked", "JNI-CLASSPATH-ERROR", h.err);
    result("relative: the error names no resolved path", -1, h.desc.find(root + "/w/lib/cp-target.jar"));
    result("relative: the error names the entry as it was given", True,
        h.desc.find("'" + root + "/w/cp-upref.jar'") >= 0);
    result("relative: nothing is added", (), jar_paths(p, root));
    result("relative: the class of the jar is not loaded", False, loads(p, "CpTarget"));

    p = new_program(get_sandbox(root, ("w", "o")));
    h = add_classpath(p, root + "/w/cp-upref.jar");
    result("relative: the allowed chain is added", "ok", h.err);
    result("relative: the class of the jar in the linked directory is loaded", True, loads(p, "CpUp"));
    result("relative: the class is loaded from the checked jar", "w/lib/cp-target.jar", location(p, root, "CpTarget"));
}

#! a cycle of manifest Class-Path attributes is expanded once
sub cycle_case(string root) {
    Program p = new_program(get_sandbox(root, ("cy",)));
    hash<auto> h = add_classpath(p, root + "/cy/cp-cyca.jar");
    result("cycle: the classpath is added", "ok", h.err);
    result("cycle: the given jar is added", ("cy/cp-cyca.jar",), jar_paths(p, root));
    result("cycle: the class of the first jar is loaded", True, loads(p, "CycA"));
    result("cycle: the class of the second jar is loaded", True, loads(p, "CycB"));

    p = new_program(get_sandbox(root, ("cy",)));
    h = add_classpath(p, root + "/cy/*.jar");
    result("cycle: a wildcard of the cycle is added", "ok", h.err);
    result("cycle: both jars are added", ("cy/cp-cyca.jar", "cy/cp-cycb.jar"), jar_paths(p, root));
    result("cycle: the class of the second jar is loaded with the wildcard", True, loads(p, "CycB"));
}

#! a chain of manifest Class-Path attributes of allowed jars is followed
sub nested_case(string root) {
    Program p = new_program(get_sandbox(root, ("n",)));
    hash<auto> h = add_classpath(p, root + "/n/cp-nesta.jar");
    result("nested: the classpath is added", "ok", h.err);
    result("nested: the class of the first jar is loaded", True, loads(p, "NestA"));
    result("nested: the class of the second jar is loaded", True, loads(p, "NestB"));
    result("nested: the class of the third jar is loaded", True, loads(p, "NestC"));
    result("nested: the third jar is resolved against the second", "n/b/c/cp-nestc.jar", location(p, root, "NestC"));
}

#! a manifest Class-Path that names an allowed jar is followed
sub allowed_case(string root) {
    Program p = new_program(get_sandbox(root, ("a",)));
    hash<auto> h = add_classpath(p, root + "/a/cp-ref.jar");
    result("allowed: the classpath is added", "ok", h.err);
    result("allowed: the given jar is added", ("a/cp-ref.jar",), jar_paths(p, root));
    result("allowed: the class of the referring jar is loaded", True, loads(p, "CpRef"));
    result("allowed: the class of the named jar is loaded", True, loads(p, "CpTarget"));
    result("allowed: the class is loaded from the named jar", "a/lib/cp-target.jar", location(p, root, "CpTarget"));

    # a named jar that does not exist, in the allowed directory, is checked as it is named, and is not an error
    p = new_program(get_sandbox(root, ("m",)));
    h = add_classpath(p, root + "/m/cp-ref.jar");
    result("allowed: a missing named jar is not an error", "ok", h.err);
    result("allowed: the class of the jar that names it is loaded", True, loads(p, "CpRef"));
    result("allowed: no class of the missing jar is loaded", False, loads(p, "CpTarget"));
}

#! with no sandbox, manifest Class-Path attributes are followed as before
sub unsandboxed_case(string root) {
    Program p = new_program();
    hash<auto> h = add_classpath(p, root + "/d/cp-ref.jar");
    result("unsandboxed: the classpath is added", "ok", h.err);
    result("unsandboxed: the given jar is added", ("d/cp-ref.jar",), jar_paths(p, root));
    result("unsandboxed: the class of the named jar is loaded", True, loads(p, "CpTarget"));
    result("unsandboxed: the class is loaded from the named jar", "d/lib/cp-target.jar", location(p, root, "CpTarget"));

    p = new_program();
    result("unsandboxed: a zip file is added", "ok", add_classpath(p, root + "/d/cp-ref.zip").err);
    result("unsandboxed: the class of the jar that the zip file names is loaded", True, loads(p, "CpTarget"));

    p = new_program();
    result("unsandboxed: a Class-Path through a link is added", "ok", add_classpath(p, root + "/s/cp-ref.jar").err);
    result("unsandboxed: the class of the linked jar is loaded", True, loads(p, "CpTarget"));

    p = new_program();
    result("unsandboxed: a Class-Path entry that is not a file is ignored", "ok",
        add_classpath(p, root + "/h/cp-http.jar").err);
    result("unsandboxed: the class of the jar with the entry is loaded", True, loads(p, "CpHttp"));

    p = new_program();
    result("unsandboxed: a cycle is added", "ok", add_classpath(p, root + "/cy/cp-cyca.jar").err);
    result("unsandboxed: the class of the second jar of the cycle is loaded", True, loads(p, "CycB"));
}

#! makes the check of manifest Class-Path attributes assume a JDK feature version, or the running JDK's with none
/** with Jni::org::qore::jni::dbg_jni_set_classpath_jdk_feature(), which only a debug build of the module has
*/
sub set_jdk_feature(*int feature) {
    Program p(PO_NEW_STYLE);
    p.parse("%requires jni
sub set(*int f) { Jni::org::qore::jni::dbg_jni_set_classpath_jdk_feature(f); }", "dbg");
    p.callFunction("set", feature);
}

#! on a JDK version whose class loader has not been verified, a manifest Class-Path attribute refuses the classpath
sub jdkgate_case(string root) {
    # a verified version: the Class-Path is checked and followed
    set_jdk_feature(25);
    Program p = new_program(get_sandbox(root, ("a",)));
    hash<auto> h = add_classpath(p, root + "/a/cp-ref.jar");
    result("jdkgate: a verified JDK version is accepted", "ok", h.err);
    result("jdkgate: the class of the named jar is loaded on it", True, loads(p, "CpTarget"));

    # an unknown version: a jar with a manifest Class-Path refuses the classpath
    set_jdk_feature(9999);
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/cp-ref.jar");
    result("jdkgate: an unknown JDK version is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("jdkgate: the error names the reason", True, h.desc =~ /unverified JDK version 9999/);
    result("jdkgate: the error names the jar", True, h.desc.find(root + "/a/cp-ref.jar") >= 0);
    result("jdkgate: nothing is added on it", (), jar_paths(p, root));
    result("jdkgate: the class of the named jar is not loaded on it", False, loads(p, "CpTarget"));
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/*.jar");
    result("jdkgate: a wildcard that selects the jar is refused on it", "JNI-CLASSPATH-ERROR", h.err);
    result("jdkgate: the wildcard error names the reason", True, h.desc =~ /unverified JDK version 9999/);
    p = new_program(get_sandbox(root, ("d",), ("d/lib",)));
    h = add_classpath(p, root + "/d/cp-ref.zip");
    result("jdkgate: a zip file with a Class-Path is refused on it", "JNI-CLASSPATH-ERROR", h.err);
    result("jdkgate: the zip file error names the reason", True, h.desc =~ /unverified JDK version 9999/);

    # an unknown version: a jar with no manifest Class-Path is accepted
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/lib/cp-target.jar");
    result("jdkgate: a jar with no Class-Path is accepted on an unknown JDK version", "ok", h.err);
    result("jdkgate: its class is loaded", True, loads(p, "CpTarget"));

    # an unknown version: no sandbox, no check
    p = new_program();
    h = add_classpath(p, root + "/a/cp-ref.jar");
    result("jdkgate: an unsandboxed classpath is unaffected", "ok", h.err);
    result("jdkgate: the class of the named jar is loaded with no sandbox", True, loads(p, "CpTarget"));

    # the running JDK's version again
    set_jdk_feature();
    p = new_program(get_sandbox(root, ("a",)));
    result("jdkgate: the running JDK version is used again", "ok", add_classpath(p, root + "/a/cp-ref.jar").err);
}

#! returns the feature version of the running JVM, the value that the product's check reads
int sub running_jdk_feature() {
    Program p(PO_NEW_STYLE);
    p.parse("%requires jni
%module-cmd(jni) import java.lang.Runtime
int sub feature() { return Jni::java::lang::Runtime::version().feature(); }", "jdk-feature");
    return p.callFunction("feature");
}

#! returns the exact description of the refusal of a jar on an unverified JDK (qore#7204)
/** The last part is added by Program::issueModuleCmd(), which names the command that failed.
*/
string sub unverified_refusal(string jar, int feature, string cp) {
    return sprintf("cannot add the classpath: the classpath jar '%s' has a manifest Class-Path attribute, which "
        "cannot be checked against the sandbox filesystem policy on an unverified JDK version %d for manifest "
        "Class-Path checking (verified: [%s]): module command error from command 'add-classpath %s'", jar, feature,
        join(", ", (map string($1), VerifiedJdkFeatures)), cp);
}

#! checks that a classpath was refused as documented on an unverified JDK, and that nothing of it was loaded
sub check_refused(string label, Program p, hash<auto> h, string root, list<string> jars, list<string> classes,
        int feature) {
    result(label + ": it is refused", "JNI-CLASSPATH-ERROR", h.err);
    list<string> messages = map unverified_refusal(root + DirSep + $1, feature, h.cp), jars;
    result(label + ": the error is the documented refusal", inlist(h.desc, messages) ? h.desc : messages[0], h.desc);
    result(label + ": nothing is added", (), jar_paths(p, root));
    foreach string cls in (classes) {
        result(sprintf("%s: the class %s is not loaded", label, cls), False, loads(p, cls));
    }
}

#! returns the classpaths of each case that are expected to be refused on a JDK outside VerifiedJdkFeatures
hash<string, list<hash<RefusedClasspath>>> sub refused_classpaths() {
    list<string> ref_classes = ("CpRef", "CpTarget");
    return {
        "deny": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "deny: named", "allowed": ("d",), "denied": ("d/lib",),
                "classpath": ("d/cp-ref.jar",), "jars": ("d/cp-ref.jar",), "classes": ref_classes},
            <RefusedClasspath>{"label": "deny: wildcard", "allowed": ("d",), "denied": ("d/lib",),
                "classpath": ("d/*.jar",), "jars": ("d/cp-ref.jar",), "classes": ref_classes},
            <RefusedClasspath>{"label": "deny: two entries", "allowed": ("a", "d"), "denied": ("d/lib",),
                "classpath": ("a/cp-ref.jar", "d/cp-ref.jar"), "jars": ("a/cp-ref.jar",), "classes": ref_classes},
            <RefusedClasspath>{"label": "deny: chain", "allowed": ("n",), "denied": ("n/b/c",),
                "classpath": ("n/cp-nesta.jar",), "jars": ("n/cp-nesta.jar",),
                "classes": ("NestA", "NestB", "NestC")},
            <RefusedClasspath>{"label": "deny: an entry that is not a file", "allowed": ("h",),
                "classpath": ("h/cp-http.jar",), "jars": ("h/cp-http.jar",), "classes": ("CpHttp",)},
            <RefusedClasspath>{"label": "deny: zip file", "allowed": ("d",), "denied": ("d/lib",),
                "classpath": ("d/cp-ref.zip",), "jars": ("d/cp-ref.zip",), "classes": ref_classes},
            <RefusedClasspath>{"label": "deny: a named file that is not a jar", "allowed": ("j",),
                "classpath": ("j/cp-ref.jar",), "jars": ("j/cp-ref.jar",), "classes": ("CpRef",)},
            <RefusedClasspath>{"label": "deny: a named device", "allowed": ("v",), "device": True,
                "classpath": ("v/cp-ref.jar",), "jars": ("v/cp-ref.jar",), "classes": ("CpRef",)},
        )),
        "escape": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "escape", "allowed": ("s",), "classpath": ("s/cp-ref.jar",),
                "jars": ("s/cp-ref.jar",), "classes": ref_classes},
        )),
        "relative": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "relative: denied", "allowed": ("w", "o"), "denied": ("w/lib",),
                "classpath": ("w/cp-upref.jar",), "jars": ("w/cp-upref.jar",),
                "classes": ("CpUpRef", "CpUp", "CpTarget")},
            <RefusedClasspath>{"label": "relative: allowed", "allowed": ("w", "o"),
                "classpath": ("w/cp-upref.jar",), "jars": ("w/cp-upref.jar",),
                "classes": ("CpUpRef", "CpUp", "CpTarget")},
        )),
        "cycle": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "cycle: named", "allowed": ("cy",), "classpath": ("cy/cp-cyca.jar",),
                "jars": ("cy/cp-cyca.jar",), "classes": ("CycA", "CycB")},
            <RefusedClasspath>{"label": "cycle: wildcard", "allowed": ("cy",), "classpath": ("cy/*.jar",),
                "jars": ("cy/cp-cyca.jar", "cy/cp-cycb.jar"), "classes": ("CycA", "CycB")},
        )),
        "nested": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "nested", "allowed": ("n",), "classpath": ("n/cp-nesta.jar",),
                "jars": ("n/cp-nesta.jar",), "classes": ("NestA", "NestB", "NestC")},
        )),
        "allowed": cast<list<hash<RefusedClasspath>>>((
            <RefusedClasspath>{"label": "allowed: named", "allowed": ("a",), "classpath": ("a/cp-ref.jar",),
                "jars": ("a/cp-ref.jar",), "classes": ref_classes},
            <RefusedClasspath>{"label": "allowed: a missing named jar", "allowed": ("m",),
                "classpath": ("m/cp-ref.jar",), "jars": ("m/cp-ref.jar",), "classes": ("CpRef",)},
        )),
    };
}

#! on a JDK outside VerifiedJdkFeatures, the classpaths of the case are refused (qore#7204)
sub unverified_case(string name, string root, int feature) {
    hash<string, list<hash<RefusedClasspath>>> cases = refused_classpaths();
    if (!cases{name}) {
        printf("unknown case %y\n", name);
        exit(1);
    }
    foreach hash<RefusedClasspath> c in (cases{name}) {
        SandboxManager sm = get_sandbox(root, c.allowed, c.denied);
        if (c.device) {
            sm.addFilesystemAllowedPath("/dev/null", QSEC_READ);
        }
        Program p = new_program(sm);
        hash<auto> h = add_classpath(p, join(PathSep, (map root + DirSep + $1, c.classpath)));
        check_refused(c.label, p, h, root, c.jars, c.classes, feature);
    }
    if (name == "deny") {
        SandboxManager smv = get_sandbox(root, ("v",));
        smv.addFilesystemAllowedPath("/dev/null", QSEC_READ);
        deny_not_regular(root, smv);
    }
}

#! on a JDK outside VerifiedJdkFeatures, no assumed version makes the check follow a Class-Path (qore#7204)
/** An assumed version can only make the check stricter, so the refusal names the running JDK's version.
*/
sub unverified_jdkgate_case(string root, int feature) {
    list<string> ref_classes = ("CpRef", "CpTarget");
    set_jdk_feature(25);
    Program p = new_program(get_sandbox(root, ("a",)));
    hash<auto> h = add_classpath(p, root + "/a/cp-ref.jar");
    check_refused("jdkgate: a verified version assumed", p, h, root, ("a/cp-ref.jar",), ref_classes, feature);

    set_jdk_feature(9999);
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/cp-ref.jar");
    check_refused("jdkgate: an unknown version assumed", p, h, root, ("a/cp-ref.jar",), ref_classes, feature);
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/*.jar");
    check_refused("jdkgate: a wildcard", p, h, root, ("a/cp-ref.jar",), ref_classes, feature);
    p = new_program(get_sandbox(root, ("d",), ("d/lib",)));
    h = add_classpath(p, root + "/d/cp-ref.zip");
    check_refused("jdkgate: a zip file", p, h, root, ("d/cp-ref.zip",), ref_classes, feature);

    # a jar with no manifest Class-Path is accepted
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/lib/cp-target.jar");
    result("jdkgate: a jar with no Class-Path is accepted", "ok", h.err);
    result("jdkgate: its class is loaded", True, loads(p, "CpTarget"));

    # no sandbox, no check
    p = new_program();
    h = add_classpath(p, root + "/a/cp-ref.jar");
    result("jdkgate: an unsandboxed classpath is unaffected", "ok", h.err);
    result("jdkgate: the class of the named jar is loaded with no sandbox", True, loads(p, "CpTarget"));

    set_jdk_feature();
    p = new_program(get_sandbox(root, ("a",)));
    h = add_classpath(p, root + "/a/cp-ref.jar");
    check_refused("jdkgate: the running version again", p, h, root, ("a/cp-ref.jar",), ref_classes, feature);
}

sub main() {
    string root = ARGV[1];
    # the same value that the product's check reads; it decides which behavior is expected (qore#7204)
    int feature = running_jdk_feature();
    bool verified = inlist(feature, VerifiedJdkFeatures);
    printf("jdk-gate: %s %d\n", verified ? "verified" : "refused", feature);
    # the check applies only to a sandboxed classpath, so the unsandboxed case is the same on every JDK
    if (!verified && ARGV[0] != "unsandboxed") {
        if (ARGV[0] == "jdkgate") {
            unverified_jdkgate_case(root, feature);
        } else {
            unverified_case(ARGV[0], root, feature);
        }
        printf("end\n");
        return;
    }
    switch (ARGV[0]) {
        case "deny": deny_case(root); break;
        case "escape": escape_case(root); break;
        case "relative": relative_case(root); break;
        case "cycle": cycle_case(root); break;
        case "nested": nested_case(root); break;
        case "allowed": allowed_case(root); break;
        case "unsandboxed": unsandboxed_case(root); break;
        case "jdkgate": jdkgate_case(root); break;
        default:
            printf("unknown case %y\n", ARGV[0]);
            exit(1);
    }
    printf("end\n");
}

main();
