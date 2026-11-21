/* Copyright (C) 2026 Qore Technologies, s.r.o.
   SPDX-License-Identifier: LGPL-2.1-or-later */

#include "JniHandleRegistry.h"

#include <array>
#include <atomic>
#include <mutex>
#include <random>
#include <unordered_map>

namespace jni {
namespace {

constexpr unsigned SpeckRounds = 27;
constexpr size_t ShardCount = 64;

inline uint32_t ror32(uint32_t v, unsigned n) {
    return (v >> n) | (v << (32 - n));
}

inline uint32_t rol32(uint32_t v, unsigned n) {
    return (v << n) | (v >> (32 - n));
}

using RoundKeys = std::array<uint32_t, SpeckRounds>;

RoundKeys expand_key(const uint32_t key[4]) {
    RoundKeys k;
    uint32_t l[3] = {key[2], key[1], key[0]};
    k[0] = key[3];
    for (unsigned i = 0; i < SpeckRounds - 1; ++i) {
        uint32_t next = (k[i] + ror32(l[i % 3], 8)) ^ i;
        l[i % 3] = next;
        k[i + 1] = rol32(k[i], 3) ^ next;
    }
    return k;
}

uint64_t encrypt(uint64_t value, const RoundKeys& k) {
    uint32_t x = static_cast<uint32_t>(value >> 32);
    uint32_t y = static_cast<uint32_t>(value);
    for (uint32_t round_key : k) {
        x = (ror32(x, 8) + y) ^ round_key;
        y = rol32(y, 3) ^ x;
    }
    return (static_cast<uint64_t>(x) << 32) | y;
}

struct Entry {
    JniHandleKind kind;
    std::shared_ptr<void> state;
};

struct alignas(64) Shard {
    std::mutex lock;
    std::unordered_map<jlong, Entry> entries;
};

struct Registry {
    std::array<Shard, ShardCount> shards;
    std::atomic<uint64_t> sequence{0};
    RoundKeys keys;

    Registry() {
        std::random_device random;
        uint32_t key[4];
        for (uint32_t& word : key) {
            word = random();
        }
        keys = expand_key(key);
    }

    Shard& shard(jlong handle) {
        return shards[static_cast<uint64_t>(handle) % ShardCount];
    }

    jlong next() {
        // a permutation of a counter: a handle is never issued twice, and a handle says nothing about another
        while (true) {
            uint64_t id = encrypt(sequence.fetch_add(1, std::memory_order_relaxed), keys);
            if (id) {
                return static_cast<jlong>(id);
            }
        }
    }
};

// never destroyed: a Java thread can use a handle while the process exits, and the state of a call in progress at the
// module's shutdown is deliberately left to the process's exit
Registry& registry() {
    static Registry* r = new Registry;
    return *r;
}

}

uint64_t jni_handle_permute(uint64_t value, const uint32_t key[4]) {
    return encrypt(value, expand_key(key));
}

jlong jni_handle_register(JniHandleKind kind, std::shared_ptr<void> state) {
    Registry& r = registry();
    jlong handle = r.next();
    Shard& shard = r.shard(handle);
    // copied: if the insertion fails, the state is released by the caller's reference, after the lock
    std::lock_guard<std::mutex> lock(shard.lock);
    shard.entries.emplace(handle, Entry{kind, state});
    return handle;
}

std::shared_ptr<void> jni_handle_find(jlong handle, JniHandleKind kind) {
    if (!handle) {
        return nullptr;
    }
    Shard& shard = registry().shard(handle);
    std::lock_guard<std::mutex> lock(shard.lock);
    auto i = shard.entries.find(handle);
    if (i == shard.entries.end() || i->second.kind != kind) {
        return nullptr;
    }
    return i->second.state;
}

std::shared_ptr<void> jni_handle_take(jlong handle, JniHandleKind kind) {
    if (!handle) {
        return nullptr;
    }
    Shard& shard = registry().shard(handle);
    // the state is returned, so that it is never destroyed with the lock held: its destruction can run Qore code
    std::lock_guard<std::mutex> lock(shard.lock);
    auto i = shard.entries.find(handle);
    if (i == shard.entries.end() || i->second.kind != kind) {
        return nullptr;
    }
    std::shared_ptr<void> state = std::move(i->second.state);
    shard.entries.erase(i);
    return state;
}

size_t jni_handle_count() {
    Registry& r = registry();
    size_t count = 0;
    for (Shard& shard : r.shards) {
        std::lock_guard<std::mutex> lock(shard.lock);
        count += shard.entries.size();
    }
    return count;
}

}
