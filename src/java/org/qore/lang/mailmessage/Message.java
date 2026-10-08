/** Java wrapper for the %Qore Message class
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
import org.qore.lang.mailmessage.Attachment;

//! Java wrapper for the @ref MailMessage::Message class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.MailMessage.Message;</tt>
*/
@Deprecated
public class Message extends QoreObjectWrapper {
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
    public Message(QoreObject ds) {
        super(ds);
    }

    //! creates a Message object from the arguments given; this variant of the constructor is designed to be used to create a Message object for sending with the SmtpClient class
    /** Use the @ref addTO() method to add message recipients

        @param n_sender the sender's email address; can be in the format \c "Full Display Name <name@example.com>"
        @param n_subject the subject line for the email; the subject will be encoded with <a href="http://tools.ietf.org/html/rfc2047">"Q" encoding</a>

        @throw MESSAGE-CONSTRUCTOR-ERROR the sender's email address is not valid; the subject is empty
    */
    public Message(String n_sender, String n_subject) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Message", n_sender, n_subject));
    }

    //! creates a Message object from raw message text from a received email message
    /** @param msg the raw message text as received

        @throw MESSAGE-PARSE-ERROR invalid message data
    */
    public Message(String msg) throws Throwable {
        super(QoreJavaApi.newObjectSave("MailMessage::Message", msg));
    }

    //! serializes the message to a string that can be sent to an SMTP server, for example
    /**
        @throw MESSAGE-ERROR the message is incomplete and cannot be sent
    */
    public String serialize() throws Throwable {
        return (String)obj.callMethod("serialize");
    }

    //! add a recipient to the Message's recipient list
    /** @param recipient the email address to send the message to
        @return the list of "To:" addresses

        @throw MESSAGE-ADDTO-ERROR the recipient's email address is invalid
    */
    public String[] addTO(String recipient) throws Throwable {
        return (String[])obj.callMethod("addTO", recipient);
    }

    //! add a recipient to the Message's cc list
    /** @param recipient the email address to send the message to (cc)
        @return the list of "CC:" addresses

        @throw MESSAGE-ADDCC-ERROR the recipient's email address is invalid
    */
    public String[] addCC(String recipient) throws Throwable {
        return (String[])obj.callMethod("addCC", recipient);
    }

    //! add a recipient to the Message's bcc list
    /** @param recipient the email address to send the message to (bcc)
        @return the list of "BCC:" addresses

        @throw MESSAGE-ADDBCC-ERROR the recipient's email address is invalid
    */
    public String[] addBCC(String recipient) throws Throwable {
        return (String[])obj.callMethod("addBCC", recipient);
    }

    //! returns the sender's address in display format
    public String getSender() throws Throwable {
        return (String)obj.callMethod("getSender");
    }

    //! returns the sender's email address
    public String getFrom() throws Throwable {
        return (String)obj.callMethod("getFrom");
    }

    //! returns the list of "To:" addresses
    public String[] getTO() throws Throwable {
        return (String[])obj.callMethod("getTO");
    }

    //! returns the list of "CC:" addresses
    public String[] getCC() throws Throwable {
        return (String[])obj.callMethod("getCC");
    }

    //! returns the list of "BCC:" addresses
    public String[] getBCC() throws Throwable {
        return (String[])obj.callMethod("getBCC");
    }

    //! returns the subject of the Message
    public String getSubject() throws Throwable {
        return (String)obj.callMethod("getSubject");
    }

    //! return all the email addresses the message will be sent to, a combination of the "To:", "CC:", and "BCC:" lists
    public String[] getRecipients() throws Throwable {
        return (String[])obj.callMethod("getRecipients");
    }

    //! returns @ref True "True" if the message can be sent, @ref False "False" if not
    /** To be able to send a message, there must be at least one recipient in the "To:" or the "CC:" or the "BCC:" lists
        and there must be a message body

        @return @ref True "True" if the message can be sent, @ref False "False" if not

        @see checkSendPossible()
    */
    public boolean sendPossible() throws Throwable {
        return (boolean)obj.callMethod("sendPossible");
    }

    //! throws a \c MESSAGE-ERROR exception if the Message cannot be sent
    /** To be able to send a message, there must be at least one recipient in the "To:" or the "CC:" or the "BCC:" lists
        and there must be a message body

        @see sendPossible()

        @throw MESSAGE-ERROR the message is incomplete and cannot be sent
    */
    public void checkSendPossible() throws Throwable {
        obj.callMethod("checkSendPossible");
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set
        @param n_enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param n_content_type the Content-Type for the message body, if any

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(String n_body, String n_enc, String n_content_type) throws Throwable {
        obj.callMethod("setBody", n_body, n_enc, n_content_type);
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set
        @param n_enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(String n_body, String n_enc) throws Throwable {
        obj.callMethod("setBody", n_body, n_enc);
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(String n_body) throws Throwable {
        obj.callMethod("setBody", n_body);
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set
        @param n_enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param n_content_type the Content-Type for the message body, if any

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(byte[] n_body, String n_enc, String n_content_type) throws Throwable {
        obj.callMethod("setBody", n_body, n_enc, n_content_type);
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set
        @param n_enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(byte[] n_body, String n_enc) throws Throwable {
        obj.callMethod("setBody", n_body, n_enc);
    }

    //! sets or replaces the Message body
    /** @param n_body the Message body to set

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
    */
    public void setBody(byte[] n_body) throws Throwable {
        obj.callMethod("setBody", n_body);
    }

    //! concatenates a string to the message body
    /** @param str the string to concatenate to the Message body

        @throw BODY-ERROR cannot concatenate a string to a binary body
    */
    public void addBody(String str) throws Throwable {
        obj.callMethod("addBody", str);
    }

    //! concatenates a binary object to the message body
    /** @param bin the binary object to concatenate to the Message body

        @throw BODY-ERROR cannot concatenate a binary object to a string body
    */
    public void addBody(byte[] bin) throws Throwable {
        obj.callMethod("addBody", bin);
    }

    //! returns the Message body
    public Object getBody() throws Throwable {
        return obj.callMethod("getBody");
    }

    //! returns the transfer encoding for the mssage body (see @ref MessageEncodings for possible values)
    public String getBodyTransferEncoding() throws Throwable {
        return (String)obj.callMethod("getBodyTransferEncoding");
    }

    //! sets/replaces the Message headers
    /** @param hdr a single string giving a single Message header to set (replaces all message headers with the given string)

        @throw HEADER-ERROR no ':' separator character found in header string

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void setHeader(String hdr) throws Throwable {
        obj.callMethod("setHeader", hdr);
    }

    //! sets/replaces the list of Message headers from a list of header strings
    /** @param hdrs a list of Message header strings to set (replaces all message headers with the given list of strings)

        @throw HEADER-ERROR no ':' separator character found in header string

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void setHeader(String[] hdrs) throws Throwable {
        obj.callMethod("setHeader", (Object)hdrs);
    }

    //! sets/replaces the list of Message headers from a hash of header info
    /** @param hdrs a hash of Message headers to set (replaces all message headers with the given hash)

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void setHeader(Map<String, String> hdrs) throws Throwable {
        obj.callMethod("setHeader", hdrs);
    }

    //! adds a header to the Message
    /** @param hdr a header to add to the Message

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void addHeader(String hdr) throws Throwable {
        obj.callMethod("addHeader", hdr);
    }

    //! adds a list of headers to the Message
    /** @param hdrs a list of headers to add to the Message

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void addHeader(String[] hdrs) throws Throwable {
        obj.callMethod("addHeader", (Object)hdrs);
    }

    //! adds a hash of headers to the Message
    /** @param hdrs a hash of headers to add to the Message

        @note headers for importance, delivery receipt and read receipt will be added automatically and should not be included here
    */
    public void addHeader(Map<String, String> hdrs) throws Throwable {
        obj.callMethod("addHeader", hdrs);
    }

    //! returns the current Message headers as a list of strings
    public String[] getHeader() throws Throwable {
        return (String[])obj.callMethod("getHeader");
    }

    //! returns the current Message headers as a hash
    @SuppressWarnings("unchecked")
    public Map<String, String> getHeaders() throws Throwable {
        return (Map<String, String>)obj.callMethod("getHeaders");
    }

    //! returns the current importance setting
    /** if this is @ref True "True", then the following headers will be sent:
        - \c Importance: high
        - \c X-Priority: 1
        - \c Priority: Urgent
    */
    public boolean important() throws Throwable {
        return (boolean)obj.callMethod("important");
    }

    //! sets the importance setting
    /** if this is @ref True "True", then the following headers will be sent:
        - \c Importance: high
        - \c X-Priority: 1
        - \c Priority: Urgent
    */
    public void important(boolean i) throws Throwable {
        obj.callMethod("important", i);
    }

    //! returns the current read delivery receipt setting
    /** if this is @ref True "True", then the following header will be sent:
        - \c Return-Receipt-To: <sender's email address>
    */
    public boolean receiptRead() throws Throwable {
        return (boolean)obj.callMethod("receiptRead");
    }

    //! sets the read delivery receipt setting
    /** if this is @ref True "True", then the following header will be sent:
        - \c Return-Receipt-To: <sender's email address>
    */
    public void receiptRead(boolean arg) throws Throwable {
        obj.callMethod("receiptRead", arg);
    }

    //! returns the delivery receipt setting
    /** if this is @ref True "True", then the following header will be sent:
        - \c Disposition-Notification-To: <sender's email address>
    */
    public boolean receiptDelivery() throws Throwable {
        return (boolean)obj.callMethod("receiptDelivery");
    }

    //! sets the delivery receipt setting
    /** if this is @ref True "True", then the following header will be sent:
        - \c Disposition-Notification-To: <sender's email address>
    */
    public void receiptDelivery(boolean arg) throws Throwable {
        obj.callMethod("receiptDelivery", arg);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param hdr optional headers for the MIME part for the attachment

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, String att, String enc, Map<String, String> hdr) throws Throwable {
        obj.callMethod("attach", name, mime, att, enc, hdr);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, String att, String enc) throws Throwable {
        obj.callMethod("attach", name, mime, att, enc);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, String att) throws Throwable {
        obj.callMethod("attach", name, mime, att);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)
        @param hdr optional headers for the MIME part for the attachment

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, byte[] att, String enc, Map<String, String> hdr) throws Throwable {
        obj.callMethod("attach", name, mime, att, enc, hdr);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself
        @param enc the encoding type for the message (see @ref MessageEncodings for possible values)

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, byte[] att, String enc) throws Throwable {
        obj.callMethod("attach", name, mime, att, enc);
    }

    //! creates an attachment for the Message
    /** @param name the name of the attachment to be displayed in the message, normally a file name without any path
        @param mime the mime type for the message
        @param att the attachment itself

        @throw UNKNOWN-ENCODING the message encoding value passed is unknown/unimplemented
        @throw INVALID-ENCODING the encoding given cannot be used with data given
    */
    public void attach(String name, String mime, byte[] att) throws Throwable {
        obj.callMethod("attach", name, mime, att);
    }

    //! adds an Attachment to the Message
    /** @param att the Attachment to add to the Message
     */
    public void attach(Attachment att) throws Throwable {
        obj.callMethod("attach", att);
    }

    //! returns a list of Attachment objects for the Message
    public Attachment[] getAttachments() throws Throwable {
        return (Attachment[])obj.callMethod("getAttachments");
    }

    //! returns a list of non-attachment Part objects for the Message
    /** these could represent alternative representations of the message body, for example
     */
    public Part[] getParts() throws Throwable {
        return (Part[])obj.callMethod("getParts");
    }

    //! returns a string of the message headers
    /** @param eol the End-Of-Line marker for the string
        @param encode encode non-ASCII values with quoted printable encoding (http://tools.ietf.org/html/rfc2047)

        @return a string with all the message headers separated by the EOL marker
    */
    public String getHeaderString(String eol, boolean encode) throws Throwable {
        return (String)obj.callMethod("getHeaderString", eol, encode);
    }

    //! returns a string of the message headers
    /** @param eol the End-Of-Line marker for the string

        @return a string with all the message headers separated by the EOL marker
    */
    public String getHeaderString(String eol) throws Throwable {
        return (String)obj.callMethod("getHeaderString", eol);
    }

    //! returns a string of the message headers
    /** @return a string with all the message headers separated by the EOL marker
    */
    public String getHeaderString() throws Throwable {
        return (String)obj.callMethod("getHeaderString");
    }

    //! returns a multi-line string representing the Message
    /** @param include_body if @ref True "True" the Message body will be included in the string output
     */
    public String toString(boolean include_body) throws Throwable {
        return (String)obj.callMethod("toString", include_body);
    }

    //! returns a single line string summarizing the Message
    /** @return a single line string summarizing the Message
    */
    public String toLine() throws Throwable {
        return (String)obj.callMethod("toLine");
    }
}

