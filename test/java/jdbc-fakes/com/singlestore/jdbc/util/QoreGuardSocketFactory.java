// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package com.singlestore.jdbc.util;

//! A class with the name of the jni module's SingleStore adapter (qore#5786 tests): it must never be used
public final class QoreGuardSocketFactory {
    public QoreGuardSocketFactory() {
        throw new IllegalStateException("QORE-FAKE-ADAPTER-USED");
    }
}
