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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

//! Makes the archives and directories of the classpath URL tests, and reads through the class loader APIs (qore#7349)
public class ClasspathUrlProbe {
    //! the name of the class in each marker directory and archive
    public static final String MARKER_CLASS = "ClasspathUrlMarker";
    //! the name of the resource in each marker directory and archive
    public static final String MARKER_RESOURCE = "classpath-url-marker.txt";

    private static final Map<String, byte[]> classes = new ConcurrentHashMap<>();

    //! Writes an archive with no content and the given manifest Class-Path attribute
    public static void writeManifestJar(String path, String classPath) throws IOException {
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        try (OutputStream os = Files.newOutputStream(p); JarOutputStream j = new JarOutputStream(os, m)) {
            j.flush();
        }
    }

    //! Writes a directory with the marker resource and class
    public static void writeMarkerDir(String dir, String marker) throws IOException {
        Path d = Path.of(dir);
        Files.createDirectories(d);
        Files.writeString(d.resolve(MARKER_RESOURCE), marker);
        Files.write(d.resolve(MARKER_CLASS + ".class"), markerClass(marker));
    }

    //! Writes an archive with the marker resource and class
    public static void writeMarkerJar(String path, String marker) throws IOException {
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        byte[] cls = markerClass(marker);
        try (OutputStream os = Files.newOutputStream(p); JarOutputStream j = new JarOutputStream(os, m)) {
            j.putNextEntry(new JarEntry(MARKER_RESOURCE));
            j.write(marker.getBytes(StandardCharsets.UTF_8));
            j.closeEntry();
            j.putNextEntry(new JarEntry(MARKER_CLASS + ".class"));
            j.write(cls);
            j.closeEntry();
        }
    }

    //! Writes an archive with the marker resource and class and a jar index entry
    public static void writeIndexedJar(String path, String marker) throws IOException {
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        byte[] cls = markerClass(marker);
        try (OutputStream os = Files.newOutputStream(p); JarOutputStream j = new JarOutputStream(os, m)) {
            j.putNextEntry(new JarEntry("META-INF/INDEX.LIST"));
            j.write("JarIndex-Version: 1.0\n\n".getBytes(StandardCharsets.UTF_8));
            j.closeEntry();
            j.putNextEntry(new JarEntry(MARKER_RESOURCE));
            j.write(marker.getBytes(StandardCharsets.UTF_8));
            j.closeEntry();
            j.putNextEntry(new JarEntry(MARKER_CLASS + ".class"));
            j.write(cls);
            j.closeEntry();
        }
    }

    //! Adds an archive by a route as add() does, with no JDK version counted as verified for the duration of the call
    public static String addOnUnverifiedJdk(ClassLoader loader, String route, String jar) throws Exception {
        Field field = QoreURLClassLoader.class.getDeclaredField("VERIFIED_CLASS_PATH_JDK_FEATURES");
        field.setAccessible(true);
        int[] features = (int[])field.get(null);
        int[] saved = features.clone();
        Arrays.fill(features, -1);
        try {
            return add(loader, route, jar);
        } finally {
            System.arraycopy(saved, 0, features, 0, saved.length);
        }
    }

    //! Calls QoreSandbox.checkCompilerClasspath(); returns "ok: <number of files>" or "refused: <exception>"
    public static String compilerClasspath(String classpath) {
        try {
            return "ok: " + QoreSandbox.checkCompilerClasspath(classpath).size();
        } catch (RuntimeException e) {
            return "refused: " + e;
        }
    }

    //! Returns true if the policy captured on this thread restricts the network, and the static check agrees
    public static boolean capturedRestrictsNetwork() {
        try (QoreSandbox.Policy policy = QoreSandbox.capture()) {
            boolean captured = policy.restrictsNetwork();
            if (captured != QoreSandbox.restrictsNetwork()) {
                throw new IllegalStateException("the captured policy and the current thread disagree");
            }
            return captured;
        }
    }

    //! Returns the marker that each read API of the class loader gives, or "none"
    /** The values are, in order: \c getResourceAsStream(), \c getResource(), \c getResources() (each distinct value,
        sorted and joined with a comma) and a class load.
    */
    public static String[] reads(ClassLoader loader) {
        return new String[] {readStream(loader), readResource(loader), readResources(loader), readClass(loader)};
    }

    //! Returns what the same read APIs give for an archive in a class loader of the JDK with no parent classpath
    public static String[] plainReads(String jar) throws IOException {
        URL url = URI.create("jar:" + Path.of(jar).toUri() + "!/").toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {url}, null)) {
            return reads(loader);
        }
    }

    //! Adds an archive to a class loader by one of its Java routes; returns "ok" or "refused: <exception>"
    /** @param loader the class loader
        @param route \c addPath, \c addPathOrig, \c addURL, \c addURLString (\a jar is the URL itself), \c addWildcard
        (the archives of the directory of \a jar) or \c addParentPath (of a class loader derived from \a loader, so that
        only \a loader changes)
        @param jar the path of the archive
    */
    public static String add(ClassLoader loader, String route, String jar) {
        QoreURLClassLoader target = (QoreURLClassLoader)loader;
        try {
            switch (route) {
                case "addPath":
                    target.addPath(jar);
                    break;
                case "addPathOrig":
                    target.addPathOrig(jar);
                    break;
                case "addURL": {
                    Method m = QoreURLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    m.setAccessible(true);
                    m.invoke(target, Path.of(jar).toUri().toURL());
                    break;
                }
                case "addURLString": {
                    Method m = QoreURLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    m.setAccessible(true);
                    m.invoke(target, URI.create(jar).toURL());
                    break;
                }
                case "addWildcard":
                    target.addWildcard(new File(jar).getParentFile(), "*.jar");
                    break;
                case "addParentPath":
                    ClassLoaderAccessProbe.newDerived(target).addParentPath(jar);
                    break;
                default:
                    throw new IllegalArgumentException("unknown route: " + route);
            }
            return "ok";
        } catch (InvocationTargetException e) {
            return "refused: " + e.getCause();
        } catch (Exception | LinkageError e) {
            return "refused: " + e;
        }
    }

    //! Returns the absolute path as it is written in a file URL, with no trailing slash
    public static String encodedPath(String path) {
        String rv = Path.of(path).toUri().getRawPath();
        return rv.endsWith("/") ? rv.substring(0, rv.length() - 1) : rv;
    }

    private static String text(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readStream(ClassLoader loader) {
        try {
            InputStream in = loader.getResourceAsStream(MARKER_RESOURCE);
            return in == null ? "none" : text(in);
        } catch (IOException | RuntimeException e) {
            return "none";
        }
    }

    private static String readResource(ClassLoader loader) {
        try {
            URL url = loader.getResource(MARKER_RESOURCE);
            return url == null ? "none" : text(url.openStream());
        } catch (IOException | RuntimeException e) {
            return "none";
        }
    }

    private static String readResources(ClassLoader loader) {
        try {
            TreeSet<String> found = new TreeSet<>();
            for (URL url : Collections.list(loader.getResources(MARKER_RESOURCE))) {
                try {
                    found.add(text(url.openStream()));
                } catch (IOException | RuntimeException e) {
                    // a URL that cannot be read gives no marker
                }
            }
            return found.isEmpty() ? "none" : String.join(",", found);
        } catch (IOException | RuntimeException e) {
            return "none";
        }
    }

    private static String readClass(ClassLoader loader) {
        try {
            return (String)loader.loadClass(MARKER_CLASS).getField("MARKER").get(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return "none";
        }
    }

    //! Returns the class file of the marker class with the given marker
    private static byte[] markerClass(String marker) throws IOException {
        byte[] rv = classes.get(marker);
        if (rv != null) {
            return rv;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("no Java compiler is available");
        }
        Path dir = Files.createTempDirectory("classpath-url-probe");
        try {
            Path src = dir.resolve(MARKER_CLASS + ".java");
            Files.writeString(src, "public class " + MARKER_CLASS + " { public static final String MARKER = \""
                + marker + "\"; }\n");
            int rc = compiler.run(null, null, null, "-proc:none", "-d", dir.toString(), src.toString());
            if (rc != 0) {
                throw new IOException("cannot compile the marker class: " + rc);
            }
            rv = Files.readAllBytes(dir.resolve(MARKER_CLASS + ".class"));
        } finally {
            removeTree(dir);
        }
        classes.put(marker, rv);
        return rv;
    }

    private static void removeTree(Path dir) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(paths::add);
        }
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }
}
