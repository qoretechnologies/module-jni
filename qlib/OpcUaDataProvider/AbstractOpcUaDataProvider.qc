# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** AbstractOpcUaDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.OpcUaClient
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.identity.X509IdentityProvider
%module-cmd(jni) import java.security.KeyStore
%module-cmd(jni) import java.io.ByteArrayInputStream
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
%module-cmd(jni) import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
%module-cmd(jni) import org.eclipse.milo.opcua.sdk.client.DiscoveryClient
%module-cmd(jni) import java.util.Optional
%module-cmd(jni) import java.lang.reflect.Method

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! Abstract base class for OPC UA data provider classes
/**
*/
public class AbstractOpcUaDataProvider inherits DataProvider::AbstractDataProvider {
    private {
        #! The OPC UA client
        OpcUaClient client;

        #! The endpoint URL
        string url;

        #! Constructor options for the client
        hash<auto> copts;
    }

    #! Creates the object without any arguments for child classes
    private constructor() {
    }

    #! Creates the object from another object of the same class for child classes
    private constructor(AbstractOpcUaDataProvider prev) {
        client = prev.client;
        url = prev.url;
        copts = prev.copts;
        if (prev.logger) {
            setLogger(prev.logger);
        }
    }

    destructor() {
        if (client) {
            try {
                client.disconnectAsync().get();
                debug("OPC UA: Disconnected from %y", url);
            } catch (hash<ExceptionInfo> ex) {
                # ignore disconnect errors in destructor
            }
        }
        delete client;
    }

    #! Returns a configured OPC UA client for the given options
    static OpcUaClient getClient(hash<auto> copts, *LoggerInterface logger) {
        @debug {
            if (logger) {
                logger.debug("AbstractOpcUaDataProvider::getClient() url: %y", copts.url);
            }
        }

        # select security policy
        string security_policy_str = copts.security_policy ?? "None";

        # create endpoint selector function
        auto endpoint_selector = implement_interface(
            new QoreInvocationHandler(auto sub (Method method, *list<auto> args) {
                if (method.getName() != "apply") {
                    return;
                }
                auto endpoints = args[0];
                SecurityPolicy sec_policy = SecurityPolicy::valueOf(security_policy_str);
                # find endpoint matching the requested security policy
                foreach auto ep in (endpoints) {
                    if (ep.getSecurityPolicyUri() == sec_policy.getUri()) {
                        return Optional::of(ep);
                    }
                }
                # fall back to first endpoint
                if (endpoints.size() > 0) {
                    return Optional::of(endpoints[0]);
                }
                return Optional::empty();
            }),
            load_class("java/util/function/Function")
        );

        # create config builder consumer (Milo 1.1.x: a Consumer<OpcUaClientConfigBuilder> that mutates
        # the builder in place; it no longer returns a built config)
        auto config_builder = implement_interface(
            new QoreInvocationHandler(auto sub (Method method, *list<auto> args) {
                if (method.getName() != "accept") {
                    return;
                }
                auto cfg = args[0];
                cfg.setApplicationName(
                    LocalizedText::english(copts.application_name ?? "QoreOpcUaClient"));
                cfg.setApplicationUri(copts.application_uri ?? "urn:qore:opcua:client");
                cfg.setRequestTimeout(UInteger::valueOf(copts.request_timeout ?? 10000));
                cfg.setSessionTimeout(UInteger::valueOf(copts.session_timeout ?? 120000));

                # set identity provider based on auth_type
                string auth_type = copts.auth_type ?? "Anonymous";
                switch (auth_type) {
                    case "UsernamePassword":
                        cfg.setIdentityProvider(new UsernameProvider(
                            copts.username ?? "", copts.password ?? ""));
                        break;
                    case "Certificate": {
                        *string ks_location = copts."ssl.keystore.location";
                        if (!ks_location) {
                            throw "OPCUA-CONFIG-ERROR", "Certificate authentication requires "
                                "`ssl.keystore.location` to be set";
                        }
                        string ks_password = copts."ssl.keystore.password" ?? "";
                        string ks_type = copts."ssl.keystore.type" ?? "PKCS12";
                        # convert password string to char array (list of ints)
                        softlist<int> pw_chars = map ord($1), ks_password;
                        # read keystore file through Qore I/O (respects filesystem sandbox)
                        binary ks_data = ReadOnlyFile::readBinaryFile(ks_location);
                        KeyStore ks = KeyStore::getInstance(ks_type);
                        {
                            ByteArrayInputStream bais(ks_data);
                            ks.load(bais, pw_chars);
                        }
                        # get the first alias
                        string alias = ks.aliases().nextElement();
                        auto private_key = ks.getKey(alias, pw_chars);
                        auto cert = ks.getCertificate(alias);
                        cfg.setIdentityProvider(new X509IdentityProvider(cert, private_key));
                        break;
                    }
                    default:
                        cfg.setIdentityProvider(new AnonymousProvider());
                        break;
                }
            }),
            load_class("java/util/function/Consumer")
        );

        # transport config consumer (Milo 1.1.x): left at defaults
        auto transport_config = implement_interface(
            new QoreInvocationHandler(auto sub (Method method, *list<auto> args) {
            }),
            load_class("java/util/function/Consumer")
        );

        # Milo 1.1.x create(): endpoint url, endpoint selector, transport config consumer, config consumer
        OpcUaClient opcua_client = OpcUaClient::create(
            copts.url,
            endpoint_selector,
            transport_config,
            config_builder
        );

        return opcua_client;
    }
}
}
