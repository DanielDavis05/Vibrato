package dev.vibrato.tasks;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftGenericManuallyTaskTest {
    @Test
    void findsRequiredIngredientInInventoryOrCursorEvenWhenGridIsEmpty() {
        assertTrue(CraftGenericManuallyTask.hasMatchingIngredient("stick"::equals,
                List.of("coal", "stick"), null));
        assertTrue(CraftGenericManuallyTask.hasMatchingIngredient("stick"::equals,
                List.of("coal"), "stick"));
        assertFalse(CraftGenericManuallyTask.hasMatchingIngredient("stick"::equals,
                List.of("coal"), null));
    }

    @Test
    void partiallyFilledGridFinishesOneRecipeBeforeBalancingSharedInputs() {
        assertEquals(1, CraftGenericManuallyTask.inputCountForGrid(2, true));
        assertEquals(2, CraftGenericManuallyTask.inputCountForGrid(2, false));
        assertTrue(CraftGenericManuallyTask.canSplitDonor(2, true));
        assertFalse(CraftGenericManuallyTask.canSplitDonor(1, true));
        assertFalse(CraftGenericManuallyTask.canSplitDonor(4, false));
    }

    @Test
    void fallbackPlansOnlyTheRemainingOutput() {
        assertEquals(1, CraftGenericManuallyTask.requiredPerSlot(8, 4, 4));
        assertEquals(2, CraftGenericManuallyTask.requiredPerSlot(9, 4, 4));
        assertEquals(0, CraftGenericManuallyTask.requiredPerSlot(8, 8, 4));
    }
}
