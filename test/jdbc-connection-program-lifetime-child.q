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
if (case_name != "own" && case_name != "escape" && (parts.size() != 3 || !Openers{parts[0]}
        || !exists Lifetimes{parts[1]} || !Ops{parts[2]})) {
    stderr.printf("unknown case %y; the case is own, escape or <%s>-<%s>-<%s>\n", case_name,
        (keys Openers).join("|"), (keys Lifetimes).join("|"), (keys Ops).join("|"));
    exit(2);
}
if (!ARGV[1]) {
    stderr.printf("missing the classpath of the test's JDBC driver\n");
    exit(2);
}
string cs = sprintf("jdbc:u/p@ignored{url=qore-lifetime7665:db,classpath=%s}", ARGV[1]);

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
