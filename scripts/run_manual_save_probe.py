#!/usr/bin/env python3
"""Run and independently verify the manual match-save process restart probe."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from run_score_persistence_probe import ProbeError, _check, _object, _read_log, _sha

TARGET = "com.example.worlddomination"
ISOLATED_TARGET = TARGET + ".saveprobe"
TEST = (TARGET + ".game.rendering.ManualSaveProcessRestartInstrumentedTest"
        "#manualSaveRestoresAfterProcessDeath")
PHASES = ("save", "load")
SLOTS = (1, 2, 3)
SCHEMA = "wd2-manual-save-persistence-v2"
DEFAULT_OUTPUT = Path("app/build/reports/androidTests/manual-save-persistence.json")


def parse_instrumentation(text, phase):
    codes = re.findall(r"(?m)^INSTRUMENTATION_CODE:[ \t]*(.*)$", text)
    _check([code.strip() for code in codes] == ["-1"],
           "Missing or unsuccessful final instrumentation code")
    _check(re.findall(r"(?m)^OK[ \t]*\(([0-9]+) tests?\)[ \t\r]*$", text) == ["1"],
           "Expected exactly one passing JUnit test")
    _check(not any(marker in text for marker in
                   ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED")),
           "Instrumentation reported failure")
    matches = re.findall(r"(?m)^INSTRUMENTATION_STATUS:[ \t]*wd2_match_save_receipt=(.*)$", text)
    _check(len(matches) == 1, "Expected exactly one manual-save receipt")
    receipt = json.loads(matches[0], object_pairs_hook=_object)
    _check(isinstance(receipt, dict), "Manual-save receipt must be an object")
    _check(phase in PHASES and receipt.get("phase") == phase, "Wrong manual-save phase")
    pid = receipt.get("pid")
    token = receipt.get("processToken")
    _check(type(pid) is int and pid > 0, "Invalid process PID")
    _check(isinstance(token, str) and re.fullmatch(rf"{pid}:[0-9]+", token),
           "Invalid PID/start-time process token")
    slots = receipt.get("slots")
    _check(isinstance(slots, list) and len(slots) == len(SLOTS), "Expected three slot receipts")
    for number, slot in zip(SLOTS, slots):
        _check(isinstance(slot, dict) and type(slot.get("slot")) is int
               and slot["slot"] == number, "Missing, duplicate or unordered slots")
        _check(type(slot.get("turn")) is int and slot["turn"] == number, "Wrong match turn")
        _check(slot.get("queued") == ["RESOURCES", "RESEARCH"], "Wrong queued orders")
        checkpoint = slot.get("checkpointSha256")
        _check(isinstance(checkpoint, str) and re.fullmatch(r"[0-9a-f]{64}", checkpoint),
               "Invalid checkpoint digest")
        for key in ("funds", "nextRandomBits"):
            _check(type(slot.get(key)) is int, "Invalid " + key)
    _check(len({slot["checkpointSha256"] for slot in slots}) == len(SLOTS),
           "Save slots must have distinct checkpoints")
    return receipt


def verify_receipt(path, environment=None):
    """Verify the saved evidence and current CI identity without accessing a device."""
    path = Path(path)
    environment = os.environ if environment is None else environment
    receipt = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_object)
    _check(isinstance(receipt, dict) and receipt.get("schema") == SCHEMA
           and receipt.get("status") == "passed", "Missing successful manual-save probe")
    _check(receipt.get("targetPackage") in (TARGET, ISOLATED_TARGET)
           and receipt.get("test") == TEST, "Unexpected target package or test")
    if environment.get("GITHUB_ACTIONS") == "true":
        _check(receipt["targetPackage"] == TARGET, "CI evidence must use the default app package")
    producer = receipt.get("producer")
    _check(isinstance(producer, dict) and producer.get("scriptSha256") == _script_digest(),
           "Probe script provenance mismatch")
    for env_key, key in (("GITHUB_SHA", "sha"), ("GITHUB_RUN_ID", "runId")):
        expected = environment.get(env_key)
        if expected or environment.get("GITHUB_ACTIONS") == "true":
            _check(bool(expected) and producer.get(key) == expected, "CI producer mismatch: " + env_key)
    phases = receipt.get("phases")
    _check(isinstance(phases, list) and all(isinstance(entry, dict) for entry in phases)
           and tuple(entry.get("phase") for entry in phases) == PHASES,
           "Missing, duplicate or unordered phases")
    evidence = []
    for entry in phases:
        phase = entry["phase"]
        _check(entry.get("status") == "passed" and all(
            type(entry.get(key)) is int and entry[key] == 0
            for key in ("forceStopExit", "instrumentExit")), "Unsuccessful phase command: " + phase)
        _read_log(path.parent, entry.get("forceStopLog"), entry.get("forceStopLogSha256"))
        log = _read_log(path.parent, entry.get("log"), entry.get("logSha256"))
        parsed = parse_instrumentation(log, phase)
        _check(json.dumps(parsed, sort_keys=True) == json.dumps(entry.get("receipt"), sort_keys=True),
               "Raw receipt disagrees with phase metadata: " + phase)
        evidence.append(parsed)
    saved, loaded = evidence
    save_token, load_token = saved["processToken"], loaded["processToken"]
    _check(save_token != load_token and int(save_token.split(":")[1]) < int(load_token.split(":")[1]),
           "Process reused or start clock did not advance")
    _check(saved["slots"] == loaded["slots"], "Saved and loaded slot evidence disagree")
    return receipt


def _script_digest():
    return _sha(Path(__file__).read_bytes())


def run_probe(adb="adb", serial=None, output=DEFAULT_OUTPUT, runner=None, environment=None,
              target=TARGET):
    _check(target in (TARGET, ISOLATED_TARGET), "Unexpected target package")
    runner = subprocess.run if runner is None else runner
    environment = os.environ if environment is None else environment
    if environment.get("GITHUB_ACTIONS") == "true":
        _check(target == TARGET, "CI must use the default app package")
    else:
        _check(target == ISOLATED_TARGET, "Local runs require --package " + ISOLATED_TARGET)
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    logs = Path(tempfile.mkdtemp(prefix="manual-save-persistence-", dir=output.parent))
    prefix = [adb] + (["-s", serial] if serial else [])
    component = target + ".test/androidx.test.runner.AndroidJUnitRunner"
    receipt = {"schema": SCHEMA, "status": "running", "targetPackage": target, "test": TEST,
               "producer": {"sha": environment.get("GITHUB_SHA"), "runId": environment.get("GITHUB_RUN_ID"),
                            "scriptSha256": _script_digest()}, "phases": []}

    def save():
        output.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")

    def command(arguments, log_path):
        try:
            result = runner(prefix + arguments, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace", timeout=120, check=False)
            text, code = result.stdout or "", result.returncode
        except subprocess.TimeoutExpired as error:
            text = error.stdout or ""
            if isinstance(text, bytes):
                text = text.decode("utf-8", errors="replace")
            text, code = text + "\nCOMMAND TIMED OUT\n", 124
        except OSError as error:
            text, code = str(error), 127
        data = text.encode("utf-8")
        log_path.write_bytes(data)
        return code, log_path.relative_to(output.parent).as_posix(), _sha(data), text

    save()
    try:
        for phase in PHASES:
            entry = {"phase": phase, "status": "running"}
            receipt["phases"].append(entry)
            code, name, digest, _ = command(["shell", "am", "force-stop", target],
                                            logs / (phase + "-force-stop.txt"))
            entry.update(forceStopExit=code, forceStopLog=name, forceStopLogSha256=digest)
            _check(code == 0, "force-stop failed: " + phase)
            code, name, digest, text = command(
                ["shell", "am", "instrument", "-w", "-r", "-e", "class", TEST,
                 "-e", "wd2MatchSavePhase", phase, component], logs / (phase + "-instrumentation.txt"))
            entry.update(instrumentExit=code, log=name, logSha256=digest)
            _check(code == 0, "Instrumentation command failed: " + phase)
            entry["receipt"] = parse_instrumentation(text, phase)
            entry["status"] = "passed"
            save()
        receipt["status"] = "passed"
        save()
        verify_receipt(output, environment)
    except (OSError, ValueError) as error:
        receipt["status"] = "failed"
        receipt["error"] = str(error)
        save()
    return receipt


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--package", choices=(ISOLATED_TARGET,), help="Use the isolated local probe APK")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--verify", type=Path, help="Verify an existing receipt and raw logs without ADB")
    args = parser.parse_args(argv)
    try:
        result = (verify_receipt(args.verify) if args.verify else
                  run_probe(args.adb, args.serial, args.output, target=args.package or TARGET))
        _check(result["status"] == "passed", result.get("error", "Probe failed"))
    except (OSError, ValueError) as error:
        print("Manual-save persistence probe FAIL: " + str(error))
        return 1
    print("Manual-save persistence probe PASS: all three slots verified in distinct processes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
