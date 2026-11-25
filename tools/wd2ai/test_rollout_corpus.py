"""Checks for rollout labels and family assignment."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from export_dataset import family_split
from game_tools import CLASSES
from rollout_corpus import keys, review_scores, setups


class RolloutCorpusTests(unittest.TestCase):
    def test_train_configurations_are_reproducible_disjoint_and_balanced(self):
        values = setups(500000, 72, False)
        self.assertEqual(values, setups(500000, 72, False))
        self.assertEqual(72, len({row[1] for row in values}))
        self.assertEqual(36, len({(r[2], r[4], len(r[5].split(","))) for r in values}))
        self.assertTrue(all(family_split(row[0]) == "train" for row in values))
        self.assertTrue(all(1 <= row[3] <= len(row[5].split(",")) for row in values))

    def test_only_unique_paired_winners_can_be_exported(self):
        row = {"screen": {"P01": 4, "P02": 2}, "paired": {"P01": [5, 5], "P02": [2, 3]},
               "selected": "P01"}
        review_scores(row)
        for invalid in (dict(row, selected="P02"),
                        dict(row, paired={"P01": [5, 5], "P02": [5, 5]}),
                        dict(row, paired={"P01": [5, 5], "P03": [1, 1]})):
            with self.assertRaises(ValueError):
                review_scores(invalid)

    def test_relabeling_the_same_state_cannot_escape_deduplication(self):
        plans = {"P01": [{"action": "RESOURCES"}], "P02": [{"action": "MASS_MEDIA"}]}
        def record(label):
            return {"messages": [{"content": "Select one pair."}, {"content": json.dumps({"plans": plans})},
                                 {"content": json.dumps({"plan_id": label})}]}
        first = keys(record("P01"))
        second = keys(record("P02"))
        self.assertEqual(first[:2], second[:2])
        self.assertNotEqual(first[2], second[2])

    def test_rollout_forks_and_replays_are_repeatable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = root / "setups.tsv"
            config.write_text("integration\t420000\t2\t1\tpressure\t1,2,3,4\n")
            outputs = (root / "one.jsonl", root / "two.jsonl")
            for output in outputs:
                subprocess.run(["java", "-XX:ActiveProcessorCount=1", "-cp", str(CLASSES),
                                "com.example.worlddomination.game.ai.RolloutTeacher",
                                str(config), str(output), "fixture", "pilot"], check=True, timeout=120)
            self.assertEqual(outputs[0].read_bytes(), outputs[1].read_bytes())
            episode = json.loads(outputs[0].read_text(encoding="utf8"))
            self.assertTrue(episode["terminal"]["terminal"])
            self.assertEqual(episode["terminal"]["turn"], len(episode["pairs"]))
            self.assertGreater(len(episode["decisions"]), 5)
            for decision in episode["decisions"]:
                review_scores(decision)
                self.assertEqual(decision["selected"], episode["pairs"][decision["turn"]])
