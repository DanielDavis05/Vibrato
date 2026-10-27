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
