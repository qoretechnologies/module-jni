#!/bin/bash
#
# Verifies every source-owned data-provider i18n catalog in this module.
#
# Copyright 2026 Qore Technologies, s.r.o.

set -e

src_dir=$(cd "$(dirname "$0")/../.." && pwd)

# Load the qmods built from this checkout first. This validates the exact installable artifacts and honors Qore's
# qmod-first module loading without allowing an older installed module to hide catalog drift.
export QORE_MODULE_DIR="${src_dir}/build/qlib-qmod${QORE_MODULE_DIR:+:${QORE_MODULE_DIR}}"

# Provider libraries intentionally depend only on logging APIs; applications choose the logging implementation. The
# metadata-only check does not log, so silence the APIs' missing-provider status diagnostics without hiding Qore errors.
export QORE_JNI_JVM_ARGS="${QORE_JNI_JVM_ARGS:+${QORE_JNI_JVM_ARGS} }-Dslf4j.internal.verbosity=ERROR -Dlog4j2.statusLoggerLevel=OFF"

qore-data-provider-i18n --no-color --check-source-tree --require-standard-locales --output "${src_dir}/qlib"
