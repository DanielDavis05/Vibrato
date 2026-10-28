package dev.vibrato.tasks.container;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoreCompletionTest {
    @Test void collectingDoesNotFinishWhenTheInventoryIsEmptyDuringAcquisition() {
        assertFalse(StoreCompletion.finished(true, new int[]{5}, new int[]{0}, () -> true));
        assertFalse(StoreCompletion.finished(true, new int[]{5}, new int[]{2}, () -> true));
    }
    @Test void havingInventoryToDepositStillDoesNotSatisfyTheOutputGoal() {
        assertFalse(StoreCompletion.finished(true, new int[]{5}, new int[]{0}, () -> false));
        assertTrue(StoreCompletion.finished(true, new int[]{5}, new int[]{5}, () -> false));
    }
    @Test void everyRequestedQuantityMustActuallyHaveBeenDeposited() {
        assertFalse(StoreCompletion.finished(true, new int[]{5, 2}, new int[]{5, 1}, () -> true));
        assertTrue(StoreCompletion.finished(true, new int[]{5, 2}, new int[]{5, 2}, () -> true));
        assertFalse(StoreCompletion.finished(true, new int[]{5}, new int[]{-1}, () -> true));
    }
    @Test void collectingCompletionNeverQueriesCurrentInventoryAsItsOracle() {
        assertTrue(StoreCompletion.finished(true, new int[]{5}, new int[]{5}, () -> { throw new AssertionError("inventory queried"); }));
    }
    @Test void nonCollectingDepositPreservesEmptyInventoryNoOpSemantics() {
        assertTrue(StoreCompletion.finished(false, new int[]{5}, new int[]{0}, () -> true));
        assertFalse(StoreCompletion.finished(false, new int[]{5}, new int[]{0}, () -> false));
    }
}
