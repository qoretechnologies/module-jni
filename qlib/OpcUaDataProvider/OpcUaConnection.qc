# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaConnection.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the OpcUaDataProvider module
public namespace OpcUaDataProvider {
#! class for OPC UA connections
/** supports the options from @ref OpcUaDataProvider::ConstructorOptions
*/
public class OpcUaConnection inherits ConnectionProvider::AbstractConnectionWithInfo {
    public {
        #! Connection entry info
        const ConnectionScheme = <ConnectionSchemeInfo>{
            "display_name": "OPC UA Connection",
            "short_desc": "A connection to an OPC UA server",
            "desc": "A connection to an OPC UA server",
            "cls": Class::forName("OpcUaConnection"),
            "options": cast<hash<string, hash<ConnectionOptionInfo>>>(
                map {
                    $1.key: cast<hash<ConnectionOptionInfo>>($1.value - ("type", "required") + {
                        "type": OpcUaConnection::getType($1.value),
                    })
                }, OpcUaDataProvider::ConstructorOptions.pairIterator(),
                $1.key !~ /^monitored_/ && $1.key != "publishing_interval"
                && $1.key != "sampling_interval" && $1.key != "queue_size"
                && $1.key != "discard_oldest" && $1.key != "url"
            ),
            "schemes": {
                "opcua": True,
                "opcuatcp": True,
                "opcuahttps": True,
            },
            "base_scheme_map": {
                "opc.tcp": "opcuatcp",
                "opc.https": "opcuahttps",
            },
        };
    }

    #! Creates the object from the arguments
    constructor(string name, string desc, string url, hash<auto> attr = {}, hash<auto> options = {},
            *LoggerInterface logger) : AbstractConnectionWithInfo(name, desc, url, attr, options, logger) {
        setRealUrl(url);
    }

    #! Creates the object from the arguments
    /** @param config with the following keys:
        - name (required string): the connection name
        - display_name (optional string): the display name
        - short_desc (optional string): a short description in plain text
        - desc (optional string): a long description with markdown formatting
        - url (required string): the connection URL
        - opts (optional hash): connection options
        - logger (optional LoggerInterface object): logger for the connection
        @param attr optional connection attributes
        - monitor (optional bool): should the connection be monitored? Default: True
        - enabled (optional bool): is the connection enabled? Default: True
        - locked (optional bool): is the connection locked? Default: False
        - debug_data (optional bool): debug data? Default: False
        - tags (optional hash): tags for the connection; no default value

        @throw CONNECTION-OPTION-ERROR missing or invalid connection option or attribute
    */
    constructor(hash<auto> config, *hash<auto> attr) : AbstractConnectionWithInfo(config, attr) {
    }

    #! returns the string type name for the connection object
    string getType() {
        return "opcuatcp";
    }

    #! Returns @ref True, as this connection class returns a data provider with the @ref getDataProvider() method
    bool hasDataProvider() {
        return True;
    }

    #! Returns the data provider application name this connection serves
    *string getAppName() {
        return "OpcUA";
    }

    #! Returns an @ref OpcUaDataProvider object for this connection
    AbstractDataProvider getDataProvider(*hash<auto> constructor_options) {
        return new OpcUaDataProvider(orig_opts - ("monitored_nodes", "publishing_interval",
            "sampling_interval", "queue_size", "discard_oldest") + {"url": real_url}
            + constructor_options);
    }

    #! Returns the string type for the first data provider type
    static string getType(hash<DataProviderOptionInfo> opt) {
        string type = opt.type[0].getName();
        if (type[0] == "*") {
            splice type, 0, 1;
        }
        return type;
    }

    #! Returns the ConnectionSchemeInfo hash for this object
    private hash<ConnectionSchemeInfo> getConnectionSchemeInfoImpl() {
        return ConnectionScheme;
    }

    #! Returns a hash of data provider constructor option info, if applicable
    private *hash<string, hash<DataProviderOptionInfo>> getDataProviderConstructorOptionsImpl() {
        return OpcUaDataProvider::ConstructorOptions;
    }

    #! Override to set child data provider capabilities once per child class
    private setChildCapabilities() {
        children_can_support_observers = True;
        children_can_support_messages = True;
    }

    #! this method must return the connection object corresponding to the object's configuration
    private OpcUaConnectionWrapper getImpl(bool connect = True, *hash<auto> rtopts) {
        @debug {
            LoggerWrapper::debug("OpcUaConnection::getImpl() connect: %y rtopts: %y", connect, rtopts);
        }
        OpcUaClient opcua_client = AbstractOpcUaDataProvider::getClient(
            orig_opts + {"url": real_url} + rtopts, logger);
        if (connect) {
            opcua_client.connect().get();
        }
        return new OpcUaConnectionWrapper(opcua_client);
    }

    #! Called by the constructor to set the real URL
    private setRealUrl(*string url) {
        if (exists url) {
            real_url = url;
            if (url =~ /^opcuatcp:/) {
                real_url =~ s/^opcuatcp:/opc.tcp:/;
            } else if (url =~ /^opcuahttps:/) {
                real_url =~ s/^opcuahttps:/opc.https:/;
            } else if (url =~ /^opcua:/) {
                real_url =~ s/^opcua:/opc.tcp:/;
            }
        }
    }
}
}
