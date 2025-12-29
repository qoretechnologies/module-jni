
/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    JniQoreClass.h

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

#ifndef _QORE_JNI_QORECLASS_H

#define _QORE_JNI_QORECLASS_H

#include <string>

namespace jni {

class QoreJniPrivateData;

class JniQoreClass : public QoreBuiltinClass {
public:
    DLLLOCAL JniQoreClass(QoreProgram* pgm, const char* name, const char* path, const char* jname)
            : QoreBuiltinClass(pgm, name, path, QDOM_UNCONTROLLED_API), jname(jname) {
        assert(pgm->getExternalData("jni"));
        addMethod(nullptr, "memberGate", (q_external_method_t)memberGate, Public, 0, QDOM_UNCONTROLLED_API,
            autoTypeInfo, paramTypeInfo);

        setPublicMemberFlag();
        setGateAccessFlag();
        setLanguage("Java");
        setRelaxedAbstractMatch();
    }

    DLLLOCAL JniQoreClass(const JniQoreClass& old) : QoreBuiltinClass(old), jname(old.jname) {
    }

    DLLLOCAL virtual ~JniQoreClass() {
    }

    DLLLOCAL virtual QoreClass* copyImport() {
        JniQoreClass* rv = new JniQoreClass;
        rv->jname = jname;
        rv->setPublicMemberFlag();
        rv->setGateAccessFlag();
        rv->setLanguage("Java");
        rv->setRelaxedAbstractMatch();
        return rv;
    }

    DLLLOCAL virtual QoreClass* copy() {
        return new JniQoreClass(*this);
    }

    DLLLOCAL const std::string& getJavaName() const {
        return jname;
    }

    static QoreValue memberGate(const QoreMethod& meth, void* m, QoreObject* self, QoreJniPrivateData* jd,
        const QoreListNode* args, q_rt_flags_t rtflags, ExceptionSink* xsink);

private:
    // java dot name for the class; ex: "java.lang.Object"
    std::string jname;

    static type_vec_t paramTypeInfo;

    DLLLOCAL JniQoreClass() {
    }
};
}

#endif
