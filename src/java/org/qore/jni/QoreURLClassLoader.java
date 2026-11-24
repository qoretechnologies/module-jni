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

import java.net.JarURLConnection;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.security.CodeSource;
import java.security.cert.Certificate;

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

import java.util.ArrayDeque;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

    //! the paths that the class loader's own additions have recorded; guarded by this object
    private final HashSet<String> classPathElements = new HashSet<String>();
    //! the paths of the class loader's own additions, in order; guarded by this object
    private String classPath = new String();

    //! Program pointer for the associated Qore Program
    /** Using AtomicLong for thread-safe access to the program pointer.
        A value of 0 indicates no program or that the program has been released.

        A class loader created for the Program of its parent class loader (ex: the class loader of a QoreJavaCompiler,
        see createDerived()) shares the parent's holder, so that the pointer that it passes to native methods is
        cleared with the parent's when the Program's jni data is destroyed: a copy of the pointer would name the
        Program after it has been deleted.  It stops sharing it when its own pointer is set or cleared (see
        setPtrIntern()).
    */
    private volatile AtomicLong pgm_ptr = new AtomicLong(0);

    //! true once the Program of pgm_ptr has been deleted (clearProgramPtr()); shared and replaced with pgm_ptr
    /** A class loader whose Program has been deleted does not fall back to the global Java context Program, which no
        sandbox governs: getProgramPtr() throws ProgramDeletedException for it (qoretechnologies/qore#6845)
    */
    private volatile AtomicBoolean pgm_cleared = new AtomicBoolean(false);
    //! the first Program pointer of the class loader (or of the class loader that it was derived from), which is kept
    //! when the pointer is cleared: the Java code of its classes is attributed to that Program (qore#7023)
    private volatile long origin_ptr;

    //! true if the class loader has no Program of its own and takes that of its parent class loader
    /** Set only for class loaders made by createWithoutProgram() and those derived from them: any other class loader
        with no Program pointer is unknown (qore#7131)
    */
    private volatile boolean inherits_program = false;

    //! what the compiler copies of the Program use, and the live copies; derived class loaders keep it
    private volatile QoreCompilerClasspath.Budget copy_budget = new QoreCompilerClasspath.Budget();

    //! true if the end of this class loader's Program ends copy_budget; false for a derived class loader; guarded by
    //! ptr_lock
    private boolean owns_budget = true;

    //! the last non-zero Program pointer set with setPtrIntern(), or 0 if none has been; guarded by ptr_lock
    private long last_ptr;

    //! true while pgm_ptr is the holder of the parent class loader; guarded by ptr_lock
    private boolean shared_ptr = false;

    //! guards the replacement of pgm_ptr and shared_ptr
    private final Object ptr_lock = new Object();

    //! true if findClass() defines pending classes: a child class loader's delegation does not call loadClass(String)
    private volatile boolean find_pending = false;

    //! applies the policy of the code that added each classpath entry to the lookups below it (qore#7276)
    /** Null until code that a filesystem policy restricts adds an entry, so that no other class loader makes any check
        for a lookup; once set, it is never cleared
    */
    private volatile QoreClasspathGuard guard;

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

    //! true on a thread that adds a classpath for a caller whose sandbox restricts only the network
    private static final ThreadLocal<Boolean> locationChecked = new ThreadLocal<Boolean>();

    //! the transparent entries of the class loader, also before it has a guard; guarded by this object
    private final HashSet<String> transparentPaths = new HashSet<String>();

    //! true if the JDK's class loader, which reads the setting when it starts, follows entries that are not files
    private static final boolean CLASS_PATH_URL_CHECK_DISABLED = isClassPathUrlCheckDisabled();

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
        super("QoreURLClassLoader", new URL[]{}, checkNewLoader(parent));
        enable_cache = true;
        setContextProgram(this);
        //System.out.printf("QoreURLClassLoader(ClassLoader parent: %s) this: %x (pgm: %x)\n",
        //    (parent == null ? "null" : parent.getClass().getCanonicalName()), hashCode(), pgm_ptr);

        // startup / system classloader
        startup = true;

        // set classpath from system classpath
        String cp = System.getProperty("java.class.path");
        if (cp != null && !cp.isEmpty()) {
            addPathIntern(cp);
        }
    }

    //! constructor for using this class as the boot classloader for the module
    public QoreURLClassLoader() {
        super("QoreURLClassLoader", new URL[]{}, checkNewLoader(ClassLoader.getSystemClassLoader()));
        enable_cache = true;
        setContextProgram(this);
        setContext();
        //System.out.printf("QoreURLClassLoader() this: %x (pgm: %x)\n", hashCode(), pgm_ptr);
    }

    //! Refuses a new class loader to code that cannot reach the global Java context Program; returns \a parent
    /** Called for the argument of the base class constructor, so that no object exists when the creation is refused,
        and no method of a subclass runs (qore#7131)
    */
    private static ClassLoader checkNewLoader(ClassLoader parent) {
        checkNewLoader0(static_bootstrap);
        return parent;
    }

    //! Thrown when Java code calls into Qore through a class loader whose Qore Program has been deleted
    /** The call is not made in another Program, such as the global Java context Program
        (qoretechnologies/qore#6845)
    */
    public static class ProgramDeletedException extends RuntimeException {
        public ProgramDeletedException() {
            super("the Qore Program of the Java class loader has been deleted");
        }
    }

    //! Creates a class loader with no Qore Program, which becomes the current thread's context
    /** Calls into Qore through it are made in the Program context of the thread, or in the global Java context
        Program; a class loader for a Qore Program is created only by the jni module

        @param parent the parent class loader

        @return the new class loader
    */
    public static QoreURLClassLoader createWithoutProgram(ClassLoader parent) {
        checkProgramAccess0(0);
        QoreURLClassLoader loader = new QoreURLClassLoader(0, parent);
        loader.inherits_program = true;
        return loader;
    }

    //! constructor for using this class as the boot classloader for the module
    /** Only for the jni module: other Java code cannot create a class loader with a Program pointer of its choice
        (qoretechnologies/qore#6845)
    */
    QoreURLClassLoader(long p_ptr) {
        //super("QoreURLClassLoader", new URL[]{}, ClassLoader.getSystemClassLoader());
        super("QoreURLClassLoader", new URL[]{}, ClassLoader.getPlatformClassLoader());
        setContext();
        pgm_ptr.set(p_ptr);
        origin_ptr = p_ptr;
        //System.out.printf("QoreURLClassLoader(long p_ptr: %x) this: %x\n", p_ptr, hashCode());
    }

    //! constructor with a QoreProgram pointer and a parent
    /** The class loader has a copy of the pointer, which is not cleared when the Program is deleted; a class loader
        for the Program of its parent class loader is created with createDerived() instead
    */
    QoreURLClassLoader(long p_ptr, ClassLoader parent) {
        super("QoreURLClassLoader", new URL[]{}, parent);
        pgm_ptr.set(p_ptr);
        origin_ptr = p_ptr;
        // set the current classloader as the thread context classloader
        setContext();
        //System.out.printf("QoreURLClassLoader(long p_ptr: %x, ClassLoader parent: %s: %x) this: %x\n",
        //    p_ptr, (parent == null ? "null" : parent.getClass().getCanonicalName()),
        //    parent == null ? 0 : parent.hashCode(), hashCode());
    }

    //! constructor for createDerived(); \a derived only makes the signature distinct from the public constructors
    private QoreURLClassLoader(QoreURLClassLoader parent, boolean derived) {
        super("QoreURLClassLoader", new URL[]{}, parent);
        // the parent's holder is taken under its lock, so that it is not taken while the parent replaces it; this
        // class loader's lock is taken as well, as setPtrIntern() reads the flag under it
        synchronized (ptr_lock) {
            synchronized (parent.ptr_lock) {
                pgm_ptr = parent.pgm_ptr;
                pgm_cleared = parent.pgm_cleared;
                copy_budget = parent.copy_budget;
                last_ptr = parent.last_ptr;
                origin_ptr = parent.origin_ptr != 0L ? parent.origin_ptr : parent.pgm_ptr.get();
            }
            shared_ptr = true;
            owns_budget = false;
        }
        inherits_program = parent.inherits_program;
        // set the current classloader as the thread context classloader
        setContext();
    }

    //! Creates a class loader for the Program of \a parent, whose child it is (ex: the class loader of a compiler)
    /** The new class loader shares the Program pointer of \a parent, so that the pointer that it passes to native
        methods is cleared with the parent's when the Program's jni data is destroyed: a copy of the pointer (as
        <tt>new QoreURLClassLoader(parent.getPtr(), parent)</tt> makes) would still name the Program after it has been
        deleted.  Setting or clearing the pointer of the new class loader changes its own only.  Like the other
        constructors, the new class loader becomes the current thread's context.

        @param parent the parent class loader, whose Program the new class loader uses

        @return the new class loader
    */
    public static QoreURLClassLoader createDerived(QoreURLClassLoader parent) {
        parent.checkAccess();
        return new QoreURLClassLoader(parent, true);
    }

    //! Creates a class loader for the Program of the thread's context, for classes that are defined for that Program
    /** The new class loader is derived from the context with createDerived() and becomes the thread's context, so
        that the classes that it defines are attributed to that Program (qore#7131).

        @return the new class loader, or null if the thread has no context or the calling code cannot reach its
        Program, in which case the context is not changed and classes defined for the caller stay unattributed
    */
    public static QoreURLClassLoader createDerivedForContext() {
        QoreURLClassLoader context = current.get();
        if (context == null || !programReachable0(context.getOriginPtr())) {
            return null;
        }
        QoreURLClassLoader derived = createDerived(context);
        derived.find_pending = true;
        return derived;
    }

    //! constructor with a name and a parent
    public QoreURLClassLoader(String name, ClassLoader parent) {
        super(name, new URL[]{}, checkNewLoader(parent));
        enable_cache = true;
        setContextProgram(this);
        setContext();
        //System.out.printf("QoreURLClassLoader(name: '%s', ClassLoader parent: %s) this: %x (pgm: %x)\n",
        //    name, (parent == null ? "null" : parent.getClass().getCanonicalName()) + ")", hashCode(), pgm_ptr);
    }

    /**
     * Add a class name/JavaFileObject mapping
     *
     * @param qualifiedClassName the name
     * @param javaFile           the file associated with the name
     */
    void addIntern(final String qualifiedClassName, final QoreJavaFileObject javaFile) {
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
        checkAccess();
        //debugLog("QoreURLClassLoader.addPath(): file://" + path);
        if (path != null && QoreSandbox.restrictsFilesystem()) {
            addResolvedPath(QoreSandbox.boundClasspath(path));
            return;
        }
        URL url = new File(path).toURI().toURL();
        // a caller that a sandbox restricts makes no grant of the host: a policy bound to the entry stays
        if (QoreSandbox.restrictsNetwork()) {
            checkClasspathLocation(url);
            addEntryUrl(url, ENTRY_TRANSPARENT);
            return;
        }
        addEntryUrl(url, ENTRY_GRANT);
    }

    //! Adds a URL for a subclass; a sandbox filesystem policy checks its file, a network policy its location
    /** The class loader's own additions use addFileUrl()
    */
    @Override
    protected void addURL(URL url) {
        checkAccess();
        if (url != null && QoreSandbox.restrictsFilesystem()) {
            File file = urlToFile(url);
            if (file == null || file.getPath().indexOf(File.pathSeparatorChar) >= 0
                    || file.getPath().indexOf(';') >= 0) {
                throw new IllegalArgumentException("a class loader URL cannot be checked against the sandbox "
                    + "filesystem policy");
            }
            addResolvedPath(QoreSandbox.boundClasspath(file.getPath()));
            return;
        }
        if (url != null) {
            // a caller that a sandbox restricts makes no grant of the host: a policy bound to the entry stays
            if (QoreSandbox.restrictsNetwork()) {
                checkClasspathLocation(url);
                addEntryUrl(url, ENTRY_TRANSPARENT);
                return;
            }
            addEntryUrl(url, ENTRY_GRANT);
            return;
        }
        super.addURL(url);
    }

    //! an entry of code that a filesystem policy restricts, whose policy was bound before its URL is added
    private static final int ENTRY_BOUND = 0;
    //! an entry of code that no sandbox restricts for the filesystem or the network: a grant of the host
    private static final int ENTRY_GRANT = 1;
    //! a new entry of code that a sandbox restricts for the network but not the filesystem: transparent
    private static final int ENTRY_TRANSPARENT = 2;

    //! For tests only: called with the class loader at named points of an addition, with its monitor held; null
    //! otherwise
    /** The points: "duplicate-check", just before a path that the class loader records is checked for a duplicate;
        "before-add", between recording a transparent entry and adding a URL; "after-add", right after a URL is added
        and before a grant is marked
    */
    private static volatile java.util.function.BiConsumer<QoreURLClassLoader, String> entryStepHook;

    //! Calls the test hook, if any, at a point of an addition
    private void entryStep(String point) {
        java.util.function.BiConsumer<QoreURLClassLoader, String> hook = entryStepHook;
        if (hook != null) {
            hook.accept(this, point);
        }
    }

    //! Adds a URL, and records the kind of its entry, under this object's monitor, which every addition takes
    /** A transparent entry is recorded before its URL is added, as a bound entry is (QoreClasspathGuard.bind()), so
        that no lookup sees it as a grant of the host.  A grant is marked after its URL is added: a transparent record
        that a caller restricted only for the network made for the same path before, in an addition that ended first,
        is then replaced, so a grant of the host is never left transparent.  A transparent or bound addition of a path
        that is already on the classpath leaves its kind as it is; a grant of the host marks it a grant.
    */
    private void addEntryUrl(URL url, int kind) {
        File file = urlToFile(url);
        String path = file == null ? null : file.getPath();
        synchronized (this) {
            if (kind == ENTRY_TRANSPARENT && path != null && isNewEntry(path)) {
                noteTransparent(path);
            }
            entryStep("before-add");
            super.addURL(url);
            entryStep("after-add");
            if (kind == ENTRY_GRANT && path != null) {
                noteTrustedPath(path);
            }
        }
    }

    //! Adds the URL of a file that the class loader was given
    /** @param kind the kind of the entry; a URL whose location is checked on this thread is the entry of a caller that
        a sandbox restricts for the network, so a new one is transparent
    */
    private void addFileUrl(File file, int kind) {
        // createUrl() returns null for a path that cannot be a URL, which adds nothing
        URL url = createUrl(file);
        if (url == null) {
            return;
        }
        if (locationChecked.get() != null) {
            // outside the lock: it reads the manifests that the URL reaches
            checkClasspathLocation(url);
            kind = ENTRY_TRANSPARENT;
        }
        synchronized (this) {
            appendClassPath(file);
            addEntryUrl(url, kind);
        }
    }

    //! Adds a file of a classpath that the class loader records, once
    /** The duplicate check, the addition, the kind of the entry and the record of its path are one step under this
        object's monitor, which addEntryUrl() takes as well, so a concurrent addition of the same path by other code
        cannot come between them: a grant of the host marks the entry a grant also if it is already on the classpath.
        An entry whose addition raises an error is not recorded, so it is added again the next time.

        @param kind the kind of the entry; overridden as for addFileUrl()
        @param usable true if the file is a directory or an archive that the class loader opens
        @param log_unusable true to log a file that is not used
    */
    private void addFileOnce(File file, int kind, boolean usable, boolean log_unusable) {
        String path = file.getPath();
        URL url = usable ? createUrl(file) : null;
        if (locationChecked.get() != null) {
            kind = ENTRY_TRANSPARENT;
            if (url != null) {
                // outside the lock: it reads the manifests that the URL reaches
                checkClasspathLocation(url);
            }
        }
        synchronized (this) {
            entryStep("duplicate-check");
            if (classPathElements.contains(path)) {
                if (kind == ENTRY_GRANT) {
                    noteTrustedPath(path);
                }
                return;
            }
            if (url != null) {
                appendClassPath(file);
                addEntryUrl(url, kind);
            } else if (!usable && log_unusable) {
                // a usable file whose URL cannot be made was logged by createUrl()
                errorLog("ClassPath element '" + file + "' is not an existing directory and is not a file " +
                    "ending with '.zip' or '.jar'");
            }
            classPathElements.add(path);
        }
    }

    //! Returns true if no URL of the class loader names a path
    private boolean isNewEntry(String path) {
        for (URL u : super.getURLs()) {
            File f = urlToFile(u);
            if (f != null && f.getPath().equals(path)) {
                return false;
            }
        }
        return true;
    }

    //! Records a new entry of a caller that a sandbox restricts for the network but not the filesystem
    /** It binds no policy and makes no grant: a lookup below it applies the policies of the bound entries that enclose
        it (see QoreClasspathGuard)
    */
    private synchronized void noteTransparent(String path) {
        transparentPaths.add(path);
        if (guard != null) {
            guard.markTransparent(path);
        }
    }

    //! Records that code that no sandbox restricts added a path: its entry is a grant of the host
    private synchronized void noteTrustedPath(String path) {
        transparentPaths.remove(path);
        if (guard != null) {
            guard.markTrusted(path);
        }
    }

    //! Records that code that no sandbox restricts added a file: its entry is a grant of the host
    /** A caller whose sandbox restricts the network makes no grant, so a policy that filesystem-restricted code bound
        to the entry stays, also if the addition is refused or adds only part of a classpath; a new entry of such a
        caller simply has no filesystem policy.
    */
    private void noteTrustedFile(File file) {
        if (locationChecked.get() == null) {
            noteTrustedPath(file.getPath());
        }
    }

    //! Returns the guard of this class loader, making it if necessary, with the transparent entries added before it
    private synchronized QoreClasspathGuard getGuard() {
        if (guard == null) {
            QoreClasspathGuard g = new QoreClasspathGuard();
            for (String path : transparentPaths) {
                g.markTransparent(path);
            }
            guard = g;
        }
        return guard;
    }

    //! Returns the paths that a list of classpath files reaches, with the manifest Class-Path of each jar
    /** Each path is as the class loader's URLs name it.  A manifest that cannot be followed leaves its entries out, so
        that a lookup below them finds no known entry and is refused.  This includes every manifest \c Class-Path entry
        on a JDK version that has not been verified (see VERIFIED_CLASS_PATH_JDK_FEATURES): in a class loader that also
        has entries of restricted code, what the manifests of trusted jars name is then not found (fail closed).
    */
    static java.util.Set<String> reachableRoots(java.util.List<File> entries) {
        ResolvedClasspath rc = new ResolvedClasspath(0, false, false);
        HashSet<String> rv = new HashSet<String>();
        for (File entry : entries) {
            rv.add(entry.getPath());
            File real = realFile(entry);
            if (real == null) {
                continue;
            }
            if (Files.isRegularFile(real.toPath())) {
                rc.addJar(real, false);
            } else {
                rc.add(real, real, false, null);
            }
        }
        for (int i = 0; i < rc.size(); ++i) {
            rc.expand(i);
        }
        rv.addAll(rc.roots);
        return rv;
    }

    //! Returns the class of a name from this class loader's own classpath, applying the policy of each entry
    /** With no guard, this is the class loader's plain lookup.  A file that the policy denies is treated as a file
        that does not exist, so that a later entry of the classpath can provide the class.
    */
    private Class<?> findClasspathClass(String name) throws ClassNotFoundException {
        QoreClasspathGuard g = guard;
        if (g == null) {
            return super.findClass(name);
        }
        String path = name.replace('.', '/') + ".class";
        URL[] urls = super.getURLs();
        Enumeration<URL> found;
        try {
            found = super.findResources(path);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        boolean first = true;
        while (found.hasMoreElements()) {
            URL url = found.nextElement();
            if (g.allows(url, path, urls)) {
                // the first match is the file that the plain lookup reads
                return first ? super.findClass(name) : defineFromUrl(name, url);
            }
            first = false;
        }
        throw new ClassNotFoundException(name);
    }

    //! Defines a class from a URL of a later classpath entry, whose check has passed
    @SuppressWarnings("deprecation")
    private Class<?> defineFromUrl(String name, URL url) throws ClassNotFoundException {
        byte[] bytes;
        URL base;
        Manifest manifest = null;
        try {
            URLConnection connection = url.openConnection();
            // not cached, so that closing the stream closes the jar, which this class loader's close() cannot reach
            connection.setUseCaches(false);
            try (InputStream in = connection.getInputStream()) {
                bytes = in.readAllBytes();
                if (connection instanceof JarURLConnection) {
                    JarURLConnection jar = (JarURLConnection) connection;
                    base = jar.getJarFileURL();
                    manifest = jar.getManifest();
                } else {
                    // the base of the entry, for a URL of any protocol: one level up for each package of the class,
                    // resolved on the URL as it is encoded
                    StringBuilder up = new StringBuilder("./");
                    for (int i = name.indexOf('.'); i >= 0; i = name.indexOf('.', i + 1)) {
                        up.append("../");
                    }
                    base = new URL(url, up.toString());
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ClassNotFoundException(name, e);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String pkg = name.substring(0, dot);
            if (getDefinedPackage(pkg) == null) {
                try {
                    if (manifest != null) {
                        definePackage(pkg, manifest, base);
                    } else {
                        definePackage(pkg, null, null, null, null, null, null, null);
                    }
                } catch (IllegalArgumentException e) {
                    // defined concurrently
                }
            }
        }
        return defineClass(name, bytes, 0, bytes.length, new CodeSource(base, (Certificate[]) null));
    }

    //! Returns the first resource of this class loader's own classpath that the policy of its entry allows
    @Override
    public URL findResource(String name) {
        QoreClasspathGuard g = guard;
        if (g == null) {
            return super.findResource(name);
        }
        URL[] urls = super.getURLs();
        try {
            Enumeration<URL> found = super.findResources(name);
            while (found.hasMoreElements()) {
                URL url = found.nextElement();
                if (g.allows(url, name, urls)) {
                    return url;
                }
            }
        } catch (IOException e) {
            // no resource can be returned
        }
        return null;
    }

    //! Returns the resources of this class loader's own classpath that the policy of their entries allows
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        QoreClasspathGuard g = guard;
        if (g == null) {
            return super.findResources(name);
        }
        URL[] urls = super.getURLs();
        ArrayList<URL> rv = new ArrayList<URL>();
        Enumeration<URL> found = super.findResources(name);
        while (found.hasMoreElements()) {
            URL url = found.nextElement();
            if (g.allows(url, name, urls)) {
                rv.add(url);
            }
        }
        return Collections.enumeration(rv);
    }

    //! Returns true if a class loader of the chain has a classpath directory that restricted code added
    /** Public because the script engine class is defined by another class loader than this class
    */
    public static boolean hasGuardedDirectory(ClassLoader loader) {
        for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
            if (cl instanceof QoreURLClassLoader) {
                QoreClasspathGuard g = ((QoreURLClassLoader) cl).guard;
                if (g != null && g.hasDirectory()) {
                    return true;
                }
            }
        }
        return false;
    }

    //! Opens the classpath of one compilation for a restricted caller: this class loader and its parents
    /** Internal to the module, for its compilers; public only because they are in another package, and not an API for
        other code.  The classpath directories that restricted code added are replaced by copies of the files that their policies
        allow, in private directories; close the result when the compilation ends, also if it fails.  For code that
        cannot reach the Program of a class loader, the entries of restricted code of that class loader are left out and
        nothing is copied.

        @return the classpath, which the caller closes

        @throw IOException a copy cannot be made, is over a limit, or was ended; nothing is left
    */
    public final QoreCompilerClasspath openCompilerClasspath() throws IOException {
        QoreCompilerClasspath rv = new QoreCompilerClasspath();
        try {
            for (ClassLoader cl = this; cl != null; cl = cl.getParent()) {
                if (cl instanceof QoreURLClassLoader) {
                    ((QoreURLClassLoader) cl).addCompilerLocations(rv);
                }
            }
            // no copy is ready before all of them are, so that no location that is returned has been removed
            rv.ready();
        } catch (Throwable e) {
            // a failure of the cleanup is suppressed in the original error
            QoreCompilerClasspath.cleanup(e, rv::abort);
            throw e;
        }
        return rv;
    }

    //! Adds the locations of this class loader to a compiler classpath
    private void addCompilerLocations(QoreCompilerClasspath classpath) throws IOException {
        QoreClasspathGuard g = guard;
        java.util.List<File> locations = getCompilerLocations();
        if (g == null) {
            for (File location : locations) {
                classpath.addLocation(location);
            }
            return;
        }
        g.addCompilerLocations(classpath, locations, programReachable0(getOriginPtr()), copy_budget, super.getURLs());
    }

    //! Ends the compiler copies of this class loader, which then makes no new ones
    private void endCompilerCopies() {
        QoreClasspathGuard g = guard;
        if (g != null) {
            g.closeCopies();
        }
    }

    //! Runs an addition; for a caller whose sandbox restricts the network, each URL added has its location checked
    /** A caller whose sandbox restricts the network but not the filesystem takes the same path as a caller with no
        sandbox, with this one check.
    */
    private static void addLocationChecked(boolean check, Runnable add) {
        if (!check) {
            add.run();
            return;
        }
        Boolean previous = locationChecked.get();
        locationChecked.set(Boolean.TRUE);
        try {
            add.run();
        } finally {
            if (previous == null) {
                locationChecked.remove();
            } else {
                locationChecked.set(previous);
            }
        }
    }

    //! Returns true if the JDK's class loader follows manifest Class-Path entries that are not files
    private static boolean isClassPathUrlCheckDisabled() {
        String value = System.getProperty("jdk.net.URLClassPath.disableClassPathURLCheck");
        return value != null && (value.isEmpty() || value.equals("true"));
    }

    //! Checks that a URL and the manifest Class-Path entries that it reaches name no other location
    /** For a caller whose sandbox restricts the network but not the filesystem; nothing at another location is opened.
        An entry that the class loader ignores is not refused.  Every local entry is confirmed when it is checked: a
        directory, or a file URL in the form of a directory, has no manifest (a \c jar: URL in that form is refused, as
        the class loader opens it as an archive); a regular file is read, and one that is not
        an archive has no entries; any other path could become an archive that the class loader opens later, so it is
        refused.  This follows the resolution of the JDK's class loader, so a manifest Class-Path attribute is refused
        on a JDK version that has not been verified for it, as for a filesystem policy.  A file that is replaced after
        it has been checked is not covered.

        @throw JNI-SANDBOX-ERROR the URL or an entry names another location, or its location cannot be confirmed
    */
    @SuppressWarnings("deprecation")
    private static void checkClasspathLocation(URL url) {
        URL root = localFileUrl(url);
        if (root == null) {
            throw QoreSandbox.classpathLocationError();
        }
        boolean follows_non_file = CLASS_PATH_URL_CHECK_DISABLED || isClassPathUrlCheckDisabled();
        ArrayDeque<URL> pending = new ArrayDeque<URL>();
        HashSet<String> visited = new HashSet<String>();
        pending.add(root);
        visited.add(urlKey(root));
        int entries = 0;
        while (!pending.isEmpty()) {
            URL base = pending.poll();
            String classpath = manifestClassPath(base);
            if (classpath == null || !new StringTokenizer(classpath).hasMoreTokens()) {
                continue;
            }
            int feature = Runtime.version().feature();
            if (!isVerifiedClassPathJdk(feature)) {
                throw QoreSandbox.classpathLocationUncheckedError("a manifest Class-Path attribute on an unverified "
                    + "JDK version " + feature);
            }
            for (StringTokenizer st = new StringTokenizer(classpath); st.hasMoreTokens();) {
                String token = st.nextToken();
                if (++entries > MAX_MANIFEST_ENTRIES) {
                    throw QoreSandbox.classpathLocationUncheckedError("the manifests name more than "
                        + MAX_MANIFEST_ENTRIES + " entries");
                }
                URL entry;
                try {
                    entry = new URL(base, token);
                } catch (MalformedURLException e) {
                    continue;
                }
                if (!"file".equals(entry.getProtocol())) {
                    if (follows_non_file) {
                        throw QoreSandbox.classpathLocationError();
                    }
                    continue;
                }
                if (!hasLocalAuthority(entry)) {
                    throw QoreSandbox.classpathLocationError();
                }
                if (visited.add(urlKey(entry))) {
                    pending.add(entry);
                }
            }
        }
    }

    //! Returns the entries of a classpath as files, exactly as they were given
    static ArrayList<File> classpathFiles(String classpath) {
        ArrayList<File> rv = new ArrayList<File>();
        for (String pathentry : classpathEntries(classpath)) {
            rv.add(new File(pathentry));
        }
        return rv;
    }

    //! Checks the location of each entry of a classpath, as checkClasspathLocation() does for a URL
    /** For a classpath that is given to a compiler for a caller whose sandbox restricts the network but not the
        filesystem.  The entries are checked where they are given, without resolving symbolic links, as the compiler
        then opens them; a wildcard entry is checked as the archives that it selects.

        @throw JNI-SANDBOX-ERROR an entry names another location, or its location cannot be confirmed
    */
    static void checkClasspathLocations(String classpath) {
        checkClasspathLocations(classpath, false);
    }

    //! Checks the location of each entry of a classpath
    /** @param as_added true to skip the entries that addPathIntern() leaves out (one that does not exist, one that is
        neither a directory nor an archive, a wildcard in a directory name or in a directory that cannot be read), so
        that a classpath is refused for the same entries as when it is added; false for a compiler, which opens every
        entry that it is given

        @throw JNI-SANDBOX-ERROR an entry names another location, or its location cannot be confirmed
    */
    static void checkClasspathLocations(String classpath, boolean as_added) {
        for (String pathentry : classpathEntries(classpath)) {
            File entry = resolveClasspathEntry(pathentry);
            String pattern = entry.getName();
            File dir = entry.getParentFile();
            if (pattern.indexOf('*') == -1 || dir == null || dir.getName().indexOf('*') != -1) {
                if (as_added && (pattern.indexOf('*') != -1 || !isAddedEntry(entry))) {
                    continue;
                }
                checkFileLocation(entry);
                continue;
            }
            File[] files = dir.listFiles((d, name) -> wildcardMatches(pattern, name) && isLoadable(name));
            if (files == null) {
                if (as_added) {
                    continue;
                }
                throw QoreSandbox.classpathLocationUncheckedError("the directory of a wildcard cannot be read");
            }
            for (File f : files) {
                checkFileLocation(f);
            }
        }
    }

    //! Returns true if addPathIntern() adds an entry: as it does, by its canonical file
    private static boolean isAddedEntry(File entry) {
        File canonical;
        try {
            canonical = entry.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }
        return canonical.exists() && (canonical.isDirectory() || isLoadable(canonical.getName()));
    }

    //! Checks the location of a file as checkClasspathLocation() does for its URL
    private static void checkFileLocation(File file) {
        URL url;
        try {
            url = file.toURI().toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw QoreSandbox.classpathLocationUncheckedError("its path cannot be a URL");
        }
        checkClasspathLocation(url);
    }

    //! Returns the local file URL of a class loader URL, or null if it names another location
    /** @throw JNI-SANDBOX-ERROR a \c jar: URL names a path inside an archive, or names its archive in the form of a
        directory, which the class loader opens as an archive although the form of the path says that it has no manifest
    */
    @SuppressWarnings("deprecation")
    private static URL localFileUrl(URL url) {
        URL file = url;
        if ("jar".equals(url.getProtocol())) {
            String spec = url.getFile();
            if (!spec.endsWith("!/")) {
                throw QoreSandbox.classpathLocationUncheckedError("a jar URL that names a path inside an archive");
            }
            try {
                file = new URL(spec.substring(0, spec.length() - 2));
            } catch (MalformedURLException e) {
                return null;
            }
            if (file.getFile().endsWith("/")) {
                throw QoreSandbox.classpathLocationUncheckedError("a jar URL that names its archive in the form of a "
                    + "directory");
            }
        }
        return "file".equals(file.getProtocol()) && hasLocalAuthority(file) ? file : null;
    }

    //! Returns the manifest Class-Path attribute of what a local file URL names, or null if it can have none
    /** A path in the form of a directory, an existing directory and a regular file that is not an archive have none.
        A jar index is not read here: the JDK 21 and 25 builds that were tested have no jar index support to enable, and
        so no setting to read; this does not cover another build before Java 22 that follows a jar index.

        @throw JNI-SANDBOX-ERROR the path does not exist and is not in the form of a directory, or it cannot be read
    */
    private static String manifestClassPath(URL url) {
        String file = url.getFile();
        if (file.endsWith("/")) {
            return null;
        }
        String path = decodeUrlPath(file);
        if (path == null || path.indexOf('\0') != -1) {
            throw QoreSandbox.classpathLocationUncheckedError("its path cannot be decoded");
        }
        File archive = new File(path);
        java.nio.file.attribute.BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(archive.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
        } catch (IOException | RuntimeException e) {
            throw QoreSandbox.classpathLocationUncheckedError("it does not exist or cannot be read");
        }
        if (attrs.isDirectory()) {
            return null;
        }
        // not opened, as reading it may not return (ex: a FIFO)
        if (!attrs.isRegularFile()) {
            throw QoreSandbox.classpathLocationUncheckedError("it is not a regular file");
        }
        JarFile opened;
        try {
            opened = new JarFile(archive, true, ZipFile.OPEN_READ, JarFile.runtimeVersion());
        } catch (java.util.zip.ZipException e) {
            // not an archive, which the class loader cannot open either
            return null;
        } catch (IOException | RuntimeException e) {
            throw QoreSandbox.classpathLocationUncheckedError("it cannot be read");
        }
        try (JarFile jf = opened) {
            Manifest man = jf.getManifest();
            Attributes main = man == null ? null : man.getMainAttributes();
            return main == null ? null : main.getValue(Attributes.Name.CLASS_PATH);
        } catch (IOException | RuntimeException e) {
            throw QoreSandbox.classpathLocationUncheckedError("its manifest cannot be read");
        }
    }

    //! Returns the file that a class loader URL names, or null if it names no local file
    /** A \c jar: URL of this class loader is the URL of the file followed by \c "!/", and the file's own path can
        contain that sequence; the URL is decoded as a URI, so that the file is the one that the URL was made from.
    */
    public static File urlToFile(URL url) {
        String spec = url.toString();
        if (spec.startsWith("jar:")) {
            if (!spec.endsWith("!/")) {
                return null;
            }
            spec = spec.substring(4, spec.length() - 2);
        }
        if (!spec.startsWith("file:")) {
            return null;
        }
        try {
            return new File(new URI(spec));
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    //! Returns the files of this class loader's own classpath that a compiler may read for a restricted caller
    /** Taken from the class loader itself, so that a subclass cannot change them.  An archive with a manifest
        \c Class-Path attribute is left out, as compilers resolve its entries in their own way, not as the class loader
        whose check allowed them does.  The Java compiler still lists the classes of such a jar that the class loader
        has as a \c jar: URL; the classes of any other such archive are not available to a compiler for this caller.
    */
    public final java.util.List<File> getCompilerLocations() {
        ArrayList<File> rv = new ArrayList<File>();
        for (URL url : super.getURLs()) {
            File file = urlToFile(url);
            if (file != null && (file.isDirectory() || (file.isFile() && !hasManifestClassPath(file)))) {
                rv.add(file);
            }
        }
        return rv;
    }

    //! Returns true if an archive has a manifest Class-Path attribute, or cannot be read to tell
    private static boolean hasManifestClassPath(File file) {
        try (JarFile jar = new JarFile(file, false)) {
            Manifest manifest = jar.getManifest();
            return manifest != null && manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH) != null;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    //! adds byte code for an inner class to the byte code cache; requires a binary name (ex: \c my.package.MyClass$1)
    void addPendingClassIntern(String bin_name, byte[] byte_code) {
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
            rv = findClasspathClass(name);
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
        checkAccess();
        pendingClasses.clear();
    }

    //! Clears the pending classes cache (deprecated)
    /** @deprecated Use clearPendingClassesCache() instead for clarity
    */
    @Deprecated
    public void clearCache() {
        clearPendingClassesCache();
    }

    //! Clears the pending classes cache, for the jni module
    void clearCacheIntern() {
        pendingClasses.clear();
    }

    //! Clears all caches in this classloader
    /** This method clears:
        - pendingClasses: byte code for classes being injected
        - classCache: cached Class objects
        - classes: QoreJavaFileObject cache for compilation
        - classInProgress: classes currently being created in the current Java call chain
    */
    public void clearAllCaches() {
        checkAccess();
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
        @throws RuntimeException if the calling code is governed by a sandbox: the registry is
                                 process-wide, so it is refused for any class loader (qore#7131)
    */
    public void registerSharedDynamicClass(String bin_name) {
        checkProgramAccess0(0);
        registerSharedDynamicClassIntern(bin_name);
    }

    //! Registers a shared class for the jni module, whose module command checks the code that issues it
    void registerSharedDynamicClassIntern(String bin_name) {
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
        checkAccess();
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

        if (find_pending) {
            byte[] pending = pendingClasses.remove(bin_name);
            if (pending != null) {
                return defineClassIntern(bin_name, pending, 0, pending.length);
            }
        }

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
        rv = findClasspathClass(bin_name);
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
        // a dynamic class that the parent's Program has is not used for code that cannot reach that Program, as for
        // the lookup of loadClass() (parentProgramReachable(), qore#7015, qore#7023)
        if (parent instanceof QoreURLClassLoader && (!isDynamic(bin_name) || parentProgramReachable())) {
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
                        return target.defineClassUnconditionalIntern(bin_name, bytes);
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

        // a kotlin.* class is not generated in a Program that the calling code cannot reach: it is looked up as an
        // ordinary class instead (see kotlinProgramReachable(); qore#7015)
        if (isDynamic(bin_name) && kotlinProgramReachable(bin_name)) {
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

        // a dynamic class that this class loader's Program does not have is not looked up in a parent class loader
        // whose Program the calling code cannot reach (qore#7015): it is not found; a kotlin.* class is looked up in
        // the parent, which does not generate it in a Program that the calling code cannot reach either (see
        // kotlinProgramReachable()); this class loader's own classpath is still searched, as the parent delegation
        // below would after the parent
        if (isDynamic(bin_name) && !isKotlinName(bin_name) && !parentProgramReachable()) {
            try {
                // under the class loading lock, as super.loadClass() searches it
                synchronized (getClassLoadingLock(bin_name)) {
                    rv = findLoadedClass(bin_name);
                    return rv != null ? rv : findClasspathClass(bin_name);
                }
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundException(String.format("could not find a Qore source class to create Java "
                    + "class '%s'", bin_name), e);
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
                rv = findClasspathClass(bin_name);

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
                        return target.defineClassUnconditionalIntern(bin_name, bytes);
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

    //! Returns true if a dynamic class or namespace that this class loader's Program does not have can be looked up
    //! in the parent class loader
    /** The parent class loader of the class loader of a Program is the class loader of another Program (the global
        Java context Program, or the Program that the jni module was loaded in first).  Java code is not governed by a
        sandbox: for code that a sandbox governs, Java's calls into Qore are made only in the Program of the calling
        code (qoretechnologies/qore#6845), so the native methods that the parent would call with its Program's pointer
        to look up the class are refused.  The parent is then not asked at all: the class is not found, as the calling
        code cannot see the parent's Program, and the refusal of the parent's native calls, which is not a
        ClassNotFoundException, does not escape a lookup that the calling code did not direct at the parent's Program
        (ex: the optional base classes that QoreKotlinCompiler loads for its stubs; qoretechnologies/qore#7015).  An
        explicit call with the class loader of another Program is still refused.

        A parent class loader with no Program, or with the Program of this class loader (ex: the parent of the class
        loader of a compiler, see createDerived()), is asked as before; so is any parent for code that no sandbox
        governs.
    */
    private boolean parentProgramReachable() {
        ClassLoader parent = getParent();
        if (!(parent instanceof QoreURLClassLoader)) {
            return true;
        }
        long parent_ptr = ((QoreURLClassLoader) parent).pgm_ptr.get();
        if (parent_ptr == 0 || parent_ptr == pgm_ptr.get()) {
            return true;
        }
        return programReachable0(parent_ptr);
    }

    //! Returns true if \a bin_name is in the kotlin package, which ordinary classes share with dynamic classes
    private static boolean isKotlinName(String bin_name) {
        return bin_name.startsWith("kotlin.");
    }

    //! Returns false if \a bin_name is a kotlin.* class and the calling code cannot reach this class loader's Program
    /** Dynamic classes for the Kotlin language are in the kotlin package (see isDynamic()), which they share with the
        Kotlin standard library: a kotlin.* class is generated from the Program first, and is otherwise an ordinary
        class, found in the classpath.  The Kotlin compiler's classes are usually defined by the class loader of
        another Program than the one that compiles (ex: the Kotlin jars are on the classpath that every Program
        shares, whose class loader has the Program that the jni module was loaded in or the global Java context
        Program), and the JVM loads the standard library classes that they link to with that class loader.

        For code that a sandbox governs, the native methods called with the pointer of a Program other than the one
        of the calling code are refused (qoretechnologies/qore#6845): the generation of a kotlin.* class would be
        refused instead of failing, and the standard library class would not be found
        (qoretechnologies/qore#7015).  The class is then not generated in that Program at all, and is looked up as an
        ordinary class: a dynamic class of that Program is not found, as the calling code cannot see that Program.

        Only kotlin.* classes are looked up so; any other dynamic class (qore.*, qoremod.*, python.* etc), whose names
        are not shared with ordinary classes, is still generated with the Program's pointer, so that an explicit load
        through the class loader of another Program is refused.
    */
    private boolean kotlinProgramReachable(String bin_name) {
        if (!isKotlinName(bin_name)) {
            return true;
        }
        long ptr = pgm_ptr.get();
        if (ptr == 0) {
            return true;
        }
        return programReachable0(ptr);
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
                // the class is defined in the owner, which the calling code must be able to reach (qore#7131)
                shared_loader.checkAccess();
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

    Class<?> defineClassUnconditionalIntern(String name, byte[] byte_code) throws ClassFormatError {
        // create the class and return it
        return defineClassIntern(name, byte_code, 0, byte_code.length);
    }

    Class<?> defineResolveClassIntern(String name, byte[] b, int off, int len) throws ClassFormatError {
        QoreURLClassLoader shared_loader = getSharedDynamicClassLoader(name);
        if (shared_loader != null && shared_loader != this) {
            // the class is defined in the owner, which the calling code must be able to reach (qore#7131)
            shared_loader.checkAccess();
            return shared_loader.defineResolveClassIntern(name, b, off, len);
        }
        Class<?> rv = defineClassIntern(name, b, off, len);
        resolveClass(rv);
        return rv;
    }

    //! Returns the Program pointer of the class loader, or the first one that it had if it has been cleared
    /** For the attribution of the Java code of its classes (qore#7023)
    */
    final long getOriginPtr() {
        long ptr = pgm_ptr.get();
        return ptr != 0L ? ptr : origin_ptr;
    }

    //! Returns true if a class loader with no Program pointer takes the Program of its parent (qore#7131)
    final boolean inheritsProgram() {
        return inherits_program;
    }

    //! Refuses an operation on this class loader to code that cannot reach its Program (qore#7023)
    /** The Program pointer of a class loader names the Program that Java code of its classes runs for: code that a
        sandbox governs cannot change another Program's class loader (its classpath, its classes, its pointer), or
        create a class loader for another Program
    */
    void checkAccess() {
        checkProgramAccess0(getOriginPtr());
    }

    //! Throws the refusal of the jni module if the calling code cannot reach the Program \a ptr (qore#7023)
    static native void checkProgramAccess0(long ptr);

    //! Refuses a new class loader to code that cannot reach the global Java context Program (qore#7131)
    private static native void checkNewLoader0(boolean bootstrap);

    //! Checks that the calling code can reach the Program \a ptr, unless the module's shutdown has started
    /** The check is serialized with the start of the module's shutdown, so that it uses no Qore API afterwards.

        @return true if the calling code can reach the Program; false if the module's shutdown has started

        @throws RuntimeException the calling code cannot reach the Program
    */
    private static native boolean checkAccessUnlessShutDown0(long ptr);

    public void add(final String qualifiedClassName, final QoreJavaFileObject javaFile) {
        checkAccess();
        addIntern(qualifiedClassName, javaFile);
    }

    public void addPendingClass(String bin_name, byte[] byte_code) {
        checkAccess();
        addPendingClassIntern(bin_name, byte_code);
    }

    public Class<?> defineClassUnconditional(String name, byte[] byte_code) throws ClassFormatError {
        checkAccess();
        return defineClassUnconditionalIntern(name, byte_code);
    }

    public Class<?> defineResolveClass(String name, byte[] b, int off, int len) throws ClassFormatError {
        checkAccess();
        return defineResolveClassIntern(name, b, off, len);
    }

    public void clearProgramPtr() {
        checkAccess();
        clearProgramPtrIntern();
    }

    //! Adds a path to the classpath of the parent class loader; see addParentPathIntern()
    /** The access check is made for the class loader whose classpath changes (qore#7131)
    */
    public void addParentPath(String classpath) {
        ClassLoader parent = getParent();
        if (parent instanceof QoreURLClassLoader) {
            ((QoreURLClassLoader)parent).checkAccess();
        } else {
            checkAccess();
        }
        if (classpath != null && QoreSandbox.restrictsFilesystem()) {
            // resolved once: the files that are added are the ones that were checked
            QoreURLClassLoader target = parent instanceof QoreURLClassLoader ? (QoreURLClassLoader)parent : this;
            target.addResolvedPath(QoreSandbox.boundClasspath(classpath));
            return;
        }
        addLocationChecked(classpath != null && QoreSandbox.restrictsNetwork(),
            () -> addParentPathIntern(classpath));
    }

    //! Closes the class loader, for code that can reach its Program (qore#7131)
    /** Once the module's shutdown has started, the access of the calling code can no longer be checked: the class
        loader is then left open, for any caller, and its resources are released when the process exits
    */
    @Override
    public void close() throws IOException {
        if (checkAccessUnlessShutDown0(getOriginPtr())) {
            // a failure to end a copy never replaces a failure to close
            Throwable failure = null;
            try {
                super.close();
            } catch (IOException | RuntimeException | Error e) {
                failure = e;
                throw e;
            } finally {
                QoreCompilerClasspath.cleanup(failure, this::endCompilerCopies);
            }
        }
    }

    //! Closes the class loader and clears its caches and Program pointer, for the compiler that owns it
    /** After the access check, the caches and the pointer are always cleared, also if closing the JAR files fails.

        @return true if the class loader was closed; false if the module's shutdown has started, in which case nothing
        is changed (see close())

        @throws RuntimeException the calling code cannot reach the class loader's Program; nothing is changed
    */
    public boolean closeAndClear() {
        if (!checkAccessUnlessShutDown0(getOriginPtr())) {
            return false;
        }
        Throwable failure = null;
        try {
            super.close();
        } catch (IOException e) {
            // every JAR file has been tried; the class loader is being discarded, and there is no recovery action
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            pendingClasses.clear();
            classCache.clear();
            classes.clear();
            classInProgress.get().clear();
            QoreCompilerClasspath.cleanup(failure, this::clearProgramPtrIntern);
        }
        return true;
    }

    //! Sets the default assertion status, for code that can reach the class loader's Program (qore#7131)
    @Override
    public void setDefaultAssertionStatus(boolean enabled) {
        checkAccess();
        super.setDefaultAssertionStatus(enabled);
    }

    //! Sets a package's assertion status, for code that can reach the class loader's Program (qore#7131)
    @Override
    public void setPackageAssertionStatus(String packageName, boolean enabled) {
        checkAccess();
        super.setPackageAssertionStatus(packageName, enabled);
    }

    //! Sets a class's assertion status, for code that can reach the class loader's Program (qore#7131)
    @Override
    public void setClassAssertionStatus(String className, boolean enabled) {
        checkAccess();
        super.setClassAssertionStatus(className, enabled);
    }

    //! Clears the assertion status settings, for code that can reach the class loader's Program (qore#7131)
    @Override
    public void clearAssertionStatus() {
        checkAccess();
        super.clearAssertionStatus();
    }

    //! Adds a classpath; a sandbox filesystem policy checks every file it reaches, a network policy their locations
    public void addPath(String classpath) {
        checkAccess();
        if (classpath != null && QoreSandbox.restrictsFilesystem()) {
            // resolved once: the files that are added are the ones that were checked
            addResolvedPath(QoreSandbox.boundClasspath(classpath));
            return;
        }
        addLocationChecked(classpath != null && QoreSandbox.restrictsNetwork(), () -> addPathIntern(classpath));
    }

    //! Adds the files of a directory that match a pattern; checked as addPath() checks a classpath
    public void addWildcard(File dir, String nam) {
        checkAccess();
        if (QoreSandbox.restrictsFilesystem()) {
            addResolvedPath(QoreSandbox.boundClasspath(new File(dir, nam).getPath()));
            return;
        }
        addLocationChecked(QoreSandbox.restrictsNetwork(), () -> addWildcardIntern(dir, nam));
    }

    //! Returns the program pointer
    /** @return the program pointer value, or 0 if not set or cleared
        @note The returned value may be 0 if the program has been released; use getValidPtr() if you need
        to ensure the pointer is valid
    */
    public long getPtr() {
        return getPtrIntern();
    }

    //! Returns the program pointer, for the jni module: a subclass cannot override it, so that no code of a subclass
    //! runs in the module's native methods (qore#7131)
    private long getPtrIntern() {
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
    void clearProgramPtrIntern() {
        // the class loader that owns the Program's budget ends the copies of the Program, also those of the class
        // loaders derived from it; a derived class loader ends only its own, and keeps the budget
        QoreCompilerClasspath.Budget program_budget = null;
        synchronized (ptr_lock) {
            if (owns_budget) {
                program_budget = copy_budget;
            }
        }
        // every step runs also if an earlier one fails; the first failure is raised at the end, with the others
        // suppressed in it
        Throwable failure = null;
        try {
            endCompilerCopies();
        } catch (RuntimeException | Error e) {
            failure = e;
        }
        if (program_budget != null) {
            try {
                program_budget.closeAll();
            } catch (RuntimeException | Error e) {
                failure = QoreCompilerClasspath.combine(failure, e);
            }
        }
        try {
            setPtrIntern(0, true);
        } catch (RuntimeException | Error e) {
            failure = QoreCompilerClasspath.combine(failure, e);
        }
        try {
            sharedDynamicClassLoaders.entrySet().removeIf(entry -> entry.getValue() == this);
        } catch (RuntimeException | Error e) {
            failure = QoreCompilerClasspath.combine(failure, e);
        }
        QoreCompilerClasspath.rethrow(failure);
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
        if (cl.pgm_cleared.get()) {
            throw new ProgramDeletedException();
        }
        return cl.pgm_ptr.get();
    }

    //! Sets the program pointer for the current thread's classloader context
    /** @param ptr the program pointer value to set
        @throws IllegalStateException if no classloader context is set for this thread
    */
    static void setProgramPtr(long ptr) {
        QoreURLClassLoader cl = current.get();
        if (cl == null) {
            throw new IllegalStateException("No classloader context set for current thread");
        }
        cl.setPtrIntern(ptr, false);
    }

    //! Sets the Program pointer of this class loader only
    /** A holder shared with the parent class loader is replaced, so that the parent's pointer is not changed; the
        pointer of a class loader whose holder is shared by the class loaders created with its pointer is set for them
        too (it is set to 0 when its Program's jni data is destroyed, which is what they must see)
    */
    private void setPtrIntern(long ptr, boolean cleared) {
        synchronized (ptr_lock) {
            // the Program that the budget belongs to: the last one that this class loader had
            long budget_program = last_ptr != 0L ? last_ptr : origin_ptr;
            if (origin_ptr == 0L && ptr != 0L) {
                origin_ptr = ptr;
            }
            if (shared_ptr) {
                pgm_ptr = new AtomicLong(ptr);
                pgm_cleared = new AtomicBoolean(cleared);
                shared_ptr = false;
            } else {
                pgm_ptr.set(ptr);
                pgm_cleared.set(cleared);
            }
            // only a new Program has a budget of its own: clearing the pointer keeps the budget of the Program
            if (ptr != 0L) {
                if (ptr != budget_program || copy_budget.isClosed()) {
                    copy_budget = new QoreCompilerClasspath.Budget();
                    owns_budget = true;
                }
                last_ptr = ptr;
            }
        }
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
    void addParentPathIntern(String classpath) {
        // also reached by the global module commands: for a caller that a network policy restricts, the location of
        // each URL is checked, as on the other routes, and no grant of the host is made
        boolean network = QoreSandbox.restrictsNetwork();
        if (network && classpath != null) {
            // every entry first, so that a classpath that is refused adds nothing, as for add-classpath
            checkClasspathLocations(classpath, true);
        }
        addLocationChecked(network, () -> addToParentPath(classpath));
    }

    //! Adds a classpath to the parent class loader, or to this one if it has no parent of this class
    private void addToParentPath(String classpath) {
        ClassLoader parent = getParent();
        if (parent instanceof QoreURLClassLoader) {
            ((QoreURLClassLoader)parent).addPathIntern(classpath);
        } else {
            addPathIntern(classpath);
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
        thrown to the caller (qore#6779).  A classpath of a caller that a filesystem policy restricts, or that a
        module command adds under a sandbox, is not added here, but with resolveClasspath() and addResolvedPath(), so
        that every file that is added is one that the policy checked (qore#6818).  A caller whose sandbox restricts
        only the network adds its classpath here, with the location of each URL checked.
    */
    void addPathIntern(String classpath) {
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
            if (basename != null && !basename.isEmpty()) {
                // the pattern is recorded once its files have been added
                synchronized (this) {
                    if (classPathElements.contains(fileentry.getPath())) {
                        continue;
                    }
                }
                addWildcardIntern(fileentry.getParentFile(), basename);
                synchronized (this) {
                    classPathElements.add(fileentry.getPath());
                }
                continue;
            }
            // added by code that no sandbox restricts: the entry is a grant of the host, also if restricted code added
            // it before; a caller that a network policy restricts makes no grant (see addFileOnce())
            // an entry that does not exist (it should not, after getCanonicalFile()) is recorded with no message
            boolean exists = fileentry.exists();
            addFileOnce(fileentry, ENTRY_GRANT, exists && (fileentry.isDirectory() || isLoadable(fileentry.getName())),
                exists);
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
        verified for these feature versions, on their first and latest updates at the time: the parsing and resolution
        of the attribute by the class loader and by the compiler's file manager was compared, and the files that each
        reaches were compared with the files that this check resolves, for relative, absolute, directory, link, encoded
        and URL entries and nested manifests.  The versions differ in how a malformed entry fails, which this check
        refuses on all of them, and in whether a file URL with another host can be opened over the network, so such
        an entry is refused on all of them for a caller whose filesystem or network access is restricted
        (qore#7195).  On any other version, a governed classpath with a jar that has a manifest Class-Path attribute
        is refused (qore#6822).
    */
    static final int[] VERIFIED_CLASS_PATH_JDK_FEATURES = {21, 25};

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
        symbolic links resolved.  So every jar and directory that the class loader opens through a manifest is one
        that was checked, and the classpath that is used is the one that was given; the files below a directory are
        not checked one by one.
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
        //! for each element: the entry of the classpath, as the caller gave it, that the element comes from
        private final ArrayList<String> givens = new ArrayList<String>();
        //! the entry of the classpath that the elements being added come from
        private String currentGiven;
        //! true once an element refuses the classpath
        private boolean refused;
        //! true if a manifest Class-Path entry can resolve differently as a path than as a URL
        private boolean ambiguousManifestEntry;
        //! the JDK feature version whose class loader has not been verified for manifest Class-Path checking: the
        //! running JDK's, or else a version assumed as well; 0 if both have been verified
        private final int unverifiedFeature;
        //! true if the policy restricts filesystem access, so that a manifest entry must have an exact path
        private final boolean restricted;
        //! true if the policy restricts network access, so that a manifest entry cannot name another location
        private final boolean networkRestricted;
        //! the policies that apply to each lookup below the entries of a restricted classpath
        private QoreSandbox.ClasspathPolicy[] policies;
        //! every path that an element names, as the class loader's URLs name it
        private final HashSet<String> roots = new HashSet<String>();

        //! Makes a resolved classpath for the running JDK, or also assuming another JDK feature version (> 0)
        /** A version assumed instead (only a debug build of the module passes one, for its tests) can only make the
            check stricter: manifest Class-Path attributes are checked only if the running JDK's version has been
            verified as well.

            @param assumedFeature the JDK feature version to assume as well, or 0
            @param restricted true if the policy restricts filesystem access
            @param networkRestricted true if the policy restricts network access
        */
        private ResolvedClasspath(int assumedFeature, boolean restricted, boolean networkRestricted) {
            this.restricted = restricted;
            this.networkRestricted = networkRestricted;
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
            givens.add(currentGiven);
            roots.add(path.getPath());
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
        /** The class loader opens such a file (ex: a device) as a jar if it has an archive name, and a compiler can
            open one with any name, so its manifest would have to be read to check it, but reading it is not safe (ex:
            a FIFO blocks).
        */
        private void refuseSpecial(File real, String referrer) {
            refuse(real, referrer, "the classpath jar '" + real + "' is not a regular file, so the jars that its "
                + "manifest Class-Path attribute could name cannot be checked against the sandbox policy");
        }

        //! Returns the number of elements
        int size() {
            return paths.size();
        }

        //! Returns the directories whose files the class loader reads: those that are added or that a manifest names
        /** The directory of a wildcard is only listed, and is not one of them
        */
        ArrayList<String> directories() {
            ArrayList<String> rv = new ArrayList<String>();
            for (int i = 0; i < paths.size(); ++i) {
                if ((files.get(i) != null || referrers.get(i) != null) && Files.isDirectory(paths.get(i).toPath())) {
                    rv.add(paths.get(i).getPath());
                }
            }
            return rv;
        }

        //! Binds the policies of the code that adds this classpath; called once
        void bindPolicies(QoreSandbox.ClasspathPolicy[] bound) {
            if (policies != null) {
                throw new IllegalStateException("the classpath already has a policy");
            }
            policies = bound;
        }

        //! Called by the module: takes the reference to the policy manager \a ptr, unless an exception is raised
        void bindPolicy(long ptr) {
            if (policies != null) {
                throw new IllegalStateException("the classpath already has a policy");
            }
            QoreSandbox.ClasspathPolicy[] bound = new QoreSandbox.ClasspathPolicy[1];
            bound[0] = new QoreSandbox.ClasspathPolicy(ptr);
            policies = bound;
        }

        //! Returns the entry of the classpath, exactly as the caller gave it, that an element comes from
        /** The only path that a denial names: the element itself can be a resolved path, the target of a link, a file
            that a wildcard selects or a file that a manifest names, which the caller may not be allowed to know
        */
        String givenEntry(int i) {
            return givens.get(i);
        }

        //! Returns the files that the classpath adds, in order, with symbolic links resolved
        java.util.List<File> addedFiles() {
            ArrayList<File> rv = new ArrayList<File>();
            for (File f : files) {
                if (f != null) {
                    rv.add(f);
                }
            }
            return rv;
        }

        //! Returns true if a manifest Class-Path entry can resolve differently as a path than as a URL
        boolean hasAmbiguousManifestEntry() {
            return ambiguousManifestEntry;
        }

        //! Returns true if an entry has URL syntax (also an authority), or a dot segment, which a URL removes without
        //! following links
        private static boolean isAmbiguousEntry(String entry) {
            if (entry.startsWith("//") || entry.indexOf('%') >= 0 || entry.indexOf(':') >= 0
                || entry.indexOf('#') >= 0 || entry.indexOf('?') >= 0 || entry.indexOf('\\') >= 0) {
                return true;
            }
            for (String segment : entry.split("/", -1)) {
                if (segment.equals(".") || segment.equals("..")) {
                    return true;
                }
            }
            return false;
        }

        //! Returns the path that is checked for an element
        File path(int i) {
            return paths.get(i);
        }

        //! Returns the path that is checked for an element as the bytes that the JVM uses for it
        /** For a path that the encoding cannot represent, the nearest parent directory that it can: the policy is
            applied to that, and the path is refused only after it has been allowed (see QoreSandbox.expandClasspath())
        */
        byte[] encodedPath(int i) {
            while (encoded.size() <= i) {
                encoded.add(QoreSandbox.encodeNearest(paths.get(encoded.size())));
            }
            return encoded.get(i);
        }

        //! Returns true if the classpath adds a file that is not a directory and whose manifest was not checked
        /** The class loader does not open such a file as an archive, but a compiler can; a file that a wildcard
            selected through a link with an archive name was opened as a jar, and its manifest was checked
        */
        boolean hasUnexpandedFile() {
            for (int i = 0; i < files.size(); ++i) {
                File f = files.get(i);
                if (f != null && jarUrls.get(i) == null && !Files.isDirectory(f.toPath())) {
                    return true;
                }
            }
            return false;
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
                    + "jars that its manifest Class-Path attribute names against the sandbox policy");
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
            // what a manifest names comes from the entry that the jar comes from
            currentGiven = givens.get(i);
            URL base = jarUrls.get(i);
            // once the classpath is refused, nothing more is read: the check raises the refusal when it reaches it
            if (base == null || refused) {
                return;
            }
            File jar = paths.get(i);
            String classpath;
            try (JarFile jf = new JarFile(jar, true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                // the JDK 21 builds that were tested have no jar index support to enable; an index is still refused
                // below Java 22 as a defense for other builds of those versions, as this check does not resolve the
                // jars that an index lists
                if (Runtime.version().feature() < 22 && jf.getEntry("META-INF/INDEX.LIST") != null) {
                    refuse(jar, null, "the classpath jar '" + jar + "' has a jar index, whose jars cannot be checked "
                        + "against the sandbox policy");
                    return;
                }
                Manifest man = jf.getManifest();
                Attributes attrs = man == null ? null : man.getMainAttributes();
                classpath = attrs == null ? null : attrs.getValue(Attributes.Name.CLASS_PATH);
            } catch (IOException | RuntimeException e) {
                refuse(jar, null, "cannot read the manifest of the classpath jar '" + jar + "' to check the jars that "
                    + "its Class-Path attribute names against the sandbox policy: " + e);
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
                    + "be checked against the sandbox policy on an unverified JDK version "
                    + unverifiedFeature + " for manifest Class-Path checking (verified: "
                    + Arrays.toString(VERIFIED_CLASS_PATH_JDK_FEATURES) + ")");
                return;
            }
            // the entries are separated by white space, as the class loader splits them
            for (StringTokenizer st = new StringTokenizer(classpath); st.hasMoreTokens();) {
                String entry = st.nextToken();
                if (++manifestEntries > MAX_MANIFEST_ENTRIES) {
                    refuse(jar, jar.getPath(), "the manifest Class-Path attributes of the classpath name more than "
                        + MAX_MANIFEST_ENTRIES + " entries, which cannot be checked against the sandbox "
                        + "policy");
                    return;
                }
                addManifestEntry(base, jar, entry);
                if (refused) {
                    return;
                }
            }
        }

        //! Returns true if a manifest Class-Path entry can be used under the policy of the caller
        /** A caller whose filesystem access is restricted needs an exact path; a caller with only its network
            restricted cannot use an entry that names another location; a caller with neither restricted is not
            affected.
        */
        private boolean isCheckableEntry(URL url) {
            if (restricted) {
                return isExactFileUrl(url);
            }
            return !networkRestricted || hasLocalAuthority(url);
        }

        //! Adds an entry of the manifest Class-Path of a jar, as the path that the class loader opens for it
        @SuppressWarnings("deprecation")
        private void addManifestEntry(URL base, File jar, String entry) {
            ambiguousManifestEntry = ambiguousManifestEntry || isAmbiguousEntry(entry);
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
                    + url + "', which is not a file, so it cannot be checked against the sandbox policy");
                return;
            }
            // checked before an entry that has been seen is skipped: a URL with a host is not a path for a compiler
            String entry_host = url.getHost();
            ambiguousManifestEntry = ambiguousManifestEntry || (entry_host != null && !entry_host.isEmpty());
            // before the URL is resolved to a path, on every JDK
            if (!isCheckableEntry(url)) {
                refuse(jar, referrer, "the manifest Class-Path entry of the classpath jar '" + jar + "' names "
                    + (restricted ? "a URL whose path cannot be checked exactly against the sandbox policy"
                        : "another location, which a sandbox that restricts network access does not allow"));
                return;
            }
            String key = urlKey(url);
            if (seen.contains(key)) {
                return;
            }
            seen.add(key);
            String file = url.getFile();
            boolean dir = file.endsWith("/");
            // the class loader opens a jar URL with a host only if the host is the local one
            if (!dir && entry_host != null && !entry_host.isEmpty() && !entry_host.equalsIgnoreCase("localhost")) {
                return;
            }
            String path = decodeUrlPath(file);
            if (path == null || path.indexOf('\0') != -1 || !new File(path).isAbsolute()) {
                refuse(jar, referrer, "the manifest Class-Path attribute of the classpath jar '" + jar + "' names '"
                    + url + "', which cannot be resolved to a file to check against the sandbox policy");
                return;
            }
            File f = new File(path);
            // the class loader's URLs name the entry by this path, which can differ from its real path
            roots.add(f.getPath());
            File real = realFile(f);
            if (real == null) {
                // as for an entry of the classpath, a file that does not exist is checked as it is named
                add(f, null, false, null, null, referrer, null);
                return;
            }
            // an entry with the form of a directory that is not one: the class loader does not open it as an archive,
            // so its manifest is not checked, but a compiler can
            ambiguousManifestEntry = ambiguousManifestEntry || (dir && !Files.isDirectory(real.toPath()));
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

    //! Returns true if the path that is checked for a manifest Class-Path entry is exactly the path that is read
    /** A manifest Class-Path entry whose path cannot be checked exactly is refused for a caller that a filesystem
        policy restricts (qore#7349).
    */
    static boolean isExactFileUrl(URL url) {
        if (url.getQuery() != null || url.getRef() != null) {
            return false;
        }
        if (!hasLocalAuthority(url)) {
            return false;
        }
        String path = decodeUrlPath(url.getPath());
        if (path == null) {
            return false;
        }
        for (String segment : path.replace(File.separatorChar, '/').split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    //! Returns true if a URL has no authority, or only the local host
    private static boolean hasLocalAuthority(URL url) {
        String authority = url.getAuthority();
        return authority == null || authority.isEmpty() || authority.equalsIgnoreCase("localhost");
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
        @param restricted true if a filesystem policy restricts the caller: a special file is then refused whatever
        its name, otherwise one without an archive name is kept as an element, which the class loader does not open,
        and a manifest Class-Path entry whose path cannot be checked exactly is refused (qore#7349)
        @param networkRestricted true if a network policy restricts the caller: a manifest Class-Path entry that
        names another location is refused (qore#7195)
    */
    static ResolvedClasspath resolveClasspath(String classpath, int jdkFeature, boolean restricted,
            boolean networkRestricted) {
        if (classpath == null) {
            throw new IllegalArgumentException("the classpath must not be null");
        }
        ResolvedClasspath rc = new ResolvedClasspath(jdkFeature, restricted, networkRestricted);
        for (String pathentry : classpathEntries(classpath)) {
            rc.currentGiven = pathentry;
            File fileentry = resolveClasspathEntry(pathentry);
            if (fileentry.getName().indexOf('*') == -1) {
                File real = realFile(fileentry);
                if (real == null) {
                    // as addPath() does, an entry that does not exist is left out with no message
                    rc.add(fileentry, null, false, null);
                } else if (Files.isRegularFile(real.toPath()) && isLoadable(real.getName())) {
                    rc.addJar(real, false);
                } else if ((restricted || isLoadable(real.getName())) && isSpecialFile(real)) {
                    // for a restricted caller, whatever its name or the name of a link to it: it is not opened
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
                // a match that cannot be resolved (ex: a dangling symbolic link) cannot be read, and is not added; it
                // is checked as it is named, as for an entry that does not exist, so that the result of the check
                // does not depend on whether the target of a link exists
                File real = realFile(f);
                if (real == null) {
                    rc.add(f, null, true, null);
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
        // before any URL is added, so that no lookup can reach an entry without its policy; a classpath of restricted
        // code with no policy is refused, as an entry with none is not checked
        if (rc.restricted) {
            if (rc.policies == null || rc.policies.length == 0) {
                throw new IllegalStateException("a classpath of code that a sandbox filesystem policy restricts has "
                    + "no policy");
            }
            getGuard().bind(rc.roots, rc.directories(), rc.policies);
        }
        for (int i = 0; i < rc.size(); ++i) {
            String note = rc.notes.get(i);
            if (note != null) {
                errorLog(note);
            }
            File fileentry = rc.files.get(i);
            if (fileentry == null) {
                continue;
            }
            // with a sandbox that restricts neither the filesystem nor the network now: a grant, as for trusted code, to
            // which a policy set later does not apply
            addFileOnce(fileentry, rc.restricted ? ENTRY_BOUND : rc.networkRestricted ? ENTRY_TRANSPARENT : ENTRY_GRANT,
                rc.isWildcardMatch(i) || fileentry.isDirectory() || isLoadable(fileentry.getName()), true);
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

        // the namespace is not looked up in a parent class loader whose Program the calling code cannot reach
        // (qore#7015): it has no classes
        if (rv.size() == 0 && parentProgramReachable()) {
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

    static void errorLog(String msg) {
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
    void addWildcardIntern(File dir, String nam) {
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
                noteTrustedFile(f);
                addFileUrl(f, ENTRY_GRANT);
            }
        }
    }

    //! Clears the compilation cache after compiling
    /** This method clears the classCache and calls the native clearCompilationCache0
        to clear native-side caches.
        @note This method requires a valid program pointer.
    */
    public void clearCompilationCache() {
        // the access is checked and the native caches are cleared together; once the module's shutdown has started,
        // the native caches are gone with the module, and the access can no longer be checked
        // the pointer is read once, so that the check is made for the Program whose cache is cleared
        long ptr = pgm_ptr.get();
        if (!clearCompilationCache0(ptr != 0L ? ptr : origin_ptr, ptr)) {
            return;
        }
        classCache.clear();
        classes.clear();
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
            return url;
        } catch (MalformedURLException thr) {
            errorLog("classpath element '" + fileentry + "' could not be used to create a valid file system URL (" +
                thr + ")");
            return null;
        }
    }

    //! Appends a path to the list of the class loader's own additions; called with this object's monitor held
    private void appendClassPath(File file) {
        if (classPath.length() > 0) {
            classPath += File.pathSeparator;
        }
        classPath += file.getPath();
    }

    private void setContextProgram(QoreURLClassLoader new_syscl) {
        BooleanWrapper created = new BooleanWrapper();
        long ptr = getContextProgram0(this, created, static_bootstrap);
        pgm_ptr.set(ptr);
        origin_ptr = ptr;

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
    // instance methods, as the native method checks that the Program pointer is still this class loader's
    private native void getClassesInNamespace0(long ptr, String packageName, String mod, boolean python,
        boolean kotlin, ArrayList<String> result);
    private native void getInternalClassesForPackage0(long ptr, String packageName, ArrayList<String> result);
    static private native long getContextProgram0(QoreURLClassLoader syscl, BooleanWrapper created, boolean finalize_init);
    static private native void shutdownContext0();
    private native boolean clearCompilationCache0(long origin_ptr, long ptr);
    static private native void dummy0();
    static private native void debug0(long ptr);
    // the Program pointer is only compared with the Program of the calling code (qore#7015)
    static private native boolean programReachable0(long ptr);

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
