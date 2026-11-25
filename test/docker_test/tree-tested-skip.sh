#!/bin/sh
# -*- mode: sh -*-
# Copyright (C) 2026 Qore Technologies, s.r.o.
#
# Sourced first by every job of the pipeline (before anything is built, tested or reported): when the
# tree-already-tested job found that this commit's tree already passed CI (QORE_TREE_TESTED_IN, see
# tree-already-tested.sh), the job names the pipeline that passed the tree and ends at once.  Otherwise nothing happens.  It is sourced, so its "exit" ends the job's script.

if [ -n "${QORE_TREE_TESTED_IN:-}" ]; then
    tested_url="${CI_PROJECT_URL}/-/pipelines/${QORE_TREE_TESTED_IN}"
    echo "this tree already passed in pipeline ${QORE_TREE_TESTED_IN} (commit ${QORE_TREE_TESTED_SHA:-?}):"
    echo "  ${tested_url}"
    echo "nothing to do"
    exit 0
fi
