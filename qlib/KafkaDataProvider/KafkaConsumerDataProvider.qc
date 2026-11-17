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
/** @anchor kafkaconsumerdataprovider_delivery
    @par Delivery and commits
    Records are delivered to the observers in the order of each partition, and a record's offset is committed only after
    every observer has accepted it (at-least-once delivery): \c enable.auto.commit is always off, and the provider
    commits the offsets of the delivered records of each poll with \c commitSync().  An observer refuses a record by
    throwing from its \c update() method (it reaches the provider as an \c OBSERVER-NOTIFICATION-ERROR); a record whose
    value fails AsyncAPI validation is refused as well.  A refused record is not lost and does not stop the consumer:
    each partition with records that were not delivered is set back to its first undelivered record, and they are
    delivered again, in order, after a back-off that starts at \c refusal_delay_ms and doubles with each consecutive
    refusal up to \c max_refusal_delay_ms; the first successful delivery resets the back-off. A record that can never be
    accepted (for example, one that fails validation) is therefore delivered again at the longest back-off until it is
    accepted: as every assigned partition is paused during a back-off, the whole consumer then delivers at most one poll
    per \c max_refusal_delay_ms.  The consumer keeps polling during a back-off, so that it stays in its group.  A
    redelivered record goes to every observer again, so an observer that accepted it before another one refused it sees
    it again.  If the partitions are reassigned before a refused record is delivered, the consumer that gets them starts
    at the last committed offset, which also delivers the record again.  A commit that fails because the partitions were
    reassigned, or with a retriable error, is logged: a later commit of the partition covers its records, and they are
    delivered again only if the partition is reassigned or the consumer restarts first; any other error of the Kafka
    client is logged and stops the consumer (qore#6395).
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
                "short_desc": "Ignored: the provider commits the offset of each record after its delivery",
                "type": AbstractDataProviderTypeMap."bool",
                "desc": "Ignored: the offsets are never committed in the background; the provider commits the "
                    "offset of a record only after every observer has accepted it (at-least-once delivery), so that "
                    "a record that an observer refuses is delivered again instead of being lost",
                "default_value": False,
            },
            "refusal_delay_ms": <DataProviderOptionInfo>{
                "display_name": "Refusal Delay",
                "short_desc": "The initial delay in milliseconds before a refused record is delivered again",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The delay in milliseconds before a record that an observer refused is delivered again; it "
                    "doubles with each consecutive refusal up to `max_refusal_delay_ms`, and the first successful "
                    "delivery resets it",
                "default_value": DefaultRefusalDelayMs,
            },
            "max_refusal_delay_ms": <DataProviderOptionInfo>{
                "display_name": "Max Refusal Delay",
                "short_desc": "The maximum delay in milliseconds before a refused record is delivered again",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The maximum delay in milliseconds before a record that an observer refused is delivered "
                    "again",
                "default_value": DefaultMaxRefusalDelayMs,
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

        #! The default initial delay in milliseconds before a refused record is delivered again
        const DefaultRefusalDelayMs = 1000;

        #! The default maximum delay in milliseconds before a refused record is delivered again
        const DefaultMaxRefusalDelayMs = 60000;

        #! The options of the provider that are not Kafka client properties
        const ProviderOptions = ("refusal_delay_ms", "max_refusal_delay_ms");

        #! The longest time in milliseconds that a poll waits during a back-off
        const BackOffPollMs = 250;
    }

    private {
        QoreKafkaConsumer cons;
        Mutex event_m();
        WaitGroup event_wg();
        bool events_started = False;
        bool events_closed = False;

        #! The initial delay in milliseconds before a refused record is delivered again
        int refusal_delay_ms = DefaultRefusalDelayMs;

        #! The maximum delay in milliseconds before a refused record is delivered again
        int max_refusal_delay_ms = DefaultMaxRefusalDelayMs;

        #! The number of refused deliveries
        int refusal_count = 0;

        #! The number of commits that failed without stopping the consumer
        int commit_error_count = 0;

        #! The number of delivered records whose offsets were committed
        int committed_count = 0;
    }

    #! Creates the object from a connection object
    constructor(KafkaConnection conn, *hash<auto> options) : KafkaDataProviderBase(conn) {
        # Java I/O that the sandbox does not control: sandboxed code is refused (qore#5632)
        DataProviderActionCatalog::checkSandboxAccess(KafkaDataProvider::AppName);
        @debug {
            LoggerWrapper::debug("KafkaConsumerDataProvider::constructor() options: %y", options);
        }
        *hash<auto> copts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions,
            DataProviderDataContextHelper::getHash(){keys ConstructorOptions} + options) + conn.getKafkaOptions();
        # before anything else is done with the options: they can name classes for the client to instantiate
        # (qore#5742)
        KafkaDataProviderBase::checkClassOptions(copts ?? {});
        configureAsyncApiOptions(copts);
        removeAsyncApiOptions(\copts);
        # add default values automatically
        map copts{$1.key} = $1.value.default_value, ConstructorOptions.pairIterator(),
            exists $1.value.default_value && !copts.hasKey($1.key);
        refusal_delay_ms = max(1, copts.refusal_delay_ms ?? DefaultRefusalDelayMs);
        max_refusal_delay_ms = max(refusal_delay_ms, copts.max_refusal_delay_ms ?? DefaultMaxRefusalDelayMs);
        copts -= ProviderOptions;
        # the provider commits each record after its delivery (qore#6395)
        if (copts."enable.auto.commit") {
            Logger::LoggerWrapper::warn("KafkaConsumerDataProvider: the \"enable.auto.commit\" option is "
                "ignored: the provider commits the offset of each record after its delivery");
        }
        copts."enable.auto.commit" = False;
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
            try {
                cons.start();
                # consecutive refusals; gives the back-off before the next delivery
                int refusals = 0;
                while (cons.isRunning()) {
                    int rc = deliverRecords(cons.poll(1000));
                    if (rc > 0) {
                        refusals = 0;
                    } else if (rc < 0) {
                        backOff(++refusals);
                    }
                }
            } catch (hash<ExceptionInfo> ex) {
                Logger::LoggerWrapper::error("KafkaConsumerDataProvider: the consumer stops after an error of the "
                    "Kafka client: %s: %s", ex.err, ex.desc);
                rethrow;
            }
        }();
    }

    #! Returns delivery statistics
    /** @return a hash with the following keys:
        - \c refusals: the number of deliveries that an observer refused
        - \c committed: the number of delivered records whose offsets were committed
        - \c commit_errors: the number of commits that failed without stopping the consumer (a later commit of the
          partition covers their records)
    */
    hash<auto> getDeliveryStats() {
        AutoLock al(event_m);
        return {
            "refusals": refusal_count,
            "committed": committed_count,
            "commit_errors": commit_error_count,
        };
    }

    #! Delivers the records of a poll to the observers and commits the delivered ones
    /** see @ref kafkaconsumerdataprovider_delivery

        @return 1 if records were delivered, 0 if there were none, -1 if an observer refused one; then each partition
        with records that were not delivered is set back to its first undelivered record
    */
    private int deliverRecords(list<auto> recs) {
        # the next offset to commit of each partition with delivered records
        hash<string, hash<auto>> delivered = {};
        int rv = recs ? 1 : 0;
        foreach hash<auto> rec in (recs) {
            # after a stop request, the records that were not delivered are not committed and are delivered again
            # when the group consumes again
            if (!cons.isRunning()) {
                break;
            }
            try {
                update(EVENT_KAFKA_MESSAGE, rec);
            } catch (hash<ExceptionInfo> ex) {
                {
                    AutoLock al(event_m);
                    ++refusal_count;
                }
                Logger::LoggerWrapper::error("KafkaConsumerDataProvider: record at offset %d of partition %d of "
                    "topic %y refused; it will be delivered again: %s: %s", rec.offset, rec.partition, rec.topic,
                    ex.err, ex.desc);
                # the refused record and every record after it are delivered again, in the order of their
                # partitions
                hash<string, bool> seen = {};
                foreach hash<auto> r in (recs[$#..]) {
                    string key = sprintf("%s:%d", r.topic, r.partition);
                    if (!seen{key}) {
                        seen{key} = True;
                        cons.seek(r.topic, r.partition, r.offset);
                    }
                }
                rv = -1;
                break;
            }
            string key = sprintf("%s:%d", rec.topic, rec.partition);
            delivered{key} = {
                "topic": rec.topic,
                "partition": rec.partition,
                "offset": rec.offset + 1,
                "count": (delivered{key}.count ?? 0) + 1,
            };
        }
        commitDelivered(delivered);
        return rv;
    }

    #! Commits the positions after the delivered records
    private commitDelivered(hash<string, hash<auto>> delivered) {
        foreach hash<auto> pos in (delivered.iterator()) {
            # any error other than a reassignment of the partition or a retriable one is thrown and stops the
            # consumer
            if (cons.commit(pos.topic, pos.partition, pos.offset)) {
                AutoLock al(event_m);
                committed_count += pos.count;
                continue;
            }
            # the partition was reassigned, or a retriable error: a later commit of the partition covers the records,
            # unless the partition is reassigned or the consumer restarts first (at-least-once delivery)
            {
                AutoLock al(event_m);
                ++commit_error_count;
            }
            Logger::LoggerWrapper::error("KafkaConsumerDataProvider: cannot commit offset %d of partition %d of "
                "topic %y: %s", pos.offset, pos.partition, pos.topic, cons.getLastCommitError());
        }
    }

    #! Waits before a refused record is delivered again
    /** The partitions are paused and the consumer keeps polling, so that it stays in its group; records of
        partitions that are assigned meanwhile are set back to be delivered after the back-off.

        @param attempt the number of consecutive refusals
    */
    private backOff(int attempt) {
        int delay_ms = refusal_delay_ms;
        while (--attempt > 0 && delay_ms < max_refusal_delay_ms) {
            delay_ms *= 2;
        }
        delay_ms = min(delay_ms, max_refusal_delay_ms);
        date end = now_us() + milliseconds(delay_ms);
        cons.pauseAll();
        on_exit cons.resumeAll();
        while (cons.isRunning()) {
            int remaining_ms = ((end - now_us()).durationMilliseconds());
            if (remaining_ms <= 0) {
                break;
            }
            list<auto> recs = cons.poll(min(remaining_ms, BackOffPollMs));
            if (recs) {
                # records of partitions assigned during the back-off: they are delivered after it
                hash<string, bool> seen = {};
                foreach hash<auto> r in (recs) {
                    string key = sprintf("%s:%d", r.topic, r.partition);
                    if (!seen{key}) {
                        seen{key} = True;
                        cons.seek(r.topic, r.partition, r.offset);
                    }
                }
                cons.pauseAll();
            }
        }
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
