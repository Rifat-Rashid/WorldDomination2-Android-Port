# Headless WD2 play and replay

The Java CLI compiles the Android game's platform-independent rules directly. It can assign one AI seat to a teacher, run the remaining seats, and replay an append-only episode log from its initial setup and recorded selections. It requires Java 11 or newer, PowerShell, and Git.

## Play an episode

Run these commands from the repository root. Choose a new log and an episode and family identifier before starting. Assign related initial configurations to the same family, including replay branches and policy variants.

```powershell
& tools/wd2play/play.ps1 -Command build
$episode = 'analysis/wd2play/teacher-a-81001.jsonl'
& tools/wd2play/play.ps1 -Command init -Log $episode -Episode teacher-a-81001 `
    -Family opening-81001 -Seed 81001 -Difficulty 2 -Actor 1 -Leaders '3,10' `
    -Teacher teacher-a -Policy baseline -HumanPolicy pressure `
    -Preference 'Develop an economy and maintain enough stock to attack on time.'
& tools/wd2play/play.ps1 -Command observe -Log $episode
& tools/wd2play/play.ps1 -Command choose -Log $episode -Plan P03 `
    -ExpectDecision teacher-a-81001:0:1 -Reason 'Explain the concrete choice from this turn.'
```

`init` emits the first observation. `observe` returns the current actor-filtered observation and exact candidate mapping. The sample ID `P03` is illustrative. After every choice, check `transition` for actual settlement feedback and `outcome` for the match status, then inspect the next observation. Send `-ExpectDecision` with the `decision_id` from that observation to detect a stale choice. If the teacher seat is eliminated while the match continues, run `-Command continue -Log $episode` to finish the other seats using the source AI. `-MaxTurns` is a diagnostic cap. A cap records `truncated:true`, which excludes the episode from training approvals.

`-Leaders` accepts one to four comma-separated source leader IDs from 0 through 18. `-Actor` is the occupied AI seat, numbered from 1, and `-Difficulty` takes 0 for Easy, 1 for Normal, or 2 for Hard. The human seat follows `pressure`, `development`, or `stochastic`. The other AI seats follow the existing planner with `baseline`, or sample legal candidates with `stochastic`. Each policy observes only what its interface supplies. The existing planner retains its original behavior.

Assign one writer to each episode. The CLI locks the log during replay and settlement. For repeated commands, `-Command stream -Log $episode` keeps the Java process open. Read one JSON object per input line. Commands are `observe`, `status`, `continue`, and `quit`. For a choice, write `choose`, ID, reason, and optional expected decision ID separated by tabs. `observe` and `choose` can also restore a stopped session from disk.

## Verify an episode

```powershell
& tools/wd2play/play.ps1 -Command replay -Log $episode
& tools/wd2play/play.ps1 -Command checkpoint -Log $episode -At 24 `
    -Out analysis/wd2play/teacher-a-81001-turn24.wd2s
```

Replay re-executes each round and compares the regenerated audit row with the saved row. A naturally completed episode ends in `VICTORY` when all rival nations have been eliminated, or `DEFEAT` when the human nation has been eliminated. Defeat does not identify a winner when several AI nations survive. Checkpoint export validates the file through the same save codec used by Android and requires an ongoing state.

`-Command compare -Log $episode` runs two fresh games from the same initial configuration, replacing the teacher with the existing AI and with legal stochastic selection. Each policy plays through the same human and rival policy setup. It emits matched outcomes without appending scripted choices to teacher logs.

For an Android end-screen receipt, set `JAVA_HOME` and `ANDROID_HOME` for the project's Android SDK and build the isolated instrumentation app. Install both APKs on the same emulator or attached device.

```powershell
& ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest `
    '-Pwd2ApplicationId=com.example.worlddomination.replayqa' '-Pwd2DeviceQa=true'
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python tools/wd2play/android-replay.py --serial emulator-5556 `
    --out analysis/wd2play/android-evidence $episode
```

The runner loads an ongoing checkpoint and applies the recorded human diplomacy answers, human orders, and opponent selections through the rules engine. It lets the game's battle playback and outcome presentation finish, checks the result and score for each seat against the log, then captures the displayed end screen and writes a receipt with file hashes. The test app uses save slot 3. The `.replayqa` application ID isolates it from normal saves.

## Evaluate model policies

`evaluate.py` runs the existing AI, legal stochastic selection, stock Qwen, the selected unquantized merged F16 GGUF, and the actual deployment GGUF on the same configurations. Provide a UTF-8 JSON manifest with `schema: 1`, `purpose: "held_out_evaluation"`, and an `episodes` list. An episode supplies `episode`, `family`, `seed`, `difficulty`, `actor`, `leaders`, `policy` for the other AI seats, `human_policy`, and an optional diagnostic `max_turns`. Freeze the manifest and save its SHA-256 before choosing a checkpoint on validation data. Every family in a held-out evaluation must appear in the frozen 1,600/200/200 dataset report as a test family.

```powershell
python tools/wd2play/evaluate.py --manifest analysis/locked-games.json `
    --manifest-sha256 <locked-sha256> --dataset-report analysis/dataset/report.json `
    --server <pinned-llama-server.exe> --stock <stock.gguf> `
    --merged-f16 <selected-merged-f16.gguf> --deployment-gguf <selected-deployment.gguf> `
    --out analysis/wd2play/held-out-results
```

The local server renders the model's non-thinking template, uses the Android context and output limits, constrains generation to available IDs, and uses greedy sampling with the per-request cache disabled. Focal requests and their exact committed decision records can be replayed. A timeout, context overflow, or invalid selection falls back to the existing AI, with the reason recorded separately from model successes. Use `--smoke` and a separate `development_smoke` manifest while checking the pipeline. An explicit `--smoke-server-commit` permits a different local binary for this check. Smoke runs permit `--policies` to select a subset and should use families outside all training, validation, and test splits.

## Data dictionary

The audit file is UTF-8 JSONL. Version 1 of its schema contains the following record types.

| Record | Fields and meaning |
| --- | --- |
| `episode` | `episode`, `family`, `seed`, `difficulty`, `actor`, explicit seat policies, leader IDs, teacher and strategic preference, `max_turns`, Git `engine_commit`, and `decision_contract`. New version 2 episodes also record `engine_source_sha256`, a SHA-256 digest of the sorted used source filenames and each file's SHA-256. Early version 1 pilot logs identify the base commit and have no compiled-source fingerprint. |
| `decision` | `decision_id` is `episode:turn:actor`. `turn` is zero-based before confirmation. `system` and `user` are exact model messages. `candidates` maps the shuffled `Pnn` identifiers to the ordered pairs. `selected` and `reason` record the teacher's choice. `human_answers`, `human_selected`, and `stochastic_selected` are privileged replay data. `transition` records actor-visible feedback. `outcome` records the current status and scores at terminal. Version 2 includes `committed_orders` for all actors and `next_observation` before its next planning budget reservations. |
| `rejection` | The attempted choice is preserved with the current `decision_id`, intended ID if provided, reason, and failure. A rejection does not advance the simulation. |
| `continuation` | Records actions after the focal actor is eliminated, using the original AI and the declared human policy. Exclude these rows from teacher data. |
| `terminal` | Final `outcome` with `terminal`, `truncated`, turn number, surviving seats, final scores for normal terminal outcomes, and the actual reason. The Android evidence receipt is separate and links the full log by hash. |
| `evaluation` | Focal selection or original-AI fallback during an evaluation. A blank `selected` value means the original planner provided the orders. The accompanying request log records source, fallback reason, output, and timing. Exclude evaluation rows from teacher data. |

The policy user message contains `own` funds, starting planning budget, city populations in millions, resource income, influence, research, stock, spy readiness, prepared carrier and known contamination. `rivals` has visible city populations, survival, the actor's regard, shared ceasefires, and publicly prepared carriers. `rival_private` marks private fields unknown. `memory` carries the actor's previous orders and last known attacker, bounded combat feedback in version 2, and a strategic preference. The policy never receives the audit copy of other seats' orders, seed, engine events after its choice, or final outcome.

Candidate action fields include `reserve` for current budget reservation, `spend` for a production category and its integer allocation, `tech` for the technologies to acquire, and attack or diplomacy parameters where applicable. `target` and `city` are zero-based game seat and city indices. A prepared carrier either has `prepare:true` or specifies a payload and city or a `strikes` list of `[target,city,payload]`. A diplomacy `offer` has an actual payment `amount` at settlement even when AI reservation is zero. Two queued actions do not produce new funds, stock, research, or launch readiness for the next slot during planning.
