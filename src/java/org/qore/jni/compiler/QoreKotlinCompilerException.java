/*
    QoreKotlinCompilerException.java

    Exception thrown when Kotlin compilation fails.

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni.compiler;

import java.util.Collection;

/**
 * An exception thrown when Kotlin compilation fails.
 */
public class QoreKotlinCompilerException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Collection<String> classNames;

    /**
     * Construct a new exception.
     *
     * @param message    The error message
     * @param classNames The class names being compiled
     */
    public QoreKotlinCompilerException(String message, Collection<String> classNames) {
        super(message);
        this.classNames = classNames;
    }

    /**
     * Construct a new exception with a cause.
     *
     * @param message    The error message
     * @param classNames The class names being compiled
     * @param cause      The underlying cause
     */
    public QoreKotlinCompilerException(String message, Collection<String> classNames, Throwable cause) {
        super(message, cause);
        this.classNames = classNames;
    }

    /**
     * @return The class names that were being compiled
     */
    public Collection<String> getClassNames() {
        return classNames;
    }
}
