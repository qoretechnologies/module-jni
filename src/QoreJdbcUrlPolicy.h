/* -*- mode: c++; indent-tabs-mode: nil -*- */
/*
    QoreJdbcUrlPolicy.h

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

#ifndef _QORE_JNI_QOREJDBCURLPOLICY_H

#define _QORE_JNI_QOREJDBCURLPOLICY_H

#include <qore/Qore.h>

#include <string>
#include <utility>
#include <vector>

namespace jni {

//! The parse options that restrict the code that chose a jdbc connection to the allow-listed drivers (qore#7867)
/** A JDBC driver is Java code that can reach the filesystem, the network and other processes, and run Java that the
    connection or its SQL defines (ex: H2's \c CREATE \c ALIAS), outside the restrictions of the code that chose the
    connection; for code with any of these options, only the drivers and URLs that qore_jdbc_check_restricted_url()
    allows can be used.  \c PO_NO_DATABASE is not one of them: the database domain is separate, and code without it
    cannot use a Datasource at all
*/
constexpr int64 JDBC_RESTRICTING_OPTIONS = PO_NO_FILESYSTEM | PO_NO_NETWORK | PO_NO_EXTERNAL_PROCESS
    | PO_NO_UNCONTROLLED_APIS | PO_NO_EMBEDDED_LOGIC;

//! The driver and properties of an allowed JDBC URL; see qore_jdbc_check_restricted_url()
struct QoreJdbcRestrictedUrl {
    //! the Java classes of the drivers that may serve the URL, each as "<class>@<major>.<minor>" with the minimum
    //! version (Driver.getMajorVersion() and getMinorVersion()) of a driver that has the properties of the
    //! connection, as a driver ignores the properties that it does not know
    std::vector<std::string> driver_classes;
    //! the driver properties that the connection is made with, which turn off the features of the driver that reach
    //! the filesystem or the credentials of the host for the server (ex: MariaDB's LOAD DATA LOCAL INFILE, which the
    //! driver allows by default, or Kerberos authentication that the server requests); the URL cannot set them, as
    //! they are not on the driver's list of parameters
    std::vector<std::pair<std::string, std::string>> properties;
};

//! Checks a JDBC URL that code restricted by JDBC_RESTRICTING_OPTIONS chose (qore#7867)
/** Allows only the client/server drivers whose URL form and parameters cannot make the driver read or write files,
    connect to other destinations than the database servers that the URL names, or load classes; failing closed for
    everything else:
    - \c jdbc:postgresql://, \c jdbc:mysql://, \c jdbc:mariadb://, \c jdbc:sqlserver:// and \c jdbc:db2:// URLs with
      host names or addresses and the parameters of the driver's allow-list;
    - \c jdbc:oracle:thin: URLs in the EZConnect form (\c @host:port:sid, \c @host[:port]/service or
      \c @//host[:port]/service) without parameters, protocols, connect descriptors or TNS aliases

    Embedded and file drivers (H2, Derby, HSQLDB, SQLite, ...), every other subprotocol, the other URL forms of the
    allowed drivers (ex: a TNS alias, \c jdbc:mysql:loadbalance://, \c address=(...) hosts) and every parameter that
    is not on the driver's allow-list are refused.

    @param url the JDBC URL, with or without the \c "jdbc:" prefix
    @param allowed the driver classes and the properties of the connection, if the URL is allowed
    @param error the reason if the URL is refused; it names the subprotocol and the refused parameter, but not the
    URL, which can contain secrets

    @return true if the URL is allowed, false if not
*/
DLLLOCAL bool qore_jdbc_check_restricted_url(const std::string& url, QoreJdbcRestrictedUrl& allowed,
    std::string& error);

}

#endif
