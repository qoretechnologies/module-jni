#!/usr/bin/env qore
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-jdbc-stale-sink.qtest (qore#6807): a JDBC prepared statement kept the ExceptionSink of the call that
# created it, and released its bind arguments into that sink when it was closed or reset in a later call; when the
# sink was gone, an exception raised by the release (here: by the destructor of an object that only the bind
# arguments reference) was written to freed stack memory, and the process crashed
#
# the statement is prepared in Qore code that Java calls back (Runnable.run() of a proxy whose invocation handler
# calls Qore): the module runs the callback with an ExceptionSink local to its native frame, which is gone when run()
# returns
#
# the argument names the case (see Cases below); the case prints "<step>: <err>: <desc>" for each exception caught, or
# "<step>: no exception", then "after: <case>" is printed, and the process exits with status 0

%modern
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import org.qore.jni.test.JdbcTransactionDriver

#! an object whose destructor raises an exception; the bind arguments of the statement hold its only reference
class Boom {
    destructor() {
        printf("Boom destructor\n");
        throw "STALE-SINK-TEST", "raised in the destructor of the bind argument";
    }
}

#! runs the code in a Qore callback of Java code, with an ExceptionSink that is gone when this function returns
sub in_java_callback(code c) {
    Jni::org::qore::jni::QoreInvocationHandler h(auto sub (auto m, *list<auto> args) {
        c();
    });
    Jni::java::lang::Runnable r = jni::implement_interface(h, jni::load_class("java/lang/Runnable"));
    jni::invoke(jni::load_class("java/lang/Runnable").getMethod("run"), r);
}

#! a deep stack frame, so that the stack memory of the callback's native frame is reused by later calls
int sub deep(int n) {
    string s = sprintf("%d", n);
    return n ? deep(n - 1) + s.size() : 0;
}

#! runs the code and prints the exceptions it raises, with the step name
sub step(string name, code c) {
    try {
        c();
        printf("%s: no exception\n", name);
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

#! prepares the statement and binds a Boom object in a Java callback
/** the SQL has no placeholder: the driver ignores excess bind arguments, so the statement can be executed, but holds
    them until it is reset, closed, or bound again
*/
sub prepare_in_callback(SQLStatement stmt) {
    in_java_callback(sub () {
        stmt.prepare("write");
        stmt.bind(new Boom());
        deep(50);
    });
    printf("callback returned\n");
    deep(50);
}

#! the statement is closed after the callback returned: the bind arguments are released into close()'s sink
sub close_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    SQLStatement stmt(ds);
    prepare_in_callback(stmt);
    step("close", \stmt.close());
}

#! the statement is executed after the callback returned and loses the connection, which cannot be re-established:
#! the statement is reset, and its bind arguments are released into exec()'s sink (by the driver's reset, or by the
#! close of the statement for the aborted connection when the Qore library holds them too)
sub reset_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    SQLStatement stmt(ds);
    prepare_in_callback(stmt);
    JdbcTransactionDriver::loseConnectionAt("execute:write:false", False);
    JdbcTransactionDriver::failNext("connect");
    step("exec", \stmt.exec());
    printf("executions: %d\n", JdbcTransactionDriver::count("execute:write:false"));
    step("close", \stmt.close());
}

#! the statement is deleted without close() after the callback returned: the destructor of SQLStatement closes it,
#! and the bind arguments are released into the sink of the code that deletes it
sub delete_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    SQLStatement stmt(ds);
    prepare_in_callback(stmt);
    step("delete", sub () { delete stmt; });
}

#! new bind arguments after the callback returned: the earlier arguments are released into bind()'s sink
sub rebind_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    SQLStatement stmt(ds);
    prepare_in_callback(stmt);
    step("bind", sub () { stmt.bind(1); });
    step("exec", \stmt.exec());
    printf("affected: %d\n", stmt.affectedRows());
    step("commit", \stmt.commit());
    printf("durable: %d\n", JdbcTransactionDriver::durable());
    step("close", \stmt.close());
}

#! the statement is prepared, executed and closed in the same code: the bind arguments are released into close()'s
#! sink
sub normal_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    SQLStatement stmt(ds);
    stmt.prepare("write");
    stmt.bind(new Boom());
    step("exec", \stmt.exec());
    printf("affected: %d\n", stmt.affectedRows());
    step("commit", \stmt.commit());
    printf("durable: %d\n", JdbcTransactionDriver::durable());
    step("close", \stmt.close());
}

#! a statement of one Datasource call in a Java callback: it is released when the call ends
sub temp_case() {
    Datasource ds("jdbc:@qore-transaction-test:fixture");
    in_java_callback(sub () {
        step("exec", sub () { printf("result: %y\n", ds.exec("write %v", new Boom())); });
        ds.commit();
    });
    deep(50);
    printf("durable: %d\n", JdbcTransactionDriver::durable());
    step("select", sub () { printf("result: %y\n", ds.select("read")); });
}

#! the case names
const Cases = {
    "close": \close_case(),
    "reset": \reset_case(),
    "delete": \delete_case(),
    "rebind": \rebind_case(),
    "normal": \normal_case(),
    "temp": \temp_case(),
};

string case_name = ARGV[0] ?? "close";
*code c = Cases{case_name};
if (!c) {
    stderr.printf("unknown case %y; known cases: %y\n", case_name, keys Cases);
    exit(2);
}

JdbcTransactionDriver::reset();
c();
deep(50);
printf("after: %s\n", case_name);
