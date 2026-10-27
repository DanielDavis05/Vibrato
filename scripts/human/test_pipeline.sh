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

"${PY[@]}" "$HERE/discriminate.py" \
    --human "$TMP"/traces/*-human.jsonl --bot "$TMP"/traces/*-bot.jsonl \
    --min-accuracy 0.8

echo "PASS: test_pipeline"
