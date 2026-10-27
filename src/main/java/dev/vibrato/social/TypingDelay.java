package dev.vibrato.social;

import java.util.random.RandomGenerator;

/**
 * How long a human would take to type a line: a random 40 to 120 ms per character, capped at 4 seconds.
 */
public final class TypingDelay {

    public static final int MIN_MS_PER_CHAR = 40;
    public static final int MAX_MS_PER_CHAR = 120;
    public static final long CAP_MS = 4000;
    public static final long MS_PER_TICK = 50;

    private TypingDelay() {}

    public static long millis(String text, RandomGenerator rng) {
        long total = 0;
        for (int i = 0; i < text.length(); ++i) {
            total += MIN_MS_PER_CHAR + rng.nextInt(MAX_MS_PER_CHAR - MIN_MS_PER_CHAR + 1);
        }
        return Math.min(total, CAP_MS);
    }

    public static int ticks(String text, RandomGenerator rng) {
        long ms = millis(text, rng);
        return (int) ((ms + MS_PER_TICK - 1) / MS_PER_TICK);
    }
}
