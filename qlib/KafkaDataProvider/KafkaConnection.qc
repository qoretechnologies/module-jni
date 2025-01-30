# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaConnection.qc Copyright 2022 - 2025 Qore Technologies, s.r.o.

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

#! Contains all public definitions in the KafkaDataProvider module
public namespace KafkaDataProvider {
#! Class for Kafka connections
public class KafkaConnection inherits ConnectionProvider::AbstractConnectionWithInfo {
    public {
        #! Connection entry info
        const ConnectionScheme = <ConnectionSchemeInfo>{
            "display_name": "Kafka Connection",
            "short_desc": "A connection to a KAFKA server",
            "desc": "A connection to a KAFKA server",
            "cls": Class::forName("KafkaConnection"),
            "options": {
                "bootstrap.servers": <ConnectionOptionInfo>{
                    "display_name": "Additional Servers",
                    "short_desc": "One or more additional servers to use to connect to the Kafka cluster",
                    "desc": "A list of additional `host:port` pair strings to use for establishing the initial "
                        "connection to the Kafka cluster to be used in addition to the main server provided in the "
                        "connection URL. The client will make use of all servers irrespective of which servers are "
                        "specified here for bootstrapping; this list only impacts the initial "
                        "hosts used to discover the full set of servers.\n\nThis list should be in the form "
                        "`host1:port1,host2:port2,...\n\nSince these servers are just used for the initial "
                        "connection to discover the full cluster membership (which may change dynamically), this "
                        "list need not contain the full set of servers (you may want more than one, though, in case "
                        "a server is down).",
                    "type": "list",
                },
                "ssl.key.password": <ConnectionOptionInfo>{
                    "display_name": "SSL Key Password",
                    "short_desc": "The password of the private key in the key store file",
                    "type": "string",
                    "desc": "The password of the private key in the key store file",
                    "sensitive": True,
                },
                "ssl.keystore.location": <ConnectionOptionInfo>{
                    "display_name": "SSL Keystore Location",
                    "short_desc": "The location of the key store file",
                    "type": "string",
                    "desc": "The location of the key store file. This is optional for clients and can be used "
                        "for two-way authentication for clients",
                },
                "ssl.keystore.password": <ConnectionOptionInfo>{
                    "display_name": "SSL Keystore Password",
                    "short_desc": "The store password for the key store file",
                    "type": "string",
                    "desc": "The store password for the key store file. This is optional for client and only needed "
                        "if `ssl.keystore.location` is configured.",
                },
                "ssl.keystore.type": <ConnectionOptionInfo>{
                    "display_name": "SSL Keystore Type",
                    "short_desc": "The file format of the key store file",
                    "type": "string",
                    "desc": "The file format of the key store file",
                    "allowed_values": (
                        <AllowedValueInfo>{
                            "value": "JKS",
                        },
                        <AllowedValueInfo>{
                            "value": "PKCS12",
                        },
                        <AllowedValueInfo>{
                            "value": "PEM",
                        },
                    ),
                    "default_value": "PEM",
                },
                "ssl.truststore.location": <ConnectionOptionInfo>{
                    "display_name": "SSL Truststore Location",
                    "short_desc": "The location of the trust store file",
                    "type": "string",
                    "desc": "The location of the trust store file",
                },
                "ssl.truststore.password": <ConnectionOptionInfo>{
                    "display_name": "SSL Truststore Password",
                    "short_desc": "The password for the trust store file",
                    "type": "string",
                    "desc": "The password for the trust store file. If a password is not set, access to the "
                        "truststore is still available, but integrity checking is disabled",
                    "sensitive": True,
                },
                "ssl.protocol": <ConnectionOptionInfo>{
                    "display_name": "SSL Protocol",
                    "short_desc": "The SSL protocol to use",
                    "type": "string",
                    "desc": "The SSL protocol used to generate the `SSLContext`.\n\nThe default is `TLSv1.3`. This "
                        "value should be fine for most use cases.\n\nWith the default "
                        "value for this config and `ssl.enabled.protocols`, clients will downgrade to `TLSv1.2` if "
                        "the server does not support `TLSv1.3`.\n\nIf this config is set to `TLSv1.2`, clients will "
                        "not use `TLSv1.3` even if it is one of the values in `ssl.enabled.protocols` and the server "
                        "only supports `TLSv1.3`",
                    "allowed_values": (
                        <AllowedValueInfo>{
                            "value": "TLSv1.3",
                        },
                        <AllowedValueInfo>{
                            "value": "TLSv1.2",
                        },
                    ),
                    "default_value": "TLSv1.3",
                },
                "ssl.provider": <ConnectionOptionInfo>{
                    "display_name": "SSL Provider",
                    "short_desc": "The name of the security provider used for SSL connections",
                    "type": "string",
                    "desc": "The name of the security provider used for SSL connections. Default value is the "
                        "default security provider of the JVM",
                },
                "ssl.truststore.type": <ConnectionOptionInfo>{
                    "display_name": "SSL Truststore Type",
                    "short_desc": "The file format of the trust store file",
                    "type": "string",
                    "desc": "The file format of the trust store file",
                    "allowed_values": (
                        <AllowedValueInfo>{
                            "value": "JKS",
                        },
                        <AllowedValueInfo>{
                            "value": "PKCS12",
                        },
                        <AllowedValueInfo>{
                            "value": "PEM",
                        },
                    ),
                    "default_value": "PEM",
                },
                "ssl.enabled.protocols": <ConnectionOptionInfo>{
                    "display_name": "SSL Protocols",
                    "short_desc": "A comma-separated list of protocols enabled for SSL connections",
                    "type": "string",
                    "desc": "A comma-separated list of protocols enabled for SSL connections.\n\nThe default is "
                        "`TLSv1.2,TLSv1.3`.\n\nWith the default value, clients and servers will prefer `TLSv1.3` if "
                        "both support it and fallback to `TLSv1.2` otherwise (assuming both support at least "
                        "`TLSv1.2`). This default should be fine for most cases. Also see the config documentation "
                        "for `ssl.protocol`",
                    "default_value": "TLSv1.2,TLSv1.3",
                },
                "ssl.cipher.suites": <ConnectionOptionInfo>{
                    "display_name": "SSL Cipher Suites",
                    "short_desc": "A comma-separated list of cipher suites",
                    "type": "string",
                    "desc": "A comma-separated list of cipher suites. This is a named combination of authentication, "
                        "encryption, MAC and key exchange algorithm used to negotiate the security settings for a "
                        "network connection using TLS or SSL network protocol.\n\nBy default all the available "
                        "cipher suites are supported",
                },
                "ssl.endpoint.identification.algorithm": <ConnectionOptionInfo>{
                    "display_name": "SSL Endpoint Identification Algorithm",
                    "short_desc": "The endpoint identification algorithm to validate server hostname using server "
                        "certificate",
                    "type": "string",
                    "desc": "The endpoint identification algorithm to validate server hostname using server "
                        "certificate",
                },
                "ssl.engine.factory.class": <ConnectionOptionInfo>{
                    "display_name": "SSL Engine Factory Class",
                    "short_desc": "The class to provide SSLEngine objects",
                    "type": "string",
                    "desc": "The class of type `org.apache.kafka.common.security.auth.SslEngineFactory` to provide "
                        "`SSLEngine` objects",
                    "default_value": "org.apache.kafka.common.security.ssl.DefaultSslEngineFactory",
                },
                "ssl.keymanager.algorithm": <ConnectionOptionInfo>{
                    "display_name": "SSL Keymanager Algorithm",
                    "short_desc": "The algorithm used by key manager factory for SSL connections",
                    "type": "string",
                    "desc": "The algorithm used by key manager factory for SSL connections. The default value is the "
                        "key manager factory algorithm configured for the Java Virtual Machine",
                },
                "ssl.secure.random.implementation": <ConnectionOptionInfo>{
                    "display_name": "SSL SecureRandom Implementation",
                    "short_desc": "The SecureRandom PRNG implementation to use for SSL cryptography operations",
                    "type": "string",
                    "desc": "The SecureRandom PRNG implementation to use for SSL cryptography operations",
                },
                "ssl.trustmanager.algorithm": <ConnectionOptionInfo>{
                    "display_name": "SSL Trustmanager Algorithm",
                    "short_desc": "The algorithm used by trust manager factory for SSL connections",
                    "type": "string",
                    "desc": "The algorithm used by trust manager factory for SSL connections. The default value is "
                        "the trust manager factory algorithm configured for the Java Virtual Machine",
                },
                "sasl.client.callback.handler.class": <ConnectionOptionInfo>{
                    "display_name": "SASL Client Callback Handler Class",
                    "short_desc": "The fully qualified name of a SASL client callback handler class",
                    "type": "string",
                    "desc": "The fully qualified name of a SASL client callback handler class that implements the "
                        "`AuthenticateCallbackHandler` interface",
                },
                "sasl.jaas.config": <ConnectionOptionInfo>{
                    "display_name": "SASL JAAS Config",
                    "short_desc": "JAAS login context parameters for SASL connections",
                    "type": "string",
                    "desc": "JAAS login context parameters for SASL connections in the format used by JAAS "
                        "configuration files.\n\nJAAS configuration file format is described here. The format for "
                        "the value is: `loginModuleClass controlFlag (optionName=optionValue)*;`.\n\nFor brokers, "
                        "the config must be prefixed with listener prefix and SASL mechanism name in lower-case.\n\n"
                        "`listener.name.sasl_ssl.scram-sha-256.sasl.jaas.config=com.example.ScramLoginModule "
                        "required;`",
                },
                "sasl.kerberos.service.name": <ConnectionOptionInfo>{
                    "display_name": "SASL Kerberos Service Name",
                    "short_desc": "The Kerberos principal name that Kafka runs as",
                    "type": "string",
                    "desc": "The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's "
                        "JAAS config or in Kafka's config",
                },
                "sasl.login.callback.handler.class": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Callback Handler Class",
                    "short_desc": "The fully qualified name of a SASL login callback handler class",
                    "type": "string",
                    "desc": "The fully qualified name of a SASL login callback handler class that implements the "
                        "`AuthenticateCallbackHandler` interface.\n\nFor brokers, the login callback handler config "
                        "must be prefixed with the listener prefix and the SASL mechanism name in lower-case.\n\nFor "
                        "example, `listener.name.sasl_ssl.scram-sha-256.sasl.login.callback.handler.class="
                        "com.example.CustomScramLoginCallbackHandler`",
                },
                "sasl.login.class": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Class",
                    "short_desc": "The fully qualified name of a class that implements the Login interface",
                    "type": "string",
                    "desc": "The fully qualified name of a class that implements the Login interface. For brokers, "
                        "the login config must be prefixed with the listener prefix and the SASL mechanism name in "
                        "lower-case.\n\nFor example, "
                        "`listener.name.sasl_ssl.scram-sha-256.sasl.login.class=com.example.CustomScramLogin`",
                },
                "sasl.mechanism": <ConnectionOptionInfo>{
                    "display_name": "SASL Mechanism",
                    "short_desc": "SASL mechanism used for client connections",
                    "type": "string",
                    "desc": "SASL mechanism used for client connections. This may be any mechanism for which a "
                        "security provider is available. GSSAPI is the default mechanism",
                    "default_value": "GSSAPI",
                },
                "sasl.kerberos.kinit.cmd": <ConnectionOptionInfo>{
                    "display_name": "SASL Kerberos Kinit Command Path",
                    "short_desc": "Kerberos kinit command path",
                    "type": "string",
                    "desc": "Kerberos kinit command path",
                    "default_value": "/usr/bin/kinit",
                },
                "sasl.kerberos.min.time.before.relogin": <ConnectionOptionInfo>{
                    "display_name": "SASL Kerberos Minimum Relogin Time",
                    "short_desc": "Login thread sleep time between refresh attempts in milliseconds",
                    "type": "int",
                    "desc": "Login thread sleep time between refresh attempts in milliseconds",
                    "default_value": 60000,
                },
                "sasl.kerberos.ticket.renew.jitter": <ConnectionOptionInfo>{
                    "display_name": "SASL Kerberos Ticket Renew Jitter",
                    "short_desc": "Percentage of random jitter added to the renewal time",
                    "type": "float",
                    "desc": "Percentage of random jitter added to the renewal time",
                    "default_value": 0.05,
                },
                "sasl.kerberos.ticket.renew.window.factor": <ConnectionOptionInfo>{
                    "display_name": "SASL Kerberos Ticket Renew Window Factor",
                    "short_desc": "Login thread sleep window factor time from the last ticket expiry",
                    "type": "float",
                    "desc": "Login thread will sleep until the specified window factor of time from last refresh to "
                        "ticket's expiry has been reached, at which time it will try to renew the ticket",
                    "default_value": 0.8,
                },
                "sasl.login.refresh.buffer.seconds": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Refresh Buffer Seconds",
                    "short_desc": "The amount of buffer time before credential expiration to maintain when "
                        "refreshing a credential, in seconds",
                    "type": "int",
                    "desc": "The amount of buffer time before credential expiration to maintain when refreshing a "
                        "credential, in seconds.\n\nIf a refresh would otherwise occur closer to expiration than the "
                        "number of buffer seconds then the refresh will be moved up to maintain as much of the "
                        "buffer time as possible.\n\nLegal values are between `0` and `3600` (1 hour); a default "
                        "value of `300` (5 minutes) is used if no value is specified.\n\nThis value and "
                        "`sasl.login.refresh.min.period.seconds` are both ignored if their sum exceeds the remaining "
                        "lifetime of a credential.\n\nCurrently applies only to `OAUTHBEARER`",
                    "default_value": 300,
                },
                "sasl.login.refresh.min.period.seconds": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Refresh Minimum Period Seconds",
                    "short_desc": "The desired minimum time for the login refresh thread to wait before refreshing a "
                        "credential, in seconds",
                    "type": "int",
                    "desc": "The desired minimum time for the login refresh thread to wait before refreshing a "
                        "credential, in seconds.\n\nLegal values are between `0` and `900` (15 minutes); a default "
                        "value of `60` (1 minute) is used if no value is specified.\n\nThis value and "
                        "`sasl.login.refresh.buffer.seconds` are both ignored if their sum exceeds the remaining "
                        "lifetime of a credential.\n\nCurrently applies only to `OAUTHBEARER`",
                    "default_value": 60,
                },
                "sasl.login.refresh.window.factor": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Refresh Window Factor",
                    "short_desc": "Login refresh thread sleep factor relative to the credential's lifetime",
                    "type": "float",
                    "desc": "Login refresh thread will sleep until the specified window factor relative to the "
                        "credential's lifetime has been reached, at which time it will try to refresh the "
                        "credential.\n\nLegal values are between `0.5` (50%) and `1.0` (100%) inclusive; a default "
                        "value of `0.8` (80%) is used if no value is specified.\n\nCurrently applies only to "
                        "`OAUTHBEARER`",
                    "default_value": 0.8,
                },
                "sasl.login.refresh.window.jitter": <ConnectionOptionInfo>{
                    "display_name": "SASL Login Refresh Window Jitter",
                    "short_desc": "Maximum jitter relative to the credential's lifetime added to the login refresh "
                        "thread's sleep time",
                    "type": "float",
                    "desc": "The maximum amount of random jitter relative to the credential's lifetime added to the "
                        "login refresh thread's sleep time.\n\nLegal values are between `0` and `0.25` (25%) "
                        "inclusive; a default value of `0.05` (5%) is used if no value is specified.\n\nCurrently "
                        "applies only to `OAUTHBEARER`",
                    "default_value": 0.05,
                },
                "batch.size": <ConnectionOptionInfo>{
                    "display_name": "Batch Size",
                    "short_desc": "The default batch size in bytes",
                    "type": "int",
                    "desc": "The producer will attempt to batch records together into fewer requests whenever "
                        "multiple records are being sent to the same partition. This helps performance on both the "
                        "client and the server. This configuration controls the default batch size in bytes.\n\nNo "
                        "attempt will be made to batch records larger than this size.\n\nRequests sent to brokers "
                        "will contain multiple batches, one for each partition with data available to be sent.\n\nA "
                        "small batch size will make batching less common and may reduce throughput (a batch size of "
                        "zero will disable batching entirely). A very large batch size may use memory a bit more "
                        "wastefully as we will always allocate a buffer of the specified batch size in anticipation "
                        "of additional records",
                },
            },
            "schemes": {
                "kafka": True,
            },
        };
    }

    #! Creates the object from the arguments
    constructor(string name, string desc, string url, hash<auto> attr = {}, hash<auto> options = {},
            *LoggerInterface logger) : AbstractConnectionWithInfo(name, desc, url, attr, options, logger) {
        setRealUrl(url);
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
        return "kafka";
    }

    #! Returns @ref True, as this connection class returns a data provider with the @ref getDataProvider() method
    /** @return @ref True, as this connection class returns a data provider with the @ref getDataProvider() method

        @see @ref getDataProvider()
    */
    bool hasDataProvider() {
        return True;
    }

    #! Returns the data provider application name this connection serves
    /** @return the data provider application name this connection serves
    */
    *string getAppName() {
        return "Kafka";
    }

    #! Returns a @ref KafkaDataProvider object for this connection
    /** @param constructor_options any additional constructor options for the data provider

        @return a data provider object for this connection, if supported
    */
    AbstractDataProvider getDataProvider(*hash<auto> constructor_options) {
        return new KafkaDataProvider(self, constructor_options);
    }

    #! Returns options that can be used to create Kafka data providers
    private hash<auto> getKafkaOptions() {
        hash<auto> opts = self.opts + {
            "bootstrap.servers": real_url,
        };
        if (self.opts.bootstrap_servers) {
            opts."bootstrap.servers" += "," + self.opts.bootstrap_servers.join(",");
        }
        return opts;
    }

    #! Returns the ConnectionSchemeInfo hash for this object
    private hash<ConnectionSchemeInfo> getConnectionSchemeInfoImpl() {
        return ConnectionScheme;
    }

    #! Returns a hash of data provider constructor option info, if applicable
    /** @return a hash of data provider constructor option info for the primary data provider, if applicable
    */
    /*
    private *hash<string, hash<DataProviderOptionInfo>> getDataProviderConstructorOptionsImpl() {
        return KafkaDataProvider::ConstructorOptions;
    }
    */

    #! Override to set child data provider capabilities once per child class
    private setChildCapabilities() {
        children_can_support_observers = True;
        children_can_support_messages = True;
    }

    #! this method must return the connection object corresponding to the object's configuration
    /** @param connect if @ref True "True" then the connection should be returned connected (and if possible a
        round-trip "ping" of the server should be made)
        @param rtopts options to override connection options

        @return the connection object
    */
    private KafkaConnectionWrapper getImpl(bool connect = True, *hash<auto> rtopts) {
        return new KafkaConnectionWrapper(getKafkaOptions());
    }

    #! Called by the constructor to set the real URL
    /** @path the new URL

        Sets the real Kafka URL in host:port format
    */
    private setRealUrl(*string url) {
        if (exists url) {
            real_url = url;
            if (url =~ /^kafka:\/\//) {
                real_url =~ s/^kafka:\/\///;
            }
        }
    }
}
}

