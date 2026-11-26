#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jdbc-connection-program-lifetime.qtest (qore#7665): a jdbc connection kept the Program that opened it
# and that Program's jni data as raw pointers; when the Program was deleted while its Datasource or DatasourcePool
# lived on in another Program, the next use of the connection read the freed jni data (SQLStatement::describe()) or
# used the deleted Program, whose jni data was gone, to convert a Java value (a java.util.UUID column), which failed
# an assertion in a debug build
#
# arguments: the case and the classpath directory of the test's JDBC driver; the case is "own", "escape", or
# <opener>-<lifetime>-<operation> (see Openers, Lifetimes and Ops below): the connection is opened in a child
# Program, which is then deleted or kept; the operation is the first use of the connection after that, and the
# datasource is then used again
#
# each step prints "<step>: <result>" or "<step>: <err>: <desc>" for an exception; then "after: <case>" is printed,
# and the process exits with status 0

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
# with -DNO_JNI, this Program, and so the child Programs that it creates, have no jni data: the jdbc driver loads the
# module, and a child Program gets jni data when it opens a connection
%ifndef NO_JNI
%requires jni
%module-cmd(jni) import java.lang.System
%module-cmd(jni) import java.util.concurrent.TimeUnit
%endif

#! the source of the child Program that opens the connection: the code that opens it is the code of that Program
const OpenerSrc = "%modern\n"
    "Datasource sub open_ds(string cs) {\n"
    "    Datasource ds(cs);\n"
    "    ds.open();\n"
    "    # the connection is used in the Program that opened it; no Java object is converted, so the class of a\n"
    "    # Java value is created only after the Program is deleted\n"
    "    printf(\"opener: %y\\n\", ds.selectRow(\"select x\"));\n"
    "    return ds;\n"
    "}\n"
    "hash<auto> sub open_ds_h(string cs) {\n"
    "    return {\"ds\": open_ds(cs)};\n"
    "}\n"
    "hash<auto> sub open_ds_tx(string cs) {\n"
    "    Datasource ds = open_ds(cs);\n"
    "    # a transaction of the connection is in progress when the Program is deleted\n"
    "    ds.beginTransaction();\n"
    "    return {\"ds\": ds};\n"
    "}\n"
    "hash<auto> sub open_pool(string cs) {\n"
    "    DatasourcePool pool(cs);\n"
    "    printf(\"opener: %y\\n\", pool.selectRow(\"select x\"));\n"
    "    return {\"ds\": pool};\n"
    "}\n"
    "sub use_pool(DatasourcePool pool) {\n"
    "    printf(\"user: %y\\n\", pool.selectRow(\"select x\"));\n"
    "}\n"
    "sub use_stmt(AbstractDatasource ds) {\n"
    "    # the statement is an object of this Program: its execution opens the datasource's connection in this\n"
    "    # Program's context\n"
    "    SQLStatement stmt(ds);\n"
    "    on_exit stmt.close();\n"
    "    stmt.prepare(\"select x\");\n"
    "    stmt.exec();\n"
    "    printf(\"user: %y\\n\", stmt.fetchRow());\n"
    "    stmt.commit();\n"
    "}\n"
    "Datasource sub new_ds(string cs) {\n"
    "    # not opened\n"
    "    return new Datasource(cs);\n"
    "}\n"
    "hash<auto> sub open_stmt(string cs) {\n"
    "    Datasource ds = open_ds(cs);\n"
    "    # a statement of the connection has been executed when the Program is deleted\n"
    "    SQLStatement stmt(ds);\n"
    "    stmt.prepare(\"select x\");\n"
    "    stmt.exec();\n"
    "    return {\"ds\": ds, \"stmt\": stmt};\n"
    "}\n"
    # a datasource that its own Program holds: the connection must not keep that Program from being deleted
    "class Marker {\n"
    "    destructor() {\n"
    "        printf(\"opener destroyed\\n\");\n"
    "    }\n"
    "}\n"
    "our *Marker marker;\n"
    "our *Datasource own_ds;\n"
    "sub open_own(string cs) {\n"
    "    marker = new Marker();\n"
    "    own_ds = new Datasource(cs);\n"
    "    own_ds.open();\n"
    "    printf(\"opener: %y\\n\", own_ds.selectRow(\"select uuid\").u.toString());\n"
    "}\n"
    # a connection opened by the Program's own destructor, once its teardown has started, which escapes the Program
    "class Escaper {\n"
    "    private {\n"
    "        string cs;\n"
    "        code escape;\n"
    "    }\n"
    "    constructor(string cs, code escape) {\n"
    "        self.cs = cs;\n"
    "        self.escape = escape;\n"
    "    }\n"
    "    destructor() {\n"
    "        Datasource ds(cs);\n"
    "        ds.open();\n"
    "        printf(\"escaper: %y\\n\", ds.selectRow(\"select x\"));\n"
    "        escape(ds);\n"
    "    }\n"
    "}\n"
    "our *Escaper escaper;\n"
    "sub open_escape(string cs, code escape) {\n"
    "    escaper = new Escaper(cs, escape);\n"
    "}\n";

#! the functions of the child Program that open each kind of datasource
const Openers = {
    "ds": "open_ds_h",
    "dstx": "open_ds_tx",
    "pool": "open_pool",
    "stmt": "open_stmt",
};

#! the time zone of the child Program that opens the connection, which is not the time zone of this Program: the
#! dates of an operation of this Program are in its own time zone
const OpenerZone = "Pacific/Kiritimati";

#! whether the child Program that opened the connection is kept
const Lifetimes = {
    "kept": True,
    "deleted": False,
};

#! the operations run first after the child Program is deleted or kept, with the datasource ("ds") and the statement
#! executed in the child Program ("stmt", for the "stmt" opener)
const Ops = {
    # the class of the column's values is loaded with the class loader of the connection's Program
    "describe": auto sub (hash<auto> h) { return describe(h.ds); },
    # a java.util.UUID value is converted to a Qore object of the connection's Program
    "uuid": auto sub (hash<auto> h) { return h.ds.selectRow("select uuid").u.toString(); },
    # a column whose value is not converted from a Java object
    "int": auto sub (hash<auto> h) { return h.ds.selectRow("select x"); },
    # a java.sql.Timestamp value, formatted with its time zone
    "ts": auto sub (hash<auto> h) {
        return h.ds.selectRow("select ts").ts.format("YYYY-MM-DD HH:mm:SS Z");
    },
    # SQL executed in a transaction
    "exec": auto sub (hash<auto> h) {
        on_success h.ds.commit();
        return h.ds.exec("update x");
    },
    # the transaction that the connection's Program started is committed
    "commit": auto sub (hash<auto> h) { return h.ds.commit(); },
    # the transaction that the connection's Program started is rolled back
    "rollback": auto sub (hash<auto> h) { return h.ds.rollback(); },
    # the result of the statement that the connection's Program executed is described
    "sdescribe": auto sub (hash<auto> h) { return h.stmt.describe(); },
    # the row of the statement that the connection's Program executed is fetched
    "sfetch": auto sub (hash<auto> h) { return h.stmt.fetchRow(); },
    # the rows of the statement that the connection's Program executed are fetched
    "sfetchrows": auto sub (hash<auto> h) { return h.stmt.fetchRows(); },
    # arguments are bound to the statement that the connection's Program executed
    "sbind": auto sub (hash<auto> h) { return h.stmt.bind(1); },
    # a statement is prepared and executed
    "prepare": auto sub (hash<auto> h) {
        SQLStatement stmt(h.ds);
        on_exit stmt.close();
        stmt.prepare("select x");
        return stmt.exec();
    },
    # the rows of a query
    "selectrows": auto sub (hash<auto> h) { return h.ds.selectRows("select x"); },
    # raw SQL executed in a transaction
    "execraw": auto sub (hash<auto> h) {
        on_success h.ds.commit();
        return h.ds.execRaw("update x");
    },
    # the classpath option loads jars with the class loader of the connection's Program
    "classpath": auto sub (hash<auto> h) { return h.ds.setOption("classpath", ARGV[1]); },
};

#! the child Program that opened the connection, while it is kept
Program opener;

#! runs the code and prints its result or the exceptions that it raises, with the step name
sub step(string name, code c) {
    try {
        printf("%s: %y\n", name, c());
    } catch (hash<ExceptionInfo> ex) {
        while (True) {
            printf("%s: %s: %s\n", name, ex.err, ex.desc);
            if (!ex.next) {
                break;
            }
            ex = ex.next;
        }
    }
}

#! opens the connection in a child Program, which is deleted unless \a keep is True
hash<auto> sub open_in_child(string func, string cs, bool keep) {
    Program p(PO_NEW_STYLE);
    p.setTimeZoneRegion(OpenerZone);
    p.parse(OpenerSrc, "opener");
    hash<auto> h = p.callFunction(func, cs);
    if (keep) {
        opener = p;
    } else {
        delete p;
    }
    return h;
}

#! describes the result of a query with a statement of the datasource
hash<auto> sub describe(AbstractDatasource ds) {
    SQLStatement stmt(ds);
    # the statement's execution started a transaction, which is ended when the statement is done
    on_success {
        stmt.commit();
    }
    on_exit {
        stmt.close();
    }
    stmt.prepare("select x");
    stmt.exec();
    return stmt.describe();
}

string case_name = ARGV[0] ?? "";
list<string> parts = case_name.split("-");
if (case_name != "own" && case_name != "escape" && case_name != "grow" && case_name != "wait"
        && case_name != "stmtopen" && case_name != "stmtopen-pool"
        && (parts.size() != 3 || !Openers{parts[0]}
        || !exists Lifetimes{parts[1]} || !Ops{parts[2]})) {
    stderr.printf("unknown case %y; the case is own, escape, grow, wait, stmtopen, stmtopen-pool or "
        "<%s>-<%s>-<%s>\n", case_name, (keys Openers).join("|"), (keys Lifetimes).join("|"), (keys Ops).join("|"));
    exit(2);
}
if (!ARGV[1]) {
    stderr.printf("missing the classpath of the test's JDBC driver\n");
    exit(2);
}
string cs = sprintf("jdbc:u/p@ignored{url=qore-lifetime7665:db,classpath=%s}", ARGV[1]);

%ifndef NO_JNI
#! the bound of the waits of wait_case(), in milliseconds: a guard against a test that would never end
const GuardMs = 60000;

#! the teardown of the Program that opened a connection waits for an operation of another thread on it
/** The operation is held in the JDBC driver by a latch that this code releases; the teardown is known to wait for it
    with the debug-build-only Jni::org::qore::jni::dbg_jni_jdbc_deleted_wait()
*/
sub wait_case(string cs) {
    hash<auto> base;
    try {
        base = call_function("Jni::org::qore::jni::dbg_jni_jdbc_deleted_wait", 0);
    } catch (hash<ExceptionInfo> ex) {
        # only a missing function (a release build) skips the case
        if (ex.err != "NO-FUNCTION") {
            rethrow;
        }
        printf("wait: skipped: the debug functions of a debug build are required\n");
        return;
    }
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "opener");
    Datasource ds = p.callFunction("open_ds_h", cs).ds;
    # the latches of the driver, which its class created when the connection was opened
    hash<auto> props = System::getProperties();
    object entered = props{"qore7665.entered"};
    object release = props{"qore7665.release"};
    Counter op_done(1);
    background sub () {
        on_exit op_done.dec();
        step("block", auto sub () { return ds.selectRow("select block"); });
    }();
    # the operation is in the driver, and holds a use of the Program; the bounds of the waits below are guards
    # against a test that would never end if the behavior regressed, which the printed results show, not a
    # synchronization
    printf("entered: %y\n", entered.await(GuardMs, TimeUnit::MILLISECONDS));
    Counter deleted(1);
    background sub () {
        on_exit deleted.dec();
        delete p;
    }();
    # the teardown marks the state (its cleanup callback), and waits for the operation: it is held until the latch
    # is released
    hash<auto> now = call_function("Jni::org::qore::jni::dbg_jni_jdbc_deleted_wait", base.marks + 1, GuardMs);
    printf("marked: %y\n", !now.timeout);
    printf("waiting: %d\n", now.waits - base.waits);
    printf("deleted before release: %y\n", !deleted.getCount());
    release.countDown();
    printf("operation done: %y\n", !op_done.waitForZero(GuardMs));
    printf("deleted: %y\n", !deleted.waitForZero(GuardMs));
    # the next operation fails cleanly
    step("next", auto sub () { return ds.selectRow("select uuid").u.toString(); });
}
%endif

if (case_name == "own") {
    # the child Program holds its own datasource and is deleted: its teardown runs and destroys its global variables
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "opener");
    p.callFunction("open_own", cs);
    delete p;
    printf("deleted\n");
} else if (case_name == "escape") {
    # the child Program opens a connection in its own destructor, during its teardown, and hands it to this Program
    *Datasource escaped;
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "opener");
    p.callFunction("open_escape", cs, sub (Datasource ds) { escaped = ds; });
    delete p;
    printf("deleted\n");
    step("uuid", auto sub () { return escaped.selectRow("select uuid").u.toString(); });
} else if (case_name == "wait") {
%ifdef NO_JNI
    printf("wait: skipped: the case needs jni in this Program\n");
%else
    wait_case(cs);
%endif
} else if (case_name == "stmtopen") {
    # a Datasource created by a kept Program Q, not opened, is opened by an SQLStatement of a short-lived Program P:
    # the statement's methods run with P as their call context, so the connection belongs to P
    Program q(PO_NEW_STYLE);
    q.parse(OpenerSrc, "q");
    Datasource ds = q.callFunction("new_ds", cs);
    opener = q;
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "p");
    p.callFunction("use_stmt", ds);
    delete p;
    printf("p deleted\n");
    # the connection that P's statement opened refuses Q's operation and is aborted
    step("uuid", auto sub () { return ds.selectRow("select uuid").u.toString(); });
    # the datasource opens a new connection, in the context of Q, which created it
    step("again", auto sub () { return ds.selectRow("select uuid").u.toString(); });
} else if (case_name == "stmtopen-pool") {
    # a pool created by a kept Program Q grows a connection for an SQLStatement of a short-lived Program P, in P's
    # thread: the connection belongs to P
    string pcs = sprintf("jdbc:u/p@ignored{url=qore-lifetime7665:db,classpath=%s,min=1,max=2}", ARGV[1]);
    Program q(PO_NEW_STYLE);
    q.parse(OpenerSrc, "q");
    DatasourcePool pool = q.callFunction("open_pool", pcs).ds;
    opener = q;
    # this thread holds the pool's first connection, so the statement of P opens a second one
    pool.beginTransaction();
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "p");
    Counter done(1);
    background sub () {
        on_exit done.dec();
        p.callFunction("use_stmt", pool);
    }();
    done.waitForZero();
    delete p;
    printf("p deleted\n");
    # this thread uses its own connection; another thread gets the second one, which refuses the operation and is
    # aborted, and the pool opens a new one for the next operation, in the context of Q
    step("main", auto sub () { return pool.selectRow("select uuid").u.toString(); });
    done.inc();
    background sub () {
        on_exit done.dec();
        step("other", auto sub () { return pool.selectRow("select uuid").u.toString(); });
        step("again", auto sub () { return pool.selectRow("select uuid").u.toString(); });
    }();
    done.waitForZero();
    step("commit", auto sub () { return pool.commit(); });
} else if (case_name == "grow") {
    # a pool created by a kept Program Q grows a connection while the code of a short-lived Program P uses it, then P
    # is deleted: the pool's connections are then used by this Program's code
    string pcs = sprintf("jdbc:u/p@ignored{url=qore-lifetime7665:db,classpath=%s,min=1,max=2}", ARGV[1]);
    Program q(PO_NEW_STYLE);
    q.parse(OpenerSrc, "q");
    DatasourcePool pool = q.callFunction("open_pool", pcs).ds;
    opener = q;
    # this thread holds the pool's first connection, so the use in P's code opens a second one
    pool.beginTransaction();
    Program p(PO_NEW_STYLE);
    p.parse(OpenerSrc, "p");
    Counter done(1);
    background sub () {
        on_exit done.dec();
        step("p", auto sub () { return p.callFunction("use_pool", pool); });
    }();
    done.waitForZero();
    delete p;
    printf("p deleted\n");
    # this thread uses its own connection, and another thread uses the second one while this thread holds the first
    step("main", auto sub () { return pool.selectRow("select uuid").u.toString(); });
    done.inc();
    background sub () {
        on_exit done.dec();
        step("other", auto sub () {
            on_exit pool.commit();
            return pool.selectRow("select uuid").u.toString();
        });
    }();
    done.waitForZero();
    step("commit", auto sub () { return pool.commit(); });
} else {
    hash<auto> h = open_in_child(Openers{parts[0]}, cs, Lifetimes{parts[1]});
    step(parts[2], auto sub () { return Ops{parts[2]}(h); });
    # the datasource is used again
    step("again", auto sub () { return h.ds.selectRow("select uuid").u.toString(); });
    if (h.stmt) {
        # the statement's transaction ended when the connection was aborted, or else it is ended here
        step("end", auto sub () { return h.stmt.commit(); });
    }
}
printf("after: %s\n", case_name);
