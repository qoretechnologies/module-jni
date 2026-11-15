// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package com.singlestore.jdbc.util;

//! The version of the fake SingleStore driver (qore#5786 tests)
public class Version {
    private final String version;

    public Version(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
