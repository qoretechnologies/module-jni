/**
    QoreURLClassLoader.java

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.

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
import java.net.URI;
import java.net.URL;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.HashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
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

    //! The Java system property that enables the debug messages of the class loader when set to \c "true"
    /** The messages are printed on \c System.out; the property can be set with the \c set-property module command
        or as a JVM option (ex: <tt>QORE_JNI_JVM_ARGS=-Dqore.QoreURLClassLoader.debug=true</tt>), and it is checked
        when a message is logged (qore#6819)
    */
    public static final String DEBUG_PROP_NAME = "qore.QoreURLClassLoader.debug";

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

    //! used to mark java class creation in progress in the current Java call chain; binary names used
    private final ThreadLocal<HashSet<String>> classInProgress = ThreadLocal.withInitial(HashSet::new);

    //! cache of inner classes to resolve circular dependencies when injecting classes
    /** Using ConcurrentHashMap for thread-safe access */
    private final ConcurrentHashMap<String, byte[]> pendingClasses = new ConcurrentHashMap<String, byte[]>();

    //! cache of classes when running as the boot classloader
    /** Using ConcurrentHashMap for thread-safe access */
    private final ConcurrentHashMap<String, Class<?>> classCache = new ConcurrentHashMap<String, Class<?>>();

    //! Explicit process-wide owners for dynamically projected Qore classes
    /** Applications can declare that a particular Qore class has one Java identity across
        Program classloaders with {@link #registerSharedDynamicClass(String)}.  The first
        registering Program loader owns the name; sibling loaders delegate to it.
    */
    private static final ConcurrentHashMap<String, QoreURLClassLoader> sharedDynamicClassLoaders =
        new ConcurrentHashMap<String, QoreURLClassLoader>();

    // when used to bootstrap Java
    private static boolean static_bootstrap = false;

    //! static initialization
    static {
        registerAsParallelCapable();

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
            QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(qualifiedClassName);
            if (shared_loader != null && shared_loader != this) {
                return shared_loader.getResourceAsStream(name);
            }
            QoreJavaFileObject file = classes.get(qualifiedClassName);
            if (file != null) {
                return new ByteArrayInputStream(file.getByteCode());
            }
            // Also serve bytecode for dynamically-generated classes that are cached in
            // pendingClasses.  This lets a Byte Buddy TypePool / ClassFileLocator read the
            // class file of a generated (e.g. module-owned) class without loading it via
            // reflection, which is required to describe a superclass during subclass
            // generation without forcing eager JVM resolution of types referenced in the
            // superclass's method signatures (see JavaClassBuilder.getClassBuilder()).
            byte[] byte_code = pendingClasses.get(qualifiedClassName);
            if (byte_code != null) {
                return new ByteArrayInputStream(byte_code);
            }
            // Also serve internal jni-module classes (org.qore.*) and embedded Byte Buddy
            // classes (net.bytebuddy.*) so a TypePool can resolve the full superclass chain
            // of a generated class from bytecode (e.g. QoreJavaClassBase, the default base).
            if (qualifiedClassName.startsWith("org.qore.")) {
                byte[] internal = getInternalClass0(qualifiedClassName);
                if (internal != null) {
                    return new ByteArrayInputStream(internal);
                }
            } else if (qualifiedClassName.startsWith("net.bytebuddy.")) {
                byte[] bb = getCachedClass0(qualifiedClassName);
                if (bb != null) {
                    return new ByteArrayInputStream(bb);
                }
            }
        }
        return super.getResourceAsStream(name);
    }

    public void addPathOrig(String path) throws Exception {
        //debugLog("QoreURLClassLoader.addPath(): file://" + path);
        super.addURL(new File(path).toURI().toURL());
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

    //! Returns true if the given binary name has bytecode in pendingClasses.
    public boolean hasPendingClass(String bin_name) {
        return pendingClasses.containsKey(bin_name);
    }

    public Class<?> getResolveClass(String name) throws ClassNotFoundException {
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(name);
        if (shared_loader != null && shared_loader != this) {
            return shared_loader.getResolveClass(name);
        }
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
        - classInProgress: classes currently being created in the current Java call chain
    */
    public void clearAllCaches() {
        pendingClasses.clear();
        classCache.clear();
        classes.clear();
        classInProgress.get().clear();
    }

    //! Registers an exact dynamically projected Qore class name as process-wide
    /** The first Program loader to register the name becomes its owner.  Definitions and
        loads through sibling <tt>QoreURLClassLoader</tt> instances then resolve through
        that owner so the JVM sees one <tt>Class</tt> object for the name.

        Registration is idempotent and must happen before the class is loaded in every
        participating Program.  Register only classes whose Qore definitions are
        semantically identical across those Programs; same-path Qore classes are otherwise
        intentionally Program-local.

        @param bin_name exact Java binary name in dotted form, beginning with <tt>qore.</tt>
                        or <tt>qoremod.</tt>
        @throws IllegalArgumentException if <tt>bin_name</tt> is not a dynamic Qore class name
        @throws IllegalStateException if this loader already defined a different Class for a
                                      name owned by another registered loader
    */
    public void registerSharedDynamicClass(String bin_name) {
        if (!isDynamicQoreBinaryName(bin_name)) {
            throw new IllegalArgumentException(String.format(
                "invalid shared dynamic class binary name; expected 'qore.<class>' or "
                + "'qoremod.<module>.<class>': '%s'", bin_name));
        }

        while (true) {
            QoreURLClassLoader owner = getSharedDynamicClassLoader(bin_name);
            if (owner == null) {
                owner = sharedDynamicClassLoaders.putIfAbsent(bin_name, this);
                if (owner == null) {
                    return;
                }
                if (owner.pgm_ptr.get() == 0) {
                    sharedDynamicClassLoaders.remove(bin_name, owner);
                    continue;
                }
            }
            if (owner != this && findLoadedClass(bin_name) != null) {
                throw new IllegalStateException(String.format(
                    "cannot share dynamic class '%s': this loader has already defined a different Class",
                    bin_name));
            }
            return;
        }
    }

    //! Returns true if the argument is a syntactically valid dynamic Qore Java binary name
    private static boolean isDynamicQoreBinaryName(String bin_name) {
        if (bin_name == null) {
            return false;
        }

        int offset;
        boolean module_name;
        if (bin_name.startsWith("qore.")) {
            offset = 5;
            module_name = false;
        } else if (bin_name.startsWith("qoremod.")) {
            offset = 8;
            module_name = true;
        } else {
            return false;
        }

        boolean segment_start = true;
        boolean segment_separator = false;
        for (int i = offset; i < bin_name.length();) {
            int c = bin_name.codePointAt(i);
            if (c == '.') {
                if (segment_start) {
                    return false;
                }
                segment_start = true;
                segment_separator = true;
            } else if (segment_start) {
                if (!Character.isJavaIdentifierStart(c)) {
                    return false;
                }
                segment_start = false;
            } else if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
            i += Character.charCount(c);
        }
        return !segment_start && (!module_name || segment_separator);
    }

    //! Returns the registered owner for a shared dynamic class, removing stale owners
    private static QoreURLClassLoader getSharedDynamicClassLoader(String bin_name) {
        while (true) {
            QoreURLClassLoader owner = sharedDynamicClassLoaders.get(bin_name);
            if (owner == null || owner.pgm_ptr.get() != 0) {
                return owner;
            }
            sharedDynamicClassLoaders.remove(bin_name, owner);
        }
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

    //! Returns the class file bytecode for the given binary name, or null if it cannot be found.
    /** Used as a Byte Buddy <tt>ClassFileLocator</tt> (via {@link QoreClassFileLocator}) so that a
        TypePool can describe the full superclass chain of a class under generation from bytecode
        (rather than via reflection on loaded classes, which forces eager JVM resolution of types
        referenced in method signatures and can re-enter generation of an in-progress class).
        Shared dynamic classes are first routed to their canonical owning loader.  Local sources,
        in order: injected compiler classes, already-generated dynamic classes (pendingClasses),
        internal jni-module classes (org.qore.*), bundled Byte Buddy classes (net.bytebuddy.*),
        classpath / JDK resources, and finally on-demand generation for dynamic classes not yet
        cached.
    */
    public byte[] getClassFileBytes(String bin_name) {
        HashMap<String, HashSet<QoreURLClassLoader>> routing = classFileBytesRouting.get();
        HashSet<QoreURLClassLoader> routed = routing.get(bin_name);
        boolean outermost = routed == null;
        if (outermost) {
            routed = new HashSet<QoreURLClassLoader>();
            routing.put(bin_name, routed);
        }
        routed.add(this);
        try {
            return getClassFileBytesIntern(bin_name, routed);
        } finally {
            if (outermost) {
                routing.remove(bin_name);
            }
        }
    }

    //! The loaders already asked for a binary name on this thread, keyed by that name
    /** Bytecode lookup is routed to the canonical owning loader from two independent sources: the
        shared-owner registry ({@link #getSharedDynamicClassLoader}) and, natively, the QoreProgram
        that owns the underlying QoreClass as seen from the asking loader's Program
        ({@link #resolveSharedClassLoader}).  The native answer depends on which Program asks, so for
        a class visible in more than one Program - a Qorus API injection such as
        <tt>qore.OMQ.UserApi.</tt><i>X</i> - loader A can route the name to B while B routes the same
        name back to A.  Each hop only checked that its target was not itself, so the two loaders
        recursed until the stack was exhausted; javac reported that as a bare
        <tt>StackOverflowError</tt> with no diagnostics while compiling a class that wildcard-imports
        a dynamic namespace.  Recording the loaders already asked for the name makes the hop back
        fall through to the local lookup instead, which is what a loader with no canonical owner
        does anyway.  The map is keyed by binary name because generating one class legitimately asks
        the same loader for others.
    */
    private static final ThreadLocal<HashMap<String, HashSet<QoreURLClassLoader>>> classFileBytesRouting =
        ThreadLocal.withInitial(HashMap::new);

    //! Returns the class file bytecode for the given binary name without re-entering a loader
    /** @param bin_name the binary name to look up
        @param routed the loaders already asked for <i>bin_name</i> on this thread; see
        {@link #classFileBytesRouting}
    */
    private byte[] getClassFileBytesIntern(String bin_name, HashSet<QoreURLClassLoader> routed) {
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(bin_name);
        if (shared_loader != null && shared_loader != this && !routed.contains(shared_loader)) {
            return shared_loader.getClassFileBytes(bin_name);
        }

        // Bytecode lookup must follow the same canonical-loader routing as loadClass().
        // Otherwise a QoreClassFileLocator attached to a consumer Program regenerates a
        // shared qoremod.* superclass in the consumer loader.  Besides giving Byte Buddy
        // the wrong type identity, recursive hierarchy analysis can then re-enter the
        // class currently under generation.
        if (isDynamic(bin_name) && isSharedDynamicClassName(bin_name)) {
            QoreURLClassLoader target = resolveSharedClassLoader(bin_name);
            if (target != null && target != this && !routed.contains(target)) {
                byte[] bytes = target.getClassFileBytes(bin_name);
                if (bytes != null) {
                    return bytes;
                }
            }
        }

        QoreJavaFileObject file = classes.get(bin_name);
        if (file != null) {
            return file.getByteCode();
        }
        byte[] rv = pendingClasses.get(bin_name);
        if (rv != null) {
            return rv;
        }
        if (bin_name.startsWith("org.qore.")) {
            rv = getInternalClass0(bin_name);
            if (rv != null) {
                return rv;
            }
        } else if (bin_name.startsWith("net.bytebuddy.")) {
            rv = getCachedClass0(bin_name);
            if (rv != null) {
                return rv;
            }
        }
        try (InputStream is = super.getResourceAsStream(bin_name.replace('.', '/') + ".class")) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            // ignore and fall through
        }
        // dynamic classes that have not yet been generated/cached: generate on demand.  These
        // are ancestors of the class under generation (an acyclic chain), so this cannot
        // re-enter generation of the in-progress class itself.
        if (isDynamic(bin_name)) {
            try {
                return generateByteCode(bin_name);
            } catch (Throwable e) {
                // ignore: report as unresolvable below
            }
        }
        return null;
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
    /** Tracks recursion in the current thread only.
        @param bin_name the binary name of the class
        @return true if the class is currently being created
    */
    public boolean checkInProgress(String bin_name) {
        return classInProgress.get().contains(bin_name);
    }

    //! Mark a class as being created
    /** Tracks recursion in the current thread only.
        @param bin_name the binary name of the class
        @return true if the class was already being created, false if it was marked
    */
    private boolean markInProgress(String bin_name) {
        HashSet<String> inProgress = classInProgress.get();
        boolean previous = !inProgress.add(bin_name);
        //debugLog("marked in progress " + bin_name);
        return previous;
    }

    //! Remove a class from the in-progress set
    /** Tracks recursion in the current thread only.
        @param bin_name the binary name of the class
    */
    private void removeInProgress(String bin_name) {
        classInProgress.get().remove(bin_name);
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
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(bin_name);
        if (shared_loader != null && shared_loader != this) {
            return shared_loader.loadClass(bin_name);
        }

        // A class already defined in THIS loader always wins; defining it again here would make a
        // duplicate Class object and a LinkageError.  The parent chain is consulted further below,
        // after locally-injected byte code has had its say.
        Class<?> rv = findLoadedClass(bin_name);
        if (rv != null) {
            //System.out.printf("loadClass() %s returning loaded\n", bin_name);
            return rv;
        }

        // A shared dynamic name must resolve to exactly one Class object process-wide, so a copy a
        // parent loader has already defined outranks everything below.  This restores the ordering
        // these names had before the injected-byte-code rule at the bottom of this method existed,
        // and it must stay ahead of that rule: pendingClasses is not only an ownership statement,
        // it is also a plain byte-code cache -- the canonical-routing block just below stores byte
        // code in the *calling* loader purely so QoreKotlinCompiler.generateDynamicStubs() can read
        // it back.  Letting tryGetPendingClass() define from that cache hands out a second Class for
        // a name a parent already owns and splits the name's identity, which the JVM reports at the
        // first cross-loader override as
        //
        //   LinkageError: loader constraint violation for class qoremod.<mod>.<X>: ... the class
        //   loader 'QoreURLClassLoader' @a of the selected method's type ... and the class loader
        //   'QoreURLClassLoader' @b for its super type ... have different Class objects for the type
        //   qoremod.SqlUtil.<Y> used in the signature
        //
        // The canonical-routing block is not sufficient on its own: it only acts when the canonical
        // owner is some *other* loader, so a loader that is itself the canonical owner fell straight
        // through to the local cache.
        if (isSharedDynamicClassName(bin_name)) {
            rv = checkLoadedClass(bin_name);
            if (rv != null) {
                //System.out.printf("loadClass() %s returning shared copy from a parent\n", bin_name);
                return rv;
            }
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

        synchronized (getClassLoadingLock(bin_name)) {
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
        }

        // Only now consider a class already loaded by a parent QoreURLClassLoader.
        //
        // Byte code injected into THIS loader is an explicit statement that this loader must own the
        // class, so it has to outrank an inherited copy.  QoreJavaCompiler compiles into a child of the
        // owning Program's loader, and a caller that compiles a class extending an injected dependency
        // injects that dependency's byte code here while the same class may also be defined in the
        // parent (Qorus does exactly this: the dependency is defined in the interface Program with the
        // jni "define-class" command, then injected for the compile).  Letting the parent's copy win put
        // subclass and superclass in different runtime packages - a runtime package is (package name +
        // defining loader) - so the JVM rejected a package-private superclass with
        // "IllegalAccessError: class X cannot access its superclass Y".
        //
        // Shared dynamic names never reach this rule with a parent copy available: they are settled
        // above, first against the parent chain and then by canonical routing.  Routing alone does not
        // settle them - it acts only when the canonical owner is some other loader - which is why the
        // parent check for those names sits above this rule instead of being folded into it.
        rv = checkLoadedClass(bin_name);
        if (rv != null) {
            //System.out.printf("loadClass() %s returning loaded from a parent\n", bin_name);
            return rv;
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

                synchronized (getClassLoadingLock(bin_name)) {
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
                }
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
    /** Called only by the jni module's native code (through JNI, which ignores access): Java code can never supply a
        native class pointer, and the byte code generator refuses one that the module's own code did not give for the
        call that runs (qore#6854)
     */
    private Class<?> loadClassWithPtr(String bin_name, long class_ptr) throws ClassNotFoundException {
        //debugLog(String.format("loadClassWithPtr() %s: %x", bin_name, class_ptr));
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(bin_name);
        if (shared_loader != null && shared_loader != this) {
            // The class pointer belongs to the calling Program.  The registered owner must
            // generate bytecode from its own view of the Qore class instead.
            return shared_loader.loadClass(bin_name);
        }

        Class<?> rv;
        synchronized (getClassLoadingLock(bin_name)) {
            rv = checkLoadedClass(bin_name);
            if (rv != null) {
                //System.out.printf("loadClassWithPtr() %s returning loaded\n", bin_name);
                return rv;
            }
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
                    // the class pointer was given for this loader's Program: the canonical loader generates the
                    // class from its own Program's view of it, found by its name (qore#6854)
                    byte[] bytes = target.generateByteCode(bin_name, 0);
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

        synchronized (getClassLoadingLock(bin_name)) {
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
        }

        // only remove from set if successful
        try {
            //System.out.printf("loadClassWithPtr() this: %x %s about to call generateByteCode(%s, %x)\n",
            //    hashCode(), bin_name, bin_name, class_ptr);
            byte[] bytes = generateByteCode(bin_name, class_ptr);

            synchronized (getClassLoadingLock(bin_name)) {
                // have to check if the class was loaded in the meantime
                rv = checkLoadedClass(bin_name);
                if (rv != null) {
                    //System.out.printf("loadClassWithPtr() %s returning loaded after bytecode generation\n", bin_name);
                    return rv;
                }

                rv = defineClassIntern(bin_name, bytes, 0, bytes.length);
                //System.out.printf("loadClassWithPtr() this: %x pgm: %x dyn %s returning generated %s\n", hashCode(),
                //    pgm_ptr, bin_name, rv);
            }
        } catch (RuntimeException e1) {
            //e1.printStackTrace();
            throw e1;
        } catch (Throwable e1) {
            //e1.printStackTrace();
            throw new RuntimeException(e1);
        }
        return rv;
    }

    //! Returns true if <tt>bin_name</tt> refers to a class that should be canonicalized
    //! through the syscl loader (so all loaders that need the same shared type see the same
    //! Class object).
    /** Routing is required for two distinct shapes:
        - <b><tt>qoremod.</tt></b><i>mod</i><tt>.</tt><i>...</i>: classes provided by a Qore
          module.  The QoreClass is shared across Programs that load the module, so a single
          canonical Java Class avoids loader-constraint violations on subclass override
          resolution and ClassCastException on legitimate casts across Program boundaries.
        - <b><tt>qore.Qore.</tt></b><i>...</i>: built-in <tt>Qore::*</tt> classes that have
          a fixed identity across Programs.  Routing them through syscl is what lets, e.g.,
          a workflow program's <tt>JavaSimpleTestStep1</tt> call into a syscl-defined
          <tt>qoremod.SqlUtil.Table.&lt;init&gt;(qore.Qore.SQL.AbstractDatasource, String)</tt>
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
        <tt>qoremod.&lt;mod&gt;.&lt;X&gt;</tt> this is the module's user-Program; for legacy / shadow
        <tt>qore.&lt;X&gt;.&lt;Y&gt;...</tt> forms it is whichever Program owns the QoreClass at qpath
        <tt>::&lt;X&gt;::&lt;Y&gt;::...</tt> as visible from the calling loader's Program (typically
        the host Program that loaded the binary module providing the class).

        Returns <tt>null</tt> when no canonical owner is known; callers should fall back
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
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(name);
        if (shared_loader != null) {
            if (shared_loader != this) {
                return shared_loader.defineClassIntern(name, byte_code, off, len);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
                return defineClassInternLocal(name, byte_code, off, len);
            }
        }

        return defineClassInternLocal(name, byte_code, off, len);
    }

    //! Defines a class in this loader without shared-owner routing
    private Class<?> defineClassInternLocal(String name, byte[] byte_code, int off, int len) throws ClassFormatError {
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
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(name);
        if (shared_loader != null && shared_loader != this) {
            return shared_loader.defineResolveClass(name, b, off, len);
        }
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
        sharedDynamicClassLoaders.entrySet().removeIf(entry -> entry.getValue() == this);
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

    //! Returns the current thread's classloader context, for restoreContext()
    /** Java's calls into Qore save the context and restore it when they return to Java (qore#6485): the Qore code
        that runs can make Java calls for other Programs, each of which sets the context to its Program's
        classloader, and the Java code that called into Qore continues with its own context; an array, not an object
        of a class of its own, as this class is defined from its bytes alone, without nested classes

        @return the thread-local reference (null if not set) and the thread's context classloader
    */
    public static Object[] saveContext() {
        return new Object[] {current.get(), Thread.currentThread().getContextClassLoader()};
    }

    //! Restores a context returned by saveContext() on the current thread
    /** The thread-local reference is restored if one was saved and its Program still exists, and is otherwise left as
        it is, as calls from Java into Qore on the thread need one; the thread's context classloader is restored as
        saved, unless it is the classloader of a Program that has been deleted: a deleted Program's classloader is not
        restored, so that a long-lived thread does not keep it, and its classes, reachable
    */
    public static void restoreContext(Object[] saved) {
        QoreURLClassLoader prev = (QoreURLClassLoader) saved[0];
        if (prev != null && prev.pgm_ptr.get() != 0) {
            current.set(prev);
        }
        ClassLoader prev_tccl = (ClassLoader) saved[1];
        if (prev_tccl instanceof QoreURLClassLoader && ((QoreURLClassLoader) prev_tccl).pgm_ptr.get() == 0) {
            return;
        }
        Thread.currentThread().setContextClassLoader(prev_tccl);
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

    //! Adds a path to the classpath of the parent class loader, the classpath that every Program shares
    /** With no parent class loader that is a QoreURLClassLoader (ex: the class loader of the bootstrap Program, whose
        parent is the platform class loader), the path is added to this class loader.  An error while the path is
        added is thrown to the caller (qore#6779).
    */
    public void addParentPath(String classpath) {
        ClassLoader parent = getParent();
        if (parent instanceof QoreURLClassLoader) {
            ((QoreURLClassLoader)parent).addPath(classpath);
        } else {
            addPath(classpath);
        }
    }

    //! Returns the entries of a classpath as addPath() uses them
    /** The entries are separated by the system path separator or \c ';'; empty entries are left out.  The sandbox
        check of a classpath (resolveClasspath()) resolves the same entries.
    */
    static ArrayList<String> classpathEntries(String classpath) {
        String seps = File.pathSeparator; // separators

        // want to accept both system separator and ';'
        if (!File.pathSeparator.equals(";")) {
            seps += ";";
        }
        ArrayList<String> rv = new ArrayList<String>();
        for (StringTokenizer st = new StringTokenizer(classpath, seps, false); st.hasMoreTokens();) {
            String pathentry = st.nextToken();
            if (pathentry.length() != 0) {
                rv.add(pathentry);
            }
        }
        return rv;
    }

    //! Returns a classpath entry as addPath() uses it: an absolute path, as the JVM resolves it
    /** A relative entry is resolved against the \c user.dir of the JVM, not the current directory of the process;
        this includes a wildcard with no directory part (ex: \c "*.jar"), which is the matching files in \c user.dir.
        The path is absolute, so a wildcard entry always has a parent directory.  The sandbox check of a classpath
        (resolveClasspath()) resolves each entry from this path, so that it checks each entry where it is used.
    */
    static File resolveClasspathEntry(String pathentry) {
        return new File(pathentry).getAbsoluteFile();
    }

    //! Adds a path to the classpath
    /** An entry that does not exist or cannot be used is logged and left out; an error while an entry is added is
        thrown to the caller (qore#6779).  A classpath that a sandbox filesystem policy governs is not added here, but
        with resolveClasspath() and addResolvedPath(), so that every file that is added is one that the policy
        checked (qore#6818).
    */
    public void addPath(String classpath) {
        //debugLog(String.format("%x: addPath(): %s", hashCode(), classpath));
        if (classpath == null) {
            throw new IllegalArgumentException("the classpath must not be null");
        }

        for (String pathentry : classpathEntries(classpath)) {
            //debugLog("addPath(): " + pathentry);
            String basename = null;

            File fileentry = resolveClasspathEntry(pathentry);
            if (fileentry.getName().indexOf('*') != -1) {
                basename = fileentry.getName();
                // not null: the path is absolute, and a root directory has no name with a wildcard
                fileentry = fileentry.getParentFile();
                if (fileentry.getName().indexOf('*') != -1) {
                    errorLog("Ignoring wildcard in classpath directory element '" + pathentry + "'");
                    continue;
                }
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
            // recorded once it has been added: an entry whose addition raised an error is added again the next time
            classPathElements.add(fileentry.getPath());
        }
        //infoLog("Class loader is using classpath: \"" + classPath + "\".");
    }

    //! The most jars and directories that the manifest Class-Path attributes of a sandboxed classpath can name
    /** Each entry that the manifests name is resolved and checked, and each jar's manifest read, before the classpath
        is added; a classpath whose manifests name more entries is refused (qore#6822).  The limit bounds that work for
        a set of manifests whose entries keep naming new paths (ex: through a directory link to its own directory).
    */
    static final int MAX_MANIFEST_ENTRIES = 4096;

    //! The JDK feature versions whose class loader resolves manifest Class-Path attributes as ResolvedClasspath does
    /** The sandbox check of a classpath resolves the manifest Class-Path attributes of its jars as the JDK's class
        loader resolves them (jdk.internal.loader.URLClassPath), so that every file that the class loader reaches is
        one that was checked; that resolution is internal to the JDK and may change in another version.  It has been
        verified for these feature versions; on any other, a governed classpath with a jar that has a manifest
        Class-Path attribute is refused (qore#6822).
    */
    static final int[] VERIFIED_CLASS_PATH_JDK_FEATURES = {25};

    //! Returns true if the class loader of the given JDK feature version has been verified for the sandbox check of
    //! manifest Class-Path attributes
    static boolean isVerifiedClassPathJdk(int feature) {
        for (int f : VERIFIED_CLASS_PATH_JDK_FEATURES) {
            if (f == feature) {
                return true;
            }
        }
        return false;
    }

    //! A classpath resolved once, for both its sandbox check and its use (qore#6818, qore#6822)
    /** Made by resolveClasspath(); each element is a path that the sandbox check of the classpath checks, and the
        file that addResolvedPath() adds for it, if any.  The check and the use take their paths from this one object,
        so every file that is added is one that was checked, with no second resolution in between.

        The sandbox check (see jni_check_classpath_policy() in the module) reads each element with size(),
        encodedPath(), isWildcardMatch(), manifestReferrer() and refusal(), and after an element has been checked, it
        calls expand() for it (with QoreSandbox.expandClasspath()), which adds the jars and directories that the
        manifest Class-Path attribute of a jar names as further elements, to be checked in turn: a file is checked
        before it is opened.  A denial refuses the whole classpath, so the classpath is then not added at all.

        The elements that a manifest names are only checked; they are not added to the class loader, which follows
        the manifest Class-Path attributes of the jars that it opens itself.  Each is the path that the class loader
        opens for the entry: the entry is resolved as a URL against the URL that the class loader has for the jar
        that names it (not against the real path of that jar), and the file that the URL names is the element, with
        symbolic links resolved.  So every file that the class loader reaches through a manifest is one that was
        checked, and the classpath that is used is the one that was given.
    */
    static final class ResolvedClasspath {
        //! the path that is checked for each element
        private final ArrayList<File> paths = new ArrayList<File>();
        //! the file that is added for each element, or null for an element that is only checked
        private final ArrayList<File> files = new ArrayList<File>();
        //! for each element: true if a wildcard selected it
        private final ArrayList<Boolean> matches = new ArrayList<Boolean>();
        //! for each element: a message logged when the element is used, or null
        private final ArrayList<String> notes = new ArrayList<String>();
        //! for each element: the URL of the jar as the class loader opens it, whose manifest is read, or null
        private final ArrayList<URL> jarUrls = new ArrayList<URL>();
        //! for each element that a manifest names: the path of the jar whose manifest names it, or null
        private final ArrayList<String> referrers = new ArrayList<String>();
        //! for each element: the reason that the classpath is refused, or null
        private final ArrayList<String> refusals = new ArrayList<String>();
        //! the checked paths as the bytes that the JVM uses for them, set by the sandbox check
        private final ArrayList<byte[]> encoded = new ArrayList<byte[]>();
        //! the URLs of the jars and directories, as the class loader identifies them, that are elements already
        private final HashSet<String> seen = new HashSet<String>();
        //! the number of elements that have been checked and expanded
        private int expanded;
        //! the number of elements that manifests named
        private int manifestEntries;
        //! true once an element refuses the classpath
        private boolean refused;
        //! the JDK feature version whose class loader has not been verified for manifest Class-Path checking: the
        //! running JDK's, or else a version assumed as well; 0 if both have been verified
        private final int unverifiedFeature;

        //! Makes a resolved classpath for the running JDK, or also assuming another JDK feature version (> 0)
        /** A version assumed instead (only a debug build of the module passes one, for its tests) can only make the
            check stricter: manifest Class-Path attributes are checked only if the running JDK's version has been
            verified as well.
        */
        private ResolvedClasspath(int assumedFeature) {
            int running = Runtime.version().feature();
            if (!isVerifiedClassPathJdk(running)) {
                unverifiedFeature = running;
            } else if (assumedFeature > 0 && !isVerifiedClassPathJdk(assumedFeature)) {
                unverifiedFeature = assumedFeature;
            } else {
                unverifiedFeature = 0;
            }
        }

        private void add(File path, File file, boolean match, String note) {
            add(path, file, match, note, null, null, null);
        }

        private void add(File path, File file, boolean match, String note, URL jarUrl, String referrer,
                String refusal) {
            paths.add(path);
            files.add(file);
            matches.add(match);
            notes.add(note);
            jarUrls.add(jarUrl);
            referrers.add(referrer);
            refusals.add(refusal);
        }

        //! Adds an element that refuses the classpath; no further element is expanded
        private void refuse(File path, String referrer, String refusal) {
            add(path, null, false, null, null, referrer, refusal);
            refused = true;
        }

        //! Refuses the classpath for a file that is neither a regular file nor a directory, which is not opened
        /** The class loader would open such a file (ex: a device) as a jar, so its manifest would have to be read
            to check it, but reading it is not safe (ex: a FIFO blocks).
        */
        private void refuseSpecial(File real, String referrer) {
            refuse(real, referrer, "the classpath jar '" + real + "' is not a regular file, so the jars that its "
                + "manifest Class-Path attribute could name cannot be checked against the sandbox filesystem policy");
        }

        //! Returns the number of elements
        int size() {
            return paths.size();
        }

        //! Returns the path that is checked for an element
        File path(int i) {
            return paths.get(i);
        }

        //! Returns the number of elements whose paths have been encoded
        int encodedSize() {
            return encoded.size();
        }

        //! Adds the encoding of the next element's path (see QoreSandbox.resolveClasspath())
        void addEncoded(byte[] bytes) {
            if (encoded.size() >= paths.size()) {
                throw new IllegalArgumentException("the encoded paths do not match the classpath");
            }
            encoded.add(bytes);
        }

        //! Returns the path that is checked for an element as the bytes that the JVM uses for it
        byte[] encodedPath(int i) {
            return encoded.get(i);
        }

        //! Returns true if a wildcard selected the element, so that its denial is reported as such
        boolean isWildcardMatch(int i) {
            return matches.get(i);
        }

        //! Returns the path of the jar whose manifest names the element, or null if the classpath gives it
        String manifestReferrer(int i) {
            return referrers.get(i);
        }

        //! Returns the reason that the element refuses the classpath, or null
        String refusal(int i) {
            return refusals.get(i);
        }

        //! Returns true if every element has been checked and expanded
        boolean isChecked() {
            return expanded == paths.size() && encoded.size() == paths.size();
        }

        //! Adds a jar of the classpath that the class loader opens, so that its manifest is read when it is expanded
        private void addJar(File real, boolean match) {
            URL url = loaderUrl(real);
            if (url == null) {
                // its manifest could not be resolved as the class loader resolves it
                refuse(real, null, "the classpath jar '" + real + "' cannot be represented as a URL to check the "
                    + "jars that its manifest Class-Path attribute names against the sandbox filesystem policy");
                return;
            }
            seen.add(urlKey(url));
            add(real, real, match, null, url, null, null);
        }

        //! Called after element \a i has been checked: adds the entries of its manifest Class-Path, if any (qore#6822)
        /** The elements are expanded in order, each once, and only after it has been checked, so that no file is
            opened before the policy has allowed it.  The jar is read at its checked (real) path.  A manifest that
            cannot be read refuses the classpath, as its Class-Path cannot be checked.
        */
        void expand(int i) {
            if (i != expanded || i >= paths.size()) {
                throw new IllegalStateException("classpath elements must be expanded in order after they are checked");
            }
            ++expanded;
            URL base = jarUrls.get(i);
            // once the classpath is refused, nothing more is read: the check raises the refusal when it reaches it
            if (base == null || refused) {
                return;
            }
            File jar = paths.get(i);
            String classpath;
            try (JarFile jf = new JarFile(jar, true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                // before Java 22, the class loader can open the jars that a jar index lists (if jar indexes are
                // enabled), which this check does not resolve
                if (Runtime.version().feature() < 22 && jf.getEntry("META-INF/INDEX.LIST") != null) {
                    refuse(jar, null, "the classpath jar '" + jar + "' has a jar index, whose jars cannot be checked "
                        + "against the sandbox filesystem policy");
                    return;
                }
                Manifest man = jf.getManifest();
                Attributes attrs = man == null ? null : man.getMainAttributes();
                classpath = attrs == null ? null : attrs.getValue(Attributes.Name.CLASS_PATH);
            } catch (IOException | RuntimeException e) {
                refuse(jar, null, "cannot read the manifest of the classpath jar '" + jar + "' to check the jars that "
                    + "its Class-Path attribute names against the sandbox filesystem policy: " + e);
                return;
            }
            // an attribute with no entries (as the class loader splits it) names nothing
            if (classpath == null || !new StringTokenizer(classpath).hasMoreTokens()) {
                return;
            }
            // the entries can only be resolved as the class loader resolves them on a JDK whose class loader has been
            // verified
            if (unverifiedFeature != 0) {
                refuse(jar, null, "the classpath jar '" + jar + "' has a manifest Class-Path attribute, which cannot "
                    + "be checked against the sandbox filesystem policy on an unverified JDK version "
                    + unverifiedFeature + " for manifest Class-Path checking (verified: "
                    + Arrays.toString(VERIFIED_CLASS_PATH_JDK_FEATURES) + ")");
                return;
            }
            // the entries are separated by white space, as the class loader splits them
            for (StringTokenizer st = new StringTokenizer(classpath); st.hasMoreTokens();) {
                String entry = st.nextToken();
                if (++manifestEntries > MAX_MANIFEST_ENTRIES) {
                    refuse(jar, jar.getPath(), "the manifest Class-Path attributes of the classpath name more than "
                        + MAX_MANIFEST_ENTRIES + " entries, which cannot be checked against the sandbox filesystem "
                        + "policy");
                    return;
                }
                addManifestEntry(base, jar, entry);
                if (refused) {
                    return;
                }
            }
        }

        //! Adds an entry of the manifest Class-Path of a jar, as the path that the class loader opens for it
        @SuppressWarnings("deprecation")
        private void addManifestEntry(URL base, File jar, String entry) {
            String referrer = jar.getPath();
            URL url;
            try {
                // as the class loader resolves the entry: against the URL of the jar as the class loader opens it
                url = new URL(base, entry);
            } catch (MalformedURLException e) {
                refuse(jar, referrer, "the manifest Class-Path attribute of the classpath jar '" + jar + "' has an "
                    + "invalid entry '" + entry + "': " + e.getMessage());
                return;
            }
            // only a file can be checked against the filesystem policy
            if (!"file".equals(url.getProtocol())) {
                refuse(jar, referrer, "the manifest Class-Path attribute of the classpath jar '" + jar + "' names '"
                    + url + "', which is not a file, so it cannot be checked against the sandbox filesystem policy");
                return;
            }
            String key = urlKey(url);
            if (seen.contains(key)) {
                return;
            }
            seen.add(key);
            String file = url.getFile();
            boolean dir = file.endsWith("/");
            if (!dir) {
                // the class loader opens a jar URL with a host only if the host is the local one
                String host = url.getHost();
                if (host != null && !host.isEmpty() && !host.equalsIgnoreCase("localhost")) {
                    return;
                }
            }
            String path = decodeUrlPath(file);
            if (path == null || path.indexOf('\0') != -1 || !new File(path).isAbsolute()) {
                refuse(jar, referrer, "the manifest Class-Path attribute of the classpath jar '" + jar + "' names '"
                    + url + "', which cannot be resolved to a file to check against the sandbox filesystem policy");
                return;
            }
            File f = new File(path);
            File real = realFile(f);
            if (real == null) {
                // as for an entry of the classpath, a file that does not exist is checked as it is named
                add(f, null, false, null, null, referrer, null);
                return;
            }
            // a directory is read as files below it and has no manifest; a jar's manifest is read when it is expanded
            if (!dir && isSpecialFile(real)) {
                refuseSpecial(real, referrer);
                return;
            }
            URL jarUrl = dir || !Files.isRegularFile(real.toPath()) ? null : url;
            add(real, null, false, null, jarUrl, referrer, null);
        }
    }

    //! Returns the URL that the class loader uses for a jar that addResolvedPath() adds, as the base of its manifest
    //! Class-Path, or null
    /** addResolvedPath() adds the URL of createUrl(): for a file ending in ".jar", a "jar:" URL, from which the class
        loader takes the URL of the file within it; otherwise the URL of the file.
    */
    @SuppressWarnings("deprecation")
    private static URL loaderUrl(File file) {
        try {
            URI uri = file.toURI();
            URL url = uri.toURL();
            if (url.getPath().endsWith(".jar")) {
                URL jarurl = URI.create("jar:" + uri + "!/").toURL();
                String spec = jarurl.getFile();
                return new URL(spec.substring(0, spec.length() - 2));
            }
            return url;
        } catch (MalformedURLException | IllegalArgumentException e) {
            return null;
        }
    }

    //! Returns the key of a URL by which the class loader identifies the loader for it (its URL with no fragment)
    private static String urlKey(URL url) {
        StringBuilder sb = new StringBuilder();
        String protocol = url.getProtocol();
        if (protocol != null) {
            sb.append(protocol.toLowerCase(Locale.ROOT)).append("://");
        }
        String host = url.getHost();
        if (host != null) {
            sb.append(host.toLowerCase(Locale.ROOT));
            int port = url.getPort();
            if (port == -1) {
                port = url.getDefaultPort();
            }
            if (port != -1) {
                sb.append(':').append(port);
            }
        }
        String file = url.getFile();
        if (file != null) {
            sb.append(file);
        }
        return sb.toString();
    }

    //! Returns the path of a file URL as the class loader decodes it, or null if it cannot be decoded exactly
    /** Each run of percent-encoded bytes is decoded as UTF-8; every other character is kept.  A malformed escape or
        byte sequence gives null, so that the entry is refused rather than resolved in another way than the class
        loader resolves it.
    */
    static String decodeUrlPath(String s) {
        if (s.indexOf('%') == -1) {
            return s;
        }
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c != '%') {
                sb.append(c);
                ++i;
                continue;
            }
            bytes.reset();
            while (i < n && s.charAt(i) == '%') {
                if (i + 2 >= n) {
                    return null;
                }
                int hi = hexValue(s.charAt(i + 1));
                int lo = hexValue(s.charAt(i + 2));
                if (hi < 0 || lo < 0) {
                    return null;
                }
                bytes.write((hi << 4) | lo);
                i += 3;
            }
            try {
                sb.append(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())));
            } catch (CharacterCodingException e) {
                return null;
            }
        }
        return sb.toString();
    }

    //! Returns the value of an ASCII hexadecimal digit, or -1
    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    //! Returns true if an existing file is neither a regular file nor a directory (ex: a device or a FIFO)
    private static boolean isSpecialFile(File real) {
        return !Files.isRegularFile(real.toPath()) && !Files.isDirectory(real.toPath());
    }

    //! Returns the real path of a file (symbolic links resolved), or null if it cannot be resolved
    private static File realFile(File f) {
        try {
            return f.toPath().toRealPath().toFile();
        } catch (IOException | java.nio.file.InvalidPathException | SecurityException e) {
            return null;
        }
    }

    //! Resolves a classpath once for its sandbox check and its use (qore#6818)
    /** The entries are those of addPath() (classpathEntries()), each made absolute as addPath() resolves it
        (resolveClasspathEntry()).  Each entry is resolved to its real path, with symbolic links resolved, which is
        the path that is checked and added.  A wildcard entry is expanded here, once: its directory is an element
        that is only checked, as its listing is read, and each matching file is an element of its own, with its real
        path, so that the policy judges each file that the wildcard selects, wherever a symbolic link points.  An
        entry that cannot be resolved (ex: it does not exist) is checked as given, and nothing is added for it.

        The jars that the manifest Class-Path attributes of these jars name are added as further elements by
        ResolvedClasspath.expand(), as each jar is checked (qore#6822).

        @param classpath the classpath
        @param jdkFeature a JDK feature version for the check of manifest Class-Path attributes to assume as well as
        the running JDK's, which can only make the check stricter, or 0 for none
    */
    static ResolvedClasspath resolveClasspath(String classpath, int jdkFeature) {
        if (classpath == null) {
            throw new IllegalArgumentException("the classpath must not be null");
        }
        ResolvedClasspath rc = new ResolvedClasspath(jdkFeature);
        for (String pathentry : classpathEntries(classpath)) {
            File fileentry = resolveClasspathEntry(pathentry);
            if (fileentry.getName().indexOf('*') == -1) {
                File real = realFile(fileentry);
                if (real == null) {
                    // as addPath() does, an entry that does not exist is left out with no message
                    rc.add(fileentry, null, false, null);
                } else if (Files.isRegularFile(real.toPath()) && isLoadable(real.getName())) {
                    rc.addJar(real, false);
                } else if (isLoadable(real.getName()) && isSpecialFile(real)) {
                    rc.refuseSpecial(real, null);
                } else {
                    rc.add(real, real, false, null);
                }
                continue;
            }

            String pattern = fileentry.getName();
            // not null: the path is absolute, and a root directory has no name with a wildcard
            File dir = fileentry.getParentFile();
            if (dir.getName().indexOf('*') != -1) {
                rc.add(fileentry, null, false, "Ignoring wildcard in classpath directory element '" + pathentry
                    + "'");
                continue;
            }
            File realdir = realFile(dir);
            if (realdir == null) {
                rc.add(dir, null, false, "Cannot find directory for classpath element '" + dir + File.separator
                    + pattern + "'");
                continue;
            }
            File[] list = realdir.listFiles();
            if (list == null) {
                rc.add(realdir, null, false, "Error accessing directory for classpath element '" + realdir
                    + File.separator + pattern + "'");
                continue;
            }
            // the directory, whose listing is read
            rc.add(realdir, null, false, null);
            for (File f : list) {
                if (!wildcardMatches(pattern, f.getName()) || !isLoadable(f.getName())) {
                    continue;
                }
                // a match that cannot be resolved (ex: a dangling symbolic link) cannot be read, and is not added
                File real = realFile(f);
                if (real == null) {
                    continue;
                }
                if (Files.isRegularFile(real.toPath())) {
                    rc.addJar(real, true);
                } else if (isSpecialFile(real)) {
                    rc.refuseSpecial(real, null);
                } else {
                    rc.add(real, real, true, null);
                }
            }
        }
        return rc;
    }

    //! Adds a classpath that the sandbox check has resolved and checked (qore#6818)
    /** Adds the files of the resolved classpath, each of which was checked; nothing is resolved again.  The elements
        that manifest Class-Path attributes name are only checked: the class loader follows the manifests of the jars
        that are added itself (qore#6822).  An error while an element is added is thrown to the caller (qore#6779).
    */
    void addResolvedPath(ResolvedClasspath rc) {
        if (rc == null) {
            throw new IllegalArgumentException("the classpath must not be null");
        }
        if (!rc.isChecked()) {
            throw new IllegalArgumentException("the classpath has not been checked");
        }
        for (int i = 0; i < rc.size(); ++i) {
            String note = rc.notes.get(i);
            if (note != null) {
                errorLog(note);
            }
            File fileentry = rc.files.get(i);
            if (fileentry == null || classPathElements.contains(fileentry.getPath())) {
                continue;
            }
            if (rc.isWildcardMatch(i) || fileentry.isDirectory() || isLoadable(fileentry.getName())) {
                addURL(createUrl(fileentry));
            } else {
                errorLog("ClassPath element '" + fileentry + "' is not an existing directory and is not a file " +
                    "ending with '.zip' or '.jar'");
            }
            // recorded once it has been added: an entry whose addition raised an error is added again the next time
            classPathElements.add(fileentry.getPath());
        }
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
            // listing the classes can load a module, whose initialization code can make Java calls for other
            // Programs: the thread's classloader context is restored when the call returns to Java (qore#6551)
            Object[] ctx = saveContext();
            try {
                getClassesInNamespace0(ptr, info.cls, info.mod, info.python, info.kotlin, rv);
            } finally {
                restoreContext(ctx);
            }
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

    //! Returns the entries of a classpath as addPath() resolves them, with a wildcard entry as its pattern in its
    //! directory
    public static ArrayList<File> splitClassPath(String classpath) {
        ArrayList<File> rv = new ArrayList<File>();

        for (String pathentry : classpathEntries(classpath)) {
            String basename = null;

            File fileentry = resolveClasspathEntry(pathentry);
            if (fileentry.getName().indexOf('*') != -1) {
                basename = fileentry.getName();
                // not null: the path is absolute, and a root directory has no name with a wildcard
                fileentry = fileentry.getParentFile();
                if (fileentry.getName().indexOf('*') != -1) {
                    errorLog("Ignoring wildcard in classpath directory element '" + pathentry + "'");
                    continue;
                }
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

    //! Prints a debug message on System.out if debugging is enabled with the DEBUG_PROP_NAME system property
    static private void debugLog(String msg) {
        if (Boolean.getBoolean(DEBUG_PROP_NAME)) {
            System.out.println("QoreURLClassLoader DEBUG: " + msg);
        }
    }

    static private void errorLog(String msg) {
        System.out.println("QoreURLClassLoader ERROR: " + msg);
    }

    //! Returns true if a file name matches the pattern of a wildcard classpath entry
    /** Only \c '*' is a wildcard, which matches any sequence of characters, also an empty one; every other character
        of the pattern matches itself, also a character that is special in a regular expression, such as \c '.',
        \c '[', \c '(', \c '+' or \c '?' (qore#6777).  The pattern matches the whole file name.  The match takes
        time proportional to the product of the lengths of the pattern and the name at most, whatever the pattern,
        unlike a regular expression with several wildcards, which can backtrack exponentially.
    */
    static boolean wildcardMatches(String pattern, String name) {
        int p = 0;
        int n = 0;
        // the position of the last '*' in the pattern, and the position in the name that it was tried at
        int star = -1;
        int mark = 0;
        while (n < name.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                mark = n;
            } else if (p < pattern.length() && pattern.charAt(p) == name.charAt(n)) {
                ++p;
                ++n;
            } else if (star != -1) {
                // the last '*' matches one more character
                p = star + 1;
                n = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            ++p;
        }
        return p == pattern.length();
    }

    /**
     * Adds a set of files using a generic base name to this loader's classpath.  See @ref addPath() for
     * details of the generic base name; only \c '*' is a wildcard (see wildcardMatches()).
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

        //debugLog("dir: " + dir + " pattern: " + nam);

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return wildcardMatches(nam, name);
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

    public byte[] generateByteCode(String bin_name) throws ClassNotFoundException {
        return generateByteCode(bin_name, 0);
    }

    //! generates the byte code of a Java class for a Qore class, given by the class pointer that the module's native
    //! code passed to loadClassWithPtr(), or by its name (\a class_ptr = 0); not public: Java code can never supply
    //! a native class pointer (qore#6854)
    private byte[] generateByteCode(String bin_name, long class_ptr) throws ClassNotFoundException {
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
                // generating the bytecode can load a Qore module, whose initialization code can make Java calls
                // for other Programs: the thread's classloader context is restored when the call returns to Java
                // (qore#6551)
                Object[] ctx = saveContext();
                try {
                    rv = generateByteCode0(ptr, info.cls, bin_name, info.mod, info.python, info.kotlin, class_ptr);
                } finally {
                    restoreContext(ctx);
                }
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
            URI uri = fileentry.toURI();
            URL url = uri.toURL();
            if (url.getPath().endsWith(".jar")) {
                url = URI.create("jar:" + uri + "!/").toURL();
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

    //! Returns the canonical "system" <tt>QoreURLClassLoader</tt> (Globals::syscl in C++).
    /** Used as the fallback shared home for <tt>qore.Qore.*</tt> built-in classes and for
        module classes whose owning user Program cannot be resolved (binary modules,
        modules not yet loaded under their canonical name).
        <tt>qoremod.&lt;mod&gt;.*</tt> normally routes to the owning user module's Program loader
        via {@link #getModuleLoader0(String)} instead.

        Returns <tt>null</tt> during very early init before syscl exists.
    */
    static private native QoreURLClassLoader getSyscl0();

    //! Returns the {@link QoreURLClassLoader} of the named user module's owning Program.
    /** User modules (.qm files) live in a dedicated QoreProgram that is the canonical home
        for their %Qore classes — and thus for the corresponding <tt>qoremod.&lt;mod&gt;.*</tt>
        Java classes.  Routing every defineClass / loadClass for <tt>qoremod.&lt;mod&gt;.*</tt>
        through this single loader keeps Class identity consistent across consumer Programs.

        Returns <tt>null</tt> for unknown module names and for binary modules that don't
        have an owning user Program; callers fall back to the syscl loader.
    */
    static private native QoreURLClassLoader getModuleLoader0(String module_name);

    //! Returns the canonical {@link QoreURLClassLoader} for <tt>bin_name</tt>.
    /** The canonical loader is the {@link QoreURLClassLoader} of the QoreProgram that owns
        the underlying QoreClass.  Two name shapes are dispatched:
        <ul>
        <li><tt>qoremod.&lt;mod&gt;.&lt;X&gt;</tt> resolves to the user module's owning Program
        <li><tt>qore.&lt;X&gt;.&lt;Y&gt;...</tt> converts to qpath <tt>::&lt;X&gt;::&lt;Y&gt;::...</tt> and looks the
            class up in the calling loader's Program (which is presumed to have the
            relevant module loaded — that is how the Java reference reached us); routes to
            <tt>qc.getProgram()</tt> of the matching QoreClass.  Catches binary-module
            shadow classes such as <tt>qore.Qore.Logger.LoggerInterface</tt> and Qorus
            injections such as <tt>qore.OMQ.UserApi.AbstractUserApi</tt>.
        </ul>

        Returns <tt>null</tt> when no canonical owner can be determined; callers fall back
        to local generation.
    */
    private native QoreURLClassLoader getCanonicalLoader0(String bin_name);
}
