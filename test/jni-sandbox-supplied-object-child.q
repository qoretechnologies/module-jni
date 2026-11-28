#!/usr/bin/env qore
# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

# run by test/jni-sandbox-supplied-object.qtest, one case per process, as whether sandboxed code has used Java in the
# process is process-wide state (qore#7081)
#
# arguments: <case> <dir> [<shape> <mode>]
# - case "matrix": a Program (sandboxed with mode "sandboxed", with no sandbox with mode "trusted") makes a Java
#   object or closure of the shape given, hands it to the host in each way, and the host runs it in each way, once
#   calling a function of the host and once creating a directory under <dir>; prints one line per combination:
#   "R|<shape>|<handover>|<runner>|<function>|<outcome>|<created|absent>"
# - case "mixed": the calls that stay allowed once sandboxed code has used Java; prints "P|<name>|<outcome>|<dir>"
# - case "define-class": sandboxed code tries to define a class in the host's class loader through the lookup of the
#   host's copy of the module's dispatch class, and the host runs an object of it if it got one (qore#7738); prints
#   "P|<name>|<outcome>|-"
# - case "origin": corner cases of the origin of an object (a deleted origin, an object passed on, another sandboxed
#   Program, class loading); prints "P|<name>|<outcome>|-"
#
# the child prints "jdk: <feature version>", "latch: <bool>" when the module can tell whether sandboxed code has used
# Java in the process (a debug build), and "end" when it ran to its end

%modern
%no-child-restrictions
%prepend-module-path "${SCRIPT_DIR}/../build"
%requires jni
%module-cmd(jni) add-relative-classpath qore-jni-test.jar
%module-cmd(jni) import java.util.ArrayList
%module-cmd(jni) import java.util.Optional
%module-cmd(jni) import java.util.concurrent.atomic.AtomicReference
%module-cmd(jni) import java.lang.Class
%module-cmd(jni) import java.lang.invoke.MethodHandles
%module-cmd(jni) import java.lang.invoke.MethodHandleProxies
%module-cmd(jni) import org.qore.jni.QoreJavaApi
%module-cmd(jni) import org.qore.jni.test.SuppliedClosureProbe
%module-cmd(jni) import org.qore.jni.test.HostObjectProbe
%module-cmd(jni) import org.qore.jni.test.SuppliedTask
%module-cmd(jni) import java.lang.Runtime

#! the code of a sandboxed Program that tries to define a class in the host's class loader (qore#7738)
const DefineClassSource = "%modern
%requires jni
%module-cmd(jni) import org.qore.jni.compiler.QoreJavaCompiler
%module-cmd(jni) import java.lang.Class
const Src = 'package org.qore.jni;
public class SuppliedDefined implements java.util.function.Function<Object, Object> {
    public Object apply(Object o) {
        try {
            return QoreJavaApi.callFunction(\"host_fn\");
        } catch (Throwable e) {
            return e.toString();
        }
    }
}';
# defines the class in the class loader of the host's copy of the module's dispatch class, and hands an object of it
# to the list given; returns the class's name, or the exception
string sub define_in(object host_loader, object l) {
    try {
        object dyn = host_loader.loadClass('org.qore.jni.QoreJavaDynamicApi');
        object lk = dyn.getMethod('lookup').invoke(NOTHING);
        QoreJavaCompiler compiler(get_class_loader(), NOTHING);
        on_exit compiler.close();
        object out = compiler.compile('org.qore.jni.SuppliedDefined', Src);
        object cls = lk.defineClass(out.file.openInputStream().readAllBytes());
        l.add(cls.getDeclaredConstructor().newInstance());
        return 'OK: ' + cls.getName();
    } catch (hash<ExceptionInfo> ex) {
        return ex.err + ': ' + ex.desc;
    }
}
";

#! the code of the Program that makes the Java objects and closures (single quotes only)
const SupplierSource = "%modern
%requires jni
%module-cmd(jni) import java.util.function.Function
%module-cmd(jni) import java.util.ArrayList
%module-cmd(jni) import java.lang.Class
%module-cmd(jni) import java.lang.invoke.MethodHandles
%module-cmd(jni) import java.lang.invoke.MethodHandleProxies
%module-cmd(jni) import org.qore.jni.test.SuppliedClosureProbe
%module-cmd(jni) import org.qore.jni.test.SuppliedTask

# QoreJavaApi.callFunction(String, Object[]) as a method handle
object sub call_handle() {
    object api = Class::forName('org.qore.jni.QoreJavaApi');
    object m = api.getMethod('callFunction', Class::forName('java.lang.String'),
        Class::forName('[Ljava.lang.Object;'));
    return MethodHandles::publicLookup().unreflect(m).asFixedArity();
}

# the arguments as a Java Object[], or NOTHING
auto sub java_args(*list<auto> args) {
    if (!args) {
        return;
    }
    object a = new_array(Class::forName('java.lang.Object'), args.size());
    foreach auto v in (args) {
        a.set($#, v);
    }
    return a;
}

# a Function of JDK classes only that ignores its argument and calls the Qore function given
object sub adapter(string fn, *list<auto> args) {
    object mh = MethodHandles::insertArguments(call_handle(), 0, fn, java_args(args));
    mh = MethodHandles::dropArguments(mh, 0, (Class::forName('java.lang.Object'),));
    return MethodHandleProxies::asInterfaceInstance(Class::forName('java.util.function.Function'), mh);
}

# the Java object of the shape given
object sub make(string shape, string fn, *list<auto> args) {
    switch (shape) {
        case 'adapter': return adapter(fn, args);
        # composed by a method of the JDK
        case 'jdk-lambda': return Function::identity().andThen(adapter(fn, args));
        # composed with a Qore closure of this Program
        case 'and-then': {
            QoreInvocationHandler h(auto sub (object method, *list<auto> margs) { return margs[0]; });
            object own = implement_interface(h, Class::forName('java.util.function.Function'));
            return own.andThen(adapter(fn, args));
        }
        # an adapter of an interface of the host's class loader, which defines the adapter's class too
        case 'host-iface': {
            object mh = MethodHandles::insertArguments(call_handle(), 0, fn, java_args(args));
            mh = MethodHandles::dropArguments(mh, 0, (Class::forName('java.lang.Object'),));
            return MethodHandleProxies::asInterfaceInstance(
                get_class_loader().loadClass('org.qore.jni.test.SuppliedFunction'), mh);
        }
        # an object of a class of the host's class loader, made here
        case 'deputy': return new SuppliedClosureProbe(fn);
    }
    throw 'UNKNOWN', shape;
}

# a method handle that takes an object and stores it in the holder given
object sub store_handle(object holder, string handover) {
    object lk = MethodHandles::publicLookup();
    object objcls = Class::forName('java.lang.Object');
    object m = handover == 'field'
        ? Class::forName('java.util.concurrent.atomic.AtomicReference').getMethod('set', objcls)
        : Class::forName('java.util.List').getMethod('add', objcls);
    return lk.unreflect(m).bindTo(holder);
}

# a Java object used as a Qore closure, of JDK classes only, made and stored by Java code: it is never a Qore value
# in this Program
nothing sub store_marker(object store, string fn) {
    # a full lookup: before JDK 22, asInterfaceInstance() is caller-sensitive, which a public lookup cannot unreflect
    object asifc = MethodHandles::lookup().unreflect(Class::forName('java.lang.invoke.MethodHandleProxies')
        .getMethod('asInterfaceInstance', Class::forName('java.lang.Class'),
        Class::forName('java.lang.invoke.MethodHandle')));
    object mk = MethodHandles::insertArguments(asifc, 0, Class::forName('org.qore.jni.QoreClosureMarkerImpl'),
        MethodHandles::insertArguments(call_handle(), 0, fn));
    auto r = MethodHandles::filterReturnValue(mk, store).invokeWithArguments(new ArrayList());
}

# hands the object of the shape given to the host: returns it ('return'), sets the host's AtomicReference ('field'),
# or adds it to the host's list ('list')
auto sub hand(string shape, string handover, *object holder, string fn, *list<auto> args) {
    if (shape == 'marker') {
        if (handover == 'return') {
            # the value is converted here, and is returned as a Java closure of this Program
            object l = new ArrayList();
            store_marker(store_handle(l, 'list'), fn);
            return l.get(0);
        }
        store_marker(store_handle(holder, handover), fn);
        return;
    }
    object v = make(shape, fn, args);
    switch (handover) {
        case 'return': return v;
        case 'field': holder.set(v); return;
        case 'list': holder.add(v); return;
    }
    throw 'UNKNOWN', handover;
}

# a Function implemented by this Program with a Qore closure, that calls the function given or creates the directory
object sub own_proxy(string fn, *string path) {
    QoreInvocationHandler h(auto sub (object method, *list<auto> margs) {
        return fn == 'mkdir' ? mkdir(path) : call_function(fn);
    });
    return implement_interface(h, Class::forName('java.util.function.Function'));
}

# the function of this Program
string sub supplier_fn() {
    return 'supplier';
}

# obtains the Java closure in the list given, and hands it to the other list given by Java code
nothing sub pass_on(object from, object to) {
    code c = from.get(0);
    to.addAll(from);
}

# hands an object of a class of the host's class loader, made here, and this Program's class loader to the list given
nothing sub hand_task(object l, string fn) {
    l.add(new SuppliedTask(fn));
    l.add(get_class_loader());
}

# hands a Java closure, made here, which calls the function given in this Program, to the list given
nothing sub hand_own_closure(object l, string fn) {
    l.add(new SuppliedClosureProbe(fn, get_class_loader()));
}

# calls the Qore closure given
auto sub call_closure(code c) {
    return c();
}

# leaves a Java closure, made here, that calls the function given in the Program that runs it, in Java state that
# every Program can reach
nothing sub leave_closure(string fn) {
    SuppliedTask::shared = new SuppliedClosureProbe(fn);
}

# obtains the Java closure left in that Java state, and calls it
auto sub call_left_closure() {
    code c = SuppliedTask::shared;
    return c();
}

# obtains the Java closure left in that Java state, and returns it
code sub obtain_left_closure() {
    return SuppliedTask::shared;
}

# calls the object that hand_task() handed over, in the Program of the class loader that it handed over
auto sub call_task(object l) {
    object task = l.get(0);
    return task.callIn(l.get(1));
}

# hands a Supplier of JDK classes only to the list given, which loads the class given with the class loader given
nothing sub hand_class_loading(object l, object loader, string name) {
    object clscls = Class::forName('java.lang.Class');
    object m = clscls.getMethod('forName', Class::forName('java.lang.String'),
        Class::forName('java.lang.Boolean').getField('TYPE').get(NOTHING), Class::forName('java.lang.ClassLoader'));
    # a full lookup: forName() is caller-sensitive, which a public lookup cannot unreflect before JDK 22
    object mh = MethodHandles::insertArguments(MethodHandles::lookup().unreflect(m), 0, name, True, loader);
    l.add(MethodHandleProxies::asInterfaceInstance(Class::forName('java.util.function.Supplier'), mh));
}

# makes an object of a class of the host's class loader, hands it to a list of this Program, and calls it as a Java
# closure of this Program
auto sub own_closure_call(string fn) {
    object l = new ArrayList();
    l.add(new SuppliedClosureProbe(fn));
    code c = l.get(0);
    return c();
}

# runs a callback that the host handed over: directly, by a method of the JDK, or on a new Java thread
auto sub run_callback(auto cb, string runner, *string path) {
    object f;
    if (cb.typeCode() == NT_OBJECT) {
        f = cb;
    } else {
        QoreInvocationHandler h(auto sub (object method, *list<auto> margs) { return cb(path); });
        f = implement_interface(h, Class::forName('java.util.function.Function'));
    }
    switch (runner) {
        case 'direct': return cb.typeCode() == NT_OBJECT ? f.apply(path) : cb(path);
        case 'jdk': return Optional::of('x').map(f).get();
        case 'thread': return SuppliedClosureProbe::applyOnThread(f, path ?? 'x', get_class_loader());
    }
    throw 'UNKNOWN', runner;
}
";

#! classes of the host whose Java classes are generated when they are loaded
class SuppliedGenA {
}
class SuppliedGenB {
}
class SuppliedGenC {
    const C = "constant";
}

#! the host's function, which the Program that makes the objects does not have
string sub host_fn(*string ignored) {
    return "host secret";
}

#! a Function implemented by the host with a Qore closure
object sub fn_of(code c) {
    QoreInvocationHandler h(auto sub (object method, *list<auto> args) { return c(); });
    return implement_interface(h, Class::forName("java.util.function.Function"));
}

#! runs the value: directly, by a method of the JDK, or on a new Java thread with the host's Java context
auto sub run_value(auto v, string runner, *list<auto> args) {
    if (v.typeCode() == NT_OBJECT && v.getClass().getName() == "org.qore.jni.test.SuppliedClosureProbe") {
        object o = v;
        if (runner == "direct") {
            return o.call(args ? args[0] : NOTHING);
        }
        # the host passes the object through Java, which returns it as a Qore closure
        ArrayList l();
        l.add(o);
        return run_value(l.get(0), runner, args);
    }
    if (v.typeCode() != NT_OBJECT) {
        code c = v;
        switch (runner) {
            case "direct": return call_function_args(c, args);
            case "jdk": return Optional::of("x").map(fn_of(auto sub () { return call_function_args(c, args); })).get();
            case "thread": return SuppliedClosureProbe::applyOnThread(fn_of(auto sub () {
                return call_function_args(c, args); }), "x", get_class_loader());
        }
    }
    object f = v;
    switch (runner) {
        case "direct": return f.apply("x");
        case "jdk": return Optional::of("x").map(f).get();
        case "thread": return SuppliedClosureProbe::applyOnThread(f, "x", get_class_loader());
    }
    throw "UNKNOWN", runner;
}

#! the outcome of the code on one line: "OK: <value>" or "<err>: <desc>"
string sub outcome(code c) {
    string rv;
    try {
        rv = sprintf("OK: %y", c());
    } catch (hash<ExceptionInfo> ex) {
        rv = sprintf("%s: %s", ex.err, ex.desc);
    }
    return replace(replace(rv, "\n", " "), "|", "/");
}

#! a new Program with the code that makes the objects, sandboxed or not
Program sub supplier(bool sandboxed) {
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.parse(SupplierSource, "supplier");
    if (sandboxed) {
        SandboxManager sm();
        sm.setUncontrolledApiPolicy(True);
        sm.setFilesystemDefaultPolicy(False);
        sm.setNetworkDefaultPolicy(False);
        p.setSandboxManager(sm);
    }
    return p;
}

#! prints the feature version of the JVM
nothing sub print_jdk() {
    printf("jdk: %d\n", Runtime::version().feature());
}

#! prints whether sandboxed code has used Java in the process, if the module can tell
nothing sub print_latch() {
    # the debug function exists only in a debug build of the module
    Program dp(PO_NEW_STYLE);
    try {
        dp.parse("%requires jni\nbool sub latch() { return Jni::org::qore::jni::dbg_jni_java_thread_policy_strict(); "
            "}", "latch");
    } catch (hash<ExceptionInfo> ex) {
        return;
    }
    printf("latch: %y\n", dp.callFunction("latch"));
}

#! returns True if the sandbox refuses the sandboxed code's first use of Java given, after printing the refusal as
#! "P|sandboxed-java|<outcome>|-"
/** qore#7832: a sandbox that trusts its code with uncontrolled APIs but restricts the filesystem or the network
    refuses its Java code, so none of the calls that need sandboxed Java can run; any other error is raised
*/
bool sub sandbox_refuses_java(code c) {
    try {
        c();
    } catch (hash<ExceptionInfo> ex) {
        if (ex.err == "INVALID-FUNCTION-ACCESS" && ex.desc =~ /\(qore#7832\)$/) {
            printf("P|sandboxed-java|%s: %s|-\n", ex.err, replace(replace(ex.desc, "\n", " "), "|", "/"));
            return True;
        }
        rethrow;
    }
    return False;
}

#! makes, hands over and runs every combination for the shape given
nothing sub matrix(string dir, string shape, string mode) {
    Program p = supplier(mode == "sandboxed");
    foreach string handover in (("return", "field", "list")) {
        foreach string runner in (("direct", "jdk", "thread")) {
            foreach string fn in (("host_fn", "mkdir")) {
                string path = sprintf("%s/%s-%s-%s-%s", dir, shape, handover, runner, fn);
                *list<auto> args = fn == "mkdir" ? (path,) : NOTHING;
                string rv = outcome(auto sub () {
                    *object holder;
                    if (handover == "field") {
                        holder = new AtomicReference();
                    } else if (handover == "list") {
                        holder = new ArrayList();
                    }
                    auto v = p.callFunction("hand", shape, handover, holder, fn, args);
                    if (handover == "field") {
                        v = holder.get();
                    } else if (handover == "list") {
                        v = holder.get(0);
                    }
                    return run_value(v, runner, args);
                });
                printf("R|%s|%s|%s|%s|%s|%s\n", shape, handover, runner, fn, rv, is_dir(path) ? "created" : "absent");
            }
        }
    }
}

#! the calls that stay allowed once sandboxed code has used Java
nothing sub mixed(string dir) {
    Program p = supplier(True);
    # sandboxed code uses Java
    if (sandbox_refuses_java(auto sub () { return p.callFunction("make", "adapter", "host_fn"); })) {
        return;
    }
    code report = nothing sub (string name, code c, *string path) {
        printf("P|%s|%s|%s\n", name, outcome(c), path ? (is_dir(path) ? "created" : "absent") : "-");
    };
    # a callback that the host hands to sandboxed code: a Qore closure, run by the sandboxed code directly, by a
    # method of the JDK and on a Java thread, and an interface that the host implements with a Qore closure, run by a
    # method of the JDK and on a Java thread
    foreach string runner in (("direct", "jdk", "thread")) {
        code cb = auto sub (*string ignored) { return host_fn(); };
        report("callback-closure-" + runner, auto sub () { return p.callFunction("run_callback", cb, runner); });
        if (runner == "direct") {
            continue;
        }
        QoreInvocationHandler h(auto sub (object method, *list<auto> args) { return host_fn(); });
        object proxy = implement_interface(h, Class::forName("java.util.function.Function"));
        report("callback-proxy-" + runner, auto sub () { return p.callFunction("run_callback", proxy, runner); });
    }
    # interfaces that sandboxed code implements with its own Qore closures stay governed by its sandbox, whatever
    # runs them
    foreach string runner in (("jdk", "thread")) {
        foreach string fn in (("host_fn", "mkdir")) {
            string path = sprintf("%s/own-proxy-%s-%s", dir, runner, fn);
            report(sprintf("own-proxy-%s-%s", runner, fn), auto sub () {
                ArrayList l();
                l.add(p.callFunction("own_proxy", fn, path));
                return run_value(l.get(0), runner);
            }, path);
        }
    }
    # the host's own calls into Qore: from Qore code, from Java code of a class of its class loader (also through an
    # adapter of the JDK that it calls), and through a Java object used as a Qore closure that it made
    report("host-direct", auto sub () { return QoreJavaApi::callFunction("host_fn"); });
    HostObjectProbe probe();
    report("host-class", auto sub () { return probe.callQore("host_fn"); });
    report("host-class-adapter", auto sub () { return probe.methodHandleProxyCall("host_fn"); });
    report("host-closure-object", auto sub () {
        ArrayList l();
        l.add(new SuppliedClosureProbe("host_fn"));
        code c = l.get(0);
        return c();
    });
    # a constant of a generated class, which its static initializer reads when reflection first uses the class
    report("host-constant-reflection", auto sub () {
        return Class::forName("qore.SuppliedGenC", False, get_class_loader()).getField("C").get(NOTHING);
    });
    # the host's own adapter of JDK classes only: its origin cannot be told from one that sandboxed code made
    Program own = supplier(False);
    report("host-adapter", auto sub () { return run_value(own.callFunction("make", "adapter", "host_fn"), "jdk"); });
}

#! a class that sandboxed code tries to define in the host's class loader (qore#7738)
nothing sub define_class() {
    code report = nothing sub (string name, code c) {
        printf("P|%s|%s|-\n", name, outcome(c));
    };
    Program p(PO_NEW_STYLE | PO_NO_CHILD_PO_RESTRICTIONS);
    p.issueModuleCmd("jni", "add-classpath " + get_script_dir() + "/../build/qore-jni-compiler.jar");
    p.parse(DefineClassSource, "define-class");
    SandboxManager sm();
    sm.setUncontrolledApiPolicy(True);
    sm.setFilesystemDefaultPolicy(False);
    sm.setNetworkDefaultPolicy(False);
    p.setSandboxManager(sm);
    ArrayList l();
    string rv = p.callFunction("define_in", get_class_loader(), l);
    printf("P|define-class-sandboxed|%s|-\n", replace(replace(rv, "\n", " "), "|", "/"));
    # the host runs what it got, if anything, through a method of the JDK
    report("define-class-host", auto sub () {
        return l.size() ? Optional::of("x").map(l.get(0)).get() : "nothing handed over";
    });
}

#! corner cases of the origin of an object
nothing sub origin() {
    code report = nothing sub (string name, code c) {
        printf("P|%s|%s|-\n", name, outcome(c));
    };
    # an object that a sandboxed Program made and handed over, whose Program is deleted before the host runs it: its
    # origin reaches no Program
    {
        Program p = supplier(True);
        ArrayList l();
        if (sandbox_refuses_java(auto sub () { return p.callFunction("hand", "deputy", "list", l, "host_fn"); })) {
            return;
        }
        delete p;
        report("deleted-origin", auto sub () { return run_value(l.get(0), "direct"); });
    }
    # an object that a sandboxed Program made and handed over, which another sandboxed Program obtains and passes
    # on: it keeps its origin, which does not reach the host's Program
    {
        Program a = supplier(True);
        Program b = supplier(True);
        ArrayList l();
        a.callFunction("hand", "deputy", "list", l, "host_fn");
        ArrayList l2();
        b.callFunction("pass_on", l, l2);
        report("passed-on-origin", auto sub () { return run_value(l2.get(0), "direct"); });
    }
    # an adapter that a sandboxed Program made, which loads a class of a dynamic name through the host's class
    # loader, run by the host: the class is not generated for it
    {
        Program p = supplier(True);
        ArrayList l();
        p.callFunction("hand_class_loading", l, get_class_loader(), "qore.SuppliedGenA");
        report("class-loading-adapter", auto sub () { return l.get(0).get().getName(); });
    }
    # the host's own class loading through the JDK's code, once sandboxed code has used Java
    report("class-loading-host", auto sub () {
        return Class::forName("qore.SuppliedGenB", True, get_class_loader()).getName();
    });
    # an object that a sandboxed Program made, which another sandboxed Program calls in the first one's Program: the
    # origin does not give the second Program the first one's reach
    {
        Program a = supplier(True);
        Program b = supplier(True);
        ArrayList l();
        a.callFunction("hand_task", l, "supplier_fn");
        report("cross-origin", auto sub () { return b.callFunction("call_task", l); });
        # the Program that made it reaches its own Program
        report("cross-origin-own", auto sub () { return a.callFunction("call_task", l); });
    }
    # a Java closure that a sandboxed Program made, which the host obtains (its owner is its origin) and lends to
    # another sandboxed Program: the closure does not give the second Program the first one's reach
    {
        Program a = supplier(True);
        Program b = supplier(True);
        ArrayList l();
        a.callFunction("hand_own_closure", l, "supplier_fn");
        code c = l.get(0);
        report("lent-closure", auto sub () { return b.callFunction("call_closure", c); });
        # the host, and the Program that made it, reach its Program
        report("lent-closure-host", auto sub () { return c(); });
        report("lent-closure-own", auto sub () { return a.callFunction("call_closure", c); });
    }
    # a Java closure that a sandboxed Program made and left in shared Java state, which another sandboxed Program
    # obtains itself and calls: it does not run with the second Program's reach
    {
        Program a = supplier(True);
        Program b = supplier(True);
        a.callFunction("leave_closure", "supplier_fn");
        report("foreign-closure", auto sub () { return b.callFunction("call_left_closure"); });
        # the Program that made it obtains and calls it with its own reach
        report("foreign-closure-own", auto sub () { return a.callFunction("call_left_closure"); });
        # the Program that made it obtains it, and the host lends that closure to the other Program: it does not run
        # with the first Program's reach for it
        code own = a.callFunction("obtain_left_closure");
        report("own-closure-lent", auto sub () { return b.callFunction("call_closure", own); });
        # the other Program obtains it and hands it to the host: it reaches no Program for the host either
        code foreign = b.callFunction("obtain_left_closure");
        report("foreign-closure-host", auto sub () { return foreign(); });
        SuppliedTask::shared = NOTHING;
    }
    # an object that a sandboxed Program made and handed over, run by that Program itself: it reaches its own Program
    {
        Program p = supplier(True);
        report("own-origin", auto sub () { return p.callFunction("own_closure_call", "supplier_fn"); });
    }
}

int sub main() {
    *string case_name = shift ARGV;
    *string dir = shift ARGV;
    if (!dir) {
        stderr.printf("usage: %s <case> <dir> [<shape> <mode>]\n", get_script_name());
        return 2;
    }
    switch (case_name) {
        case "matrix": {
            *string shape = shift ARGV;
            *string mode = shift ARGV;
            if (!shape || (mode != "sandboxed" && mode != "trusted")) {
                stderr.printf("matrix: shape and mode (sandboxed or trusted) are required\n");
                return 2;
            }
            matrix(dir, shape, mode);
            break;
        }
        case "mixed":
            mixed(dir);
            break;
        case "origin":
            origin();
            break;
        case "define-class":
            define_class();
            break;
        default:
            stderr.printf("unknown case %y\n", case_name);
            return 2;
    }
    print_jdk();
    print_latch();
    print("end\n");
    return 0;
}

exit(main());
