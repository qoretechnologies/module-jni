/* Copyright (C) 2026 Qore Technologies, s.r.o.
   SPDX-License-Identifier: LGPL-2.1-or-later */

#ifndef QORE_JNI_HANDLE_REGISTRY_H
#define QORE_JNI_HANDLE_REGISTRY_H

#include <qore/Qore.h>
#include <jni.h>

#include <cstdint>
#include <functional>
#include <memory>
#include <stdexcept>
#include <utility>

namespace jni {

//! The kinds of native state that Java refers to by a handle
enum class JniHandleKind : unsigned char {
    Object,
    Closure,
    InvocationHandler,
    ExceptionWrapper,
    SandboxPolicy,
    ConnectionWaiter,
};

//! Raised when Java passes a value that is not a live handle of the expected kind
class JniInvalidHandle : public std::runtime_error {
public:
    DLLLOCAL JniInvalidHandle() : std::runtime_error("invalid or released Qore native handle") {
    }
};

//! Registers native state and returns its opaque handle
/** A handle is never a native address and is never issued twice in a process; handles cannot be predicted from each
    other.  Handle 0 is never issued: it is the value of a Java wrapper with no native state.

    @throw std::bad_alloc memory cannot be allocated
*/
DLLLOCAL jlong jni_handle_register(JniHandleKind kind, std::shared_ptr<void> state);

//! Returns the state of a live handle of the given kind, or an empty pointer
/** The pointer returned keeps the state valid after the handle is released, so that a use cannot race with a release:
    the state is destroyed when the last pointer to it is released, outside of the registry's locks.
*/
DLLLOCAL std::shared_ptr<void> jni_handle_find(jlong handle, JniHandleKind kind);

//! Removes a live handle of the given kind and returns its state, or an empty pointer
/** Only one caller gets the state of a handle; afterwards the handle is unknown.  A handle of another kind is not
    removed.
*/
DLLLOCAL std::shared_ptr<void> jni_handle_take(jlong handle, JniHandleKind kind);

//! Returns the number of live handles
DLLLOCAL size_t jni_handle_count();

#ifdef DEBUG
//! Runs the function with every lock of the registry held, for tests of what a child process created with fork() uses
DLLLOCAL void jni_handle_with_all_locks(const std::function<void()>& f);
#endif

//! The permutation that makes handles from a counter (Speck64/128); exposed for its known-answer test
/** @param value the value to encrypt
    @param key the key words: l2, l1, l0, k0
*/
DLLLOCAL uint64_t jni_handle_permute(uint64_t value, const uint32_t key[4]);

//! Maps a native state type to its kind; specialized next to each type
template <typename T>
struct JniHandleTraits;

//! Registers the state and returns its handle
template <typename T>
jlong jni_handle_add(std::shared_ptr<T> state) {
    return jni_handle_register(JniHandleTraits<T>::kind, std::move(state));
}

//! Returns the state of a live handle
/** @throw JniInvalidHandle the handle is not a live handle of this type
*/
template <typename T>
std::shared_ptr<T> jni_handle_get(jlong handle) {
    std::shared_ptr<void> state = jni_handle_find(handle, JniHandleTraits<T>::kind);
    if (!state) {
        throw JniInvalidHandle();
    }
    return std::static_pointer_cast<T>(state);
}

//! Removes a live handle and returns its state, or an empty pointer if the handle is not a live handle of this type
template <typename T>
std::shared_ptr<T> jni_handle_consume(jlong handle) {
    return std::static_pointer_cast<T>(jni_handle_take(handle, JniHandleTraits<T>::kind));
}

//! Removes a handle when it goes out of scope, unless ownership was passed to a Java wrapper with release()
template <typename T>
class JniHandleGuard {
public:
    DLLLOCAL explicit JniHandleGuard(std::shared_ptr<T> state) : handle(jni_handle_add<T>(std::move(state))) {
    }

    DLLLOCAL ~JniHandleGuard() {
        if (handle) {
            jni_handle_consume<T>(handle);
        }
    }

    //! Returns the handle
    DLLLOCAL jlong get() const {
        return handle;
    }

    //! The Java wrapper owns the handle now
    DLLLOCAL void release() {
        handle = 0;
    }

    JniHandleGuard(const JniHandleGuard&) = delete;
    JniHandleGuard& operator=(const JniHandleGuard&) = delete;

private:
    jlong handle;
};

}

#endif
