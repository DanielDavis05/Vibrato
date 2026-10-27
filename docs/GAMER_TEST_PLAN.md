# `@gamer` test campaign: parallel instances, rounds, and what "works" means

Architecture update: the testing system must live in a separate project. [External testing specification](EXTERNAL_TESTING_SPEC.md) defines that boundary and supersedes the infrastructure placement proposed below. This file remains useful for historical failures and long-term reliability targets.

Written for the agent (or agents) who will build the test harness and run the rounds. Companion docs: `docs/NETHER_FIX_PLAN.md` (the
Nether-specific analysis), `docs/NETHER_FIX_VALIDATION_2026-10-27.md` (what upstream already fixed and tested), `docs/smoke-test.md` (the script
runner and single-instance smoke tests), `scripts/fleet/README.md` (an untested multi-client launcher).

`@gamer` runs `BeatMinecraft2Task` (see `commands/GamerCommand`). Fixing failures one at a time from a single hand-driven run has not converged:
each run gets further and then fails somewhere new. The plan is to replace that with a repeatable campaign that finds many failures per round
across many seeds, then fixes them in batches, then re-runs.

## 1. Definition of "works"

A run is a **pass** for a stage when the stage's goal is reached in the stage's time budget with no death, no crash and no manual input. A run
is a **fail** if it ends in any other way, including: a task failure that ends the user task, a stuck-ladder abandon, a stall (no change of the
innermost task or of position for 60 s), a client crash, a death, or hitting the time budget.

The campaign target, in order (do not skip ahead; a later target is meaningless until the earlier one is solid):
1. Stage tests (section 4) pass at least 95% over at least 20 seeded runs each.
2. `@gamer` reaches **Nether entry** from a fresh survival world in at most 15 minutes in at least 90% of 20 runs.
3. Each later milestone (fortress/blaze rods, pearls, stronghold, End) in the same way, one at a time.
4. A final soak: the full `@gamer` goal for N parallel runs of at least 60 minutes game time each, and the failure classes recorded.

"Nothing failed in my one run" is not evidence. Pass rates need counts: say "17 of 20", not "works".

## 2. What the latest `@gamer` run showed (2026-10-27, 16:20-16:25)

Task `Beating the Game.` ran 4:48 and then ended with `Task failed: PlaceBlockTask: No usable block to place at 19, 55, 15 (requested
[furnace])` followed by `Stopped`. The condensed, redacted log is `docs/nether-logs/gamer-run-2026-10-27-1620.txt` (task starts, chat, stuck and failure lines only; no player names,
paths or ids). Reading of the log (nothing here is reproduced):

| # | What the log shows | Reading |
|---|---|---|
| G1 | 16:20:30-16:23:15 gear stage (iron sword, 2 iron pickaxes) completed, including about 70 s in a furnace wait: `Picking up from container ... iron_ingot x 8` every 10 s with `Cursor stack is held for too long` six times (16:21:33-16:22:43). | Works, but the bot stands at the furnace re-opening it every 10 s instead of doing other work. Not necessarily a bug; a cost to measure. |
| G2 | 16:23:15-16:25:13 food stage (`Collect 220.0 units of food`): about 49 `Collect ... SheepEntity [mutton x 1]` starts, with `Kill sheep`, `Pickup mutton`, `Wander for 64 blocks` cycling, plus two `Unstuck JUMP (STATIONARY_WITH_INTENT)` (16:24:22, 16:25:11). | Churn: the same small loop for about 90 s. Unclear whether drops were collected. This is the same shape as the Nether runs' task churn (`NETHER_FIX_PLAN.md` step 4). |
| G3 | 16:25:13-16:25:19 cooking: furnace placed at (23,65,13), beef moved in, needed fuel, walked to coal, fell about 9 blocks (MLG clutch at 16:25:16), crafted a second furnace and tried to place it at (19,55,15). | The place failed 1 s after `Receiving output` for the furnace craft. Hypothesis: the crafted furnace was still on the cursor or not yet in the inventory the placement checks. Not verified. |
| G4 | The failure of one placement leaf ended the whole user task (`Stopped`). | A leaf failure should be retried or handed to a recovery task, not end `@gamer`. Upstream commit `89d35cb` "Propagate task failures..." appears to have introduced this propagation; whether it should end the top task is a design question to settle in round 1. |

Which commits that run contained is not known. `311fb91` "Preserve crafted placement items through the inventory handoff" and `06ba199` "Wait for
protected pickup completion ..." look aimed at G3 but may have been built after the run. Round 0 starts by recording the git SHA and jar hash with
every run so this question never comes up again.

## 3. Infrastructure to build (round 0)

Machine on 2026-10-27: 24 logical cores, 62 GB RAM (about 47 GB available with the user's session open), `DISPLAY=:0` set, `Xvfb` not installed
(`Xorg` and `Xwayland` are). The user's own desktop and possibly their own Minecraft client are running on the same machine.

### 3.1 One isolated instance per run
* One game directory per instance under `~/.vibrato-tests/<campaign>/<run-id>/` (outside the repo, so raw logs are never committed). It holds its
  own `options.txt`, `logs/`, `saves/`, `traces/`, `config/`, mods symlink and `script-result.json`. `./gradlew runClient` cannot be used for
  parallel instances because it shares `run/`; launch the built mod through the same Fabric launch that `scripts/fleet/launch.py` installs, or
  extend it. `scripts/fleet` is untested in game; treat getting one instance to run end to end under it as the first deliverable.
* Singleplayer world per instance, created from a fixed seed (`-Dvibrato.world=<name> -Dvibrato.worldSeed=<seed>`), or copied from a saved
  template for a stage test. Never run on `run/saves/human` or any save the user plays on.
* Unique per instance: `-Dvibrato.botId`, `-Dvibrato.seed` (the human-profile and shaping RNG), window, port if a server is used. Record all of them.
* Scripted start and finish: `-Dvibrato.script=<file>` (see `docs/smoke-test.md`) with `@gamer` or a stage command, a per-stage `wait-idle`, and
  `exit`. `script-result.json` is the machine-readable outcome. Extend it so every run also records: git SHA, jar sha256, seed, stage reached,
  per-stage time, deaths, stuck-ladder firings, task restarts (the counters currently `null`), failure messages, and the last 50 task starts.
* Display: either a virtual display per batch (`Xvfb` needs installing, ask the user first) or the existing X session with small windows. Either
  way instances must start at low priority (`nice`) and the harness must only ever kill process groups it started. Do not kill other `java`.
* Resource sizing: start at 4 instances, then 6, then 8; measure per-instance RAM and CPU and tick rate before going higher. Use
  `scripts/fleet/README.md`'s low profile (render distance 5, 30 fps) as the baseline. If client TPS falls below 18 in any run the run is
  invalid for timing metrics (mark it `overloaded`, do not count it as a bot failure).

### 3.2 Watchdog and classification
A supervisor process per batch, with no game logic, that:
* Kills a run on: stall (60 s with no new task start and no position change, from the per-second navigation evidence), client death, a hard
  time budget per stage, or `exit`. Records which one fired.
* On run end, copies logs and results into the run directory and writes a one-line summary. Classifies the run: `pass`, `fail:<class>` with the
  class from this fixed list, or `invalid:<reason>`:
  `task-failure`, `stuck-abandon`, `stall`, `death`, `crash`, `timeout`, `churn` (same leaf task started more than 8 times in 10 s), `overloaded`.
* A churn check at the log level so the Nether and food loops above are caught automatically.

### 3.3 Log handling
* Raw logs stay in `~/.vibrato-tests/` (local only). Only summaries and **redacted** excerpts go in git, under `docs/test-rounds/`.
* Redaction script (write it once, reuse it): strip player names (`Player\d+`), home paths, UUIDs, IPs, audio device names, e-mail addresses,
  and any token-looking strings, keep task lines and coordinates. Scan the output with a grep for `/home`, `@`, `token`, `password`, `uuid` before
  every commit. This matches what was done for `docs/nether-logs/`.

## 4. Test matrix

**Seeds:** a fixed list of at least 10 world seeds chosen for variety (forest, plains, desert, mountains, ocean-adjacent, swamp, spawn near water,
spawn near a village, spawn near caves/ravine). Keep the list in the repo so rounds are comparable. Add a seed to the list whenever a failure
needs a specific terrain to reproduce.

**Stage tests** (each starts from a prepared state so a failure in one stage does not hide the next; each has a time budget and a goal):

| Stage | Start | Goal | Notes |
|---|---|---|---|
| wood | fresh spawn | 16 logs, crafting table, wooden pickaxe | the log-trunk failure from `NETHER_RUN_2026-10-27.md` |
| stone | wooden pickaxe | stone pickaxe, furnace, 20 cobblestone | |
| iron | stone tools | iron pickaxe x2, sword (the G1 path) | furnace wait behaviour |
| food | iron gear | 220 food units cooked (the G2/G3 path) | animals, smelting, fuel, drops |
| water-lava | iron gear + empty buckets | water and lava buckets | |
| portal | gear + buckets | lit portal, enter the Nether (`NETHER_FIX_VALIDATION` has a surface fixture) | |
| descent | surface | reach y=-50 and return alive | falls, MLG, cave escapes |
| full-to-nether | fresh spawn | Nether entry | the first end-to-end target |
| full-to-fortress, -to-end | per milestone | per milestone | only after full-to-nether is solid |

Each stage is run on every seed in the list; a batch is `stages x seeds` runs split across the available instances.

**Controls** (to separate Vibrato bugs from the engine): run the same stage once with the motion layers off (`-Dvibrato.human=off`) and once with
`-Dvibrato.nav=off` for any seed that fails. A failure that disappears under a control points at that layer.

## 5. Rounds

Each round is a fixed procedure; write the result to `docs/test-rounds/ROUND_<n>.md` (summary and redacted excerpts only).

1. **Prepare:** pull `master`, build, record SHA and jar hash, confirm the tree is clean. Run the unit tests (`./gradlew test`) first.
2. **Run:** the full matrix with as many instances as the sizing step allows. Do not edit code while a batch runs.
3. **Collect and classify:** the supervisor's summaries into one table (stage x seed x outcome) plus a failure-class histogram.
4. **Triage:** group failures by root cause, not by symptom (for example G2 churn and the log-trunk retargeting are probably one cause). Each group
   gets an issue entry: failing runs, first bad log line, hypothesis marked as hypothesis, and a reproduction (seed plus script).
5. **Fix:** one agent per issue group, each in its **own git worktree** off the current `master`, to avoid the shared-checkout problems seen on
   2026-10-27 (a dirty tree and a diverged `master`). Each fix needs a unit test, the reproduction run passing, and a note of what it could break.
6. **Integrate:** a single integrator merges fixes in small commits, runs `./gradlew test`, and pushes. No force pushes, no rewriting history.
7. **Re-run:** the same matrix, same seeds. A fix counts only if its failing runs now pass and nothing else regressed. Record new failures as
   new issue groups.
8. **Stop rule:** done with a target when three consecutive rounds meet its pass-rate threshold and a round introduces no new failure class.
   If a round gets worse, find which merge did it before going on.

**Round 0** is the harness itself: one instance end to end, then 4, then 6, with the redaction script and supervisor. Its output is a baseline
matrix on `master` with no code fixes, which is the number every later round is measured against.

## 6. Delegation

* Harness and supervisor, the results schema, the redaction script: a Sonnet-class agent (it touches launch and process handling).
* Log triage, table building, redaction scans, the seed-list research: Haiku-class agents with a fixed output format.
* Fixes: one Sonnet-class agent per issue group, each in its own worktree, each told to report the exact files it changed.
* The integrator is a single agent or the user; only it pushes. Pull before every round; never leave a long-lived dirty checkout.

## 7. Seed issues for round 1

From `NETHER_FIX_PLAN.md` steps 4-6 and the table in section 2:
* Task-churn detector (same leaf restarted repeatedly) at the task-chain level, with a per-component kill switch.
* Should a leaf placement failure end `@gamer`? Decide a retry-then-recover policy.
* Furnace wait: do other work or wait without re-opening the furnace every 10 s.
* Fall handling: MLG attempts, the fall start and landing outcome logged, no deaths in the descent stage.
* Whether the log-trunk and obsidian-cast failures from earlier runs still reproduce on current `master` (they may already be fixed).

## 8. Needs the user

* Installing `Xvfb` (a package install) if a virtual display is wanted, and how many instances may run alongside their own session.
* Approval to run `uv`/`pip` for `minecraft-launcher-lib` if the fleet launcher is used.
* Whether runs may use the user's GPU/desktop session or must stay hidden.
