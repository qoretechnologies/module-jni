// Copyright (C) 2026 Qore Technologies, s.r.o.; SPDX-License-Identifier: MIT
package com.amazon.redshift.util;

//! The version of the fake Redshift driver (qore#5786 tests): not the audited version
public final class DriverInfo {
    public static final String DRIVER_VERSION;

    static {
        DRIVER_VERSION = "2.2.0";
    }

    private DriverInfo() {
    }
}
