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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.qore.jni.Hash;

//! Overloads whose parameters take converted %Qore values, and the variants Java selects for the converted values
/** Each method returns the class of the variant called.  javaChoices() calls them from Java with the values that the
    jni module converts the %Qore arguments of test/jni.qtest "parameter overload choices test" to (a string to a
    String, an integer to a Long, a float to a Double, a boolean to a Boolean, a number to a BigDecimal, a date to a
    ZonedDateTime, a binary to a byte[], a list to an ArrayList, a hash to a Hash), so that the test can check that a
    %Qore argument selects the variant Java selects.
*/
public class OverloadChoices {
    public static String objOrCs(Object o) { return "Object"; }
    public static String objOrCs(CharSequence cs) { return "CharSequence"; }

    public static String serOrObj(Serializable s) { return "Serializable"; }
    public static String serOrObj(Object o) { return "Object"; }

    public static String numOrObj(Number n) { return "Number"; }
    public static String numOrObj(Object o) { return "Object"; }

    public static String cmpOrObj(Comparable<?> c) { return "Comparable"; }
    public static String cmpOrObj(Object o) { return "Object"; }

    public static String temporalOrObj(Temporal t) { return "Temporal"; }
    public static String temporalOrObj(Object o) { return "Object"; }

    public static String iterOrObj(Iterable<?> i) { return "Iterable"; }
    public static String iterOrObj(Object o) { return "Object"; }

    public static String charOrObj(Character c) { return "Character"; }
    public static String charOrObj(Object o) { return "Object"; }

    public static String charOnly(Character c) { return "Character"; }

    public static String charOrLong(Character c) { return "Character"; }
    public static String charOrLong(long l) { return "long"; }

    public static String strOrCs(String s) { return "String"; }
    public static String strOrCs(CharSequence cs) { return "CharSequence"; }

    public static String strOrObj(String s) { return "String"; }
    public static String strOrObj(Object o) { return "Object"; }

    public static String strOrArr(String s) { return "String"; }
    public static String strOrArr(String[] a) { return "String[]"; }

    //! returns the variant Java selects for each call, by call name (method:argument)
    public static Map<String, String> javaChoices() {
        Map<String, String> rv = new TreeMap<String, String>();
        String s = "x";
        Long l = Long.valueOf(1);
        Double d = Double.valueOf(1.5);
        Boolean b = Boolean.TRUE;
        BigDecimal n = new BigDecimal("1.5");
        ZonedDateTime date = ZonedDateTime.now();
        byte[] bin = new byte[] {1};
        ArrayList<Object> list = new ArrayList<Object>();
        Hash hash = new Hash();
        StringBuilder sb = new StringBuilder("sb");

        rv.put("objOrCs:string", objOrCs(s));
        rv.put("objOrCs:int", objOrCs(l));
        rv.put("objOrCs:float", objOrCs(d));
        rv.put("objOrCs:bool", objOrCs(b));
        rv.put("objOrCs:object", objOrCs(sb));
        rv.put("objOrCs:null", objOrCs(null));

        rv.put("serOrObj:string", serOrObj(s));
        rv.put("serOrObj:int", serOrObj(l));
        rv.put("serOrObj:float", serOrObj(d));
        rv.put("serOrObj:bool", serOrObj(b));
        rv.put("serOrObj:number", serOrObj(n));
        rv.put("serOrObj:date", serOrObj(date));
        rv.put("serOrObj:binary", serOrObj(bin));
        rv.put("serOrObj:list", serOrObj(list));
        rv.put("serOrObj:hash", serOrObj(hash));
        rv.put("serOrObj:null", serOrObj(null));

        rv.put("numOrObj:int", numOrObj(l));
        rv.put("numOrObj:float", numOrObj(d));
        rv.put("numOrObj:number", numOrObj(n));
        rv.put("numOrObj:string", numOrObj(s));
        rv.put("numOrObj:null", numOrObj(null));

        rv.put("cmpOrObj:string", cmpOrObj(s));
        rv.put("cmpOrObj:int", cmpOrObj(l));
        rv.put("cmpOrObj:float", cmpOrObj(d));
        rv.put("cmpOrObj:bool", cmpOrObj(b));
        rv.put("cmpOrObj:number", cmpOrObj(n));
        rv.put("cmpOrObj:date", cmpOrObj(date));
        rv.put("cmpOrObj:list", cmpOrObj(list));
        rv.put("cmpOrObj:null", cmpOrObj(null));

        rv.put("temporalOrObj:date", temporalOrObj(date));
        rv.put("temporalOrObj:string", temporalOrObj(s));
        rv.put("temporalOrObj:null", temporalOrObj(null));

        rv.put("iterOrObj:list", iterOrObj(list));
        rv.put("iterOrObj:string", iterOrObj(s));
        rv.put("iterOrObj:null", iterOrObj(null));

        rv.put("charOrObj:int", charOrObj(l));
        rv.put("charOrObj:null", charOrObj(null));

        rv.put("charOrLong:int", charOrLong(l));
        rv.put("charOrLong:null", charOrLong(null));

        rv.put("strOrCs:string", strOrCs(s));
        rv.put("strOrCs:object", strOrCs(sb));
        rv.put("strOrCs:null", strOrCs(null));

        rv.put("strOrObj:string", strOrObj(s));
        rv.put("strOrObj:int", strOrObj(l));
        rv.put("strOrObj:null", strOrObj(null));

        rv.put("strOrArr:string", strOrArr(s));
        rv.put("strOrArr:list", strOrArr(new String[] {"a"}));
        return rv;
    }
}
