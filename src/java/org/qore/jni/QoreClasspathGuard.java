// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//! Applies the filesystem policy of the code that added a classpath entry to each lookup below the entry
/** A class loader has a guard only once code that a filesystem policy restricts has added an entry to it; a class
    loader without one makes no check for any file, so trusted code and sandboxes that restrict nothing pay nothing.

    <b>Whose policy applies:</b> the policy of the code that added the entry, captured when it was added and kept with
    the entry.  A lookup can run on any thread, and the JVM makes lookups by itself, with no %Qore caller on the stack,
    so the thread's caller is never consulted.  An entry with no policy is not checked: it was added by trusted code,
    or when no filesystem policy restricted the code that added it (no sandbox yet, or a sandbox that restricted nothing
    then), and a policy that is set or tightened later does not apply to it.

    <b>Each lookup</b> resolves the file that was found to its real path, with symbolic links resolved, and applies the
    policy to that path.  A file that the policy denies, or that cannot be checked for any reason, is treated as a file
    that does not exist, so that the following classpath entries are still searched.

    <b>No decision is cached.</b>  A sandbox policy can be changed at any time and has no change indicator, so a cached
    answer could outlive the rule that it was made from; the policy bound to an entry is therefore read for every lookup.

    A file that is replaced between its check and the moment that it is opened is not covered.

    The object is thread-safe.

    @since jni 3.0
*/
final class QoreClasspathGuard {
    //! the most files that are copied for a compiler for one classpath
    static final int MAX_COMPILER_FILES = 100000;
    //! the most bytes that are copied for a compiler for one classpath
    static final long MAX_COMPILER_BYTES = 1L << 30;
    //! the most directory entries that are visited for a compiler for one classpath
    static final int MAX_COMPILER_ENTRIES = 200000;
    //! the deepest directory level that is copied for a compiler
    private static final int MAX_COMPILER_DEPTH = 256;
    //! a system property that can lower MAX_COMPILER_ENTRIES
    private static final String MAX_ENTRIES_PROP = "qore.jni.compiler.maxEntries";

    //! the policies of each path that restricted code added, as the class loader's URLs name it
    private final ConcurrentHashMap<String, QoreSandbox.ClasspathPolicy[]> roots = new ConcurrentHashMap<>();
    //! the roots that are directories which the class loader reads files below
    private final Set<String> directories = ConcurrentHashMap.newKeySet();
    //! the private directories of the compiler copies that have not been released
    private final Set<Path> copies = ConcurrentHashMap.newKeySet();
    //! incremented when the roots change, to invalidate the trusted roots
    private volatile int version;
    //! the paths that trusted entries reach, the version of the roots and the number of URLs that they were made for
    private volatile Object[] trusted;

    //! Binds \a policies to the given paths of a class loader's classpath; each path keeps the policies it had too
    /** @param paths the paths that the classpath reaches
        @param dirs those of the paths that are directories which the class loader reads files below
        @param policies the policies of the code that adds the classpath
    */
    synchronized void bind(Collection<String> paths, Collection<String> dirs,
            QoreSandbox.ClasspathPolicy[] policies) {
        for (String path : paths) {
            roots.merge(path, policies, QoreClasspathGuard::union);
        }
        directories.addAll(dirs);
        ++version;
    }

    //! Marks a path as added by trusted code, so that it is a grant of the host
    synchronized void markTrusted(String path) {
        directories.remove(path);
        if (roots.remove(path) != null) {
            ++version;
        }
    }

    //! Returns true if the classpath has a directory that restricted code added, itself or through a manifest
    boolean hasDirectory() {
        return !directories.isEmpty();
    }

    //! Returns the policies of both arguments, each once
    private static QoreSandbox.ClasspathPolicy[] union(QoreSandbox.ClasspathPolicy[] a,
            QoreSandbox.ClasspathPolicy[] b) {
        ArrayList<QoreSandbox.ClasspathPolicy> rv = new ArrayList<>(a.length + b.length);
        for (QoreSandbox.ClasspathPolicy p : a) {
            rv.add(p);
        }
        for (QoreSandbox.ClasspathPolicy p : b) {
            boolean found = false;
            for (QoreSandbox.ClasspathPolicy q : a) {
                found = found || q.id() == p.id();
            }
            if (!found) {
                rv.add(p);
            }
        }
        return rv.toArray(new QoreSandbox.ClasspathPolicy[0]);
    }

    //! Returns true if a URL that the class loader found may be used
    /** Every policy of the entries that contain the file applies, up to the nearest entry of trusted code.  A name with
        a parent directory element is used only if it names the same file as its URL.

        @param url the URL of a class or resource
        @param name the name that was looked up
        @param loaderUrls the URLs of the class loader's classpath
    */
    boolean allows(URL url, String name, URL[] loaderUrls) {
        String spec = url.toString();
        boolean jar = spec.startsWith("jar:");
        if (jar) {
            // as the JDK splits a jar URL
            int sep = spec.lastIndexOf("!/");
            if (sep < 0) {
                return false;
            }
            spec = spec.substring(4, sep);
        }
        if (!spec.startsWith("file:")) {
            // restricted code only adds files: anything else can only come from an entry of trusted code
            return isTrustedUrl(url.toString(), loaderUrls);
        }
        File file;
        try {
            file = new File(new URI(spec));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
        Set<String> trusted_roots = trustedRoots(loaderUrls);
        boolean parent_elements = !jar && hasParentElement(name);
        ArrayList<QoreSandbox.ClasspathPolicy[]> applied = null;
        File base = null;
        // a jar is an entry itself
        for (File f = file; f != null; f = jar ? null : f.getParentFile()) {
            String path = f.getPath();
            QoreSandbox.ClasspathPolicy[] policies = roots.get(path);
            if (policies != null) {
                if (applied == null) {
                    applied = new ArrayList<>(2);
                }
                applied.add(policies);
                if (parent_elements && base == null && isBaseOf(f, name, file)) {
                    base = f;
                }
                continue;
            }
            if (trusted_roots.contains(path)) {
                if (applied == null) {
                    return true;
                }
                break;
            }
        }
        // in no known entry: it cannot be attributed to any code
        if (applied == null) {
            return false;
        }
        Path real;
        try {
            real = file.toPath().toRealPath();
            if (parent_elements && (base == null || !new File(base, name).toPath().toRealPath().equals(real))) {
                return false;
            }
        } catch (IOException | InvalidPathException | SecurityException e) {
            return false;
        }
        for (QoreSandbox.ClasspathPolicy[] policies : applied) {
            if (!checkReal(real, policies)) {
                return false;
            }
        }
        return true;
    }

    //! Returns true if a resource name has a parent directory element
    private static boolean hasParentElement(String name) {
        if (!name.contains("..")) {
            return false;
        }
        for (String element : name.split("/", -1)) {
            if (element.equals("..")) {
                return true;
            }
        }
        return false;
    }

    //! Returns true if \a file is the normalized path of \a name below the directory \a dir
    private static boolean isBaseOf(File dir, String name, File file) {
        try {
            return new File(dir, name).toPath().normalize().equals(file.toPath());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    //! Returns true if a URL is below a URL of the classpath that names no local file
    private static boolean isTrustedUrl(String url, URL[] loaderUrls) {
        for (URL u : loaderUrls) {
            if (QoreURLClassLoader.urlToFile(u) == null && url.startsWith(u.toString())) {
                return true;
            }
        }
        return false;
    }

    //! Returns true if every policy allows reading a real path
    /** A path whose name the JVM's file name encoding cannot represent exactly is refused, as the bytes that would be
        checked would not be those of the file
    */
    private static boolean checkReal(Path real, QoreSandbox.ClasspathPolicy[] policies) {
        String text = real.toString();
        try {
            if (!real.getFileSystem().getPath(text).equals(real)) {
                return false;
            }
        } catch (InvalidPathException e) {
            return false;
        }
        byte[] bytes = QoreSandbox.encodeRealPath(text);
        if (bytes == null) {
            return false;
        }
        for (QoreSandbox.ClasspathPolicy p : policies) {
            if (!p.allowsRead(bytes)) {
                return false;
            }
        }
        return true;
    }

    //! Returns the paths that the entries of trusted code reach, with the manifests of their jars
    private Set<String> trustedRoots(URL[] loaderUrls) {
        Object[] current = trusted;
        if (current != null && (Integer) current[1] == version && (Integer) current[2] == loaderUrls.length) {
            @SuppressWarnings("unchecked")
            Set<String> rv = (Set<String>) current[0];
            return rv;
        }
        synchronized (this) {
            ArrayList<File> files = new ArrayList<>();
            for (URL u : loaderUrls) {
                File f = QoreURLClassLoader.urlToFile(u);
                if (f != null && !roots.containsKey(f.getPath())) {
                    files.add(f);
                }
            }
            Set<String> rv = QoreURLClassLoader.reachableRoots(files);
            trusted = new Object[] {rv, version, loaderUrls.length};
            return rv;
        }
    }

    //! Returns the locations to give a compiler for the locations of the class loader
    /** An entry of trusted code is used as it is.  An entry of restricted code is used only if \a reachable, and if
        the policy allows it: a jar as it is, and for a directory a copy of the class files and Kotlin module files below
        it that the policy allows, with no other file, so that the compiler cannot read a file that the policy denies,
        and does not see files that make it load code.  The copies are in a new private directory, which
        releaseCopies() removes; the work for all of them is limited, and nothing is left if it fails.

        @param locations the locations, as the class loader has them
        @param reachable true if the calling code can reach the Program of the class loader

        @throw IOException a copy cannot be made, or is larger than the limits
    */
    List<File> compilerLocations(List<File> locations, boolean reachable) throws IOException {
        ArrayList<File> rv = new ArrayList<>(locations.size());
        long[] budget = {MAX_COMPILER_FILES, MAX_COMPILER_BYTES, maxCompilerEntries()};
        Path dir = null;
        int n = 0;
        try {
            for (File location : locations) {
                QoreSandbox.ClasspathPolicy[] policies = roots.get(location.getPath());
                if (policies == null) {
                    rv.add(location);
                    continue;
                }
                if (!reachable) {
                    continue;
                }
                Path real;
                try {
                    real = location.toPath().toRealPath();
                } catch (IOException | InvalidPathException | SecurityException e) {
                    continue;
                }
                if (!checkReal(real, policies)) {
                    continue;
                }
                if (!Files.isDirectory(real)) {
                    rv.add(location);
                    continue;
                }
                if (dir == null) {
                    // only its owner can use it
                    dir = Files.createTempDirectory("qore-classpath-");
                    copies.add(dir);
                }
                Path copy = Files.createDirectory(dir.resolve("cp" + n++));
                copyAllowed(real, copy, "", policies, budget, new HashSet<>(), 0);
                rv.add(copy.toFile());
            }
        } catch (Throwable e) {
            if (dir != null) {
                copies.remove(dir);
                deleteTree(dir);
            }
            throw e;
        }
        return rv;
    }

    //! Removes the private directories of the given locations; a location that is not a copy is not changed
    void releaseCopies(List<File> locations) {
        for (File location : locations) {
            File parent = location.getParentFile();
            if (parent != null) {
                Path dir = parent.toPath();
                if (copies.remove(dir)) {
                    deleteTree(dir);
                }
            }
        }
    }

    //! Returns the most directory entries to visit for a compiler; a system property can only lower it
    private static long maxCompilerEntries() {
        Integer limit = Integer.getInteger(MAX_ENTRIES_PROP);
        return limit != null && limit > 0 && limit < MAX_COMPILER_ENTRIES ? limit : MAX_COMPILER_ENTRIES;
    }

    //! Deletes what it can of a directory tree, without following links; never throws
    private static void deleteTree(Path path) {
        try {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                    for (Path entry : entries) {
                        deleteTree(entry);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // the directory is still tried below
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            // nothing more can be done for this path
        }
    }

    //! Copies the allowed class and module files below a checked directory
    /** Every directory entry that is visited is charged to \a budget, as the links of a tree can make the number of
        paths far larger than the number of files.

        @param budget the files, bytes and directory entries that remain
        @param active the real paths of the directories being copied, so that a link cycle ends

        @throw IOException a limit has been exceeded, or a file cannot be copied
    */
    private static void copyAllowed(Path src, Path dest, String relative, QoreSandbox.ClasspathPolicy[] policies,
            long[] budget, Set<Path> active, int depth) throws IOException {
        if (depth > MAX_COMPILER_DEPTH || !active.add(src)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(src)) {
            for (Path entry : entries) {
                if (--budget[2] < 0) {
                    throw new IOException("the classpath has too many directory entries to give a compiler");
                }
                String name = entry.getFileName().toString();
                Path real;
                try {
                    real = entry.toRealPath();
                } catch (IOException | SecurityException e) {
                    continue;
                }
                if (!checkReal(real, policies)) {
                    continue;
                }
                if (Files.isDirectory(real)) {
                    Path sub = dest.resolve(name);
                    Files.createDirectories(sub);
                    copyAllowed(real, sub, relative + name + "/", policies, budget, active, depth + 1);
                } else if (Files.isRegularFile(real) && isCompilerFile(relative, name)) {
                    copyFile(real, dest.resolve(name), budget);
                }
            }
        } finally {
            active.remove(src);
        }
    }

    //! Returns true for a class file, or a Kotlin module file directly in \c META-INF
    private static boolean isCompilerFile(String relative, String name) {
        if (relative.equals("META-INF/")) {
            return name.endsWith(".kotlin_module");
        }
        return name.endsWith(".class") && !relative.startsWith("META-INF/");
    }

    //! Copies a checked file, within the budget, without following a link that replaced it
    private static void copyFile(Path real, Path target, long[] budget) throws IOException {
        if (--budget[0] < 0) {
            throw new IOException("the classpath has too many files to give a compiler");
        }
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS);
                OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                budget[1] -= n;
                if (budget[1] < 0) {
                    throw new IOException("the classpath has too much data to give a compiler");
                }
                out.write(buf, 0, n);
            }
        }
    }
}
