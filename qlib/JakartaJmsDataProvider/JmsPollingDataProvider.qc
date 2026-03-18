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
#! JMS data provider for asynchronous event-driven message consumption
/** Uses the JMS MessageListener interface via implement_interface() with a Qore Queue-based
    handoff pattern for thread safety. The Java callback thread pushes messages to a Qore Queue,
    and a background Qore thread drains the queue and notifies observers.

    @note The callback uses a class method (not a closure) to avoid variable scoping issues
    when the JMS provider thread invokes the callback in a different Qore program context.
*/
public class JmsPollingDataProvider inherits JmsDataProviderBase, DataProvider::DelayedObservable {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "polling",
            "desc": "Data provider for event-driven JMS message consumption via MessageListener",
            "supports_observable": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            AbstractDataProvider::DataProviderSummaryInfoKeys
        });

        #! JMS message event constant
        const EVENT_JMS_MESSAGE = "jms-message-event";
    }

    private {
        #! The parent JMS data provider
        JmsDataProvider parent_provider;

        #! Dedicated JMS Connection for this listener (not pooled)
        *Connection listener_conn;

        #! Dedicated JMS Session for this listener
        *Session listener_session;

        #! The JMS MessageConsumer
        *MessageConsumer consumer;

        #! Reference to prevent GC of the listener callback proxy
        auto jms_listener;

        #! Internal queue for thread-safe message handoff from JMS thread to Qore thread
        Qore::Thread::Queue msg_queue();

        #! Whether the listener is running
        bool running = False;

        #! Counter for drain thread completion
        Counter drain_counter(1);
    }

    #! Creates the object from a parent JmsDataProvider
    constructor(JmsDataProvider parent) : JmsDataProviderBase(parent.getConnectionOptions()) {
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
    stopEvents() {
        stop();
    }

    #! Starts the message listener
    private start() {
        if (running) {
            return;
        }

        *string dest_str = copts.destination;
        if (!dest_str.val()) {
            throw "JMS-POLLING-ERROR", "no destination specified; set the 'destination' constructor option";
        }

        try {
            # create a dedicated connection and session (not pooled) for the listener
            ConnectionFactory factory = parent_provider.getConnectionFactory();
            listener_conn = JmsDataProviderBase::createJmsConnection(factory, copts);

            int ack_mode = JmsDataProviderBase::getSessionMode(copts.session_mode ?? "AUTO_ACKNOWLEDGE");
            listener_session = listener_conn.createSession(ack_mode);

            # parse destination
            Destination dest = JmsDataProviderBase::parseDestination(listener_session, dest_str);

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

            # register MessageListener via implement_interface()
            # NOTE: uses a class method (not closure) to avoid variable scoping issues
            # when the JMS provider thread invokes the callback
            lang::Class listener_cls = load_class("jakarta/jms/MessageListener");
            jms_listener = cast<MessageListener>(
                implement_interface(new QoreInvocationHandler(\onMessageCallback()), listener_cls));
            consumer.setMessageListener(jms_listener);

            running = True;

            # start background drain thread to notify observers from a Qore thread
            drain_counter = new Counter(1);
            background drainQueue();

            # start the connection to begin message delivery
            listener_conn.start();
            debug("JMS: Listening on %y", dest_str);
        } catch (hash<ExceptionInfo> ex) {
            # cleanup on failure
            stop();
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            rethrow;
        }
    }

    #! Stops the message listener and cleans up resources
    private stop() {
        if (!running) {
            return;
        }
        running = False;

        # unregister listener
        if (consumer) {
            try {
                consumer.setMessageListener(NOTHING);
                consumer.close();
            } catch () {
                # ignore
            }
            delete consumer;
        }
        delete jms_listener;

        # push sentinel to unblock drain thread
        msg_queue.push(NOTHING);
        # wait with timeout to avoid indefinite blocking; drain thread should exit
        # within one poll interval (500ms) after seeing the sentinel
        drain_counter.waitForZero(5000);

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
        debug("JMS: Stopped listening");
    }

    #! JMS MessageListener callback - called on the JMS provider thread
    /** Converts the message and pushes to the internal queue for thread-safe handoff.
        This is a class method (not a closure) to ensure proper variable access when
        the callback is invoked on a foreign Java thread.
    */
    private onMessageCallback(Method method, *list<auto> args) {
        if (method.getName() != "onMessage") {
            return;
        }
        try {
            Message msg = args[0];
            hash<auto> event_data = JmsTypeMapper::fromJmsMessage(msg);
            msg_queue.push(event_data);
        } catch (hash<ExceptionInfo> ex) {
            LoggerWrapper::error("JMS: Error in onMessage callback: %s", get_exception_string(ex));
        }
    }

    #! Background Qore thread that drains the internal queue and notifies observers
    private drainQueue() {
        on_exit drain_counter.dec();

        debug("JMS: Drain thread started");
        while (running) {
            try {
                # blocking get with 500ms timeout for cancellation checking
                auto event_data = msg_queue.get(500);
                if (!exists event_data) {
                    # NOTHING sentinel or timeout - check if still running
                    continue;
                }
                notifyObservers(EVENT_JMS_MESSAGE, event_data);
            } catch (hash<ExceptionInfo> ex) {
                if (ex.err == "QUEUE-TIMEOUT") {
                    # normal timeout, check running flag
                    continue;
                }
                LoggerWrapper::error("JMS: Error in drain thread: %s", get_exception_string(ex));
            }
        }
        debug("JMS: Drain thread ended");
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
