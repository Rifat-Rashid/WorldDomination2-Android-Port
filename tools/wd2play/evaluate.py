"""Evaluate fixed WD2 policies on held-out games or a separate development smoke manifest."""

import argparse
from collections import Counter
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import time
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
CLASSES = ROOT / "analysis/wd2play/classes"
JAVA = ["java", "-cp", str(CLASSES), "com.example.worlddomination.game.ai.Play"]
LLAMA_COMMIT = "707cbafcaa3a88d893147bcc107e76b925a867da"
CONTEXT = 2048
OUTPUT_TOKENS = 20
CHAT_SUFFIX = "<|im_start|>assistant\n<think>\n\n</think>\n\n"
PREFERENCE = "Preserve citizens, exploit threats, and build sustainable fighting strength."
POLICIES = ("existing", "stochastic", "stock", "prequant", "deployment")


def compact(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def verified_manifest(args):
    manifest = read_json(args.manifest)
    if sha256(args.manifest) != args.manifest_sha256:
        raise ValueError("Manifest checksum does not match its locked value")
    expected = "development_smoke" if args.smoke else "held_out_evaluation"
    if manifest.get("schema") != 1 or manifest.get("purpose") != expected:
        raise ValueError("Manifest purpose or schema does not match this run")
    configs = manifest.get("episodes", [])
    episodes = {config["episode"] for config in configs}
    families = {config["family"] for config in configs}
    if (not episodes or len(episodes) != len(configs)
            or any(not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,79}", item)
                   for item in episodes | families)):
        raise ValueError("Episode names must be unique local identifiers")
    by_seed = {}
    for config in configs:
        if by_seed.setdefault(config["seed"], config["family"]) != config["family"]:
            raise ValueError("Repeat seeds must retain their original family")
    if args.smoke:
        if args.dataset_report is not None:
            trained_families = read_json(args.dataset_report)["families"]
            if families & set(trained_families):
                raise ValueError("Development smoke seeds must be separate from dataset families")
    else:
        if args.dataset_report is None:
            raise ValueError("Held-out runs require the frozen dataset report")
        report = read_json(args.dataset_report)
        if report["counts"] != {"train": 1600, "validation": 200, "test": 200}:
            raise ValueError("Held-out evaluation requires the complete reviewed dataset")
        if any(report["families"].get(family) != "test" for family in families):
            raise ValueError("Held-out gameplay may use locked test families only")
    return manifest


def java(*arguments):
    result = subprocess.run([*JAVA, *(str(value) for value in arguments)], cwd=ROOT,
                            text=True, encoding="utf-8", capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stdout + result.stderr)
    return json.loads(result.stdout)


@contextmanager
def java_episode(path):
    with subprocess.Popen([*JAVA, "stream", str(path)], cwd=ROOT, text=True,
                          encoding="utf-8", stdin=subprocess.PIPE, stdout=subprocess.PIPE) as stream:
        def command(*parts):
            stream.stdin.write("\t".join(str(part) for part in parts) + "\n")
            stream.stdin.flush()
            line = stream.stdout.readline()
            result = json.loads(line) if line else {"type": "error", "message": "Java stream ended"}
            if result["type"] == "error":
                raise RuntimeError(result["message"])
            return result

        try:
            yield command
        finally:
            stream.stdin.close()


class LocalServer:
    def __init__(self, port, timeout):
        self.url = f"http://127.0.0.1:{port}"
        self.timeout = timeout
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def call(self, endpoint, body=None, timeout=5):
        request = urllib.request.Request(self.url + endpoint,
                                         None if body is None else compact(body).encode("utf-8"),
                                         {"Content-Type": "application/json"})
        with self.http.open(request, timeout=timeout) as response:
            return json.load(response)

    def select(self, observation):
        started = time.monotonic()
        ids = list(observation["candidates"])

        def request(endpoint, payload):
            remaining = self.timeout - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError("Opponent deadline exceeded")
            return self.call(endpoint, payload, remaining)

        prompt = request("/apply-template", {
            "messages": [{"role": role, "content": observation[role]} for role in ("system", "user")],
            "add_generation_prompt": True,
            "chat_template_kwargs": {"enable_thinking": False},
        })["prompt"]
        if not prompt.endswith(CHAT_SUFFIX):
            raise ValueError("llama.cpp did not apply the pinned non-thinking prompt form")
        tokens = request("/tokenize", {
            "content": prompt, "add_special": True, "parse_special": True,
        })["tokens"]
        if not tokens or len(tokens) + OUTPUT_TOKENS > CONTEXT:
            return None, {"reason": "context_budget", "prompt_tokens": len(tokens)}
        grammar = 'root ::= "{\\\"plan_id\\\":\\\"" id "\\\"}"\nid ::= '
        grammar += " | ".join(compact(plan_id) for plan_id in ids)
        result = request("/completion", {
            "prompt": tokens, "grammar": grammar, "n_predict": OUTPUT_TOKENS,
            "temperature": 0, "samplers": [], "stream": False, "cache_prompt": False,
        })
        content = result["content"]
        chosen = re.fullmatch(r'\{"plan_id":"(P[0-9]{2})"\}', content)
        valid = (chosen and chosen[1] in ids and result["stop_type"] == "eos"
                 and not result.get("truncated", False))
        metrics = {"reason": "accepted" if valid else "invalid_or_incomplete",
                   "prompt_tokens": len(tokens), "output": content,
                   "stop_type": result.get("stop_type"), "timings": result.get("timings", {})}
        return chosen[1] if valid else None, metrics


@contextmanager
def inference_server(args, policy, model, directory):
    if model is None:
        yield None
        return
    server = LocalServer(args.port, args.deadline)
    try:
        server.call("/health", timeout=0.5)
    except OSError:
        pass
    else:
        raise ValueError(f"Port {args.port} already has an active server")
    command = [str(args.server), "-m", str(model), "--host", "127.0.0.1", "--port", str(args.port),
               "-c", str(CONTEXT), "-np", "1", "-b", "128", "-ub", "128", "--jinja",
               "--chat-template-kwargs", '{"enable_thinking":false}', "--no-context-shift",
               "-t", str(args.threads), "-ngl", str(args.gpu_layers)]
    with (directory / f"{policy}-server.log").open("ab") as stdout:
        with subprocess.Popen(command, stdout=stdout, stderr=subprocess.STDOUT) as process:
            try:
                expires = time.monotonic() + 180
                while time.monotonic() < expires:
                    if process.poll() is not None:
                        raise RuntimeError(f"llama-server exited. See {stdout.name}")
                    try:
                        if server.call("/health", timeout=1).get("status") == "ok":
                            break
                    except OSError:
                        pass
                    time.sleep(0.25)
                else:
                    raise TimeoutError(f"llama-server startup timed out. See {stdout.name}")
                yield server
            finally:
                process.terminate()


def stream_rows(path):
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            yield json.loads(line)


def evaluate_episode(args, config, policy, server):
    episode = config["episode"] + "-" + policy
    raw = args.out / (episode + ".jsonl")
    diagnostics = args.out / (episode + "-requests.jsonl")
    if not raw.exists():
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        java("init", raw, episode, config["family"], config["seed"], config["difficulty"],
             config["actor"], config["policy"], config["human_policy"], PREFERENCE,
             config["leaders"], "evaluator:" + policy, config.get("max_turns", 180), commit)
    with java_episode(raw) as game, diagnostics.open("a", encoding="utf-8") as log:
        while True:
            observation = game("observe")
            if observation["type"] != "observation":
                if not observation["outcome"]["terminal"] and not observation["outcome"]["truncated"]:
                    game("continue")
                break
            if json.loads(observation["user"])["v"] != 2:
                raise ValueError("Evaluation must use the Android v2 contract")
            decision = observation["decision_id"]
            plan = None
            started = time.perf_counter()
            if server is not None and len(observation["candidates"]) >= 2:
                try:
                    plan, evidence = server.select(observation)
                except (TimeoutError, urllib.error.URLError) as error:
                    cause = error.reason if isinstance(error, urllib.error.URLError) else error
                    evidence = {"reason": "timeout" if isinstance(cause, TimeoutError) else "server_error",
                                "error": str(error)}
            elif policy == "stochastic":
                ids = list(observation["candidates"])
                entropy = hashlib.sha256(f"{config['seed']}:{observation['turn']}:{config['actor']}".encode())
                plan = ids[int.from_bytes(entropy.digest()[:8], "big") % len(ids)]
                evidence = {"reason": "legal_stochastic"}
            elif policy == "existing":
                evidence = {"reason": "existing_ai"}
            else:
                evidence = {"reason": "no_alternative_plan"}
            elapsed = round(1000 * (time.perf_counter() - started), 3)
            # Write the observed inference result before committing. A resumed process may retry
            # an interrupted request, but only the result linked to the committed row is counted.
            bound_reason = f"{policy}:{evidence['reason']}:{time.time_ns()}"
            log.write(compact({"decision_id": decision, "decision_reason": bound_reason,
                               "elapsed_ms": elapsed, "selected": plan, **evidence}) + "\n")
            log.flush()
            row = (game("choose", plan, bound_reason, decision) if plan
                   else game("fallback", "", bound_reason, decision))
            if row["type"] != "evaluation":
                raise RuntimeError(f"Evaluation command did not settle: {row}")
    terminal = java("replay", raw)
    return summarize(config, policy, terminal, raw, diagnostics)


def summarize(config, policy, terminal, raw, diagnostics):
    requests = {row["decision_reason"]: row for row in stream_rows(diagnostics)}
    actions = Counter()
    choices = Counter()
    latencies = []
    token_counts = []
    inflicted = lost = 0
    for row in stream_rows(raw):
        if row["type"] != "evaluation":
            continue
        request = requests[row["reason"]]
        choices[request["reason"]] += 1
        if policy not in {"existing", "stochastic"}:
            latencies.append(request["elapsed_ms"])
        if "prompt_tokens" in request:
            token_counts.append(request["prompt_tokens"])
        for order in row["committed_orders"].get(str(config["actor"]), []):
            actions[order["action"]] += 1
        for event in row["transition"]["combat"]:
            if event["from"] == config["actor"]:
                inflicted += event["casualties"]
            else:
                lost += event["casualties"]
    outcome = terminal["outcome"]
    focal = next(nation for nation in outcome["nations"] if nation["actor"] == config["actor"])
    return {"episode": config["episode"], "family": config["family"], "policy": policy,
            "outcome": outcome, "focal": focal, "decisions": sum(choices.values()),
            "decision_sources": dict(choices), "actions": dict(actions),
            "combat_casualties_inflicted": inflicted, "combat_casualties_taken": lost,
            "max_prompt_tokens": max(token_counts, default=None),
            "mean_request_ms": statistics.mean(latencies) if latencies else None,
            "median_request_ms": statistics.median(latencies) if latencies else None,
            "engine_log_sha256": sha256(raw), "diagnostics_sha256": sha256(diagnostics)}


def evaluate(args):
    manifest = verified_manifest(args)
    labels = args.policies.split(",")
    if len(set(labels)) != len(labels) or not set(labels) <= set(POLICIES):
        raise ValueError("Unknown or duplicate policy label")
    if not args.smoke and set(labels) != set(POLICIES):
        raise ValueError("A held-out evaluation must specify all five policies")
    models = {"existing": None, "stochastic": None, "stock": args.stock,
              "prequant": args.merged_f16, "deployment": args.deployment_gguf}
    needs_models = any(label not in {"existing", "stochastic"} for label in labels)
    if needs_models and (args.server is None or any(models[label] is None for label in labels
                                                   if label not in {"existing", "stochastic"})):
        raise ValueError("Specify llama-server and each requested policy's model")
    version = None
    if needs_models:
        result = subprocess.run([str(args.server), "--version"], capture_output=True, text=True, check=True)
        version = " ".join((result.stdout + result.stderr).split())
        required = args.smoke_server_commit if args.smoke and args.smoke_server_commit else LLAMA_COMMIT[:9]
        if required not in version:
            raise ValueError(f"llama-server version does not match {required}")
    parameters = {"purpose": manifest["purpose"], "manifest_sha256": args.manifest_sha256,
                  "dataset_report_sha256": sha256(args.dataset_report) if args.dataset_report else None,
                  "server_version": version, "android_llama_commit": LLAMA_COMMIT,
                  "context_tokens": CONTEXT, "max_output_tokens": OUTPUT_TOKENS,
                  "deadline_seconds": args.deadline, "threads": args.threads, "gpu_layers": args.gpu_layers,
                  "policies": labels, "model_sha256": {label: sha256(models[label]) for label in labels
                                                       if models[label] is not None}}
    args.out.mkdir(parents=True, exist_ok=True)
    locked = args.out / "parameters.json"
    if locked.exists() and read_json(locked) != parameters:
        raise ValueError("This output directory already belongs to a different evaluation")
    if not locked.exists():
        write_json(locked, parameters)
    results = []
    for label in labels:
        with inference_server(args, label, models[label], args.out) as server:
            for episode in manifest["episodes"]:
                result = evaluate_episode(args, episode, label, server)
                print(compact(result), flush=True)
                results.append(result)
                write_json(args.out / "results.json", {"parameters": parameters, "games": results})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--dataset-report", type=Path)
    parser.add_argument("--server", type=Path)
    parser.add_argument("--stock", type=Path)
    parser.add_argument("--merged-f16", type=Path)
    parser.add_argument("--deployment-gguf", type=Path)
    parser.add_argument("--policies", default=",".join(POLICIES))
    parser.add_argument("--port", type=int, default=8796)
    parser.add_argument("--deadline", type=float, default=60)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--gpu-layers", type=int, default=0)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--smoke-server-commit")
    evaluate(parser.parse_args())
