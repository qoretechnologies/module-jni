# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsBrowserDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! JMS browser data provider for non-destructive queue browsing
/**
*/
public class JmsBrowserDataProvider inherits JmsDataProviderBase {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "name": "browser",
            "desc": "Data provider for non-destructive browsing of JMS queue contents",
            "supports_read": True,
            "supports_request": True,
        };

        #! Provider summary info
        const ProviderSummaryInfo = cast<hash<DataProviderSummaryInfo>>(ProviderInfo{
            AbstractDataProvider::DataProviderSummaryInfoKeys
        });
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
        return "browser";
    }

    #! Returns data provider static info
    hash<DataProvider::DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }

    #! Makes a browse request and returns the messages
    /** @param req the request containing the queue to browse
        @param request_options the request options

        @return a list of message hashes from the queue
    */
    private auto doRequestImpl(auto req, *hash<auto> request_options) {
        # cancellation point before blocking JNI operations
        sleep(0);

        string dest_str = req.destination ?? copts.destination;
        if (!dest_str.val()) {
            throw "JMS-BROWSER-ERROR", "no destination specified; provide 'destination' in the request "
                "or set the 'destination' constructor option";
        }

        JmsSessionPool pool = parent_provider.getSessionPool();
        Session session = pool.acquire(copts.connection_timeout_ms ?? 5000);
        on_exit pool.release(session);

        # parse destination - browser only works with queues
        Destination dest = JmsDataProviderBase::parseDestination(session, dest_str);
        if (!(dest instanceof jms::Queue)) {
            throw "JMS-BROWSER-ERROR", "QueueBrowser can only browse queues, not topics";
        }
        jms::Queue queue = cast<jms::Queue>(dest);

        # build message selector if filter clause provided
        *string selector = JmsSelectorBuilder::build(req.selector);

        QueueBrowser browser;
        if (selector.val()) {
            browser = session.createBrowser(queue, selector);
        } else {
            browser = session.createBrowser(queue);
        }
        on_exit browser.close();

        list<hash<auto>> result = ();
        auto msg_list = Collections::list(browser.getEnumeration());
        if (msg_list instanceof list) {
            foreach auto item in (msg_list) {
                push result, JmsTypeMapper::fromJmsMessage(cast<Message>(item));
            }
        } else {
            for (int i = 0, int sz = msg_list.size(); i < sz; ++i) {
                push result, JmsTypeMapper::fromJmsMessage(cast<Message>(msg_list.get(i)));
            }
        }
        return result;
    }

    #! Returns the description of a successful request message, if any
    private *AbstractDataProviderType getRequestTypeImpl() {
        return JmsBrowseRequestDataType;
    }

    #! Returns the description of a response message
    private *AbstractDataProviderType getResponseTypeImpl() {
        return JmsEventDataType;
    }
}
}
