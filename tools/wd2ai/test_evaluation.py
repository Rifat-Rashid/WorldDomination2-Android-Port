"""A dead local inference server must stop a benchmark instead of scoring fallbacks."""
from pathlib import Path
from types import SimpleNamespace
import json
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "wd2play"))
from compare_checkpoints import compare
from evaluate import CHAT_SUFFIX, LocalServer, complete_reviewed_report, sha256


class EvaluationTransportTest(unittest.TestCase):
    def test_completion_applies_greedy_temperature_after_grammar(self):
        server = LocalServer(0, 10)
        with patch.object(server, "call", side_effect=[
                {"prompt": "game" + CHAT_SUFFIX}, {"tokens": [1, 2]},
                {"content": '{"plan_id":"P01"}', "stop_type": "eos"}]) as calls:
            chosen, _ = server.select({"system": "s", "user": "u", "candidates": ["P01"]})
        self.assertEqual("P01", chosen)
        endpoint, request, _ = calls.call_args.args
        self.assertEqual("/completion", endpoint)
        self.assertEqual(["temperature"], request["samplers"])
        self.assertEqual(0, request["temperature"])
        self.assertIn("P01", request["grammar"])

    def test_both_holdouts_and_declared_training_size_are_required(self):
        sizes = {"train": 6400, "validation": 200, "test": 200}
        self.assertTrue(complete_reviewed_report(dict(complete=True, target_counts=sizes, counts=sizes)))
        for incorrect in (dict(sizes, train=1600), dict(sizes, test=199), dict(sizes, validation=199)):
            self.assertFalse(complete_reviewed_report(dict(complete=True, target_counts=sizes, counts=incorrect)))

    def test_prospective_games_reject_changed_manifest_and_seen_families_or_seeds(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest, report = (Path(tmp) / name for name in ("games.json", "corpus.json"))
            episodes = [{"family": "new-game", "episode": "new-game", "seed": 923}]
            manifest.write_text(json.dumps({"purpose": "prospective_gameplay", "episodes": episodes}))
            args = SimpleNamespace(label="existing", manifest=manifest, dataset_report=report,
                                   manifest_sha256="incorrect")
            with self.assertRaisesRegex(ValueError, "locked prospective"):
                compare(args)
            args.manifest_sha256 = sha256(manifest)
            for families, seeds, error in [({"new-game": "train"}, {}, "families overlap"),
                                           ({}, {"923": "other-game"}, "seeds disjoint")]:
                report.write_text(json.dumps({"families": families, "seed_families": seeds,
                                              "counts": {"train": 1600, "validation": 200, "test": 200},
                                              "complete": True}))
                with self.subTest(error=error), self.assertRaisesRegex(ValueError, error):
                    compare(args)

    def test_separates_dead_server_from_individual_request_timeout(self):
        server = LocalServer(0, 1)
        server.process = Mock()
        with patch.object(server.http, "open", side_effect=TimeoutError("request expired")):
            server.process.poll.return_value = None
            with self.assertRaises(TimeoutError):
                server.call("/completion")
            server.process.poll.return_value = 1
            with self.assertRaisesRegex(RuntimeError, "server exited"):
                server.call("/completion")
