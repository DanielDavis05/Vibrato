package dev.vibrato.tasks.enchant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnchantOptionChooserTest {

    private static final int[] COSTS = {1, 2, 3};

    @Test
    void picksMostExpensiveAffordableOption() {
        assertEquals(1, EnchantOptionChooser.choose(new int[]{5, 12, 0}, COSTS, 2, 5, 1));
    }

    @Test
    void picksLastOptionWhenFullyAffordable() {
        assertEquals(2, EnchantOptionChooser.choose(new int[]{5, 12, 22}, COSTS, 3, 3, 1));
    }

    @Test
    void returnsMinusOneWithoutEnoughXpLevels() {
        assertEquals(-1, EnchantOptionChooser.choose(new int[]{5, 12, 22}, COSTS, 0, 5, 1));
    }

    @Test
    void fallsBackToCheaperOptionWhenLapisRunsShort() {
        assertEquals(0, EnchantOptionChooser.choose(new int[]{5, 12, 22}, COSTS, 5, 1, 1));
    }

    @Test
    void returnsMinusOneWhenNoOptionMeetsMinimumPower() {
        assertEquals(-1, EnchantOptionChooser.choose(new int[]{5, 12, 0}, COSTS, 3, 3, 13));
    }

    @Test
    void ignoresEmptyOptions() {
        assertEquals(-1, EnchantOptionChooser.choose(new int[]{0, 0, 0}, COSTS, 3, 3, 1));
    }
}
