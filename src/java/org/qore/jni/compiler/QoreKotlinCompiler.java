/*
    QoreKotlinCompiler.java

    Compile Kotlin source code with support for dynamic Qore class imports.

    This class provides compilation of Kotlin source code using the embedded
    Kotlin compiler with access to dynamically-generated Qore classes through
    the QoreURLClassLoader, enabling Kotlin code to use imports like:
        import qore.OMQ.*
        import qore.OMQ.UserApi.*

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

import org.qore.jni.QoreCompilerClasspath;
import org.qore.jni.QoreSandbox;
import org.qore.jni.QoreURLClassLoader;

/**
 * Compile Kotlin source code with support for dynamic Qore class imports.
 *
 * This class wraps the Kotlin compiler to enable compilation of Kotlin code
 * that uses dynamic imports from Qore (e.g., import qore.OMQ.*).
 *
 * The compiler generates stub classes for the dynamic imports and makes them
 * available during compilation through a temporary classpath directory.
 */
public class QoreKotlinCompiler implements AutoCloseable {
    private final QoreURLClassLoader classLoader;
    private final List<String> options;
    private final List<String> additionalBaseClasses = new ArrayList<>();
    private Path tempDir;
    private volatile boolean closed = false;

    /**
     * Construct a new instance which delegates to a new Qore classloader.
     */
    public QoreKotlinCompiler() {
        this((List<String>) null);
    }

    /**
     * Construct a new instance which delegates to a new Qore classloader.
     *
     * @param options The compiler options (passed to kotlinc)
     */
    public QoreKotlinCompiler(List<String> options) {
        this(QoreURLClassLoader.getCurrent(), options);
    }

    /**
     * Construct a new instance which delegates to the named class loader.
     *
     * @param loader  the application ClassLoader
     * @param options The compiler options (passed to kotlinc)
     */
    public QoreKotlinCompiler(QoreURLClassLoader loader, List<String> options) {
        // the class loader shares the Program pointer of loader, so that it is cleared when the Program is deleted
        this.classLoader = QoreURLClassLoader.createDerived(loader);
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    /**
     * Compile Kotlin source code and return the compiled bytecode.
     *
     * @param sources A map of qualified class names to Kotlin source code
     * @return A map of class names to compiled bytecode
     * @throws QoreKotlinCompilerException if compilation fails
     */
    public synchronized Map<String, byte[]> compile(Map<String, String> sources)
            throws QoreKotlinCompilerException {
        return compile(sources, null);
    }

    /**
     * Compile Kotlin source code and return the compiled bytecode.
     *
     * @param sources         A map of qualified class names to Kotlin source code
     * @param injectedClasses A map of class binary names to bytecode for dependencies
     * @return A map of class names to compiled bytecode
     * @throws QoreKotlinCompilerException if compilation fails
     */
    public synchronized Map<String, byte[]> compile(Map<String, String> sources,
            Map<String, byte[]> injectedClasses) throws QoreKotlinCompilerException {

        if (closed) {
            throw new IllegalStateException("Compiler has been closed");
        }

        // every name is checked before any directory is created or file written
        checkNames(sources.keySet());
        if (injectedClasses != null) {
            checkNames(injectedClasses.keySet());
        }
        final boolean restricted = QoreSandbox.restrictsFilesystem();
        // the classpath of this compilation for a restricted caller, which owns its private copies
        QoreCompilerClasspath compilerClasspath = null;

        try {
            // Create temporary directories for source and output
            tempDir = Files.createTempDirectory("qore-kotlin-", privateAttributes("rwx------"));
            Path sourceDir = tempDir.resolve("src");
            // one file: the names of the classes, which the source determines, are never paths of the filesystem; it
            // is created here, as a new file that is not a link, for the compiler to write
            Path outputJar = Files.createFile(tempDir.resolve("output.jar"), privateAttributes("rw-------"));
            Path stubsDir = tempDir.resolve("stubs");
            Files.createDirectories(sourceDir);
            Files.createDirectories(stubsDir);

            // Write source files
            List<String> sourceFiles = new ArrayList<>();
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String className = entry.getKey();
                String source = entry.getValue();

                // Convert class name to path (com.foo.Bar -> com/foo/Bar.kt)
                Path sourceFile = containedPath(sourceDir, className, ".kt");
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, source, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                sourceFiles.add(sourceFile.toString());
            }

            // Write injected class dependencies (e.g., from Qorus classes table)
            if (injectedClasses != null && !injectedClasses.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : injectedClasses.entrySet()) {
                    String binName = entry.getKey();
                    byte[] bytecode = entry.getValue();
                    writeClassFile(stubsDir, binName, bytecode);
                }
            }

            // Generate stub classes for dynamic Qore imports
            generateDynamicStubs(stubsDir, sources);

            // Build classpath - stubs directory first, then URLs from class loader
            StringBuilder classpath = new StringBuilder();
            classpath.append(stubsDir.toString());

            // Add URLs from the whole class loader CHAIN, not just this one.
            //
            // The constructor wraps the loader it is given in a fresh child
            // (`QoreURLClassLoader.createDerived(loader)`), and
            // `URLClassLoader.getURLs()` reports only the URLs of the loader it
            // is called on - never the parent's. Every path the caller added
            // with `QoreURLClassLoader.addPath()` therefore sat on the parent
            // and was invisible here, so the compile classpath contained the
            // stubs directory and nothing else.
            //
            // That is why Kotlin could not resolve any Qorus class while Java
            // compiled the identical source: QoreJavaCompiler reads
            // `loader.getURLs()` - the parent - when it populates the file
            // manager's CLASS_PATH.
            if (restricted) {
                // only the files that a class loader of the module has checked, as they were checked, with a private
                // copy of the files that the policies of entries of restricted code allow below a directory; the
                // compiler's classpath is one string, so an entry cannot contain its separator; checked for the
                // entries themselves as well, as the path of a private copy has no separator
                for (ClassLoader cl = classLoader; cl != null; cl = cl.getParent()) {
                    if (cl instanceof QoreURLClassLoader) {
                        for (File location : ((QoreURLClassLoader) cl).getCompilerLocations()) {
                            checkClasspathEntry(location);
                        }
                    }
                }
                compilerClasspath = classLoader.openCompilerClasspath();
                for (File location : compilerClasspath.getLocations()) {
                    checkClasspathEntry(location);
                    classpath.append(File.pathSeparator).append(location.getPath());
                }
            } else {
                for (ClassLoader cl = classLoader; cl != null; cl = cl.getParent()) {
                    if (cl instanceof java.net.URLClassLoader) {
                        for (java.net.URL url : ((java.net.URLClassLoader) cl).getURLs()) {
                            String entry = urlToClasspathEntry(url);
                            if (entry != null) {
                                classpath.append(File.pathSeparator).append(entry);
                            }
                        }
                    }
                }
            }

            // Add qore-jni.jar for base classes like QoreJavaClassBase
            String omqDir = System.getenv("OMQ_DIR");
            if (omqDir != null && !omqDir.isEmpty()) {
                String qoreJniJar = omqDir + "/jar/qore-jni.jar";
                if (new File(qoreJniJar).exists()) {
                    classpath.append(File.pathSeparator).append(qoreJniJar);
                }
            }
            // Also check standard install locations
            for (String stdPath : new String[]{
                "/usr/share/qore/java/qore-jni.jar",
                "/usr/local/share/qore/java/qore-jni.jar",
                "/opt/share/qore/java/qore-jni.jar",
            }) {
                if (new File(stdPath).exists()) {
                    classpath.append(File.pathSeparator).append(stdPath);
                    break;
                }
            }

            // Add kotlin-stdlib to classpath
            String kotlinHome = System.getenv("KOTLIN_HOME");
            if (kotlinHome == null || kotlinHome.isEmpty()) {
                kotlinHome = new File("/opt/kotlin/lib/kotlin-stdlib.jar").isFile()
                    ? "/opt/kotlin" : "/usr/share/qore/java/kotlin";
            }
            String kotlinStdlib = kotlinHome + "/lib/kotlin-stdlib.jar";
            if (new File(kotlinStdlib).exists()) {
                classpath.append(File.pathSeparator).append(kotlinStdlib);
            }

            // Set up compiler arguments
            K2JVMCompilerArguments args = new K2JVMCompilerArguments();
            args.setFreeArgs(sourceFiles);
            args.setDestination(outputJar.toString());
            args.setIncludeRuntime(false);
            args.setClasspath(classpath.toString());
            args.setJvmTarget("21");
            args.setNoStdlib(false);
            args.setNoReflect(true);
            if (restricted) {
                // the scripting plugin discovers script definitions on the classpath and runs their code
                args.setDisableDefaultScriptingPlugin(true);
            }

            // Add any custom options
            // Note: K2JVMCompilerArguments doesn't have a direct way to add arbitrary options
            // They would need to be parsed or set individually

            // Set up message collector to capture errors
            StringBuilder errorMessages = new StringBuilder();
            StringBuilder warningMessages = new StringBuilder();
            MessageCollector messageCollector = new MessageCollector() {
                @Override
                public void clear() {}

                @Override
                public boolean hasErrors() {
                    return errorMessages.length() > 0;
                }

                @Override
                public void report(CompilerMessageSeverity severity,
                        String message, CompilerMessageSourceLocation location) {
                    String fullMessage;
                    if (location != null) {
                        fullMessage = String.format("%s:%d:%d: %s",
                            location.getPath(), location.getLine(), location.getColumn(), message);
                    } else {
                        fullMessage = message;
                    }

                    if (severity == CompilerMessageSeverity.ERROR ||
                        severity == CompilerMessageSeverity.EXCEPTION) {
                        if (errorMessages.length() > 0) {
                            errorMessages.append("\n");
                        }
                        errorMessages.append(fullMessage);
                    } else if (severity == CompilerMessageSeverity.WARNING ||
                               severity == CompilerMessageSeverity.STRONG_WARNING) {
                        if (warningMessages.length() > 0) {
                            warningMessages.append("\n");
                        }
                        warningMessages.append(fullMessage);
                    }
                }
            };

            // Run the compiler with stderr filtering to suppress JVM deprecation warnings
            // from the Kotlin compiler's use of sun.misc.Unsafe
            K2JVMCompiler compiler = new K2JVMCompiler();
            ExitCode exitCode;
            PrintStream originalErr = System.err;
            try {
                // Redirect stderr to filter out Kotlin compiler's JVM warnings
                System.setErr(new PrintStream(new java.io.OutputStream() {
                    private StringBuilder lineBuffer = new StringBuilder();

                    @Override
                    public void write(int b) throws IOException {
                        if (b == '\n') {
                            String line = lineBuffer.toString();
                            // Filter out JVM deprecation warnings from Kotlin compiler
                            if (!line.contains("sun.misc.Unsafe") &&
                                !line.contains("terminally deprecated") &&
                                !line.contains("will be removed in a future release") &&
                                !line.contains("Please consider reporting this")) {
                                originalErr.println(line);
                            }
                            lineBuffer.setLength(0);
                        } else {
                            lineBuffer.append((char) b);
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                        if (lineBuffer.length() > 0) {
                            String line = lineBuffer.toString();
                            if (!line.contains("sun.misc.Unsafe") &&
                                !line.contains("terminally deprecated") &&
                                !line.contains("will be removed in a future release") &&
                                !line.contains("Please consider reporting this")) {
                                originalErr.print(line);
                            }
                            lineBuffer.setLength(0);
                        }
                    }
                }));
                exitCode = compiler.exec(messageCollector, Services.EMPTY, args);
            } finally {
                System.setErr(originalErr);
            }

            if (exitCode != ExitCode.OK) {
                String msg = "Kotlin compilation failed";
                if (errorMessages.length() > 0) {
                    msg += ": " + errorMessages.toString();
                }
                throw new QoreKotlinCompilerException(msg, sources.keySet());
            }

            return readOutput(outputJar);

        } catch (IOException e) {
            throw new QoreKotlinCompilerException("I/O error during compilation: " + e.getMessage(),
                sources.keySet(), e);
        } finally {
            try {
                if (compilerClasspath != null) {
                    compilerClasspath.close();
                }
            } catch (RuntimeException | Error e) {
                // never replaces the result or the error of the compilation: a copy that could not be removed stays
                // charged, and is removed again when its class loader is closed or its Program ends
            } finally {
                // Clean up temp directory
                cleanupTempDir();
            }
        }
    }

    //! Refuses a classpath entry that contains the separator of the compiler's classpath string
    private static void checkClasspathEntry(File location) throws QoreKotlinCompilerException {
        if (location.getPath().indexOf(File.pathSeparatorChar) >= 0) {
            throw new QoreKotlinCompilerException("a classpath entry contains the path separator, so it cannot be "
                + "given to the compiler", java.util.Collections.emptyList());
        }
    }

    /**
     * Generate stub classes for dynamic Qore imports found in the source code.
     *
     * Scans the source code for imports like "qore.OMQ.*" and generates
     * minimal stub classes that will allow the Kotlin compiler to proceed.
     * At runtime, these stubs will be replaced by the actual dynamic classes.
     *
     * This method also captures parent classes in the dynamic class hierarchy,
     * since loading a class like QorusService triggers generation of its
     * parent classes (e.g., ServiceApi).
     */
    private void generateDynamicStubs(Path stubsDir, Map<String, String> sources)
            throws IOException, QoreKotlinCompilerException {
        // Track classes we've already written to avoid duplicates
        java.util.Set<String> writtenClasses = new java.util.HashSet<>();

        // Collect all dynamic imports from source files
        for (String source : sources.values()) {
            // Find imports like: import qore.OMQ.UserApi.*
            java.util.regex.Pattern importPattern = java.util.regex.Pattern.compile(
                "import\\s+(qore\\.[\\w.]+)(?:\\.\\*)?");
            java.util.regex.Matcher matcher = importPattern.matcher(source);

            while (matcher.find()) {
                String importPath = matcher.group(1);
                // Request the classloader to resolve this class/package
                // This triggers generation of the dynamic class bytecode
                try {
                    // Try to load the class - this will trigger bytecode generation
                    // for dynamic Qore classes AND their parent classes
                    Class<?> cls = classLoader.loadClass(importPath);
                    ensureQoreSupertypesPending(cls);
                } catch (ClassNotFoundException e) {
                    // Class might be a package prefix - that's OK
                }
            }
        }

        // Load any additional base classes that were registered
        loadAdditionalBaseClasses();

        // Now collect ALL pending classes that were generated (including parent classes)
        // and write them to the stubs directory
        Map<String, byte[]> allPendingClasses = classLoader.getAllPendingClasses();
        // every stub name is checked before the first stub is written
        for (Map.Entry<String, byte[]> entry : allPendingClasses.entrySet()) {
            if (entry.getKey().startsWith("qore.") && entry.getValue() != null) {
                checkNames(java.util.Collections.singleton(entry.getKey()));
            }
        }
        for (Map.Entry<String, byte[]> entry : allPendingClasses.entrySet()) {
            String className = entry.getKey();
            // Only write qore.* classes (dynamic imports)
            if (className.startsWith("qore.") && !writtenClasses.contains(className)) {
                byte[] bytecode = entry.getValue();
                if (bytecode != null) {
                    writeClassFile(stubsDir, className, bytecode);
                    writtenClasses.add(className);
                }
            }
        }
    }

    //! Reads the classes of the compiler's output; every name is checked before any class is read or registered
    private Map<String, byte[]> readOutput(Path jar) throws IOException, QoreKotlinCompilerException {
        Map<String, byte[]> result = new HashMap<>();
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            throw new QoreKotlinCompilerException("invalid compiler output: expecting a regular file",
                java.util.Collections.emptyList());
        }
        // a compilation with no classes leaves the file that was created for it empty
        if (Files.size(jar) == 0) {
            return result;
        }
        try (java.util.jar.JarFile output = new java.util.jar.JarFile(jar.toFile(), false)) {
            Map<String, java.util.jar.JarEntry> classes = new java.util.LinkedHashMap<>();
            java.util.Enumeration<java.util.jar.JarEntry> entries = output.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/") || !name.endsWith(".class")) {
                    continue;
                }
                try {
                    classes.put(QoreCompilerPolicy.outputClassName(name), entry);
                } catch (IllegalArgumentException e) {
                    throw new QoreKotlinCompilerException(e.getMessage(), java.util.Collections.emptyList());
                }
            }
            for (Map.Entry<String, java.util.jar.JarEntry> i : classes.entrySet()) {
                String fullClassName = i.getKey();
                byte[] bytecode;
                try (java.io.InputStream in = output.getInputStream(i.getValue())) {
                    bytecode = in.readAllBytes();
                }
                // the simple class name for the result map; the full name for the class loader
                result.put(fullClassName.substring(fullClassName.lastIndexOf('.') + 1), bytecode);
                classLoader.addPendingClass(fullClassName, bytecode);
            }
        }
        return result;
    }

    //! Refuses names that are not binary class names; the error does not repeat them
    private static void checkNames(java.util.Collection<String> names) throws QoreKotlinCompilerException {
        for (String name : names) {
            if (!QoreCompilerPolicy.isValidBinaryName(name)) {
                throw new QoreKotlinCompilerException("invalid class name for compilation: expecting a Java binary "
                    + "name", java.util.Collections.emptyList());
            }
        }
    }

    //! Returns the file of a class name, whose canonical path is in \a dir; creates nothing
    private static Path containedPath(Path dir, String name, String extension)
            throws IOException, QoreKotlinCompilerException {
        Path real = dir.toRealPath();
        Path file;
        try {
            file = QoreCompilerPolicy.resolveUnder(real, name, extension);
        } catch (IllegalArgumentException e) {
            throw new QoreKotlinCompilerException(e.getMessage(), java.util.Collections.emptyList());
        }
        Path current = real;
        for (Path element : real.relativize(file)) {
            current = current.resolve(element);
            if (Files.isSymbolicLink(current)) {
                throw new QoreKotlinCompilerException("invalid class name for compilation: its file is reached "
                    + "through a symbolic link", java.util.Collections.emptyList());
            }
        }
        return file;
    }

    //! Writes a class file for a class name in \a dir; a symbolic link is not followed
    private static void writeClassFile(Path dir, String name, byte[] bytecode)
            throws IOException, QoreKotlinCompilerException {
        Path file = containedPath(dir, name, ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytecode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Load additional base classes to trigger their bytecode generation.
     * These classes are loaded to make them available as stubs during compilation.
     */
    private void loadAdditionalBaseClasses() {
        for (String className : additionalBaseClasses) {
            try {
                Class<?> cls = classLoader.loadClass(className);
                ensureQoreSupertypesPending(cls);
            } catch (ClassNotFoundException e) {
                // Class not available - that's OK, might not be needed
            }
        }
    }

    /**
     * Walk the loaded class's superclass and interface chain and ensure that
     * every transitive {@code qore.*} ancestor's bytecode is present in the
     * calling classLoader's pendingClasses.
     *
     * <p>When a class like {@code qore.OMQ.UserApi.Job.QorusJob} is defined,
     * the JVM verifier resolves its supertype {@code qore.OMQ.UserApi.Job.JobApi}
     * via the canonical (target) loader, which generates JobApi's bytecode
     * into <em>target</em>'s pendingClasses — but not into this loader's.
     * {@link #generateDynamicStubs} writes only this loader's pendingClasses to
     * the stubs directory, so without this step JobApi.class is missing from
     * the Kotlin compile classpath and the compiler reports "Cannot access
     * 'JobApi' which is a supertype of ...".
     *
     * <p>For each {@code qore.*} ancestor (including interfaces, transitively)
     * whose bytecode is not yet in this loader's pendingClasses, we ask the
     * ancestor's owning {@link QoreURLClassLoader} for the cached bytecode
     * via {@code generateByteCode()} (a cache hit at this point) and copy it
     * into this loader's pendingClasses.
     */
    private void ensureQoreSupertypesPending(Class<?> cls) {
        if (cls == null) {
            return;
        }
        // Walk superclass chain
        Class<?> sc = cls.getSuperclass();
        while (sc != null) {
            if (!ensureSinglePending(sc)) {
                break;
            }
            sc = sc.getSuperclass();
        }
        // Walk interfaces (recursively, since interfaces can extend interfaces)
        for (Class<?> iface : cls.getInterfaces()) {
            if (ensureSinglePending(iface)) {
                ensureQoreSupertypesPending(iface);
            }
        }
    }

    /**
     * If {@code ancestor} is a {@code qore.*} class loaded by a
     * {@link QoreURLClassLoader} and the calling loader doesn't yet have its
     * bytecode in pendingClasses, copy the bytecode in.  Returns true if the
     * caller should keep walking past this ancestor (i.e., it's a Qore-bridged
     * class), false to stop.
     */
    private boolean ensureSinglePending(Class<?> ancestor) {
        String name = ancestor.getName();
        if (!name.startsWith("qore.")) {
            return false;
        }
        if (classLoader.hasPendingClass(name)) {
            return true;
        }
        ClassLoader ownerLoader = ancestor.getClassLoader();
        if (!(ownerLoader instanceof QoreURLClassLoader)) {
            return true;
        }
        QoreURLClassLoader qcl = (QoreURLClassLoader) ownerLoader;
        try {
            // generateByteCode() returns the cached bytecode from the owning
            // loader's pendingClasses (the class is already defined, so this
            // is a cache lookup, not a regeneration).
            byte[] bytes = qcl.generateByteCode(name);
            if (bytes != null) {
                classLoader.addPendingClass(name, bytes);
            }
        } catch (ClassNotFoundException e) {
            // shouldn't happen for an already-loaded class; ignore and keep walking
        }
        return true;
    }

    /**
     * Add a base class to be loaded for stub generation.
     *
     * Call this method before compile() to ensure specific classes are available
     * during Kotlin compilation. This is useful for application-specific classes
     * like Qorus base classes (QorusService, QorusJob, etc.) or constants classes.
     *
     * @param className The fully qualified class name (e.g., "qore.OMQ.$Constants")
     */
    public void addBaseClass(String className) {
        additionalBaseClasses.add(className);
    }

    /**
     * Add multiple base classes to be loaded for stub generation.
     *
     * @param classNames Collection of fully qualified class names
     */
    public void addBaseClasses(java.util.Collection<String> classNames) {
        additionalBaseClasses.addAll(classNames);
    }

    /**
     * Add a class to the compiler's classloader.
     *
     * @param binName  The binary name of the class
     * @param byteCode The compiled bytecode
     */
    public void injectClass(String binName, byte[] byteCode) {
        classLoader.addPendingClass(binName, byteCode);
    }

    /**
     * Converts a class loader URL into a filesystem path the compiler can open.
     *
     * `QoreURLClassLoader.addPath()` registers jars as `jar:` URLs, whose
     * `getPath()` is the inner spec — `file:/path/to.jar!/` — not a filesystem
     * path. Handing that to the compiler produces a classpath entry it cannot
     * open, which reads exactly like the jar not being on the classpath at all.
     *
     * @param url a class loader URL
     * @return the filesystem path, or null if the URL names no local file
     */
    private static String urlToClasspathEntry(java.net.URL url) {
        String spec = url.toString();

        if (spec.startsWith("jar:")) {
            spec = spec.substring(4);
            // the last separator: the path of the file can contain the same characters
            int sep = spec.lastIndexOf("!/");
            if (sep >= 0) {
                spec = spec.substring(0, sep);
            }
        }

        if (!spec.startsWith("file:")) {
            return null;
        }

        try {
            return new File(new java.net.URI(spec)).getPath();
        } catch (Exception e) {
            // A URI the parser rejects (an unencoded space, for instance) still
            // names a real file often enough to be worth the fallback.
            return spec.substring("file:".length());
        }
    }

    /**
     * Add a path to the classpath.
     *
     * @param path The path to add
     */
    public void addClassPath(String path) {
        classLoader.addPath(path);
    }

    /**
     * @return This compiler's class loader.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Clean up the temporary directory.
     */
    private void cleanupTempDir() {
        if (tempDir != null) {
            Path dir = tempDir;
            tempDir = null;
            deleteTree(dir);
        }
    }

    //! Deletes what it can of a file or directory, without following links; never throws, so that an error of the
    //! compilation is not replaced, and a file that cannot be deleted does not stop the others from being deleted
    private static void deleteTree(Path path) {
        try {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
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

    //! Returns the attributes of a file or directory that only its owner can use, where the filesystem has them
    private static java.nio.file.attribute.FileAttribute<?>[] privateAttributes(String permissions) {
        if (!java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new java.nio.file.attribute.FileAttribute<?>[0];
        }
        return new java.nio.file.attribute.FileAttribute<?>[] {
            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString(permissions)),
        };
    }

    /**
     * Close the compiler and release resources.
     * <p>
     * Code that cannot reach the class loader's Program is refused, and the
     * compiler then stays open until code that can reach its Program closes
     * it. Once the module's shutdown has started, nothing is closed.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        // a refusal, or the module's shutdown, leaves the compiler open, with all of its resources
        if (classLoader != null && !classLoader.closeAndClear()) {
            return;
        }
        try {
            cleanupTempDir();
        } finally {
            closed = true;
        }
    }

    /**
     * @return true if the compiler has been closed
     */
    public boolean isClosed() {
        return closed;
    }
}
