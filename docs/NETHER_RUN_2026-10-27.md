# Nether run, 2026-10-27 12:18: what happened

Written from `run/logs/latest.log` and `debug.log` of the 12:16-12:21 session in the `human` world, plus one screenshot
(`run/screenshots/2026-10-27_12.18.53.png`). That session ran a build from before the commits now at the head of `master`
(`f7f9e60` .. `1286e70`), so some of this may already be changed. Nothing here was reproduced; it is a reading of the logs.

## Summary

The bot was given `Going to dimension: NETHER` with an empty inventory, spent about 3 minutes working down the dependency chain,
got stuck on one step (collecting logs for planks), and the stuck detector abandoned the whole task. No crash, no exception.
Nothing in the log points at the human-motion layers. It looks like a task-planning and navigation problem.

## Timeline

| Time | Event |
|---|---|
| 12:17:08 | `Getting to block (0,0,0)` completes. |
| 12:18:36 | `Getting to block (0,100,0)` path found (41 blocks) and then replaced. |
| 12:18:38 | User task set: `Going to dimension: NETHER`. Chain: Construct Nether Portal -> 2 buckets -> 6 iron ingots -> furnace (materials, fuel, crafting table) -> 4 planks -> logs. |
| 12:18:39-12:19:14 | Iron stage works: raw iron mined at (2,7,3), (3,7,4); some blocks at (3,3,8), (3,2,8), (4,2,8) fail once with "unreachable" and are blacklisted, then it moves on. |
| 12:19:14-12:19:21 | Coal collected (fuel). |
| 12:19:21 | Needs a crafting table, so planks, so logs. Target: a tree trunk at x=-4, z=0. |
| 12:19:21-12:21:11 | `Destroy block at -4,63,0` fails ("Failed to mine block. Suggesting it may be unreachable."), then the same for y=64, 65, 66, 67, 68 (about 6-12 s each), then a block at (3,63,-10). Baritone logs "cost coefficient is greater than three ... pretty terrible" for the 32-37 block path to the trunk. |
| 12:19:31 | `Unstuck JUMP (STATIONARY_WITH_INTENT)` |
| 12:19:51, 12:20:11, 12:20:31 | `Unstuck BACK_OFF_AND_REPATH`, `SIDESTEP_LEFT`, `SIDESTEP_RIGHT` (all `OSCILLATING`) |
| 12:20:51 | `Unstuck PLACE_STEP_BLOCK`: `PlaceBlockTask` logs "Failed to find throwaway block". |
| 12:21:11 | `BACK_OFF_AND_REPATH` again. |
| 12:21:31 | `Unstuck ABANDON_TASK (OSCILLATING)`, task stopped, "User task cancelled after 2 minutes and 52". |

Every `Chain Interrupted: User Tasks by Never Stuck Navigation Chain` restarts the Nether chain from the top (the same ~15 `Task START` lines
repeat), which is expected for that mechanism but means each rung costs a full re-plan.

## Findings

1. **The "cancelled" message is our own stuck ladder, not the user.** `UnstuckLadder` ended at `ABANDON_TASK`.
2. **Likely Vibrato bug: retargeting the next log in the same trunk.** After each failed log the mining task picks the block above it, six times in a
   row, instead of treating the trunk (or its approach) as unreachable. The retargeting is what the detector sees as `OSCILLATING`. A short blacklist
   per block does not help when the replacement is a neighbour with the same reachability problem.
3. **Possibly a Vibrato bug, unconfirmed: "Failed to find throwaway block".** The hotbar in the 12:18:53 screenshot shows what look like dirt and
   cobblestone stacks (about 10, 12, 51, 10 items). The message is logged at `PlaceBlockTask.java:189` (path as of the commit this was written
   against). I did not read the code or check the inventory at 12:20:51, so I cannot say whether the lookup is wrong or the items were gone.
4. **Not determined: why the trunk was unreachable.** The screenshot is dark and the bot appears to be underground near the origin (stone at
   y 2-9), while the logs are at y 63-68. No position was logged, and `run/traces` has no recording for this session. The "cost coefficient >
   three" warning means Baritone found a very expensive path, not that none existed.
5. **Motion layers: nothing in this log implicates them.** I did not check the key-layer or camera debug output for this session
   (`-Dvibrato.debugKeys` was not confirmed on).

## Suggested next steps

* Reproduce: fresh copy of the `human` save, empty inventory, `@get oak_log` first, then the Nether task, with
  `-Dvibrato.debugNavigation=true`, and record position with `@record start`.
* If the trunk is reachable on the surface, the bot being underground is the story (needs a way up, or a different log source).
  If it is reachable and still fails, look at the mining task's approach goal for a tall target column.
* Make log collection give up on a whole connected trunk (or a region) after a few failures, instead of one block at a time.
* Read `PlaceBlockTask` around line 189 and check how the throwaway candidates are chosen against the hotbar/inventory.
* Re-run on current `master` first: the commits after this session (`f7f9e60` bounded search pauses and protected water recovery,
  `ff4aeec` script timeouts through crafting) may change the behaviour.
