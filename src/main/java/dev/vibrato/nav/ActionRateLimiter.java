package dev.vibrato.nav;

/** Lets at most one navigation intervention through per {@link #intervalTicks}. Times are game ticks. */
public final class ActionRateLimiter {

    public static final long DEFAULT_INTERVAL_TICKS = 20 * 20;

    private final long _intervalTicks;
    private boolean _fired;
    private long _lastTick;

    public ActionRateLimiter() {
        this(DEFAULT_INTERVAL_TICKS);
    }

    public ActionRateLimiter(long intervalTicks) {
        _intervalTicks = intervalTicks;
    }

    /** @return true and records the intervention if the interval has passed since the last one */
    public boolean tryAcquire(long tick) {
        if (_fired && tick - _lastTick < _intervalTicks) {
            return false;
        }
        _fired = true;
        _lastTick = tick;
        return true;
    }
}
