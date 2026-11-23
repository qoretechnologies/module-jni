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
string sub end_copy(string dir, int at, int limit) {
    return ClasspathLookupProbe::endCopyDuringMake(get_class_loader(), dir, at, limit);
}
string sub detached_limit(string dir) {
    return ClasspathLookupProbe::detachedLimit(get_class_loader(), dir);
}
object sub capture_policy() {
    return ClasspathLookupProbe::capturePolicy();
}
string sub removal_failure(string dir, string scenario) {
    return ClasspathLookupProbe::removalFailure(get_class_loader(), dir, scenario);
}
string sub copy_outcome() {
    return ClasspathLookupProbe::copyOutcome(get_class_loader());
}
string sub root_refusals() {
    return ClasspathLookupProbe::rootRefusals(get_class_loader());
}
string sub blocked_recovery(string scenario) {
    return ClasspathLookupProbe::blockedRecovery(get_class_loader(), scenario);
}
string sub compiler_cleanup() {
    return ClasspathLookupProbe::compilerCleanupContained(get_class_loader());
}
string sub program_switch() {
    return ClasspathLookupProbe::programSwitchBudget(get_class_loader());
}
string sub end_with_error(string dir, string mode) {
    return ClasspathLookupProbe::endCopiesWithError(get_class_loader(), dir, mode);
}
string sub compile_ending(string key, int at, string source) {
    return ClasspathLookupProbe::compileWhileEnding(get_class_loader(), key, at, source);
}
string sub hold_derived(string dir) {
    return ClasspathLookupProbe::holdDerived(get_class_loader(), dir);
}
int sub program_ptr() {
    return ClasspathLookupProbe::programPtr(get_class_loader());
}
int sub hold_copies(int max) {
    return ClasspathLookupProbe::holdCopies(get_class_loader(), max);
}
";

#! the directory entries of the \"ending\" directory of the test tree
const EndingEntries = 5;
#! the compiler copies that a Program can hold at once
const MaxOutstandingCopies = 32;
#! the refusal of a copy over the limit of the copies of a Program
const LimitRefusal = "the Program has as many compiler classpath copies as it can have at the same time";
#! the refusal of a copy from the closed budget of a Program that has ended
const EndedRefusal = "the Program of the class loader has ended";
#! the refusal of a copy in a temporary directory that other users can change
const TempRefusal = "the JVM's temporary directory can be changed by other users, or it or the JVM's user ID cannot "
    "be verified, so a private compiler classpath copy cannot be made";
#! how many new Programs are tried for one at the address of a Program that has ended
const AddressTries = 64;

const EntriesProp = "qore.jni.compiler.maxEntries";
const EncodingProp = "sun.jnu.encoding";
#! a system property that the Kotlin compiler reads while it runs, after its classpath has been made
const CompilerProp = "kotlin.environment.keepalive";

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

#! a copy that is ended at each step of making it leaves nothing, and returns its charges once
sub end_copy_case(string root, string label) {
    # the derived class loader and its parent each have the directory on their classpath
    int entries = EndingEntries * 2;
    for (int at = 1; at <= entries; ++at) {
        Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
        result(sprintf("%s at %d", label, at), "clean", p.callFunction("end_copy", root + "/ending", at, entries));
    }
}

#! returns a sandboxed Program with no classpath of its own
Program sub new_empty_program(SandboxManager sm) {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.parse(TenantSource, "tenant");
    p.setSandboxManager(sm);
    return p;
}

#! a derived class loader stays under the limits of its Program, and its copies end with the Program
sub detached_case(string root) {
    Program p = new_empty_program(get_policy(root, ("ending",)));
    object policy = p.callFunction("capture_policy");
    result("detached limit", sprintf("held %d; another refused: %s", MaxOutstandingCopies, LimitRefusal),
        p.callFunction("detached_limit", root + "/ending"));
    string copy = ClasspathLookupProbe::lastKeptCopy();
    result("detached held", True, is_dir(copy));
    delete p;
    result("detached removed", False, is_dir(copy));
    result("detached new copy", "refused: " + EndedRefusal, ClasspathLookupProbe::detachedCopy());
    result("detached new guard", "refused: " + EndedRefusal,
        ClasspathLookupProbe::copyWithNewGuard(policy, root + "/ending"));
}

#! an error while a copy is removed does not stop the removal of the others or lose a charge
sub removal_error_case(string root) {
    foreach string mode in ("close", "abort", "program", "close-program") {
        Program p = new_empty_program(get_policy(root, ("ending",)));
        result("removal-error " + mode, "clean", p.callFunction("end_with_error", root + "/ending", mode));
    }
    result("removal-error left", 0, ClasspathLookupProbe::copyDirectories());
}

#! a removal that really fails, a retry that touches nothing unless the private parent verifies, and the report of a
#! failure
sub removal_failure_case(string root) {
    foreach string scenario in ("confirmed", "during", "signal", "parent-replaced", "parent-mode", "parent-owner",
        "parent-link", "parent-handle", "root-final", "root-file", "root-unverified",
        "root-open-fails") {
        Program p = new_empty_program(get_policy(root, ("ending",)));
        result("removal-failure " + scenario, "clean", p.callFunction("removal_failure", root + "/ending", scenario));
    }
    result("removal-failure left", 0, ClasspathLookupProbe::copyDirectories());
}

#! a restricted copy is made only in a temporary directory that other users cannot change; other compiles make no copy
sub tmpdir_case(string root, string expected) {
    Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
    result("tmpdir restricted", expected == "allowed" ? "copies=1" : TempRefusal, p.callFunction("copy_outcome"));
    result("tmpdir left", 0, ClasspathLookupProbe::copyDirectories());

    Program trusted(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    trusted.parse(TenantSource, "trusted");
    trusted.issueModuleCmd("jni", "add-classpath " + root + "/ending");
    result("tmpdir trusted", "copies=0", trusted.callFunction("copy_outcome"));

    SandboxManager open();
    open.setUncontrolledApiPolicy(True);
    open.setFilesystemDefaultPolicy(True);
    Program permissive = new_program(open, root + "/ending");
    result("tmpdir permissive", "copies=0", permissive.callFunction("copy_outcome"));
    result("tmpdir none left", 0, ClasspathLookupProbe::copyDirectories());
}

#! the temporary directory is checked again when a private parent that exists is used for a new copy
sub tmpdir_reuse_case(string root, string tmp) {
    Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
    string held = p.callFunction("hold_derived", root + "/ending");
    result("reuse held", True, is_dir(held));
    result("reuse safe", "copies=1", p.callFunction("copy_outcome"));
    chmod(tmp, 0777);
    {
        on_exit chmod(tmp, 0700);
        result("reuse refused", TempRefusal, p.callFunction("copy_outcome"));
        result("reuse keeps the held copy", True, is_dir(held));
    }
    result("reuse safe again", "copies=1", p.callFunction("copy_outcome"));
    delete p;
    result("reuse left", 0, ClasspathLookupProbe::copyDirectories());
}

#! the temporary directory must be owned by the JVM's numeric UID or by root, when a parent is created and when it is reused
sub tmpdir_owner_case(string root, string tmp) {
    Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
    result("owner JVM user", "copies=1", p.callFunction("copy_outcome"));
    string held = p.callFunction("hold_derived", root + "/ending");
    {
        ClasspathLookupProbe::setUnexpectedTempOwner(tmp);
        on_exit ClasspathLookupProbe::restoreTempOwner(tmp);
        result("owner refused on reuse", TempRefusal, p.callFunction("copy_outcome"));
        result("owner keeps the held copy", True, is_dir(held));
    }
    result("owner allowed again", "copies=1", p.callFunction("copy_outcome"));
    delete p;
    result("owner parent removed", 0, ClasspathLookupProbe::copyDirectories());

    Program q = new_program(get_policy(root, ("ending",)), root + "/ending");
    {
        ClasspathLookupProbe::setUnexpectedTempOwner(tmp);
        on_exit ClasspathLookupProbe::restoreTempOwner(tmp);
        result("owner refused at creation", TempRefusal, q.callFunction("copy_outcome"));
        result("owner nothing created", 0, ClasspathLookupProbe::copyDirectories());
    }
    {
        ClasspathLookupProbe::setUnknownJvmUid();
        on_exit ClasspathLookupProbe::restoreTempOwner(tmp);
        result("owner unreadable JVM UID", TempRefusal, q.callFunction("copy_outcome"));
        result("owner unreadable nothing created", 0, ClasspathLookupProbe::copyDirectories());
    }
    result("owner by UID", "clean", ClasspathLookupProbe::uidRule(tmp));
    result("owner identity of a parent and a root", "clean", ClasspathLookupProbe::identityChecks(tmp));
    result("owner refused roots", "clean", q.callFunction("root_refusals"));
    result("owner allowed at the end", "copies=1", q.callFunction("copy_outcome"));
}

#! a private directory that takes no more copies recovers once it is empty or gone, and refuses until then
sub blocked_case(string root) {
    foreach string scenario in ("after-release", "parent-removed", "stays", "not-intact", "in-flight", "in-flight-empty",
            "in-flight-removed", "newroot-check", "discard-verifies") {
        Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
        result("blocked " + scenario, "clean", p.callFunction("blocked_recovery", scenario));
    }
    result("blocked left", 0, ClasspathLookupProbe::copyDirectories());
}

#! a caller that a sandbox restricts only for the network makes no grant of the host: a bound policy stays
sub network_readd_case(string root) {
    SandboxManager net();
    net.setUncontrolledApiPolicy(True);
    net.setFilesystemDefaultPolicy(True);
    net.setNetworkDefaultPolicy(False);
    Program np = new_empty_program(net);
    object policy = np.callFunction("capture_policy");
    result("network-only policy", True, policy.restrictsNetwork() && !policy.restrictsFilesystem());

    foreach string route in ("addPath", "addPathOrig", "addURL", "addParentPathIntern", "resolved") {
        Program p = new_program(get_policy(root, ("allowed",)), root + "/allowed");
        result("readd before " + route, "missing", p.callFunction("lookup", "getResource", "denied/g.txt"));
        result("readd added " + route, "ok", ClasspathLookupProbe::addRouteUnder(policy,
            p.callFunction("own_loader"), route, root + "/allowed"));
        result("readd kept " + route, "missing", p.callFunction("lookup", "getResource", "denied/g.txt"));
    }

    # a wildcard re-add of a bound jar: the policy is read for each lookup, so it applies only if the entry kept it
    {
        string wdir = root + "/netwild";
        mkdir(wdir, 0700);
        ClasspathLookupProbe::writeJar(root + "/jars/src", wdir + "/w.jar", "");
        SandboxManager wsm = get_policy(root, ("netwild",));
        Program p = new_program(wsm, wdir + "/w.jar");
        result("readd before addWildcard", True,
            p.callFunction("lookup", "getResource", "denied/JB.class") != "missing");
        result("readd added addWildcard", "ok", ClasspathLookupProbe::addRouteUnder(policy,
            p.callFunction("own_loader"), "addWildcard", wdir + "/*.jar"));
        wsm.addFilesystemDeniedPath(wdir + "/w.jar");
        result("readd kept addWildcard", "missing", p.callFunction("lookup", "getResource", "denied/JB.class"));
    }

    # the control: code that no sandbox restricts can still make a grant of the host
    {
        Program p = new_program(get_policy(root, ("allowed",)), root + "/allowed");
        result("readd host add", "ok", ClasspathLookupProbe::addPath(p.callFunction("own_loader"), root + "/allowed"));
        result("readd host grant", True, p.callFunction("lookup", "getResource", "denied/g.txt") != "missing");
    }

    # a classpath that re-adds a bound entry and is then refused at a later entry, whose manifest names another host,
    # leaves the policy of the bound entry in place
    string dir = root + "/netjar";
    string jar = dir + "/n.jar";
    mkdir(dir, 0700);
    ClasspathLookupProbe::writeJar(root + "/jars/src", jar, "");
    ClasspathLookupProbe::writeJar(root + "/jars/src", dir + "/bad.jar", "file://otherhost/x.jar");
    SandboxManager sm = get_policy(root, ("netjar",));
    Program p = new_program(sm, jar);
    result("refused add found", True, p.callFunction("lookup", "getResource", "denied/JB.class") != "missing");
    result("refused add", "refused", ClasspathLookupProbe::addRouteUnder(policy, p.callFunction("own_loader"),
        "addPath", jar + PathSep + dir + "/bad.jar"));
    # the policy is read for each lookup: it still applies only if the entry kept it
    sm.addFilesystemDeniedPath(jar);
    result("refused add kept", "missing", p.callFunction("lookup", "getResource", "denied/JB.class"));

    # the global module commands check the location of each entry for such a caller, as add-classpath does
    foreach string cmd in ("add-classpath", "global-add-classpath") {
        Program g(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
        g.parse(TenantSource, "network-only");
        g.setSandboxManager(net);
        string err;
        try {
            g.issueModuleCmd("jni", cmd + " " + dir + "/bad.jar");
            err = "added";
        } catch (hash<ExceptionInfo> ex) {
            err = ex.err;
        }
        result("network-only " + cmd, "JNI-CLASSPATH-ERROR", err);
    }
}

#! each new Program of a class loader has a budget of its own
sub program_switch_case(string root) {
    Program p = new_empty_program(get_policy(root, ("ending",)));
    result("program-switch", "own budgets", p.callFunction("program_switch"));
}

#! a compile whose Program ends while its classpath is made, or while the compiler runs
sub compile_end_case(string root) {
    list<string> homes = ();
    if (ENV.KOTLIN_HOME) {
        push homes, ENV.KOTLIN_HOME;
    }
    homes += ("/usr/share/qore/java/kotlin", "/opt/kotlin", "/opt/kotlinc", "/usr/share/kotlin", "/usr/lib/kotlin",
        "/usr/local/kotlin");
    *string home;
    foreach string h in (homes) {
        if (is_file(h + "/lib/kotlin-compiler.jar") && is_file(h + "/lib/kotlin-stdlib.jar")) {
            home = h;
            break;
        }
    }
    if (!home) {
        if (ENV.CI) {
            throw "KOTLIN-MISSING", "the Kotlin compiler JAR is required in CI for this test";
        }
        printf("result: skipped: the Kotlin compiler JAR is not available\n");
        return;
    }
    Program host(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    host.loadModule("jni");
    map host.issueModuleCmd("jni", "global-add-classpath " + home + "/lib/" + $1),
        ("kotlin-compiler.jar", "kotlin-stdlib.jar");
    {
        Program p = new_program(get_policy(root, ("allowed",)), root + "/allowed");
        result("compile-end cleanup", "contained", p.callFunction("compiler_cleanup"));
    }
    string source = "class KEnd { fun f(x: pkg.Ok?) = x }\n";
    foreach hash<auto> i in ({"label": "copy", "key": EntriesProp}, {"label": "compiler", "key": CompilerProp}) {
        Program p = new_program(get_policy(root, ("allowed",)), root + "/allowed");
        result("compile-end during " + i.label, "clean", p.callFunction("compile_ending", i.key, 1, source));
    }
}

#! the end of a Program ends a copy of a derived class loader, and a new Program has a full budget
sub program_end_case(string root) {
    Program p = new_program(get_policy(root, ("ending",)), root + "/ending");
    string copy = p.callFunction("hold_derived", root + "/ending");
    int old = p.callFunction("program_ptr");
    result("program-end held", True, is_dir(copy));
    delete p;
    result("program-end removed", False, is_dir(copy));
    *Program q;
    bool same = False;
    list<Program> others = ();
    for (int i = 0; i < AddressTries; ++i) {
        q = new_program(get_policy(root, ("ending",)), root + "/ending");
        if (q.callFunction("program_ptr") == old) {
            same = True;
            break;
        }
        push others, q;
    }
    printf("info: a new Program at the same address: %y\n", same);
    result("program-end budget", MaxOutstandingCopies, q.callFunction("hold_copies", MaxOutstandingCopies * 2));
}

string case_name = ARGV[0];
string root = ARGV[1];
# these cases do not depend on the encoding of file names
switch (case_name) {
    case "budget": budget_case(root); exit(0);
    case "end-close": end_copy_case(root, case_name); exit(0);
    case "program-end": program_end_case(root); exit(0);
    case "detached": detached_case(root); exit(0);
    case "removal-error": removal_error_case(root); exit(0);
    case "removal-failure": removal_failure_case(root); exit(0);
    case "program-switch": program_switch_case(root); exit(0);
    case "compile-end": compile_end_case(root); exit(0);
    case "tmpdir": tmpdir_case(root, ARGV[2]); exit(0);
    case "tmpdir-reuse": tmpdir_reuse_case(root, ARGV[2]); exit(0);
    case "tmpdir-owner": tmpdir_owner_case(root, ARGV[2]); exit(0);
    case "blocked": blocked_case(root); exit(0);
    case "network-readd": network_readd_case(root); exit(0);
}
if (ClasspathLookupProbe::fileNameEncoding() != "UTF-8") {
    printf("result: skipped: the file names of the JVM are in %s\n", ClasspathLookupProbe::fileNameEncoding());
    exit(0);
}
switch (case_name) {
    case "lossy": lossy_case(root); break;
    case "nonascii": non_ascii_case(root); break;
    case "charset": charset_case(root); break;
    default: throw "TEST-ERROR", sprintf("unknown case %y", case_name);
}
