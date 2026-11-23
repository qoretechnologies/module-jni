/* Copyright (C) 2026 Qore Technologies, s.r.o.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.qore.jni.test;

import org.qore.jni.QoreSandbox;
import org.qore.jni.QoreURLClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

//! Class and resource lookups through a class loader, for the tests of the sandbox classpath policy
/** A lookup returns "found", or the normalized outcome of a lookup that finds nothing, so that a test can assert that
    a file that the policy denies and a file that does not exist give the same outcome.
*/
public class ClasspathLookupProbe {
    //! A class loader subclass, for the protected operations of the class loader
    public static class SubLoader extends QoreURLClassLoader {
        public SubLoader(ClassLoader parent) {
            super("ClasspathLookupProbe.SubLoader", parent);
        }

        public void addUrlFromSubclass(String path) throws IOException {
            addURL(new File(path).toURI().toURL());
        }

        public void addUrlDirect(URL url) {
            addURL(url);
        }

        public Class<?> findClassFromSubclass(String name) throws ClassNotFoundException {
            return findClass(name);
        }
    }

    //! Returns a class loader subclass with the given parent
    public static SubLoader newSubLoader(ClassLoader parent) {
        return new SubLoader(parent);
    }

    //! Adds a classpath with the class loader's Java API; returns "ok" or the error
    public static String addPath(QoreURLClassLoader loader, String classpath) {
        try {
            loader.addPath(classpath);
            return "ok";
        } catch (Throwable e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Adds the files of a directory that match a pattern; returns "ok" or the error
    public static String addWildcard(QoreURLClassLoader loader, String dir, String pattern) {
        try {
            loader.addWildcard(new File(dir), pattern);
            return "ok";
        } catch (Throwable e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Returns the sandbox policy of the calling code
    public static QoreSandbox.Policy capturePolicy() {
        return QoreSandbox.capture();
    }

    //! Adds a URL through the protected method of a subclass, under a captured policy; returns "ok" or the error
    public static String addUrlUnder(QoreSandbox.Policy policy, SubLoader loader, String path) {
        try {
            policy.call(() -> {
                loader.addUrlFromSubclass(path);
                return null;
            });
            return "ok";
        } catch (Throwable e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Adds a classpath to the parent of a class loader; returns "ok" or the error
    public static String addParentPath(QoreURLClassLoader loader, String classpath) {
        try {
            loader.addParentPath(classpath);
            return "ok";
        } catch (Throwable e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Returns true if the class loader has a public method that writes compiler locations to a path of the caller
    public static boolean hasCallerPathCopy() {
        try {
            QoreURLClassLoader.class.getMethod("getCheckedCompilerLocations", Path.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    //! Asks for the compiler locations of a class loader; a form that takes a directory is given \a dir
    @SuppressWarnings("unchecked")
    private static List<File> checkedLocations(QoreURLClassLoader loader, String dir) throws Exception {
        if (hasCallerPathCopy()) {
            return (List<File>) QoreURLClassLoader.class.getMethod("getCheckedCompilerLocations", Path.class)
                .invoke(loader, Path.of(dir));
        }
        java.lang.reflect.Method open = findMethod("openCompilerClasspath");
        if (open != null) {
            Object handle = open.invoke(loader);
            List<File> rv = new ArrayList<>((List<File>) handle.getClass().getMethod("getLocations").invoke(handle));
            synchronized (handles) {
                handles.put(rv, handle);
            }
            return rv;
        }
        return (List<File>) QoreURLClassLoader.class.getMethod("getCheckedCompilerLocations").invoke(loader);
    }

    //! the handles of the compiler classpaths that checkedLocations() opened, by the list that it returned
    private static final java.util.IdentityHashMap<List<File>, Object> handles = new java.util.IdentityHashMap<>();

    //! Returns a public method of the class loader with no arguments, or null
    private static java.lang.reflect.Method findMethod(String name) {
        try {
            return QoreURLClassLoader.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    //! Returns true if the class loader releases compiler copies by their paths
    public static boolean hasReleaseByPath() {
        try {
            QoreURLClassLoader.class.getMethod("releaseCompilerCopies", List.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    //! Releases compiler locations with their handle, or where the class loader has a release by path, with that
    private static void releaseLocations(QoreURLClassLoader loader, List<File> locations) throws Exception {
        Object handle;
        synchronized (handles) {
            handle = handles.remove(locations);
        }
        if (handle != null) {
            handle.getClass().getMethod("close").invoke(handle);
        } else if (hasReleaseByPath()) {
            QoreURLClassLoader.class.getMethod("releaseCompilerCopies", List.class).invoke(loader, locations);
        }
    }

    //! The system properties with an action that runs at a given read of one property, on the thread that reads it
    private static final class ReadHook extends java.util.Properties {
        private final String key;
        private final transient Runnable action;
        private int countdown;

        ReadHook(java.util.Properties defaults, String key, int at, Runnable action) {
            super(defaults);
            this.key = key;
            this.countdown = at;
            this.action = action;
        }

        @Override
        public String getProperty(String name) {
            if (name.equals(key) && countdown > 0 && --countdown == 0) {
                action.run();
            }
            return super.getProperty(name);
        }
    }

    //! An error that a test makes the removal of a compiler copy raise
    private static final class InjectedError extends Error {
        private static final long serialVersionUID = 1L;
    }

    //! a class loader of a Program whose own pointer was cleared
    private static QoreURLClassLoader detached;

    //! Holds as many copies as possible on a derived class loader with a cleared pointer, then tries another loader
    /** @return "held <n>; another <ok|refused>"
    */
    public static String detachedLimit(QoreURLClassLoader parent, String dir) throws Exception {
        Object[] context = QoreURLClassLoader.saveContext();
        try {
            detached = QoreURLClassLoader.createDerived(parent);
            detached.clearProgramPtr();
            detached.addPath(dir);
            int held = 0;
            try {
                while (held < 64) {
                    kept.add(checkedLocations(detached, null));
                    ++held;
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                // the limit has been reached
            }
            QoreURLClassLoader other = QoreURLClassLoader.createDerived(parent);
            other.addPath(dir);
            String another;
            try {
                releaseLocations(other, checkedLocations(other, null));
                another = "ok";
            } catch (java.lang.reflect.InvocationTargetException e) {
                another = "refused";
            }
            return "held " + held + "; another " + another;
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! Returns the path of the last copy that is kept
    public static String lastKeptCopy() {
        for (File location : kept.get(kept.size() - 1)) {
            if (isCopy(location)) {
                return location.getPath();
            }
        }
        throw new IllegalStateException("no copy");
    }

    //! Tries to make a copy on the class loader of detachedLimit(); returns "ok" or "refused"
    public static String detachedCopy() throws Exception {
        try {
            releaseLocations(detached, checkedLocations(detached, null));
            return "ok";
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "refused";
        }
    }

    //! Tries to make a copy with a guard that is created now, under \a policy, on a child of detachedLimit()'s loader
    public static String copyWithNewGuard(QoreSandbox.Policy policy, String dir) throws Exception {
        Object[] context = QoreURLClassLoader.saveContext();
        try {
            QoreURLClassLoader loader = QoreURLClassLoader.createDerived(detached);
            String added = addPathUnder(policy, loader, dir);
            if (!added.equals("ok")) {
                return added;
            }
            try {
                releaseLocations(loader, checkedLocations(loader, null));
                return "ok";
            } catch (java.lang.reflect.InvocationTargetException e) {
                return "refused";
            }
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! Ends two copies whose removal raises an error; returns "clean" or what is wrong
    /** @param mode "close" for the handle, "abort" for the failure path of the handle, "program" for the Program's end
    */
    public static String endCopiesWithError(QoreURLClassLoader parent, String dir, String mode) throws Exception {
        Object[] context = QoreURLClassLoader.saveContext();
        try {
            QoreURLClassLoader first = QoreURLClassLoader.createDerived(parent);
            first.addPath(dir);
            QoreURLClassLoader second = QoreURLClassLoader.createDerived(first);
            second.addPath(dir);
            Object handle = QoreURLClassLoader.class.getMethod("openCompilerClasspath").invoke(second);
            java.lang.reflect.Field copiesField = handle.getClass().getDeclaredField("copies");
            copiesField.setAccessible(true);
            List<?> copies = new ArrayList<>((List<?>) copiesField.get(handle));
            if (copies.size() != 2) {
                return "expected two copies, found " + copies.size();
            }
            Class<?> copyClass = copies.get(0).getClass();
            java.lang.reflect.Field rootField = copyClass.getDeclaredField("root");
            rootField.setAccessible(true);
            java.lang.reflect.Field budgetField = copyClass.getDeclaredField("budget");
            budgetField.setAccessible(true);
            Object budget = budgetField.get(copies.get(0));
            java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
            ArrayList<Path> roots = new ArrayList<>();
            for (Object copy : copies) {
                roots.add((Path) rootField.get(copy));
                rootField.set(copy, java.lang.reflect.Proxy.newProxyInstance(Path.class.getClassLoader(),
                    new Class<?>[] {Path.class}, (proxy, method, args) -> {
                        attempts.incrementAndGet();
                        throw new InjectedError();
                    }));
            }
            boolean raised = false;
            try {
                switch (mode) {
                    case "close":
                        handle.getClass().getMethod("close").invoke(handle);
                        break;
                    case "abort": {
                        java.lang.reflect.Method abort = handle.getClass().getDeclaredMethod("abort");
                        abort.setAccessible(true);
                        abort.invoke(handle);
                        break;
                    }
                    default:
                        parent.clearProgramPtr();
                        break;
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                raised = e.getCause() instanceof InjectedError;
            } catch (InjectedError e) {
                raised = true;
            }
            for (Path root : roots) {
                deleteTree(root);
            }
            if (!raised) {
                return "the error was not raised";
            }
            if (attempts.get() != 2) {
                return "removed " + attempts.get() + " of 2 copies";
            }
            java.lang.reflect.Field liveField = budget.getClass().getDeclaredField("live");
            liveField.setAccessible(true);
            java.lang.reflect.Field usedField = budget.getClass().getDeclaredField("used");
            usedField.setAccessible(true);
            synchronized (budget) {
                if (!((java.util.Set<?>) liveField.get(budget)).isEmpty()) {
                    return "a copy is still live";
                }
                for (long used : (long[]) usedField.get(budget)) {
                    if (used != 0) {
                        return "a charge was not returned";
                    }
                }
            }
            if (mode.equals("program") && parent.getPtr() != 0) {
                return "the Program pointer was not cleared";
            }
            return "clean";
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! Deletes a directory tree of a test
    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> entries = new ArrayList<>();
            walk.forEach(entries::add);
            Collections.reverse(entries);
            for (Path entry : entries) {
                Files.delete(entry);
            }
        }
    }

    //! Compiles Kotlin while the Program of the class loader ends at the given read of a system property
    /** @return "clean" if the compile returned or raised its own exception, the hook ran and nothing is left
    */
    public static String compileWhileEnding(QoreURLClassLoader loader, String key, int at, String source)
            throws Exception {
        Class<?> cls = loader.loadClass("org.qore.jni.compiler.QoreKotlinCompiler");
        Object compiler = cls.getConstructor(QoreURLClassLoader.class, List.class).newInstance(loader, null);
        boolean[] fired = {false};
        java.util.Properties saved = System.getProperties();
        System.setProperties(new ReadHook(saved, key, at, () -> {
            fired[0] = true;
            loader.clearProgramPtr();
        }));
        String outcome;
        try {
            cls.getMethod("compile", java.util.Map.class).invoke(compiler, java.util.Map.of("KEnd", source));
            outcome = "compiled";
        } catch (java.lang.reflect.InvocationTargetException e) {
            outcome = e.getCause().getClass().getSimpleName();
        } finally {
            System.setProperties(saved);
        }
        cls.getMethod("close").invoke(compiler);
        if (!fired[0]) {
            return "the Program was not ended during the compile";
        }
        int left = copyDirectories() + countTemp("qore-kotlin-*");
        if (left != 0) {
            return "left " + left + " directories";
        }
        return outcome.equals("compiled") || outcome.equals("QoreKotlinCompilerException") ? "clean" : outcome;
    }

    //! Returns the number of entries of the JVM's temporary directory that match a pattern
    private static int countTemp(String pattern) throws IOException {
        int rv = 0;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(
                Path.of(System.getProperty("java.io.tmpdir")), pattern)) {
            for (Path entry : entries) {
                ++rv;
            }
        }
        return rv;
    }

    //! Makes a compiler copy, ending it from the copying thread at a given step; returns "clean" or what is left
    /** The limit of the entries visited is set to \a limit, the number of entries of \a dir, so that anything that a
        copy that was ended leaves charged makes the next copy fail.
    */
    public static String endCopyDuringMake(QoreURLClassLoader parent, String dir, int at, int limit)
            throws Exception {
        final String prop = "qore.jni.compiler.maxEntries";
        QoreURLClassLoader loader = QoreURLClassLoader.createDerived(parent);
        loader.addPath(dir);
        java.util.Properties saved = System.getProperties();
        System.setProperty(prop, Integer.toString(limit));
        List<File> got = null;
        System.setProperties(new ReadHook(saved, prop, at, () -> {
            try {
                loader.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }));
        try {
            got = checkedLocations(loader, null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // the copy was ended: it is not returned
        } finally {
            System.setProperties(saved);
        }
        String rv = "clean";
        if (got != null) {
            for (File location : got) {
                if (!location.exists()) {
                    rv = "a location that was returned does not exist";
                }
            }
            releaseLocations(loader, got);
        }
        int left = copyDirectories();
        if (left != 0) {
            return "left " + left + " directories";
        }
        QoreURLClassLoader next = QoreURLClassLoader.createDerived(parent);
        next.addPath(dir);
        try {
            releaseLocations(next, checkedLocations(next, null));
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "the next copy is over a limit";
        } finally {
            System.clearProperty(prop);
        }
        return rv;
    }

    //! the locations that holdDerived() holds; they are never released, for a test of the end of their Program
    private static final ArrayList<List<File>> kept = new ArrayList<>();

    //! Holds a copy on a loader derived from \a parent, and returns the path of the copy
    public static String holdDerived(QoreURLClassLoader parent, String dir) throws Exception {
        QoreURLClassLoader loader = QoreURLClassLoader.createDerived(parent);
        loader.addPath(dir);
        List<File> locations = checkedLocations(loader, null);
        kept.add(locations);
        for (File location : locations) {
            if (isCopy(location)) {
                return location.getPath();
            }
        }
        throw new IllegalStateException("no copy");
    }

    //! Returns true if a compiler location is a private copy
    private static boolean isCopy(File location) {
        File parent = location.getParentFile();
        return parent != null && parent.getName().startsWith("qore-classpath-");
    }

    //! Returns the Program pointer of a class loader
    public static long programPtr(QoreURLClassLoader loader) {
        return loader.getPtr();
    }

    //! Returns "yes" if a compiler location of the class loader has the file \a rel, "no" if not, or the refusal
    public static String locationsHold(QoreURLClassLoader loader, String dir, String rel) {
        try {
            List<File> locations = checkedLocations(loader, dir);
            try {
                for (File location : locations) {
                    if (new File(location, rel).exists()) {
                        return "yes";
                    }
                }
                return "no";
            } finally {
                releaseLocations(loader, locations);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "refused: " + e.getCause().getClass().getSimpleName();
        } catch (Exception e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Returns true if a location made for the class loader no longer exists once it has been released
    public static boolean copiesReleased(QoreURLClassLoader loader, String original) throws Exception {
        if (hasCallerPathCopy()) {
            return false;
        }
        List<File> locations = checkedLocations(loader, null);
        ArrayList<File> copies = new ArrayList<>();
        for (File location : locations) {
            if (isCopy(location)) {
                copies.add(location);
            }
        }
        releaseLocations(loader, locations);
        for (File copy : copies) {
            if (copy.exists()) {
                return false;
            }
        }
        return !copies.isEmpty();
    }

    //! Returns the number of compiler copies that can be held at once, up to \a max; releases them
    public static int holdCopies(QoreURLClassLoader loader, int max) {
        ArrayList<List<File>> held = new ArrayList<>();
        try {
            for (int i = 0; i < max; ++i) {
                held.add(checkedLocations(loader, null));
            }
        } catch (Exception e) {
            // the limit has been reached
        } finally {
            for (List<File> locations : held) {
                try {
                    releaseLocations(loader, locations);
                } catch (Exception e) {
                    // the others are still released
                }
            }
        }
        return held.size();
    }

    //! Holds the compiler locations of a chain of \a len derived class loaders that each have \a dir on their classpath
    /** @return "ok", or the refusal
    */
    public static String holdChain(QoreURLClassLoader first, String dir, int len) {
        ArrayList<QoreURLClassLoader> loaders = new ArrayList<>();
        ArrayList<List<File>> held = new ArrayList<>();
        try {
            QoreURLClassLoader prev = first;
            for (int i = 0; i < len; ++i) {
                prev = QoreURLClassLoader.createDerived(prev);
                prev.addPath(dir);
                loaders.add(prev);
            }
            for (QoreURLClassLoader loader : loaders) {
                held.add(checkedLocations(loader, null));
            }
            return "ok";
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "refused: " + e.getCause().getClass().getSimpleName();
        } catch (Exception e) {
            return "refused: " + e.getClass().getSimpleName();
        } finally {
            for (int i = 0; i < held.size(); ++i) {
                try {
                    releaseLocations(loaders.get(i), held.get(i));
                } catch (Exception e) {
                    // the others are still released
                }
            }
        }
    }

    //! Returns true if closing a derived class loader removes the compiler copies that it holds
    public static boolean closeRemovesCopies(QoreURLClassLoader parent, String dir) throws Exception {
        QoreURLClassLoader loader = QoreURLClassLoader.createDerived(parent);
        loader.addPath(dir);
        List<File> locations = checkedLocations(loader, null);
        ArrayList<File> copies = new ArrayList<>();
        for (File location : locations) {
            if (isCopy(location)) {
                copies.add(location);
            }
        }
        loader.close();
        boolean rv = !copies.isEmpty();
        for (File copy : copies) {
            rv = rv && !copy.exists();
        }
        releaseLocations(loader, locations);
        return rv;
    }

    //! Checks the copy of a class loader's classpath directory; returns "ok" or what is wrong with it
    public static String checkCopy(QoreURLClassLoader loader, String original, String allowedFile, String deniedFile,
            String otherFile) throws Exception {
        List<File> locations = checkedLocations(loader, null);
        try {
            for (File location : locations) {
                if (location.getPath().equals(original)) {
                    return "the directory itself was given";
                }
                if (!new File(location, allowedFile).isFile()) {
                    continue;
                }
                String perms = java.nio.file.attribute.PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(location.toPath().getParent()));
                if (!perms.equals("rwx------")) {
                    return "the directory of the copy is not private: " + perms;
                }
                if (new File(location, deniedFile).exists()) {
                    return "the copy has a denied file";
                }
                if (new File(location, otherFile).exists()) {
                    return "the copy has a file that is not for a compiler";
                }
                return "ok";
            }
            return "no copy";
        } finally {
            releaseLocations(loader, locations);
        }
    }

    //! Returns the number of compiler copy directories in the JVM's temporary directory
    public static int copyDirectories() throws IOException {
        int rv = 0;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(
                Path.of(System.getProperty("java.io.tmpdir")), "qore-classpath-*")) {
            for (Path entry : entries) {
                ++rv;
            }
        }
        return rv;
    }

    //! Returns true if the JDK finds a path
    public static boolean pathExists(String path) {
        return Files.exists(Path.of(path));
    }

    //! Adds a classpath under a captured policy; returns "ok" or the error
    public static String addPathUnder(QoreSandbox.Policy policy, QoreURLClassLoader loader, String path) {
        try {
            policy.call(() -> {
                loader.addPath(path);
                return null;
            });
            return "ok";
        } catch (Throwable e) {
            return "refused: " + e.getClass().getSimpleName();
        }
    }

    //! Serves the files of a directory with a URL protocol that names no local file
    private static final class ServedDirectory extends java.net.URLStreamHandler {
        private final Path dir;

        ServedDirectory(Path dir) {
            this.dir = dir;
        }

        @Override
        protected java.net.URLConnection openConnection(URL url) {
            Path file = dir.resolve(url.getPath().substring(1));
            return new java.net.URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!Files.isRegularFile(file)) {
                        throw new java.io.FileNotFoundException(url.toString());
                    }
                    return Files.newInputStream(file);
                }
            };
        }
    }

    //! Adds a URL that names no local file, which serves the files of \a dir, as trusted code
    @SuppressWarnings("deprecation")
    public static void addServedUrl(SubLoader loader, String dir) throws IOException {
        loader.addUrlDirect(new URL(null, "qoreprobe:/", new ServedDirectory(Path.of(dir))));
    }

    //! Sets or clears a system property
    public static void setProperty(String name, String value) {
        if (value == null || value.isEmpty()) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    //! Returns true if the module has verified its handling of manifest Class-Path attributes for the running JDK
    public static boolean manifestClassPathVerified() throws Exception {
        java.lang.reflect.Method verified = QoreURLClassLoader.class.getDeclaredMethod("isVerifiedClassPathJdk",
            int.class);
        verified.setAccessible(true);
        return (boolean) verified.invoke(null, Runtime.version().feature());
    }

    //! Returns the encoding of file names in the JVM
    public static String fileNameEncoding() {
        return System.getProperty("sun.jnu.encoding");
    }

    //! Returns the number of file descriptors of the process that are open on \a path, or -1 if that is not known
    public static int openDescriptors(String path) throws IOException {
        Path fds = Path.of("/proc/self/fd");
        if (!Files.isDirectory(fds)) {
            return -1;
        }
        int rv = 0;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(fds)) {
            for (Path fd : entries) {
                try {
                    if (Files.readSymbolicLink(fd).toString().equals(path)) {
                        ++rv;
                    }
                } catch (IOException e) {
                    // closed since it was listed
                }
            }
        }
        return rv;
    }

    //! Closes a class loader
    public static void closeLoader(QoreURLClassLoader loader) throws IOException {
        loader.close();
    }

    //! Evaluates a Kotlin script through the public API for \a loader, with another thread context class loader
    public static String evalWithOtherContext(QoreURLClassLoader loader, String script, boolean bindings) {
        Object[] saved = QoreURLClassLoader.saveContext();
        loader.setContext();
        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            Object rv = bindings
                ? org.qore.jni.KotlinScriptEngine.evalWithBindings(script, new java.util.HashMap<>())
                : org.qore.jni.KotlinScriptEngine.eval(script);
            return "result: " + rv;
        } catch (Throwable e) {
            return "refused: " + e.getMessage();
        } finally {
            QoreURLClassLoader.restoreContext(saved);
        }
    }

    //! Returns a class loader derived from \a parent, for delegation to it
    public static QoreURLClassLoader derived(QoreURLClassLoader parent) {
        return QoreURLClassLoader.createDerived(parent);
    }

    //! Makes a jar of the files below \a dir, with a manifest Class-Path attribute if \a classPath is not empty
    public static void writeJar(String dir, String jar, String classPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (!classPath.isEmpty()) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        }
        Path root = Path.of(dir);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        Collections.sort(files);
        try (OutputStream out = Files.newOutputStream(Path.of(jar));
                JarOutputStream jos = new JarOutputStream(out, manifest)) {
            for (Path file : files) {
                jos.putNextEntry(new JarEntry(root.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
    }

    //! Makes one lookup on the current thread; see the class description for the result
    /** @param cl the class loader
        @param op forName, init, loadClass, location, getResource, getResourceAsStream, getResources, findResource,
        findResources or findClass (the last three need a SubLoader or any URLClassLoader)
        @param name the binary name of a class or the name of a resource
    */
    public static String lookup(ClassLoader cl, String op, String name) {
        try {
            switch (op) {
                case "forName":
                    Class.forName(name, false, cl);
                    return "found";
                case "init":
                    Class.forName(name, true, cl);
                    return "found";
                case "loadClass":
                    cl.loadClass(name);
                    return "found";
                case "location": {
                    Class<?> c = cl.loadClass(name);
                    return c.getProtectionDomain().getCodeSource().getLocation().getPath();
                }
                case "getResource":
                    return cl.getResource(name) != null ? "found" : "missing";
                case "getResourceAsStream":
                    try (InputStream in = cl.getResourceAsStream(name)) {
                        if (in == null) {
                            return "missing";
                        }
                        in.readAllBytes();
                        return "found";
                    }
                case "getResources":
                    return count(cl.getResources(name));
                case "findResource":
                    return ((java.net.URLClassLoader) cl).findResource(name) != null ? "found" : "missing";
                case "findResources":
                    return count(((java.net.URLClassLoader) cl).findResources(name));
                case "findClass":
                    ((SubLoader) cl).findClassFromSubclass(name);
                    return "found";
                default:
                    throw new IllegalArgumentException("unknown lookup: " + op);
            }
        } catch (ClassNotFoundException | LinkageError e) {
            String msg = e.getMessage();
            // the class name is the only variable part of the outcome
            return "missing: " + e.getClass().getName() + ": "
                + (msg == null ? "" : msg.replace(name.replace('.', '/'), "<name>").replace(name, "<name>"));
        } catch (IOException e) {
            return "missing: " + e.getClass().getName();
        }
    }

    //! Returns "found" if the enumeration has an element, or else "missing"
    private static String count(Enumeration<URL> urls) {
        return urls.hasMoreElements() ? "found" : "missing";
    }

    //! Makes one lookup on a new Java thread, which has no Qore caller
    public static String lookupOnThread(ClassLoader cl, String op, String name) throws InterruptedException {
        AtomicReference<String> rv = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                rv.set(lookup(cl, op, name));
            } catch (Throwable e) {
                rv.set("error: " + e.getClass().getName());
            }
        });
        thread.start();
        thread.join();
        return rv.get();
    }
}
