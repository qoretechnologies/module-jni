// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryIteratorException;
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
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
                copy.parent = PrivateParent.acquire();
                copy.root = copy.parent.newRoot();
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

        A retry only removes the directory it created: every root is inside a PrivateParent, which is verified before
        anything below it is removed.
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
        //! the private directory that holds the root; set before the root
        private PrivateParent parent;
        //! the private directory of this copy; set before the copy is used
        private Path root;
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
            end(false);
        }

        //! Called by the thread that makes the copy when it fails, also after a release marked it
        void abort() {
            end(true);
        }

        //! Moves the copy to REMOVING if it can be removed now, and removes its directory
        /** What the copy uses is returned only if the directory is confirmed to be gone.  The removal runs on the one
            thread that moved the copy to REMOVING, again for a release that arrives while an attempt fails.  A failed
            removal never frees any of the budget.  The transition and the guarded removal are in this one frame, with
            no call between them, so that the copy never stays in REMOVING, whatever is raised.

            @param abort true for the thread that makes the copy
        */
        private void end(boolean abort) {
            synchronized (budget) {
                if (!abort && state == MAKING) {
                    state = ENDING;
                    return;
                }
                if (state == REMOVING) {
                    retryRequested = true;
                    return;
                }
                if (state == DONE || (!abort && state == ENDING)) {
                    return;
                }
                state = REMOVING;
            }
            Throwable failure = null;
            boolean settled = false;
            try {
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
                settled = true;
            } finally {
                if (!settled) {
                    synchronized (budget) {
                        if (state == REMOVING) {
                            state = FAILED;
                            retryRequested = false;
                        }
                    }
                }
            }
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
                try {
                    owner.forgetCopy(this);
                } finally {
                    if (parent != null) {
                        parent.release();
                    }
                }
            } else if (report) {
                reportPersistentFailure();
            }
            return false;
        }

        //! Reports once that a copy keeps failing to be removed; names no path, and never raises an error
        private static void reportPersistentFailure() {
            try {
                QoreURLClassLoader.errorLog("a private compiler classpath copy could not be removed: it stays in the "
                    + "JVM's temporary directory and keeps its share of the limits of its Program; closing its "
                    + "classpath or class loader and the end of its Program try again");
            } catch (RuntimeException | Error e) {
                // the state of the copy does not depend on the report
            }
        }

        //! Removes the directory of this copy from its private parent
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
            return parent.removeEntry(root.getFileName(), retry);
        }
    }

    //! The private directory of this JVM that holds the root of every copy
    /** Other users cannot create, rename or replace anything below it, so a copy's root needs no identity of its own.
        Before anything below it is removed, the directory is verified: not a link, a directory, owned by the JVM's
        user, with mode 0700 and the file key that it had when it was created.  If that fails or cannot be determined,
        nothing below it is touched and its copies stay charged.  The check and the removal act on one opened directory.

        A copy is made and read by path, so it is only made in a temporary directory that other users cannot change: its
        real path must be a directory owned by the JVM's numeric UID or by root, that is not writable by group or others
        or that has the sticky bit.  User names are not used, and nothing is created to find the UID.  This is checked
        when this directory is created, whose UID must then be the JVM's, and against that recorded UID each time it is
        used for a new copy.  The root of each new copy must also have the recorded UID, so a process whose effective UID
        changed before this copy's root is created makes no copy under another identity.  A copy is refused otherwise,
        also if a UID cannot be read.

        The removal of a refused root is attempted.  A root that cannot be removed stays inside this directory, keeps it
        in place, and blocks it: while it is the current directory, compiles that need a copy are refused, and the first
        block is logged once.  Blocking, and the check, creation and discard of a root, are made under one lock, so a
        directory holds at most one such root.  When no copy is in flight, the next copy recovers: if this directory no
        longer exists a new one is made, and if it verifies and is empty it is used again.  A leftover that never goes
        means refusal until the process restarts.
        Only where the platform has no POSIX attributes at all can this not be checked, and a copy is made.  The
        directories above it and ACLs are not judged.

        Where the platform has no POSIX attributes or no secure directory stream, only the first removal of a copy walks
        it by path, and a retry leaves it charged.  Code of the same JVM is not defended against.

        It is removed when its last copy is gone, if it is then empty, under the lock that a new copy takes, so a new copy
        never uses a directory that is being removed.  Removing it needs a secure directory stream on the temporary
        directory, so it stays, empty, where the JVM cannot read that directory (ex: mode 1733 of another owner, or 0300
        owned by the JVM's user) or the JDK has no such stream; the next copy then uses it, also after it was blocked.
    */
    static final class PrivateParent {
        //! guards #current and the number of copies of each parent
        private static final Object LOCK = new Object();
        //! the only permissions of a verified directory
        private static final Set<PosixFilePermission> OWNER_ONLY = Collections.unmodifiableSet(EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        //! the temporary directory of the JVM, read once
        private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
        //! the refusal for the temporary directory or the identity of the JVM; it names no path
        private static final String TEMP_REFUSAL = "the JVM's temporary directory can be changed by other users, or it "
            + "or the JVM's user ID cannot be verified, so a private compiler classpath copy cannot be made";
        //! the refusal while this directory holds a refused root; it names no path
        private static final String BLOCKED_REFUSAL = "a refused private compiler classpath directory could not be "
            + "removed, so no new copy can be made until it is gone";
        //! the sticky bit of a Unix file mode
        private static final int STICKY = 01000;
        //! the numeric UID of this process, or null if it cannot be read; guarded by LOCK
        private static Long processUid = jvmUid();
        //! the directory that new copies use; guarded by LOCK
        private static PrivateParent current;

        //! the absolute path of the directory
        private Path path;
        //! the real path of the temporary directory that contains it
        private final Path container;
        //! the owner of the directory, or null if the platform has no POSIX attributes
        private UserPrincipal owner;
        //! the numeric UID of the directory when it was created, or -1 if the platform has no POSIX attributes
        private long uid = -1;
        //! the identity of the directory when it was created, or null if the platform has none
        private final Object key;
        //! true if the directory can be fully verified
        private final boolean posix;
        //! the copies that have a root in this directory; guarded by LOCK
        private int copies;
        //! a refused root could not be removed, so no further copies are made here; guarded by LOCK
        private boolean blocked;
        //! the current block has been logged; guarded by LOCK
        private boolean blockReported;

        private PrivateParent(Path path, Path container) throws IOException {
            this.path = path;
            this.container = container;
            PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                key = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
                posix = false;
                return;
            }
            PosixFileAttributes attrs = view.readAttributes();
            long created = uidOf(path);
            if (!attrs.isDirectory() || !OWNER_ONLY.equals(attrs.permissions()) || processUid == null
                    || created != processUid) {
                throw new IOException(TEMP_REFUSAL);
            }
            uid = created;
            owner = attrs.owner();
            key = attrs.fileKey();
            posix = key != null;
        }

        //! Returns the numeric UID of this process from the JDK, or null if it is not available
        private static Long jvmUid() {
            try {
                // by name: this class exists only in a JDK for a Unix platform
                Class<?> cls = Class.forName("com.sun.security.auth.module.UnixSystem", true,
                    ClassLoader.getPlatformClassLoader());
                long rv = (Long) cls.getMethod("getUid").invoke(cls.getConstructor().newInstance());
                return rv < 0 ? null : Long.valueOf(rv);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        //! Returns the numeric UID of the owner of a path, without following a link
        /** @throw IOException the UID cannot be read
            @throw UnsupportedOperationException the platform has no unix attribute view
            @throw IllegalArgumentException the platform has no unix attribute view
        */
        private static long uidOf(Path path) throws IOException {
            return Integer.toUnsignedLong((Integer) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS));
        }

        //! Returns the directory for a new copy, with the copy counted; a directory that fails verification is left
        /** @throw IOException the temporary directory can be changed by other users, it or the JVM's user ID cannot be
            verified, or the current directory holds a refused root
        */
        static PrivateParent acquire() throws IOException {
            synchronized (LOCK) {
                Path temp = checkedTempDirectory();
                // never replaced while its leftover remains, as a new directory could collect another one
                if (current != null && current.blocked && !current.recoverBlocked()) {
                    throw new IOException(BLOCKED_REFUSAL);
                }
                if (current == null || !temp.equals(current.container) || !current.isIntact()) {
                    current = create(temp);
                }
                ++current.copies;
                return current;
            }
        }

        //! Ends the block of the current directory if no copy is in flight and it is gone, or verified and empty
        /** Called with LOCK held, which a new copy needs in order to be counted
            @return true if a copy can now be made; #current is null if the directory is gone
        */
        private boolean recoverBlocked() {
            if (copies != 0) {
                return false;
            }
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                current = null;
                return true;
            }
            if (!isVerifiedEmpty()) {
                return false;
            }
            blocked = false;
            blockReported = false;
            return true;
        }

        //! Returns true if this directory verifies and has no entries
        /** Through the opened and verified directory where there is a secure directory stream.  Elsewhere the directory
            is verified by name and then listed, which needs no access to the temporary directory.
        */
        private boolean isVerifiedEmpty() {
            if (!isIntact()) {
                return false;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                if (posix && stream instanceof SecureDirectoryStream && !matches(((SecureDirectoryStream<Path>) stream)
                        .getFileAttributeView(PosixFileAttributeView.class).readAttributes())) {
                    return false;
                }
                return !stream.iterator().hasNext();
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        //! Returns the real path of the JVM's temporary directory, if other users cannot change it
        /** @throw IOException it can be changed by other users, or it or the JVM's user ID cannot be verified; the error
            names no path
        */
        private static Path checkedTempDirectory() throws IOException {
            Path real = null;
            boolean safe = false;
            try {
                real = Path.of(TEMP_DIR).toRealPath();
                // an existing parent is compared with the UID that was recorded when it was created
                safe = safeDirectory(real, current != null ? Long.valueOf(current.uid) : processUid);
            } catch (IOException | RuntimeException e) {
                // refused below, without the text of this error, which can name a path
            }
            if (!safe) {
                throw new IOException(TEMP_REFUSAL);
            }
            return real;
        }

        //! Returns true if other users cannot change a directory, for a JVM with the given UID
        /** @param real the real path of the directory
            @param expected the UID of the JVM; null if it cannot be read, which refuses on a POSIX platform
            @return false also if anything that the rule needs cannot be read
        */
        private static boolean safeDirectory(Path real, Long expected) {
            try {
                PosixFileAttributeView view = Files.getFileAttributeView(real, PosixFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
                if (view == null) {
                    return Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS);
                }
                PosixFileAttributes attrs = view.readAttributes();
                long dirUid = uidOf(real);
                if (!attrs.isDirectory() || expected == null || (dirUid != expected && dirUid != 0)) {
                    return false;
                }
                Set<PosixFilePermission> perms = attrs.permissions();
                return (!perms.contains(PosixFilePermission.GROUP_WRITE)
                        && !perms.contains(PosixFilePermission.OTHERS_WRITE))
                    || ((Integer) Files.getAttribute(real, "unix:mode", LinkOption.NOFOLLOW_LINKS) & STICKY) != 0;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        //! Creates a new private directory in a temporary directory that has been checked
        private static PrivateParent create(Path temp) throws IOException {
            Path dir;
            try {
                dir = Files.createTempDirectory(temp, "qore-classpath-");
            } catch (IOException e) {
                throw new IOException("a private directory for the compiler classpath could not be created");
            }
            try {
                return new PrivateParent(dir, temp);
            } catch (IOException | RuntimeException e) {
                // its text can name a path
                cleanup(e, () -> removeEmpty(dir));
                throw new IOException(TEMP_REFUSAL);
            } catch (Error e) {
                cleanup(e, () -> removeEmpty(dir));
                throw e;
            }
        }

        //! Creates the root of a new copy, which must have the UID recorded for this directory
        /** The check, the creation, the check of its UID and its discard are made under LOCK, so no root is created in a
            directory after it is blocked.  A root is empty here, so no content is copied or removed under the lock.

            @throw IOException it could not be created, this directory holds a refused root, or it has another UID or its
            UID cannot be read, in which case its removal is attempted
        */
        Path newRoot() throws IOException {
            try {
                synchronized (LOCK) {
                    if (blocked) {
                        throw new IOException(BLOCKED_REFUSAL);
                    }
                    Path root;
                    try {
                        root = Files.createTempDirectory(path, "qore-classpath-");
                    } catch (IOException e) {
                        throw new IOException("a private directory for the compiler classpath could not be created");
                    }
                    if (uid < 0) {
                        return root;
                    }
                    boolean same = false;
                    try {
                        same = uidOf(root) == uid;
                    } catch (IOException | RuntimeException e) {
                        // refused below, without the text of this error, which can name a path
                    } catch (Error e) {
                        cleanup(e, () -> discardRoot(root));
                        throw e;
                    }
                    if (!same) {
                        discardRoot(root);
                        throw new IOException(TEMP_REFUSAL);
                    }
                    return root;
                }
            } finally {
                // outside the lock: logging does not run under it
                reportBlocked();
            }
        }

        //! Removes a root that was refused, with LOCK held; if it cannot be removed, this directory is blocked
        private void discardRoot(Path root) {
            boolean removed = false;
            try {
                removed = removeEntry(root.getFileName(), false);
            } finally {
                if (!removed) {
                    blocked = true;
                }
            }
        }

        //! Logs a block once, with no path; never raises an error
        private void reportBlocked() {
            try {
                boolean report;
                synchronized (LOCK) {
                    report = blocked && !blockReported;
                    blockReported = blockReported || report;
                }
                if (report) {
                    QoreURLClassLoader.errorLog(BLOCKED_REFUSAL);
                }
            } catch (RuntimeException | Error e) {
                // the state of the directory does not depend on the report
            }
        }

        //! Called when a copy's root is gone: the last copy removes the directory
        void release() {
            synchronized (LOCK) {
                if (--copies == 0 && isIntact() && removeEmpty(path) && current == this) {
                    current = null;
                }
            }
        }

        //! Returns true if the directory verifies by its name
        private boolean isIntact() {
            try {
                if (posix) {
                    return matches(Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
                }
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                return attrs.isDirectory() && Objects.equals(key, attrs.fileKey());
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        //! Returns true if the attributes are those of this directory
        private boolean matches(PosixFileAttributes attrs) {
            return attrs.isDirectory() && !attrs.isSymbolicLink() && key.equals(attrs.fileKey())
                && owner.equals(attrs.owner()) && OWNER_ONLY.equals(attrs.permissions());
        }

        //! Removes the only level of a copy's root below this directory, without following links
        /** @param name the name of the root in this directory
            @param retry true if a removal of this root has already failed
            @return true if the root is confirmed to be gone; false if it may remain or nothing could be verified
        */
        boolean removeEntry(Path name, boolean retry) {
            if (retry && !posix) {
                return false;
            }
            if (!isIntact()) {
                return false;
            }
            try {
                if (posix) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                        if (stream instanceof SecureDirectoryStream) {
                            @SuppressWarnings("unchecked")
                            SecureDirectoryStream<Path> dir = (SecureDirectoryStream<Path>) stream;
                            // the opened directory is the one that is verified and used, whatever its name is now
                            if (!matches(dir.getFileAttributeView(PosixFileAttributeView.class).readAttributes())) {
                                return false;
                            }
                            try {
                                // only a directory is a root: anything else at its name stays
                                if (!dir.getFileAttributeView(name, BasicFileAttributeView.class,
                                        LinkOption.NOFOLLOW_LINKS).readAttributes().isDirectory()) {
                                    return false;
                                }
                                deleteDirectoryAt(dir, name);
                            } catch (NoSuchFileException e) {
                                // it is already gone
                            }
                            return true;
                        }
                    }
                }
                if (retry) {
                    return false;
                }
                Path target = path.resolve(name);
                if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                    deleteTree(target);
                }
                return Files.notExists(target, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        //! Deletes an entry of an opened directory relative to it, without following links
        /** A failure below a directory leaves that entry in place and the other entries are still tried, also if listing
            the directory fails; the directory then cannot be deleted, which raises the error to the caller
        */
        private static void deleteEntry(SecureDirectoryStream<Path> dir, Path name) throws IOException {
            BasicFileAttributes attrs = dir.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
            if (attrs.isDirectory()) {
                deleteDirectoryAt(dir, name);
            } else {
                dir.deleteFile(name);
            }
        }

        //! Deletes a directory of an opened directory and what is below it; it never removes anything but a directory
        private static void deleteDirectoryAt(SecureDirectoryStream<Path> dir, Path name) throws IOException {
            try (SecureDirectoryStream<Path> sub = dir.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                for (Path entry : sub) {
                    try {
                        deleteEntry(sub, entry.getFileName());
                    } catch (IOException | RuntimeException e) {
                        // the entry stays, so the deletion of this directory fails below
                    }
                }
            } catch (DirectoryIteratorException e) {
                // what was not listed stays, so the deletion of this directory fails below
            }
            dir.deleteDirectory(name);
        }

        //! Deletes what it can of a directory tree by path, without following links, on a first removal
        /** Only where the platform has no secure directory stream or no POSIX attributes
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

        //! Removes an empty directory from the directory that contains it; it never removes anything else
        /** @return true if it was removed; false if not, also where the platform has no secure directory stream
        */
        private static boolean removeEmpty(Path dir) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.getParent())) {
                if (stream instanceof SecureDirectoryStream) {
                    ((SecureDirectoryStream<Path>) stream).deleteDirectory(dir.getFileName());
                    return true;
                }
            } catch (IOException | RuntimeException e) {
                // it stays, and is empty
            }
            return false;
        }
    }
}
