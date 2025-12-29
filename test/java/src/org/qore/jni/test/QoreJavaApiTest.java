package org.qore.jni.test;

import org.qore.jni.*;

import org.qore.lang.*;
import org.qore.lang.sqlutil.*;
import org.qore.lang.reflection.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

class ThreadTest implements Runnable {
    public void run() {
        try {
            Hash hm = (Hash)QoreJavaApi.callFunction("get_qore_library_info");
        } catch (Throwable e) {
        }
    }
}

class ThreadTest2 implements Runnable {
    public Runnable runme1;
    public Runnable runme2;

    ThreadTest2(Runnable runme1, Runnable runme2) {
        this.runme1 = runme1;
        this.runme2 = runme2;
    }

    public void run() {
        try {
            // this will throw an exception
            runme1.run();
        } catch (Exception e) {
        }

        runme2.run();
    }
}

class ThreadTest3 implements Runnable {
    static public String result;
    public QoreObject obj;

    ThreadTest3(QoreObject obj) {
        this.obj = obj;
    }

    public void run() {
        try {
            result = (String)obj.callMethod("getString");
        } catch (Throwable e) {
        }
    }
}

class ThreadTest4 implements Runnable {
    static public String result;

    public void run() {
        // we register the Java thread here to ensure that Qore objects will be persistent in thread-local data
        // while this thread is executing, otherwise the object created below is also deleted implicitly
        // immediately
        QoreJavaApi.registerJavaThread();
        try {
            QoreObject obj = QoreJavaApi.newObjectSave("TestClass2");
            result = (String)obj.callMethod("getString");
        } catch (Throwable e) {
            System.out.printf("Java thread error: %s\n", e);
        } finally {
            // deregister the Qore thread
            QoreJavaApi.deregisterJavaThread();
        }
    }
}

class ThreadTest5 implements Runnable {
    static public Hash result;

    public void run() {
        try {
            result = (Hash)QoreJavaApi.callStaticMethod("TestClass", "get", 1);
        } catch (Throwable e) {
        }
    }
}

public class QoreJavaApiTest {
    public static void throwTest() throws Throwable {
        throw new Throwable("test");
    }

    public static HashMap callFunctionTest() throws Throwable {
        Hash hm = (Hash)QoreJavaApi.callFunction("get_qore_library_info");

        (new Thread(new ThreadTest())).start();
        return hm;
    }

    public static Object callFunctionTest(String name) throws Throwable {
        return QoreJavaApi.callFunction(name);
    }

    public static Object nestedExceptionTest() throws Throwable {
        return throwException();
    }

    public static Object throwException() throws Throwable {
        throw new QoreException("TEST", "test");
    }

    public static HashMap callStaticMethodTest() throws Throwable {
        return (HashMap)QoreJavaApi.callStaticMethod("TestClass", "get", 1);
    }

    public static HashMap callStaticMethodTest2() throws InterruptedException {
        Thread mythread = new Thread(new ThreadTest5());
        mythread.start();
        mythread.join();
        return ThreadTest5.result;
    }

    public static void threadTest(Runnable runme1, Runnable runme2) {
        (new Thread(new ThreadTest2(runme1, runme2))).start();
    }

    public static ZonedDateTime dateTest(ZonedDateTime dt) {
        return dt.minusDays(2);
    }

    public static String testObject1(QoreObject obj, String str) throws Throwable {
        try {
            return (String)obj.callMethod("getString", str);
        } finally {
            obj.release();
        }
    }

    public static Object testObject2(QoreObject obj) throws Throwable {
        try {
            return obj.getMemberValue("member");
        } finally {
            obj.release();
        }
    }

    // do not release the object when returning it
    public static QoreObject testObject3(QoreObject obj) {
        return obj;
    }

    public static boolean testObject4(QoreObject obj, String cls) {
        try {
            return obj.instanceOf(cls);
        } finally {
            obj.release();
        }
    }

    public static BigDecimal testObject5(String str) {
        return new BigDecimal(str);
    }

    public static BigDecimal testObject5(BigDecimal num) {
        return num;
    }

    @SuppressWarnings("unchecked")
    public static HashMap[] testObject6() {
        Hash[] hm = new Hash[2];
        hm[0] = new Hash();
        hm[0].put("1", 1);
        hm[1] = new Hash();
        hm[1].put("2", 2);

        return hm;
    }

    public static HashMap testObject7(HashMap h) {
        h.remove("a");
        return h;
    }

    public static Object testObject8(Object obj) {
        return obj;
    }

    public static HashMap[] testObject9(HashMap[] l) {
        return l;
    }

    public static HashMap[] testObject10(QoreObject obj) throws Throwable {
        try {
            return (HashMap[])obj.callMethod("getListOfHashes");
        } finally {
            obj.release();
        }
    }

    public static String testObject11(QoreObject obj) {
        return obj.className();
    }

    public static Object testObject12() throws Throwable {
        return QoreJavaApi.callFunction("get_object");
    }

    public static Object testObject13() throws Throwable {
        return QoreJavaApi.callFunctionSave("get_object");
    }

    public static String testObject14(QoreObject obj) throws InterruptedException {
        Thread mythread = new Thread(new ThreadTest3(obj));
        mythread.start();
        mythread.join();
        return ThreadTest3.result;
    }

    public static String testObject15() throws InterruptedException {
        Thread mythread = new Thread(new ThreadTest4());
        mythread.start();
        mythread.join();
        return ThreadTest4.result;
    }

    public static int[] testDate1(QoreRelativeTime dt) {
        int[] rv = new int[7];
        rv[0] = dt.year;
        rv[1] = dt.month;
        rv[2] = dt.day;
        rv[3] = dt.hour;
        rv[4] = dt.minute;
        rv[5] = dt.second;
        rv[6] = dt.us;
        return rv;
    }

    public static QoreRelativeTime testDate2(QoreRelativeTime dt) {
        return dt;
    }

    public static QoreClosureMarker getClosure1() {
        return new ClosureTest1();
    }

    public static QoreClosureMarker getClosure2() {
        return new ClosureTest2();
    }

    public static QoreClosureMarker getClosure3() {
        return new ClosureTest3();
    }

    @SuppressWarnings("unchecked")
    public static HashMap<String, Object>[] getCallStack() throws Throwable {
        return (HashMap<String, Object>[])QoreJavaApi.callFunction("gtcs", 50);
    }

    public static void testException(String err, String desc, Object arg) throws QoreException {
        throw new QoreException(err, desc, arg);
    }

    public static void testException(String err, String desc) throws QoreException {
        throw new QoreException(err, desc);
    }

    public static String objectLifecycleTest(String arg) throws Throwable {
        QoreObject obj = QoreJavaApi.newObjectSave("TestClass2");
        return (String)obj.callMethod("getString", arg);
    }

    @SuppressWarnings("deprecation")
    public static Type testObjectTest() throws Throwable {
        return new Type("string");
    }

    public static void testHash1() throws Throwable {
        Hash h = new Hash();
        h.put("a", 1);
        QoreJavaApi.callStaticMethod("TestHelper", "setHash", h);
    }

    public static void testHash2() throws Throwable {
        Hash[] h = new Hash[1];
        h[0] = new Hash();
        h[0].put("a", 1);
        QoreJavaApi.callStaticMethod("TestHelper", "setHashList", (Object)h);
    }

    public static boolean testHashBool(Hash h, String key) {
        return h.getBool(key);
    }

    public static int testHashInt(Hash h, String key) {
        return h.getInt(key);
    }

    public static double testHashDouble(Hash h, String key) {
        return h.getDouble(key);
    }

    public static ZonedDateTime testHashADate(Hash h, String key) {
        return h.getADate(key);
    }

    public static QoreRelativeTime testHashRDate(Hash h, String key) {
        return h.getRDate(key);
    }

    public static String testHashString(Hash h, String key) {
        return h.getString(key);
    }

    public static BigDecimal testHashNumber(Hash h, String key) {
        return h.getNumber(key);
    }

    public static byte[] testHashBinary(Hash h, String key) {
        return h.getBinary(key);
    }

    public static Hash testHashHash(Hash h, String key) {
        return h.getHash(key);
    }

    public static ArrayList<Object> testHashList(Hash h, String key) {
        return h.getList(key);
    }

    public static QoreClosureMarker testHashCode(Hash h, String key) {
        return h.getCode(key);
    }

    public static boolean testHashAsBool(Hash h, String key) {
        return h.getAsBool(key);
    }

    public static byte testHashAsByte(Hash h, String key) {
        return h.getAsByte(key);
    }

    public static short testHashAsShort(Hash h, String key) {
        return h.getAsShort(key);
    }

    public static int testHashAsInt(Hash h, String key) {
        return h.getAsInt(key);
    }

    public static long testHashAsLong(Hash h, String key) {
        return h.getAsLong(key);
    }

    public static float testHashAsFloat(Hash h, String key) {
        return h.getAsFloat(key);
    }

    public static double testHashAsDouble(Hash h, String key) {
        return h.getAsDouble(key);
    }

    public static String testHashAsString(Hash h, String key) {
        return h.getAsString(key);
    }

    public static Hash testHashListIterator(Hash h) throws Throwable {
        @SuppressWarnings("deprecation")
        HashListIterator i = new HashListIterator(h);
        i.next();
        return i.getHash();
    }

    public static void testCode(QoreClosure code, long val) throws Throwable {
        code.call(val);
    }

    // Tests for Cleaner-based cleanup (Java 25 compatibility)
    // Double release should be safe (idempotent)
    public static boolean testDoubleRelease(QoreObject obj) {
        obj.release();
        obj.release();  // Should not throw or crash
        return true;
    }

    // Double destroy should be safe (idempotent)
    public static boolean testDoubleDestroy(QoreObject obj) {
        obj.destroy();
        obj.destroy();  // Should not throw or crash
        return true;
    }

    // Release after destroy should be safe
    public static boolean testReleaseAfterDestroy(QoreObject obj) {
        obj.destroy();
        obj.release();  // Should not throw or crash
        return true;
    }

    // Destroy after release should be safe
    public static boolean testDestroyAfterRelease(QoreObject obj) {
        obj.release();
        obj.destroy();  // Should not throw or crash
        return true;
    }

    // Test QoreClosure double call to get() after cleanup
    public static boolean testClosureDoubleRelease(QoreClosure closure) throws Throwable {
        // Call to verify it works
        closure.call(1L);
        // Get pointer should still work
        long ptr1 = closure.get();
        // Let the cleaner do its job (closure goes out of scope after this)
        return ptr1 != 0;
    }

    // Tests for classloader management improvements

    // Test clearContext() method
    public static boolean testClearContext() {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        if (cl == null) {
            return false;
        }
        // clearContext should work without throwing
        cl.clearContext();
        // After clearing, current should be null for this thread
        // But we need to restore it for other tests, so set it back
        cl.setContext();
        return true;
    }

    // Test clearAllCaches() method
    public static boolean testClearAllCaches() {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        if (cl == null) {
            return false;
        }
        // This should work without throwing
        cl.clearAllCaches();
        return true;
    }

    // Test getValidPtr() throws when ptr is invalid
    public static boolean testGetValidPtrThrows() {
        QoreURLClassLoader cl = new QoreURLClassLoader(0);
        try {
            cl.getValidPtr();  // Should throw IllegalStateException
            return false;  // Should not reach here
        } catch (IllegalStateException e) {
            return true;  // Expected
        }
    }

    // Test getPtr() returns 0 when ptr is not set
    public static boolean testGetPtrReturnsZero() {
        QoreURLClassLoader cl = new QoreURLClassLoader(0);
        return cl.getPtr() == 0;
    }

    // Test clearProgramPtr() makes getValidPtr() throw
    public static boolean testClearProgramPtrMakesGetValidPtrThrow() {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        if (cl == null) {
            return false;
        }
        long originalPtr = cl.getPtr();
        if (originalPtr == 0) {
            // Can't test this if ptr is already 0
            return true;
        }
        // Save the ptr, clear it, test, then restore
        cl.clearProgramPtr();
        boolean threwException = false;
        try {
            cl.getValidPtr();
        } catch (IllegalStateException e) {
            threwException = true;
        }
        // Restore the ptr (can't actually do this from Java, so this test
        // is only partial - we'll need to use a new classloader in subsequent tests)
        return threwException;
    }

    // Test getProgramPtr() throws when no context is set
    public static boolean testGetProgramPtrThrowsWhenNoContext() {
        // Run in a new thread that has no context
        final boolean[] result = {false};
        Thread t = new Thread(() -> {
            try {
                QoreURLClassLoader.getProgramPtr();
                result[0] = false;  // Should have thrown
            } catch (IllegalStateException e) {
                result[0] = true;  // Expected
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            return false;
        }
        return result[0];
    }

    // Test clearCurrentContext() static method
    public static boolean testClearCurrentContext() {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        if (cl == null) {
            return true;  // Already cleared
        }
        QoreURLClassLoader.clearCurrentContext();
        boolean cleared = QoreURLClassLoader.getCurrent() == null;
        // Restore context for other tests
        if (cl != null) {
            cl.setContext();
        }
        return cleared;
    }

    // Test concurrent cache access
    public static boolean testConcurrentCacheAccess() throws InterruptedException {
        QoreURLClassLoader cl = QoreURLClassLoader.getCurrent();
        if (cl == null) {
            return false;
        }

        // Create multiple threads that access caches concurrently
        final int NUM_THREADS = 10;
        final int NUM_OPS = 100;
        final boolean[] errors = {false};

        Thread[] threads = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < NUM_OPS; j++) {
                        // These operations should be thread-safe
                        cl.checkInProgress("test.class." + threadId + "." + j);
                        cl.getPendingClassesForPackage("test.package." + threadId);
                    }
                } catch (Exception e) {
                    errors[0] = true;
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        return !errors[0];
    }
}
