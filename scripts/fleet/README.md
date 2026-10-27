# Vibrato fleet

Runs many Vibrato bots at once. The mod is client side, so each bot is its own Minecraft client process with the mod
loaded. `launch.py` installs the game, runs a local offline-mode server, and starts N clients with their own
identities. Offline mode only: bots use offline usernames (`Vibrato01`..`VibratoNN`) and the script never asks for or
stores Microsoft credentials.

Status: the launcher and the `FleetConfig`/`PerformanceProfile` code are untested in game. The dry-run output and the
unit tests are the only checks so far. Treat the first real run as a smoke test.

## Quick start

Prerequisites: Java 21 on `PATH` (or set `VIBRATO_JAVA`), Python 3.11+, and `uv` (or pip) for minecraft-launcher-lib.

```bash
# 1. Build the mod (from the repo root)
./gradlew build -PbaritoneDist=/path/to/baritone/dist

# 2. Install Minecraft 1.21.1, Fabric, the built mod and fabric-api into ~/.vibrato-fleet
uv run --with minecraft-launcher-lib scripts/fleet/launch.py install

# 3. Start the server (offline mode). Accepts the Mojang EULA only when you pass the flag.
uv run --with minecraft-launcher-lib scripts/fleet/launch.py server --accept-eula --difficulty easy

# 4. In another terminal: start 10 bots against it
uv run --with minecraft-launcher-lib scripts/fleet/launch.py up -n 10 --server localhost:25565 --accept-offline

# 5. Check and stop
uv run --with minecraft-launcher-lib scripts/fleet/launch.py status
uv run --with minecraft-launcher-lib scripts/fleet/launch.py down
```

Add `--dry-run` to `install`, `server` or `up` to print the steps and commands without downloading, writing or
starting anything. `server --dry-run` and `up --dry-run` work without minecraft-launcher-lib installed.

`up` staggers client starts 8 seconds apart (`--stagger`) so the bots do not all join at once.

## Layout

All state lives under `~/.vibrato-fleet` (override with `VIBRATO_FLEET_HOME`):

| Path | Contents |
|---|---|
| `minecraft/` | shared game install (libraries, assets, Fabric profile) |
| `mods/` | shared mods folder: `vibrato-*.jar` and `fabric-api-*.jar`, symlinked into every bot |
| `server/` | vanilla server jar, `server.properties`, `eula.txt` (only with `--accept-eula`) |
| `bots/VibratoNN/` | per-bot game directory: `options.txt` (written once), `mods` symlink, `vibrato/` config |
| `logs/VibratoNN.log` | stdout and stderr of each client |
| `fleet.json` | running bots and their process groups, used by `status` and `down` |

Each bot gets, as system properties: `-Dvibrato.botId`, `-Dvibrato.seed` (stable per name), `-Dvibrato.autostart=true`,
`-Dvibrato.server=host:port`, `-Dvibrato.perf=low`. `FleetConfig` reads them. A bot's working directory is its own
game directory, so its config files do not collide with the others.

## Resource sizing for 10 bots

These are planning estimates, not measurements. Measure on your machine before you rely on them.

- **RAM:** each client gets a 1536 MB heap (`--memory`) plus roughly 0.5 to 1 GB of native memory, so about 2 to 2.5 GB
  per client. Ten bots need about 20 to 25 GB, plus about 3 GB for the server (`SERVER_HEAP` in `launch.py`, 2 GB).
  Lower `--memory` to 1024M if the machine is short. Watch for GC thrashing in the logs.
- **CPU:** the low profile caps each client at 30 FPS, render distance 5, simulation distance 5, minimal particles and
  no shadows or clouds. Expect roughly 0.5 to 1 core per client with the game's render thread and Baritone's pathing
  thread. Ten bots want about 8 or more logical cores. The server needs about 2 cores.
- **GPU:** every client opens a window, so each one needs OpenGL. On a headless box use a virtual display (Xvfb) with
  Mesa software rendering. Software rendering costs more CPU per client than a real GPU.
- **Disk:** the shared install is about 1 GB, and each bot adds a world-state folder and logs.

## Troubleshooting

- **`minecraft-launcher-lib is not installed`**: run through `uv run --with minecraft-launcher-lib ...` or
  `pip install minecraft-launcher-lib`.
- **`the Minecraft EULA has not been accepted`**: read <https://aka.ms/MinecraftEULA> and rerun `server` with
  `--accept-eula`.
- **`no built mod jar in build/libs`**: run `./gradlew build -PbaritoneDist=...` first.
- **`fabric-api ... no version` / Modrinth errors**: the fabric-api version is read from `fabric_version` in
  `gradle.properties`. Check that Modrinth still lists that version for 1.21.1.
- **Clients exit at once**: read `~/.vibrato-fleet/logs/VibratoNN.log`. A common cause is a stale `fabric-api` jar or
  a different Fabric loader version from `gradle.properties`. Rerun `install`.
- **`a fleet is already running`**: run `down` first. `status` shows what `fleet.json` tracks.
- **A client connects but does not join**: check that the server runs with `online-mode=false`, and that the port in
  `--server` matches `server-port` in `server.properties`.
- **Bots all act at the same moment**: the start delay is derived from each bot's seed (5 to 25 seconds) and
  `-Dvibrato.startDelay` overrides it. The `up` stagger only controls when the clients launch.
- **Options reset between runs**: each bot's `options.txt` is written only when missing. Delete the file to pick up
  the template again.
