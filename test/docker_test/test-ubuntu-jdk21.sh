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

die() {
    echo "ERROR: $*" >&2
    exit 1
}

echo "-- installing ${JDK_PACKAGE} --"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq || die "cannot fetch the apt package lists for ${JDK_PACKAGE}"
# apt-get update can end with success when a fetch failed, which then shows here
apt-get install -y -qq --no-install-recommends "${JDK_PACKAGE}" \
    || die "cannot install ${JDK_PACKAGE} from the distribution's repository (see the apt output above)"

# dpkg refuses to remove a package that another one depends on, so this cannot take anything else with it
dpkg --remove "${IMAGE_JDK_PACKAGE}"
rm -f /usr/lib/jvm/default-jvm
update-alternatives --set java "${JDK_HOME}/bin/java"
update-alternatives --set javac "${JDK_HOME}/bin/javac"

# env.sh also derives the libjsig preload from JAVA_HOME
sed -i "s|^export JAVA_HOME=.*|export JAVA_HOME=${JDK_HOME}|" "${ENV_FILE}"
java_home_lines=$(grep -c "^export JAVA_HOME=${JDK_HOME}\$" "${ENV_FILE}" || true)
if [ "${java_home_lines}" != "1" ]; then
    die "${ENV_FILE} must set JAVA_HOME exactly once to rewrite it to ${JDK_HOME}; found ${java_home_lines} such lines"
fi

(. "${ENV_FILE}" && "${script_dir}/check-jdk.sh" "${JDK_MAJOR}" "${JDK_HOME}" tools)

rc=0
"${script_dir}/test-ubuntu.sh" || rc=$?

# the install manifest is written when the install completes: without it, there is no build to check and no test ran
if [ "${rc}" != "0" ] && [ ! -s "${src_dir}/build/install_manifest.txt" ]; then
    echo "ERROR: the build or install failed on JDK ${JDK_MAJOR} (test-ubuntu.sh exit code ${rc}); no test ran" >&2
    exit "${rc}"
fi

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
if [ "${rc}" != "0" ]; then
    echo "ERROR: the tests or checks failed on JDK ${JDK_MAJOR} (test-ubuntu.sh exit code ${rc})" >&2
fi
exit "${rc}"
