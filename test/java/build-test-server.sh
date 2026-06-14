#!/bin/sh
# build-test-server.sh Copyright 2026 Qore Technologies, s.r.o.
# Rebuilds the OPC UA in-process test server jar used by OpcUaDataProvider.qtest.
# Compiles against the Milo 1.1.4 jars bundled with the module.
set -e
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
# join the jar paths with ':' WITHOUT a trailing separator (a trailing ':' adds an empty classpath
# element, which Java interprets as the current directory and can pollute compilation)
cp=$(ls "$root"/qlib/OpcUaDataProvider/jar/*.jar | paste -sd: -)
rm -rf "$here/classes"
mkdir -p "$here/classes"
javac -cp "$cp" -d "$here/classes" "$here/org/qore/opcua/test/QoreOpcUaTestServer.java"
jar cf "$root/test/opcua-test-server.jar" -C "$here/classes" .
echo "built $root/test/opcua-test-server.jar"
