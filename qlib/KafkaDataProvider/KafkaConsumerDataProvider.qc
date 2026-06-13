# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaConsumerDataProvider.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

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

#%module-cmd(jni) import org.apache.kafka.clients.consumer.KafkaConsumer
#%module-cmd(jni) import org.apache.kafka.common.errors.WakeupException
#%module-cmd(jni) import org.apache.kafka.clients.consumer.ConsumerRecords
#%module-cmd(jni) import org.apache.kafka.clients.consumer.ConsumerRecord
#%module-cmd(jni) import org.apache.kafka.common.record.TimestampType
#%module-cmd(jni) import org.apache.kafka.common.header.Headers
#%module-cmd(jni) import org.apache.kafka.common.header.Header

%module-cmd(jni) import org.qore.dataprovider.kafka.QoreKafkaConsumer

#! Contains all public definitions in the KafkaDataProvider module
public namespace KafkaDataProvider {
#! Event-based data provider for Kafka events
/**
*/
public class KafkaConsumerDataProvider inherits DataProvider::Observer, KafkaDataProviderBase,
        DataProvider::DelayedObservable {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "consumer",
            "desc": "Data provider for listening to KAFKA messages",
            "supports_observable": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "topics": <DataProviderOptionInfo>{
                "display_name": "Topics",
                "short_desc": "One or more topics to subscribe to; must be a list of strings",
                "type": new SoftListDataType("KafkaTopicList", StringType),
                "desc": "One or more topics to subscribe to; must be a list of strings",
                "required": True,
            },
            "asyncapi": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Schema",
                "short_desc": "AsyncAPI schema URL or file path",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI schema URL or file path used to validate incoming Kafka message values. "
                    "This option conflicts with `validator`",
            },
            "asyncapi_channel": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Channel",
                "short_desc": "AsyncAPI channel for validation",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "AsyncAPI channel name to use when `asyncapi` or `validator` is configured",
            },
            "key.deserializer": <DataProviderOptionInfo>{
                "display_name": "Key Deserializer",
                "short_desc": "Serializer / deserializer class for Kafka messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Serializer class for key that implements the "
                    "`org.apache.kafka.common.serialization.Deserializer` interface",
                "default_value": "org.apache.kafka.common.serialization.StringDeserializer",
            },
            "value.deserializer": <DataProviderOptionInfo>{
                "display_name": "Value Deserializer",
                "short_desc": "Serializer / deserializer class for Kafka messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Serializer class for key that implements the "
                    "`org.apache.kafka.common.serialization.Deserializer` interface",
                "default_value": "org.apache.kafka.common.serialization.StringDeserializer",
            },
            "max.poll.interval.ms": <DataProviderOptionInfo>{
                "display_name": "Max Poll Interval",
                "short_desc": "The maximum delay in milliseconds between invocations of poll()",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum delay between invocations of `poll()` when using consumer group management. "
                    "This places an upper bound on the amount of time that the consumer can be idle before fetching "
                    "more records. If `poll()` is not called before expiration of this timeout, then the consumer is "
                    "considered failed and the group will rebalance in order to reassign the partitions to another "
                    "member. For consumers using a non-null `group.instance.id` which reach this timeout, partitions "
                    "will not be immediately reassigned. Instead, the consumer will stop sending heartbeats and "
                    "partitions will be reassigned after expiration of `session.timeout.ms`. This mirrors the "
                    "behavior of a static consumer which has shutdown",
                "default_value": 300000,
            },
            "enable.auto.commit": <DataProviderOptionInfo>{
                "display_name": "Enable Auto Commit",
                "short_desc": "If True the consumer's offset will be periodically committed in the background",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "If `True` the consumer's offset will be periodically committed in the background",
                "default_value": True,
            },
            "auto.offset.reset": <DataProviderOptionInfo>{
                "display_name": "Auto Offset Reset",
                "short_desc": "What to do when there is no initial offset in Kafka or if the current offset does not "
                    "exist any more on the server",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "What to do when there is no initial offset in Kafka or if the current offset does not exist "
                    "any more on the server (e.g. because that data has been deleted).\n\n"
                    "Note that altering partition numbers while setting this config to latest may cause message "
                    "delivery loss since producers could start to send messages to newly added partitions (i.e. no "
                    "initial offsets exist yet) before consumers reset their offsets",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "display_name": "latest",
                        "short_desc": "Automatically reset the offset to the latest offset",
                        "desc": "Automatically reset the offset to the latest offset",
                        "value": "latest",
                    },
                    <AllowedValueInfo>{
                        "display_name": "earliest",
                        "short_desc": "Automatically reset the offset to the earliest offset",
                        "desc": "Automatically reset the offset to the earliest offset",
                        "value": "earliest",
                    },
                    <AllowedValueInfo>{
                        "display_name": "none",
                        "short_desc": "Throw exception to the consumer if no previous offset is found for the "
                            "consumer's group",
                        "desc": "Throw exception to the consumer if no previous offset is found for the consumer's "
                            "group",
                        "value": "none",
                    },
                ),
                "default_value": "latest",
            },
            "fetch.min.bytes": <DataProviderOptionInfo>{
                "display_name": "Fetch Min Bytes",
                "short_desc": "The minimum amount of data the server should return for a fetch request",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The minimum amount of data the server should return for a fetch request.\n\nIf insufficient "
                    "data is available, the request will wait for that much data to accumulate before answering the "
                    "request.\n\nThe default setting of 1 byte means that fetch requests are answered as soon as a "
                    "single byte of data is available or the fetch request times out waiting for data to arrive.\n\n"
                    "Setting this to something greater than `1` will cause the server to wait for larger amounts of "
                    "data to accumulate which can improve server throughput a bit at the cost of some additional "
                    "latency",
                "default_value": 1,
            },
            "group.id": <DataProviderOptionInfo>{
                "display_name": "Group ID",
                "short_desc": "The ID of the consumer group this consumer belongs to",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "A unique string that identifies the consumer group this consumer belongs to.\n\nThis "
                    "property is required if the consumer uses either the group management functionality by using "
                    "`subscribe(topic)` or the Kafka-based offset management strategy",
            },
            "heartbeat.interval.ms": <DataProviderOptionInfo>{
                "display_name": "Heartbeat Interval",
                "short_desc": "The expected time between heartbeats to the consumer coordinator in milliseconds",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The expected time in milliseconds between heartbeats to the consumer coordinator when using "
                    "Kafka's group management facilities.\n\nHeartbeats are used to ensure that the consumer's "
                    "session stays active and to facilitate rebalancing when new consumers join or leave the "
                    "group.\n\nThe value must be set lower than `session.timeout.ms`, but typically should be set no "
                    "higher than 1/3 of that value.\n\nIt can be adjusted even lower to control the expected time "
                    "for normal rebalances",
                "default_value": 3000,
            },
            "max.partition.fetch.bytes": <DataProviderOptionInfo>{
                "display_name": "Max Partition Fetch Bytes",
                "short_desc": "The maximum amount of data per-partition the server will return",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum amount of data per-partition the server will return.\n\nRecords are fetched in "
                    "batches by the consumer.\n\nIf the first record batch in the first non-empty partition of the "
                    "fetch is larger than this limit, the batch will still be returned to ensure that the consumer "
                    "can make progress.\n\nThe maximum record batch size accepted by the broker is defined via "
                    "`message.max.bytes` (broker config) or `max.message.bytes` (topic config).\n\nSee "
                    "`fetch.max.bytes` for limiting the consumer request size",
                "default_value": 1048576,
            },
            "session.timeout.ms": <DataProviderOptionInfo>{
                "display_name": "Session Timeout",
                "short_desc": "The timeout in milliseconds used to detect client failures",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The timeout used to detect client failures when using Kafka's group management facility.\n\n"
                    "The client sends periodic heartbeats to indicate its liveness to the broker.\n\n"
                    "If no heartbeats are received by the broker before the expiration of this session timeout, then "
                    "the broker will remove this client from the group and initiate a rebalance.\n\nNote that the "
                    "value must be in the allowable range as configured in the broker configuration by "
                    "`group.min.session.timeout.ms` and `group.max.session.timeout.ms`",
                "default_value": 10000,
            },
            "validator": <DataProviderOptionInfo>{
                "display_name": "AsyncAPI Validator",
                "short_desc": "AsyncAPI schema location or file data",
                "type": AbstractDataProviderType::get(StringType, NOTHING, {
                    DTT_FromFile: True,
                    DTT_FromLocation: True,
                }),
                "desc": "An AsyncAPI schema location or schema file data used to validate incoming Kafka message "
                    "values. This option conflicts with `asyncapi` and requires `asyncapi_channel`",
            },
        };

        #! Kafka message event constant
        const EVENT_KAFKA_MESSAGE = "kafka-message";
    }

    private {
        QoreKafkaConsumer cons;
        Mutex event_m();
        WaitGroup event_wg();
        bool events_started = False;
        bool events_closed = False;
    }

    #! Creates the object from a connection object
    constructor(KafkaConnection conn, *hash<auto> options) : KafkaDataProviderBase(conn) {
        @debug {
            LoggerWrapper::debug("KafkaConsumerDataProvider::constructor() options: %y", options);
        }
        *hash<auto> copts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions,
            DataProviderDataContextHelper::getHash(){keys ConstructorOptions} + options) + conn.getKafkaOptions();
        configureAsyncApiOptions(copts);
        removeAsyncApiOptions(\copts);
        # add default values automatically
        map copts{$1.key} = $1.value.default_value, ConstructorOptions.pairIterator(),
            exists $1.value.default_value && !copts.hasKey($1.key);
        cons = new QoreKafkaConsumer(copts ?? {}, EVENT_KAFKA_MESSAGE);
    }

    destructor() {
        if (cons) {
            stopEvents();
        }
    }

    string getName() {
        return "consumer";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Called when all observers have been added to the object
    /** This method is meant to trigger event generation
    */
    observersReady() {
        {
            AutoLock al(event_m);
            if (events_started || events_closed) {
                return;
            }
            events_started = True;
            event_wg.add();
        }
        background sub () {
            on_exit {
                closeConsumer();
                {
                    AutoLock al(event_m);
                    events_started = False;
                }
                event_wg.done();
            }
            cons.start();
            while (cons.isRunning()) {
                foreach hash<auto> data in ((cons.poll(1000))) {
                    update(EVENT_KAFKA_MESSAGE, data);
                }
            }
        }();
    }

    #! Called to stop generating events
    /** Stops polling for new events
    */
    stopEvents() {
        bool wait;
        {
            AutoLock al(event_m);
            wait = events_started;
        }
        if (!wait) {
            closeConsumer();
            return;
        }
        cons.stop();
        event_wg.wait();
    }

    private closeConsumer() {
        bool close;
        {
            AutoLock al(event_m);
            close = !events_closed;
            events_closed = True;
        }
        if (close) {
            cons.done();
        }
    }

    #! This method is called when an event is raised
    /**
        @param event_id unique ID identifying an event type or class
        @param data_ the data generated by the Observable event source
    */
    update(string event_id, hash<auto> data_) {
        if (event_id == EVENT_KAFKA_MESSAGE) {
            auto value = data_.value;
            validateIncomingMessage(\value, data_.headers ?? {});
            data_.value = value;
        }
        # pass on the event to our observers
        notifyObservers(event_id, data_);
    }

    #! Returns a hash of all supported event types
    /** @return a hash of all supported event types

        @note only called if the provider supports the observer pattern / event API
    */
    private hash<string, hash<DataProviderMessageInfo>> getEventTypesImpl() {
        return {
            EVENT_KAFKA_MESSAGE: <DataProviderMessageInfo>{
                "desc": "This event is raised when an incoming Kafka message matching the subscription topic arrives",
                "type": KafkaEventRecvDataType,
            },
        };
    }
}
}
