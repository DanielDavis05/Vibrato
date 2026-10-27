package dev.vibrato.director;

/**
 * Pure stuck verdict for one step. A step is only judged stuck after it has run for {@link #minRuntimeTicks} and the
 * stuck signal has persisted for {@link #confirmTicks} in a row. Times are game ticks.
 */
public final class StepHealth {

    public static final long DEFAULT_MIN_RUNTIME_TICKS = 20 * 20;
    public static final long DEFAULT_CONFIRM_TICKS = 30 * 20;

    private final long _minRuntimeTicks;
    private final long _confirmTicks;
    private long _age;
    private long _stuckTicks;
    private boolean _hasPosition;
    private double _x, _y, _z;
    private static final double PROGRESS_DISTANCE_SQUARED = 1;

    public StepHealth() {
        this(DEFAULT_MIN_RUNTIME_TICKS, DEFAULT_CONFIRM_TICKS);
    }

    public StepHealth(long minRuntimeTicks, long confirmTicks) {
        _minRuntimeTicks = minRuntimeTicks;
        _confirmTicks = confirmTicks;
    }

    /** Records one tick of the step, with whether the stuck signal is raised on it. */
    public void tick(boolean stuckNow) {
        _age++;
        _stuckTicks = stuckNow ? _stuckTicks + 1 : 0;
    }

    /** A wander counts as stuck only while it fails to move meaningfully. */
    public void tick(boolean wandering, double x, double y, double z) {
        if (!wandering) {
            _hasPosition = false;
            tick(false);
            return;
        }
        double dx = x - _x, dy = y - _y, dz = z - _z;
        if (!_hasPosition || dx * dx + dy * dy + dz * dz >= PROGRESS_DISTANCE_SQUARED) {
            _x = x;
            _y = y;
            _z = z;
            _hasPosition = true;
            tick(false);
        } else {
            tick(true);
        }
    }

    public boolean verdict() {
        return _age >= _minRuntimeTicks && _stuckTicks >= _confirmTicks;
    }
}
