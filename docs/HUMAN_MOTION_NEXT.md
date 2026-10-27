# Human motion: state and next steps

Written for the agent continuing this work on the main checkout. Read `docs/HUMAN_MOTION_PLAN.md` first for the design and the
trace format. This file is what has been learned since, what is broken, and what to do, in priority order.

Branch `human-motion` is ahead of `master` and builds; the full unit suite passes (about 510 tests). Nothing on it has been
validated in game except the recorder (it wrote real traces). Merge it into `master` only after step 1 passes.

Rules that still apply: do not edit `../Baritone` unless a step says so, and if you do, list every change in the commit message
and tell the user (that checkout is local only, not in git). Another Minecraft client may be running on the user's machine; do
not kill java processes you did not start. Use a copy of the save for experiments, never `run/saves/human`.

## What exists

| Piece | Where | Status |
|---|---|---|
| Recorder (`@record start <name> [human\|bot]`, `@record stop`) | `human/record/`, `mixins/MouseRecorderMixin`, `commands/RecordCommand` | Ran in game. Writes `run/traces/<name>-<source>.jsonl`. Keys come from the effective player input; `edge` is only sampled while grounded (unverified in game). |
| Key layer | `human/wire/*`, `mixins/KeyShapeMixin`, `human/motion/KeyShaper` | Ran in game (profile sampled, keys ON). Hesitation, strafe taps, jump timing, a one-shot W release before a 2+ block drop. |
| Camera | `human/HumanLook` | Ran in game. Submovements, overshoot, wind, sensitivity grid. |
| Fitting and discriminator | `scripts/human/` | Works on synthetic data. Real data so far: 8 human windows, 1 bot window, so no usable accuracy yet. |

Real recordings: `run/traces/walk1-*.jsonl` (older format, bot mostly crafting), `run/traces/run1-human.jsonl` (107 s) and
`run/traces/run1-bot.jsonl` (139 s, open-water route, key layer on). Analyse with
`python3 -I -B scripts/human/discriminate.py --human <human.jsonl> --bot <bot.jsonl>` and
`python3 -I -B scripts/human/fit.py <human.jsonl> -o human_profile.json`.

## Findings from run1 (the evidence behind the tasks below)

* Bot moved at 4.2 blocks/s while moving (p90 5.6); the human at 5.6 (p90 6.9). The human bunny-hops on flat ground
  (sprint-jump), Baritone does not. This is the largest remaining tell.
* Bot pressed forward 51 times in 139 s (human: 8), median release 1.4 ticks, 58 sprint restarts. In vanilla a W release cancels
  sprint. Suspected cause: `ContextBuilder` treated any drop over 0.5 blocks as a gap and `KeyShaper` released W with a 50% coin
  flip per tick. Both were changed (commit `b56e088`: drops need 2+ blocks, never while SPRINT is intended, one 1-2 tick event
  per approach). Not yet confirmed in game. Hesitation can still release W while sprinting (rate about 0.2% per tick, left on).
* The bot stood still for 321 ticks (12% of the run) in four stretches, the longest 165 ticks. Not vines: the world file shows
  open water at (140,62,21), (131,62,20), (124,62,20), (119,62,20), feet at y about 62.6, `ground=false`, bobbing at the surface.
  Baritone's next node was a plain traverse west and no MOVE_FORWARD was issued (forward released at tick 847, pressed again at
  1062; only jump about every 8 ticks). The human key layer passes keys through in fluid (`Bypass.java`), so it is not the shaper.
  Unknown whether the human camera (`HumanLook` replacing Baritone's aiming) is involved.
* `StuckDetector` fired twice (STATIONARY_WITH_INTENT, then a one-tick jump and replan) and never reached later ladder rungs. It
  did not classify the state as water drift although the feet were in water, and `distance3d` counts y, so surface bobbing can
  evade its 0.3 threshold.
* Separate crash (not from this branch's code): `StackOverflowError` in `Task.tick` (`Task.java:76`, about 2000 frames) while the
  bot ran autonomous crafting. The log shows one cycle repeating: `Doing stuff in crafting_table` -> `Collect Recipe Resources
  (wooden_axe)` -> `Crafting 2 sticks` -> `Mine And Collect dead_bush x999999` -> back to the crafting-table task, nesting deeper each
  pass. Crash report: `run/crash-reports/crash-2026-10-27_10.39.34-client.txt`.

## Steps

### 1. Experiments first (decide what is actually ours)
Build a dev-only scripted-run harness so the same route can be run unattended and compared:
* `-Dvibrato.script=<file>`: lines of `<delay seconds> <text>`. `@...` runs through the command executor as if typed, `/...` is a
  vanilla command, `exit` stops the client, `wait-idle <max s>` waits for no path and no task. Pure parser with unit tests, zero
  effect when the property is unset. Write `run/script-result.json` on exit. Document in `docs/smoke-test.md`.
* Copy `run/saves/human` to `run/saves/ab-water` and use `-Dvibrato.world=ab-water`. The route: from the first tick of
  `run1-bot.jsonl` west across the water to about (119, 62, 20).
* Run the same script four times and record each: stock (`-Dvibrato.human=off`), camera only (`-Dvibrato.humanKeys=off`), keys only
  (needs a `-Dvibrato.humanLook=off` switch in `Human.install`, add it), and everything on, with `-Dvibrato.debugKeys=true`.
  Compare per run: moving speed, forward releases, sprint restarts, stationary ticks, StuckDetector firings.
* Decision rule: if the water stall happens with `-Dvibrato.human=off` it is Baritone/nav, otherwise it is the camera or keys.
  If speed and stutter are fine with human off and bad with it on, use the `Keys stats` lines to find which component acts.

### 2. Water stall and stuck detection (after step 1 says where it comes from)
* `nav/StuckDetector`: use horizontal displacement when in water, climbing or airborne without gravity progress; keep 3D otherwise.
* In `NavSafetyChain`, set `MotionSample.inWater` from `isTouchingWater` (feet in water), not only when submerged.
* Add a ladder rung for stationary-with-intent in water: after the first jump, hold forward and sprint about 20 ticks while looking at
  the target, then a sidestep, then replan. Keep the existing rungs for other states. Unit tests for every change.
* If step 1 shows Baritone sends no forward in water: log in `MovementTraverse.updateState` near line 261 (the vine wait) and check
  whether it waits on a rotation target that `HumanLook` does not reach. That is a Baritone edit: tell the user.

### 3. Task-loop crash
Reproduce with `@get wooden_axe` (or the autonomous crafting path) on a fresh world. The cycle is a planning loop: the sticks step
re-enters the crafting-table task that is waiting on the sticks. Fix by detecting a task re-entering itself in the chain (cycle
guard in `Task.tick`/`TaskChain`) and by not collecting sticks via `Mine And Collect dead_bush` from inside the crafting task.
Add a unit test that builds the cycle and asserts a bounded depth, and log a clear error instead of recursing.

### 4. Sprint-jump on flat ground (the biggest human tell)
Baritone walks flat ground at plain sprint speed; humans hop. Add a hop layer: while the path is a straight sprint traverse on flat
ground with headroom, press JUMP on landing with a profile-sampled cadence and small timing jitter. Constraints: never when a
jump or ascend is planned in the next 3 nodes, in water, climbing, under a 2-high ceiling, near edges (use `ContextBuilder` gap),
or when the path turns more than about 20 degrees within 3 nodes. Put the pure logic in `human/motion/` with tests, wire it in
`human/wire/ShapingLayer`. Target: bot p90 speed within about 10% of the human's 6.9 blocks/s in a matching run.

### 5. Wire `PathOffset` (lateral wander)
It exists in `human/motion/PathOffset` and is tested but not used. It needs an aim-target offset, which needs a hook in Baritone's
`LookBehavior`/`RotationSmoother` (Baritone edit, tell the user) or, without touching Baritone, an offset applied inside
`HumanLook.smooth` to the desired yaw proportional to the OU offset and the corridor half-width the key layer already computes
(0 for bridging, one-wide tunnels, ledges).

### 6. Better recordings and acceptance
* Record both the intended (Baritone) and the shaped keys in the trace (new `ikey` events) so stutter can be attributed from a trace
  alone. Update `TraceEvent`, writer, reader, `features.py`.
* Record at least 4 minutes of actual movement per side on the same route; the discriminator warns below 20 windows per class.
* Acceptance for merging the whole feature: no regression in the existing nav smoke tests (`docs/smoke-test.md`), bot moving speed
  and forward-release counts close to the human's, no stalls longer than 2 s on the water route, discriminator held-out accuracy
  well below the synthetic 1.0 (target under about 0.65 on real data with at least 20 windows per class), full `./gradlew test` green.

### 7. Merging
Merge `master` into `human-motion` first, resolve, run the full suite, then merge `human-motion` into `master` and push both.
