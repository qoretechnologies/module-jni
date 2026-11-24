// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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
    so the thread's caller is never consulted.  Each entry is one of three kinds, by the code that added it:
    - a grant of the host: added by code that no sandbox restricted for the filesystem or the network then (trusted
      code, no sandbox yet, or a sandbox that restricted neither); a lookup below it applies no policy, and a policy that
      is set or tightened later does not apply to it
    - transparent: added by code that a sandbox restricts for the network but not the filesystem, which binds no
      filesystem policy and makes no grant; a lookup below it applies the policies of the bound entries that enclose it,
      as if it were not there, and none if no bound entry encloses it
    - bound: added by code that a filesystem policy restricts, with that policy (see bind()); a lookup below it applies
      that policy and the policies of the bound entries that enclose it

    An entry encloses a file if the file is below the entry's path as the class loader's URLs name it, not its real
    path.  The rule keeps an entry of other code from widening by accident what restricted code can read below its own
    entries; it does not contain the code that adds an entry, which its own sandbox governs.

    Below the entries of restricted code, a \c jar: URL with more than one \c "!/" is refused, as the class loader finds
    an entry at the last separator and a connection to the URL opens the archive at the first one.

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
    //! the deepest directory level that is copied for a compiler
    private static final int MAX_COMPILER_DEPTH = 256;

    //! the policies of each path that restricted code added, as the class loader's URLs name it
    private final ConcurrentHashMap<String, QoreSandbox.ClasspathPolicy[]> roots = new ConcurrentHashMap<>();
    //! the roots that are directories which the class loader reads files below
    private final Set<String> directories = ConcurrentHashMap.newKeySet();
    //! the compiler copies of this class loader that have not ended
    private final Set<QoreCompilerClasspath.Copy> copies = ConcurrentHashMap.newKeySet();
    //! true once the class loader has been closed: no new copy is made
    private volatile boolean closed;
    //! the paths of transparent entries, as the class loader's URLs name them
    private final Set<String> transparent = ConcurrentHashMap.newKeySet();
    //! incremented when the roots or the transparent entries change, to invalidate the trusted roots
    private volatile int version;
    //! the paths that trusted and transparent entries reach, the version of the roots and the number of URLs that they
    //! were made for
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
        boolean changed = roots.remove(path) != null;
        if (transparent.remove(path) || changed) {
            ++version;
        }
    }

    //! Marks a new entry of code that a sandbox restricts for the network but not the filesystem as transparent
    /** A bound entry stays bound
    */
    synchronized void markTransparent(String path) {
        if (!roots.containsKey(path) && transparent.add(path)) {
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
                found = found || q.samePolicy(p);
            }
            if (!found) {
                rv.add(p);
            }
        }
        return rv.toArray(new QoreSandbox.ClasspathPolicy[0]);
    }

    //! Returns true if a URL that the class loader found may be used
    /** Every policy of the entries that contain the file applies, up to the nearest entry of trusted code
        (enclosingPolicies(), the rule that compiler copies use as well).  A name with a parent directory element is
        used only if it names the same file as its URL.

        @param url the URL of a class or resource
        @param name the name that was looked up
        @param loaderUrls the URLs of the class loader's classpath
    */
    boolean allows(URL url, String name, URL[] loaderUrls) {
        String spec = url.toString();
        boolean jar = spec.startsWith("jar:");
        // the class loader finds an entry at the last separator, but a connection to the URL opens the archive at the
        // first one, so with two of them the file that is judged need not be the file that is read
        boolean two_separators = false;
        if (jar) {
            int sep = spec.lastIndexOf("!/");
            if (sep < 0) {
                return false;
            }
            two_separators = spec.indexOf("!/") != sep;
            spec = spec.substring(4, sep);
        }
        if (!spec.startsWith("file:")) {
            // restricted code only adds files: anything else can only come from an entry of trusted code
            return isTrustedUrl(url.toString(), loaderUrls);
        }
        File file = localFile(spec);
        if (file == null) {
            return false;
        }
        ArrayList<QoreSandbox.ClasspathPolicy[]> applied = enclosingPolicies(file, jar, reachedRoots(loaderUrls));
        // in no known entry: it cannot be attributed to any code
        if (applied == null) {
            return false;
        }
        if (applied.isEmpty()) {
            return true;
        }
        // only below the entries of restricted code, where a policy judges the file
        if (two_separators) {
            return false;
        }
        Path real;
        try {
            real = file.toPath().toRealPath();
            if (!jar && hasParentElement(name)) {
                File base = findBase(file, name);
                if (base == null || !new File(base, name).toPath().toRealPath().equals(real)) {
                    return false;
                }
            }
        } catch (IOException | InvalidPathException | SecurityException e) {
            return false;
        }
        return checkAll(real, applied);
    }

    //! Returns the local file of a file URL, or null if it names no local file
    /** A manifest \c Class-Path entry can name the local host as its authority (\c "file://localhost/..."), and the
        class loader then finds files below it with that authority; any other authority names no local file
    */
    static File localFile(String spec) {
        try {
            URI uri = new URI(spec);
            String authority = uri.getRawAuthority();
            if (authority != null && !authority.isEmpty()) {
                // as new File(URI) refuses a query or fragment, which a URL of a file never has
                if (!authority.equalsIgnoreCase("localhost") || uri.getRawQuery() != null
                        || uri.getRawFragment() != null) {
                    return null;
                }
                uri = new URI("file:" + uri.getRawPath());
            }
            return new File(uri);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    //! Returns the nearest entry of restricted code below which \a name is the normalized path \a file, or null
    private File findBase(File file, String name) {
        for (File f = file; f != null; f = f.getParentFile()) {
            if (roots.containsKey(f.getPath()) && isBaseOf(f, name, file)) {
                return f;
            }
        }
        return null;
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

    //! Returns the paths that the entries of trusted code reach, and those that transparent entries reach, with the
    //! manifests of their jars
    /** @return the trusted paths, then the transparent paths
    */
    @SuppressWarnings("unchecked")
    private Set<String>[] reachedRoots(URL[] loaderUrls) {
        Object[] current = trusted;
        if (current != null && (Integer) current[1] == version && (Integer) current[2] == loaderUrls.length) {
            return (Set<String>[]) current[0];
        }
        synchronized (this) {
            ArrayList<File> grants = new ArrayList<>();
            ArrayList<File> others = new ArrayList<>();
            for (URL u : loaderUrls) {
                File f = QoreURLClassLoader.urlToFile(u);
                if (f != null && !roots.containsKey(f.getPath())) {
                    (transparent.contains(f.getPath()) ? others : grants).add(f);
                }
            }
            Set<String>[] rv = (Set<String>[]) new Set<?>[] {QoreURLClassLoader.reachableRoots(grants),
                QoreURLClassLoader.reachableRoots(others)};
            // a path that a grant also reaches is a grant
            rv[1].removeAll(rv[0]);
            trusted = new Object[] {rv, version, loaderUrls.length};
            return rv;
        }
    }

    //! Returns the policies of the entries that contain a file, up to the nearest entry of trusted code
    /** The one rule for lookups and compiler copies.  A transparent entry is passed through, also a jar, so the bound
        entries that enclose it apply; a bound jar or a jar of trusted code is an entry itself, which ends the walk.

        @param reached the paths that trusted entries reach, then those that transparent entries reach

        @return the policies; an empty list if the nearest entry is one of trusted code, or if only transparent entries
        contain the file; null if no entry contains it
    */
    private ArrayList<QoreSandbox.ClasspathPolicy[]> enclosingPolicies(File file, boolean jar, Set<String>[] reached) {
        ArrayList<QoreSandbox.ClasspathPolicy[]> rv = null;
        boolean in_transparent = false;
        for (File f = file; f != null; f = f.getParentFile()) {
            String path = f.getPath();
            QoreSandbox.ClasspathPolicy[] policies = roots.get(path);
            boolean pass = false;
            if (policies != null) {
                if (rv == null) {
                    rv = new ArrayList<>(2);
                }
                rv.add(policies);
            } else if (reached[0].contains(path)) {
                return rv == null ? new ArrayList<>(0) : rv;
            } else if (reached[1].contains(path)) {
                in_transparent = true;
                pass = true;
            }
            // a jar is an entry itself, unless it is transparent
            if (jar && f == file && !pass) {
                break;
            }
        }
        return rv == null && in_transparent ? new ArrayList<>(0) : rv;
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

    //! Adds the locations of the class loader to a compiler classpath
    /** An entry of trusted code is used as it is.  An entry of restricted code is used only if \a reachable, and if
        every policy that a lookup of it would apply allows it: a jar as it is, and for a directory a copy of the class
        files and Kotlin module files below it that those policies allow, with no other file, so that the compiler
        cannot read a file that a policy denies, and does not see files that make it load code.  The copies of this
        class loader are in one private directory, which \a classpath owns; nothing is left if it fails.

        @param classpath the compiler classpath
        @param locations the locations, as the class loader has them
        @param reachable true if the calling code can reach the Program of the class loader
        @param budget what the copies of the Program use
        @param loaderUrls the URLs of the class loader's classpath

        @throw IOException a copy cannot be made, is over a limit, or has been ended
    */
    void addCompilerLocations(QoreCompilerClasspath classpath, List<File> locations, boolean reachable,
            QoreCompilerClasspath.Budget budget, URL[] loaderUrls) throws IOException {
        Set<String>[] trusted_roots = reachedRoots(loaderUrls);
        QoreCompilerClasspath.Copy copy = null;
        int n = 0;
        try {
            for (File location : locations) {
                ArrayList<QoreSandbox.ClasspathPolicy[]> applied = enclosingPolicies(location,
                    !location.isDirectory(), trusted_roots);
                if (applied == null) {
                    continue;
                }
                if (applied.isEmpty()) {
                    classpath.addLocation(location);
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
                    classpath.addLocation(location);
                    continue;
                }
                if (copy == null) {
                    copy = budget.open(this);
                    copies.add(copy);
                    classpath.addCopy(copy);
                    // after it is recorded: a close either sees it or is seen here
                    if (closed) {
                        throw new IOException("the class loader has been closed");
                    }
                }
                Path dest = copy.newLocation("cp" + n++);
                copyAllowed(copy, real, dest, location, "", trusted_roots, new HashSet<>(), 0);
                classpath.addLocation(dest.toFile());
            }
        } catch (Throwable e) {
            if (copy != null) {
                // a failure of the cleanup is suppressed in the original error
                QoreCompilerClasspath.cleanup(e, copy::abort);
            }
            throw e;
        }
    }

    //! Forgets a copy that has ended
    void forgetCopy(QoreCompilerClasspath.Copy copy) {
        copies.remove(copy);
    }

    //! Ends every copy of this class loader, and refuses new ones: the class loader is closed
    void closeCopies() {
        closed = true;
        QoreCompilerClasspath.endAll(copies, false);
    }

    //! Copies the allowed class and module files below a checked directory
    /** Every directory entry that is visited is charged, as the links of a tree can make the number of paths far larger
        than the number of files.  Each entry is judged as a lookup of it is: by every policy of the entries of the
        classpath that contain it.

        @param copy the copy, which fails its next charge once it has been released
        @param src the real path of the directory
        @param dest the directory of the copy
        @param logical the directory as the class loader's URLs name it
        @param relative the path of the directory below the classpath entry, with a trailing slash
        @param trusted_roots the paths that entries of trusted code reach, then those of transparent entries
        @param active the real paths of the directories being copied, so that a link cycle ends

        @throw IOException a limit has been exceeded, the copy was released, or a file cannot be copied
    */
    private void copyAllowed(QoreCompilerClasspath.Copy copy, Path src, Path dest, File logical, String relative,
            Set<String>[] trusted_roots, Set<Path> active, int depth) throws IOException {
        if (depth > MAX_COMPILER_DEPTH || !active.add(src)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(src)) {
            for (Path entry : entries) {
                copy.charge(QoreCompilerClasspath.ENTRIES, 1);
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
                    QoreCompilerClasspath.Copy.createBelow(sub);
                    copyAllowed(copy, real, sub, child, relative + name + "/", trusted_roots, active, depth + 1);
                } else if (Files.isRegularFile(real) && isCompilerFile(relative, name)) {
                    copy.copyFile(real, dest.resolve(name));
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
}
