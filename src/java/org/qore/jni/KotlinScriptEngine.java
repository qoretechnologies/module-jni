/*
    KotlinScriptEngine.java

    Helper class for Kotlin JSR-223 scripting integration.
    Provides lazy initialization and evaluation of Kotlin scripts.

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.Bindings;
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
    private static volatile ScriptEngine engine = null;
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
        return engine != null;
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
        if (engine == null) {
            throw new RuntimeException("Kotlin scripting not available: " +
                (initError != null ? initError : "unknown error"));
        }
        try {
            return engine.eval(script);
        } catch (ScriptException e) {
            throw new RuntimeException("Kotlin script error: " + e.getMessage(), e);
        }
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
        if (engine == null) {
            throw new RuntimeException("Kotlin scripting not available: " +
                (initError != null ? initError : "unknown error"));
        }
        try {
            Bindings b = new SimpleBindings();
            if (bindings != null) {
                b.putAll(bindings);
            }
            return engine.eval(script, b);
        } catch (ScriptException e) {
            throw new RuntimeException("Kotlin script error: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize the Kotlin script engine if not already done.
     * Thread-safe lazy initialization.
     */
    private static void ensureInitialized() {
        if (!initAttempted) {
            synchronized (lock) {
                if (!initAttempted) {
                    try {
                        ScriptEngineManager manager = new ScriptEngineManager();
                        engine = manager.getEngineByExtension("kts");
                        if (engine == null) {
                            // Try by name as fallback
                            engine = manager.getEngineByName("kotlin");
                        }
                        if (engine == null) {
                            initError = "Kotlin script engine not found. " +
                                "Ensure kotlin-scripting-jsr223.jar and related JARs are in the classpath. " +
                                "Required: kotlin-scripting-jsr223, kotlin-scripting-compiler-embeddable, " +
                                "kotlin-compiler-embeddable, kotlin-script-runtime";
                        }
                    } catch (Exception e) {
                        initError = "Failed to initialize Kotlin script engine: " + e.getMessage();
                        engine = null;
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
            engine = null;
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
            if (engine == null) {
                initAttempted = false;
                initError = null;
                ensureInitialized();
            }
            return engine != null;
        }
    }
}

