package org.qore.jni;

import org.qore.jni.QoreURLClassLoader;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//! This class provides methods that allow Java to interface with Qore code
/**
 */
public class QoreJavaApi {
    //! Initialize the \c qore library and the \c jni module from a native Java thread
    /** This method allows \c Qore functionality or Java APIs backed by \c %Qore APIs to be used from native Java
        threads in an existing JVM process not started by \c %Qore itself.

        This requires the platform-dependent \c qore library to be found in a directory set in the
        \c java.library.path or by setting the \c QORE_LIBRARY environment variable to the absolute path of the
        \c qore library.

        If neither of these are set, then calling this method will result in a \c java.lang.UnsatisfiedLinkError
        exception being raised.

        This method is also safe to call when the Qore library and the jni module have been initialized already.
     */
    public static void initQore() throws Throwable {
        //System.out.println("DBG: QoreJavaApi::initQore()");
        try {
            QoreURLClassLoader.getProgramPtr();
        } catch (NullPointerException | IllegalStateException e0) {
            //ClassLoader ldr = QoreJavaApi.class.getClassLoader();
            //System.out.printf("QoreJavaApi.initQore() cl: %x (%s)\n", ldr.hashCode(),
            //  ldr.getClass().getCanonicalName());

            // initialize jni module
            long ptr;
            try {
                // try to get a Qore context for a Java thread not associated with a Qore Program object
                // but within a process initialized by the Qore library and the jni module
                ptr = initQore0();
            } catch (UnsatisfiedLinkError e1) {
                // check for environment variable to Qore lib
                String qore_lib_loc = System.getenv("QORE_LIBRARY");
                if (qore_lib_loc != null) {
                    // load the qore library dynamically, which will result in the jni module being loaded automatically
                    // do to a JNI_OnLoad() function in libqore that initializes the Qore library and loads the jni
                    // module
                    System.load(qore_lib_loc);
                } else {
                    // load the qore library from \c java.library.path
                    System.loadLibrary("qore");
                }
                // now we can run our native methods
                ptr = initQore0();
            }
            QoreURLClassLoader.setProgramPtr(ptr);
        }
    }

    //! Initialize the \c qore library and the \c jni module in memory only from a native Java thread
    /** This method ensures that the qore library is loaded when the jni module is bootstrapped from Java

        This requires the platform-dependent \c qore library to be found in a directory set in the
        \c java.library.path or by setting the \c QORE_LIBRARY environment variable to the absolute path of the
        \c qore library.

        If neither of these are set, then calling this method will result in a \c java.lang.UnsatisfiedLinkError
        exception being raised.

        This method is also safe to call when the Qore library and the jni module have been initialized already.
     */
    public static void initQoreBootstrap() throws Throwable {
        //System.out.println("DBG: QoreJavaApi::initQoreBootstrap()");
        try {
            QoreURLClassLoader.getProgramPtr();
        } catch (NullPointerException | IllegalStateException e0) {
            // initialize jni module
            try {
                // try to get a Qore context for a Java thread not associated with a Qore Program object
                // but within a process initialized by the Qore library and the jni module
                initQoreBootstrap0();
            } catch (UnsatisfiedLinkError e1) {
                // check for environment variable to Qore lib
                String qore_lib_loc = System.getenv("QORE_LIBRARY");
                if (qore_lib_loc != null) {
                    // load the qore library dynamically, which will result in the jni module being loaded automatically
                    // do to a JNI_OnLoad() function in libqore that initializes the Qore library and loads the jni
                    // module
                    System.load(qore_lib_loc);
                } else {
                    // load the qore library from \c java.library.path
                    System.loadLibrary("qore");
                }
                initQoreBootstrap0();
            }
        }
    }

    //! Calls the given Qore function with the given arguments and returns the result
    public static Object callFunction(String name, Object... args) throws Throwable {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.format("cl: %s func: '%s' pgm: %x\n", (cl != null ? cl.toString() : "null"), name,
        //    cl.getProgramPtr());
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callFunction0(QoreURLClassLoader.getProgramPtr(), name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls a Qore function and returns the result as a Java Object, takes a variable number of arguments
    public static Object callFunctionArgs(String name, Object[] args) throws Throwable {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.format("cl: %s func: '%s' pgm: %x\n", (cl != null ? cl.toString() : "null"), name,
        //    cl.getProgramPtr());
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callFunction0(QoreURLClassLoader.getProgramPtr(), name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls the given Qore function with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param name the name of the function to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static Object callFunctionSave(String name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callFunctionSave0(QoreURLClassLoader.getProgramPtr(), name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls the given Qore function with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data, takes a variable number of arguments
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param name the name of the function to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static Object callFunctionSaveArgs(String name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callFunctionSave0(QoreURLClassLoader.getProgramPtr(), name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls the given Qore static method with the given arguments and returns the result
    public static Object callStaticMethod(String class_name, String method_name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callStaticMethod0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls a Qore static method and returns the result as a Java Object, takes a variable number of arguments
    public static Object callStaticMethodArgs(String class_name, String method_name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callStaticMethod0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls the given Qore static method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param class_name the name of the class where the static method is defined; can have a namespace-justified path
     * (ex: \c "Namespace::ClassName")
     * @param method_name the static method name to call
     * @param args argument to the static method call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static Object callStaticMethodSave(String class_name, String method_name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callStaticMethodSave0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Calls the given Qore static method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data, takes a variable number of arguments
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param class_name the name of the class where the static method is defined; can have a namespace-justified path
     * (ex: \c "Namespace::ClassName")
     * @param method_name the static method name to call
     * @param args argument to the static method call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static Object callStaticMethodSaveArgs(String class_name, String method_name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return callStaticMethodSave0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Creates a new QoreObject from the class name and constructor arguments; a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param class_name the class name or namespace-justified path (ex: \c "Qore::SQL::SQLStatement") of the object to create
     * @param args optional arguments to the constructor
     * @return the object created
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static QoreObject newObjectSave(String class_name, Object... args) throws Throwable {
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return newObjectSave0(QoreURLClassLoader.getProgramPtr(), class_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Creates a new QoreObject from the class name and constructor arguments; a strong reference to the object is stored in thread-local data under the given key
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param class_name the class name or namespace-justified path (ex: \c "Qore::SQL::SQLStatement") of the object to create
     * @param args optional arguments to the constructor
     * @return the object created
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public static QoreObject newObjectSaveArgs(String class_name, Object[] args) throws Throwable {
        Object[] ctx = QoreURLClassLoader.saveContext();
        try {
            return newObjectSave0(QoreURLClassLoader.getProgramPtr(), class_name, args);
        } finally {
            QoreURLClassLoader.restoreContext(ctx);
        }
    }

    //! Registers the current Java thread as a Qore thread
    /**
     *
     * @return true if the thread was registered, false if not (i.e. the current thread is already a valid Qore
     * thread)
     *
     * @since jni module v2.0.6
     */
    public static boolean registerJavaThread() {
        return registerJavaThread0();
    }

    //! Deregisters the current thread as a Qore thread
    /**
     *
     * @throws Throwable if there is an internal Qore library error deregistering the thread; if the current thread
     * is not a valid Qore thread, no error is raised
     *
     * @note only call this in the same thread as a successful call to registerJavaThread()
     *
     * @since jni module v2.0.6
     */
    public static void deregisterJavaThread() {
        deregisterJavaThread0();
    }

    //! Returns the Program pointers of the class loaders that defined the classes of the calling thread's stack
    /** For each frame, the Program of the class loader that defined the frame's class (see getLoaderProgramPtr());
        frames of the JDK and of the JVM's classpath are left out.  The module refuses a call into a Program from Java
        code of a sandboxed Program other than that Program, and from code of an unknown class loader (qore#7023,
        qore#7131)

        @return the distinct Program pointers, in stack order
    */
    static long[] getStackProgramPtrs() {
        return STACK_WALKER.walk(QoreJavaApi::stackProgramPtrs);
    }

    //! the pointer that getStackProgramPtrs() adds when the code that made the call is not known (qore#7081)
    /** The first frame below the entry point of the call that is not passed over is a class of the JDK, an adapter
        or a proxy (see callOrigin())
    */
    static final long UNKNOWN_ORIGIN_PTR = -2L;

    //! the pointer that getStackProgramPtrs() adds when an adapter or a proxy made the call (qore#7081)
    /** As UNKNOWN_ORIGIN_PTR, also passing over the frames of the JDK's named classes, whose code does not depend on
        an object that other code made (see adapterOrigin()): for class loading, which the JDK's class loading code
        mediates for any code
    */
    static final long ADAPTER_ORIGIN_PTR = -3L;

    //! walks every frame, also those of hidden classes (ex: lambdas), which are defined by the class loader of the
    //! class that defines them
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(EnumSet.of(
        StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES));

    //! Returns the Program pointers of the frames, and the pointers of the origin of the call (qore#7081)
    /** The stack is walked from the innermost frame: the frames of the walk itself, up to the native method of the
        call from Java into Qore that asks (the first native frame), are not the caller's.  Below it, the frames of
        the call's entry point (this class, the class loader, the generated bindings) and of the JDK's reflection,
        method handle and class loading machinery are passed over, as they run no code of their own choice; the first
        other frame is the code that made the call (see callOrigin()), and UNKNOWN_ORIGIN_PTR is added if it is not
        known.  ADAPTER_ORIGIN_PTR is added if an adapter or a proxy made the call, also passing over the frames of
        the JDK's named classes (see adapterOrigin())
    */
    private static long[] stackProgramPtrs(java.util.stream.Stream<StackWalker.StackFrame> frames) {
        long[] rv = new long[8];
        int n = 0;
        // true once the native method of the call has been passed
        boolean below = false;
        // the origins, ORIGIN_PASS while not known
        int strict = ORIGIN_PASS;
        int adapter = ORIGIN_PASS;
        for (Iterator<StackWalker.StackFrame> i = frames.iterator(); i.hasNext(); ) {
            StackWalker.StackFrame f = i.next();
            Class<?> cls = f.getDeclaringClass();
            long ptr = getLoaderProgramPtr(cls.getClassLoader());
            if (ptr != 0L) {
                boolean seen = false;
                for (int j = 0; j < n; ++j) {
                    if (rv[j] == ptr) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    if (n == rv.length) {
                        rv = Arrays.copyOf(rv, n * 2);
                    }
                    rv[n++] = ptr;
                }
            }
            if (!below) {
                below = f.isNativeMethod();
                continue;
            }
            if (adapter != ORIGIN_PASS) {
                continue;
            }
            int kind = frameKind(cls);
            boolean nativeMethod = f.isNativeMethod();
            if (strict == ORIGIN_PASS) {
                strict = callOrigin(kind, nativeMethod, ptr);
            }
            adapter = adapterOrigin(kind, nativeMethod, ptr);
        }
        if (strict == ORIGIN_UNKNOWN) {
            rv = Arrays.copyOf(rv, n + 2);
            rv[n++] = UNKNOWN_ORIGIN_PTR;
            if (adapter == ORIGIN_UNKNOWN) {
                rv[n++] = ADAPTER_ORIGIN_PTR;
            }
        }
        return Arrays.copyOf(rv, n);
    }

    //! a frame that runs no code of its own choice for the call (see callOrigin())
    private static final int ORIGIN_PASS = 0;
    //! the code that made the call is known: Qore code (through the module), or Java code of a Program or of a trusted
    //! class loader
    private static final int ORIGIN_KNOWN = 1;
    //! the code that made the call cannot be told: an object of the JDK's classes only, or an adapter or proxy class
    private static final int ORIGIN_UNKNOWN = 2;

    //! Returns the origin of the call that a frame tells (qore#7081)
    /** A Java object has no Program of its own: an object made only of the JDK's classes, of an adapter or of a proxy
        class does the same whatever code made it, so a call into Qore that such a frame makes has no known origin.
        A frame of a class of a Program's class loader is that Program's code (see getLoaderProgramPtr()), and a
        frame of a trusted class loader's class other than the JDK's (ex: the module's own library classes) is
        trusted code; the native method of a call from Qore into Java (ex: the module calling a Java method for Qore
        code) is the boundary to the Qore code that the call records as its caller

        @param kind the kind of the frame's class (see frameKind())
        @param nativeMethod true if the frame is of a native method
        @param ptr the Program pointer of the class's class loader (see getLoaderProgramPtr())
    */
    private static int callOrigin(int kind, boolean nativeMethod, long ptr) {
        if (nativeMethod && kind == KIND_MODULE) {
            return ORIGIN_KNOWN;
        }
        if (kind == KIND_PASS || kind == KIND_MODULE || kind == KIND_DISPATCH) {
            return ORIGIN_PASS;
        }
        // an adapter or a proxy does what the code that made it chose, whatever its class loader
        if (kind == KIND_ADAPTER) {
            return ORIGIN_UNKNOWN;
        }
        // a class of a Program's class loader (also a lambda of the Program's code)
        if (ptr != 0L) {
            return ORIGIN_KNOWN;
        }
        return kind == KIND_JDK ? ORIGIN_UNKNOWN : ORIGIN_KNOWN;
    }

    //! Returns the origin of the call that a frame tells, passing over the frames of the JDK's own code
    /** As callOrigin(), except that a frame of the JDK's own code (a named class of the JDK, or a lambda of one) is
        passed over: its code is the JDK's, whatever code called it, and the frame below it is asked
    */
    private static int adapterOrigin(int kind, boolean nativeMethod, long ptr) {
        if (kind == KIND_JDK && ptr == 0L) {
            return ORIGIN_PASS;
        }
        return callOrigin(kind, nativeMethod, ptr);
    }

    //! the frames of the entry points of Java's calls into Qore and of the JDK's reflection and method handles
    private static final int KIND_PASS = 0;
    //! the classes of this module that run Java's calls into Qore (their native methods are the boundary to Qore)
    private static final int KIND_MODULE = 1;
    //! the module's class that calls Java methods for Qore code (one copy is defined for each Program)
    private static final int KIND_DISPATCH = 2;
    //! the JDK's own code: a named class of a module of the JDK, or a lambda of one
    private static final int KIND_JDK = 3;
    //! an adapter or a proxy: a proxy class, a hidden class that is not a lambda of the class that defines it, or a
    //! class of a module that is not of the JDK's boot layer (ex: a dynamic module), whatever its class loader
    private static final int KIND_ADAPTER = 4;
    //! any other class
    private static final int KIND_OTHER = 5;

    //! Returns the kind of the frames of the class \a cls; only the frames near the entry point of the call are
    //! classified, until the code that made the call is found
    private static int frameKind(Class<?> cls) {
        String name = cls.getName();
        ClassLoader loader = cls.getClassLoader();
        // by name, as the module defines some of them with other class loaders: a class of the same name that a
        // Program's class loader or an unknown class loader defines is attributed to its class loader all the same
        // (see stackProgramPtrs()), which refuses its calls into another Program
        if (MODULE_CLASS_NAMES.contains(name)) {
            return KIND_MODULE;
        }
        // one copy is defined by the class loader of each Program
        if (loader instanceof QoreURLClassLoader && !cls.isHidden() && (name.equals("org.qore.jni.QoreJavaDynamicApi")
            || name.startsWith("org.qore.jni.QoreJavaDynamicApi$"))) {
            return KIND_DISPATCH;
        }
        // the Java classes generated for the Qore definitions of a Program call into Qore through JavaClassBuilder
        // and QoreObjectBase with the bindings of their Program, which are checked
        if (loader instanceof QoreURLClassLoader && !cls.isHidden() && isGeneratedBindingName(name)) {
            return KIND_PASS;
        }
        if (Proxy.isProxyClass(cls)) {
            return KIND_ADAPTER;
        }
        Module module = cls.getModule();
        // the JDK's own code: a named module of the boot or platform class loader
        boolean jdk = false;
        if (module.isNamed()) {
            if (module.getLayer() == null) {
                // a dynamic module, of no layer (ex: of an adapter that the JDK defines for an interface of any class
                // loader)
                return KIND_ADAPTER;
            }
            jdk = loader == null || loader == PLATFORM_LOADER;
        }
        if (jdk) {
            String pkg = cls.getPackageName();
            // the machinery of reflection, method handles and class loading runs no code of its own choice: the
            // frame below it made the call (an object that carries the code that it runs has frames of its own
            // class, which are not passed over)
            if (pkg.equals("jdk.internal.reflect") || cls == java.lang.reflect.Method.class
                || cls == java.lang.reflect.Constructor.class || cls == ClassLoader.class || cls == Class.class
                || pkg.equals("jdk.internal.loader")
                || (pkg.equals("java.lang.invoke") && (cls.isHidden() || name.endsWith("$Holder")))) {
                return KIND_PASS;
            }
        }
        if (cls.isHidden()) {
            // a lambda is defined in the nest of the class that defines it, by its class loader and in its module: its
            // code is that class's code; any other hidden class is an adapter
            Class<?> host = cls.getNestHost();
            if (host == cls || host.isHidden() || host.getModule() != module || host.getClassLoader() != loader) {
                return KIND_ADAPTER;
            }
            return frameKind(host);
        }
        // a named module of another class loader (ex: an application's module) is the code of its class loader
        return jdk ? KIND_JDK : KIND_OTHER;
    }

    //! the classes of the module whose methods are the entry points of Java's calls into Qore
    private static final java.util.Set<String> MODULE_CLASS_NAMES = java.util.Set.of(
        "org.qore.jni.QoreJavaApi", "org.qore.jni.QoreURLClassLoader", "org.qore.jni.JavaClassBuilder",
        "org.qore.jni.QoreObjectBase", "org.qore.jni.QoreClosure", "org.qore.jni.QoreObject",
        "org.qore.jni.QoreInvocationHandler");

    //! Returns true if \a name is the name of a Java class generated for the Qore definitions of a Program
    private static boolean isGeneratedBindingName(String name) {
        return name.startsWith("qore.") || name.startsWith("qoremod.") || name.startsWith("python.")
            || name.startsWith("pythonmod.");
    }

    //! the origin that setOrigin() records for an object that more than one origin supplied (qore#7081)
    static final long ORIGIN_CONFLICT = -1L;

    //! the origins of the Java objects that sandboxed code made or handed over, by the identity hash of the objects
    /** Each bucket is an array of entries, which is replaced, never changed, so that it is read without a lock; each
        entry is a pair of a weak reference to the object, which does not keep it alive, and its origin (qore#7081)
    */
    private static final ConcurrentHashMap<Integer, Object[][]> ORIGINS = new ConcurrentHashMap<>();
    //! the number of origins recorded since the entries of the collected objects were last removed
    private static final AtomicInteger ORIGIN_ADDS = new AtomicInteger();
    //! the number of origins recorded at which the entries of the collected objects are removed
    private static volatile int originPurgeAt = 64;
    //! true while the entries of the collected objects are being removed
    private static final AtomicBoolean ORIGIN_PURGING = new AtomicBoolean();

    //! Removes the entries of the objects that have been collected, once enough origins have been recorded since
    //! they were last removed (amortized)
    private static void purgeOrigins() {
        if (ORIGIN_ADDS.get() < originPurgeAt || !ORIGIN_PURGING.compareAndSet(false, true)) {
            return;
        }
        try {
            ORIGIN_ADDS.set(0);
            int live = 0;
            for (Integer key : ORIGINS.keySet()) {
                Object[][] rv = ORIGINS.computeIfPresent(key, (k, bucket) -> {
                    int n = 0;
                    for (Object[] e : bucket) {
                        if (((WeakReference<?>)e[0]).get() != null) {
                            ++n;
                        }
                    }
                    if (n == bucket.length) {
                        return bucket;
                    }
                    if (n == 0) {
                        return null;
                    }
                    Object[][] kept = new Object[n][];
                    int j = 0;
                    for (Object[] e : bucket) {
                        if (((WeakReference<?>)e[0]).get() != null) {
                            kept[j++] = e;
                        }
                    }
                    return kept;
                });
                if (rv != null) {
                    live += rv.length;
                }
            }
            originPurgeAt = Math.max(64, live * 2);
        } finally {
            ORIGIN_PURGING.set(false);
        }
    }

    //! Records the origin \a origin (the ID of a Program plus one, or ORIGIN_CONFLICT) of the object \a obj
    /** An origin, once recorded, is kept: an object that another origin supplies too gets ORIGIN_CONFLICT, which
        reaches no Program.  Called by the module only (qore#7081)
    */
    static void setOrigin(Object obj, long origin) {
        if (obj == null) {
            return;
        }
        boolean[] added = new boolean[1];
        ORIGINS.compute(System.identityHashCode(obj), (k, bucket) -> {
            if (bucket != null) {
                for (int i = 0; i < bucket.length; ++i) {
                    if (((WeakReference<?>)bucket[i][0]).get() == obj) {
                        if ((Long)bucket[i][1] == origin || (Long)bucket[i][1] == ORIGIN_CONFLICT) {
                            return bucket;
                        }
                        Object[][] rv = bucket.clone();
                        rv[i] = new Object[] {bucket[i][0], ORIGIN_CONFLICT};
                        return rv;
                    }
                }
            }
            int n = bucket == null ? 0 : bucket.length;
            Object[][] rv = bucket == null ? new Object[1][] : Arrays.copyOf(bucket, n + 1);
            rv[n] = new Object[] {new WeakReference<Object>(obj), origin};
            added[0] = true;
            return rv;
        });
        if (added[0]) {
            ORIGIN_ADDS.incrementAndGet();
            purgeOrigins();
        }
    }

    //! Returns the origin recorded for the object \a obj, or 0 if none (qore#7081)
    static long getOrigin(Object obj) {
        if (obj == null) {
            return 0L;
        }
        Object[][] bucket = ORIGINS.get(System.identityHashCode(obj));
        if (bucket != null) {
            for (Object[] e : bucket) {
                if (((WeakReference<?>)e[0]).get() == obj) {
                    return (Long)e[1];
                }
            }
        }
        return 0L;
    }

    //! the class loader of the JDK, whose classes are trusted, like those of the system class loader, which is read
    //! when it is needed: this class is initialized while the JVM creates it if it is a QoreURLClassLoader
    private static final ClassLoader PLATFORM_LOADER = ClassLoader.getPlatformClassLoader();

    //! Returns the Program that the Java code of the classes of the class loader \a first is attributed to (qore#7023)
    /** @return 0 for a class loader of the JDK or of the JVM's classpath (trusted); for a QoreURLClassLoader, its
        Program pointer (the first pointer of a class loader whose pointer has been cleared, ex: one whose Program has
        been deleted), or that of its nearest QoreURLClassLoader parent if it was made to have none with
        QoreURLClassLoader.createWithoutProgram(); -1 for any other class loader (unknown), which counts as a sandboxed
        Program

        A class loader that is not a QoreURLClassLoader inherits no Program from its parents: any code can create one
        with any parent (qore#7131).  Only objects of this module's QoreURLClassLoader class count: a class of another
        class loader with the same name (ex: one that Java code defined) is an unknown class loader
    */
    private static long getLoaderProgramPtr(ClassLoader first) {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (first == null || first == PLATFORM_LOADER || isSystemLoaderOrParent(first, system)) {
            return 0L;
        }
        for (ClassLoader cl = first; cl != null; cl = cl.getParent()) {
            // checked first: the system class loader can be a QoreURLClassLoader of a trusted Program, whose trust is
            // not inherited by the class loaders below it
            if (cl == PLATFORM_LOADER || cl == system) {
                break;
            }
            if (!(cl instanceof QoreURLClassLoader)) {
                break;
            }
            QoreURLClassLoader qcl = (QoreURLClassLoader)cl;
            long ptr = qcl.getOriginPtr();
            if (ptr != 0L) {
                return ptr;
            }
            // only a class loader made to have no Program is attributed to its parent's
            if (!qcl.inheritsProgram()) {
                break;
            }
        }
        return -1L;
    }

    //! Returns true if \a loader is the system class loader or one of its parents
    /** If the system class loader is a QoreURLClassLoader, the JVM's classpath is loaded by its parent
    */
    private static boolean isSystemLoaderOrParent(ClassLoader loader, ClassLoader system) {
        for (ClassLoader cl = system; cl != null; cl = cl.getParent()) {
            if (cl == loader) {
                return true;
            }
        }
        return false;
    }

    //! Returns the current stack trace, not including the call to this method
    public static StackTraceElement[] getStackTrace() {
        StackTraceElement[] stack = new Exception().getStackTrace();
        return stack.length > 0 ? Arrays.copyOfRange(stack, 1, stack.length) : null;
    }

    /** Runs a JDBC connection attempt with cooperative Qore cancellation.
     *
     * The virtual thread makes Java socket connects and reads interruptible. The worker is always
     * joined, and a connection returned concurrently with cancellation is closed before returning.
     * Drivers doing native I/O must themselves honor interruption or configure a finite login timeout.
     *
     * The connection type is erased to AutoCloseable because this class is bootstrap-loaded.
     * @param connect connection factory in the caller's class-loader context
     * @return the established connection
     * @throws Throwable the original connection error or Qore cancellation exception
     */
    public static <T extends AutoCloseable> T openJdbcConnection(
            java.util.concurrent.Callable<T> connect) throws Throwable {
        java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
        long waiter = newConnectionWaiter0();
        Thread worker = null;
        boolean returned = false;
        try {
            worker = Thread.ofVirtual().name("qore-jdbc-connect").start(() -> {
                try {
                    result.complete(connect.call());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    completeConnectionWaiter0(waiter);
                }
            });
            awaitConnectionWaiter0(waiter);
            try {
                T connection = result.get();
                returned = true;
                return connection;
            } catch (java.util.concurrent.ExecutionException error) {
                throw error.getCause();
            }
        } finally {
            if (worker != null) {
                if (!returned) {
                    worker.interrupt();
                }
                // Cleanup cannot leave a live worker referencing the native waiter. Preserve any
                // Java interrupt of the calling thread, but finish the join before freeing it.
                boolean interrupted = false;
                for (;;) {
                    try {
                        worker.join();
                        break;
                    } catch (InterruptedException error) {
                        interrupted = true;
                        worker.interrupt();
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (!returned && !result.isCompletedExceptionally()) {
                    T connection = result.getNow(null);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (Throwable ignored) {
                            // Preserve the cancellation or connection failure already being thrown.
                        }
                    }
                }
            }
            deleteConnectionWaiter0(waiter);
        }
    }

    private native static long newConnectionWaiter0();
    private native static void completeConnectionWaiter0(long waiter);
    private native static void awaitConnectionWaiter0(long waiter) throws Throwable;
    private native static void deleteConnectionWaiter0(long waiter);

    private native static long initQore0() throws Throwable;
    private native static void initQoreBootstrap0() throws Throwable;
    private native static Object callFunction0(long pgm_ptr, String name, Object... args) throws Throwable;
    private native static Object callFunctionSave0(long pgm_ptr, String name, Object... args) throws Throwable;
    private native static Object callStaticMethod0(long pgm_ptr, String class_name, String method_name, Object... args) throws Throwable;
    private native static Object callStaticMethodSave0(long pgm_ptr, String class_name, String method_name, Object... args) throws Throwable;
    private native static QoreObject newObjectSave0(long pgm_ptr, String class_name, Object...args) throws Throwable;
    private native static boolean registerJavaThread0();
    private native static void deregisterJavaThread0();
}
