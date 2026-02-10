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
/// \brief Defines the QoreJniFunctionalInterface class
//------------------------------------------------------------------------------

#include <qore/Qore.h>
#include "jni.h"
#include "GlobalReference.h"
#include "Method.h"

namespace jni {
class QoreJniFunctionalInterface : public ResolvedCallReferenceNode {
public:
    DLLLOCAL QoreJniFunctionalInterface(jobject obj);

    DLLLOCAL virtual QoreValue execValue(const QoreListNode* args, ExceptionSink* xsink) const override;

    DLLLOCAL virtual QoreFunction* getFunction() const override {
        return nullptr;
    }

    DLLLOCAL virtual int getAsString(QoreString& str, int foff, ExceptionSink* xsink) const override;
    DLLLOCAL virtual QoreString* getAsString(bool& del, int foff, ExceptionSink* xsink) const override;

    DLLLOCAL virtual const char* getTypeName() const override {
        return "java closure wrapper";
    }

private:
    GlobalReference<jobject> obj;
    SimpleRefHolder<Class> cls;
    SimpleRefHolder<BaseMethod> method;
    QoreProgram* src_pgm;
};
}
