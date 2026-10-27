package dev.vibrato.director;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts recent step failures in a sliding window. Times are game ticks. */
public final class FailureChurn {

    public static final long DEFAULT_WINDOW_TICKS = 30 * 20;
    public static final int DEFAULT_THRESHOLD = 4;

    private final long _windowTicks;
    private final int _threshold;
    private final Deque<Long> _failures = new ArrayDeque<>();

    public FailureChurn() {
        this(DEFAULT_WINDOW_TICKS, DEFAULT_THRESHOLD);
    }

    public FailureChurn(long windowTicks, int threshold) {
        _windowTicks = windowTicks;
        _threshold = threshold;
    }

    public void failed(long tick) {
        _failures.addLast(tick);
        prune(tick);
    }

    /** True while at least {@code threshold} failures fall inside the window. */
    public boolean churning(long tick) {
        prune(tick);
        return _failures.size() >= _threshold;
    }

    /** Forgets the recorded failures, so a breather gets a clean count. */
    public void clear() {
        _failures.clear();
    }

    private void prune(long tick) {
        while (!_failures.isEmpty() && tick - _failures.peekFirst() > _windowTicks) {
            _failures.removeFirst();
        }
    }
}
