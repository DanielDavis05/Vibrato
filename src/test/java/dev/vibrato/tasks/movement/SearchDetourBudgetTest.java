package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchDetourBudgetTest {
    @Test void shortSearchLegsAccumulateAndInterruptionsDoNotAdvanceTime() {
        SearchActiveClock clock = new SearchActiveClock();
        SearchDetourBudget budget = new SearchDetourBudget();
        for (int i = 0; i < 240; i++) clock.tick(); // first 12-second leg
        double pausedAt = clock.elapsed();
        assertEquals(pausedAt, clock.elapsed()); // no search tick during a defense interrupt
        for (int i = 0; i < 240; i++) clock.tick(); // second leg
        assertFalse(budget.canStart(clock.elapsed(), Double.POSITIVE_INFINITY));
        for (int i = 0; i < 20; i++) clock.tick(); // next leg reaches 25 seconds
        assertTrue(budget.canStart(clock.elapsed(), Double.POSITIVE_INFINITY));
        budget.start(clock.elapsed());
        for (int i = 0; i < 20; i++) clock.tick();
        budget.end(clock.elapsed());
        for (int i = 0; i < 600; i++) clock.tick();
        assertFalse(budget.canStart(clock.elapsed(), 143));
        assertTrue(budget.canStart(clock.elapsed(), 144));
    }

    @Test void waitsAndStopsAtTimeOrShareLimit() {
        SearchDetourBudget budget = new SearchDetourBudget();
        assertFalse(budget.canStart(24.9, Double.POSITIVE_INFINITY));
        assertTrue(budget.canStart(25, Double.POSITIVE_INFINITY));
        budget.start(25);
        assertTrue(budget.canContinue(25.7));
        assertFalse(budget.canContinue(25.9));
        assertFalse(budget.canContinue(29));
        budget.end(29);
        // Four seconds is within 15% of the 29 seconds spent searching.
        assertFalse(budget.canStart(30, 1000));
    }

    @Test void cooldownAndTravelAreBothRequired() {
        SearchDetourBudget budget = new SearchDetourBudget();
        budget.start(30);
        budget.end(32);
        assertFalse(budget.canStart(61, 144));
        assertFalse(budget.canStart(62, 143));
        assertTrue(budget.canStart(62, 144));
    }

    @Test void totalDetoursCannotExceedSearchTimeShare() {
        SearchDetourBudget budget = new SearchDetourBudget();
        budget.start(25);
        assertFalse(budget.canContinue(29));
        budget.end(29);
        assertTrue(budget.canStart(59, 144));
        budget.start(59);
        assertTrue(budget.canContinue(59.7));
        assertFalse(budget.canContinue(63));
        budget.end(63);
        assertFalse(budget.canStart(64, 144));
        budget.reset();
        assertFalse(budget.canStart(24, 144));
    }
}
