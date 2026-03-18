# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsDataProviderBase.qc Copyright 2026 Qore Technologies, s.r.o.

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

# import Jakarta JMS classes
%module-cmd(jni) import jakarta.jms.ConnectionFactory
%module-cmd(jni) import jakarta.jms.Connection
%module-cmd(jni) import jakarta.jms.Session
%module-cmd(jni) import jakarta.jms.Destination
%module-cmd(jni) import jakarta.jms.Queue
%module-cmd(jni) import jakarta.jms.Topic
%module-cmd(jni) import jakarta.jms.Message
%module-cmd(jni) import jakarta.jms.TextMessage
%module-cmd(jni) import jakarta.jms.MapMessage
%module-cmd(jni) import jakarta.jms.BytesMessage
%module-cmd(jni) import jakarta.jms.MessageProducer
%module-cmd(jni) import jakarta.jms.MessageConsumer
%module-cmd(jni) import jakarta.jms.MessageListener
%module-cmd(jni) import jakarta.jms.QueueBrowser
%module-cmd(jni) import jakarta.jms.TemporaryQueue
%module-cmd(jni) import jakarta.jms.DeliveryMode
%module-cmd(jni) import jakarta.jms.JMSException
%module-cmd(jni) import java.util.Enumeration
%module-cmd(jni) import java.util.Collections
%module-cmd(jni) import java.util.ArrayList

%try-module json >= 1.5
%define NoJson
%endtry

%try-module yaml
%define NoYaml
%endtry

#! contains all public definitions in the JakartaJmsDataProvider module
public namespace JakartaJmsDataProvider {
#! Java byte[] class reference for use with getBody() — cached to avoid repeated load_class() calls
/** The JNI bridge automatically converts Java \c byte[] return values to Qore \c binary.
    Use this with \c Message.getBody(ByteArrayClass) to extract binary message bodies.
*/
public const ByteArrayClass = load_class("[B");

#! Base class for JMS data provider classes with shared JNI imports and utility methods
/**
*/
public class JmsDataProviderBase inherits DataProvider::AbstractDataProvider {
    private {
        #! Connection options
        hash<auto> copts;
    }

    #! Creates the object from constructor options
    constructor(hash<auto> copts) {
        self.copts = copts;
    }

    #! Creates a JMS ConnectionFactory from options
    /** @param copts the connection options

        @return the ConnectionFactory instance

        @note The JMS provider JARs must be on the classpath before this method is called.
        Use the \c provider_classpath option to specify JARs to add, or set the
        \c QORE_JNI_CLASSPATH environment variable, or use
        \c %%module-cmd(jni) add-classpath directives in your code.
    */
    static ConnectionFactory createConnectionFactory(hash<auto> copts) {
        # load provider JARs at runtime if specified
        if (copts.provider_classpath) {
            list<string> paths = copts.provider_classpath.split(",");
            foreach string path in (paths) {
                path = trim(path);
                if (path.val()) {
                    Program::issueModuleCmd("jni", "add-classpath " + path);
                }
            }
        }

        # load the connection factory class
        lang::Class factory_cls = load_class(copts.connection_factory_class);

        # try single-arg String constructor with URL (works for Artemis, TIBCO, etc.)
        try {
            auto factory = factory_cls.getConstructor(load_class("java/lang/String"))
                .newInstance(copts.url);
            return cast<ConnectionFactory>(factory);
        } catch () {
            # fall through to no-arg constructor
        }

        # try no-arg constructor + setter
        auto factory = factory_cls.getConstructor().newInstance();

        # try common setter methods
        list<string> setter_methods = ("setBrokerURL", "setServerUrl", "setBrokerUri",
            "setHostName", "setConnectionString");
        bool url_set;
        foreach string method_name in (setter_methods) {
            try {
                factory_cls.getMethod(method_name, load_class("java/lang/String"))
                    .invoke(factory, copts.url);
                url_set = True;
                break;
            } catch () {
                # try next method
            }
        }
        if (!url_set) {
            throw "JMS-CONNECTION-ERROR", sprintf("cannot set URL on connection factory class %y; "
                "tried constructors and setter methods: %y", copts.connection_factory_class,
                setter_methods);
        }
        return cast<ConnectionFactory>(factory);
    }

    #! Creates a JMS Connection from a ConnectionFactory
    /** @param factory the ConnectionFactory
        @param copts the connection options

        @return the JMS Connection
    */
    static Connection createJmsConnection(ConnectionFactory factory, hash<auto> copts) {
        Connection conn;
        if (copts.username.val()) {
            conn = factory.createConnection(copts.username, copts.password);
        } else {
            conn = factory.createConnection();
        }
        if (copts.client_id.val()) {
            conn.setClientID(copts.client_id);
        }
        return conn;
    }

    #! Parses a destination string into a Destination object
    /** @param session the JMS Session
        @param dest_str destination string in format "queue://name" or "topic://name"

        @return the Destination object
    */
    static Destination parseDestination(Session session, string dest_str) {
        if (dest_str =~ /^queue:\/\//) {
            string name = dest_str;
            name =~ s/^queue:\/\///;
            return session.createQueue(name);
        }
        if (dest_str =~ /^topic:\/\//) {
            string name = dest_str;
            name =~ s/^topic:\/\///;
            return session.createTopic(name);
        }
        # default: treat as queue name
        return session.createQueue(dest_str);
    }

    #! Returns the session acknowledge mode integer from option string
    static int getSessionMode(string mode) {
        switch (mode) {
            case "AUTO_ACKNOWLEDGE": return Session::AUTO_ACKNOWLEDGE;
            case "CLIENT_ACKNOWLEDGE": return Session::CLIENT_ACKNOWLEDGE;
            case "DUPS_OK_ACKNOWLEDGE": return Session::DUPS_OK_ACKNOWLEDGE;
            case "SESSION_TRANSACTED": return Session::SESSION_TRANSACTED;
        }
        throw "JMS-SESSION-ERROR", sprintf("unknown session mode %y; valid modes: AUTO_ACKNOWLEDGE, "
            "CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE, SESSION_TRANSACTED", mode);
    }
}
}
