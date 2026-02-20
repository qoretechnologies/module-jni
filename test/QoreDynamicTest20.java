// Copyright 2026 Qore Technologies, s.r.o.

package org.qore.test;

import qore.AutoParamTest;

public class QoreDynamicTest20 {
    // test full constructor: (string, auto, auto)
    public static AutoParamTest testFullCtor() throws Throwable {
        return new AutoParamTest("full", 1, 2);
    }

    // test constructor with last auto param omitted: (string, auto)
    public static AutoParamTest testCtorOmitOne() throws Throwable {
        return new AutoParamTest("omit1", 42);
    }

    // test constructor with both auto params omitted: (string)
    public static AutoParamTest testCtorOmitTwo() throws Throwable {
        return new AutoParamTest("omit2");
    }

    // test method with trailing auto param: (string, auto)
    public static String testMethodWithOpt() throws Throwable {
        AutoParamTest obj = new AutoParamTest("test", null, null);
        return obj.staticTest("hello", "world");
    }

    // test method with trailing auto param omitted: (string)
    public static String testMethodWithoutOpt() throws Throwable {
        AutoParamTest obj = new AutoParamTest("test", null, null);
        return obj.staticTest("hello");
    }
}
