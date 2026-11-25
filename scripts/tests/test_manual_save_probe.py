"""Tests for restart evidence using a fake ADB subprocess."""
from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_manual_save_probe as probe


def slot_receipts():
    return [dict(slot=number, turn=number, queued=["RESOURCES", "RESEARCH"], funds=11 + number,
                 checkpointSha256=digest * 64, nextRandomBits=4_600_000_000_000_000_000 + number)
            for number, digest in enumerate("abc", 1)]


def transcript(phase, pid=1001, start=5000, slot_changes=None, **changes):
    slots = slot_receipts()
    slots[0].update(slot_changes or {})
    receipt = dict(phase=phase, pid=pid, processToken=f"{pid}:{start}", slots=slots)
    receipt.update(changes)
    return ("INSTRUMENTATION_STATUS: wd2_match_save_receipt=" + json.dumps(receipt)
            + "\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stream=\n"
              "\nTime: 0.1\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n")


class ManualSaveProbeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name) / "manual-save-persistence.json"
        self.environment = {"GITHUB_ACTIONS": "true", "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "123"}

    def run_probe(self, load=None, force_exit=0, instrument_exit=0, target=probe.TARGET, environment=None):
        self.adb = Mock(side_effect=[
            subprocess.CompletedProcess([], force_exit, ""),
            subprocess.CompletedProcess([], instrument_exit, transcript("save")),
            subprocess.CompletedProcess([], 0, ""),
            subprocess.CompletedProcess([], 0, load or transcript("load", 1002, 5001)),
        ])
        environment = self.environment if environment is None else environment
        return probe.run_probe("fake-adb", "device-1", self.output, self.adb, environment, target)

    def test_commands_and_offline_verification_for_each_package(self):
        prefix = ["fake-adb", "-s", "device-1", "shell", "am"]
        for target in (probe.TARGET, probe.ISOLATED_TARGET):
            with self.subTest(target=target):
                environment = self.environment if target == probe.TARGET else {}
                result = self.run_probe(target=target, environment=environment)
                self.assertEqual("passed", result["status"])
                expected = []
                for phase in ("save", "load"):
                    expected.extend([
                        prefix + ["force-stop", target],
                        prefix + ["instrument", "-w", "-r", "-e", "class", probe.TEST,
                                  "-e", "wd2MatchSavePhase", phase,
                                  target + ".test/androidx.test.runner.AndroidJUnitRunner"],
                    ])
                self.assertEqual(expected, [call.args[0] for call in self.adb.call_args_list])
                self.assertEqual(result, probe.verify_receipt(self.output, environment))
                self.assertEqual(result, probe.verify_receipt(self.output, {}))

    def test_protects_default_local_package_and_requires_default_ci_package(self):
        for target, environment in ((probe.TARGET, {}), (probe.ISOLATED_TARGET, self.environment)):
            with self.subTest(target=target), self.assertRaises(probe.ProbeError):
                self.run_probe(target=target, environment=environment)
            self.adb.assert_not_called()
            self.assertFalse(self.output.exists())
        self.run_probe(target=probe.ISOLATED_TARGET, environment={})
        with self.assertRaisesRegex(probe.ProbeError, "default app package"):
            probe.verify_receipt(self.output, self.environment)

    def test_rejects_unlisted_package_before_any_adb_call(self):
        with self.assertRaises(probe.ProbeError):
            self.run_probe(target="com.example.other")
        self.adb.assert_not_called()

    def test_requires_single_successful_test_code_and_receipt(self):
        text = transcript("save")
        receipt_line = text.splitlines()[0]
        for altered in (text.replace(receipt_line, ""), text + receipt_line + "\n",
                        text.replace("OK (1 test)", "OK (2 tests)"), text + "OK (1 test)\n",
                        text.replace("INSTRUMENTATION_CODE: -1", ""),
                        text.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
                        text + "INSTRUMENTATION_CODE: unknown\n", text + "FAILURES!!!\n",
                        text.replace('"turn": 1', '"turn": 1, "turn": 1')):
            with self.subTest(text=altered), self.assertRaises(probe.ProbeError):
                probe.parse_instrumentation(altered, "save")

    def test_rejects_wrong_or_invalid_receipt_fields(self):
        for changes in (dict(pid=True), dict(pid=0), dict(processToken="1002:5000"),
                        dict(processToken="1001:-1")):
            with self.subTest(changes=changes), self.assertRaises(probe.ProbeError):
                probe.parse_instrumentation(transcript("save", **changes), "save")
        for changes in (dict(slot=True), dict(slot=2), dict(turn=True), dict(turn=2),
                        dict(queued=["RESEARCH", "RESOURCES"]), dict(funds=True), dict(funds="12"),
                        dict(nextRandomBits=0.5), dict(checkpointSha256="A" * 64),
                        dict(checkpointSha256="b" * 64)):
            with self.subTest(changes=changes), self.assertRaises(probe.ProbeError):
                probe.parse_instrumentation(transcript("save", slot_changes=changes), "save")
        with self.assertRaises(probe.ProbeError):
            probe.parse_instrumentation(transcript("load"), "save")

    def test_rejects_missing_duplicate_and_out_of_order_slots(self):
        first, second, third = slot_receipts()
        for slots in ([first, second], [first, second, second], [first, third, second]):
            with self.subTest(slots=slots), self.assertRaises(probe.ProbeError):
                probe.parse_instrumentation(transcript("save", slots=slots), "save")

    def test_rejects_reused_processes_and_mismatched_restore(self):
        for load in (transcript("load"), transcript("load", 1002, 4999),
                     transcript("load", 1002, 5000),
                     transcript("load", 1002, 5001, slot_changes={"funds": 13}),
                     transcript("load", 1002, 5001, slot_changes={"checkpointSha256": "d" * 64}),
                     transcript("load", 1002, 5001, slot_changes={"nextRandomBits": 2})):
            with self.subTest(load=load):
                result = self.run_probe(load=load)
                self.assertEqual("failed", result["status"])
                self.assertEqual(4, self.adb.call_count)
                with redirect_stdout(StringIO()):
                    self.assertEqual(1, probe.main(["--verify", str(self.output)]))

    def test_failed_command_preserves_available_logs_and_stops(self):
        for force_exit, instrument_exit, count in ((1, 0, 1), (0, 1, 2)):
            with self.subTest(force_exit=force_exit, instrument_exit=instrument_exit):
                result = self.run_probe(force_exit=force_exit, instrument_exit=instrument_exit)
                self.assertEqual("failed", result["status"])
                self.assertEqual(count, self.adb.call_count)
                entry = result["phases"][0]
                self.assertTrue((self.output.parent / entry["forceStopLog"]).is_file())
                if instrument_exit:
                    self.assertIn("OK (1 test)", (self.output.parent / entry["log"]).read_text())

    def test_verification_rejects_mismatched_ci_identity(self):
        self.run_probe()
        for environment in (dict(self.environment, GITHUB_SHA="b" * 40),
                            dict(self.environment, GITHUB_RUN_ID="124")):
            with self.subTest(environment=environment), self.assertRaises(probe.ProbeError):
                probe.verify_receipt(self.output, environment)

    def test_verification_rejects_incomplete_or_altered_metadata(self):
        result = self.run_probe()
        save, load = result["phases"]
        for phases in ([save], [load, save], [save, save], [dict(save, instrumentExit=True), load],
                       [dict(save, receipt=dict(save["receipt"], turn=True)), load]):
            with self.subTest(phases=phases), self.assertRaises(probe.ProbeError):
                self.output.write_text(json.dumps(dict(result, phases=phases)), encoding="utf-8")
                probe.verify_receipt(self.output, self.environment)

    def test_verification_rejects_tampered_or_missing_logs(self):
        for log_key in ("log", "forceStopLog"):
            result = self.run_probe()
            log = self.output.parent / result["phases"][0][log_key]
            log.write_text("tampered log", encoding="utf-8")
            with self.assertRaises(probe.ProbeError):
                probe.verify_receipt(self.output, self.environment)
            log.unlink()
            with self.assertRaises(OSError):
                probe.verify_receipt(self.output, self.environment)


if __name__ == "__main__":
    unittest.main()
