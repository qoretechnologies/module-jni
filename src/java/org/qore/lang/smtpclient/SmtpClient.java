/** Java wrapper for the %Qore SmtpClient class
 *
 */
package org.qore.lang.smtpclient;

// java imports
import java.util.Map;
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreRelativeTime;

// qore imports
import org.qore.lang.mailmessage.Message;

//! Java wrapper for the @ref SmtpClient::SmtpClient class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.SmtpClient.SmtpClient;</tt>
*/
@Deprecated
public class SmtpClient extends QoreObjectWrapper {
    // static initialization
    static {
        // initialize the Qore library if necessary
        try {
            QoreJavaApi.initQore();
            QoreJavaApi.callFunction("load_module", "SmtpClient");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object
    public SmtpClient(QoreObject ds) {
        super(ds);
    }

    //! creates the SmtpClient object
    /** @param host the hostname of the SMTP server (use \c "[hostname]" to explicitly specify an ipv6 connection)
        @param port the port number of the SMTP server
    */
    public SmtpClient(String host, int port) throws Throwable {
        super(QoreJavaApi.newObjectSave("SmtpClient::SmtpClient", host, port));
    }

    //! creates the SmtpClient object
    /** @par Example:
        @code{.py}
SmtpClient smtp("smtptls://user@gmail.com:password@smtp.gmail.com", \log(), \log());
        @endcode

        @param url the URL of the SMTP server (use \c "[hostname]" or \c "[address]" for ipv6 connections); if no
        protocol (scheme) and no port is given for non-UNIX sockets, then @ref SmtpClient::SmtpClient::SmtpPort is used as the default port
        number.  This argument is parsed with parse_url(); see @ref smtpclient_protocols for a description of the
        handling of the protocol (scheme) component of the URL including default ports per protocol (scheme). If an
        unknown protocol (scheme) is given then a \c SMTP-UNKNOWN-PROTOCOL exception is raised

        @throw PARSE-URL-ERROR the \a url argument could not be parsed with parse_url()
        @throw SMTPCLIENT-UNKNOWN-PROTOCOL the protocol (scheme) given is unknown or unsupported
        @throw SMTPCLIENT-INVALID-AUTHENTICATION partial authentication credentials passed; the username or password
        is missing
    */
    public SmtpClient(String url) throws Throwable {
        super(QoreJavaApi.newObjectSave("SmtpClient::SmtpClient", url));
    }

    //! Returns the connection target string
    /** @since %SmtpClient 1.7
    */
    public String getTarget() throws Throwable {
        return (String)obj.callMethod("getTarget");
    }

    //! sets the TLS/SSL flag
    /** @param n_tls if @ref True "True" then use TLS/SSL; if the TLS/SSL flag is set then the client will issue a
        \c "STARTTLS" command after connecting and negotiate a secure TLS/SSL connection to the server; will also in
        this case turn off the SSL connection flag
     */
    public void tls(boolean n_tls) throws Throwable {
        obj.callMethod("tls", n_tls);
    }

    //! returns the TLS/SSL flag
    public boolean tls() throws Throwable {
        return (boolean)obj.callMethod("tls");
    }

    //! sets the SSL connection flag
    /** @param n_ssl if @ref True "True" then connections to the SMTP server will immediately try to negotiate
        transport layer TSL/SSL security; will also in this case turn off the TLS/SSL \c "STARTTLS" application layer
        security flag
     */
    public void ssl(boolean n_ssl) throws Throwable {
        obj.callMethod("ssl", n_ssl);
    }

    //! returns the SSL connection flag
    public boolean ssl() throws Throwable {
        return (boolean)obj.callMethod("ssl");
    }

    //! sets the username and password for authenticated connections
    /** @param n_user the username to set for authentication
        @param n_pass the password to set for authentication

        @note
        - Currently this class only knows how to do AUTH PLAIN authentication
        - This method is subject to thread serialization
    */
    public void setUserPass(String n_user, String n_pass) throws Throwable {
        obj.callMethod("setUserPass", n_user, n_pass);
    }

    //! sets or disables test mode; no connections are made in test mode
    public void test(boolean ns) throws Throwable {
        obj.callMethod("test", ns);
    }

    //! returns the test mode flag
    public boolean test() throws Throwable {
        return (boolean)obj.callMethod("test");
    }

    //! Connect to the server with the connection parameters set in the @ref SmtpClient(String)
    /** @note
        - For possible exceptions, see %Qore's @ref Qore::Socket::connect() "Socket::connect()" method
        - This method is subject to thread serialization
    */
    public void connect() throws Throwable {
        obj.callMethod("connect");
    }

    //! return connection status
    public boolean isConnected() throws Throwable {
        return (boolean)obj.callMethod("isConnected");
    }

    //! disconnect from the server
    /** @note This method is subject to thread serialization
     */
    public void disconnect() throws Throwable {
        obj.callMethod("disconnect");
    }

    //! sets the read timeout from an integer in milliseconds
    public void setReadTimeout(int to) throws Throwable {
        obj.callMethod("setReadTimeout", to);
    }

    //! sets the read timeout
    public void setReadTimeout(QoreRelativeTime to) throws Throwable {
        obj.callMethod("setReadTimeout", to);
    }

    //! returns the read timeout as an integer giving milliseconds
    public int getReadTimeoutMs() throws Throwable {
        return ((Long)obj.callMethod("getReadTimeoutMs")).intValue();
    }

    //! returns the read timeout as a relative time value
    public QoreRelativeTime getReadTimeoutDate() throws Throwable {
        return (QoreRelativeTime)obj.callMethod("getReadTimeoutDate");
    }

    //! sets the connect timeout from an integer in milliseconds
    public void setConnectTimeout(int to) throws Throwable {
        obj.callMethod("setConnectTimeout", to);
    }

    //! sets the connect timeout
    public void setConnectTimeout(QoreRelativeTime to) throws Throwable {
        obj.callMethod("setConnectTimeout", to);
    }

    //! returns the connect timeout as an integer giving milliseconds
    public int getConnectTimeoutMs() throws Throwable {
        return ((Long)obj.callMethod("getConnectTimeoutMs")).intValue();
    }

    //! returns the connect timeout as a relative time value
    public QoreRelativeTime getConnectTimeoutDate() throws Throwable {
        return (QoreRelativeTime)obj.callMethod("getConnectTimeoutDate");
    }

    //! send a Message to the server
    /** @param message the Message to send

        @return a hash of data returned by the SMTP server (the return structure hashes described below are made up of
        the following keys: \c "code": the return code, \c "desc": the string description):
        - \c HELO or \c EHLO: a hash of the reply received from the \c HELO or \c EHLO command
        - \c RCPT: hash keyed by email address with hash return structures values for the RCPT TO command
        - \c MSGID: return structure after the send; generally contains message id
        - \c QUIT: the server response of the disconnect command

        @note This method is subject to thread serialization

        @throw MESSAGE-ERROR the message is incomplete and cannot be sent
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> sendMessage(Message message) throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("sendMessage", message.getQoreObject());
    }

    //! force disconnect of socket without error
    /** @note This method is subject to thread serialization
     */
    public void forceDisconnect() throws Throwable {
        obj.callMethod("forceDisconnect");
    }

    //! Returns performance statistics for the socket
    /** @par Example:
        @code{.py}
HashMap<String, Object> h = smtp.getUsageInfo();
        @endcode

        @return a hash with the following keys:
        - \c "bytes_sent": an integer giving the total amount of bytes sent
        - \c "bytes_recv": an integer giving the total amount of bytes received
        - \c "us_sent": an integer giving the total number of microseconds spent sending data
        - \c "us_recv": an integer giving the total number of microseconds spent receiving data
        - \c "arg": the optional argument for warning hashes if applicable
        - \c "timeout": the warning timeout in microseconds if set
        - \c "min_throughput": the minimum warning throughput in bytes/sec if set

        @since %SmtpClient 1.3

        @see clearStats()
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getUsageInfo() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getUsageInfo");
    }

    //! Clears performance statistics
    /** @par Example:
        @code{.py}
smtp.clearStats();
        @endcode

        @since %SmtpClient 1.3

        @see getUsageInfo()
    */
    public void clearStats() throws Throwable {
        obj.callMethod("clearStats");
    }
}
