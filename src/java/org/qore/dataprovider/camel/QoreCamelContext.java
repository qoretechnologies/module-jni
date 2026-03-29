/*  QoreCamelContext.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.Route;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.qore.jni.Hash;

/**
 * Wraps an Apache Camel CamelContext for managing routes and sending/receiving messages.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class QoreCamelContext implements Closeable {
    private DefaultCamelContext context;

    /**
     * Creates a new QoreCamelContext and starts the underlying CamelContext.
     *
     * @throws Exception if the context fails to start
     */
    public QoreCamelContext() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    /**
     * Sends a message to an endpoint and returns the response.
     *
     * @param endpointUri the target endpoint URI
     * @param headers     optional headers to set on the message (may be null)
     * @param body        optional body content as a byte array (may be null)
     * @return a Hash with "body" (response body as String or byte[]) and "headers" (response headers as Hash)
     * @throws Exception if sending the message fails
     */
    public Hash sendMessage(String endpointUri, Hash headers, byte[] body) throws Exception {
        ProducerTemplate producer = context.createProducerTemplate();
        try {
            Exchange result = producer.send(endpointUri, exchange -> {
                if (body != null) {
                    exchange.getIn().setBody(body);
                }
                if (headers != null) {
                    for (Map.Entry<String, Object> entry : headers.entrySet()) {
                        exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                    }
                }
            });

            Hash response = new Hash();
            Object responseBody = result.getMessage().getBody();
            response.put("body", responseBody != null ? responseBody.toString() : null);
            Hash responseHeaders = new Hash();
            for (Map.Entry<String, Object> entry : result.getMessage().getHeaders().entrySet()) {
                responseHeaders.put(entry.getKey(), entry.getValue());
            }
            response.put("headers", responseHeaders);

            if (result.getException() != null) {
                throw result.getException();
            }

            return response;
        } finally {
            producer.close();
        }
    }

    /**
     * Simplified method to send a body and headers to an endpoint.
     *
     * @param endpointUri the target endpoint URI
     * @param body        the message body (may be null)
     * @param headers     optional headers to set on the message (may be null)
     * @return a Hash with "body" (response body) and "headers" (response headers as Hash)
     * @throws Exception if sending the message fails
     */
    public Hash sendBodyAndHeaders(String endpointUri, Object body, Hash headers) throws Exception {
        ProducerTemplate producer = context.createProducerTemplate();
        try {
            Map<String, Object> headerMap = null;
            if (headers != null) {
                headerMap = new java.util.HashMap<>(headers);
            }

            producer.sendBodyAndHeaders(endpointUri, body, headerMap);

            Hash response = new Hash();
            response.put("body", body != null ? body.toString() : null);
            response.put("headers", headers != null ? headers : new Hash());
            return response;
        } finally {
            producer.close();
        }
    }

    /**
     * Adds a simple route from one endpoint to another.
     *
     * @param fromUri the source endpoint URI
     * @param toUri   the destination endpoint URI
     * @throws Exception if adding the route fails
     */
    public void addRoute(String fromUri, String toUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fromUri).to(toUri);
            }
        });
    }

    /**
     * Returns a list of all routes with their id, endpoint URI, and status.
     *
     * @return a list of Hash entries with "id", "endpoint_uri", and "status"
     */
    public ArrayList<Hash> getRoutes() {
        ArrayList<Hash> list = new ArrayList<>();
        for (Route route : context.getRoutes()) {
            Hash h = new Hash();
            h.put("id", route.getId());
            h.put("endpoint_uri", route.getEndpoint().getEndpointUri());
            h.put("status", context.getRouteController().getRouteStatus(route.getId()).toString());
            list.add(h);
        }
        return list;
    }

    /**
     * Starts the CamelContext.
     *
     * @throws Exception if the context fails to start
     */
    public void start() throws Exception {
        context.start();
    }

    /**
     * Stops the CamelContext.
     *
     * @throws Exception if the context fails to stop
     */
    public void stop() throws Exception {
        context.stop();
    }

    /**
     * Checks whether the CamelContext is started.
     *
     * @return true if the context is started, false otherwise
     */
    public boolean isStarted() {
        return context.isStarted();
    }

    /**
     * Closes the CamelContext and releases resources.
     */
    @Override
    public void close() throws IOException {
        try {
            if (context != null) {
                context.stop();
                context.close();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to close CamelContext", e);
        } finally {
            context = null;
        }
    }
}
