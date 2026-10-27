# Nether recovery fixes and validation — 2026-10-27

The reported underground log loop is fixed in the controlled reproduction. The final cave request completed in 33.200 seconds without navigation recovery; the original current-master baseline cancelled after 109.384 seconds. A separate surface fixture collected fluids, cast and lit a portal, and entered the Nether in 89.835 seconds with full health.

Work is unified on `master`. The original `human` save was not available on this machine. These results use disposable survival copies of seed 424242, with peaceful difficulty and commands confined to fixture preparation. They are individual runs, not an exact reproduction of the unavailable terrain. Movement recordings in `/opt/traces` are for comparing human and bot movement; the save is needed to repeat the exact terrain.

## Results

| Check | Observed result |
| --- | --- |
| Baseline underground `@get oak_log 1`, `1e9fa30` | Paced at an ascent, fired six navigation recoveries, cancelled after 109.384 s. |
| First bounded-recovery implementation, `f882222` | Escaped and collected a log in 130.382 s. |
| Direct engine placement fix, `e816bfe` | Collected a log in 32.706 s, no navigation recoveries. |
| Final code, `ebef8c9`, same cave setup and bot configuration | Collected a log in 33.200 s, no navigation recoveries. |
| Independent dirt-ceiling escape, no pickaxe | Climbed from y61 to y80, waited for a grounded landing; completed in 70.497 s. |
| Water above the escape column | Reported bounded failure after its walk attempt; did not dig into the water. |
| Final non-hotbar recovery placement | Dirt from slot 27 placed in 0.816 s; cobblestone from slot 28 placed in 0.031 s. Server block assertions passed; saved inventory retained all eight protected dirt and seven cobblestone. |
| Native bridge across a five-block gap | Completed in 3.753 s, used placement, finished grounded at y80 with full health. No navigation recovery. |
| Nether prerequisites | Gathered wood, made a table and furnace, smelted seven iron in one pass, crafted two buckets and ignition. |
| Final surface portal, `ebef8c9` | Filled buckets from nearby pools, cast all ten frame blocks, lit the portal and entered `minecraft:the_nether` in 89.835 s. Saved health 20; no navigation recovery. One bounded cast reposition occurred. |

`./gradlew :test :build`: **594 tests passed**, no failures or skipped tests.

Sibling engine `./gradlew :test :fabric:build -Pavailable_loaders=fabric`: **53 tests passed**, build succeeded.

## Changes

* Connected vertical logs share temporary approach failures. A persistent 45-second check requires an improved best distance or confirmed excavation; pacing and interruptions cannot renew it indefinitely. Nearby ores remain independent.
* Deep surface-log failures can request a bounded escape. Escape avoids recursive wooden-pick acquisition, retains its progress through interruptions, checks liquids/falling blocks, physically ascends and waits for landing at the rim. A fresh attempt replaces a failed instance. A walked exit at a lower altitude can finish legitimately.
* Before cancelling a manual root request, navigation can ask its deepest resource dependency to try another approach. Two scoped retries are available per root, renewed by completed mining or a new root. Director abandonment retains its step scope.
* Stalled resource containers are deferred locally without deleting their cached contents. Entering interaction range makes them eligible again. Portal iron is gathered for missing buckets and ignition together.
* Recovery selects unprotected materials from all 36 main inventory slots and retains the interrupted task's protection snapshot. It can gather one safe adjacent dirt block and preserves an occupied cursor. It equips its material before planning placement.
* Reachable placement now uses geometric face selection, human camera easing, actual held-item and cursor checks, crouching, collision checks and an exact support-face crosshair check before a throttled native interaction. Movement, obstruction handling and airborne placement still use the native builder.
* Construction retains a valid actual-inventory material when Baritone's candidate list is temporarily empty. Completed cast placement/removal counts as progress before deciding to wander.
* General tracking no longer applies mining-only breakability filters to fluid sources and other interaction targets. Actual mining still uses `WorldHelper.canBreak` and the strict native mining search. Tracking refreshes loaded terrain even with a full cache.
* Capped global results can still omit a nearby fluid source. Bucket collection therefore checks loaded sources within 16 blocks first, caching this local query for half a second. Portal lake selection seeds its normal flood/size check from a local source. Debug fluid traces distinguish raw, cached and direct local results.
* Trimmed cache indexes can be repaired on reinsertion. Target heuristics now use the planner's vertical direction and floor behavior at negative heights. Active mining can equip an owned hotbar tool if a bucket or block is held, while retaining native choices when a tool is already equipped.

## Engine checkout and build

The sibling checkout remains in `/opt/Baritone`; existing modifications were preserved. This iteration changed:

* `src/main/java/baritone/pathing/movement/MovementHelper.java`
* `src/main/java/baritone/process/MineProcess.java`
* `src/main/java/baritone/process/WorldScanPolicy.java`
* `src/test/java/baritone/process/WorldScanPolicyTest.java`

The movement change tests a placement face at the intended geometric rotation when Vibrato's smoother is installed, so the camera can acquire its target. Real crosshair alignment still gates clicks. Native mining keeps its original search constraints and cache threshold.

The updated engine is required to build this revision and is bundled in the packaged mod. Engine jar SHA-256:

`4061f6ab410a86e826385fff8692f2c8108eb365a5566fdeb188bc53d2b9b59f`

A complete engine working-tree source snapshot accompanies the jar. It can be overlaid onto the base checkout recorded in its `SNAPSHOT.json`; it excludes Git metadata and build output. Engine Git publication remains deferred as requested.

## Reproduction and limits

The evidence bundle contains the exact setup scripts, logs, input/position traces, saved-player summaries and a video of the final cave run. Its `run_case.py` copies `survival-fixed-20261027`, prepares the chamber and supplies a stone pickaxe and 64 cobblestone before the log request. The chamber has cover at y79 and an underground room at y61–64; the player starts at (140.5,62,20.5). The baseline and final cave runs use the same bot ID and seed.

`surface_portal.py` prepares a larger stable surface, water and lava pools, then supplies two empty buckets, ignition, a pickaxe and 64 cobblestone. Collection, casting, clearing, lighting and entry are performed by the task. Prerequisite acquisition was checked separately; the final surface test does not claim an uninterrupted empty-inventory Nether run.

Earlier unsuccessful runs are retained as failure history. They exposed the extra iron/fuel trip, inaccessible furnace retrieval, invisible/capped source search, and upper support placement. A timed-out or cancelled run is not counted as successful merely because `wait-idle` returned.

Escape remains bounded when tools, scaffold materials or a safe route are unavailable. The original `human` terrain and a full uninterrupted empty-inventory Nether run remain useful follow-up checks.
