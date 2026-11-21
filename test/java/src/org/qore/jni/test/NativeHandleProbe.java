/* Copyright (C) 2026 Qore Technologies, s.r.o.
 * SPDX-License-Identifier: MIT
 */
package org.qore.jni.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import org.qore.jni.JavaClassBuilder;
import org.qore.jni.QoreClosure;
import org.qore.jni.QoreExceptionWrapper;
import org.qore.jni.QoreInvocationHandler;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreJavaClassBase;
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectBase;
import org.qore.jni.QoreSandbox;
import org.qore.jni.QoreURLClassLoader;

/** Gives the module's native methods values that it did not issue (see test/jni-native-handles.qtest)

    Everything is reached by reflection, so that the class loads with any version of the module.  A case returns
    <tt>"exception: <class>"</tt> if it raised a Java exception, or <tt>"ok: <value>"</tt>.
*/
public final class NativeHandleProbe {
    private NativeHandleProbe() {
    }

    private interface Call {
        Object run() throws Throwable;
    }

    private static Method method(Class<?> cls, String name, Class<?>... args) throws Exception {
        Method m = cls.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static Object invoke(Object receiver, Class<?> cls, String name, Class<?>[] types, Object... args)
            throws Throwable {
        try {
            return method(cls, name, types).invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static <T> T construct(Class<T> cls, Class<?> type, Object arg) throws Throwable {
        Constructor<T> c = cls.getDeclaredConstructor(type);
        c.setAccessible(true);
        try {
            return c.newInstance(arg);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static String attempt(Call call) {
        try {
            return "ok: " + call.run();
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | AssertionError e) {
            // an error of the probe itself is not the exception that a case expects
            return "probe-error: " + e;
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            return "exception: " + t.getClass().getName() + ": " + t.getMessage();
        } catch (Throwable t) {
            return "exception: " + t.getClass().getName() + ": " + t.getMessage();
        }
    }

    private static final Class<?>[] J = {long.class};
    private static final Class<?>[] J_S = {long.class, String.class};
    private static final Class<?>[] J_J_S_A = {long.class, long.class, String.class, Object[].class};
    private static final Class<?>[] J_J_A = {long.class, long.class, Object[].class};
    private static final Class<?>[] J_S_A = {long.class, String.class, Object[].class};
    private static final Class<?>[] J_S_S_A = {long.class, String.class, String.class, Object[].class};

    private static QoreObject wrapObject(long h) throws Throwable {
        return construct(QoreObject.class, long.class, h);
    }

    private static QoreClosure wrapClosure(long h) throws Throwable {
        return construct(QoreClosure.class, long.class, h);
    }

    private static QoreInvocationHandler wrapHandler(long h) throws Throwable {
        return construct(QoreInvocationHandler.class, long.class, h);
    }

    private static QoreExceptionWrapper wrapException(long h) throws Throwable {
        return construct(QoreExceptionWrapper.class, long.class, h);
    }

    private static Object handlerInvoke0(long h) throws Throwable {
        QoreInvocationHandler receiver = wrapHandler(0);
        return invoke(receiver, QoreInvocationHandler.class, "invoke0",
            new Class<?>[] {long.class, Object.class, Method.class, Object[].class}, h, null,
            Object.class.getMethod("toString"), new Object[0]);
    }

    //! The native methods and wrappers that take a handle, by name; each uses the value given as the handle
    private static Map<String, java.util.function.LongFunction<Call>> handleCases(QoreObject obj) {
        Map<String, java.util.function.LongFunction<Call>> c = new LinkedHashMap<>();
        // objects: the native methods
        c.put("object.className0", h -> () -> invoke(obj, QoreObject.class, "className0", J, h));
        c.put("object.instanceOf0", h -> () -> invoke(obj, QoreObject.class, "instanceOf0", J_S, h, "Mutex"));
        c.put("object.callMethod0", h -> () -> invoke(obj, QoreObject.class, "callMethod0", J_J_S_A, 0L, h, "m",
            new Object[0]));
        c.put("object.callMethodSave0", h -> () -> invoke(obj, QoreObject.class, "callMethodSave0", J_J_S_A, 0L, h,
            "m", new Object[0]));
        c.put("object.getMemberValue0", h -> () -> invoke(obj, QoreObject.class, "getMemberValue0", J_S, h, "m"));
        c.put("object.release0", h -> () -> invoke(obj, QoreObjectBase.class, "release0", J, h));
        c.put("object.destroy0", h -> () -> invoke(obj, QoreObjectBase.class, "destroy0", J, h));
        // objects: the public methods of a wrapper of the value
        c.put("object.wrap.className", h -> () -> wrapObject(h).className());
        c.put("object.wrap.instanceOf", h -> () -> wrapObject(h).instanceOf("Mutex"));
        c.put("object.wrap.callMethod", h -> () -> wrapObject(h).callMethod("m"));
        c.put("object.wrap.callMethodSave", h -> () -> wrapObject(h).callMethodSave("m"));
        c.put("object.wrap.getMemberValue", h -> () -> wrapObject(h).getMemberValue("m"));
        c.put("object.wrap.release", h -> () -> {
            wrapObject(h).release();
            return "released";
        });
        c.put("object.wrap.destroy", h -> () -> {
            wrapObject(h).destroy();
            return "destroyed";
        });
        // closures
        c.put("closure.call0", h -> () -> {
            QoreClosure receiver = wrapClosure(0);
            return invoke(receiver, QoreClosure.class, "call0", J_J_A, 0L, h, new Object[0]);
        });
        c.put("closure.callSave0", h -> () -> {
            QoreClosure receiver = wrapClosure(0);
            return invoke(receiver, QoreClosure.class, "callSave0", J_J_A, 0L, h, new Object[0]);
        });
        c.put("closure.wrap.call", h -> () -> wrapClosure(h).call());
        c.put("closure.wrap.callSave", h -> () -> wrapClosure(h).callSave());
        // invocation handlers
        c.put("handler.invoke0", h -> () -> handlerInvoke0(h));
        c.put("handler.release0", h -> () -> invoke(null, QoreInvocationHandler.class, "release0", J, h));
        c.put("handler.wrap.invoke", h -> () -> wrapHandler(h).invoke(null, Object.class.getMethod("toString"),
            new Object[0]));
        // exception wrappers
        c.put("exception.getMessage0", h -> () -> {
            QoreExceptionWrapper receiver = wrapException(0);
            return invoke(receiver, QoreExceptionWrapper.class, "getMessage0", J, h);
        });
        c.put("exception.wrap.getMessage", h -> () -> wrapException(h).getMessage());
        // sandbox policies
        c.put("sandbox.capture0", h -> () -> invoke(null, QoreSandbox.class, "capture0", J, h));
        c.put("sandbox.release0", h -> () -> invoke(null, QoreSandbox.class, "release0", J, h));
        c.put("sandbox.isSandboxed0", h -> () -> invoke(null, QoreSandbox.class, "isSandboxed0", J, h));
        c.put("sandbox.checkNetworkAccess0", h -> () -> invoke(null, QoreSandbox.class, "checkNetworkAccess0",
            new Class<?>[] {long.class, byte[].class, int.class, int.class}, h, "localhost".getBytes(), 80, 1));
        c.put("sandbox.checkFileAccess0", h -> () -> invoke(null, QoreSandbox.class, "checkFileAccess0",
            new Class<?>[] {long.class, byte[].class, int.class}, h, "/x".getBytes(), 1));
        // JDBC connection waiters
        c.put("waiter.complete", h -> () -> invoke(null, QoreJavaApi.class, "completeConnectionWaiter0", J, h));
        c.put("waiter.await", h -> () -> invoke(null, QoreJavaApi.class, "awaitConnectionWaiter0", J, h));
        c.put("waiter.delete", h -> () -> invoke(null, QoreJavaApi.class, "deleteConnectionWaiter0", J, h));
        return c;
    }

    //! The native methods that take a Program pointer, which the module validates
    private static Map<String, java.util.function.LongFunction<Call>> programCases(QoreObject obj) {
        Map<String, java.util.function.LongFunction<Call>> c = new LinkedHashMap<>();
        Class<?> l = QoreURLClassLoader.class;
        Class<?> b = JavaClassBuilder.class;
        c.put("program.programReachable0", h -> () -> invoke(null, l, "programReachable0", J, h));
        c.put("program.checkProgramAccess0", h -> () -> invoke(null, l, "checkProgramAccess0", J, h));
        // the handles of generated bindings
        c.put("binding.create0", h -> () -> invoke(obj, QoreObjectBase.class, "create0",
            new Class<?>[] {long.class, long.class, long.class, Object.class, Object[].class}, h, 0L, 0L, obj,
            new Object[0]));
        c.put("binding.doStaticCall0", h -> () -> invoke(null, b, "doStaticCall0",
            new Class<?>[] {String.class, long.class, long.class, long.class, long.class, Object[].class}, "m", h, h,
            h, 0L, new Object[0]));
        c.put("binding.doFunctionCall0", h -> () -> invoke(null, b, "doFunctionCall0",
            new Class<?>[] {long.class, long.class, long.class, Object[].class}, h, h, 0L, new Object[0]));
        c.put("binding.getConstantValue0", h -> () -> invoke(null, b, "getConstantValue0",
            new Class<?>[] {long.class, long.class}, h, h));
        c.put("program.callFunction0", h -> () -> invoke(null, QoreJavaApi.class, "callFunction0", J_S_A, h, "f",
            new Object[0]));
        c.put("program.callFunctionSave0", h -> () -> invoke(null, QoreJavaApi.class, "callFunctionSave0", J_S_A, h,
            "f", new Object[0]));
        c.put("program.callStaticMethod0", h -> () -> invoke(null, QoreJavaApi.class, "callStaticMethod0", J_S_S_A, h,
            "C", "m", new Object[0]));
        c.put("program.callStaticMethodSave0", h -> () -> invoke(null, QoreJavaApi.class, "callStaticMethodSave0",
            J_S_S_A, h, "C", "m", new Object[0]));
        c.put("program.newObjectSave0", h -> () -> invoke(null, QoreJavaApi.class, "newObjectSave0", J_S_A, h, "C",
            new Object[0]));
        c.put("program.generateByteCode0", h -> () -> invoke(QoreURLClassLoader.getCurrent(), l, "generateByteCode0",
            new Class<?>[] {long.class, String.class, String.class, String.class, boolean.class, boolean.class,
                long.class}, h, "x", "x", null, false, false, 0L));
        c.put("program.getClassesInNamespace0", h -> () -> invoke(QoreURLClassLoader.getCurrent(), l,
            "getClassesInNamespace0", new Class<?>[] {long.class, String.class, String.class, boolean.class,
                boolean.class, ArrayList.class}, h, "x", null, false, false, new ArrayList<String>()));
        c.put("program.getInternalClassesForPackage0", h -> () -> invoke(QoreURLClassLoader.getCurrent(), l,
            "getInternalClassesForPackage0", new Class<?>[] {long.class, String.class, ArrayList.class}, h, "x",
            new ArrayList<String>()));
        c.put("program.clearCompilationCache0", h -> () -> invoke(QoreURLClassLoader.getCurrent(), l,
            "clearCompilationCache0", new Class<?>[] {long.class, long.class}, h, h));
        return c;
    }

    //! Returns the names of the cases that take a handle
    public static List<String> handleCaseNames() {
        return new ArrayList<>(handleCases(null).keySet());
    }

    //! Returns the names of the cases that take a Program pointer
    public static List<String> programCaseNames() {
        return new ArrayList<>(programCases(null).keySet());
    }

    //! Runs a case with a value that the module did not issue
    /** @param name the name of the case
        @param obj a live wrapper, the receiver of the native methods of objects
        @param value the value
    */
    public static String forged(String name, QoreObject obj, long value) {
        java.util.function.LongFunction<Call> f = handleCases(obj).get(name);
        if (f == null) {
            f = programCases(obj).get(name);
        }
        if (f == null) {
            throw new IllegalArgumentException(name);
        }
        return attempt(f.apply(value));
    }

    //! Returns the value that a wrapper holds
    public static long handleOf(Object wrapper) throws Throwable {
        Field f = wrapper.getClass().getDeclaredField("ref");
        f.setAccessible(true);
        Object ref = f.get(wrapper);
        Field ptr = ref.getClass().getDeclaredField("ptr");
        ptr.setAccessible(true);
        return ptr.getLong(ref);
    }

    private static long objectHandle(QoreObjectBase obj) throws Throwable {
        Field f = QoreObjectBase.class.getDeclaredField("ref");
        f.setAccessible(true);
        Object ref = f.get(obj);
        Field ptr = ref.getClass().getDeclaredField("ptr");
        ptr.setAccessible(true);
        return ptr.getLong(ref);
    }

    //! Runs a case with the handle of an object that has been released
    public static String afterRelease(String name, QoreObject obj, QoreObject receiver) throws Throwable {
        long h = objectHandle(obj);
        obj.release();
        return forged(name, receiver, h);
    }

    //! Releases the handle of the object twice with the native method; returns the result of the second release
    public static String doubleRelease(QoreObject obj, QoreObject receiver) throws Throwable {
        long h = objectHandle(obj);
        obj.release();
        return attempt(() -> invoke(receiver, QoreObjectBase.class, "release0", J, h));
    }

    //! Destroys the object, and destroys its handle again with the native method
    public static String doubleDestroy(QoreObject obj, QoreObject receiver) throws Throwable {
        long h = objectHandle(obj);
        obj.destroy();
        return attempt(() -> invoke(receiver, QoreObjectBase.class, "destroy0", J, h));
    }

    //! Makes a second wrapper over the handle and releases it; the first wrapper must then fail cleanly
    public static String doubleWrapObject(QoreObject obj) throws Throwable {
        QoreObject second = wrapObject(objectHandle(obj));
        second.release();
        String use = attempt(obj::className);
        String release = attempt(() -> {
            obj.release();
            return "released";
        });
        return use + "; " + release;
    }

    //! Makes a second wrapper over the handle of a closure and has the cleanup thread release it
    /** The caller waits for the closure to be released (a destructor of an object that it captures) and calls
        afterAliasRelease().
    */
    public static void aliasClosure(QoreClosure closure) throws Throwable {
        kept = closure;
        alias = wrapClosure(handleOf(closure));
        enqueue(alias);
    }

    //! Calls the closure kept by aliasClosure()
    public static String afterAliasRelease() {
        QoreClosure closure = (QoreClosure)kept;
        String rv = attempt(closure::call);
        kept = null;
        alias = null;
        return rv;
    }

    private static volatile Object kept;
    private static volatile Object alias;

    private static void enqueue(Object wrapper) throws Exception {
        Field f = wrapper.getClass().getDeclaredField("ref");
        f.setAccessible(true);
        ((java.lang.ref.Reference<?>)f.get(wrapper)).enqueue();
    }

    //! Uses the closure with a handle of another kind
    public static String wrongKind(String name, QoreObject obj, Object other) throws Throwable {
        long h = other instanceof QoreObjectBase ? objectHandle((QoreObjectBase)other) : handleOf(other);
        return forged(name, obj, h);
    }

    //! Destroys a proxy's handler through a second wrapper; the proxy must then fail cleanly
    public static String doubleWrapHandler(Object proxy) throws Throwable {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        QoreInvocationHandler second = wrapHandler(handleOf(handler));
        invoke(second, QoreInvocationHandler.class, "destroy", new Class<?>[0]);
        @SuppressWarnings("unchecked")
        Supplier<Object> supplier = (Supplier<Object>)proxy;
        return attempt(supplier::get);
    }

    private static volatile Object keptException;

    //! Keeps the wrapper of the exception that the closure throws, and throws a second wrapper of it to Qore, which
    //! takes the exception; messageOfKept() then uses the first wrapper
    public static void throwAliasException(QoreClosure closure) throws Throwable {
        try {
            closure.call();
        } catch (QoreExceptionWrapper e) {
            keptException = e;
            throw wrapException(handleOf(e));
        }
        throw new AssertionError("the closure did not throw");
    }

    //! Returns the message of the wrapper that throwAliasException() kept
    public static String messageOfKept() {
        QoreExceptionWrapper e = (QoreExceptionWrapper)keptException;
        keptException = null;
        return attempt(e::getMessage);
    }

    //! Returns the message of the Qore exception that a closure throws
    public static String exceptionMessage(QoreClosure closure) throws Throwable {
        try {
            closure.call();
        } catch (QoreExceptionWrapper e) {
            return e.getMessage();
        }
        throw new AssertionError("the closure did not throw");
    }

    //! Calls the object's method and the closure through the public API
    public static String normalUse(QoreObject obj, QoreClosure closure) throws Throwable {
        return obj.className() + " " + obj.instanceOf(obj.className()) + " " + obj.callMethod("m") + " "
            + obj.getMemberValue("m") + " " + closure.call();
    }

    //! Calls a proxy
    @SuppressWarnings("unchecked")
    public static Object callProxy(Object proxy) {
        return ((Supplier<Object>)proxy).get();
    }

    //! Captures the policy of the caller, uses it and closes it
    public static boolean policyUse() throws Throwable {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            return policy.isSandboxed();
        }
    }

    //! Runs a call that throws the exception, so that Qore converts it
    public static void rethrow(QoreExceptionWrapper wrapper) {
        throw wrapper;
    }

    //! Throws a wrapper of a value that the module did not issue, so that Qore converts it
    public static void throwForgedException(long value) throws Throwable {
        throw wrapException(value);
    }

    //! Returns a wrapper of a value that the module did not issue, so that Qore converts it
    public static Object forgedObject(long value) throws Throwable {
        return wrapObject(value);
    }

    //! Returns a closure wrapper of a value that the module did not issue, so that Qore converts it
    public static Object forgedClosure(long value) throws Throwable {
        return wrapClosure(value);
    }

    //! Releases the captured policy twice; returns the result of the second release
    public static String doublePolicyRelease() throws Throwable {
        Object h = invoke(null, QoreSandbox.class, "capture0", J, 0L);
        invoke(null, QoreSandbox.class, "release0", J, h);
        return attempt(() -> invoke(null, QoreSandbox.class, "release0", J, h));
    }

    //! Uses a captured policy after its release; returns the result
    public static String policyAfterRelease() throws Throwable {
        Object h = invoke(null, QoreSandbox.class, "capture0", J, 0L);
        invoke(null, QoreSandbox.class, "release0", J, h);
        return attempt(() -> invoke(null, QoreSandbox.class, "isSandboxed0", J, h));
    }

    //! Deletes a connection waiter twice, and uses it afterwards; returns the two results
    public static String waiterAfterDelete() throws Throwable {
        Object h = invoke(null, QoreJavaApi.class, "newConnectionWaiter0", new Class<?>[0]);
        invoke(null, QoreJavaApi.class, "deleteConnectionWaiter0", J, h);
        String second = attempt(() -> invoke(null, QoreJavaApi.class, "deleteConnectionWaiter0", J, h));
        String use = attempt(() -> invoke(null, QoreJavaApi.class, "completeConnectionWaiter0", J, h));
        return second + "; " + use;
    }

    //! Returns the first registered binding handle in the method of the generated class
    public static long methodBinding(byte[] bytes, String method) {
        List<Long> handles = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if (!name.equals(method)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Long && (Long)value < 0) {
                            handles.add((Long)value);
                        }
                    }
                };
            }
        }, 0);
        if (handles.isEmpty()) {
            throw new AssertionError("no binding handle in " + method);
        }
        return handles.get(0);
    }

    //! A Java class that declares the field of a generated class's binding
    public static final class ClaimsClass extends QoreObject {
        public static long $qore_cls_handle;

        public ClaimsClass(long classHandle) throws Throwable {
            super(classHandle, 0L, 0L);
        }
    }

    private static volatile long pendingClaim;

    //! Java classes that declare the static final field of a class binding, set when they are initialized
    public static final class ClaimsSame extends QoreObject {
        public static final long $qore_cls_handle = pendingClaim;

        public ClaimsSame(long classHandle) throws Throwable {
            super(classHandle, 0L, 0L);
        }
    }

    public static final class ClaimsOther extends QoreObject {
        public static final long $qore_cls_handle = pendingClaim;

        public ClaimsOther(long classHandle) throws Throwable {
            super(classHandle, 0L, 0L);
        }
    }

    public static final class ClaimsBuiltin extends QoreObject {
        public static final long $qore_cls_handle = pendingClaim;

        public ClaimsBuiltin(long classHandle) throws Throwable {
            super(classHandle, 0L, 0L);
        }
    }

    //! Creates an object of a class with a static final binding field, once for each class
    /** @param which \c "same", \c "other" or \c "builtin"
    */
    public static String claimFinal(String which, long claimed, long ctor, String method, Object... args) {
        pendingClaim = claimed;
        return attempt(() -> {
            QoreObject o;
            switch (which) {
                case "same": o = new ClaimsSame(ctor); break;
                case "other": o = new ClaimsOther(ctor); break;
                default: o = new ClaimsBuiltin(ctor); break;
            }
            try {
                return o.className() + " " + o.callMethod(method, args);
            } finally {
                o.destroy();
            }
        });
    }

    private static int subclassCount;

    //! Defines a Java class that extends the class, with a constructor that passes its arguments on
    private static synchronized Class<?> defineSubclass(Class<?> parent) {
        String name = "org/qore/jni/test/GeneratedSubclass" + (++subclassCount);
        String parentName = parent.getName().replace('.', '/');
        String descriptor = "(JJJ[Ljava/lang/Object;)V";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parentName, null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.LLOAD, 1);
        m.visitVarInsn(Opcodes.LLOAD, 3);
        m.visitVarInsn(Opcodes.LLOAD, 5);
        m.visitVarInsn(Opcodes.ALOAD, 7);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, parentName, "<init>", descriptor, false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        // in the class loader of the generated class, where the module finds the class's superclasses by name
        try {
            Method define = QoreURLClassLoader.class.getDeclaredMethod("defineClassUnconditional", String.class,
                byte[].class);
            define.setAccessible(true);
            return (Class<?>)define.invoke(parent.getClassLoader(), name.replace('/', '.'), w.toByteArray());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot define the subclass", e);
        }
    }

    //! Creates an object of a Java subclass of a generated class, \a depth levels below it, with a class binding
    public static String constructSubclass(Class<?> generated, int depth, long binding, Object... args) {
        return attempt(() -> {
            Class<?> cls = generated;
            for (int i = 0; i < depth; ++i) {
                cls = defineSubclass(cls);
            }
            QoreObject o = (QoreObject)cls.getConstructor(long.class, long.class, long.class, Object[].class)
                .newInstance(binding, 0L, 0L, args);
            try {
                return "constructed";
            } finally {
                o.destroy();
            }
        });
    }

    private static volatile java.util.function.LongSupplier lateHook;

    //! Called by the class initializer of the class that lateBindingField() defines
    public static long runLateHook() {
        return lateHook.getAsLong();
    }

    private static byte[] subclassBytes(String name, String parent, boolean lateField) {
        String descriptor = "(JJJ[Ljava/lang/Object;)V";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.LLOAD, 1);
        m.visitVarInsn(Opcodes.LLOAD, 3);
        m.visitVarInsn(Opcodes.LLOAD, 5);
        m.visitVarInsn(Opcodes.ALOAD, 7);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", descriptor, false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        if (lateField) {
            w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "$qore_cls_handle", "J", null,
                null).visitEnd();
            MethodVisitor c = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            c.visitCode();
            c.visitMethodInsn(Opcodes.INVOKESTATIC, "org/qore/jni/test/NativeHandleProbe", "runLateHook", "()J", false);
            c.visitFieldInsn(Opcodes.PUTSTATIC, name, "$qore_cls_handle", "J");
            c.visitInsn(Opcodes.RETURN);
            c.visitMaxs(0, 0);
            c.visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    private static Class<?> defineIn(ClassLoader loader, String name, byte[] bytes) throws Exception {
        Method define = QoreURLClassLoader.class.getDeclaredMethod("defineClassUnconditional", String.class,
            byte[].class);
        define.setAccessible(true);
        return (Class<?>)define.invoke(loader, name.replace('/', '.'), bytes);
    }

    //! A class whose binding field is set after a Java subclass of it was first constructed
    /** The subclass is first constructed with the constructor of \a child while the field is still 0, in the class
        initializer, which then sets the field to the binding of the ancestor \a parent.

        @return the result of the first construction, and of a construction with the ancestor's constructor
    */
    public static String lateBindingField(String child, String parent, Object[] childArgs, Object[] parentArgs)
            throws Throwable {
        ClassLoader l = QoreURLClassLoader.getCurrent();
        Class<?> generated = l.loadClass(child);
        long own = generated.getField("$qore_cls_handle").getLong(null);
        long ancestor = l.loadClass(parent).getField("$qore_cls_handle").getLong(null);
        int id;
        synchronized (NativeHandleProbe.class) {
            id = ++subclassCount;
        }
        String midName = "org/qore/jni/test/LateMiddle" + id;
        String subName = "org/qore/jni/test/LateLeaf" + id;
        String[] first = {"not run"};
        Class<?>[] leaf = new Class<?>[1];
        lateHook = () -> {
            first[0] = attempt(() -> {
                leaf[0] = defineIn(l, subName, subclassBytes(subName, midName, false));
                QoreObject o = (QoreObject)leaf[0].getConstructor(long.class, long.class, long.class, Object[].class)
                    .newInstance(own, 0L, 0L, childArgs);
                o.destroy();
                return "constructed";
            });
            return ancestor;
        };
        Class<?> mid = defineIn(l, midName, subclassBytes(midName, generated.getName().replace('.', '/'), true));
        Class.forName(mid.getName(), true, l);
        String second = attempt(() -> {
            QoreObject o = (QoreObject)leaf[0].getConstructor(long.class, long.class, long.class, Object[].class)
                .newInstance(ancestor, 0L, 0L, parentArgs);
            o.destroy();
            return "constructed";
        });
        return first[0] + "; " + second;
    }

    //! Defines M extends the generated class, A extends M and B extends A, where a method of M returns B, so that the
    //! Qore class of B is made while the one of A is still in progress; returns the result of constructing A, then B
    public static String loadOrder(String generatedName, Object... args) throws Throwable {
        ClassLoader l = QoreURLClassLoader.getCurrent();
        Class<?> generated = l.loadClass(generatedName);
        long own = generated.getField("$qore_cls_handle").getLong(null);
        int id;
        synchronized (NativeHandleProbe.class) {
            id = ++subclassCount;
        }
        String m = "org/qore/jni/test/OrderM" + id;
        String a = "org/qore/jni/test/OrderA" + id;
        String b = "org/qore/jni/test/OrderB" + id;
        ClassWriter w = new ClassWriter(new ClassReader(subclassBytes(m, generated.getName().replace('.', '/'), false)),
            ClassWriter.COMPUTE_MAXS);
        new ClassReader(subclassBytes(m, generated.getName().replace('.', '/'), false)).accept(w, 0);
        MethodVisitor make = w.visitMethod(Opcodes.ACC_PUBLIC, "make", "()L" + b + ";", null, null);
        make.visitCode();
        make.visitInsn(Opcodes.ACONST_NULL);
        make.visitInsn(Opcodes.ARETURN);
        make.visitMaxs(0, 0);
        make.visitEnd();
        defineIn(l, m, w.toByteArray());
        Class<?> ca = defineIn(l, a, subclassBytes(a, m, false));
        Class<?> cb = defineIn(l, b, subclassBytes(b, a, false));
        String first = construct(ca, own, args);
        return first + "; " + construct(cb, own, args);
    }

    private static String construct(Class<?> cls, long binding, Object[] args) {
        return attempt(() -> {
            QoreObject o = (QoreObject)cls.getConstructor(long.class, long.class, long.class, Object[].class)
                .newInstance(binding, 0L, 0L, args);
            o.destroy();
            return "constructed";
        });
    }

    //! Calls an invocation handler with a null method through the public interface
    public static String nullHandlerMethod(Object proxy) {
        return attempt(() -> Proxy.getInvocationHandler(proxy).invoke(proxy, null, null));
    }

    //! Calls a static method with a null name through the public API
    public static String nullStaticMethodName() {
        return attempt(() -> QoreJavaApi.callStaticMethod("HandleTarget", null));
    }

    //! A class with a binding field and Java subclasses below it: the classes between a subclass and its binding
    public abstract static class ChainBase extends QoreJavaClassBase {
        public static final long $qore_cls_handle = pendingClaim;

        public ChainBase(long classHandle, Object... args) throws Throwable {
            super(classHandle, 0L, 0L, args);
        }
    }

    public static class ChainMiddle extends ChainBase {
        public ChainMiddle(long classHandle, Object... args) throws Throwable {
            super(classHandle, args);
        }
    }

    public static final class ChainLeaf extends ChainMiddle {
        public ChainLeaf(long classHandle, Object... args) throws Throwable {
            super(classHandle, args);
        }
    }

    //! Creates an object of a Java class two levels below a class bound to \a claimed (set by the first call)
    public static String constructChain(long claimed, long ctor, Object... args) {
        // read once, when ChainBase is initialized
        pendingClaim = claimed;
        return attempt(() -> {
            ChainLeaf o = new ChainLeaf(ctor, args);
            try {
                return "constructed";
            } finally {
                o.destroy();
            }
        });
    }

    //! A Java subclass of a class that declares the field of a generated class's binding
    public static final class ClaimsSubclass extends QoreObject {
        public ClaimsSubclass(long classHandle) throws Throwable {
            super(classHandle, 0L, 0L);
        }
    }

    //! Returns the class binding handle of a generated class
    public static long classBinding(byte[] bytes) {
        long[] rv = new long[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public net.bytebuddy.jar.asm.FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (name.equals("$qore_cls_handle")) {
                    rv[0] = (Long)value;
                }
                return null;
            }
        }, 0);
        if (rv[0] == 0) {
            throw new AssertionError("no class binding");
        }
        return rv[0];
    }

    //! Creates an object of a Java class that claims the class \a claimed with the constructor of the class \a ctor
    public static String claimClass(long claimed, long ctor, String method, Object... args) {
        ClaimsClass.$qore_cls_handle = claimed;
        return attempt(() -> {
            ClaimsClass o = new ClaimsClass(ctor);
            try {
                return o.className() + " " + (method == null ? "" : o.callMethod(method, args));
            } finally {
                o.destroy();
            }
        });
    }

    //! Creates an object of a Java class with no class binding of its own with the constructor of an unrelated class
    public static String unrelatedConstructor(long ctor) {
        return attempt(() -> new ClaimsSubclass(ctor).className());
    }

    //! Passes null arrays to the native sandbox checks
    public static List<String> nullSandboxArrays() {
        List<String> rv = new ArrayList<>();
        rv.add(attempt(() -> invoke(null, QoreSandbox.class, "checkNetworkAccess0",
            new Class<?>[] {long.class, byte[].class, int.class, int.class}, 0L, null, 80, 1)));
        rv.add(attempt(() -> invoke(null, QoreSandbox.class, "checkFileAccess0",
            new Class<?>[] {long.class, byte[].class, int.class}, 0L, null, 1)));
        return rv;
    }

    //! Calls a method binding on an object through the public entry point that takes the wrapper
    public static String callBinding(String name, Object obj, long binding, Object... args) {
        return attempt(() -> JavaClassBuilder.doNormalCall(name, (QoreObjectBase)obj, binding, 0L, args));
    }

    //! Calls a method binding with a value as the object through the public entry point that takes a handle
    public static String callBindingHandle(String name, long value, long binding, Object... args) {
        return attempt(() -> JavaClassBuilder.doNormalCall(name, value, binding, 0L, args));
    }

    //! Calls the method with a null name through the public API
    public static String nullMethodName(QoreObject obj) {
        return attempt(() -> obj.callMethod(null));
    }

    //! Returns the names of the members of the public API that must not take or give a raw handle and are public
    public static List<String> publicHandleMembers() throws Exception {
        List<String> rv = new ArrayList<>();
        checkConstructor(rv, QoreObject.class, long.class);
        checkConstructor(rv, QoreObjectBase.class, long.class);
        checkConstructor(rv, QoreClosure.class, long.class);
        checkConstructor(rv, org.qore.jni.QoreJavaObjectPtr.class, long.class);
        for (Class<?> cls : new Class<?>[] {QoreObjectBase.class, QoreClosure.class}) {
            for (String name : new String[] {"get", "qoreJniEnterHandle", "qoreJniExitHandle"}) {
                if (Modifier.isPublic(cls.getDeclaredMethod(name).getModifiers())) {
                    rv.add(cls.getSimpleName() + "." + name + "()");
                }
            }
        }
        if (Modifier.isPublic(org.qore.jni.QoreJavaObjectPtr.class.getDeclaredField("ptr").getModifiers())) {
            rv.add("QoreJavaObjectPtr.ptr");
        }
        for (Field f : QoreObjectBase.class.getDeclaredFields()) {
            if (f.getType() == long.class && (f.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
                rv.add("QoreObjectBase." + f.getName());
            }
        }
        return rv;
    }

    private static void checkConstructor(List<String> rv, Class<?> cls, Class<?> arg) throws Exception {
        if (Modifier.isPublic(cls.getDeclaredConstructor(arg).getModifiers())) {
            rv.add(cls.getSimpleName() + "(long)");
        }
    }

    private static volatile CountDownLatch inCall;
    private static volatile CountDownLatch gate;

    //! Called by a Qore method: signals that the call is in progress and waits for the gate
    public static void blockInCall() throws InterruptedException {
        inCall.countDown();
        gate.await();
    }

    //! Releases the handle through a second wrapper while a call on the first is in progress on another thread
    /** The call holds the native state, so it completes on a valid object; the state is freed when it returns.

        @return the result of the call and of a use of the wrapper after it
    */
    public static String releaseAliasDuringCall(QoreObject obj) throws Throwable {
        inCall = new CountDownLatch(1);
        gate = new CountDownLatch(1);
        final String[] result = {"no result"};
        QoreObject second = wrapObject(objectHandle(obj));
        Thread t = new Thread(() -> result[0] = attempt(() -> obj.callMethod("block")));
        t.start();
        try {
            inCall.await();
            second.release();
        } finally {
            gate.countDown();
            t.join();
        }
        return result[0] + "; " + attempt(obj::className);
    }
}
