# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaProducerDataProvider.qc Copyright 2022 - 2025 Qore Technologies, s.r.o.

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


%module-cmd(jni) import org.qore.dataprovider.kafka.QoreKafkaProducer

#! contains all public definitions in the KafkaDataProvider module
public namespace KafkaDataProvider {
#! Message-based data provider for Kafka messages
/**
*/
public class KafkaProducerDataProvider inherits KafkaDataProviderBase {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "producer",
            "desc": "Data provider for sending KAFKA messages",
            "supports_request": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "acks": <DataProviderOptionInfo>{
                "display_name": "Acknowledgement Handling",
                "short_desc": "Determines how the client handles acknowledgements from the server",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Determines how the client handles acknowledgements from the server:\n\n"
                    "- `0`: The producer will not wait for any acknowledgment from the server at all. The record "
                    "will be immediately added to the socket buffer and  considered sent. No guarantee can be made "
                    "that the server has received the record in this case, and the `retries` configuration will not "
                    "take effect (as the client won't generally know of any failures). The offset given back for "
                    "each record will always be set to `-1`\n\n"
                    "- `1`: The leader will write the record to its local log but will respond without awaiting full "
                    "acknowledgement from all followers. In this case should the leader fail immediately after "
                    "acknowledging the record but before the followers have replicated it then the record will be "
                    "lost\n\n"
                    "- `all`: This means the leader will wait for the full set of in-sync replicas to acknowledge "
                    "the record. This guarantees that the record will not be lost as long as at least one in-sync "
                    "replica remains alive. This is the strongest available guarantee. This is equivalent to the "
                    "`acks=-1` setting.",
                "default_value": "1",
            },
            "key.serializer": <DataProviderOptionInfo>{
                "display_name": "Key Serializer",
                "short_desc": "Serializer / deserializer class for Kafka messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Serializer class for key that implements the "
                    "`org.apache.kafka.common.serialization.Serializer` interface",
                "default_value": "org.apache.kafka.common.serialization.StringSerializer",
            },
            "value.serializer": <DataProviderOptionInfo>{
                "display_name": "Value Serializer",
                "short_desc": "Serializer / deserializer class for Kafka messages",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "Serializer class for key that implements the "
                    "`org.apache.kafka.common.serialization.Serializer` interface",
                "default_value": "org.apache.kafka.common.serialization.StringSerializer",
            },
            "buffer.memory": <DataProviderOptionInfo>{
                "display_name": "Buffer Memory",
                "short_desc": "The total bytes of memory the producer can use to buffer records waiting to be sent",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "The total bytes of memory the producer can use to buffer records waiting to be sent to the "
                    "server. If records are sent faster than they can be delivered to the server the producer will "
                    "block for `max.block.ms` after which it will throw an exception.\n\n"
                    "This setting should correspond roughly to the total memory the producer will use, but is not a "
                    "hard bound since not all memory the producer uses is used for buffering. Some additional memory "
                    "will be used for compression (if compression is enabled) as well as for maintaining in-flight "
                    "requests",
            },
            "compression.type": <DataProviderOptionInfo>{
                "display_name": "Compression",
                "short_desc": "The compression type for all data generated by the producer",
                "type": AbstractDataProviderTypeMap."string",
                "desc": "The compression type for all data generated by the producer. The default is `none` (i.e. no "
                    "compression). Compression is of full batches of data, so the efficacy of batching will also "
                    "impact the compression ratio (more batching means better compression)",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "value": "none",
                    },
                    <AllowedValueInfo>{
                        "value": "gzip",
                    },
                    <AllowedValueInfo>{
                        "value": "snappy",
                    },
                    <AllowedValueInfo>{
                        "value": "lz4",
                    },
                    <AllowedValueInfo>{
                        "value": "zstd",
                    },
                ),
                "default_value": "none",
            },
            "retries": <DataProviderOptionInfo>{
                "display_name": "Retries",
                "short_desc": "The number of retry attempts for failed sends",
                "type": AbstractDataProviderTypeMap."int",
                "desc": "Setting a value greater than zero will cause the client to resend any record whose send "
                    "fails with a potentially transient error. Note that this retry is no different than if the "
                    "client resent the record upon receiving the error.\n\n"
                    "Allowing retries without setting `max.in.flight.requests.per.connection` to `1` will "
                    "potentially change the ordering of records because if two batches are sent to a single "
                    "partition, and the first fails and is retried but the second succeeds, then the records in the "
                    "second batch may appear first.\n\n"
                    "Note additionally that produce requests will be failed before the number of retries has been "
                    "exhausted if the timeout configured by `delivery.timeout.ms` expires first before successful "
                    "acknowledgement. Users should generally prefer to leave this config unset and instead use "
                    "`delivery.timeout.ms` to control retry behavior",
            },
        };
    }

    private {
        QoreKafkaProducer prod;
    }

    #! Creates the object from constructor options
    constructor(KafkaConnection conn, *hash<auto> options) : KafkaDataProviderBase(conn) {
        *hash<auto> copts = checkOptions("CONSTRUCTOR-ERROR", ConstructorOptions,
            DataProviderDataContextHelper::getHash(){keys ConstructorOptions} + options) + conn.getKafkaOptions();
        # add default values automatically
        map copts{$1.key} = $1.value.default_value, ConstructorOptions.pairIterator(),
            exists $1.value.default_value && !copts.hasKey($1.key);
        prod = new QoreKafkaProducer(copts ?? {});
    }

    string getName() {
        return "producer";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response
    /** @param req the request to serialize and make according to the request type
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the response to the request
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        return prod.sendMessage(req);
    }

    #! Returns the description of a successful request message, if any
    /** @return the request type for this provider

        @note only called if the provider supports the request / response API
    */
    private *AbstractDataProviderType getRequestTypeImpl() {
        return KafkaEventDataType;
    }

    #! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message

        @note only called if the provider supports the request / response API
    */
    private *AbstractDataProviderType getResponseTypeImpl() {
        return KafkaMessageResponseType;
    }
}
}
