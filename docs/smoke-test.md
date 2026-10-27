# In-game smoke test

Starts one bot in a fixed-seed singleplayer world and lets the Director run its first activities.
It opens a game window, so only run it on a machine where that is acceptable.

Run from the repository root (or a worktree):

```bash
./gradlew runClient \
  -PgameProps="-Dvibrato.world=smoke -Dvibrato.worldSeed=424242 -Dvibrato.autostart=true -Dvibrato.botId=smoke1 -Dvibrato.seed=7 -Dvibrato.startDelay=8" \
  --args="--width 854 --height 480"
```

- `-PgameProps` is split on spaces and passed as JVM arguments to the client run.
- `vibrato.world` loads the singleplayer world with that name, or creates it (survival, normal difficulty, cheats off,
  structures on, seed from `vibrato.worldSeed`, default 424242). It is entered from the title screen, once per process.
- `vibrato.autostart=true` starts the Director `vibrato.startDelay` seconds after joining.
- Logs go to `run/logs/latest.log` under the project directory.

## Kill switch for navigation safety

Start the client with `-Dvibrato.nav=off` to disable the Never Stuck Navigation chain entirely: no unstuck jumps,
sidesteps, back-offs, abandons or underground escapes. The log then prints `Navigation safety: OFF` at startup. Any other
value (or none) leaves it on, and the log prints `Navigation safety: ON`. Waypoint tracking stays on either way.

## Movement and collection regressions

Use a disposable world with cheats enabled for deterministic fixtures. Disable autostart while checking a single command:

```bash
./gradlew runClient \
  -PgameProps="-Dvibrato.world=nav-polish-smoke -Dvibrato.autostart=false -Dvibrato.seed=7"
```

Prepare a clear stone platform with a two-block-deep, eleven-block-wide water lane. Wait for chunks to load before
using `/fill`. Keep these setup commands confined to the disposable world; normal automation does not teleport.

- With an empty inventory, run `@goto` to a point 25 blocks away across the water. It should swim, climb onto the dry
  bank and finish without repeatedly cancelling the route or forcing forward through turns.
- Give the player dirt and request an exact XYZ destination six blocks directly above its feet. Wait until the player
  lands, then check `@coords` and `@inventory dirt`. The task must place all six blocks and finish at the requested Y,
  rather than declaring success while the jump passes through the goal.
- Place eight exposed stone blocks next to a stone floor, give the player a pickaxe and run `@get cobblestone 8`.
  Mining and pickup should stay under one collection task, complete the requested count, and avoid unnecessarily
  removing the player's support when another reachable target exists.
- Under tree leaves or wooden shelter, `@play` should not immediately schedule an underground escape. In a stone
  cave, the surface check should still recognize overhead terrain.
- Type `@he`, use Tab, and submit `@help`; test `@play` persona completion and a normal `/` or `#` command afterward.
  Suggestions must remain local to Vibrato commands. Clicking a command reference should put editable text in chat.
- After `@play`, run `@stop` and wait beyond the 60-second restart interval. The Director should stay stopped until
  another `@play` or a new world session.

The unit suite covers repeated three-cell routes, legitimate return paths, vertical motion, productive mining,
interruption protection and local suggestion ranges. Run `./gradlew test build` against the sibling engine checkout.

## Navigation trace

Add `-Dvibrato.debugNavigation=true` to `gameProps` to log position, aim, block-breaking progress, forced engine inputs,
current goal and task chain four times per second. Local block breaks are logged with their positions too. This is
console-only and off by default. Use `-Dvibrato.human=off` for a stock-camera comparison of the same fixture.

## Validated on 2026-10-27

With human aim and navigation safety enabled in the isolated world above:

- A 25-block trip through the water lane finished in 8.959 seconds, including the exit onto dry ground.
- The vertical goal placed six dirt blocks in 2.809 seconds; after settling the player remained at the requested Y
  with 58 of the original 64 dirt remaining.
- Collecting eight cobblestone finished in 7.916 seconds and left the player at the original Y=131, without falling
  through the platform. Live nearby-block selection avoids the capped tracker cache hiding exposed targets.
- `@play builder` started autonomous play; `@stop` remained stopped for 77 seconds. Stopping a queued movement command
  prevented its following `coords` command from running.
- Local completion and grouped/clickable help were exercised in the running client.

These are controlled regression checks, rather than an exhaustive playthrough of every registered activity.

The same collection fixture with `vibrato.human=off` finished with eight cobblestone in 7.162 seconds; the human-camera
run took 7.916 seconds. The final `./gradlew test build` passed 351 tests with no failures, errors or skips.

## Normal survival follow-up, 2026-10-27

The subsequent watch used normal survival worlds with seeds 424242 and 1, cheats off, and ordinary inventory loss on
death. Seven sessions recorded about 49 minutes of client trace, including idle observations, autonomous generalist
and explorer plans, and manual resource/travel requests. The structured record is
`docs/survival-watch-2026-10-27.json`; capture hashes identify the accompanying evidence bundle.

The watch reproduced unprepared XP combat, worn-out tools falling back to aimless searching, interrupted wanders
resetting their origin and budget, recovery jumps during productive mining, and an indirect ocean search for sheep.
It also exposed a death/respawn being counted as successful travel. Fixes preserve task progress across interruptions,
require XP preparation, replace tools, recognize real mining work, choose dry bank and non-overlapping placement
targets, and prefer nearby grassland for terrestrial animals. Projectile predictions now use future trajectories and
evaluate each dodge candidate independently. Protected nested movement remains covered by interruption tests.

The natural river check crossed roughly 28 water blocks in two travel legs and exited onto the bank with full air.
Later ordinary requests completed a stone pickaxe, three wool, six wool, and a feather with full health. The last
land-selector correction uses a heightmap transmitted to clients; selection stability and that data contract have
regression tests. The final test/build run passed 399 tests, with no failures, errors or skips.

Earlier night sessions included combat deaths, and one bed/spawn attempt exceeded its budget after a long animal
search. These are recorded in the watch report. This sampled watch does not exhaust every activity or terrain.

### Scripted client runs

For unattended development runs, create a UTF-8 script with one `<delay seconds> <action>` per line. Delays are relative to the previous action; blank lines and `#` comments are ignored. The clock starts only after the player and world are loaded and no screen is open; later open screens do not pause it, so timeouts still fire. Actions beginning with `@` use Vibrato's command executor; `/` sends a vanilla command. `wait-idle <maximum seconds>` waits until there is neither an active user task nor an active or calculating path, then continues; a timeout is recorded and also continues. `exit` writes the result and stops the client. For example:

```text
0 /time set day
1 @goto 119 62 20
0 wait-idle 120
0 exit
```

Run with `./gradlew runClient -PgameProps="-Dvibrato.world=ab-water -Dvibrato.autostart=false -Dvibrato.script=/absolute/path/to/script.txt"`. Use a disposable copy of a save. Script execution is disabled when the property is unset. `run/script-result.json` is atomically replaced at script completion or client shutdown and includes command dispatch/completion/error and wait timeout statuses. Vanilla commands report only that they were sent, not whether the server accepted them. Movement metrics sample effective input and horizontal displacement per client tick: moving speed assumes 20 ticks/s, stationary means at most 0.005 blocks of horizontal displacement in a tick, and unavailable stuck-firing/task-restart counters are `null`. The result is a run-wide aggregate, so use separate runs for comparisons. A script without `exit` leaves the client running after its final action.

For movement comparisons, `-Dvibrato.humanHops=off` disables the extra sprint-hop policy. Hops also stop when the key layer is off or the upcoming path and surrounding clearance cannot be verified. The corridor-gated camera offset is visual only; Baritone still controls the actual path.

The script result also includes a one-second `timeline` (position, dimension, health, path status and current leaf task) and `taskOutcomes` (completed, cancelled or failed, including the failure reason). An idle wait or command dispatch does not establish task success; check the task outcome and world state. `@test cast` starts only an obsidian bucket cast three blocks east of the player, for disposable-world fixtures. It supplies no terrain or items.

Repeated starts of an opted-in placement or travel target are guarded at eight starts in ten seconds without observed progress. Interruptions and protected crafting/airborne work clear the observation window. `-Dvibrato.churnGuard=off` disables this guard for comparisons.
