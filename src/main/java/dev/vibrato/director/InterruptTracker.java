package dev.vibrato.director;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts how often a step was interrupted in a sliding window. Progress resets the count: an interruption that comes
 * after the bot moved or its inventory changed is not counted as harassment. Times are game ticks.
 */
public final class InterruptTracker {

    public static final long DEFAULT_WINDOW_TICKS = 60 * 20;
    public static final int DEFAULT_THRESHOLD = 6;

    private final long _windowTicks;
    private final int _threshold;
    private final Deque<Long> _interrupts = new ArrayDeque<>();
    private long _progressKey;
    private boolean _hasKey;

    public InterruptTracker() {
        this(DEFAULT_WINDOW_TICKS, DEFAULT_THRESHOLD);
    }

    public InterruptTracker(long windowTicks, int threshold) {
        _windowTicks = windowTicks;
        _threshold = threshold;
    }

    /** Records an interruption; {@code progressKey} changes whenever the bot has made progress. */
    public void interrupted(long tick, long progressKey) {
        if (!_hasKey || progressKey != _progressKey) {
            _interrupts.clear();
            _progressKey = progressKey;
            _hasKey = true;
        }
        _interrupts.addLast(tick);
        prune(tick);
    }

    public boolean harassed(long tick) {
        prune(tick);
        return _interrupts.size() >= _threshold;
    }

    private void prune(long tick) {
        while (!_interrupts.isEmpty() && tick - _interrupts.peekFirst() > _windowTicks) {
            _interrupts.removeFirst();
        }
    }
}
