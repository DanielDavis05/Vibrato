#!/usr/bin/env python3
"""Run a fleet of Vibrato bot clients against one offline-mode Minecraft server.

Subcommands: install, server, up, status, down. Offline mode only: bots use offline usernames and this script never
asks for or stores Microsoft credentials. See scripts/fleet/README.md.

Run with `uv run --with minecraft-launcher-lib scripts/fleet/launch.py ...`, or with plain Python 3.11+ once
minecraft-launcher-lib is installed. `server --dry-run` and `up --dry-run` work without it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any

if sys.version_info < (3, 11):
    sys.exit("launch.py needs Python 3.11 or newer")

REPO = Path(__file__).resolve().parents[2]
HOME = Path(os.environ.get("VIBRATO_FLEET_HOME", Path.home() / ".vibrato-fleet"))
MC_DIR = HOME / "minecraft"
MODS_DIR = HOME / "mods"
SERVER_DIR = HOME / "server"
BOTS_DIR = HOME / "bots"
LOG_DIR = HOME / "logs"
STATE_FILE = HOME / "fleet.json"

JAVA = os.environ.get("VIBRATO_JAVA", "java")
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MODRINTH_VERSIONS_URL = "https://api.modrinth.com/v2/project/fabric-api/version"
USER_AGENT = "vibrato-fleet-launcher"
MAX_PLAYERS = 32
STAGGER_SECONDS = 8
SERVER_HEAP = "2G"
DEFAULT_MEMORY = "1536M"
BOT_NAME = "Vibrato{:02d}"
DIFFICULTIES = ("peaceful", "easy", "normal", "hard")

# Read from gradle.properties so the fleet always matches the mod build.
GRADLE_PROPERTIES = dict(
    line.split("=", 1)
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines()
    if "=" in line and not line.lstrip().startswith("#")
)
MC_VERSION = GRADLE_PROPERTIES["minecraft_version"].strip()
LOADER_VERSION = GRADLE_PROPERTIES["loader_version"].strip()
FABRIC_API_VERSION = GRADLE_PROPERTIES["fabric_version"].strip()
FABRIC_PROFILE = f"fabric-loader-{LOADER_VERSION}-{MC_VERSION}"

# Minimal per-bot options.txt. Written only when absent, so a bot's own edits survive. Vibrato's PerformanceProfile
# applies the same low-resource values in-game as well.
OPTIONS_TEMPLATE = """onboardAccessibility:false
joinedFirstServer:true
skipMultiplayerWarning:true
tutorialStep:none
lang:en_us
pauseOnLostFocus:false
renderDistance:5
simulationDistance:5
maxFps:30
enableVsync:false
graphicsMode:0
particles:2
cloudRenderMode:0
entityShadows:false
bobView:false
biomeBlendRadius:0
guiScale:1
soundCategory_master:0.0
soundCategory_music:0.0
soundCategory_ambient:0.0
"""


def die(message: str, code: int = 1) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(code)


def require_mll() -> Any:
    try:
        import minecraft_launcher_lib
    except ImportError:
        die("minecraft-launcher-lib is not installed. Run with "
            "`uv run --with minecraft-launcher-lib scripts/fleet/launch.py ...` "
            "or `pip install minecraft-launcher-lib`.")
    return minecraft_launcher_lib


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def verify_digest(data: bytes, algorithm: str, expected: str | None) -> None:
    if expected is None:
        return
    actual = hashlib.new(algorithm, data).hexdigest()
    if actual != expected:
        die(f"{algorithm} mismatch: expected {expected}, got {actual}")


def download(url: str, dest: Path, algorithm: str | None = None, expected: str | None = None) -> None:
    data = fetch(url)
    if algorithm:
        verify_digest(data, algorithm, expected)
    dest.parent.mkdir(parents=True, exist_ok=True)
    temp = dest.with_name(dest.name + ".part")
    temp.write_bytes(data)
    temp.replace(dest)
    print(f"downloaded {dest} ({len(data)} bytes)")


def find_mod_jar() -> Path:
    candidates = [
        jar for jar in (REPO / "build" / "libs").glob("vibrato-*.jar")
        if not jar.name.endswith(("-sources.jar", "-dev.jar"))
    ]
    if not candidates:
        die("no built mod jar in build/libs. Run ./gradlew build -PbaritoneDist=... first.")
    return max(candidates, key=lambda jar: jar.stat().st_mtime)


def replace_jars(pattern: str, keep: Path) -> None:
    for old in MODS_DIR.glob(pattern):
        if old.name != keep.name:
            old.unlink()
    shutil.copy2(keep, MODS_DIR / keep.name)


def fabric_api_file() -> dict[str, Any]:
    query = urllib.parse.urlencode({
        "loaders": json.dumps(["fabric"]),
        "game_versions": json.dumps([MC_VERSION]),
    })
    versions = json.loads(fetch(f"{MODRINTH_VERSIONS_URL}?{query}"))
    match = next((v for v in versions if v["version_number"] == FABRIC_API_VERSION), None)
    if match is None:
        die(f"Modrinth has no fabric-api {FABRIC_API_VERSION} for Minecraft {MC_VERSION}")
    return next((f for f in match["files"] if f.get("primary")), match["files"][0])


def cmd_install(args: argparse.Namespace) -> int:
    mod_jar = find_mod_jar()
    if args.dry_run:
        print("[dry-run] nothing is downloaded, written or copied.")
        print(f"[dry-run] minecraft-launcher-lib: install Minecraft {MC_VERSION} into {MC_DIR}")
        print(f"[dry-run] minecraft-launcher-lib: install Fabric loader {LOADER_VERSION} into {MC_DIR}")
        print(f"[dry-run] copy {mod_jar} -> {MODS_DIR}/")
        print(f"[dry-run] download fabric-api {FABRIC_API_VERSION} from Modrinth -> {MODS_DIR}/")
        return 0

    mll = require_mll()
    print(f"installing Minecraft {MC_VERSION} into {MC_DIR}")
    mll.install.install_minecraft_version(MC_VERSION, str(MC_DIR))
    print(f"installing Fabric loader {LOADER_VERSION}")
    mll.fabric.install_fabric(MC_VERSION, str(MC_DIR), loader_version=LOADER_VERSION)

    MODS_DIR.mkdir(parents=True, exist_ok=True)
    replace_jars("vibrato-*.jar", mod_jar)
    api = fabric_api_file()
    api_name = Path(api["filename"]).name
    api_path = MODS_DIR / api_name
    download(api["url"], api_path, "sha512", api["hashes"]["sha512"])
    for old in MODS_DIR.glob("fabric-api-*.jar"):
        if old.name != api_name:
            old.unlink()
    print(f"installed {FABRIC_PROFILE} with mods in {MODS_DIR}")
    return 0


def cmd_server(args: argparse.Namespace) -> int:
    properties = {
        "online-mode": "false",
        "enforce-secure-profile": "false",
        "spawn-protection": "0",
        "difficulty": args.difficulty,
        "max-players": str(MAX_PLAYERS),
        "server-port": str(args.port),
        "motd": "Vibrato fleet",
        "view-distance": "8",
        "simulation-distance": "8",
        "white-list": "false",
        "level-name": "world",
    }
    eula_accepted = (SERVER_DIR / "eula.txt").exists() and "eula=true" in (SERVER_DIR / "eula.txt").read_text()
    command = [JAVA, f"-Xmx{SERVER_HEAP}", "-jar", "server.jar", "nogui"]

    if args.dry_run:
        print("[dry-run] nothing is downloaded, written or started.")
        print(f"[dry-run] fetch {MANIFEST_URL}, take the {MC_VERSION} server.jar, verify sha1, save to {SERVER_DIR}/")
        print(f"[dry-run] write {SERVER_DIR}/server.properties:")
        for key, value in properties.items():
            print(f"    {key}={value}")
        if args.accept_eula:
            print(f"[dry-run] write {SERVER_DIR}/eula.txt (eula=true)")
        print(f"[dry-run] cwd={SERVER_DIR} command: {shlex.join(command)}")
        return 0

    if not (args.accept_eula or eula_accepted):
        die("the Minecraft EULA has not been accepted. Read https://aka.ms/MinecraftEULA, then rerun with "
            "--accept-eula to write eula.txt.", code=2)

    SERVER_DIR.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(fetch(MANIFEST_URL))
    entry = next((v for v in manifest["versions"] if v["id"] == MC_VERSION), None)
    if entry is None:
        die(f"Mojang manifest has no version {MC_VERSION}")
    server = json.loads(fetch(entry["url"]))["downloads"]["server"]
    jar = SERVER_DIR / "server.jar"
    if not jar.exists() or hashlib.sha1(jar.read_bytes()).hexdigest() != server["sha1"]:
        download(server["url"], jar, "sha1", server["sha1"])

    if args.accept_eula:
        (SERVER_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (SERVER_DIR / "server.properties").write_text(
        "#Generated by scripts/fleet/launch.py\n"
        + "".join(f"{key}={value}\n" for key, value in properties.items()),
        encoding="utf-8",
    )
    print(f"starting the server in {SERVER_DIR} (Ctrl+C to stop)")
    return subprocess.call(command, cwd=SERVER_DIR)


def offline_uuid(name: str) -> str:
    """Same value as Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name), which vanilla uses offline."""
    digest = hashlib.md5(f"OfflinePlayer:{name}".encode("utf-8")).digest()
    return uuid.UUID(bytes=digest, version=3).hex


def bot_seed(name: str) -> int:
    return int.from_bytes(hashlib.sha256(name.encode("utf-8")).digest()[:8], "big") >> 1


def bot_options(name: str, bot_dir: Path, host: str, port: int, memory: str) -> dict[str, Any]:
    return {
        "username": name,
        "uuid": offline_uuid(name),
        "token": "0",
        "gameDirectory": str(bot_dir),
        "executablePath": JAVA,
        "launcherName": "vibrato-fleet",
        "launcherVersion": "1",
        "server": host,
        "port": str(port),
        "jvmArguments": [
            f"-Xmx{memory}",
            f"-Dvibrato.botId={name}",
            f"-Dvibrato.seed={bot_seed(name)}",
            "-Dvibrato.autostart=true",
            f"-Dvibrato.server={host}:{port}",
            "-Dvibrato.perf=low",
        ],
    }


def prepare_bot_dir(bot_dir: Path) -> None:
    bot_dir.mkdir(parents=True, exist_ok=True)
    options = bot_dir / "options.txt"
    if not options.exists():
        options.write_text(OPTIONS_TEMPLATE, encoding="utf-8")
    mods = bot_dir / "mods"
    if mods.is_symlink():
        if os.readlink(mods) != str(MODS_DIR):
            mods.unlink()
            mods.symlink_to(MODS_DIR, target_is_directory=True)
    elif mods.exists():
        die(f"{mods} is a real directory; move it aside so it can link to {MODS_DIR}")
    else:
        mods.symlink_to(MODS_DIR, target_is_directory=True)


def is_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def load_state() -> dict[str, Any]:
    if not STATE_FILE.exists():
        return {"bots": []}
    return json.loads(STATE_FILE.read_text(encoding="utf-8"))


def save_state(state: dict[str, Any]) -> None:
    HOME.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")


def base_command(mll: Any, options: dict[str, Any]) -> str:
    """The launcher's own argv for a bot, or a placeholder when minecraft-launcher-lib or the install is missing."""
    if mll is None:
        return "<minecraft_launcher_lib.command.get_minecraft_command(...)>"
    try:
        argv = mll.command.get_minecraft_command(FABRIC_PROFILE, str(MC_DIR), dict(options))
    except Exception as error:  # the Fabric profile is not installed yet
        return f"<get_minecraft_command failed: {error}; run install first>"
    return shlex.join(argv)


def cmd_up(args: argparse.Namespace) -> int:
    if not args.accept_offline:
        die("up starts offline-mode accounts only. Pass --accept-offline to confirm the server is offline-mode.")
    host, _, port_text = args.server.rpartition(":")
    if not host or not port_text.isdigit():
        die(f"--server must be host:port, got '{args.server}'")
    port = int(port_text)
    if not 1 <= args.count <= MAX_PLAYERS:
        die(f"-n must be between 1 and {MAX_PLAYERS} (the server's max-players)")
    if not re.fullmatch(r"\d+[MG]", args.memory):
        die(f"--memory must look like 1536M or 2G, got '{args.memory}'")

    state = load_state()
    if any(is_alive(bot["pid"]) for bot in state["bots"]):
        die("a fleet is already running. Stop it with `launch.py down` first.")

    if args.dry_run:
        mll = None
        try:
            import minecraft_launcher_lib
            mll = minecraft_launcher_lib
        except ImportError:
            pass
        print(f"[dry-run] {args.count} bots -> {host}:{port}, offline, stagger {args.stagger}s, heap {args.memory}")
        print("[dry-run] nothing is written or started.")
    else:
        mll = require_mll()
        if not (MC_DIR / "versions" / FABRIC_PROFILE / f"{FABRIC_PROFILE}.json").exists():
            die(f"{FABRIC_PROFILE} is not installed. Run `launch.py install` first.")
        if not any(MODS_DIR.glob("vibrato-*.jar")):
            die(f"no vibrato jar in {MODS_DIR}. Run `launch.py install` first.")
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        state = {"server": args.server, "bots": []}

    for index in range(args.count):
        name = BOT_NAME.format(index + 1)
        bot_dir = BOTS_DIR / name
        options = bot_options(name, bot_dir, host, port, args.memory)
        if args.dry_run:
            print(f"[dry-run] {name}: cwd={bot_dir}")
            print(f"[dry-run]   options.txt (if absent), mods -> {MODS_DIR}")
            print(f"[dry-run]   options={json.dumps(options)}")
            print(f"[dry-run]   command: {base_command(mll, options)}")
        else:
            prepare_bot_dir(bot_dir)
            log_path = LOG_DIR / f"{name}.log"
            with log_path.open("ab") as log:
                command = mll.command.get_minecraft_command(FABRIC_PROFILE, str(MC_DIR), options)
                process = subprocess.Popen(command, cwd=bot_dir, stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=True)
            state["bots"].append({"name": name, "pid": process.pid, "pgid": process.pid, "log": str(log_path)})
            save_state(state)
            print(f"started {name} (pid {process.pid}, log {log_path})")

        if index < args.count - 1 and args.stagger > 0:
            if args.dry_run:
                print(f"[dry-run] sleep {args.stagger}s")
            else:
                time.sleep(args.stagger)
    return 0


def cmd_status(_: argparse.Namespace) -> int:
    state = load_state()
    if not state["bots"]:
        print("no fleet running")
        return 0
    running = 0
    print(f"server {state.get('server', '?')}")
    for bot in state["bots"]:
        alive = is_alive(bot["pid"])
        running += alive
        print(f"{bot['name']:<12} pid={bot['pid']:<8} {'running' if alive else 'exited':<8} log={bot['log']}")
    print(f"{running}/{len(state['bots'])} running")
    return 0


def cmd_down(_: argparse.Namespace) -> int:
    state = load_state()
    bots = state["bots"]
    for bot in bots:
        if is_alive(bot["pid"]):
            try:
                os.killpg(bot["pgid"], signal.SIGTERM)
            except ProcessLookupError:
                pass
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline and any(is_alive(bot["pid"]) for bot in bots):
        time.sleep(0.5)
    for bot in bots:
        if is_alive(bot["pid"]):
            try:
                os.killpg(bot["pgid"], signal.SIGKILL)
            except ProcessLookupError:
                pass
            print(f"killed {bot['name']} after the 20 s grace period")
    STATE_FILE.unlink(missing_ok=True)
    print(f"stopped {len(bots)} bot(s)")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Vibrato fleet launcher (offline mode only).")
    commands = parser.add_subparsers(dest="command", required=True)

    install = commands.add_parser("install", help="install Minecraft, Fabric, the built mod and fabric-api")
    install.add_argument("--dry-run", action="store_true", help="print the steps without doing them")
    install.set_defaults(func=cmd_install)

    server = commands.add_parser("server", help="download the vanilla server and start it")
    server.add_argument("--accept-eula", action="store_true", help="write eula.txt (accepts the Mojang EULA)")
    server.add_argument("--difficulty", choices=DIFFICULTIES, default="easy")
    server.add_argument("--port", type=int, default=25565)
    server.add_argument("--dry-run", action="store_true")
    server.set_defaults(func=cmd_server)

    up = commands.add_parser("up", help="start N offline bot clients")
    up.add_argument("-n", "--count", type=int, default=10)
    up.add_argument("--server", required=True, metavar="HOST:PORT")
    up.add_argument("--accept-offline", action="store_true", help="confirm the server runs in offline mode")
    up.add_argument("--memory", default=DEFAULT_MEMORY, help="heap per client, e.g. 1536M")
    up.add_argument("--stagger", type=int, default=STAGGER_SECONDS, help="seconds between client starts")
    up.add_argument("--dry-run", action="store_true")
    up.set_defaults(func=cmd_up)

    status = commands.add_parser("status", help="show which bot clients are running")
    status.set_defaults(func=cmd_status)

    down = commands.add_parser("down", help="stop every bot client in the fleet")
    down.set_defaults(func=cmd_down)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
