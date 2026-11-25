"""Measure a pinned GGUF against the reviewed targets for one frozen split."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import random
import subprocess

from evaluate import LLAMA_COMMIT, compact, inference_server, sha256, stream_rows, write_json


def family_interval(groups):
    """Resample whole families to preserve dependence within each trajectory."""
    choices = list(groups.values())
    if len(choices) < 2:
        return None
    rng = random.Random(3407)
    samples = []
    for _ in range(2000):
        picked = rng.choices(choices, k=len(choices))
        samples.append(sum(v[0] for v in picked) / sum(v[1] for v in picked))
    samples.sort()
    return [samples[49], samples[1949]]


def score(args):
    report = json.loads((args.dataset / "report.json").read_text(encoding="utf8"))
    path = args.dataset / (args.split + ".jsonl")
    if report["counts"] != {"train": 1600, "validation": 200, "test": 200} or sha256(path) != report["sha256"][args.split]:
        raise ValueError("Expected a complete reviewed export and the frozen split")
    version = subprocess.run([str(args.server), "--version"], capture_output=True, text=True, check=True)
    if LLAMA_COMMIT[:9] not in version.stdout + version.stderr:
        raise ValueError("Use the native revision deployed on Android")
    rows = list(stream_rows(path))
    provenance = [r for r in stream_rows(args.dataset / "provenance.jsonl") if r["split"] == args.split]
    if len(rows) != 200 or len(rows) != len(provenance):
        raise ValueError("Incomplete row provenance")
    for index, (line, entry) in enumerate(zip(path.read_bytes().splitlines(), provenance), start=1):
        if (entry["row"] != index or entry["row_sha256"] != hashlib.sha256(line).hexdigest()
                or report["families"].get(entry["family"]) != args.split):
            raise ValueError("Provenance does not match the frozen row or family split")
    params = {"split": args.split, "split_sha256": sha256(path), "model_sha256": sha256(args.model),
              "llama_commit": LLAMA_COMMIT, "gpu_layers": args.gpu_layers, "threads": args.threads,
              "deadline_s": args.deadline}
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = args.out / "parameters.json"
    if manifest.exists() and json.loads(manifest.read_text()) != params:
        raise ValueError("Use an independent output directory for this configuration")
    write_json(manifest, params)
    record = args.out / "choices.jsonl"
    results = list(stream_rows(record)) if record.exists() else []
    if [r["id"] for r in results] != [r["decision_id"] for r in provenance[:len(results)]]:
        raise ValueError("Interrupted evaluation does not match the frozen rows")
    with inference_server(args, "policy", args.model, args.out) as server, record.open("a", encoding="utf8") as out:
        for row, src in zip(rows[len(results):], provenance[len(results):]):
            system, user, assistant = row["messages"]
            policy_view = {"system": system["content"], "user": user["content"],
                           "candidates": json.loads(user["content"])["plans"]}
            try:
                chosen, timing = server.select(policy_view)
            except (TimeoutError, OSError, ValueError) as error:
                chosen, timing = None, {"reason": type(error).__name__}
            target = json.loads(assistant["content"])["plan_id"]
            decision = {"id": src["decision_id"], "family": src["family"], "row_sha256": src["row_sha256"],
                        "target": target, "selected": chosen, "correct": chosen == target, "reason": timing["reason"]}
            results.append(decision)
            out.write(compact(decision) + "\n")
            out.flush()
    grouped = {}
    for row in results:
        values = grouped.setdefault(row["family"], [0, 0])
        values[0] += int(row["correct"])
        values[1] += 1
    measurements = {"n": len(results), "families": len(grouped),
                    "accuracy": sum(r["correct"] for r in results) / len(results),
                    "family_bootstrap_95_interval": family_interval(grouped),
                    "selected_ids": Counter(str(r["selected"]) for r in results),
                    "target_ids": Counter(r["target"] for r in results),
                    "decision_sources": Counter(r["reason"] for r in results)}
    write_json(args.out / "metrics.json", measurements)
    print(compact(measurements))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", choices=("validation", "test"), required=True)
    for key in ("dataset", "server", "model", "out"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--port", type=int, default=8797)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--gpu-layers", type=int, default=0)
    parser.add_argument("--deadline", type=int, default=60)
    score(parser.parse_args())
