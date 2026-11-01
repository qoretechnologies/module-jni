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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.Date;

//! Methods whose return values are converted to %Qore values of other types than the declared classes, or are null
public class ConvertedValueReturns {
    // return values converted to Qore values of other types than the declared class
    public static CharSequence charSequence() {
        return "cs";
    }

    public static Comparable<String> comparable() {
        return "c";
    }

    public static Number number() {
        return Integer.valueOf(3);
    }

    public static Character character() {
        return Character.valueOf('a');
    }

    public static Temporal temporal() {
        return LocalDate.of(2026, 1, 2);
    }

    public static Date timestampInDate() {
        return new Timestamp(0);
    }

    public static Character[] characters() {
        return new Character[] {'a', null};
    }

    // null return values
    public static String nullString() {
        return null;
    }

    public static Integer nullInteger() {
        return null;
    }

    public static int[] nullIntArray() {
        return null;
    }

    public static Character nullCharacter() {
        return null;
    }

    // return values whose type is exact
    public static String string() {
        return "s";
    }

    public static int prim() {
        return 1;
    }

    public ConvertedValueReturns self() {
        return this;
    }
}
