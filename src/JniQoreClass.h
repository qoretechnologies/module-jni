
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
        // the Program's jni data is not checked here: the class is created under the class-map lock, a leaf, and
        // QoreProgram::getExternalData() takes the Program's lock
        assert(pgm);
#ifdef QORE_HAVE_UNCONTROLLED_API_ENFORCEMENT
        // the restriction classes that the Java code enforces itself: none (qore#7832); set before the class is
        // published (added to a namespace) or any object of it is created, and shared by the copies of the class.
        // The call fails only for a class in a namespace or for bits outside the classes, neither of which a new
        // class with 0 can have; a failure would leave the value of a class that a Program owns, which the Qore
        // library never stamps with a module default, at 0, the most restrictive value (fail closed).  The
        // constructor does not throw: the base class's private data and its reference to the Program are released
        // only by derefClass(), not by the destructor that a throw would run
        {
            int rc = setEnforcedDomains(QORE_JNI_ENFORCED_DOMAINS);
            assert(!rc);
            (void)rc;
            assert(getEnforcedDomains() == QORE_JNI_ENFORCED_DOMAINS);
        }
#endif
        addMethod(nullptr, "memberGate", (q_external_method_t)memberGate, Public, 0, QDOM_UNCONTROLLED_API,
            autoTypeInfo, paramTypeInfo);

        setPublicMemberFlag();
        setGateAccessFlag();
        setLanguage("Java");
        setRelaxedAbstractMatch();
    }

    DLLLOCAL JniQoreClass(const JniQoreClass& old) : QoreBuiltinClass(old), jname(old.jname),
            bin_name(old.bin_name) {
    }

    DLLLOCAL virtual ~JniQoreClass() {
    }

    DLLLOCAL virtual QoreClass* copyImport() {
        JniQoreClass* rv = new JniQoreClass;
        rv->jname = jname;
        rv->bin_name = bin_name;
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

    //! Returns the binary name of the Java class (ex: "java.util.Map$Entry"), or an empty string if not known
    /** Unlike getJavaName(), the name of a nested class has its '$' separator
    */
    DLLLOCAL const std::string& getJavaBinaryName() const {
        return bin_name;
    }

    //! Sets the binary name of the Java class from its internal name (ex: "java/util/Map$Entry")
    DLLLOCAL void setJavaInternalName(const char* jpath) {
        bin_name = jpath;
        for (char& c : bin_name) {
            if (c == '/') {
                c = '.';
            }
        }
    }

    static QoreValue memberGate(const QoreMethod& meth, void* m, QoreObject* self, QoreJniPrivateData* jd,
        const QoreListNode* args, q_rt_flags_t rtflags, ExceptionSink* xsink);

private:
    // java dot name for the class; ex: "java.lang.Object"; a nested class has "__" instead of '$'
    std::string jname;
    // the binary name of the Java class; ex: "java.util.Map$Entry"
    std::string bin_name;

    static type_vec_t paramTypeInfo;

    DLLLOCAL JniQoreClass() {
    }
};
}

#endif
