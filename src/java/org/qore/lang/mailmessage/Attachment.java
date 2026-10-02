/** Java wrapper for the %Qore Attachment class
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

// qore imports
import org.qore.lang.mailmessage.Part;

//! Java wrapper for the @ref MailMessage::Attachment class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.MailMessage.Attachment;</tt>
*/
@Deprecated
public class Attachment extends Part {
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
    public Attachment(QoreObject ds) {
        super(ds);
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param hdr optional headers for the MIME part for the attachment

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, String data, String enc, Map<String, String> hdr) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data, enc, hdr));
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, String data, String enc) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data, enc));
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, String data) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data));
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param hdr optional headers for the MIME part for the attachment

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, byte[] data, String enc, Map<String, String> hdr) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data, enc, hdr));
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, byte[] data, String enc) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data, enc));
    }

    //! creates an Attachment object for a Message object
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param data the attachment data itself

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
     */
    public Attachment(String name, String mime, byte[] data) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Attachment", name, mime, data));
    }
}
