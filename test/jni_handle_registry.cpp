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

int main() {
    known_answer();
    lifecycle();
    guard();
    unique_ids();
    concurrent_release();
    printf("jni_handle_registry: all tests passed\n");
    return 0;
}
