#!/usr/bin/env python3
"""Trace reading and per-window motion features (docs/HUMAN_MOTION_PLAN.md, W4).

A trace is JSON Lines: a header line, then mouse, key and tick events in time order. Features are computed per
window of trace time. A feature the window cannot define is NaN; callers drop or impute it.

Key events with "carry": true are synthetic: split_windows() re-states keys that were already down when a window
starts, so holds and hesitations are measured correctly. Edge counts ignore them.

Mouse outliers are removed by read_trace() (see clean_mouse): a delta over 200 px in either axis is a cursor recentre
or screen switch, and after a gap of more than 500 ms without mouse events, deltas over 50 px in the first 100 ms
are the same kind of artifact.

Ticks may carry "edge": blocks along the movement heading to the first gap or drop within 6 blocks, absent when there
is none. When any tick has it, jump timing and jump misses come from edges. Otherwise they come from path nodes
(nextX/nextY/nextZ). Human traces have no path, so the lateral features come from heading wander when no tick has a
path node.
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
MOUSE_MAX_PX = 200.0    # a delta above this in either axis is a recentre or screen switch, not a turn
MOUSE_GAP_MS = 500.0    # a silence longer than this, followed by a mouse event...
MOUSE_RESYNC_MS = 100.0 # ...starts a resync window; within it, deltas above HUGE are dropped
MOUSE_HUGE_PX = 50.0    # a first sample after a silence this large is treated as an artifact
EDGE_NEAR = 1.5         # blocks, an approach to an edge counts from this distance
FALL_DY = 1.0           # blocks, a drop this large within FALL_TICKS after an approach is a fall
FALL_TICKS = 20         # ticks after an approach in which a fall or jump press is looked for
MIN_SPEED_BPT = 0.1     # blocks per tick, slower than this is not moving
HEADING_HALF_TICKS = 10 # the heading reference is a centred 1 s moving average (10 ticks each side)
HEADING_MIN_TICKS = 10  # fewer neighbours than this and the tick has no heading reference

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
    """Return (header, events) with mouse outliers removed. Raises ValueError if the first line is not a header."""
    with open(path, encoding="utf-8") as f:
        lines = [ln for ln in f if ln.strip()]
    if not lines:
        raise ValueError(f"{path}: empty trace")
    header = json.loads(lines[0])
    if header.get("t") != "header":
        raise ValueError(f"{path}: first line is not a header")
    return header, clean_mouse([json.loads(ln) for ln in lines[1:]])


def clean_mouse(events):
    """Drop mouse events that are recentres or screen switches rather than turns.

    A delta with |dx| or |dy| above MOUSE_MAX_PX is dropped. A silence of more than MOUSE_GAP_MS with no mouse events
    opens a resync window of MOUSE_RESYNC_MS starting at the next mouse event; inside it, deltas above MOUSE_HUGE_PX
    are dropped. Other events are kept in order.
    """
    out, last, resync_until = [], None, None
    for e in events:
        if e["t"] != "mouse":
            out.append(e)
            continue
        size = max(abs(e["dx"]), abs(e["dy"]))
        if size > MOUSE_MAX_PX:
            continue
        if last is not None and e["ms"] - last > MOUSE_GAP_MS:
            resync_until = e["ms"] + MOUSE_RESYNC_MS
        last = e["ms"]
        if resync_until is not None and e["ms"] <= resync_until and size > MOUSE_HUGE_PX:
            continue
        out.append(e)
    return out


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


def window_features(events, seconds=10.0, edge_ref=None):
    """Feature dict for one window. Keys are FEATURE_NAMES.

    edge_ref is the best-case ticks-to-edge for edge-based jump timing (fit.py passes the median over all traces).
    None means the median of this window's own sample.
    """
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

    if any("edge" in t for t in ticks):
        # PROXY: no ideal reference exists without a path, so the best case is taken as the median of the sample.
        # Offsets are then centred on the median; the mean and sd are relative to it.
        ttes = jump_edge_ticks(events)
        ref = edge_ref if edge_ref is not None else _median(ttes)
        offs = [x - ref for x in ttes]
        f["jump_offset_mean_ticks"] = _mean(offs)
        f["jump_offset_sd_ticks"] = _sd(offs)
        f["jump_miss_frac"] = _edge_miss_frac(events, jumps)
    else:
        ledges = _ledges(ticks)
        offsets, misses = _jump_offsets(ledges, jumps)
        f["jump_offset_mean_ticks"] = _mean(offsets)
        f["jump_offset_sd_ticks"] = _sd(offsets)
        f["jump_miss_frac"] = misses / len(ledges) if ledges else NAN

    if any("nextX" in t for t in ticks):
        f["lateral_sd"], f["lateral_theta"] = _lateral(ticks)
    else:
        f["lateral_sd"], f["lateral_theta"] = _heading_wander(events)
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

    Returns (lateral sigma per tick in blocks, reversion per tick), the HumanProfile units. The rms is taken about zero, not about each
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
    rms, k = _ou_from_segments(segments)
    return _lateral_units(rms, k)


def _lateral_units(rms, k):
    """Map a stationary rms offset (blocks) and per-tick reversion k to HumanProfile units.

    lateralSigma is the per-tick noise of an AR(1) offset with stationary sd rms and reversion k, which is
    rms * sqrt(2k - k^2). lateralReversion is k itself. Returns (sigma, k).
    """
    if math.isnan(k):
        return rms, NAN
    return rms * math.sqrt(2.0 * k - k * k), k


def _ou_from_segments(segments):
    """(rms about zero, per-tick reversion k) of a series cut into runs, or (rms, NaN) with no lag-1 signal.

    Lag-1 autocorrelation is normalized by the head and tail sums so short runs do not bias it. For a discrete OU
    (AR(1)) process the per-tick reversion is k = 1 - rho.
    """
    count = sum(len(v) for v in segments)
    if not count:
        return NAN, NAN
    sumsq = sum(x * x for v in segments for x in v)
    rms = math.sqrt(sumsq / count)
    if rms < 1e-3:
        return rms, NAN  # on the centerline: no wander to measure
    num = sum(v[i] * v[i + 1] for v in segments for i in range(len(v) - 1))
    head = sum(x * x for v in segments for x in v[:-1])
    tail = sum(x * x for v in segments for x in v[1:])
    if head <= 0 or tail <= 0:
        return rms, NAN
    rho = min(max(num / math.sqrt(head * tail), 0.01), 0.999)
    return rms, 1.0 - rho


def _forward_states(events):
    """(tick, forward key held) for each tick, in order."""
    held, out = False, []
    for e in events:
        if e["t"] == "key" and e["key"] == "forward":
            held = bool(e["down"])
        elif e["t"] == "tick":
            out.append((e, held))
    return out


def _speed(t):
    """Horizontal speed in blocks per tick."""
    return math.hypot(t["vx"], t["vz"])


def jump_edge_ticks(events):
    """Ticks-to-edge for each grounded, moving jump press that has an edge reading (edge / horizontal speed).

    The reading comes from the nearest tick at or before the press. Presses with no edge in that tick are skipped.
    """
    ticks = [e for e in events if e["t"] == "tick"]
    tick_ms = [t["ms"] for t in ticks]
    out = []
    for ms in _jump_presses(events):
        i = bisect.bisect_right(tick_ms, ms) - 1
        if i < 0:
            continue
        t = ticks[i]
        speed = _speed(t)
        if "edge" not in t or not t.get("ground") or speed <= MIN_SPEED_BPT:
            continue
        out.append(t["edge"] / speed)
    return out


def _jump_presses(events):
    return sorted(e["ms"] for e in events if e["t"] == "key" and e["down"] and e["key"] == "jump"
                  and not e.get("carry"))


def _edge_miss_frac(events, jumps=None):
    """PROXY for jumpMissChance: share of edge approaches that end in a fall with no jump press.

    An approach starts on the first tick of a run where the edge is under EDGE_NEAR blocks, the player is grounded,
    moving and holding forward. It is counted only if FALL_TICKS further ticks are in the window. It is a miss if
    some tick in those FALL_TICKS is more than FALL_DY below the approach and no jump press falls in the span.
    """
    seq = _forward_states(events)
    presses = _jump_presses(events) if jumps is None else jumps
    approaches = misses = 0
    prev = False
    for i, (t, fwd) in enumerate(seq):
        cond = (fwd and bool(t.get("ground")) and "edge" in t and t["edge"] < EDGE_NEAR
                and _speed(t) > MIN_SPEED_BPT)
        start = cond and not prev
        prev = cond
        if not start or i + FALL_TICKS >= len(seq):
            continue
        approaches += 1
        y0 = t["y"]
        end_ms = seq[i + FALL_TICKS][0]["ms"]
        fell = any(seq[j][0]["y"] < y0 - FALL_DY for j in range(i + 1, i + FALL_TICKS + 1))
        pressed = any(t["ms"] <= p <= end_ms for p in presses)
        if fell and not pressed:
            misses += 1
    return misses / approaches if approaches else NAN


def _heading_wander(events):
    """PROXY for lateral wander on pathless traces: heading error of the velocity, as an OU process.

    Runs are ticks with forward held and speed above MIN_SPEED_BPT. For each tick, the reference heading is the
    velocity summed over the HEADING_HALF_TICKS ticks each side (a centred 1 s moving average, the tick excluded).
    The angle between the tick's velocity and that reference is the heading error. Its rms is the stationary sd in
    radians. Taking the rms of error times speed gives the per-tick lateral step, which is the lateralSigma unit.
    Returns (lateral sigma per tick in blocks, reversion per tick).
    """
    runs, cur = [], []
    for t, fwd in _forward_states(events):
        if fwd and _speed(t) > MIN_SPEED_BPT:
            cur.append(t)
        elif cur:
            runs.append(cur)
            cur = []
    if cur:
        runs.append(cur)

    segments, speeds = [], []
    for run in runs:
        vals = []
        for i, t in enumerate(run):
            near = run[max(0, i - HEADING_HALF_TICKS):i] + run[i + 1:i + 1 + HEADING_HALF_TICKS]
            if len(near) < HEADING_MIN_TICKS:
                continue
            mx = sum(p["vx"] for p in near)
            mz = sum(p["vz"] for p in near)
            vals.append(math.atan2(mx * t["vz"] - mz * t["vx"], mx * t["vx"] + mz * t["vz"]))
            speeds.append(_speed(t))
        if len(vals) >= 6:
            segments.append(vals)
    rms, k = _ou_from_segments(segments)
    if math.isnan(rms) or not speeds:
        return NAN, NAN
    return rms * statistics.fmean(speeds), k


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
