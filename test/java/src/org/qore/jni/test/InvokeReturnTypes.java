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

//! Methods with primitive, void, String and object return types for jni::invoke() and jni::invoke_nonvirtual()
/** used by test/jni-invoke-return-type.qtest (qore#6808); the class is in this jar and not in java.base, as
    jni::invoke_nonvirtual() needs access to the declaring class of the method
*/
public class InvokeReturnTypes {
    private final String value;
    private int touched = 0;
    private static int staticTouched = 0;

    public InvokeReturnTypes(String value) {
        this.value = value;
    }

    public int length() {
        return value.length();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public double half(double d) {
        return d / 2;
    }

    public char charAt(int index) {
        return value.charAt(index);
    }

    public void touch() {
        ++touched;
    }

    public int getTouched() {
        return touched;
    }

    public String upper() {
        return value.toUpperCase();
    }

    public InvokeReturnTypes self() {
        return this;
    }

    public static double staticHalf(double d) {
        return d / 2;
    }

    public static void staticTouch() {
        ++staticTouched;
    }

    public static int getStaticTouched() {
        return staticTouched;
    }
}
