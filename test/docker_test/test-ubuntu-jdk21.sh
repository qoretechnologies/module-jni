#!/bin/bash
#
# Builds and tests the module on JDK 21, the JDK of the Debian packages and of the RPMs below Fedora 44, in the
# qore-test-base image, whose JDK is 25.  In this job's container only, OpenJDK 21 replaces the image's JDK, so that
# nothing can fall back to 25, and test-ubuntu.sh then runs unchanged.  The job fails unless check-jdk.sh proves JDK 21
# both before the build and, from what was built and from a running JVM, after the tests.
#
# Copyright 2026 Qore Technologies, s.r.o.

set -e
set -o pipefail

JDK_MAJOR=21
JDK_PACKAGE=openjdk-${JDK_MAJOR}-jdk-headless
JDK_HOME=/usr/lib/jvm/java-${JDK_MAJOR}-openjdk-amd64
IMAGE_JDK_PACKAGE=temurin-25-jdk
ENV_FILE=/tmp/env.sh

script_dir=$(cd "$(dirname "$0")" && pwd)
src_dir=$(cd "${script_dir}/../.." && pwd)

echo "-- installing ${JDK_PACKAGE} --"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends "${JDK_PACKAGE}"

# dpkg refuses to remove a package that another one depends on, so this cannot take anything else with it
dpkg --remove "${IMAGE_JDK_PACKAGE}"
rm -f /usr/lib/jvm/default-jvm
update-alternatives --set java "${JDK_HOME}/bin/java"
update-alternatives --set javac "${JDK_HOME}/bin/javac"

# env.sh also derives the libjsig preload from JAVA_HOME
sed -i "s|^export JAVA_HOME=.*|export JAVA_HOME=${JDK_HOME}|" "${ENV_FILE}"
[ "$(grep -c "^export JAVA_HOME=${JDK_HOME}\$" "${ENV_FILE}")" = "1" ]

(. "${ENV_FILE}" && "${script_dir}/check-jdk.sh" "${JDK_MAJOR}" "${JDK_HOME}" tools)

rc=0
"${script_dir}/test-ubuntu.sh" || rc=$?

# always checked, also after a failed test: a result is only worth something on the right JDK
proof=0
(
    . "${ENV_FILE}"
    export QORE_CLASSPATH=${src_dir}/build/qore-jni.jar
    export QORE_MODULE_DIR=${src_dir}/qlib:${src_dir}/build:${src_dir}/test/JakartaJms/lib:${QORE_MODULE_DIR}
    cd "${src_dir}"
    "${script_dir}/check-jdk.sh" "${JDK_MAJOR}" "${JDK_HOME}" built "${src_dir}/build" gosu qore:qore
) || proof=$?

if [ "${proof}" != "0" ]; then
    echo "ERROR: this job did not prove JDK ${JDK_MAJOR}" >&2
    exit 1
fi
exit "${rc}"
