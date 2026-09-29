#!/usr/bin/python3
# Copyright (C) 2026 David Nichols
# SPDX-License-Identifier: MIT
"""Compile first-party fixtures against the installed bridge and provider JARs."""

from pathlib import Path
import subprocess

root = Path("test")
# Test-only Java helpers are imported by the upstream suites through relative
# JAR paths. Supply that layout using installed files, without source modules.
for provider_jars in sorted(Path("/usr/share/qore-modules").glob("*/jar")):
    target = Path("qlib") / provider_jars.parent.name / "jar"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.symlink_to(provider_jars, target_is_directory=True)
output = root / "java-classes"
output.mkdir()
subprocess.run(["javac", "--release", "21", "-cp",
                "/usr/share/qore/java/qore-jni.jar", "-d", str(output)]
               + [str(p) for p in sorted((root / "java/src").rglob("*.java"))], check=True)
subprocess.run(["jar", "cf", str(root / "qore-jni-test.jar"), "-C", str(output), "."], check=True)
provider = Path("/usr/share/qore-modules/OpcUaDataProvider/jar")
classpath = ":".join(str(p) for p in sorted(provider.glob("*.jar")))
if not classpath:
    raise ValueError("Installed OPC UA provider JARs are missing")
server_output = root / "opcua-classes"
server_output.mkdir()
subprocess.run(["javac", "--release", "21", "-cp", classpath, "-d", str(server_output),
                str(root / "java/org/qore/opcua/test/QoreOpcUaTestServer.java")], check=True)
subprocess.run(["jar", "cf", str(root / "opcua-test-server.jar"), "-C", str(server_output), "."], check=True)
