"""Replay an audited completed episode on Android from a validated checkpoint."""

import argparse
import base64
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time


REPOSITORY = Path(__file__).resolve().parents[2]
PLAY = "com.example.worlddomination.game.ai.Play"
TEST = "com.example.worlddomination.game.rendering.CompletedEpisodeReplayInstrumentedTest"


def run(command, timeout=60):
    return subprocess.run(
        [str(value) for value in command],
        timeout=timeout,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def checked(result):
    if result.returncode != 0:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


@contextmanager
def device_slot(serial):
    """Serialize independent replay writers on one physical Android test device."""
    name = hashlib.sha256(serial.encode()).hexdigest()
    path = Path(tempfile.gettempdir()) / f"wd2-replay-{name}.lock"
    with path.open("a+b") as handle:
        if handle.tell() == 0:
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        if os.name == "nt":
            import msvcrt
            while True:
                try:
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                    break
                except PermissionError:
                    time.sleep(0.5)
        else:
            import fcntl
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        # Closing the file releases the OS lock, including on worker termination.
        yield


def play(command, *args):
    return run([
        "java", "-cp", REPOSITORY / "analysis/wd2play/classes", PLAY, command, *args,
    ])


def episode_fixture(log, destination):
    records = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines()]
    header = records[0]
    episode = header["episode"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", episode) or episode in {".", ".."}:
        raise ValueError("Episode identifier must be usable as a local filename")
    terminal = json.loads(checked(play("replay", log)))
    if terminal["type"] != "terminal" or not terminal["outcome"]["terminal"]:
        raise ValueError("Android replay requires a naturally completed episode")
    orders = [row for row in records if row["type"] in {"decision", "continuation"}]
    final_turn = orders[-1]["turn"]
    failures = []
    for turn in range(final_turn, max(-1, final_turn - 5), -1):
        checkpoint = destination / f"{episode}-turn{turn}.wd2s"
        if not checkpoint.exists():
            result = play("checkpoint", log, turn, checkpoint)
            if result.returncode != 0:
                failures.append(result.stdout.strip())
                continue
        remaining = []
        for row in orders:
            if row["turn"] < turn:
                continue
            selections = dict(row["stochastic_selected"])
            if row["type"] == "decision":
                selections[str(row["actor"])] = row["selected"]
            remaining.append({
                "turn": row["turn"],
                "human_answers": row["human_answers"],
                "human_selected": row["human_selected"],
                "selections": selections,
            })
        return checkpoint, {
            "episode": episode,
            "checkpoint": base64.b64encode(checkpoint.read_bytes()).decode("ascii"),
            "decision_contract": header["decision_contract"],
            "human_policy": header["human_policy"],
            "preference": header["preference"],
            "turns": remaining,
            "outcome": terminal["outcome"],
        }
    raise RuntimeError("No decodable preterminal checkpoint: " + "; ".join(failures))


def collect_evidence(args, log):
    checkpoint, fixture = episode_fixture(log, args.out)
    episode = fixture["episode"]
    serialized = json.dumps(fixture, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    fixture_path = args.out / f"{episode}-fixture.json"
    fixture_path.write_bytes(serialized + b"\n")
    argument = base64.b64encode(serialized).decode("ascii")
    adb = [args.adb, "-s", args.serial]
    test_result = run([
        *adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST,
        "-e", "wd2_episode_fixture", argument,
        f"{args.app_id}.test/androidx.test.runner.AndroidJUnitRunner",
    ], timeout=230)
    console = args.out / f"{episode}-instrumentation.txt"
    console.write_text(test_result.stdout + test_result.stderr, encoding="utf-8")
    if test_result.returncode != 0 or "OK (1 test)" not in test_result.stdout:
        raise RuntimeError(f"Android replay failed. See {console}")
    screenshot = args.out / f"{episode}.png"
    checked(run([
        *adb, "pull",
        f"/sdcard/Android/data/{args.app_id}/files/wd2play-evidence/{episode}.png", screenshot,
    ]))
    if not screenshot.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Invalid screenshot file {screenshot}")
    receipt = {
        "episode": episode,
        "adb_serial": args.serial,
        "app_id": args.app_id,
        "outcome": fixture["outcome"],
        "log": str(log.resolve()),
        "log_sha256": digest(log),
        "checkpoint": checkpoint.name,
        "checkpoint_sha256": digest(checkpoint),
        "fixture": fixture_path.name,
        "fixture_sha256": digest(fixture_path),
        "screenshot": screenshot.name,
        "screenshot_sha256": digest(screenshot),
        "device_test": console.name,
    }
    (args.out / f"{episode}-receipt.json").write_text(
        json.dumps(receipt, indent=2) + "\n", encoding="utf-8",
    )
    print(json.dumps(receipt))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--app-id", default="com.example.worlddomination.replayqa")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    if not args.app_id.endswith(".replayqa"):
        parser.error("Use an isolated .replayqa application ID")
    args.out.mkdir(parents=True, exist_ok=True)
    for path in args.logs:
        with device_slot(args.serial):
            collect_evidence(args, path)


if __name__ == "__main__":
    main()
