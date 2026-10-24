package org.qore.jni.test;

import java.lang.reflect.*;

import org.qore.jni.QoreClosure;
import org.qore.jni.QoreClosureMarker;
import org.qore.jni.QoreObjectBase;

public class Callbacks {
    // A Java-owned callback returned to Qore; its wrapper must retain the caller's provenance.
    public static QoreClosureMarker readingClosure(QoreClosure reader) {
        return new ReadingClosure(reader);
    }

    public static class ReadingClosure implements QoreClosureMarker {
        private final QoreClosure reader;

        public ReadingClosure(QoreClosure reader) {
            this.reader = reader;
        }

        public Object call() throws Throwable {
            return reader.call();
        }
    }

    public static void callNow(Runnable r) {
        r.run();
    }

    public static void callInThread(Runnable r) {
        new Thread(r).start();
    }

    // calls the closure in a new Java thread; the closure reports its result itself
    public static void callClosureInThread(QoreClosure c) {
        new Thread(() -> {
            try {
                c.call();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).start();
    }

    // calls the readFile() method of a Qore object with QoreObject.callMethod() in a new Java thread; the method is
    // looked up by reflection, as the class of the object can come from another class loader than this class's
    // QoreObject
    public static void callReadFileInThread(Object obj) {
        new Thread(() -> {
            try {
                obj.getClass().getMethod("callMethod", String.class, Object[].class).invoke(obj, "readFile",
                    new Object[0]);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).start();
    }

    // calls the readFile() method of the Java class of a Qore object by reflection in a new Java thread
    public static void invokeReadFileInThread(Object obj) {
        new Thread(() -> {
            try {
                obj.getClass().getMethod("readFile").invoke(obj);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).start();
    }

    // destroys a Qore object in a new Java thread
    public static void destroyInThread(QoreObjectBase obj) {
        new Thread(() -> obj.destroy()).start();
    }

    public static boolean trueIfThrows(Runnable r) {
        try {
            r.run();
            return false;
        } catch (MyException e) {
            return true;
        } catch (UndeclaredThrowableException e) {
            return ((InvocationTargetException)e.getCause()).getTargetException() instanceof MyException ? true : false;
        }
    }

    public static void doThrow() {
        throw new MyException();
    }

    public static String createString(StringFactory factory) {
        return "*" + factory.create() + "*";
    }

    private static class MyException extends RuntimeException {
    }
}
