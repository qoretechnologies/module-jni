package org.qore.jni.compiler;

import java.io.*;
import java.net.URI;
import java.util.*;
import javax.tools.*;

import org.qore.jni.QoreSandbox;
import org.qore.jni.QoreURLClassLoader;

/**
 * JavaFileManager used for dynamic imports from %Qore into Java
 *
 * @author adapted for %Qore by <a href="mailto:david@qore.org">David Nichols</a>
 * @since 07-Feb-2021
 */
public class QoreJavaFileManager implements JavaFileManager {
    private final QoreURLClassLoader classLoader;
    private final StandardJavaFileManager standardFileManager;
    private final PackageInternalsFinder finder;
    //! the policy of a restricted caller, which each file found on the classpath is checked with, or null
    private QoreSandbox.Policy readPolicy;

    /**
     * Creates the object with the %Qore class loader and a file manager for real file operations
     * @param classLoader the classloader to use
     * @param standardFileManager the StandardJavaFileManager to use for real file operations
     */
    public QoreJavaFileManager(QoreURLClassLoader classLoader, StandardJavaFileManager standardFileManager) {
        this.classLoader = classLoader;
        this.standardFileManager = standardFileManager;
        finder = new PackageInternalsFinder(classLoader);
    }

    //! Sets the policy of a restricted caller: files that it denies below a directory of the classpath are not seen
    void setReadPolicy(QoreSandbox.Policy policy) {
        readPolicy = policy;
        finder.setReadPolicy(policy);
    }

    //! Returns \a file, or null if the file is on the classpath and the policy of a restricted caller denies it
    private <F extends FileObject> F checked(Location location, F file) {
        return file == null || isVisible(location, file) ? file : null;
    }

    //! Returns false if the policy of a restricted caller denies a file of the classpath
    /** Only a file of the filesystem is checked: an entry of an archive is in a file that was checked as a whole
    */
    private boolean isVisible(Location location, FileObject file) {
        if (readPolicy == null || location != StandardLocation.CLASS_PATH || file instanceof QoreJavaClassObject) {
            return true;
        }
        URI uri = file.toUri();
        if (!"file".equals(uri.getScheme())) {
            return true;
        }
        try {
            // the path that the file manager opens for the object, also with another file name encoding
            return QoreCompilerPolicy.isReadable(readPolicy, java.nio.file.Path.of(uri));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return classLoader;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof QoreJavaClassObject) {
            return ((QoreJavaClassObject) file).binaryName();
        } else if (file instanceof CustomJavaFileObject) {
            return ((CustomJavaFileObject) file).binaryName();
        } else { // if it's not QoreJavaClassObject, then it's coming from standard file manager - let it handle the file
            return standardFileManager.inferBinaryName(location, file);
        }
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return standardFileManager.isSameFile(a, b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return standardFileManager.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(Location location) {
        // we don't care about source and other location types - not needed for compilation
        if (location == StandardLocation.CLASS_PATH || location == StandardLocation.PLATFORM_CLASS_PATH
            || location.getName().startsWith("SYSTEM_MODULES")) {
            return true;
        }

        return false;
    }

    @Override
    public Location getLocationForModule(Location location, String moduleName) throws IOException {
        return standardFileManager.getLocationForModule(location, moduleName);
    }

    @Override
    public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
        return standardFileManager.getLocationForModule(location, fo);
    }

    @Override
    public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
        return standardFileManager.listLocationsForModules(location);
    }

    @Override
    public String inferModuleName(Location location) throws IOException {
        return standardFileManager.inferModuleName(location);
    }

    public void setLocation(StandardLocation location, List<? extends File> files) throws IOException {
        standardFileManager.setLocation(location, files);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        return checked(location, standardFileManager.getJavaFileForInput(location, className, kind));
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        return standardFileManager.getJavaFileForOutput(location, className, kind, sibling);
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        return checked(location, standardFileManager.getFileForInput(location, packageName, relativeName));
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling)
            throws IOException {
        return standardFileManager.getFileForOutput(location, packageName, relativeName, sibling);
    }

    @Override
    public void flush() throws IOException {
        standardFileManager.flush();
    }

    @Override
    public void close() throws IOException {
        standardFileManager.close();
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
            boolean recurse) throws IOException {
        Iterable<JavaFileObject> files = listIntern(location, packageName, kinds, recurse);
        if (readPolicy == null || location != StandardLocation.CLASS_PATH) {
            return files;
        }
        List<JavaFileObject> visible = new ArrayList<JavaFileObject>();
        for (JavaFileObject file : files) {
            if (isVisible(location, file)) {
                visible.add(file);
            }
        }
        return visible;
    }

    private Iterable<JavaFileObject> listIntern(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
            boolean recurse) throws IOException {
        boolean baseModule = location.getName().startsWith("SYSTEM_MODULES");

        //System.out.printf("QJFM.list() loc: %s pn: %s kinds: %s recurse: %s (base: %s)\n", location.getName(),
        //    packageName, kinds, recurse, baseModule ? "true" : "false");

        if (baseModule || location == StandardLocation.PLATFORM_CLASS_PATH
            || !kinds.contains(JavaFileObject.Kind.CLASS)) {
            return standardFileManager.list(location, packageName, kinds, recurse);
        } else if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
            List<JavaFileObject> list = finder.find(packageName);
            standardFileManager.list(location, packageName, kinds, recurse).forEach((e) -> {
                list.add(e);
            });

            //System.out.printf("QJFM.list() loc: %s pn: %s kinds: %s recurse: %s (base: %s) list: %s\n",
            //    location.getName(), packageName, kinds, recurse, baseModule ? "true" : "false", list);

            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public int isSupportedOption(String option) {
        return standardFileManager.isSupportedOption(option);
    }
}