# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsConnection.qc Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

#! contains all public definitions in the JakartaJmsDataProvider module
public namespace JakartaJmsDataProvider {
#! Class for JMS connections
/** supports the options from @ref JakartaJmsDataProvider::JmsDataProvider::ConstructorOptions
*/
public class JmsConnection inherits ConnectionProvider::AbstractConnectionWithInfo {
    public {
        #! Connection entry info
        const ConnectionScheme = <ConnectionSchemeInfo>{
            "display_name": "JMS Connection",
            "short_desc": "A connection to a Jakarta JMS messaging broker",
            "desc": "A connection to a Jakarta JMS 3.x messaging broker (e.g. ActiveMQ Artemis, TIBCO EMS, "
                "IBM MQ, Oracle AQ)",
            "cls": Class::forName("JmsConnection"),
            "options": {
                "connection_factory_class": <ConnectionOptionInfo>{
                    "display_name": "Connection Factory Class",
                    "short_desc": "Fully-qualified Java class name of the JMS ConnectionFactory",
                    "type": "string",
                    "desc": "Fully-qualified Java class name of the JMS ConnectionFactory implementation "
                        "(e.g. `org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory`)",
                    "required": True,
                },
                "provider_classpath": <ConnectionOptionInfo>{
                    "display_name": "Provider Classpath",
                    "short_desc": "Comma-separated list of JAR files or directories for the JMS provider",
                    "type": "string",
                    "desc": "Comma-separated list of JAR files or directories to add to the classpath "
                        "for the JMS provider (e.g. the Artemis client JARs)",
                },
                "username": <ConnectionOptionInfo>{
                    "display_name": "Username",
                    "short_desc": "The username for authentication",
                    "type": "string",
                    "desc": "The username for authentication with the JMS broker; if any",
                },
                "password": <ConnectionOptionInfo>{
                    "display_name": "Password",
                    "short_desc": "The password for authentication",
                    "type": "string",
                    "desc": "The password for authentication with the JMS broker; if any",
                    "sensitive": True,
                },
                "client_id": <ConnectionOptionInfo>{
                    "display_name": "Client ID",
                    "short_desc": "The JMS client ID for durable subscriptions",
                    "type": "string",
                    "desc": "The JMS client ID; required for durable topic subscriptions",
                },
                "session_mode": <ConnectionOptionInfo>{
                    "display_name": "Session Mode",
                    "short_desc": "The JMS session acknowledgment mode",
                    "type": "string",
                    "desc": "The JMS session acknowledgment mode",
                    "default_value": "AUTO_ACKNOWLEDGE",
                    "allowed_values": (
                        <AllowedValueInfo>{
                            "display_name": "Auto Acknowledge",
                            "short_desc": "Automatic message acknowledgment",
                            "desc": "The session automatically acknowledges a client's receipt of a message "
                                "either when the session has successfully returned from a call to receive "
                                "or when the message listener the session has called to process the message "
                                "successfully returns",
                            "value": "AUTO_ACKNOWLEDGE",
                        },
                        <AllowedValueInfo>{
                            "display_name": "Client Acknowledge",
                            "short_desc": "Client explicitly acknowledges messages",
                            "desc": "The client acknowledges a consumed message by calling the message's "
                                "acknowledge method. Acknowledging a consumed message acknowledges all "
                                "messages that the session has consumed",
                            "value": "CLIENT_ACKNOWLEDGE",
                        },
                        <AllowedValueInfo>{
                            "display_name": "Dups OK Acknowledge",
                            "short_desc": "Lazy acknowledgment allowing duplicates",
                            "desc": "The session lazily acknowledges the delivery of messages. This is "
                                "likely to result in the delivery of some duplicate messages if the JMS "
                                "provider fails, so it should only be used by consumers that can tolerate "
                                "duplicate messages",
                            "value": "DUPS_OK_ACKNOWLEDGE",
                        },
                        <AllowedValueInfo>{
                            "display_name": "Session Transacted",
                            "short_desc": "Transactional session",
                            "desc": "The session is transacted; messages are delivered in a transaction "
                                "and must be committed or rolled back",
                            "value": "SESSION_TRANSACTED",
                        },
                    ),
                },
                "connection_timeout_ms": <ConnectionOptionInfo>{
                    "display_name": "Connection Timeout (ms)",
                    "short_desc": "Connection timeout in milliseconds",
                    "type": "int",
                    "desc": "Connection timeout in milliseconds; used for ping/connection test",
                    "default_value": 5000,
                },
                "max_sessions": <ConnectionOptionInfo>{
                    "display_name": "Max Sessions",
                    "short_desc": "Maximum number of pooled JMS sessions",
                    "type": "int",
                    "desc": "Maximum number of JMS sessions in the session pool",
                    "default_value": 4,
                },
            },
            "schemes": {
                "jms": True,
            },
        };
    }

    #! Creates the object from the arguments
    constructor(string name, string desc, string url, hash<auto> attr = {}, hash<auto> options = {},
            *LoggerInterface logger) : AbstractConnectionWithInfo(name, desc, url, attr, options, logger) {
    }

    #! Creates the object from the arguments
    /** @param config with the following keys:
        - name (required string): the connection name
        - display_name (optional string): the display name
        - short_desc (optional string): a short description in plain text
        - desc (optional string): a long description with markdown formatting
        - url (required string): the connection URL
        - opts (optional hash): connection options
        - logger (optional LoggerInterface object): logger for the connection
        @param attr optional connection attributes
        - monitor (optional bool): should the connection be monitored? Default: True
        - enabled (optional bool): is the connection enabled? Default: True
        - locked (optional bool): is the connection locked? Default: False
        - debug_data (optional bool): debug data? Default: False
        - tags (optional hash): tags for the connection; no default value

        @throw CONNECTION-OPTION-ERROR missing or invalid connection option or attribute
    */
    constructor(hash<auto> config, *hash<auto> attr) : AbstractConnectionWithInfo(config, attr) {
    }

    #! returns the string type name for the connection object
    string getType() {
        return "jms";
    }

    #! Returns @ref True, as this connection class returns a data provider with the @ref getDataProvider() method
    bool hasDataProvider() {
        return True;
    }

    #! Returns the data provider application name this connection serves
    *string getAppName() {
        return "JakartaJMS";
    }

    #! Returns a @ref JmsDataProvider object for this connection
    /** @param constructor_options any additional constructor options for the data provider

        @return a data provider object for this connection, if supported
    */
    AbstractDataProvider getDataProvider(*hash<auto> constructor_options) {
        return new JmsDataProvider(orig_opts + {"url": url} + constructor_options);
    }

    #! Returns the ConnectionSchemeInfo hash for this object
    private hash<ConnectionSchemeInfo> getConnectionSchemeInfoImpl() {
        return ConnectionScheme;
    }

    #! Returns a hash of data provider constructor option info, if applicable
    private *hash<string, hash<DataProviderOptionInfo>> getDataProviderConstructorOptionsImpl() {
        return JmsDataProvider::ConstructorOptions;
    }

    #! Override to set child data provider capabilities once per child class
    private setChildCapabilities() {
        children_can_support_observers = True;
        children_can_support_messages = True;
    }

    #! this method must return the connection object corresponding to the object's configuration
    /** @param connect if @ref True "True" then the connection should be returned connected
        @param rtopts options to override connection options

        @return the connection object
    */
    private JmsConnectionWrapper getImpl(bool connect = True, *hash<auto> rtopts) {
        hash<auto> copts = orig_opts + {"url": url} + rtopts;
        ConnectionFactory factory = JmsDataProviderBase::createConnectionFactory(copts);
        if (!connect) {
            return new JmsConnectionWrapper(factory);
        }
        Connection conn = JmsDataProviderBase::createJmsConnection(factory, copts);
        # create a test session for ping
        int mode = JmsDataProviderBase::getSessionMode(copts.session_mode ?? "AUTO_ACKNOWLEDGE");
        Session test_session = conn.createSession(mode);
        test_session.close();
        return new JmsConnectionWrapper(factory, conn);
    }
}
}
