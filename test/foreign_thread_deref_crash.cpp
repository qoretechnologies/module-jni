/*  foreign_thread_deref_crash.cpp

    Regression test for the module-jni NativeCleanup use-after-free crash.

    module-jni's NativeCleanup C++ daemon thread (native_cleanup_dispatch() in
    src/Globals.cpp) dereferences Java-held Qore values when their Java wrapper
    is garbage-collected.  Those derefs can run Qore-level cleanup (object
    destructors, closure-variable derefs, QoreProgram dependency derefs) that
    require a valid Qore thread context.  The cleanup thread is attached to the
    JVM but is NOT a registered Qore thread, so without a Qore thread context
    such a deref crashes (SIGSEGV) -- this is what masked the real fault behind
    JVM signal-handler frames in field crash reports (e.g. OPC-UA service
    shutdown).

    The fix wraps the dispatch in a QoreForeignThreadHelper, which registers the
    current thread as a foreign Qore thread for the duration of the deref.

    This test models the situation directly at the libqore level: it derefs a
    closure (whose deref runs an object destructor) on a bare std::thread that
    has no Qore thread context.

      - "unsafe" mode: deref with no thread context         -> must crash (signal)
      - "safe"   mode: deref under QoreForeignThreadHelper   -> must succeed

    Run with no arguments, the program is a self-validating driver: it forks a
    child for the unsafe path (asserting it dies by signal) and runs the safe
    path (asserting it succeeds), exiting 0 only if both hold.
*/

#include <qore/Qore.h>

#include <thread>
#include <string>
#include <cstdio>
#include <cstring>

#include <sys/types.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <unistd.h>

// Build a closure (in a program kept alive) that, when its last reference is
// released, runs an object destructor -- i.e. a deref that needs a Qore thread
// context.  Returns the closure's call reference with one extra reference held
// (simulating a Java QoreClosure holding the call ref).
static ResolvedCallReferenceNode* make_closure(QoreProgram*& pgm) {
    ExceptionSink xsink;
    pgm = new QoreProgram(PO_NEW_STYLE);
    const char* code =
        "class Tracker { destructor() { string s = sprintf(\"t%d\", gettid()); s += \"!\"; } }\n"
        "code sub mk() { Tracker o(); return int sub () { return exists o ? 1 : 0; }; }\n";
    pgm->parse(code, "foreign_thread_deref_crash", &xsink);
    if (xsink) { xsink.handleExceptions(); return nullptr; }
    QoreValue c = pgm->callFunction("mk", nullptr, &xsink);
    if (xsink) { xsink.handleExceptions(); return nullptr; }
    ResolvedCallReferenceNode* call = c.get<ResolvedCallReferenceNode>();
    call->ref();          // simulate the Java QoreClosure holding the call ref
    c.discard(&xsink);    // drop the local QoreValue ref; closure is sole owner of the Tracker
    return call;          // program kept alive
}

// Deref the closure on a bare native thread, with or without a Qore thread context.
static int run_mode(bool use_fix) {
    qore_init(QL_GPL);
    {
        QoreProgram* pgm = nullptr;
        ResolvedCallReferenceNode* call = make_closure(pgm);
        if (!call) { qore_cleanup(); return 2; }
        std::thread t([call, use_fix]() {
            ExceptionSink xs;
            if (use_fix) {
                QoreForeignThreadHelper qfth;   // the fix: register the foreign thread
                call->deref(&xs);
            } else {
                call->deref(&xs);               // bug: deref with no Qore thread context
            }
            xs.clear();
        });
        t.join();
        ExceptionSink xsink;
        pgm->waitForTerminationAndDeref(&xsink);
        xsink.clear();
    }
    qore_cleanup();
    return 0;
}

int main(int argc, char** argv) {
    if (argc > 1 && !strcmp(argv[1], "unsafe")) {
        struct rlimit rl = {0, 0};
        setrlimit(RLIMIT_CORE, &rl);            // suppress core dump for the intentional crash
        return run_mode(false);
    }
    if (argc > 1 && (!strcmp(argv[1], "safe") || !strcmp(argv[1], "fix"))) {
        return run_mode(true);
    }

    // self-validating driver
    bool ok = true;

    // 1) unsafe path must die by signal (reproduces the bug)
    pid_t pid = fork();
    if (pid == 0) {
        struct rlimit rl = {0, 0};
        setrlimit(RLIMIT_CORE, &rl);
        _exit(run_mode(false));
    }
    int status = 0;
    waitpid(pid, &status, 0);
    if (WIFSIGNALED(status)) {
        printf("PASS: unsafe deref (no thread context) crashed as expected (signal %d)\n",
            WTERMSIG(status));
    } else {
        printf("FAIL: unsafe deref did NOT crash (exit %d) -- bug no longer reproduces\n",
            WIFEXITED(status) ? WEXITSTATUS(status) : -1);
        ok = false;
    }

    // 2) safe path (QoreForeignThreadHelper) must succeed (verifies the fix)
    pid = fork();
    if (pid == 0) {
        _exit(run_mode(true));
    }
    status = 0;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        printf("PASS: safe deref under QoreForeignThreadHelper succeeded\n");
    } else {
        printf("FAIL: safe deref crashed/failed (signaled=%d, exit=%d)\n",
            WIFSIGNALED(status) ? WTERMSIG(status) : 0,
            WIFEXITED(status) ? WEXITSTATUS(status) : -1);
        ok = false;
    }

    printf("%s\n", ok ? "OK" : "FAILED");
    return ok ? 0 : 1;
}
