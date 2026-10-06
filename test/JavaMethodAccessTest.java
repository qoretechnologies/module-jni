/* Copyright 2026 Qore Technologies, s.r.o. */
import java.lang.reflect.Method;
import java.util.*;
import org.qore.jni.QoreJavaDynamicApi;

/** Standalone regression for the reflection bridge; no module opens are used. */
public class JavaMethodAccessTest {
    private static int checks;
    public interface Action {
        String apply(String value);
    }
    private static final class HiddenAction implements Action {
        int calls;
        public String apply(String value) {
            ++calls;
            if (value.equals("throw")) {
                throw new IllegalStateException("target failure");
            }
            return value + ":" + calls;
        }
        private String secret() { return "private accessible"; }
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Throwable {
        Method method = target.getClass().getMethod(name, types);
        return QoreJavaDynamicApi.invokeMethod(method, target, args);
    }
    private static void equal(Object expected, Object actual) {
        ++checks;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
    public static void main(String[] args) throws Throwable {
        Class<?>[] noArgs = new Class<?>[0];
        List<String> list = Collections.unmodifiableList(List.of("one", "two"));
        // Confirm the regression actually exercises an inaccessible implementation method.
        equal(false, list.getClass().getMethod("size").trySetAccessible());
        equal(2, invoke(list, "size", noArgs));
        equal("two", invoke(list, "get", new Class<?>[]{int.class}, 1));
        equal(true, invoke(list, "contains", new Class<?>[]{Object.class}, "one"));
        equal(0, invoke(Collections.emptyList(), "size", noArgs));
        equal(1, invoke(Collections.singletonList("one"), "size", noArgs));
        equal(2, invoke(Collections.unmodifiableSet(Set.of("one", "two")), "size", noArgs));
        equal(1, invoke(Map.of("one", "two"), "size", noArgs));
        equal("two", invoke(Collections.unmodifiableMap(Map.of("one", "two")), "get",
            new Class<?>[]{Object.class}, "one"));
        equal("one", invoke(list.iterator(), "next", noArgs));
        // Superclass declarations and normal static dispatch are preserved.
        equal("[one, two]", invoke(list, "toString", noArgs));
        equal(7, QoreJavaDynamicApi.invokeMethod(Integer.class.getMethod("parseInt", String.class), null, "7"));
        HiddenAction action = new HiddenAction();
        equal("value:1", invoke(action, "apply", new Class<?>[]{String.class}, "value"));
        equal("private accessible", QoreJavaDynamicApi.invokeMethod(HiddenAction.class.getDeclaredMethod("secret"), action));
        ClassLoader marker = new ClassLoader(Thread.currentThread().getContextClassLoader()) {};
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(marker);
        try {
            try {
                invoke(action, "apply", new Class<?>[]{String.class}, "throw");
                throw new AssertionError("Target exception missing");
            } catch (IllegalStateException e) {
                equal("target failure", e.getMessage());
            }
            equal(2, action.calls); // never retry an already invoked target
            equal(marker, Thread.currentThread().getContextClassLoader());
            try {
                QoreJavaDynamicApi.invokeMethod(String.class.getDeclaredMethod("coder"), "closed");
                throw new AssertionError("Private closed-module method was exposed");
            } catch (IllegalAccessException expected) {
                ++checks;
            }
            equal(marker, Thread.currentThread().getContextClassLoader());
            try {
                invoke(list, "get", new Class<?>[]{int.class}, 99);
                throw new AssertionError("Bounds failure missing");
            } catch (IndexOutOfBoundsException expected) {
                ++checks;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
        System.out.println(checks + " Java method-access assertions passed");
    }
}
