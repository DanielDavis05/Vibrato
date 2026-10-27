# Nether dependency recovery, 2026-10-27

This follows `NETHER_RUN_2026-10-27.md`. The original `human` save is not available on this machine; the historical session cannot be reproduced exactly here.

## Current-master baseline

Built detached `1e9fa30` against the existing sibling Baritone checkout. In a disposable survival copy of seed 424242, made a stone chamber at y61–64 with surface cover at y79, teleported the player to (140.5,62,20.5), and supplied a stone pickaxe and 64 cobblestone before requesting `@get oak_log 1`. Setup commands were confined to the disposable fixture. Navigation debug and a position recording were enabled.

The bot selected a surface trunk, then a nearby natural trunk at (126,73,14). It paced at the same ascent, fired three JUMP recoveries followed by two BACK_OFF_AND_REPATH recoveries, and cancelled the request after 109.384 seconds. This reproduces the cancellation and unproductive approach problem on current master, though it is not the original world or full Nether chain.

An earlier natural-cave smoke check, without the constructed chamber, completed the log request in 60.900 seconds. Neither result establishes universal reachability.

## Changes under validation

* Mining exclusions expire. Two related failures temporarily suppress connected vertical logs, without excluding adjacent ores or permanently poisoning global tracking.
* A persistent 45-second mining approach check requires a better best distance or confirmed excavation. Pacing, changing the target to another block in the same trunk, and navigation interruptions cannot keep renewing it.
* Surface-log collection can request a bounded escape after a failed approach from deep underground. Escape no longer recursively acquires a wooden pickaxe; it first tries an existing walkable route, then clears safe headroom and physically climbs. Failure is terminal for that escape attempt and is reported as failure by the director.
* Manual requests receive at most two scoped resource recoveries before cancellation, renewed by completed mining or a new root request. Director step abandonment keeps its existing scope.
* Recovery selects available cheap blocks from all 36 inventory slots and retains the interrupted parent's protection snapshot. With room, it can collect one reachable safe dirt block. An occupied cursor is preserved. Explicit placement no longer silently substitutes cobblestone or reports the misleading throwaway warning.

The existing development command `@test` has `escape`, `recovery`, and `recovery-protected` cases for exercising these paths. The recovery cases target the block immediately east and below the player; prepare an empty placement spot in a disposable world. The protected case models dirt protection being released by the interrupted parent before the delegate starts.

## Verification so far

The first fixed run (`f882222`) completed the same chamber log request in 130.382 seconds: the persistent check rejected the paced approach, escape changed from WALK to ASCEND after its 30-second walk limit, and the bot reached y80 and collected an oak log. The baseline cancelled at 109.384 seconds; completion took longer than that earlier cancellation, but the request succeeded.

A subsequent review tightened ascent completion: the bot must land before advancing a pillar step, retain the original rim height while digging, and distinguish a fresh escape attempt from a failed equal child. A walked exit at a lower altitude can complete after leaving the original column. These refinements are being live-tested.

`./gradlew :test :build`: 583 tests passed, no failures or skipped tests. Live validation of the changes is in progress; this file will be updated with outcomes and limitations.

Two later fixes change the sibling engine. `MovementHelper.java` now tests placement-face geometry independently of the next smoothed camera step; the actual crosshair still gates clicks. `MineProcess.java` adds `searchWorldForTracking`, retaining cached and loaded scanning while omitting mining-only eligibility filters. Vibrato's block tracker uses that method; actual mining still calls `WorldHelper.canBreak` and the existing strict mining search is unchanged.

These changes are in `/opt/Baritone/src/main/java/baritone/pathing/movement/MovementHelper.java` and `/opt/Baritone/src/main/java/baritone/process/MineProcess.java`. Existing engine working-tree edits are preserved. The updated engine is required for building this Vibrato revision; it is included in the packaged mod. Engine jar SHA-256: `4061f6ab410a86e826385fff8692f2c8108eb365a5566fdeb188bc53d2b9b59f`.


## Longer dependency run

The first Nether dependency run passed log collection, crafting table and furnace construction, and two-bucket crafting. It then requested a seventh iron ingot for ignition, dug down again, and got stuck retrieving coal from the cached furnace above. The mining-only scoped recovery could not handle that container branch, and the root was cancelled after 224.776 seconds. This was a real remaining failure, not a successful Nether run.

The follow-up batches iron for missing buckets plus ignition before crafting (7 ingots for two missing buckets without an igniter), and extends scoped recovery to a resource task's current container. A stalled container is deferred locally, retaining its cached contents; entering interaction range enables it again. Other containers and normal resource collection remain available. The global two-recovery limit still applies.


The first complete portal fixture reached buckets and ignition with the iron batched in one pass, then spent its remaining budget searching for fluids. Analysis found that the general tracker used mining search, discarding sources the liquid task protects from breaking. Its 300-second scripted budget expired; it did not enter the Nether. The tracking-specific engine path addresses that source discovery error. The next engine fixture and portal continuation are recorded separately, rather than counting this timed-out run as success.


Later surface checks confirmed a second tracking issue: a full cache of distant underground lava could skip the loaded-chunk scan. Tracking now always merges a fresh loaded scan before sorting/limiting; native mining retains its existing cache threshold. The engine adds `src/main/java/baritone/process/WorldScanPolicy.java` and `src/test/java/baritone/process/WorldScanPolicyTest.java`. The engine suite now has 53 passing tests; Vibrato retains 589 passing tests.

The portal task now gives its first lava scan a bounded 0.5-second grace instead of immediately wandering while the scan starts. Active breaking can equip an owned hotbar tool when a bucket or block is held, while leaving native tool choices intact when a tool is already equipped. The stable surface casting fixture is being rerun with these changes.
