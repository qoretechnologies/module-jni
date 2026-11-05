#!/bin/bash
#
# usage: check-loaded-modules.sh BUILD_DIR [RUNNER...]
#
# Fails unless every module that this repository installs loads from this job's own build, so that CI can never pass
# by testing a copy that the image already has. Run it with the QORE_MODULE_DIR that the tests use; RUNNER, if given,
# prefixes each qore command, as it does for the tests (for example "gosu qore:qore").
#
# The modules are taken from BUILD_DIR/install_manifest.txt: binary modules (<name>-api-<x>.<y>.qmod), AOT-compiled
# user modules (<name>.qmod, <name>/<name>.qmod) and user module sources (<name>.qm, <name>/<name>.qm). A module
# that this repository does not install, such as a dependency from the image, is not checked.
#
# A module passes only if the file that it loads from, with symbolic links resolved, is in the build directory or in
# this checkout's qlib directory, neither of which the image can have a copy in. A file that the manifest lists does
# not pass: the manifest gives the path that the install wrote to, which is also where the image has its own copy, so
# the path does not show which of the two is there (under "make install DESTDIR=...", the image's copy stays there and
# the manifest still lists it). The module path of the tests must therefore start with these two directories.
#
# Copyright 2026 Qore Technologies, s.r.o.

set -e

build_dir=$(readlink -f "$1")
shift
src_dir=$(readlink -f "$(dirname "$0")/../..")
manifest="${build_dir}/install_manifest.txt"

if [ ! -s "${manifest}" ]; then
    echo "ERROR: ${manifest} does not exist or is empty: this job installed nothing to test" >&2
    exit 1
fi

# the module files in the manifest; other .qm files installed below a module directory are parts of that module
module_files=$(grep -E '\.(qmod|qm)$' "${manifest}" || true)
modules=$(echo "${module_files}" | awk -F/ 'NF > 1 {
    name = $NF
    sub(/\.(qmod|qm)$/, "", name)
    sub(/-api-[0-9]+\.[0-9]+$/, "", name)
    dir = $(NF - 1)
    if (dir == name || dir == "qore-modules" || dir ~ /^[0-9]+(\.[0-9]+)*$/) {
        print name
    }
}' | sort -u)
if [ -z "${modules}" ]; then
    echo "ERROR: ${manifest} lists no modules" >&2
    exit 1
fi

failed=0
for module in ${modules}; do
    # the marker keeps anything else that loading the module prints on stdout out of the result
    loaded=$("$@" qore -l "${module}" \
        -e "printf(\"\nloaded-module-file=%s\n\", get_module_hash().\"${module}\".filename);" \
        | sed -n 's/^loaded-module-file=//p') || true
    if [ -z "${loaded}" ]; then
        echo "ERROR: module '${module}', which this job installed, cannot be loaded" >&2
        failed=1
        continue
    fi
    resolved=$(readlink -f "${loaded}")
    case "${resolved}" in
        "${build_dir}"/*|"${src_dir}/qlib"/*)
            ;;
        *)
            echo "ERROR: module '${module}' loads from '${loaded}', which is not in ${build_dir} or ${src_dir}/qlib" >&2
            failed=1
            continue
            ;;
    esac
    echo "-- module ${module} loads from ${loaded}"
done

exit ${failed}
