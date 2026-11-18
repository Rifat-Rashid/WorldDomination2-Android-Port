"""Host-only guards; subprocess injection guarantees no ADB/device access."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "score_probe", Path(__file__).resolve().parents[1] / "run_score_persistence_probe.py")
PROBE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PROBE)


def transcript(source_phase, pid=1001, start=5000, **changes):
    expected = next(x for x in PROBE.PHASES if x[0] == source_phase)
    receipt = dict(phase=source_phase, pid=pid, processToken=f"{pid}:{start}",
                   before=expected[1], after=expected[2])
    receipt.update(changes)
    return ("INSTRUMENTATION_STATUS: wd2_score_receipt=" + json.dumps(receipt)
            + "\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stream=\n"
              "\nTime: 0.1\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n")


class FakeAdb:
    def __init__(self):
        self.calls = []
        self.force_exit = 0
        self.instrument_exit = 0
        self.timeout = False
        self.transform = lambda phase, text: text

    def __call__(self, command, **kwargs):
        self.calls.append(command)
        if "force-stop" in command:
            return subprocess.CompletedProcess(command, self.force_exit, "force-stop output\n")
        phase = command[command.index("wd2ScorePhase") + 1]
        index = [x[0] for x in PROBE.PHASES].index(phase)
        text = transcript(phase, 1001 + index, 5000 + index)
        if self.timeout:
            raise subprocess.TimeoutExpired(command, 120, output=b"partial instrumentation output")
        return subprocess.CompletedProcess(command, self.instrument_exit, self.transform(phase, text))


class ScorePersistenceProbeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.output = Path(self.temporary.name) / "score-persistence.json"
        self.fake = FakeAdb()

    def run_probe(self, environment=None):
        return PROBE.run_probe("fake-adb", "test-serial", self.output, self.fake,
                               {} if environment is None else environment)

    def successful(self):
        result = self.run_probe()
        self.assertEqual("passed", result["status"])
        return result

    def test_valid_instrumentation_receipt(self):
        parsed = PROBE.parse_instrumentation(transcript("seed"), "seed")
        self.assertEqual(3_000_000_000, parsed["after"])

    def test_relative_output_path_keeps_logs_under_resolved_parent(self):
        previous = Path.cwd()
        try:
            os.chdir(self.temporary.name)
            result = PROBE.run_probe("fake-adb", "test-serial", Path("nested/probe.json"),
                                     self.fake, {})
            self.assertEqual("passed", result["status"])
            PROBE.verify_receipt(Path("nested/probe.json"), {})
        finally:
            os.chdir(previous)

    def test_missing_or_duplicate_receipt_rejected(self):
        text = transcript("seed")
        line = text.splitlines()[0]
        for altered in [text.replace(line, ""), line + "\n" + text]:
            with self.subTest(altered=altered), self.assertRaises(PROBE.ProbeError):
                PROBE.parse_instrumentation(altered, "seed")

    def test_wrong_phase_scores_and_identity_rejected(self):
        for changes in [dict(phase="lower"), dict(before=1), dict(after=2),
                        dict(after=True), dict(pid=True), dict(pid=0),
                        dict(processToken="1002:5000"), dict(processToken="missing")]:
            with self.subTest(changes=changes), self.assertRaises(PROBE.ProbeError):
                PROBE.parse_instrumentation(transcript("seed", **changes), "seed")

    def test_duplicate_json_keys_rejected(self):
        text = transcript("seed").replace('"phase": "seed"', '"phase": "seed", "phase": "seed"')
        with self.assertRaises(PROBE.ProbeError):
            PROBE.parse_instrumentation(text, "seed")

    def test_final_result_must_be_one_passing_test(self):
        text = transcript("seed")
        for altered in [text.replace("OK (1 test)", "OK (0 tests)"),
                        text.replace("OK (1 test)", "OK (2 tests)"),
                        text.replace("INSTRUMENTATION_CODE: -1", ""),
                        text.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
                        text + "INSTRUMENTATION_CODE: -1\n", text + "FAILURES!!!\n"]:
            with self.subTest(altered=altered), self.assertRaises(PROBE.ProbeError):
                PROBE.parse_instrumentation(altered, "seed")

    def test_four_fresh_phases_and_downloaded_receipt_verify(self):
        result = self.successful()
        self.assertEqual(8, len(self.fake.calls))
        for stop, instrument in zip(self.fake.calls[::2], self.fake.calls[1::2]):
            self.assertEqual(["fake-adb", "-s", "test-serial", "shell", "am", "force-stop", PROBE.TARGET], stop)
            self.assertEqual(PROBE.COMPONENT, instrument[-1])
            self.assertIn(PROBE.TEST, instrument)
        self.assertEqual(result, PROBE.verify_receipt(self.output, {}))

    def test_nonzero_instrumentation_exit_retains_logs_and_stops(self):
        self.fake.instrument_exit = 7
        result = self.run_probe()
        self.assertEqual("failed", result["status"])
        self.assertEqual(2, len(self.fake.calls))
        self.assertIn("OK (1 test)", (self.output.parent / result["phases"][0]["log"]).read_text())

    def test_failed_force_stop_does_not_instrument(self):
        self.fake.force_exit = 1
        result = self.run_probe()
        self.assertEqual("failed", result["status"])
        self.assertEqual(1, len(self.fake.calls))
        self.assertTrue((self.output.parent / result["phases"][0]["forceStopLog"]).is_file())

    def test_timeout_preserves_partial_raw_output(self):
        self.fake.timeout = True
        result = self.run_probe()
        self.assertEqual("failed", result["status"])
        entry = result["phases"][0]
        self.assertEqual(124, entry["instrumentExit"])
        self.assertIn("partial instrumentation output", (self.output.parent / entry["log"]).read_text())

    def test_reused_process_or_nonadvancing_start_clock_rejected(self):
        for reuse in [True, False]:
            self.fake = FakeAdb()
            self.fake.transform = lambda phase, text: transcript(phase, 1001 if reuse else 1002, 5000)
            self.assertEqual("failed", self.run_probe()["status"])

    def test_tampered_log_rejected(self):
        result = self.successful()
        (self.output.parent / result["phases"][0]["log"]).write_text("tampered")
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, {})

    def test_missing_duplicate_or_unordered_phases_rejected(self):
        result = self.successful()
        for phases in [result["phases"][:-1], result["phases"] + result["phases"][:1],
                       list(reversed(result["phases"]))]:
            changed = dict(result, phases=phases)
            self.output.write_text(json.dumps(changed))
            with self.assertRaises(PROBE.ProbeError):
                PROBE.verify_receipt(self.output, {})

    def test_receipt_log_path_cannot_escape(self):
        result = self.successful()
        result["phases"][0]["log"] = "../unrelated.txt"
        self.output.write_text(json.dumps(result))
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, {})

    def test_ci_and_script_provenance_checked(self):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1"}
        result = self.run_probe(env)
        self.assertEqual("passed", result["status"])
        for mismatch in [dict(env, GITHUB_SHA="b" * 40), dict(env, GITHUB_RUN_ID="124")]:
            with self.assertRaises(PROBE.ProbeError):
                PROBE.verify_receipt(self.output, mismatch)
        result["producer"]["scriptSha256"] = "0" * 64
        self.output.write_text(json.dumps(result))
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, env)

    def test_bad_target_or_claimed_exit_rejected(self):
        result = self.successful()
        result["targetPackage"] = "com.rashid.worlddomination2"
        self.output.write_text(json.dumps(result))
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, {})
        result["targetPackage"] = PROBE.TARGET
        result["phases"][0]["instrumentExit"] = 1
        self.output.write_text(json.dumps(result))
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, {})

    def test_missing_logs_and_unsuccessful_receipt_rejected(self):
        result = self.successful()
        result["status"] = "failed"
        self.output.write_text(json.dumps(result))
        with self.assertRaises(PROBE.ProbeError):
            PROBE.verify_receipt(self.output, {})
        result["status"] = "passed"
        self.output.write_text(json.dumps(result))
        (self.output.parent / result["phases"][0]["log"]).unlink()
        with self.assertRaises(OSError):
            PROBE.verify_receipt(self.output, {})


if __name__ == "__main__":
    unittest.main()
