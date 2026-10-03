package org.qore.jni;

import org.qore.jni.QoreObjectBase;
import org.qore.jni.QoreURLClassLoader;

//! wrapper class for a %Qore object; this class holds a weak reference to the %Qore object
/** Due to the different in garbage collecting approaches (%Qore's garbage collector being
    <a href="https://github.com/qorelanguage/qore/wiki/Prompt-Collection">deterministic</a> and Java's not),
    strong references to %Qore objects must be managed outside of Java.

    @note API usage errors such as with releasing / deleting the object and then calling methods
    on the object will cause a crash
 */
public class QoreObject extends QoreObjectBase {
    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObject(long obj) {
        super(obj);
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreObject(long qcptr, long mptr, long vptr, Object... args) throws Throwable {
        super(qcptr, mptr, vptr, args);
    }

    //! returns the class name for the object
    public String className() {
        return className0(obj);
    }

    //! returns true if the object is an instance of the given class
    public boolean instanceOf(String class_name) {
        return instanceOf0(obj, class_name);
    }

    //! calls the given method with the given arguments and returns the result
    /**
     * @param name the name of the method to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see callMethodSave()
    */
    public Object callMethod(String name, Object... args) throws Throwable {
        return callMethod0(QoreURLClassLoader.getProgramPtr(), obj, name, args);
    }

    //! calls the given method with the given arguments and returns the result
    /**
     * @param name the name of the method to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see callMethodArgsSave()
    */
    public Object callMethodArgs(String name, Object[] args) throws Throwable {
        return callMethod0(QoreURLClassLoader.getProgramPtr(), obj, name, args);
    }

    //! Calls the given method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param name the name of the method to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public Object callMethodSave(String name, Object... args) throws Throwable {
        return callMethodSave0(QoreURLClassLoader.getProgramPtr(), obj, name, args);
    }

    //! Calls the given method with the given arguments and returns the result; if an object is returned, then a strong reference to the object is stored in thread-local data
    /**
     * This method can be used to save objects in thread-local data that would otherwise go out of scope; see
     * @ref jni_qore_object_lifecycle_managementguide for more information
     *
     * @param name the name of the method to call
     * @param args argument to the function call
     * @return the result of the call
     * @throws Throwable any Qore-language exception is rethrown here
     *
     * @see @ref jni_qore_object_lifecycle_managementguide
     */
    public Object callMethodArgsSave(String name, Object[] args) throws Throwable {
        return callMethodSave0(QoreURLClassLoader.getProgramPtr(), obj, name, args);
    }

    //! returns the value of the given member
    public Object getMemberValue(String name) throws Throwable {
        return getMemberValue0(obj, name);
    }

    private native String className0(long obj_ptr);
    private native boolean instanceOf0(long obj_ptr, String class_name);
    private native Object callMethod0(long pgm_ptr, long obj_ptr, String name, Object... args);
    private native Object callMethodSave0(long pgm_ptr, long obj_ptr, String name, Object... args);
    private native Object getMemberValue0(long obj_ptr, String name);
}
