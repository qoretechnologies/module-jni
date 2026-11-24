package org.qore.jni;

//! marker base class for a %Qore classes imported dynamically into Java
/**
    @since 2.0
 */
public abstract class QoreJavaClassBase extends QoreObject {
    //! Default constructor
    public QoreJavaClassBase(long cptr, long mptr, long vptr, Object... args) throws Throwable {
        super(cptr, mptr, vptr, args);
    }

    //! creates the wrapper object with a pointer to an object; this Java object holds a weak reference to the Qore object passed here
    public QoreJavaClassBase(QoreJavaObjectPtr obj) {
        super(obj);
    }
}
