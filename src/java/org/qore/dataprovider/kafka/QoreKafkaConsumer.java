package org.qore.dataprovider.kafka;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RebalanceInProgressException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.Header;

import org.qore.jni.Hash;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.Iterator;
import java.util.Optional;
import java.util.TimeZone;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Creates a KafkaConsumer object based on configuration
 */
class QoreKafkaConsumer {
    /** The consumer
    */
    protected KafkaConsumer<Object, Object> cons;

    /** quit flag
    */
    private final AtomicBoolean quit = new AtomicBoolean(false);

    /** done lock
    */
    private final Lock lock = new ReentrantLock();
    /** done condition
    */
    private Condition done_cond = lock.newCondition();
    /** running flag
    */
    private volatile boolean running = false;
    // array of subscription topics
    String topic_array[];

    // describe the actual types, as kafka will convert from strings to anything, but insists on the correct base type
    /** keys are the actual configuration names, not the config item names
    */
    public static final Map<String, QoreJavaConfig.ConfType> PropTypeMap =
        Collections.unmodifiableMap(new HashMap<String, QoreJavaConfig.ConfType>() {
        {
            put("fetch.min.bytes", QoreJavaConfig.ConfType.INT);
            put("heartbeat.interval.ms", QoreJavaConfig.ConfType.INT);
            put("max.partition.fetch.bytes", QoreJavaConfig.ConfType.INT);
            put("session.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("default.api.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("fetch.max.bytes", QoreJavaConfig.ConfType.INT);
            put("max.poll.interval.ms", QoreJavaConfig.ConfType.INT);
            put("max.poll.records", QoreJavaConfig.ConfType.INT);
            put("receive.buffer.bytes", QoreJavaConfig.ConfType.INT);
            put("request.timeout.ms", QoreJavaConfig.ConfType.INT);
            put("send.buffer.bytes", QoreJavaConfig.ConfType.INT);
            put("auto.commit.interval.ms", QoreJavaConfig.ConfType.INT);
            put("fetch.max.wait.ms", QoreJavaConfig.ConfType.INT);
            put("metrics.num.samples", QoreJavaConfig.ConfType.INT);
            put("max.poll.interval.ms", QoreJavaConfig.ConfType.INT);
            put("sasl.login.refresh.buffer.seconds", QoreJavaConfig.ConfType.SHORT);
            put("sasl.login.refresh.min.period.seconds", QoreJavaConfig.ConfType.SHORT);
        }
    });

    /** Creates the object from configuration in config items
     */
    public QoreKafkaConsumer(Hash conf, String event_name) throws Throwable {
        topic_array = (String[])conf.get("topics");
        cons = createConsumer(conf);
    }

    public void registerObserver(Object ignored) throws Throwable {
    }

    /** Stops polling and closes the object
     */
    public Object stop(Object ignored) {
        quit.set(true);
        cons.wakeup();

        lock.lock();
        try {
            while (running) {
                done_cond.awaitUninterruptibly();
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    /** Close this consumer
     */
    public void close() {
        if (cons != null) {
            cons.close();
        }
    }

    /** Returns the consumer object
     */
    public KafkaConsumer<Object, Object> getConsumer() {
        return cons;
    }

    /** Returns a KafkaConsumer object based on configuration
    */
    public static KafkaConsumer<Object, Object> createConsumer(Hash conf) throws Throwable {
        Hash kafka_config = QoreJavaConfig.get(conf, PropTypeMap);
        //System.out.printf("Using KafkaConsumer config: %s\n", kafka_config.toString());
        return new KafkaConsumer<Object, Object>(kafka_config);
    }

    /** Starts receiving messages
     */
    public Boolean start(Object ignored) throws Throwable {
        // make sure this only runs once
        lock.lock();
        try {
            if (running) {
                return false;
            }
            running = true;
        } finally {
            lock.unlock();
        }

        cons.subscribe(Arrays.asList(topic_array));
        return true;
    }

    /** Returns true if polling should continue
     */
    public Boolean isRunning() throws Throwable {
        return running && !quit.get();
    }

    /** Polls Kafka records and returns converted event hashes
     */
    public ArrayList<Hash> poll(long timeoutMillis) throws Throwable {
        ArrayList<Hash> events = new ArrayList<Hash>();
        if (quit.get()) {
            return events;
        }

        try {
            ConsumerRecords<Object, Object> recs = cons.poll(Duration.ofMillis(timeoutMillis));
            Iterator<ConsumerRecord<Object, Object>> i = recs.iterator();
            while (i.hasNext()) {
                events.add(recordToHash(i.next()));
            }
        } catch (WakeupException e) {
            if (!quit.get()) {
                throw e;
            }
        }
        return events;
    }

    /** The description of the last commit that failed without stopping the consumer (a reassignment of the
        partition, or a retriable error)
     */
    private String lastCommitError;

    /** Commits the position after a delivered record of a partition (qore#6395)

        The provider commits offsets itself, after its observers have accepted the records, so that a record is never
        committed before it has been delivered (\c enable.auto.commit is always off).

        A commit that a stop request interrupts (stop() wakes the consumer up) is made again, as the wakeup is
        one-shot, so that the records delivered before the stop stay committed.

        @param topic the topic of the partition
        @param partition the partition
        @param nextOffset the offset of the next record to deliver: the offset of the last delivered record plus one

        @return true if the position was committed, false if the commit failed because the partition was reassigned
        or with a retriable error (a later commit of the partition covers its records, unless the partition is
        reassigned or the consumer restarts first); getLastCommitError() describes it; any other error is thrown
     */
    public boolean commit(String topic, int partition, long nextOffset) throws Throwable {
        Map<TopicPartition, OffsetAndMetadata> pos = Collections.singletonMap(new TopicPartition(topic, partition),
            new OffsetAndMetadata(nextOffset));
        try {
            try {
                cons.commitSync(pos);
            } catch (WakeupException e) {
                if (!quit.get()) {
                    throw e;
                }
                cons.commitSync(pos);
            }
            return true;
        } catch (CommitFailedException | RebalanceInProgressException | RetriableException e) {
            lastCommitError = e.toString();
            return false;
        }
    }

    /** Returns the description of the last commit that failed without stopping the consumer
     */
    public String getLastCommitError() {
        return lastCommitError;
    }

    /** Sets the position of a partition, so that the next poll() returns the record at the given offset again
        (qore#6395)
     */
    public void seek(String topic, int partition, long offset) throws Throwable {
        cons.seek(new TopicPartition(topic, partition), offset);
    }

    /** Pauses fetching from every assigned partition; poll() then returns no records from them, but keeps the
        consumer in its group (qore#6395)
     */
    public void pauseAll() throws Throwable {
        cons.pause(cons.assignment());
    }

    /** Resumes fetching from every paused partition (qore#6395)
     */
    public void resumeAll() throws Throwable {
        cons.resume(cons.paused());
    }

    /** Marks polling done and closes this consumer
     */
    public void done() {
        try {
            cons.close();
        } finally {
            lock.lock();
            try {
                running = false;
                done_cond.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /** Converts a Kafka consumer record to a Qore event hash
     */
    private Hash recordToHash(ConsumerRecord<Object, Object> rec) {
        Hash event = new Hash();
        event.put("key", rec.key());
        event.put("value", rec.value());
        event.put("headers", headerToHash(rec.headers()));
        Optional<Integer> leader_epoch = rec.leaderEpoch();
        if (leader_epoch.isPresent()) {
            event.put("leader_epoch", leader_epoch.get());
        }
        event.put("offset", rec.offset());
        event.put("partition", rec.partition());
        event.put("serialized_key_size", rec.serializedKeySize());
        event.put("serialized_value_size", rec.serializedValueSize());
        {
            TimestampType tt = rec.timestampType();
            switch (tt) {
                case CREATE_TIME: event.put("timestamp_type", "CreateTime"); break;
                case LOG_APPEND_TIME: event.put("timestamp_type", "LogAppendTime"); break;
                case NO_TIMESTAMP_TYPE: event.put("timestamp_type", "NotAvailable"); break;
                default: event.put("timestamp_type", "unknown"); break;
            }
        }
        ZonedDateTime ts_date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rec.timestamp()),
            TimeZone.getDefault().toZoneId());
        event.put("timestamp", ts_date);
        event.put("topic", rec.topic());
        return event;
    }

    /** Returns the headers of a record as a hash of header names to values

        A value that is valid UTF-8 is returned as a string, any other value as the bytes it is (a Qore binary), and a
        header without a value as null.  When a record carries a header more than once, the last value is returned,
        as Headers.lastHeader() returns it.
     */
    public static Hash headerToHash(Headers hdrs) {
        Hash rv = new Hash();
        Iterator<Header> i = hdrs.iterator();
        while (i.hasNext()) {
            Header hdr = i.next();
            rv.put(hdr.key(), decodeHeaderValue(hdr.value()));
        }
        return rv;
    }

    /** Returns a header value as a string when it is valid UTF-8, else as the bytes it is
     */
    public static Object decodeHeaderValue(byte[] value) {
        if (value == null) {
            return null;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException e) {
            return value;
        }
    }
}
