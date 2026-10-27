#!/usr/bin/env python3
"""Fit human_profile.json from human traces (docs/HUMAN_MOTION_PLAN.md, W4).

Each trait is estimated from one window feature (see features.py). Every usable window of every human trace gives
one estimate; mean is the average over windows and sigma is their sample standard deviation. Traits with no finite
estimate are left out so Population falls back to its baked-in defaults.

Usage: fit.py TRACE [TRACE ...] [-o human_profile.json] [--window 10]
"""

import argparse
import json
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from features import jump_edge_ticks, read_trace, split_windows, window_features, window_is_usable  # noqa: E402

# HumanProfile trait -> window feature it is estimated from.
TRAITS = (
    ("reactionMedianTicks", "reaction_median_ticks"),
    ("jumpTimingBiasTicks", "jump_offset_mean_ticks"),
    ("jumpTimingSigmaTicks", "jump_offset_sd_ticks"),
    ("jumpMissChance", "jump_miss_frac"),
    ("strafeTapRate", "strafe_taps_per_s"),
    ("keyHoldMedianTicks", "hold_median_ticks"),
    ("keyHoldSigma", "hold_logsd"),
    ("hesitationRate", "hesitation_per_min"),
    ("lateralSigma", "lateral_sd"),
    ("lateralReversion", "lateral_theta"),
    ("lookAheadBlocks", "look_ahead_blocks"),
    ("primaryFraction", "primary_fraction_mean"),
    ("correctionCountMean", "correction_count_mean"),
    ("overshootScale", "overshoot_mean"),
    ("turnPeakSpeedScale", "turn_peak_norm"),
)


def main():
    ap = argparse.ArgumentParser(description="Fit human_profile.json from human traces.")
    ap.add_argument("traces", nargs="+", help="JSONL trace files with source 'human'")
    ap.add_argument("-o", "--out", default="human_profile.json")
    ap.add_argument("--window", type=float, default=10.0, help="window length in seconds")
    args = ap.parse_args()

    windows = []
    used = 0
    for path in args.traces:
        header, events = read_trace(path)
        if header.get("source") != "human":
            print(f"skip {path}: source is {header.get('source')!r}", file=sys.stderr)
            continue
        used += 1
        windows += [w for _, w in split_windows(events, args.window) if window_is_usable(w)]
    if not used:
        sys.exit("no human traces given")

    # Edge jump timing is relative to a best case. Without a path that best case is taken as the median of all
    # ticks-to-edge values in the human traces (a proxy; see features.py).
    edge_samples = [x for w in windows for x in jump_edge_ticks(w)]
    edge_ref = statistics.median(edge_samples) if edge_samples else None
    if edge_ref is not None:
        print(f"edge reference: median ticks-to-edge {edge_ref:.3f} over {len(edge_samples)} jump press(es)")

    samples = {feat: [] for _, feat in TRAITS}
    for window in windows:
        # The human-only fit uses the mouse channel: physical mouse turns and path-free reaction are valid here.
        feats = window_features(window, args.window, edge_ref=edge_ref, channel="mouse")
        for feat in samples:
            if not math.isnan(feats[feat]):
                samples[feat].append(feats[feat])

    profile = {}
    for trait, feat in TRAITS:
        vals = samples[feat]
        if not vals:
            print(f"warning: no data for {trait}; Population default will be used", file=sys.stderr)
            continue
        sigma = statistics.stdev(vals) if len(vals) > 1 else 0.0
        profile[trait] = {"mean": round(statistics.fmean(vals), 6), "sigma": round(sigma, 6)}
        print(f"{trait:24s} mean={profile[trait]['mean']:<12g} sigma={profile[trait]['sigma']:<12g} n={len(vals)}")

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(profile, f, indent=2)
        f.write("\n")
    print(f"wrote {args.out} ({len(profile)} traits from {used} trace(s))")


if __name__ == "__main__":
    main()
