#!/usr/bin/env bash
# Smoke test: synth -> fit -> discriminate in a temp dir. Fails if held-out accuracy on synthetic data is below 0.8,
# or if the profile does not contain every HumanProfile trait.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# -I isolates the interpreter and -B keeps __pycache__ out of the repo.
PY=(python3 -I -B)

"${PY[@]}" "$HERE/synth.py" "$TMP/traces" --humans 8 --bots 8 --seconds 90 --seed 7 > /dev/null
"${PY[@]}" "$HERE/synth.py" "$TMP/traces_reclocked" --humans 8 --bots 8 --seconds 90 --seed 7 --clock-seed 5 > /dev/null
"${PY[@]}" "$HERE/fit.py" "$TMP"/traces/*-human.jsonl -o "$TMP/human_profile.json"

"${PY[@]}" - "$TMP/human_profile.json" <<'EOF'
import json, sys
profile = json.load(open(sys.argv[1]))
expected = {"reactionMedianTicks", "jumpTimingBiasTicks", "jumpTimingSigmaTicks", "jumpMissChance",
            "strafeTapRate", "keyHoldMedianTicks", "keyHoldSigma", "hesitationRate", "lateralSigma",
            "lateralReversion", "lookAheadBlocks", "primaryFraction", "correctionCountMean",
            "overshootScale", "turnPeakSpeedScale"}
missing = expected - profile.keys()
if missing:
    sys.exit(f"profile is missing traits: {sorted(missing)}")
for name, v in profile.items():
    if set(v) != {"mean", "sigma"}:
        sys.exit(f"{name}: expected keys mean and sigma, got {sorted(v)}")
print(f"profile ok: {len(profile)} traits")
EOF

# Regression: an 870 px mouse delta (a recentre) injected into a synthetic trace must change nothing.
"${PY[@]}" - "$HERE" "$TMP/traces/synth00-human.jsonl" "$TMP/injected.jsonl" <<'EOF'
import json, sys
sys.path.insert(0, sys.argv[1])
import features

src, dst = sys.argv[2], sys.argv[3]
lines = open(src, encoding="utf-8").read().splitlines()
# Insert right after a mouse event in the middle of the trace, with the same timestamp so time order holds.
mid = next(i for i in range(len(lines) // 2, len(lines)) if json.loads(lines[i])["t"] == "mouse")
ev = json.loads(lines[mid])
bad = {"t": "mouse", "ms": ev["ms"], "dx": 870.0, "dy": -483.0}
with open(dst, "w", encoding="utf-8") as f:
    f.write("\n".join(lines[:mid + 1] + [json.dumps(bad)] + lines[mid + 1:]) + "\n")

_, clean = features.read_trace(src)
_, dirty = features.read_trace(dst)
if dirty != clean:
    sys.exit("870 px mouse delta was not ignored by read_trace")
vec = lambda evs: [repr(features.window_features(w)[n]) for _, w in features.split_windows(evs)
                   for n in features.FEATURE_NAMES]
if vec(dirty) != vec(clean):
    sys.exit("870 px mouse delta changed window features")
print("mouse outlier ignored: events and features unchanged")
EOF

# discriminate uses the tick channel. Mouse events are a recording artifact (the bot's camera is driven by code, so its
# mouse stream is near empty), so no feature discriminate reads may depend on them. Check that: no discriminate
# feature reads mouse events, mouse-only features are NaN on the tick channel, and removing every mouse event from a
# trace leaves its tick-channel features unchanged. Then mouse-rich human and mouse-less bot traces cannot separate
# through a mouse feature.
"${PY[@]}" - "$HERE" "$TMP/traces/synth00-human.jsonl" "$TMP/traces/synth00-bot.jsonl" <<'EOF'
import math, sys
sys.path.insert(0, sys.argv[1])
import features as F

if set(F.TICK_FEATURE_NAMES) & set(F.MOUSE_ONLY_FEATURES):
    sys.exit("discriminate feature list contains a mouse-reading feature")

def tick_vectors(evs):
    out = []
    for _, w in F.split_windows(evs, 10.0):
        if F.window_is_usable(w):
            feats = F.window_features(w, channel="tick")
            if any(not math.isnan(feats[n]) for n in F.MOUSE_ONLY_FEATURES):
                sys.exit("a mouse-only feature is defined on the tick channel")
            out.append([repr(x) for x in F.vector(feats, F.TICK_FEATURE_NAMES)])
    return out

for path in sys.argv[2:]:
    _, evs = F.read_trace(path)
    stripped = [e for e in evs if e["t"] != "mouse"]
    if tick_vectors(evs) != tick_vectors(stripped):
        sys.exit(f"{path}: tick-channel features depend on mouse events")
    print(f"{path.rsplit('/', 1)[-1]}: {len(evs) - len(stripped)} mouse events, tick features unchanged without them")
EOF

# Tick clock. The tick channel reads the tick index n, not the bursty sampler ms. Regenerate the same game with another
# clock seed: the game content and n are identical, only ms differ. Tick-channel features (window features, edge
# ticks used for the fit reference) must then agree. The tolerance is the ms rounding bound: a key's tick position is
# off by at most 0.5 ms per end of its bracket, which is 0.05 ticks at the 20 ms minimum sampler gap.
"${PY[@]}" - "$HERE" "$TMP/traces" "$TMP/traces_reclocked" <<'EOF'
import glob, math, os, sys
sys.path.insert(0, sys.argv[1])
import features as F

TOL_TICKS = 0.05
pa, pb = sys.argv[2], sys.argv[3]
moved_ms = 0
worst = 0.0
for fa in sorted(glob.glob(os.path.join(pa, "*.jsonl"))):
    fb = os.path.join(pb, os.path.basename(fa))
    _, ea = F.read_trace(fa)
    _, eb = F.read_trace(fb)
    if [e["t"] for e in ea] != [e["t"] for e in eb]:
        sys.exit(f"{fa}: event kinds differ between clock seeds")
    ticks_a = [e for e in ea if e["t"] == "tick"]
    ticks_b = [e for e in eb if e["t"] == "tick"]
    if [e["n"] for e in ticks_a] != [e["n"] for e in ticks_b]:
        sys.exit(f"{fa}: tick indices differ between clock seeds")
    moved_ms += sum(1 for a, b in zip(ticks_a, ticks_b) if a["ms"] != b["ms"])
    wa = [F.window_features(w, channel="tick") for _, w in F.split_windows(ea)]
    wb = [F.window_features(w, channel="tick") for _, w in F.split_windows(eb)]
    if len(wa) != len(wb):
        sys.exit(f"{fa}: window count changes with the ms jitter")
    for (_, wv_a), x, y in zip(F.split_windows(ea), wa, wb):
        # short_hold_frac counts holds at a 2-tick threshold, so a hold within the tolerance of that line can flip. One
        # flip moves the fraction by one hold's share of the window's moving holds, and that is the allowance.
        ticks_w = [e for e in wv_a if e["t"] == "tick"]
        tick_tk = [t["tk"] for t in ticks_w]
        holds_w = sum(1 for k, s, _ in F._holds([e for e in wv_a if e["t"] == "key"])
                      if k in F.HOLD_KEYS and F._tick_moving_at(ticks_w, tick_tk, s))
        for name in F.TICK_FEATURE_NAMES:
            a, b = x[name], y[name]
            if math.isnan(a) or math.isnan(b):
                if math.isnan(a) != math.isnan(b):
                    sys.exit(f"{fa}: {name} is NaN under one clock seed only")
                continue
            worst = max(worst, abs(a - b))
            allowed = max(1, holds_w) ** -1 if name == "short_hold_frac" else TOL_TICKS
            if abs(a - b) > allowed + 1e-12:
                sys.exit(f"{fa}: {name} changes with the ms jitter ({a} vs {b})")
    ja, jb = F.jump_edge_ticks(ea), F.jump_edge_ticks(eb)
    if len(ja) != len(jb) or any(abs(p - q) > TOL_TICKS for p, q in zip(ja, jb)):
        sys.exit(f"{fa}: jump ticks-to-edge change with the ms jitter")
if moved_ms == 0:
    sys.exit("the two clock seeds gave identical tick ms: the check is not testing anything")
print(f"tick clock: sampler ms differ on {moved_ms} ticks, tick features unchanged (worst {worst:.3f} ticks)")
EOF

"${PY[@]}" "$HERE/discriminate.py" \
    --human "$TMP"/traces/*-human.jsonl --bot "$TMP"/traces/*-bot.jsonl \
    --min-accuracy 0.8

echo "PASS: test_pipeline"
