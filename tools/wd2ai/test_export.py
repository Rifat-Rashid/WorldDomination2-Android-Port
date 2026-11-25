"""Integrity tests for the reviewed dataset boundary. Fixtures are never training data."""
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from collect import endpoint
from export_dataset import evidence, family_split, fingerprint, principal, sample_families, unambiguous_choices


class DatasetContractTests(unittest.TestCase):
    def test_conflicting_semantic_labels_drop_both_new_choices_and_preserve_the_parent(self):
        def record(name, state, plan):
            return {"audit": {"decision_id": name}, "fingerprint": (state, plan)}

        rows = [record("old", "opening", "invest"), record("new", "opening", "attack"),
                record("a", "late", "fund"), record("b", "late", "defend"),
                record("unique", "threat", "defend")]
        for order in (rows, list(reversed(rows))):
            self.assertEqual({"old", "unique"},
                             {r["audit"]["decision_id"] for r in unambiguous_choices(order, {"old"})})

    def test_review_role_alias_cannot_approve_own_decisions(self):
        self.assertEqual(principal("teacher-b-v2"), principal("Teacher-B-independent-review"))
        self.assertNotEqual(principal("teacher-a"), principal("teacher-b-independent-review"))

    def test_oversubscribed_split_balances_families_independent_of_file_order(self):
        records = [{"audit": {"family": family, "decision_id": f"{family}-{turn}"}}
                   for family in ("A", "B", "C") for turn in range(9)]
        selected = list(sample_families(records, 6))
        self.assertEqual(selected, list(sample_families(reversed(records), 6)))
        self.assertEqual(6, len({r["audit"]["decision_id"] for r in selected}))
        for family in ("A", "B", "C"):
            self.assertEqual(2, sum(r["audit"]["family"] == family for r in selected))

    def test_family_split_is_stable(self):
        self.assertEqual("train", family_split("teacher-b-83321"))
        self.assertEqual("validation", family_split("teacher-c-84000"))
        self.assertEqual("test", family_split("teacher-c-84008"))

    def test_alias_and_small_budget_change_are_one_decision(self):
        research = {"action": "RESEARCH", "reserve": 11}
        income = {"action": "RESOURCES", "reserve": 0}
        chosen = [research, income]
        original = {"turn": 5, "own": {"budget": 61}, "plans": {"P01": chosen, "P02": [income, income]}}
        changed = copy.deepcopy(original)
        changed["turn"] += 1
        changed["own"]["budget"] += 3
        changed["plans"]["P09"] = changed["plans"].pop("P01")
        changed["plans"]["P09"][0]["reserve"] = 12
        self.assertEqual(fingerprint(original, chosen), fingerprint(changed, changed["plans"]["P09"]))
        self.assertNotEqual(fingerprint(original, chosen), fingerprint(original, [income, research]))

    def test_replay_proof_requires_identical_log_and_screenshot_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            log = directory / "episode.jsonl"
            log.write_bytes(b"test trajectory")
            receipt = {"episode": "one", "log_sha256": hashlib.sha256(log.read_bytes()).hexdigest(),
                       "outcome": {"terminal": True}, "device_test": "result.txt"}
            for field in ("checkpoint", "screenshot", "fixture"):
                path = directory / field
                path.write_bytes(field.encode())
                receipt[field] = path.name
                receipt[field + "_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
            (directory / "result.txt").write_text("OK (1 test)")
            (directory / "one-receipt.json").write_text(json.dumps(receipt))
            self.assertEqual(receipt, evidence(log, directory, "one", receipt["outcome"]))
            (directory / "screenshot").write_bytes(b"replaced")
            with self.assertRaisesRegex(ValueError, "screenshot evidence"):
                evidence(log, directory, "one", receipt["outcome"])

    def test_collector_accepts_only_local_endpoints(self):
        for url in ("http://127.0.0.1:8795@remote.example", "http://example.com:8795",
                    "http://127.0.0.1:8795/other", "https://127.0.0.1:8795"):
            with self.subTest(url=url), patch.dict(os.environ, {"WD2_TEACHER_URL": url}):
                with self.assertRaises(ValueError):
                    endpoint()
