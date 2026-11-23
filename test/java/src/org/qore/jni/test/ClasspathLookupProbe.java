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
                another = "refused: " + e.getCause().getMessage();
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

    //! Tries to make a copy on the class loader of detachedLimit(); returns "ok" or "refused: <the reason>"
    public static String detachedCopy() throws Exception {
        try {
            releaseLocations(detached, checkedLocations(detached, null));
            return "ok";
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "refused: " + e.getCause().getMessage();
        }
    }

    //! Returns whether each new Program of a class loader gets a budget of its own, also when it returns to the first
    public static String programSwitchBudget(QoreURLClassLoader parent) throws Exception {
        Object[] context = QoreURLClassLoader.saveContext();
        try {
            QoreURLClassLoader loader = QoreURLClassLoader.createDerived(parent);
            java.lang.reflect.Method set = QoreURLClassLoader.class.getDeclaredMethod("setPtrIntern", long.class,
                boolean.class);
            set.setAccessible(true);
            java.lang.reflect.Field budget = QoreURLClassLoader.class.getDeclaredField("copy_budget");
            budget.setAccessible(true);
            long first = loader.getPtr();
            Object inherited = budget.get(loader);
            // the class loader is not used with this value, which only has to differ from the first
            set.invoke(loader, first + 16, false);
            Object second = budget.get(loader);
            set.invoke(loader, first, false);
            Object third = budget.get(loader);
            if (second == inherited) {
                return "a new Program kept the budget";
            }
            return third == second ? "a return to the first Program kept the budget of the second" : "own budgets";
        } finally {
            QoreURLClassLoader.restoreContext(context);
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
                return "refused: " + e.getCause().getMessage();
            }
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! Ends two copies whose removal fails, then retries once it can succeed; returns "clean" or what is wrong
    /** @param mode the first attempt and the retry: "close" and "abort" are retried by closing the handle, "program"
        (the end of the Program) too, and "close-program" is a close that is retried by the end of the Program
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
                    case "close-program":
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
            if (!raised) {
                return "the error was not raised";
            }
            if (attempts.get() != 2) {
                return "tried to remove " + attempts.get() + " of 2 copies";
            }
            java.lang.reflect.Field liveField = budget.getClass().getDeclaredField("live");
            liveField.setAccessible(true);
            java.lang.reflect.Field usedField = budget.getClass().getDeclaredField("used");
            usedField.setAccessible(true);
            // a failed removal keeps what the copies use charged
            long[] heldCharges;
            synchronized (budget) {
                if (((java.util.Set<?>) liveField.get(budget)).size() != 2) {
                    return "a failed removal released a copy";
                }
                heldCharges = ((long[]) usedField.get(budget)).clone();
            }
            if (heldCharges[2] == 0) {
                return "a failed removal returned its charges";
            }
            for (Path root : roots) {
                if (!Files.isDirectory(root)) {
                    return "a directory is gone after a failed removal";
                }
            }
            if (mode.equals("program") && parent.getPtr() != 0) {
                return "the Program pointer was not cleared";
            }
            // the cause of the failure has gone: the removal is retried
            for (int i = 0; i < copies.size(); ++i) {
                rootField.set(copies.get(i), roots.get(i));
            }
            if (mode.equals("close-program")) {
                parent.clearProgramPtr();
            } else {
                handle.getClass().getMethod("close").invoke(handle);
            }
            for (Path root : roots) {
                if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    return "the retry left a directory";
                }
            }
            // a further close returns nothing a second time
            handle.getClass().getMethod("close").invoke(handle);
            synchronized (budget) {
                if (!((java.util.Set<?>) liveField.get(budget)).isEmpty()) {
                    return "a copy is still live after the retry";
                }
                for (long used : (long[]) usedField.get(budget)) {
                    if (used != 0) {
                        return "the charges were not returned exactly once: " + used;
                    }
                }
            }
            return "clean";
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! A file system provider over the default one, whose deletions fail with an I/O error while \a fail is set
    /** So that a failed removal is real for any user, also for root, for whom permissions do not stop a deletion
    */
    private static final class FailingProvider extends java.nio.file.spi.FileSystemProvider {
        private final java.nio.file.spi.FileSystemProvider real = java.nio.file.FileSystems.getDefault().provider();
        volatile boolean fail = true;
        //! runs once, at the first deletion that fails
        volatile Runnable onFailure;
        //! the attributes of an opened directory cannot be read while this is set
        volatile boolean failHandle;
        //! opening the directory named #rootName fails while this is set
        volatile boolean failOpen;
        //! the name of the root of the copy
        volatile Path rootName;
        //! runs once, just before the root of the copy is deleted relative to its parent
        volatile Runnable onRootDelete;
        //! reading a numeric UID raises an I/O error
        static final int UID_ERROR = 1;
        //! reading a numeric UID finds no unix attribute view
        static final int UID_NO_VIEW = 2;
        //! how reading a numeric UID fails, or 0
        volatile int failUid;
        //! paths that are made from a path of this provider also go through it
        volatile boolean wrapDerived;
        //! the reads of the attributes of an opened directory that failed with the injected I/O error
        int handleFailures;
        //! the deletions made by path
        int pathDeletes;
        int failures;

        //! Returns a path for \a target whose operations go through this provider
        Path wrap(Path target) {
            java.nio.file.FileSystem fs = new ProviderFileSystem(target.getFileSystem(), this);
            return (Path) java.lang.reflect.Proxy.newProxyInstance(ClasspathLookupProbe.class.getClassLoader(),
                new Class<?>[] {Path.class, Unwrappable.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getFileSystem":
                            return fs;
                        case "unwrap":
                            return target;
                        case "resolve":
                        case "getParent":
                            Object derived = callOn(target, method, args);
                            return wrapDerived && derived != null ? wrap((Path) derived) : derived;
                        default:
                            return callOn(target, method, args);
                    }
                });
        }

        private static Object callOn(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private static Path unwrap(Path path) {
            return path instanceof Unwrappable ? ((Unwrappable) path).unwrap() : path;
        }

        void failDeletion() throws IOException {
            if (fail) {
                ++failures;
                Runnable hook = onFailure;
                onFailure = null;
                if (hook != null) {
                    hook.run();
                }
                throw new IOException("injected deletion failure");
            }
        }

        @Override public String getScheme() { return real.getScheme(); }
        @Override public java.nio.file.FileSystem newFileSystem(java.net.URI uri, java.util.Map<String, ?> env) {
            throw new UnsupportedOperationException();
        }
        @Override public java.nio.file.FileSystem getFileSystem(java.net.URI uri) {
            return real.getFileSystem(uri);
        }
        @Override public Path getPath(java.net.URI uri) { return real.getPath(uri); }
        @Override public java.nio.channels.SeekableByteChannel newByteChannel(Path path,
                java.util.Set<? extends java.nio.file.OpenOption> options,
                java.nio.file.attribute.FileAttribute<?>... attrs) throws IOException {
            return real.newByteChannel(unwrap(path), options, attrs);
        }
        @Override public java.nio.file.DirectoryStream<Path> newDirectoryStream(Path dir,
                java.nio.file.DirectoryStream.Filter<? super Path> filter) throws IOException {
            java.nio.file.DirectoryStream<Path> stream = real.newDirectoryStream(unwrap(dir), filter);
            return stream instanceof java.nio.file.SecureDirectoryStream
                ? new FailingStream((java.nio.file.SecureDirectoryStream<Path>) stream, this) : stream;
        }
        @Override public void createDirectory(Path dir, java.nio.file.attribute.FileAttribute<?>... attrs)
                throws IOException {
            real.createDirectory(unwrap(dir), attrs);
        }
        @Override public void delete(Path path) throws IOException {
            ++pathDeletes;
            failDeletion();
            real.delete(unwrap(path));
        }
        @Override public void copy(Path source, Path target, java.nio.file.CopyOption... options) throws IOException {
            real.copy(unwrap(source), unwrap(target), options);
        }
        @Override public void move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException {
            real.move(unwrap(source), unwrap(target), options);
        }
        @Override public boolean isSameFile(Path path, Path path2) throws IOException {
            return real.isSameFile(unwrap(path), unwrap(path2));
        }
        @Override public boolean isHidden(Path path) throws IOException { return real.isHidden(unwrap(path)); }
        @Override public java.nio.file.FileStore getFileStore(Path path) throws IOException {
            return real.getFileStore(unwrap(path));
        }
        @Override public void checkAccess(Path path, java.nio.file.AccessMode... modes) throws IOException {
            real.checkAccess(unwrap(path), modes);
        }
        @Override public <V extends java.nio.file.attribute.FileAttributeView> V getFileAttributeView(Path path,
                Class<V> type, java.nio.file.LinkOption... options) {
            return real.getFileAttributeView(unwrap(path), type, options);
        }
        @Override public <A extends java.nio.file.attribute.BasicFileAttributes> A readAttributes(Path path,
                Class<A> type, java.nio.file.LinkOption... options) throws IOException {
            return real.readAttributes(unwrap(path), type, options);
        }
        @Override public java.util.Map<String, Object> readAttributes(Path path, String attributes,
                java.nio.file.LinkOption... options) throws IOException {
            if (attributes.contains("uid")) {
                if (failUid == UID_ERROR) {
                    throw new IOException("injected attribute failure");
                }
                if (failUid == UID_NO_VIEW) {
                    throw new UnsupportedOperationException("injected: no such view");
                }
            }
            return real.readAttributes(unwrap(path), attributes, options);
        }
        @Override public void setAttribute(Path path, String attribute, Object value,
                java.nio.file.LinkOption... options) throws IOException {
            real.setAttribute(unwrap(path), attribute, value, options);
        }
    }

    //! A path of FailingProvider
    public interface Unwrappable {
        Path unwrap();
    }

    //! The default file system with another provider
    private static final class ProviderFileSystem extends java.nio.file.FileSystem {
        private final java.nio.file.FileSystem real;
        private final java.nio.file.spi.FileSystemProvider provider;

        ProviderFileSystem(java.nio.file.FileSystem real, java.nio.file.spi.FileSystemProvider provider) {
            this.real = real;
            this.provider = provider;
        }

        @Override public java.nio.file.spi.FileSystemProvider provider() { return provider; }
        @Override public void close() { throw new UnsupportedOperationException(); }
        @Override public boolean isOpen() { return real.isOpen(); }
        @Override public boolean isReadOnly() { return real.isReadOnly(); }
        @Override public String getSeparator() { return real.getSeparator(); }
        @Override public Iterable<Path> getRootDirectories() { return real.getRootDirectories(); }
        @Override public Iterable<java.nio.file.FileStore> getFileStores() { return real.getFileStores(); }
        @Override public java.util.Set<String> supportedFileAttributeViews() {
            return real.supportedFileAttributeViews();
        }
        @Override public Path getPath(String first, String... more) { return real.getPath(first, more); }
        @Override public java.nio.file.PathMatcher getPathMatcher(String pattern) {
            return real.getPathMatcher(pattern);
        }
        @Override public java.nio.file.attribute.UserPrincipalLookupService getUserPrincipalLookupService() {
            return real.getUserPrincipalLookupService();
        }
        @Override public java.nio.file.WatchService newWatchService() throws IOException {
            return real.newWatchService();
        }
    }

    //! A secure directory stream whose deletions go through FailingProvider's failure
    private static final class FailingStream implements java.nio.file.SecureDirectoryStream<Path> {
        private final java.nio.file.SecureDirectoryStream<Path> real;
        private final FailingProvider provider;

        FailingStream(java.nio.file.SecureDirectoryStream<Path> real, FailingProvider provider) {
            this.real = real;
            this.provider = provider;
        }

        @Override public java.util.Iterator<Path> iterator() { return real.iterator(); }
        @Override public void close() throws IOException { real.close(); }
        @Override public java.nio.file.SecureDirectoryStream<Path> newDirectoryStream(Path path,
                java.nio.file.LinkOption... options) throws IOException {
            if (provider.failOpen && path.equals(provider.rootName)) {
                throw new IOException("injected open failure");
            }
            return new FailingStream(real.newDirectoryStream(path, options), provider);
        }
        @Override public java.nio.channels.SeekableByteChannel newByteChannel(Path path,
                java.util.Set<? extends java.nio.file.OpenOption> options,
                java.nio.file.attribute.FileAttribute<?>... attrs) throws IOException {
            return real.newByteChannel(path, options, attrs);
        }
        @Override public void deleteFile(Path path) throws IOException {
            provider.failDeletion();
            real.deleteFile(path);
        }
        @Override public void deleteDirectory(Path path) throws IOException {
            provider.failDeletion();
            if (path.equals(provider.rootName)) {
                Runnable hook = provider.onRootDelete;
                provider.onRootDelete = null;
                if (hook != null) {
                    hook.run();
                }
            }
            real.deleteDirectory(path);
        }
        @Override public void move(Path source, java.nio.file.SecureDirectoryStream<Path> target, Path targetPath)
                throws IOException {
            real.move(source, target, targetPath);
        }
        @Override public <V extends java.nio.file.attribute.FileAttributeView> V getFileAttributeView(Class<V> type) {
            if (provider.failHandle) {
                // a view of the type that is asked for, whose attributes cannot be read
                return type.cast(java.lang.reflect.Proxy.newProxyInstance(ClasspathLookupProbe.class.getClassLoader(),
                    new Class<?>[] {type}, (proxy, method, args) -> {
                        if (method.getName().equals("readAttributes")) {
                            ++provider.handleFailures;
                            throw new IOException("injected attribute failure");
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }));
            }
            return real.getFileAttributeView(type);
        }
        @Override public <V extends java.nio.file.attribute.FileAttributeView> V getFileAttributeView(Path path,
                Class<V> type, java.nio.file.LinkOption... options) {
            return real.getFileAttributeView(path, type, options);
        }
    }

    //! Returns the value of a private field
    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    //! Returns the value of a private static int constant of a class
    private static int constant(Class<?> cls, String name) throws Exception {
        java.lang.reflect.Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    //! Deletes a directory tree of a test
    private static void removeTestTree(Path path) throws IOException {
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

    //! Runs a removal of a compiler copy that really fails; returns "clean" or what is wrong
    /** @param scenario "confirmed": a failed deletion keeps the charges, and a retry removes the copy; "during": a
        release arrives while a removal runs; "signal": a removal that keeps failing is reported, without its path;
        "parent-replaced", "parent-mode", "parent-owner", "parent-link", "parent-handle": the private parent no longer
        verifies at a retry; "root-final", "root-file", "root-unverified", "root-open-fails": a retry does not remove
        anything but the directory of the copy
    */
    public static String removalFailure(QoreURLClassLoader parent, String dir, String scenario) throws Exception {
        Object[] context = QoreURLClassLoader.saveContext();
        java.io.PrintStream savedOut = System.out;
        try {
            QoreURLClassLoader loader = QoreURLClassLoader.createDerived(parent);
            loader.addPath(dir);
            Object handle = QoreURLClassLoader.class.getMethod("openCompilerClasspath").invoke(loader);
            java.lang.reflect.Method close = handle.getClass().getMethod("close");
            List<?> copies = (List<?>) field(handle, "copies");
            if (copies.size() != 1) {
                return "expected one copy, found " + copies.size();
            }
            Object copy = copies.get(0);
            Class<?> copyClass = copy.getClass();
            Object budget = field(copy, "budget");
            Path root = (Path) field(copy, "root");
            Object parentObject = field(copy, "parent");
            java.lang.reflect.Field parentPath = parentObject.getClass().getDeclaredField("path");
            parentPath.setAccessible(true);
            Path realParent = (Path) parentPath.get(parentObject);
            int failedState = constant(copyClass, "FAILED");
            int doneState = constant(copyClass, "DONE");
            FailingProvider provider = new FailingProvider();
            parentPath.set(parentObject, provider.wrap(realParent));

            if (scenario.equals("during")) {
                // the release arrives during the first failing deletion, whose attempt then fails
                provider.onFailure = () -> {
                    provider.fail = false;
                    try {
                        close.invoke(handle);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                    provider.fail = true;
                    provider.onFailure = () -> provider.fail = false;
                };
                close.invoke(handle);
                provider.fail = false;
                if (provider.failures < 1) {
                    return "the removal did not fail";
                }
                parentPath.set(parentObject, realParent);
                if ((int) field(copy, "state") != doneState) {
                    close.invoke(handle);
                    return "a release during a removal did not get its retry";
                }
                return chargesReturned(budget) ? (Files.exists(root) ? "the directory remains" : "clean")
                    : "the charges were not returned";
            }

            java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
            if (scenario.equals("signal")) {
                System.setOut(new java.io.PrintStream(captured, true));
            }
            // no exception: the deletion fails with an I/O error, which the removal handles
            close.invoke(handle);
            if ((int) field(copy, "state") != failedState) {
                return "a failed deletion was taken as a removal";
            }
            if (chargesReturned(budget)) {
                return "a failed removal returned its charges";
            }
            if (!Files.isDirectory(root)) {
                return "the directory is gone after a failed removal";
            }
            if (scenario.equals("signal")) {
                close.invoke(handle);
                close.invoke(handle);
                System.out.flush();
                System.setOut(savedOut);
                String text = captured.toString();
                int count = text.split("could not be removed", -1).length - 1;
                if (count != 1) {
                    return "reported " + count + " times";
                }
                if (text.contains(root.toString()) || text.contains(root.getFileName().toString())) {
                    return "the report names the path";
                }
            }
            if (scenario.startsWith("root-")) {
                return changedRoot(scenario, handle, copy, budget, parentObject, parentPath, realParent, root,
                    provider);
            }
            if (scenario.startsWith("parent-")) {
                return tamperedParent(scenario, loader, handle, copy, budget, parentObject, parentPath, realParent,
                    root, provider);
            }
            parentPath.set(parentObject, realParent);
            close.invoke(handle);
            if ((int) field(copy, "state") != doneState) {
                return "the retry did not end the copy";
            }
            if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return "the retry left the directory";
            }
            return chargesReturned(budget) ? "clean" : "the charges were not returned";
        } finally {
            System.setOut(savedOut);
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! A retry where the name of the root is not the directory of the copy, or where it cannot be opened
    /** "root-final": it becomes a file just before the final deletion; "root-file": it is a file when the retry starts;
        "root-unverified": it is a file and the opened parent cannot be verified; "root-open-fails": the root cannot be
        opened.  Nothing is removed by path, what is at the name stays, and the copy stays charged until it is restored.
    */
    private static String changedRoot(String scenario, Object handle, Object copy, Object budget, Object parentObject,
            java.lang.reflect.Field parentPath, Path parent, Path root, FailingProvider provider) throws Exception {
        java.lang.reflect.Method close = handle.getClass().getMethod("close");
        int failedState = constant(copy.getClass(), "FAILED");
        int doneState = constant(copy.getClass(), "DONE");
        final String foreign = "not the copy's";
        Path moved = parent.resolve(root.getFileName() + "-moved");
        Runnable swap = () -> {
            try {
                Files.move(root, moved);
                Files.writeString(root, foreign);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        int entries = countTree(root);
        provider.fail = false;
        provider.rootName = root.getFileName();
        provider.pathDeletes = 0;
        String rv = "clean";
        try {
            switch (scenario) {
                case "root-final":
                    provider.onRootDelete = swap;
                    break;
                case "root-file":
                    swap.run();
                    break;
                case "root-unverified":
                    provider.failHandle = true;
                    swap.run();
                    break;
                case "root-open-fails":
                    provider.failOpen = true;
                    break;
                default:
                    return "unknown scenario " + scenario;
            }
            close.invoke(handle);
            boolean swapped = Files.exists(moved, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (provider.pathDeletes != 0) {
                rv = "the retry deleted by path";
            } else if (scenario.equals("root-unverified") && provider.handleFailures == 0) {
                rv = "the failure to read the opened directory was not injected";
            } else if ((int) field(copy, "state") != failedState) {
                rv = "the retry did not leave the copy failed";
            } else if (chargesReturned(budget)) {
                rv = "the retry returned the charges";
            } else if (swapped && (!Files.isRegularFile(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || !foreign.equals(Files.readString(root)))) {
                rv = "the retry removed what was at the name of the root";
            } else if (!swapped && countTree(root) != entries) {
                rv = "the retry changed the copy";
            }
        } finally {
            provider.failHandle = false;
            provider.failOpen = false;
            provider.onRootDelete = null;
            if (Files.exists(moved, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(root);
                Files.move(moved, root);
            }
            parentPath.set(parentObject, parent);
        }
        close.invoke(handle);
        if (!rv.equals("clean")) {
            return rv;
        }
        if ((int) field(copy, "state") != doneState) {
            return "the copy did not end once its root was restored";
        }
        if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return "the copy remains once its root was restored";
        }
        return chargesReturned(budget) ? "clean" : "the charges were not returned";
    }

    //! the owner that setUnexpectedTempOwner() changed, or null
    private static java.nio.file.attribute.UserPrincipal savedTempOwner;
    //! the JVM's UID before a simulation
    private static Object savedProcessUid;
    //! the private parent whose recorded UID a simulation changed, and that UID
    private static Object savedParent;
    private static long savedParentUid;

    //! Returns the private parent class
    private static Class<?> parentClass() throws Exception {
        return Class.forName("org.qore.jni.QoreCompilerClasspath$PrivateParent", true,
            QoreURLClassLoader.class.getClassLoader());
    }

    //! Returns a private static field of the private parent class
    private static java.lang.reflect.Field parentStatic(String name) throws Exception {
        java.lang.reflect.Field f = parentClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    //! Returns the numeric UID of a path
    private static long uidOf(Path path) throws IOException {
        return Integer.toUnsignedLong((Integer) Files.getAttribute(path, "unix:uid",
            java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    //! Makes the UID of a directory that the JVM owns differ from the JVM's, with no second user needed
    /** A JVM of root really changes the owner; any other JVM is told that its UID, and the one that its current private
        parent recorded, is another one
    */
    public static void setUnexpectedTempOwner(String dir) throws Exception {
        Path path = Path.of(dir);
        savedProcessUid = parentStatic("processUid").get(null);
        long dirUid = uidOf(path);
        if (dirUid == 0) {
            java.nio.file.attribute.UserPrincipal owner = Files.getOwner(path,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
            savedTempOwner = owner;
            Files.setOwner(path, otherUser(owner));
            return;
        }
        parentStatic("processUid").set(null, Long.valueOf(dirUid + 1));
        Object current = parentStatic("current").get(null);
        if (current != null) {
            savedParent = current;
            savedParentUid = (long) field(current, "uid");
            java.lang.reflect.Field uid = parentClass().getDeclaredField("uid");
            uid.setAccessible(true);
            uid.setLong(current, dirUid + 1);
        }
    }

    //! Makes the UID of the JVM unreadable, as for a platform where the JDK cannot give it
    public static void setUnknownJvmUid() throws Exception {
        savedProcessUid = parentStatic("processUid").get(null);
        parentStatic("processUid").set(null, null);
    }

    //! Undoes setUnexpectedTempOwner() or setUnknownJvmUid()
    public static void restoreTempOwner(String dir) throws Exception {
        if (savedTempOwner != null) {
            Files.setOwner(Path.of(dir), savedTempOwner);
            savedTempOwner = null;
        }
        if (savedParent != null) {
            java.lang.reflect.Field uid = parentClass().getDeclaredField("uid");
            uid.setAccessible(true);
            uid.setLong(savedParent, savedParentUid);
            savedParent = null;
        }
        parentStatic("processUid").set(null, savedProcessUid);
    }

    //! Applies the rule for the temporary directory by numeric UID; no user name is involved
    /** Unit level for root: the real root-owned directory "/" is judged, as a JVM that is not root cannot make a
        root-owned directory that it can write to
        @return "clean", or what is wrong
    */
    public static String uidRule(String dir) throws Exception {
        java.lang.reflect.Method rule = parentClass().getDeclaredMethod("safeDirectory", Path.class, Long.class);
        rule.setAccessible(true);
        Path path = Path.of(dir);
        long own = uidOf(path);
        if (!(boolean) rule.invoke(null, path, Long.valueOf(own))) {
            return "the same UID was refused";
        }
        if (own != 0 && (boolean) rule.invoke(null, path, Long.valueOf(own + 1))) {
            return "another UID was allowed";
        }
        if ((boolean) rule.invoke(null, path, (Long) null)) {
            return "an unreadable JVM UID was allowed";
        }
        Path top = Path.of("/");
        if (uidOf(top) != 0) {
            return "no root-owned directory";
        }
        if (!(boolean) rule.invoke(null, top, Long.valueOf(own + 1))) {
            return "a directory of root was refused";
        }
        FailingProvider provider = new FailingProvider();
        Path wrapped = provider.wrap(path);
        provider.failUid = FailingProvider.UID_ERROR;
        if ((boolean) rule.invoke(null, wrapped, Long.valueOf(own))) {
            return "an unreadable directory UID was allowed";
        }
        provider.failUid = FailingProvider.UID_NO_VIEW;
        if ((boolean) rule.invoke(null, wrapped, Long.valueOf(own))) {
            return "a POSIX platform with no unix attribute view was allowed";
        }
        provider.failUid = 0;
        return (boolean) rule.invoke(null, wrapped, Long.valueOf(own)) ? "clean" : "the provider refused a readable UID";
    }

    //! Returns null if a method raises the refusal of the private parent, or else what it did
    private static String refused(java.lang.reflect.Method method, Object target, String refusal, Object... args)
            throws Exception {
        try {
            method.invoke(target, args);
            return method.getName() + " did not refuse";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            return cause instanceof IOException && refusal.equals(cause.getMessage()) ? null
                : method.getName() + " raised " + cause;
        }
    }

    //! Creates a parent and a root under another identity than the one expected: both are refused and leave nothing
    /** The recorded and process UIDs are changed through reflection; the real UID of the process never changes
        @param dir the JVM's temporary directory, with no private parent in it
        @return "clean", or what is wrong
    */
    public static String identityChecks(String dir) throws Exception {
        Class<?> cls = parentClass();
        String refusal = (String) parentStatic("TEMP_REFUSAL").get(null);
        Path temp = Path.of(dir).toRealPath();
        long own = uidOf(temp);
        int before = countTree(temp);
        java.lang.reflect.Field processUid = parentStatic("processUid");
        Object savedUid = processUid.get(null);
        java.lang.reflect.Method create = cls.getDeclaredMethod("create", Path.class);
        create.setAccessible(true);
        try {
            for (Long uid : new Long[] {own + 1, null}) {
                processUid.set(null, uid);
                String rv = refused(create, null, refusal, temp);
                if (rv != null) {
                    return rv;
                }
                if (countTree(temp) != before) {
                    return "a refused parent remains";
                }
            }
        } finally {
            processUid.set(null, savedUid);
        }

        java.lang.reflect.Method acquire = cls.getDeclaredMethod("acquire");
        java.lang.reflect.Method newRoot = cls.getDeclaredMethod("newRoot");
        java.lang.reflect.Method release = cls.getDeclaredMethod("release");
        java.lang.reflect.Field recorded = cls.getDeclaredField("uid");
        for (java.lang.reflect.AccessibleObject o : new java.lang.reflect.AccessibleObject[] {acquire, newRoot, release,
                recorded}) {
            o.setAccessible(true);
        }
        Object parent = acquire.invoke(null);
        Path parentDir = (Path) field(parent, "path");
        try {
            recorded.setLong(parent, own + 1);
            String rv = refused(newRoot, parent, refusal);
            recorded.setLong(parent, own);
            if (rv != null) {
                return rv;
            }
            if (countTree(parentDir) != 1) {
                return "a refused root remains";
            }
            Path root = (Path) newRoot.invoke(parent);
            if (uidOf(root) != own) {
                return "a root has another UID";
            }
            Files.delete(root);
        } finally {
            recorded.setLong(parent, own);
            try (java.nio.file.DirectoryStream<Path> left = Files.newDirectoryStream(parentDir)) {
                for (Path entry : left) {
                    removeTestTree(entry);
                }
            }
            release.invoke(parent);
        }
        return Files.exists(parentDir, java.nio.file.LinkOption.NOFOLLOW_LINKS) ? "the parent remains" : "clean";
    }

    //! Returns null if opening a compiler classpath raises the refusal; a classpath that is opened is closed again
    private static String openRefused(java.lang.reflect.Method open, QoreURLClassLoader loader, String refusal)
            throws Exception {
        Object handle;
        try {
            handle = open.invoke(loader);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            return cause instanceof IOException && refusal.equals(cause.getMessage()) ? null : "raised " + cause;
        }
        handle.getClass().getMethod("close").invoke(handle);
        return "a copy was made";
    }

    //! Returns what is wrong if a refusal changed the charges, the live copies or the count of the parent
    private static String unchanged(Object budget, Object parent, long[] used, int live, int copies) throws Exception {
        synchronized (budget) {
            if (!java.util.Arrays.equals(used, (long[]) field(budget, "used"))) {
                return "a refusal changed the charges";
            }
            if (((java.util.Set<?>) field(budget, "live")).size() != live) {
                return "a refusal changed the live copies";
            }
        }
        return (int) field(parent, "copies") == copies ? null : "a refusal changed the count of the parent";
    }

    //! A root that is refused after it was created: it is removed, or else its parent takes no more copies
    /** While one copy is held: an error from reading the UID of a new root; the same refusal through the budget, which
        unwinds completely; and a root that cannot be removed, after which every further copy is refused, with nothing
        more left in the parent.  Once that root is gone and the held copy ends, the parent goes and copies work again.
        @return "clean", or what is wrong
    */
    public static String rootRefusals(QoreURLClassLoader loader) throws Exception {
        java.lang.reflect.Method open = QoreURLClassLoader.class.getMethod("openCompilerClasspath");
        Object held = open.invoke(loader);
        java.lang.reflect.Method close = held.getClass().getMethod("close");
        Object copy = ((List<?>) field(held, "copies")).get(0);
        Object budget = field(copy, "budget");
        Object parent = field(copy, "parent");
        Path heldRoot = (Path) field(copy, "root");
        java.lang.reflect.Field pathField = parentClass().getDeclaredField("path");
        pathField.setAccessible(true);
        Path real = (Path) pathField.get(parent);
        String refusal = (String) parentStatic("TEMP_REFUSAL").get(null);
        java.lang.reflect.Method newRoot = parentClass().getDeclaredMethod("newRoot");
        newRoot.setAccessible(true);
        FailingProvider provider = new FailingProvider();
        provider.fail = false;
        provider.wrapDerived = true;
        String rv;
        try {
            pathField.set(parent, provider.wrap(real));
            rv = rootRefusalSteps(open, loader, newRoot, budget, parent, real, refusal, provider);
        } finally {
            provider.fail = false;
            provider.failUid = 0;
            pathField.set(parent, real);
            try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(real)) {
                for (Path entry : entries) {
                    if (!entry.getFileName().equals(heldRoot.getFileName())) {
                        removeTestTree(entry);
                    }
                }
            }
            close.invoke(held);
        }
        if (!rv.equals("clean")) {
            return rv;
        }
        if (Files.exists(real, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return "the parent remains";
        }
        if (!chargesReturned(budget)) {
            return "the charges were not returned";
        }
        Object next = open.invoke(loader);
        next.getClass().getMethod("close").invoke(next);
        return "clean";
    }

    private static String rootRefusalSteps(java.lang.reflect.Method open, QoreURLClassLoader loader,
            java.lang.reflect.Method newRoot, Object budget, Object parent, Path real, String refusal,
            FailingProvider provider) throws Exception {
        int entries = countTree(real);
        long[] used;
        int live;
        synchronized (budget) {
            used = ((long[]) field(budget, "used")).clone();
            live = ((java.util.Set<?>) field(budget, "live")).size();
        }
        int count = (int) field(parent, "copies");

        // the UID of the new root cannot be read: it is refused and removed
        for (int mode : new int[] {FailingProvider.UID_ERROR, FailingProvider.UID_NO_VIEW}) {
            provider.failUid = mode;
            String rv = refused(newRoot, parent, refusal);
            if (rv != null) {
                return "reading the UID of a root: " + rv;
            }
            if (countTree(real) != entries) {
                return "a root whose UID cannot be read remains";
            }
        }

        // the same refusal through the budget unwinds what it charged and counted
        provider.failUid = FailingProvider.UID_ERROR;
        String rv = openRefused(open, loader, refusal);
        if (rv != null) {
            return "through the budget: " + rv;
        }
        rv = unchanged(budget, parent, used, live, count);
        if (rv != null) {
            return rv;
        }
        if (countTree(real) != entries) {
            return "a root refused through the budget remains";
        }

        // a refused root that cannot be removed stays, and stops new copies in this parent
        provider.fail = true;
        rv = openRefused(open, loader, refusal);
        provider.fail = false;
        provider.failUid = 0;
        if (rv != null) {
            return "a root that cannot be removed: " + rv;
        }
        if (countTree(real) != entries + 1) {
            return "the root that could not be removed is not there";
        }
        String blockedRefusal = (String) parentStatic("BLOCKED_REFUSAL").get(null);
        for (int i = 0; i < 3; ++i) {
            rv = openRefused(open, loader, blockedRefusal);
            if (rv != null) {
                return "the next copy after a root that cannot be removed: " + rv;
            }
            if (countTree(real) != entries + 1) {
                return "leftovers accumulate in the parent";
            }
            rv = unchanged(budget, parent, used, live, count);
            if (rv != null) {
                return rv;
            }
        }
        return "clean";
    }

    //! Opens a compiler classpath that must work, in the given parent or in another one; returns "clean" or the problem
    private static String copyWorks(java.lang.reflect.Method open, QoreURLClassLoader loader, Object parent,
            boolean sameParent) throws Exception {
        Object handle;
        try {
            handle = open.invoke(loader);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "a copy was refused: " + e.getCause().getMessage();
        }
        try {
            Object used = field(((List<?>) field(handle, "copies")).get(0), "parent");
            if ((used == parent) != sameParent) {
                return sameParent ? "the copy did not reuse the parent" : "the copy used the old parent";
            }
        } finally {
            handle.getClass().getMethod("close").invoke(handle);
        }
        return "clean";
    }

    //! Returns the entry of a directory other than \a kept, or null
    private static Path otherEntry(Path dir, Path kept) throws IOException {
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (!entry.getFileName().equals(kept.getFileName())) {
                    return entry;
                }
            }
        }
        return null;
    }

    //! Blocks the private parent with a root that cannot be removed, and checks how it recovers or keeps refusing
    /** @param scenario "after-release": the leftover goes after the last release; "parent-removed": the whole parent is
        removed; "stays": a parent that holds the leftover refuses, in acquire() too, and logs once; "not-intact": an empty
        parent that does not verify refuses; "in-flight": no recovery while a copy is held; "in-flight-empty" and
        "in-flight-removed": no recovery while a copy is counted that has no root yet, with the parent empty or removed,
        and recovery once it is released; "newroot-check": a compile
        that is past acquire() makes no root; "discard-verifies": a refused root is not removed from an unverified parent
        @return "clean", or what is wrong
    */
    public static String blockedRecovery(QoreURLClassLoader loader, String scenario) throws Exception {
        java.lang.reflect.Method open = QoreURLClassLoader.class.getMethod("openCompilerClasspath");
        Object held = open.invoke(loader);
        java.lang.reflect.Method close = held.getClass().getMethod("close");
        Object copy = ((List<?>) field(held, "copies")).get(0);
        Object parent = field(copy, "parent");
        Path heldRoot = (Path) field(copy, "root");
        Class<?> cls = parentClass();
        java.lang.reflect.Field pathField = cls.getDeclaredField("path");
        java.lang.reflect.Field ownerField = cls.getDeclaredField("owner");
        java.lang.reflect.Method newRoot = cls.getDeclaredMethod("newRoot");
        java.lang.reflect.Method acquire = cls.getDeclaredMethod("acquire");
        java.lang.reflect.Method release = cls.getDeclaredMethod("release");
        for (java.lang.reflect.AccessibleObject o : new java.lang.reflect.AccessibleObject[] {pathField, ownerField,
                newRoot, acquire, release}) {
            o.setAccessible(true);
        }
        Path real = (Path) pathField.get(parent);
        Object savedOwner = ownerField.get(parent);
        String tempRefusal = (String) parentStatic("TEMP_REFUSAL").get(null);
        int before = countTree(real);
        FailingProvider provider = new FailingProvider();
        provider.fail = false;
        provider.wrapDerived = true;
        boolean heldOpen = true;
        boolean counted = false;
        java.io.PrintStream savedOut = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured, true));
        try {
            if (scenario.startsWith("in-flight-")) {
                // a copy that is counted and has no root yet
                if (acquire.invoke(null) != parent) {
                    return "the counted copy is in another parent";
                }
                counted = true;
            }
            if (scenario.equals("discard-verifies")) {
                // nothing fails but the verification of the parent
                ownerField.set(parent, otherUser(savedOwner));
                pathField.set(parent, provider.wrap(real));
                provider.failUid = FailingProvider.UID_ERROR;
                String rv = refused(newRoot, parent, tempRefusal);
                provider.failUid = 0;
                pathField.set(parent, real);
                ownerField.set(parent, savedOwner);
                if (rv != null) {
                    return rv;
                }
                return countTree(real) == before + 1 ? "clean" : "a root was removed from a parent that does not verify";
            }
            pathField.set(parent, provider.wrap(real));
            provider.failUid = FailingProvider.UID_ERROR;
            provider.fail = true;
            String rv = openRefused(open, loader, tempRefusal);
            provider.fail = false;
            provider.failUid = 0;
            pathField.set(parent, real);
            if (rv != null) {
                return "blocking the parent: " + rv;
            }
            Path leftover = otherEntry(real, heldRoot);
            if (leftover == null) {
                return "no root was left";
            }
            switch (scenario) {
                case "after-release":
                    close.invoke(held);
                    heldOpen = false;
                    Files.delete(leftover);
                    return copyWorks(open, loader, parent, true);
                case "parent-removed":
                    close.invoke(held);
                    heldOpen = false;
                    removeTestTree(real);
                    return copyWorks(open, loader, parent, false);
                case "stays": {
                    close.invoke(held);
                    heldOpen = false;
                    String blocked = (String) parentStatic("BLOCKED_REFUSAL").get(null);
                    int left = countTree(real);
                    for (int i = 0; i < 3; ++i) {
                        rv = openRefused(open, loader, blocked);
                        if (rv == null) {
                            rv = refused(acquire, null, blocked);
                        }
                        if (rv != null) {
                            return rv;
                        }
                        if (countTree(real) != left) {
                            return "leftovers accumulate";
                        }
                    }
                    System.out.flush();
                    String text = captured.toString();
                    int count = text.split(java.util.regex.Pattern.quote(blocked), -1).length - 1;
                    if (count != 1) {
                        return "logged " + count + " times";
                    }
                    return text.contains(real.getFileName().toString()) ? "the log names the path" : "clean";
                }
                case "not-intact": {
                    close.invoke(held);
                    heldOpen = false;
                    Files.delete(leftover);
                    String blocked = (String) parentStatic("BLOCKED_REFUSAL").get(null);
                    Files.setPosixFilePermissions(real,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---"));
                    rv = openRefused(open, loader, blocked);
                    Files.setPosixFilePermissions(real,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                    return rv != null ? rv : copyWorks(open, loader, parent, true);
                }
                case "in-flight": {
                    Files.delete(leftover);
                    rv = openRefused(open, loader, (String) parentStatic("BLOCKED_REFUSAL").get(null));
                    if (rv != null) {
                        return "with a copy in flight: " + rv;
                    }
                    close.invoke(held);
                    heldOpen = false;
                    return copyWorks(open, loader, parent, false);
                }
                case "in-flight-empty":
                case "in-flight-removed": {
                    close.invoke(held);
                    heldOpen = false;
                    Files.delete(leftover);
                    if (scenario.equals("in-flight-removed")) {
                        Files.delete(real);
                    } else if (countTree(real) != 1) {
                        return "the parent is not empty";
                    }
                    rv = openRefused(open, loader, (String) parentStatic("BLOCKED_REFUSAL").get(null));
                    if (rv != null) {
                        return "with a counted copy: " + rv;
                    }
                    counted = false;
                    release.invoke(parent);
                    return copyWorks(open, loader, parent, false);
                }
                case "newroot-check": {
                    int left = countTree(real);
                    rv = refused(newRoot, parent, (String) parentStatic("BLOCKED_REFUSAL").get(null));
                    if (rv != null) {
                        return rv;
                    }
                    return countTree(real) == left ? "clean" : "a root was made in a blocked parent";
                }
                default:
                    return "unknown scenario " + scenario;
            }
        } finally {
            System.setOut(savedOut);
            provider.fail = false;
            provider.failUid = 0;
            pathField.set(parent, real);
            ownerField.set(parent, savedOwner);
            if (Files.isDirectory(real, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(real,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            }
            if (heldOpen) {
                close.invoke(held);
            }
            if (counted) {
                release.invoke(parent);
            }
            removeTestTree(real);
            java.lang.reflect.Field current = parentStatic("current");
            if (current.get(null) == parent) {
                current.set(null, null);
            }
        }
    }

    //! Opens and closes a compiler classpath: returns "copies=<n>", or the message of the refusal
    public static String copyOutcome(QoreURLClassLoader loader) throws Exception {
        Object handle;
        try {
            handle = QoreURLClassLoader.class.getMethod("openCompilerClasspath").invoke(loader);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return String.valueOf(e.getCause().getMessage());
        }
        try {
            return "copies=" + ((List<?>) field(handle, "copies")).size();
        } finally {
            handle.getClass().getMethod("close").invoke(handle);
        }
    }

    //! Makes the private parent fail its verification; a retry touches nothing, and works again once it is restored
    private static String tamperedParent(String scenario, QoreURLClassLoader loader, Object handle, Object copy,
            Object budget, Object parentObject, java.lang.reflect.Field parentPath, Path parent, Path root,
            FailingProvider provider) throws Exception {
        java.lang.reflect.Method close = handle.getClass().getMethod("close");
        int failedState = constant(copy.getClass(), "FAILED");
        int doneState = constant(copy.getClass(), "DONE");
        Path moved = parent.resolveSibling(parent.getFileName() + "-moved");
        Path marker = parent.resolve("marker");
        java.lang.reflect.Field ownerField = parentObject.getClass().getDeclaredField("owner");
        ownerField.setAccessible(true);
        Object savedOwner = ownerField.get(parentObject);
        int entries = countTree(parent);
        Path held = parent;
        boolean swapped = false;
        String rv = "clean";
        try {
            switch (scenario) {
                case "parent-replaced":
                    Files.move(parent, moved);
                    swapped = true;
                    held = moved;
                    Files.createDirectory(parent, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
                    Files.writeString(marker, "not the copy's");
                    break;
                case "parent-link":
                    Files.move(parent, moved);
                    swapped = true;
                    held = moved;
                    Files.createSymbolicLink(parent, moved);
                    break;
                case "parent-mode":
                    Files.setPosixFilePermissions(parent,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---"));
                    break;
                case "parent-owner":
                    ownerField.set(parentObject, otherUser(savedOwner));
                    break;
                case "parent-handle":
                    provider.fail = false;
                    provider.failHandle = true;
                    break;
                default:
                    return "unknown scenario " + scenario;
            }
            if (!scenario.equals("parent-handle")) {
                parentPath.set(parentObject, parent);
            }
            // each of two retries leaves everything in place
            for (int i = 0; i < 2 && rv.equals("clean"); ++i) {
                close.invoke(handle);
                if ((int) field(copy, "state") != failedState) {
                    rv = "the retry did not leave the copy failed";
                } else if (chargesReturned(budget)) {
                    rv = "the retry returned the charges";
                } else if (countTree(held) != entries) {
                    rv = "the retry changed the copy";
                } else if (scenario.equals("parent-replaced") && !Files.exists(marker)) {
                    rv = "the retry touched the directory that replaced the parent";
                } else if (scenario.equals("parent-handle") && provider.handleFailures == 0) {
                    rv = "the failure to read the opened directory was not injected";
                }
            }
            // a new copy does not use a parent that does not verify
            if (rv.equals("clean") && !scenario.equals("parent-handle")) {
                Object next = QoreURLClassLoader.class.getMethod("openCompilerClasspath").invoke(loader);
                try {
                    Object nextCopy = ((List<?>) field(next, "copies")).get(0);
                    if (field(nextCopy, "parent") == parentObject) {
                        rv = "a new copy used a parent that does not verify";
                    }
                } finally {
                    next.getClass().getMethod("close").invoke(next);
                }
            }
        } finally {
            provider.failHandle = false;
            ownerField.set(parentObject, savedOwner);
            if (swapped) {
                Files.deleteIfExists(marker);
                Files.delete(parent);
                Files.move(moved, parent);
            }
            Files.setPosixFilePermissions(parent,
                java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            parentPath.set(parentObject, parent);
        }
        if (!rv.equals("clean")) {
            close.invoke(handle);
            return rv;
        }
        close.invoke(handle);
        if ((int) field(copy, "state") != doneState) {
            return "the copy did not end once the parent was restored";
        }
        if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return "the copy remains once the parent was restored";
        }
        if (Files.exists(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return "the parent remains after its last copy";
        }
        return chargesReturned(budget) ? "clean" : "the charges were not returned";
    }

    //! Returns a user other than \a owner
    private static java.nio.file.attribute.UserPrincipal otherUser(Object owner) throws IOException {
        java.nio.file.attribute.UserPrincipalLookupService users =
            java.nio.file.FileSystems.getDefault().getUserPrincipalLookupService();
        for (String name : new String[] {"root", "nobody", "daemon"}) {
            try {
                java.nio.file.attribute.UserPrincipal user = users.lookupPrincipalByName(name);
                if (!user.equals(owner)) {
                    return user;
                }
            } catch (java.nio.file.attribute.UserPrincipalNotFoundException e) {
                // the next name is tried
            }
        }
        throw new IOException("no other user is known");
    }

    //! Returns the number of entries of a directory tree, without following links
    private static int countTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.count();
        }
    }

    //! Returns true if a budget has no charge and no live copy
    private static boolean chargesReturned(Object budget) throws Exception {
        synchronized (budget) {
            if (!((java.util.Set<?>) field(budget, "live")).isEmpty()) {
                return false;
            }
            for (long used : (long[]) field(budget, "used")) {
                if (used != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    //! Returns "contained" if the removal of the Kotlin compiler's temporary directory raises nothing for any failure
    public static String compilerCleanupContained(QoreURLClassLoader loader) throws Exception {
        Class<?> cls = loader.loadClass("org.qore.jni.compiler.QoreKotlinCompiler");
        java.lang.reflect.Method delete = cls.getDeclaredMethod("deleteTree", Path.class);
        delete.setAccessible(true);
        Path failing = (Path) java.lang.reflect.Proxy.newProxyInstance(Path.class.getClassLoader(),
            new Class<?>[] {Path.class}, (proxy, method, args) -> {
                throw new InjectedError();
            });
        try {
            delete.invoke(null, failing);
            return "contained";
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "raised " + e.getCause().getClass().getSimpleName();
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

    //! Adds a path by one route of the class loader under a captured policy; returns "ok" or "refused"
    /** @param route "addPath", "addPathOrig", "addURL", "addWildcard" (\a path is the pattern in its directory),
        "addParentPathIntern" (the route of the global module commands, on a class
        loader derived from \a loader) or "resolved" (the route of the \c add-classpath module command)
    */
    public static String addRouteUnder(QoreSandbox.Policy policy, QoreURLClassLoader loader, String route, String path) {
        try {
            policy.call(() -> {
                switch (route) {
                    case "addPath":
                        loader.addPath(path);
                        break;
                    case "addPathOrig":
                        loader.addPathOrig(path);
                        break;
                    case "addURL": {
                        java.lang.reflect.Method add = QoreURLClassLoader.class.getDeclaredMethod(route, URL.class);
                        add.setAccessible(true);
                        add.invoke(loader, new File(path).toURI().toURL());
                        break;
                    }
                    case "addWildcard": {
                        File file = new File(path);
                        loader.addWildcard(file.getParentFile(), file.getName());
                        break;
                    }
                    case "addParentPathIntern": {
                        java.lang.reflect.Method add = QoreURLClassLoader.class.getDeclaredMethod(route, String.class);
                        add.setAccessible(true);
                        add.invoke(QoreURLClassLoader.createDerived(loader), path);
                        break;
                    }
                    case "resolved": {
                        java.lang.reflect.Method check = QoreSandbox.class.getDeclaredMethod("checkedClasspath",
                            String.class);
                        check.setAccessible(true);
                        Object resolved = check.invoke(null, path);
                        java.lang.reflect.Method add = QoreURLClassLoader.class.getDeclaredMethod("addResolvedPath",
                            resolved.getClass());
                        add.setAccessible(true);
                        add.invoke(loader, resolved);
                        break;
                    }
                    default:
                        throw new IllegalArgumentException(route);
                }
                return null;
            });
            return "ok";
        } catch (Throwable e) {
            return "refused";
        }
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
