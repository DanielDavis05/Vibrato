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

`./gradlew :test :build`: 583 tests passed, no failures or skipped tests. Live validation of the changes is in progress; this file will be updated with outcomes and limitations.

No sibling Baritone source changes were made for this fix.
