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

/**
 * Test class for overloaded method dispatch with array parameters.
 *
 * This class simulates the pattern found in Eclipse Paho MQTT v5
 * MqttClient where overloaded methods delegate internally and
 * incorrect varargs handling causes infinite recursion.
 */
public class OverloadedMethods {
    private int callCount = 0;

    /**
     * Overloaded method taking String[] and int[] that delegates
     * internally to the String[] overload.
     */
    public String process(String[] names, int[] values) {
        // Delegate to the single-array overload internally
        // If fake varargs causes wrong dispatch, this will recurse infinitely
        return process(names);
    }

    /**
     * Overloaded method taking String[] only.
     */
    public String process(String[] names) {
        callCount++;
        if (callCount > 10) {
            throw new StackOverflowError("Infinite recursion detected in overloaded method dispatch");
        }
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /**
     * Returns the call count for verification.
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * Resets the call count.
     */
    public void resetCallCount() {
        callCount = 0;
    }

    /**
     * Static test method: calls process(String[], int[]) and verifies
     * no infinite recursion occurs.
     */
    public static String testOverloadedDispatch() {
        OverloadedMethods obj = new OverloadedMethods();
        String[] names = {"foo", "bar"};
        int[] values = {1, 2};
        String result = obj.process(names, values);
        if (obj.getCallCount() != 1) {
            throw new RuntimeException("Expected callCount=1, got " + obj.getCallCount());
        }
        return result;
    }

    /**
     * Static test method: calls process(String[]) directly.
     */
    public static String testDirectArrayCall() {
        OverloadedMethods obj = new OverloadedMethods();
        String[] names = {"hello", "world"};
        return obj.process(names);
    }
}
