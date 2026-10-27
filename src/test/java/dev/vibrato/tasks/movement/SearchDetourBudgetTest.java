package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchDetourBudgetTest {
    @Test void waitsAndStopsAtTimeOrShareLimit() {
        SearchDetourBudget budget = new SearchDetourBudget();
        assertFalse(budget.canStart(24.9, Double.POSITIVE_INFINITY));
        assertTrue(budget.canStart(25, Double.POSITIVE_INFINITY));
        budget.start(25);
        assertTrue(budget.canContinue(28));
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
        assertTrue(budget.canContinue(62));
        assertFalse(budget.canContinue(63));
        budget.end(63);
        assertFalse(budget.canStart(64, 144));
        budget.reset();
        assertFalse(budget.canStart(24, 144));
    }
}
