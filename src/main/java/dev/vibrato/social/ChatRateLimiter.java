package dev.vibrato.social;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps a bot from spamming chat: at most {@code maxPerMinute} sends in any sliding minute, and at least
 * {@code minGapMillis} between two sends. Times are passed in, so the limiter is pure and testable.
 */
public final class ChatRateLimiter {

    public static final int DEFAULT_MAX_PER_MINUTE = 4;
    public static final long DEFAULT_MIN_GAP_MILLIS = 6000;
    private static final long WINDOW_MILLIS = 60_000;

    private static final ChatRateLimiter SHARED = new ChatRateLimiter(DEFAULT_MAX_PER_MINUTE, DEFAULT_MIN_GAP_MILLIS);

    private final int _maxPerMinute;
    private final long _minGapMillis;
    private final Deque<Long> _sent = new ArrayDeque<>();

    public ChatRateLimiter(int maxPerMinute, long minGapMillis) {
        if (maxPerMinute < 1 || minGapMillis < 0) {
            throw new IllegalArgumentException("maxPerMinute must be >= 1 and minGapMillis >= 0");
        }
        _maxPerMinute = maxPerMinute;
        _minGapMillis = minGapMillis;
    }

    /** Process-wide limiter; each bot runs in its own JVM, so this is per bot. */
    public static ChatRateLimiter shared() {
        return SHARED;
    }

    /** Earliest time at which a send is allowed, given the sends recorded so far. */
    public synchronized long nextAllowedAt(long now) {
        prune(now);
        long allowed = now;
        if (!_sent.isEmpty()) {
            allowed = Math.max(allowed, _sent.peekLast() + _minGapMillis);
        }
        if (_sent.size() >= _maxPerMinute) {
            allowed = Math.max(allowed, _sent.peekFirst() + WINDOW_MILLIS);
        }
        return allowed;
    }

    public synchronized boolean canSend(long now) {
        return nextAllowedAt(now) <= now;
    }

    /** Records a send if one is allowed now. */
    public synchronized boolean tryAcquire(long now) {
        if (!canSend(now)) {
            return false;
        }
        _sent.addLast(now);
        return true;
    }

    private void prune(long now) {
        while (!_sent.isEmpty() && now - _sent.peekFirst() >= WINDOW_MILLIS) {
            _sent.removeFirst();
        }
    }
}
