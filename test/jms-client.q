# -*- mode: qore; indent-tabs-mode: nil -*-
# jms-client.q: the JMS client code of jms.qtest
# Copyright (C) 2026 Qore Technologies, s.r.o.

# jms.qtest parses this file at run time into a Program of its own, after it has added the JAR in $PAYARA_JAR to the
# classpath of that Program and imported javax.naming.InitialContext and javax.jms.* into it; the JMS types used here
# exist only after that, so this code cannot be parsed into the test script itself, which must run, and skip, without
# a JMS provider

%modern

#! the JMS client: connects to the server, and sends and receives a text message
class JmsClient {
    private {
        Counter c(1);
        *string data;
        bool verbose;

        Connection connection;
        Session session;
        MessageProducer producer;
        MessageConsumer consumer;
    }

    #! connects to the server and looks up the queue; throws an exception if the server or the queue is not available
    constructor(string host, int port, string queue_name, bool verbose) {
        self.verbose = verbose;

        # these properties are the default; included here to provide an example for connecting to a remote server
        # NOTE: the InitialContext constructor takes a Hashtable object, which can also be passed as a hash
        Properties props();
        props.setProperty("java.naming.factory.initial", "com.sun.enterprise.naming.SerialInitContextFactory");
        props.setProperty("org.omg.CORBA.ORBInitialHost", host);
        props.setProperty("org.omg.CORBA.ORBInitialPort", port.toString());

        InitialContext ctx(props);

        connection = cast<ConnectionFactory>(ctx.lookup("jms/__defaultConnectionFactory")).createConnection();
        # close the connection if the rest of the setup fails
        on_error {
            connection.close();
        }
        session = connection.createSession(Session::CLIENT_ACKNOWLEDGE);

        Destination queue = cast<Destination>(ctx.lookup(queue_name));

        producer = session.createProducer(queue);
        consumer = session.createConsumer(queue);
    }

    #! closes the connection, if there is one
    destructor() {
        if (connection) {
            connection.close();
        }
    }

    #! sends a text message to the queue, waits for the message listener to receive it, and returns its text
    *string sendAndReceive(string text) {
        # in order to implement the MessageListener interface for the callback, we have to use implement_interface()
        # as follows:
        ClassLoader loader = connection.getClass().getClassLoader();
        consumer.setMessageListener(cast<MessageListener>(implement_interface(loader,
            new QoreInvocationHandler(\messageCallback()), Class::forName("javax.jms.MessageListener", True,
            loader))));
        # unset the listener when leaving, even if an exception is thrown
        on_exit {
            consumer.setMessageListener(NOTHING);
        }

        connection.start();

        TextMessage message = session.createTextMessage();
        message.setText(text);

        producer.send(message);
        # wait for message to be received
        c.waitForZero();

        return data;
    }

    #! JMS message callback for the MessageListener interface
    private messageCallback(Method method, *list<auto> args) {
        TextMessage msg = args[0];
        # ignore redeliveries
        if (msg.getJMSRedelivered()) {
            return;
        }
        data = msg.getText();
        msg.acknowledge();
        if (verbose) {
            printf("*** JMS message received: %y (id: %y)\n", msg.getText(), msg.getJMSMessageID());
        }
        c.dec();
    }
}

#! the Java exceptions (and their subclasses) in the cause chain of an exception that mean that the JMS server or a
#! JMS resource is not available, with the reason for skipping the test
/** A missing JNDI provider class (javax.naming.NoInitialContextException) is not one of them: with $PAYARA_JAR set,
    the provider is in that JAR, and failing to load it is an error of the module's class loading, which the test must
    report
*/
const UnavailableExceptions = {
    "java.net.ConnectException": "the JMS server refused the connection",
    "java.net.NoRouteToHostException": "there is no route to the JMS server",
    "java.net.UnknownHostException": "the JMS server host is unknown",
    "java.net.SocketTimeoutException": "the connection to the JMS server timed out",
    "javax.naming.CommunicationException": "the JNDI server cannot be reached",
    "javax.naming.ServiceUnavailableException": "the JNDI service is not available",
    "javax.naming.NameNotFoundException": "the JMS connection factory or the queue does not exist on the server (the "
        "queue \"abc\" must be created in advance)",
    "org.omg.CORBA.COMM_FAILURE": "the ORB cannot reach the JNDI server",
    "org.omg.CORBA.TRANSIENT": "the ORB cannot reach the JNDI server",
};

#! returns a new JmsClient connected to the server
JmsClient sub jms_connect(string host, int port, string queue_name, bool verbose) {
    return new JmsClient(host, port, queue_name, verbose);
}

#! sends and receives a text message with the client given and returns the text received
*string sub jms_send_receive(JmsClient client, string text) {
    return client.sendAndReceive(text);
}

#! returns the reason for skipping the test if the exception means that the JMS server or a resource is not available
/** @return the reason, or @ref nothing if the exception is a real error
*/
*string sub jms_unavailable_reason(hash<ExceptionInfo> ex) {
    if (ex.err != "JNI-ERROR" || !(ex.arg instanceof Throwable)) {
        return;
    }
    # the identity hash codes of the throwables seen, to stop at a cycle in the cause chain
    hash<string, bool> seen;
    for (*Throwable t = ex.arg; t; t = t.getCause()) {
        string id = System::identityHashCode(t).toString();
        if (seen{id}) {
            break;
        }
        seen{id} = True;
        for (*Class cls = t.getClass(); cls; cls = cls.getSuperclass()) {
            *string reason = UnavailableExceptions{cls.getName()};
            if (reason) {
                return sprintf("%s (%s: %s)", reason, t.getClass().getName(), t.getMessage() ?? "");
            }
        }
    }
}
