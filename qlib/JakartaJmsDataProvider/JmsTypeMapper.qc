# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsTypeMapper.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Bidirectional type mapper between Qore types and JMS message types
/** Handles:
    - string -> TextMessage
    - hash -> MapMessage (nested hashes are flattened as JSON in each key)
    - binary -> BytesMessage
    - list/other -> BytesMessage with JSON serialization and content_type property
*/
public class JmsTypeMapper {
    #! Creates a JMS message from a Qore value
    /** @param session the JMS Session to create the message on
        @param body the Qore value to map
        @param headers optional JMS string properties to set on the message

        @return the JMS Message
    */
    static Message toJmsMessage(Session session, auto body, *hash<auto> headers) {
        Message msg;
        int type_code = body.typeCode();
        switch (type_code) {
            case NT_STRING: {
                TextMessage tmsg = session.createTextMessage();
                tmsg.setText(body);
                msg = tmsg;
                break;
            }

            case NT_HASH: {
                MapMessage mmsg = session.createMapMessage();
                JmsTypeMapper::setMapMessageFields(mmsg, body);
                msg = mmsg;
                break;
            }

            case NT_BINARY: {
                BytesMessage bmsg = session.createBytesMessage();
                bmsg.writeBytes(body);
                msg = bmsg;
                break;
            }

            default: {
                # serialize as JSON into a BytesMessage
%ifndef NoJson
                string json_str = make_json(body);
                BytesMessage bmsg = session.createBytesMessage();
                bmsg.writeBytes(binary(json_str));
                bmsg.setStringProperty("content_type", "application/json");
                msg = bmsg;
%else
                throw "JMS-TYPE-ERROR", sprintf("cannot serialize a value of type %y to a JMS message "
                    "without the json module", body.fullType());
%endif
                break;
            }
        }

        # set user-defined string properties
        if (headers) {
            map msg.setStringProperty($1.key, string($1.value)), headers.pairIterator();
        }
        return msg;
    }

    #! Converts a JMS Message to a Qore hash with standard fields
    /** @param msg the JMS Message

        @return a hash with standard JMS event fields
    */
    static hash<auto> fromJmsMessage(Message msg) {
        hash<auto> result = {
            "message_id": msg.getJMSMessageID(),
            "correlation_id": msg.getJMSCorrelationID(),
            "delivery_mode": msg.getJMSDeliveryMode() == DeliveryMode::PERSISTENT ? "PERSISTENT" : "NON_PERSISTENT",
            "priority": msg.getJMSPriority(),
            "timestamp": msg.getJMSTimestamp(),
            "expiration": msg.getJMSExpiration(),
            "type": msg.getJMSType(),
            "redelivered": msg.getJMSRedelivered(),
        };

        # get destination name
        try {
            Destination dest = msg.getJMSDestination();
            if (dest instanceof jms::Queue) {
                result.destination = "queue://" + cast<jms::Queue>(dest).getQueueName();
            } else if (dest instanceof Topic) {
                result.destination = "topic://" + cast<Topic>(dest).getTopicName();
            }
        } catch () {
            # ignore
        }

        # extract string properties as headers
        hash<string, string> hdrs;
        try {
            # Collections::list() auto-converts ArrayList<String> to Qore list<string>,
            # but ArrayList<Object> stays as a Java ArrayList — handle both cases
            auto prop_list = Collections::list(msg.getPropertyNames());
            if (prop_list instanceof list) {
                foreach auto name in (prop_list) {
                    auto val = msg.getStringProperty(string(name));
                    if (exists val) {
                        hdrs{string(name)} = val;
                    }
                }
            } else {
                for (int i = 0, int sz = prop_list.size(); i < sz; ++i) {
                    string name = prop_list.get(i).toString();
                    auto val = msg.getStringProperty(name);
                    if (exists val) {
                        hdrs{name} = val;
                    }
                }
            }
        } catch () {
            # ignore property enumeration errors
        }
        result.headers = hdrs.val() ? hdrs : NOTHING;

        # extract body based on message type
        if (msg instanceof TextMessage) {
            result.body = cast<TextMessage>(msg).getText();
        } else if (msg instanceof MapMessage) {
            result.body = JmsTypeMapper::getMapMessageBody(cast<MapMessage>(msg));
        } else if (msg instanceof BytesMessage) {
            BytesMessage bmsg = cast<BytesMessage>(msg);
            int len = bmsg.getBodyLength();
            if (len > 0) {
                # NOTE: use getBody() instead of readBytes() because the JNI bridge
                # automatically converts Java byte[] return values to Qore binary;
                # readBytes(byte[]) fills an array in-place which cannot propagate
                # back to Qore — getBody() returns the data as a return value instead
                binary buf = bmsg.getBody(ByteArrayClass);
                # check for JSON content_type
                *string ct = result.headers.content_type;
                if (ct == "application/json") {
%ifndef NoJson
                    try {
                        result.body = parse_json(buf.toString());
                    } catch () {
                        result.body = buf;
                    }
%else
                    result.body = buf;
%endif
                } else {
                    result.body = buf;
                }
            } else {
                result.body = binary();
            }
        }
        return result;
    }

    #! Sets MapMessage fields from a Qore hash
    private static setMapMessageFields(MapMessage mmsg, hash<auto> data) {
        foreach hash<auto> entry in (data.pairIterator()) {
            auto val = entry.value;
            switch (val.typeCode()) {
                case NT_STRING:
                    mmsg.setString(entry.key, val);
                    break;
                case NT_INT:
                    mmsg.setLong(entry.key, val);
                    break;
                case NT_FLOAT:
                    mmsg.setDouble(entry.key, val);
                    break;
                case NT_BOOLEAN:
                    mmsg.setBoolean(entry.key, val);
                    break;
                case NT_BINARY:
                    mmsg.setBytes(entry.key, val);
                    break;
                default:
                    # for nested hashes, lists, etc., serialize to JSON string
%ifndef NoJson
                    mmsg.setString(entry.key, make_json(val));
%else
                    mmsg.setString(entry.key, string(val));
%endif
                    break;
            }
        }
    }

    #! Extracts the body from a MapMessage as a Qore hash
    private static hash<auto> getMapMessageBody(MapMessage mmsg) {
        hash<auto> result;
        # Collections::list() auto-converts ArrayList<String> to Qore list<string>
        auto names_list = Collections::list(mmsg.getMapNames());
        if (names_list instanceof list) {
            foreach auto name in (names_list) {
                result{string(name)} = mmsg.getObject(string(name));
            }
        } else {
            for (int i = 0, int sz = names_list.size(); i < sz; ++i) {
                string name = names_list.get(i).toString();
                result{name} = mmsg.getObject(name);
            }
        }
        return result;
    }
}
}
