/** Java wrapper for the %Qore RestClient class
 *
 */
package org.qore.lang.restclient;

// java imports
import java.util.Map;
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;

// Qore imports
import org.qore.lang.HTTPClient;

//! Java wrapper for the @ref RestClient::RestClient class in %Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.RestClient.RestClient;</tt>
*/
@Deprecated
public class RestClient extends HTTPClient {
    // static initialization
    static {
        // initialize the Qore library if necessary
        try {
            QoreJavaApi.initQore();
            QoreJavaApi.callFunction("load_module", "RestClient");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object
    public RestClient(QoreObject ds) {
        super(ds);
    }

    //! calls the base class HTTPClient constructor and optionally connects to the REST server
    /** @par Example:
        @code{.java}
HashMap<String, Object> opts = new HashMap<String, Object>() {
    {
        put("url", "http://localhost:8001/rest");
    }
};
RestClient rest = new RestClient(opts);
        @endcode

        @param opts valid options are:
        - \c additional_methods: Optional hash with more but not-HTTP-standardized methods to handle. It allows to create various HTTP extensions like e.g. WebDAV. The hash takes the method name as a key, and the value is a boolean true or false: indicating if the method can accept a message body as well. Example:
            @code{.java}
// add new HTTP methods for WebDAV. Both of them require body posting to the server
{"additional_methods": {"PROPFIND": true, "MKCOL": true}};
            @endcode
        - \c connect_timeout: The timeout value in milliseconds for establishing a new socket connection (also can be a relative date-time value for clarity, ex: \c 20s)
        - \c content_encoding: for possible values, see @ref RestClient::RestClient::EncodingSupport; this sets the send encoding (if the \c "send_encoding" option is not set) and the requested response encoding (note that the @ref RestClient::RestClient "RestClient" class will only compress outgoing message bodies over @ref RestClient::RestClient::CompressionThreshold "CompressionThreshold" bytes in size)
        - \c data: a @ref RestClient::RestClient::DataSerializationOptions "data serialization option"; if not present defaults to \c "auto"
        - \c default_path: The default path to use for new connections if a path is not otherwise specified in the connection URL
        - \c default_port: The default port number to connect to if none is given in the URL
        - \c error_passthru: if true then HTTP status codes indicating errors will not cause a
          \c REST-RESPONSE-ERROR exception to be raised, rather such responses will be passed through to the caller
          like any other response
        - \c headers: an optional hash of headers to send with every request, these can also be overridden in
          request method calls
        - \c http_version: Either '1.0' or '1.1' for the claimed HTTP protocol version compliancy in outgoing message headers
        - \c max_redirects: The maximum number of redirects before throwing an exception (the default is 5)
        - \c proxy: The proxy URL for connecting through a proxy
        - \c redirect_passthru: if true then redirect responses will be passed to the caller instead of
          processed
        - \c send_encoding: a @ref RestClient::RestClient::EncodingSupport "send data encoding option" or the value \c "auto" which means to use automatic encoding; if not present defaults to no content-encoding on sent message bodies (note that the @ref RestClient::RestClient "RestClient" class will only compress outgoing message bodies over @ref RestClient::RestClient::CompressionThreshold "CompressionThreshold" bytes in size)
        - \c swagger: the path to a <a href="https://swagger.io/">Swagger 2.0</a> REST schema file for API validation; only used if \a validator not provided (see the @ref swaggerintro "Swagger" module)
        - \c timeout: The timeout value in milliseconds (also can be a relative date-time value for clarity, ex: \c 30s)
        - \c url: A string giving the URL to connect to; if not given then the target URL will be taken from any \c validator option, if given by calling @ref RestSchemaValidator::AbstractRestSchemaValidator::getTargetUrl() "AbstractRestSchemaValidator::getTargetUrl()"
        - \c validator: an @ref RestSchemaValidator::AbstractRestSchemaValidator "AbstractRestSchemaValidator" object to validate request and response messages; overrides \a swagger
        - \c no_charset: if true no charset will be added to the Content-Type header
        @param do_not_connect if \c False (the default), then a connection will be immediately established to the remote server

        @throw RESTCLIENT-ERROR invalid option passed to constructor, unsupported data serialization, etc
    */
    public RestClient(Map<String, Object> opts, boolean do_not_connect) throws Throwable {
        super(QoreJavaApi.newObjectSave("RestClient::RestClient", opts, do_not_connect));
    }

    //! calls the base class HTTPClient constructor and optionally connects to the REST server
    /** @par Example:
        @code{.java}
HashMap<String, Object> opts = new HashMap<String, Object>() {
    {
        put("url", "http://localhost:8001/rest");
    }
};
RestClient rest = new RestClient(opts);
        @endcode

        @param opts valid options are:
        - \c additional_methods: Optional hash with more but not-HTTP-standardized methods to handle. It allows to create various HTTP extensions like e.g. WebDAV. The hash takes the method name as a key, and the value is a boolean true or false: indicating if the method can accept a message body as well. Example:
            @code{.java}
// add new HTTP methods for WebDAV. Both of them require body posting to the server
{"additional_methods": {"PROPFIND": true, "MKCOL": true}};
            @endcode
        - \c connect_timeout: The timeout value in milliseconds for establishing a new socket connection (also can be a relative date-time value for clarity, ex: \c 20s)
        - \c content_encoding: for possible values, see @ref RestClient::RestClient::EncodingSupport; this sets the send encoding (if the \c "send_encoding" option is not set) and the requested response encoding (note that the @ref RestClient::RestClient "RestClient" class will only compress outgoing message bodies over @ref RestClient::RestClient::CompressionThreshold "CompressionThreshold" bytes in size)
        - \c data: a @ref RestClient::RestClient::DataSerializationOptions "data serialization option"; if not present defaults to \c "auto"
        - \c default_path: The default path to use for new connections if a path is not otherwise specified in the connection URL
        - \c default_port: The default port number to connect to if none is given in the URL
        - \c error_passthru: if true then HTTP status codes indicating errors will not cause a
          \c REST-RESPONSE-ERROR exception to be raised, rather such responses will be passed through to the caller
          like any other response
        - \c headers: an optional hash of headers to send with every request, these can also be overridden in
          request method calls
        - \c http_version: Either '1.0' or '1.1' for the claimed HTTP protocol version compliancy in outgoing message headers
        - \c max_redirects: The maximum number of redirects before throwing an exception (the default is 5)
        - \c proxy: The proxy URL for connecting through a proxy
        - \c redirect_passthru: if true then redirect responses will be passed to the caller instead of
          processed
        - \c send_encoding: a @ref RestClient::RestClient::EncodingSupport "send data encoding option" or the value \c "auto" which means to use automatic encoding; if not present defaults to no content-encoding on sent message bodies (note that the @ref RestClient::RestClient "RestClient" class will only compress outgoing message bodies over @ref RestClient::RestClient::CompressionThreshold "CompressionThreshold" bytes in size)
        - \c swagger: the path to a <a href="https://swagger.io/">Swagger 2.0</a> REST schema file for API validation; only used if \a validator not provided (see the @ref swaggerintro "Swagger" module)
        - \c timeout: The timeout value in milliseconds (also can be a relative date-time value for clarity, ex: \c 30s)
        - \c url: A string giving the URL to connect to; if not given then the target URL will be taken from any \c validator option, if given by calling @ref RestSchemaValidator::AbstractRestSchemaValidator::getTargetUrl() "AbstractRestSchemaValidator::getTargetUrl()"
        - \c validator: an @ref RestSchemaValidator::AbstractRestSchemaValidator "AbstractRestSchemaValidator" object to validate request and response messages; overrides \a swagger
        - \c no_charset: if true no charset will be added to the Content-Type header

        @throw RESTCLIENT-ERROR invalid option passed to constructor, unsupported data serialization, etc
    */
    public RestClient(Map<String, Object> opts) throws Throwable {
        super(QoreJavaApi.newObjectSave("RestClient::RestClient", opts));
    }

    //! calls the base class HTTPClient constructor and optionally connects to the REST server
    /** @par Example:
        @code{.java}
RestClient rest = new RestClient();
        @endcode
    */
    public RestClient() throws Throwable {
        super(QoreJavaApi.newObjectSave("RestClient::RestClient"));
    }

    //! change the serialization option for the object; see @ref RestClient::RestClient::DataSerializationOptions for valid options
    /** @par Example:
        @code{.java}
rest.setSerialization("yaml");
        @endcode

        @param data the serialization option for the object; see @ref RestClient::RestClient::DataSerializationOptions for valid options

        @throw RESTCLIENT-ERROR invalid or unsupported serialization option

        @see @ref getSerialization()
    */
    public void setSerialization(String data) throws Throwable {
        obj.callMethod("setSerialization", data);
    }

    //! change the data content encoding (compression) option for the object; see @ref RestClient::RestClient::EncodingSupport for valid options
    /** @par Example:
        @code{.java}
rest.setSendEncoding("gzip");
        @endcode

        The default is to send requests unencoded/uncompressed.

        @param enc the data content encoding (compression) option for the object; see @ref RestClient::RestClient::EncodingSupport for valid options; if the value \c "auto" is passed then \c "gzip" encoding is used

        @throw RESTCLIENT-ERROR invalid or unsupported data content encoding / compression option

        @see
        - @ref setContentEncoding()
        - @ref getSendEncoding()
    */
    public void setSendEncoding(String enc) throws Throwable {
        obj.callMethod("setSendEncoding", enc);
    }

    //! sets the request and desired response encoding for the object; see @ref RestClient::RestClient::EncodingSupport for valid options
    /** @par Example:
        @code{.java}
rest.setContentEncoding("gzip");
        @endcode

        @param enc the data content encoding (compression) option for requests and the desired response content encoding for the object; see @ref RestClient::RestClient::EncodingSupport for valid options; if the value \c "auto" is passed then \c "gzip" encoding is used for outgoing requests and requested for responses

        @throw RESTCLIENT-ERROR invalid or unsupported data content encoding / compression option

        @see
        - @ref getSendEncoding()
        - @ref setSendEncoding()

        @since %RestClient 1.3
    */
    public void setContentEncoding(String enc) throws Throwable {
        obj.callMethod("setContentEncoding", enc);
    }

    //! adds default headers to each request; these headers will be sent in all requests but can be overridden in requests as well
    /** @par Example:
        @code{.java}
// disable gzip and bzip encoding in responses
rest.addDefaultHeaders(("Accept-Encoding": "compress"));
        @endcode

        @param h a hash of headers to add to the default headers to send on each request

        @note default headers can also be set in the constructor

        @see @ref getDefaultHeaders()

        @since %RestClient 1.3
    */
    public void addDefaultHeaders(Map<String, Object> h) throws Throwable {
        obj.callMethod("addDefaultHeaders", h);
    }

    //! returns the hash of default headers to sent in all requests
    /** @par Example:
        @code{.java}
HashMap<String, Object> h = rest.getDefaultHeaders();
        @endcode

        @return the hash of default headers to sent in all requests

        @note default headers can be set in the constructor and in addDefaultHeaders()

        @see @ref addDefaultHeaders()

        @since %RestClient 1.3
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getDefaultHeaders() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getDefaultHeaders");
    }

    //! returns the current data content encoding (compression) object or null if no encoding option is set; see @ref RestClient::RestClient::EncodingSupport for valid options
    /** @par Example:
        @code{.java}
String ce = rest.getSendEncoding();
        @endcode

        @return the current data content encoding (compression) object or null if no encoding option is set; see @ref RestClient::RestClient::EncodingSupport for valid options

        @see
        - @ref setContentEncoding()
        - @ref setSendEncoding()

        @since %RestClient 1.3
    */
    public String getSendEncoding() throws Throwable {
        return (String)obj.callMethod("getSendEncoding");
    }

    //! returns the current data serialization format currently in effect for the object (see @ref RestClient::RestClient::DataSerializationOptions for possible values)
    /** @par Example:
        @code{.java}
String ser = rest.getSerialization();
        @endcode

        @return the current data serialization format currently in effect for the object (see @ref RestClient::RestClient::DataSerializationOptions for possible values)

        @see @ref setSerialization()
     */
    public String getSerialization() throws Throwable {
        return (String)obj.callMethod("getSerialization");
    }

    //! sends an HTTP \c GET request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.get("/orders/1?info=verbose");
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restGet(String path, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("get", path, null, null, hdr);
    }

    //! sends an HTTP \c GET request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.get("/orders/1?info=verbose");
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"; note that sending a message body with an HTTP \c GET request is not standards compliant; see @ref httpclient_get_with_body for more information; for maximum compatibility, use null for this argument when calling this method
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restGet(String path, Object body, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("get", path, body, null, hdr);
    }

    //! sends an HTTP \c GET request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.get("/orders/1?info=verbose");
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restGet(String path) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("get", path);
    }

    //! sends an HTTP \c PUT request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.put("/orders/1", ("action": "cancel"));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPut(String path, Object body, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("put", path, body, null, hdr);
    }

    //! sends an HTTP \c PUT request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.put("/orders/1", ("action": "cancel"));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPut(String path, Object body) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("put", path, body);
    }

    //! sends an HTTP \c PATCH request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.patch("/orders/1", ("action": "cancel"));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPatch(String path, Object body, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("patch", path, body, null, hdr);
    }

    //! sends an HTTP \c PATCH request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.patch("/orders/1", ("action": "cancel"));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPatch(String path, Object body) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("patch", path, body);
    }

    //! sends an HTTP \c POST request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.post("/orders", ("product": "xyz123", "options": 500));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPost(String path, Object body, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("post", path, body, null, hdr);
    }

    //! sends an HTTP \c POST request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.post("/orders", ("product": "xyz123", "options": 500));
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restPost(String path, Object body) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("post", path, body);
    }

    //! sends an HTTP \c DELETE request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.del("/orders/1");
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restDel(String path, Object body, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("del", path, body, null, hdr);
    }

    //! sends an HTTP \c DELETE request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.del("/orders/1");
        @endcode

        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> restDel(String path, Object body) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("del", path, body);
    }

    //! sends an HTTP request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.doRequest("DELETE", "/orders/1");
        @endcode

        @param m the HTTP method to be used; case is ignored (if not a valid method an \c HTTP-CLIENT-METHOD-ERROR exception is raised)
        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param decode_errors decode the message body with HTTP error responses and throw an exception based on the message body
        @param hdr any headers to be sent with the request; headers here will override default headers for the object as well

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw HTTP-CLIENT-METHOD-ERROR invalid HTTP method argument passed
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true
        @throw REST-ACCEPT-ERROR if the message has a validator that indicates that the response message only
        supports types not supported by the current options or environment

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> doRequest(String m, String path, Object body, boolean decode_errors, Map<String, Object> hdr) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("doRequest", m, path, body, null, decode_errors, hdr);
    }

    //! sends an HTTP request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.doRequest("DELETE", "/orders/1");
        @endcode

        @param m the HTTP method to be used; case is ignored (if not a valid method an \c HTTP-CLIENT-METHOD-ERROR exception is raised)
        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"
        @param decode_errors decode the message body with HTTP error responses and throw an exception based on the message body

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw HTTP-CLIENT-METHOD-ERROR invalid HTTP method argument passed
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true
        @throw REST-ACCEPT-ERROR if the message has a validator that indicates that the response message only
        supports types not supported by the current options or environment

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> doRequest(String m, String path, Object body, boolean decode_errors) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("doRequest", m, path, body, null, decode_errors);
    }

    //! sends an HTTP request to the REST server and returns the response
    /** @par Example:
        @code{.java}
HashMap<String, Object> ans = rest.doRequest("DELETE", "/orders/1");
        @endcode

        @param m the HTTP method to be used; case is ignored (if not a valid method an \c HTTP-CLIENT-METHOD-ERROR exception is raised)
        @param path the URI path to add (will be appended to any root path given in the constructor)
        @param body an optional message body to be included in the request; if a value for this parameter is passed to the method, then the body will be serialized according to the serialization rules set in @ref RestClient::RestClient::constructor() "RestClient::constructor()"

        @return A hash of headers received from the HTTP server with all key names converted to lower-case; if any message body is included in the response, it will be deserialized to %Qore data and assigned to the value of the \c "body" key

        @throw DESERIALIZATION-ERROR the response body could not be deserialized (unknown \c Content-Type or invalid serialization)
        @throw HTTP-CLIENT-METHOD-ERROR invalid HTTP method argument passed
        @throw REST-RESPONSE-ERROR if this exception is thrown by the @ref Qore::HTTPClient::send() call in case of an HTTP response code < 100 or >= 300, the message body is still deserialized if possible and the response information can be retrieved in the \a info hash output keys as follows:
        - \c "response-code": the HTTP response code given
        - \c "response-headers": a hash of processed response headers
        - \c "response-headers-raw": a hash of raw unprocessed response headers
        - \c "response-body": the decoded response body
        .
        Note that this exception is not raised for HTTP status codes indicating an error if the \c error_passthru
        option is set to true
        @throw REST-ACCEPT-ERROR if the message has a validator that indicates that the response message only
        supports types not supported by the current options or environment

        Other exceptions can be thrown by the @ref Qore::HTTPClient::send() call used to make the HTTP request.

        @see
        - @ref getSerialization()
        - @ref setSerialization()
        - @ref httpclient_get_with_body
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> doRequest(String m, String path, Object body) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("doRequest", m, path, body);
    }
}
