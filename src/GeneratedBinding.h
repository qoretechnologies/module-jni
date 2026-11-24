/* Copyright (C) 2026 Qore Technologies, s.r.o.
   SPDX-License-Identifier: LGPL-2.1-or-later */

#ifndef QORE_JNI_GENERATED_BINDING_H
#define QORE_JNI_GENERATED_BINDING_H

#include <qore/Qore.h>
#include <jni.h>
#include <memory>

namespace jni {

enum class GeneratedBindingKind { Class, Method, StaticMethod, Function, Constant };
struct GeneratedProgram;

// Only the opaque handle is serialized. All borrowed native metadata stays here.
struct GeneratedBinding {
    QoreProgram* const pgm;
    QoreProgram* const metadata_pgm;
    const GeneratedBindingKind kind;
    const QoreClass* cls = nullptr;
    const QoreMethod* method = nullptr;
    const QoreExternalVariant* variant = nullptr;
    const QoreExternalFunction* function = nullptr;
    const QoreExternalConstant* constant = nullptr;
    std::shared_ptr<GeneratedProgram> lifetime;
    std::shared_ptr<GeneratedProgram> metadata_lifetime;

    DLLLOCAL GeneratedBinding(QoreProgram* pgm, GeneratedBindingKind kind, QoreProgram* metadata_pgm = nullptr)
            : pgm(pgm), metadata_pgm(metadata_pgm ? metadata_pgm : pgm), kind(kind) {
        assert(pgm);
        pgm->depRef();
        if (this->metadata_pgm != pgm) {
            this->metadata_pgm->depRef();
        }
    }

    DLLLOCAL ~GeneratedBinding() {
        if (metadata_pgm != pgm) {
            metadata_pgm->depDeref();
        }
        pgm->depDeref();
    }

    GeneratedBinding(const GeneratedBinding&) = delete;
    GeneratedBinding& operator=(const GeneratedBinding&) = delete;
};

DLLLOCAL jlong register_generated_binding(std::shared_ptr<GeneratedBinding> binding);
DLLLOCAL std::shared_ptr<const GeneratedBinding> find_generated_binding(jlong handle, GeneratedBindingKind kind);
DLLLOCAL void purge_generated_bindings(QoreProgram* pgm);
DLLLOCAL void clear_generated_bindings();
//! Called in a child process created with fork() after the JVM was started, by the module's fork handler
/** The registry is then left as it is in the child: the leases of the bindings are held by Java threads of the
    parent, which do not exist in the child and never release them, a thread of the parent can have held the
    registry's locks when the process was created, and Java, which alone uses the bindings, cannot be used in the
    child.  Purging the bindings of a Program and clearing the registry do nothing in the child.  Takes no lock and
    allocates nothing.
*/
DLLLOCAL void generated_bindings_fork_child();

class GeneratedBindingLease {
public:
    DLLLOCAL GeneratedBindingLease(jlong handle, GeneratedBindingKind kind);
    DLLLOCAL ~GeneratedBindingLease();
    GeneratedBindingLease(const GeneratedBindingLease&) = delete;
    GeneratedBindingLease& operator=(const GeneratedBindingLease&) = delete;

    DLLLOCAL const GeneratedBinding* operator->() const {
        return binding.get();
    }

private:
    std::shared_ptr<const GeneratedBinding> binding;
};

// A weak reference from the registry keeps the Program allocation valid while the
// context helper atomically enters execution or rejects a Program being torn down.
// Never hold the registry mutex while acquiring a Program context or executing code.
// The entries do not set a pending Java exception aside when they are left: a JNI caller declares a
// JniJavaExceptionAside before the context and its guard after it (see JniProgramEntryHelper in Jvm.h).
class GeneratedBindingContext {
public:
    //! Holds the binding and enters its Programs
    /** @param handle the binding's handle
        @param kind the kind of binding expected
        @param xsink the sink of the entries
        @param check_program if not nullptr, called with the binding's Program before the Program is entered; it
        throws to refuse the call (jni_check_governed_caller_reaches(), qore#7023), so that a refused call runs no code
        of the Program
    */
    DLLLOCAL GeneratedBindingContext(jlong handle, GeneratedBindingKind kind, ExceptionSink* xsink,
        void (*check_program)(const QoreProgram*) = nullptr);

    DLLLOCAL const GeneratedBinding* operator->() const {
        return binding.operator->();
    }

private:
    GeneratedBindingLease binding;
    std::unique_ptr<QoreExternalProgramContextHelper> metadata_context;
    std::unique_ptr<QoreExternalProgramContextHelper> context;
};

}

#endif
