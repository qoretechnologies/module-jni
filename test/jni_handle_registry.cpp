/* Copyright (C) 2026 Qore Technologies, s.r.o.
   SPDX-License-Identifier: LGPL-2.1-or-later */

// Unit test of the registry of the opaque handles that Java holds for native state

#include "JniHandleRegistry.h"

#include <atomic>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <set>
#include <stdexcept>
#include <thread>
#include <vector>

using namespace jni;

namespace jni {
struct TestState {
    std::atomic<int>* destroyed;
    explicit TestState(std::atomic<int>* destroyed) : destroyed(destroyed) {
    }
    ~TestState() {
        destroyed->fetch_add(1);
    }
};

template <>
struct JniHandleTraits<TestState> {
    static constexpr JniHandleKind kind = JniHandleKind::Object;
};
}

static void check(bool condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAILED: %s\n", message);
        exit(1);
    }
}

// the published test vector of Speck64/128
static void known_answer() {
    const uint32_t key[4] = {0x1b1a1918, 0x13121110, 0x0b0a0908, 0x03020100};
    check(jni_handle_permute(0x3b7265747475432dull, key) == 0x8c6fa548454e028bull, "Speck64/128 known answer");
}

static void lifecycle() {
    std::atomic<int> destroyed{0};
    size_t base = jni_handle_count();
    jlong h = jni_handle_add(std::make_shared<TestState>(&destroyed));
    check(h != 0, "a handle is never zero");
    check(jni_handle_count() == base + 1, "the handle is counted");
    check(!jni_handle_find(h, JniHandleKind::Closure), "a handle of another kind is not found");
    check(!jni_handle_take(h, JniHandleKind::Closure), "a handle of another kind is not removed");
    check((bool)jni_handle_get<TestState>(h), "a live handle is found");

    bool raised = false;
    try {
        jni_handle_get<TestState>(h ^ 1);
    } catch (const JniInvalidHandle&) {
        raised = true;
    }
    check(raised, "an unknown handle raises an error");
    check(!jni_handle_find(0, JniHandleKind::Object), "handle zero is not found");
    check(!jni_handle_consume<TestState>(0), "handle zero is not removed");

    // a lease keeps the state valid after the release
    std::shared_ptr<TestState> lease = jni_handle_get<TestState>(h);
    check((bool)jni_handle_consume<TestState>(h), "the first release gets the state");
    check(!jni_handle_consume<TestState>(h), "the second release gets nothing");
    check(!jni_handle_find(h, JniHandleKind::Object), "a released handle is not found");
    check(destroyed.load() == 0, "the state outlives the release while a lease exists");
    lease.reset();
    check(destroyed.load() == 1, "the state is destroyed once, with the last lease");
    check(jni_handle_count() == base, "the handle is no longer counted");
}

static void guard() {
    std::atomic<int> destroyed{0};
    jlong kept;
    {
        JniHandleGuard<TestState> g(std::make_shared<TestState>(&destroyed));
        kept = g.get();
        g.release();
    }
    check(destroyed.load() == 0 && jni_handle_find(kept, JniHandleKind::Object), "a released guard keeps the handle");
    jni_handle_consume<TestState>(kept);
    check(destroyed.load() == 1, "the state is destroyed");
    jlong dropped;
    {
        JniHandleGuard<TestState> g(std::make_shared<TestState>(&destroyed));
        dropped = g.get();
    }
    check(destroyed.load() == 2 && !jni_handle_find(dropped, JniHandleKind::Object), "a guard removes the handle");
}

static void unique_ids() {
    std::atomic<int> destroyed{0};
    std::set<jlong> seen;
    for (int i = 0; i < 20000; ++i) {
        jlong h = jni_handle_add(std::make_shared<TestState>(&destroyed));
        check(seen.insert(h).second, "a handle is not issued twice, also after a release");
        jni_handle_consume<TestState>(h);
    }
    check(destroyed.load() == 20000, "every state is destroyed");
}

static void concurrent_release() {
    constexpr int Threads = 8;
    constexpr int Rounds = 2000;
    for (int round = 0; round < Rounds; ++round) {
        std::atomic<int> destroyed{0};
        std::atomic<int> winners{0};
        std::mutex start_lock;
        std::condition_variable start_cond;
        bool start = false;
        jlong h = jni_handle_add(std::make_shared<TestState>(&destroyed));
        std::vector<std::thread> threads;
        threads.reserve(Threads);
        for (int t = 0; t < Threads; ++t) {
            threads.emplace_back([&, t]() {
                {
                    std::unique_lock<std::mutex> lock(start_lock);
                    start_cond.wait(lock, [&]() { return start; });
                }
                if (t % 2) {
                    // a use either gets a valid state or none
                    std::shared_ptr<void> lease = jni_handle_find(h, JniHandleKind::Object);
                    if (lease) {
                        check(static_cast<TestState*>(lease.get())->destroyed == &destroyed, "a lease is valid");
                    }
                } else if (jni_handle_consume<TestState>(h)) {
                    winners.fetch_add(1);
                }
            });
        }
        {
            std::lock_guard<std::mutex> lock(start_lock);
            start = true;
        }
        start_cond.notify_all();
        for (std::thread& thread : threads) {
            thread.join();
        }
        check(winners.load() == 1, "exactly one concurrent release gets the state");
        check(destroyed.load() == 1, "the state is destroyed exactly once");
    }
}

// a publication that does not reach its Java wrapper is rolled back: the handle is dead and its state is released,
// including when the failure is an exception, while a lease that was taken keeps the state valid
static void publication_rollback() {
    std::atomic<int> destroyed{0};
    size_t base = jni_handle_count();
    jlong unpublished = 0;
    bool caught = false;
    try {
        JniHandleGuard<TestState> pending(std::make_shared<TestState>(&destroyed));
        unpublished = pending.get();
        check((bool)jni_handle_find(unpublished, JniHandleKind::Object), "an unfinished publication is live");
        check(jni_handle_count() == base + 1, "an unfinished publication is counted");
        throw std::runtime_error("the Java wrapper could not be created");
    } catch (const std::runtime_error&) {
        caught = true;
    }
    check(caught, "the failure of the publication reached the caller");
    check(!jni_handle_find(unpublished, JniHandleKind::Object), "a rolled back handle is not found");
    check(!jni_handle_consume<TestState>(unpublished), "a rolled back handle cannot be released again");
    check(destroyed.load() == 1, "a rolled back publication releases its state once");
    check(jni_handle_count() == base, "a rolled back handle is no longer counted");

    std::shared_ptr<TestState> lease;
    jlong leased;
    {
        JniHandleGuard<TestState> pending(std::make_shared<TestState>(&destroyed));
        leased = pending.get();
        lease = jni_handle_get<TestState>(leased);
    }
    check(!jni_handle_find(leased, JniHandleKind::Object), "the rollback removes a handle that has a lease");
    check(destroyed.load() == 1, "a lease keeps the state of a rolled back publication");
    lease.reset();
    check(destroyed.load() == 2, "the last lease releases the state of a rolled back publication");

    // a publication that completed is not rolled back
    jlong published;
    {
        JniHandleGuard<TestState> done(std::make_shared<TestState>(&destroyed));
        published = done.get();
        done.release();
    }
    check((bool)jni_handle_find(published, JniHandleKind::Object), "a completed publication stays live");
    check((bool)jni_handle_consume<TestState>(published), "a completed publication is released by its owner");
    check(destroyed.load() == 3, "the owner's release destroys the state");
}

// the registry identifies state by handle and kind, never by what the state is: a class of the same name that was
// parsed separately is a different class, and only the class ID decides.  This is the check that a generated method
// call makes on its receiver
static void namesake_class() {
    ExceptionSink xsink;
    QoreProgramHelper first(PO_NEW_STYLE, xsink);
    first->parse("%new-style\nclass RefBase {int value() {return 1;}} class Derived inherits RefBase {} "
        "class Other {} Derived sub receiver() {return new Derived();}", "handle-registry-first", &xsink);
    check(!xsink, "the first fixture parses");
    QoreProgramHelper second(PO_NEW_STYLE, xsink);
    second->parse("%new-style\nclass RefBase {int value() {return 2;}}", "handle-registry-second", &xsink);
    check(!xsink, "the second fixture parses");

    const QoreClass* base = first->findClass("RefBase", &xsink);
    const QoreClass* other = first->findClass("Other", &xsink);
    const QoreClass* namesake = second->findClass("RefBase", &xsink);
    check(!xsink && base && other && namesake, "the fixture classes are found");
    check(base != namesake && base->getID() != namesake->getID(), "a separately parsed class has its own ID");

    ValueHolder value(first->callFunction("receiver", nullptr, &xsink), &xsink);
    check(!xsink && value->getType() == NT_OBJECT, "the receiver is created");
    QoreObject* obj = value->get<QoreObject>();

    check(obj->getClass()->getClass(base->getID()) != nullptr, "the receiver is an instance of its parent by ID");
    check(obj->getClass()->getClass(obj->getClass()->getID()) != nullptr, "the receiver is an instance of its class");
    check(!obj->getClass()->getClass(other->getID()), "the receiver is not an instance of an unrelated class");
    check(!obj->getClass()->getClass(namesake->getID()), "the receiver is not an instance of the namesake by ID");
    check(!base->getInjectedAsClass() && !namesake->getInjectedAsClass(), "no injection relates the two classes");
}

int main() {
    known_answer();
    lifecycle();
    guard();
    unique_ids();
    concurrent_release();
    publication_rollback();
    qore_init(QL_MIT, nullptr, false, QLO_DISABLE_SIGNAL_HANDLING);
    namesake_class();
    qore_cleanup();
    printf("jni_handle_registry: all tests passed\n");
    return 0;
}
