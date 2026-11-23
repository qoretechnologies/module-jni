/*
    QoreCompilerPolicy.java

    Validates the names and options that the Java and Kotlin compilers are given.

    Copyright (C) 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni.compiler;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.qore.jni.QoreSandbox;

//! Validates the names and options that the Java and Kotlin compilers are given
/** Class names become file names in a compiler's private temporary directory, so each one must be a binary name and
    its file must stay in that directory.  The options of the Java compiler can name files, so when a sandbox
    filesystem policy governs the caller, only options that name no file are taken as they are, and those that name a
    classpath or an output directory are checked against the policy.

    @since jni 3.0
*/
public final class QoreCompilerPolicy {
    //! The characters that a class name cannot have (the JVM's and Kotlin's illegal name characters, except the dot)
    private static final String ILLEGAL_NAME_CHARS = ";[]/<>:\\";

    //! javac options that name no file and take no value
    private static final Set<String> FLAGS = Set.of("-g", "-nowarn", "-verbose", "-deprecation", "-parameters",
        "-Werror", "-proc:none");

    //! The prefixes of javac options that name no file, with their value in the same argument
    private static final String[] FLAG_PREFIXES = {"-g:", "-Xlint", "-Xdiags:", "-implicit:"};

    //! javac options that name no file and take a value
    private static final Set<String> VALUE_OPTIONS = Set.of("-encoding", "-source", "--source", "-target", "--target",
        "--release", "-Xmaxerrs", "-Xmaxwarns");

    //! javac options that take a classpath
    private static final Set<String> CLASSPATH_OPTIONS = Set.of("-cp", "-classpath", "--class-path");

    //! javac options that take an output directory
    private static final Set<String> OUTPUT_OPTIONS = Set.of("-d", "-s", "-h");

    private QoreCompilerPolicy() {
    }

    //! Returns true if \a name is a binary class name: dot-separated, non-empty segments with no illegal character
    /** Nested (\c $), package-qualified, default-package and Unicode names are valid.
    */
    public static boolean isValidBinaryName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        boolean segment_start = true;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '.') {
                if (segment_start) {
                    return false;
                }
                segment_start = true;
                continue;
            }
            segment_start = false;
            if (ILLEGAL_NAME_CHARS.indexOf(c) >= 0 || Character.isISOControl(c)) {
                return false;
            }
            if (Character.isHighSurrogate(c)) {
                if (++i == name.length() || !Character.isLowSurrogate(name.charAt(i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return !segment_start;
    }

    //! Refuses a name that is not a binary class name; the error does not repeat the name
    /** @throw IllegalArgumentException the name is not valid
    */
    public static void checkBinaryName(String name) {
        if (!isValidBinaryName(name)) {
            throw new IllegalArgumentException("invalid class name for compilation: expecting a Java binary name");
        }
    }

    //! Returns the file for a class name under \a base; this does not access the filesystem
    /** The caller resolves \a base to its real path first and checks that no element of the result below it is a
        symbolic link.

        @param base the real path of the directory
        @param name the binary class name
        @param extension the file name extension, with its dot

        @return the path of the file, under \a base

        @throw IllegalArgumentException the name is not valid, or its file would not be under the directory
    */
    public static Path resolveUnder(Path base, String name, String extension) {
        checkBinaryName(name);
        Path file;
        try {
            file = base.resolve(name.replace('.', '/') + extension).normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("invalid class name for compilation: it cannot be a file name");
        }
        if (!file.startsWith(base) || file.equals(base)) {
            throw new IllegalArgumentException("invalid class name for compilation: its file is outside of the "
                + "compiler's directory");
        }
        return file;
    }

    //! Returns the binary class name of an entry of a compiler's output, which is a path with \c '/' separators
    /** @param entry the name of the entry, ending in \c ".class"

        @return the binary class name

        @throw IllegalArgumentException the entry is not the file of a binary class name
    */
    public static String outputClassName(String entry) {
        if (entry == null || !entry.endsWith(".class")) {
            throw new IllegalArgumentException("invalid compiler output: expecting a class file");
        }
        String path = entry.substring(0, entry.length() - ".class".length());
        // a dot in a path element would be taken as a package separator
        if (path.indexOf('.') >= 0 || !isValidBinaryName(path.replace('/', '.'))) {
            throw new IllegalArgumentException("invalid compiler output: expecting the file of a Java binary name");
        }
        return path.replace('/', '.');
    }

    //! Returns true if \a policy allows reading a file that a compiler found below a directory of its classpath
    /** Anything that stops the check counts as a denial, so that the compiler does not see the file
    */
    public static boolean isReadable(QoreSandbox.Policy policy, Path file) {
        try {
            policy.checkFileAccess(file.toString(), QoreSandbox.READ);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    //! Checks and rewrites the options of the Java compiler for a caller that a sandbox filesystem policy restricts
    /** Options that name no file are kept; a classpath and an output directory are checked against the policy; any
        other option is refused, as it could name a file.  In \a options, the classpath is removed, as the compiler is
        given the checked files instead, an output directory is replaced by the absolute path that was checked, and
        annotation processing is turned off, as it would run code found on the classpath.

        @param options the options, which are changed only if they are accepted

        @return the checked files of the classpath to give the compiler, or null if the options have no classpath

        @throw IllegalArgumentException an option is not allowed or has no value
        @throw FILESYSTEM-ACCESS-DENIED the policy denies a path that an option names
    */
    public static List<File> checkJavacOptions(List<String> options) {
        ArrayList<String> checked = new ArrayList<String>();
        List<File> files = null;
        boolean proc_none = false;
        for (int i = 0; i < options.size(); ++i) {
            String option = options.get(i);
            if (option == null) {
                throw refused();
            }
            if (isFlag(option)) {
                proc_none = proc_none || option.equals("-proc:none");
                checked.add(option);
                continue;
            }
            String key = option;
            String value = null;
            int eq = option.startsWith("--") ? option.indexOf('=') : -1;
            if (eq > 0) {
                key = option.substring(0, eq);
                value = option.substring(eq + 1);
            }
            boolean classpath = CLASSPATH_OPTIONS.contains(key);
            boolean output = OUTPUT_OPTIONS.contains(key);
            if (!classpath && !output && !VALUE_OPTIONS.contains(key)) {
                throw refused();
            }
            if (value == null) {
                if (++i == options.size() || options.get(i) == null) {
                    throw new IllegalArgumentException("a Java compiler option has no value");
                }
                value = options.get(i);
            }
            if (classpath) {
                checkClasspathForm(value);
                // the last classpath is the one used, as for the compiler; each one is checked
                files = QoreSandbox.checkCompilerClasspath(value);
            } else if (output) {
                // the compiler is given the path that is checked; a denial names the path as it was given
                String path = new File(value).getAbsolutePath();
                QoreSandbox.checkGivenPath(path, value, QoreSandbox.CREATE | QoreSandbox.WRITE);
                checked.add(key);
                checked.add(path);
            } else {
                checked.add(key);
                checked.add(value);
            }
        }
        if (!proc_none) {
            checked.add("-proc:none");
        }
        options.clear();
        options.addAll(checked);
        return files;
    }

    //! Refuses a classpath that the compiler would read differently from the way that the policy check resolves it
    /** The compiler takes an empty element as the current directory, splits only on the system path separator, and
        expands a wildcard only as a whole last path element.
    */
    private static void checkClasspathForm(String classpath) {
        for (String element : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
            int wildcard = element.indexOf('*');
            boolean whole = wildcard >= 0 && wildcard == element.length() - 1
                && (wildcard == 0 || element.charAt(wildcard - 1) == File.separatorChar);
            if (element.isEmpty() || element.indexOf(';') >= 0 || (wildcard >= 0 && !whole)) {
                throw new IllegalArgumentException("a Java compiler classpath has a form that cannot be checked "
                    + "against the sandbox filesystem policy");
            }
        }
    }

    //! Returns true if \a option is a javac option that names no file and takes no separate value
    private static boolean isFlag(String option) {
        if (FLAGS.contains(option)) {
            return true;
        }
        for (String prefix : FLAG_PREFIXES) {
            if (option.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    //! Returns the error for an option that is not allowed; the error does not repeat the option, which can be a path
    private static IllegalArgumentException refused() {
        return new IllegalArgumentException("a Java compiler option is not allowed for code that a sandbox filesystem "
            + "policy restricts; use the add-classpath module command for a classpath");
    }
}
