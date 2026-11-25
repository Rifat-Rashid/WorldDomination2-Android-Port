"""A dead local inference server must stop a benchmark instead of scoring fallbacks."""
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "wd2play"))
from evaluate import LocalServer


class EvaluationTransportTest(unittest.TestCase):
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
