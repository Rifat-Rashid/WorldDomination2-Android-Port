"""Durable actor-scoped development tools for the authoritative Java harness."""
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import uuid
from typing import Literal

ROOT = Path(__file__).resolve().parents[2]
CLASSES = ROOT / "analysis/wd2play/classes"
MAIN = "com.example.worlddomination.game.ai.Play"
Strategy = Literal["economy", "pressure", "recovery"]
PREFERENCES = {"economy": "Fund development while defending the population.",
               "pressure": "Use available forces efficiently against visible targets.",
               "recovery": "Protect vulnerable cities and rebuild through cooperation."}


def log_path(episode):
    if not re.fullmatch(r"[a-f0-9]{32}", episode):
        raise ValueError("Invalid episode ID")
    root = Path(os.environ.get("WD2_EPISODES", ROOT / "analysis/mcp-episodes"))
    root.mkdir(parents=True, exist_ok=True)
    return root / (episode + ".jsonl")


def engine(command, path, *args):
    if not (CLASSES / "com/example/worlddomination/game/ai/Play.class").is_file():
        raise RuntimeError("Compile the Java harness with tools/wd2play/play.ps1 -Command build")
    result = subprocess.run(["java", "-cp", str(CLASSES), MAIN, command, str(path), *map(str, args)],
                            cwd=ROOT, capture_output=True, encoding="utf8", timeout=90)
    try:
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    except ValueError as error:
        raise RuntimeError("Game harness returned invalid JSON") from error
    if result.returncode or not rows:
        raise ValueError(rows[-1].get("message", "Game harness failed") if rows else "Game harness failed")
    return rows[-1]


def player_view(result):
    if result.get("type") == "observation":
        return {key: result[key] for key in ("episode", "decision_id", "turn", "system", "user")}
    outcome = result.get("outcome", {})
    return {"episode": result.get("episode"), "focal_alive": result.get("focal_alive"),
            **{key: outcome.get(key) for key in ("status", "turn", "reason", "terminal", "truncated")}}


def game_start(strategy: Strategy = "economy", difficulty: Literal[0, 1, 2] = 1,
               other_opponents: Literal["baseline", "stochastic"] = "baseline") -> dict:
    """Start an independent game as an AI opponent and receive its first legal plans."""
    episode = uuid.uuid4().hex
    path = log_path(episode)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    result = engine("init", path, episode, episode, secrets.randbits(62), difficulty, 1,
                    other_opponents, "pressure", PREFERENCES[strategy], "0,1,2,3", "desktop", 150, commit)
    return player_view(result)


def game_observe(episode: str) -> dict:
    """Read the focal opponent's observation and current candidate plans."""
    path = log_path(episode)
    return player_view(engine("observe", path))


def game_choose(episode: str, decision_id: str, plan_id: str, reason: str) -> dict:
    """Commit both actions in a listed plan. Supply the decision ID returned by observe."""
    if not reason.strip() or len(reason) > 300 or not re.fullmatch(r"P[0-9]{2}", plan_id):
        raise ValueError("Expected a current plan ID and a short reason")
    path = log_path(episode)
    if not decision_id.startswith(episode + ":"):
        raise ValueError("The decision ID must belong to this episode")
    decision = engine("choose", path, plan_id, reason, decision_id)
    if decision["type"] == "rejection":
        raise ValueError(decision["error"] + ". Call game_observe again.")
    return {"transition": decision["transition"], "next": player_view(engine("observe", path))}


def game_finish(episode: str) -> dict:
    """Finish a match through its continuation policy after the focal seat is eliminated."""
    path = log_path(episode)
    engine("continue", path)
    return player_view(engine("status", path))


def game_replay(episode: str) -> dict:
    """Replay and verify the recorded decisions of the specified episode."""
    path = log_path(episode)
    return engine("replay", path)

