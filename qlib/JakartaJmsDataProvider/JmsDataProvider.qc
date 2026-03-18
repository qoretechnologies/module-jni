# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Root data provider for JMS messaging functionality
/**
*/
public class JmsDataProvider inherits JmsDataProviderBase {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "jms",
            "desc": "Parent data provider for Jakarta JMS messaging functionality",
            "supports_children": True,
            "children_can_support_apis": True,
            "children_can_support_observers": True,
            "children_can_support_messages": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "url": <DataProviderOptionInfo>{
                "display_name": "URL",
                "short_desc": "The URL to the JMS broker",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The URL to the JMS broker (e.g. `tcp://localhost:61616`)",
                "required": True,
            },
            "connection_factory_class": <DataProviderOptionInfo>{
                "display_name": "Connection Factory Class",
                "short_desc": "Fully-qualified Java class name of the JMS ConnectionFactory",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Fully-qualified Java class name of the JMS ConnectionFactory implementation "
                    "(e.g. `org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory`)",
                "required": True,
            },
            "provider_classpath": <DataProviderOptionInfo>{
                "display_name": "Provider Classpath",
                "short_desc": "Comma-separated list of JAR files for the JMS provider",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Comma-separated list of JAR files or directories to add to the classpath "
                    "for the JMS provider at runtime",
            },
            "username": <DataProviderOptionInfo>{
                "display_name": "Username",
                "short_desc": "The username for authentication",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The username for authentication with the JMS broker; if any",
            },
            "password": <DataProviderOptionInfo>{
                "display_name": "Password",
                "short_desc": "The password for authentication",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The password for authentication with the JMS broker; if any",
                "sensitive": True,
            },
            "client_id": <DataProviderOptionInfo>{
                "display_name": "Client ID",
                "short_desc": "The JMS client ID for durable subscriptions",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The JMS client ID; required for durable topic subscriptions",
            },
            "session_mode": <DataProviderOptionInfo>{
                "display_name": "Session Mode",
                "short_desc": "The JMS session acknowledgment mode",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The JMS session acknowledgment mode",
                "default_value": "AUTO_ACKNOWLEDGE",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "Auto Acknowledge",
                        "short_desc": "Automatic message acknowledgment",
                        "desc": "The session automatically acknowledges a client's receipt of a message",
                        "value": "AUTO_ACKNOWLEDGE",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Client Acknowledge",
                        "short_desc": "Client explicitly acknowledges messages",
                        "desc": "The client acknowledges a consumed message by calling the message's "
                            "acknowledge method",
                        "value": "CLIENT_ACKNOWLEDGE",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Dups OK Acknowledge",
                        "short_desc": "Lazy acknowledgment allowing duplicates",
                        "desc": "The session lazily acknowledges the delivery of messages",
                        "value": "DUPS_OK_ACKNOWLEDGE",
                    },
                    <AllowedValueInfo>{
                        "display_name": "Session Transacted",
                        "short_desc": "Transactional session",
                        "desc": "The session is transacted",
                        "value": "SESSION_TRANSACTED",
                    },
                ),
            },
            "destination": <DataProviderOptionInfo>{
                "display_name": "Destination",
                "short_desc": "Default destination for messaging operations",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Default destination in format `queue://name` or `topic://name`; "
                    "used by the polling provider for event subscription",
            },
            "message_selector": <DataProviderOptionInfo>{
                "display_name": "Message Selector",
                "short_desc": "JMS message selector for filtering messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "A JMS SQL-92 message selector expression for filtering incoming messages "
                    "(e.g. `priority > 5 AND color = 'red'`)",
            },
            "max_sessions": <DataProviderOptionInfo>{
                "display_name": "Max Sessions",
                "short_desc": "Maximum number of pooled JMS sessions",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Maximum number of JMS sessions in the session pool",
                "default_value": 4,
            },
            "connection_timeout_ms": <DataProviderOptionInfo>{
                "display_name": "Connection Timeout (ms)",
                "short_desc": "Connection timeout in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Connection timeout in milliseconds for operations like request-reply",
                "default_value": 5000,
            },
        };

        #! Child data provider map
        const ChildMap = {
            "producer": Class::forName("JmsProducerDataProvider"),
            "browser": Class::forName("JmsBrowserDataProvider"),
            "polling": Class::forName("JmsPollingDataProvider"),
        };
    }

    private {
        #! The JMS ConnectionFactory
        ConnectionFactory factory;

        #! The JMS Connection
        Connection jms_conn;

        #! The session pool
        JmsSessionPool session_pool;
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) : JmsDataProviderBase(options ?? {}) {
        hash<auto> validated_opts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions, options);
        copts = validated_opts;

        # create connection factory
        factory = JmsDataProviderBase::createConnectionFactory(copts);

        # create JMS connection
        jms_conn = JmsDataProviderBase::createJmsConnection(factory, copts);

        # create session pool
        int ack_mode = JmsDataProviderBase::getSessionMode(copts.session_mode ?? "AUTO_ACKNOWLEDGE");
        session_pool = new JmsSessionPool(jms_conn, ack_mode, copts.max_sessions ?? 4);

        # start the connection to allow message delivery
        jms_conn.start();
    }

    destructor() {
        if (session_pool) {
            session_pool.close();
        }
        if (jms_conn) {
            try {
                jms_conn.close();
            } catch () {
                # ignore
            }
        }
    }

    string getName() {
        return "jms";
    }

    #! Returns the JMS Connection
    Connection getJmsConnection() {
        return jms_conn;
    }

    #! Returns the JMS ConnectionFactory
    ConnectionFactory getConnectionFactory() {
        return factory;
    }

    #! Returns the session pool
    JmsSessionPool getSessionPool() {
        return session_pool;
    }

    #! Returns connection options
    hash<auto> getConnectionOptions() {
        return copts;
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Return data provider summary info
    *list<hash<DataProvider::DataProviderSummaryInfo>> getChildProviderSummaryInfo() {
        return map $1.getConstant("ProviderSummaryInfo").getValue(), ChildMap.iterator();
    }

    #! Returns a list of child data provider names, if any
    private *list<string> getChildProviderNamesImpl() {
        return keys ChildMap;
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    private *DataProvider::AbstractDataProvider getChildProviderImpl(string name) {
        if (*Class cls = ChildMap{name}) {
            return cls.newObject(self);
        }
    }
}
}
