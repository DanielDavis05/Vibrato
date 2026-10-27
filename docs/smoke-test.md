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
