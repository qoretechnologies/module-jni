package org.qore.jni;

//! Helper class for pointer arguments to weak references Qore objects
/** This class is used as the constructor argument in order to not conflict with native arguments taking a Qore "int"

    @since 2.0
 */
public class QoreJavaObjectPtr {
    //! the opaque handle that the module issued
    final long ptr;

    //! created by the module only
    QoreJavaObjectPtr(long ptr) {
        this.ptr = ptr;
    }
}
