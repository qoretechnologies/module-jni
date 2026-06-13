# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaDataProvider.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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
#! Event- and message-based data provider for KAFKA client-side events
/**
*/
public class KafkaDataProvider inherits KafkaDataProviderBase {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "kafka",
            "desc": "Parent data provider for KAFKA messaging functionality",
            "supports_children": True,
            "children_can_support_apis": True,
            "children_can_support_observers": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options used to create the shared Kafka connection
        const ConnectionConstructorOptions = {
            "asyncapi": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Schema",
                "short_desc": "AsyncAPI schema URL or file path",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI schema URL or file path used by child producer and consumer providers to "
                    "validate Kafka message values. This option conflicts with `validator`",
            },
            "asyncapi_channel": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Channel",
                "short_desc": "AsyncAPI channel for validation",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI channel name to use when `asyncapi` or `validator` is configured",
            },
            "server": <DataProviderOptionInfo>{
                "display_name": "Server",
                "short_desc": "The server in host:port form to connect to",
                "desc": "The server in `host:port` form to connect to",
                "type": AbstractDataProviderTypeMap."string",
                "required": True,
            },
            "bootstrap.servers": <DataProviderOptionInfo>{
                "display_name": "Bootstrap Servers",
                "short_desc": "One or more additional servers to use to connect to the Kafka cluster",
                "desc": "A list of `host:port` pair strings to use for establishing the initial "
                    "connection to the Kafka cluster. The client will make use of all servers irrespective of which "
                    "servers are specified here for bootstrapping; this list only impacts the initial "
                    "hosts used to discover the full set of servers.\n\nThis list should be in the form "
                    "`host1:port1,host2:port2,...`\n\nSince these servers are just used for the initial "
                    "connection to discover the full cluster membership (which may change dynamically), this "
                    "list need not contain the full set of servers (you may want more than one, though, in case "
                    "a server is down).",
                "type": AbstractDataProviderTypeMap."list",
            },
            "validator": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Validator",
                "short_desc": "AsyncAPI schema location or file data",
                "type": AbstractDataProviderType::get(StringType, NOTHING, {
                    DTT_FromFile: True,
                    DTT_FromLocation: True,
                }),
                "desc": "An AsyncAPI schema location or schema file data used by child producer and consumer "
                    "providers to validate Kafka message values. This option conflicts with `asyncapi` and "
                    "requires `asyncapi_channel`",
            },
        };

        #! Child provider options accepted at the root factory boundary for forwarding to children
        const ChildConstructorOptions =
            KafkaConsumerDataProvider::ConstructorOptions - ("asyncapi", "asyncapi_channel", "validator",
                "topics") + {
                    "topics": KafkaConsumerDataProvider::ConstructorOptions.topics - "required",
                }
            + KafkaProducerDataProvider::ConstructorOptions - ("asyncapi", "asyncapi_channel", "validator");

        #! Constructor options accepted by the factory
        const ConstructorOptions = ConnectionConstructorOptions + ChildConstructorOptions;

        #! Child data provider map
        const ChildMap = {
            "consumer": Class::forName("KafkaConsumerDataProvider"),
            "producer": Class::forName("KafkaProducerDataProvider"),
        };
    }

    private {
        *hash<auto> opts;
    }

    private static hash<auto> makeConnectionConfig(*hash<auto> opts) {
        hash<auto> conn_opts = opts ? opts{keys ConnectionConstructorOptions} : {};
        return {
            "name": "kafka-connection",
            "url": remove conn_opts.server,
            "opts": conn_opts,
        };
    }

    constructor(*hash<auto> opts) : KafkaDataProviderBase(
            new KafkaConnection(KafkaDataProvider::makeConnectionConfig(opts))) {
        self.opts = opts ? opts - keys ConnectionConstructorOptions : NOTHING;
        @debug {
            LoggerWrapper::debug("KafkaDataProvider::constructor() opts: %y", self.opts);
        }
    }

    #! Creates the object from a connection object
    constructor(KafkaConnection conn, *hash<auto> opts) : KafkaDataProviderBase(conn) {
        self.opts = opts;
    }

    string getName() {
        return "kafka";
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
    /** @return a list of child data provider names, if any
    */
    private *list<string> getChildProviderNamesImpl() {
        return keys ChildMap;
    }

    #! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown

        @see getChildProviderEx()
    */
    private *DataProvider::AbstractDataProvider getChildProviderImpl(string name) {
        if (*Class cls = ChildMap{name}) {
            return cls.newObject(conn, opts);
        }
    }
}
}
