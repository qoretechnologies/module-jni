/* Copyright (C) 2026 Qore Technologies, s.r.o.
   SPDX-License-Identifier: LGPL-2.1-or-later */

#include "GeneratedBinding.h"
#include "defs.h"

#include <limits>
#include <array>
#include <condition_variable>
#include <map>
#include <mutex>
#include <random>
#include <set>
#include <stdexcept>

namespace jni {
struct GeneratedProgram {
    std::mutex mutex;
    std::condition_variable idle;
    size_t active = 0;
    bool live = true;
    // Membership is protected by binding_mutex, not the execution-state mutex.
    std::set<jlong> handles;
};

namespace {
std::mutex binding_mutex;
std::map<jlong, std::shared_ptr<GeneratedBinding>> bindings;
using BindingKey = std::array<uintptr_t, 8>;
std::map<BindingKey, jlong> binding_ids;
std::map<QoreProgram*, std::shared_ptr<GeneratedProgram>> program_lifetimes;
bool bindings_shutdown = false;
// set in a child process created with fork() after the JVM was started; see generated_bindings_fork_child()
bool bindings_forked_child = false;

constexpr const char* expired_binding = "The Qore Program that generated this class is no longer available, "
    "or the generated binding belongs to another process or an older JNI version; regenerate the class";

BindingKey binding_key(const GeneratedBinding& binding) {
    return {{reinterpret_cast<uintptr_t>(binding.pgm), static_cast<uintptr_t>(binding.kind),
        reinterpret_cast<uintptr_t>(binding.cls), reinterpret_cast<uintptr_t>(binding.method),
        reinterpret_cast<uintptr_t>(binding.variant), reinterpret_cast<uintptr_t>(binding.function),
        reinterpret_cast<uintptr_t>(binding.constant), reinterpret_cast<uintptr_t>(binding.metadata_pgm)}};
}

std::shared_ptr<GeneratedProgram> program_lifetime(QoreProgram* pgm) {
    auto i = program_lifetimes.find(pgm);
    if (i == program_lifetimes.end()) {
        i = program_lifetimes.emplace(pgm, std::make_shared<GeneratedProgram>()).first;
    }
    std::lock_guard<std::mutex> lock(i->second->mutex);
    if (!i->second->live) {
        throw std::runtime_error(expired_binding);
    }
    return i->second;
}

void acquire_lease(const std::shared_ptr<GeneratedProgram>& lifetime) {
    std::lock_guard<std::mutex> lock(lifetime->mutex);
    if (!lifetime->live) {
        throw std::runtime_error(expired_binding);
    }
    ++lifetime->active;
}

void release_lease(const std::shared_ptr<GeneratedProgram>& lifetime) {
    std::lock_guard<std::mutex> lock(lifetime->mutex);
    assert(lifetime->active);
    if (!--lifetime->active) {
        lifetime->idle.notify_all();
    }
}

// Negative handles cannot be confused with legacy embedded user-space pointers.
// A process-random origin prevents jars from another process resolving simply
// because Qore Program IDs and binding counters start over in that process.
uint64_t next_binding_id() {
    static const uint64_t origin = []() {
        std::random_device random;
        return (static_cast<uint64_t>(random()) << 32) ^ random();
    }();
    static uint64_t sequence = 0;
    if (sequence == static_cast<uint64_t>(std::numeric_limits<jlong>::max())) {
        throw std::overflow_error("Generated Qore binding handle space exhausted");
    }
    return (origin + ++sequence) & static_cast<uint64_t>(std::numeric_limits<jlong>::max());
}
}

jlong register_generated_binding(std::shared_ptr<GeneratedBinding> binding) {
    std::lock_guard<std::mutex> lock(binding_mutex);
    if (bindings_shutdown) {
        throw std::runtime_error(expired_binding);
    }
    binding->lifetime = program_lifetime(binding->pgm);
    binding->metadata_lifetime = program_lifetime(binding->metadata_pgm);
    BindingKey key = binding_key(*binding);
    auto existing = binding_ids.find(key);
    if (existing != binding_ids.end()) {
        return existing->second;
    }
    jlong handle = -1 - static_cast<jlong>(next_binding_id());
    auto entry = bindings.emplace(handle, std::move(binding));
    assert(entry.second);
    try {
        binding_ids.emplace(key, handle);
        try {
            entry.first->second->lifetime->handles.insert(handle);
            try {
                entry.first->second->metadata_lifetime->handles.insert(handle);
            } catch (...) {
                entry.first->second->lifetime->handles.erase(handle);
                throw;
            }
        } catch (...) {
            binding_ids.erase(key);
            throw;
        }
    } catch (...) {
        bindings.erase(entry.first);
        throw;
    }
    return handle;
}

std::shared_ptr<const GeneratedBinding> find_generated_binding(jlong handle, GeneratedBindingKind kind) {
    std::lock_guard<std::mutex> lock(binding_mutex);
    auto i = bindings.find(handle);
    if (i == bindings.end() || i->second->kind != kind) {
        throw std::runtime_error(expired_binding);
    }
    return i->second;
}

void purge_generated_bindings(QoreProgram* pgm) {
    if (bindings_forked_child) {
        // see generated_bindings_fork_child()
        return;
    }
    std::shared_ptr<GeneratedProgram> lifetime;
    {
        std::lock_guard<std::mutex> lock(binding_mutex);
        auto i = program_lifetimes.find(pgm);
        if (i == program_lifetimes.end()) {
            return;
        }
        lifetime = i->second;
        std::lock_guard<std::mutex> state_lock(lifetime->mutex);
        lifetime->live = false;
    }
    // Qore invokes cleanup after its initial thread drain, but before marking
    // the Program deleted. A call can enter in that interval. Close admission
    // and drain our own leases before allowing namespace destruction to proceed.
    {
        std::unique_lock<std::mutex> lock(lifetime->mutex);
        lifetime->idle.wait(lock, [&]() { return !lifetime->active; });
    }
    std::lock_guard<std::mutex> lock(binding_mutex);
    while (!lifetime->handles.empty()) {
        jlong handle = *lifetime->handles.begin();
        auto i = bindings.find(handle);
        assert(i != bindings.end());
        const GeneratedBinding& binding = *i->second;
        binding_ids.erase(binding_key(binding));
        binding.lifetime->handles.erase(handle);
        binding.metadata_lifetime->handles.erase(handle);
        bindings.erase(i);
    }
    program_lifetimes.erase(pgm);
}

void generated_bindings_fork_child() {
    bindings_forked_child = true;
}

void clear_generated_bindings() {
    if (bindings_forked_child) {
        // see generated_bindings_fork_child()
        return;
    }
    // Close generation before draining, so a concurrent JVM callback cannot
    // repopulate an already-cleared program while the module is shutting down.
    {
        std::lock_guard<std::mutex> lock(binding_mutex);
        bindings_shutdown = true;
    }
    while (true) {
        QoreProgram* pgm;
        {
            std::lock_guard<std::mutex> lock(binding_mutex);
            if (program_lifetimes.empty()) {
                return;
            }
            pgm = program_lifetimes.begin()->first;
        }
        purge_generated_bindings(pgm);
    }
}

GeneratedBindingLease::GeneratedBindingLease(jlong handle, GeneratedBindingKind kind)
        : binding(find_generated_binding(handle, kind)) {
    acquire_lease(binding->lifetime);
    if (binding->metadata_lifetime != binding->lifetime) {
        try {
            acquire_lease(binding->metadata_lifetime);
        } catch (...) {
            release_lease(binding->lifetime);
            throw;
        }
    }
}

GeneratedBindingLease::~GeneratedBindingLease() {
    if (binding->metadata_lifetime != binding->lifetime) {
        release_lease(binding->metadata_lifetime);
    }
    release_lease(binding->lifetime);
}

GeneratedBindingContext::GeneratedBindingContext(jlong handle, GeneratedBindingKind kind, ExceptionSink* xsink,
        void (*check_program)(const QoreProgram*)) : binding(handle, kind) {
    if (check_program) {
        // before any entry, so that a refused call runs no code of the Program (an entry handler, thread
        // initialization code)
        check_program(binding->pgm);
    }
    if (binding->metadata_pgm != binding->pgm) {
        metadata_context.reset(new QoreExternalProgramContextHelper(xsink, binding->metadata_pgm));
        if (*xsink) {
            throw XsinkException(*xsink);
        }
    }
    context.reset(new QoreExternalProgramContextHelper(xsink, binding->pgm));
    if (*xsink) {
        throw XsinkException(*xsink);
    }
}

}
