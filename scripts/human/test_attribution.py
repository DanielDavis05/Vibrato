#!/usr/bin/env python3
"""Synthetic input attribution and tick-clock regression (no game recordings)."""
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import features as F


class AttributionTest(unittest.TestCase):
    def test_matching_ticks_and_missing_samples(self):
        events = []
        for n in range(1, 6):
            if n != 4:
                # Release forward in shaping on tick 2; intended releases on tick 3.
                intended = 33 if n < 3 else 32
                shaped = 33 if n == 1 else 32
                events.append(dict(t="ikey", n=n, ms=n * 100 + 9, intended=intended, shaped=shaped))
            if n == 1:
                events.extend([dict(t="key", ms=n * 100, key="forward", down=True),
                               dict(t="key", ms=n * 100, key="sprint", down=True)])
            if n == 2:
                events.append(dict(t="key", ms=n * 100, key="forward", down=False))
            if n == 3:
                events.append(dict(t="key", ms=n * 100, key="sprint", down=False))
            if n == 5:
                events.append(dict(t="key", ms=n * 100, key="sprint", down=True))
            events.append(dict(t="tick", n=n, ms=n * 100, vx=0, vz=0, nextX=1))
        F.tick_clock(events)
        self.assertEqual([e["tk"] for e in events if e["t"] == "ikey"], [50, 100, 150, 250])
        a = F.input_attribution(events)
        self.assertEqual(a["matched_ticks"], 4)
        self.assertEqual(a["intended_forward_releases"], 1)
        self.assertEqual(a["shaped_forward_releases"], 1)
        self.assertEqual(a["effective_forward_releases"], 1)
        self.assertEqual(a["effective_sprint_restarts"], 0)  # missing tick 4 breaks transition
        self.assertEqual(a["stationary_path_intended_forward_ticks"], 2)
        self.assertEqual(a["stationary_path_shaped_forward_ticks"], 1)
        self.assertEqual(a["stationary_path_effective_forward_ticks"], 1)
        self.assertIsNone(F.input_attribution([e for e in events if e["t"] != "ikey"]))

    def test_windows_order_future_snapshots(self):
        events = [dict(t="ikey", n=200, ms=9990, intended=0, shaped=0),
                  dict(t="mouse", ms=9991, dx=0, dy=0),
                  dict(t="tick", n=200, ms=10000)]
        F.tick_clock(events)
        windows = F.split_windows(events)
        self.assertEqual([start for start, _ in windows], [0, 10000])


if __name__ == "__main__":
    unittest.main()
