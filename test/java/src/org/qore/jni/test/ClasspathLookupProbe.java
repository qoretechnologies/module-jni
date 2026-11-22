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
