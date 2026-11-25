"""Collect simulation-teacher episodes and extend the frozen reviewed corpus."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import math
from pathlib import Path
import random
import shutil
import sqlite3
import subprocess
import time

from corpus import frozen_decisions, verify_dataset
from export_dataset import family_split, fingerprint, identity, lines
from sft import file_hash

POLICY = "rollout-v1"
ROOT = Path(__file__).resolve().parents[2]


def setups(start, count, pilot):
    rng = random.Random(start)
    combinations = [(rivals, difficulty, human) for rivals in range(1, 5)
                    for difficulty in range(3) for human in ("pressure", "development", "stochastic")]
    result = []
    seed = start
    while len(result) < count:
        rng.shuffle(combinations)
        for rivals, difficulty, human in combinations:
            while True:
                family = f"{POLICY}-{seed}"
                seed += 1
                if pilot or family_split(family) == "train":
                    break
            actor = rng.randint(1, rivals)
            leaders = ",".join(map(str, rng.sample(range(19), rivals)))
            result.append((family, seed - 1, difficulty, actor, human, leaders))
            if len(result) == count:
                break
    return result


def generate(args):
    output = args.out
    output.mkdir(parents=True, exist_ok=True)
    configs = setups(args.start, args.games, args.pilot)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    root = ROOT / "analysis/wd2play/classes"
    source = (root / "wd2-engine-sources.sha256").read_text()
    def run(batch):
        index, episodes = batch
        tsv = output / f"batch-{index:05}.tsv"
        dst = tsv.with_suffix(".jsonl")
        tmp = tsv.with_suffix(".working")
        inputs = "".join("\t".join(map(str, cfg)) + "\n" for cfg in episodes)
        meta = tsv.with_suffix(".engine")
        if dst.exists():
            if meta.read_text() != source or tsv.read_text() != inputs:
                raise ValueError(f"Cannot reuse mismatched shard {dst}")
        else:
            tsv.write_text(inputs, encoding="ascii")
            meta.write_text(source, encoding="ascii")
            tmp.unlink(missing_ok=True)
            subprocess.run([args.java, "-XX:ActiveProcessorCount=1", "-Xmx512m", "-cp", str(root),
                            "com.example.worlddomination.game.ai.RolloutTeacher", str(tsv), str(tmp), commit,
                            "pilot" if args.pilot else "collect"], check=True)
            tmp.replace(dst)
        actual = 0
        selected = 0
        both = []
        for row in lines(dst):
            if row["terminal"]["terminal"] and not row["terminal"]["truncated"]:
                actual += 1
                selected += len(row["decisions"])
            if args.pilot:
                actor = json.loads(row["config"])["actor"]
                both.append([outcome["nations"][actor]["population"]
                             if outcome["nations"][actor]["alive"] else 0
                             for outcome in (row["terminal"], row["java"])])
        return actual, selected, both

    batches = list(enumerate(configs[i:i + 24] for i in range(0, len(configs), 24)))
    start = time.monotonic()
    finished = 0
    collected = 0
    pilot_results = []
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        for actual, selected, both in pool.map(run, batches):
            finished += actual
            collected += selected
            pilot_results.extend(both)
            print(f"{finished} completed games, {collected} candidate decisions in {time.monotonic() - start:.1f}s",
                  flush=True)
    if args.pilot:
        gaps = [teacher - java for teacher, java in pilot_results]
        summary = {"games": len(gaps), "mean_population_delta_millions": sum(gaps) / len(gaps),
                   "population_win": sum(gap > 0 for gap in gaps), "population_loss": sum(gap < 0 for gap in gaps),
                   "survived_rollout": sum(a > 0 for a, b in pilot_results),
                   "survived_java": sum(b > 0 for a, b in pilot_results)}
        (output / "pilot.json").write_text(json.dumps(summary, indent=2))
        print(json.dumps(summary))


def review_scores(decision):
    screened = decision["screen"]
    scores = decision["paired"]
    if (not all(math.isfinite(score) for score in screened.values())
            or not all(len(pair) == 2 and all(map(math.isfinite, pair)) for pair in scores.values())):
        raise ValueError("Expected finite scores for two matched branches")
    expected = set(sorted(screened, key=screened.get, reverse=True)[:4])
    if set(scores) != expected:
        raise ValueError("Paired plans do not match the short screen")
    ranked = sorted(((sum(x) - 0.25 * abs(x[0] - x[1]), choice) for choice, x in scores.items()),
                    reverse=True)
    if len(ranked) < 2 or ranked[0][1] != decision["selected"] or ranked[0][0] - ranked[1][0] < 0.25:
        raise ValueError("Choice does not meet the paired comparison margin")


def keys(message):
    system, user, response = (m["content"] for m in message["messages"])
    observation = json.loads(user)
    plan = observation["plans"][json.loads(response)["plan_id"]]
    state, action = fingerprint(observation, plan)
    return identity([system, observation]), state, action


def export(args):
    report, frozen = frozen_decisions(args.parent)
    if args.target <= report["counts"]["train"]:
        raise ValueError("Target must extend the parent training split")
    output = args.out
    output.mkdir(parents=True, exist_ok=False)
    database = sqlite3.connect(output / "dedup.sqlite")
    database.executescript("""
        CREATE TABLE observations (exact TEXT PRIMARY KEY, state TEXT, action TEXT);
        CREATE INDEX coarse_state ON observations(state);
        CREATE TABLE candidates (exact TEXT, state TEXT, action TEXT, ordering TEXT,
                                  provenance TEXT, message TEXT);
        CREATE INDEX candidate_state ON candidates(state);
    """)
    for split in ("train", "validation", "test"):
        database.executemany("INSERT OR IGNORE INTO observations VALUES (?,?,?)",
                             (keys(row) for row in lines(args.parent / (split + ".jsonl"))))
    used_seeds = set(map(int, report["seed_families"]))
    reserved = {cfg["seed"] for cfg in json.loads(args.reserved.read_bytes())["episodes"]}
    counts = Counter()
    accepted_families = {}
    for shard in sorted(args.shards.glob("batch-*.jsonl")):
        for episode in lines(shard):
            cfg = json.loads(episode["config"])
            seed = cfg["seed"]
            family = cfg["family"]
            if (family in report["families"] or family_split(family) != "train" or
                    seed in used_seeds or seed in reserved or cfg["teacher"] != POLICY):
                raise ValueError(f"Unapproved family {family}")
            used_seeds.add(seed)
            ending = episode["terminal"]
            if ending["truncated"] or not ending["terminal"]:
                counts["nonterminal"] += len(episode["decisions"])
                continue
            if ending["turn"] != len(episode["pairs"]):
                raise ValueError("Missing replay turn")
            accepted_families[family] = seed
            for decision in episode["decisions"]:
                review_scores(decision)
                observation = json.loads(decision["user"])
                choice = decision["selected"]
                if (decision["id"] != f"{family}:{decision['turn']}:{cfg['actor']}" or
                        decision["turn"] != observation["turn"] or observation["actor"] != cfg["actor"] or
                        decision["turn"] >= len(episode["pairs"]) or episode["pairs"][decision["turn"]] != choice or
                        set(decision["screen"]) != set(observation["plans"])):
                    raise ValueError("Decision does not match replay transcript")
                row = {"messages": [{"role": role, "content": content} for role, content in
                                    zip(("system", "user", "assistant"),
                                        (decision["system"], decision["user"],
                                         json.dumps({"plan_id": choice}, separators=(",", ":"))))]}
                message = json.dumps(row, ensure_ascii=False, separators=(",", ":"))
                exact, state, action = keys(row)
                provenance = {"decision_id": decision["id"], "family": family, "episode": family,
                              "split": "train", "teacher": POLICY, "evidence_sha256": identity(decision),
                              "row_sha256": identity(row), "source_shard": shard.name}
                database.execute("INSERT INTO candidates VALUES(?,?,?,?,?,?)",
                                 (exact, state, action, identity(decision["id"]), json.dumps(provenance), message))
                counts["candidates"] += 1
        print(f"{shard.name}: {counts['candidates']} candidate rows indexed", flush=True)
    database.commit()

    conflicting = """
        SELECT state FROM (
          SELECT state, action FROM candidates UNION SELECT state, action FROM observations
        ) GROUP BY state HAVING COUNT(*) > 1
    """
    database.execute("CREATE TABLE ambiguities AS " + conflicting)
    database.execute("CREATE INDEX ambiguity_state ON ambiguities(state)")
    query = """
        SELECT c.exact, c.state, c.action, c.provenance, c.message FROM candidates c
        WHERE NOT EXISTS(SELECT 1 FROM observations o WHERE o.state=c.state)
          AND NOT EXISTS(SELECT 1 FROM ambiguities a WHERE a.state=c.state)
        GROUP BY c.state HAVING c.ordering = MIN(c.ordering)
        ORDER BY c.ordering
    """
    wanted = args.target - report["counts"]["train"]
    available = database.execute("SELECT COUNT(*) FROM ("+query+")").fetchone()[0]
    if available < wanted:
        raise ValueError(f"Only {available} unique, unambiguous additions for {wanted} slots")
    for split in ("train", "validation", "test"):
        shutil.copyfile(args.parent / (split + ".jsonl"), output / (split + ".jsonl"))
    families = {}
    by_difficulty = Counter()
    plan_labels = Counter()
    with (output / "train.jsonl").open("ab") as data, (output / "provenance.jsonl").open("w", encoding="utf8") as audit:
        for prior in frozen["train"]:
            audit.write(json.dumps(prior, ensure_ascii=False) + "\n")
        for index, (exact, state, action, source, message) in enumerate(database.execute(query + " LIMIT ?", (wanted,)), 1):
            encoded = message.encode("utf8")
            data.write(encoded + b"\n")
            provenance = json.loads(source)
            provenance["row"] = report["counts"]["train"] + index
            provenance["row_sha256"] = hashlib.sha256(encoded).hexdigest()
            audit.write(json.dumps(provenance, ensure_ascii=False) + "\n")
            family = provenance["family"]
            families[family] = "train"
            by_difficulty[json.loads(json.loads(message)["messages"][1]["content"])["difficulty"]] += 1
            plan_labels[json.loads(json.loads(message)["messages"][2]["content"])["plan_id"]] += 1
        for split in ("validation", "test"):
            for prior in frozen[split]:
                audit.write(json.dumps(prior, ensure_ascii=False) + "\n")
    database.close()
    (output / "dedup.sqlite").unlink()
    split_counts = dict(report["counts"], train=args.target)
    seed_families = dict(report["seed_families"])
    seed_families.update({str(accepted_families[f]): f for f in families})
    new_report = {"counts": split_counts, "target_counts": split_counts, "complete": True,
                  "families": dict(report["families"], **families), "seed_families": seed_families,
                  "parent_split_sha256": report["sha256"], "sha256": {split: file_hash(output / (split + ".jsonl"))
                                                                    for split in split_counts},
                  "collection": {"teacher": POLICY, "fresh_games": len(families), "available": available,
                                 "counts": counts, "difficulty": by_difficulty, "selected_ids": plan_labels,
                                 "verification": "complete Java replay; newly automated labels have no Android or manual review"}}
    (output / "report.json").write_text(json.dumps(new_report, indent=2), encoding="utf8")
    verify_dataset(output)
    print(json.dumps(new_report["collection"]))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    collect = commands.add_parser("generate")
    for name in ("start", "games", "jobs"):
        collect.add_argument("--" + name, required=True, type=int)
    collect.add_argument("--java", default="java")
    collect.add_argument("--out", type=Path, required=True)
    collect.add_argument("--pilot", action="store_true")
    build = commands.add_parser("export")
    for name in ("parent", "shards", "reserved", "out"):
        build.add_argument("--" + name, type=Path, required=True)
    build.add_argument("--target", type=int, required=True)
    args = parser.parse_args()
    (generate if args.command == "generate" else export)(args)
