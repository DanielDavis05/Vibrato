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

"${PY[@]}" "$HERE/discriminate.py" \
    --human "$TMP"/traces/*-human.jsonl --bot "$TMP"/traces/*-bot.jsonl \
    --min-accuracy 0.8

echo "PASS: test_pipeline"
