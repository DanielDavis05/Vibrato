package dev.vibrato.util.progresscheck;

import net.minecraft.util.math.Vec3d;

/**
 * Bounds how long a still player may be excused while Baritone is computing a path. A search
 * that never finds a route restarts forever, so an unbounded excuse hides a permanent stall.
 */
final class PathCalculationGrace {
    private static final double MOVED_BLOCKS = 2;

    private final long _graceNanos;
    private Vec3d _anchor;
    private long _sinceNanos;

    PathCalculationGrace(double graceSeconds) {
        _graceNanos = (long) (graceSeconds * 1_000_000_000L);
    }

    /** True while the player may still be waiting on a legitimate path calculation. */
    boolean excuses(Vec3d position, long nowNanos) {
        if (_anchor == null || position.distanceTo(_anchor) >= MOVED_BLOCKS) {
            _anchor = position;
            _sinceNanos = nowNanos;
            return true;
        }
        return nowNanos - _sinceNanos < _graceNanos;
    }

    void reset() {
        _anchor = null;
    }
}
