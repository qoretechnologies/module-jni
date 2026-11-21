/* -*- mode: java; indent-tabs-mode: nil -*- */
/** Copyright (C) 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
    documentation files (the "Software"), to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of
    the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
    THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
    TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

package org.qore.jni.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreClosureMarker;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreURLClassLoader;

//! A Java object whose methods call into Qore in the Program of the thread's class loader context (qore#7023)
/** The Program that a call of a method of a Java object enters is the Program that owns the object: Java's calls into
    Qore during the call must be governed by the Program of the calling code, not by the owner
*/
public class HostObjectProbe {
    //! an instance field, for field reads and writes
    public Object field;
    //! a static field, for static field reads and writes
    public static Object staticField;
    //! the result of the call that the constructor made, if any
    public final String created;
    //! the class loader context of the code that created the probe: the probe's calls into Qore are made with it
    private final QoreURLClassLoader owner = QoreURLClassLoader.getCurrent();

    //! An exception that carries a probe, for calls that throw across the boundary
    public static class ProbeException extends RuntimeException {
        //! the probe, reachable from the exception
        public final HostObjectProbe probe;

        public ProbeException(HostObjectProbe probe) {
            super("probe exception");
            this.probe = probe;
        }
    }

    public HostObjectProbe() {
        created = null;
    }

    //! calls the Qore function \a function from the constructor; the result is in \c created
    public HostObjectProbe(String function) {
        created = call(function);
    }

    //! calls the Qore function \a function with the probe \a via from the constructor; the result is in \c created
    public HostObjectProbe(HostObjectProbe via, String function) {
        created = via.callQore(function);
    }

    //! calls the Qore function \a function in the class loader context of the code that created the probe; returns
    //! "ok: <the result>" or the exception
    public String callQore(String function) {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        if (owner != null) {
            owner.setContext();
        }
        try {
            return call(function);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }

    //! calls \a via.callQore() from a static method
    public static String staticCallVia(HostObjectProbe via, String function) {
        return via.callQore(function);
    }

    //! returns the class name and the class loader of the probe's class, which tells the copy of the class
    public String classIdentity() {
        return getClass().getName() + "@" + System.identityHashCode(getClass().getClassLoader());
    }

    //! calls callQore() through a method handle (a gadget of pure JDK classes)
    public String methodHandleCall(String function) throws Throwable {
        MethodHandle mh = MethodHandles.publicLookup().findVirtual(HostObjectProbe.class, "callQore",
            MethodType.methodType(String.class, String.class));
        return (String)mh.invoke(this, function);
    }

    //! calls callQore() through an interface that MethodHandleProxies implements (a gadget of pure JDK classes)
    @SuppressWarnings("unchecked")
    public String methodHandleProxyCall(String function) throws Throwable {
        MethodHandle mh = MethodHandles.publicLookup().findVirtual(HostObjectProbe.class, "callQore",
            MethodType.methodType(String.class, String.class)).bindTo(this);
        Function<String, String> f = MethodHandleProxies.asInterfaceInstance(Function.class, mh);
        return f.apply(function);
    }

    //! calls callQore() through a java.lang.reflect.Proxy (a gadget of pure JDK classes)
    @SuppressWarnings("unchecked")
    public String proxyCall(String function) {
        Function<String, String> f = (Function<String, String>)Proxy.newProxyInstance(
            Function.class.getClassLoader(), new Class<?>[] {Function.class},
            (proxy, method, args) -> callQore((String)args[0]));
        return f.apply(function);
    }

    //! calls callQore() on another thread: \c thread, \c executor, \c completable (the common pool),
    //! \c timer, \c forkjoin (the common pool) or \c pool (\a pool)
    public String threadCall(String mode, String function, ExecutorService pool) throws Throwable {
        Supplier<String> task = () -> callQore(function);
        switch (mode) {
            case "thread": {
                String[] rv = new String[1];
                Thread t = new Thread(() -> rv[0] = task.get());
                t.start();
                t.join();
                return rv[0];
            }
            case "executor": {
                ExecutorService ex = Executors.newSingleThreadExecutor();
                try {
                    return ex.submit(task::get).get();
                } finally {
                    ex.shutdown();
                }
            }
            case "completable":
                return CompletableFuture.supplyAsync(task).get();
            case "timer": {
                CompletableFuture<String> rv = new CompletableFuture<>();
                Timer timer = new Timer(true);
                try {
                    timer.schedule(new TimerTask() {
                        public void run() {
                            rv.complete(task.get());
                        }
                    }, 0);
                    return rv.get();
                } finally {
                    timer.cancel();
                }
            }
            case "forkjoin":
                return ForkJoinPool.commonPool().submit(task::get).get();
            case "pool":
                return pool.submit(task::get).get();
            default:
                throw new IllegalArgumentException(mode);
        }
    }

    //! calls the Qore function \a function with \a loader as the class loader context; returns "ok: <the result>" or
    //! the exception
    public static String callIn(Object loader, String function) {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ((QoreURLClassLoader)loader).setContext();
        try {
            return call(function);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }

    //! calls callIn() as a method of an object, for a host that calls a method of an object of a tenant
    public String callInFromObject(Object loader, String function) {
        return callIn(loader, function);
    }

    //! returns the result of \a task run on a thread of the given kind: \c thread, \c virtual (a virtual thread),
    //! \c noinherit (a thread that inherits no thread-locals)
    private static String onThread(String mode, Supplier<String> task) throws Throwable {
        String[] rv = new String[1];
        Runnable r = () -> rv[0] = task.get();
        Thread t;
        switch (mode) {
            case "thread": t = new Thread(r); break;
            case "virtual": t = Thread.ofVirtual().unstarted(r); break;
            case "noinherit": t = new Thread(null, r, "noinherit", 0, false); break;
            default: throw new IllegalArgumentException(mode);
        }
        t.start();
        t.join();
        return rv[0];
    }

    //! calls callIn() on a thread of the given kind (see onThread())
    public static String threadCallIn(String mode, Object loader, String function) throws Throwable {
        return onThread(mode, () -> callIn(loader, function));
    }

    //! calls callIn() as a task of \a pool
    public static String submitCallIn(ExecutorService pool, Object loader, String function) throws Throwable {
        return pool.submit(() -> callIn(loader, function)).get();
    }

    //! calls the Qore closure \a cb with a Java closure argument on a thread of the given kind, with \a loader as
    //! the class loader context; returns the closure's result or the exception
    public static String callbackWithContext(String mode, Object loader, QoreClosure cb) throws Throwable {
        return onThread(mode, () -> {
            ((QoreURLClassLoader)loader).setContext();
            try {
                return String.valueOf(cb.call(new Caller()));
            } catch (Throwable e) {
                return e.toString();
            }
        });
    }

    //! hands a Java closure to \a consumer (ex: a listener that a host implemented)
    public static void giveCaller(java.util.function.Consumer<Object> consumer) {
        consumer.accept(new Caller());
    }

    //! calls callIn() on the ForkJoin common pool with the class loader of the Program \a ptr, found as the context
    //! class loader of a live thread; returns "not found" if no thread has it
    public static String contextLoaderCall(long ptr, String function) throws Throwable {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            ClassLoader cl = t.getContextClassLoader();
            if (cl instanceof QoreURLClassLoader && ((QoreURLClassLoader)cl).getPtr() == ptr) {
                return ForkJoinPool.commonPool().submit(() -> callIn(cl, function)).get();
            }
        }
        return "not found";
    }

    //! returns \a o.toString(), called by Java code of the host
    public static String callToString(Object o) {
        return o.toString();
    }

    //! calls \a listener.accept(), as Java code of the host that calls a listener that it holds
    public static void callAccept(java.util.function.Consumer<Object> listener) {
        listener.accept(null);
    }

    //! starts the thread of \a pool
    public static void prestart(ExecutorService pool) throws Throwable {
        pool.submit(() -> { }).get();
    }

    //! calls the Qore closure \a c on a new thread; returns its result or the exception
    public static String threadCallClosure(QoreClosure c) throws Throwable {
        String[] rv = new String[1];
        Thread t = new Thread(() -> {
            try {
                rv[0] = "ok: " + c.call();
            } catch (Throwable e) {
                rv[0] = e.toString();
            }
        });
        t.start();
        t.join();
        return rv[0];
    }

    //! calls the Qore function \a function from a static method; returns "ok: <the result>" or the exception
    public static String staticCallQore(String function) {
        return call(function);
    }

    //! A Java object used as a Qore closure, which calls the Qore function given to it
    public static class Caller implements QoreClosureMarker {
        public String call(String function) {
            return HostObjectProbe.call(function);
        }
    }

    //! returns a Java object used as a Qore closure, which calls the Qore function given to it
    public static QoreClosureMarker caller() {
        return new Caller();
    }

    //! calls the method \a method of the Qore object in the field of \a holder from Java, with \a loader as the
    //! thread's class loader context
    public static Object callHeldMethod(Object loader, HostObjectProbe holder, String method) throws Throwable {
        QoreURLClassLoader prev = QoreURLClassLoader.getCurrent();
        ((QoreURLClassLoader)loader).setContext();
        try {
            return ((QoreObject)holder.field).callMethod(method);
        } finally {
            if (prev != null) {
                prev.setContext();
            }
        }
    }

    //! returns the value of the supplier given, called from Java
    public static Object supply(java.util.function.Supplier<?> supplier) {
        return supplier.get();
    }

    //! returns its argument, for values passed through a Java object of another Program
    public Object echo(Object o) {
        return o;
    }

    //! returns a new probe, for chains of objects returned by calls
    public HostObjectProbe next() {
        return new HostObjectProbe();
    }

    //! returns the value of \a o.field, for a field read made by Java
    public static Object readField(HostObjectProbe o) {
        return o.field;
    }

    //! throws an exception that carries a new probe
    public void fail() {
        throw new ProbeException(new HostObjectProbe());
    }

    private static String call(String function) {
        try {
            return "ok: " + QoreJavaApi.callFunction(function);
        } catch (Throwable e) {
            return e.toString();
        }
    }
}
