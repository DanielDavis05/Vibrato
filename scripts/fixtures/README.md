# Disposable food fixture

`food-mutton-retention.script` overwrites a small region at the world origin, teleports and clears the player, and changes world settings. Use it only with a new disposable single-player world with cheats enabled. Never point it at a player's save.

Run the current build with `-Dvibrato.script=<absolute path>/food-mutton-retention.script`, `-Dvibrato.world=<new disposable world>`, and `-Dvibrato.autostart=false`. The world must allow the script's vanilla commands. Use `-Dvibrato.debugNavigation=true` to record movement and blocks. The script places the bot and eight sheep on an enclosed platform with one raw mutton, an iron sword, coal and a furnace, then requests `@food 24`.

Inspect `script-result.json` and the client log: a pass needs an actual completed food task, at least four cooked mutton in inventory, and no death or timeout. The initial raw mutton alone is insufficient. Repeated animal-task starts are not failures by themselves; distinguish kills, pickups, and cooking from a repeated already-satisfied target. A timeout, failure, death or missing inventory evidence is not a pass. The fixture passed on 2026-10-27: 50.533 seconds, four cooked mutton, full health. Setup uses creative mode until the platform exists, then switches to survival before supplying resources or starting the task. See `docs/GAMER_RECOVERY_AUDIT.md`.
