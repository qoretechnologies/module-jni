package org.qore.jni;

import org.qore.jni.QoreURLClassLoader;

import java.util.Arrays;

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
        return callFunction0(QoreURLClassLoader.getProgramPtr(), name, args);
    }

    //! Calls a Qore function and returns the result as a Java Object, takes a variable number of arguments
    public static Object callFunctionArgs(String name, Object[] args) throws Throwable {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.format("cl: %s func: '%s' pgm: %x\n", (cl != null ? cl.toString() : "null"), name,
        //    cl.getProgramPtr());
        return callFunction0(QoreURLClassLoader.getProgramPtr(), name, args);
    }

    //! Calls the given Qore function with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param name the name of the function to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static Object callFunctionSave(String name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callFunctionSave0(QoreURLClassLoader.getProgramPtr(), name, args);
    }

    //! Calls the given Qore function with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data, takes a variable number of arguments
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param name the name of the function to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static Object callFunctionSaveArgs(String name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callFunctionSave0(QoreURLClassLoader.getProgramPtr(), name, args);
    }

    //! Calls the given Qore static method with the given arguments and returns the result
    public static Object callStaticMethod(String class_name, String method_name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callStaticMethod0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
    }

    //! Calls a Qore static method and returns the result as a Java Object, takes a variable number of arguments
    public static Object callStaticMethodArgs(String class_name, String method_name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callStaticMethod0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
    }

    //! Calls the given Qore static method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param class_name the name of the class where the static method is defined; can have a namespace-justified path
     * (ex: \c "Namespace::ClassName")
     * @param method_name the static method name to call
     * @param args argument to the static method call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static Object callStaticMethodSave(String class_name, String method_name, Object... args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callStaticMethodSave0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
    }

    //! Calls the given Qore static method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data, takes a variable number of arguments
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param class_name the name of the class where the static method is defined; can have a namespace-justified path
     * (ex: \c "Namespace::ClassName")
     * @param method_name the static method name to call
     * @param args argument to the static method call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static Object callStaticMethodSaveArgs(String class_name, String method_name, Object[] args) throws Throwable {
        //QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        //System.out.println("cl: " + (cl != null ? cl.toString() : "null"));
        return callStaticMethodSave0(QoreURLClassLoader.getProgramPtr(), class_name, method_name, args);
    }

    //! Creates a new QoreObject from the class name and constructor arguments; a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param class_name the class name or namespace-justified path (ex: \c "Qore::SQL::SQLStatement") of the object to create
     * @param args optional arguments to the constructor
     * @return the object created
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static QoreObject newObjectSave(String class_name, Object... args) throws Throwable {
        return newObjectSave0(QoreURLClassLoader.getProgramPtr(), class_name, args);
    }

    //! Creates a new QoreObject from the class name and constructor arguments; a strong reference to the object is stored in thread-local data under the given key
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_management for more information
     *
     * @param class_name the class name or namespace-justified path (ex: \c "Qore::SQL::SQLStatement") of the object to create
     * @param args optional arguments to the constructor
     * @return the object created
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_management
     */
    public static QoreObject newObjectSaveArgs(String class_name, Object[] args) throws Throwable {
        return newObjectSave0(QoreURLClassLoader.getProgramPtr(), class_name, args);
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

    //! Returns the current stack trace, not including the call to this method
    public static StackTraceElement[] getStackTrace() {
        StackTraceElement[] stack = new Exception().getStackTrace();
        return stack.length > 0 ? Arrays.copyOfRange(stack, 1, stack.length) : null;
    }

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
