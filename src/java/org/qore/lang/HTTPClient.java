/** Java wrapper for the %Qore HTTPClient class
 *
 */
package org.qore.lang;

// java imports
import java.util.Map;
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;

//! Java wrapper for the @ref Qore::HTTPClient class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qore.Qore.HTTPClient;</tt>
*/
@Deprecated
public class HTTPClient extends QoreObjectWrapper {
    // static initialization
    static {
        // initialize the Qore library if necessary
        try {
            QoreJavaApi.initQore();
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object
    public HTTPClient(QoreObject ds) {
        super(ds);
    }

    //! Creates the HTTPClient object based on the option parameter passed
    /** To connect, call any method that requires a connection and an implicit connection is established, or call HTTPClient::connect().

        @par Example:
        @code{.java}
    HTTPClient httpclient = new HTTPClient(opts);
        @endcode

        @param opts sets options and changes default behaviour for the object, etc; key names are case-sensitive and therefore must all be in lower-case:
        - \c additional_methods: An optional hash defining additional HTTP methods to handle.  This allows the HTTPClient class to handle various HTTP extensions like e.g. WebDAV. The hash is defined with new method names as keys; the values are \c true or \c false indicating if the method can accept a message body; for example:
        @code{.java}
        # add two new HTTP methods for WebDAV; both require message bodies
        HTTPClient httpclient(("url": url, "additional_methods": ("PROPFIND": True, "MKCOL": True )));
        @endcode
        - \c connect_timeout: The timeout value in milliseconds for establishing a new socket connection (also can be a @ref relative_dates "relative date-time value" for clarity, ex: \c 30s)
        - \c default_path: The default path to use for new connections if a path is not otherwise specified in the connection URL
        - \c default_port: The default port number to connect to if none is given in the URL
        - \c http_version: Either \c "1.0" or \c "1.1" for the claimed HTTP protocol version compliancy in outgoing message headers
        - \c max_redirects: The maximum number of redirects before throwing an exception (the default is 5)
        - \c protocols: A hash describing new protocols, the key is the protocol name and the value is either an integer giving the default port number or a hash with \c "port" and \c "ssl" keys giving the default port number and a boolean value to indicate that an SSL connection should be established
        - \c proxy: The proxy URL for connecting through a proxy
        - \c ssl_cert_path: a path to an X.509 client certificate file in PEM format; if this option is used, then the calling context must not be restricted with sandbox restriction @ref Qore::PO_NO_FILESYSTEM which is checked at runtime
        - \c ssl_key_path: a path to a private key file in PEM format for the X.509 client certificate; if this option is used, then the calling context must not be restricted with sandbox restriction @ref Qore::PO_NO_FILESYSTEM which is checked at runtime
        - \c ssl_key_password: the password to the private key given with \c ssl_key_path
        - \c ssl_verify_cert: if \c true then the server's certificate will only be accepted if it's verified
        - \c timeout: The timeout value in milliseconds (also can be a @ref relative_dates "relative date-time value" for clarity, ex: \c 5m)
        - \c url: A string giving the URL to connect to

        @throw HTTP-CLIENT-OPTION-ERROR invalid or unknown option passed in option hash
        @throw HTTP-CLIENT-URL-ERROR invalid URL string
        @throw HTTP-CLIENT-UNKNOWN-PROTOCOL unknown protocol passed in URL
        @throw ILLEGAL-FILESYSTEM-ACCESS if the calling context is restricted with the @ref Qore::PO_NO_FILESYSTEM sandboxing restriction and one of the following options is used: \c ssl_cert_path or \c ssl_key_path

        @note
        - URLs with UNIX sockets are generally supported in Qore with the following syntax: <tt><b>scheme://socket=</b></tt><i>url_encoded_path</i><tt><b>/path</b></tt>, where <i>url_encoded_path</i> is a path with URL-encoding as performed by @ref Qore::encode_url() "encode_url(string, True)"; for example: \c "http://socket=%2ftmp%socket-dir%2fsocket-file-1/url/path"; this allows a filesystem path to be used in the host portion of the URL and for the URL to include a URL path as well.
        - other I/O errors can be thrown opening and reading the certificate and/or private key files if the \c ssl_cert_path or \c ssl_key_path options are used
        - the @ref Qore::PO_NO_FILESYSTEM sandbox restriction is checked at runtime if one of the following options is used: \c ssl_cert_path or \c ssl_key_path

        @since %Qore 0.8.13 added the following options:
        - \c ssl_cert_path
        - \c ssl_key_path
        - \c ssl_key_password
        - \c ssl_verify_cert
    */
    public HTTPClient(Map<String, Object> opts) throws Throwable {
        super(QoreJavaApi.newObjectSave("Qore::HTTPClient", opts));
    }

    //! Sets the HTTP protocol version string for headers in outgoing messages, allowed values are \c "1.0" and \"1.1\".
    /** @param ver \c "1.0" or \"1.1\" for the HTTP protocol compliance version

        @par Example:
        @code{.java}
    httpclient.setHTTPVersion("1.1");
        @endcode

        @throw HTTP-VERSION-ERROR invalid HTTP version passed (allowed values: \c "1.0" and \"1.1\")
    */
    public void setHTTPVersion(String ver) throws Throwable {
        obj.callMethod("setHTTPVersion", ver);
    }

    //! Returns the HTTP protocol version string used in outgoing messages
    /** @return the HTTP protocol version string used in outgoing messages

        @par Example:
        @code{.java}
    string version = httpclient.getHTTPVersion();
        @endcode
    */
    public String getHTTPVersion() throws Throwable {
        return (String)obj.callMethod("getHTTPVersion");
    }

    //! Sets the object to make a secure SSL/TLS connection on the next connect if the passed argument is true, or an unencrypted cleartext connection if it is false
    /** This method overrides the default behaviour for the protocol set for the object

        Note that the behavior of this method when called with no argument changed in version 0.8.0; prior to version 0.8.0 calling this method with no argument would turn off secure mode; the behavior was changed to the current functionality in order to make the usage of this method consistent with other methods of the same name and to make it more logical.

        @par Example:
        @code{.java}
    httpclient.setSecure(True);
        @endcode

        @param secure if true, a SSL/TLS connection will be attempted on the next connection. If false, an unencrypted cleartext connection will be established
    */
    public void setSecure(boolean secure) throws Throwable {
        obj.callMethod("setSecure", secure);
    }

    //! Returns true if the current connection is encrypted, false if not
    /** @return true if the current connection is encrypted, false if not

        @par Example:
        @code{.java}
    if (httpclient.isSecure())
        printf("secure connection: %s %s\n", httpclient.getSSLCipherName(), httpclient.getSSLCipherVersion());
        @endcode
    */
    public boolean isSecure() throws Throwable {
        return (boolean)obj.callMethod("isSecure");
    }

    //! Connects to the remote socket; SSL/TLS negotiation is performed if required
    /** If the protocol indicates that a secure connection should be established (or HTTPClient::setSecure() was called previsouly), SSL/TLS negotiation will be attempted.

        If the \c TCP_NODELAY flag has been set (see HTTPClient::setNoDelay()), then after a successful connection to the remote socket, this option will be set on the socket. If an error occurs setting the \c TCP_NODELAY option, the internal flag is set to false (use HTTPClient::getNoDelay() to check the flag's state) and the error code can be retrieved with errno().

        @par Example:
        @code{.java}
    httpclient.connect();
        @endcode

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED

        @note For possible exceptions, see the Socket::connect() method (or Socket::connectSSL() for secure connections).
    */
    public void connect() throws Throwable {
        obj.callMethod("connect");
    }

    //! Disconnects from the remote socket if a connection is established (otherwise does nothing)
    /**
         @par Example:
        @code{.java}
    httpclient.disconnect();
        @endcode
    */
    public void disconnect() throws Throwable {
        obj.callMethod("disconnect");
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search
        @param getbody If this argument is true, then the object will try to receive a message body even if no \c "Content-Length" header is present in the response. Use this only with broken servers that send message bodies without a \c "Content-Length" header.

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(String body, String method, String path, Map<String, Object> headers, boolean getbody) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path, headers, getbody);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(String body, String method, String path, Map<String, Object> headers) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path, headers);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(String body, String method, String path) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(String body, String method) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; pass null (no value) to send no body
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search
        @param getbody If this argument is true, then the object will try to receive a message body even if no \c "Content-Length" header is present in the response. Use this only with broken servers that send message bodies without a \c "Content-Length" header.

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(byte[] body, String method, String path, Map<String, Object> headers, boolean getbody) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path, headers, getbody);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; pass null (no value) to send no body
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(byte[] body, String method, String path, Map<String, Object> headers) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path, headers);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; pass null (no value) to send no body
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.
        @param path The path for the message (i.e. \c "/path/resource?method&param=value")

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(byte[] body, String method, String path) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method, path);
    }

    //! Sends an HTTP request with the specified method and optional message body and returns headers and any body received as a response in a hash format
    /** If a connection has not already been established, an internal call to HTTPClient::connect() will be made before sending the message

        @par Example:
        @code{.java}
    hash msg = httpclient.send(body, "POST", "/path", ("Content-Type":"application/x-yaml"));
        @endcode

        @param body The message body to send; pass null (no value) to send no body
        @param method The name of the HTTP method (\c "GET", \c "POST", \c "HEAD", \c "OPTIONS", \c "PUT", \c "DELETE", \c "TRACE", \c "CONNECT", or \c "PATCH"). Additional methods can be added in the constructor with the a \c additional_methods option.

        @return The headers received from the HTTP server with all key names converted to lower-case. The message body (if any) will be assigned to the value of the \c "body" key and the HTTP status will be assigned to the \c "status_code" key.

        @throw HTTP-CLIENT-METHOD-ERROR invalid/unknown HTTP method passed
        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the normal return value of this method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> send(byte[] body, String method) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("send", body, method);
    }

    //! Sends an HTTP \c GET request and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body, use the HTTPClient::send() method instead (although note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information).

        If no connection has already been established, an internal call to HTTPClient::connect() will be made before sending the request.

        If any content encoding is used for the message body in the reply, the content is decoded and returned as a string; if the content encoding uses an unknown method, then an exception is thrown.

        @par Example:
        @code{.java}
    *string html = httpclient.get("/path/file.html");
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return the message body in the reply to this message or null in case of an error or an erroneous reply by the server with no body

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    public String get(String path, Map<String, Object> headers) throws Throwable {
        return (String)obj.callMethod("get", path, headers);
    }

    //! Sends an HTTP \c GET request and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body, use the HTTPClient::send() method instead (although note that sending an HTTP message bosdy with a \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information).

        If no connection has already been established, an internal call to HTTPClient::connect() will be made before sending the request.

        If any content encoding is used for the message body in the reply, the content is decoded and returned as a string; if the content encoding uses an unknown method, then an exception is thrown.

        @par Example:
        @code{.java}
    *string html = httpclient.get("/path/file.html");
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")

        @return the message body in the reply to this message or null in case of an error or an erroneous reply by the server with no body

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)

        @see @ref httpclient_get_with_body
    */
    public String get(String path) throws Throwable {
        return (String)obj.callMethod("get", path);
    }

    //! Sends an HTTP \c HEAD request and returns as hash of the headers received
    /** If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.hava}
    String msg = httpclient.head("/path");
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return the headers received from the HTTP server with all key names converted to lower-case

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String head(String path, Map<String, Object> headers) throws Throwable {
        return (String)obj.callMethod("head", path, headers);
    }

    //! Sends an HTTP \c HEAD request and returns as hash of the headers received
    /** If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.hava}
    String msg = httpclient.head("/path");
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")

        @return the headers received from the HTTP server with all key names converted to lower-case

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String head(String path) throws Throwable {
        return (String)obj.callMethod("head", path);
    }

    //! Sends an HTTP \c POST request with a message body and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body in the response, use the HTTPClient::send() method instead.

        If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.java}
    String str = httpclient.post("/path", body);
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param body the string to use as the message body
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return the message body in the reply to this message or null in case no body was present in the response

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String post(String path, String body, Map<String, Object> headers) throws Throwable {
        return (String)obj.callMethod("post", path, body, headers);
    }

    //! Sends an HTTP \c POST request with a message body and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body in the response, use the HTTPClient::send() method instead.

        If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.java}
    String str = httpclient.post("/path", body);
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param body the string to use as the message body

        @return the message body in the reply to this message or null in case no body was present in the response

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String post(String path, String body) throws Throwable {
        return (String)obj.callMethod("post", path, body);
    }

    //! Sends an HTTP \c POST request without a message body and returns the response message body received as a string or null if no message body is received
    /** In order to get the headers and the body in the response, use the HTTPClient::send() method instead.

        If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.java}
    String str = httpclient.post("/path");
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")

        @return the message body in the reply to this message or null in case no body was present in the response

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw ENCODING-CONVERSION-ERROR the given string could not be converted to the socket's character encoding
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String post(String path) throws Throwable {
        return (String)obj.callMethod("post", path);
    }

    //! Sends an HTTP \c POST request with a message body and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body in the response, use the HTTPClient::send() method instead.

        If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.java}
    httpclient.post("/path", body);
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param body the optional data to use as the message body
        @param headers An optional hash of headers to include in the message; values are converted to strings; a list is converted to a string of comma-separated values; headers that differ only in case will be overwritten by the last header in the hash with a matching name with a case-insensitive search

        @return the message body in the reply to this message or null in case no body was present in the response

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String post(String path, byte[] body, Map<String, Object> headers) throws Throwable {
        return (String)obj.callMethod("post", path, body, headers);
    }

    //! Sends an HTTP \c POST request with a message body and returns the message body received as a string or null if no message body is received
    /** In order to get the headers and the body in the response, use the HTTPClient::send() method instead.

        If no connection is established, an internal call to HTTPClient::connect() will be made before sending the message.

        @par Example:
        @code{.java}
    httpclient.post("/path", body);
        @endcode

        @param path the path for the message (i.e. \c "/path/resource?method&param=value")
        @param body the optional data to use as the message body

        @return the message body in the reply to this message or null in case no body was present in the response

        @par Events:
        @ref EVENT_CONNECTING, @ref EVENT_CONNECTED, @ref EVENT_HOSTNAME_LOOKUP, @ref EVENT_HOSTNAME_RESOLVED, @ref EVENT_START_SSL, @ref EVENT_SSL_ESTABLISHED, @ref EVENT_HTTP_SEND_MESSAGE, @ref EVENT_PACKET_SENT, @ref EVENT_HTTP_MESSAGE_RECEIVED, @ref EVENT_PACKET_READ, @ref EVENT_HTTP_CONTENT_LENGTH, @ref EVENT_HTTP_CHUNKED_START, @ref EVENT_HTTP_CHUNKED_END, @ref EVENT_HTTP_CHUNKED_DATA_RECEIVED, @ref EVENT_HTTP_CHUNK_SIZE, @ref EVENT_HTTP_FOOTERS_RECEIVED, @ref EVENT_HTTP_REDIRECT

        @throw HTTP-CLIENT-REDIRECT-ERROR invalid redirect location given by remote
        @throw HTTP-CLIENT-MAXIMUM-REDIRECTS-EXCEEDED maximum redirect count exceeded
        @throw HTTP-CLIENT-RECEIVE-ERROR unknown content encoding received or status error communicating with HTTP server (status code < 100 or > 299); in case of a status error the \c "arg" key of the exception hash will be set to a hash equal to the return value of the send() method including a \c "status_code" key (giving the status code) and a \c "body" key (giving the message body returned by the server)
        @throw SOCKET-SEND-ERROR There was an error sending the data
        @throw SOCKET-CLOSED The remote end closed the connection
        @throw SOCKET-RECV-ERROR There was an error receiving the data
        @throw SOCKET-TIMEOUT Data transmission or reception for a single send() or recv() action exceeded the timeout period
        @throw SOCKET-SSL-ERROR There was an SSL error while reading data from the socket
        @throw SOCKET-HTTP-ERROR Invalid HTTP data was received

        @note For possible exceptions when implicitly establishing a connection, see the Socket::connect() method (or Socket::connectSSL() for secure connections)
    */
    public String post(String path, byte[] body) throws Throwable {
        return (String)obj.callMethod("post", path, body);
    }

    //! Sets the default I/O timeout value in milliseconds
    /** @param timeout_ms 0 means immediate timeout (when reading will return data only if it is already available), and negative numbers mean never timeout

        @par Example:
        @code{.java}
    httpclient.setTimeout(120000);
        @endcode
    */
    public void setTimeout(int timeout_ms) throws Throwable {
        obj.callMethod("setTimeout", timeout_ms);
    }

    //! Returns the default I/O timeout as an integer in milliseconds
    /** @return the default I/O timeout as an integer in milliseconds; 0 means immediate timeout (when reading only returns data if it is already available), and negative numbers mean never timeout

        @par Example:
        @code{.java}
    int timeout = httpclient.getTimeout();
        @endcode
    */
    public int getTimeout() throws Throwable {
        return ((Long)obj.callMethod("getTimeout")).intValue();
    }

    //! Sets the string encoding for the object; any strings deserialized with this object will be tagged with this character encoding
    /** @param encoding the string encoding for the object; any strings deserialized with this object will be tagged with this character encoding

        @par Example:
        @code{.java}
    httpclient.setEncoding("UTF-8");
        @endcode
    */
    public void setEncoding(String encoding) throws Throwable {
        obj.callMethod("setEncoding", encoding);
    }

    //! Returns the character encoding used for the object
    /** @return the character encoding used for the object

        @par Example:
        @code{.java}
    string encoding = httpclient.getEncoding();
        @endcode
    */
    public String getEncoding() throws Throwable {
        return (String)obj.callMethod("getEncoding");
    }

    //! Sets a new URL value for the next connection
    /** To retrieve the current URL value, use the HTTPClient::getURL() method

        @par Example:
        @code{.java}
    httpclient.setURL("https://user:password@hostname:8080/path");
        @endcode

        @param url the new URL for the object

        @throw HTTP-CLIENT-URL-ERROR invalid URL string; invalid authorization credentials in  URL (username without password or vice-versa)
        @throw HTTP-CLIENT-UNKNOWN-PROTOCOL unknown protocol (scheme) passed in URL

        @see HTTPClient::getURL()

        @note URLs with UNIX sockets are generally supported in Qore with the following syntax: <tt><b>scheme://socket=</b></tt><i>url_encoded_path</i><tt><b>/path</b></tt>, where <i>url_encoded_path</i> is a path with URL-encoding as performed by @ref Qore::encode_url() "encode_url(string, True)"; for example: \c "http://socket=%2ftmp%socket-dir%2fsocket-file-1/url/path"; this allows a filesystem path to be used in the host portion of the URL and for the URL to include a URL path as well.
    */
    public void setURL(String url) throws Throwable {
        obj.callMethod("setURL", url);
    }

    //! Returns the current URL
    /** @return the current URL

        @par Example:
        @code{.java}
    String url = httpclient.getURL();
        @endcode
    */
    public String getURL() throws Throwable {
        return (String)obj.callMethod("getURL");
    }

    //! Clears the new proxy URL value for the next connection
    /** This variant of the method is equivalent to HTTPClient::clearProxyURL()

        @par Example:
        @code{.java}
    httpclient.setProxyURL();
        @endcode
    */
    public void setProxyURL() throws Throwable {
        obj.callMethod("setProxyURL");
    }

    //! Sets a new proxy URL value for the next connection
    /** @param url the new proxy URL value for the next connection

        @par Example:
        @code{.java}
    httpclient.setProxyURL("http://user:password@proxy_host:8080/path");
        @endcode

        @throw HTTP-CLIENT-URL-ERROR invalid proxy URL string; invalid authorization credentials in proxy URL (username without password or vice-versa)
        @throw HTTP-CLIENT-PROXY-PROTOCOL-ERROR unknown protocol passed in URL

        @note URLs with UNIX sockets are generally supported in Qore with the following syntax: <tt><b>scheme://socket=</b></tt><i>url_encoded_path</i><tt><b>/path</b></tt>, where <i>url_encoded_path</i> is a path with URL-encoding as performed by @ref Qore::encode_url() "encode_url(string, True)"; for example: \c "http://socket=%2ftmp%socket-dir%2fsocket-file-1/url/path"; this allows a filesystem path to be used in the host portion of the URL and for the URL to include a URL path as well.
    */
    public void setProxyURL(String url) throws Throwable {
        obj.callMethod("setProxyURL", url);
    }

    //! Returns the current proxy URL as a string or null if no proxy URL is set
    /** @return the current proxy URL as a string or null if no proxy URL is set

        @par Example:
        @code{.java}
    String proxy_url = httpclient.getProxyURL();
        @endcode
    */
    public String getProxyURL() throws Throwable {
        return (String)obj.callMethod("getProxyURL");
    }

    //! Clears the new proxy URL value for the next connection
    /**
         @par Example:
        @code{.java}
    httpclient.setProxyURL();
        @endcode
    */
    public void clearProxyURL() throws Throwable {
        obj.callMethod("clearProxyURL");
    }

    //! Sets the SSL/TLS flag for the next connection to the proxy
    /**
         @par Example:
        @code{.java}
    httpclient.setProxySecure(true);
        @endcode

        @see HTTPClient::isProxySecure() to check the flag
    */
    public void setProxySecure(boolean b) throws Throwable {
        obj.callMethod("setProxySecure", b);
    }

    //! Returns the SSL/TLS flag for the next proxy connection
    /** @return the SSL/TLS flag for the next proxy connection

        @par Example:
        @code{.java}
    boolean b = httpclient.isProxySecure();
        @endcode
    */
    public boolean isProxySecure() throws Throwable {
        return (boolean)obj.callMethod("isProxySecure");
    }

    //! Updates the setting for the \c max_redirects value for the object (maximum number of HTTP redirects that will be processed before an exception is raised)
    /** @param mr the setting for the maximum number of HTTP redirects that will be processed before an exception is raised

        @par Example:
        @code{.java}
    httpclient.setMaxRedirects(5);
        @endcode

        @see HTTPClient::getMaxRedirects() to retrieve this value
    */
    public void setMaxRedirects(int mr) throws Throwable {
        obj.callMethod("setMaxRedirects", mr);
    }

    //! Returns the current \c max_redirects value for the object (the maximum number of HTTP redirects that will be processed before an exception is raised)
    /** @return the current \c max_redirects value for the object (the maximum number of HTTP redirects that will be processed before an exception is raised)

        @par Example:
        @code{.java}
    int mr = httpclient.getMaxRedirects();
        @endcode
    */
    public int getMaxRedirects() throws Throwable {
        return ((Long)obj.callMethod("getMaxRedirects")).intValue();
    }

    //! Sets the connect timeout in milliseconds
    /** @param timeout_ms the connect timeout in milliseconds; negative numbers mean use the default system connect timeout. Note that like all %Qore functions and methods taking timeout values, a @ref relative_dates "relative date/time value" can be used to make the units clear (i.e. \c 30s = 30 seconds, etc.).

        @par Example:
        @code{.java}
    httpclient.setConnectTimeout(120000);
        @endcode
    */
    public void setConnectTimeout(int timeout_ms) throws Throwable {
        obj.callMethod("setConnectTimeout", timeout_ms);
    }

    //! Returns the connect timeout as an integer in milliseconds
    /** @return Returns the connect timeout as an integer in milliseconds; negative numbers mean the system default timeout is used

        @par Example:
        @code{.java}
    int to = httpclient.getConnectTimeout();
        @endcode
    */
    public int getConnectTimeout() throws Throwable {
        return ((Long)obj.callMethod("getConnectTimeout")).intValue();
    }

    //! Sets the \c TCP_NODELAY setting for the object
    /** When this setting is True, then data will be immediately sent out over the HTTPClient object's socket, when it is false, then data transmission may be delayed to be packaged with other data for the same target.

        Delayed data transmissions may cause problems when the sender immediately closes the socket after sending data; in this case the receiver may not get the data even though the send succeeded.

        Note that if no value is given to the method, the argument will be assumed to be true, and output buffering will be turned off for the HTTPClient object.

        If the socket is not connected when this call is made, then an internal flag is set and the \c TCP_NODELAY option is enabled when the next connection is established. If the socket is connected, then if an error occurs setting the \c TCP_NODELAY option on the socket, this method will return a non-zero error code; the actual error can be checked with the errno() function.

        @par Example:
        @code{.java}
    httpclient.setNoDelay(true);
        @endcode

        @param b the \c TCP_NODELAY setting for the object

        @see HTTPClient::getNoDelay()
    */
    public int setNoDelay(boolean b) throws Throwable {
        return ((Long)obj.callMethod("setNoDelay", b)).intValue();
    }

    //! Returns the \c TCP_NODELAY setting for the HTTPClient object
    /**
         @par Example:
        @code{.java}
    boolean b = httpclient.getNoDelay();
        @endcode

        @see also HTTPClient::setNoDelay()
    */
    public boolean getNoDelay() throws Throwable {
        return (boolean)obj.callMethod("getNoDelay");
    }

    //! Returns true or false giving the current connection state
    /** @return true or false giving the current connection state

        @par Example:
        @code{.java}
    boolean b = httpclient.isConnected();
        @endcode
    */
    public boolean isConnected() throws Throwable {
        return (boolean)obj.callMethod("isConnected");
    }

    //! Sets the username and password for the connection; call after HTTPClient::setURL()
    /** Call this method after calling HTTPClient::setURL() to set authentication information when not present in the URL used in HTTPClient::setURL()

        @param user the username to use for authentication in the next HTTP connection
        @param pass the password to use for authentication in the next HTTP connection

        @par Example:
        @code{.java}
    httpclient.setUserPassword(user, pass);
        @endcode
    */
    public void setUserPassword(String user, String pass) throws Throwable {
        obj.callMethod("setUserPassword", user, pass);
    }

    //! Clears the username and password for the connection
    /** Call this method after calling HTTPClient::setURL() to clear any authentication information present in the URL used in HTTPClient::setURL()

        This variant of the method is equivalent to HTTPClient::clearUserPassword()

        @par Example:
        @code{.java}
    httpclient.setUserPassword();
        @endcode

        @see HTTPClient::clearUserPassword()
    */
    public void setUserPassword() throws Throwable {
        obj.callMethod("setUserPassword");
    }

    //! Clears the username and password for the connection
    /** Call this method after calling HTTPClient::setURL() to clear any authentication information present in the URL used in HTTPClient::setURL()

        @par Example:
        @code{.java}
    httpclient.clearUserPassword();
        @endcode
    */
    public void clearUserPassword() throws Throwable {
        obj.callMethod("clearUserPassword");
    }

    //! Sets the username and password for the connection to the proxy; call after HTTPClient::setProxyURL()
    /** Call this method after calling HTTPClient::setProxyURL() to set proxy authentication information when not present in the URL used in HTTPClient::setProxyURL()

        @par Example:
        @code{.java}
    httpclient.setProxyUserPassword(user, pass);
        @endcode

        @param user the username to use for proxy authentication in the next HTTP connection
        @param pass the password to use for proxy authentication in the next HTTP connection
    */
    public void setProxyUserPassword(String user, String pass) throws Throwable {
        obj.callMethod("setProxyUserPassword", user, pass);
    }

    //! Clears the username and password for the next proxy connection
    /** Call this method after calling HTTPClient::setProxyURL() to clear any proxy authentication information present in the URL used in HTTPClient::setProxyURL()

        This variant of the method is equivalent to HTTPClient::clearProxyUserPassword()

        @par Example:
        @code{.java}
    httpclient.setProxyUserPassword();
        @endcode

        @see HTTPClient::clearProxyUserPassword()
    */
    public void setProxyUserPassword() throws Throwable {
        obj.callMethod("setProxyUserPassword");
    }

    //! Clears the username and password for the next proxy connection
    /** Call this method after calling HTTPClient::setProxyURL() to clear any proxy authentication information present in the URL used in HTTPClient::setProxyURL()

        @par Example:
        @code{.java}
    httpclient.clearProxyUserPassword();
        @endcode
    */
    public void clearProxyUserPassword() throws Throwable {
        obj.callMethod("clearProxyUserPassword");
    }

    //! Sets the default path used by the object if no path is set in the URL
    /** @param path the default path value to set or if null then clears the path

        @par Example:
        @code{.java}
    httpclient.setDefaultPath(null);
        @endcode
    */
    public void setDefaultPath(String path) throws Throwable {
        obj.callMethod("setDefaultPath", path);
    }

    //! Returns the default path used by the object if no path is set in the URL
    /** @return the default path used by the object if no path is set in the URL

        @par Example:
        @code{.java}
    String def_path = httpclient.getDefaultPath();
        @endcode
    */
    public String getDefaultPath() throws Throwable {
        return (String)obj.callMethod("getDefaultPath");
    }

    //! Returns the current connection path set in the URL
    /** @return the current connection path set in the URL

        @par Example:
        @code{.java}
    *string path = httpclient.getConnectionPath();
        @endcode
    */
    public String getConnectionPath() throws Throwable {
        return (String)obj.callMethod("getConnectionPath");
    }

    //! Overrides any connection path set in the URL
    /** @par Example:
        @code{.java}
    httpclient.setConnectionPath(new_path);
        @endcode

        @see getConnectionPath()
    */
    public void setConnectionPath(String uri_path) throws Throwable {
        obj.callMethod("setConnectionPath", uri_path);
    }

    //! Overrides any connection path set in the URL
    /** @par Example:
        @code{.java}
    httpclient.setConnectionPath();
        @endcode

        @see getConnectionPath()
    */
    public void setConnectionPath() throws Throwable {
        obj.callMethod("setConnectionPath");
    }

    //! Returns performance statistics for the socket
    /** @par Example:
         @code{.java}
    Map<String, Object> h = httpclient.getUsageInfo();
        @endcode

        @return a hash with the following keys:
        - \c "bytes_sent": an integer giving the total amount of bytes sent
        - \c "bytes_recv": an integer giving the total amount of bytes received
        - \c "us_sent": an integer giving the total number of microseconds spent sending data
        - \c "us_recv": an integer giving the total number of microseconds spent receiving data
        - \c "arg": (only if warning values have been set with @ref Qore::HTTPClient::setWarningQueue() "HTTPClient::setWarningQueue()") the optional argument for warning hashes
        - \c "timeout": (only if warning values have been set with @ref Qore::HTTPClient::setWarningQueue() "HTTPClient::setWarningQueue()") the warning timeout in microseconds
        - \c "min_throughput": (only if warning values have been set with @ref Qore::HTTPClient::setWarningQueue() "HTTPClient::setWarningQueue()") the minimum warning throughput in bytes/sec

        @since Qore 0.8.9

        @see HTTPClient::clearStats()
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getUsageInfo() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getUsageInfo");
    }

    //! Clears performance statistics
    /** @par Example:
         @code{.java}
    httpclient.clearStats();
        @endcode

        @since Qore 0.8.9

        @see HTTPClient::getUsageInfo()
    */
    public void clearStats() throws Throwable {
        obj.callMethod("clearStats");
    }

    //! temporarily disables implicit reconnections; must be called when the server is already connected
    /** @par Example:
         @code{.java}
    httpclient.connect();
    httpclient.setPersistent();
        @endcode

        The persistent flag is automatically reset to \c false whenever the connection is closed; it must be called manually for every connection to turn off implicit reconnections.

        To turn off the persistent flag manually, call @ref HTTPClient::disconnect()

        @since Qore 0.8.10
    */
    public void setPersistent() throws Throwable {
        obj.callMethod("setPersistent");
    }

    //! set the error passthru status
    /** @par Example:
        @code{.java}
    httpclient.setErrorPassthru();
        @endcode

        @param set if true then HTTP status codes indicating errors will not cause an
        \c HTTP-CLIENT-RECEIVE-ERROR exception to be raised, rather such responses will be passed through to the caller
        like any other response

        @return the old \c error_passthru value

        If true then HTTP status codes indicating errors will not cause an
        \c HTTP-CLIENT-RECEIVE-ERROR exception to be raised, rather such responses will be passed through to the caller
        like any other response.

        @since %Qore 0.9.3
    */
    public boolean setErrorPassthru(boolean set) throws Throwable {
        return (boolean)obj.callMethod("setErrorPassthru", set);
    }

    //! set the error passthru status
    /** @par Example:
        @code{.java}
    httpclient.setErrorPassthru();
        @endcode

        @return the old \c error_passthru value

        If true then HTTP status codes indicating errors will not cause an
        \c HTTP-CLIENT-RECEIVE-ERROR exception to be raised, rather such responses will be passed through to the caller
        like any other response.

        @since %Qore 0.9.3
    */
    public boolean setErrorPassthru() throws Throwable {
        return (boolean)obj.callMethod("setErrorPassthru");
    }

    //! get the error passthru status
    /** @par Example:
        @code{.java}
    boolean b = httpclient.getErrorPassthru();
        @endcode

        @return the current \c error_passthru value

        If true then HTTP status codes indicating errors will not cause an
        \c HTTP-CLIENT-RECEIVE-ERROR exception to be raised, rather such responses will be passed through to the caller
        like any other response.

        @since %Qore 0.9.3
    */
    public boolean getErrorPassthru() throws Throwable {
        return (boolean)obj.callMethod("getErrorPassthru");
    }

    //! set the redirect passthru status
    /** @par Example:
        @code{.java}
    httpclient.setRedirectPassthru(true);
        @endcode

        @param set if true then redirect messages will be passed to the caller instead of proceessed

        @return the old \c reddirect_passthru value

        If true then redirect messages will be passed to the callers instead of proceessed

        @since %Qore 0.9.3
    */
    public boolean setRedirectPassthru(boolean set) throws Throwable {
        return (boolean)obj.callMethod("setRedirectPassthru", set);
    }

    //! set the redirect passthru status
    /** @par Example:
        @code{.java}
    httpclient.setRedirectPassthru();
        @endcode

        @return the old \c reddirect_passthru value

        If true then redirect messages will be passed to the callers instead of proceessed

        @since %Qore 0.9.3
    */
    public boolean setRedirectPassthru() throws Throwable {
        return (boolean)obj.callMethod("setRedirectPassthru");
    }

    //! get the redirect passthru status
    /** @par Example:
        @code{.java}
    boolean b = httpclient.getRedirectPassthru();
        @endcode

        @return the current \c redirect_passthru value

        If true then redirect messages will be passed to the caller instead of proceessed

        @since %Qore 0.9.3
    */
    public boolean getRedirectPassthru() throws Throwable {
        return (boolean)obj.callMethod("getRedirectPassthru");
    }

    //! set the encoding passthru status
    /** @par Example:
        @code{.java}
    httpclient.setEncodingPassthru(true);
        @endcode

        @param set if true then message bodies received with known content encodings are not decoded but
        rather passed through as-is

        @return the old \c encoding_passthru value

        If true then message bodies received with known content encodings are not decoded but
        rather passed through as-is

        @since %Qore 0.9.3
    */
    public boolean setEncodingPassthru(boolean set) throws Throwable {
        return (boolean)obj.callMethod("setEncodingPassthru", set);
    }

    //! set the encoding passthru status
    /** @par Example:
        @code{.java}
    httpclient.setEncodingPassthru();
        @endcode

        @return the old \c encoding_passthru value

        If true then message bodies received with known content encodings are not decoded but
        rather passed through as-is

        @since %Qore 0.9.3
    */
    public boolean setEncodingPassthru() throws Throwable {
        return (boolean)obj.callMethod("setEncodingPassthru");
    }

    //! get the encoding passthru status
    /** @par Example:
        @code{.java}
    bool b = httpclient.getErrorPassthru();
        @endcode

        @return the current \c encoding_passthru value

        If true then message bodies received with known content encodings are not decoded but
        rather passed through as-is

        @since %Qore 0.9.3
    */
    public boolean getEncodingPassthru() throws Throwable {
        return (boolean)obj.callMethod("getEncodingPassthru");
    }

    //! returns the \c Host header value for this object
    /** @par Example:
        @code{.java}
    string host = httpclient.getHostHeaderValue();
        @endcode

        @return the \c Host header value for this object

        @since %Qore 0.9.3
    */
    public String getHostHeaderValue() throws Throwable {
        return (String)obj.callMethod("getHostHeaderValue");
    }
}
