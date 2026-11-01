// Copyright (C) 2026 Qore Technologies, s.r.o.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.

package org.qore.jni.test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

//! Fields of reference types, which are null until they are assigned, and fields of primitive types, which cannot be
public class NullableFields {
    // instance fields of reference types; all are null
    public String str;
    public Integer boxedInt;
    public Long boxedLong;
    public Boolean boxedBool;
    public Double boxedDouble;
    public Character boxedChar;
    public Number num;
    public BigDecimal dec;
    public NullableFields self;
    public Runnable task;
    public String[] strArray;
    public Integer[] boxedArray;
    public int[] intArray;
    public byte[] bytes;
    public String[][] nestedArray;
    public List<String> strList;
    public Map<String, String> strMap;

    // instance fields of primitive types
    public int prim;
    public boolean primBool;
    public double primDouble;

    // static fields of reference types; all are null
    public static String staticStr;
    public static Integer staticBoxedInt;
    public static NullableFields staticSelf;
    public static String[] staticStrArray;
    public static int[] staticIntArray;

    // static fields of primitive types
    public static int staticPrim;
    public static boolean staticPrimBool;

    //! Returns true if all instance fields of reference types are null
    public boolean allNull() {
        return str == null && boxedInt == null && boxedLong == null && boxedBool == null && boxedDouble == null
            && boxedChar == null && num == null && dec == null && self == null && task == null && strArray == null
            && boxedArray == null && intArray == null && bytes == null && nestedArray == null && strList == null
            && strMap == null;
    }

    //! Returns true if all static fields of reference types are null
    public static boolean allStaticNull() {
        return staticStr == null && staticBoxedInt == null && staticSelf == null && staticStrArray == null
            && staticIntArray == null;
    }
}
