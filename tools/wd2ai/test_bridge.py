"""Run with the pinned MCP environment after building tools/wd2play."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import game_tools
import studio
from training_tools import certified


class BridgeTests(unittest.TestCase):
    def test_external_urls_and_embedded_credentials_rejected(self):
        for url in ("https://example.com", "http://127.0.0.1/path", "http://user@127.0.0.1",
                    "http://127.0.0.1?dest=external", "http://192.168.0.1"):
            with self.subTest(url=url), patch.dict(os.environ, {"WD2_STUDIO_URL": url}):
                with self.assertRaises(ValueError):
                    studio.studio_url()

    def test_2026_discovery_and_tools_over_stdio(self):
        meta = {"io.modelcontextprotocol/protocolVersion": "2026-07-28",
                "io.modelcontextprotocol/clientCapabilities": {}}
        with subprocess.Popen([sys.executable, str(Path(__file__).with_name("server.py"))],
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE, encoding="utf8") as p:
            try:
                def call(i, method):
                    p.stdin.write(json.dumps({"jsonrpc": "2.0", "id": i, "method": method,
                                              "params": {"_meta": meta}}) + "\n")
                    p.stdin.flush()
                    return json.loads(p.stdout.readline())["result"]
                self.assertIn("2026-07-28", call(1, "server/discover")["supportedVersions"])
                names = {tool["name"] for tool in call(2, "tools/list")["tools"]}
                self.assertTrue({"game_choose", "training_status", "start_reviewed_training"} <= names)
            finally:
                p.stdin.close()
                p.wait(timeout=15)

    def test_legacy_desktop_handshake(self):
        payload = {"jsonrpc": "2.0", "id": 1, "method": "initialize",
                   "params": {"protocolVersion": "2025-11-25", "capabilities": {},
                              "clientInfo": {"name": "studio-compatibility-test", "version": "1"}}}
        with subprocess.Popen([sys.executable, str(Path(__file__).with_name("server.py"))],
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE, encoding="utf8") as p:
            try:
                p.stdin.write(json.dumps(payload) + "\n")
                p.stdin.flush()
                self.assertEqual("2025-11-25", json.loads(p.stdout.readline())["result"]["protocolVersion"])
            finally:
                p.stdin.close()
                p.wait(timeout=15)

    def test_game_can_submit_once_and_resume(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"WD2_EPISODES": directory}):
            first = game_tools.game_start()
            self.assertNotIn("seed", json.loads(first["user"]))
            episode = first["episode"]
            self.assertEqual(first, game_tools.game_observe(episode))
            plan = next(iter(json.loads(first["user"])["plans"]))
            after = game_tools.game_choose(episode, first["decision_id"], plan, "Integration smoke")
            self.assertEqual(1, after["next"]["turn"])
            with self.assertRaisesRegex(ValueError, "stale_decision"):
                game_tools.game_choose(episode, first["decision_id"], plan, "Retry request")
            self.assertEqual(1, game_tools.game_observe(episode)["turn"])

    def test_cannot_start_full_training_with_partial_split(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"WD2_TRAINING_ROOT": directory}):
            dataset = Path(directory) / "reviewed"
            dataset.mkdir()
            (dataset / "report.json").write_text('{"counts":{"train":1},"sha256":{}}')
            (dataset / "train.jsonl").write_text('{}\n')
            with self.assertRaisesRegex(ValueError, "count and checksum"):
                certified()

    def test_episode_ids_cannot_escape_collection_folder(self):
        with self.assertRaises(ValueError):
            game_tools.game_observe("../separate-log")


if __name__ == "__main__":
    unittest.main()

