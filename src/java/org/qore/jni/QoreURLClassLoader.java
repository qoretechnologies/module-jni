/**
    QoreURLClassLoader.java

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2023 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package org.qore.jni;

import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.nio.file.Files;
import java.nio.file.FileSystems;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.HashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

//! Main ClassLoader for Java <-> %Qore and Java <-> Python integration
/** This ClassLoader supports dynamic imports from %Qore and Java using the following special packages:
    - <b><tt>python.</tt></b><i>[path...]</i>: indicates that the given path should be imported from Python to Java (after being
      imported to %Qore if necessary).
    - <b><tt>pythonmod.</tt></b><i>mod</i><tt>.</tt><i>[path...]</i>: indicates that the given path should be mapped to %Qore
      namespaces and/or classes after loading the Python module <i>mod</i> and importing into %Qore; the Java package
      segments after <tt><b>pythonmod.</b></tt><i>mod</i><tt>.</tt> are then converted to the equivalent %Qore namespace path
    - \c \b qore: indicates that the given path should be mapped to %Qore namespaces and/or classes; the Java package
      segments after <tt><b>qore.</b></tt> are then converted to the equivalent %Qore namespace path
    - <b><tt>qoremod.</tt></b><i>mod</i><tt>.</tt><i>[path...]</i>: indicates that the given path should be mapped to %Qore
      namespaces and/or classes after loading the %Qore module <i>mod</i>; the Java package
      segments after <tt><b>qoremod.</b></tt><i>mod</i><tt>.</tt> are then converted to the equivalent %Qore namespace path
 */
public class QoreURLClassLoader extends URLClassLoader {
    public static String INIT_PROP_NAME = "qore.QoreURLClassLoader.init";

    //! Thread-local storage for the current classloader context
    /** Using InheritableThreadLocal so that child threads inherit the parent's classloader context.
        This is required for Java threads to work properly with Qore's context.
        Call clearContext() to clean up when a thread is done with this classloader to allow
        garbage collection.
    */
    private static InheritableThreadLocal<QoreURLClassLoader> current =
        new InheritableThreadLocal<QoreURLClassLoader>();

    private HashSet<String> classPathElements = new HashSet<String>();
    private String classPath = new String();

    //! Program pointer for the associated Qore Program
    /** Using AtomicLong for thread-safe access to the program pointer.
        A value of 0 indicates no program or that the program has been released.
    */
    private final AtomicLong pgm_ptr = new AtomicLong(0);

    private boolean enable_cache = false;
    // when used as the system class loader
    /** if true, we need to ensure that this object loads classes to make dynamic imports work
    */
    private boolean startup = false;

    //! for caching files during compilation
    /** Using ConcurrentHashMap for thread-safe access */
    private final ConcurrentHashMap<String, QoreJavaFileObject> classes = new ConcurrentHashMap<String, QoreJavaFileObject>();

    //! used to mark java class creation in progress; binary names used
    private final ConcurrentHashMap<String, Boolean> classInProgress = new ConcurrentHashMap<String, Boolean>();

    //! cache of inner classes to resolve circular dependencies when injecting classes
    /** Using ConcurrentHashMap for thread-safe access */
    private final ConcurrentHashMap<String, byte[]> pendingClasses = new ConcurrentHashMap<String, byte[]>();

    //! cache of classes when running as the boot classloader
    /** Using ConcurrentHashMap for thread-safe access */
    private final ConcurrentHashMap<String, Class<?>> classCache = new ConcurrentHashMap<String, Class<?>>();

    // when used to bootstrap Java
    private static boolean static_bootstrap = false;

    //! static initialization
    static {
        // check if we are using this class as the system class loader
        if ("org.qore.jni.QoreURLClassLoader".equals(System.getProperty("java.system.class.loader"))) {
            //System.out.printf("setting static_bootstrap = true\n");
            static_bootstrap = true;
        }

        System.setProperty(INIT_PROP_NAME, "true");
        // loads and initializes the Qore library and the jni module (if necessary)
        try {
            dummy0();
        } catch (UnsatisfiedLinkError e0) {
            try {
                //debugLog("about to call initQore(): " + e0.toString());
                if (static_bootstrap) {
                    QoreJavaApi.initQoreBootstrap();
                } else {
                    QoreJavaApi.initQore();
                }
            } catch (ExceptionInInitializerError e1) {
                throw e1;
            } catch (Throwable e1) {
                throw new ExceptionInInitializerError(e1);
            }
        } catch (Throwable e0) {
            throw new ExceptionInInitializerError(e0);
        } finally {
            System.clearProperty(INIT_PROP_NAME);
        }
    }

    //! constructor for using this class as the boot classloader
    /** For example, starting Java like: java -cp xxx -Djava.system.class.loader=org.qore.jni.QoreURLClassLoader ...
        will result in this constructor being called
     */
    public QoreURLClassLoader(ClassLoader parent) {
        super("QoreURLClassLoader", new URL[]{}, parent);
        enable_cache = true;
        setContextProgram(this);
        //System.out.printf("QoreURLClassLoader(ClassLoader parent: %s) this: %x (pgm: %x)\n",
        //    (parent == null ? "null" : parent.getClass().getCanonicalName()), hashCode(), pgm_ptr);

        // startup / system classloader
        startup = true;

        // set classpath from system classpath
        String cp = System.getProperty("java.class.path");
        if (cp != null && !cp.isEmpty()) {
            addPath(cp);
        }
    }

    //! constructor for using this class as the boot classloader for the module
    public QoreURLClassLoader() {
        super("QoreURLClassLoader", new URL[]{}, ClassLoader.getSystemClassLoader());
        setContext();
        enable_cache = true;
        setContextProgram(this);
        //System.out.printf("QoreURLClassLoader() this: %x (pgm: %x)\n", hashCode(), pgm_ptr);
    }

    //! constructor for using this class as the boot classloader for the module
    public QoreURLClassLoader(long p_ptr) {
        //super("QoreURLClassLoader", new URL[]{}, ClassLoader.getSystemClassLoader());
        super("QoreURLClassLoader", new URL[]{}, ClassLoader.getPlatformClassLoader());
        setContext();
        pgm_ptr.set(p_ptr);
        //System.out.printf("QoreURLClassLoader(long p_ptr: %x) this: %x\n", p_ptr, hashCode());
    }

    //! constructor with a QoreProgram pointer and a parent
    public QoreURLClassLoader(long p_ptr, ClassLoader parent) {
        super("QoreURLClassLoader", new URL[]{}, parent);
        // set the current classloader as the thread context classloader
        pgm_ptr.set(p_ptr);
        setContext();
        //System.out.printf("QoreURLClassLoader(long p_ptr: %x, ClassLoader parent: %s: %x) this: %x\n",
        //    p_ptr, (parent == null ? "null" : parent.getClass().getCanonicalName()),
        //    parent == null ? 0 : parent.hashCode(), hashCode());
    }

    //! constructor with a name and a parent
    public QoreURLClassLoader(String name, ClassLoader parent) {
        super(name, new URL[]{}, parent);
        setContext();
        enable_cache = true;
        setContextProgram(this);
        //System.out.printf("QoreURLClassLoader(name: '%s', ClassLoader parent: %s) this: %x (pgm: %x)\n",
        //    name, (parent == null ? "null" : parent.getClass().getCanonicalName()) + ")", hashCode(), pgm_ptr);
    }

    /**
     * Add a class name/JavaFileObject mapping
     *
     * @param qualifiedClassName the name
     * @param javaFile           the file associated with the name
     */
    public void add(final String qualifiedClassName, final QoreJavaFileObject javaFile) {
        classes.put(qualifiedClassName, javaFile);
    }

    /**
     * @return An collection of QoreJavaFileObject instances for the classes in the
     * class loader.
     */
    public Collection<QoreJavaFileObject> files() {
        return Collections.unmodifiableCollection(classes.values());
    }

    @Override
    public InputStream getResourceAsStream(final String name) {
        if (name.endsWith(".class")) {
            String qualifiedClassName = name.substring(0,
                    name.length() - ".class".length()).replace('/', '.');
            QoreJavaFileObject file = classes.get(qualifiedClassName);
            if (file != null) {
                return new ByteArrayInputStream(file.getByteCode());
            }
        }
        return super.getResourceAsStream(name);
    }

    public void addPathOrig(String path) throws Exception {
        //debugLog("QoreURLClassLoader.addPath(): file://" + path);
        super.addURL(new URL("file", null, 0, path));
    }

    //! adds byte code for an inner class to the byte code cache; requires a binary name (ex: \c my.package.MyClass$1)
    public void addPendingClass(String bin_name, byte[] byte_code) {
        if (byte_code == null) {
            throw new RuntimeException("QoreURLClassLoader.addPendingClass() called with null byte_code");
        }
        pendingClasses.put(bin_name, byte_code);
        //System.out.printf("addPendingClass() this: %x '%s' len: %d hm size: %d\n", hashCode(), bin_name,
        //  byte_code.length, pendingClasses.size());
    }

    public Class<?> getResolveClass(String name) throws ClassNotFoundException {
        Class<?> rv = tryGetPendingClass(name);
        if (rv == null) {
            rv = super.findClass(name);
        }
        resolveClass(rv);
        //System.out.printf("returning resolved %s\n", name);
        return rv;
    }

    //! Clears the pending classes cache
    /** This cache stores byte code for classes that are being injected.
        @note This method only clears the pendingClasses cache; use clearAllCaches()
        to clear all caches.
    */
    public void clearPendingClassesCache() {
        pendingClasses.clear();
    }

    //! Clears the pending classes cache (deprecated)
    /** @deprecated Use clearPendingClassesCache() instead for clarity
    */
    @Deprecated
    public void clearCache() {
        clearPendingClassesCache();
    }

    //! Clears all caches in this classloader
    /** This method clears:
        - pendingClasses: byte code for classes being injected
        - classCache: cached Class objects
        - classes: QoreJavaFileObject cache for compilation
        - classInProgress: classes currently being created
    */
    public void clearAllCaches() {
        pendingClasses.clear();
        classCache.clear();
        classes.clear();
        classInProgress.clear();
    }

    public byte[] removePendingByteCode(String bin_name) {
        byte[] rv = pendingClasses.get(bin_name);
        return rv;
    }

    //! Returns a copy of all pending class names and their bytecode
    /** This method returns all classes currently in the pending classes cache.
        Used by QoreKotlinCompiler to retrieve all dynamically generated classes
        including parent classes in the hierarchy.
        @return A HashMap containing class binary names mapped to their bytecode
    */
    public HashMap<String, byte[]> getAllPendingClasses() {
        return new HashMap<>(pendingClasses);
    }

    public ArrayList<String> getPendingClassesForPackage(String packageName) {
        ArrayList<String> rv = new ArrayList<String>();
        pendingClasses.forEach((k, v) -> {
            // see if binary name matches the package name
            int dot = k.indexOf(".");
            if (dot == -1 && packageName.isEmpty()
                || (dot > 0 && k.startsWith(packageName + ".") && k.indexOf(".", packageName.length() + 1) == -1)) {
                rv.add(k);
            }
        });
        //System.out.printf("getPendingClassesForPackage(%s) this: %x rv: %s (cache: %s)\n", packageName, hashCode(),
        //  rv, pendingClasses);
        return rv;
    }

    //! for resolving circular dependencies when defining inner classes
    private Class<?> tryGetPendingClass(String name) {
        byte[] byte_code = pendingClasses.remove(name);

        if (byte_code == null) {
            if (enable_cache) {
                Class<?> rv = classCache.remove(name);
                if (rv == null) {
                    ClassLoader parent = getParent();
                    if (parent != null && (parent instanceof QoreURLClassLoader)) {
                        rv = ((QoreURLClassLoader)parent).tryGetPendingClass(name);
                    }
                }
                return rv;
            }

            return null;
        }
        // create the class and return it
        return defineClassIntern(name, byte_code, 0, byte_code.length);
    }

    //! Supports generating classes from byte code as well as returning classes built in to the jni module
    protected Class<?> findClass(String bin_name) throws ClassNotFoundException {
        //System.out.printf("findClass() this: %x %s\n", hashCode(), bin_name);
        //try {
        Class<?> rv;

        if (bin_name.startsWith("net.bytebuddy.")) {
            byte[] byte_code = getCachedClass0(bin_name);
            //System.out.printf("findClass() %s: %s\n", bin_name, byte_code);
            if (byte_code != null) {
                //System.out.printf("findClass() %s returning cached\n", bin_name);
                return defineClassIntern(bin_name, byte_code, 0, byte_code.length);
            }
        } else if (bin_name.startsWith("org.qore.")) {
            byte[] byte_code = getInternalClass0(bin_name);
            //System.out.printf("findClass() %s: %s\n", bin_name, byte_code);
            if (byte_code != null) {
                //System.out.printf("findClass() %s returning internal\n", bin_name);
                return defineClassIntern(bin_name, byte_code, 0, byte_code.length);
            }
        }

        //System.out.printf("findClass() %s calling super\n", bin_name);
        //return super.findClass(bin_name);
        rv = super.findClass(bin_name);
        //System.out.printf("findClass() %x %s: returning super: %s\n", hashCode(), bin_name, rv);
        return rv;
        //} catch (ClassNotFoundException e) {
            //e.printStackTrace();
            //throw e;
        //}
    }

    public byte[] getInternalClass(String bin_name) throws ClassNotFoundException {
        byte[] rv = getInternalClass0(bin_name);
        if (rv != null) {
            return rv;
        }
        throw new ClassNotFoundException(String.format("unknown internal class '%s'", bin_name));
    }

    //! Returns a list of internal classes in the given package
    /** @param packageName the package name to search
        @return a list of class names in the package
        @note This method requires a valid program pointer.
    */
    public ArrayList<String> getInternalClassesForPackage(String packageName) {
        ArrayList<String> rv = new ArrayList<String>();
        long ptr = pgm_ptr.get();
        if (ptr != 0) {
            getInternalClassesForPackage0(ptr, packageName, rv);
        }
        //System.out.printf("getInternalClassesForPackage(%s) rv: '%s'\n", packageName, rv);
        return rv;
    }

    //! Check if a class is currently being created
    /** Thread-safe using ConcurrentHashMap
        @param bin_name the binary name of the class
        @return true if the class is currently being created
    */
    public boolean checkInProgress(String bin_name) {
        return classInProgress.containsKey(bin_name);
    }

    //! Mark a class as being created
    /** Thread-safe using ConcurrentHashMap.putIfAbsent()
        @param bin_name the binary name of the class
        @return true if the class was already being created, false if it was marked
    */
    private boolean markInProgress(String bin_name) {
        Boolean previous = classInProgress.putIfAbsent(bin_name, Boolean.TRUE);
        //debugLog("marked in progress " + bin_name);
        return previous != null;
    }

    //! Remove a class from the in-progress set
    /** Thread-safe using ConcurrentHashMap
        @param bin_name the binary name of the class
    */
    private void removeInProgress(String bin_name) {
        classInProgress.remove(bin_name);
        //debugLog("removed in progress " + bin_name);
    }

    public Class<?> loadResolveClass(String name) throws ClassNotFoundException {
        return loadClass(name);
    }

    /** Check if the class is loaded in this class loader or in the parent if it's a QoreURLClassLoader
     *
     * @param bin_name the class to check
     * @return the class if it's loaded
     */
    public Class<?> checkLoadedClass(String bin_name) {
        Class<?> rv = findLoadedClass(bin_name);
        if (rv != null) {
            return rv;
        }
        ClassLoader parent = getParent();
        if (parent instanceof QoreURLClassLoader) {
            return ((QoreURLClassLoader)parent).checkLoadedClass(bin_name);
        }
        return null;
    }

    // NOTE: loadClass(String, boolean) performs synchronization
    /**
     * Loads classes; returns pending classes injected by the jni module or the compiler
     */
    public Class<?> loadClass(String bin_name) throws ClassNotFoundException {
        //System.out.printf("QoreURLClassLoader.loadClass() this: %x '%s' pgm: %x (startup: %s)\n",
        //    hashCode(), bin_name, pgm_ptr, startup);
        Class<?> rv = checkLoadedClass(bin_name);
        if (rv != null) {
            //System.out.printf("loadClass() %s returning loaded\n", bin_name);
            return rv;
        }

        // Canonical routing for shared dynamic classes MUST come before tryGetPendingClass:
        // pendingClasses may contain bytecode cached during a previous canonical routing
        // (for Kotlin stub generation), and tryGetPendingClass would defineClass on THIS
        // loader, creating a duplicate Class object that causes LinkageError.
        if (isDynamic(bin_name) && isSharedDynamicClassName(bin_name)) {
            QoreURLClassLoader target = resolveSharedClassLoader(bin_name);
            if (target != null && target != this) {
                Class<?> shared = target.checkLoadedClass(bin_name);
                if (shared != null) {
                    return shared;
                }
                try {
                    byte[] bytes = target.generateByteCode(bin_name);
                    // Also store in our own pendingClasses so callers like
                    // QoreKotlinCompiler.generateDynamicStubs() that call
                    // getAllPendingClasses() on this loader can retrieve the bytecode
                    pendingClasses.put(bin_name, bytes);
                    synchronized (target.getClassLoadingLock(bin_name)) {
                        shared = target.checkLoadedClass(bin_name);
                        if (shared != null) {
                            return shared;
                        }
                        return target.defineClassUnconditional(bin_name, bytes);
                    }
                } catch (ClassNotFoundException e) {
                    // canonical loader can't make this class; fall through
                } catch (LinkageError le) {
                    shared = target.checkLoadedClass(bin_name);
                    if (shared != null) {
                        return shared;
                    }
                    throw le;
                }
            }
        }

        rv = tryGetPendingClass(bin_name);
        if (rv != null) {
            //System.out.printf("loadClass() %s returning pending\n", bin_name);
            return rv;
        }
        QoreJavaFileObject file = classes.get(bin_name);
        if (file != null) {
            byte[] bytes = file.getByteCode();
            return defineClass(bin_name, bytes, 0, bytes.length);
        }

        if (bin_name.startsWith("java.")
            || bin_name.startsWith("javax.")
            || bin_name.startsWith("sun.")
            || bin_name.startsWith("jdk.")
            || bin_name.startsWith("org.qore.jni.")
            || bin_name.startsWith("org.qore.lang.")) {
            //return super.loadClass(bin_name);
            rv = super.loadClass(bin_name);
            //System.out.printf("loadClass() %s resolved %s with super: %s\n", bin_name, rv, rv.getClass().getClassLoader());
            return rv;
        }

        if (isDynamic(bin_name)) {
            // only remove from set if successful
            try {
                byte[] bytes = generateByteCode(bin_name);

                // have to check if the class was loaded in the meantime
                rv = checkLoadedClass(bin_name);
                if (rv != null) {
                    //System.out.printf("loadClass() %s returning loaded after bytecode generation\n", bin_name);
                    return rv;
                }

                rv = defineClassIntern(bin_name, bytes, 0, bytes.length);
                //System.out.printf("loadClass() this: %x pgm: %x dyn %s returning generated %s\n", hashCode(),
                //    pgm_ptr, bin_name, rv);
                return rv;
            } catch (ClassNotFoundException e1) {
                //e1.printStackTrace();
                // block left empty on purpose
            } catch (RuntimeException e1) {
                //e1.printStackTrace();
                throw e1;
            } catch (Throwable e1) {
                //e1.printStackTrace();
                throw new RuntimeException(e1);
            }
        }

        if (!startup) {
            ClassLoader parent = getParent();
            if (parent == null) {
                parent = getSystemClassLoader();
            }
            if (parent != null) {
                try {
                    //return parent.loadClass(bin_name);
                    rv = parent.loadClass(bin_name);
                    //System.out.printf("loadClass() %s returning parent: %s\n", bin_name, rv);
                    return rv;
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }
        } else {
            try {
                // we must load classes first when we are a "startup" class loader, so that referenced dynamic
                // classes will be loadable
                rv = super.findClass(bin_name);

                if (rv != null) {
                    //System.out.printf("loadClass() %s returning super.findClass()\n", bin_name);
                    return rv;
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }

        //System.out.printf("QoreURLClassLoader.loadClass() %s call super... (classes: %s)\n", bin_name,
        //    classes.keySet());
        return super.loadClass(bin_name);
    }

    //! Returns the Java class from the given Qore class with a Qore class ptr
    /**
     */
    public Class<?> loadClassWithPtr(String bin_name, long class_ptr) throws ClassNotFoundException {
        //debugLog(String.format("loadClassWithPtr() %s: %x", bin_name, class_ptr));
        Class<?> rv;
        synchronized(getClassLoadingLock(bin_name)) {
            rv = checkLoadedClass(bin_name);
            if (rv != null) {
                //System.out.printf("loadClassWithPtr() %s returning loaded\n", bin_name);
                return rv;
            }

            // Canonical routing MUST come before tryGetPendingClass — see loadClass() above.
            if (isSharedDynamicClassName(bin_name)) {
                QoreURLClassLoader target = resolveSharedClassLoader(bin_name);
                if (target != null && target != this) {
                    Class<?> shared = target.checkLoadedClass(bin_name);
                    if (shared != null) {
                        return shared;
                    }
                    try {
                        byte[] bytes = target.generateByteCode(bin_name, class_ptr);
                        // Also store in our own pendingClasses for Kotlin stub generation
                        pendingClasses.put(bin_name, bytes);
                        synchronized (target.getClassLoadingLock(bin_name)) {
                            shared = target.checkLoadedClass(bin_name);
                            if (shared != null) {
                                return shared;
                            }
                            return target.defineClassUnconditional(bin_name, bytes);
                        }
                    } catch (ClassNotFoundException e) {
                        // canonical loader can't make this class; fall through
                    } catch (LinkageError le) {
                        shared = target.checkLoadedClass(bin_name);
                        if (shared != null) {
                            return shared;
                        }
                        throw le;
                    }
                }
            }

            rv = tryGetPendingClass(bin_name);
            if (rv != null) {
                //System.out.printf("loadClassWithPtr() %s returning pending\n", bin_name);
                return rv;
            }
            QoreJavaFileObject file = classes.get(bin_name);
            if (file != null) {
                byte[] bytes = file.getByteCode();

                //System.out.printf("QoreURLClassLoader.loadClassWithPtr() %x: %s returning defineClass()\n",
                //    hashCode(), bin_name);

                return defineClass(bin_name, bytes, 0, bytes.length);
            }

            // only remove from set if successful
            try {
                //System.out.printf("loadClassWithPtr() this: %x %s about to call generateByteCode(%s, %x)\n",
                //    hashCode(), bin_name, bin_name, class_ptr);
                byte[] bytes = generateByteCode(bin_name, class_ptr);

                // have to check if the class was loaded in the meantime
                rv = checkLoadedClass(bin_name);
                if (rv != null) {
                    //System.out.printf("loadClassWithPtr() %s returning loaded after bytecode generation\n", bin_name);
                    return rv;
                }

                rv = defineClassIntern(bin_name, bytes, 0, bytes.length);
                //System.out.printf("loadClassWithPtr() this: %x pgm: %x dyn %s returning generated %s\n", hashCode(),
                //    pgm_ptr, bin_name, rv);
            } catch (RuntimeException e1) {
                //e1.printStackTrace();
                throw e1;
            } catch (Throwable e1) {
                //e1.printStackTrace();
                throw new RuntimeException(e1);
            }
        }
        return rv;
    }

    //! Returns true if {@code bin_name} refers to a class that should be canonicalized
    //! through the syscl loader (so all loaders that need the same shared type see the same
    //! Class object).
    /** Routing is required for two distinct shapes:
        - <b><tt>qoremod.</tt></b><i>mod</i><tt>.</tt><i>...</i>: classes provided by a Qore
          module.  The QoreClass is shared across Programs that load the module, so a single
          canonical Java Class avoids loader-constraint violations on subclass override
          resolution and ClassCastException on legitimate casts across Program boundaries.
        - <b><tt>qore.Qore.</tt></b><i>...</i>: built-in <tt>Qore::*</tt> classes that have
          a fixed identity across Programs.  Routing them through syscl is what lets, e.g.,
          a workflow program's {@code JavaSimpleTestStep1} call into a syscl-defined
          {@code qoremod.SqlUtil.Table.&lt;init&gt;(qore.Qore.SQL.AbstractDatasource, String)}
          without the JVM rejecting the call site with "loader constraint violation".

        We deliberately do NOT route bare <tt>qore.</tt><i>X</i><tt>.</tt><i>Y</i> when X is
        not <tt>Qore</tt> (e.g. <tt>qore.OMQ.$Constants</tt>): such names address per-Program
        runtime namespaces (Qorus' OMQ namespace, code-generation tests' custom classes etc.)
        whose contents legitimately differ between Programs, and routing them up to syscl
        would resolve to syscl's stripped-down copy and cause NoSuchFieldError /
        ClassCastException at the use site.

        We also do not route <tt>qore.</tt><i>X</i> single-segment names (no second dot) — they
        also address per-Program top-level user classes.
    */
    static private boolean isSharedDynamicClassName(String bin_name) {
        if (bin_name.startsWith("qoremod.") && bin_name.length() > 8) {
            return true;
        }
        // qore.<X>.<Y>... can be a shadow / runtime-aliased class from a binary module
        // (e.g. qore.Qore.Logger.LoggerInterface from logger_bin) or a Qorus injection
        // (qore.OMQ.UserApi.*); the owning loader is determined by QoreClass lookup at
        // resolveSharedClassLoader() time, not by name shape.
        if (bin_name.startsWith("qore.") && bin_name.length() > 5
                && bin_name.indexOf('.', 5) > 0) {
            return true;
        }
        return false;
    }

    //! Resolves the canonical loader for a shared dynamic class name.
    /** Routes to the loader of the QoreProgram that owns the underlying QoreClass.  For
        {@code qoremod.<mod>.<X>} this is the module's user-Program; for legacy / shadow
        {@code qore.<X>.<Y>...} forms it is whichever Program owns the QoreClass at qpath
        {@code ::<X>::<Y>::...} as visible from the calling loader's Program (typically
        the host Program that loaded the binary module providing the class).

        Returns {@code null} when no canonical owner is known; callers should fall back
        to local generation in that case.
     */
    private QoreURLClassLoader resolveSharedClassLoader(String bin_name) {
        return getCanonicalLoader0(bin_name);
    }

    //! Returns true if the given package name is dynamic
    static public boolean isDynamic(String bin_name) {
        return bin_name.equals("qore") || bin_name.equals("python") || bin_name.equals("kotlin")
            || (bin_name.startsWith("qore.") && bin_name.length() > 5)
            || (bin_name.startsWith("qoremod.") && bin_name.length() > 8)
            || (bin_name.startsWith("python.") && bin_name.length() > 7)
            || (bin_name.startsWith("pythonmod.") && bin_name.length() > 10)
            || (bin_name.startsWith("kotlin.") && bin_name.length() > 7)
            || (bin_name.startsWith("kotlinmod.") && bin_name.length() > 10);
    }

    protected Class<?> defineClassIntern(String name, byte[] byte_code, int off, int len) throws ClassFormatError {
        Class<?> rv = defineClass(name, byte_code, off, len);

        if (enable_cache) {
            classCache.put(name, rv);
            //debugLog("QoreURLClassLoader.defineClassIntern() caching " + name);
        } else {
            //debugLog("QoreURLClassLoader.defineClassIntern() not caching " + name);
        }
        return rv;
    }

    public Class<?> defineClassUnconditional(String name, byte[] byte_code) throws ClassFormatError {
        // create the class and return it
        return defineClassIntern(name, byte_code, 0, byte_code.length);
    }

    public Class<?> defineResolveClass(String name, byte[] b, int off, int len) throws ClassFormatError {
        Class<?> rv = defineClassIntern(name, b, off, len);
        resolveClass(rv);
        return rv;
    }

    //! Returns the program pointer
    /** @return the program pointer value, or 0 if not set or cleared
        @note The returned value may be 0 if the program has been released; use getValidPtr() if you need
        to ensure the pointer is valid
    */
    public long getPtr() {
        return pgm_ptr.get();
    }

    //! Returns the program pointer, throwing an exception if it's invalid
    /** @return the program pointer value
        @throws IllegalStateException if the program pointer is 0 (not set or cleared)
    */
    public long getValidPtr() {
        long ptr = pgm_ptr.get();
        if (ptr == 0) {
            throw new IllegalStateException("Program pointer is not valid; the associated Qore Program " +
                "may have been released");
        }
        return ptr;
    }

    //! Clears the program pointer
    /** After calling this method, the classloader should not be used for operations
        that require a valid Qore Program.
    */
    public void clearProgramPtr() {
        pgm_ptr.set(0);
    }

    //! Returns the program pointer from the current thread's classloader context
    /** @return the program pointer value
        @throws IllegalStateException if no classloader context is set for this thread
    */
    public static long getProgramPtr() {
        QoreURLClassLoader cl = current.get();
        if (cl == null) {
            throw new IllegalStateException("No classloader context set for current thread");
        }
        return cl.pgm_ptr.get();
    }

    //! Sets the program pointer for the current thread's classloader context
    /** @param ptr the program pointer value to set
        @throws IllegalStateException if no classloader context is set for this thread
    */
    public static void setProgramPtr(long ptr) {
        QoreURLClassLoader cl = current.get();
        if (cl == null) {
            throw new IllegalStateException("No classloader context set for current thread");
        }
        cl.pgm_ptr.set(ptr);
    }

    //! Returns the current thread's classloader context
    /** @return the current classloader, or null if not set
    */
    public static QoreURLClassLoader getCurrent() {
        return current.get();
    }

    //! Sets this classloader as the current thread's context classloader
    /** This method sets both the thread's context classloader and the thread-local reference
        used by getCurrent() and other static methods.
        @note Call clearContext() when done to allow garbage collection of the classloader.
    */
    public void setContext() {
        Thread.currentThread().setContextClassLoader(this);
        current.set(this);
    }

    //! Clears the current thread's classloader context
    /** This method should be called when a thread is done using this classloader
        to allow garbage collection. This clears both the thread's context classloader
        and the thread-local reference.
    */
    public void clearContext() {
        if (current.get() == this) {
            current.remove();
        }
        if (Thread.currentThread().getContextClassLoader() == this) {
            Thread.currentThread().setContextClassLoader(getParent());
        }
    }

    //! Static method to clear the current thread's classloader context
    /** This method should be called when a thread is done using the current classloader
        to allow garbage collection.
    */
    public static void clearCurrentContext() {
        QoreURLClassLoader cl = current.get();
        if (cl != null) {
            cl.clearContext();
        }
    }

    //! Adds a path to the classpath
    public void addParentPath(String classpath) {
        ClassLoader parent = getParent();
        if (parent == null) {
            throw new RuntimeException("No parent ClassLoader present");
        }
        if (!(parent instanceof QoreURLClassLoader)) {
            throw new RuntimeException(String.format("Parent ClassLoader is not a QoreURLClassLoader (%s)",
                parent.getClass().getCanonicalName()));
        }
        ((QoreURLClassLoader)parent).addPath(classpath);
    }

    //! Adds a path to the classpath
    public void addPath(String classpath) {
        //debugLog(String.format("%x: addPath(): %s", hashCode(), classpath));

        String seps = File.pathSeparator; // separators

        // want to accept both system separator and ';'
        if (!File.pathSeparator.equals(";")) {
            seps += ";";
        }
        for (StringTokenizer st = new StringTokenizer(classpath, seps, false); st.hasMoreTokens();) {
            String pathentry = st.nextToken();
            //debugLog("addPath(): " + pathentry);
            String basename = null;

            if (pathentry.length() == 0) {
                continue;
            }

            File fileentry = new File(pathentry);
            if (fileentry.getName().indexOf('*') != -1) {
                basename = fileentry.getName();
                fileentry = fileentry.getParentFile();
                if (fileentry.getName().indexOf('*') != -1) {
                    errorLog("Ignoring wildcard in classpath directory element '" + pathentry + "'");
                    continue;
                }
            }

            if (!fileentry.isAbsolute() && pathentry.charAt(0) != '/' && pathentry.charAt(0) != '\\') {
                fileentry = new File(fileentry.getPath());
            }
            try {
                fileentry = fileentry.getCanonicalFile();
            } catch (IOException thr) {
                errorLog("Ignoring non-existent classpath element '" + fileentry + "' (" + thr + ").");
                continue;
            }
            if (basename != null && !basename.isEmpty()) {
                fileentry = new File(fileentry, basename);
            }
            if (classPathElements.contains(fileentry.getPath())) {
                //errorLog("Skipping duplicate classpath element '" + fileentry + "'.");
                continue;
            } else {
                classPathElements.add(fileentry.getPath());
            }

            if (basename != null && !basename.isEmpty()) {
                //debugLog("addWildcard() parent: " + fileentry.getParentFile().getPath() + " basename: " + basename);
                addWildcard(fileentry.getParentFile(), basename);
            } else if (!fileentry.exists()) { // s/never be due getCanonicalFile() above
                //errorLog("Could not find classpath element '" + fileentry + "'");
            } else if (fileentry.isDirectory()) {
                //debugLog("adding dir: " + fileentry.getName() + " (" + fileentry.toString() + ")");
                addURL(createUrl(fileentry));
            } else if (isLoadable(fileentry.getName())) {
                //debugLog("adding jar: " + fileentry.getName() + " (" + fileentry.toString() + ")");
                addURL(createUrl(fileentry));
            } else {
                errorLog("ClassPath element '" + fileentry + "' is not an existing directory and is not a file " +
                    "ending with '.zip' or '.jar'");
            }
        }
        //infoLog("Class loader is using classpath: \"" + classPath + "\".");
    }

    //! Returns a list of classes in the given dynamic package
    /** @param packageName the package name to search
        @return a list of class names in the package
        @note This method requires a valid program pointer; if the pointer is invalid,
        delegation to the parent classloader is attempted.
    */
    public ArrayList<String> getClassesInNamespace(String packageName) {
        ArrayList<String> rv = new ArrayList<String>();
        ClassModInfo info = new ClassModInfo(packageName, true);
        long ptr = pgm_ptr.get();
        if (ptr != 0) {
            getClassesInNamespace0(ptr, info.cls, info.mod, info.python, info.kotlin, rv);
        }
        //System.out.printf("getClassesInNamespace(%s) pgm: %x cls: '%s' mod: '%s' rv: %s\n", packageName, ptr,
        //    info.cls, info.mod, rv);

        if (rv.size() == 0) {
            ClassLoader parent = getParent();
            //System.out.printf("getClassesInNamespace() %s; parent is %s\n", packageName,
            //    parent == null ? "null" : parent.getClass().getName());
            if (parent instanceof QoreURLClassLoader) {
                return ((QoreURLClassLoader)parent).getClassesInNamespace(packageName);
            }
        }

        return rv;
    }

    public static ArrayList<File> splitClassPath(String classpath) {
        //debugLog("addPath: " + classpath);
        String seps = File.pathSeparator; // separators

        ArrayList<File> rv = new ArrayList<File>();

        // want to accept both system separator and ';'
        if (!File.pathSeparator.equals(";")) {
            seps += ";";
        }
        for (StringTokenizer st = new StringTokenizer(classpath, seps, false); st.hasMoreTokens();) {
            String pathentry = st.nextToken();
            String basename = null;

            if (pathentry.length() == 0) {
                continue;
            }

            File fileentry = new File(pathentry);
            if (fileentry.getName().indexOf('*') != -1) {
                basename = fileentry.getName();
                fileentry = fileentry.getParentFile();
                if (fileentry.getName().indexOf('*') != -1) {
                    errorLog("Ignoring wildcard in classpath directory element '" + pathentry + "'");
                    continue;
                }
            }

            if (!fileentry.isAbsolute() && pathentry.charAt(0) != '/' && pathentry.charAt(0) != '\\') {
                fileentry = new File(fileentry.getPath());
            }
            try {
                fileentry = fileentry.getCanonicalFile();
            } catch (IOException thr) {
                errorLog("Ignoring non-existent classpath element '" + fileentry + "' (" + thr + ").");
                continue;
            }
            if (basename != null && !basename.isEmpty()) {
                fileentry = new File(fileentry, basename);
            }
            rv.add(fileentry);
        }
        return rv;
    }

    static boolean isLoadable(String name) {
        name = name.toLowerCase();
        return (name.endsWith(".zip") || name.endsWith(".jar")) ? true : false;
    }

    static private void infoLog(String msg) {
        System.out.println("QoreURLClassLoader INFO: " + msg);
    }

    static private void debugLog(String msg) {
        System.out.println("QoreURLClassLoader DEBUG: " + msg);
    }

    static private void errorLog(String msg) {
        System.out.println("QoreURLClassLoader ERROR: " + msg);
    }

    /**
     * Adds a set of files using a generic base name to this loader's classpath.  See @link:addClassPath(String) for
     * details of the generic base name.
     */
    public void addWildcard(File dir, String nam) {
        if (!dir.exists()) {
            errorLog("Cannot find directory for classpath element '" + dir + File.separator + nam + "'");
            return;
        }

        if (!dir.canRead()) {
            errorLog("Cannot read directory for classpath element '" + dir + File.separator + nam + "'");
            return;
        }

        // make a regex pattern from the glob pattern
        final String pattern = nam.replace(new StringBuffer("*"), new StringBuffer(".*"));

        //debugLog("dir: " + dir + " pattern: " + pattern);

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("^" + pattern + "$");
            }
        });

        if (files == null) {
            errorLog("Error accessing directory for classpath element '" + dir + File.separator + nam + "'");
            return;
        }

        if (files.length == 0) {
            debugLog("no matching files for classpath element '" + dir + File.separator + nam + "'");
            return;
        }

        for (File f : files) {
            if (isLoadable(f.getName())) {
                //debugLog("adding file: " + f.getName());
                addURL(createUrl(f));
            }
        }
    }

    //! Clears the compilation cache after compiling
    /** This method clears the classCache and calls the native clearCompilationCache0
        to clear native-side caches.
        @note This method requires a valid program pointer.
    */
    public void clearCompilationCache() {
        classCache.clear();
        classes.clear();
        long ptr = pgm_ptr.get();
        if (ptr != 0) {
            clearCompilationCache0(ptr);
        }
    }

    public synchronized byte[] generateByteCode(String bin_name) throws ClassNotFoundException {
        return generateByteCode(bin_name, 0);
    }

    public synchronized byte[] generateByteCode(String bin_name, long class_ptr) throws ClassNotFoundException {
        byte[] rv = pendingClasses.get(bin_name);
        //System.out.printf("QoreURLClassLoader.generateByteCode() this: %x class: '%s' ptr: %x (pend: %s)\n",
        //    hashCode(), bin_name, class_ptr, rv == null ? "false" : "true");
        if (rv == null) {
            if (markInProgress(bin_name)) {
                throw new ClassNotFoundException(String.format("%s is already being created", bin_name));
            }
            try {
                //System.out.printf("QoreURLClassLoader.generateByteCode() this: %x class: '%s' ptr: %x about to call " +
                //    "generateByteCodeIntern()\n", hashCode(), bin_name, class_ptr);
                rv = generateByteCodeIntern(bin_name, class_ptr);
                //System.out.printf("QoreURLClassLoader.generateByteCode() this: %x class: '%s' ptr: %x rv: %s\n",
                //    hashCode(), bin_name, class_ptr, rv);
            } finally {
                removeInProgress(bin_name);
            }
        }
        //System.out.printf("generateByteCode() this: %x '%s': %s\n", hashCode(), bin_name, rv);
        return rv;
    }

    private byte[] generateByteCodeIntern(String bin_name, long class_ptr) throws ClassNotFoundException {
        ClassModInfo info = new ClassModInfo(bin_name);
        if (info.cls == null) {
            throw new ClassNotFoundException(String.format("invalid dynamic import path '%s'", bin_name));
        }

        byte[] rv = null;
        long ptr = pgm_ptr.get();
        if (ptr != 0) {
            try {
                //System.out.printf("QoreURLClassLoader.generateByteCodeIntern() this: %x pgm: %x '%s' cptr: %x\n",
                //    hashCode(), ptr, bin_name, class_ptr);
                rv = generateByteCode0(ptr, info.cls, bin_name, info.mod, info.python, info.kotlin, class_ptr);
                //System.out.printf("QoreURLClassLoader.generateByteCodeIntern() this: %x pgm: %x '%s' cptr: %x " +
                //    "rv: %s\n", hashCode(), ptr, bin_name, class_ptr, rv);
            } catch (ClassNotFoundException e) {
                throw e;
            } catch (RuntimeException e) {
                //e.printStackTrace();
                throw e;
            } catch (Throwable e) {
                //e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        if (rv == null) {
            throw new ClassNotFoundException(String.format("could not find a Qore source class matching '%s' to " +
                "create Java class '%s'; program pointer: %s", info.cls, bin_name,
                ptr == 0 ? "0 (not set or cleared)" : "valid"));
        }
        // only put in the cache if the byte code is present
        pendingClasses.put(bin_name, rv);
        //System.out.printf("QoreURLClassLoader.generateByteCodeIntern() this: %x created/cached %s: %s\n",
        //    hashCode(), bin_name, rv.toString());
        return rv;
    }

    private URL createUrl(File fileentry) {
        try {
            URL url = fileentry.toURI().toURL();
            String path = url.getPath();
            if (url.getPath().endsWith(".jar")) {
                url = new URL("jar:file:" + path + "!/");
            }
            //infoLog("Added URL: '" + url.toString() + "'");
            if (classPath.length() > 0) {
                classPath += File.pathSeparator;
            }
            classPath += fileentry.getPath();
            return url;
        } catch (MalformedURLException thr) {
            errorLog("classpath element '" + fileentry + "' could not be used to create a valid file system URL (" +
                thr + ")");
            return null;
        }
    }

    private void setContextProgram(QoreURLClassLoader new_syscl) {
        BooleanWrapper created = new BooleanWrapper();
        long ptr = getContextProgram0(this, created, static_bootstrap);
        pgm_ptr.set(ptr);

        if (created.val) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    shutdownContext0();
                }
            });
        }
    }

    static private native void finalizeModuleInit0();
    static private native byte[] getCachedClass0(String bin_name);
    static private native byte[] getInternalClass0(String name);
    private native byte[] generateByteCode0(long ptr, String qname, String name, String qore_module, boolean python,
        boolean kotlin, long class_ptr) throws Throwable;
    static private native void getClassesInNamespace0(long ptr, String packageName, String mod, boolean python,
        boolean kotlin, ArrayList<String> result);
    static private native void getInternalClassesForPackage0(long ptr, String packageName, ArrayList<String> result);
    static private native long getContextProgram0(QoreURLClassLoader syscl, BooleanWrapper created, boolean finalize_init);
    static private native void shutdownContext0();
    static private native void clearCompilationCache0(long ptr);
    static private native void dummy0();
    static private native void debug0(long ptr);

    //! Returns the canonical "system" {@code QoreURLClassLoader} (Globals::syscl in C++).
    /** Used as the fallback shared home for {@code qore.Qore.*} built-in classes and for
        module classes whose owning user Program cannot be resolved (binary modules,
        modules not yet loaded under their canonical name).
        {@code qoremod.<mod>.*} normally routes to the owning user module's Program loader
        via {@link #getModuleLoader0(String)} instead.

        Returns {@code null} during very early init before syscl exists.
    */
    static private native QoreURLClassLoader getSyscl0();

    //! Returns the {@link QoreURLClassLoader} of the named user module's owning Program.
    /** User modules (.qm files) live in a dedicated QoreProgram that is the canonical home
        for their %Qore classes — and thus for the corresponding {@code qoremod.<mod>.*}
        Java classes.  Routing every defineClass / loadClass for {@code qoremod.<mod>.*}
        through this single loader keeps Class identity consistent across consumer Programs.

        Returns {@code null} for unknown module names and for binary modules that don't
        have an owning user Program; callers fall back to the syscl loader.
    */
    static private native QoreURLClassLoader getModuleLoader0(String module_name);

    //! Returns the canonical {@link QoreURLClassLoader} for {@code bin_name}.
    /** The canonical loader is the {@link QoreURLClassLoader} of the QoreProgram that owns
        the underlying QoreClass.  Two name shapes are dispatched:
        <ul>
        <li>{@code qoremod.<mod>.<X>} resolves to the user module's owning Program
        <li>{@code qore.<X>.<Y>...} converts to qpath {@code ::<X>::<Y>::...} and looks the
            class up in the calling loader's Program (which is presumed to have the
            relevant module loaded — that is how the Java reference reached us); routes to
            {@code qc.getProgram()} of the matching QoreClass.  Catches binary-module
            shadow classes such as {@code qore.Qore.Logger.LoggerInterface} and Qorus
            injections such as {@code qore.OMQ.UserApi.AbstractUserApi}.
        </ul>

        Returns {@code null} when no canonical owner can be determined; callers fall back
        to local generation.
    */
    private native QoreURLClassLoader getCanonicalLoader0(String bin_name);
}
