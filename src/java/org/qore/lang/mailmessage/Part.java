/** Java wrapper for the %Qore Part class
 *
 */
package org.qore.lang.mailmessage;

// java imports
import java.util.Map;
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreRelativeTime;

//! Java wrapper for the @ref MailMessage::Part class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.MailMessage.Part;</tt>
*/
@Deprecated
public class Part extends QoreObjectWrapper {
    // static initialization
    static {
        // initialize the Qore library if necessary
        try {
            QoreJavaApi.initQore();
            QoreJavaApi.callFunction("load_module", "MailMessage");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object
    public Part(QoreObject ds) {
        super(ds);
    }

    //! returns the name of the Part
    public String getName() throws Throwable {
        return (String)obj.callMethod("getName");
    }

    //! returns the mime type of the Part
    public String getMime() throws Throwable {
        return (String)obj.callMethod("getMime");
    }

    //! returns the data of the Part
    public Object getData() throws Throwable {
        return obj.callMethod("getData");
    }

    //! returns the transfer encoding of the Part
    public String getTransferEncoding() throws Throwable {
        return (String)obj.callMethod("getTransferEncoding");
    }

    //! returns any headers for the Part
    @SuppressWarnings("unchecked")
    public Map<String, String> getHeaders() throws Throwable {
        return (Map<String, String>)obj.callMethod("getHeaders");
    }
}
