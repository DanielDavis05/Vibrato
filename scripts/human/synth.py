#!/usr/bin/env python3
"""Synthetic traces in the human-motion JSONL format (docs/HUMAN_MOTION_PLAN.md, W4).

Stand-ins so fit.py and discriminate.py can run before real recordings exist. They are not a model of real players.

'human': a ballistic primary camera move after a reaction delay, 0-3 corrections, occasional overshoot, log-normal
strafe taps, hesitations, jump timing jitter with rare misses, lateral Ornstein-Uhlenbeck wander around the path.
'bot': one smooth bell-shaped move per turn, forward and sprint held all the time, exact centerline, jumps exactly
at the ledge.

Usage: synth.py OUT_DIR [--humans 8] [--bots 8] [--seconds 90] [--seed 1]
"""

import argparse
import bisect
import heapq
import itertools
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from features import FRAME_MS, LEDGE_DY, LEDGE_RANGE, TICK_MS, _wrap  # noqa: E402

SPRINT_BPS = 5.612   # blocks per second, sprinting
STEP_DEG = 0.15      # degrees per mouse pixel at sensitivity 0.5 (vanilla grid)


def make_path(rng, count):
    """Random walk of path nodes [x, y, z]. A step from y=64 to y=65 is a ledge that needs a jump."""
    nodes = [[0.0, 64.0, 0.0]]
    heading = rng.uniform(0.0, 2.0 * math.pi)
    for _ in range(count):
        if rng.random() < 0.3:
            heading += rng.uniform(-math.pi / 2, math.pi / 2)
        length = rng.uniform(3.0, 7.0)
        x, y, z = nodes[-1]
        if y == 64.0:
            y = 65.0 if rng.random() < 0.3 else 64.0
        else:
            y = 64.0 if rng.random() < 0.5 else 65.0
        nodes.append([x + length * math.cos(heading), y, z + length * math.sin(heading)])
    return nodes


def _human_params(rng):
    def jitter(scale):
        return math.exp(rng.gauss(0.0, scale))
    return {
        "reaction_ms": 250.0 * jitter(0.3),
        "threshold": 2.0,
        "overshoot_base": 0.05,
        "jump_bias": rng.uniform(0.8, 2.2),   # ticks after the ledge is first seen
        "jump_sd": 1.2 * jitter(0.1),
        "jump_miss": 0.05,
        "strafe_per_s": 0.25 * jitter(0.2),
        "tap_hold_ms": 150.0 * jitter(0.15),
        "tap_sigma": 0.4,
        "hesitation_per_min": 2.0 * jitter(0.3),
        "lat_sigma": 0.2 * jitter(0.2),
        "lat_theta": 1.0 * jitter(0.2),       # per second
        "look_ahead": 2.5 * jitter(0.15),
    }


def _bot_params():
    return {"reaction_ms": 0.0, "threshold": 3.0, "look_ahead": 2.5, "overshoot_base": 0.0}


def make_plan(rng, human, P, yaw0, err, t0):
    """Camera move from yaw0 towards yaw0 + err, starting at t0 ms. Returns (t0, t1, a0, a1) segments."""
    target = yaw0 + err
    if not human:
        dur = 120.0 + 15.0 * math.sqrt(abs(err))
        return [(t0, t0 + dur, yaw0, target)]
    primary = rng.gauss(0.93, 0.03)
    if rng.random() < min(0.5, P["overshoot_base"] + abs(err) / 120.0):
        primary = 1.0 + abs(rng.gauss(0.05, 0.03))
    segs = []
    t, a = t0, yaw0
    end = yaw0 + err * primary
    dur = (110.0 + 18.0 * math.sqrt(abs(err))) * math.exp(rng.gauss(0.0, 0.1))
    segs.append((t, t + dur, a, end))
    t, a = t + dur, end
    for _ in range(rng.choices([0, 1, 2, 3], weights=[0.35, 0.4, 0.2, 0.05])[0]):
        e = target - a
        if abs(e) < 0.05:
            break
        nxt = a + e * rng.uniform(0.6, 0.9)
        dur = (70.0 + 12.0 * math.sqrt(abs(e))) * math.exp(rng.gauss(0.0, 0.1))
        segs.append((t, t + dur, a, nxt))
        t, a = t + dur, nxt
    return segs


def planned_yaw(segs, ms):
    """Planned camera yaw at ms: minimum-jerk interpolation along the consecutive segments."""
    if ms <= segs[0][0]:
        return segs[0][2]
    for t0, t1, a0, a1 in segs:
        if ms < t1:
            u = (ms - t0) / (t1 - t0)
            return a0 + (a1 - a0) * u * u * u * (10.0 - 15.0 * u + 6.0 * u * u)
    return segs[-1][3]


def _key(ms, name, down):
    return {"t": "key", "ms": int(round(ms)), "key": name, "down": down}


def simulate(source, seed, seconds):
    """One trace as a list of dicts (header first), already in time order."""
    rng = random.Random(seed)
    human = source == "human"
    P = _human_params(rng) if human else _bot_params()
    nodes = make_path(rng, int(seconds * SPRINT_BPS / 3.0) + 20)
    arc = [0.0]
    for a, b in zip(nodes, nodes[1:]):
        arc.append(arc[-1] + math.hypot(b[0] - a[0], b[2] - a[2]))

    def locate(s):
        """Index of the segment holding arc length s, position on the path, and its unit direction."""
        s = min(max(s, 0.0), arc[-1] - 1e-6)
        i = bisect.bisect_right(arc, s) - 1
        a, b = nodes[i], nodes[i + 1]
        length = arc[i + 1] - arc[i]
        u = (s - arc[i]) / length
        return i, a[0] + (b[0] - a[0]) * u, a[2] + (b[2] - a[2]) * u, (b[0] - a[0]) / length, (b[2] - a[2]) / length

    dt = FRAME_MS / 1000.0
    s, off = 0.0, 0.0
    _, bx, bz, _, _ = locate(0.0)
    _, ax, az, _, _ = locate(P["look_ahead"])
    yaw = math.degrees(math.atan2(-(ax - bx), az - bz))

    # Vertical steps along the path, as arc positions. Ticks report "edge" as the distance to the next one within
    # 6 blocks. Synthetic ledges are up and down steps alike; the real recorder reports gaps and drops.
    steps = [arc[j - 1] for j in range(1, len(nodes)) if nodes[j][1] != nodes[j - 1][1]]

    lines = [{"t": "header", "v": 1, "source": source, "mcVersion": "1.21.1", "guiScale": 2,
              "sensitivity": 0.5, "fps": 144, "note": f"synthetic {source} seed={seed}"}]
    heap, counter = [], itertools.count()

    def push(ms, ev):
        heapq.heappush(heap, (int(round(ms)), next(counter), ev))

    def flush(now):
        while heap and heap[0][0] <= now:
            lines.append(heapq.heappop(heap)[2])

    push(0, _key(0, "forward", True))
    push(0, _key(0, "sprint", True))
    air, ledges = [], set()
    plan, plan_prev, acc, pending = None, 0.0, 0.0, None
    hesitation_until = -1.0
    prev = None
    next_tick = 0
    frames = int(seconds * 1000.0 / FRAME_MS)

    for f in range(frames + 1):
        ms = int(round(f * FRAME_MS))
        s += SPRINT_BPS * dt
        if human:
            decay = math.exp(-P["lat_theta"] * dt)
            off = off * decay + P["lat_sigma"] * math.sqrt(1.0 - decay * decay) * rng.gauss(0.0, 1.0)
        i, bx, bz, ux, uz = locate(s)
        px, pz = bx - uz * off, bz + ux * off
        _, ax, az, _, _ = locate(s + P["look_ahead"])
        want = math.degrees(math.atan2(-(ax - px), az - pz))

        # Camera: one planned move at a time, started after the reaction delay.
        err = _wrap(want - yaw)
        if plan is None and pending is None and abs(err) > P["threshold"]:
            pending = ms + P["reaction_ms"] * math.exp(rng.gauss(0.0, 0.2))
        if pending is not None and plan is None and ms >= pending:
            pending = None
            err = _wrap(want - yaw)
            if abs(err) > 0.5 * P["threshold"]:
                plan = make_plan(rng, human, P, yaw, err, ms)
                plan_prev = yaw
        if plan is not None:
            value = planned_yaw(plan, ms)
            acc += (value - plan_prev) / STEP_DEG
            plan_prev = value
            if ms >= plan[-1][1]:
                plan = None
        dx = round(acc)  # whole pixels, carrying the remainder
        if dx:
            acc -= dx
            yaw += dx * STEP_DEG
            push(ms, {"t": "mouse", "ms": ms, "dx": float(dx), "dy": 0.0})

        if human:
            if rng.random() < P["strafe_per_s"] * dt:
                name = rng.choice(("left", "right"))
                hold = P["tap_hold_ms"] * math.exp(rng.gauss(0.0, P["tap_sigma"]))
                push(ms, _key(ms, name, True))
                push(ms + hold, _key(ms + hold, name, False))
            if ms >= hesitation_until and rng.random() < P["hesitation_per_min"] / 60.0 * dt:
                n = rng.randint(2, 6)
                push(ms, _key(ms, "forward", False))
                push(ms + n * TICK_MS, _key(ms + n * TICK_MS, "forward", True))
                hesitation_until = ms + n * TICK_MS

        while next_tick * TICK_MS <= ms:
            tms = int(next_tick * TICK_MS)
            nx, ny, nz = (round(v, 4) for v in nodes[i + 1])
            t = {"t": "tick", "n": next_tick, "ms": tms,
                 "x": round(px, 4), "y": round(nodes[i][1], 4), "z": round(pz, 4),
                 "vx": 0.0, "vy": 0.0, "vz": 0.0,
                 "yaw": round(yaw, 3), "pitch": round(4.0 + rng.gauss(0.0, 0.3), 3) if human else 0.0,
                 "ground": True, "sprint": True, "nextX": nx, "nextY": ny, "nextZ": nz}
            if prev is not None:
                t["vx"] = round(t["x"] - prev["x"], 4)
                t["vy"] = round(t["y"] - prev["y"], 4)
                t["vz"] = round(t["z"] - prev["z"], 4)
            # Real bot ticks carry edges too, so both classes get them. Otherwise edge presence alone would separate.
            k = bisect.bisect_left(steps, s)
            if k < len(steps) and steps[k] - s <= 6.0:
                t["edge"] = round(steps[k] - s, 3)
            prev = t
            push(tms, t)
            node = (nx, ny, nz)
            close = math.hypot(nx - t["x"], nz - t["z"]) <= LEDGE_RANGE
            if close and ny - t["y"] >= LEDGE_DY and node not in ledges:
                ledges.add(node)
                if human:
                    if rng.random() < P["jump_miss"]:
                        off_ticks = rng.randint(8, 12)
                    else:
                        off_ticks = min(5, max(0, round(rng.gauss(P["jump_bias"], P["jump_sd"]))))
                    hold = 100.0 * math.exp(rng.gauss(0.0, 0.25))
                else:
                    off_ticks, hold = 0, 50.0
                press = tms + off_ticks * TICK_MS
                push(press, _key(press, "jump", True))
                push(press + hold, _key(press + hold, "jump", False))
                air.append((press + 1.0, press + 200.0))  # the tick the jump is pressed on is still grounded
            t["ground"] = not any(a <= tms < b for a, b in air)
            next_tick += 1
        flush(ms)

    flush(float("inf"))
    return lines


def write_trace(path, lines):
    with open(path, "w", encoding="utf-8") as f:
        for ev in lines:
            f.write(json.dumps(ev, separators=(",", ":")) + "\n")


def main():
    ap = argparse.ArgumentParser(description="Generate synthetic human and bot traces.")
    ap.add_argument("out_dir")
    ap.add_argument("--humans", type=int, default=8)
    ap.add_argument("--bots", type=int, default=8)
    ap.add_argument("--seconds", type=float, default=90.0)
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()
    os.makedirs(args.out_dir, exist_ok=True)
    for source, count in (("human", args.humans), ("bot", args.bots)):
        for i in range(count):
            seed = args.seed * 1000 + i + (0 if source == "human" else 500)
            path = os.path.join(args.out_dir, f"synth{i:02d}-{source}.jsonl")
            write_trace(path, simulate(source, seed, args.seconds))
            print(f"wrote {path}")


if __name__ == "__main__":
    main()
