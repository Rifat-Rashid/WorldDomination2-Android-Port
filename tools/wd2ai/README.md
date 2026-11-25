# WD2 local AI development tools

Unsloth Desktop and Codex can use the same authoritative Java game harness. The bridge runs on the development computer. The Android game runs its own model through JNI and needs no connection to this server.

## Connect Unsloth Desktop

Start Desktop and check its **MCP servers** settings. From the repository root in PowerShell, run the following. The bridge environment is separate from the Python installation used by Desktop for training.

```powershell
$env:WD2_TRAINING_ROOT = "D:\wd2-training"
$env:WD2_UNSLOTH_PYTHON = "$HOME\.unsloth\studio\unsloth_studio\Scripts\python.exe"
python -m venv "$env:WD2_TRAINING_ROOT\bridge"
$bridge = "$env:WD2_TRAINING_ROOT\bridge\Scripts\python.exe"
& $bridge -m pip install -r tools/wd2ai/requirements-mcp.txt
& tools/wd2play/play.ps1 -Command build
& tools/wd2ai/start-desktop.ps1 -Python $bridge
```

The launcher starts a loopback-only HTTP service on port 8794. It registers **WD2 local AI** through the running Desktop backend after Desktop successfully probes the available tools. Re-run the launcher after reboot. The Windows user’s DPAPI-protected bearer is in `%LOCALAPPDATA%\WD2-AI`. Desktop stores the connection using its normal server settings. Set `WD2_STUDIO_URL` for a different loopback Studio address and `WD2_STUDIO_TOKEN` if your installation requires API authentication. Keep tokens in the environment.

The server uses Python MCP SDK 2.3.0. Modern clients use per-request metadata and `server/discover` from the July 2026 specification. Desktop versions with the earlier handshake can use the same server. Select **studio_health** for a read-only connection check. Desktop can ask for confirmation before invoking a tool. [MCP specification](https://modelcontextprotocol.io/specification/2026-07-28) · [Unsloth Desktop guide](https://unsloth.ai/docs/new/studio/start)

To use stdio from Codex, run the following from the checkout. This adds tools to the client’s configuration.

```powershell
$entry = (Resolve-Path tools/wd2ai/server.py).Path
codex mcp add wd2-unsloth --env "WD2_TRAINING_ROOT=$env:WD2_TRAINING_ROOT" --env "WD2_UNSLOTH_PYTHON=$env:WD2_UNSLOTH_PYTHON" -- $bridge $entry
```

## Tools

| Tool | Operation |
| --- | --- |
| `studio_health`, `training_hardware`, `training_status`, `export_status` | Read live Unsloth Desktop backend status. |
| `game_start`, `game_observe` | Start or reopen a durable episode. The actor sees its own assets, public observations, memory and legal ordered plans. |
| `game_choose` | Commit a listed plan for the specified decision ID and return real turn feedback. A retry of an old decision cannot advance the game. |
| `game_finish`, `game_replay` | Run the continuation after focal elimination or verify the recorded trajectory by re-execution. |
| `preview_reviewed_training` | Upload the verified training split and ask Studio to inspect the messages column. |
| `start_reviewed_training`, `wd2_training_status` | Start and inspect a bounded LoRA run using the installed Unsloth interpreter, the pinned base and response-only labels. Logs and checkpoints appear under `WD2_TRAINING_ROOT/reviewed-run`. The worker has its own status tool because it runs outside the Desktop job dashboard. |

## Collect and verify evidence

Use `tools/wd2play/README.md` to create separate episodes, submit teacher choices, verify replay, generate a genuine preterminal checkpoint and capture the Android end screen. Give each writer its own file. Assign seed families before training. Use the same family for any branch or teacher variant of an initial state. The exporter assigns the family to a stable train, validation or test partition. Reserve pilots used for debugging outside the final dataset.

`collect.py` also supports a local language-model teacher served by llama.cpp. It asks the model for a choice at each turn, submits it to Java, and supplies actual actor-visible feedback before the next choice. Set `WD2_TEACHER_URL` and optional `WD2_TEACHER_TOKEN` for the local model. The included recipe identifies the Qwen3.5-9B Q8_0 snapshot in the raw teacher identity. Resume by passing the same episode and configuration. Keep these teachers distinct from the manually played agents, the rule-based AI and the legal stochastic policy.

### Data dictionary

| Location | Fields and use |
| --- | --- |
| Episode header | Episode/family, seed, actor, leaders, difficulty, teacher, other policies, turn cap, decision-contract version, engine commit/build identity. Privileged provenance. |
| Decision | One observed turn and a complete ordered pair. `system` and `user` contain the exact deployment input; `candidates` maps each ID to actions, quantities, costs and targets. `selected` is the teacher target. |
| Audit | `decision_id`, `reason`, RNG-independent candidate mapping, all committed orders, transition, next observation, continuation orders, rejection attempts and terminal outcome permit independent review and replay. Hidden orders and outcomes stay outside the exported prompt. |
| Terminal evidence | Android receipt links the raw-log SHA-256 to the checkpoint, fixture, screenshot and instrumentation result. DEFEAT means the human was eliminated. Several AI nations may still be alive. |
| Independent review JSONL | `decision_id`, `row_sha256` (canonical sorted-key compact UTF-8 JSON), `reviewer`, `accepted` boolean and `why`. Append corrections. Evaluate from the information the actor had when it decided. |
| Export | Each split contains only three messages per row. The response is compact plan-ID JSON. `provenance.jsonl` links each row hash to raw evidence and its review. `report.json` records split hashes, counts, token range, coverage and exclusions. |

```powershell
$py = $env:WD2_UNSLOTH_PYTHON
$root = $env:WD2_TRAINING_ROOT
& $py tools/wd2ai/base_model.py "$root/base"
& $py tools/wd2ai/export_dataset.py --episodes "$root/episodes" --evidence "$root/android-evidence" --reviews "$root/reviews.jsonl" --base "$root/base" --out "$root/reviewed"
& $py tools/wd2ai/sft.py --base "$root/base" --dataset "$root/reviewed" --out "$root/experiment" --merge
```

The exporter requires 1,600 train, 200 validation and 200 test rows for a full run. `--partial` creates an explicitly incomplete export in a new directory. The training entry point checks the full split counts and hashes again. To verify model loading, label masking, CUDA training, checkpoint saving and sampling using a separate pilot, point `--dataset` at a directory with real pilot decisions in `train.jsonl` and pass `--smoke`. Smoke output has separate provenance and represents one optimizer step.

The pinned trainable checkpoint is Qwen/Qwen3-0.6B revision `c1899de289a04d12100db370d81485cdf75e47ca`. The input includes the official non-thinking generation prefix. Only the JSON response and EOS receive loss. Inputs beyond 2,048 tokens fail validation. The training job records its base and data hashes, visible GPU, mask example, metrics, adapter and tokenizer. With `--merge` it writes a separate standalone checkpoint ready for the pinned llama.cpp converter. The Android build accepts a development GGUF only with an exact size and checksum. See [Android runtime and provisioning](../../docs/qwen-runtime.md).

The verified Windows training stack used Python 3.12, Unsloth 2026.9.14, unsloth-zoo 2026.9.9, PyTorch 2.11.0+cu130, Transformers 5.5.0, TRL 0.23.1, PEFT 0.18.1, datasets 4.3.0 and bitsandbytes 0.50.2. Install the desktop environment separately from the MCP dependencies. Large weights, private trajectories and checkpoints belong outside ordinary Git history.

Run `export_gguf.py --run <training-run> --source <b7700-source-dir> --bin <b7700-binary-dir>` with the training interpreter to create standalone F16, Q8_0 and Q4_K_M exports and their byte counts and checksums. Use `tools/wd2play/score_choices.py` on the validation split before choosing a deployment quantization. The bundled APK option in the runtime guide makes that specific file usable offline from first launch.

```powershell
& $bridge -m unittest discover -s tools/wd2ai -p 'test_*.py'
```
