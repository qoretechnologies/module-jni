# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore OpcUaDataProvider module definition

/** OpcUaConnectionWrapper.qc Copyright 2026 Qore Technologies, s.r.o.

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
#! Class that encapsulates the OPC UA client
public class OpcUaConnectionWrapper {
    public {
        OpcUaClient client;
    }

    #! Creates the object from the arguments
    constructor(OpcUaClient client) {
        self.client = client;
    }

    #! Redirects method calls to the client
    auto methodGate(string method, ...) {
        return call_object_method_args(client, method, argv);
    }
}
}
