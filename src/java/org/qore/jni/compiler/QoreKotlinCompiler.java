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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
        this.classLoader = new QoreURLClassLoader(loader.getPtr(), loader);
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

        try {
            // Create temporary directories for source and output
            tempDir = Files.createTempDirectory("qore-kotlin-");
            Path sourceDir = tempDir.resolve("src");
            Path outputDir = tempDir.resolve("out");
            Path stubsDir = tempDir.resolve("stubs");
            Files.createDirectories(sourceDir);
            Files.createDirectories(outputDir);
            Files.createDirectories(stubsDir);

            // Write source files
            List<String> sourceFiles = new ArrayList<>();
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String className = entry.getKey();
                String source = entry.getValue();

                // Convert class name to path (com.foo.Bar -> com/foo/Bar.kt)
                String relativePath = className.replace('.', '/') + ".kt";
                Path sourceFile = sourceDir.resolve(relativePath);
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, source);
                sourceFiles.add(sourceFile.toString());
            }

            // Write injected class dependencies (e.g., from Qorus classes table)
            if (injectedClasses != null && !injectedClasses.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : injectedClasses.entrySet()) {
                    String binName = entry.getKey();
                    byte[] bytecode = entry.getValue();
                    String classPath = binName.replace('.', '/') + ".class";
                    Path classFile = stubsDir.resolve(classPath);
                    Files.createDirectories(classFile.getParent());
                    Files.write(classFile, bytecode);
                }
            }

            // Generate stub classes for dynamic Qore imports
            generateDynamicStubs(stubsDir, sources);

            // Build classpath - stubs directory first, then URLs from class loader
            StringBuilder classpath = new StringBuilder();
            classpath.append(stubsDir.toString());

            // Add URLs from the class loader
            for (java.net.URL url : classLoader.getURLs()) {
                classpath.append(File.pathSeparator).append(url.getPath());
            }

            // Add qore-jni.jar for base classes like QoreJavaClassBase
            String omqDir = System.getenv("OMQ_DIR");
            if (omqDir != null && !omqDir.isEmpty()) {
                String qoreJniJar = omqDir + "/jar/qore-jni.jar";
                if (new File(qoreJniJar).exists()) {
                    classpath.append(File.pathSeparator).append(qoreJniJar);
                }
            }
            // Also check standard install location
            String stdJniJar = "/usr/share/qore/java/qore-jni.jar";
            if (new File(stdJniJar).exists()) {
                classpath.append(File.pathSeparator).append(stdJniJar);
            }

            // Add kotlin-stdlib to classpath
            String kotlinHome = System.getenv("KOTLIN_HOME");
            if (kotlinHome == null || kotlinHome.isEmpty()) {
                kotlinHome = "/opt/kotlin";
            }
            String kotlinStdlib = kotlinHome + "/lib/kotlin-stdlib.jar";
            if (new File(kotlinStdlib).exists()) {
                classpath.append(File.pathSeparator).append(kotlinStdlib);
            }

            // Set up compiler arguments
            K2JVMCompilerArguments args = new K2JVMCompilerArguments();
            args.setFreeArgs(sourceFiles);
            args.setDestination(outputDir.toString());
            args.setClasspath(classpath.toString());
            args.setJvmTarget("21");
            args.setNoStdlib(false);
            args.setNoReflect(true);

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

            // Run the compiler
            K2JVMCompiler compiler = new K2JVMCompiler();
            ExitCode exitCode = compiler.exec(messageCollector, Services.EMPTY, args);

            if (exitCode != ExitCode.OK) {
                String msg = "Kotlin compilation failed";
                if (errorMessages.length() > 0) {
                    msg += ": " + errorMessages.toString();
                }
                throw new QoreKotlinCompilerException(msg, sources.keySet());
            }

            // Read compiled class files
            Map<String, byte[]> result = new HashMap<>();
            Files.walk(outputDir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(classFile -> {
                    try {
                        // Convert file path to class name
                        String relativePath = outputDir.relativize(classFile).toString();
                        String fullClassName = relativePath
                            .replace(File.separatorChar, '.')
                            .replace(".class", "");

                        // Extract simple class name (without package prefix) for the result map
                        // The full class name is used for the classloader registration below
                        String className = fullClassName;
                        int lastDot = fullClassName.lastIndexOf('.');
                        if (lastDot >= 0) {
                            className = fullClassName.substring(lastDot + 1);
                        }

                        byte[] bytecode = Files.readAllBytes(classFile);
                        result.put(className, bytecode);

                        // Also add to classloader for potential subsequent compilations
                        // Use full class name for classloader
                        classLoader.addPendingClass(fullClassName, bytecode);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read class file: " + classFile, e);
                    }
                });

            return result;

        } catch (IOException e) {
            throw new QoreKotlinCompilerException("I/O error during compilation: " + e.getMessage(),
                sources.keySet(), e);
        } finally {
            // Clean up temp directory
            cleanupTempDir();
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
            throws IOException {
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
                    classLoader.loadClass(importPath);
                } catch (ClassNotFoundException e) {
                    // Class might be a package prefix - that's OK
                }
            }
        }

        // Also load common Qorus base classes that might be needed
        generateQorusBaseClassStubs();

        // Now collect ALL pending classes that were generated (including parent classes)
        // and write them to the stubs directory
        Map<String, byte[]> allPendingClasses = classLoader.getAllPendingClasses();
        for (Map.Entry<String, byte[]> entry : allPendingClasses.entrySet()) {
            String className = entry.getKey();
            // Only write qore.* classes (dynamic imports)
            if (className.startsWith("qore.") && !writtenClasses.contains(className)) {
                byte[] bytecode = entry.getValue();
                if (bytecode != null) {
                    String classPath = className.replace('.', '/') + ".class";
                    Path stubFile = stubsDir.resolve(classPath);
                    Files.createDirectories(stubFile.getParent());
                    Files.write(stubFile, bytecode);
                    writtenClasses.add(className);
                }
            }
        }
    }

    /**
     * Load common Qorus base classes to trigger their bytecode generation.
     */
    private void generateQorusBaseClassStubs() {
        // List of common base classes that Kotlin code might extend
        String[] baseClasses = {
            "qore.OMQ.UserApi.Service.QorusService",
            "qore.OMQ.UserApi.Job.QorusJob",
            "qore.OMQ.UserApi.Workflow.QorusNormalStep",
            "qore.OMQ.UserApi.Workflow.QorusAsyncStep",
            "qore.OMQ.UserApi.Workflow.QorusEventStep",
            "qore.OMQ.UserApi.Workflow.QorusSubworkflowStep",
            "qore.OMQ.UserApi.UserApi",
            "qore.OMQ.OMQ"
        };

        for (String className : baseClasses) {
            try {
                classLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                // Class not available - that's OK, might not be needed
            }
        }
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
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                // Ignore cleanup errors
            }
            tempDir = null;
        }
    }

    /**
     * Close the compiler and release resources.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            cleanupTempDir();
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException e) {
                    // Ignore close errors
                } finally {
                    classLoader.clearAllCaches();
                    classLoader.clearProgramPtr();
                }
            }
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
