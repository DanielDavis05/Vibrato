package dev.vibrato.nav;

/**
 * Decides when an activity should be abandoned: it has gone too long without progress, or it has used up its
 * wall-clock budget. Times are game ticks.
 */
public final class WasteGuard {

    public static final long DEFAULT_NO_PROGRESS_TICKS = 2400;
    public static final long DEFAULT_BUDGET_TICKS = 36000;

    private final long _noProgressTicks;
    private final long _budgetTicks;
    private long _startTick;
    private long _lastProgressTick;

    public WasteGuard(long noProgressTicks, long budgetTicks) {
        _noProgressTicks = noProgressTicks;
        _budgetTicks = budgetTicks;
    }

    public static WasteGuard defaults() {
        return new WasteGuard(DEFAULT_NO_PROGRESS_TICKS, DEFAULT_BUDGET_TICKS);
    }

    /** Starts a new activity: its budget and progress clock begin now. */
    public void start(long tick) {
        _startTick = tick;
        _lastProgressTick = tick;
    }

    public void onProgress(long tick) {
        _lastProgressTick = tick;
    }

    public boolean isWasting(long tick) {
        return tick - _lastProgressTick >= _noProgressTicks || tick - _startTick > _budgetTicks;
    }
}
