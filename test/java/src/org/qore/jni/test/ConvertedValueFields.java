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

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.Date;

//! Fields whose values are converted to %Qore values of other types than the declared class of the field
public class ConvertedValueFields {
    // instance fields
    public Character ch = 'a';
    public Number num = Integer.valueOf(3);
    public CharSequence chars = "x";
    public Comparable<String> comparable = "y";
    public Date utilDate = new Date(0);
    public Date timestampInDate = new Timestamp(0);
    public Temporal temporal = LocalDate.of(2026, 1, 2);
    public Character[] charArray = {'a', 'b'};
    public Cloneable cloneable = new int[] {1, 2};
    // a Number whose values are not converted
    public BigInteger big = BigInteger.TEN;

    // static fields
    public static Number staticNum = Long.valueOf(4);
    public static CharSequence staticChars = "z";
    public static Character staticCh = 'c';
}
