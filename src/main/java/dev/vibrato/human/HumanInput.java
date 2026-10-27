package dev.vibrato.human;

import java.util.random.RandomGenerator;

/**
 * Timing for inputs, so the bot does not react or press keys on a perfect frame grid.
 */
public final class HumanInput {

    private static final double REACTION_MEDIAN_TICKS = 5;
    private static final double REACTION_SPREAD = 0.3;
    private static final long MIN_REACTION_TICKS = 3;
    private static final long MAX_REACTION_TICKS = 9;

    private HumanInput() {}

    /**
     * Delay before reacting to a new stimulus: log-normal around 5 ticks, clamped to 3..9.
     */
    public static int reactionTicks(RandomGenerator rng) {
        double sample = REACTION_MEDIAN_TICKS * Math.exp(REACTION_SPREAD * rng.nextGaussian());
        long ticks = Math.round(sample);
        return (int) Math.max(MIN_REACTION_TICKS, Math.min(MAX_REACTION_TICKS, ticks));
    }

    /**
     * Holds a key for {@code baseTicks} give or take one tick.
     */
    public static int keyHoldJitterTicks(RandomGenerator rng, int baseTicks) {
        return Math.max(0, baseTicks + rng.nextInt(3) - 1);
    }
}
