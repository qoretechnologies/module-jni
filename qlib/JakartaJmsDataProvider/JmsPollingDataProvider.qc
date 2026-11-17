# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsPollingDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! The error code that an observer throws to refuse a JMS message permanently
/** See @ref jmspollingdataprovider_delivery
*/
public const JMS_MESSAGE_REJECTED_ERR = "JMS-MESSAGE-REJECTED";

#! JMS data provider for asynchronous event-driven message consumption
/** A background Qore thread receives the messages of the destination and notifies the observers.

    @anchor jmspollingdataprovider_delivery
    @par Delivery
    The provider receives each message in a transacted session of its own (\c SESSION_TRANSACTED, whatever the
    \c session_mode option, which applies to the pooled sessions of the other providers), one message per
    transaction, and commits the transaction only after every observer has accepted the message (at-least-once
    delivery); the session is used only by the receiving thread, as JMS requires.  A transacted session is used
    rather than \c CLIENT_ACKNOWLEDGE, as a rollback is the standard way to return a message to the broker: every
    broker counts it as a delivery attempt and applies its redelivery limit and dead-letter destination to it
    (for example \c max-delivery-attempts and the \c DLQ address of ActiveMQ Artemis).
    - An observer refuses a message by throwing from its \c update() method (it reaches the provider as an
      \c OBSERVER-NOTIFICATION-ERROR).  The transaction is then rolled back after a delay, and the broker delivers
      the message again (with a single consumer and no redelivery delay of the broker, as in the default
      configuration of ActiveMQ Artemis, before the messages that follow it; a broker or client redelivery
      policy can add its own delay and limit); the delay starts at \c refusal_delay_ms and doubles
      with each delivery of the message (its \c JMSXDeliveryCount property) up to \c max_refusal_delay_ms.  A
      redelivered message goes to every observer again, so an observer that accepted it before another one
      refused it sees it again.  A refusal never stops the provider; when the broker's redelivery limit is
      reached, the broker moves the message to its dead-letter destination.
    - An observer refuses a message permanently by throwing @ref JMS_MESSAGE_REJECTED_ERR (when every observer that
      fails throws it).  The message is then sent to the \c dead_letter_destination option in the same transaction,
      if it is set, so that it is moved atomically; otherwise it is acknowledged (the transaction is committed) and
      logged as an error, as JMS has no way to reject a message.  A message that cannot be converted to an event is
      handled the same way.
    - When the provider is stopped, it waits for the message being handled; a message whose redelivery delay has
      not elapsed is rolled back at once (a message of a non-durable topic subscription is lost then, as the
      subscription ends).  A commit that fails is logged and the transaction is rolled back; the broker then
      delivers the message again.  A receive error is logged and the provider receives again after a second,
      unless the session or the connection is closed, which stops the provider (see isActive()).
    - As each message is committed separately, the provider makes a round trip to the broker for every message
      whatever the \c session_mode option.
*/
public class JmsPollingDataProvider inherits JmsDataProviderBase, DataProvider::DelayedObservable {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "polling",
            "desc": "Data provider for event-driven JMS message consumption",
            "supports_observable": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            AbstractDataProvider::DataProviderSummaryInfoKeys
        });

        #! JMS message event constant
        const EVENT_JMS_MESSAGE = "jms-message-event";

        #! The default initial redelivery delay of a refused message in milliseconds
        const DefaultRefusalDelayMs = 1000;

        #! The default maximum redelivery delay of a refused message in milliseconds
        const DefaultMaxRefusalDelayMs = 60000;

        #! The largest redelivery delay in milliseconds that is accepted (one day)
        const MaxRefusalDelayMs = 86400000;

        #! The longest time in milliseconds that one receive call waits, so that a stop request is seen promptly
        const ReceiveWaitMs = 500;

        #! The time in milliseconds to wait after a receive error before receiving again
        const ReceiveErrorDelayMs = 1000;
    }

    private {
        #! The parent JMS data provider
        JmsDataProvider parent_provider;

        #! Dedicated JMS Connection for this listener (not pooled)
        *Connection listener_conn;

        #! Dedicated transacted JMS Session for this listener; used only by the receive thread
        *Session listener_session;

        #! The JMS MessageConsumer
        *MessageConsumer consumer;

        #! The dead-letter destination for permanently refused messages, if any
        *Destination dead_letter_dest;

        #! Whether the provider is running
        bool running = False;

        #! Whether the provider is being stopped
        bool stopping = False;

        #! Lock for the state and the counters
        Mutex m();

        #! Wakes the receive thread in a wait when the provider is stopped
        Condition cond();

        #! Counter of the current receive thread; a new one for each start, so that a stop waits for its own
        #! thread
        Counter loop_counter();

        #! The TID of the receive thread
        int loop_tid;

        #! The number of messages accepted by the observers and committed
        int accepted_count = 0;

        #! The number of refusals (the messages were rolled back to be delivered again)
        int refused_count = 0;

        #! The number of permanently refused messages (dead-lettered or acknowledged)
        int rejected_count = 0;

        #! The number of commits or rollbacks that failed
        int settle_error_count = 0;
    }

    #! Creates the object from a parent JmsDataProvider
    constructor(JmsDataProvider parent) : JmsDataProviderBase(parent.getConnectionOptions()) {
        # Java I/O that the sandbox does not control: sandboxed code is refused (qore#5632)
        DataProviderActionCatalog::checkSandboxAccess(JakartaJmsDataProvider::AppName);
        parent_provider = parent;
    }

    destructor() {
        stop();
    }

    string getName() {
        return "polling";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Called when all observers have been added to the object
    /** This method is meant to trigger event generation
    */
    observersReady() {
        start();
    }

    #! Called to stop generating events
    /** Waits for the message being handled, unless called from an observer
    */
    stopEvents() {
        stop();
    }

    #! Returns @ref True if the provider is receiving messages
    /** The provider stops by itself only when its session or connection is closed
    */
    bool isActive() {
        AutoLock al(m);
        return running && !stopping;
    }

    #! Returns the delivery statistics of the provider
    /** @return a hash with the following keys:
        - \c accepted: the number of messages that every observer accepted and that were committed
        - \c refused: the number of refusals (the messages were rolled back to be delivered again)
        - \c rejected: the number of permanently refused messages (moved to the dead-letter destination, or
          acknowledged and logged)
        - \c settle_errors: the number of commits or rollbacks that failed (the broker delivers those messages
          again)
    */
    hash<auto> getDeliveryStats() {
        AutoLock al(m);
        return {
            "accepted": accepted_count,
            "refused": refused_count,
            "rejected": rejected_count,
            "settle_errors": settle_error_count,
        };
    }

    #! Starts the receive thread
    /** If the provider is being stopped, waits for the stop to complete first
    */
    private start() {
        while (True) {
            Counter prev;
            {
                AutoLock al(m);
                if (!running) {
                    break;
                }
                if (!stopping) {
                    return;
                }
                if (gettid() == loop_tid) {
                    throw "JMS-POLLING-ERROR", "the provider cannot be started again by an observer while it is "
                        "being stopped";
                }
                prev = loop_counter;
            }
            prev.waitForZero();
        }

        AutoLock al(m);
        if (running) {
            # started by another thread in the meantime
            return;
        }

        *string dest_str = copts.destination;
        if (!dest_str.val()) {
            throw "JMS-POLLING-ERROR", "no destination specified; set the 'destination' constructor option";
        }
        if (copts.dead_letter_destination.val()
            && normalizeDestination(copts.dead_letter_destination) == normalizeDestination(dest_str)) {
            throw "JMS-POLLING-ERROR", sprintf("the dead_letter_destination option cannot be the destination of "
                "the provider (%y)", dest_str);
        }

        try {
            # create a dedicated connection and transacted session (not pooled) for the listener
            ConnectionFactory factory = parent_provider.getConnectionFactory();
            listener_conn = JmsDataProviderBase::createJmsConnection(factory, copts);
            listener_session = listener_conn.createSession(Session::SESSION_TRANSACTED);

            # parse destination
            Destination dest = JmsDataProviderBase::parseDestination(listener_session, dest_str);
            if (copts.dead_letter_destination.val()) {
                dead_letter_dest = JmsDataProviderBase::parseDestination(listener_session,
                    copts.dead_letter_destination);
            }

            # build message selector
            *string selector = copts.message_selector;

            # create consumer - durable subscription for topics with client_id
            if (dest instanceof Topic && copts.client_id.val()) {
                Topic topic = cast<Topic>(dest);
                if (selector.val()) {
                    consumer = listener_session.createDurableSubscriber(topic, copts.client_id, selector, False);
                } else {
                    consumer = listener_session.createDurableSubscriber(topic, copts.client_id);
                }
            } else {
                if (selector.val()) {
                    consumer = listener_session.createConsumer(dest, selector);
                } else {
                    consumer = listener_session.createConsumer(dest);
                }
            }

            # start the connection to begin message delivery
            listener_conn.start();
        } catch (hash<ExceptionInfo> ex) {
            # cleanup on failure
            closeResources();
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            rethrow;
        }

        running = True;
        loop_counter = new Counter(1);
        try {
            loop_tid = background receiveLoop();
        } catch (hash<ExceptionInfo> ex) {
            loop_counter.dec();
            running = False;
            closeResources();
            rethrow;
        }
        debug("JMS: Listening on %y", dest_str);
    }

    #! Stops the receive thread, which closes the resources
    /** Waits for the receive thread to end, unless called from it; idempotent
    */
    private stop() {
        Counter cnt;
        {
            AutoLock al(m);
            if (!running) {
                return;
            }
            stopping = True;
            cond.broadcast();
            if (gettid() == loop_tid) {
                # called by an observer: the loop ends after the current message
                return;
            }
            cnt = loop_counter;
        }
        cnt.waitForZero();
    }

    #! Closes the consumer, the session and the connection
    private closeResources() {
        if (consumer) {
            try {
                consumer.close();
            } catch () {
                # ignore
            }
            delete consumer;
        }
        if (listener_session) {
            try {
                listener_session.close();
            } catch () {
                # ignore
            }
            delete listener_session;
        }
        if (listener_conn) {
            try {
                listener_conn.close();
            } catch () {
                # ignore
            }
            delete listener_conn;
        }
        delete dead_letter_dest;
    }

    #! Returns True if the provider is being stopped
    private bool isStopping() {
        AutoLock al(m);
        return stopping;
    }

    #! The receive thread: receives each message and settles it after the observers have handled it
    /** See @ref jmspollingdataprovider_delivery
    */
    private receiveLoop() {
        Counter cnt = loop_counter;
        on_exit {
            # a message received but not committed is rolled back by closing the session
            closeResources();
            AutoLock al(m);
            # the provider can be started again
            running = False;
            stopping = False;
            cnt.dec();
            debug("JMS: Stopped listening");
        }

        # consecutive refusals; gives the redelivery delay of a message without a delivery count
        int refusals = 0;
        while (!isStopping()) {
            *Message msg;
            try {
                msg = consumer.receive(ReceiveWaitMs);
            } catch (hash<ExceptionInfo> ex) {
                if (isStopping()) {
                    break;
                }
                # a closed session or connection cannot receive again
                if (isClosedError(ex)) {
                    LoggerWrapper::error("JMS: the consumer is closed; the provider stops: %s",
                        getErrorString(ex));
                    break;
                }
                LoggerWrapper::error("JMS: receive error; receiving again in %d ms: %s", ReceiveErrorDelayMs,
                    getErrorString(ex));
                waitBeforeRetry(ReceiveErrorDelayMs);
                continue;
            }
            if (!msg) {
                continue;
            }

            hash<auto> event_data;
            try {
                event_data = JmsTypeMapper::fromJmsMessage(msg);
            } catch (hash<ExceptionInfo> ex) {
                LoggerWrapper::error("JMS: message %y cannot be converted to an event: %s", getMessageId(msg),
                    getErrorString(ex));
                reject(msg);
                refusals = 0;
                continue;
            }

            try {
                notifyObservers(EVENT_JMS_MESSAGE, event_data);
            } catch (hash<ExceptionInfo> ex) {
                if (isPermanentRefusal(ex)) {
                    LoggerWrapper::error("JMS: message %y refused permanently: %s: %s", event_data.message_id,
                        ex.err, ex.desc);
                    reject(msg);
                    refusals = 0;
                    continue;
                }
                int delay_ms = getDelay(getDeliveryCount(msg, ++refusals));
                {
                    AutoLock al(m);
                    ++refused_count;
                }
                LoggerWrapper::error("JMS: message %y refused; it will be delivered again in %d ms: %s: %s",
                    event_data.message_id, delay_ms, ex.err, ex.desc);
                waitBeforeRetry(delay_ms);
                settle(False);
                continue;
            }
            refusals = 0;
            if (settle(True)) {
                AutoLock al(m);
                ++accepted_count;
            }
        }
    }

    #! Handles a permanently refused message: moves it to the dead-letter destination or acknowledges it
    private reject(Message msg) {
        if (dead_letter_dest) {
            try {
                MessageProducer prod = listener_session.createProducer(dead_letter_dest);
                on_exit prod.close();
                *string orig_id = getMessageId(msg);
                prod.send(msg);
                # send() gives the message a new ID
                LoggerWrapper::error("JMS: message %y is moved to the dead-letter destination %y as message %y",
                    orig_id, copts.dead_letter_destination, getMessageId(msg));
            } catch (hash<ExceptionInfo> ex) {
                # the message is delivered again, to be rejected again
                LoggerWrapper::error("JMS: message %y cannot be sent to the dead-letter destination; it will be "
                    "delivered again: %s", getMessageId(msg), getErrorString(ex));
                waitBeforeRetry(getDelay(getDeliveryCount(msg, 1)));
                settle(False);
                return;
            }
        } else {
            LoggerWrapper::error("JMS: message %y is acknowledged without being delivered, as no "
                "dead_letter_destination is set", getMessageId(msg));
        }
        if (settle(True)) {
            AutoLock al(m);
            ++rejected_count;
        }
    }

    #! Commits or rolls back the transaction of the current message
    /** @return @ref True if the transaction was settled
    */
    private bool settle(bool commit) {
        try {
            if (commit) {
                listener_session.commit();
            } else {
                listener_session.rollback();
            }
            return True;
        } catch (hash<ExceptionInfo> ex) {
            {
                AutoLock al(m);
                ++settle_error_count;
            }
            LoggerWrapper::error("JMS: cannot %s the transaction of the message; the broker delivers it "
                "again unless the commit was completed: %s", commit ? "commit" : "roll back", getErrorString(ex));
            if (commit) {
                # a transaction left open would add the next message to it
                try {
                    listener_session.rollback();
                } catch () {
                }
            }
        }
        return False;
    }

    #! Waits for the given time; returns early when the provider is stopped
    private waitBeforeRetry(int delay_ms) {
        date end = now_us() + milliseconds(delay_ms);
        AutoLock al(m);
        while (!stopping) {
            int remaining_ms = (end - now_us()).durationMilliseconds();
            if (remaining_ms <= 0) {
                break;
            }
            cond.wait(m, remaining_ms);
        }
    }

    #! Returns the redelivery delay in milliseconds of a message delivered the given number of times
    private int getDelay(int attempt) {
        int delay = min(MaxRefusalDelayMs, max(1, int(copts.refusal_delay_ms ?? DefaultRefusalDelayMs)));
        int max_delay = min(MaxRefusalDelayMs, max(delay,
            int(copts.max_refusal_delay_ms ?? DefaultMaxRefusalDelayMs)));
        while (--attempt > 0 && delay < max_delay) {
            delay *= 2;
        }
        return min(delay, max_delay);
    }

    #! Returns the number of deliveries of a message (its JMSXDeliveryCount property), or the given value
    private static int getDeliveryCount(Message msg, int fallback) {
        try {
            if (msg.propertyExists("JMSXDeliveryCount")) {
                return max(1, msg.getIntProperty("JMSXDeliveryCount"));
            }
        } catch () {
        }
        return fallback;
    }

    #! Returns a destination name with its scheme: a bare name is a queue
    private static string normalizeDestination(string dest) {
        return (dest =~ /^(queue|topic):\/\//) ? dest : "queue://" + dest;
    }

    #! Returns the ID of a message, if it can be read
    private static *string getMessageId(Message msg) {
        try {
            return msg.getJMSMessageID();
        } catch () {
        }
    }

    #! Returns True if the observers refused a message permanently
    /** That is, if every observer that failed threw @ref JMS_MESSAGE_REJECTED_ERR
    */
    private static bool isPermanentRefusal(hash<ExceptionInfo> ex) {
        if (ex.err == "OBSERVER-NOTIFICATION-ERROR" && ex.arg.typeCode() == NT_LIST && ex.arg) {
            foreach auto err in (ex.arg) {
                if (err.typeCode() != NT_HASH || err.err != JMS_MESSAGE_REJECTED_ERR) {
                    return False;
                }
            }
            return True;
        }
        return ex.err == JMS_MESSAGE_REJECTED_ERR;
    }

    #! Returns True if the exception means that the consumer, its session or its connection is closed
    private static bool isClosedError(hash<ExceptionInfo> ex) {
        if (ex.arg instanceof object && ex.arg.hasCallableMethod("getClass")) {
            return ex.arg.getClass().getName() == "jakarta.jms.IllegalStateException";
        }
        # the jni module gives the class of the Java exception at the start of the description
        return ex.desc.typeCode() == NT_STRING && ex.desc =~ /^jakarta\.jms\.IllegalStateException\b/;
    }

    #! Returns a description of an exception, with the Java exception's message, if any
    private static string getErrorString(hash<ExceptionInfo> ex) {
        string str = sprintf("%s: %s", ex.err, ex.desc);
        if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
            str += sprintf(" (%s)", ex.arg.toString());
        }
        return str;
    }

    #! Returns a hash of all supported event types
    private hash<string, hash<DataProviderMessageInfo>> getEventTypesImpl() {
        return {
            EVENT_JMS_MESSAGE: <DataProviderMessageInfo>{
                "desc": "This event is raised when an incoming JMS message is received from the destination",
                "type": JmsEventDataType,
            },
        };
    }

    #! Returns example event data for the given event
    private auto getExampleEventDataImpl(string event_id) {
        switch (event_id) {
            case EVENT_JMS_MESSAGE:
                return {
                    "message_id": "ID:broker-12345-1234567890123-1:1:1:1:1",
                    "correlation_id": "order-456",
                    "destination": "queue://orders",
                    "delivery_mode": "PERSISTENT",
                    "priority": 4,
                    "timestamp": 1711900800000,
                    "expiration": 0,
                    "type": "OrderMessage",
                    "redelivered": False,
                    "headers": {"source": "order-system", "region": "us-east"},
                    "body": "Hello, JMS World!",
                };
        }
    }
}
}
