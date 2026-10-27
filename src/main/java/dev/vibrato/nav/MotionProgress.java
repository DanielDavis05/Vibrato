package dev.vibrato.nav;

/** Tracks displacement while rebasing during idle periods without declaring recovery progress. */
final class MotionProgress {
    enum Result { IDLE_OR_INITIAL, MOVED, NONE }

    private final double _distance;
    private boolean _anchored;
    private boolean _inWater;
    private double _x, _y, _z;

    MotionProgress(double distance) {
        _distance = distance;
    }

    Result observe(double x, double y, double z, boolean wantsToMove, boolean inWater) {
        // Rebase while a recovery owns movement and at water transitions. Neither
        // action movement nor a change of coordinate metric proves path progress.
        if (!_anchored || !wantsToMove || inWater != _inWater) {
            _inWater = inWater;
            anchor(x, y, z);
            return Result.IDLE_OR_INITIAL;
        }
        double dy = inWater ? 0 : y - _y;
        if (Math.sqrt(Math.pow(x - _x, 2) + dy * dy + Math.pow(z - _z, 2)) >= _distance) {
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
