package org.qore.dataprovider.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.qore.jni.Hash;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.TimeZone;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.Instant;

/** Creates a KafkaProducer object based on configuration
 */
public class QoreKafkaProducer {
    /** The producer
     */
    protected KafkaProducer<Object, Object> prod;

    // describe the actual types, as kafa will convert from strings to anything, but insists on the correct base type
    /** keys are the actual configuration names, not the config item names
     */
    public static final Map<String, QoreJavaConfig.ConfType> PropTypeMap =
        Collections.unmodifiableMap(new HashMap<String, QoreJavaConfig.ConfType>() {
        {
            put("retries", QoreJavaConfig.ConfType.INT);
            put("batch.size", QoreJavaConfig.ConfType.INT);
            put("delivery.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("max.request.size", QoreJavaConfig.ConfType.INT);
            put("receive.buffer.bytes", QoreJavaConfig.ConfType.INT);
            put("request.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("send.buffer.bytes", QoreJavaConfig.ConfType.INT);
            put("max.in.flight.requests.per.connection", QoreJavaConfig.ConfType.INT);
            put("metrics.num.samples", QoreJavaConfig.ConfType.INT);
            put("sasl.login.refresh.buffer.seconds", QoreJavaConfig.ConfType.SHORT);
            put("sasl.login.refresh.min.period.seconds", QoreJavaConfig.ConfType.SHORT);
            put("transaction.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("org.apache.kafka.common.config.ConfigException", QoreJavaConfig.ConfType.SHORT);
        }
    });

    /** Creates the object from configuration in config items
     */
    public QoreKafkaProducer(Hash conf) throws Throwable {
        prod = get(conf);
    }

    /** Close this producer

        This method blocks until all previously sent requests complete.
     */
    public void close() {
        prod.close();
    }

    /** Close this producer

        This method waits up to timeout for the producer to complete the sending of all incomplete requests.

        If the producer is unable to complete all requests before the timeout expires, this method will fail any
        unsent and unacknowledged records immediately. It will also abort the ongoing transaction if it's not already
        completing.
    */
    public void close(Duration timeout) {
        prod.close(timeout);
    }

    /** Returns the producer object
     */
    public KafkaProducer<Object, Object> getProducer() {
        return prod;
    }

    /** Connector for sending a message synchronously

        input type: qoretechnologies/building-blocks/kafka/send-message
        output type: qoretechnologies/building-blocks/kafka/record-metadata
     */
    public Hash sendMessage(Map<String, Object> info) throws Throwable {
        Future<RecordMetadata> f = sendMessageAsyncIntern(info, null);
        // wait for message to be sent
        RecordMetadata md = f.get();
        // convert timestamp to date
        ZonedDateTime ts_date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(md.timestamp()),
            TimeZone.getDefault().toZoneId());
        // return info
        return new Hash() {
            {
                put("offset", md.offset());
                put("partition", md.partition());
                put("serialized_key_size", md.serializedKeySize());
                put("serialized_value_size", md.serializedValueSize());
                put("timestamp", ts_date);
                put("topic", md.topic());
            }
        };
    }

    /** Connector for sending a message asynchronously

        input type: qoretechnologies/building-blocks/kafka/send-message

        @return a future regarding the message sent

        exceptions will be logged with INFO level asynchronously
    */
    public Future<RecordMetadata> sendMessageAsync(Map<String, Object> info) throws Throwable {
        return sendMessageAsyncIntern(info, new Callback() {
            public void onCompletion(RecordMetadata metadata, Exception e) {
                if (e != null) {
                    StringWriter writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter( writer );
                    e.printStackTrace(printWriter);
                    printWriter.flush();
                    System.out.printf("Exception with msg %y: %s\n", info, writer.toString());
                }
            }
        });
    }

    protected Future<RecordMetadata> sendMessageAsyncIntern(Map<String, Object> info, Callback callback) throws Throwable {
        String topic = (String)info.get("topic");
        Object value = info.get("value");

        ProducerRecord<Object, Object> rec;
        if (info.containsKey("key")) {
            Object key = (String)info.get("key");
            if (info.containsKey("partition")) {
                int partition = ((Number)info.get("partition")).intValue();
                rec = new ProducerRecord<Object, Object>(topic, partition, key, value);
            } else {
                rec = new ProducerRecord<Object, Object>(topic, key, value);
            }
        } else {
            rec = new ProducerRecord<Object, Object>(topic, value);
        }
        return callback != null ? prod.send(rec, callback) : prod.send(rec);
    }

     /** Returns a KafkaProducer object based on configuration
     */
    public static KafkaProducer<Object, Object> get(Hash conf) throws Throwable {
        Hash kafka_config = QoreJavaConfig.get(conf, PropTypeMap);
        //System.out.printf("using KafkaProducer config: %s\n", kafka_config.toString());
        return new KafkaProducer<Object, Object>(kafka_config);
    }
}
