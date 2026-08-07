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

qore-data-provider-i18n --no-color --check-source-tree --output "${src_dir}/qlib"
