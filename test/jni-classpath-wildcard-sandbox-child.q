#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-classpath-wildcard-sandbox.qtest; the arguments are the case to run and the root of the test tree
# that the parent made (see the parent for its layout).  Each check prints "result: <name>: OK", or the values found,
# and the child prints "end" when it reaches its end.

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"

#! a class of the jni test jar (a.jar in the test tree)
const TestClass = "org.qore.jni.test.A";
#! a class of the Kotlin test jar (d.jar and k.jar in the test tree)
const KotlinClass = "org.qore.jni.test.SimpleKotlinClass";

#! the source of a Program that lists the classpath of its class loaders and loads classes
const LoaderSource = "%requires jni
%module-cmd(jni) import java.lang.ClassLoader
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

#! returns a sandbox that allows reading the given directories of the test tree, and denies the given files
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
    foreach string file in (denied) {
        sm.addFilesystemDeniedPath(root + DirSep + file);
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
        return {"err": "ok", "desc": ""};
    } catch (hash<ExceptionInfo> ex) {
        return {"err": ex.err, "desc": ex.desc};
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

#! a wildcard that selects a file that the policy denies, inside an allowed directory, is refused as a whole
sub deny_case(string root) {
    SandboxManager sm = get_sandbox(root, ("mix",), ("mix/d.jar",));
    Program p = new_program(sm);
    hash<auto> h = add_classpath(p, root + "/mix/*");
    result("deny: the wildcard is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("deny: the error names the denied file", True, h.desc.find(root + "/mix/d.jar") >= 0);
    result("deny: nothing is added", (), jar_paths(p, root));
    result("deny: the class of the allowed jar is not loaded", False, p.callFunction("can_load", TestClass));
    result("deny: the class of the denied jar is not loaded", False, p.callFunction("can_load", KotlinClass));

    # a wildcard that selects only allowed files is added
    h = add_classpath(p, root + "/mix/a*.jar");
    result("deny: a wildcard that selects only the allowed jar is added", "ok", h.err);
    result("deny: only the allowed jar is added", ("mix/a.jar",), jar_paths(p, root));
    result("deny: the class of the allowed jar is loaded", True, p.callFunction("can_load", TestClass));
    result("deny: the class of the denied jar is still not loaded", False, p.callFunction("can_load", KotlinClass));

    # the denied file, given as it is, is refused by the policy
    result("deny: the denied file as an entry", "FILESYSTEM-ACCESS-DENIED",
        add_classpath(new_program(sm), root + "/mix/d.jar").err);
}

#! a symbolic link in an allowed directory that points outside it is not followed
sub escape_case(string root) {
    SandboxManager sm = get_sandbox(root, ("esc",));
    Program p = new_program(sm);
    hash<auto> h = add_classpath(p, root + "/esc/*.jar");
    result("escape: the wildcard is refused", "JNI-CLASSPATH-ERROR", h.err);
    result("escape: the error names the file that the link points to", True,
        h.desc.find(root + "/outside/k.jar") >= 0);
    result("escape: nothing is added", (), jar_paths(p, root));
    result("escape: the class of the allowed jar is not loaded", False, p.callFunction("can_load", TestClass));
    result("escape: the class of the jar outside is not loaded", False, p.callFunction("can_load", KotlinClass));

    # a wildcard in a linked directory that points outside the allowed directory is refused by the policy
    result("escape: a wildcard in a linked directory outside", "FILESYSTEM-ACCESS-DENIED",
        add_classpath(new_program(sm), root + "/esc/outdir/*").err);
    # the link, given as it is, is refused by the policy
    result("escape: the link as an entry", "FILESYSTEM-ACCESS-DENIED",
        add_classpath(new_program(sm), root + "/esc/out.jar").err);

    # a wildcard that selects only the allowed jar is added
    h = add_classpath(p, root + "/esc/a*.jar");
    result("escape: a wildcard that selects only the allowed jar is added", "ok", h.err);
    result("escape: only the allowed jar is added", ("esc/a.jar",), jar_paths(p, root));
}

#! a symbolic link that points inside the allowed directory is added, as the file that it points to
sub inside_case(string root) {
    Program p = new_program(get_sandbox(root, ("in",)));
    hash<auto> h = add_classpath(p, root + "/in/*.jar");
    result("inside: the wildcard is added", "ok", h.err);
    result("inside: the file that the link points to is added", ("in/sub/k.jar",), jar_paths(p, root));
    result("inside: its class is loaded", True, p.callFunction("can_load", KotlinClass));
}

#! a wildcard in an allowed directory with allowed files only is added
sub plain_case(string root) {
    Program p = new_program(get_sandbox(root, ("plain",)));
    hash<auto> h = add_classpath(p, root + "/plain/*");
    result("plain: the wildcard is added", "ok", h.err);
    result("plain: the jar is added", ("plain/a.jar",), jar_paths(p, root));
    result("plain: its class is loaded", True, p.callFunction("can_load", TestClass));
    # a wildcard that selects no file adds nothing, and is not an error
    h = add_classpath(new_program(get_sandbox(root, ("plain",))), root + "/plain/none*.jar");
    result("plain: a wildcard that selects no file", "ok", h.err);
}

#! with no sandbox, wildcards are expanded as before: every matching file is added, as the path in the directory
sub unsandboxed_case(string root) {
    Program p = new_program();
    hash<auto> h = add_classpath(p, root + "/mix/*");
    result("unsandboxed: the wildcard is added", "ok", h.err);
    result("unsandboxed: every jar is added", ("mix/a.jar", "mix/d.jar"), jar_paths(p, root));
    result("unsandboxed: the class of a.jar is loaded", True, p.callFunction("can_load", TestClass));
    result("unsandboxed: the class of d.jar is loaded", True, p.callFunction("can_load", KotlinClass));

    Program p2 = new_program();
    h = add_classpath(p2, root + "/esc/*.jar");
    result("unsandboxed: a wildcard with a link is added", "ok", h.err);
    result("unsandboxed: the link is added as it is named", ("esc/a.jar", "esc/out.jar"), jar_paths(p2, root));
    result("unsandboxed: the class of the linked jar is loaded", True, p2.callFunction("can_load", KotlinClass));
}

sub main() {
    string root = ARGV[1];
    switch (ARGV[0]) {
        case "deny": deny_case(root); break;
        case "escape": escape_case(root); break;
        case "inside": inside_case(root); break;
        case "plain": plain_case(root); break;
        case "unsandboxed": unsandboxed_case(root); break;
        default:
            printf("unknown case %y\n", ARGV[0]);
            exit(1);
    }
    printf("end\n");
}

main();
