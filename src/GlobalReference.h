//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.
//
//  Permission is hereby granted, free of charge, to any person obtaining a
//  copy of this software and associated documentation files (the "Software"),
//  to deal in the Software without restriction, including without limitation
//  the rights to use, copy, modify, merge, publish, distribute, sublicense,
//  and/or sell copies of the Software, and to permit persons to whom the
//  Software is furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
//  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
//  DEALINGS IN THE SOFTWARE.
//
//------------------------------------------------------------------------------
///
/// \file
/// \brief Defines the GlobalReference helper class template
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_GLOBALREFERENCE_H_
#define QORE_JNI_GLOBALREFERENCE_H_

#include <qore/Qore.h>
#include "Jvm.h"
#include "defs.h"

namespace jni {

template<typename T>
class LocalReference;

/**
 * \brief A RAII wrapper for JNI's global references.
 *
 * Destructor attempts to attach the current thread to the JVM - if it is not possible, the reference leaks.
 * \tparam T the type of the reference (jobject, jclass etc.)
 */
template<typename T>
class GlobalReference {
public:
    /**
     * \brief Creates an instance.
     * \param ref the global reference
     */
    GlobalReference(T ref = nullptr) : ref(ref) {
        assert(ref == nullptr || Jvm::getEnv()->GetObjectRefType(ref) == JNIGlobalRefType);
        if (ref != nullptr) {
            printd(LogLevel + 1, "GlobalReference created: %p\n", ref);
        }
    }

    /**
     * \brief Destroys the global reference represented by this instance.
     */
    ~GlobalReference() {
        del();
    }

    /**
     * \brief Move constructor.
     * \param src the source global reference wrapper
     */
    GlobalReference(GlobalReference &&src) : ref(src.ref) {
        src.ref = nullptr;
    }

    /**
     * \brief Move assignment.
     * \param src the source global reference wrapper
     * \return *this
     */
    GlobalReference &operator=(GlobalReference &&src) {
        del();
        ref = src.ref;
        src.ref = nullptr;
        return *this;
    }

    /**
     * \brief Implicit conversion to the reference type.
     */
    operator T() const {
        return ref;
    }

    template<typename T2>
    T2 cast() const {
        return static_cast<T2>(ref);
    }

    /**
     * \brief Creates a global reference from a local reference.
     * \param ref the local reference
     * \return global reference
     */
    static GlobalReference<T> fromLocal(T ref) {
        assert(ref != nullptr);
        T global = static_cast<T>(Jvm::getEnv()->NewGlobalRef(ref));
        if (global == nullptr) {
            throw JavaException();
        }
        return global;
    }

    /**
     * \brief Returns a local reference from the global reference
     * \return the local reference
     */
    DLLLOCAL T toLocal() const;

    /**
     * \brief Gives up the reference without deleting it, for a reference that another owner deletes
     * \return the reference
     */
    T release() {
        T rv = ref;
        ref = nullptr;
        return rv;
    }

private:
    GlobalReference(const GlobalReference &) = delete;
    GlobalReference &operator=(const GlobalReference &) = delete;

    void del() {
        if (ref != nullptr) {
            try {
                printd(LogLevel + 1, "GlobalReference deleted: %p\n", ref);
                Jvm::attachAndGetEnv()->DeleteGlobalRef(ref);
            } catch (Exception& e) {
                printd(LogLevel, "Unable to delete GlobalReference");
            }
        }
    }

private:
    T ref;
};

} // namespace jni

#endif // QORE_JNI_GLOBALREFERENCE_H_
