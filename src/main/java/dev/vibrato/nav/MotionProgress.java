package dev.vibrato.nav;

/** Tracks displacement while rebasing during idle periods without declaring recovery progress. */
final class MotionProgress {
    enum Result { IDLE_OR_INITIAL, MOVED, NONE }

    private final double _distance;
    private boolean _anchored;
    private double _x, _y, _z;

    MotionProgress(double distance) {
        _distance = distance;
    }

    Result observe(double x, double y, double z, boolean wantsToMove) {
        if (!_anchored || !wantsToMove) {
            anchor(x, y, z);
            return Result.IDLE_OR_INITIAL;
        }
        if (Math.sqrt(Math.pow(x - _x, 2) + Math.pow(y - _y, 2) + Math.pow(z - _z, 2)) >= _distance) {
            anchor(x, y, z);
            return Result.MOVED;
        }
        return Result.NONE;
    }

    void reset() { _anchored = false; }

    private void anchor(double x, double y, double z) {
        _anchored = true;
        _x = x;
        _y = y;
        _z = z;
    }
}
