"""MCP entry points for the local Studio dataset preview and the WD2 Unsloth job."""
import json
import os
from pathlib import Path
import subprocess
import sys

import httpx

from studio import studio_request, studio_url, training_status
from corpus import verify_dataset


def workspace():
    value = os.environ.get("WD2_TRAINING_ROOT")
    if not value:
        raise ValueError("Set WD2_TRAINING_ROOT to the directory containing base and reviewed")
    return Path(value).resolve()


def certified():
    directory = workspace() / "reviewed"
    return directory, verify_dataset(directory)


async def preview_reviewed_training() -> dict:
    """Upload the reviewed training split into Desktop and inspect its real column mapping."""
    directory, manifest = certified()
    path = directory / "train.jsonl"
    filename = "wd2-train-" + manifest["sha256"]["train"][:12] + ".jsonl"
    token = os.environ.get("WD2_STUDIO_TOKEN")
    headers = {"Authorization": "Bearer " + token} if token else {}
    async with httpx.AsyncClient(base_url=studio_url(), trust_env=False, headers=headers,
                                 timeout=60, follow_redirects=False) as client:
        with path.open("rb") as stream:
            response = await client.post("/api/datasets/upload", files={"file": (filename, stream, "application/jsonl")})
        if not response.is_success:
            raise ValueError(f"Desktop dataset upload returned HTTP {response.status_code}")
    data = await studio_request("/api/datasets/check-format", "POST", {"dataset_name": response.json()["stored_path"]})
    return {k: data.get(k) for k in ("detected_format", "columns", "chat_column", "requires_manual_mapping", "warning")}


def wd2_training_status() -> dict:
    """Inspect the WD2 response-masked training worker and the final checkpoint status."""
    root = workspace()
    path = root / "reviewed-run" / "status.json"
    if not path.is_file():
        return {"phase": "not_started", "reviewed_dataset_present": (root / "reviewed/report.json").is_file()}
    return json.loads(path.read_text(encoding="utf8"))


async def start_reviewed_training() -> dict:
    """Start one bounded local Unsloth LoRA run on the verified WD2 split and save a merged checkpoint."""
    directory, _ = certified()
    root = workspace()
    python_bin = os.environ.get("WD2_UNSLOTH_PYTHON")
    if not python_bin or not Path(python_bin).is_file():
        raise ValueError("Set WD2_UNSLOTH_PYTHON to the installed Unsloth training interpreter")
    if (await training_status()).get("is_training_running"):
        raise ValueError("Studio already has an active training job")
    if (await studio_request("/api/export/status")).get("is_export_active"):
        raise ValueError("Studio already has an active export")
    run = root / "reviewed-run"
    # Atomic reservation across multiple stdio processes and Desktop HTTP requests.
    run.mkdir(exist_ok=False)
    update(run, {"phase": "starting"})
    try:
        with (run / "training.log").open("wb") as log:
            worker = subprocess.Popen([python_bin, str(Path(__file__).resolve()), "--worker",
                                       str(run), str(root / "base"), str(directory)],
                                      stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                      creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except OSError:
        update(run, {"phase": "failed_to_start"})
        raise
    return {"phase": "starting", "pid": worker.pid}


def update(run, state):
    tmp = run / "status.tmp"
    tmp.write_text(json.dumps(state), encoding="utf8")
    tmp.replace(run / "status.json")


if __name__ == "__main__" and len(sys.argv) == 5 and sys.argv[1] == "--worker":
    run, base, dataset = map(Path, sys.argv[2:])
    update(run, {"phase": "running", "pid": os.getpid()})
    try:
        environment = os.environ.copy()
        environment.setdefault("PYTHONUTF8", "1")
        environment.setdefault("HF_HUB_OFFLINE", "1")
        environment.setdefault("TORCHINDUCTOR_CACHE_DIR", str(run.parent / "inductor"))
        environment.setdefault("UNSLOTH_COMPILE_LOCATION", str(run.parent / "unsloth_compile"))
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("sft.py")),
                                 "--out", str(run / "model"), "--dataset", str(dataset),
                                 "--base", str(base), "--merge"], check=False, env=environment)
        update(run, {"phase": "completed" if result.returncode == 0 else "failed", "exit_code": result.returncode})
    except BaseException:
        update(run, {"phase": "failed"})
        raise
