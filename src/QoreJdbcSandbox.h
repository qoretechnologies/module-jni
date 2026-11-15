/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcSandbox.h

    Qore Programming Language JNI Module

    Copyright (C) 2026 Qore Technologies, s.r.o.

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

//! \file
//! \brief The per-driver sandbox enforcement of the jdbc DBI driver (qore#5786)

#ifndef _QORE_JNI_QOREJDBCSANDBOX_H
#define _QORE_JNI_QOREJDBCSANDBOX_H

#include <qore/Qore.h>
#include <qore/QoreSandboxManager.h>

#include "defs.h"
#include "GlobalReference.h"

#include <memory>
#include <string>
#include <vector>

// The per-driver enforcement is built with the prerequisites of its trusted class loader rule (see
// jdbc_trusted_loader()): module commands judged by their issuer (module API 2.3), datasource options judged by the
// code that set them (qore#5741), and the run-time refusal of uncontrolled APIs to sandboxed code (qore#5741).
// Without them, the jdbc driver keeps refusing every connection for restricted code.
#if defined(QORE_JNI_HAS_COMMAND_CONTEXT) && defined(_QORE_HAS_DATASOURCE_OPTION_RESTRICTIONS) \
    && defined(_QORE_HAS_SANDBOX_UNCONTROLLED_API_POLICY)
#define QORE_JNI_JDBC_SANDBOX_DRIVERS 1
#endif

namespace jni {

class Env;

//! Where the policy of a jdbc connection comes from, and whether it restricts the connection (qore#5786)
/** Made only by jdbc_connect_decision(); every caller is agnostic of where the decision comes from.
*/
struct JdbcConnectDecision {
    //! true if the connection is restricted: it is made only with an audited driver, through the guard
    bool restricted = false;

    //! the policy that the destinations must pass, for the module-side check; null in Qore-5747 mode
    std::unique_ptr<QoreSandboxManagerHelper> policy;

    //! the destinations that Qore checked (Qore-5747 mode: QDBI_METHOD_GET_CONNECT_TARGETS), or nullptr
    const QoreListNode* qore_checked = nullptr;

    //! Returns the policies that the module-side check applies
    DLLLOCAL std::vector<QoreSandboxManager*> managers() const;
};

//! Decides whether a jdbc connection is restricted, and by which policy: the only place that knows the policy source
/** PROVISIONAL (design section 6 / 11, item 1): the policy source is the thread: a connection is restricted if a
    sandbox that does not allow uncontrolled APIs governs the code that opens it
    (jni_sandbox_restricts_caller(QoreSandboxManagerHelper::Policy)), and its destinations are checked against that
    thread's sandbox network policy.  So a trusted pool that grows on a sandboxed thread connects only to destinations
    that the thread's policy allows, which is not the qore#6142 rule (the code that chose the datasource decides).
    The chooser record of qore#6142 Option A, or the Qore side of qore#5747, replaces this function only.

    With a %Qore library that has \c QDBI_METHOD_GET_CONNECT_TARGETS (the planned Qore side of qore#5747), Qore
    decides: a connection is restricted if Qore checked its destinations (\c Datasource::getCheckedConnectTargets()),
    and the checked destinations are used.

    @param ds the datasource
    @param d receives the decision
    @param xsink receives the exception of a refusal

    @return 0 for success, -1 if an exception was raised
*/
DLLLOCAL int jdbc_connect_decision(Datasource* ds, JdbcConnectDecision& d, ExceptionSink* xsink);

//! Holds the JdbcSandboxConnect object of a restricted connection; closes its guard when it is released
/** The guard is closed (its sockets refuse to connect, and its token is removed from the registry) when the holder
    is destroyed or reset, on every path: a failed connection attempt as well as a closed connection.
*/
class JdbcGuardHolder {
public:
    DLLLOCAL JdbcGuardHolder() = default;

    DLLLOCAL ~JdbcGuardHolder() {
        close();
    }

    DLLLOCAL JdbcGuardHolder(JdbcGuardHolder&& old) : prep(std::move(old.prep)) {
    }

    DLLLOCAL JdbcGuardHolder& operator=(JdbcGuardHolder&& old) {
        if (this != &old) {
            close();
            prep = std::move(old.prep);
        }
        return *this;
    }

    //! Returns true if the holder has a restricted connection
    DLLLOCAL explicit operator bool() const {
        return static_cast<jobject>(prep) != nullptr;
    }

    //! Returns the JdbcSandboxConnect object
    DLLLOCAL jobject get() const {
        return prep;
    }

    //! Takes the JdbcSandboxConnect object
    DLLLOCAL void reset(GlobalReference<jobject>&& p) {
        close();
        prep = std::move(p);
    }

    //! Closes the guard and releases the object; never throws
    DLLLOCAL void close();

private:
    GlobalReference<jobject> prep;

    JdbcGuardHolder(const JdbcGuardHolder&) = delete;
    JdbcGuardHolder& operator=(const JdbcGuardHolder&) = delete;
};

//! Returns true if an audited driver profile accepts the URL (Amazon Redshift, SingleStore)
/** @throw jni::Exception a Java exception was raised
*/
DLLLOCAL bool jdbc_sandbox_supported_url(Env& env, const char* url);

//! Returns the scheme of a JDBC URL for an error message, without its hosts or options
DLLLOCAL std::string jdbc_url_scheme(const char* url);

//! Prepares a restricted connection: checks the URL and options, the driver, and the destinations, and pins them
/** @param env the JNI environment
    @param d the decision of jdbc_connect_decision(); restricted
    @param url the JDBC URL
    @param user the user name, or empty
    @param pass the password, or empty
    @param holder receives the JdbcSandboxConnect object, ready to connect
    @param xsink receives \c NETWORK-ACCESS-DENIED for a refused connection, \c JNI-SANDBOX-ERROR for a failed
    integrity check

    @return 0 for success, -1 if an exception was raised
*/
DLLLOCAL int jdbc_prepare_guarded(Env& env, const JdbcConnectDecision& d, const char* url, const std::string& user,
    const std::string& pass, JdbcGuardHolder& holder, ExceptionSink* xsink);

//! Opens a prepared restricted connection through its guard and returns it
/** @throw jni::Exception the connection failed or was refused
*/
DLLLOCAL GlobalReference<jobject> jdbc_guarded_connect(Env& env, const JdbcGuardHolder& holder);

//! Checks SQL that a restricted connection is given (SingleStore: LOAD DATA LOCAL INFILE)
/** @throw jni::Exception (JNI-SANDBOX-ERROR) the SQL is not allowed
*/
DLLLOCAL void jdbc_guarded_check_sql(Env& env, const JdbcGuardHolder& holder, const char* sql);

//! Defines the Java classes of the per-driver enforcement and registers their natives (see Globals::init())
/** @throw jni::Exception the classes cannot be defined
*/
DLLLOCAL void jdbc_sandbox_init(Env& env);

//! Releases the global references of jdbc_sandbox_init()
DLLLOCAL void jdbc_sandbox_cleanup();

#ifdef QDBI_METHOD_GET_CONNECT_TARGETS
//! QDBI_METHOD_GET_CONNECT_TARGETS: the destinations of a jdbc connection for Qore's sandbox check (qore#5747)
/** Raises \c NETWORK-ACCESS-DENIED for a URL of a driver without a profile, or with an option that a restricted
    connection may not use.  Whether Qore calls this for restricted connections only is left to the qore#5747 design.
*/
DLLLOCAL QoreListNode* jdbc_get_connect_targets(Datasource* ds, ExceptionSink* xsink);
#endif

}

#endif
