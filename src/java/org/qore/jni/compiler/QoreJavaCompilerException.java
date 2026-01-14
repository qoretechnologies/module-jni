package org.qore.jni.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
     * Gets the raw diagnostics collector.
     *
     * @return this exception's raw diagnostics collector
     * @deprecated Use {@link #getDiagnostics()} or {@link #getDiagnosticMessages()} instead.
     *             Raw DiagnosticCollector access can cause IllegalAccessException in JDK 25+
     *             when accessed via reflection/JNI due to internal wrapper classes.
     */
    @Deprecated
    public DiagnosticCollector<JavaFileObject> getRawDiagnostics() {
        return diagnostics;
    }

    /**
     * Gets the diagnostics as safe wrapper objects that can be accessed from Qore via JNI
     * without triggering JDK module access issues.
     *
     * @return List of SafeDiagnostic objects containing diagnostic information
     */
    public List<SafeDiagnostic> getDiagnostics() {
        return extractSafeDiagnostics(diagnostics);
    }

    /**
     * Static utility method to safely extract diagnostics from any DiagnosticCollector.
     * Returns SafeDiagnostic wrapper objects that avoid JDK module access issues.
     *
     * @param collector The DiagnosticCollector to extract diagnostics from
     * @return List of SafeDiagnostic objects
     */
    public static List<SafeDiagnostic> extractSafeDiagnostics(DiagnosticCollector<? extends JavaFileObject> collector) {
        List<SafeDiagnostic> result = new ArrayList<>();
        if (collector != null) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                result.add(new SafeDiagnostic(diagnostic));
            }
        }
        return result;
    }

    /**
     * A safe wrapper for compiler diagnostics that extracts all information at construction time,
     * avoiding JDK module access issues when accessed via reflection/JNI.
     */
    public static class SafeDiagnostic {
        private final String kind;
        private final String source;
        private final long position;
        private final long startPosition;
        private final long endPosition;
        private final long lineNumber;
        private final long columnNumber;
        private final String code;
        private final String message;

        public SafeDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
            this.kind = diagnostic.getKind().toString();
            this.position = diagnostic.getPosition();
            this.startPosition = diagnostic.getStartPosition();
            this.endPosition = diagnostic.getEndPosition();
            this.lineNumber = diagnostic.getLineNumber();
            this.columnNumber = diagnostic.getColumnNumber();
            this.code = diagnostic.getCode();
            this.message = diagnostic.getMessage(Locale.getDefault());

            // Extract source name safely - this is where the module access issue occurs
            String sourceName = "unknown";
            try {
                JavaFileObject sourceObj = diagnostic.getSource();
                if (sourceObj != null) {
                    sourceName = sourceObj.getName();
                }
            } catch (Exception e) {
                // Fall back to unknown if we can't access the source
            }
            this.source = sourceName;
        }

        public String getKind() { return kind; }
        public String getSource() { return source; }
        public long getPosition() { return position; }
        public long getStartPosition() { return startPosition; }
        public long getEndPosition() { return endPosition; }
        public long getLineNumber() { return lineNumber; }
        public long getColumnNumber() { return columnNumber; }
        public String getCode() { return code; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("%s:%d: %s", source, lineNumber, message);
        }
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