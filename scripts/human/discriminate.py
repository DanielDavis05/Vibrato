#!/usr/bin/env python3
"""Human vs bot discriminator (docs/HUMAN_MOTION_PLAN.md, W4).

Splits each trace into windows, computes features per window, and trains a small logistic regression by gradient
descent. The train/test split is by trace file, so windows from one trace never appear on both sides. Reports
held-out window accuracy and the features with the largest weights.

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
from features import FEATURE_NAMES, read_trace, split_windows, vector, window_features, window_is_usable  # noqa: E402


def expand(paths):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files.extend(sorted(glob.glob(os.path.join(p, "*.jsonl"))))
        else:
            files.append(p)
    return files


def load(files, label, window):
    """Rows of (trace path, label, feature vector) for every usable window."""
    rows = []
    for path in files:
        _, events = read_trace(path)
        for _, evs in split_windows(events, window):
            if window_is_usable(evs):
                rows.append((path, label, vector(window_features(evs, window))))
    return rows


def standardize(train, test):
    """Impute NaN with the training mean, then z-score with training statistics."""
    d = len(FEATURE_NAMES)
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

    files = {"human": expand(args.human), "bot": expand(args.bot)}
    for label, group in files.items():
        if len(group) < 2:
            sys.exit(f"need at least 2 {label} traces for a file-level split, got {len(group)}")

    rng = random.Random(args.seed)
    test_files = set()
    for label in ("human", "bot"):
        group = sorted(files[label])
        rng.shuffle(group)
        test_files.update(group[:max(1, round(0.3 * len(group)))])

    rows = load(files["human"], 1, args.window) + load(files["bot"], 0, args.window)
    train = [r for r in rows if r[0] not in test_files]
    test = [r for r in rows if r[0] in test_files]
    if not test or not train:
        sys.exit("no usable windows in the train or test split")

    X_train, X_test = standardize([r[2] for r in train], [r[2] for r in test])
    y_train = [r[1] for r in train]
    y_test = [r[1] for r in test]
    w, b = train_logreg(X_train, y_train)
    acc = sum(p == t for p, t in zip(predict(w, b, X_test), y_test)) / len(y_test)

    print(f"traces: {len(files['human'])} human, {len(files['bot'])} bot; "
          f"windows: {len(train)} train, {len(test)} held-out")
    print(f"held-out accuracy: {acc:.3f} ({len(test)} windows)")
    print("top features (standardized weight, + = human):")
    for name, weight in sorted(zip(FEATURE_NAMES, w), key=lambda t: -abs(t[1]))[:10]:
        print(f"  {name:26s} {weight:+.3f}")
    if acc < args.min_accuracy:
        print(f"FAIL: accuracy {acc:.3f} below {args.min_accuracy}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
