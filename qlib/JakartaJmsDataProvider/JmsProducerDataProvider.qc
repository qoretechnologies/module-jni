# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsProducerDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JMS producer data provider for sending messages and request-reply
/**
*/
public class JmsProducerDataProvider inherits JmsDataProviderBase {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "producer",
            "desc": "Data provider for sending JMS messages and performing request-reply operations",
            "supports_request": True,
            "supports_messages": MSG_Async,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            AbstractDataProvider::DataProviderSummaryInfoKeys
        });

        #! JMS send message type constant
        const MESSAGE_JMS_SEND = "message";
    }

    private {
        #! The parent JMS data provider
        JmsDataProvider parent_provider;
    }

    #! Creates the object from a parent JmsDataProvider
    constructor(JmsDataProvider parent) : JmsDataProviderBase(parent.getConnectionOptions()) {
        parent_provider = parent;
    }

    string getName() {
        return "producer";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a request and returns the response (request-reply pattern)
    /** @param req the request message hash
        @param request_options the request options

        @return the response message hash
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        # cancellation point before blocking JNI operations
        sleep(0);

        JmsSessionPool pool = parent_provider.getSessionPool();
        Session session = pool.acquire(copts.connection_timeout_ms ?? 5000);
        on_exit pool.release(session);

        # parse destination
        string dest_str = req.destination ?? copts.destination;
        if (!dest_str.val()) {
            throw "JMS-PRODUCER-ERROR", "no destination specified; provide 'destination' in the request "
                "or set the 'destination' constructor option";
        }
        Destination dest = JmsDataProviderBase::parseDestination(session, dest_str);

        # create message
        Message msg = JmsTypeMapper::toJmsMessage(session, req.body, req.headers);

        # set optional JMS headers
        if (req.correlation_id.val()) {
            msg.setJMSCorrelationID(req.correlation_id);
        }
        if (req.type.val()) {
            msg.setJMSType(req.type);
        }

        # set up reply-to with temporary queue
        TemporaryQueue reply_queue = session.createTemporaryQueue();
        msg.setJMSReplyTo(reply_queue);
        on_exit {
            try {
                reply_queue.delete();
            } catch () {
                # ignore
            }
        }

        # create producer and send
        MessageProducer producer = session.createProducer(dest);
        on_exit producer.close();

        int delivery_mode = req.delivery_mode == "NON_PERSISTENT"
            ? DeliveryMode::NON_PERSISTENT : DeliveryMode::PERSISTENT;
        int priority = req.priority ?? 4;
        int ttl = req.time_to_live ?? 0;
        producer.send(msg, delivery_mode, priority, ttl);

        # wait for reply; cancellation point before blocking receive
        MessageConsumer reply_consumer = session.createConsumer(reply_queue);
        on_exit reply_consumer.close();

        int timeout_ms = copts.connection_timeout_ms ?? 5000;
        sleep(0);
        *Message reply = reply_consumer.receive(timeout_ms);
        if (!reply) {
            throw "JMS-TIMEOUT-ERROR", sprintf("no reply received within %d ms", timeout_ms);
        }

        hash<auto> result = JmsTypeMapper::fromJmsMessage(reply);
        return {
            "message_id": msg.getJMSMessageID(),
            "correlation_id": result.correlation_id,
            "destination": result.destination,
            "body": result.body,
            "headers": result.headers,
        };
    }

    #! Returns the description of a successful request message, if any
    private *AbstractDataProviderType getRequestTypeImpl() {
        return JmsMessageDataType;
    }

    #! Returns the description of a response message
    private *AbstractDataProviderType getResponseTypeImpl() {
        return JmsMessageResponseType;
    }

    #! Returns the description of an outbound message, if any
    private hash<string, hash<DataProviderMessageInfo>> getMessageTypesImpl() {
        return {
            MESSAGE_JMS_SEND: <DataProviderMessageInfo>{
                "desc": "An outgoing JMS message that will be sent to the specified destination",
                "type": JmsMessageDataType,
            },
        };
    }

    #! Sends a message from message-capable data providers
    private sendMessageImpl(string message_id, auto msg, *hash<auto> send_message_options) {
        # cancellation point before blocking JNI operations
        sleep(0);

        try {
            switch (message_id) {
                case MESSAGE_JMS_SEND: {
                    JmsSessionPool pool = parent_provider.getSessionPool();
                    Session session = pool.acquire(copts.connection_timeout_ms ?? 5000);
                    on_exit pool.release(session);

                    # parse destination
                    string dest_str = msg.destination ?? copts.destination;
                    if (!dest_str.val()) {
                        throw "JMS-PRODUCER-ERROR", "no destination specified; provide 'destination' "
                            "in the message or set the 'destination' constructor option";
                    }
                    Destination dest = JmsDataProviderBase::parseDestination(session, dest_str);

                    # create JMS message
                    Message jms_msg = JmsTypeMapper::toJmsMessage(session, msg.body, msg.headers);

                    # set optional JMS headers
                    if (msg.correlation_id.val()) {
                        jms_msg.setJMSCorrelationID(msg.correlation_id);
                    }
                    if (msg.type.val()) {
                        jms_msg.setJMSType(msg.type);
                    }

                    # create producer and send
                    MessageProducer producer = session.createProducer(dest);
                    on_exit producer.close();

                    int delivery_mode = msg.delivery_mode == "NON_PERSISTENT"
                        ? DeliveryMode::NON_PERSISTENT : DeliveryMode::PERSISTENT;
                    int priority = msg.priority ?? 4;
                    int ttl = msg.time_to_live ?? 0;
                    producer.send(jms_msg, delivery_mode, priority, ttl);
                    debug("JMS: Sent message to %y", dest_str);
                    break;
                }

                default:
                    throw "UNSUPPORTED-MESSAGE", sprintf("message_id %y is unsupported; supported messages: %y",
                        message_id, keys getMessageTypesImpl());
            }
        } catch (hash<ExceptionInfo> ex) {
            if (ex.arg instanceof object && ex.arg.hasCallableMethod("toString")) {
                LoggerWrapper::error("JMS: Error sending message: %s", ex.arg.toString());
                rethrow ex.err, sprintf("%s: %s", ex.desc, ex.arg.toString()), ex.arg;
            }
            rethrow;
        }
    }
}
}
