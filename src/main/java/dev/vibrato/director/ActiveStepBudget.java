package dev.vibrato.director;

import java.time.Duration;

/** Measures a step against Director ticks, which do not advance while another chain has control. */
final class ActiveStepBudget {
    private static final long MILLIS_PER_TICK = 50;
    private final long startTick;
    private final long budgetTicks;

    ActiveStepBudget(long startTick, Duration duration) {
        this.startTick = startTick;
        long millis = duration.toMillis();
        this.budgetTicks = Math.max(1, millis / MILLIS_PER_TICK + (millis % MILLIS_PER_TICK == 0 ? 0 : 1));
    }

    boolean elapsed(long tick) {
        return tick - startTick >= budgetTicks;
    }
}
