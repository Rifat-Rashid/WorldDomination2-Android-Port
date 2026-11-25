"""Run one policy on prospectively selected, unseen WD2 games for paired comparisons."""
import argparse
from pathlib import Path
import re
import subprocess

from evaluate import (DECODER, LLAMA_COMMIT, complete_reviewed_report, evaluate_episode,
                      inference_server, read_json, sha256, write_json)


def compare(args):
    if not re.fullmatch(r"[a-z0-9-]+", args.label):
        raise ValueError("Use a local policy label")
    manifest = read_json(args.manifest)
    if sha256(args.manifest) != args.manifest_sha256 or manifest.get("purpose") != "prospective_gameplay":
        raise ValueError("Expected the locked prospective gameplay manifest")
    report = read_json(args.dataset_report)
    configs = manifest["episodes"]
    names = [x["family"] for x in configs]
    seeds = [x["seed"] for x in configs]
    if (not configs or len(names) != len(set(names)) or len(seeds) != len(set(seeds))
            or names != [x["episode"] for x in configs]
            or any(not re.fullmatch(r"[a-z0-9-]+", n) for n in names)):
        raise ValueError("Expected unique, safely named game families and seeds")
    if not complete_reviewed_report(report) or set(names) & set(report["families"]):
        raise ValueError("Evaluation families overlap collection or the dataset is incomplete")
    if "seed_families" not in report or set(map(str, seeds)) & set(report["seed_families"]):
        raise ValueError("Expected verified collection seeds disjoint from every prospective setup")
    if args.model:
        version = subprocess.run([str(args.server), "--version"], capture_output=True, text=True, check=True)
        if LLAMA_COMMIT[:9] not in version.stdout + version.stderr:
            raise ValueError("Use the same llama.cpp revision as Android")
    elif args.label not in ("existing", "stochastic"):
        raise ValueError("This policy requires a GGUF")
    args.out.mkdir(parents=True, exist_ok=True)
    params = dict(manifest_sha256=args.manifest_sha256, report_sha256=sha256(args.dataset_report),
                  policy=args.label, model_sha256=sha256(args.model) if args.model else None,
                  gpu_layers=args.gpu_layers, threads=args.threads, deadline=args.deadline,
                  decoder=DECODER if args.model else None)
    stamp = args.out / "parameters.json"
    if stamp.exists() and read_json(stamp) != params:
        raise ValueError("Output directory belongs to another experiment")
    write_json(stamp, params)
    results = []
    with inference_server(args, args.label, args.model, args.out) as server:
        for config in configs:
            result = evaluate_episode(args, config, args.label, server)
            results.append(result)
            write_json(args.out / "games.json", results)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    for field in ("manifest", "dataset-report", "out"):
        p.add_argument("--" + field, type=Path, required=True)
    p.add_argument("--manifest-sha256", required=True)
    p.add_argument("--label", required=True)
    p.add_argument("--model", type=Path)
    p.add_argument("--server", type=Path)
    p.add_argument("--threads", type=int, default=4)
    p.add_argument("--gpu-layers", type=int, default=0)
    p.add_argument("--port", type=int, default=8796)
    p.add_argument("--deadline", type=float, default=30)
    compare(p.parse_args())
