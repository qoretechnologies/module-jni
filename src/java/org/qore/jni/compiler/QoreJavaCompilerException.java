package org.qore.jni.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

/**
 * An exception thrown when trying to compile Java programs from strings
 * containing source.
 *
 * @author <a href="mailto:David.Biesack@sas.com">David J. Biesack</a>
 */
public class QoreJavaCompilerException extends Exception {
    private static final long serialVersionUID = 1L;
    /**
     * The fully qualified name of the class that was being compiled.
     */
    private Set<String> classNames;
    // Unfortunately, Diagnostic and Collector are not Serializable, so we can't
    // serialize the collector.
    transient private DiagnosticCollector<JavaFileObject> diagnostics;

    public QoreJavaCompilerException(String message,
                                         Set<String> qualifiedClassNames, Throwable cause,
                                         DiagnosticCollector<JavaFileObject> diagnostics) {
        super(message, cause);
        setClassNames(qualifiedClassNames);
        setDiagnostics(diagnostics);
    }

    public QoreJavaCompilerException(String message,
                                         Set<String> qualifiedClassNames,
                                         DiagnosticCollector<JavaFileObject> diagnostics) {
        super(message);
        setClassNames(qualifiedClassNames);
        setDiagnostics(diagnostics);
    }

    public QoreJavaCompilerException(Set<String> qualifiedClassNames,
                                         Throwable cause, DiagnosticCollector<JavaFileObject> diagnostics) {
        super(cause);
        setClassNames(qualifiedClassNames);
        setDiagnostics(diagnostics);
    }

    private void setClassNames(Set<String> qualifiedClassNames) {
        // create a new HashSet because the set passed in may not
        // be Serializable. For example, Map.keySet() returns a non-Serializable
        // set.
        classNames = new HashSet<String>(qualifiedClassNames);
    }

    private void setDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Gets the diagnostics collected by this exception.
     *
     * @return this exception's diagnostics
     */
    public DiagnosticCollector<JavaFileObject> getDiagnostics() {
        return diagnostics;
    }

    /**
     * @return The name of the classes whose compilation caused the compile
     * exception
     */
    public Collection<String> getClassNames() {
        return Collections.unmodifiableSet(classNames);
    }

    /**
     * Gets diagnostic messages as simple strings, safely extracting information
     * without exposing internal JDK classes that may not be accessible via reflection.
     *
     * @return List of diagnostic messages in the format "source:line: message"
     */
    public List<String> getDiagnosticMessages() {
        return extractDiagnosticMessages(diagnostics);
    }

    /**
     * Static utility method to safely extract diagnostic messages from any DiagnosticCollector.
     * This avoids JDK module access issues with internal compiler wrapper classes in JDK 25+.
     *
     * @param collector The DiagnosticCollector to extract messages from
     * @return List of diagnostic messages in the format "source:line: message"
     */
    public static List<String> extractDiagnosticMessages(DiagnosticCollector<? extends JavaFileObject> collector) {
        List<String> messages = new ArrayList<>();
        if (collector != null) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                try {
                    String sourceName = "unknown";
                    JavaFileObject source = diagnostic.getSource();
                    if (source != null) {
                        sourceName = source.getName();
                    }
                    messages.add(String.format("%s:%d: %s",
                        sourceName,
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(null)));
                } catch (Exception e) {
                    // If we can't access source info due to JDK module restrictions,
                    // fall back to just the message
                    messages.add(String.format("line %d: %s",
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(null)));
                }
            }
        }
        return messages;
    }
}