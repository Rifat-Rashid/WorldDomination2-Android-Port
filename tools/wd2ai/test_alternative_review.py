"""Integration checks for the headless paired-future reviewer; first compile tools/wd2play."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from game_tools import CLASSES, engine


class AlternativeReviewTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.log = Path(temp.name) / "example.jsonl"
        row = engine("init", self.log, "test", "test", 721456, 1, 1, "baseline",
                     "pressure", "Protect the nation.", "1,4", "teacher", 100, "fixture")
        self.first, self.second = list(json.loads(row["user"])["plans"])[:2]

    def review(self, choices, seeds="102,207,309", turn=0, check=True):
        result = subprocess.run(
            ["java", "-cp", str(CLASSES), "com.example.worlddomination.game.ai.AlternativeReview",
             str(self.log), str(turn), "5", choices, seeds],
            text=True, capture_output=True, check=check)
        if not check:
            return result
        rows = json.loads(result.stdout)["results"]
        return {(r["choice"], r["future_seed"]): r for r in rows}

    def test_branches_are_paired_repeatable_and_leave_the_log_unchanged(self):
        before = self.log.read_bytes()
        result = self.review(f"{self.first},java,{self.second}")
        reverse = self.review(f"{self.second},{self.first},java", "309,207,102")
        self.assertEqual(9, len(result))
        self.assertEqual(result, reverse)
        self.assertEqual(before, self.log.read_bytes())
        self.assertTrue(all(r["turn"] == 5 for r in result.values()))
        self.assertGreater(len({r["population"] for r in result.values()}), 1)
        self.assertEqual("observation", engine("observe", self.log)["type"])

    def test_invalid_comparisons_fail_and_later_logged_turn_is_supported(self):
        engine("choose", self.log, self.first, "Choose the first offered pair.", "test:0:1")
        obs = engine("observe", self.log)
        choices = list(json.loads(obs["user"])["plans"])
        self.assertEqual(6, len(self.review(f"{choices[0]},java", turn=1)))
        bad_comparisons = [
            (f"{self.first},P99", "12,24", 0),
            (f"{self.first},java", "12,12", 0),
            (f"{self.first},java", "12,-12", 0),
            (f"{self.first},{self.first}", "12,24", 0),
            (f"{self.first},java", "12,24", 5),
        ]
        for plans, seeds, turn in bad_comparisons:
            with self.subTest(plans=plans, seeds=seeds, turn=turn):
                self.assertNotEqual(0, self.review(plans, seeds, turn, check=False).returncode)
