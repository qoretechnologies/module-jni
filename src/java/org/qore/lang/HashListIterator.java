/** Java wrapper for the %Qore HashListIterator class
 *
 */
package org.qore.lang;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.Hash;

// qore imports
import org.qore.lang.AbstractIterator;

//! Java wrapper for the @ref Qore::HashListIterator class in Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qore.Qore.HashListIterator;</tt>
*/
@Deprecated
public class HashListIterator extends AbstractIterator {
    //! creates the object as a wrapper for the Qore object
    public HashListIterator(QoreObject obj) throws Throwable {
        super(obj);
    }

    //! creates the object as a wrapper for the Qore object
    public HashListIterator(Hash h) throws Throwable {
        super(QoreJavaApi.newObjectSave("HashListIterator", h));
    }

    //! Returns the current hash corresponding to the iterator position
    public Hash getHash() throws Throwable {
        return (Hash)getValue();
    }
}
