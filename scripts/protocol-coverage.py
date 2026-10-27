#!/usr/bin/env python3
"""Run disposable, loopback-only protocol fixtures using the real Minecraft client.

Requires Java 21 and a working DISPLAY (for example Xvfb). Captures retain actual
task attribution; the separate packetCoverageReport gate checks completeness.
"""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import time
import json

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODES = ["login_query", "login_cookie", "login_disconnect", "login_encrypt",
                 "config_common", "config_cookie", "config_disconnect", "config_pack",
                 "config_transfer", "play_transfer", "play_disconnect", "play_session_negative", "normal"]


def properties(directory, port):
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "eula.txt").write_text("eula=true\n")
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "enable-command-block=true\nspawn-protection=0\nenable-query=false\n"
        "enable-rcon=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\n"
        "generate-structures=false\ndifficulty=peaceful\ngamemode=creative\n"
        "accepts-transfers=true\nview-distance=3\nsimulation-distance=3\n")


def start(command, log, env):
    handle = log.open("w")
    process = subprocess.Popen(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                               stdout=handle, stderr=subprocess.STDOUT, start_new_session=True)
    return process, handle


def stop(process):
    if process and process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=25)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


def ready(process, log):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Fixture exited: {log}")
        if log.exists() and 'For help, type "help"' in log.read_text(errors="replace"):
            return
        time.sleep(0.5)
    raise TimeoutError(f"Fixture startup timed out: {log}")


def interrupted(*_):
    raise KeyboardInterrupt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modes", default=",".join(DEFAULT_MODES))
    parser.add_argument("--logs", type=Path, default=ROOT / "run/vibrato/protocol-logs")
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--display", default=os.environ.get("DISPLAY", ":99"))
    parser.add_argument("--steps", help="Override normal suite: fixture:name,client:name,...")
    parser.add_argument("--verify", action="store_true", help="Require all 224 packets from this run's captures")
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    modes = args.modes.split(",")
    if any(mode not in DEFAULT_MODES for mode in modes):
        parser.error("Unknown fixture mode")
    args.logs.mkdir(parents=True, exist_ok=True)
    properties(ROOT / "run-protocol-fixture", 25576)
    properties(ROOT / "run-protocol-transfer", 25566)
    env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE="1", ALSOFT_DRIVERS="null")
    captures = []
    for mode in modes:
        server = destination = client = None
        handles = []
        try:
            if mode == "config_transfer":
                log = args.logs / f"{mode}-destination.log"
                destination, handle = start(["./gradlew", ":protocol-fixture:runServer", "-PfixturePort=25566",
                    "-PfixtureRunDir=../run-protocol-transfer", "--console=plain"], log, env)
                handles.append(handle)
                ready(destination, log)
            log = args.logs / f"{mode}-server.log"
            lifecycle = "normal" if mode.startswith("play_") else mode
            server, handle = start(["./gradlew", ":protocol-fixture:runServer", f"-PfixtureMode={lifecycle}",
                                    "--console=plain"], log, env)
            handles.append(handle)
            ready(server, log)
            steps = args.steps if mode == "normal" else None
            if mode == "play_transfer": steps = "fixture:transfer"
            if mode == "play_disconnect": steps = "fixture:disconnect"
            if mode == "play_session_negative": steps = "client:player_session_untrusted"
            suite = mode == "normal" or steps is not None
            props = ["-Dvibrato.packetCoverage=true", "-Dvibrato.protocolConnect=127.0.0.1:25576",
                     f"-Dvibrato.packetCoverageScenario=fixture_{mode}", "-Dvibrato.protocolAutoExit=true",
                     f"-Dvibrato.protocolSuite={str(suite).lower()}", "-Dvibrato.perf=low",
                     f"-Dvibrato.packetCoverageFile=vibrato/captures/{mode}.json",
                     f"-Dvibrato.protocolReport=vibrato/reports/{mode}.json"]
            if steps: props.append(f"-Dvibrato.protocolSteps={steps}")
            log = args.logs / f"{mode}-client.log"
            capture = ROOT / f"run/vibrato/captures/{mode}.json"
            previous = json.loads(capture.read_text()).get("captureId") if capture.exists() else None
            client, handle = start(["./gradlew", ":runClient", "-PgameProps=" + " ".join(props),
                                   "--console=plain"], log, env)
            handles.append(handle)
            print(f"Running {mode}; log {log}", flush=True)
            code = client.wait(timeout=args.timeout)
            if code:
                raise RuntimeError(f"Client exited {code}: {log}")
            if not capture.exists(): raise RuntimeError(f"No capture: {capture}")
            if json.loads(capture.read_text()).get("captureId") in (None, previous):
                raise RuntimeError(f"Client did not produce a fresh capture: {capture}")
            print(f"Captured {mode}: {capture}", flush=True)
            captures.append(capture)
        finally:
            stop(client)
            stop(server)
            stop(destination)
            for handle in handles: handle.close()
    if args.verify or (set(modes) == set(DEFAULT_MODES) and len(modes) == len(DEFAULT_MODES) and not args.steps):
        subprocess.run(["./gradlew", ":packetCoverageReport", "--console=plain",
                        "-PpacketCaptures=" + ",".join(map(str, captures)),
                        "-PpacketReport=" + str(ROOT / "run/vibrato/reports/merged.json")],
                       cwd=ROOT, env=env, check=True)


if __name__ == "__main__":
    main()
