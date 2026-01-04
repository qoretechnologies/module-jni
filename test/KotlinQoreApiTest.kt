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
        return QoreJavaApi.callFunction("get_qore_library_info") as HashMap<*, *>
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
}
