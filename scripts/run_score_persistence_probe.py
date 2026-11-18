#!/usr/bin/env python3
"""Run and independently verify the CI-only, cross-process score durability probe."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

TARGET = "com.example.worlddomination"
COMPONENT = TARGET + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = ("com.example.worlddomination.game.persistence.PersonalBestPersistenceInstrumentedTest"
        "#realAndroidPreferencesRetainOnlyImprovedScores")
SCHEMA = "wd2-score-persistence-v1"
PHASES = (("seed", 0, 3_000_000_000), ("lower", 3_000_000_000, 3_000_000_000),
          ("higher", 3_000_000_000, 5_000_000_000), ("verify", 5_000_000_000, 5_000_000_000))
DEFAULT_OUTPUT = Path("app/build/reports/androidTests/score-persistence.json")


class ProbeError(ValueError):
    pass


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ProbeError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _check(condition, message):
    if not condition:
        raise ProbeError(message)


def parse_instrumentation(text, phase):
    """Require both the actual JUnit result and exactly one typed phase receipt."""
    expected = next((x for x in PHASES if x[0] == phase), None)
    _check(expected is not None, "Unknown phase")
    codes = re.findall(r"(?m)^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", text)
    _check(codes == ["-1"], "Missing or unsuccessful final instrumentation code")
    _check(re.findall(r"(?m)^OK\s*\((\d+) tests?\)\s*$", text) == ["1"],
           "Expected exactly one passing JUnit test")
    _check("FAILURES!!!" not in text and "INSTRUMENTATION_FAILED" not in text
           and "INSTRUMENTATION_ABORTED" not in text, "Instrumentation reported failure")
    matches = re.findall(r"(?m)^INSTRUMENTATION_STATUS:\s*wd2_score_receipt=(.*)$", text)
    _check(len(matches) == 1, "Expected exactly one score receipt")
    try:
        receipt = json.loads(matches[0], object_pairs_hook=_object)
    except (json.JSONDecodeError, TypeError) as error:
        raise ProbeError("Malformed score receipt") from error
    _check(isinstance(receipt, dict), "Receipt must be an object")
    _check(receipt.get("phase") == phase, "Wrong score phase")
    _check(type(receipt.get("pid")) is int and receipt["pid"] > 0, "Invalid process PID")
    token = receipt.get("processToken")
    token_match = re.fullmatch(r"([1-9]\d*):(\d+)", token) if isinstance(token, str) else None
    _check(token_match is not None and int(token_match[1]) == receipt["pid"],
           "Invalid PID/start-time process token")
    for key, value in (("before", expected[1]), ("after", expected[2])):
        _check(type(receipt.get(key)) is int and receipt[key] == value, "Wrong score " + key)
    return receipt


def _read_log(root, name, digest):
    _check(isinstance(name, str) and bool(name), "Missing raw log path")
    path = (root / name).resolve()
    _check(path.is_relative_to(root.resolve()), "Raw log escapes receipt directory")
    data = path.read_bytes()
    _check(_sha(data) == digest, "Raw log digest mismatch")
    return data.decode("utf-8")


def verify_receipt(path, environment=None):
    """No device access: revalidate receipt, raw logs and current CI provenance."""
    path = Path(path)
    environment = os.environ if environment is None else environment
    receipt = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_object)
    _check(isinstance(receipt, dict), "Probe receipt must be an object")
    _check(receipt.get("schema") == SCHEMA and receipt.get("status") == "passed",
           "Missing successful durability probe")
    _check(receipt.get("targetPackage") == TARGET and receipt.get("test") == TEST,
           "Unexpected target package or test")
    producer = receipt.get("producer", {})
    _check(isinstance(producer, dict), "Invalid producer metadata")
    _check(producer.get("scriptSha256") == _sha(Path(__file__).read_bytes()),
           "Probe script provenance mismatch")
    for env_key, key in (("GITHUB_SHA", "sha"), ("GITHUB_RUN_ID", "runId")):
        expected = environment.get(env_key)
        if expected:
            _check(producer.get(key) == expected, "CI producer mismatch: " + key)
        elif environment.get("GITHUB_ACTIONS") == "true":
            raise ProbeError("Missing CI identity: " + env_key)
    phases = receipt.get("phases")
    _check(isinstance(phases, list) and all(isinstance(x, dict) for x in phases) and [x.get("phase") for x in phases] == [x[0] for x in PHASES],
           "Missing, duplicate or unordered phases")
    tokens = set()
    previous_start = -1
    for entry, (phase, _, _) in zip(phases, PHASES):
        _check(entry.get("status") == "passed" and type(entry.get("forceStopExit")) is int
               and entry["forceStopExit"] == 0 and type(entry.get("instrumentExit")) is int
               and entry["instrumentExit"] == 0, "Unsuccessful phase command: " + phase)
        _read_log(path.parent, entry.get("forceStopLog"), entry.get("forceStopLogSha256"))
        text = _read_log(path.parent, entry.get("log"), entry.get("logSha256"))
        parsed = parse_instrumentation(text, phase)
        _check(parsed == entry.get("receipt"), "Raw receipt disagrees with phase metadata")
        token = parsed["processToken"]
        start = int(token.split(":")[1])
        _check(token not in tokens and start > previous_start,
               "Process reused or start clock did not advance")
        tokens.add(token)
        previous_start = start
    return receipt


def run_probe(adb="adb", serial=None, output=DEFAULT_OUTPUT, runner=None, environment=None):
    """Only the hardcoded CI package is stopped; installed APKs/data are retained."""
    runner = subprocess.run if runner is None else runner
    environment = os.environ if environment is None else environment
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    logs = Path(tempfile.mkdtemp(prefix="score-persistence-", dir=output.parent))
    prefix = [adb] + (["-s", serial] if serial else [])
    receipt = {"schema": SCHEMA, "status": "running", "targetPackage": TARGET, "test": TEST,
               "producer": {"sha": environment.get("GITHUB_SHA"), "runId": environment.get("GITHUB_RUN_ID"),
                            "runAttempt": environment.get("GITHUB_RUN_ATTEMPT"),
                            "scriptSha256": _sha(Path(__file__).read_bytes())}, "phases": []}

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
            text += "\nCOMMAND TIMED OUT\n"
            code = 124
        except OSError as error:
            text, code = str(error), 127
        data = text.encode("utf-8")
        log_path.write_bytes(data)
        return code, log_path.relative_to(output.parent).as_posix(), _sha(data), text

    save()
    try:
        for phase, _, _ in PHASES:
            entry = {"phase": phase, "status": "running"}
            receipt["phases"].append(entry)
            code, name, digest, _ = command(["shell", "am", "force-stop", TARGET],
                                            logs / (phase + "-force-stop.txt"))
            entry.update(forceStopExit=code, forceStopLog=name, forceStopLogSha256=digest)
            save()
            _check(code == 0, "force-stop failed: " + phase)
            code, name, digest, text = command(
                ["shell", "am", "instrument", "-w", "-r", "-e", "class", TEST,
                 "-e", "wd2ScorePhase", phase, COMPONENT], logs / (phase + "-instrumentation.txt"))
            entry.update(instrumentExit=code, log=name, logSha256=digest)
            save()
            _check(code == 0, "Instrumentation command failed: " + phase)
            entry["receipt"] = parse_instrumentation(text, phase)
            entry["status"] = "passed"
            save()
        receipt["status"] = "passed"
        save()
        verify_receipt(output, environment)
    except (ProbeError, OSError, ValueError, TypeError, KeyError) as error:
        receipt["status"] = "failed"
        receipt["error"] = str(error)
        save()
    return receipt


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--verify", type=Path, help="Verify a downloaded receipt and its raw logs; no ADB")
    args = parser.parse_args(argv)
    try:
        result = (verify_receipt(args.verify) if args.verify else
                  run_probe(args.adb, args.serial, args.output))
        if result["status"] != "passed":
            raise ProbeError(result.get("error", "Probe failed"))
        print("Score persistence probe PASS: four verified process phases")
        return 0
    except (ProbeError, OSError, ValueError, TypeError, KeyError) as error:
        print("Score persistence probe FAIL: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
