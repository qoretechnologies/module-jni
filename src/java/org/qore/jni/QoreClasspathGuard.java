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
import java.util.HashMap;
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
    //! the most files in the outstanding compiler copies of a Program
    static final int MAX_COMPILER_FILES = 100000;
    //! the most bytes in the outstanding compiler copies of a Program
    static final long MAX_COMPILER_BYTES = 1L << 30;
    //! the most directory entries visited for the outstanding compiler copies of a Program
    static final int MAX_COMPILER_ENTRIES = 200000;
    //! the most outstanding private copy directories of a Program
    static final int MAX_OUTSTANDING_COPIES = 32;

    //! the kinds of resources that a copy is charged for, and the index of its Program in the record of a copy
    private static final int CHARGE_FILES = 0;
    private static final int CHARGE_BYTES = 1;
    private static final int CHARGE_ENTRIES = 2;
    private static final int CHARGE_COPIES = 3;
    private static final int CHARGE_PROGRAM = 4;
    private static final int CHARGE_SIZE = 5;
    //! the names of the kinds of resources, for errors
    private static final String[] CHARGE_NAMES = {"files", "bytes", "directory entries", "copies"};
    //! what the outstanding copies of each Program use; guarded by itself
    private static final HashMap<Long, long[]> OUTSTANDING = new HashMap<>();

    //! the deepest directory level that is copied for a compiler
    private static final int MAX_COMPILER_DEPTH = 256;
    //! a system property that can lower MAX_COMPILER_ENTRIES
    private static final String MAX_ENTRIES_PROP = "qore.jni.compiler.maxEntries";

    //! the policies of each path that restricted code added, as the class loader's URLs name it
    private final ConcurrentHashMap<String, QoreSandbox.ClasspathPolicy[]> roots = new ConcurrentHashMap<>();
    //! the roots that are directories which the class loader reads files below
    private final Set<String> directories = ConcurrentHashMap.newKeySet();
    //! the private directories of the compiler copies that have not been released, with what each one has used
    private final ConcurrentHashMap<Path, long[]> copies = new ConcurrentHashMap<>();
    //! true once the class loader has been closed: no new copy is made
    private volatile boolean closed;
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

    //! Returns the policies of the entries that contain a file, up to the nearest entry of trusted code
    /** @return the policies; an empty list if the nearest entry is one of trusted code; null if no entry contains it
    */
    private ArrayList<QoreSandbox.ClasspathPolicy[]> enclosingPolicies(File file, boolean jar,
            Set<String> trusted_roots) {
        ArrayList<QoreSandbox.ClasspathPolicy[]> rv = null;
        for (File f = file; f != null; f = jar ? null : f.getParentFile()) {
            String path = f.getPath();
            QoreSandbox.ClasspathPolicy[] policies = roots.get(path);
            if (policies != null) {
                if (rv == null) {
                    rv = new ArrayList<>(2);
                }
                rv.add(policies);
            } else if (trusted_roots.contains(path)) {
                return rv == null ? new ArrayList<>(0) : rv;
            }
        }
        return rv;
    }

    //! Returns true if every one of the sets of policies allows reading a real path
    private static boolean checkAll(Path real, List<QoreSandbox.ClasspathPolicy[]> applied) {
        for (QoreSandbox.ClasspathPolicy[] policies : applied) {
            if (!checkReal(real, policies)) {
                return false;
            }
        }
        return true;
    }

    //! Returns the locations to give a compiler for the locations of the class loader
    /** An entry of trusted code is used as it is.  An entry of restricted code is used only if \a reachable, and if
        every policy that a lookup of it would apply allows it: a jar as it is, and for a directory a copy of the class
        files and Kotlin module files below it that those policies allow, with no other file, so that the compiler
        cannot read a file that a policy denies, and does not see files that make it load code.  The copies are in a new
        private directory, which releaseCopies() removes.  What they use is charged to the Program of the class loader
        until they are released, so that the limits hold for a compilation across a chain of class loaders and for
        copies that are kept; nothing is left if a copy fails.

        @param locations the locations, as the class loader has them
        @param reachable true if the calling code can reach the Program of the class loader
        @param program the Program of the class loader, which the copies are charged to
        @param loaderUrls the URLs of the class loader's classpath

        @throw IOException a copy cannot be made, is over a limit, or the class loader has been closed
    */
    List<File> compilerLocations(List<File> locations, boolean reachable, long program, URL[] loaderUrls)
            throws IOException {
        ArrayList<File> rv = new ArrayList<>(locations.size());
        Set<String> trusted_roots = trustedRoots(loaderUrls);
        Path dir = null;
        long[] charged = null;
        int n = 0;
        try {
            for (File location : locations) {
                ArrayList<QoreSandbox.ClasspathPolicy[]> applied = enclosingPolicies(location,
                    !location.isDirectory(), trusted_roots);
                if (applied == null) {
                    continue;
                }
                if (applied.isEmpty()) {
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
                if (!checkAll(real, applied)) {
                    continue;
                }
                if (!Files.isDirectory(real)) {
                    rv.add(location);
                    continue;
                }
                if (dir == null) {
                    charged = new long[CHARGE_SIZE];
                    charged[CHARGE_PROGRAM] = program;
                    charge(charged, CHARGE_COPIES, 1);
                    // only its owner can use it
                    dir = Files.createTempDirectory("qore-classpath-");
                    copies.put(dir, charged);
                    // after it is recorded: a close either sees it or is seen here
                    if (closed) {
                        throw new IOException("the class loader has been closed");
                    }
                }
                Path copy = Files.createDirectory(dir.resolve("cp" + n++));
                copyAllowed(real, copy, location, "", trusted_roots, charged, new HashSet<>(), 0);
                rv.add(copy.toFile());
            }
        } catch (Throwable e) {
            if (dir != null) {
                releaseDirectory(dir);
            } else if (charged != null) {
                refund(charged);
            }
            throw e;
        }
        return rv;
    }

    //! Returns true if one of the locations is a copy that this guard holds
    boolean holdsCopy(List<File> locations) {
        for (File location : locations) {
            File parent = location.getParentFile();
            if (parent != null && copies.containsKey(parent.toPath())) {
                return true;
            }
        }
        return false;
    }

    //! Removes the private directories of the given locations; a location that is not a copy is not changed
    void releaseCopies(List<File> locations) {
        for (File location : locations) {
            File parent = location.getParentFile();
            if (parent != null) {
                releaseDirectory(parent.toPath());
            }
        }
    }

    //! Removes every copy, and refuses new ones: the class loader is closed
    void releaseAllCopies() {
        closed = true;
        for (Path dir : copies.keySet()) {
            releaseDirectory(dir);
        }
    }

    //! Removes a private directory that this guard holds, and returns what it used to its Program
    private void releaseDirectory(Path dir) {
        long[] charged = copies.remove(dir);
        if (charged != null) {
            deleteTree(dir);
            refund(charged);
        }
    }

    //! Charges \a n of a resource to the Program of a copy; raises an error if that is over the limit of the Program
    private static void charge(long[] charged, int kind, long n) throws IOException {
        synchronized (OUTSTANDING) {
            long[] used = OUTSTANDING.computeIfAbsent(charged[CHARGE_PROGRAM], k -> new long[CHARGE_PROGRAM]);
            if (used[kind] + n > chargeLimit(kind)) {
                if (isUnused(used)) {
                    OUTSTANDING.remove(charged[CHARGE_PROGRAM]);
                }
                throw new IOException("the classpath is over the limit of " + CHARGE_NAMES[kind]
                    + " that compilers of a Program can use at the same time");
            }
            used[kind] += n;
        }
        charged[kind] += n;
    }

    //! Returns what a copy used to its Program
    private static void refund(long[] charged) {
        synchronized (OUTSTANDING) {
            long[] used = OUTSTANDING.get(charged[CHARGE_PROGRAM]);
            if (used == null) {
                return;
            }
            for (int i = 0; i < CHARGE_PROGRAM; ++i) {
                used[i] -= charged[i];
                charged[i] = 0;
            }
            if (isUnused(used)) {
                OUTSTANDING.remove(charged[CHARGE_PROGRAM]);
            }
        }
    }

    //! Returns true if a Program has nothing outstanding
    private static boolean isUnused(long[] used) {
        for (long v : used) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    //! Returns the limit of a kind of resource for the outstanding copies of a Program
    private static long chargeLimit(int kind) {
        switch (kind) {
            case CHARGE_FILES:
                return MAX_COMPILER_FILES;
            case CHARGE_BYTES:
                return MAX_COMPILER_BYTES;
            case CHARGE_ENTRIES:
                return maxCompilerEntries();
            default:
                return MAX_OUTSTANDING_COPIES;
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
    /** Every directory entry that is visited is charged, as the links of a tree can make the number of paths far larger
        than the number of files.  Each entry is judged as a lookup of it is: by every policy of the entries of the
        classpath that contain it.

        @param src the real path of the directory
        @param dest the directory of the copy
        @param logical the directory as the class loader's URLs name it
        @param relative the path of the directory below the classpath entry, with a trailing slash
        @param trusted_roots the paths that entries of trusted code reach
        @param charged what the copy has used
        @param active the real paths of the directories being copied, so that a link cycle ends

        @throw IOException a limit has been exceeded, or a file cannot be copied
    */
    private void copyAllowed(Path src, Path dest, File logical, String relative, Set<String> trusted_roots,
            long[] charged, Set<Path> active, int depth) throws IOException {
        if (depth > MAX_COMPILER_DEPTH || !active.add(src)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(src)) {
            for (Path entry : entries) {
                charge(charged, CHARGE_ENTRIES, 1);
                String name = entry.getFileName().toString();
                Path real;
                try {
                    real = entry.toRealPath();
                } catch (IOException | SecurityException e) {
                    continue;
                }
                File child = new File(logical, name);
                ArrayList<QoreSandbox.ClasspathPolicy[]> applied = enclosingPolicies(child, false, trusted_roots);
                if (applied == null || !checkAll(real, applied)) {
                    continue;
                }
                if (Files.isDirectory(real)) {
                    Path sub = dest.resolve(name);
                    Files.createDirectories(sub);
                    copyAllowed(real, sub, child, relative + name + "/", trusted_roots, charged, active, depth + 1);
                } else if (Files.isRegularFile(real) && isCompilerFile(relative, name)) {
                    copyFile(real, dest.resolve(name), charged);
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

    //! Copies a checked file, within the limits, without following a link that replaced it
    private static void copyFile(Path real, Path target, long[] charged) throws IOException {
        charge(charged, CHARGE_FILES, 1);
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS);
                OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                charge(charged, CHARGE_BYTES, n);
                out.write(buf, 0, n);
            }
        }
    }
}
