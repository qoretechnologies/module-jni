/*
    KotlinQoreApiTest.kt

    Kotlin test class for testing Kotlin calling Qore APIs via JNI.
    This tests bidirectional interoperability - Kotlin -> Qore.

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni.test

import org.qore.jni.QoreJavaApi
import org.qore.jni.QoreObject
import org.qore.jni.QoreClosure
import org.qore.jni.Hash

/**
 * Tests Kotlin calling Qore functions and methods via QoreJavaApi.
 * Mirrors the Java QoreJavaApiTest to ensure Kotlin has equivalent capabilities.
 */
object KotlinQoreApiTest {
    /**
     * Call a Qore function and return the result.
     * Tests QoreJavaApi.callFunction() from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun callQoreFunction(name: String): Any? {
        return QoreJavaApi.callFunction(name)
    }

    /**
     * Call a Qore static method and return the result.
     * Tests QoreJavaApi.callStaticMethod() from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun callQoreStaticMethod(className: String, methodName: String, vararg args: Any?): Any? {
        return QoreJavaApi.callStaticMethod(className, methodName, *args)
    }

    /**
     * Create a new Qore object and call a method on it.
     * Tests QoreJavaApi.newObjectSave() and QoreObject.callMethod() from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun createAndCallQoreObject(className: String, methodName: String, vararg args: Any?): Any? {
        val obj = QoreJavaApi.newObjectSave(className)
        return try {
            obj.callMethod(methodName, *args)
        } finally {
            obj.release()
        }
    }

    /**
     * Get a member value from a Qore object.
     * Tests QoreObject.getMemberValue() from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun getQoreObjectMember(obj: QoreObject, memberName: String): Any? {
        return try {
            obj.getMemberValue(memberName)
        } finally {
            obj.release()
        }
    }

    /**
     * Check if a Qore object is an instance of a class.
     * Tests QoreObject.instanceOf() from Kotlin.
     */
    @JvmStatic
    fun checkQoreObjectInstance(obj: QoreObject, className: String): Boolean {
        return try {
            obj.instanceOf(className)
        } finally {
            obj.release()
        }
    }

    /**
     * Get the class name of a Qore object.
     * Tests QoreObject.className() from Kotlin.
     */
    @JvmStatic
    fun getQoreObjectClassName(obj: QoreObject): String {
        return obj.className()
    }

    /**
     * Call a Qore closure from Kotlin.
     * Tests QoreClosure.call() from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun callQoreClosure(closure: QoreClosure, vararg args: Any?): Any? {
        return closure.call(*args)
    }

    /**
     * Create a Hash in Kotlin and pass it to Qore.
     * Tests Hash usage from Kotlin.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun createHashForQore(): Hash {
        val h = Hash()
        h["name"] = "Kotlin"
        h["version"] = 2
        h["enabled"] = true
        return h
    }

    /**
     * Read values from a Hash passed from Qore.
     * Tests Hash getter methods from Kotlin.
     * Note: Qore integers are converted to Java Long, not Integer
     */
    @JvmStatic
    fun readHashFromQore(h: Hash): String {
        val name = h.getString("name") ?: "unknown"
        // Use getAsLong or get() since Qore integers map to Java Long
        val value = h.getAsLong("value")
        return "$name:$value"
    }

    /**
     * Test thread registration for Kotlin code running in a new thread.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testThreadedQoreCall(): String {
        var result: String? = null
        val thread = Thread {
            QoreJavaApi.registerJavaThread()
            try {
                val obj = QoreJavaApi.newObjectSave("TestClass2")
                result = obj.callMethod("getString") as? String
            } finally {
                QoreJavaApi.deregisterJavaThread()
            }
        }
        thread.start()
        thread.join()
        return result ?: "error"
    }

    /**
     * Test calling a Qore function that returns library info.
     * This is the same test as in Java to verify identical behavior.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun getQoreLibraryInfo(): HashMap<*, *> {
        return QoreJavaApi.callFunction("::get_qore_library_info") as HashMap<*, *>
    }

    /**
     * Kotlin-specific: Use Kotlin's null safety with Qore API.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun safeCallQoreFunction(name: String): String {
        val result = QoreJavaApi.callFunction(name)
        return result?.toString() ?: "null"
    }

    /**
     * Kotlin-specific: Use extension-like pattern with Qore objects.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun qoreObjectToString(obj: QoreObject): String {
        return try {
            val className = obj.className()
            val method = try {
                obj.callMethod("toString") as? String
            } catch (e: Throwable) {
                null
            }
            "$className: ${method ?: "no toString"}"
        } finally {
            obj.release()
        }
    }

    // =====================================================
    // HashMap/Map compatibility tests (issue #4892)
    // These tests verify that Kotlin's native HashMap can be used
    // directly with Qore methods expecting hash<auto> parameters
    // =====================================================

    /**
     * Test passing Kotlin's hashMapOf() result to Qore method expecting hash<auto>.
     * This is the primary use case for the HashMap compatibility fix.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testHashMapToQoreHash(): String {
        // Create a Kotlin HashMap using the idiomatic hashMapOf() function
        val data = hashMapOf<String, Any?>(
            "name" to "test",
            "value" to 42
        )
        // Call Qore static method that expects hash<auto> parameter
        return QoreJavaApi.callStaticMethod("HashParamTest", "processHash", data) as String
    }

    /**
     * Test that LinkedHashMap also works (preserves insertion order).
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testLinkedHashMapToQoreHash(): Int {
        val data = linkedMapOf<String, Any?>(
            "first" to 1,
            "second" to 2,
            "third" to 3
        )
        return (QoreJavaApi.callStaticMethod("HashParamTest", "getHashSize", data) as Number).toInt()
    }

    /**
     * Test passing an empty HashMap.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testEmptyMapToQoreHash(): Int {
        val data = hashMapOf<String, Any?>()
        return (QoreJavaApi.callStaticMethod("HashParamTest", "getHashSize", data) as Number).toInt()
    }

    /**
     * Test HashMap with mixed value types (string, int, bool, list).
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testMapWithMixedValues(): String {
        val data = hashMapOf<String, Any?>(
            "string" to "hello",
            "int" to 123,
            "bool" to true,
            "list" to listOf(1, 2, 3)
        )
        return QoreJavaApi.callStaticMethod("HashParamTest", "describeMixedHash", data) as String
    }

    /**
     * Test nested HashMap (HashMap containing another HashMap).
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testNestedMapToQoreHash(): Any? {
        val inner = hashMapOf<String, Any?>(
            "key" to "nested_value"
        )
        val outer = hashMapOf<String, Any?>(
            "nested" to inner
        )
        return QoreJavaApi.callStaticMethod("HashParamTest", "getNestedValue", outer, "nested", "key")
    }

    /**
     * Test passing null to optional hash parameter.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testNullMapHandling(): String {
        return QoreJavaApi.callStaticMethod("HashParamTest", "processOptionalHash", null) as String
    }

    /**
     * Test merging two HashMaps via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testMergeHashMaps(): HashMap<*, *> {
        val h1 = hashMapOf<String, Any?>("a" to 1, "b" to 2)
        val h2 = hashMapOf<String, Any?>("c" to 3, "d" to 4)
        return QoreJavaApi.callStaticMethod("HashParamTest", "mergeHashes", h1, h2) as HashMap<*, *>
    }

    /**
     * Test modifying a HashMap via Qore and getting back the result.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testModifyHashMap(): HashMap<*, *> {
        val data = hashMapOf<String, Any?>("original" to true)
        return QoreJavaApi.callStaticMethod("HashParamTest", "modifyHash", data) as HashMap<*, *>
    }

    /**
     * Test retrieving a specific value from a HashMap via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testGetValueFromHashMap(): Any? {
        val data = hashMapOf<String, Any?>(
            "name" to "test_name",
            "count" to 42
        )
        return QoreJavaApi.callStaticMethod("HashParamTest", "getValue", data, "name")
    }

    /**
     * Test hash value type narrowing - HashMap<String, Int> should convert to hash<string, int>.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testHashMapIntValues(): Int {
        val data = hashMapOf<String, Int>(
            "a" to 10,
            "b" to 20,
            "c" to 30
        )
        return (QoreJavaApi.callStaticMethod("HashParamTest", "sumHashIntValues", data) as Number).toInt()
    }

    /**
     * Test hash value type narrowing - HashMap<String, String> should convert to hash<string, string>.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testHashMapStringValues(): String {
        val data = hashMapOf<String, String>(
            "first" to "hello",
            "second" to "world"
        )
        return QoreJavaApi.callStaticMethod("HashParamTest", "joinHashStringValues", data, " ") as String
    }

    // =====================================================
    // List/Array compatibility tests
    // These tests verify that Kotlin's native list types can be used
    // directly with Qore methods expecting list<auto> parameters
    // =====================================================

    /**
     * Test passing Kotlin's listOf() result to Qore method expecting list<auto>.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testListOfToQoreList(): Int {
        // Create a Kotlin immutable list using listOf()
        val data = listOf(1, 2, 3)
        return (QoreJavaApi.callStaticMethod("ListParamTest", "getListSize", data) as Number).toInt()
    }

    /**
     * Test that ArrayList also works.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testArrayListToQoreList(): String {
        val data = arrayListOf("one", "two", "three", "four")
        return QoreJavaApi.callStaticMethod("ListParamTest", "describeList", data) as String
    }

    /**
     * Test passing an empty list.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testEmptyListToQoreList(): Int {
        val data = emptyList<Any>()
        return (QoreJavaApi.callStaticMethod("ListParamTest", "getListSize", data) as Number).toInt()
    }

    /**
     * Test list of integers with sum.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testListOfIntegers(): Int {
        val data = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        return (QoreJavaApi.callStaticMethod("ListParamTest", "sumIntegers", data) as Number).toInt()
    }

    /**
     * Test list of strings with join.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testListOfStrings(): String {
        val data = listOf("a", "b", "c", "d")
        return QoreJavaApi.callStaticMethod("ListParamTest", "joinStrings", data, ",") as String
    }

    /**
     * Test nested list (list of lists).
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testNestedListToQoreList(): Any? {
        val inner = listOf("inner_value", "other")
        val outer = listOf(inner, listOf("second_list"))
        return QoreJavaApi.callStaticMethod("ListParamTest", "getNestedElement", outer, 0, 0)
    }

    /**
     * Test passing null to optional list parameter.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testNullListHandling(): String {
        return QoreJavaApi.callStaticMethod("ListParamTest", "processOptionalList", null) as String
    }

    /**
     * Test appending to a list.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testAppendToList(): List<*> {
        val data = mutableListOf(1, 2, 3)
        @Suppress("UNCHECKED_CAST")
        return QoreJavaApi.callStaticMethod("ListParamTest", "appendToList", data, 4) as List<*>
    }

    // =====================================================
    // Date/Time compatibility tests
    // These tests verify that Kotlin/Java's java.time types work
    // with Qore's date type
    // Supported types: ZonedDateTime, LocalDateTime, Instant
    // =====================================================

    /**
     * Test that ZonedDateTime works with Qore date formatting.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testZonedDateTimeToQoreDate(): String {
        val dt = java.time.ZonedDateTime.of(2024, 12, 25, 12, 0, 0, 0, java.time.ZoneId.of("UTC"))
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "formatDate", dt) as String
    }

    /**
     * Test extracting year from ZonedDateTime via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testZonedDateTimeYear(): Int {
        val dt = java.time.ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, java.time.ZoneId.of("UTC"))
        return (QoreJavaApi.callStaticMethod("DateTimeParamTest", "getYear", dt) as Number).toInt()
    }

    /**
     * Test adding days to ZonedDateTime via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testZonedDateTimeAddDays(): String {
        val dt = java.time.ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, java.time.ZoneId.of("UTC"))
        val result = QoreJavaApi.callStaticMethod("DateTimeParamTest", "addDays", dt, 10) as java.time.ZonedDateTime
        return result.toString()
    }

    /**
     * Test passing null to optional date parameter.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testNullDateHandling(): String {
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "processOptionalDate", null) as String
    }

    /**
     * Test getting current time from Qore and comparing.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testGetCurrentTime(): java.time.ZonedDateTime {
        @Suppress("UNCHECKED_CAST")
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "getCurrentTime") as java.time.ZonedDateTime
    }

    /**
     * Test checking if a date is in the past.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testZonedDateTimeInPast(): Boolean {
        val dt = java.time.ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, java.time.ZoneId.of("UTC"))
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "isInPast", dt) as Boolean
    }

    // =====================================================
    // LocalDateTime tests (issue #4892)
    // =====================================================

    /**
     * Test that LocalDateTime is automatically converted to Qore date.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testLocalDateTimeToQoreDate(): String {
        val dt = java.time.LocalDateTime.of(2024, 12, 25, 12, 30, 45)
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "formatDate", dt) as String
    }

    /**
     * Test extracting year from LocalDateTime via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testLocalDateTimeYear(): Int {
        val dt = java.time.LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        return (QoreJavaApi.callStaticMethod("DateTimeParamTest", "getYear", dt) as Number).toInt()
    }

    /**
     * Test LocalDateTime with nanoseconds.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testLocalDateTimeWithNanos(): String {
        val dt = java.time.LocalDateTime.of(2024, 3, 15, 14, 30, 45, 123456789)
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "formatDateWithMicros", dt) as String
    }

    // =====================================================
    // Instant tests (issue #4892)
    // =====================================================

    /**
     * Test that Instant is automatically converted to Qore date.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testInstantToQoreDate(): String {
        // Create Instant from epoch seconds (2024-12-25 12:00:00 UTC)
        val dt = java.time.Instant.parse("2024-12-25T12:00:00Z")
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "formatDate", dt) as String
    }

    /**
     * Test extracting year from Instant via Qore.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testInstantYear(): Int {
        val dt = java.time.Instant.parse("2024-06-15T10:30:00Z")
        return (QoreJavaApi.callStaticMethod("DateTimeParamTest", "getYear", dt) as Number).toInt()
    }

    /**
     * Test Instant epoch handling.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testInstantEpoch(): Long {
        val dt = java.time.Instant.EPOCH  // 1970-01-01T00:00:00Z
        return (QoreJavaApi.callStaticMethod("DateTimeParamTest", "getEpochSeconds", dt) as Number).toLong()
    }

    /**
     * Test Instant with nanoseconds.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun testInstantWithNanos(): String {
        val dt = java.time.Instant.parse("2024-03-15T14:30:45.123456789Z")
        return QoreJavaApi.callStaticMethod("DateTimeParamTest", "formatDateWithMicros", dt) as String
    }
}
