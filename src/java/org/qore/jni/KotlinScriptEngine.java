/*
    KotlinScriptEngine.java

    Helper class for Kotlin JSR-223 scripting integration.
    Provides lazy initialization and evaluation of Kotlin scripts.

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for Kotlin scripting via JSR-223.
 *
 * Provides lazy initialization of the Kotlin script engine and methods
 * to evaluate Kotlin code from Qore. The script engine is only initialized
 * when first needed, and will fail gracefully if the required Kotlin
 * scripting JARs are not available.
 *
 * Required JARs (in KOTLIN_HOME/lib or classpath):
 * - kotlin-scripting-jsr223.jar
 * - kotlin-scripting-compiler-embeddable.jar
 * - kotlin-compiler-embeddable.jar
 * - kotlin-stdlib.jar
 * - kotlin-script-runtime.jar
 */
public class KotlinScriptEngine {
    private static volatile boolean available = false;
    private static volatile boolean initAttempted = false;
    private static volatile String initError = null;
    private static final Object lock = new Object();

    /**
     * Check if Kotlin scripting is available.
     *
     * @return true if the Kotlin script engine can be initialized
     */
    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    /**
     * Get the initialization error message, if any.
     *
     * @return the error message or null if initialization succeeded
     */
    public static String getInitError() {
        ensureInitialized();
        return initError;
    }

    /**
     * Evaluate a Kotlin script and return the result.
     *
     * @param script the Kotlin code to evaluate
     * @return the result of evaluating the script
     * @throws Exception if evaluation fails or scripting is not available
     */
    public static Object eval(String script) throws Exception {
        ensureInitialized();
        if (!available) {
            throw new RuntimeException("Kotlin scripting not available: " +
                (initError != null ? initError : "unknown error"));
        }
        return evalAttributed(script, null);
    }

    /**
     * Evaluate a Kotlin script with bindings and return the result.
     *
     * @param script the Kotlin code to evaluate
     * @param bindings a map of variable names to values to bind in the script
     * @return the result of evaluating the script
     * @throws Exception if evaluation fails or scripting is not available
     */
    public static Object evalWithBindings(String script, HashMap<String, Object> bindings) throws Exception {
        ensureInitialized();
        if (!available) {
            throw new RuntimeException("Kotlin scripting not available: " +
                (initError != null ? initError : "unknown error"));
        }
        Bindings b = new SimpleBindings();
        if (bindings != null) {
            b.putAll(bindings);
        }
        return evalAttributed(script, b);
    }

    //! Evaluates a script whose classes are defined by a class loader of the Program of the calling code (qore#7131)
    /** Kotlin defines the classes of a script in a class loader of its own, which belongs to no Program and whose
        parent is the thread's context class loader, which it asks first.  The compiled classes are given to a class
        loader derived for the Program of the thread's context, which is that parent, so that it defines them: they
        are then attributed to that Program like any other class of the Program.  One class loader and one engine
        are made for each evaluation, and nothing is shared between evaluations.

        If the classes cannot be given to it (no context that the calling code can reach, or a Kotlin version with
        another compiler API), the script runs with the classes in Kotlin's class loader, which is an unknown class
        loader: its calls into Qore are refused once sandboxed code has used Java.
    */
    private static Object evalAttributed(String script, Bindings bindings) throws Exception {
        // the script compiler takes the classpath from the class loaders and reads the files below its directories
        // itself, so the policy cannot be applied to each of them (qore#7276)
        if (QoreURLClassLoader.hasGuardedDirectory(Thread.currentThread().getContextClassLoader())
                && QoreSandbox.restrictsFilesystem()) {
            throw new RuntimeException("Kotlin scripts cannot be evaluated by code that a sandbox filesystem policy "
                + "restricts while a directory that such code added is on its classpath; use jars for that classpath");
        }
        Object[] context = QoreURLClassLoader.saveContext();
        try {
            QoreURLClassLoader scope = QoreURLClassLoader.createDerivedForContext();
            ScriptEngine engine = createEngine();
            if (bindings != null) {
                // set before the script is compiled, as the compiler reads the engine's context
                engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            }
            if (scope != null && engine instanceof Compilable) {
                CompiledScript compiled = ((Compilable)engine).compile(script);
                addCompiledClasses(scope, compiled);
                return compiled.eval();
            }
            return engine.eval(script);
        } catch (ScriptException e) {
            throw new RuntimeException("Kotlin script error: " + e.getMessage(), e);
        } finally {
            QoreURLClassLoader.restoreContext(context);
        }
    }

    //! Gives the classes of a compiled script to \a scope; gives none if the compiler's API is not the expected one
    private static void addCompiledClasses(QoreURLClassLoader scope, CompiledScript compiled) {
        Map<String, byte[]> files;
        try {
            Object data = compiled.getClass().getMethod("getCompiledData").invoke(compiled);
            Object script = data.getClass().getMethod("getData").invoke(data);
            Object module = findCompiledModule(script);
            Class<?> in_memory = Class.forName("kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory", false,
                module.getClass().getClassLoader());
            @SuppressWarnings("unchecked")
            Map<String, byte[]> output = (Map<String, byte[]>)in_memory.getMethod("getCompilerOutputFiles")
                .invoke(module);
            files = output;
        } catch (ReflectiveOperationException | IllegalArgumentException | NullPointerException
                | ClassCastException e) {
            // the classes stay in Kotlin's class loader, which is unknown (fail closed)
            return;
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if (path.endsWith(".class")) {
                scope.addPendingClass(path.substring(0, path.length() - 6).replace('/', '.'), entry.getValue());
            }
        }
    }

    //! Returns the compiled module of the script data, which a REPL snippet list holds in its last element
    private static Object findCompiledModule(Object script) throws ReflectiveOperationException {
        Object current = script;
        try {
            current = current.getClass().getMethod("get").invoke(current);
        } catch (NoSuchMethodException e) {
            // not a snippet: the data is the compiled script
        }
        return current.getClass().getMethod("getCompiledModule").invoke(current);
    }

    /**
     * Creates a Kotlin script engine using the current Qore URL classloader.
     *
     * Kotlin's JSR-223 engine has mutable compiler state and is not safe to
     * share across independent Qorus service worker threads.
     */
    private static ScriptEngine createEngine() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ScriptEngineManager manager = loader == null
            ? new ScriptEngineManager()
            : new ScriptEngineManager(loader);
        ScriptEngine engine = manager.getEngineByExtension("kts");
        if (engine == null) {
            // Try by name as fallback
            engine = manager.getEngineByName("kotlin");
        }
        if (engine == null) {
            throw new RuntimeException("Kotlin script engine not found. " +
                "Ensure kotlin-scripting-jsr223.jar and related JARs are in the classpath. " +
                "Required: kotlin-scripting-jsr223, kotlin-scripting-compiler-embeddable, " +
                "kotlin-compiler-embeddable, kotlin-script-runtime");
        }
        return engine;
    }

    /**
     * Check that Kotlin script engines can be created.
     * Thread-safe lazy initialization.
     */
    private static void ensureInitialized() {
        if (!initAttempted) {
            synchronized (lock) {
                if (!initAttempted) {
                    try {
                        createEngine();
                        available = true;
                    } catch (Exception e) {
                        initError = "Failed to initialize Kotlin script engine: " + e.getMessage();
                        available = false;
                    } finally {
                        initAttempted = true;
                    }
                }
            }
        }
    }

    /**
     * Reset the script engine (for testing purposes).
     * Forces re-initialization on next use.
     */
    public static void reset() {
        synchronized (lock) {
            available = false;
            initAttempted = false;
            initError = null;
        }
    }

    /**
     * Retry initialization if it failed previously.
     * Unlike reset(), this only retries if initialization failed.
     *
     * @return true if scripting is now available
     */
    public static boolean retryInit() {
        synchronized (lock) {
            if (!available) {
                initAttempted = false;
                initError = null;
                ensureInitialized();
            }
            return available;
        }
    }
}
