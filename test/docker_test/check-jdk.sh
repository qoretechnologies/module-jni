#!/bin/bash
#
# usage: check-jdk.sh MAJOR JAVA_HOME tools
#        check-jdk.sh MAJOR JAVA_HOME built BUILD_DIR [RUNNER...]
#
# Fails unless this job really uses JDK MAJOR.
#
# tools: java and javac on PATH and in JAVA_HOME are MAJOR, and JAVA_HOME holds the only libjvm in the container.
# built: the same, and also the module that BUILD_DIR built links the libjvm in JAVA_HOME, no jar that it installed
# has a class file newer than MAJOR, and a JVM started through the module (under RUNNER, as the tests run) is MAJOR.
#
# Copyright 2026 Qore Technologies, s.r.o.

set -e
set -o pipefail

major=$1
java_home=$2
mode=$3
script_dir=$(cd "$(dirname "$0")" && pwd)

fail() {
    echo "ERROR: check-jdk: $*" >&2
    exit 1
}

[[ "${major}" =~ ^[0-9]+$ ]] || fail "invalid major version '${major}'"
[ -x "${java_home}/bin/java" ] || fail "${java_home} has no bin/java"

# "Picked up JAVA_TOOL_OPTIONS" may precede the version on stderr
java_major() {
    "$1" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -n 1
}
javac_major() {
    "$1" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p' | head -n 1
}

for tool in java "${java_home}/bin/java"; do
    found=$(java_major "${tool}")
    [ "${found}" = "${major}" ] || fail "${tool} is version '${found}', not ${major}"
done
for tool in javac "${java_home}/bin/javac"; do
    found=$(javac_major "${tool}")
    [ "${found}" = "${major}" ] || fail "${tool} is version '${found}', not ${major}"
done
real_home=$(readlink -f "${java_home}")
[ "$(readlink -f "$(command -v java)")" = "${real_home}/bin/java" ] || fail "java on PATH is not in ${java_home}"
[ "$(readlink -f "$(command -v javac)")" = "${real_home}/bin/javac" ] || fail "javac on PATH is not in ${java_home}"

jvms=$(find / -xdev -name libjvm.so -not -path '/proc/*' -exec readlink -f {} + | sort -u)
[ -n "${jvms}" ] || fail "no libjvm.so found"
while read -r jvm; do
    case "${jvm}" in
        "${real_home}"/*) ;;
        *) fail "another JVM is present: ${jvm}" ;;
    esac
done <<< "${jvms}"
echo "-- JDK ${major}: java and javac in ${java_home}; only JVM: ${jvms//$'\n'/ }"

if [ "${mode}" = "tools" ]; then
    exit 0
fi
[ "${mode}" = "built" ] || fail "invalid mode '${mode}'"

build_dir=$4
shift 4
manifest=${build_dir}/install_manifest.txt
[ -s "${manifest}" ] || fail "${manifest} does not exist or is empty"

# the module that this job built links this JDK's libjvm
qmod=$(find "${build_dir}" -maxdepth 1 -name 'jni-api-*.qmod' | head -n 1)
[ -n "${qmod}" ] || fail "no jni module in ${build_dir}"
linked=$(ldd "${qmod}" | sed -n 's/.*libjvm\.so => \([^ ]*\).*/\1/p')
[ -n "${linked}" ] || fail "${qmod} does not resolve libjvm"
case "$(readlink -f "${linked}")" in
    "${real_home}"/*) ;;
    *) fail "${qmod} links ${linked}, not the libjvm in ${java_home}" ;;
esac
echo "-- ${qmod} links ${linked}"

# no installed jar needs a newer JVM; multi-release entries are chosen by the JVM at run time and are not checked
python3 -I -B -c '
import struct, sys, zipfile
limit = int(sys.argv[1]) + 44
checked = 0
bad = []
for path in map(str.strip, open(sys.argv[2])):
    if not path.endswith(".jar"):
        continue
    checked += 1
    with zipfile.ZipFile(path) as jar:
        newest = max((struct.unpack(">H", jar.read(n)[6:8])[0] for n in jar.namelist()
                      if n.endswith(".class") and not n.startswith("META-INF/")), default=0)
    if newest > limit:
        bad.append("%s (class file %d)" % (path, newest))
if not checked:
    sys.exit("ERROR: check-jdk: the install manifest lists no jars")
if bad:
    sys.exit("ERROR: check-jdk: jars newer than class file %d:\n  %s" % (limit, "\n  ".join(bad)))
print("-- %d installed jars have class files of at most %d" % (checked, limit))
' "${major}" "${manifest}"

# the JVM that the module really starts, under the tests' user and module path
output=$("$@" qore "${script_dir}/print-jvm.qr" 2>&1) || fail "cannot start a JVM through the module: ${output}"
version=$(sed -n 's/^jvm-version=//p' <<< "${output}")
home=$(sed -n 's/^jvm-home=//p' <<< "${output}")
libs=$(sed -n 's/^jvm-lib=//p' <<< "${output}")
[ "${version%%.*}" = "${major}" ] || fail "the module's JVM is version '${version}', not ${major}"
[ "$(readlink -f "${home}")" = "${real_home}" ] || fail "the module's JVM runs from '${home}', not ${java_home}"
[ -n "${libs}" ] || fail "no libjvm is mapped in the module's process"
while read -r lib; do
    case "$(readlink -f "${lib}")" in
        "${real_home}"/*) ;;
        *) fail "the module's process mapped ${lib}" ;;
    esac
done <<< "${libs}"
echo "-- the module's JVM: version ${version}, home ${home}, libjvm ${libs//$'\n'/ }"
