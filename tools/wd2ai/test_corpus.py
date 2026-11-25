import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from corpus import ORIGINAL_COUNTS, expected_counts, frozen_decisions, verify_dataset


class CorpusTests(unittest.TestCase):
    def test_legacy_quota_and_explicit_extension(self):
        self.assertEqual(ORIGINAL_COUNTS, expected_counts({}))
        expanded = dict(ORIGINAL_COUNTS, train=6400)
        self.assertEqual(expanded, expected_counts({"target_counts": expanded}))
        for targets in (dict(expanded, train=12), dict(expanded, test=0), dict(expanded, train=True)):
            with self.subTest(targets=targets), self.assertRaises(ValueError):
                expected_counts({"target_counts": targets})

    def test_parent_order_and_raw_split_digest_are_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            families, hashes, audit = {}, {}, []
            for split, n in ORIGINAL_COUNTS.items():
                family = split + "-family"
                families[family] = split
                records = []
                for i in range(n):
                    line = json.dumps({"test_fixture": i}).encode()
                    records.append(line)
                    audit.append({"split": split, "row": i + 1, "family": family,
                                  "row_sha256": hashlib.sha256(line).hexdigest()})
                content = b"\n".join(records) + b"\n"
                (d / (split + ".jsonl")).write_bytes(content)
                hashes[split] = hashlib.sha256(content).hexdigest()
            (d / "report.json").write_text(json.dumps(
                {"counts": ORIGINAL_COUNTS, "families": families, "sha256": hashes, "complete": True}))
            def write_audit():
                (d / "provenance.jsonl").write_text("\n".join(map(json.dumps, audit)) + "\n")
            write_audit()
            _, original = frozen_decisions(d)
            self.assertEqual(1600, len(original["train"]))
            audit[1], audit[0] = audit[0], audit[1]
            write_audit()
            with self.assertRaisesRegex(ValueError, "row number"):
                frozen_decisions(d)
            (d / "train.jsonl").write_bytes(b"unverified\n" * 1600)
            with self.assertRaisesRegex(ValueError, "count and checksum"):
                verify_dataset(d)
