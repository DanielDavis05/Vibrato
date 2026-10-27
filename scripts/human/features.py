#!/usr/bin/env python3
"""Trace reading and per-window motion features (docs/HUMAN_MOTION_PLAN.md, W4).

A trace is JSON Lines: a header line, then mouse, key and tick events in time order. Features are computed per
window of trace time. A feature the window cannot define is NaN; callers drop or impute it.

Key events with "carry": true are synthetic: split_windows() re-states keys that were already down when a window
starts, so holds and hesitations are measured correctly. Edge counts ignore them.
"""

import bisect
import json
import math
import statistics

TICK_MS = 50.0
FRAME_MS = 1000.0 / 144.0
MOVE_KEYS = ("forward", "back", "left", "right")
HOLD_KEYS = MOVE_KEYS + ("jump",)
MIN_WINDOW_TICKS = 10   # windows with fewer tick events (about half a second) are skipped
MIN_TURN_PX = 8.0       # mouse bursts smaller than this are jitter, not turns
BURST_GAP_MS = 100.0    # mouse events further apart than this start a new burst
SUBMOVE_MERGE_MS = 25.0 # active samples this close together belong to one submovement
LEDGE_RANGE = 3.0       # blocks, horizontal distance at which an upcoming ledge is first seen
LEDGE_DY = 0.5          # the next node must be this much higher to count as a ledge
JUMP_WINDOW_TICKS = 6   # a jump press this close (in ticks) to a ledge counts for it

NAN = float("nan")

# Fixed order. fit.py and discriminate.py refer to features by name.
FEATURE_NAMES = (
    "mouse_rate_hz", "dx_nonint_frac", "turn_count", "turn_peak_norm", "submove_count_mean",
    "correction_count_mean", "primary_fraction_mean", "overshoot_mean",
    "hold_median_ticks", "hold_logsd", "hold_cv", "short_hold_frac",
    "strafe_taps_per_s", "jump_per_s", "jump_interval_cv",
    "jump_offset_mean_ticks", "jump_offset_sd_ticks", "jump_miss_frac",
    "lateral_sd", "lateral_theta", "reaction_median_ticks", "hesitation_per_min",
    "look_ahead_blocks",
)


def read_trace(path):
    """Return (header, events). Raises ValueError if the first line is not a header."""
    with open(path, encoding="utf-8") as f:
        lines = [ln for ln in f if ln.strip()]
    if not lines:
        raise ValueError(f"{path}: empty trace")
    header = json.loads(lines[0])
    if header.get("t") != "header":
        raise ValueError(f"{path}: first line is not a header")
    return header, [json.loads(ln) for ln in lines[1:]]


def split_windows(events, seconds=10.0):
    """Split events into fixed-length windows by ms. Returns a list of (start_ms, events).

    Keys already down at a window start are re-stated there as carry-in key-down events.
    """
    span = seconds * 1000.0
    windows = []
    current = None
    held = set()
    for e in events:
        index = int(e["ms"] // span)
        if index != current:
            current = index
            start = index * span
            carry = [{"t": "key", "ms": start, "key": k, "down": True, "carry": True} for k in sorted(held)]
            windows.append((start, carry))
        windows[-1][1].append(e)
        if e["t"] == "key":
            if e["down"]:
                held.add(e["key"])
            else:
                held.discard(e["key"])
    return windows


def window_is_usable(events):
    return sum(1 for e in events if e["t"] == "tick") >= MIN_WINDOW_TICKS


def window_features(events, seconds=10.0):
    """Feature dict for one window. Keys are FEATURE_NAMES."""
    ticks = [e for e in events if e["t"] == "tick"]
    mouse = [e for e in events if e["t"] == "mouse"]
    keys = [e for e in events if e["t"] == "key"]
    downs = [e for e in keys if e["down"] and not e.get("carry")]
    jumps = sorted(e["ms"] for e in downs if e["key"] == "jump")

    f = {}
    f["mouse_rate_hz"] = len(mouse) / seconds
    f["dx_nonint_frac"] = _frac(lambda d: abs(d - round(d)) > 1e-6, [m["dx"] for m in mouse])

    turns = [s for s in (_turn_stats(b) for b in _bursts(mouse)) if s is not None]
    f["turn_count"] = float(len(turns))
    f["turn_peak_norm"] = _median([t["norm"] for t in turns])
    f["submove_count_mean"] = _mean([t["subs"] for t in turns])
    f["correction_count_mean"] = _mean([t["subs"] - 1 for t in turns])
    f["primary_fraction_mean"] = _mean([t["primary"] for t in turns])
    f["overshoot_mean"] = _mean([t["overshoot"] for t in turns])

    holds = [(e - s) / TICK_MS for k, s, e in _holds(keys) if k in HOLD_KEYS]
    f["hold_median_ticks"] = _median([max(h, 0.5) for h in holds])
    f["hold_logsd"] = _sd([math.log(max(h, 0.5)) for h in holds])
    f["hold_cv"] = _cv(holds)
    f["short_hold_frac"] = _frac(lambda h: h <= 2.0, holds)

    f["strafe_taps_per_s"] = sum(1 for e in downs if e["key"] in ("left", "right")) / seconds
    f["jump_per_s"] = len(jumps) / seconds
    f["jump_interval_cv"] = _cv([b - a for a, b in zip(jumps, jumps[1:])])

    ledges = _ledges(ticks)
    offsets, misses = _jump_offsets(ledges, jumps)
    f["jump_offset_mean_ticks"] = _mean(offsets)
    f["jump_offset_sd_ticks"] = _sd(offsets)
    f["jump_miss_frac"] = misses / len(ledges) if ledges else NAN

    f["lateral_sd"], f["lateral_theta"] = _lateral(ticks)
    acts = sorted([m["ms"] for m in mouse] + [e["ms"] for e in downs])
    f["reaction_median_ticks"] = _reaction_ticks(ticks, acts)
    f["hesitation_per_min"] = _hesitations(events, seconds)
    f["look_ahead_blocks"] = _look_ahead(ticks)
    return f


def vector(feats):
    return [feats[name] for name in FEATURE_NAMES]


# Helpers

def _mean(xs):
    return sum(xs) / len(xs) if xs else NAN


def _median(xs):
    return statistics.median(xs) if xs else NAN


def _sd(xs):
    return statistics.stdev(xs) if len(xs) > 1 else NAN


def _cv(xs):
    if len(xs) < 2:
        return NAN
    m = statistics.fmean(xs)
    return statistics.stdev(xs) / m if m > 0 else NAN


def _frac(pred, xs):
    return sum(1 for x in xs if pred(x)) / len(xs) if xs else NAN


def _wrap(deg):
    return (deg + 180.0) % 360.0 - 180.0


def _bursts(mouse, gap_ms=BURST_GAP_MS):
    bursts, cur = [], []
    for e in mouse:
        if cur and e["ms"] - cur[-1]["ms"] > gap_ms:
            bursts.append(cur)
            cur = []
        cur.append(e)
    if cur:
        bursts.append(cur)
    return bursts


def _turn_stats(burst):
    """Shape of one camera move, or None if it is too small to be a turn.

    norm: peak speed * duration / amplitude (1.875 for a minimum-jerk bell).
    subs: submovements (runs of speed above 15% of peak). primary: share of the move in the first submovement.
    overshoot: how far the cumulative move passes its final value, as a share of the amplitude.
    """
    total = sum(e["dx"] for e in burst)
    amp = abs(total)
    if amp < MIN_TURN_PX or len(burst) < 3:
        return None
    sign = 1.0 if total > 0 else -1.0
    raw = []
    for i, e in enumerate(burst):
        dt = e["ms"] - burst[i - 1]["ms"] if i else FRAME_MS
        raw.append(abs(e["dx"]) / max(dt, 1.0))
    smooth = []
    for i in range(len(raw)):
        part = raw[max(0, i - 1):i + 2]
        smooth.append(sum(part) / len(part))
    peak = max(smooth)
    dur = max(burst[-1]["ms"] - burst[0]["ms"] + FRAME_MS, 10.0)
    runs = []
    for i, s in enumerate(smooth):
        if s <= 0.15 * peak:
            continue
        if runs and burst[i]["ms"] - burst[runs[-1][1]]["ms"] <= SUBMOVE_MERGE_MS:
            runs[-1][1] = i
        else:
            runs.append([i, i])
    first = burst[runs[0][0]:runs[0][1] + 1]
    primary = sum(e["dx"] for e in first) * sign / amp
    cum, best = 0.0, 0.0
    for e in burst:
        cum += e["dx"] * sign
        best = max(best, cum)
    return {
        "norm": peak * dur / amp,
        "subs": len(runs),
        "primary": primary,
        "overshoot": max(0.0, best - amp) / amp,
    }


def _holds(keys):
    """Completed presses as (key, start_ms, end_ms)."""
    down, out = {}, []
    for e in keys:
        if e["down"]:
            down.setdefault(e["key"], e["ms"])
        else:
            start = down.pop(e["key"], None)
            if start is not None:
                out.append((e["key"], start, e["ms"]))
    return out


def _ledges(ticks):
    """Ms of the first tick at which each upcoming ledge is seen (next node higher and close)."""
    seen, out = set(), []
    for t in ticks:
        if "nextX" not in t:
            continue
        node = (t["nextX"], t["nextY"], t["nextZ"])
        if node in seen:
            continue
        close = math.hypot(t["nextX"] - t["x"], t["nextZ"] - t["z"]) <= LEDGE_RANGE
        if close and t["nextY"] - t["y"] >= LEDGE_DY:
            seen.add(node)
            out.append(t["ms"])
    return out


def _jump_offsets(ledge_ms, jump_ms):
    """Per ledge, the jump press nearest in time within JUMP_WINDOW_TICKS, in ticks. Returns (offsets, misses)."""
    offsets, misses = [], 0
    limit = JUMP_WINDOW_TICKS * TICK_MS
    for ledge in ledge_ms:
        best = None
        for j in jump_ms:
            d = j - ledge
            if abs(d) <= limit and (best is None or abs(d) < abs(best)):
                best = d
        if best is None:
            misses += 1
        else:
            offsets.append(best / TICK_MS)
    return offsets, misses


def _lateral(ticks):
    """Signed offset from the line between the previous and next path node, per path segment.

    Returns (rms offset in blocks, OU reversion rate per second). The rms is taken about zero, not about each
    segment's mean, because segments are short (about one correlation time) and per-segment means and
    autocorrelations are strongly biased. Only the middle 70% of each segment is used, so corners do not count
    as wander.
    """
    segments, vals = [], []
    frm = to = None
    for t in ticks:
        if "nextX" not in t:
            continue
        node = (t["nextX"], t["nextZ"])
        if node != to:
            if len(vals) >= 6:
                segments.append(vals)
            frm, to, vals = to, node, []
        if frm is None:
            continue
        dx, dz = to[0] - frm[0], to[1] - frm[1]
        length = math.hypot(dx, dz)
        if length < 1.0:
            continue
        px, pz = t["x"] - frm[0], t["z"] - frm[1]
        along = (px * dx + pz * dz) / length
        if 0.15 * length <= along <= 0.85 * length:
            vals.append((-dz * px + dx * pz) / length)
    if len(vals) >= 6:
        segments.append(vals)

    count = sum(len(v) for v in segments)
    if not count:
        return NAN, NAN
    sumsq = sum(x * x for v in segments for x in v)
    rms = math.sqrt(sumsq / count)
    if rms < 1e-3:
        return rms, NAN  # on the centerline: no wander to measure
    # Lag-1 autocorrelation, normalized by the head and tail sums so short segments do not bias it.
    num = sum(v[i] * v[i + 1] for v in segments for i in range(len(v) - 1))
    head = sum(x * x for v in segments for x in v[:-1])
    tail = sum(x * x for v in segments for x in v[1:])
    if head <= 0 or tail <= 0:
        return rms, NAN
    rho = min(max(num / math.sqrt(head * tail), 0.01), 0.999)
    return rms, -math.log(rho) * 1000.0 / TICK_MS


def _reaction_ticks(ticks, acts):
    """Ticks from a path-node change to the next camera or key activity (median over changes)."""
    starts, last = [], None
    for t in ticks:
        node = (t.get("nextX"), t.get("nextZ"))
        if last is not None and node != last and node[0] is not None:
            starts.append(t["ms"])
        last = node
    lat = []
    for s in starts:
        i = bisect.bisect_left(acts, s)
        if i < len(acts) and acts[i] - s <= 2000.0:
            lat.append((acts[i] - s) / TICK_MS)
    return _median(lat)


def _hesitations(events, seconds):
    """Runs of 2 to 6 ticks with no movement key down while pathing, per minute."""
    down, run, count = set(), 0, 0
    for e in events:
        if e["t"] == "key":
            if e["down"]:
                down.add(e["key"])
            else:
                down.discard(e["key"])
        elif e["t"] == "tick" and "nextX" in e:
            if any(k in down for k in MOVE_KEYS):
                if 2 <= run <= 6:
                    count += 1
                run = 0
            else:
                run += 1
    return count * 60.0 / seconds


def _look_ahead(ticks):
    """Proxy: median distance to the next path node over ticks where the camera is steady."""
    dists = []
    for prev, t in zip(ticks, ticks[1:]):
        if "nextX" not in t or abs(_wrap(t["yaw"] - prev["yaw"])) >= 0.5:
            continue
        d = math.hypot(t["nextX"] - t["x"], t["nextZ"] - t["z"])
        if d > 0.5:
            dists.append(d)
    return _median(dists)
