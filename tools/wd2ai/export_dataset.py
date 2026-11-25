"""Export independently reviewed, replayed and Android-verified WD2 episodes."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import tempfile

from game_tools import engine
from sft import encode_example, file_hash

QUOTA = {"train": 1600, "validation": 200, "test": 200}


def packed(x):
    return json.dumps(x, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def identity(obj):
    return hashlib.sha256(packed(obj).encode()).hexdigest()


def lines(path):
    with path.open(encoding="utf8") as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line)


def family_split(family):
    # Fixed before collection. Related seeds and all replay branches share one family.
    bucket = int(hashlib.sha256(("wd2-family-v1/" + family).encode()).hexdigest()[:12], 16) % 10
    return "test" if bucket == 0 else "validation" if bucket == 1 else "train"


def fingerprint(observation, plan):
    # Collapse position permutations, obvious aliases and small resource/population changes.
    # Keep actual available choices, stock, readiness and agreements to distinguish decisions.
    def coarse(value, field=""):
        if isinstance(value, dict):
            return {k: coarse(v, k) for k, v in value.items() if k not in ("turn", "priority")}
        if isinstance(value, list):
            return [coarse(v, field) for v in value]
        if type(value) is int and field in ("cash", "budget", "cities", "influence", "regard", "income"):
            return value // 10
        return value
    state = {k: v for k, v in observation.items() if k != "plans"}
    choices = sorted(set(packed(candidate) for candidate in observation["plans"].values()))
    return identity([coarse(state), choices, plan])


def evidence(log, evidence_dir, episode, outcome):
    receipt = json.loads((evidence_dir / (episode + "-receipt.json")).read_text(encoding="utf8"))
    if receipt["episode"] != episode or receipt["log_sha256"] != file_hash(log) or receipt["outcome"] != outcome:
        raise ValueError("Android receipt does not match the completed episode")
    for key in ("screenshot", "checkpoint", "fixture"):
        proof = evidence_dir / receipt[key]
        if proof.parent != evidence_dir or file_hash(proof) != receipt[key + "_sha256"]:
            raise ValueError(f"Invalid {key} evidence")
    if "OK (1 test)" not in (evidence_dir / receipt["device_test"]).read_text(encoding="utf8"):
        raise ValueError("Android replay did not pass")
    return receipt


def export(args):
    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True)
    reviews = {}
    for review in lines(args.reviews):
        decision = review["decision_id"]
        # Corrections supersede older reviews, which remain in the append-only journal.
        if not review["reviewer"] or not review["why"]:
            raise ValueError("Unattributed review")
        reviews[decision] = review
    counts = Counter()
    rejected = Counter()
    turns = Counter()
    actions = Counter()
    seen = set()
    seeds = {}
    families = {}
    token_lengths = []
    output = {k: [] for k in QUOTA}
    audit = []
    for log in sorted(args.episodes.glob("*.jsonl")):
        raw = list(lines(log))
        cfg, last = raw[0], raw[-1]
        family = cfg["family"]
        split = family_split(family)
        if seeds.setdefault(cfg["seed"], family) != family:
            raise ValueError("Related seeds must be registered as the same family")
        if last["type"] != "terminal" or not last["outcome"]["terminal"] or last["outcome"]["truncated"]:
            rejected["unfinished_episode"] += 1
            continue
        if cfg.get("decision_contract") != 2 or not any(r.get("decision_id") in reviews for r in raw):
            rejected["unreviewed_or_old_episode"] += 1
            continue
        replayed = engine("replay", log)
        if replayed["outcome"] != last["outcome"]:
            raise ValueError("Terminal engine replay mismatch")
        proof = evidence(log, args.evidence, cfg["episode"], last["outcome"])
        families[family] = split
        for row in raw:
            if row["type"] != "decision":
                continue
            review = reviews.get(row["decision_id"])
            if not review or not review["accepted"]:
                rejected["unreviewed_or_rejected"] += 1
                continue
            if review["reviewer"] == row["teacher"] or review["row_sha256"] != identity(row):
                raise ValueError("Review must link an independent reviewer and the exact raw record")
            obs = json.loads(row["user"])
            plans = row["candidates"]
            selected = row["selected"]
            if (obs["v"] != 2 or obs["plans"] != plans or selected not in plans
                    or row["role"] != "opponent" or len(plans[selected]) != 2
                    or row.get("engine_source_sha256") is None):
                raise ValueError("Policy observation or plan provenance mismatch")
            if len(set(map(packed, plans.values()))) < 2:
                rejected["forced"] += 1
                continue
            fp = fingerprint(obs, plans[selected])
            if fp in seen:
                rejected["duplicate"] += 1
                continue
            if counts[split] >= QUOTA[split]:
                rejected["split_full"] += 1
                continue
            message = {"messages": [{"role": "system", "content": row["system"]},
                                    {"role": "user", "content": row["user"]},
                                    {"role": "assistant", "content": json.dumps(
                                        {"plan_id": selected}, separators=(",", ":"))}]}
            tokens = encode_example(tokenizer, message)
            seen.add(fp)
            encoded = packed(message)
            output[split].append(encoded)
            token_lengths.append(len(tokens["input_ids"]))
            counts[split] += 1
            turns[str(row["turn"] // 10 * 10)] += 1
            actions.update(action["action"] for action in plans[selected])
            audit.append(packed({"split": split, "row": counts[split], "family": family,
                                 "episode": cfg["episode"], "decision_id": row["decision_id"],
                                 "raw_sha256": identity(row), "row_sha256": hashlib.sha256(encoded.encode()).hexdigest(),
                                 "review": review, "screenshot_sha256": proof["screenshot_sha256"]}))
    if not args.partial and any(counts[k] != target for k, target in QUOTA.items()):
        raise ValueError(f"Insufficient verified, reviewed data: {dict(counts)}; quota {QUOTA}")
    if args.out.exists():
        raise ValueError("Choose a fresh output directory")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.out.parent) as stage:
        dest = Path(stage) / "dataset"
        dest.mkdir()
        for split, records in {**output, "provenance": audit}.items():
            (dest / (split + ".jsonl")).write_bytes("".join(r + "\n" for r in records).encode())
        report = {"counts": {k: counts[k] for k in QUOTA}, "families": families,
                  "sha256": {k: file_hash(dest / (k + ".jsonl")) for k in QUOTA},
                  "action_counts": actions, "turn_decades": turns, "rejections": rejected,
                  "token_range": [min(token_lengths), max(token_lengths)] if token_lengths else []}
        (dest / "report.json").write_text(json.dumps(report, indent=2), encoding="utf8")
        dest.rename(args.out)
    print(dict(counts))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for arg in ("episodes", "evidence", "reviews", "base", "out"):
        parser.add_argument("--" + arg, required=True, type=Path)
    parser.add_argument("--partial", action="store_true", help="Export available evidence without claiming the quota")
    export(parser.parse_args())

