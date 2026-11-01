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

//! Static final fields, which are mirrored by %Qore class constants, holding null and holding values
public class FinalFields {
    // null fields of reference types
    public static final String NULL_STR = null;
    public static final Integer NULL_BOXED_INT = null;
    public static final Boolean NULL_BOXED_BOOL = null;
    public static final Character NULL_CHAR = null;
    public static final Number NULL_NUM = null;
    public static final Object NULL_OBJ = null;
    public static final FinalFields NULL_SELF = null;
    public static final int[] NULL_INT_ARRAY = null;
    public static final String[] NULL_STR_ARRAY = null;

    // fields holding values
    public static final String STR = "s";
    public static final Integer BOXED_INT = Integer.valueOf(5);
    public static final Boolean BOXED_BOOL = Boolean.FALSE;
    public static final Character CHAR = Character.valueOf('a');
    public static final Number NUM = Integer.valueOf(3);
    public static final int PRIM = 7;
    public static final boolean PRIM_BOOL = false;
    public static final long ZERO = 0;
    public static final int[] INT_ARRAY = {1, 2};
    public static final FinalFields SELF = new FinalFields();
}
