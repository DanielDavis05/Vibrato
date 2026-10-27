# Nether task: fix plan

Written for the agent continuing work on `master`. Evidence is from two in-game runs of `@get`-style `Going to dimension: NETHER` on the
`human` test world on 2026-10-27, read from `run/logs/latest.log` and `debug.log`. Run A (12:18) is written up in
`docs/NETHER_RUN_2026-10-27.md`. Run B (15:52-15:56) is summarised below. Nothing was reproduced or fixed yet; every cause below is a reading of
the logs unless it says "confirmed in code".

Goal: the Nether task either makes real progress from an empty inventory or fails fast with a clear reason. It must never spend minutes in a
silent loop. Rules from `docs/HUMAN_MOTION_NEXT.md` still apply: do not edit `../Baritone` unless a step says so (and tell the user if you do),
do not kill java processes you did not start, run experiments on a copy of the save, never on `run/saves/human`.

## Status after the upstream fixes (read this first)

This plan was written against runs from before commits `f882222` .. `80fd041` on `master`. `docs/NETHER_FIX_VALIDATION_2026-10-27.md`
describes what those commits changed and tested. Reading it against this plan:

| Plan step | State |
|---|---|
| Run A, log loop underground | Reported fixed in a controlled reproduction (`f882222`, `4e1a7a7`, `e816bfe`); not re-run on the original `human` save, which was not available to that agent. |
| 2. Empty placeable-block list | Addressed per the validation doc: construction keeps a valid actual-inventory material when Baritone's candidate list is empty (`8145f35`); recovery equips its material first (`7d7db4e`). Not verified by me. |
| 3. Cast materials | Partly: the validation run supplied 64 cobblestone, so "ask for cobblestone when none" is still unchecked. Completed cast placement now counts as progress. |
| 4. Task churn guard | Not mentioned upstream. Still open. |
| 5. Descent safety | Not mentioned upstream. Still open. |
| 6. Full run | The validation doc says an uninterrupted empty-inventory Nether run is still a follow-up. Still open. |

Before doing anything below, check the state of each row on current `master`; treat the rest of this file as the original analysis.

## Evidence

Condensed, redacted log excerpts (task starts, chat, stuck and failure lines; no player names, paths or ids) are in
`docs/nether-logs/run-a-2026-10-27-1218.txt` and `docs/nether-logs/run-b-2026-10-27-1553.txt`. The full logs stay local in `run/logs/`
(git-ignored); do not commit them, they contain machine details.

**Run A (12:18, older build): stuck collecting logs.** Iron and coal worked. The planks step targeted a tree trunk at x=-4, z=0, failed with
"Failed to mine block ... unreachable" on each block y=63..68 in turn, then the stuck ladder reached `ABANDON_TASK` after 2:52. Details in
`NETHER_RUN_2026-10-27.md`.

**Run B (15:52-15:56, newer build): got much further, then looped at the lava lake.**
* Worked: iron mined and smelted (7 ingots), crafting table and furnace crafted and placed, bucket crafted, flint collected, water bucket and
  lava bucket collected, descent to a lava lake at (64,-55,16) with MLG clutches on the way down (`Epic gaemer moment`, several `HIT`).
* Failed: `PlaceObsidianBucketTask` at (64,-55,16) ("Clearing space around lava", then "Positioning player before lava"). From 15:55:54 to
  15:56:34 the log alternates once per second between `Task START: <Place structure{} at 64, -56, 16>` and `Task START: <Getting to block
  (63,-54,16)>`, each time followed by four `[Baritone] Done building`. Note the structure is `structure{}`: **an empty set of allowed blocks**.
  It then moved to `(63,-55,16)`, printed "Failed to place, wandering timeout" at 15:56:41, and the user stopped it at 15:56:48 (3:44 total).
* The stuck detector only fired `JUMP (STATIONARY_WITH_INTENT)` twice (15:55:17, 15:55:37) and never escalated, because the bot was not
  stationary or oscillating in the position sense: it was churning between two tasks.

**Common thread (a hypothesis, not proven):** both runs hit a `PlaceBlockTask` that cannot find a block to place. Run A logged "Failed to find
throwaway block" (`PlaceBlockTask.java:189` when this was written) with dirt and cobblestone visibly in the hotbar. Run B built an empty
`structure{}`. Both suggest the candidate-block list used for placing is empty or filtered wrongly (confirmed only that the message exists in code).

## Steps

Delegation: steps 1-4 are task-logic changes, give them to a Sonnet-class agent. The tests and the log lines can go to a Haiku-class agent once the
design is fixed. Commit each step separately with the unit tests green (`./gradlew test`).

### 1. Reproduce (before changing anything)
* Check `docs/smoke-test.md` and the script runner on `master` (`ff4aeec` mentions script timeouts) for how to run a scripted job unattended. If it
  can run a Vibrato command, script: `@get`-equivalent Nether task from an empty inventory on a copy of the `human` save
  (`-Dvibrato.world=<copy>`), with `-Dvibrato.debugNavigation=true`.
* Record: the run result, the leaf task over time, and the player position every second. If there is no way to log position, add it to the
  script runner's result file first.
* Also run a short pass that gives the player a pickaxe, 20 cobblestone and a lava bucket at (64,-55,16) and starts only the obsidian cast.
  That isolates run B's loop from the long dependency chain. Use `/give` and `/tp` in the script.

### 2. Find why the placeable-block list is empty (confirmed message in code, cause unknown)
* Read `PlaceBlockTask` (the "Failed to find throwaway block" site) and `PlaceStructureBlockTask`, and find where the accepted-blocks set is
  built. Check whether an empty set is expected when the player has no throwaway blocks, and why `structure{}` prints empty.
* Compare against the inventory at the moment of failure (log it: slot contents, selected hotbar slot, and the candidate list).
* Fix the cause. Add a unit test that builds the inventory from run A (dirt and cobblestone in the hotbar) and asserts a block is found.

### 3. Make the obsidian cast ask for its materials
* `PlaceObsidianBucketTask` builds `CAST_FRAME` (6 blocks) by placing blocks but never checks it has any. Before the build loop, require enough
  placeable blocks (at least the number of missing frame positions) and otherwise return `TaskCatalogue.getItemTask(...)` for a cheap
  placeable (cobblestone), the way it already does for the lava bucket and water bucket.
* Any placement task that cannot place anything must report failure to its parent immediately, not run the Baritone build with an empty set
  (which finishes in one tick and leaves the task unsatisfied).
* Unit test: with no placeable blocks the task returns an acquire task, not a `PlaceStructureBlockTask`.

### 4. Stop silent task churn (generic guard, fixes both runs)
Both runs show the same shape: a child task flips between two or more leaves (run A: next log in the same trunk, then the Nether chain restart;
run B: place structure vs get to block) without the position-based stuck detector noticing.
* Add a churn detector at the task-chain level: if the same leaf task (same class and same target) is started more than N times in M seconds
  (suggest 8 in 10 s, tune from logs), treat it as failed: blacklist that target for a while, log one clear line, and let the parent choose another
  strategy. Make it fail open (if unsure, do nothing) and add a per-task kill switch, for example `-Dvibrato.churnGuard=off`.
* For log collection specifically (run A): after K failures on blocks of the same connected trunk, blacklist the whole trunk, not only the one block.
* Unit tests with a fake task that alternates between two children; assert the guard trips within the bound and does not trip on normal task
  sequences.

### 5. Descent safety review (watch item, not a known bug)
Run B descended from y about 68 to y=-55 with several MLG clutches. It survived, but check that the lava-lake search and the path down avoid water
and lava, and that MLG is only attempted with a water bucket in hand. Add a debug line with the fall start y and the outcome so any death can be
read from the log.

### 6. Verify
* Re-run step 1's full Nether task from an empty inventory. Acceptance: no leaf task started more than 8 times in 10 s, no stretch over 60 s
  without a new Vibrato state change, and either a lit portal or a single clear failure message naming the step.
* Re-run the existing smoke tests in `docs/smoke-test.md` and the full `./gradlew test`.
* Attach the run logs to the commit message or a new `docs/` note (what was run, what changed, what is still open).

## Not covered / open

* Why the log trunk in run A was unreachable (the bot appeared to be underground, no position logged). Step 1's position log will answer this.
* Whether the human-motion layers (camera, key shaper, sprint-hops) contributed to either failure. Nothing in the logs points at them, but
  `-Dvibrato.debugKeys` was not confirmed on in either run. If a loop reproduces, run it once with `-Dvibrato.human=off` to rule them in or out.
