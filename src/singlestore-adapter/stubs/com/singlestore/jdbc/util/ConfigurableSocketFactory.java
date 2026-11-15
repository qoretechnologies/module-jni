/*
    Copyright (C) 2026 Qore Technologies, s.r.o.

    A signature-only stub of the SingleStore JDBC driver's ConfigurableSocketFactory class, against which the jni
    module's SingleStore adapter (com.singlestore.jdbc.util.QoreGuardSocketFactory) is compiled (qore#5786); it is
    not part of any jar.  The signatures are the driver's (version 1.2.12).
*/

package com.singlestore.jdbc.util;

import com.singlestore.jdbc.Configuration;
import javax.net.SocketFactory;

public abstract class ConfigurableSocketFactory extends SocketFactory {
    public abstract void setConfiguration(Configuration conf, String host);
}
