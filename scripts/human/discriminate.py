#!/usr/bin/env python3
"""Human vs bot discriminator (docs/HUMAN_MOTION_PLAN.md, W4).

Splits each trace into windows, computes the 'tick' channel features for each window (what a server sees: per-tick yaw,
position and key events, no mouse events, no path nodes), and trains a small logistic regression by gradient descent.

Windows with less than MIN_MOVING_S seconds of movement are dropped. The train/test split is by trace file when a
class has at least two traces. A class with one trace is split by time within that trace instead, and the report says
so, because that accuracy is optimistic. Warns when a class has fewer than MIN_WINDOW_COUNT usable windows.

Usage: discriminate.py --human PATH [PATH ...] --bot PATH [PATH ...]
       PATH is a trace file or a directory (every *.jsonl inside is used).
Exit status is 1 when held-out accuracy is below --min-accuracy.
"""

import argparse
import glob
import math
import os
import random
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from features import (DEFAULT_CHANNEL, MIN_MOVING_S, MOUSE_ONLY_FEATURES, TICK_FEATURE_NAMES,  # noqa: E402
                      moving_seconds, read_trace, split_windows, vector, window_features, window_is_usable)

MIN_WINDOW_COUNT = 20  # usable windows per class below this make the accuracy unreliable
TRAIN_FRACTION = 0.7   # used for the time split of a single-trace class


def expand(paths):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files.extend(sorted(glob.glob(os.path.join(p, "*.jsonl"))))
        else:
            files.append(p)
    return files


def load(path, window):
    """Feature vectors of the usable windows in one trace, in time order, plus counts of dropped windows."""
    _, events = read_trace(path)
    rows = []
    too_few_ticks = too_still = 0
    for _, evs in split_windows(events, window):
        if not window_is_usable(evs):
            too_few_ticks += 1
        elif moving_seconds(evs) < MIN_MOVING_S:
            too_still += 1
        else:
            feats = window_features(evs, window, channel=DEFAULT_CHANNEL)
            rows.append(vector(feats, TICK_FEATURE_NAMES))
    return rows, too_few_ticks, too_still


def standardize(train, test):
    """Impute NaN with the training mean, then z-score with training statistics."""
    d = len(TICK_FEATURE_NAMES)
    mu, sd = [], []
    for j in range(d):
        col = [r[j] for r in train if not math.isnan(r[j])]
        m = statistics.fmean(col) if col else 0.0
        s = statistics.pstdev(col) if len(col) > 1 else 0.0
        mu.append(m)
        sd.append(s if s > 1e-9 else 1.0)

    def apply(rows):
        return [[((mu[j] if math.isnan(r[j]) else r[j]) - mu[j]) / sd[j] for j in range(d)] for r in rows]

    return apply(train), apply(test)


def _sigmoid(z):
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    e = math.exp(z)
    return e / (1.0 + e)


def train_logreg(X, y, epochs=800, lr=0.5, l2=1e-3):
    """Batch gradient descent on the L2-regularized logistic loss. Returns (weights, bias)."""
    n, d = len(X), len(X[0])
    w, b = [0.0] * d, 0.0
    for _ in range(epochs):
        gw, gb = [0.0] * d, 0.0
        for xi, yi in zip(X, y):
            err = _sigmoid(b + sum(wj * xj for wj, xj in zip(w, xi))) - yi
            gb += err
            for j, xj in enumerate(xi):
                gw[j] += err * xj
        for j in range(d):
            w[j] -= lr * (gw[j] / n + l2 * w[j])
        b -= lr * gb / n
    return w, b


def predict(w, b, X):
    return [1 if _sigmoid(b + sum(wj * xj for wj, xj in zip(w, xi))) >= 0.5 else 0 for xi in X]


def main():
    ap = argparse.ArgumentParser(description="Train a tiny human vs bot classifier on trace windows.")
    ap.add_argument("--human", nargs="+", required=True)
    ap.add_argument("--bot", nargs="+", required=True)
    ap.add_argument("--window", type=float, default=10.0, help="window length in seconds")
    ap.add_argument("--seed", type=int, default=1, help="seed for the file split")
    ap.add_argument("--min-accuracy", type=float, default=0.0, help="exit 1 if held-out accuracy is below this")
    args = ap.parse_args()

    # The mouse channel is never used here: mouse events are a recording artifact on the bot side.
    refused = sorted(set(TICK_FEATURE_NAMES) & set(MOUSE_ONLY_FEATURES))
    if refused:
        sys.exit(f"refusing mouse-reading features: {refused}")

    files = {"human": expand(args.human), "bot": expand(args.bot)}
    for label, group in files.items():
        if not group:
            sys.exit(f"no {label} traces given")

    # Per class and per trace: the usable window vectors, in time order.
    data = {label: {path: load(path, args.window) for path in group} for label, group in files.items()}

    rng = random.Random(args.seed)
    test_files = set()
    single = {}
    for label in ("human", "bot"):
        group = sorted(files[label])
        single[label] = len(group) < 2
        if not single[label]:
            rng.shuffle(group)
            test_files.update(group[:max(1, round(0.3 * len(group)))])

    print("dropped windows (not counted as usable):")
    for label in ("human", "bot"):
        few = sum(data[label][p][1] for p in files[label])
        still = sum(data[label][p][2] for p in files[label])
        print(f"  {label:6s} {few} with fewer than 10 ticks, {still} with under {MIN_MOVING_S:g} s of movement")

    train, test = [], []
    counts = {}
    for label, y in (("human", 1), ("bot", 0)):
        n_usable = 0
        for path in sorted(files[label]):
            rows = data[label][path][0]
            n_usable += len(rows)
            if single[label]:
                cut = round(TRAIN_FRACTION * len(rows))
                train += [(r, y) for r in rows[:cut]]
                test += [(r, y) for r in rows[cut:]]
            elif path in test_files:
                test += [(r, y) for r in rows]
            else:
                train += [(r, y) for r in rows]
        counts[label] = n_usable

    print("usable windows (at least 10 ticks and 3 s of movement):")
    for label in ("human", "bot"):
        kind = "one trace, split by time" if single[label] else f"{len(files[label])} traces, split by file"
        print(f"  {label:6s} {counts[label]:4d}  ({kind})")
    for label in ("human", "bot"):
        if counts[label] < MIN_WINDOW_COUNT:
            print(f"*** WARNING: {label} has only {counts[label]} usable windows (fewer than {MIN_WINDOW_COUNT}). "
                  f"The accuracy below is NOT reliable. ***", file=sys.stderr)
        if single[label]:
            print(f"*** WARNING: {label} has one trace, so its split is by time within that trace, not by file. "
                  f"Held-out accuracy is optimistic. ***", file=sys.stderr)

    for label in ("human", "bot"):
        if counts[label] == 0:
            sys.exit(f"no usable {label} windows: nothing to train or test against")
    if not train or not test:
        sys.exit("no usable windows in the train or test split")
    if len({y for _, y in train}) < 2:
        sys.exit("training split has only one class")

    X_train, X_test = standardize([r for r, _ in train], [r for r, _ in test])
    y_train = [y for _, y in train]
    y_test = [y for _, y in test]
    w, b = train_logreg(X_train, y_train)
    acc = sum(p == t for p, t in zip(predict(w, b, X_test), y_test)) / len(y_test)

    print(f"traces: {len(files['human'])} human, {len(files['bot'])} bot; "
          f"windows: {len(train)} train, {len(test)} held-out")
    print(f"held-out accuracy: {acc:.3f} ({len(test)} windows)")
    print("top features (standardized weight, + = human):")
    for name, weight in sorted(zip(TICK_FEATURE_NAMES, w), key=lambda t: -abs(t[1]))[:10]:
        print(f"  {name:26s} {weight:+.3f}")
    if acc < args.min_accuracy:
        print(f"FAIL: accuracy {acc:.3f} below {args.min_accuracy}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
