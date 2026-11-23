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
            real.deleteDirectory(path);
        }
        @Override public void move(Path source, java.nio.file.SecureDirectoryStream<Path> target, Path targetPath)
                throws IOException {
            real.move(source, target, targetPath);
        }
        @Override public <V extends java.nio.file.attribute.FileAttributeView> V getFileAttributeView(Class<V> type) {
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
    /** @param scenario "confirmed": a failed deletion keeps the charges, and a retry removes the copy; "replaced": the
        name of the private directory comes to refer to another directory before the retry; "during": a release arrives
        while a removal runs; "signal": a removal that keeps failing is reported, without its path
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
            java.lang.reflect.Field rootField = copyClass.getDeclaredField("root");
            rootField.setAccessible(true);
            Object budget = field(copy, "budget");
            Path root = (Path) rootField.get(copy);
            int failedState = constant(copyClass, "FAILED");
            int doneState = constant(copyClass, "DONE");
            FailingProvider provider = new FailingProvider();
            rootField.set(copy, provider.wrap(root));

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
                rootField.set(copy, root);
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
            rootField.set(copy, root);
            if (scenario.equals("replaced")) {
                Path moved = root.resolveSibling(root.getFileName() + "-moved");
                Files.move(root, moved);
                Files.createDirectory(root);
                Path marker = root.resolve("marker");
                Files.writeString(marker, "not the copy's");
                String rv = "clean";
                try {
                    close.invoke(handle);
                    if (!Files.exists(marker)) {
                        rv = "the retry removed a directory that it did not create";
                    } else if ((int) field(copy, "state") != doneState) {
                        rv = "the copy did not end";
                    } else if (!chargesReturned(budget)) {
                        rv = "the charges were not returned";
                    }
                    close.invoke(handle);
                    if (rv.equals("clean") && !chargesReturned(budget)) {
                        rv = "the charges were not returned exactly once";
                    }
                } finally {
                    removeTestTree(root);
                    removeTestTree(moved);
                }
                return rv;
            }
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
