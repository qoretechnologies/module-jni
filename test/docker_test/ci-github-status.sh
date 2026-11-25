#!/bin/sh
# Copyright (C) 2026 Qore Technologies, s.r.o.
# Publish the repository's required GitHub check for the entire pipeline (qore#7203).
set -eu
set +x

post() {
    state=$1
    case "$state" in
        pending) description='GitLab CI is running'; set -- --max-time 10 ;;
        success) description='Every required GitLab CI job passed'; set -- --max-time 30 --retry 3 --retry-delay 5 ;;
        failure) description='A required GitLab CI job failed or was canceled'; set -- --max-time 30 --retry 3 --retry-delay 5 ;;
    esac
    if [ -z "${GITHUB_ACCESS_TOKEN:-}" ]; then
        echo "Cannot publish GitHub status: GITHUB_ACCESS_TOKEN is unset" >&2
        return 1
    fi
    body=$(printf '{"state":"%s","context":"%s","description":"%s","target_url":"%s"}' \
        "$state" "$REPO_NAME" "$description" "$CI_PIPELINE_URL")
    curl -fsS -o /dev/null "$@" \
        "https://api.github.com/repos/qoretechnologies/${REPO_NAME}/statuses/${CI_COMMIT_SHA}" \
        --oauth2-bearer "$GITHUB_ACCESS_TOKEN" -H 'Content-Type: application/json' -d "$body"
}

case "${1:-}" in
    pending) post pending || echo "The initial GitHub status could not be published" >&2 ;;
    passed)
        # Only the job that needs every required job produces this artifact. On retry it also updates GitHub.
        printf '%s\n' "$CI_PIPELINE_ID:$CI_COMMIT_SHA" > ci-pipeline-passed
        post success
        ;;
    final)
        if [ -f ci-pipeline-passed ] && [ "$(cat ci-pipeline-passed)" = "$CI_PIPELINE_ID:$CI_COMMIT_SHA" ]; then
            post success
        else
            post failure
        fi
        ;;
    *) echo "Usage: $0 pending|passed|final" >&2; exit 2 ;;
esac
