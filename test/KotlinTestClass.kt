/*
    KotlinTestClass.kt

    Kotlin test class for JNI module Kotlin integration testing.

    Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
*/

package org.qore.jni.test

// Data class - generates component1(), component2(), copy(), etc.
data class KotlinDataClass(
    val name: String,
    val value: Int
)

// Class with companion object and default parameters
class KotlinTestClass(val id: Int) {
    // Method with default parameter - generates $default method
    fun greet(name: String = "World"): String {
        return "Hello, $name! ID: $id"
    }

    // Regular method
    fun add(a: Int, b: Int): Int {
        return a + b
    }

    // Companion object - generates $Companion inner class
    companion object {
        const val VERSION = "1.0"

        fun create(id: Int): KotlinTestClass {
            return KotlinTestClass(id)
        }
    }
}

// Simple class for basic testing
class SimpleKotlinClass {
    fun getMessage(): String {
        return "Hello from Kotlin!"
    }
}
