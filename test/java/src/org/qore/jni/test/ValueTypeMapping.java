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

//! Values of reference and primitive types in each use converted to %Qore (return values, fields, static fields
//! and constants), for checking their declared %Qore types (see test/jni.qtest "value type mapping guard test")
public class ValueTypeMapping {
    public String fieldString;
    public static String staticString;
    public static final String CONST_STRING = null;
    public static String returnString() {
        return null;
    }
    public Integer fieldInteger;
    public static Integer staticInteger;
    public static final Integer CONST_INTEGER = null;
    public static Integer returnInteger() {
        return null;
    }
    public Long fieldLong;
    public static Long staticLong;
    public static final Long CONST_LONG = null;
    public static Long returnLong() {
        return null;
    }
    public Short fieldShort;
    public static Short staticShort;
    public static final Short CONST_SHORT = null;
    public static Short returnShort() {
        return null;
    }
    public Byte fieldByte;
    public static Byte staticByte;
    public static final Byte CONST_BYTE = null;
    public static Byte returnByte() {
        return null;
    }
    public Boolean fieldBoolean;
    public static Boolean staticBoolean;
    public static final Boolean CONST_BOOLEAN = null;
    public static Boolean returnBoolean() {
        return null;
    }
    public Double fieldDouble;
    public static Double staticDouble;
    public static final Double CONST_DOUBLE = null;
    public static Double returnDouble() {
        return null;
    }
    public Float fieldFloat;
    public static Float staticFloat;
    public static final Float CONST_FLOAT = null;
    public static Float returnFloat() {
        return null;
    }
    public Character fieldCharacter;
    public static Character staticCharacter;
    public static final Character CONST_CHARACTER = null;
    public static Character returnCharacter() {
        return null;
    }
    public java.math.BigDecimal fieldBigDecimal;
    public static java.math.BigDecimal staticBigDecimal;
    public static final java.math.BigDecimal CONST_BIGDECIMAL = null;
    public static java.math.BigDecimal returnBigDecimal() {
        return null;
    }
    public java.time.ZonedDateTime fieldZonedDateTime;
    public static java.time.ZonedDateTime staticZonedDateTime;
    public static final java.time.ZonedDateTime CONST_ZONEDDATETIME = null;
    public static java.time.ZonedDateTime returnZonedDateTime() {
        return null;
    }
    public java.time.LocalDate fieldLocalDate;
    public static java.time.LocalDate staticLocalDate;
    public static final java.time.LocalDate CONST_LOCALDATE = null;
    public static java.time.LocalDate returnLocalDate() {
        return null;
    }
    public java.sql.Timestamp fieldTimestamp;
    public static java.sql.Timestamp staticTimestamp;
    public static final java.sql.Timestamp CONST_TIMESTAMP = null;
    public static java.sql.Timestamp returnTimestamp() {
        return null;
    }
    public Object fieldObject;
    public static Object staticObject;
    public static final Object CONST_OBJECT = null;
    public static Object returnObject() {
        return null;
    }
    public Number fieldNumber;
    public static Number staticNumber;
    public static final Number CONST_NUMBER = null;
    public static Number returnNumber() {
        return null;
    }
    public CharSequence fieldCharSequence;
    public static CharSequence staticCharSequence;
    public static final CharSequence CONST_CHARSEQUENCE = null;
    public static CharSequence returnCharSequence() {
        return null;
    }
    public Comparable<String> fieldComparable;
    public static Comparable<String> staticComparable;
    public static final Comparable<String> CONST_COMPARABLE = null;
    public static Comparable<String> returnComparable() {
        return null;
    }
    public java.time.temporal.Temporal fieldTemporal;
    public static java.time.temporal.Temporal staticTemporal;
    public static final java.time.temporal.Temporal CONST_TEMPORAL = null;
    public static java.time.temporal.Temporal returnTemporal() {
        return null;
    }
    public java.util.Date fieldUtilDate;
    public static java.util.Date staticUtilDate;
    public static final java.util.Date CONST_UTILDATE = null;
    public static java.util.Date returnUtilDate() {
        return null;
    }
    public java.io.Serializable fieldSerializable;
    public static java.io.Serializable staticSerializable;
    public static final java.io.Serializable CONST_SERIALIZABLE = null;
    public static java.io.Serializable returnSerializable() {
        return null;
    }
    public java.util.Collection<String> fieldCollection;
    public static java.util.Collection<String> staticCollection;
    public static final java.util.Collection<String> CONST_COLLECTION = null;
    public static java.util.Collection<String> returnCollection() {
        return null;
    }
    public java.util.ArrayList<String> fieldArrayList;
    public static java.util.ArrayList<String> staticArrayList;
    public static final java.util.ArrayList<String> CONST_ARRAYLIST = null;
    public static java.util.ArrayList<String> returnArrayList() {
        return null;
    }
    public java.util.Map<String, String> fieldStringMap;
    public static java.util.Map<String, String> staticStringMap;
    public static final java.util.Map<String, String> CONST_STRINGMAP = null;
    public static java.util.Map<String, String> returnStringMap() {
        return null;
    }
    public java.util.Map<Integer, String> fieldIntegerMap;
    public static java.util.Map<Integer, String> staticIntegerMap;
    public static final java.util.Map<Integer, String> CONST_INTEGERMAP = null;
    public static java.util.Map<Integer, String> returnIntegerMap() {
        return null;
    }
    public int[] fieldIntArray;
    public static int[] staticIntArray;
    public static final int[] CONST_INTARRAY = null;
    public static int[] returnIntArray() {
        return null;
    }
    public String[] fieldStringArray;
    public static String[] staticStringArray;
    public static final String[] CONST_STRINGARRAY = null;
    public static String[] returnStringArray() {
        return null;
    }
    public Integer[][] fieldIntegerArray2;
    public static Integer[][] staticIntegerArray2;
    public static final Integer[][] CONST_INTEGERARRAY2 = null;
    public static Integer[][] returnIntegerArray2() {
        return null;
    }
    public Character[] fieldCharacterArray;
    public static Character[] staticCharacterArray;
    public static final Character[] CONST_CHARACTERARRAY = null;
    public static Character[] returnCharacterArray() {
        return null;
    }
    public byte[] fieldByteArray;
    public static byte[] staticByteArray;
    public static final byte[] CONST_BYTEARRAY = null;
    public static byte[] returnByteArray() {
        return null;
    }
    public ValueTypeMapping fieldSelf;
    public static ValueTypeMapping staticSelf;
    public static final ValueTypeMapping CONST_SELF = null;
    public static ValueTypeMapping returnSelf() {
        return null;
    }
    public Runnable fieldRunnable;
    public static Runnable staticRunnable;
    public static final Runnable CONST_RUNNABLE = null;
    public static Runnable returnRunnable() {
        return null;
    }
    public java.math.BigInteger fieldBigInteger;
    public static java.math.BigInteger staticBigInteger;
    public static final java.math.BigInteger CONST_BIGINTEGER = null;
    public static java.math.BigInteger returnBigInteger() {
        return null;
    }
    public int primInt;
    public static int staticPrimInt;
    public static int returnPrimInt() {
        return 0;
    }
    public long primLong;
    public static long staticPrimLong;
    public static long returnPrimLong() {
        return 0;
    }
    public char primChar;
    public static char staticPrimChar;
    public static char returnPrimChar() {
        return 0;
    }
    public boolean primBoolean;
    public static boolean staticPrimBoolean;
    public static boolean returnPrimBoolean() {
        return false;
    }
    public double primDouble;
    public static double staticPrimDouble;
    public static double returnPrimDouble() {
        return 0;
    }
}
