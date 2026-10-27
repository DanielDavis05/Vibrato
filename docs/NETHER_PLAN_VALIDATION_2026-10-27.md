# Nether plan implementation and validation, 2026-10-27

Follow-up to `NETHER_FIX_PLAN.md`, starting at `4e48840`. Changes are on `master`; no Baritone source was changed in this follow-up. The independent sibling engine remains locally modified from earlier work. The engine dependency used here has SHA-256 `4061f6ab410a86e826385fff8692f2c8108eb365a5566fdeb188bc53d2b9b59f`.

## Changes

- `89d35cb`: task failures propagate after child cleanup. User requests report one named failure and cancel after the tick unwinds; the Director records a failed step and proceeds. An opt-in guard detects eight starts of the same class and immutable objective in ten seconds. Placement and block travel opt in. Actual block changes, interruptions, crafting grids, handled screens and protected actions clear the observation window. Merely moving does not reset it. Recovery is limited to an eligible leaf willing to recover, with a bounded root/step budget; otherwise the failure propagates. Use `-Dvibrato.churnGuard=off` for comparison. Tasks whose equality cannot reliably identify an objective remain outside this guard.
- `5b366bb`: obsidian casts count the missing cells of the six-block scaffold and acquire only the missing usable cobblestone. Main-inventory, accepted, unprotected, solid, non-falling blocks count; cursor and offhand do not. Counting and both placement paths use the same cast material filter. A placement with no actual usable material reports failure before launching an empty builder.
- `6696af6`: MLG input requires the intended item in the selected main hand; an asynchronous equip request alone is insufficient. Water use is forbidden in ultrawarm dimensions, including configured fallback items. Itemless emergency steering remains available. An internal fall summary records estimated start/end height, health, attempted use and subsequently observed water. Repeated target/HIT chat was removed. Entity tracker collections now return snapshots under the refresh lock, fixing a crash reproduced during the baseline descent.
- `2dc7c9c`: scripted results include a one-second position/leaf/dimension/health timeline and separate completed/cancelled/failed task outcomes. `wait-idle` alone is not a success assertion. `@test cast` starts an isolated cast three blocks east of the player without supplying items or terrain.
- `311fb91`: preserve a matching crafted block on the cursor before closing the crafting screen; allow a bounded inventory handoff wait without starting the builder. Successful completion events capture the finished task before disabling the runner clears it.
- `06ba199`: a finished resource child can still have a forced pickup descendant. Wait until it actually detaches and releases item protection before recounting scaffold materials.

`structure{}` is an intentional sentinel meaning “use a suitable throwaway”; its empty printed requested array does not by itself demonstrate missing materials. The actual inventory and protection state determine availability.

## Reproductions that informed the fixes

The baseline ran unmodified `4e48840` from an empty inventory in a new normal-difficulty survival world, seed 424242, cheats off. It gathered tools, iron and fuel, made buckets and descended into a cave. About four minutes after the request, it crashed with `ConcurrentModificationException` in `MobDefenseChain.hasVisibleRangedDanger`: an entity tracker refresh changed the live list while the caller iterated it. Returning snapshots under the same lock fixes the collection ownership problem. A later fall ended in water; the old HIT text alone cannot establish a bucket clutch.

An intermediate full run exposed a premature missing-furnace error immediately after the crafting output click. This became a clear failure rather than a loop, but it was an incorrect failure: the furnace was still in a cursor/container handoff. The cursor fix addresses that transition. The next cast-only test collected five cobblestone but initially rejected it because a forced pickup child still owned resource protection. The scheduler regression test covers retaining that child, waiting for its stop, then recounting on the following tick.

## Controlled cast results

Each disposable survival fixture supplies a stone pickaxe and filled water/lava buckets on a stable platform. The cast target is `(143,80,20)`; its floor exists and **five** scaffold positions are missing. The source save was copied and left untouched. Cheats and peaceful difficulty are fixture setup only.

| Initial cobblestone | Requested resource target | Newly mined blocks | Cast duration | Minimum health | Result |
|---|---|---|---|---|---|
| 0 | 5 total | 5 | 14.730 s | 20 | Completed |
| 2 | 5 total | 3 | 10.339 s | 20 | Completed |
| 20 | None | 0 | 5.911 s | 20 | Completed; 15 cobblestone left |

Completion is the cast task's obsidian-and-water-cleared predicate, recorded as a completed task outcome. Each placement/travel target started once in these runs; no empty-builder alternation or navigation recovery occurred. The largest gap between logged task state events was one second.

## Survival and regression verification

The fresh normal-difficulty survival watch on `06ba199` started empty, with cheats off and no supplied gear. It crafted tools, smelted seven iron, made two buckets and an igniter, collected water and found a portal site/lava goal. It **failed**: a skeleton killed the player after 5:07 of the task. It respawned and resumed gathering prerequisites; the observer stopped the request afterward (6:32 total), so this is not an unattended pass or proof of reliable Nether entry. No lit portal was reached. The full-run acceptance remains open.

The watch exposed both buckets becoming water buckets. The exact second click was not captured, but the actual inventory transition was. `257bfeb` guards a resumed fluid task against interacting after its filled-bucket goal is already in the main inventory, handles cursor output, and corrects absolute empty-bucket acquisition counts. A separate disposable test then completed water collection in 0.066 s and lava collection in 5.450 s using the original two empty buckets: exactly one water bucket and one lava bucket remained, and no new bucket was crafted.

Movement and fall checks on disposable terrain:

| Check | Observation |
|---|---|
| Water lane, 25-block trip | Completed in 8.996 s, full health. |
| Six-block pillar | Completed in 3.408 s; 58 of 64 dirt remained and player settled at target Y. |
| Dry-floor fall, water bucket | 20-block fall, health 20→20; `waterAttempted=true`, `waterObserved=true`, ended in water. |
| Fall without clutch item | Existing-water landing, health 20→20; no water attempt or claimed observed placement. |
| Play then stop | Director started a `collect_milk` step; remained stopped for 65 s with no restart. |
| Mining/pickup in a shallow hollow platform | Initial run timed out at 90 s with six cobblestone: failed path calculations kept resetting the movement check. |
| Same mining fixture after `1fd4081` | Completed eight cobblestone in 105.173 s, full health; two stationary drop approaches were rejected after their bounded windows, then mining/pickup finished. |

The pickup watchdog measures 45 seconds of active time without progress toward one drop, independently of repeated path calculation. Genuine approach, acquired items and mining progress reset its budget; protected work and interruptions pause it. Rejection applies to that entity, allowing resource selection to continue. This bounds the reproduced silent stall but does not make this difficult fixture fast, or fix every pursuit/calculation loop.

The food loop in the newly supplied gamer log has a separate confirmed cause: `CollectFoodTask` requested a fixed absolute inventory count of one raw drop. After the first item, later same-species tasks could already be satisfied. `fdb4712` requests an additional drop relative to inventory and bounds food-stage placement retries; `220d230` retains bounded animal reconsideration and protected-child detachment. The controlled `@food 24` fixture passed in 50.533 s with four cooked mutton and full health, starting with only one raw mutton. See `GAMER_RECOVERY_AUDIT.md` for its scope and remaining validation.

## Scope and evidence

The original `human` save is still absent. New fixed-seed survival worlds and copies of the existing development save were used; differing spawn positions mean the fresh survival runs are not strict same-terrain A/B comparisons. No original player save or `/opt/traces` input was modified. Normal survival scripts contain only recording, task, observation and stop commands; fixture scripts explicitly supply terrain/items and teleport.

The planner still treats verified water landings as legitimate descent routes. This review does not claim that every route avoids all water/lava. Native Baritone water-bucket fall planning checks a hotbar bucket and the literal Nether dimension; its handling of custom ultrawarm dimensions was not changed. Vibrato's actual emergency water use checks `ultrawarm` at the point of use. An observed source at an attempted target is evidence of water there, not proof the click caused it; hard process crashes cannot emit a final fall summary.

Raw logs remain local because they contain machine paths and player details. The attached local evidence bundle contains logs, scripts, structured results, trace captures and hashes; only redacted summaries belong in Git.
