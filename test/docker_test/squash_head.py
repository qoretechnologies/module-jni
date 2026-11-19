#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Find the tested PR tip of a develop squash merge, including in a shallow checkout."""

import json
import os
import re
import sys
from http.client import HTTPException
from urllib.request import Request, urlopen


SHA = re.compile(r"[0-9a-f]{40}")


def merged_tip(read, repo, head, tree):
    for pr in read(f"commits/{head}/pulls?per_page=100"):
        if (pr["state"] != "closed" or not pr["merged_at"] or pr["merge_commit_sha"] != head
                or pr["base"]["ref"] != "develop" or pr["base"]["repo"]["full_name"] != repo):
            continue
        tip = pr["head"]["sha"]
        if not SHA.fullmatch(tip) or tip == head:
            continue
        commit = read(f"git/commits/{tip}")
        if commit["sha"] == tip and commit["tree"]["sha"] == tree:
            return tip
    return None


def squash_head(head, tree):
    # Existing commit/parent evidence remains usable even if GitHub is unavailable.
    env = os.environ
    if (env.get("CI_COMMIT_BRANCH") != "develop" or env.get("CI_PIPELINE_SOURCE") != "push"
            or env.get("CI_COMMIT_SHA") != head or env.get("QORE_CI_FORCE_FULL") == "1"
            or not SHA.fullmatch(head) or not SHA.fullmatch(tree)):
        return None
    name = env.get("CI_PROJECT_NAME", "")
    token = env.get("GITHUB_ACCESS_TOKEN")
    if not token or not re.fullmatch(r"module-[a-z0-9-]+", name):
        return None
    repo = "qoretechnologies/" + name

    def read(path):
        request = Request(f"https://api.github.com/repos/{repo}/{path}",
                          headers={"Accept": "application/vnd.github+json"})
        request.add_unredirected_header("Authorization", "Bearer " + token)
        with urlopen(request, timeout=15) as response:
            return json.load(response)

    try:
        return merged_tip(read, repo, head, tree)
    except (OSError, HTTPException, KeyError, TypeError, ValueError):
        return None


if __name__ == "__main__":
    candidate = squash_head(*sys.argv[1:])
    if candidate:
        print(candidate)
