/*
    Copyright (C) 2026 Qore Technologies, s.r.o.

    A signature-only stub of the SingleStore JDBC driver's Configuration class, against which the jni module's
    SingleStore adapter (com.singlestore.jdbc.util.QoreGuardSocketFactory) is compiled (qore#5786); it is not part of
    any jar.  Only the members that the adapter uses are declared, with the driver's signatures (version 1.2.12).
*/

package com.singlestore.jdbc;

import java.util.Properties;

public final class Configuration {
    private Configuration() {
    }

    public Properties nonMappedOptions() {
        throw new UnsupportedOperationException("stub");
    }
}
