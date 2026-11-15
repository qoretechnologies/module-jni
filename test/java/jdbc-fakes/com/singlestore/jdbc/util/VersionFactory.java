// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package com.singlestore.jdbc.util;

//! The version factory of the fake SingleStore driver (qore#5786 tests): not the audited version
public final class VersionFactory {
    private VersionFactory() {
    }

    public static Version getInstance() {
        return new Version("1.2.11");
    }
}
