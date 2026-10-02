//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
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
/// \brief Defines the Array class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_ARRAY_H_
#define QORE_JNI_ARRAY_H_

#include <qore/Qore.h>
#include "LocalReference.h"
#include "QoreJniPrivateData.h"
#include "Globals.h"
#include "Env.h"

extern QoreClass* QC_JAVAARRAY;
extern qore_classid_t CID_JAVAARRAY;

namespace jni {

// forward reference
class JniExternalProgramData;

/**
 * \brief Represents a Java array instance.
 */
class Array : public QoreJniPrivateData {
    public:
    /**
     * \brief Constructor.
     * \param array a local reference to a Java array instance
     * \throws JavaException if a global reference cannot be created
     */
    DLLLOCAL Array(jarray array);

    /**
     * \brief Constructor
     * \param elementClass a borrowed reference to the component class
     * \param size the size of the array
     */
    DLLLOCAL Array(jclass elementClass, int size);

    DLLLOCAL ~Array() {
        printd(LogLevel, "Array::~Array(), this: %p, object: %p\n", this, jobj.cast<jarray>());
    }

    DLLLOCAL int64 length() const;
    DLLLOCAL QoreValue get(int64 index, QoreProgram* pgm, bool compat_types) const;
    DLLLOCAL void set(int64 index, const QoreValue &value, JniExternalProgramData* jpc = nullptr);
    DLLLOCAL QoreStringNodeHolder deepToString() const;

    DLLLOCAL static void getArgList(ReferenceHolder<QoreListNode>& return_value, Env& env, jarray array,
            QoreProgram* pgm, bool varargs = false);

    DLLLOCAL static void set(jarray array, Type elementType, jclass elementClass, int64 index,
            const QoreValue &value, JniExternalProgramData* jpc = nullptr);

    DLLLOCAL static QoreStringNodeHolder deepToString(Env& env, jarray array);

    DLLLOCAL static void getList(ReferenceHolder<>& return_value, Env& env, jarray array,
            jclass arrayClass, QoreProgram* pgm, bool compat_types = false, bool varargs = false);

    DLLLOCAL static QoreValue get(Env& env, jarray array, Type elementType, jclass elementClass, int64 index,
            QoreProgram* pgm, bool compat_types);

    DLLLOCAL static LocalReference<jarray> getNew(Type elementType, jclass elementClass, jsize size);

    DLLLOCAL static LocalReference<jarray> toJava(const QoreListNode* l, size_t start = 0,
            JniExternalProgramData* jpc = nullptr);
    DLLLOCAL static LocalReference<jarray> toObjectArray(const QoreListNode* l, jclass elementClass,
            size_t start = 0, JniExternalProgramData* jpc = nullptr);

    DLLLOCAL static LocalReference<jclass> getClassForValue(QoreValue v, JniExternalProgramData* jpc = nullptr);

    DLLLOCAL static SimpleRefHolder<BinaryNode> getBinary(Env& env, jarray array);

private:
    GlobalReference<jclass> elementClass;
    Type elementType;
};

} // namespace jni

#endif // QORE_JNI_ARRAY_H_
