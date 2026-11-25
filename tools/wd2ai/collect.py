"""Run one independent local-model teacher through a complete authoritative Java episode."""
import argparse
import hashlib
import json
import os
import subprocess
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

from game_tools import CLASSES, MAIN, PREFERENCES, ROOT, engine

TEACHER = "unsloth/Qwen3.5-9B-GGUF@3885219b6810b007914f3a7950a8d1b469d598a5:Q8_0"
TEACHER_INSTRUCTION = (
    "You are the focal opponent in this simulated board game. Consider the two commands together. "
    "Use this turn's observations and the last public feedback. Quantities for each action are reserved "
    "before settlement. A prepared carrier expires unless launched next turn. The goal is to survive "
    "and defeat the human seat (id 0). Select a plan ID FIRST, then explain the two actions "
    "actually listed under that ID in one short sentence, supported by visible numbers. "
    "Never claim a rival's private stock or intent. RESOURCES earns money, not population. "
    "MASS_MEDIA raises influence; PROPAGANDA can transfer citizens. RESEARCH unlocks equipment; "
    "PRODUCTION purchases stock. Preparing a carrier does not launch it. A ceasefire offer is "
    "conditional. Consider fires already aimed at small cities to avoid wasting a second attack. "
    "When low on cash and stock, fund production rather than repeatedly holding idle cash. "
    "Defend against visible prepared carriers if you have a compatible counter. "
    "If possible take an affordable path to victory before a long-term investment pays off.")


def endpoint():
    url = os.environ.get("WD2_TEACHER_URL", "http://127.0.0.1:8795")
    parts = urlsplit(url)
    if (parts.scheme != "http" or parts.hostname not in ("127.0.0.1", "::1") or not parts.port
            or parts.username or parts.password or parts.path not in ("", "/") or parts.query or parts.fragment):
        raise ValueError("Teacher endpoint must be local")
    return url.rstrip("/")


def local_request(path, body=None):
    headers = {"Content-Type": "application/json"}
    if os.environ.get("WD2_TEACHER_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["WD2_TEACHER_TOKEN"]
    request = urllib.request.Request(endpoint() + path, None if body is None else json.dumps(body).encode(), headers)
    with urllib.request.urlopen(request, timeout=180) as response:
        return json.load(response)


def complete(messages, schema, seed, temperature=0.35):
    body = {"model": "wd2-teacher", "messages": messages, "temperature": temperature,
            "seed": seed, "max_tokens": 160, "chat_template_kwargs": {"enable_thinking": False},
            "response_format": {"type": "json_schema", "json_schema": {"name": "decision", "schema": schema}}}
    result = local_request("/v1/chat/completions", body)
    choice = result["choices"][0]
    if choice["finish_reason"] != "stop":
        raise ValueError("Teacher did not finish its response")
    return json.loads(choice["message"]["content"])


def journal(path, entry):
    with path.open("a", encoding="utf8") as out:
        out.write(json.dumps(entry, separators=(",", ":")) + "\n")
        out.flush()
        os.fsync(out.fileno())


def collect(root, family, index, strategy, difficulty, leaders, policy, human):
    # Refuse to label a differently provisioned server as the pinned teacher.
    props = local_request("/props")
    model_path = Path(props["model_path"])
    if (model_path.name.lower() != "qwen3.5-9b-q8_0.gguf"
            or TEACHER.split("@")[1].split(":")[0] not in model_path.parts):
        raise ValueError("Teacher server must use the documented Qwen3.5-9B Q8_0 snapshot")
    episode = family + "-" + strategy
    path = root / (episode + ".jsonl")
    root.mkdir(parents=True, exist_ok=True)
    seed = int.from_bytes(hashlib.sha256(family.encode()).digest()[:7], "big")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if not path.exists():
        engine("init", path, episode, family, seed, difficulty, 1, policy, human,
               PREFERENCES[strategy], leaders, TEACHER + ":" + strategy, 180, commit)
    feedback = {}
    for line in path.read_text(encoding="utf8").splitlines():
        row = json.loads(line)
        if row["type"] == "episode" and any(row[key] != value for key, value in {
                "seed": seed, "family": family, "difficulty": difficulty, "leaders": leaders,
                "human_policy": human, "policy": policy, "teacher": TEACHER + ":" + strategy}.items()):
            raise ValueError("Resume parameters disagree with the recorded episode")
        if row["type"] == "decision":
            feedback = row["transition"]
    with subprocess.Popen(["java", "-cp", str(CLASSES), MAIN, "stream", str(path)],
                          cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                          encoding="utf8") as proc:
        def command(*args):
            proc.stdin.write("\t".join(args) + "\n")
            proc.stdin.flush()
            data = json.loads(proc.stdout.readline())
            if data["type"] == "error":
                raise ValueError(data["message"])
            return data

        try:
            while True:
                observation = command("observe")
                if observation["type"] != "observation":
                    if not observation["outcome"]["terminal"] and not observation["outcome"]["truncated"]:
                        command("continue")
                    break
                payload = observation["user"]
                ids = list(observation["candidates"])
                properties = {"plan_id": {"type": "string", "enum": ids},
                              "intent": {"type": "string", "minLength": 4, "maxLength": 180}}
                schema = {"type": "object", "properties": properties,
                          "required": list(properties), "additionalProperties": False}
                # Teacher sees only focal observation and feedback. Seed/family/log stay out of text.
                msgs = [{"role": "system", "content": TEACHER_INSTRUCTION},
                        {"role": "user", "content": payload + "\nPrevious feedback: " + json.dumps(feedback)}]
                for attempt in range(3):
                    try:
                        answer = complete(msgs, schema, index + observation["turn"] + attempt)
                        if answer.get("plan_id") not in ids or not isinstance(answer.get("intent"), str):
                            raise ValueError("Teacher proposed an unavailable plan or missing reason")
                        break
                    except (ValueError, KeyError) as error:
                        journal(path.with_suffix(".attempts.json"), {"decision_id": observation["decision_id"],
                                "attempt": attempt + 1, "error": str(error)})
                        if attempt == 2:
                            raise
                reason = " ".join(answer["intent"].split())[:250]
                row = command("choose", answer["plan_id"], reason, observation["decision_id"])
                if row["type"] != "decision":
                    raise ValueError("Stale or rejected teacher choice")
                feedback = row["transition"]
        finally:
            proc.stdin.close()
    verified = engine("replay", path)
    print(episode, json.dumps(verified, separators=(",", ":")), flush=True)
    return verified


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("directory", type=Path)
    p.add_argument("family")
    p.add_argument("--strategy", choices=PREFERENCES, required=True)
    p.add_argument("--difficulty", type=int, choices=range(3), default=1)
    p.add_argument("--leaders", default="0,1")
    p.add_argument("--other-policy", choices=("baseline", "stochastic"), default="baseline")
    p.add_argument("--human", choices=("pressure", "development", "stochastic"), default="pressure")
    args = p.parse_args()
    if not args.family.replace("-", "").isalnum() or len(args.family) > 60:
        p.error("Family must be a short alphanumeric identifier")
    collect(args.directory.resolve(), args.family, 3407, args.strategy, args.difficulty,
            args.leaders, args.other_policy, args.human)
