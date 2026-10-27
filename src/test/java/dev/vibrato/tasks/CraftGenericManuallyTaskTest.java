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
    void fallbackPlansOnlyTheRemainingOutput() {
        assertEquals(1, CraftGenericManuallyTask.requiredPerSlot(8, 4, 4));
        assertEquals(2, CraftGenericManuallyTask.requiredPerSlot(9, 4, 4));
        assertEquals(0, CraftGenericManuallyTask.requiredPerSlot(8, 8, 4));
    }
}
