/* Copyright (C) 2026 Qore Technologies, s.r.o.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
#include "GeneratedBinding.h"
#include "defs.h"

#include <future>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <vector>

using namespace jni;

// the state of the JVM that the entries of the binding contexts read (see JniProgramEntryHelper in Jvm.h): no JVM is
// created here, so no thread is attached, and no Java exception is set aside when an entry is left
thread_local JNIEnv* Jvm::env = nullptr;
bool Jvm::forked_child = false;
std::atomic<bool> Jvm::exiting{false};

static void check(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

template <typename Call>
static void rejected(Call&& call) {
    try {
        call();
    } catch (const std::runtime_error& e) {
        check(std::string(e.what()).find("no longer available") != std::string::npos,
            "missing expired binding diagnostic");
        return;
    }
    throw std::runtime_error("invalid binding was accepted");
}

static void run() {
    ExceptionSink xsink;
    QoreProgramHelper pgm(PO_NEW_STYLE, xsink);
    pgm->parse("%modern\nconst Value = 42;", "binding-registry", &xsink);
    check(!xsink, "fixture parse failed");
    const QoreNamespace* ns = nullptr;
    const QoreExternalConstant* constant = pgm->findNamespaceConstant("Value", ns);
    check(constant, "fixture constant missing");
    auto make = [&]() {
        auto binding = std::make_shared<GeneratedBinding>(*pgm, GeneratedBindingKind::Constant);
        binding->constant = constant;
        return register_generated_binding(std::move(binding));
    };
    jlong handle = make();
    check(handle < 0, "handle overlaps legacy pointer space");
    check(handle == make(), "repeated generation changed the binding handle");
    {
        GeneratedBindingContext context(handle, GeneratedBindingKind::Constant, &xsink);
        check(context->constant->getReferencedValue().getAsBigInt() == 42, "live binding resolved wrong metadata");
        // Reentrant dispatch must support multiple leases on one thread.
        GeneratedBindingContext nested(handle, GeneratedBindingKind::Constant, &xsink);
        check(nested->pgm == *pgm, "nested context changed the owning Program");
    }
    rejected([&]() { GeneratedBindingLease wrong(handle, GeneratedBindingKind::Function); });
    for (jlong invalid : {jlong(0), jlong(1), jlong(0x12345678abcdef), handle + 1}) {
        rejected([&]() { GeneratedBindingLease missing(invalid, GeneratedBindingKind::Constant); });
    }

    // Concurrent generation must return one identity, and foreign-thread contexts
    // must resolve and release their leases independently without serializing calls.
    std::vector<std::future<void>> workers;
    for (int i = 0; i < 4; ++i) {
        workers.push_back(std::async(std::launch::async, [&]() {
            QoreForeignThreadHelper thread;
            for (int j = 0; j < 100; ++j) {
                check(make() == handle, "concurrent generation created another identity");
                ExceptionSink sink;
                GeneratedBindingContext context(handle, GeneratedBindingKind::Constant, &sink);
                check(!sink && context->constant == constant, "foreign-thread binding lookup failed");
            }
        }));
    }
    for (auto& worker : workers) {
        worker.get();
    }

    jlong imported_handle;
    {
        QoreProgramHelper owner(PO_NEW_STYLE, xsink);
        owner->parse("%modern\nconst Imported = 73;", "binding-owner", &xsink);
        check(!xsink, "metadata owner parse failed");
        auto imported = std::make_shared<GeneratedBinding>(*pgm, GeneratedBindingKind::Constant, *owner);
        imported->constant = owner->findNamespaceConstant("Imported", ns);
        check(imported->constant, "imported constant missing");
        imported_handle = register_generated_binding(std::move(imported));
        GeneratedBindingContext context(imported_handle, GeneratedBindingKind::Constant, &xsink);
        check(context->constant->getReferencedValue().getAsBigInt() == 73,
            "cross-program metadata resolved incorrectly");
    }
    rejected([&]() { GeneratedBindingLease expired(imported_handle, GeneratedBindingKind::Constant); });
    check(make() == handle, "metadata owner cleanup invalidated unrelated bindings");
    {
        QoreProgramHelper caller(PO_NEW_STYLE, xsink);
        auto imported = std::make_shared<GeneratedBinding>(*caller, GeneratedBindingKind::Constant, *pgm);
        imported->constant = constant;
        imported_handle = register_generated_binding(std::move(imported));
        GeneratedBindingContext context(imported_handle, GeneratedBindingKind::Constant, &xsink);
        check(context->constant == constant, "cross-program caller resolved incorrectly");
    }
    rejected([&]() { GeneratedBindingLease expired(imported_handle, GeneratedBindingKind::Constant); });
    check(make() == handle, "caller cleanup invalidated unrelated metadata bindings");

    // Keep a registry snapshot across invalidation, modeling a lookup that races
    // cleanup before it acquires its lease. Its weak Program reference must not
    // keep execution alive, and all subsequent context acquisitions must fail.
    auto snapshot = find_generated_binding(handle, GeneratedBindingKind::Constant);
    purge_generated_bindings(*pgm);
    rejected([&]() { GeneratedBindingContext expired(handle, GeneratedBindingKind::Constant, &xsink); });
    jlong replacement = make();
    check(replacement != handle, "expired handle was reused");
    check(replacement == make(), "replacement handle was not deduplicated");
    purge_generated_bindings(*pgm);
    snapshot.reset();
    clear_generated_bindings();
    rejected(make);
    check(!xsink, "unexpected Qore exception");
}

int main() {
    qore_init(QL_MIT, nullptr, false, QLO_DISABLE_SIGNAL_HANDLING);
    qore_register_program_cleanup_callback(purge_generated_bindings);
    int result = 0;
    try {
        run();
        std::cout << "Generated binding registry tests passed\n";
    } catch (jni::Exception& e) {
        ExceptionSink sink;
        e.convert(&sink);
        sink.handleExceptions();
        result = 1;
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        result = 1;
    }
    clear_generated_bindings();
    qore_deregister_program_cleanup_callback(purge_generated_bindings);
    qore_cleanup();
    return result;
}
