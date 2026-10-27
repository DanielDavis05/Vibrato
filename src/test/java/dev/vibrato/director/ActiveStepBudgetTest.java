package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ActiveStepBudgetTest {
    @Test
    void resumesWithRemainingActiveTimeAndIgnoresPausedTicks() {
        long directorTick = 100;
        ActiveStepBudget budget = new ActiveStepBudget(directorTick, Duration.ofSeconds(10));
        directorTick += 80;
        assertFalse(budget.elapsed(directorTick));
        // Another chain runs for 1,000 world ticks; the director's tick does not advance.
        for (int worldTick = 0; worldTick < 1000; worldTick++) assertFalse(budget.elapsed(directorTick));
        directorTick += 119;
        assertFalse(budget.elapsed(directorTick));
        directorTick++;
        assertTrue(budget.elapsed(directorTick));
    }

    @Test
    void roundsFractionalTicksUp() {
        ActiveStepBudget budget = new ActiveStepBudget(5, Duration.ofMillis(51));
        assertFalse(budget.elapsed(6));
        assertTrue(budget.elapsed(7));
    }
}
