// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

//! The classpath of one compilation for a caller that a filesystem policy restricts
/** Made by QoreURLClassLoader.openCompilerClasspath() for the class loader and its parents, and closed when the
    compilation ends, also if it fails.  Directories that restricted code added are given as private copies of the
    files that their policies allow; closing the object removes them.

    @since jni 3.0
*/
public final class QoreCompilerClasspath implements AutoCloseable {
    //! the most files in the outstanding copies of a Program
    static final int MAX_FILES = 100000;
    //! the most bytes in the outstanding copies of a Program
    static final long MAX_BYTES = 1L << 30;
    //! the most directory entries visited for the outstanding copies of a Program
    static final int MAX_ENTRIES = 200000;
    //! the most outstanding copies of a Program
    static final int MAX_COPIES = 32;
    //! the deepest directory level that a removal descends to; deeper than any copy, whose depth is limited
    private static final int MAX_REMOVE_DEPTH = 1024;
    //! a system property that can lower MAX_ENTRIES
    private static final String MAX_ENTRIES_PROP = "qore.jni.compiler.maxEntries";

    //! the kinds of resources that a copy is charged for
    static final int FILES = 0;
    static final int BYTES = 1;
    static final int ENTRIES = 2;
    private static final int KINDS = 3;
    //! the names of the kinds of resources, for errors
    private static final String[] KIND_NAMES = {"files", "bytes", "directory entries"};

    //! the locations of the classpath, in order
    private final ArrayList<File> locations = new ArrayList<>();
    //! the copies that this object owns
    private final ArrayList<Copy> copies = new ArrayList<>();

    QoreCompilerClasspath() {
    }

    //! Returns the files and directories to give the compiler, in order
    public List<File> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    //! Removes the private copies of this classpath; closing it again has no effect
    @Override
    public void close() {
        endAll(copies, false);
    }

    //! Ends each copy, also after one of them fails; the first failure is raised afterwards, with the others suppressed
    /** @param abort true for the thread that makes the copies, which removes them; false for a release
    */
    static void endAll(Iterable<Copy> copies, boolean abort) {
        Throwable first = null;
        for (Copy copy : copies) {
            try {
                if (abort) {
                    copy.abort();
                } else {
                    copy.release();
                }
            } catch (RuntimeException | Error e) {
                // recording the failure can itself fail (ex: no stack left): the loop continues in any case
                try {
                    first = combine(first, e);
                } catch (RuntimeException | Error recording) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
        }
        rethrow(first);
    }

    //! Returns the first failure, with a later one suppressed in it; recording it can never raise an error
    static Throwable combine(Throwable first, Throwable later) {
        if (first == null) {
            return later;
        }
        if (later != first) {
            try {
                first.addSuppressed(later);
            } catch (RuntimeException | Error e) {
                // the first failure is still the one that is raised
            }
        }
        return first;
    }

    //! Raises a failure that was caught as a RuntimeException or an Error; does nothing for null
    static void rethrow(Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure != null) {
            throw (RuntimeException) failure;
        }
    }

    //! Runs a cleanup action while \a primary, if any, is being raised: a failure of the action never replaces it
    /** @param primary the failure that is being raised, in which a failure of the action is suppressed; if null, a
        failure of the action is raised
    */
    static void cleanup(Throwable primary, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            if (primary == null) {
                throw e;
            }
            try {
                combine(primary, e);
            } catch (RuntimeException | Error recording) {
                // the primary failure is still the one that is raised
            }
        }
    }

    //! Called when every class loader of the chain has been added: only then can a copy be removed by a release
    /** @throw IOException a copy was released while the classpath was being made
    */
    void ready() throws IOException {
        for (Copy copy : copies) {
            copy.ready();
        }
    }

    //! Called by the thread that makes the classpath when it fails: removes every copy
    void abort() {
        endAll(copies, true);
    }

    void addLocation(File location) {
        locations.add(location);
    }

    void addCopy(Copy copy) {
        copies.add(copy);
    }

    //! What the copies of one Program use, and the copies that are live
    /** One object is shared by the class loader of a Program and the class loaders derived from it, so that the limits
        hold across them, and the end of the Program ends all of their copies.  Derived loaders keep the Program's copy
        budget also when their own Program pointer is cleared.  It belongs to the Program's class loader, so it cannot
        be confused with another Program.
    */
    static final class Budget {
        //! what the live copies use; guarded by this object
        private final long[] used = new long[KINDS];
        //! the copies that have not ended; guarded by this object
        private final HashSet<Copy> live = new HashSet<>();
        //! true once the Program has ended; guarded by this object
        private boolean closed;

        //! Starts a copy in a new private directory
        /** @throw IOException the Program has ended, or has as many copies as it can have
        */
        Copy open(QoreClasspathGuard owner) throws IOException {
            Copy copy = new Copy(this, owner);
            synchronized (this) {
                if (closed) {
                    throw new IOException("the Program of the class loader has ended");
                }
                if (live.size() >= MAX_COPIES) {
                    throw new IOException("the Program has as many compiler classpath copies as it can have at the "
                        + "same time");
                }
                live.add(copy);
            }
            try {
                // only its owner can use it
                copy.root = Files.createTempDirectory("qore-classpath-");
                // the identity of the directory: a later removal acts only on this directory
                copy.rootKey = Files.readAttributes(copy.root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                    .fileKey();
            } catch (Throwable e) {
                cleanup(e, copy::abort);
                throw e;
            }
            return copy;
        }

        //! Ends every copy, and refuses new ones
        void closeAll() {
            Copy[] current;
            synchronized (this) {
                closed = true;
                current = live.toArray(new Copy[0]);
            }
            endAll(Arrays.asList(current), false);
        }

        //! Returns true if the Program has ended
        synchronized boolean isClosed() {
            return closed;
        }
    }

    //! One private copy, with one owner at each moment and one return of what it used
    /** The thread that makes a copy owns it until it is ready.  A release while it is being made only marks it: the next
        charge fails, and the making thread removes it.  After it is ready, a release removes it.  What the copy uses is
        returned only once its directory is confirmed to be gone, in the transition to DONE, so that happens once.  A
        removal that fails leaves the copy charged and live in the FAILED state, and the next release (closing its
        classpath or its class loader, or the end of its Program) tries again; a release that arrives during a removal
        makes the thread that is removing try again if that attempt fails.  Directories are only created below a root
        that exists and that this object owns, by the thread that owns it.

        A retry only removes the directory it created: the identity of the root is recorded when it is created, and if
        its name is missing or refers to anything else, the private directory is gone and that name is not touched.  The
        entries are removed relative to the opened directory, without following links, where the platform has a secure
        directory stream.  Where it does not, only the first removal walks the directory by path; a retry leaves the copy
        charged.
    */
    static final class Copy {
        private static final int MAKING = 0;
        private static final int READY = 1;
        private static final int ENDING = 2;
        //! one thread is removing the directory
        private static final int REMOVING = 3;
        //! a removal failed: still charged, and removed again by the next release
        private static final int FAILED = 4;
        private static final int DONE = 5;

        private final Budget budget;
        private final QoreClasspathGuard owner;
        //! what this copy has charged; guarded by the budget
        private final long[] charged = new long[KINDS];
        //! the state; guarded by the budget
        private int state = MAKING;
        //! the private directory; set before the copy is used
        private Path root;
        //! the identity of the private directory when it was created, or null if the platform has none
        private Object rootKey;
        //! a release arrived during a removal; guarded by the budget
        private boolean retryRequested;
        //! the number of removals that have ended; guarded by the budget
        private int attempts;
        //! true once a persistent failure has been reported; guarded by the budget
        private boolean reported;

        private Copy(Budget budget, QoreClasspathGuard owner) {
            this.budget = budget;
            this.owner = owner;
        }

        //! Charges \a n of a resource
        /** @throw IOException the copy has been released, or the charge is over the limit of the Program
        */
        void charge(int kind, long n) throws IOException {
            // read before the lock: reading a system property can run code
            long limit = limit(kind);
            synchronized (budget) {
                if (state != MAKING) {
                    throw new IOException("the compiler classpath has been released");
                }
                if (budget.used[kind] + n > limit) {
                    throw new IOException("the classpath is over the limit of " + KIND_NAMES[kind]
                        + " that compilers of a Program can use at the same time");
                }
                budget.used[kind] += n;
                charged[kind] += n;
            }
        }

        //! Returns the limit of a kind of resource; a system property can only lower the limit of entries
        private static long limit(int kind) {
            switch (kind) {
                case FILES:
                    return MAX_FILES;
                case BYTES:
                    return MAX_BYTES;
                default:
                    Integer limit = Integer.getInteger(MAX_ENTRIES_PROP);
                    return limit != null && limit > 0 && limit < MAX_ENTRIES ? limit : MAX_ENTRIES;
            }
        }

        //! Creates a directory directly below the root, for the thread that makes the copy
        Path newLocation(String name) throws IOException {
            return Files.createDirectory(root.resolve(name));
        }

        //! Creates one directory level below a directory of this copy; its parent already exists
        static void createBelow(Path dir) throws IOException {
            try {
                Files.createDirectory(dir);
            } catch (FileAlreadyExistsException e) {
                // two names can have the same form in the JVM's encoding; a link is never used
                if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                    throw e;
                }
            }
        }

        //! Copies a checked file, within the limits, without following a link that replaced it
        void copyFile(Path real, Path target) throws IOException {
            charge(FILES, 1);
            byte[] buf = new byte[8192];
            try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS);
                    OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    charge(BYTES, n);
                    out.write(buf, 0, n);
                }
            }
        }

        //! Called by the thread that makes the copy when it is complete
        /** @throw IOException the copy was released while it was being made
        */
        void ready() throws IOException {
            synchronized (budget) {
                if (state == MAKING) {
                    state = READY;
                    return;
                }
            }
            throw new IOException("the compiler classpath has been released");
        }

        //! Ends the copy: a copy being made is marked for the thread that makes it, any other is removed
        void release() {
            synchronized (budget) {
                if (state == MAKING) {
                    state = ENDING;
                    return;
                }
                if (state == REMOVING) {
                    retryRequested = true;
                    return;
                }
                if (state != READY && state != FAILED) {
                    return;
                }
                state = REMOVING;
            }
            remove();
        }

        //! Called by the thread that makes the copy when it fails, also after a release marked it
        void abort() {
            synchronized (budget) {
                if (state == DONE) {
                    return;
                }
                if (state == REMOVING) {
                    retryRequested = true;
                    return;
                }
                state = REMOVING;
            }
            remove();
        }

        //! Removes the directory; what the copy uses is returned only if the directory is confirmed to be gone
        /** Runs on the one thread that moved the copy to REMOVING, again for a release that arrives while an attempt
            fails.  A failed removal never frees any of the budget.
        */
        private void remove() {
            Throwable failure = null;
            boolean again;
            do {
                boolean gone = false;
                try {
                    gone = removeOwnDirectory();
                } catch (RuntimeException | Error e) {
                    failure = combine(failure, e);
                }
                again = finishRemoval(gone);
            } while (again);
            rethrow(failure);
        }

        //! Ends a removal: returns the charges once if the directory is gone, or else leaves the copy to be retried
        /** @return true if a release arrived during a removal that failed, so that it is tried again at once
        */
        private boolean finishRemoval(boolean gone) {
            boolean report = false;
            synchronized (budget) {
                ++attempts;
                if (!gone) {
                    if (retryRequested) {
                        retryRequested = false;
                        return true;
                    }
                    state = FAILED;
                    report = attempts > 1 && !reported;
                    reported = reported || report;
                } else {
                    state = DONE;
                    for (int i = 0; i < KINDS; ++i) {
                        budget.used[i] -= charged[i];
                        charged[i] = 0;
                    }
                    budget.live.remove(this);
                }
            }
            if (gone) {
                owner.forgetCopy(this);
            } else if (report) {
                reportPersistentFailure();
            }
            return false;
        }

        //! Reports once that a copy keeps failing to be removed; names no path, and never raises an error
        private static void reportPersistentFailure() {
            try {
                QoreURLClassLoader.errorLog("a private compiler classpath copy could not be removed: it keeps its "
                    + "share of the limits of its Program, and its removal is tried again when its class loader is "
                    + "closed and when its Program ends");
            } catch (RuntimeException | Error e) {
                // the state of the copy does not depend on the report
            }
        }

        //! Returns true if the name of the root is missing or refers to something other than the directory created
        /** Any other failure to tell means that the directory may still be there
        */
        private boolean rootIsGone() {
            try {
                BasicFileAttributes attrs = Files.readAttributes(root, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                return rootKey != null && !rootKey.equals(attrs.fileKey());
            } catch (NoSuchFileException e) {
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        //! Removes the directory that this copy created, and nothing else
        /** @return true if the directory is confirmed to be gone
        */
        private boolean removeOwnDirectory() {
            if (root == null) {
                return true;
            }
            boolean retry;
            synchronized (budget) {
                retry = attempts > 0;
            }
            if (rootIsGone()) {
                return true;
            }
            // on a retry, a directory whose identity cannot be verified is not walked
            if (retry && rootKey == null) {
                return false;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                if (stream instanceof SecureDirectoryStream) {
                    @SuppressWarnings("unchecked")
                    SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) stream;
                    Object opened = secure.getFileAttributeView(BasicFileAttributeView.class).readAttributes()
                        .fileKey();
                    // the name came to refer to another directory after it was checked: it is not touched
                    if (rootKey != null && !rootKey.equals(opened)) {
                        return true;
                    }
                    deleteEntries(secure, 0);
                } else if (retry) {
                    // no way to act relative to the verified directory: the copy stays charged
                    return false;
                } else {
                    for (Path entry : stream) {
                        deleteTree(entry);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // what is left is found below
            }
            try {
                if (!rootIsGone()) {
                    Files.delete(root);
                }
            } catch (IOException | RuntimeException e) {
                // what is left is found below
            }
            return rootIsGone();
        }

        //! Deletes the entries of an opened directory relative to it, without following links
        /** A failure leaves the entry in place, for the caller to find, and the other entries are still tried
        */
        private static void deleteEntries(SecureDirectoryStream<Path> dir, int depth) {
            for (Path entry : dir) {
                Path name = entry.getFileName();
                try {
                    BasicFileAttributes attrs = dir.getFileAttributeView(name, BasicFileAttributeView.class,
                        LinkOption.NOFOLLOW_LINKS).readAttributes();
                    if (attrs.isDirectory()) {
                        if (depth < MAX_REMOVE_DEPTH) {
                            try (SecureDirectoryStream<Path> sub = dir.newDirectoryStream(name,
                                    LinkOption.NOFOLLOW_LINKS)) {
                                deleteEntries(sub, depth + 1);
                            }
                        }
                        dir.deleteDirectory(name);
                    } else {
                        dir.deleteFile(name);
                    }
                } catch (IOException | RuntimeException e) {
                    // the entry stays; the directory is then found not to be gone
                }
            }
        }

        //! Deletes what it can of a directory tree by path, without following links, on a first removal
        /** Only where the platform has no secure directory stream; an I/O error is left for the caller to find, as the
            directory then still exists
        */
        private static void deleteTree(Path path) {
            try {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
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
    }
}
