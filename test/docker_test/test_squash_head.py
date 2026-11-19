#!/usr/bin/env python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check squash candidates independently of whether an earlier CI pipeline passed."""

import io
import json
import os
import unittest
from unittest.mock import patch

import squash_head as ci


class SquashHeadTest(unittest.TestCase):
    def setUp(self):
        self.merge, self.tip, self.tree = ("a" * 40, "b" * 40, "c" * 40)
        self.repo = "qoretechnologies/module-example"
        self.env = dict(CI_COMMIT_BRANCH="develop", CI_PIPELINE_SOURCE="push", CI_COMMIT_SHA=self.merge,
                        GITHUB_ACCESS_TOKEN="fixture", CI_PROJECT_NAME="module-example")
        self.pr = dict(state="closed", merged_at="today", merge_commit_sha=self.merge,
                       base=dict(ref="develop", repo=dict(full_name=self.repo)), head=dict(sha=self.tip))
        self.commit = dict(sha=self.tip, tree=dict(sha=self.tree))

    def check(self, **changes):
        def serve(request, timeout):
            self.assertEqual("Bearer fixture", request.unredirected_hdrs["Authorization"])
            self.assertNotIn("Authorization", request.headers)
            prefix = "https://api.github.com/repos/" + self.repo + "/"
            self.assertTrue(request.full_url.startswith(prefix))
            path = request.full_url[len(prefix):]
            payload = {f"commits/{self.merge}/pulls?per_page=100": [self.pr],
                       f"git/commits/{self.tip}": self.commit}[path]
            return io.BytesIO(json.dumps(payload).encode())
        with patch.dict(os.environ, dict(self.env, **changes), clear=True), patch.object(ci, "urlopen", side_effect=serve):
            return ci.squash_head(self.merge, self.tree)

    def test_only_return_the_merged_pr_with_the_identical_tree(self):
        self.assertEqual(self.tip, self.check())
        cases = (
            (self.pr, "state", "open"),
            (self.pr, "merged_at", None),
            (self.pr, "merge_commit_sha", self.tip),
            (self.pr["base"], "ref", "other"),
            (self.pr["base"]["repo"], "full_name", "another/repo"),
            (self.pr["head"], "sha", "invalid"),
            (self.commit, "sha", self.merge),
            (self.commit["tree"], "sha", self.tip),
        )
        for obj, key, value in cases:
            with self.subTest(key=key), patch.dict(obj, {key: value}):
                self.assertIsNone(self.check())

    def test_ineligible_runs_and_checkout_mismatch(self):
        for change in (dict(CI_COMMIT_BRANCH="feature"), dict(CI_PIPELINE_SOURCE="schedule"),
                       dict(CI_COMMIT_SHA=self.tip), dict(QORE_CI_FORCE_FULL="1"),
                       dict(CI_PROJECT_NAME="unrelated"), dict(GITHUB_ACCESS_TOKEN="")):
            with self.subTest(change=change):
                self.assertIsNone(self.check(**change))

    def test_invalid_or_unavailable_github_data(self):
        with patch.dict(os.environ, self.env, clear=True):
            for failure in (OSError(), ValueError(), TypeError(), KeyError()):
                with self.subTest(failure=failure), patch.object(ci, "urlopen", side_effect=failure):
                    self.assertIsNone(ci.squash_head(self.merge, self.tree))


if __name__ == "__main__":
    unittest.main()
