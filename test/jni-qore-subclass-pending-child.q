#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-qore-subclass-pending-program.qtest in a new process for each case, so that the byte code of the
# Java class for the Qore superclass (qore.Qore.AbstractIterator) has not been generated in the JVM yet, as in a new
# Qorus container process: the arguments are the case to run and the directory with the compiled Java classes (each
# in its package directory); it prints "result: <name>: OK" for each check that passes, or the values found, and
# "skip: <reason>" for a case that the module cannot run (a release build has no debug functions)
#
# the Java classes extend qore.Qore.AbstractIterator; the class files are named after the case: Sub<Case> and, for the
# define-pending-class case, Main<Case>, which extends Sub<Case>

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni

#! the code that each Program has pending when the classes are defined or imported
const PendingCode = "string sub pending() { return 'pending'; }";

#! the package of the test classes
const Package = "org.qore.jni.test.pending";

#! prints the result of a check
sub result(string name, auto expected, auto found) {
    printf("result: %s: %s\n", name, expected == found ? "OK" : sprintf("expected %y, found %y", expected, found));
}

#! returns a new Program with the jni module loaded and code pending, as a Qorus Program from its construction until
#! its first commit
Program sub get_pending_program() {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.parsePending(PendingCode, "pending");
    p.loadModule("jni");
    return p;
}

#! returns a new Program with the jni module loaded and no code pending
Program sub get_committed_program() {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.loadModule("jni");
    p.parse(PendingCode, "committed");
    return p;
}

#! returns the byte code of a test class
binary sub byte_code(string dir, string cls) {
    return ReadOnlyFile::readBinaryFile(dir + DirSep + replace(Package, ".", DirSep) + DirSep + cls + ".class");
}

#! issues a module command and returns "OK" or the exception
string sub issue(Program p, string cmd) {
    try {
        p.issueModuleCmd("jni", cmd);
        return "OK";
    } catch (hash<ExceptionInfo> ex) {
        return sprintf("%s: %s", ex.err, ex.desc);
    }
}

#! commits the Program's pending code and checks the class that it got
sub check_after_commit(string name, Program p, string cls) {
    p.parseCommit();
    result(name + ": the pending code is committed", "pending", p.callFunction("pending"));
    string path = "Jni::" + replace(Package, ".", "::") + "::" + cls;
    try {
        Expression e(p, sprintf("new %s()", path), "new-object");
        object o = e.eval();
        result(name + ": the class is in the Program", cls, o.className());
        result(name + ": the class is a subclass of the Qore class", True, o instanceof AbstractIterator);
        # the Java class's methods override the Qore class's abstract methods
        result(name + ": the Java method is called", False, o.valid());
    } catch (hash<ExceptionInfo> ex) {
        result(name + ": the class is in the Program", cls, sprintf("%s: %s", ex.err, ex.desc));
    }
}

#! returns a Program that imports a class into another Program with dbg_jni_import_into_program(), or NOTHING (and
#! prints a skip line) if the module is a release build, which does not have the function
*Program sub get_importer() {
    Program importer(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    importer.loadModule("jni");
    try {
        importer.parse("sub import_into(Program p, string import) {\n"
            "    Jni::org::qore::jni::dbg_jni_import_into_program(p, import);\n"
            "}", "importer");
    } catch (hash<ExceptionInfo> ex) {
        # only the function's absence (a release build of the module) skips the case
        if (ex.err != "PARSE-EXCEPTION" || ex.desc !~ /dbg_jni_import_into_program/) {
            rethrow;
        }
        printf("skip: the jni module is not a debug build: %s\n", ex.desc);
        return;
    }
    return importer;
}

string mode = ARGV[0];
string dir = ARGV[1];

switch (mode) {
    case "define-class": {
        # QorusProgram::cacheJavaClass(): the main class is defined from its byte code before the commit
        string cls = "SubDefineClass";
        Program p = get_pending_program();
        result(mode + ": define-class", "OK", issue(p, sprintf("define-class %s/%s %s", replace(Package, ".", "/"),
            cls, byte_code(dir, cls).toBase64())));
        check_after_commit(mode, p, cls);
        break;
    }

    case "define-pending-class": {
        # QorusProgram::cacheJavaClass(): the other classes are pending classes, and the main class, which extends one
        # of them here, is defined after them
        string sub_cls = "SubDefinePendingClass";
        string main_cls = "MainDefinePendingClass";
        string pkg_path = replace(Package, ".", "/");
        Program p = get_pending_program();
        result(mode + ": define-pending-class", "OK", issue(p, sprintf("define-pending-class %s/%s %s", pkg_path,
            sub_cls, byte_code(dir, sub_cls).toBase64())));
        result(mode + ": define-class", "OK", issue(p, sprintf("define-class %s/%s %s", pkg_path, main_cls,
            byte_code(dir, main_cls).toBase64())));
        check_after_commit(mode, p, main_cls);
        break;
    }

    case "import": {
        # a class from a directory added to the Program's classpath
        string cls = "SubImport";
        Program p = get_pending_program();
        result(mode + ": add-classpath", "OK", issue(p, "add-classpath " + dir + DirSep));
        result(mode + ": import", "OK", issue(p, "import " + Package + "." + cls));
        check_after_commit(mode, p, cls);
        break;
    }

    case "later-code": {
        # code parsed after the import, still before the commit, uses the class
        string cls = "SubLaterCode";
        Program p = get_pending_program();
        result(mode + ": add-classpath", "OK", issue(p, "add-classpath " + dir + DirSep));
        result(mode + ": import", "OK", issue(p, "import " + Package + "." + cls));
        p.parsePending(sprintf("bool sub get_valid() { %s i(); return i.valid(); }", cls), "later");
        check_after_commit(mode, p, cls);
        result(mode + ": the later code uses the class", False, p.callFunction("get_valid"));
        break;
    }

    case "not-current": {
        # a class added to a Program that is not current enters it, also to generate the byte code of the Java class
        # of its Qore superclass
        *Program importer = get_importer();
        if (!importer) {
            break;
        }
        string cls = "SubNotCurrent";
        Program p = get_committed_program();
        result(mode + ": add-classpath", "OK", issue(p, "add-classpath " + dir + DirSep));
        Counter enter();
        Counter exit();
        p.setThreadEntryHandler(sub () { enter.inc(); }, sub () { exit.inc(); });
        importer.callFunction("import_into", p, Package + "." + cls);
        result(mode + ": the entry handler runs", True, enter.getCount() > 0);
        result(mode + ": the exit handler runs when each entry is left", enter.getCount(), exit.getCount());
        p.setThreadEntryHandler();
        Expression e(p, sprintf("new %s()", cls), "new-object");
        object o = e.eval();
        result(mode + ": the class is a subclass of the Qore class", True, o instanceof AbstractIterator);
        break;
    }

    case "not-current-pending": {
        # a Program that is not current is entered at runtime to add a class to it, which is refused while it has code
        # pending, as before
        *Program importer = get_importer();
        if (!importer) {
            break;
        }
        string cls = "SubNotCurrentPending";
        Program p = get_pending_program();
        result(mode + ": add-classpath", "OK", issue(p, "add-classpath " + dir + DirSep));
        string err;
        try {
            importer.callFunction("import_into", p, Package + "." + cls);
            err = "no exception";
        } catch (hash<ExceptionInfo> ex) {
            err = sprintf("%s: %s", ex.err, ex.desc);
        }
        result(mode + ": the entry is refused", True, err =~ /currently undergoing parsing/);
        # the Program is unchanged: its code is committed, and the class can be imported then
        p.parseCommit();
        result(mode + ": the pending code is committed", "pending", p.callFunction("pending"));
        result(mode + ": import after the commit", "OK", issue(p, "import " + Package + "." + cls));
        Expression e(p, sprintf("new %s()", cls), "new-object");
        object o = e.eval();
        result(mode + ": the class is a subclass of the Qore class", True, o instanceof AbstractIterator);
        break;
    }

    default:
        throw "TEST-ERROR", sprintf("unknown case %y", mode);
}
