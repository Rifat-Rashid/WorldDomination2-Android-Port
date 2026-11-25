"""Export independently reviewed, replayed and Android-verified WD2 episodes."""
import argparse
from collections import Counter, defaultdict, deque
import hashlib
import json
from pathlib import Path
import re
import tempfile

from game_tools import engine
from sft import encode_example, file_hash
from corpus import ORIGINAL_COUNTS, frozen_decisions


def packed(x):
    return json.dumps(x, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def identity(obj):
    return hashlib.sha256(packed(obj).encode()).hexdigest()


def principal(label):
    """A collector's review-session suffix does not create an independent person."""
    label = label.strip().casefold()
    match = re.match(r"^(teacher-[abc]|coordinator)(?:-|$)", label)
    return match.group(1) if match else label


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
            if field == "spend":
                return {key: amount // 5 for key, amount in value.items()}
            return {k: coarse(v, k) for k, v in value.items() if k not in ("turn", "priority")}
        if isinstance(value, list):
            return [coarse(v, field) for v in value]
        if type(value) is int and field in ("cash", "budget", "cities", "influence", "regard", "income"):
            return value // 10
        if type(value) is int and field == "reserve":
            return value // 5
        return value
    state = {k: v for k, v in observation.items() if k != "plans"}
    choices = sorted(set(packed(coarse(candidate)) for candidate in observation["plans"].values()))
    return identity([coarse(state), choices]), identity(coarse(plan))


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


def sample_families(rows, limit):
    """Round-robin the eligible families, shuffling turns by a fixed content hash."""
    groups = defaultdict(list)
    for item in rows:
        groups[item["audit"]["family"]].append(item)
    queue = deque(deque(sorted(groups[name], key=lambda x: identity(x["audit"]["decision_id"])))
                  for name in sorted(groups, key=identity))
    while queue and limit:
        family = queue.popleft()
        yield family.popleft()
        if family:
            queue.append(family)
        limit -= 1


def export(args):
    from transformers import AutoTokenizer
    parent_report, frozen = (frozen_decisions(args.extend_from) if args.extend_from else
                             ({}, {split: [] for split in ORIGINAL_COUNTS}))
    if args.train_count < ORIGINAL_COUNTS["train"] or (args.extend_from and
            args.train_count <= parent_report["counts"]["train"]):
        raise ValueError("Choose a larger training target for an extension")
    quota = dict(ORIGINAL_COUNTS, train=args.train_count)
    original_episodes = {row["episode"] for rows in frozen.values() for row in rows}
    retained = {row["decision_id"] for rows in frozen.values() for row in rows}
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True)
    reviews = {}
    for review in lines(args.reviews):
        decision = review["decision_id"]
        # Corrections supersede older reviews, which remain in the append-only journal.
        if not review["reviewer"] or not review["why"] or type(review["accepted"]) is not bool:
            raise ValueError("Unattributed review")
        reviews[decision] = review
    counts = Counter()
    rejected = Counter()
    turns = Counter()
    actions = Counter()
    seen = set()
    state_splits = {}
    seeds = {}
    families = {}
    token_lengths = []
    degrees = Counter()
    teachers = Counter()
    output = {k: [] for k in quota}
    eligible = {k: [] for k in quota}
    audit = []
    for log in sorted(args.episodes.glob("*.jsonl"), key=lambda p: (p.stem not in original_episodes, p.name)):
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
            if family in parent_report.get("families", {}) and row["decision_id"] not in retained:
                continue
            review = reviews.get(row["decision_id"])
            if not review or not review["accepted"]:
                rejected["unreviewed_or_rejected"] += 1
                continue
            if principal(review["reviewer"]) == principal(row["teacher"]) or review["row_sha256"] != identity(row):
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
            state_id, selection_id = fingerprint(obs, plans[selected])
            fp = (state_id, selection_id)
            if fp in seen or state_splits.get(state_id, split) != split:
                rejected["duplicate"] += 1
                continue
            message = {"messages": [{"role": "system", "content": row["system"]},
                                    {"role": "user", "content": row["user"]},
                                    {"role": "assistant", "content": json.dumps(
                                        {"plan_id": selected}, separators=(",", ":"))}]}
            tokens = encode_example(tokenizer, message)
            seen.add(fp)
            state_splits[state_id] = split
            encoded = packed(message)
            provenance = {"split": split, "family": family,
                                 "episode": cfg["episode"], "decision_id": row["decision_id"],
                                 "raw_sha256": identity(row), "row_sha256": hashlib.sha256(encoded.encode()).hexdigest(),
                                 "review": review, "screenshot_sha256": proof["screenshot_sha256"]}
            eligible[split].append({"encoded": encoded, "length": len(tokens["input_ids"]), "turn": row["turn"],
                                    "actions": [a["action"] for a in plans[selected]], "difficulty": cfg["difficulty"],
                                    "teacher": cfg["teacher"], "audit": provenance})
    for split, choices in eligible.items():
        by_id = {x["audit"]["decision_id"]: x for x in choices}
        originals = []
        for prior in frozen[split]:
            item = by_id.pop(prior["decision_id"], None)
            if item is None or dict(item["audit"], row=prior["row"]) != prior:
                raise ValueError("Original decision, evidence or review changed: " + prior["decision_id"])
            originals.append(item)
        added = sample_families(by_id.values(), quota[split] - len(originals))
        for item in [*originals, *added]:
            counts[split] += 1
            output[split].append(item["encoded"])
            token_lengths.append(item["length"])
            turns[str(item["turn"] // 10 * 10)] += 1
            actions.update(item["actions"])
            degrees[str(item["difficulty"])] += 1
            teachers[item["teacher"]] += 1
            audit.append(packed(dict(item["audit"], row=counts[split])))
        rejected["split_full"] += len(choices) - counts[split]
    if not args.partial and any(counts[k] != target for k, target in quota.items()):
        raise ValueError(f"Insufficient verified, reviewed data: {dict(counts)}; quota {quota}")
    if args.out.exists():
        raise ValueError("Choose a fresh output directory")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.out.parent) as stage:
        dest = Path(stage) / "dataset"
        dest.mkdir()
        for split, records in {**output, "provenance": audit}.items():
            (dest / (split + ".jsonl")).write_bytes("".join(r + "\n" for r in records).encode())
        report = {"counts": {k: counts[k] for k in quota}, "families": families, "target_counts": quota,
                  "seed_families": seeds, "parent_split_sha256": parent_report.get("sha256"),
                  "sha256": {k: file_hash(dest / (k + ".jsonl")) for k in quota},
                  "action_counts": actions, "turn_decades": turns, "rejections": rejected,
                  "teachers": teachers, "difficulty_counts": degrees, "complete": all(counts[k] == v for k, v in quota.items()),
                  "token_range": [min(token_lengths), max(token_lengths)] if token_lengths else []}
        (dest / "report.json").write_text(json.dumps(report, indent=2), encoding="utf8")
        (dest / "DATA_CARD.md").write_text(
            "# World Domination 2 opponent decisions\n\n"
            + ("Full quota reached." if report["complete"] else "Partial collection.")
            + f" Contains {counts['train']} training, {counts['validation']} validation and {counts['test']} test decisions "
            + f"from {len(families)} families. Each target selects one legal ordered pair on one observed turn.\n\n"
            + "Families, including related seeds and branches, use the WD2 family-v1 hash split. The exporter verifies "
            + "engine replay, Android end-screen evidence and an independent review of each accepted choice. It drops "
            + "near-duplicate decisions and excludes repeated observations across splits. Review is qualitative. "
            + "Human elimination may leave several surviving AI nations. The data alone do not establish that a trained opponent plays better.\n\n"
            + "See report.json for coverage, exclusions, token lengths and SHA-256 hashes. provenance.jsonl links every "
            + "exported example to its decision record, review and device screenshot.\n", encoding="utf8")
        dest.rename(args.out)
    print(dict(counts))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for arg in ("episodes", "evidence", "reviews", "base", "out"):
        parser.add_argument("--" + arg, required=True, type=Path)
    parser.add_argument("--extend-from", type=Path, help="Preserve all certified parent decisions and holdout splits")
    parser.add_argument("--train-count", type=int, default=ORIGINAL_COUNTS["train"])
    parser.add_argument("--partial", action="store_true", help="Export available evidence without claiming the quota")
    export(parser.parse_args())
